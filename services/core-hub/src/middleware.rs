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
    let auth_header = req
        .headers()
        .get("authorization")
        .and_then(|v| v.to_str().ok())
        .map(|s| {
            let trimmed = s.trim();
            if let Some(stripped) = trimmed.strip_prefix("Bearer ") {
                stripped.trim().to_string()
            } else {
                trimmed.to_string()
            }
        });

    let raw_token = auth_header
        .or_else(|| {
            req.headers()
                .get("x-admin-pin")
                .and_then(|v| v.to_str().ok())
                .map(|s| s.trim().to_string())
        })
        .or_else(|| {
            req.uri().query().and_then(|q| {
                q.split('&')
                    .find(|pair| pair.starts_with("pin="))
                    .map(|pair| pair.trim_start_matches("pin=").to_string())
            })
        })
        .unwrap_or_default();

    // 1. Check for Machine Personal Access Token (PAT)
    if raw_token.starts_with("nex_live_") {
        let token_hash = crate::auth::crypto::sha256_hash(&raw_token);
        let conn = state.db.lock().await;
        let is_valid: bool = conn
            .query_row(
                r#"
                SELECT 1 FROM api_tokens t
                JOIN users u ON u.id = t.user_id
                WHERE t.token_hash = ?1 
                  AND u.status = 'active'
                  AND (u.role = 'owner' OR u.role = 'admin')
                  AND (t.expires_at IS NULL OR t.expires_at > CURRENT_TIMESTAMP)
                "#,
                rusqlite::params![token_hash],
                |_| Ok(true),
            )
            .unwrap_or(false);

        if is_valid {
            let _ = conn.execute(
                "UPDATE api_tokens SET last_used_at = CURRENT_TIMESTAMP WHERE token_hash = ?1",
                rusqlite::params![token_hash],
            );
            return next.run(req).await;
        }
    }

    // 2. Check for Modern Signed JWT Session Token
    let jwt_secret = state.config.admin_pin.as_bytes();
    if let Ok(claims) = crate::auth::crypto::verify_jwt(&raw_token, jwt_secret) {
        let conn = state.db.lock().await;
        let is_admin: bool = conn
            .query_row(
                "SELECT 1 FROM users WHERE id = ?1 AND status = 'active' AND token_version = ?2 AND (role = 'owner' OR role = 'admin')",
                rusqlite::params![claims.sub, claims.token_version],
                |_| Ok(true),
            )
            .unwrap_or(false);

        if is_admin {
            return next.run(req).await;
        }
    }

    // 3. Backward Compatibility Fallback: Legacy 4-digit PIN
    let conn = state.db.lock().await;
    let effective_pin: String = conn
        .query_row(
            "SELECT value FROM settings WHERE key = 'admin_pin' AND length(value) = 4",
            [],
            |row| row.get(0),
        )
        .unwrap_or_else(|_| state.config.admin_pin.clone());
    drop(conn);

    if !raw_token.is_empty() && raw_token == effective_pin {
        next.run(req).await
    } else {
        (
            StatusCode::FORBIDDEN,
            Json(serde_json::json!({
                "success": false,
                "error": "Unauthorized. Valid Nexus session token or admin credential required."
            })),
        )
            .into_response()
    }
}
