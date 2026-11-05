use crate::mesh::protocol::{
    CpuTelemetry, DiskTelemetry, MemoryTelemetry, NetworkTelemetry, ServiceTelemetry,
    TelemetryPayload, ThermalTelemetry,
};
use std::time::Instant;
use sysinfo::{
    Components, Disks, Networks, System,
};
use tracing::debug;

pub struct TelemetryCollector {
    sys: System,
    disks: Disks,
    networks: Networks,
    components: Components,
    last_net_sample: Instant,
    last_rx_total: u64,
    last_tx_total: u64,
}

impl TelemetryCollector {
    pub fn new() -> Self {
        let mut sys = System::new_all();
        sys.refresh_all();
        let disks = Disks::new_with_refreshed_list();
        let networks = Networks::new_with_refreshed_list();
        let components = Components::new_with_refreshed_list();

        let mut initial_rx = 0;
        let mut initial_tx = 0;
        for (_, net) in &networks {
            initial_rx += net.total_received();
            initial_tx += net.total_transmitted();
        }

        Self {
            sys,
            disks,
            networks,
            components,
            last_net_sample: Instant::now(),
            last_rx_total: initial_rx,
            last_tx_total: initial_tx,
        }
    }

    pub fn collect(&mut self) -> TelemetryPayload {
        self.sys.refresh_cpu_all();
        self.sys.refresh_memory();
        self.disks.refresh(true);
        self.networks.refresh(true);
        self.components.refresh(true);

        let hostname = System::host_name().unwrap_or_else(|| "unknown".into());
        let os_name = System::name().unwrap_or_else(|| "Unknown OS".into());
        let os_ver = System::os_version().unwrap_or_default();
        let os_label = format!("{} {}", os_name, os_ver);
        let uptime_secs = System::uptime();

        // ─── CPU ───
        let cpus = self.sys.cpus();
        let cpu_model = cpus.first().map(|c| c.brand().to_string()).unwrap_or_default();
        let global_cpu_usage = self.sys.global_cpu_usage();
        let load_avg = System::load_average();

        let cpu = CpuTelemetry {
            model: cpu_model,
            physical_cores: self.sys.physical_core_count().unwrap_or(cpus.len()),
            logical_cores: cpus.len(),
            usage_pct: global_cpu_usage,
            load_avg: [load_avg.one, load_avg.five, load_avg.fifteen],
        };

        // ─── Memory ───
        let total_mem = self.sys.total_memory();
        let used_mem = self.sys.used_memory();
        let free_mem = self.sys.free_memory();
        let avail_mem = self.sys.available_memory();
        let used_pct = if total_mem > 0 {
            (used_mem as f32 / total_mem as f32) * 100.0
        } else {
            0.0
        };

        let memory = MemoryTelemetry {
            total_bytes: total_mem,
            used_bytes: used_mem,
            free_bytes: free_mem,
            available_bytes: avail_mem,
            used_pct,
            swap_total_bytes: self.sys.total_swap(),
            swap_used_bytes: self.sys.used_swap(),
        };

        // ─── Disks ───
        let mut disks = Vec::new();
        for disk in &self.disks {
            let total = disk.total_space();
            let avail = disk.available_space();
            let used = total.saturating_sub(avail);
            let pct = if total > 0 {
                (used as f32 / total as f32) * 100.0
            } else {
                0.0
            };

            disks.push(DiskTelemetry {
                name: disk.name().to_string_lossy().to_string(),
                mount_point: disk.mount_point().to_string_lossy().to_string(),
                fs_type: disk.file_system().to_string_lossy().to_string(),
                total_bytes: total,
                available_bytes: avail,
                used_bytes: used,
                used_pct: pct,
            });
        }

        // ─── Network Rates ───
        let now = Instant::now();
        let elapsed_secs = now.duration_since(self.last_net_sample).as_secs_f64().max(0.1);

        let mut current_rx = 0;
        let mut current_tx = 0;
        let mut primary_ip = "127.0.0.1".to_string();

        for (iface, net) in &self.networks {
            current_rx += net.total_received();
            current_tx += net.total_transmitted();

            // Look for non-loopback IP
            if !iface.contains("lo") && !iface.contains("loopback") {
                for ip in net.ip_networks() {
                    let addr = ip.addr;
                    if !addr.is_loopback() {
                        primary_ip = addr.to_string();
                        break;
                    }
                }
            }
        }

        let rx_rate = ((current_rx.saturating_sub(self.last_rx_total)) as f64 / elapsed_secs) as u64;
        let tx_rate = ((current_tx.saturating_sub(self.last_tx_total)) as f64 / elapsed_secs) as u64;

        self.last_net_sample = now;
        self.last_rx_total = current_rx;
        self.last_tx_total = current_tx;

        let network = NetworkTelemetry {
            primary_ip,
            rx_bytes_sec: rx_rate,
            tx_bytes_sec: tx_rate,
            total_rx_bytes: current_rx,
            total_tx_bytes: current_tx,
        };

        // ─── Thermals ───
        let mut cpu_temp: Option<f32> = None;
        for comp in &self.components {
            let label = comp.label().to_lowercase();
            if label.contains("cpu") || label.contains("core") || label.contains("package") {
                if let Some(temp) = comp.temperature() {
                    cpu_temp = Some(temp);
                    break;
                }
            }
        }

        let thermals = cpu_temp.map(|t| ThermalTelemetry { cpu_temp_c: Some(t) });

        // ─── Monitored Services ───
        let mut services = Vec::new();
        let monitored_targets = ["dockerd", "docker", "ollama", "sshd", "ssh", "stalwart", "personal-site-rust"];
        
        self.sys.refresh_processes(sysinfo::ProcessesToUpdate::All, true);
        for target in monitored_targets {
            for (pid, proc) in self.sys.processes() {
                if proc.name().to_string_lossy().to_lowercase().contains(target) {
                    services.push(ServiceTelemetry {
                        name: target.to_string(),
                        status: "running".into(),
                        pid: Some(pid.as_u32()),
                        memory_mb: Some(proc.memory() as f64 / (1024.0 * 1024.0)),
                    });
                    break;
                }
            }
        }

        debug!("Collected telemetry snapshot for {}", hostname);

        TelemetryPayload {
            hostname,
            os_label,
            uptime_secs,
            cpu,
            memory,
            disks,
            network,
            gpu: None, // Will be populated by GpuCollector
            ai_engine: None, // Will be populated by AiBridge
            thermals,
            services,
        }
    }
}

