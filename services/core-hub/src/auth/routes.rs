use crate::auth::crypto::{
    generate_otp_code, generate_pat_token, generate_random_token, hash_password,
    hash_security_answer, sha256_hash, sign_jwt, verify_password, verify_security_answer,
    TokenClaims,
};
use crate::auth::device_flow::{generate_user_code, normalize_user_code};
use crate::auth::email_flows::{send_magic_link_email, send_otp_email, send_reset_password_email};
use crate::auth::middleware::AuthenticatedUser;
use crate::auth::models::*;
use crate::auth::oauth::{verify_google_id_token, verify_meta_access_token};
use crate::auth::security_questions::{is_valid_question_key, STANDARD_SECURITY_QUESTIONS};
use crate::state::SharedState;
use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    response::IntoResponse,
    routing::{delete, get, post, put},
    Json, Router,
};
use chrono::Utc;
use rusqlite::params;
use serde::Deserialize;
use serde_json::json;
use tracing::{error, info, warn};

pub fn auth_router() -> Router<SharedState> {
    Router::new()
        // Core Identity
        .route("/register", post(register))
        .route("/login", post(login))
        .route("/me", get(get_current_user))
        .route("/logout", post(logout))
        .route("/keys", get(get_public_keys))
        // Passwordless Flows
        .route("/magic-link", post(request_magic_link))
        .route("/verify-magic-link", get(verify_magic_link))
        .route("/otp/send", post(send_otp))
        .route("/otp/verify", post(verify_otp))
        // Password Reset & Recovery
        .route("/password-reset/request", post(request_password_reset))
        .route("/password-reset/confirm", post(confirm_password_reset))
        // Security Questions
        .route("/security-questions", get(get_security_questions))
        .route("/security-questions/setup", post(setup_security_question))
        .route("/security-questions/recover", post(recover_with_security_question))
        // Social OAuth
        .route("/google", post(google_auth))
        .route("/meta", post(meta_auth))
        // RFC 8628 Device Flow (CLI)
        .route("/device/code", post(request_device_code))
        .route("/device/token", post(poll_device_token))
        .route("/device/approve", post(approve_device_code))
        // Personal Access Tokens (PATs)
        .route("/tokens", post(create_pat).get(list_pats))
        .route("/tokens/:id", delete(revoke_pat))
        // Ecosystem Applications Registry ("Where Nexus Account can be used")
        .route("/apps", get(list_registered_apps).post(register_app))
        .route("/apps/:id", put(update_registered_app).delete(delete_registered_app))
        // Connected Sessions & Devices ("Where you're signed in")
        .route("/sessions", get(list_user_sessions).delete(revoke_all_sessions))
        .route("/sessions/:id", delete(revoke_user_session))
        // User App Grants & Delegated Permissions
        .route("/grants", get(list_user_grants))
        .route("/grants/:app_id", delete(revoke_user_grant))
}

// ─── Token Issuance Helper ────────────────────────────────────────────────────

fn issue_user_session(
    user: &User,
    device_id: Option<String>,
    secret: &[u8],
) -> Result<String, String> {
    let now = Utc::now().timestamp() as usize;
    let claims = TokenClaims {
        sub: user.id.clone(),
        email: user.email.clone(),
        name: user.full_name.clone(),
        roles: vec![user.role.clone()],
        token_version: user.token_version,
        device_id,
        exp: now + (30 * 24 * 3600), // 30-day session
        iat: now,
        jti: uuid::Uuid::new_v4().to_string(),
    };
    sign_jwt(&claims, secret)
}

// ─── Core Authentication Handlers ─────────────────────────────────────────────

async fn register(
    State(state): State<SharedState>,
    Json(payload): Json<RegisterRequest>,
) -> impl IntoResponse {
    let email = payload.email.trim().to_lowercase();
    if !email.contains('@') {
        return (
            StatusCode::BAD_REQUEST,
            Json(json!({ "success": false, "error": "Invalid email address format" })),
        );
    }

    let password_hash = if let Some(pass) = payload.password {
        if pass.len() < 8 {
            return (
                StatusCode::BAD_REQUEST,
                Json(json!({ "success": false, "error": "Password must be at least 8 characters long" })),
            );
        }
        match hash_password(&pass) {
            Ok(h) => Some(h),
            Err(e) => return (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({ "success": false, "error": e }))),
        }
    } else {
        None
    };

    let user_id = format!("usr_{}", uuid::Uuid::new_v4().simple());
    let username = payload.username.map(|u| u.trim().to_lowercase());
    let full_name = payload.full_name.trim().to_string();

    let conn = state.db.lock().await;

    // Check if this is the very first user registered in system -> assign 'owner' role
    let existing_count: i64 = conn
        .query_row("SELECT COUNT(*) FROM users", [], |row| row.get(0))
        .unwrap_or(0);
    let role = if existing_count == 0 { "owner" } else { "admin" };

    let res = conn.execute(
        r#"
        INSERT INTO users (id, email, username, full_name, password_hash, role, status)
        VALUES (?1, ?2, ?3, ?4, ?5, ?6, 'active')
        "#,
        params![user_id, email, username, full_name, password_hash, role],
    );

    match res {
        Ok(_) => {
            info!("Registered new Nexus user: {} ({}) with role {}", email, user_id, role);
            let user = User {
                id: user_id.clone(),
                email: email.clone(),
                username,
                full_name,
                avatar_url: None,
                password_hash,
                security_question: None,
                security_answer_hash: None,
                role: role.to_string(),
                status: "active".to_string(),
                token_version: 1,
                created_at: Utc::now().to_rfc3339(),
                updated_at: Utc::now().to_rfc3339(),
            };

            let jwt_secret = state.config.admin_pin.as_bytes();
            let token = issue_user_session(&user, None, jwt_secret).ok();

            (
                StatusCode::CREATED,
                Json(json!({
                    "success": true,
                    "token": token,
                    "user": PublicUserProfile::from(&user)
                })),
            )
        }
        Err(e) => {
            warn!("Registration failed: {}", e);
            (
                StatusCode::CONFLICT,
                Json(json!({
                    "success": false,
                    "error": "A user with this email or username already exists"
                })),
            )
        }
    }
}

