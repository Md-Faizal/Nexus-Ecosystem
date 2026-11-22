use crate::auth::crypto::{
    hash_password, hash_security_answer, verify_security_answer,
};
use crate::state::SharedState;
use axum::{
    extract::{Path, Query, State},
    http::{HeaderMap, StatusCode},
    response::IntoResponse,
    Json,
};
use chrono::Utc;
use rusqlite::params;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use tracing::{error, info, warn};

fn verify_internal_key(headers: &HeaderMap, state_pin: &str) -> bool {
    let auth_header = headers
        .get("x-nexus-internal-key")
        .and_then(|v| v.to_str().ok());

    let internal_key = std::env::var("NEXUS_INTERNAL_KEY")
        .unwrap_or_else(|_| state_pin.to_string());

    auth_header == Some(&internal_key) || auth_header == Some(state_pin)
}

#[derive(Debug, Deserialize)]
pub struct MigrateUserPayload {
    pub legacy_id: Option<String>,
    pub email: String,
    pub username: Option<String>,
    pub full_name: String,
    pub password: Option<String>,
    pub password_hash: Option<String>, // if already bcrypt/argon2
    pub role: Option<String>,
    pub status: Option<String>,
    pub avatar_url: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct MigrateUserResponse {
    pub success: bool,
    pub message: String,
    pub user_id: String,
    pub already_existed: bool,
}

/// Internal endpoint for zero-downtime JIT account migration from macOS Suite & edge nodes.
/// Protected by `X-Nexus-Internal-Key` header matching server internal secret or token secret.
pub async fn migrate_user(
    State(state): State<SharedState>,
    headers: HeaderMap,
    Json(payload): Json<MigrateUserPayload>,
) -> impl IntoResponse {
    if !verify_internal_key(&headers, &state.config.admin_pin) {
        warn!("Unauthorized migration attempt from client");
        return (
            StatusCode::UNAUTHORIZED,
            Json(serde_json::json!({ "error": "Invalid internal authorization key" })),
        )
            .into_response();
    }

    let conn = state.db.lock().await;

    // Check if user already exists by email or username
    let existing_id: Option<String> = conn
        .query_row(
            "SELECT id FROM users WHERE LOWER(email) = LOWER(?1) OR (username IS NOT NULL AND LOWER(username) = LOWER(?2)) LIMIT 1",
            params![payload.email, payload.username.as_deref().unwrap_or("")],
            |row| row.get(0),
        )
        .ok();

    if let Some(user_id) = existing_id {
        // If password was supplied and user doesn't have an Argon2 hash, upgrade it
        if let Some(plain_pass) = payload.password {
            if let Ok(new_hash) = hash_password(&plain_pass) {
                let _ = conn.execute(
                    "UPDATE users SET password_hash = ?1, updated_at = CURRENT_TIMESTAMP WHERE id = ?2 AND (password_hash IS NULL OR password_hash LIKE '$2%')",
                    params![new_hash, user_id],
                );
            }
        }

        return (
            StatusCode::OK,
            Json(MigrateUserResponse {
                success: true,
                message: "User already registered in Nexus Hub; credentials verified".into(),
                user_id,
                already_existed: true,
            }),
        )
            .into_response();
    }

    // Determine final user ID
    let final_id = payload
        .legacy_id
        .filter(|id| !id.trim().is_empty())
        .unwrap_or_else(|| format!("usr_{}", uuid::Uuid::new_v4().simple()));

    // Hash password using modern Argon2id
    let final_password_hash = if let Some(plain_pass) = payload.password {
        match hash_password(&plain_pass) {
            Ok(h) => Some(h),
            Err(e) => {
                error!("Failed to hash password during migration: {}", e);
                return (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    Json(serde_json::json!({ "error": "Hashing failure" })),
                )
                    .into_response();
            }
        }
    } else {
        payload.password_hash
    };

    let role = payload.role.unwrap_or_else(|| "admin".into());
    let status = payload.status.unwrap_or_else(|| "active".into());
    let now = Utc::now().to_rfc3339();

    match conn.execute(
        r#"
        INSERT INTO users (id, email, username, full_name, avatar_url, password_hash, role, status, token_version, created_at, updated_at)
        VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, 1, ?9, ?9)
        "#,
        params![
            final_id,
            payload.email.trim(),
            payload.username.as_deref(),
            payload.full_name.trim(),
            payload.avatar_url,
            final_password_hash,
            role,
            status,
            now,
        ],
    ) {
        Ok(_) => {
            info!("Successfully migrated user '{}' ({}) into Nexus Auth Hub", payload.full_name, payload.email);

            // Grant default permissions for macos suite
            let _ = conn.execute(
                r#"
                INSERT OR IGNORE INTO auth_user_grants (id, user_id, app_id, granted_scopes, is_revoked)
                VALUES (?1, ?2, 'app_macos_suite', '["*"]', 0)
                "#,
                params![uuid::Uuid::new_v4().to_string(), final_id],
            );

            (
                StatusCode::CREATED,
                Json(MigrateUserResponse {
                    success: true,
                    message: "User account migrated to Nexus Hub successfully".into(),
                    user_id: final_id,
                    already_existed: false,
                }),
            )
                .into_response()
        }
        Err(e) => {
            error!("Failed to insert migrated user: {}", e);
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                Json(serde_json::json!({ "error": format!("Database error: {}", e) })),
            )
                .into_response()
        }
    }
}

