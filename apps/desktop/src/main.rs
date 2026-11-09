mod config;
mod mesh;
mod modules;
mod service;
mod updater;

use clap::{Parser, Subcommand};
use config::AgentConfig;
use mesh::client::MeshClient;
use service::ServiceManager;
use tracing::info;

#[derive(Parser)]
#[command(name = "nexus")]
#[command(bin_name = "nexus")]
#[command(author = "Faizal")]
#[command(version = env!("CARGO_PKG_VERSION"))]
#[command(about = "Unified distributed fleet telemetry & remote cluster operations console", long_about = None)]
struct Cli {
    #[command(subcommand)]
    command: Option<Commands>,

    /// Override the primary mesh hub URL (e.g. wss://faizal.uk/api/mesh/ws)
    #[arg(short, long)]
    hub: Option<String>,

    /// Override the local Ollama AI endpoint (default: http://127.0.0.1:11434)
    #[arg(long)]
    ai_endpoint: Option<String>,

    /// Optional Admin PIN for cluster commands (or set NEXUS_ADMIN_PIN / FAIZAL_ADMIN_PIN env var)
    #[arg(long)]
    pin: Option<String>,

    /// Run in foreground directly (default when no command provided)
    #[arg(long)]
    run: bool,
}

#[derive(Subcommand)]
enum Commands {
    /// [CRITICAL] Start background telemetry agent, WebSocket mesh tunnel & taskbar tray
    Run {
        /// Run directly in foreground without detaching into a background worker
        #[arg(short, long)]
        foreground: bool,
    },

    /// Show local node identity, live hardware telemetry, and daemon status
    Status,

    /// Upgrade to Administrator / Root background service (triggers UAC / sudo)
    Elevate,

    /// Inspect 3-VM cluster (my-mac, cos, pro) and edge workstations
    Cluster,

    /// Inspect cluster services, docker containers, and game servers
    Services,

    /// 1-Click restart a service across the cluster (e.g. 'nexus restart personal-site-rust')
    Restart {
        /// Name or ID of service to restart
        service: String,
    },

    /// Start a service across the cluster
    Start {
        /// Name or ID of service to start
        service: String,
    },

    /// Stop a service across the cluster
    Stop {
        /// Name or ID of service to stop
        service: String,
    },

    /// View 24h cluster error logs and system health digest
    Health,

    /// Manage native OS background service (systemd, Windows Startup, launchd)
    Service {
        #[command(subcommand)]
        action: ServiceCommands,
    },

    /// Chat interactively in terminal (Fleet Chat & Distributed AI Studio)
    Chat {
        /// Optional console mode: 'chat' (Fleet Chat) or 'ai' (Distributed AI Studio) or 'local'
        #[arg(short = 'c', long)]
        mode: Option<String>,

        /// Target a specific compute PC (e.g. potato-pro, rida-260-p020il, local)
        #[arg(short = 'n', long)]
        node: Option<String>,

        /// Optional model to use (e.g. qwen2.5-coder:7b)
        #[arg(short = 'm', long)]
        model: Option<String>,

        /// Force offline standalone local mode (direct to 127.0.0.1:11434)
        #[arg(short = 'l', long)]
        local: bool,

        /// Instantly resume a saved conversation session by ID or index
        #[arg(short = 'r', long)]
        resume: Option<String>,
    },

    /// List models currently active in VRAM / RAM
    Ps,

    /// Pre-load model weights into VRAM
    Load {
        /// Model name to load
        model: String,
        /// Keep-alive duration (default: 15m)
        #[arg(short, long, default_value = "15m")]
        time: String,
    },

    /// Evict model weights from VRAM immediately
    Unload {
        /// Model name to unload
        model: String,
    },

    /// Run single prompt through local AI model and stream output to terminal
    Ai {
        /// Prompt to execute
        #[arg(required = true)]
        prompt: Vec<String>,

        /// Optional model override
        #[arg(short, long)]
        model: Option<String>,
    },

    /// List all local AI models available on Ollama
    Models,

    /// Display comprehensive manual & operator guide
    Manual,

    /// Check for updates and automatically upgrade the agent binary
    Update {
        /// Only check for updates without installing
        #[arg(long)]
        check: bool,

        /// Force re-download and re-install even if version matches
        #[arg(long)]
        force: bool,

        /// Custom update manifest URL
        #[arg(long, default_value = "https://faizal.uk/releases/latest.json")]
        manifest_url: String,
    },

    /// Low-latency reverse TCP tunneling for game servers & distributed edge ingress
    Tunnel {
        #[command(subcommand)]
        action: TunnelCommands,
    },

    /// Dedicated Minecraft server node orchestration & auto-tuning (Forge, Fabric, Paper, Vanilla)
    Mc {
        #[command(subcommand)]
        action: McCommands,
    },
}

#[derive(Subcommand)]
enum TunnelCommands {
    /// Run reverse tunnel edge gateway on cloud ingress node
    Listen {
        /// Public address for incoming game clients
        #[arg(short, long, default_value = "0.0.0.0:25565")]
        bind: String,

        /// Internal control address for local server connection
        #[arg(short, long, default_value = "0.0.0.0:25560")]
        control: String,

        /// Authentication token required from reverse client
        #[arg(short, long)]
        token: Option<String>,
    },

    /// Connect reverse tunnel worker from local node to cloud edge gateway
    Connect {
        /// Cloud edge gateway control address (e.g. 35.208.86.56:25560 or pro:25560)
        #[arg(short, long)]
        edge: String,

        /// Local game server address
        #[arg(short, long, default_value = "127.0.0.1:25565")]
        target: String,

        /// Authentication token matching edge gateway
        #[arg(short, long)]
        token: Option<String>,
    },
}

