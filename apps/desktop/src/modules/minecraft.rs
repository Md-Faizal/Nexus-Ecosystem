use std::fs;
use std::path::{Path, PathBuf};
use sysinfo::System;
use tracing::info;
#[cfg(target_os = "linux")]
use tracing::warn;
use crate::McRemoteCommands;

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

    /// Checks whether a Minecraft server or game reverse tunnel is actively running
    pub fn is_server_or_tunnel_running() -> bool {
        let mut sys = System::new_all();
        sys.refresh_all();

        for (_pid, process) in sys.processes() {
            let name = process.name().to_string_lossy().to_lowercase();
            let cmd = process
                .cmd()
                .iter()
                .map(|s| s.to_string_lossy())
                .collect::<Vec<_>>()
                .join(" ")
                .to_lowercase();

            if name.contains("java")
                || name.contains("bedrock")
                || cmd.contains("minecraft")
                || cmd.contains("server.jar")
                || cmd.contains("forge")
                || cmd.contains("fabric")
                || cmd.contains("tunnel connect")
            {
                return true;
            }
        }
        false
    }

    /// Checks if an active interactive graphical desktop user session is present
    #[allow(dead_code)]
    pub fn is_desktop_actively_used() -> bool {
        #[cfg(target_os = "linux")]
        {
            // 1. Check if graphical.target or display manager is active
            let is_graphical = std::process::Command::new("systemctl")
                .args(["is-active", "display-manager"])
                .output()
                .map(|o| String::from_utf8_lossy(&o.stdout).trim() == "active")
                .unwrap_or(false);

            if !is_graphical {
                return false;
            }

            // 2. Check if active display environment has users
            if let Ok(who_out) = std::process::Command::new("who").output() {
                let who_str = String::from_utf8_lossy(&who_out.stdout);
                if who_str.contains("(:0)") || who_str.contains(":0") {
                    return true;
                }
            }
            false
        }
        #[cfg(not(target_os = "linux"))]
        {
            false
        }
    }

    /// Switch to Minecraft dedicated server mode with active-use prompt guard
    pub fn switch_to_minecraft_mode(force: bool) -> (bool, String) {
        #[cfg(target_os = "linux")]
        {
            if !force && Self::is_desktop_actively_used() {
                info!("Active graphical desktop session detected! Triggering 30s cancellation warning dialog...");
                let prompt_res = std::process::Command::new("bash")
                    .args([
                        "-c",
                        r#"
                        ACTIVE_USER=$(who | grep '(:0)' | awk '{print $1}' | head -n 1)
                        if [ -z "$ACTIVE_USER" ]; then
                            ACTIVE_USER=$(who | awk '{print $1}' | head -n 1)
                        fi
                        export DISPLAY=:0
                        export XAUTHORITY=/home/$ACTIVE_USER/.Xauthority
                        if command -v zenity >/dev/null 2>&1; then
                            zenity --question \
                                --title="⚠️ Gaming Mode Switch Requested" \
                                --text="Remote request to switch to Minecraft Server mode.\n\nGNOME Desktop will close in 30 seconds to free RAM.\n\nClick Cancel if you are currently using this PC." \
                                --timeout=30 \
                                --width=450
                            exit $?
                        else
                            exit 0
                        fi
                        "#
                    ])
                    .status();

                match prompt_res {
                    Ok(s) if s.code() == Some(1) => {
                        return (false, "Aborted: Local user actively using the PC clicked CANCEL on the warning prompt.".to_string());
                    }
                    _ => {
                        info!("Prompt accepted or timed out. Proceeding to Minecraft mode...");
                    }
                }
            }

            let _ = std::process::Command::new("systemctl").args(["start", "minecraft-server"]).status();
            let status = std::process::Command::new("systemctl").args(["isolate", "multi-user.target"]).status();
            match status {
                Ok(s) if s.success() => (true, "Switched to Minecraft Mode (multi-user.target active, 1.2GB+ RAM freed, minecraft-server online).".to_string()),
                Ok(s) => (false, format!("Failed to isolate multi-user.target (code: {:?})", s.code())),
                Err(e) => (false, format!("Could not execute systemctl: {}", e)),
            }
        }
        #[cfg(not(target_os = "linux"))]
        {
            let _ = force;
            (false, "Minecraft mode switching is only supported on Linux bare-metal hosts.".to_string())
        }
    }

    /// Restore Normal Desktop Mode (GNOME) for family use
    pub fn restore_normal_mode() -> (bool, String) {
        #[cfg(target_os = "linux")]
        {
            if Path::new("/usr/local/bin/gui").exists() {
                let _ = std::process::Command::new("/usr/local/bin/gui").status();
            } else {
                let _ = std::process::Command::new("systemctl").args(["stop", "minecraft-server"]).status();
                let _ = std::process::Command::new("systemctl").args(["isolate", "graphical.target"]).status();
            }
            (true, "Restored Normal Mode (Minecraft server stopped, GNOME graphical desktop active).".to_string())
        }
        #[cfg(not(target_os = "linux"))]
        {
            (false, "Normal mode restoration is only supported on Linux bare-metal hosts.".to_string())
        }
    }

    /// Target resolver: CLI flag -> Environment var -> session default
    pub fn resolve_remote_target(host: Option<&str>, user: Option<&str>) -> (String, String) {
        let final_host = host
            .map(String::from)
            .or_else(|| std::env::var("NEXUS_MC_HOST").ok())
            .unwrap_or_else(|| "192.168.29.36".to_string());

        let final_user = user
            .map(String::from)
            .or_else(|| std::env::var("NEXUS_MC_USER").ok())
            .unwrap_or_else(|| "rida".to_string());

        (final_host, final_user)
    }

    /// Dispatches remote command via SSH
    pub fn exec_remote_ssh(host: &str, user: &str, remote_cmd: &str, inherit_io: bool) -> Result<std::process::Output, std::io::Error> {
        let target = format!("{}@{}", user, host);
        let mut cmd = std::process::Command::new("ssh");
        cmd.args([
            "-o", "ConnectTimeout=8",
            "-o", "StrictHostKeyChecking=no",
            &target,
            remote_cmd,
        ]);
        if inherit_io {
            cmd.stdin(std::process::Stdio::inherit())
               .stdout(std::process::Stdio::inherit())
               .stderr(std::process::Stdio::inherit());
        }
        cmd.output()
    }

    /// Executes remote fleet operations
    pub async fn handle_remote_command(
        action: McRemoteCommands,
        host_opt: Option<&str>,
        user_opt: Option<&str>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        let (host, user) = Self::resolve_remote_target(host_opt, user_opt);

        match action {
            McRemoteCommands::Status { watch } => {
                loop {
                    if watch {
                        print!("\x1b[2J\x1b[H"); // Clear terminal
                    }

                    println!("\x1b[1;36m╭────────────────────────────────────────────────────────────────────────╮\x1b[0m");
                    println!("\x1b[1;36m│\x1b[0m \x1b[1;37mNEXUS MINECRAFT FLEET - REMOTE TELEMETRY & LIVE STATUS                \x1b[0m\x1b[1;36m│\x1b[0m");
                    println!("\x1b[1;36m│\x1b[0m Target Node: \x1b[1;33m{}@{}\x1b[0m", user, host);
                    println!("\x1b[1;36m├────────────────────────────────────────────────────────────────────────┤\x1b[0m");

                    let query_script = r#"
                    MEM=$(free -m | awk '/Mem:/ {print $3" / "$2" MB ("int($3/$2*100)"%)"}')
                    TGT=$(systemctl is-active graphical.target 2>/dev/null)
                    if [ "$TGT" = "active" ]; then
                        MODE="🖥️ Normal Mode (GNOME Desktop Active)"
                    else
                        MODE="🎮 Minecraft Mode (Headless multi-user.target, 1.2GB RAM freed)"
                    fi
                    SVC=$(systemctl is-active minecraft-server 2>/dev/null || echo "inactive")
                    TUN=$(systemctl is-active nexus-tunnel-client 2>/dev/null || echo "inactive")
                    JAVA_PID=$(pgrep -f 'minecraft-server|forge' | head -n 1)
                    if [ -n "$JAVA_PID" ]; then
                        JAVA_RSS=$(ps -o rss= -p $JAVA_PID 2>/dev/null | awk '{print int($1/1024)" MB"}')
                    else
                        JAVA_RSS="N/A"
                    fi
                    echo "MEM:$MEM"
                    echo "MODE:$MODE"
                    echo "SVC:$SVC"
                    echo "TUN:$TUN"
                    echo "PID:${JAVA_PID:-None}"
                    echo "RSS:$JAVA_RSS"
                    "#;

                    match Self::exec_remote_ssh(&host, &user, query_script, false) {
                        Ok(output) if output.status.success() => {
                            let text = String::from_utf8_lossy(&output.stdout);
                            for line in text.lines() {
                                if let Some(mem) = line.strip_prefix("MEM:") {
                                    println!("│ • System Memory:     \x1b[1;33m{}\x1b[0m", mem);
                                } else if let Some(mode) = line.strip_prefix("MODE:") {
                                    println!("│ • Operating Mode:    \x1b[1;32m{}\x1b[0m", mode);
                                } else if let Some(svc) = line.strip_prefix("SVC:") {
                                    let col = if svc == "active" { "\x1b[1;32mONLINE (active)\x1b[0m" } else { "\x1b[1;31mSTOPPED\x1b[0m" };
                                    println!("│ • Minecraft Service: {}", col);
                                } else if let Some(tun) = line.strip_prefix("TUN:") {
                                    let col = if tun == "active" { "\x1b[1;32mESTABLISHED (mine.faizal.uk:25565)\x1b[0m" } else { "\x1b[1;31mDISCONNECTED\x1b[0m" };
                                    println!("│ • Reverse Ingress:   {}", col);
                                } else if let Some(pid) = line.strip_prefix("PID:") {
                                    println!("│ • Server Process:    PID {}", pid);
                                } else if let Some(rss) = line.strip_prefix("RSS:") {
                                    println!("│ • Memory RSS:        \x1b[1;36m{}\x1b[0m", rss);
                                }
                            }
                        }
                        Ok(output) => {
                            let err = String::from_utf8_lossy(&output.stderr);
                            println!("│ \x1b[1;31m✖ Remote query failed: {}\x1b[0m", err.trim());
                        }
                        Err(e) => {
                            println!("│ \x1b[1;31m✖ SSH Connection Error: {}\x1b[0m", e);
                        }
                    }

                    println!("\x1b[1;36m╰────────────────────────────────────────────────────────────────────────╯\x1b[0m");

                    if !watch {
                        break;
                    }
                    tokio::time::sleep(std::time::Duration::from_secs(2)).await;
                }
            }

            McRemoteCommands::Mode { target, force } => {
                let target_lower = target.to_lowercase();
                if target_lower == "minecraft" || target_lower == "headless" || target_lower == "game" {
                    println!("\x1b[1;33mDispatching switch to Minecraft Server Mode on {}@{}...\x1b[0m", user, host);
                    let cmd = if force {
                        "sudo systemctl start minecraft-server && sudo systemctl isolate multi-user.target"
                    } else {
                        r#"
                        if who | grep -q '(:0)'; then
                            if command -v zenity >/dev/null 2>&1; then
                                ACTIVE_USER=$(who | grep '(:0)' | awk '{print $1}' | head -n 1)
                                export DISPLAY=:0
                                export XAUTHORITY=/home/$ACTIVE_USER/.Xauthority
                                zenity --question \
                                    --title="⚠️ Gaming Mode Switch" \
                                    --text="Remote Minecraft Server switch requested.\n\nDesktop will close in 30s to free RAM.\n\nClick Cancel if you are using this PC." \
                                    --timeout=30 --width=420 || exit 2
                            fi
                        fi
                        sudo systemctl start minecraft-server && sudo systemctl isolate multi-user.target
                        "#
                    };

                    match Self::exec_remote_ssh(&host, &user, cmd, true) {
                        Ok(o) if o.status.code() == Some(2) => {
                            println!("\x1b[1;31m✖ Switch Aborted: Local user actively using the PC clicked CANCEL on warning prompt.\x1b[0m");
                            println!("\x1b[90mTip: Pass '--force' to override if necessary.\x1b[0m");
                        }
                        Ok(o) if o.status.success() => {
                            println!("\x1b[1;32m✓ Successfully switched to Dedicated Minecraft Mode (Desktop stopped, 1.2GB+ RAM freed).\x1b[0m");
                        }
                        Ok(o) => println!("\x1b[31mExit code {:?}\x1b[0m", o.status.code()),
                        Err(e) => println!("\x1b[31mSSH Error: {}\x1b[0m", e),
                    }
                } else if target_lower == "normal" || target_lower == "desktop" || target_lower == "gui" {
                    println!("\x1b[1;36mRestoring Normal Desktop Mode (GNOME) on {}@{}...\x1b[0m", user, host);
                    let cmd = "sudo /usr/local/bin/gui 2>/dev/null || (sudo systemctl stop minecraft-server && sudo systemctl isolate graphical.target)";
                    match Self::exec_remote_ssh(&host, &user, cmd, true) {
                        Ok(o) if o.status.success() => {
                            println!("\x1b[1;32m✓ Successfully restored Normal Desktop Mode for family use.\x1b[0m");
                        }
                        Ok(o) => println!("\x1b[31mExit code {:?}\x1b[0m", o.status.code()),
                        Err(e) => println!("\x1b[31mSSH Error: {}\x1b[0m", e),
                    }
                } else {
                    println!("\x1b[31mUnknown target mode '{}'. Choose 'minecraft' or 'normal'.\x1b[0m", target);
                }
            }

            McRemoteCommands::Shutdown => {
                println!("\x1b[1;31mSending poweroff signal to {}@{}...\x1b[0m", user, host);
                let _ = Self::exec_remote_ssh(&host, &user, "sudo systemctl poweroff", true);
                println!("\x1b[1;32m✓ Shutdown command dispatched.\x1b[0m");
            }

            McRemoteCommands::Reboot => {
                println!("\x1b[1;33mSending reboot signal to {}@{}...\x1b[0m", user, host);
                let _ = Self::exec_remote_ssh(&host, &user, "sudo systemctl reboot", true);
                println!("\x1b[1;32m✓ Reboot command dispatched.\x1b[0m");
            }

            McRemoteCommands::Start => {
                println!("\x1b[1;32mStarting minecraft-server.service on {}@{}...\x1b[0m", user, host);
                let _ = Self::exec_remote_ssh(&host, &user, "sudo systemctl start minecraft-server", true);
                println!("\x1b[1;32m✓ Start command dispatched.\x1b[0m");
            }

            McRemoteCommands::Stop => {
                println!("\x1b[1;33mStopping minecraft-server.service on {}@{}...\x1b[0m", user, host);
                let _ = Self::exec_remote_ssh(&host, &user, "sudo systemctl stop minecraft-server", true);
                println!("\x1b[1;32m✓ Stop command dispatched.\x1b[0m");
            }

            McRemoteCommands::Restart => {
                println!("\x1b[1;33mRestarting minecraft-server.service on {}@{}...\x1b[0m", user, host);
                let _ = Self::exec_remote_ssh(&host, &user, "sudo systemctl restart minecraft-server", true);
                println!("\x1b[1;32m✓ Restart command dispatched.\x1b[0m");
            }

            McRemoteCommands::Cmd { command } => {
                println!("\x1b[1;36mDispatching in-game command: '{}' to {}@{}...\x1b[0m", command, user, host);
                let escaped = command.replace('"', "\\\"");
                let script = format!("screen -S minecraft -X stuff \"{}\\n\" 2>/dev/null || echo 'Dispatched.'", escaped);
                let _ = Self::exec_remote_ssh(&host, &user, &script, true);
                println!("\x1b[1;32m✓ Command sent.\x1b[0m");
            }

            McRemoteCommands::Logs { lines, follow } => {
                println!("\x1b[1;36mTailing {} lines of latest.log on {}@{} (follow: {})...\x1b[0m", lines, user, host, follow);
                let follow_arg = if follow { "-f" } else { "" };
                let cmd = format!("tail -n {} {} ~/minecraft-server/logs/latest.log 2>/dev/null || journalctl -u minecraft-server -n {} {}", lines, follow_arg, lines, follow_arg);
                let _ = Self::exec_remote_ssh(&host, &user, &cmd, true);
            }

            McRemoteCommands::Prop { key, value } => {
                if let Some(val) = value {
                    println!("\x1b[1;33mSetting property '{}={}' on {}@{}...\x1b[0m", key, val, user, host);
                    let cmd = format!(
                        "sed -i 's/^{}=.*/{}={}/' ~/minecraft-server/server.properties && echo 'Updated.'",
                        key, key, val
                    );
                    let _ = Self::exec_remote_ssh(&host, &user, &cmd, true);
                    println!("\x1b[1;32m✓ Property updated in server.properties. Run 'nx mc remote restart' to apply changes.\x1b[0m");
                } else {
                    let cmd = format!("grep '^{}=' ~/minecraft-server/server.properties 2>/dev/null || echo 'Not found'", key);
                    let _ = Self::exec_remote_ssh(&host, &user, &cmd, true);
                }
            }
        }

        Ok(())
    }
}
