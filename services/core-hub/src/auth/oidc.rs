use crate::auth::crypto::{sha256_hash, sign_jwt, verify_jwt, TokenClaims};
use crate::state::SharedState;
use axum::{
    extract::{Query, State},
    http::{HeaderMap, StatusCode},
    response::{IntoResponse, Redirect},
    Form, Json,
};
use chrono::Utc;
use rusqlite::params;
use serde::Deserialize;
use tracing::{error, info, warn};

fn url_encode(s: &str) -> String {
    let mut encoded = String::with_capacity(s.len());
    for b in s.bytes() {
        match b {
            b'a'..=b'z' | b'A'..=b'Z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                encoded.push(b as char);
            }
            _ => {
                encoded.push_str(&format!("%{:02X}", b));
            }
        }
    }
    encoded
}

/// Standard OpenID Connect Discovery document (.well-known/openid-configuration)
pub async fn get_openid_configuration() -> impl IntoResponse {
    let base_url = std::env::var("NEXUS_PUBLIC_URL").unwrap_or_else(|_| "https://faizal.uk".into());

    Json(serde_json::json!({
        "issuer": base_url,
        "authorization_endpoint": format!("{}/oauth/authorize", base_url),
        "token_endpoint": format!("{}/oauth/token", base_url),
        "userinfo_endpoint": format!("{}/oauth/userinfo", base_url),
        "jwks_uri": format!("{}/.well-known/jwks.json", base_url),
        "response_types_supported": ["code", "token", "id_token"],
        "subject_types_supported": ["public"],
        "id_token_signing_alg_values_supported": ["HS256"],
        "scopes_supported": ["openid", "email", "profile", "mail:send", "offline_access"],
        "token_endpoint_auth_methods_supported": ["client_secret_basic", "client_secret_post", "none"],
        "claims_supported": [
            "sub",
            "iss",
            "aud",
            "exp",
            "iat",
            "email",
            "email_verified",
            "name",
            "preferred_username"
        ]
    }))
}

/// Standard JWKS endpoint (.well-known/jwks.json)
pub async fn get_jwks() -> impl IntoResponse {
    Json(serde_json::json!({
        "keys": [
            {
                "kty": "oct",
                "use": "sig",
                "alg": "HS256",
                "kid": "nexus-core-key-1"
            }
        ]
    }))
}

#[derive(Debug, Deserialize)]
pub struct OidcAuthorizeQuery {
    pub client_id: Option<String>,
    pub redirect_uri: Option<String>,
    pub response_type: Option<String>,
    pub scope: Option<String>,
    pub state: Option<String>,
    pub code_challenge: Option<String>,
    pub code_challenge_method: Option<String>,
}

/// OAuth 2.0 / OIDC Authorization Endpoint (GET /oauth/authorize)
pub async fn oauth_authorize(
    State(state): State<SharedState>,
    Query(query): Query<OidcAuthorizeQuery>,
) -> impl IntoResponse {
    let client_id = match &query.client_id {
        Some(cid) => cid.trim(),
        None => {
            return (
                StatusCode::BAD_REQUEST,
                Json(serde_json::json!({ "error": "invalid_request", "error_description": "client_id is required" })),
            )
                .into_response();
        }
    };

    let redirect_uri = match &query.redirect_uri {
        Some(r) => r.trim(),
        None => {
            return (
                StatusCode::BAD_REQUEST,
                Json(serde_json::json!({ "error": "invalid_request", "error_description": "redirect_uri is required" })),
            )
                .into_response();
        }
    };

    let conn = state.db.lock().await;

    // Validate registered app by id or slug
    let app: Option<(String, String, String)> = conn
        .query_row(
            "SELECT id, slug, allowed_redirect_uris FROM auth_registered_apps WHERE (id = ?1 OR slug = ?1) AND is_enabled = 1 LIMIT 1",
            params![client_id],
            |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)),
        )
        .ok();

    let (app_id, _app_slug, allowed_uris_raw) = match app {
        Some(a) => a,
        None => {
            warn!("OAuth authorization failed: unknown client_id '{}'", client_id);
            return (
                StatusCode::UNAUTHORIZED,
                Json(serde_json::json!({ "error": "unauthorized_client", "error_description": "Client app is not registered or disabled" })),
            )
                .into_response();
        }
    };

    // Verify redirect_uri is permitted
    let allowed_uris: Vec<String> = serde_json::from_str(&allowed_uris_raw).unwrap_or_default();
    let is_permitted = allowed_uris.is_empty()
        || allowed_uris.iter().any(|u| redirect_uri.starts_with(u) || u == "*");

    if !is_permitted {
        warn!("OAuth authorization failed: redirect_uri '{}' not allowed for app '{}'", redirect_uri, app_id);
        return (
            StatusCode::BAD_REQUEST,
            Json(serde_json::json!({ "error": "invalid_request", "error_description": "redirect_uri is not authorized for this client" })),
        )
            .into_response();
    }

    // Default to canonical owner account
    let owner_email: String = conn
        .query_row(
            "SELECT email FROM users WHERE role = 'owner' AND status = 'active' LIMIT 1",
            [],
            |r| r.get(0),
        )
        .unwrap_or_else(|_| "fazztronic@gmail.com".into());

    let auth_code = format!("code_{}", uuid::Uuid::new_v4().simple());
    let code_hash = sha256_hash(&auth_code);
    let expires_at = (Utc::now() + chrono::Duration::minutes(10)).to_rfc3339();

    let meta = serde_json::json!({
        "client_id": app_id,
        "redirect_uri": redirect_uri,
        "scope": query.scope.unwrap_or_else(|| "openid profile email".into()),
        "code_challenge": query.code_challenge,
        "code_challenge_method": query.code_challenge_method,
    })
    .to_string();

    let _ = conn.execute(
        r#"
        INSERT INTO auth_codes (id, email, code_hash, code_type, metadata, expires_at)
        VALUES (?1, ?2, ?3, 'oauth_code', ?4, ?5)
        "#,
        params![auth_code, owner_email, code_hash, meta, expires_at],
    );

    let state_param = query.state.map(|s| format!("&state={}", url_encode(&s))).unwrap_or_default();
    let separator = if redirect_uri.contains('?') { "&" } else { "?" };
    let destination = format!("{}{}{}code={}{}", redirect_uri, separator, "", auth_code, state_param);

    info!("OAuth authorization code issued for app '{}' -> redirecting to {}", app_id, destination);
    Redirect::to(&destination).into_response()
}

