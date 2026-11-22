use crate::auth::crypto::hash_password;
use crate::state::SharedState;
use axum::{
    extract::State,
    http::{HeaderMap, StatusCode},
    response::IntoResponse,
    Json,
};
use chrono::Utc;
use rusqlite::params;
use serde::{Deserialize, Serialize};
use tracing::{error, info, warn};

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
    let auth_header = headers
        .get("x-nexus-internal-key")
        .and_then(|v| v.to_str().ok());

    let internal_key = std::env::var("NEXUS_INTERNAL_KEY")
        .unwrap_or_else(|_| state.config.admin_pin.clone());

    if auth_header != Some(&internal_key) && auth_header != Some(state.config.admin_pin.as_str()) {
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
