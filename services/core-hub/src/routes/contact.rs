use crate::mailer::send_contact_notification;
use crate::models::{ApiResponse, SaveMessageRequest};
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


pub async fn save_message(
    State(state): State<SharedState>,
    ConnectInfo(addr): ConnectInfo<SocketAddr>,
    headers: HeaderMap,
    Json(payload): Json<SaveMessageRequest>,
) -> impl IntoResponse {
    if payload.name.trim().is_empty()
        || payload.email.trim().is_empty()
        || payload.subject.trim().is_empty()
        || payload.message.trim().is_empty()
    {
        return Json(ApiResponse::<()>::err("All fields are required."));
    }

    // Extract client IP
    let ip = headers
        .get("x-forwarded-for")
        .and_then(|v| v.to_str().ok())
        .map(|s| s.split(',').next().unwrap_or(s).trim().to_string())
        .unwrap_or_else(|| addr.ip().to_string());

    let (name, email, subject, content) = (
        payload.name.clone(),
        payload.email.clone(),
        payload.subject.clone(),
        payload.message.clone(),
    );

    // Insert into SQLite database
    let insert_result = {
        let conn = state.db.lock().await;
        conn.execute(
            "INSERT INTO messages (name, email, subject, message, ip_address) VALUES (?1, ?2, ?3, ?4, ?5)",
            params![name, email, subject, content, ip],
        )
    };

    if let Err(e) = insert_result {
        error!("Error inserting contact message: {}", e);
        return Json(ApiResponse::<()>::err("Database error"));
    }

    // Trigger background email dispatch via Brevo relay
    let state_clone = state.clone();
    let ip_clone = ip.clone();
    tokio::spawn(async move {
        send_contact_notification(
            &state_clone.config,
            &state_clone.db,
            &name,
            &email,
            &subject,
            &content,
            &ip_clone,
        )
        .await;
    });

    Json(ApiResponse::ok(()))
}

pub async fn get_messages(
    State(state): State<SharedState>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    let mut stmt = match conn.prepare(
        "SELECT id, name, email, subject, message, ip_address, created_at FROM messages ORDER BY created_at DESC",
    ) {
        Ok(s) => s,
        Err(e) => return Json(serde_json::json!({ "success": false, "error": e.to_string() })),
    };

    let messages = stmt
        .query_map([], |row| {
            Ok(serde_json::json!({
                "id": row.get::<_, i64>(0)?,
                "name": row.get::<_, String>(1)?,
                "email": row.get::<_, String>(2)?,
                "subject": row.get::<_, String>(3)?,
                "message": row.get::<_, String>(4)?,
                "ip_address": row.get::<_, Option<String>>(5)?,
                "created_at": row.get::<_, Option<String>>(6)?,
            }))
        })
        .map(|rows| rows.filter_map(Result::ok).collect::<Vec<_>>())
        .unwrap_or_default();

    Json(serde_json::json!({
        "success": true,
        "messages": messages
    }))
}

pub async fn delete_message(
    State(state): State<SharedState>,
    axum::extract::Path(id): axum::extract::Path<i64>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    match conn.execute("DELETE FROM messages WHERE id = ?1", params![id]) {
        Ok(_) => Json(serde_json::json!({ "success": true })),
        Err(e) => Json(serde_json::json!({ "success": false, "error": e.to_string() })),
    }
}