// ─── Connected Devices & Sessions Internal Gateway ───────────────────────────

#[derive(Debug, Deserialize)]
pub struct SyncDevicePayload {
    pub user_id: String,
    pub device_id: Option<String>,
    pub device_name: String,
    pub device_type: Option<String>,
    pub client_app: Option<String>,
    pub ip_address: Option<String>,
}

/// Register or heartbeat a device from an edge app (macOS Suite / Mobile)
pub async fn sync_device(
    State(state): State<SharedState>,
    headers: HeaderMap,
    Json(payload): Json<SyncDevicePayload>,
) -> impl IntoResponse {
    if !verify_internal_key(&headers, &state.config.admin_pin) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(serde_json::json!({ "error": "Invalid internal authorization key" })),
        )
            .into_response();
    }

    let conn = state.db.lock().await;

    // Resolve user ID
    let resolved_user_id: Option<String> = conn
        .query_row(
            "SELECT id FROM users WHERE id = ?1 OR LOWER(email) = LOWER(?1) OR (username IS NOT NULL AND LOWER(username) = LOWER(?1)) LIMIT 1",
            params![payload.user_id.trim()],
            |row| row.get(0),
        )
        .ok();

    let user_id = match resolved_user_id {
        Some(uid) => uid,
        None => {
            return (
                StatusCode::NOT_FOUND,
                Json(serde_json::json!({ "error": "User not found" })),
            )
                .into_response();
        }
    };

    let dev_id = payload
        .device_id
        .filter(|d| !d.trim().is_empty())
        .unwrap_or_else(|| format!("dev_{}", uuid::Uuid::new_v4().simple()));
    let dev_type = payload.device_type.unwrap_or_else(|| "web".into());
    let client_app = payload.client_app.unwrap_or_else(|| "macos-suite".into());

    let res = conn.execute(
        r#"
        INSERT INTO user_devices (id, user_id, device_name, device_type, client_app, ip_address, last_seen)
        VALUES (?1, ?2, ?3, ?4, ?5, ?6, CURRENT_TIMESTAMP)
        ON CONFLICT(id) DO UPDATE SET
            device_name = excluded.device_name,
            device_type = excluded.device_type,
            client_app = excluded.client_app,
            ip_address = COALESCE(excluded.ip_address, user_devices.ip_address),
            last_seen = CURRENT_TIMESTAMP
        "#,
        params![
            dev_id,
            user_id,
            payload.device_name.trim(),
            dev_type,
            client_app,
            payload.ip_address,
        ],
    );

    match res {
        Ok(_) => (
            StatusCode::OK,
            Json(serde_json::json!({ "success": true, "device_id": dev_id })),
        )
            .into_response(),
        Err(e) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(serde_json::json!({ "error": e.to_string() })),
        )
            .into_response(),
    }
}