async fn login(
    State(state): State<SharedState>,
    Json(payload): Json<LoginRequest>,
) -> impl IntoResponse {
    let identifier = payload.identifier.trim().to_lowercase();
    let conn = state.db.lock().await;

    let user_row = conn.query_row(
        r#"
        SELECT id, email, username, full_name, avatar_url, password_hash,
               security_question, security_answer_hash, role, status, token_version,
               created_at, updated_at
        FROM users
        WHERE LOWER(email) = ?1 OR LOWER(username) = ?1
        LIMIT 1
        "#,
        params![identifier],
        |row| {
            Ok(User {
                id: row.get(0)?,
                email: row.get(1)?,
                username: row.get(2)?,
                full_name: row.get(3)?,
                avatar_url: row.get(4)?,
                password_hash: row.get(5)?,
                security_question: row.get(6)?,
                security_answer_hash: row.get(7)?,
                role: row.get(8)?,
                status: row.get(9)?,
                token_version: row.get(10)?,
                created_at: row.get(11)?,
                updated_at: row.get(12)?,
            })
        },
    );

    let user = match user_row {
        Ok(u) => u,
        Err(_) => {
            return (
                StatusCode::UNAUTHORIZED,
                Json(json!({ "success": false, "error": "Invalid username/email or password" })),
            );
        }
    };

    if user.status != "active" {
        return (
            StatusCode::FORBIDDEN,
            Json(json!({ "success": false, "error": "Account is inactive or suspended" })),
        );
    }

    let password_hash = match &user.password_hash {
        Some(h) => h,
        None => {
            return (
                StatusCode::UNAUTHORIZED,
                Json(json!({
                    "success": false,
                    "error": "This account uses passwordless or social login. Please use Magic Link or Google to sign in."
                })),
            );
        }
    };

    if !verify_password(&payload.password, password_hash) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(json!({ "success": false, "error": "Invalid username/email or password" })),
        );
    }

    let device_id = if let Some(dname) = payload.device_name {
        let dev_id = format!("dev_{}", uuid::Uuid::new_v4().simple());
        let _ = conn.execute(
            r#"
            INSERT OR REPLACE INTO user_devices (id, user_id, device_name, device_type, client_app)
            VALUES (?1, ?2, ?3, 'web', 'nexus-suite')
            "#,
            params![dev_id, user.id, dname],
        );
        Some(dev_id)
    } else {
        None
    };

    let jwt_secret = state.config.admin_pin.as_bytes();
    let token = match issue_user_session(&user, device_id.clone(), jwt_secret) {
        Ok(t) => t,
        Err(e) => return (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({ "success": false, "error": e }))),
    };

    let mut profile = PublicUserProfile::from(&user);
    profile.active_device_id = device_id;

    (
        StatusCode::OK,
        Json(json!({
            "success": true,
            "token": token,
            "user": profile
        })),
    )
}

async fn get_current_user(
    State(state): State<SharedState>,
    auth: AuthenticatedUser,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    let user_row = conn.query_row(
        r#"
        SELECT id, email, username, full_name, avatar_url, password_hash,
               security_question, security_answer_hash, role, status, token_version,
               created_at, updated_at
        FROM users
        WHERE id = ?1
        "#,
        params![auth.user_id],
        |row| {
            Ok(User {
                id: row.get(0)?,
                email: row.get(1)?,
                username: row.get(2)?,
                full_name: row.get(3)?,
                avatar_url: row.get(4)?,
                password_hash: row.get(5)?,
                security_question: row.get(6)?,
                security_answer_hash: row.get(7)?,
                role: row.get(8)?,
                status: row.get(9)?,
                token_version: row.get(10)?,
                created_at: row.get(11)?,
                updated_at: row.get(12)?,
            })
        },
    );

    match user_row {
        Ok(u) => {
            let mut profile = PublicUserProfile::from(&u);
            profile.active_device_id = auth.device_id;
            (StatusCode::OK, Json(json!({ "success": true, "user": profile })))
        }
        Err(_) => (
            StatusCode::NOT_FOUND,
            Json(json!({ "success": false, "error": "User not found" })),
        ),
    }
}

async fn logout(
    State(state): State<SharedState>,
    auth: AuthenticatedUser,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    // Increment token_version to immediately invalidate all existing JWTs for this user
    let _ = conn.execute(
        "UPDATE users SET token_version = token_version + 1 WHERE id = ?1",
        params![auth.user_id],
    );

    (
        StatusCode::OK,
        Json(json!({ "success": true, "message": "Logged out successfully" })),
    )
}

async fn get_public_keys(_state: State<SharedState>) -> impl IntoResponse {
    // Expose public metadata for fleet token verification
    Json(json!({
        "issuer": "https://faizal.uk/api/auth",
        "algorithms_supported": ["HS256"],
        "token_endpoint": "https://faizal.uk/api/auth/login",
        "userinfo_endpoint": "https://faizal.uk/api/auth/me",
        "jwks_uri": "https://faizal.uk/api/auth/keys"
    }))
}

// ─── Passwordless: Magic Link & 6-Digit OTP ───────────────────────────────────

async fn request_magic_link(
    State(state): State<SharedState>,
    Json(payload): Json<MagicLinkRequest>,
) -> impl IntoResponse {
    let email = payload.email.trim().to_lowercase();
    let raw_token = generate_random_token(32);
    let token_hash = sha256_hash(&raw_token);
    let code_id = format!("code_{}", uuid::Uuid::new_v4().simple());
    let expires_at = (Utc::now() + chrono::Duration::minutes(15)).to_rfc3339();

    let conn = state.db.lock().await;
    let _ = conn.execute(
        r#"
        INSERT INTO auth_codes (id, email, code_hash, code_type, expires_at)
        VALUES (?1, ?2, ?3, 'magic_link', ?4)
        "#,
        params![code_id, email, token_hash, expires_at],
    );

    let verify_url = format!("https://faizal.uk/admin/auth/verify?token={}", raw_token);

    // Dispatch email asynchronously
    let config = state.config.clone();
    let db = state.db.clone();
    let recipient = email.clone();
    tokio::spawn(async move {
        if let Err(e) = send_magic_link_email(&config, &db, &recipient, &verify_url).await {
            error!("Failed to send magic link email: {}", e);
        }
    });

    (
        StatusCode::OK,
        Json(json!({
            "success": true,
            "message": "If an account exists, a magic link has been sent to your email."
        })),
    )
}

#[derive(Debug, Deserialize)]
pub struct MagicLinkVerifyQuery {
    pub token: String,
}

