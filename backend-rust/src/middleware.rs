use crate::db::{is_ip_banned, track_visitor};
use crate::state::SharedState;
use axum::{
    extract::{ConnectInfo, Request, State},
    http::{HeaderMap, StatusCode},
    middleware::Next,
    response::{IntoResponse, Response},
    Json,
};
use std::net::SocketAddr;

pub fn extract_client_ip(headers: &HeaderMap, addr: &SocketAddr) -> String {
    if let Some(forwarded) = headers.get("x-forwarded-for").and_then(|v| v.to_str().ok()) {
        if let Some(first) = forwarded.split(',').next() {
            let trimmed = first.trim();
            if !trimmed.is_empty() {
                return trimmed.to_string();
            }
        }
    }
    addr.ip().to_string()
}


pub async fn ip_blacklist_middleware(
    State(state): State<SharedState>,
    ConnectInfo(addr): ConnectInfo<SocketAddr>,
    req: Request,
    next: Next,
) -> Response {
    let ip = extract_client_ip(req.headers(), &addr);

    if is_ip_banned(&state.db, &ip).await {
        return (StatusCode::FORBIDDEN, "Your IP is banned.").into_response();
    }

    // Auto visitor tracking for page views (GET, non-API, non-asset)
    if req.method() == axum::http::Method::GET {
        let path = req.uri().path();
        if !path.starts_with("/api") && !path.contains('.') {
            let ua = req
                .headers()
                .get("user-agent")
                .and_then(|v| v.to_str().ok())
                .unwrap_or("")
                .to_string();
            let db = state.db.clone();
            let ip_clone = ip.clone();
            let path_clone = path.to_string();
            tokio::spawn(async move {
                track_visitor(&db, &ip_clone, &ua, &path_clone).await;
            });
        }
    }

    next.run(req).await
}

pub async fn require_admin_middleware(
    State(state): State<SharedState>,
    req: Request,
    next: Next,
) -> Response {
    let pin_from_header = req
        .headers()
        .get("authorization")
        .or_else(|| req.headers().get("x-admin-pin"))
        .and_then(|v| v.to_str().ok())
        .map(|s| {
            let trimmed = s.trim();
            if let Some(stripped) = trimmed.strip_prefix("Bearer ") {
                stripped.trim().to_string()
            } else {
                trimmed.to_string()
            }
        });

    let pin_from_query = req.uri().query().and_then(|q| {
        q.split('&')
            .find(|pair| pair.starts_with("pin="))
            .map(|pair| pair.trim_start_matches("pin=").to_string())
    });

    let pin = pin_from_header.or(pin_from_query).unwrap_or_default();

    let conn = state.db.lock().await;
    let effective_pin: String = conn
        .query_row(
            "SELECT value FROM settings WHERE key = 'admin_pin' AND length(value) = 4",
            [],
            |row| row.get(0),
        )
        .unwrap_or_else(|_| state.config.admin_pin.clone());
    drop(conn);

    if pin == effective_pin {
        next.run(req).await
    } else {
        (
            StatusCode::FORBIDDEN,
            Json(serde_json::json!({
                "success": false,
                "error": "Unauthorized. Invalid PIN."
            })),
        )
            .into_response()
    }
}
