mod config;
mod db;
mod health;
mod mailer;
mod middleware;
mod models;
mod routes;
mod state;

use axum::{
    middleware::from_fn_with_state,
    routing::{delete, get, post},
    Router,
};
use config::AppConfig;
use db::open_database;
use state::{AppStateInner, SharedState};
use std::net::SocketAddr;
use std::sync::Arc;
use tower_http::cors::CorsLayer;
use tower_http::services::{ServeDir, ServeFile};
use tracing::info;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "personal_site_rust=info,tower_http=info".into()),
        )
        .init();

    let config = AppConfig::from_env();
    info!("Starting personal-site Rust Axum backend on port {}", config.port);
    info!("Database path: {:?}", config.db_path);
    info!("Static SPA path: {:?}", config.static_dir);
    info!("Public path: {:?}", config.public_dir);

    let db = open_database(&config.db_path)?;

    let state: SharedState = Arc::new(AppStateInner {
        config: config.clone(),
        db: db.clone(),
        fleet: Arc::new(routes::mesh_api::FleetHubState::new()),
    });

    // Spawn 24h health report background cron
    tokio::spawn(health::run_scheduled_reporter(config.clone(), db.clone()));

    // Admin protected sub-router
    let admin_routes = Router::new()
        .route("/messages", get(routes::contact::get_messages))
        .route("/messages/:id", delete(routes::contact::delete_message))
        .route("/upload-picture", post(routes::profile::upload_profile_pic))
        .route("/profile-history", get(routes::profile::get_profile_history))
        .route("/profile-history/:id", delete(routes::profile::delete_profile_pic))
        .route("/select-profile-pic/:id", post(routes::profile::select_profile_pic))
        .route("/use-history-pic/:id", post(routes::profile::select_profile_pic))
        .route("/profile-pic/:id", delete(routes::profile::delete_profile_pic))
        .route("/projects", post(routes::projects::create_project))
        .route("/projects/:id", delete(routes::projects::delete_project))
        .route("/system-stats", get(routes::health_api::get_system_stats))
        .route("/services", get(routes::services_api::list_services))
        .route("/services/:id/action", post(routes::services_api::control_service))
        .route("/cluster/traffic", get(routes::services_api::get_cluster_traffic))
        .route("/cluster/action", post(routes::services_api::execute_cluster_action))
        .route("/access-logs", get(routes::security::get_access_logs))
        .route("/ban-ip", post(routes::security::ban_ip))
        .route("/banned-ips", get(routes::security::get_banned_ips))
        .route("/ban-ip/:ip", delete(routes::security::unban_ip))
        .route("/settings", get(routes::settings::get_settings).post(routes::settings::update_setting))
        .route("/test-email", post(routes::health_api::test_email))
        .route("/health-report/data", get(routes::health_api::get_health_data))
        .route("/health-report/preview", get(routes::health_api::get_health_preview))
        .route("/health-report/send", post(routes::health_api::send_health_report))
        .route("/health-report/status", get(routes::health_api::get_health_status))
        .route("/health-report/config", post(routes::health_api::update_health_config))
        .route("/health-report/errors", get(routes::health_api::get_health_errors))
        .route("/oci-allocator/send-mail", post(routes::health_api::send_oci_digest))
        .route("/fleet/nodes", get(routes::mesh_api::list_nodes))
        .route("/fleet/telemetry/bedrock", get(routes::health_api::get_bedrock_telemetry))
        .route("/fleet/nodes/:id/command", post(routes::mesh_api::dispatch_command))
        .route("/fleet/nodes/:id/features", post(routes::mesh_api::update_node_features))
        .route("/fleet/nodes/:id/ai/stream", post(routes::mesh_api::stream_ai_prompt))
        .route("/fleet/nodes/:id/ai/load", post(routes::mesh_api::load_model_vram))
        .route("/fleet/nodes/:id/ai/unload", post(routes::mesh_api::unload_model_vram))
        .route("/fleet/nodes/:id/ai/ps", get(routes::mesh_api::get_vram_processes))
        .route("/fleet/nodes/:id/ai/pull", post(routes::mesh_api::pull_model))
        .route("/fleet/chat/messages", get(routes::mesh_api::list_chat_messages).post(routes::mesh_api::send_chat_message))
        .route("/fleet/ai/conversations", get(routes::mesh_api::list_ai_conversations).post(routes::mesh_api::create_ai_conversation))
        .route("/fleet/ai/conversations/:id/messages", get(routes::mesh_api::get_ai_conversation_messages).post(routes::mesh_api::send_ai_conversation_message))
        .route("/fleet/ai/conversations/:id", delete(routes::mesh_api::delete_ai_conversation))
        .layer(from_fn_with_state(
            state.clone(),
            middleware::require_admin_middleware,
        ));

    // Public API sub-router
    let api_routes = Router::new()
        .route("/projects", get(routes::projects::list_projects))
        .route("/project-image/:id", get(routes::projects::get_project_image))
        .route("/profile-pic", get(routes::profile::get_current_profile_pic))
        .route("/profile-pic/:id", get(routes::profile::get_profile_pic_by_id))
        .route("/maintenance-status", get(routes::settings::get_maintenance_status))
        .route("/theme-config", get(routes::settings::get_theme_config))
        .route("/track-visit", post(routes::visitors::track_visit))
        .route("/verify-pin", post(routes::security::verify_pin))
        .route("/change-pin", post(routes::security::change_pin))
        .route("/stats", get(routes::health_api::get_node_stats))
        .merge(admin_routes);

    // Dynamic uploads directory
    let uploads_dir = config.public_dir.join("uploads");
    let _ = std::fs::create_dir_all(&uploads_dir);

    // Static fallback SPA service
    let spa_index = config.static_dir.join("index.html");
    let spa_service = if spa_index.exists() {
        ServeDir::new(&config.static_dir).fallback(ServeFile::new(&spa_index))
    } else {
        // Fallback to public if frontend/dist not yet built
        let public_index = config.public_dir.join("index.html");
        ServeDir::new(&config.public_dir).fallback(ServeFile::new(&public_index))
    };

    let admin_file = config.public_dir.join("admin.html");

    let app = Router::new()
        .route("/api/mesh/ws", get(routes::mesh_api::handle_mesh_ws))
        .route("/install.sh", get(routes::mesh_api::serve_install_sh))
        .route("/install.ps1", get(routes::mesh_api::serve_install_ps1))
        .route("/save-message", post(routes::contact::save_message))
        .route("/stats", get(routes::health_api::get_node_stats))
        .nest("/api", api_routes)
        .nest_service("/uploads", ServeDir::new(&uploads_dir))
        .nest_service(
            "/releases",
            ServeDir::new(config.public_dir.join("releases")),
        )
        .nest_service(
            "/admin-assets",
            ServeDir::new(config.public_dir.join("admin-assets")),
        )
        .route_service("/admin.html", ServeFile::new(&admin_file))
        .fallback_service(spa_service)
        .layer(from_fn_with_state(
            state.clone(),
            middleware::ip_blacklist_middleware,
        ))
        .layer(CorsLayer::permissive())
        .with_state(state);

    let addr = SocketAddr::from(([0, 0, 0, 0], config.port));
    let listener = tokio::net::TcpListener::bind(addr).await?;
    info!("Faizal Axum Server successfully listening on http://{}", addr);

    axum::serve(
        listener,
        app.into_make_service_with_connect_info::<SocketAddr>(),
    )
    .await?;

    Ok(())
}