#[derive(Subcommand)]
pub enum McCommands {
    /// Auto-provision dedicated Minecraft server, tuning JVM heap and network kernel
    Setup {
        /// Installation / Server directory
        #[arg(short, long, default_value = ".")]
        dir: String,

        /// Custom JVM max heap in MB (auto-calculated from system RAM if omitted)
        #[arg(long)]
        heap: Option<u64>,

        /// Override server execution command (auto-detected if omitted)
        #[arg(short, long)]
        exec: Option<String>,

        /// Switch Linux target to headless multi-user.target
        #[arg(long)]
        headless: bool,

        /// Apply BBR and low-latency TCP network sysctl parameters
        #[arg(long)]
        sysctl: bool,
    },

    /// Inspect local Minecraft server process, memory RSS, and node telemetry
    Status {
        /// Server directory to inspect configuration presence
        #[arg(short, long)]
        dir: Option<String>,
    },

    /// Apply Linux kernel BBR and FQ network optimizations for latency
    TuneSysctl,

    /// Remote Minecraft Fleet Operations (live status, start/stop/restart, active guard, mode switch)
    Remote {
        #[command(subcommand)]
        action: McRemoteCommands,

        /// Remote target host or IP (default: $NEXUS_MC_HOST or 192.168.29.36)
        #[arg(short = 'H', long)]
        host: Option<String>,

        /// Remote SSH user (default: rida)
        #[arg(short, long)]
        user: Option<String>,
    },
}

#[derive(Subcommand)]
pub enum McRemoteCommands {
    /// Live telemetry: Host CPU, RAM, Minecraft server PID, memory RSS, and reverse tunnel status
    Status {
        /// Watch and continuously refresh every 2 seconds
        #[arg(short, long)]
        watch: bool,
    },

    /// Switch remote host operating target (headless 'minecraft' mode vs 'normal' GNOME desktop)
    Mode {
        /// Target mode: 'minecraft' (frees 1.2GB+ RAM) or 'normal' (restores desktop)
        target: String,

        /// Bypass local user active-use cancellation grace period
        #[arg(short, long)]
        force: bool,
    },

    /// Remotely shut down / power off the target machine
    Shutdown,

    /// Remotely reboot the target machine
    Reboot,

    /// Start the Minecraft server service on the remote node
    Start,

    /// Stop the Minecraft server service on the remote node
    Stop,

    /// Restart the Minecraft server service on the remote node
    Restart,

    /// Send in-game command to Minecraft console (e.g. 'time set day', 'op Fazz', 'difficulty hard')
    Cmd {
        /// Command to execute on Minecraft server
        command: String,
    },

    /// Tail live Minecraft server logs (latest.log)
    Logs {
        /// Initial line count to tail
        #[arg(short = 'n', long, default_value = "40")]
        lines: usize,

        /// Follow stream continuously
        #[arg(short, long)]
        follow: bool,
    },

    /// Read or update server.properties remotely (e.g. 'nx mc remote prop view-distance 6')
    Prop {
        /// Property key name
        key: String,

        /// New value (omitting prints current value)
        value: Option<String>,
    },
}

#[derive(Subcommand)]
enum ServiceCommands {
    /// Install and enable agent to run automatically on system boot
    Install {
        /// Install for current user only without root/admin privileges
        #[arg(long)]
        user: bool,
    },
    /// Stop and remove the background service
    Uninstall {
        #[arg(long)]
        user: bool,
    },
    /// Start the background service
    Start {
        #[arg(long)]
        user: bool,
    },
    /// Stop the background service
    Stop {
        #[arg(long)]
        user: bool,
    },
    /// Restart the background service
    Restart {
        #[arg(long)]
        user: bool,
    },
    /// Check background service status
    Status {
        #[arg(long)]
        user: bool,
    },
}

fn is_daemon_running() -> bool {
    modules::daemon_lock::is_daemon_running()
}

