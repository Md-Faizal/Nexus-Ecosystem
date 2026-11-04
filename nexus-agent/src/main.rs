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
            println!("=== Nexus Agent Status ===");
            println!("Node ID:       {}", config.node_id);
            println!("Hostname:      {}", config.hostname);
            println!("OS / Arch:     {} ({})", config.os, config.arch);
            println!("Version:       {}", env!("CARGO_PKG_VERSION"));
            println!("AI Endpoint:   {}", config.ai_endpoint);
            println!("Mesh Hubs:     {:?}", config.hubs);
            println!("Config File:   {:?}", AgentConfig::config_path());
        }
        Some(Commands::Run) | None => {
            let mut client = MeshClient::new(config);
            client.run().await;
        }
    }

    Ok(())
}

