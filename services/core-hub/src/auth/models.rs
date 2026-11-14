use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct User {
    pub id: String,
    pub email: String,
    pub username: Option<String>,
    pub full_name: String,
    pub avatar_url: Option<String>,
    #[serde(skip_serializing)]
    pub password_hash: Option<String>,
    pub security_question: Option<String>,
    #[serde(skip_serializing)]
    pub security_answer_hash: Option<String>,
    pub role: String,
    pub status: String,
    pub token_version: i64,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PublicUserProfile {
    pub id: String,
    pub email: String,
    pub username: Option<String>,
    pub full_name: String,
    pub avatar_url: Option<String>,
    pub role: String,
    pub status: String,
    pub has_password: bool,
    pub has_security_question: bool,
    pub active_device_id: Option<String>,
}

impl From<&User> for PublicUserProfile {
    fn from(u: &User) -> Self {
        Self {
            id: u.id.clone(),
            email: u.email.clone(),
            username: u.username.clone(),
            full_name: u.full_name.clone(),
            avatar_url: u.avatar_url.clone(),
            role: u.role.clone(),
            status: u.status.clone(),
            has_password: u.password_hash.is_some(),
            has_security_question: u.security_question.is_some() && u.security_answer_hash.is_some(),
            active_device_id: None,
        }
    }
}

// ─── Authentication Requests ──────────────────────────────────────────────────

#[derive(Debug, Deserialize)]
pub struct LoginRequest {
    pub identifier: String, // email or username
    pub password: String,
    pub device_name: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct RegisterRequest {
    pub email: String,
    pub username: Option<String>,
    pub full_name: String,
    pub password: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct MagicLinkRequest {
    pub email: String,
}

#[derive(Debug, Deserialize)]
pub struct OtpSendRequest {
    pub email: String,
}

#[derive(Debug, Deserialize)]
pub struct OtpVerifyRequest {
    pub email: String,
    pub code: String,
    pub device_name: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct PasswordResetRequest {
    pub email: String,
}

#[derive(Debug, Deserialize)]
pub struct PasswordResetConfirmRequest {
    pub token: String,
    pub new_password: String,
}

#[derive(Debug, Deserialize)]
pub struct SecurityQuestionSetupRequest {
    pub question_key: String,
    pub answer: String,
}

#[derive(Debug, Deserialize)]
pub struct SecurityQuestionVerifyRequest {
    pub identifier: String,
    pub answer: String,
    pub new_password: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct GoogleAuthRequest {
    pub credential: Option<String>, // Google ID Token
    pub code: Option<String>,       // OAuth2 Authorization Code
    pub device_name: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct DeviceCodeRequest {
    pub client_id: Option<String>,
    pub client_name: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct DeviceTokenPollRequest {
    pub device_code: String,
}

#[derive(Debug, Deserialize)]
pub struct DeviceApproveRequest {
    pub user_code: String,
}

#[derive(Debug, Deserialize)]
pub struct CreatePatRequest {
    pub name: String,
    pub scopes: Option<Vec<String>>,
    pub expires_in_days: Option<i64>,
}

// ─── Authentication Responses ─────────────────────────────────────────────────

#[derive(Debug, Serialize)]
pub struct AuthResponse {
    pub success: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub token: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub refresh_token: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub user: Option<PublicUserProfile>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct DeviceCodeResponse {
    pub device_code: String,
    pub user_code: String,
    pub verification_uri: String,
    pub expires_in: u64,
    pub interval: u64,
}

#[derive(Debug, Serialize)]
pub struct PatResponse {
    pub success: bool,
    pub id: String,
    pub name: String,
    pub token: String, // Plaintext returned only once upon creation
    pub prefix: String,
}

// ─── Ecosystem Apps & Session Governance Models ───────────────────────────────

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RegisteredApp {
    pub id: String,
    pub name: String,
    pub slug: String,
    pub description: Option<String>,
    pub allowed_redirect_uris: Vec<String>,
    pub allowed_scopes: Vec<String>,
    pub icon_url: Option<String>,
    pub is_first_party: bool,
    pub is_enabled: bool,
    pub created_at: String,
}

#[derive(Debug, Deserialize)]
pub struct CreateAppRequest {
    pub name: String,
    pub slug: Option<String>,
    pub description: Option<String>,
    pub allowed_redirect_uris: Option<Vec<String>>,
    pub allowed_scopes: Option<Vec<String>>,
    pub is_first_party: Option<bool>,
}

#[derive(Debug, Deserialize)]
pub struct UpdateAppRequest {
    pub name: Option<String>,
    pub description: Option<String>,
    pub allowed_redirect_uris: Option<Vec<String>>,
    pub allowed_scopes: Option<Vec<String>>,
    pub is_enabled: Option<bool>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ConnectedDeviceSession {
    pub id: String,
    pub device_name: String,
    pub device_type: String,
    pub client_app: String,
    pub ip_address: Option<String>,
    pub last_seen: String,
    pub created_at: String,
    pub is_current: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserAppGrantItem {
    pub id: String,
    pub app_id: String,
    pub app_name: String,
    pub app_slug: String,
    pub description: Option<String>,
    pub is_first_party: bool,
    pub granted_scopes: Vec<String>,
    pub created_at: String,
    pub last_used_at: String,
}