#[cfg(target_os = "windows")]
fn enable_virtual_terminal_processing() {
    use windows_sys::Win32::System::Console::{
        GetConsoleMode, GetStdHandle, SetConsoleMode, ENABLE_VIRTUAL_TERMINAL_PROCESSING,
        STD_ERROR_HANDLE, STD_OUTPUT_HANDLE,
    };
    unsafe {
        let h_out = GetStdHandle(STD_OUTPUT_HANDLE);
        if h_out != windows_sys::Win32::Foundation::INVALID_HANDLE_VALUE {
            let mut mode = 0u32;
            if GetConsoleMode(h_out, &mut mode) != 0 {
                let _ = SetConsoleMode(h_out, mode | ENABLE_VIRTUAL_TERMINAL_PROCESSING);
            }
        }
        let h_err = GetStdHandle(STD_ERROR_HANDLE);
        if h_err != windows_sys::Win32::Foundation::INVALID_HANDLE_VALUE {
            let mut mode = 0u32;
            if GetConsoleMode(h_err, &mut mode) != 0 {
                let _ = SetConsoleMode(h_err, mode | ENABLE_VIRTUAL_TERMINAL_PROCESSING);
            }
        }
    }
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    #[cfg(target_os = "windows")]
    enable_virtual_terminal_processing();

    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "nexus_agent=info".into()),
        )
        .init();

    // Catch panics gracefully
    std::panic::set_hook(Box::new(|panic_info| {
        eprintln!("Nexus Agent encountered panic: {:?}", panic_info);
    }));

    // Ergonomic CLI translation: if user ran "nexus services install" (plural), map to "service"
    let mut raw_args: Vec<String> = std::env::args().collect();
    if raw_args.len() >= 2 && raw_args[1].eq_ignore_ascii_case("services") {
        if raw_args.len() >= 3 {
            let sub = raw_args[2].to_lowercase();
            if matches!(sub.as_str(), "install" | "uninstall" | "start" | "stop" | "restart" | "status") {
                raw_args[1] = "service".to_string();
            }
        }
    }

    let cli = Cli::parse_from(&raw_args);
    let mut config = AgentConfig::load_or_init();

    if let Some(hub) = cli.hub {
        config.hubs.insert(0, hub);
    }
    if let Some(ai) = cli.ai_endpoint {
        config.ai_endpoint = ai;
    }

    if let Some(ref p) = cli.pin {
        config.token = Some(p.clone());
        let _ = config.save();
    }

    let primary_hub = config.hubs.first().cloned().unwrap_or_else(|| "wss://faizal.uk/api/mesh/ws".into());
    let admin_pin = cli.pin.clone()
        .or_else(|| std::env::var("NEXUS_ADMIN_PIN").ok())
        .or_else(|| std::env::var("FAIZAL_ADMIN_PIN").ok())
        .or_else(|| config.token.clone());

    match cli.command {
        Some(Commands::Run { foreground }) => {
            handle_run_command(config, foreground).await?;
        }

        Some(Commands::Elevate) => {
            println!("\x1b[1;36m=== Nexus Elevation: Requesting Administrator / Root Privileges ===\x1b[0m");
            #[cfg(target_os = "windows")]
            {
                println!("Triggering Windows User Account Control (UAC) elevation prompt...");
                let script = "Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile -ExecutionPolicy Bypass -Command \"irm https://faizal.uk/install.ps1 | iex\"'";
                let status = std::process::Command::new("powershell")
                    .args(["-NoProfile", "-Command", script])
                    .status();
                match status {
                    Ok(s) if s.success() => println!("\x1b[32mElevation prompt launched! Complete the prompt to register system service.\x1b[0m"),
                    _ => eprintln!("\x1b[31mFailed to launch UAC elevation prompt.\x1b[0m"),
                }
            }
            #[cfg(target_os = "macos")]
            {
                println!("Requesting sudo elevation for macOS launchd daemon installation...");
                let status = std::process::Command::new("sudo")
                    .args(["sh", "-c", "curl -fsSL https://faizal.uk/install.sh | bash"])
                    .status();
                match status {
                    Ok(s) if s.success() => println!("\x1b[32mSuccessfully elevated and installed root launchd daemon!\x1b[0m"),
                    _ => eprintln!("\x1b[31mElevation failed or sudo cancelled.\x1b[0m"),
                }
            }
            #[cfg(target_os = "linux")]
            {
                println!("Requesting sudo elevation for Linux systemd service installation...");
                let status = std::process::Command::new("sudo")
                    .args(["sh", "-c", "curl -fsSL https://faizal.uk/install.sh | bash"])
                    .status();
                match status {
                    Ok(s) if s.success() => println!("\x1b[32mSuccessfully elevated and installed root systemd service!\x1b[0m"),
                    _ => eprintln!("\x1b[31mElevation failed or sudo cancelled.\x1b[0m"),
                }
            }
            #[cfg(not(any(target_os = "windows", target_os = "linux", target_os = "macos")))]
            {
                eprintln!("\x1b[31mElevate is not supported on this operating system.\x1b[0m");
            }
        }

        Some(Commands::Cluster) => {
            modules::cluster_ops::ClusterOps::print_cluster_nodes(&primary_hub, admin_pin.as_deref()).await;
        }

        Some(Commands::Services) => {
            modules::cluster_ops::ClusterOps::print_services(&primary_hub, admin_pin.as_deref()).await;
        }

        Some(Commands::Restart { service }) => {
            modules::cluster_ops::ClusterOps::control_service(&primary_hub, admin_pin.as_deref(), &service, "restart").await;
        }

        Some(Commands::Start { service }) => {
            modules::cluster_ops::ClusterOps::control_service(&primary_hub, admin_pin.as_deref(), &service, "start").await;
        }

        Some(Commands::Stop { service }) => {
            modules::cluster_ops::ClusterOps::control_service(&primary_hub, admin_pin.as_deref(), &service, "stop").await;
        }

        Some(Commands::Health) => {
            modules::cluster_ops::ClusterOps::print_health_report(&primary_hub, admin_pin.as_deref()).await;
        }

        Some(Commands::Service { action }) => match action {
            ServiceCommands::Install { user } => {
                info!("Installing Nexus Agent as an automatic background service...");
                ServiceManager::install(user)?;
            }
            ServiceCommands::Uninstall { user } => {
                info!("Uninstalling Nexus Agent background service...");
                ServiceManager::uninstall(user)?;
            }
            ServiceCommands::Start { user } => {
                ServiceManager::start(user)?;
            }
            ServiceCommands::Stop { user } => {
                ServiceManager::stop(user)?;
            }
            ServiceCommands::Restart { user } => {
                ServiceManager::restart(user)?;
            }
            ServiceCommands::Status { user } => {
                ServiceManager::status(user)?;
            }
        },

        Some(Commands::Status) => {
            modules::banner::print_banner();

            let mut sys = sysinfo::System::new_all();
            sys.refresh_all();
            let total_ram_gb = sys.total_memory() as f64 / 1_073_741_824.0;
            let used_ram_gb = sys.used_memory() as f64 / 1_073_741_824.0;
            let ram_pct = if total_ram_gb > 0.0 { (used_ram_gb / total_ram_gb) * 100.0 } else { 0.0 };
            let cpu_model = sys.cpus().first().map(|c| c.brand().trim()).unwrap_or("Unknown CPU");
            let cpu_cores = sys.cpus().len();

            let gpus = modules::gpu::GpuCollector::collect();
            let ai_bridge = modules::ai_bridge::AiBridge::new(&config.ai_endpoint);
            let ai_info = ai_bridge.check_models().await;
            let ai_online = ai_info.status.contains("ready");
            let daemon_live = is_daemon_running();

            let (hub_online, hub_sub_status) = probe_hub_node_status(&config).await;
            let hub_host = config.primary_hub_host();

            modules::banner::print_status_box(
                &config.node_id,
                &config.hostname,
                &hub_host,
                hub_online,
                &config.ai_endpoint,
                ai_online,
            );

            println!("\x1b[1;36m╭─ FLEET TELEMETRY & DAEMON STATUS ───────────────────────────────────────────╮\x1b[0m");
            let daemon_state = modules::daemon_lock::read_daemon_state();
            let daemon_tag = if daemon_live {
                if let Some(ds) = daemon_state {
                    format!("\x1b[1;32m● RUNNING\x1b[0m \x1b[90m(PID: {}, v{})\x1b[0m", ds.pid, ds.agent_version)
                } else {
                    "\x1b[1;32m● RUNNING\x1b[0m \x1b[90m(Active)\x1b[0m".to_string()
                }
            } else {
                "\x1b[1;31m○ NOT RUNNING\x1b[0m \x1b[90m(Run '\x1b[1;32mnexus run\x1b[0m\x1b[90m' to start)\x1b[0m".to_string()
            };

            let hub_tag = if hub_online {
                format!("\x1b[1;32m● CONNECTED\x1b[0m \x1b[90m({} - {})\x1b[0m", hub_host, hub_sub_status)
            } else {
                format!("\x1b[1;31m○ DISCONNECTED\x1b[0m \x1b[90m({} - {})\x1b[0m", hub_host, hub_sub_status)
            };

            let uptime_secs = sysinfo::System::uptime();
            let up_days = uptime_secs / 86400;
            let up_hours = (uptime_secs % 86400) / 3600;
            let up_mins = (uptime_secs % 3600) / 60;
            let uptime_formatted = if up_days > 0 {
                format!("{}d {}h {}m", up_days, up_hours, up_mins)
            } else {
                format!("{}h {}m", up_hours, up_mins)
            };

            let components = sysinfo::Components::new_with_refreshed_list();
            let mut cpu_temp_c: Option<f32> = None;
            let thermal_keywords = [
                "cpu", "core", "package", "tctl", "tdie", "k10temp", "temp", "acpitz",
                "composite", "zenpower", "soc", "die", "peci"
            ];
            for comp in &components {
                let label = comp.label().to_lowercase();
                if thermal_keywords.iter().any(|&k| label.contains(k)) {
                    if let Some(t) = comp.temperature() {
                        if t > 15.0 && t < 125.0 {
                            cpu_temp_c = Some(t);
                            break;
                        }
                    }
                }
            }
            if cpu_temp_c.is_none() {
                for comp in &components {
                    if let Some(t) = comp.temperature() {
                        if t > 18.0 && t < 115.0 {
                            cpu_temp_c = Some(t);
                            break;
                        }
                    }
                }
            }

            println!("│  \x1b[1mDaemon State:\x1b[0m {}", daemon_tag);
            println!("│  \x1b[1mHub Mesh:\x1b[0m     {}", hub_tag);
            println!("│  \x1b[1mSystem Uptime:\x1b[0m{}", uptime_formatted);
            println!("│  \x1b[1mCPU:\x1b[0m          {} ({} cores)", cpu_model, cpu_cores);
            if let Some(temp) = cpu_temp_c {
                println!("│  \x1b[1mThermals:\x1b[0m     {:.1}°C (CPU Package)", temp);
            } else {
                println!("│  \x1b[1mThermals:\x1b[0m     N/A");
            }
            println!("│  \x1b[1mRAM:\x1b[0m          {:.2} GB / {:.2} GB ({:.1}%)", used_ram_gb, total_ram_gb, ram_pct);
            if let Some(g) = gpus {
                println!("│  \x1b[1mGPU:\x1b[0m          {} ({})", g.name, g.vendor);
                println!("│  \x1b[1mVRAM:\x1b[0m         {:.1} GB / {:.1} GB ({:.0}%) | Temp: {:.0}°C", 
                    g.vram_used_mb as f64 / 1024.0, 
                    g.vram_total_mb as f64 / 1024.0,
                    g.utilization_pct,
                    g.temperature_c.unwrap_or(0.0));
            }
            println!("\x1b[1;36m╰─────────────────────────────────────────────────────────────────────────────╯\x1b[0m");
            println!();

            println!("\x1b[1;35m╭─ LOCAL AI ENGINE (OLLAMA) ──────────────────────────────────────────────────╮\x1b[0m");
            println!("│  \x1b[1mEndpoint:\x1b[0m     {} [{}]", config.ai_endpoint, if ai_online { "\x1b[32mONLINE\x1b[0m" } else { "\x1b[31mOFFLINE\x1b[0m" });
            if ai_info.models.is_empty() {
                println!("│  \x1b[1mModels:\x1b[0m       \x1b[90m(No models loaded. Run 'ollama pull llama3.2:3b')\x1b[0m");
            } else {
                println!("│  \x1b[1mModels:\x1b[0m       \x1b[32m{}\x1b[0m", ai_info.models.join(", "));
            }
            let loaded = ai_bridge.poll_loaded_models().await;
            if !loaded.is_empty() {
                for p in loaded {
                    let vram_mb = p.size_vram.unwrap_or(0) as f64 / 1_048_576.0;
                    println!("│  \x1b[1mActive in VRAM:\x1b[0m \x1b[1;33m{}\x1b[0m ({:.1} MB)", p.name, vram_mb);
                }
            }
            println!("\x1b[1;35m╰─────────────────────────────────────────────────────────────────────────────╯\x1b[0m");
            println!();

            println!("\x1b[1;32m╭─ TOP OPERATOR COMMANDS ─────────────────────────────────────────────────────╮\x1b[0m");
            println!("│  \x1b[1;32mnexus run\x1b[0m                   Start background telemetry daemon & tray icon");
            println!("│  \x1b[1;32mnexus elevate\x1b[0m               Upgrade to Administrator / Root background service");
            println!("│  \x1b[1;32mnexus cluster\x1b[0m               Inspect 3-VM cluster (my-mac, cos, pro)");
            println!("│  \x1b[1;32mnexus services\x1b[0m              Inspect running containers, web engines & games");
            println!("│  \x1b[1;32mnexus restart <service>\x1b[0m     Trigger 1-click restart on cluster");
            println!("│  \x1b[1;32mnexus chat\x1b[0m                  Start interactive AI session with slash console");
            println!("│  \x1b[1;32mnexus ps\x1b[0m                    List models actively residing in VRAM");
            println!("│  \x1b[1;32mnexus manual\x1b[0m                Display comprehensive operator reference manual");
            println!("\x1b[1;32m╰─────────────────────────────────────────────────────────────────────────────╯\x1b[0m");
        }

        Some(Commands::Chat { mode, node, model, local, resume }) => {
            let effective_pin = admin_pin.or_else(|| {
                config.token = Some("2004".into());
                let _ = config.save();
                Some("2004".into())
            });

            let mut console = modules::operator_console::OperatorConsole::new(
                &primary_hub,
                effective_pin.as_deref(),
                &config.ai_endpoint,
                &config.node_id,
                &config.hostname,
            );
            console.run(mode, node, model, local, resume).await?;
        }

        Some(Commands::Ps) => {
            let ai_bridge = modules::ai_bridge::AiBridge::new(&config.ai_endpoint);
            let procs = ai_bridge.poll_loaded_models().await;
            println!("\x1b[1;36m=== Models Currently Loaded in VRAM / RAM ===\x1b[0m");
            if procs.is_empty() {
                println!("  \x1b[90m(No models actively loaded in VRAM. Cold start required)\x1b[0m");
            } else {
                for p in procs {
                    let total_mb = p.size.unwrap_or(0) as f64 / 1_048_576.0;
                    let vram_mb = p.size_vram.unwrap_or(0) as f64 / 1_048_576.0;
                    let exp = p.expires_at.as_deref().unwrap_or("never");
                    println!("  • \x1b[1;32m{}\x1b[0m: VRAM: \x1b[33m{:.1} MB\x1b[0m / Total: {:.1} MB | Expires: {}", p.name, vram_mb, total_mb, exp);
                }
            }
        }

        Some(Commands::Load { model, time }) => {
            let ai_bridge = modules::ai_bridge::AiBridge::new(&config.ai_endpoint);
            print!("Pre-loading '{}' into VRAM (keep_alive: {})... ", model, time);
            match ai_bridge.load_model(&model, Some(&time)).await {
                Ok(_) => println!("\x1b[32mSuccess! Model is warm in VRAM.\x1b[0m"),
                Err(e) => println!("\x1b[31mFailed: {}\x1b[0m", e),
            }
        }

        Some(Commands::Unload { model }) => {
            let ai_bridge = modules::ai_bridge::AiBridge::new(&config.ai_endpoint);
            print!("Unloading '{}' from VRAM... ", model);
            match ai_bridge.unload_model(&model).await {
                Ok(_) => println!("\x1b[32mSuccess! Model evicted and memory released.\x1b[0m"),
                Err(e) => println!("\x1b[31mFailed: {}\x1b[0m", e),
            }
        }

        Some(Commands::Ai { prompt, model }) => {
            let ai_bridge = modules::ai_bridge::AiBridge::new(&config.ai_endpoint);
            let models_info = ai_bridge.check_models().await;
            let selected_model = model
                .or_else(|| models_info.models.first().cloned())
                .unwrap_or_else(|| "llama3.2:3b".to_string());
            let prompt_text = prompt.join(" ");
            println!("\x1b[90m[Model: {}]\x1b[0m", selected_model);
            ai_bridge.stream_to_stdout(&selected_model, &prompt_text, None, None).await?;
        }

        Some(Commands::Models) => {
            let ai_bridge = modules::ai_bridge::AiBridge::new(&config.ai_endpoint);
            let info = ai_bridge.check_models().await;
            println!("=== Local AI Models (Ollama at {}) ===", config.ai_endpoint);
            if info.models.is_empty() {
                println!("No models found. Try running: ollama pull llama3.2:3b");
            } else {
                for m in info.models {
                    println!("  - {}", m);
                }
            }
        }

        Some(Commands::Manual) => {
            modules::helpbook::print_manual();
        }

        Some(Commands::Update { check, force, manifest_url }) => {
            updater::AgentUpdater::run_update(&manifest_url, check, force).await?;
        }

        Some(Commands::Tunnel { action }) => match action {
            TunnelCommands::Listen { bind, control, token } => {
                modules::tunnel::TunnelServer::run(&bind, &control, token).await.map_err(|e| e.to_string())?;
            }
            TunnelCommands::Connect { edge, target, token } => {
                modules::tunnel::TunnelClient::run(&edge, &target, token).await.map_err(|e| e.to_string())?;
            }
        },

        Some(Commands::Mc { action }) => match action {
            McCommands::Setup { dir, heap, exec, headless, sysctl } => {
                modules::minecraft::MinecraftManager::setup(
                    &dir, heap, exec.as_deref(), headless, sysctl,
                ).map_err(|e| e.to_string())?;
            }
            McCommands::Status { dir } => {
                modules::minecraft::MinecraftManager::status(dir.as_deref());
            }
            McCommands::TuneSysctl => {
                modules::minecraft::MinecraftManager::apply_network_sysctl().map_err(|e| e.to_string())?;
            }
            McCommands::Remote { action, host, user } => {
                modules::minecraft::MinecraftManager::handle_remote_command(
                    action, host.as_deref(), user.as_deref(),
                ).await.map_err(|e| e.to_string())?;
            }
        },

        None => {
            if cli.run {
                handle_run_command(config, false).await?;
            } else {
                modules::banner::print_banner();

                let mut sys = sysinfo::System::new_all();
                sys.refresh_all();
                let total_ram_gb = sys.total_memory() as f64 / 1_073_741_824.0;
                let used_ram_gb = sys.used_memory() as f64 / 1_073_741_824.0;
                let ram_pct = if total_ram_gb > 0.0 { (used_ram_gb / total_ram_gb) * 100.0 } else { 0.0 };
                let cpu_model = sys.cpus().first().map(|c| c.brand().trim()).unwrap_or("Unknown CPU");
                let cpu_cores = sys.cpus().len();

                let ai_bridge = modules::ai_bridge::AiBridge::new(&config.ai_endpoint);
                let ai_info = ai_bridge.check_models().await;
                let ai_online = ai_info.status.contains("ready");
                let daemon_live = is_daemon_running();

                let (hub_online, hub_sub_status) = probe_hub_node_status(&config).await;
                let hub_host = config.primary_hub_host();

                modules::banner::print_status_box(
                    &config.node_id,
                    &config.hostname,
                    &hub_host,
                    hub_online,
                    &config.ai_endpoint,
                    ai_online,
                );

                let daemon_state = modules::daemon_lock::read_daemon_state();
                let daemon_tag = if daemon_live {
                    if let Some(ds) = daemon_state {
                        format!("\x1b[1;32m● RUNNING\x1b[0m \x1b[90m(PID: {}, v{})\x1b[0m", ds.pid, ds.agent_version)
                    } else {
                        "\x1b[1;32m● RUNNING\x1b[0m \x1b[90m(Active)\x1b[0m".to_string()
                    }
                } else {
                    "\x1b[1;31m○ NOT RUNNING\x1b[0m \x1b[90m(Run '\x1b[1;32mnexus run\x1b[0m\x1b[90m' to start)\x1b[0m".to_string()
                };

                let hub_tag = if hub_online {
                    format!("\x1b[1;32m● CONNECTED\x1b[0m \x1b[90m({} - {})\x1b[0m", hub_host, hub_sub_status)
                } else {
                    format!("\x1b[1;31m○ DISCONNECTED\x1b[0m \x1b[90m({} - {})\x1b[0m", hub_host, hub_sub_status)
                };

                println!("\x1b[1;37mDaemon State:\x1b[0m   {}", daemon_tag);
                println!("\x1b[1;37mHub Mesh:\x1b[0m       {}", hub_tag);
                println!("\x1b[1;37mSystem:\x1b[0m         {} ({}) | RAM: {:.1}/{:.1} GB ({:.0}%)", cpu_model, cpu_cores, used_ram_gb, total_ram_gb, ram_pct);
                if !ai_info.models.is_empty() {
                    println!("\x1b[1;37mAI Models:\x1b[0m      \x1b[32m{}\x1b[0m", ai_info.models.join(", "));
                }
                println!();

                println!("\x1b[1;32mEssential Fleet Commands:\x1b[0m");
                println!("  \x1b[1;36mnexus run\x1b[0m                      \x1b[1m[CRITICAL]\x1b[0m Start background telemetry daemon & tray");
                println!("  \x1b[1;36mnexus status\x1b[0m                   Display live telemetry dashboard & metrics");
                println!("  \x1b[1;36mnexus elevate\x1b[0m                  Upgrade to Administrator / Root background service");
                println!("  \x1b[1;36mnexus cluster\x1b[0m                  Inspect 3-VM cluster (my-mac, cos, pro)");
                println!("  \x1b[1;36mnexus services\x1b[0m                 View active containers, web engines & games");
                println!("  \x1b[1;36mnexus restart <service>\x1b[0m        Trigger 1-click remote restart across cluster");
                println!("  \x1b[1;36mnexus mc setup\x1b[0m                 Auto-provision Minecraft server (Forge, Fabric, Paper, Vanilla)");
                println!("  \x1b[1;36mnexus mc status\x1b[0m                Check Minecraft server process & memory usage");
                println!("  \x1b[1;36mnexus tunnel connect\x1b[0m           Reverse TCP tunnel to Cloud Edge (e.g. --edge pro:25560)");
                println!("  \x1b[1;36mnexus chat\x1b[0m                     Start interactive AI chat & slash console");
                println!("  \x1b[1;36mnexus manual\x1b[0m                   Display full operator reference manual");
                println!();
            }
        }
    }

    Ok(())
}

