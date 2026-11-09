use nexus_core::{config::AppConfig, run_server};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "personal_site_rust=info,tower_http=info".into()),
        )
        .init();

    let config = AppConfig::from_env();
    if let Err(e) = run_server(config).await {
        eprintln!("Fatal server error: {e}");
        std::process::exit(1);
    }

    Ok(())
}
