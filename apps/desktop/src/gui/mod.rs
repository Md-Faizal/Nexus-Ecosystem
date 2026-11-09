use std::net::SocketAddr;
use std::process::Command;
use tracing::info;

pub const DASHBOARD_HTML: &str = include_str!("dashboard.html");

/// Spawns the local SpaceX-themed Flight Deck HUD HTTP server on 127.0.0.1:9876
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

/// Launches the Flight Deck HUD in a standalone native app window (via MS Edge App mode on Windows)
pub fn launch_gui() {
    let url = "http://127.0.0.1:9876/dashboard";

    #[cfg(target_os = "windows")]
    {
        // Try launching in native app mode using Microsoft Edge (installed on all Windows 10/11)
        // This opens a clean borderless desktop window without browser tabs or URL bars
        let edge_paths = [
            r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
            r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
        ];

        let mut launched = false;
        for path in edge_paths {
            if std::path::Path::new(path).exists() {
                if Command::new(path)
                    .args([&format!("--app={}", url), "--window-size=1080,720"])
                    .spawn()
                    .is_ok()
                {
                    launched = true;
                    break;
                }
            }
        }

        if !launched {
            let _ = Command::new("cmd").args(["/c", "start", url]).spawn();
        }
    }

    #[cfg(target_os = "macos")]
    {
        let _ = Command::new("open").arg(url).spawn();
    }

    #[cfg(target_os = "linux")]
    {
        let _ = Command::new("xdg-open").arg(url).spawn();
    }
}