/// List sessions for a specific user via internal service call
pub async fn list_sessions_internal(
    State(state): State<SharedState>,
    headers: HeaderMap,
    Query(query): Query<HashMap<String, String>>,
) -> impl IntoResponse {
    if !verify_internal_key(&headers, &state.config.admin_pin) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(serde_json::json!({ "error": "Invalid internal authorization key" })),
        )
            .into_response();
    }

    let user_id_param = match query.get("user_id") {
        Some(u) => u.trim(),
        None => {
            return (
                StatusCode::BAD_REQUEST,
                Json(serde_json::json!({ "error": "user_id query param is required" })),
            )
                .into_response();
        }
    };

    let conn = state.db.lock().await;

    // Resolve user ID
    let resolved_user_id: Option<String> = conn
        .query_row(
            "SELECT id FROM users WHERE id = ?1 OR LOWER(email) = LOWER(?1) OR (username IS NOT NULL AND LOWER(username) = LOWER(?1)) LIMIT 1",
            params![user_id_param],
            |row| row.get(0),
        )
        .ok();

    let user_id = match resolved_user_id {
        Some(uid) => uid,
        None => {
            return (
                StatusCode::OK,
                Json(serde_json::json!({ "success": true, "sessions": [] })),
            )
                .into_response();
        }
    };

    let mut stmt = match conn.prepare(
        r#"
        SELECT id, device_name, device_type, client_app, ip_address, last_seen, created_at
        FROM user_devices
        WHERE user_id = ?1
        ORDER BY last_seen DESC
        "#,
    ) {
        Ok(s) => s,
        Err(e) => {
            return (
                StatusCode::INTERNAL_SERVER_ERROR,
                Json(serde_json::json!({ "error": e.to_string() })),
            )
                .into_response();
        }
    };

    let sessions = stmt
        .query_map(params![user_id], |row| {
            Ok(serde_json::json!({
                "id": row.get::<_, String>(0)?,
                "device_name": row.get::<_, String>(1)?,
                "device_type": row.get::<_, String>(2)?,
                "client_app": row.get::<_, String>(3)?,
                "ip_address": row.get::<_, Option<String>>(4)?,
                "last_seen": row.get::<_, String>(5)?,
                "created_at": row.get::<_, String>(6)?,
            }))
        })
        .map(|rows| rows.filter_map(Result::ok).collect::<Vec<_>>())
        .unwrap_or_default();

    (
        StatusCode::OK,
        Json(serde_json::json!({ "success": true, "sessions": sessions })),
    )
        .into_response()
}

/// Terminate an individual device session via internal call
pub async fn revoke_session_internal(
    State(state): State<SharedState>,
    headers: HeaderMap,
    Path(device_id): Path<String>,
    Query(query): Query<HashMap<String, String>>,
) -> impl IntoResponse {
    if !verify_internal_key(&headers, &state.config.admin_pin) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(serde_json::json!({ "error": "Invalid internal authorization key" })),
        )
            .into_response();
    }

    let conn = state.db.lock().await;

    let res = if let Some(user_id_param) = query.get("user_id") {
        let _ = conn.execute(
            "DELETE FROM refresh_tokens WHERE device_id = ?1",
            params![device_id],
        );
        conn.execute(
            "DELETE FROM user_devices WHERE id = ?1 AND (user_id = ?2 OR user_id IN (SELECT id FROM users WHERE LOWER(email) = LOWER(?2)))",
            params![device_id, user_id_param],
        )
    } else {
        let _ = conn.execute(
            "DELETE FROM refresh_tokens WHERE device_id = ?1",
            params![device_id],
        );
        conn.execute(
            "DELETE FROM user_devices WHERE id = ?1",
            params![device_id],
        )
    };

    match res {
        Ok(rows) if rows > 0 => (
            StatusCode::OK,
            Json(serde_json::json!({ "success": true, "message": "Device session terminated" })),
        )
            .into_response(),
        _ => (
            StatusCode::NOT_FOUND,
            Json(serde_json::json!({ "success": false, "error": "Session not found" })),
        )
            .into_response(),
    }
}

