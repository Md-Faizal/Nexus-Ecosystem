use std::fs;
use std::path::{Path, PathBuf};
use sysinfo::System;
use tracing::info;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ServerType {
    Forge,
    Fabric,
    Paper,
    Vanilla,
    Custom(String),
}

pub struct MinecraftManager;

impl MinecraftManager {
    /// Auto-detects the server engine type and startup command within a directory
    pub fn detect_server_launch(dir: &Path) -> (ServerType, String, String) {
        // 1. Check for Forge unix/win args in libraries
        let forge_dir = dir.join("libraries").join("net").join("minecraftforge").join("forge");
        if forge_dir.is_dir() {
            if let Ok(entries) = fs::read_dir(&forge_dir) {
                for entry in entries.flatten() {
                    let version_path = entry.path();
                    let unix_args = version_path.join("unix_args.txt");
                    let win_args = version_path.join("win_args.txt");
                    if unix_args.exists() || win_args.exists() {
                        let rel_unix = unix_args.strip_prefix(dir).unwrap_or(&unix_args).to_string_lossy().replace('\\', "/");
                        let rel_win = win_args.strip_prefix(dir).unwrap_or(&win_args).to_string_lossy().replace('\\', "/");
                        return (
                            ServerType::Forge,
                            format!("java @user_jvm_args.txt @{} nogui", rel_unix),
                            format!("java @user_jvm_args.txt @{} nogui", rel_win),
                        );
                    }
                }
            }
        }

        // 2. Check for Fabric
        if dir.join("fabric-server-launch.jar").exists() {
            return (
                ServerType::Fabric,
                "java @user_jvm_args.txt -jar fabric-server-launch.jar nogui".to_string(),
                "java @user_jvm_args.txt -jar fabric-server-launch.jar nogui".to_string(),
            );
        }

        // 3. Check for Paper / Purpur / Velocity
        if let Ok(entries) = fs::read_dir(dir) {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_lowercase();
                if name.starts_with("paper") && name.ends_with(".jar") {
                    let fname = entry.file_name().to_string_lossy().to_string();
                    return (
                        ServerType::Paper,
                        format!("java @user_jvm_args.txt -jar {} nogui", fname),
                        format!("java @user_jvm_args.txt -jar {} nogui", fname),
                    );
                }
            }
        }

        // 4. Check for standard server.jar
        if dir.join("server.jar").exists() {
            return (
                ServerType::Vanilla,
                "java @user_jvm_args.txt -jar server.jar nogui".to_string(),
                "java @user_jvm_args.txt -jar server.jar nogui".to_string(),
            );
        }

        // 5. Check for startup scripts (run.sh / run.bat)
        if dir.join("run.sh").exists() {
            return (
                ServerType::Custom("run.sh".to_string()),
                "./run.sh".to_string(),
                "run.bat".to_string(),
            );
        }

