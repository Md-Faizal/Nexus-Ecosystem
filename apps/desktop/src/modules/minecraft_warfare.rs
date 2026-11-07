use std::fs;
use std::path::{Path, PathBuf};
use sysinfo::System;
use tracing::info;

pub struct MinecraftWarfareManager;

impl MinecraftWarfareManager {
    /// Generates industrial-standard Forge 1.20.1 configuration files, systemd unit, and kernel parameters
    #[allow(unused_variables)]
    pub fn setup(
        install_dir: &str,
        heap_mb: Option<u64>,
        headless: bool,
        apply_sysctl: bool,
    ) -> Result<(), Box<dyn std::error::Error>> {
        let mut sys = System::new_all();
        sys.refresh_all();

        let total_ram_mb = sys.total_memory() / (1024 * 1024);
        let cpu_cores = sys.cpus().len();

        info!("============================================================");
        info!("   NEXUS MINECRAFT WARFARE FLEET - NODE AUTO-PROVISIONER");
        info!("   Detected Hardware:  {} cores | {} MB RAM", cpu_cores, total_ram_mb);
        info!("   Target Directory:   {}", install_dir);
        info!("============================================================");

        // 1. Calculate optimal JVM heap based on physical memory
        let (xms, xmx) = if let Some(custom) = heap_mb {
            let initial = (custom * 3 / 4).max(1024);
            (initial, custom)
        } else if total_ram_mb <= 4500 {
            // Strict 4 GB profile (e.g. i3-6100 headless Zorin host)
            info!("Applying ultra-lean 4 GB profile (leaves ~1.2 GB for OS + native off-heap)");
            (2048, 2800)
        } else if total_ram_mb <= 9000 {
            // 8 GB profile (e.g. i3-8100 Fedora rig)
            info!("Applying 8 GB profile (leaves 3 GB for OS + client)");
            (4096, 5120)
        } else {
            // High memory workstation profile
            let target_xmx = (total_ram_mb * 6 / 10).min(12288);
            (target_xmx / 2, target_xmx)
        };

        let target_dir = PathBuf::from(install_dir);
        fs::create_dir_all(&target_dir)?;

        // 2. Write user_jvm_args.txt
        let jvm_args_content = format!(
            r#"# ==============================================================================
# NEXUS INDUSTRIAL WARFARE - FORGE 1.20.1 JVM ENGINE FLAGS
# Engineered for low-latency dogfights, radar tracking & ballistic missile simulations
# Memory Envelope: -Xms{xms}M -Xmx{xmx}M
# ==============================================================================

-Xms{xms}M
-Xmx{xmx}M

# Garbage Collector: Low-Pause G1GC
-XX:+UseG1GC
-XX:+ParallelRefProcEnabled
-XX:MaxGCPauseMillis=25
-XX:+UnlockExperimentalVMOptions
-XX:+DisableExplicitGC
-XX:+AlwaysPreTouch

# G1 Region & Young/Old Generation Tuning
-XX:G1NewSizePercent=20
-XX:G1MaxNewSizePercent=35
-XX:G1ReservePercent=15
-XX:G1HeapRegionSize=8M
-XX:InitiatingHeapOccupancyPercent=45
-XX:G1MixedGCCountTarget=4
-XX:G1PeriodicGCInterval=0

# Strict Native Memory Bounds (Avoids Host OOM-Killer)
-XX:MetaspaceSize=256M
-XX:MaxMetaspaceSize=384M
-XX:MaxDirectMemorySize=256M
-XX:CompressedClassSpaceSize=128M

# CPU & String Optimization
-XX:+UseStringDeduplication
-XX:-OmitStackTraceInFastThrow
"#
        );

        let jvm_args_path = target_dir.join("user_jvm_args.txt");
        fs::write(&jvm_args_path, &jvm_args_content)?;
        info!("Generated JVM arguments: {}", jvm_args_path.display());

        // 3. Write low-latency server.properties
        let server_props_content = r#"# Minecraft server properties - Nexus Low-Latency Warfare Profile
server-port=25565
enable-rcon=false
online-mode=false
allow-flight=true
view-distance=7
simulation-distance=5
network-compression-threshold=256
max-tick-time=180000
difficulty=hard
gamemode=survival
spawn-protection=0
sync-chunk-writes=true
entity-broadcast-range-percentage=100
rate-limit=0
motd=\u00a74\u00a7l[NEXUS WARFARE]\u00a7r \u00a77Fighter Jets \u00a78|\u00a7r \u00a7cBallistix ICBM \u00a78|\u00a7r \u00a7eTaCZ
"#;

        let props_path = target_dir.join("server.properties");
        if !props_path.exists() {
            fs::write(&props_path, server_props_content)?;
            info!("Generated server.properties: {}", props_path.display());
        }

        // 4. Linux Specific Provisioning (Headless, Sysctl, Systemd)
        #[cfg(target_os = "linux")]
        {
            if headless {
                info!("Configuring OS for headless terminal target (multi-user.target)...");
                let status = Command::new("systemctl")
                    .args(["set-default", "multi-user.target"])
                    .status();
                match status {
                    Ok(s) if s.success() => info!("✅ Set default systemd target to multi-user.target"),
                    Ok(s) => warn!("Failed to set multi-user.target (exit code: {:?})", s.code()),
                    Err(e) => warn!("Could not invoke systemctl: {}", e),
                }
            }

            if apply_sysctl {
                Self::apply_network_sysctl()?;
            }

            // Generate systemd unit file
            let current_user = std::env::var("USER").unwrap_or_else(|_| "ubuntu".to_string());
            let unit_content = format!(
                r#"[Unit]
Description=Minecraft Forge 1.20.1 Warfare Dedicated Server
After=network-online.target local-fs.target
Wants=network-online.target

[Service]
Type=simple
User={user}
WorkingDirectory={workdir}
ProtectSystem=full
ProtectHome=false
PrivateTmp=true

# Real-time game tick priority
Nice=-10
CPUSchedulingPolicy=other

# Memory safety guards against OOM (cgroups v2)
MemoryAccounting=true
MemoryHigh={mem_high}M
MemoryMax={mem_max}M
LimitNOFILE=65536

# Launch Forge using modern argument files
ExecStart=/usr/bin/java @user_jvm_args.txt @libraries/net/minecraftforge/forge/1.20.1-47.3.0/unix_args.txt nogui

Restart=on-failure
RestartSec=15s
KillSignal=SIGTERM
TimeoutStopSec=60

[Install]
WantedBy=multi-user.target
"#,
                user = current_user,
                workdir = target_dir.canonicalize().unwrap_or(target_dir.clone()).display(),
                mem_high = xmx + 700,
                mem_max = xmx + 950,
            );

            let service_file = target_dir.join("minecraft-warfare.service");
            fs::write(&service_file, unit_content.clone())?;
            info!("Generated systemd service template: {}", service_file.display());

            // If running as root, attempt direct installation
            let sys_unit_path = Path::new("/etc/systemd/system/minecraft-warfare.service");
            if let Ok(_) = fs::write(sys_unit_path, &unit_content) {
                info!("Installed systemd unit to /etc/systemd/system/minecraft-warfare.service");
                let _ = Command::new("systemctl").arg("daemon-reload").status();
            }
        }

        // 5. Windows Helper Script
        #[cfg(target_os = "windows")]
        {
            let run_bat = target_dir.join("run.bat");
            let bat_content = format!(
                "@echo off\r\ntitle Nexus Warfare Server\r\njava @user_jvm_args.txt @libraries/net/minecraftforge/forge/1.20.1-47.3.0/win_args.txt nogui\r\npause\r\n"
            );
            fs::write(&run_bat, bat_content)?;
            info!("Generated Windows launch script: {}", run_bat.display());
        }

        info!("Provisioning complete! Ready for warfare operations.");
        Ok(())
    }

