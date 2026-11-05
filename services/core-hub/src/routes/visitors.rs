use crate::models::TrackVisitRequest;
use crate::state::SharedState;
use axum::{
    extract::{ConnectInfo, State},
    http::HeaderMap,
    response::IntoResponse,
    Json,
};
use rusqlite::params;
use std::net::SocketAddr;
use tracing::error;

pub async fn track_visit(
    State(state): State<SharedState>,
    ConnectInfo(addr): ConnectInfo<SocketAddr>,
    headers: HeaderMap,
    payload: Option<Json<TrackVisitRequest>>,
) -> impl IntoResponse {
    let ip = headers
        .get("x-forwarded-for")
        .and_then(|v| v.to_str().ok())
        .map(|s| s.split(',').next().unwrap_or(s).trim().to_string())
        .unwrap_or_else(|| addr.ip().to_string());

    let user_agent = headers
        .get("user-agent")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("");

    let path = payload
        .and_then(|p| p.path.clone())
        .unwrap_or_else(|| "/".into());

    let conn = state.db.lock().await;
    let res = conn.execute(
        "INSERT INTO visitors (ip, user_agent, path) VALUES (?1, ?2, ?3)",
        params![ip, user_agent, path],
    );

    if let Err(e) = res {
        error!("Error inserting visitor track: {}", e);
        Json(serde_json::json!({ "success": false }))
    } else {
        Json(serde_json::json!({ "success": true }))
    }
}