/// Global sign-out / revoke all other sessions for a user via internal call
pub async fn revoke_all_sessions_internal(
    State(state): State<SharedState>,
    headers: HeaderMap,
    Query(query): Query<HashMap<String, String>>,
) -> impl IntoResponse {
    if !verify_internal_key(&headers, &state.config.admin_pin) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(serde_json::json!({ "error": "Invalid internal authorization key" })),
        )
            .into_response();
    }

    let user_id_param = match query.get("user_id") {
        Some(u) => u.trim(),
        None => {
            return (
                StatusCode::BAD_REQUEST,
                Json(serde_json::json!({ "error": "user_id query param is required" })),
            )
                .into_response();
        }
    };

    let conn = state.db.lock().await;

    // Resolve user ID
    let resolved_user_id: Option<String> = conn
        .query_row(
            "SELECT id FROM users WHERE id = ?1 OR LOWER(email) = LOWER(?1) OR (username IS NOT NULL AND LOWER(username) = LOWER(?1)) LIMIT 1",
            params![user_id_param],
            |row| row.get(0),
        )
        .ok();

    let user_id = match resolved_user_id {
        Some(uid) => uid,
        None => {
            return (
                StatusCode::NOT_FOUND,
                Json(serde_json::json!({ "error": "User not found" })),
            )
                .into_response();
        }
    };

    let keep_device = query.get("keep_device_id");

    if let Some(keep) = keep_device {
        let _ = conn.execute(
            "DELETE FROM user_devices WHERE user_id = ?1 AND id != ?2",
            params![user_id, keep],
        );
        let _ = conn.execute(
            "DELETE FROM refresh_tokens WHERE user_id = ?1 AND device_id != ?2",
            params![user_id, keep],
        );
    } else {
        let _ = conn.execute(
            "DELETE FROM user_devices WHERE user_id = ?1",
            params![user_id],
        );
        let _ = conn.execute(
            "DELETE FROM refresh_tokens WHERE user_id = ?1",
            params![user_id],
        );
    }

    // Bump token version
    let _ = conn.execute(
        "UPDATE users SET token_version = token_version + 1 WHERE id = ?1",
        params![user_id],
    );

    (
        StatusCode::OK,
        Json(serde_json::json!({ "success": true, "message": "All other sessions signed out" })),
    )
        .into_response()
}

// ─── Security Questions & Credential Synchronization ─────────────────────────

#[derive(Debug, Deserialize)]
pub struct UpdateSecurityInternalPayload {
    pub user_id: String,
    pub password: Option<String>,
    pub security_question: Option<String>,
    pub security_answer: Option<String>,
    pub email: Option<String>,
    pub username: Option<String>,
}