async fn verify_magic_link(
    State(state): State<SharedState>,
    Query(query): Query<MagicLinkVerifyQuery>,
) -> impl IntoResponse {
    let token_hash = sha256_hash(query.token.trim());
    let conn = state.db.lock().await;

    let code_check = conn.query_row(
        r#"
        SELECT email, expires_at, used_at FROM auth_codes
        WHERE code_hash = ?1 AND code_type = 'magic_link'
        LIMIT 1
        "#,
        params![token_hash],
        |row| {
            Ok((
                row.get::<_, String>(0)?,
                row.get::<_, String>(1)?,
                row.get::<_, Option<String>>(2)?,
            ))
        },
    );

    let (email, expires_at_str, used_at) = match code_check {
        Ok(res) => res,
        Err(_) => {
            return (
                StatusCode::BAD_REQUEST,
                Json(json!({ "success": false, "error": "Invalid or expired magic link" })),
            );
        }
    };

    if used_at.is_some() {
        return (
            StatusCode::BAD_REQUEST,
            Json(json!({ "success": false, "error": "This magic link has already been used" })),
        );
    }

    if let Ok(exp) = chrono::DateTime::parse_from_rfc3339(&expires_at_str) {
        if exp < Utc::now() {
            return (
                StatusCode::BAD_REQUEST,
                Json(json!({ "success": false, "error": "Magic link has expired" })),
            );
        }
    }

    // Mark as used
    let _ = conn.execute(
        "UPDATE auth_codes SET used_at = CURRENT_TIMESTAMP WHERE code_hash = ?1",
        params![token_hash],
    );

    // Look up or auto-create user
    let user = get_or_create_user_by_email(&conn, &email);
    let jwt_secret = state.config.admin_pin.as_bytes();
    let token = issue_user_session(&user, None, jwt_secret).ok();

    (
        StatusCode::OK,
        Json(json!({
            "success": true,
            "token": token,
            "user": PublicUserProfile::from(&user)
        })),
    )
}

async fn send_otp(
    State(state): State<SharedState>,
    Json(payload): Json<OtpSendRequest>,
) -> impl IntoResponse {
    let email = payload.email.trim().to_lowercase();
    let otp_code = generate_otp_code();
    let code_hash = sha256_hash(&otp_code);
    let code_id = format!("otp_{}", uuid::Uuid::new_v4().simple());
    let expires_at = (Utc::now() + chrono::Duration::minutes(10)).to_rfc3339();

    let conn = state.db.lock().await;
    // Invalidate previous unused OTPs for this email
    let _ = conn.execute(
        "DELETE FROM auth_codes WHERE email = ?1 AND code_type = 'otp' AND used_at IS NULL",
        params![email],
    );

    let _ = conn.execute(
        r#"
        INSERT INTO auth_codes (id, email, code_hash, code_type, expires_at)
        VALUES (?1, ?2, ?3, 'otp', ?4)
        "#,
        params![code_id, email, code_hash, expires_at],
    );

    let config = state.config.clone();
    let db = state.db.clone();
    let recipient = email.clone();
    let code_to_send = otp_code.clone();
    tokio::spawn(async move {
        if let Err(e) = send_otp_email(&config, &db, &recipient, &code_to_send).await {
            error!("Failed to send OTP verification email: {}", e);
        }
    });

    (
        StatusCode::OK,
        Json(json!({
            "success": true,
            "message": "6-digit verification code has been sent to your email."
        })),
    )
}

async fn verify_otp(
    State(state): State<SharedState>,
    Json(payload): Json<OtpVerifyRequest>,
) -> impl IntoResponse {
    let email = payload.email.trim().to_lowercase();
    let code_hash = sha256_hash(payload.code.trim());
    let conn = state.db.lock().await;

    let otp_row = conn.query_row(
        r#"
        SELECT id, expires_at, used_at, attempts FROM auth_codes
        WHERE email = ?1 AND code_type = 'otp' AND used_at IS NULL
        ORDER BY created_at DESC LIMIT 1
        "#,
        params![email],
        |row| {
            Ok((
                row.get::<_, String>(0)?,
                row.get::<_, String>(1)?,
                row.get::<_, Option<String>>(2)?,
                row.get::<_, i64>(3)?,
            ))
        },
    );

    let (code_id, expires_at_str, _, attempts) = match otp_row {
        Ok(res) => res,
        Err(_) => {
            return (
                StatusCode::BAD_REQUEST,
                Json(json!({ "success": false, "error": "No pending verification code found" })),
            );
        }
    };

    if attempts >= 5 {
        return (
            StatusCode::TOO_MANY_REQUESTS,
            Json(json!({ "success": false, "error": "Too many failed attempts. Please request a new code." })),
        );
    }

    if let Ok(exp) = chrono::DateTime::parse_from_rfc3339(&expires_at_str) {
        if exp < Utc::now() {
            return (
                StatusCode::BAD_REQUEST,
                Json(json!({ "success": false, "error": "Verification code has expired" })),
            );
        }
    }

    // Verify hash match
    let match_exists: bool = conn
        .query_row(
            "SELECT 1 FROM auth_codes WHERE id = ?1 AND code_hash = ?2",
            params![code_id, code_hash],
            |_| Ok(true),
        )
        .unwrap_or(false);

    if !match_exists {
        let _ = conn.execute(
            "UPDATE auth_codes SET attempts = attempts + 1 WHERE id = ?1",
            params![code_id],
        );
        return (
            StatusCode::UNAUTHORIZED,
            Json(json!({ "success": false, "error": "Invalid 6-digit code" })),
        );
    }

    // Mark as used
    let _ = conn.execute(
        "UPDATE auth_codes SET used_at = CURRENT_TIMESTAMP WHERE id = ?1",
        params![code_id],
    );

    let user = get_or_create_user_by_email(&conn, &email);
    let device_id = if let Some(dname) = payload.device_name {
        let dev_id = format!("dev_{}", uuid::Uuid::new_v4().simple());
        let _ = conn.execute(
            r#"
            INSERT OR REPLACE INTO user_devices (id, user_id, device_name, device_type, client_app)
            VALUES (?1, ?2, ?3, 'web', 'nexus-suite')
            "#,
            params![dev_id, user.id, dname],
        );
        Some(dev_id)
    } else {
        None
    };

    let jwt_secret = state.config.admin_pin.as_bytes();
    let token = issue_user_session(&user, device_id.clone(), jwt_secret).ok();

    let mut profile = PublicUserProfile::from(&user);
    profile.active_device_id = device_id;

    (
        StatusCode::OK,
        Json(json!({
            "success": true,
            "token": token,
            "user": profile
        })),
    )
}

// ─── Password Reset & Security Questions ──────────────────────────────────────

