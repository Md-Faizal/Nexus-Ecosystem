pub mod native_app;

pub use native_app::launch_native_gui;

use std::net::SocketAddr;
use tracing::info;

pub const DASHBOARD_HTML: &str = include_str!("dashboard.html");

/// Launches the native Rust SpaceX/xAI monochrome Flight Deck GUI window
pub fn launch_gui() {
    launch_native_gui();
}

/// Spawns the local Flight Deck status HTTP server on 127.0.0.1:9876
pub fn start_local_gui_server() {
    tokio::spawn(async {
        use axum::{response::Html, routing::get, Router};

        let app = Router::new()
            .route("/", get(|| async { Html(DASHBOARD_HTML) }))
            .route("/dashboard", get(|| async { Html(DASHBOARD_HTML) }))
            .route("/api/status", get(get_local_gui_status));

        let addr = SocketAddr::from(([127, 0, 0, 1], 9876));
        if let Ok(listener) = tokio::net::TcpListener::bind(addr).await {
            info!("[Flight Deck] Local GUI server active on http://{}", addr);
            let _ = axum::serve(listener, app).await;
        }
    });
}

async fn get_local_gui_status() -> axum::Json<serde_json::Value> {
    let is_cloud = crate::modules::fitness::FitnessScore::detect_is_cloud();
    let mut sys = sysinfo::System::new_all();
    sys.refresh_all();

    let total_ram_mb = sys.total_memory() / (1024 * 1024);
    let used_ram_mb = sys.used_memory() / (1024 * 1024);
    let cpu_usage = sys.global_cpu_usage();

    let fitness = crate::modules::fitness::FitnessScore::calculate(
        total_ram_mb,
        used_ram_mb,
        cpu_usage,
        is_cloud,
        false,
        true,
    );

    axum::Json(serde_json::json!({
        "node_id": crate::config::AgentConfig::load_or_init().node_id,
        "hostname": sysinfo::System::host_name().unwrap_or_else(|| "POTATO-PRO".into()),
        "fitness": fitness,
        "total_ram_mb": total_ram_mb,
        "used_ram_mb": used_ram_mb,
        "cpu_usage_pct": cpu_usage,
        "uptime_secs": sysinfo::System::uptime(),
    }))
}
