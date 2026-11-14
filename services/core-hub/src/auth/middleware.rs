use crate::auth::crypto::{sha256_hash, verify_jwt};
use crate::state::SharedState;
use axum::{
    async_trait,
    extract::FromRequestParts,
    http::{request::Parts, StatusCode},
    response::{IntoResponse, Response},
    Json,
};
use rusqlite::params;
use serde::Serialize;
use tracing::warn;

#[derive(Debug, Clone, Serialize)]
pub struct AuthenticatedUser {
    pub user_id: String,
    pub email: String,
    pub name: String,
    pub roles: Vec<String>,
    pub is_admin: bool,
    pub device_id: Option<String>,
}

#[async_trait]
impl FromRequestParts<SharedState> for AuthenticatedUser {
    type Rejection = Response;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &SharedState,
    ) -> Result<Self, Self::Rejection> {
        let auth_header = parts
            .headers
            .get("Authorization")
            .and_then(|h| h.to_str().ok());

        let cookie_token = parts
            .headers
            .get("Cookie")
            .and_then(|c| c.to_str().ok())
            .and_then(|cookie_str| {
                cookie_str.split(';').find_map(|s| {
                    let mut parts = s.trim().splitn(2, '=');
                    let name = parts.next()?;
                    let val = parts.next()?;
                    if name == "auth_token" {
                        Some(val)
                    } else {
                        None
                    }
                })
            });

        // 1. Check Bearer token or Cookie
        let raw_token = if let Some(header) = auth_header {
            if let Some(stripped) = header.strip_prefix("Bearer ") {
                stripped.trim()
            } else {
                header.trim()
            }
        } else if let Some(cookie) = cookie_token {
            cookie.trim()
        } else {
            return Err((
                StatusCode::UNAUTHORIZED,
                Json(serde_json::json!({
                    "success": false,
                    "error": "Missing authorization token"
                })),
            )
                .into_response());
        };

        // 2. Machine Personal Access Token (PAT) authentication (nex_live_...)
        if raw_token.starts_with("nex_live_") {
            let token_hash = sha256_hash(raw_token);
            let conn = state.db.lock().await;

            let user_record = conn.query_row(
                r#"
                SELECT u.id, u.email, u.full_name, u.role, u.status
                FROM api_tokens t
                JOIN users u ON u.id = t.user_id
                WHERE t.token_hash = ?1 AND (t.expires_at IS NULL OR t.expires_at > CURRENT_TIMESTAMP)
                "#,
                params![token_hash],
                |row| {
                    Ok((
                        row.get::<_, String>(0)?,
                        row.get::<_, String>(1)?,
                        row.get::<_, String>(2)?,
                        row.get::<_, String>(3)?,
                        row.get::<_, String>(4)?,
                    ))
                },
            );

            match user_record {
                Ok((id, email, name, role, status)) => {
                    if status != "active" {
                        return Err((
                            StatusCode::FORBIDDEN,
                            Json(serde_json::json!({
                                "success": false,
                                "error": "Account is suspended or inactive"
                            })),
                        )
                            .into_response());
                    }

                    // Update last used timestamp
                    let _ = conn.execute(
                        "UPDATE api_tokens SET last_used_at = CURRENT_TIMESTAMP WHERE token_hash = ?1",
                        params![token_hash],
                    );

                    let is_admin = role == "owner" || role == "admin";
                    return Ok(AuthenticatedUser {
                        user_id: id,
                        email,
                        name,
                        roles: vec![role],
                        is_admin,
                        device_id: None,
                    });
                }
                Err(_) => {
                    return Err((
                        StatusCode::UNAUTHORIZED,
                        Json(serde_json::json!({
                            "success": false,
                            "error": "Invalid or expired Personal Access Token"
                        })),
                    )
                        .into_response());
                }
            }
        }

        // 3. JWT Verification
        let jwt_secret = state.config.admin_pin.as_bytes(); // Fallback / or DB settings
        let claims = match verify_jwt(raw_token, jwt_secret) {
            Ok(c) => c,
            Err(err) => {
                warn!("JWT validation failed: {}", err);
                return Err((
                    StatusCode::UNAUTHORIZED,
                    Json(serde_json::json!({
                        "success": false,
                        "error": "Invalid or expired session token"
                    })),
                )
                    .into_response());
            }
        };

        // 4. Verify user exists, is active, and token_version matches
        let conn = state.db.lock().await;
        let db_check = conn.query_row(
            "SELECT role, status, token_version FROM users WHERE id = ?1",
            params![claims.sub],
            |row| {
                Ok((
                    row.get::<_, String>(0)?,
                    row.get::<_, String>(1)?,
                    row.get::<_, i64>(2)?,
                ))
            },
        );

        match db_check {
            Ok((role, status, version)) => {
                if status != "active" {
                    return Err((
                        StatusCode::FORBIDDEN,
                        Json(serde_json::json!({
                            "success": false,
                            "error": "Account is inactive"
                        })),
                    )
                        .into_response());
                }
                if claims.token_version != version {
                    return Err((
                        StatusCode::UNAUTHORIZED,
                        Json(serde_json::json!({
                            "success": false,
                            "error": "Session revoked; please sign in again"
                        })),
                    )
                        .into_response());
                }

                let is_admin = role == "owner" || role == "admin" || claims.roles.contains(&"admin".to_string());
                Ok(AuthenticatedUser {
                    user_id: claims.sub,
                    email: claims.email,
                    name: claims.name,
                    roles: claims.roles,
                    is_admin,
                    device_id: claims.device_id,
                })
            }
            Err(_) => Err((
                StatusCode::UNAUTHORIZED,
                Json(serde_json::json!({
                    "success": false,
                    "error": "User account no longer exists"
                })),
            )
                .into_response()),
        }
    }
}