async fn request_password_reset(
    State(state): State<SharedState>,
    Json(payload): Json<PasswordResetRequest>,
) -> impl IntoResponse {
    let email = payload.email.trim().to_lowercase();
    let conn = state.db.lock().await;

    let user_exists: bool = conn
        .query_row("SELECT 1 FROM users WHERE LOWER(email) = ?1", params![email], |_| Ok(true))
        .unwrap_or(false);

    if user_exists {
        let raw_token = generate_random_token(32);
        let token_hash = sha256_hash(&raw_token);
        let code_id = format!("reset_{}", uuid::Uuid::new_v4().simple());
        let expires_at = (Utc::now() + chrono::Duration::hours(1)).to_rfc3339();

        let _ = conn.execute(
            r#"
            INSERT INTO auth_codes (id, email, code_hash, code_type, expires_at)
            VALUES (?1, ?2, ?3, 'password_reset', ?4)
            "#,
            params![code_id, email, token_hash, expires_at],
        );

        let reset_url = format!("https://faizal.uk/admin/auth/reset?token={}", raw_token);
        let config = state.config.clone();
        let db = state.db.clone();
        let recipient = email.clone();
        tokio::spawn(async move {
            if let Err(e) = send_reset_password_email(&config, &db, &recipient, &reset_url).await {
                error!("Failed to send password reset email: {}", e);
            }
        });
    }

    (
        StatusCode::OK,
        Json(json!({
            "success": true,
            "message": "If an account exists, password reset instructions have been sent."
        })),
    )
}

async fn confirm_password_reset(
    State(state): State<SharedState>,
    Json(payload): Json<PasswordResetConfirmRequest>,
) -> impl IntoResponse {
    if payload.new_password.len() < 8 {
        return (
            StatusCode::BAD_REQUEST,
            Json(json!({ "success": false, "error": "New password must be at least 8 characters long" })),
        );
    }

    let token_hash = sha256_hash(payload.token.trim());
    let conn = state.db.lock().await;

    let code_row = conn.query_row(
        r#"
        SELECT email, expires_at, used_at FROM auth_codes
        WHERE code_hash = ?1 AND code_type = 'password_reset'
        LIMIT 1
        "#,
        params![token_hash],
        |row| {
            Ok((
                row.get::<_, String>(0)?,
                row.get::<_, String>(1)?,
                row.get::<_, Option<String>>(2)?,
            ))
        },
    );

    let (email, expires_at_str, used_at) = match code_row {
        Ok(res) => res,
        Err(_) => {
            return (
                StatusCode::BAD_REQUEST,
                Json(json!({ "success": false, "error": "Invalid or expired reset token" })),
            );
        }
    };

    if used_at.is_some() {
        return (
            StatusCode::BAD_REQUEST,
            Json(json!({ "success": false, "error": "Reset token has already been used" })),
        );
    }

    if let Ok(exp) = chrono::DateTime::parse_from_rfc3339(&expires_at_str) {
        if exp < Utc::now() {
            return (
                StatusCode::BAD_REQUEST,
                Json(json!({ "success": false, "error": "Reset token has expired" })),
            );
        }
    }

    let new_hash = match hash_password(&payload.new_password) {
        Ok(h) => h,
        Err(e) => return (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({ "success": false, "error": e }))),
    };

    let _ = conn.execute(
        "UPDATE users SET password_hash = ?1, token_version = token_version + 1 WHERE LOWER(email) = ?2",
        params![new_hash, email],
    );
    let _ = conn.execute(
        "UPDATE auth_codes SET used_at = CURRENT_TIMESTAMP WHERE code_hash = ?1",
        params![token_hash],
    );

    (
        StatusCode::OK,
        Json(json!({ "success": true, "message": "Password updated successfully. You can now log in." })),
    )
}

async fn get_security_questions() -> impl IntoResponse {
    Json(json!({
        "success": true,
        "questions": STANDARD_SECURITY_QUESTIONS
    }))
}

async fn setup_security_question(
    State(state): State<SharedState>,
    auth: AuthenticatedUser,
    Json(payload): Json<SecurityQuestionSetupRequest>,
) -> impl IntoResponse {
    if !is_valid_question_key(&payload.question_key) {
        return (
            StatusCode::BAD_REQUEST,
            Json(json!({ "success": false, "error": "Invalid security question key" })),
        );
    }
    if payload.answer.trim().len() < 2 {
        return (
            StatusCode::BAD_REQUEST,
            Json(json!({ "success": false, "error": "Answer is too short" })),
        );
    }

    let answer_hash = match hash_security_answer(&payload.answer) {
        Ok(h) => h,
        Err(e) => return (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({ "success": false, "error": e }))),
    };

    let conn = state.db.lock().await;
    let _ = conn.execute(
        "UPDATE users SET security_question = ?1, security_answer_hash = ?2 WHERE id = ?3",
        params![payload.question_key, answer_hash, auth.user_id],
    );

    (
        StatusCode::OK,
        Json(json!({ "success": true, "message": "Security question successfully configured." })),
    )
}

async fn recover_with_security_question(
    State(state): State<SharedState>,
    Json(payload): Json<SecurityQuestionVerifyRequest>,
) -> impl IntoResponse {
    let identifier = payload.identifier.trim().to_lowercase();
    let conn = state.db.lock().await;

    let user_row = conn.query_row(
        r#"
        SELECT id, email, username, full_name, avatar_url, password_hash,
               security_question, security_answer_hash, role, status, token_version,
               created_at, updated_at
        FROM users
        WHERE LOWER(email) = ?1 OR LOWER(username) = ?1
        LIMIT 1
        "#,
        params![identifier],
        |row| {
            Ok(User {
                id: row.get(0)?,
                email: row.get(1)?,
                username: row.get(2)?,
                full_name: row.get(3)?,
                avatar_url: row.get(4)?,
                password_hash: row.get(5)?,
                security_question: row.get(6)?,
                security_answer_hash: row.get(7)?,
                role: row.get(8)?,
                status: row.get(9)?,
                token_version: row.get(10)?,
                created_at: row.get(11)?,
                updated_at: row.get(12)?,
            })
        },
    );

    let user = match user_row {
        Ok(u) => u,
        Err(_) => return (StatusCode::NOT_FOUND, Json(json!({ "success": false, "error": "Account not found" }))),
    };

    let answer_hash = match &user.security_answer_hash {
        Some(h) => h,
        None => {
            return (
                StatusCode::BAD_REQUEST,
                Json(json!({ "success": false, "error": "No security question set up on this account" })),
            );
        }
    };

    if !verify_security_answer(&payload.answer, answer_hash) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(json!({ "success": false, "error": "Security answer is incorrect" })),
        );
    }

    // If new password provided, update it immediately
    if let Some(new_pass) = payload.new_password {
        if new_pass.len() >= 8 {
            if let Ok(new_hash) = hash_password(&new_pass) {
                let _ = conn.execute(
                    "UPDATE users SET password_hash = ?1, token_version = token_version + 1 WHERE id = ?2",
                    params![new_hash, user.id],
                );
            }
        }
    }

    let jwt_secret = state.config.admin_pin.as_bytes();
    let token = issue_user_session(&user, None, jwt_secret).ok();

    (
        StatusCode::OK,
        Json(json!({
            "success": true,
            "token": token,
            "user": PublicUserProfile::from(&user)
        })),
    )
}