async fn probe_hub_node_status(config: &AgentConfig) -> (bool, String) {
    let hub_host = config.primary_hub_host();
    let probe_url = format!("https://{}/api/fleet/nodes", hub_host);
    let client = match reqwest::Client::builder()
        .timeout(std::time::Duration::from_millis(2500))
        .build()
    {
        Ok(c) => c,
        Err(_) => return (false, "UNREACHABLE".to_string()),
    };

    let mut req = client.get(&probe_url);
    if let Some(token) = &config.token {
        req = req.header("X-Admin-Pin", token).header("Authorization", token);
    }

    match req.send().await {
        Ok(res) if res.status().is_success() => {
            if let Ok(nodes) = res.json::<Vec<serde_json::Value>>().await {
                for node in nodes {
                    if let Some(id) = node.get("node_id").and_then(|v| v.as_str()) {
                        if id == config.node_id {
                            let status = node.get("status").and_then(|v| v.as_str()).unwrap_or("offline");
                            if status == "online" {
                                return (true, "SESSION ACTIVE".to_string());
                            } else {
                                return (false, "REGISTERED (OFFLINE)".to_string());
                            }
                        }
                    }
                }
                (false, "NOT REGISTERED".to_string())
            } else {
                (false, "INVALID RESPONSE".to_string())
            }
        }
        Ok(res) => (false, format!("HTTP {}", res.status())),
        Err(_) => (false, "UNREACHABLE".to_string()),
    }
}