    /// Applies kernel TCP latency optimizations (BBR, FQ, bufferbloat mitigation)
    pub fn apply_network_sysctl() -> Result<(), Box<dyn std::error::Error>> {
        #[cfg(target_os = "linux")]
        {
            let conf = r#"# Nexus Warfare - Kernel Low-Latency TCP Configuration
net.core.default_qdisc = fq
net.ipv4.tcp_congestion_control = bbr
net.ipv4.tcp_notsent_lowat = 16384
net.ipv4.tcp_slow_start_after_idle = 0
net.ipv4.tcp_no_metrics_save = 1
net.ipv4.tcp_keepalive_time = 30
net.ipv4.tcp_keepalive_intvl = 5
net.ipv4.tcp_keepalive_probes = 3
net.ipv4.tcp_fastopen = 3
net.core.rmem_max = 8388608
net.core.wmem_max = 8388608
net.ipv4.tcp_rmem = 4096 87380 8388608
net.ipv4.tcp_wmem = 4096 65536 8388608
"#;
            let conf_path = "/etc/sysctl.d/99-minecraft-latency.conf";
            match fs::write(conf_path, conf) {
                Ok(_) => {
                    info!("Wrote low-latency network sysctl to {}", conf_path);
                    let _ = std::process::Command::new("sysctl").args(["--system"]).status();
                    info!("Applied kernel network parameters.");
                }
                Err(e) => {
                    tracing::warn!(
                        "Could not write {}: {}. Run with sudo to apply kernel network tuning.",
                        conf_path, e
                    );
                }
            }
        }
        #[cfg(not(target_os = "linux"))]
        {
            info!("Network sysctl tuning is only applicable to Linux kernels.");
        }
        Ok(())
    }