// ─── Social OAuth Handlers ────────────────────────────────────────────────────

async fn google_auth(
    State(state): State<SharedState>,
    Json(payload): Json<GoogleAuthRequest>,
) -> impl IntoResponse {
    let id_token = match payload.credential {
        Some(t) => t,
        None => {
            return (
                StatusCode::BAD_REQUEST,
                Json(json!({ "success": false, "error": "Missing Google ID token" })),
            );
        }
    };

    let client = reqwest::Client::new();
    let expected_client_ids = [
        "540487611333-t0i54c3b0k07nuoaljkdr68otst89se5.apps.googleusercontent.com",
        "540487611333-2sknab3189vnh0f4dm6ftnsnddsl447f.apps.googleusercontent.com",
    ];

    let identity = match verify_google_id_token(&client, &id_token, &expected_client_ids).await {
        Ok(id) => id,
        Err(e) => {
            return (
                StatusCode::UNAUTHORIZED,
                Json(json!({ "success": false, "error": e })),
            );
        }
    };

    let conn = state.db.lock().await;
    let user = get_or_create_social_user(&conn, &identity);

    let device_id = if let Some(dname) = payload.device_name {
        let dev_id = format!("dev_{}", uuid::Uuid::new_v4().simple());
        let _ = conn.execute(
            r#"
            INSERT OR REPLACE INTO user_devices (id, user_id, device_name, device_type, client_app)
            VALUES (?1, ?2, ?3, 'web', 'nexus-suite')
            "#,
            params![dev_id, user.id, dname],
        );
        Some(dev_id)
    } else {
        None
    };

    let jwt_secret = state.config.admin_pin.as_bytes();
    let token = issue_user_session(&user, device_id.clone(), jwt_secret).ok();

    let mut profile = PublicUserProfile::from(&user);
    profile.active_device_id = device_id;

    (
        StatusCode::OK,
        Json(json!({
            "success": true,
            "token": token,
            "user": profile
        })),
    )
}

async fn meta_auth(
    State(state): State<SharedState>,
    Json(payload): Json<GoogleAuthRequest>, // Reuses credential field for access token
) -> impl IntoResponse {
    let access_token = match payload.credential {
        Some(t) => t,
        None => return (StatusCode::BAD_REQUEST, Json(json!({ "success": false, "error": "Missing Meta token" }))),
    };

    let client = reqwest::Client::new();
    let identity = match verify_meta_access_token(&client, &access_token).await {
        Ok(id) => id,
        Err(e) => return (StatusCode::UNAUTHORIZED, Json(json!({ "success": false, "error": e }))),
    };

    let conn = state.db.lock().await;
    let user = get_or_create_social_user(&conn, &identity);

    let jwt_secret = state.config.admin_pin.as_bytes();
    let token = issue_user_session(&user, None, jwt_secret).ok();

    (
        StatusCode::OK,
        Json(json!({
            "success": true,
            "token": token,
            "user": PublicUserProfile::from(&user)
        })),
    )
}

// ─── RFC 8628 Device Flow (CLI Agent: 'nexus auth login') ─────────────────────

async fn request_device_code(
    State(state): State<SharedState>,
    Json(_payload): Json<DeviceCodeRequest>,
) -> impl IntoResponse {
    let device_code = generate_random_token(32);
    let user_code = generate_user_code();
    let expires_in: u64 = 600; // 10 minutes
    let expires_at = (Utc::now() + chrono::Duration::seconds(expires_in as i64)).to_rfc3339();

    let conn = state.db.lock().await;
    let _ = conn.execute(
        r#"
        INSERT INTO device_codes (device_code, user_code, status, expires_at)
        VALUES (?1, ?2, 'pending', ?3)
        "#,
        params![device_code, user_code, expires_at],
    );

    (
        StatusCode::OK,
        Json(DeviceCodeResponse {
            device_code,
            user_code,
            verification_uri: "https://faizal.uk/auth/device".to_string(),
            expires_in,
            interval: 5,
        }),
    )
}

async fn poll_device_token(
    State(state): State<SharedState>,
    Json(payload): Json<DeviceTokenPollRequest>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;

    let row = conn.query_row(
        "SELECT user_id, status, expires_at FROM device_codes WHERE device_code = ?1",
        params![payload.device_code],
        |r| Ok((r.get::<_, Option<String>>(0)?, r.get::<_, String>(1)?, r.get::<_, String>(2)?)),
    );

    let (user_id_opt, status, expires_at_str) = match row {
        Ok(res) => res,
        Err(_) => {
            return (
                StatusCode::BAD_REQUEST,
                Json(json!({ "error": "invalid_grant", "error_description": "Unknown device code" })),
            );
        }
    };

    if let Ok(exp) = chrono::DateTime::parse_from_rfc3339(&expires_at_str) {
        if exp < Utc::now() {
            let _ = conn.execute("DELETE FROM device_codes WHERE device_code = ?1", params![payload.device_code]);
            return (
                StatusCode::BAD_REQUEST,
                Json(json!({ "error": "expired_token", "error_description": "Device authorization code expired" })),
            );
        }
    }

    if status == "pending" {
        return (
            StatusCode::BAD_REQUEST,
            Json(json!({ "error": "authorization_pending", "error_description": "User has not approved yet" })),
        );
    }

    if status == "denied" {
        let _ = conn.execute("DELETE FROM device_codes WHERE device_code = ?1", params![payload.device_code]);
        return (
            StatusCode::FORBIDDEN,
            Json(json!({ "error": "access_denied", "error_description": "User denied authorization" })),
        );
    }

    if status == "approved" {
        if let Some(uid) = user_id_opt {
            let user = conn.query_row(
                "SELECT id, email, username, full_name, avatar_url, password_hash, security_question, security_answer_hash, role, status, token_version, created_at, updated_at FROM users WHERE id = ?1",
                params![uid],
                |r| {
                    Ok(User {
                        id: r.get(0)?,
                        email: r.get(1)?,
                        username: r.get(2)?,
                        full_name: r.get(3)?,
                        avatar_url: r.get(4)?,
                        password_hash: r.get(5)?,
                        security_question: r.get(6)?,
                        security_answer_hash: r.get(7)?,
                        role: r.get(8)?,
                        status: r.get(9)?,
                        token_version: r.get(10)?,
                        created_at: r.get(11)?,
                        updated_at: r.get(12)?,
                    })
                },
            );

            if let Ok(u) = user {
                let _ = conn.execute("DELETE FROM device_codes WHERE device_code = ?1", params![payload.device_code]);
                let jwt_secret = state.config.admin_pin.as_bytes();
                let token = issue_user_session(&u, Some("cli_device".to_string()), jwt_secret).ok();
                return (
                    StatusCode::OK,
                    Json(json!({
                        "success": true,
                        "token": token,
                        "user": PublicUserProfile::from(&u)
                    })),
                );
            }
        }
    }

    (
        StatusCode::BAD_REQUEST,
        Json(json!({ "error": "server_error" })),
    )
}