        // Default generic launcher fallback
        (
            ServerType::Vanilla,
            "java @user_jvm_args.txt -jar server.jar nogui".to_string(),
            "java @user_jvm_args.txt -jar server.jar nogui".to_string(),
        )
    }

    /// Auto-provisions JVM flags, systemd unit, and kernel options dynamically for any PC and engine
    #[allow(unused_variables)]
    pub fn setup(
        install_dir: &str,
        heap_mb: Option<u64>,
        exec_cmd: Option<&str>,
        headless: bool,
        apply_sysctl: bool,
    ) -> Result<(), Box<dyn std::error::Error>> {
        let mut sys = System::new_all();
        sys.refresh_all();

        let total_ram_mb = sys.total_memory() / (1024 * 1024);
        let cpu_cores = sys.cpus().len();
        let target_dir = PathBuf::from(install_dir);
        fs::create_dir_all(&target_dir)?;

        info!("============================================================");
        info!("   NEXUS MINECRAFT SERVER - DYNAMIC NODE PROVISIONER");
        info!("   Detected Hardware:  {} cores | {} MB System RAM", cpu_cores, total_ram_mb);
        info!("   Target Directory:   {}", target_dir.display());
        info!("============================================================");

        // Calculate optimal JVM heap dynamically based on total host RAM
        let (xms, xmx) = if let Some(custom) = heap_mb {
            let initial = (custom * 3 / 4).max(512);
            (initial, custom)
        } else if total_ram_mb <= 2048 {
            // Very small VMs (<= 2GB)
            (768, 1280)
        } else if total_ram_mb <= 4500 {
            // Budget nodes (3GB - 4GB) - leave ~1.2 GB for OS & off-heap
            let calculated = total_ram_mb.saturating_sub(1200).max(1536);
            ((calculated * 3 / 4), calculated)
        } else if total_ram_mb <= 9000 {
            // Standard workstations (8GB) - leave ~2.5 GB for OS & apps
            let calculated = total_ram_mb.saturating_sub(2500);
            ((calculated * 3 / 4), calculated)
        } else {
            // Large hosts (> 8GB) - dedicate up to 70% of RAM
            let calculated = (total_ram_mb * 70) / 100;
            (calculated / 2, calculated)
        };

        info!("Configured JVM Heap Boundaries: -Xms{}M -Xmx{}M", xms, xmx);

        // 1. Write user_jvm_args.txt
        let jvm_args_content = format!(
            r#"# ==============================================================================
# NEXUS DYNAMIC MINECRAFT ENGINE CONFIGURATION
# Generated dynamically for: {cpu_cores} cores, {total_ram_mb} MB system RAM
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

# Native Memory Guards
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
        info!("Wrote JVM arguments: {}", jvm_args_path.display());

        // 2. Write server.properties if not already present
        let props_path = target_dir.join("server.properties");
        if !props_path.exists() {
            let default_props = r#"# Minecraft server properties - Nexus Dynamic Profile
server-port=25565
enable-rcon=false
online-mode=false
allow-flight=true
view-distance=8
simulation-distance=6
network-compression-threshold=256
max-tick-time=180000
difficulty=hard
gamemode=survival
spawn-protection=0
sync-chunk-writes=true
entity-broadcast-range-percentage=100
rate-limit=0
motd=A Nexus Managed Minecraft Server
"#;
            fs::write(&props_path, default_props)?;
            info!("Generated default server.properties: {}", props_path.display());
        }

        // 3. Detect launch command
        let (server_type, unix_launch, win_launch) = Self::detect_server_launch(&target_dir);
        let final_unix_launch = exec_cmd.map(String::from).unwrap_or(unix_launch);
        let final_win_launch = exec_cmd.map(String::from).unwrap_or(win_launch);

        info!("Detected Engine Architecture: {:?}", server_type);
        info!("Selected Unix Launch Command:  {}", final_unix_launch);

        // 4. Linux specific configuration (Headless target, Sysctl, Systemd service)
        #[cfg(target_os = "linux")]
        {
            if headless {
                info!("Setting systemd boot target to multi-user.target (headless mode)...");
                let status = std::process::Command::new("systemctl")
                    .args(["set-default", "multi-user.target"])
                    .status();
                match status {
                    Ok(s) if s.success() => info!("✅ Set default target to multi-user.target"),
                    Ok(s) => warn!("Failed to set multi-user.target (exit code: {:?})", s.code()),
                    Err(e) => warn!("Could not invoke systemctl: {}", e),
                }
            }

            if apply_sysctl {
                Self::apply_network_sysctl()?;
            }

            let current_user = std::env::var("USER").unwrap_or_else(|_| "ubuntu".to_string());
            let workdir = target_dir.canonicalize().unwrap_or(target_dir.clone());
            let unit_content = format!(
                r#"[Unit]
Description=Nexus Dedicated Minecraft Server
After=network-online.target local-fs.target
Wants=network-online.target

[Service]
Type=simple
User={user}
WorkingDirectory={workdir}
ProtectSystem=full
ProtectHome=false
PrivateTmp=true

# High priority scheduling
Nice=-10
CPUSchedulingPolicy=other

# Memory bounds via cgroups v2
MemoryAccounting=true
MemoryHigh={mem_high}M
MemoryMax={mem_max}M
LimitNOFILE=65536

# Execution Command
ExecStart={exec_cmd}

Restart=on-failure
RestartSec=15s
KillSignal=SIGTERM
TimeoutStopSec=60

[Install]
WantedBy=multi-user.target
"#,
                user = current_user,
                workdir = workdir.display(),
                mem_high = xmx + 800,
                mem_max = xmx + 1100,
                exec_cmd = final_unix_launch,
            );

            let service_file = target_dir.join("minecraft-server.service");
            fs::write(&service_file, &unit_content)?;
            info!("Generated systemd service: {}", service_file.display());

            let sys_unit_path = Path::new("/etc/systemd/system/minecraft-server.service");
            if let Ok(_) = fs::write(sys_unit_path, &unit_content) {
                info!("Installed systemd unit to /etc/systemd/system/minecraft-server.service");
                let _ = std::process::Command::new("systemctl").arg("daemon-reload").status();
            }
        }

        // 5. Windows Launch Script
        #[cfg(target_os = "windows")]
        {
            let run_bat = target_dir.join("run.bat");
            let bat_content = format!(
                "@echo off\r\ntitle Nexus Minecraft Server\r\n{}\r\npause\r\n",
                final_win_launch
            );
            fs::write(&run_bat, bat_content)?;
            info!("Generated Windows launch script: {}", run_bat.display());
        }

        info!("Provisioning complete! Ready for operation.");
        Ok(())
    }

    /// Applies kernel TCP latency optimizations (BBR, FQ, bufferbloat mitigation)
    pub fn apply_network_sysctl() -> Result<(), Box<dyn std::error::Error>> {
        #[cfg(target_os = "linux")]
        {
            let conf = r#"# Nexus - Low-Latency Network Configuration
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

    /// Inspects any running Minecraft or Java server process
    pub fn status(server_dir: Option<&str>) {
        let mut sys = System::new_all();
        sys.refresh_all();

        println!("\x1b[1;36m============================================================\x1b[0m");
        println!("\x1b[1;37m   NEXUS MINECRAFT FLEET - NODE STATUS & TELEMETRY          \x1b[0m");
        println!("\x1b[1;36m============================================================\x1b[0m");

        let total_ram_mb = sys.total_memory() / (1024 * 1024);
        let used_ram_mb = sys.used_memory() / (1024 * 1024);
        let pct = if total_ram_mb > 0 {
            (used_ram_mb as f64 / total_ram_mb as f64) * 100.0
        } else {
            0.0
        };

        println!("• System Memory:  \x1b[1;33m{used_ram_mb} MB / {total_ram_mb} MB ({pct:.1}%)\x1b[0m");

        // Scan for running game/java servers
        let mut found = false;
        for (pid, process) in sys.processes() {
            let name = process.name().to_string_lossy().to_lowercase();
            let cmd = process
                .cmd()
                .iter()
                .map(|s| s.to_string_lossy())
                .collect::<Vec<_>>()
                .join(" ");
            let cmd_lower = cmd.to_lowercase();

            if name.contains("java")
                || name.contains("bedrock")
                || cmd_lower.contains("minecraft")
                || cmd_lower.contains("server.jar")
                || cmd_lower.contains("paper")
                || cmd_lower.contains("forge")
                || cmd_lower.contains("fabric")
            {
                let rss_mb = process.memory() / (1024 * 1024);
                let cpu = process.cpu_usage();
                println!("• Active Engine:  \x1b[1;32mONLINE\x1b[0m (PID: {pid})");
                println!("• Memory RSS:     \x1b[1;36m{rss_mb} MB\x1b[0m");
                println!("• CPU Usage:      \x1b[1;35m{cpu:.1}%\x1b[0m");
                println!("• Command:        {}", cmd.chars().take(80).collect::<String>());
                found = true;
                break;
            }
        }

        if !found {
            println!("• Active Engine:  \x1b[1;31mSTOPPED / NOT RUNNING\x1b[0m");
        }

        if let Some(dir) = server_dir {
            let p = Path::new(dir);
            println!("• Directory:      {}", p.display());
            println!("• JVM Args:       {}", if p.join("user_jvm_args.txt").exists() { "Present" } else { "Missing" });
            println!("• Properties:     {}", if p.join("server.properties").exists() { "Present" } else { "Missing" });
            let (detected_type, unix_cmd, _) = Self::detect_server_launch(p);
            println!("• Detected Engine:{:?}", detected_type);
            println!("• Default Launch: {}", unix_cmd);
        }
        println!("\x1b[1;36m============================================================\x1b[0m");
    }
}
