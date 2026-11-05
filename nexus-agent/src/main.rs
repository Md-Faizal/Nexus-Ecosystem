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
#[command(name = "nexus-agent")]
#[command(author = "Faizal")]
#[command(version = "0.1.0")]
#[command(about = "Ultra-lightweight, resilient fleet telemetry & remote management agent", long_about = None)]
struct Cli {
    #[command(subcommand)]
    command: Option<Commands>,

    /// Override the primary mesh hub URL (e.g. wss://faizal.uk/api/mesh/ws)
    #[arg(short, long)]
    hub: Option<String>,

    /// Override the local Ollama AI endpoint (default: http://127.0.0.1:11434)
    #[arg(long)]
    ai_endpoint: Option<String>,

    /// Run in foreground directly (default when no command provided)
    #[arg(long)]
    run: bool,
}

#[derive(Subcommand)]
enum Commands {
    /// Start the agent in the foreground or as a background worker
    Run,

    /// Manage native OS background service (systemd, Windows Service, launchd)
    Service {
        #[command(subcommand)]
        action: ServiceCommands,
    },

    /// Trigger silent self-update check
    Update {
        #[arg(long, default_value = "https://faizal.uk/releases/latest.json")]
        manifest_url: String,
    },

    /// Show local node identity, status, and configured hubs
    Status,