async fn approve_device_code(
    State(state): State<SharedState>,
    auth: AuthenticatedUser,
    Json(payload): Json<DeviceApproveRequest>,
) -> impl IntoResponse {
    let normalized = normalize_user_code(&payload.user_code);
    let conn = state.db.lock().await;

    let res = conn.execute(
        r#"
        UPDATE device_codes
        SET status = 'approved', user_id = ?1
        WHERE user_code = ?2 AND status = 'pending' AND expires_at > CURRENT_TIMESTAMP
        "#,
        params![auth.user_id, normalized],
    );

    match res {
        Ok(rows) if rows > 0 => {
            info!("User {} approved CLI device code {}", auth.user_id, normalized);
            (
                StatusCode::OK,
                Json(json!({ "success": true, "message": "Device successfully authorized!" })),
            )
        }
        _ => (
            StatusCode::BAD_REQUEST,
            Json(json!({ "success": false, "error": "Invalid or expired user code" })),
        ),
    }
}

// ─── Machine Personal Access Tokens (PATs) ────────────────────────────────────

async fn create_pat(
    State(state): State<SharedState>,
    auth: AuthenticatedUser,
    Json(payload): Json<CreatePatRequest>,
) -> impl IntoResponse {
    let (raw_token, prefix, token_hash) = generate_pat_token();
    let pat_id = format!("pat_{}", uuid::Uuid::new_v4().simple());
    let scopes_json = serde_json::to_string(&payload.scopes.unwrap_or_else(|| vec!["*".to_string()]))
        .unwrap_or_else(|_| "[\"*\"]".to_string());

    let expires_at = payload.expires_in_days.map(|days| {
        (Utc::now() + chrono::Duration::days(days)).to_rfc3339()
    });

    let conn = state.db.lock().await;
    let res = conn.execute(
        r#"
        INSERT INTO api_tokens (id, user_id, name, token_prefix, token_hash, scopes, expires_at)
        VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)
        "#,
        params![pat_id, auth.user_id, payload.name, prefix, token_hash, scopes_json, expires_at],
    );

    match res {
        Ok(_) => (
            StatusCode::CREATED,
            Json(json!({
                "success": true,
                "token": raw_token,
                "prefix": prefix,
                "id": pat_id,
                "name": payload.name,
                "message": "Copy this token now! You will not be able to see it again."
            })),
        ),
        Err(e) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({ "success": false, "error": e.to_string() })),
        ),
    }
}

async fn list_pats(
    State(state): State<SharedState>,
    auth: AuthenticatedUser,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    let mut stmt = match conn.prepare(
        "SELECT id, name, token_prefix, scopes, last_used_at, expires_at, created_at FROM api_tokens WHERE user_id = ?1 ORDER BY created_at DESC"
    ) {
        Ok(s) => s,
        Err(e) => return (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({ "success": false, "error": e.to_string() }))),
    };

    let pats = stmt
        .query_map(params![auth.user_id], |row| {
            Ok(json!({
                "id": row.get::<_, String>(0)?,
                "name": row.get::<_, String>(1)?,
                "prefix": row.get::<_, String>(2)?,
                "scopes": row.get::<_, String>(3)?,
                "last_used_at": row.get::<_, Option<String>>(4)?,
                "expires_at": row.get::<_, Option<String>>(5)?,
                "created_at": row.get::<_, String>(6)?,
            }))
        })
        .map(|rows| rows.filter_map(Result::ok).collect::<Vec<_>>())
        .unwrap_or_default();

    (StatusCode::OK, Json(json!({ "success": true, "tokens": pats })))
}

async fn revoke_pat(
    State(state): State<SharedState>,
    auth: AuthenticatedUser,
    Path(id): Path<String>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    let _ = conn.execute(
        "DELETE FROM api_tokens WHERE id = ?1 AND user_id = ?2",
        params![id, auth.user_id],
    );

    (StatusCode::OK, Json(json!({ "success": true, "message": "Token revoked" })))
}

// ─── Helpers: User Upsert ─────────────────────────────────────────────────────

fn get_or_create_user_by_email(conn: &rusqlite::Connection, email: &str) -> User {
    let existing = conn.query_row(
        r#"
        SELECT id, email, username, full_name, avatar_url, password_hash,
               security_question, security_answer_hash, role, status, token_version,
               created_at, updated_at
        FROM users WHERE LOWER(email) = ?1 LIMIT 1
        "#,
        params![email],
        |r| {
            Ok(User {
                id: r.get(0)?,
                email: r.get(1)?,
                username: r.get(2)?,
                full_name: r.get(3)?,
                avatar_url: r.get(4)?,
                password_hash: r.get(5)?,
                security_question: r.get(6)?,
                security_answer_hash: r.get(7)?,
                role: r.get(8)?,
                status: r.get(9)?,
                token_version: r.get(10)?,
                created_at: r.get(11)?,
                updated_at: r.get(12)?,
            })
        },
    );

    if let Ok(u) = existing {
        return u;
    }

    let user_id = format!("usr_{}", uuid::Uuid::new_v4().simple());
    let default_name = email.split('@').next().unwrap_or("User").to_string();
    let existing_count: i64 = conn.query_row("SELECT COUNT(*) FROM users", [], |row| row.get(0)).unwrap_or(0);
    let role = if existing_count == 0 { "owner" } else { "admin" };

    let _ = conn.execute(
        "INSERT INTO users (id, email, full_name, role, status) VALUES (?1, ?2, ?3, ?4, 'active')",
        params![user_id, email, default_name, role],
    );

    User {
        id: user_id,
        email: email.to_string(),
        username: None,
        full_name: default_name,
        avatar_url: None,
        password_hash: None,
        security_question: None,
        security_answer_hash: None,
        role: role.to_string(),
        status: "active".to_string(),
        token_version: 1,
        created_at: Utc::now().to_rfc3339(),
        updated_at: Utc::now().to_rfc3339(),
    }
}

