mod config;
mod mesh;
mod modules;
mod service;
mod updater;

use clap::{Parser, Subcommand};
use config::AgentConfig;
use mesh::client::MeshClient;
use service::ServiceManager;
use tracing::{error, info};

#[derive(Parser)]
#[command(name = "nexus")]
#[command(bin_name = "nexus")]
#[command(author = "Faizal")]
#[command(version = "0.1.0")]
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
    Run,

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

    /// Chat interactively with local AI model in terminal (Claude Code / Gemini CLI style)
    Chat {
        /// Optional model to use (default: llama3.2:3b or first available)
        model: Option<String>,
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
}

fn is_daemon_running() -> bool {
    modules::daemon_lock::is_daemon_running()
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "nexus_agent=info".into()),
        )
        .init();

    // Catch panics gracefully
    std::panic::set_hook(Box::new(|panic_info| {
        error!("Nexus Agent encountered panic: {:?}", panic_info);
    }));

    let cli = Cli::parse();
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
        Some(Commands::Run) => {
            // Check & acquire exclusive single-instance lock to prevent duplicate daemons & tray icons
            let _daemon_lock = match modules::daemon_lock::try_acquire_daemon_lock() {
                Ok(guard) => guard,
                Err(_) => {
                    println!("\x1b[1;33m[!] Nexus Fleet Daemon is already active in the background.\x1b[0m");
                    println!("\x1b[90mTelemetry streaming, WebSocket mesh, and taskbar tray icon are already running.\x1b[0m");
                    println!("\x1b[90mUse '\x1b[1;32mnexus status\x1b[0m\x1b[90m' to inspect live metrics or '\x1b[1;32mnexus chat\x1b[0m\x1b[90m' to interact with AI.\x1b[0m");
                    return Ok(());
                }
            };

            // Hide background console window when running as daemon
            modules::tray::hide_console_window();

            // Launch non-blocking Windows Taskbar Notification Area (System Tray) icon
            modules::tray::start_system_tray(config.node_id.clone());

            let mut client = MeshClient::new(config);
            client.run().await;
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
            #[cfg(not(target_os = "windows"))]
            {
                println!("Requesting sudo elevation for systemd service installation...");
                let status = std::process::Command::new("sudo")
                    .args(["sh", "-c", "curl -fsSL https://faizal.uk/install.sh | bash"])
                    .status();
                match status {
                    Ok(s) if s.success() => println!("\x1b[32mSuccessfully elevated and installed root systemd service!\x1b[0m"),
                    _ => eprintln!("\x1b[31mElevation failed or sudo cancelled.\x1b[0m"),
                }
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
            let is_online = ai_info.status.contains("ready");
            let daemon_live = is_daemon_running();

            modules::banner::print_status_box(&config.node_id, &config.hostname, &config.ai_endpoint, is_online);

            println!("\x1b[1;36m╭─ FLEET TELEMETRY & DAEMON STATUS ───────────────────────────────────────────╮\x1b[0m");
            let daemon_tag = if daemon_live {
                "\x1b[1;32m● RUNNING (Telemetry Streaming Active)\x1b[0m"
            } else {
                "\x1b[1;31m○ NOT RUNNING\x1b[0m \x1b[90m(Run '\x1b[1;32mnexus run\x1b[0m\x1b[90m' to start)\x1b[0m"
            };
            println!("│  \x1b[1mDaemon State:\x1b[0m {}", daemon_tag);
            println!("│  \x1b[1mCPU:\x1b[0m          {} ({} cores)", cpu_model, cpu_cores);
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
            println!("│  \x1b[1mEndpoint:\x1b[0m     {} [{}]", config.ai_endpoint, if is_online { "\x1b[32mONLINE\x1b[0m" } else { "\x1b[31mOFFLINE\x1b[0m" });
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

        Some(Commands::Chat { model }) => {
            let ai_bridge = modules::ai_bridge::AiBridge::new(&config.ai_endpoint);
            ai_bridge.run_interactive_repl(model, Some(primary_hub), admin_pin).await?;
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

        None => {
            if cli.run {
                let _daemon_lock = match modules::daemon_lock::try_acquire_daemon_lock() {
                    Ok(guard) => guard,
                    Err(_) => {
                        println!("\x1b[1;33m[!] Nexus Fleet Daemon is already active in the background.\x1b[0m");
                        println!("\x1b[90mTelemetry streaming, WebSocket mesh, and taskbar tray icon are already running.\x1b[0m");
                        return Ok(());
                    }
                };

                modules::tray::hide_console_window();
                modules::tray::start_system_tray(config.node_id.clone());
                let mut client = MeshClient::new(config);
                client.run().await;
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
                let is_online = ai_info.status.contains("ready");
                let daemon_live = is_daemon_running();

                modules::banner::print_status_box(&config.node_id, &config.hostname, &config.ai_endpoint, is_online);

                let daemon_tag = if daemon_live {
                    "\x1b[1;32m● RUNNING (Telemetry Streaming Active)\x1b[0m"
                } else {
                    "\x1b[1;31m○ NOT RUNNING\x1b[0m \x1b[90m(Run '\x1b[1;32mnexus run\x1b[0m\x1b[90m' to start)\x1b[0m"
                };
                println!("\x1b[1;37mDaemon State:\x1b[0m   {}", daemon_tag);
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
                println!("  \x1b[1;36mnexus chat\x1b[0m                     Start interactive AI chat & slash console");
                println!("  \x1b[1;36mnexus manual\x1b[0m                   Display full operator reference manual");
                println!();
            }
        }
    }

    Ok(())
}
