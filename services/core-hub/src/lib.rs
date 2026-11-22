pub mod auth;
pub mod auto_responder;
pub mod config;
pub mod db;
pub mod health;
pub mod mail_gateway;
pub mod mailer;
pub mod middleware;
pub mod models;
pub mod routes;
pub mod state;

use axum::{
    middleware::from_fn_with_state,
    routing::{delete, get, post},
    Router,
};
pub use config::AppConfig;
pub use db::open_database;
pub use state::{AppStateInner, SharedState};
use std::net::SocketAddr;
use std::sync::Arc;
use tower_http::cors::CorsLayer;
use tower_http::services::{ServeDir, ServeFile};
use tracing::info;

pub async fn run_server(config: AppConfig) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
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

    // Spawn Tier-2 Doomsday Local SMTP Ingress Gateway for Stalwart Mail Relay
    let gateway_state = state.clone();
    tokio::spawn(async move {
        mail_gateway::start_local_smtp_ingress("127.0.0.1:2525", gateway_state).await;
    });

    // Spawn Tier-2 Periodic Spool Retry & Drain Worker (Exponential Backoff up to 72h)
    routes::mesh_api::start_spool_retry_worker(state.clone());

    // Spawn Native Rust Mail Auto-Responder Engine (Direct IMAP Ingress & SMTP Acknowledgment)
    auto_responder::start_auto_responder(state.clone());

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
        .route("/oci-allocator/status", get(routes::health_api::get_oci_status))
        .route("/oci-allocator/trigger", post(routes::health_api::trigger_oci_check))
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
        .route("/fleet/mail-relay/status", get(routes::mesh_api::get_mail_relay_status))
        .route("/fleet/mail-relay/drain", post(routes::mesh_api::trigger_mail_spool_drain))
        .route("/snapshot/:db_name", get(routes::mesh_api::download_snapshot))
        .route("/auto-responder/config", get(routes::auto_responder_api::get_config).post(routes::auto_responder_api::update_config))
        .route("/auto-responder/preview-html", get(routes::auto_responder_api::get_preview_html))
        .route("/auto-responder/test", post(routes::auto_responder_api::send_test))
        .route("/auto-responder/logs", get(routes::auto_responder_api::get_logs))
        .route("/auto-responder/clear-cooldown", post(routes::auto_responder_api::clear_cooldown))
        .route("/admin/auto-responder/config", get(routes::auto_responder_api::get_config).post(routes::auto_responder_api::update_config))
        .route("/admin/auto-responder/preview-html", get(routes::auto_responder_api::get_preview_html))
        .route("/admin/auto-responder/test", post(routes::auto_responder_api::send_test))
        .route("/admin/auto-responder/logs", get(routes::auto_responder_api::get_logs))
        .route("/admin/auto-responder/clear-cooldown", post(routes::auto_responder_api::clear_cooldown))
        .layer(from_fn_with_state(
            state.clone(),
            middleware::require_admin_middleware,
        ));

    // Public API sub-router
    let api_routes = Router::new()
        .nest("/auth", auth::auth_router())
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
        // Standard OpenID Connect (OIDC) & OAuth 2.0 Discovery and Endpoints (e.g. Stalwart Mail SSO)
        .route("/.well-known/openid-configuration", get(auth::oidc::get_openid_configuration))
        .route("/.well-known/jwks.json", get(auth::oidc::get_jwks))
        .route("/oauth/authorize", get(auth::oidc::oauth_authorize).post(auth::oidc::oauth_authorize))
        .route("/oauth/token", post(auth::oidc::oauth_token))
        .route("/oauth/userinfo", get(auth::oidc::oauth_userinfo).post(auth::oidc::oauth_userinfo))
        .route("/api/mesh/ws", get(routes::mesh_api::handle_mesh_ws))
        .route("/api/mesh/crashes", get(routes::mesh_api::get_mesh_crashes))
        .route("/install.sh", get(routes::mesh_api::serve_install_sh))
        .route("/install.ps1", get(routes::mesh_api::serve_install_ps1))
        .route("/save-message", post(routes::contact::save_message))
        .route("/stats", get(routes::health_api::get_node_stats))
        .nest("/api", api_routes)
        .nest_service("/uploads", ServeDir::new(&uploads_dir))
        .nest_service(
            "/images",
            ServeDir::new(config.public_dir.join("images")),
        )
        .nest_service(
            "/releases",
            ServeDir::new(config.public_dir.join("releases")),
        )
        .nest_service(
            "/admin-assets",
            ServeDir::new(config.public_dir.join("admin-assets")),
        )
        .route_service("/admin", ServeFile::new(&admin_file))
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