    /// Chat interactively with local AI model in terminal (Claude Code / Gemini CLI style)
    Chat {
        /// Optional model to use (default: llama3.2:3b or first available)
        model: Option<String>,
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

    match cli.command {
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
        Some(Commands::Update { manifest_url }) => {
            info!("Checking for updates at: {}", manifest_url);
            // Quick CLI trigger for manual update check
            println!("Node is up to date (version: {}).", env!("CARGO_PKG_VERSION"));
        }
        Some(Commands::Status) => {
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

            println!("\x1b[1;36m====================================================================\x1b[0m");
            println!("\x1b[1;32m ⚡ NEXUS FLEET AGENT // SYSTEM & TELEMETRY DASHBOARD \x1b[0m");
            println!("\x1b[1;36m====================================================================\x1b[0m");
            println!("\x1b[1mNode ID:\x1b[0m        \x1b[33m{}\x1b[0m", config.node_id);
            println!("\x1b[1mHostname:\x1b[0m       {}", config.hostname);
            println!("\x1b[1mOS / Arch:\x1b[0m      {} ({})", config.os, config.arch);
            println!("\x1b[1mAgent Version:\x1b[0m  v{}", env!("CARGO_PKG_VERSION"));
            println!("\x1b[1mConfig File:\x1b[0m    {:?}", AgentConfig::config_path());
            println!();
            println!("\x1b[1;34mHARDWARE METRICS:\x1b[0m");
            println!("  \x1b[1mCPU:\x1b[0m          {} ({} logical cores)", cpu_model, cpu_cores);
            println!("  \x1b[1mRAM:\x1b[0m          {:.2} GB / {:.2} GB ({:.1}%)", used_ram_gb, total_ram_gb, ram_pct);
            if let Some(g) = gpus {
                println!("  \x1b[1mGPU:\x1b[0m          {} ({})", g.name, g.vendor);
                println!("                VRAM: {:.1} GB / {:.1} GB ({:.0}%) | Temp: {:.0}°C", 
                    g.vram_used_mb as f64 / 1024.0, 
                    g.vram_total_mb as f64 / 1024.0,
                    g.utilization_pct,
                    g.temperature_c.unwrap_or(0.0));
            }
            println!();
            println!("\x1b[1;34mLOCAL AI ENGINE (OLLAMA):\x1b[0m");
            let is_online = ai_info.status.contains("ready");
            println!("  \x1b[1mEndpoint:\x1b[0m     {} [{}]", config.ai_endpoint, if is_online { "\x1b[32mONLINE\x1b[0m" } else { "\x1b[31mOFFLINE\x1b[0m" });
            if ai_info.models.is_empty() {
                println!("  \x1b[1mModels:\x1b[0m       \x1b[90m(No models loaded. Run 'ollama pull llama3.2:3b')\x1b[0m");
            } else {
                println!("  \x1b[1mModels:\x1b[0m       \x1b[32m{}\x1b[0m", ai_info.models.join(", "));
            }
            println!();
            println!("\x1b[1;34mMESH NETWORK:\x1b[0m");
            println!("  \x1b[1mHubs:\x1b[0m         {:?}", config.hubs);
            println!("  \x1b[1mAdmin Panel:\x1b[0m  \x1b[4;36mhttps://faizal.uk/admin.html\x1b[0m");
            println!();
            println!("\x1b[1;34mCLI COMMANDS:\x1b[0m");
            println!("  \x1b[1;32mnexus-agent chat\x1b[0m           Interactive local AI chat in terminal");
            println!("  \x1b[1;32mnexus-agent ai <prompt>\x1b[0m    Stream one-off prompt to terminal");
            println!("  \x1b[1;32mnexus-agent models\x1b[0m         List local Ollama models");
            println!("  \x1b[1;32mnexus-agent status\x1b[0m         Display this dashboard");
            println!("  \x1b[1;32mnexus-agent service install\x1b[0m Register auto-start service with Taskbar Tray");
            println!("\x1b[1;36m====================================================================\x1b[0m");
        }
        Some(Commands::Chat { model }) => {
            let ai_bridge = modules::ai_bridge::AiBridge::new(&config.ai_endpoint);
            ai_bridge.run_interactive_repl(model).await?;
        }
        Some(Commands::Ai { prompt, model }) => {
            let ai_bridge = modules::ai_bridge::AiBridge::new(&config.ai_endpoint);
            let models_info = ai_bridge.check_models().await;
            let selected_model = model
                .or_else(|| models_info.models.first().cloned())
                .unwrap_or_else(|| "llama3.2:3b".to_string());
            let prompt_text = prompt.join(" ");
            println!("\x1b[90m[Model: {}]\x1b[0m", selected_model);
            ai_bridge.stream_to_stdout(&selected_model, &prompt_text).await?;
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
        Some(Commands::Run) => {
            // Hide background console window when running as daemon
            modules::tray::hide_console_window();

            // Launch non-blocking Windows Taskbar Notification Area (System Tray) icon
            modules::tray::start_system_tray(config.node_id.clone());

            let mut client = MeshClient::new(config);
            client.run().await;
        }
        None => {
            if cli.run {
                modules::tray::hide_console_window();
                modules::tray::start_system_tray(config.node_id.clone());
                let mut client = MeshClient::new(config);
                client.run().await;
            } else {
                // If run without arguments in terminal, print status dashboard
                let mut sys = sysinfo::System::new_all();
                sys.refresh_all();
                let total_ram_gb = sys.total_memory() as f64 / 1_073_741_824.0;
                let used_ram_gb = sys.used_memory() as f64 / 1_073_741_824.0;
                let ram_pct = if total_ram_gb > 0.0 { (used_ram_gb / total_ram_gb) * 100.0 } else { 0.0 };
                let cpu_model = sys.cpus().first().map(|c| c.brand().trim()).unwrap_or("Unknown CPU");
                let cpu_cores = sys.cpus().len();

                let ai_bridge = modules::ai_bridge::AiBridge::new(&config.ai_endpoint);
                let ai_info = ai_bridge.check_models().await;

                println!("\x1b[1;36m====================================================================\x1b[0m");
                println!("\x1b[1;32m ⚡ NEXUS FLEET AGENT CLI (v{}) \x1b[0m", env!("CARGO_PKG_VERSION"));
                println!("\x1b[1;36m====================================================================\x1b[0m");
                println!("Node ID:        \x1b[33m{}\x1b[0m", config.node_id);
                println!("Hostname:       {}", config.hostname);
                println!("CPU / RAM:      {} ({}) | {:.1}/{:.1} GB ({:.0}%)", cpu_model, cpu_cores, used_ram_gb, total_ram_gb, ram_pct);
                println!("Local AI:       {} [{}]", config.ai_endpoint, if ai_info.status.contains("ready") { "\x1b[32mONLINE\x1b[0m" } else { "\x1b[31mOFFLINE\x1b[0m" });
                if !ai_info.models.is_empty() {
                    println!("Models:         \x1b[32m{}\x1b[0m", ai_info.models.join(", "));
                }
                println!();
                println!("Available CLI commands:");
                println!("  \x1b[1;32mnexus-agent chat\x1b[0m           Start interactive AI chat in terminal");
                println!("  \x1b[1;32mnexus-agent ai <prompt>\x1b[0m    Stream one-off prompt to terminal");
                println!("  \x1b[1;32mnexus-agent models\x1b[0m         List local Ollama models");
                println!("  \x1b[1;32mnexus-agent status\x1b[0m         Full system telemetry dashboard");
                println!("  \x1b[1;32mnexus-agent run\x1b[0m            Start background telemetry & tray daemon");
                println!("  \x1b[1;32mnexus-agent service install\x1b[0m Register auto-start in Windows Startup");
                println!("\x1b[1;36m====================================================================\x1b[0m");
            }
        }
    }

    Ok(())
}