    /// Inspects the node's Minecraft server process and memory telemetry
    pub fn status(server_dir: Option<&str>) {
        let mut sys = System::new_all();
        sys.refresh_all();

        println!("\x1b[1;36m============================================================\x1b[0m");
        println!("\x1b[1;37m   NEXUS MINECRAFT WARFARE - NODE STATUS & TELEMETRY        \x1b[0m");
        println!("\x1b[1;36m============================================================\x1b[0m");

        let total_ram_mb = sys.total_memory() / (1024 * 1024);
        let used_ram_mb = sys.used_memory() / (1024 * 1024);
        let pct = (used_ram_mb as f64 / total_ram_mb as f64) * 100.0;

        println!("• System Memory:  \x1b[1;33m{used_ram_mb} MB / {total_ram_mb} MB ({pct:.1}%)\x1b[0m");

        // Scan for running java process with minecraft / forge
        let mut mc_found = false;
        for (pid, process) in sys.processes() {
            let name = process.name().to_string_lossy();
            let cmd = process
                .cmd()
                .iter()
                .map(|s| s.to_string_lossy())
                .collect::<Vec<_>>()
                .join(" ");
            if name.contains("java") || cmd.contains("forge") || cmd.contains("minecraft") {
                let rss_mb = process.memory() / (1024 * 1024);
                let cpu = process.cpu_usage();
                println!("• Active Engine:  \x1b[1;32mONLINE\x1b[0m (PID: {pid})");
                println!("• Engine RSS:     \x1b[1;36m{rss_mb} MB\x1b[0m");
                println!("• Engine CPU:     \x1b[1;35m{cpu:.1}%\x1b[0m");
                println!("• Command:        {}", cmd.chars().take(80).collect::<String>());
                mc_found = true;
                break;
            }
        }

        if !mc_found {
            println!("• Active Engine:  \x1b[1;31mSTOPPED / NOT RUNNING\x1b[0m");
        }

        if let Some(dir) = server_dir {
            let p = Path::new(dir);
            println!("• Directory:      {}", p.display());
            println!("• JVM Args:       {}", if p.join("user_jvm_args.txt").exists() { "Present" } else { "Missing" });
            println!("• Properties:     {}", if p.join("server.properties").exists() { "Present" } else { "Missing" });
        }
        println!("\x1b[1;36m============================================================\x1b[0m");
    }
}