#[derive(Debug, Deserialize)]
pub struct OidcTokenRequest {
    pub grant_type: String,
    pub code: Option<String>,
    pub client_id: Option<String>,
    pub client_secret: Option<String>,
    pub redirect_uri: Option<String>,
    pub code_verifier: Option<String>,
    pub refresh_token: Option<String>,
}

/// OAuth 2.0 / OIDC Token Endpoint (POST /oauth/token)
pub async fn oauth_token(
    State(state): State<SharedState>,
    _headers: HeaderMap,
    Form(payload): Form<OidcTokenRequest>,
) -> impl IntoResponse {
    let grant_type = payload.grant_type.trim();

    if grant_type == "authorization_code" {
        let code = match &payload.code {
            Some(c) => c.trim(),
            None => {
                return (
                    StatusCode::BAD_REQUEST,
                    Json(serde_json::json!({ "error": "invalid_request", "error_description": "code is required" })),
                )
                    .into_response();
            }
        };

        let conn = state.db.lock().await;

        let code_record: Option<(String, String, String, String)> = conn
            .query_row(
                r#"
                SELECT id, email, metadata, expires_at
                FROM auth_codes
                WHERE id = ?1 AND code_type = 'oauth_code' AND used_at IS NULL AND datetime(expires_at) > datetime('now')
                LIMIT 1
                "#,
                params![code],
                |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?)),
            )
            .ok();

        let (code_id, user_email, _metadata_raw, _) = match code_record {
            Some(rec) => rec,
            None => {
                return (
                    StatusCode::BAD_REQUEST,
                    Json(serde_json::json!({ "error": "invalid_grant", "error_description": "Code is invalid, expired, or already used" })),
                )
                    .into_response();
            }
        };

        // Mark code as used
        let _ = conn.execute(
            "UPDATE auth_codes SET used_at = CURRENT_TIMESTAMP WHERE id = ?1",
            params![code_id],
        );

        // Fetch user record
        let user: Option<(String, String, String, String, i64)> = conn
            .query_row(
                "SELECT id, email, username, full_name, token_version FROM users WHERE LOWER(email) = LOWER(?1) LIMIT 1",
                params![user_email],
                |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?, r.get(4)?)),
            )
            .ok();

        let (user_id, email, _username, full_name, token_version) = match user {
            Some(u) => u,
            None => {
                return (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    Json(serde_json::json!({ "error": "server_error", "error_description": "User associated with code not found" })),
                )
                    .into_response();
            }
        };

        let now = Utc::now().timestamp() as usize;
        let claims = TokenClaims {
            sub: user_id.clone(),
            email: email.clone(),
            name: full_name.clone(),
            roles: vec!["owner".into(), "admin".into()],
            token_version,
            device_id: Some("stalwart_mail_sso".into()),
            exp: now + (30 * 86400),
            iat: now,
            jti: uuid::Uuid::new_v4().to_string(),
        };

        let secret = state.config.admin_pin.as_bytes();
        let access_token = match sign_jwt(&claims, secret) {
            Ok(t) => t,
            Err(e) => {
                error!("Failed to sign OIDC access token: {}", e);
                return (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    Json(serde_json::json!({ "error": "server_error", "error_description": "JWT signing failed" })),
                )
                    .into_response();
            }
        };

        let id_token = access_token.clone();
        let refresh_token = format!("rt_sso_{}", uuid::Uuid::new_v4().simple());

        // Save refresh token
        let _ = conn.execute(
            r#"
            INSERT INTO refresh_tokens (id, user_id, device_id, family_id)
            VALUES (?1, ?2, 'stalwart_mail_sso', ?3)
            "#,
            params![refresh_token, user_id, uuid::Uuid::new_v4().to_string()],
        );

        return (
            StatusCode::OK,
            Json(serde_json::json!({
                "access_token": access_token,
                "token_type": "Bearer",
                "expires_in": 30 * 86400,
                "id_token": id_token,
                "refresh_token": refresh_token,
                "scope": "openid email profile mail:send"
            })),
        )
            .into_response();
    }

    if grant_type == "refresh_token" {
        let refresh_token = match &payload.refresh_token {
            Some(rt) => rt.trim(),
            None => {
                return (
                    StatusCode::BAD_REQUEST,
                    Json(serde_json::json!({ "error": "invalid_request", "error_description": "refresh_token is required" })),
                )
                    .into_response();
            }
        };

        let conn = state.db.lock().await;
        let user_id: Option<String> = conn
            .query_row(
                "SELECT user_id FROM refresh_tokens WHERE id = ?1 LIMIT 1",
                params![refresh_token],
                |r| r.get(0),
            )
            .ok();

        let uid = match user_id {
            Some(u) => u,
            None => {
                return (
                    StatusCode::BAD_REQUEST,
                    Json(serde_json::json!({ "error": "invalid_grant", "error_description": "Invalid refresh token" })),
                )
                    .into_response();
            }
        };

        let user: Option<(String, String, i64)> = conn
            .query_row(
                "SELECT email, full_name, token_version FROM users WHERE id = ?1 LIMIT 1",
                params![uid],
                |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)),
            )
            .ok();

        if let Some((email, full_name, token_version)) = user {
            let now = Utc::now().timestamp() as usize;
            let claims = TokenClaims {
                sub: uid.clone(),
                email,
                name: full_name,
                roles: vec!["owner".into(), "admin".into()],
                token_version,
                device_id: Some("stalwart_mail_sso".into()),
                exp: now + (30 * 86400),
                iat: now,
                jti: uuid::Uuid::new_v4().to_string(),
            };

            let secret = state.config.admin_pin.as_bytes();
            if let Ok(new_token) = sign_jwt(&claims, secret) {
                return (
                    StatusCode::OK,
                    Json(serde_json::json!({
                        "access_token": new_token,
                        "token_type": "Bearer",
                        "expires_in": 30 * 86400,
                        "scope": "openid email profile mail:send"
                    })),
                )
                    .into_response();
            }
        }
    }

    (
        StatusCode::BAD_REQUEST,
        Json(serde_json::json!({ "error": "unsupported_grant_type", "error_description": "Grant type not supported" })),
    )
        .into_response()
}