fn get_or_create_social_user(
    conn: &rusqlite::Connection,
    identity: &crate::auth::oauth::NormalizedOAuthIdentity,
) -> User {
    // Check if oauth account is already linked
    let linked = conn.query_row(
        r#"
        SELECT u.id, u.email, u.username, u.full_name, u.avatar_url, u.password_hash,
               u.security_question, u.security_answer_hash, u.role, u.status, u.token_version,
               u.created_at, u.updated_at
        FROM user_oauth_accounts oa
        JOIN users u ON u.id = oa.user_id
        WHERE oa.provider = ?1 AND oa.provider_user_id = ?2
        LIMIT 1
        "#,
        params![identity.provider, identity.provider_user_id],
        |r| {
            Ok(User {
                id: r.get(0)?,
                email: r.get(1)?,
                username: r.get(2)?,
                full_name: r.get(3)?,
                avatar_url: r.get(4)?,
                password_hash: r.get(5)?,
                security_question: r.get(6)?,
                security_answer_hash: r.get(7)?,
                role: r.get(8)?,
                status: r.get(9)?,
                token_version: r.get(10)?,
                created_at: r.get(11)?,
                updated_at: r.get(12)?,
            })
        },
    );

    if let Ok(u) = linked {
        return u;
    }

    // Otherwise check if a user with this email already exists
    let mut user = get_or_create_user_by_email(conn, &identity.email);

    // Update avatar if provided by social provider
    if user.avatar_url.is_none() && identity.avatar_url.is_some() {
        let _ = conn.execute(
            "UPDATE users SET avatar_url = ?1 WHERE id = ?2",
            params![identity.avatar_url, user.id],
        );
        user.avatar_url = identity.avatar_url.clone();
    }

    // Link social account
    let link_id = format!("oa_{}", uuid::Uuid::new_v4().simple());
    let _ = conn.execute(
        r#"
        INSERT OR IGNORE INTO user_oauth_accounts (id, user_id, provider, provider_user_id, provider_email, provider_picture)
        VALUES (?1, ?2, ?3, ?4, ?5, ?6)
        "#,
        params![link_id, user.id, identity.provider, identity.provider_user_id, identity.email, identity.avatar_url],
    );

    user
}

// ─── Ecosystem Applications Governance ────────────────────────────────────────

async fn list_registered_apps(State(state): State<SharedState>) -> impl IntoResponse {
    let conn = state.db.lock().await;
    let mut stmt = match conn.prepare(
        r#"
        SELECT id, name, slug, description, allowed_redirect_uris, allowed_scopes,
               icon_url, is_first_party, is_enabled, created_at
        FROM auth_registered_apps
        ORDER BY is_first_party DESC, name ASC
        "#,
    ) {
        Ok(s) => s,
        Err(e) => return (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({ "success": false, "error": e.to_string() }))),
    };

    let apps = stmt
        .query_map([], |row| {
            let uris_raw: String = row.get(4)?;
            let scopes_raw: String = row.get(5)?;
            let allowed_redirect_uris = serde_json::from_str(&uris_raw).unwrap_or_default();
            let allowed_scopes = serde_json::from_str(&scopes_raw).unwrap_or_default();

            Ok(RegisteredApp {
                id: row.get(0)?,
                name: row.get(1)?,
                slug: row.get(2)?,
                description: row.get(3)?,
                allowed_redirect_uris,
                allowed_scopes,
                icon_url: row.get(6)?,
                is_first_party: row.get::<_, i64>(7)? != 0,
                is_enabled: row.get::<_, i64>(8)? != 0,
                created_at: row.get(9)?,
            })
        })
        .map(|rows| rows.filter_map(Result::ok).collect::<Vec<_>>())
        .unwrap_or_default();

    (StatusCode::OK, Json(json!({ "success": true, "apps": apps })))
}

async fn register_app(
    State(state): State<SharedState>,
    auth: AuthenticatedUser,
    Json(payload): Json<CreateAppRequest>,
) -> impl IntoResponse {
    if !auth.is_admin {
        return (StatusCode::FORBIDDEN, Json(json!({ "success": false, "error": "Admin required" })));
    }

    let app_id = format!("app_{}", uuid::Uuid::new_v4().simple());
    let raw_slug = payload.slug.unwrap_or_else(|| payload.name.to_lowercase().replace(' ', "-"));
    let slug: String = raw_slug.chars().filter(|c| c.is_ascii_alphanumeric() || *c == '-').collect();
    let uris_json = serde_json::to_string(&payload.allowed_redirect_uris.unwrap_or_default()).unwrap_or_else(|_| "[]".into());
    let scopes_json = serde_json::to_string(&payload.allowed_scopes.unwrap_or_else(|| vec!["profile:read".into()])).unwrap_or_else(|_| "[]".into());
    let is_first_party = if payload.is_first_party.unwrap_or(true) { 1 } else { 0 };

    let conn = state.db.lock().await;
    let res = conn.execute(
        r#"
        INSERT INTO auth_registered_apps (id, name, slug, description, allowed_redirect_uris, allowed_scopes, is_first_party, is_enabled)
        VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, 1)
        "#,
        params![app_id, payload.name, slug, payload.description, uris_json, scopes_json, is_first_party],
    );

    match res {
        Ok(_) => {
            info!("Registered new ecosystem app: {} ({})", payload.name, slug);
            (StatusCode::CREATED, Json(json!({ "success": true, "id": app_id, "slug": slug })))
        }
        Err(e) => (StatusCode::BAD_REQUEST, Json(json!({ "success": false, "error": e.to_string() }))),
    }
}

async fn update_registered_app(
    State(state): State<SharedState>,
    auth: AuthenticatedUser,
    Path(id): Path<String>,
    Json(payload): Json<UpdateAppRequest>,
) -> impl IntoResponse {
    if !auth.is_admin {
        return (StatusCode::FORBIDDEN, Json(json!({ "success": false, "error": "Admin required" })));
    }

    let conn = state.db.lock().await;
    let mut updates = Vec::new();

    if let Some(name) = &payload.name {
        let _ = conn.execute("UPDATE auth_registered_apps SET name = ?1 WHERE id = ?2", params![name, id]);
        updates.push("name");
    }
    if let Some(desc) = &payload.description {
        let _ = conn.execute("UPDATE auth_registered_apps SET description = ?1 WHERE id = ?2", params![desc, id]);
        updates.push("description");
    }
    if let Some(uris) = &payload.allowed_redirect_uris {
        let json_str = serde_json::to_string(uris).unwrap_or_else(|_| "[]".into());
        let _ = conn.execute("UPDATE auth_registered_apps SET allowed_redirect_uris = ?1 WHERE id = ?2", params![json_str, id]);
        updates.push("uris");
    }
    if let Some(scopes) = &payload.allowed_scopes {
        let json_str = serde_json::to_string(scopes).unwrap_or_else(|_| "[]".into());
        let _ = conn.execute("UPDATE auth_registered_apps SET allowed_scopes = ?1 WHERE id = ?2", params![json_str, id]);
        updates.push("scopes");
    }
    if let Some(is_enabled) = payload.is_enabled {
        let val = if is_enabled { 1 } else { 0 };
        let _ = conn.execute("UPDATE auth_registered_apps SET is_enabled = ?1 WHERE id = ?2", params![val, id]);
        updates.push("status");
    }

    let _ = conn.execute("UPDATE auth_registered_apps SET updated_at = CURRENT_TIMESTAMP WHERE id = ?1", params![id]);
    (StatusCode::OK, Json(json!({ "success": true, "updated_fields": updates })))
}

