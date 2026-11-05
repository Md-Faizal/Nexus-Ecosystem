use crate::models::{AccessLogItem, BanIpRequest, BannedIpItem, ChangePinRequest, VerifyPinRequest};
use crate::state::SharedState;
use axum::{
    extract::{ConnectInfo, Path, State},
    http::HeaderMap,
    response::IntoResponse,
    Json,
};
use rusqlite::params;
use std::net::SocketAddr;
use tracing::info;

pub async fn get_access_logs(
    State(state): State<SharedState>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    let mut stmt = match conn.prepare(
        "SELECT id, ip, status, timestamp FROM access_logs ORDER BY timestamp DESC LIMIT 50",
    ) {
        Ok(s) => s,
        Err(e) => return Json(serde_json::json!({ "success": false, "error": e.to_string(), "logs": [] })),
    };

    let logs = stmt
        .query_map([], |row| {
            Ok(AccessLogItem {
                id: row.get(0)?,
                ip: row.get(1)?,
                status: row.get(2)?,
                timestamp: row.get(3)?,
            })
        })
        .map(|rows| rows.filter_map(Result::ok).collect::<Vec<_>>())
        .unwrap_or_default();

    Json(serde_json::json!({
        "success": true,
        "logs": logs
    }))
}

pub async fn get_banned_ips(
    State(state): State<SharedState>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    let mut stmt = match conn.prepare("SELECT ip, reason, timestamp FROM banned_ips") {
        Ok(s) => s,
        Err(e) => return Json(serde_json::json!({ "success": false, "error": e.to_string(), "ips": [] })),
    };

    let ips = stmt
        .query_map([], |row| {
            Ok(BannedIpItem {
                ip: row.get(0)?,
                reason: row.get(1)?,
                timestamp: row.get(2)?,
            })
        })
        .map(|rows| rows.filter_map(Result::ok).collect::<Vec<_>>())
        .unwrap_or_default();

    Json(serde_json::json!({
        "success": true,
        "ips": ips
    }))
}

pub async fn ban_ip(
    State(state): State<SharedState>,
    Json(payload): Json<BanIpRequest>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    match conn.execute(
        "INSERT OR REPLACE INTO banned_ips (ip, reason) VALUES (?1, ?2)",
        params![payload.ip, payload.reason],
    ) {
        Ok(_) => Json(serde_json::json!({ "success": true })),
        Err(e) => Json(serde_json::json!({ "success": false, "error": e.to_string() })),
    }
}

pub async fn unban_ip(
    State(state): State<SharedState>,
    Path(ip): Path<String>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    match conn.execute("DELETE FROM banned_ips WHERE ip = ?1", params![ip]) {
        Ok(_) => Json(serde_json::json!({ "success": true })),
        Err(e) => Json(serde_json::json!({ "success": false, "error": e.to_string() })),
    }
}

pub async fn verify_pin(
    State(state): State<SharedState>,
    ConnectInfo(addr): ConnectInfo<SocketAddr>,
    headers: HeaderMap,
    Json(payload): Json<VerifyPinRequest>,
) -> impl IntoResponse {
    let ip = headers
        .get("x-forwarded-for")
        .and_then(|v| v.to_str().ok())
        .map(|s| s.split(',').next().unwrap_or(s).trim().to_string())
        .unwrap_or_else(|| addr.ip().to_string());

    let conn = state.db.lock().await;
    let effective_pin: String = conn
        .query_row(
            "SELECT value FROM settings WHERE key = 'admin_pin' AND length(value) = 4",
            [],
            |row| row.get(0),
        )
        .unwrap_or_else(|_| state.config.admin_pin.clone());

    let is_valid = payload.pin.trim() == effective_pin;
    let status_str = if is_valid { "SUCCESS" } else { "FAILURE" };

    info!("PIN verification from {} - {}", ip, status_str);

    let _ = conn.execute(
        "INSERT INTO access_logs (ip, status) VALUES (?1, ?2)",
        params![ip, status_str],
    );

    Json(serde_json::json!({ "success": is_valid }))
}

pub async fn change_pin(
    State(state): State<SharedState>,
    ConnectInfo(addr): ConnectInfo<SocketAddr>,
    headers: HeaderMap,
    Json(payload): Json<ChangePinRequest>,
) -> impl IntoResponse {
    let ip = headers
        .get("x-forwarded-for")
        .and_then(|v| v.to_str().ok())
        .map(|s| s.split(',').next().unwrap_or(s).trim().to_string())
        .unwrap_or_else(|| addr.ip().to_string());

    let conn = state.db.lock().await;
    let effective_pin: String = conn
        .query_row(
            "SELECT value FROM settings WHERE key = 'admin_pin' AND length(value) = 4",
            [],
            |row| row.get(0),
        )
        .unwrap_or_else(|_| state.config.admin_pin.clone());

    if payload.current_pin.trim() != effective_pin {
        info!("Failed PIN change attempt from {} - incorrect current PIN", ip);
        return (
            axum::http::StatusCode::FORBIDDEN,
            Json(serde_json::json!({
                "success": false,
                "error": "Current 4-digit PIN is incorrect."
            })),
        );
    }

    let new_pin = payload.new_pin.trim();
    if new_pin.len() != 4 || !new_pin.chars().all(|c| c.is_ascii_digit()) {
        return (
            axum::http::StatusCode::BAD_REQUEST,
            Json(serde_json::json!({
                "success": false,
                "error": "New PIN must be exactly 4 numeric digits."
            })),
        );
    }

    match conn.execute(
        "INSERT OR REPLACE INTO settings (key, value) VALUES ('admin_pin', ?1)",
        params![new_pin],
    ) {
        Ok(_) => {
            info!("Admin PIN successfully changed from {}", ip);
            let _ = conn.execute(
                "INSERT INTO access_logs (ip, status) VALUES (?1, 'PIN_CHANGED')",
                params![ip],
            );
            (
                axum::http::StatusCode::OK,
                Json(serde_json::json!({
                    "success": true,
                    "message": "Admin PIN updated successfully."
                })),
            )
        }
        Err(e) => (
            axum::http::StatusCode::INTERNAL_SERVER_ERROR,
            Json(serde_json::json!({
                "success": false,
                "error": format!("Database error: {}", e)
            })),
        ),
    }
}