/// OIDC UserInfo Endpoint (GET/POST /oauth/userinfo)
pub async fn oauth_userinfo(
    State(state): State<SharedState>,
    headers: HeaderMap,
) -> impl IntoResponse {
    let auth_header = headers
        .get("authorization")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.strip_prefix("Bearer "));

    let token = match auth_header {
        Some(t) => t.trim(),
        None => {
            return (
                StatusCode::UNAUTHORIZED,
                Json(serde_json::json!({ "error": "invalid_token", "error_description": "Bearer token required" })),
            )
                .into_response();
        }
    };

    let secret = state.config.admin_pin.as_bytes();
    let claims = match verify_jwt(token, secret) {
        Ok(c) => c,
        Err(_) => {
            return (
                StatusCode::UNAUTHORIZED,
                Json(serde_json::json!({ "error": "invalid_token", "error_description": "Invalid or expired access token" })),
            )
                .into_response();
        }
    };

    let conn = state.db.lock().await;
    let user_row: Option<(String, String, Option<String>, String)> = conn
        .query_row(
            "SELECT id, email, username, full_name FROM users WHERE id = ?1 LIMIT 1",
            params![claims.sub],
            |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?)),
        )
        .ok();

    match user_row {
        Some((id, email, username, full_name)) => (
            StatusCode::OK,
            Json(serde_json::json!({
                "sub": id,
                "email": email,
                "email_verified": true,
                "name": full_name,
                "preferred_username": username.unwrap_or_else(|| email.clone())
            })),
        )
            .into_response(),
        None => (
            StatusCode::NOT_FOUND,
            Json(serde_json::json!({ "error": "user_not_found" })),
        )
            .into_response(),
    }
}