async fn delete_registered_app(
    State(state): State<SharedState>,
    auth: AuthenticatedUser,
    Path(id): Path<String>,
) -> impl IntoResponse {
    if !auth.is_admin {
        return (StatusCode::FORBIDDEN, Json(json!({ "success": false, "error": "Admin required" })));
    }

    let conn = state.db.lock().await;
    let _ = conn.execute("DELETE FROM auth_user_grants WHERE app_id = ?1", params![id]);
    let res = conn.execute("DELETE FROM auth_registered_apps WHERE id = ?1", params![id]);

    match res {
        Ok(rows) if rows > 0 => (StatusCode::OK, Json(json!({ "success": true, "message": "App deleted" }))),
        _ => (StatusCode::NOT_FOUND, Json(json!({ "success": false, "error": "App not found" }))),
    }
}

// ─── Connected Sessions & Devices Governance ─────────────────────────────────

async fn list_user_sessions(
    State(state): State<SharedState>,
    auth: AuthenticatedUser,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    let mut stmt = match conn.prepare(
        r#"
        SELECT id, device_name, device_type, client_app, ip_address, last_seen, created_at
        FROM user_devices
        WHERE user_id = ?1
        ORDER BY last_seen DESC
        "#,
    ) {
        Ok(s) => s,
        Err(e) => return (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({ "success": false, "error": e.to_string() }))),
    };

    let current_dev = auth.device_id.clone();
    let devices = stmt
        .query_map(params![auth.user_id], |row| {
            let id: String = row.get(0)?;
            let is_current = current_dev.as_ref().map(|d| d == &id).unwrap_or(false);

            Ok(ConnectedDeviceSession {
                id,
                device_name: row.get(1)?,
                device_type: row.get(2)?,
                client_app: row.get(3)?,
                ip_address: row.get(4)?,
                last_seen: row.get(5)?,
                created_at: row.get(6)?,
                is_current,
            })
        })
        .map(|rows| rows.filter_map(Result::ok).collect::<Vec<_>>())
        .unwrap_or_default();

    (StatusCode::OK, Json(json!({ "success": true, "sessions": devices })))
}

async fn revoke_user_session(
    State(state): State<SharedState>,
    auth: AuthenticatedUser,
    Path(device_id): Path<String>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    let _ = conn.execute(
        "DELETE FROM refresh_tokens WHERE device_id = ?1 AND user_id = ?2",
        params![device_id, auth.user_id],
    );
    let res = conn.execute(
        "DELETE FROM user_devices WHERE id = ?1 AND user_id = ?2",
        params![device_id, auth.user_id],
    );

    match res {
        Ok(rows) if rows > 0 => (StatusCode::OK, Json(json!({ "success": true, "message": "Device session terminated" }))),
        _ => (StatusCode::NOT_FOUND, Json(json!({ "success": false, "error": "Session not found" }))),
    }
}

async fn revoke_all_sessions(
    State(state): State<SharedState>,
    auth: AuthenticatedUser,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    // Invalidate refresh tokens
    let _ = conn.execute(
        "DELETE FROM refresh_tokens WHERE user_id = ?1",
        params![auth.user_id],
    );
    // Remove all devices except current
    if let Some(cur) = &auth.device_id {
        let _ = conn.execute(
            "DELETE FROM user_devices WHERE user_id = ?1 AND id != ?2",
            params![auth.user_id, cur],
        );
    } else {
        let _ = conn.execute(
            "DELETE FROM user_devices WHERE user_id = ?1",
            params![auth.user_id],
        );
    }
    // Bump token version to instantly revoke all other active JWTs
    let _ = conn.execute(
        "UPDATE users SET token_version = token_version + 1 WHERE id = ?1",
        params![auth.user_id],
    );

    (StatusCode::OK, Json(json!({ "success": true, "message": "All other sessions signed out" })))
}

// ─── Connected App Grants & Delegated Permissions ─────────────────────────────

async fn list_user_grants(
    State(state): State<SharedState>,
    auth: AuthenticatedUser,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    let mut stmt = match conn.prepare(
        r#"
        SELECT g.id, g.app_id, a.name, a.slug, a.description, a.is_first_party,
               g.granted_scopes, g.created_at, g.last_used_at
        FROM auth_user_grants g
        JOIN auth_registered_apps a ON a.id = g.app_id
        WHERE g.user_id = ?1 AND g.is_revoked = 0
        ORDER BY g.last_used_at DESC
        "#,
    ) {
        Ok(s) => s,
        Err(e) => return (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({ "success": false, "error": e.to_string() }))),
    };

    let grants = stmt
        .query_map(params![auth.user_id], |row| {
            let scopes_raw: String = row.get(6)?;
            let granted_scopes = serde_json::from_str(&scopes_raw).unwrap_or_default();

            Ok(UserAppGrantItem {
                id: row.get(0)?,
                app_id: row.get(1)?,
                app_name: row.get(2)?,
                app_slug: row.get(3)?,
                description: row.get(4)?,
                is_first_party: row.get::<_, i64>(5)? != 0,
                granted_scopes,
                created_at: row.get(7)?,
                last_used_at: row.get(8)?,
            })
        })
        .map(|rows| rows.filter_map(Result::ok).collect::<Vec<_>>())
        .unwrap_or_default();

    (StatusCode::OK, Json(json!({ "success": true, "grants": grants })))
}

async fn revoke_user_grant(
    State(state): State<SharedState>,
    auth: AuthenticatedUser,
    Path(app_id): Path<String>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    let _ = conn.execute(
        "UPDATE auth_user_grants SET is_revoked = 1 WHERE user_id = ?1 AND app_id = ?2",
        params![auth.user_id, app_id],
    );

    (StatusCode::OK, Json(json!({ "success": true, "message": "App access revoked" })))
}