async fn handle_run_command(config: config::AgentConfig, foreground: bool) -> Result<(), Box<dyn std::error::Error>> {
    if !foreground {
        // 1. Check if daemon is already active
        if modules::daemon_lock::is_daemon_running() {
            println!("\x1b[1;33m[!] Nexus Fleet Daemon is already active in the background.\x1b[0m");
            println!("\x1b[90mTelemetry streaming, WebSocket mesh, and taskbar tray icon are running.\x1b[0m");
            println!("\x1b[90mUse '\x1b[1;32mnexus status\x1b[0m\x1b[90m' to inspect live metrics or '\x1b[1;32mnexus chat\x1b[0m\x1b[90m' to interact with AI.\x1b[0m");
            return Ok(());
        }

        // 2. Windows: spawn 100% detached process with CREATE_NO_WINDOW and CREATE_BREAKAWAY_FROM_JOB
        #[cfg(target_os = "windows")]
        {
            use std::os::windows::process::CommandExt;
            const CREATE_NEW_PROCESS_GROUP: u32 = 0x00000200;
            const CREATE_NO_WINDOW: u32 = 0x08000000;
            const CREATE_BREAKAWAY_FROM_JOB: u32 = 0x01000000;

            let current_exe = std::env::current_exe()?;
            let child = std::process::Command::new(&current_exe)
                .args(["run", "--foreground"])
                .creation_flags(CREATE_NEW_PROCESS_GROUP | CREATE_NO_WINDOW | CREATE_BREAKAWAY_FROM_JOB)
                .stdin(std::process::Stdio::null())
                .stdout(std::process::Stdio::null())
                .stderr(std::process::Stdio::null())
                .spawn()
                .or_else(|_| {
                    std::process::Command::new(&current_exe)
                        .args(["run", "--foreground"])
                        .creation_flags(CREATE_NEW_PROCESS_GROUP | CREATE_NO_WINDOW)
                        .stdin(std::process::Stdio::null())
                        .stdout(std::process::Stdio::null())
                        .stderr(std::process::Stdio::null())
                        .spawn()
                });

            match child {
                Ok(p) => {
                    println!("\x1b[1;32m✓ Nexus Fleet background daemon started successfully (PID: {}).\x1b[0m", p.id());
                    println!("\x1b[90mTelemetry is streaming. Windows Taskbar Tray icon is active.\x1b[0m");
                    println!("\x1b[90mYou can safely close this terminal.\x1b[0m");
                    return Ok(());
                }
                Err(e) => {
                    eprintln!("\x1b[31mFailed to spawn detached daemon: {}. Falling back to foreground.\x1b[0m", e);
                }
            }
        }

        // 3. Linux: check systemd (verifying is-active) or detach with setsid
        #[cfg(target_os = "linux")]
        {
            let uid = unsafe { libc::geteuid() };
            let user_run_dir = format!("/run/user/{}", uid);

            // Attempt systemd --user start if enabled
            let mut check_user = std::process::Command::new("systemctl");
            check_user.args(["--user", "is-enabled", "--quiet", "nexus-agent"]);
            if std::env::var("XDG_RUNTIME_DIR").is_err() && std::path::Path::new(&user_run_dir).exists() {
                check_user.env("XDG_RUNTIME_DIR", &user_run_dir);
            }
            if check_user.status().map(|s| s.success()).unwrap_or(false) {
                let mut start_user = std::process::Command::new("systemctl");
                start_user.args(["--user", "start", "nexus-agent"]);
                if std::env::var("XDG_RUNTIME_DIR").is_err() && std::path::Path::new(&user_run_dir).exists() {
                    start_user.env("XDG_RUNTIME_DIR", &user_run_dir);
                }
                let _ = start_user.status();

                let mut active_user = std::process::Command::new("systemctl");
                active_user.args(["--user", "is-active", "--quiet", "nexus-agent"]);
                if std::env::var("XDG_RUNTIME_DIR").is_err() && std::path::Path::new(&user_run_dir).exists() {
                    active_user.env("XDG_RUNTIME_DIR", &user_run_dir);
                }
                if active_user.status().map(|s| s.success()).unwrap_or(false) {
                    println!("\x1b[1;32m✓ Nexus Fleet background service started via systemd (--user).\x1b[0m");
                    println!("\x1b[90mTelemetry is streaming. You can safely close this terminal.\x1b[0m");
                    return Ok(());
                }
            }

            // Attempt system-wide systemd start if root
            if uid == 0 {
                let root_s = std::process::Command::new("systemctl")
                    .args(["is-enabled", "--quiet", "nexus-agent"])
                    .status();
                if root_s.map(|s| s.success()).unwrap_or(false) {
                    let _ = std::process::Command::new("systemctl").args(["start", "nexus-agent"]).status();
                    let active_root = std::process::Command::new("systemctl")
                        .args(["is-active", "--quiet", "nexus-agent"])
                        .status();
                    if active_root.map(|s| s.success()).unwrap_or(false) {
                        println!("\x1b[1;32m✓ Nexus Fleet background service started via systemd.\x1b[0m");
                        println!("\x1b[90mTelemetry is streaming. You can safely close this terminal.\x1b[0m");
                        return Ok(());
                    }
                }
            }

            // Fallback: Daemonize cleanly with setsid
            use std::os::unix::process::CommandExt;
            let current_exe = std::env::current_exe()?;
            let mut cmd = std::process::Command::new(&current_exe);
            cmd.args(["run", "--foreground"])
                .stdin(std::process::Stdio::null())
                .stdout(std::process::Stdio::null())
                .stderr(std::process::Stdio::null());

            unsafe {
                cmd.pre_exec(|| {
                    libc::setsid();
                    libc::signal(libc::SIGHUP, libc::SIG_IGN);
                    Ok(())
                });
            }

            match cmd.spawn() {
                Ok(p) => {
                    println!("\x1b[1;32m✓ Nexus Fleet background daemon detached (PID: {}).\x1b[0m", p.id());
                    println!("\x1b[90mTelemetry is streaming. You can safely close this terminal.\x1b[0m");
                    return Ok(());
                }
                Err(e) => {
                    eprintln!("\x1b[31mFailed to detach: {}. Running in foreground...\x1b[0m", e);
                }
            }
        }

        // 4. macOS: check launchd or detach with setsid
        #[cfg(target_os = "macos")]
        {
            let home_plist = std::env::var("HOME")
                .map(|h| std::path::PathBuf::from(h).join("Library/LaunchAgents/uk.faizal.nexus-agent.plist"))
                .unwrap_or_default();
            let root_plist = std::path::PathBuf::from("/Library/LaunchDaemons/uk.faizal.nexus-agent.plist");

            if home_plist.exists() || root_plist.exists() {
                let _ = std::process::Command::new("launchctl").args(["start", "uk.faizal.nexus-agent"]).status();
                let list_s = std::process::Command::new("launchctl").args(["list", "uk.faizal.nexus-agent"]).output();
                if list_s.map(|o| o.status.success()).unwrap_or(false) {
                    println!("\x1b[1;32m✓ Nexus Fleet background service started via macOS launchd.\x1b[0m");
                    println!("\x1b[90mTelemetry is streaming. You can safely close this terminal.\x1b[0m");
                    return Ok(());
                }
            }

            // Fallback: Detach with setsid
            use std::os::unix::process::CommandExt;
            let current_exe = std::env::current_exe()?;
            let mut cmd = std::process::Command::new(&current_exe);
            cmd.args(["run", "--foreground"])
                .stdin(std::process::Stdio::null())
                .stdout(std::process::Stdio::null())
                .stderr(std::process::Stdio::null());

            unsafe {
                cmd.pre_exec(|| {
                    libc::setsid();
                    libc::signal(libc::SIGHUP, libc::SIG_IGN);
                    Ok(())
                });
            }

            match cmd.spawn() {
                Ok(p) => {
                    println!("\x1b[1;32m✓ Nexus Fleet background daemon detached (PID: {}).\x1b[0m", p.id());
                    println!("\x1b[90mTelemetry is streaming. You can safely close this terminal.\x1b[0m");
                    return Ok(());
                }
                Err(e) => {
                    eprintln!("\x1b[31mFailed to detach: {}. Running in foreground...\x1b[0m", e);
                }
            }
        }
    }

    // Foreground execution: acquire exclusive single-instance lock
    #[cfg(not(target_os = "windows"))]
    unsafe {
        libc::signal(libc::SIGHUP, libc::SIG_IGN);
    }
    let _daemon_lock = match modules::daemon_lock::try_acquire_daemon_lock() {
        Ok(guard) => guard,
        Err(_) => {
            println!("\x1b[1;33m[!] Nexus Fleet Daemon is already active in the background.\x1b[0m");
            println!("\x1b[90mTelemetry streaming, WebSocket mesh, and taskbar tray icon are already running.\x1b[0m");
            println!("\x1b[90mUse '\x1b[1;32mnexus status\x1b[0m\x1b[90m' to inspect live metrics or '\x1b[1;32mnexus chat\x1b[0m\x1b[90m' to interact with AI.\x1b[0m");
            return Ok(());
        }
    };

    // Record initial daemon state
    modules::daemon_lock::write_daemon_state(std::process::id());

    // Launch background auto-updater task if enabled
    if config.is_feature_enabled("auto_update") {
        tokio::spawn(async {
            // First check 60 seconds after startup
            tokio::time::sleep(std::time::Duration::from_secs(60)).await;
            updater::AgentUpdater::run_auto_update_cycle().await;

            // Then check periodically every 4 hours
            let mut interval = tokio::time::interval(std::time::Duration::from_secs(4 * 3600));
            loop {
                interval.tick().await;
                updater::AgentUpdater::run_auto_update_cycle().await;
            }
        });
    }

    // Launch non-blocking Windows Taskbar Notification Area (System Tray) icon
    modules::tray::start_system_tray(config.node_id.clone());

    let mut client = MeshClient::new(config);
    client.run().await;

    // Clean up daemon state on shutdown
    modules::daemon_lock::remove_daemon_state();
    Ok(())
}