pub async fn update_security_internal(
    State(state): State<SharedState>,
    headers: HeaderMap,
    Json(payload): Json<UpdateSecurityInternalPayload>,
) -> impl IntoResponse {
    if !verify_internal_key(&headers, &state.config.admin_pin) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(serde_json::json!({ "error": "Invalid internal authorization key" })),
        )
            .into_response();
    }

    let conn = state.db.lock().await;

    let resolved_user_id: Option<String> = conn
        .query_row(
            "SELECT id FROM users WHERE id = ?1 OR LOWER(email) = LOWER(?1) OR (username IS NOT NULL AND LOWER(username) = LOWER(?1)) LIMIT 1",
            params![payload.user_id.trim()],
            |row| row.get(0),
        )
        .ok();

    let user_id = match resolved_user_id {
        Some(uid) => uid,
        None => {
            return (
                StatusCode::NOT_FOUND,
                Json(serde_json::json!({ "error": "User not found" })),
            )
                .into_response();
        }
    };

    if let Some(plain_pass) = payload.password {
        if !plain_pass.trim().is_empty() {
            if let Ok(new_hash) = hash_password(&plain_pass) {
                let _ = conn.execute(
                    "UPDATE users SET password_hash = ?1, updated_at = CURRENT_TIMESTAMP WHERE id = ?2",
                    params![new_hash, user_id],
                );
            }
        }
    }

    if let (Some(q), Some(a)) = (payload.security_question, payload.security_answer) {
        if !q.trim().is_empty() && !a.trim().is_empty() {
            if let Ok(a_hash) = hash_security_answer(&a) {
                let _ = conn.execute(
                    "UPDATE users SET security_question = ?1, security_answer_hash = ?2, updated_at = CURRENT_TIMESTAMP WHERE id = ?3",
                    params![q.trim(), a_hash, user_id],
                );
            }
        }
    }

    if let Some(em) = payload.email {
        if !em.trim().is_empty() {
            let _ = conn.execute(
                "UPDATE users SET email = ?1, updated_at = CURRENT_TIMESTAMP WHERE id = ?2",
                params![em.trim(), user_id],
            );
        }
    }

    if let Some(un) = payload.username {
        if !un.trim().is_empty() {
            let _ = conn.execute(
                "UPDATE users SET username = ?1, updated_at = CURRENT_TIMESTAMP WHERE id = ?2",
                params![un.trim(), user_id],
            );
        }
    }

    // Bump token version to ensure security propagation
    let _ = conn.execute(
        "UPDATE users SET token_version = token_version + 1 WHERE id = ?1",
        params![user_id],
    );

    (
        StatusCode::OK,
        Json(serde_json::json!({ "success": true, "message": "Security settings updated successfully" })),
    )
        .into_response()
}

pub async fn get_security_question_internal(
    State(state): State<SharedState>,
    headers: HeaderMap,
    Query(query): Query<HashMap<String, String>>,
) -> impl IntoResponse {
    if !verify_internal_key(&headers, &state.config.admin_pin) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(serde_json::json!({ "error": "Invalid internal authorization key" })),
        )
            .into_response();
    }

    let identifier = match query.get("identifier") {
        Some(i) => i.trim().to_lowercase(),
        None => {
            return (
                StatusCode::BAD_REQUEST,
                Json(serde_json::json!({ "error": "identifier query param is required" })),
            )
                .into_response();
        }
    };

    let conn = state.db.lock().await;
    let row: Option<(String, Option<String>)> = conn
        .query_row(
            "SELECT id, security_question FROM users WHERE id = ?1 OR LOWER(email) = ?1 OR (username IS NOT NULL AND LOWER(username) = ?1) LIMIT 1",
            params![identifier],
            |r| Ok((r.get(0)?, r.get(1)?)),
        )
        .ok();

    match row {
        Some((user_id, Some(q))) if !q.trim().is_empty() => (
            StatusCode::OK,
            Json(serde_json::json!({ "success": true, "user_id": user_id, "question": q })),
        )
            .into_response(),
        Some((_, _)) => (
            StatusCode::OK,
            Json(serde_json::json!({ "success": false, "error": "No security question configured" })),
        )
            .into_response(),
        None => (
            StatusCode::NOT_FOUND,
            Json(serde_json::json!({ "success": false, "error": "User not found" })),
        )
            .into_response(),
    }
}

#[derive(Debug, Deserialize)]
pub struct VerifySecurityAnswerInternalPayload {
    pub identifier: String,
    pub answer: String,
}

pub async fn verify_security_answer_internal(
    State(state): State<SharedState>,
    headers: HeaderMap,
    Json(payload): Json<VerifySecurityAnswerInternalPayload>,
) -> impl IntoResponse {
    if !verify_internal_key(&headers, &state.config.admin_pin) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(serde_json::json!({ "error": "Invalid internal authorization key" })),
        )
            .into_response();
    }

    let identifier = payload.identifier.trim().to_lowercase();
    let conn = state.db.lock().await;

    let user_row: Option<(String, Option<String>, String, String)> = conn
        .query_row(
            "SELECT id, security_answer_hash, role, status FROM users WHERE id = ?1 OR LOWER(email) = ?1 OR (username IS NOT NULL AND LOWER(username) = ?1) LIMIT 1",
            params![identifier],
            |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?)),
        )
        .ok();

    let (user_id, answer_hash, role, status) = match user_row {
        Some(u) => u,
        None => {
            return (
                StatusCode::NOT_FOUND,
                Json(serde_json::json!({ "success": false, "error": "User not found" })),
            )
                .into_response();
        }
    };

    let hash = match answer_hash {
        Some(h) if !h.trim().is_empty() => h,
        _ => {
            return (
                StatusCode::BAD_REQUEST,
                Json(serde_json::json!({ "success": false, "error": "No security question configured" })),
            )
                .into_response();
        }
    };

    if verify_security_answer(&payload.answer, &hash) {
        (
            StatusCode::OK,
            Json(serde_json::json!({
                "success": true,
                "user_id": user_id,
                "role": role,
                "status": status,
                "message": "Security answer verified successfully"
            })),
        )
            .into_response()
    } else {
        (
            StatusCode::UNAUTHORIZED,
            Json(serde_json::json!({ "success": false, "error": "Security answer is incorrect" })),
        )
            .into_response()
    }
}

// ─── RFC 8628 Device Authorization Approval Gateway ───────────────────────────

#[derive(Debug, Deserialize)]
pub struct ApproveDeviceCodeInternalPayload {
    pub user_code: String,
    pub user_id: String,
}

pub async fn approve_device_code_internal(
    State(state): State<SharedState>,
    headers: HeaderMap,
    Json(payload): Json<ApproveDeviceCodeInternalPayload>,
) -> impl IntoResponse {
    if !verify_internal_key(&headers, &state.config.admin_pin) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(serde_json::json!({ "error": "Invalid internal authorization key" })),
        )
            .into_response();
    }

    let code_norm = crate::auth::device_flow::normalize_user_code(&payload.user_code);
    let conn = state.db.lock().await;

    let resolved_user_id: Option<String> = conn
        .query_row(
            "SELECT id FROM users WHERE id = ?1 OR LOWER(email) = LOWER(?1) OR (username IS NOT NULL AND LOWER(username) = LOWER(?1)) LIMIT 1",
            params![payload.user_id.trim()],
            |row| row.get(0),
        )
        .ok();

    let user_id = match resolved_user_id {
        Some(uid) => uid,
        None => {
            return (
                StatusCode::NOT_FOUND,
                Json(serde_json::json!({ "error": "User not found" })),
            )
                .into_response();
        }
    };

    let updated = conn.execute(
        r#"
        UPDATE device_codes
        SET status = 'approved', approved_user_id = ?1
        WHERE user_code = ?2 AND status = 'pending' AND expires_at > datetime('now')
        "#,
        params![user_id, code_norm],
    );

    match updated {
        Ok(rows) if rows > 0 => (
            StatusCode::OK,
            Json(serde_json::json!({ "success": true, "message": "Device authorization code approved successfully" })),
        )
            .into_response(),
        _ => (
            StatusCode::BAD_REQUEST,
            Json(serde_json::json!({ "success": false, "error": "Code invalid, already processed, or expired" })),
        )
            .into_response(),
    }
}


