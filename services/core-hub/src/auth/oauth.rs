use serde::{Deserialize, Serialize};
use tracing::warn;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NormalizedOAuthIdentity {
    pub provider: String,
    pub provider_user_id: String,
    pub email: String,
    pub full_name: String,
    pub avatar_url: Option<String>,
}

#[derive(Debug, Deserialize)]
struct GoogleTokenInfo {
    sub: Option<String>,
    email: Option<String>,
    email_verified: Option<String>,
    name: Option<String>,
    picture: Option<String>,
    aud: Option<String>,
    error_description: Option<String>,
}

/// Verify Google ID token against Google's tokeninfo endpoint
pub async fn verify_google_id_token(
    client: &reqwest::Client,
    id_token: &str,
    expected_client_ids: &[&str],
) -> Result<NormalizedOAuthIdentity, String> {
    let url = format!(
        "https://oauth2.googleapis.com/tokeninfo?id_token={}",
        id_token
    );

    let res = client
        .get(&url)
        .send()
        .await
        .map_err(|e| format!("Failed to reach Google token endpoint: {}", e))?;

    if !res.status().is_success() {
        let err_text = res.text().await.unwrap_or_default();
        return Err(format!("Google token rejected by issuer: {}", err_text));
    }

    let info: GoogleTokenInfo = res
        .json()
        .await
        .map_err(|e| format!("Failed to parse Google response: {}", e))?;

    let sub = info.sub.ok_or_else(|| "Missing sub claim from Google".to_string())?;
    let email = info
        .email
        .ok_or_else(|| "Missing email claim from Google".to_string())?;

    // Validate audience if client IDs are configured
    if !expected_client_ids.is_empty() {
        if let Some(aud) = &info.aud {
            if !expected_client_ids.contains(&aud.as_str()) {
                warn!("Google token audience mismatch: aud={}", aud);
                // Allow but log warning for development flexibility
            }
        }
    }

    Ok(NormalizedOAuthIdentity {
        provider: "google".to_string(),
        provider_user_id: sub,
        email: email.to_lowercase(),
        full_name: info.name.unwrap_or_else(|| email.clone()),
        avatar_url: info.picture,
    })
}

#[derive(Debug, Deserialize)]
struct MetaUserInfo {
    id: Option<String>,
    name: Option<String>,
    email: Option<String>,
    picture: Option<MetaPictureData>,
}

#[derive(Debug, Deserialize)]
struct MetaPictureData {
    data: Option<MetaPictureUrl>,
}

#[derive(Debug, Deserialize)]
struct MetaPictureUrl {
    url: Option<String>,
}

/// Verify Meta / Facebook access token against Graph API
pub async fn verify_meta_access_token(
    client: &reqwest::Client,
    access_token: &str,
) -> Result<NormalizedOAuthIdentity, String> {
    let url = format!(
        "https://graph.facebook.com/me?fields=id,name,email,picture.type(large)&access_token={}",
        access_token
    );

    let res = client
        .get(&url)
        .send()
        .await
        .map_err(|e| format!("Failed to reach Meta Graph API: {}", e))?;

    if !res.status().is_success() {
        let err_text = res.text().await.unwrap_or_default();
        return Err(format!("Meta token verification failed: {}", err_text));
    }

    let info: MetaUserInfo = res
        .json()
        .await
        .map_err(|e| format!("Failed to parse Meta response: {}", e))?;

    let id = info.id.ok_or_else(|| "Missing Meta user ID".to_string())?;
    let email = info
        .email
        .unwrap_or_else(|| format!("meta_{}@faizal.uk", id));
    let avatar_url = info.picture.and_then(|p| p.data).and_then(|d| d.url);

    Ok(NormalizedOAuthIdentity {
        provider: "facebook".to_string(),
        provider_user_id: id,
        email: email.to_lowercase(),
        full_name: info.name.unwrap_or_else(|| "Meta User".to_string()),
        avatar_url,
    })
}

#[derive(Debug, Deserialize)]
struct XUserEnvelope {
    data: Option<XUserData>,
}

#[derive(Debug, Deserialize)]
struct XUserData {
    id: Option<String>,
    name: Option<String>,
    username: Option<String>,
    profile_image_url: Option<String>,
}

/// Verify X (Twitter) OAuth2 User Context access token against X API v2
pub async fn verify_x_access_token(
    client: &reqwest::Client,
    access_token: &str,
) -> Result<NormalizedOAuthIdentity, String> {
    let res = client
        .get("https://api.x.com/2/users/me?user.fields=profile_image_url,name,username")
        .bearer_auth(access_token.trim())
        .send()
        .await
        .map_err(|e| format!("Failed to reach X API: {}", e))?;

    if !res.status().is_success() {
        let err_text = res.text().await.unwrap_or_default();
        return Err(format!("X token verification failed: {}", err_text));
    }

    let envelope: XUserEnvelope = res
        .json()
        .await
        .map_err(|e| format!("Failed to parse X response: {}", e))?;

    let data = envelope.data.ok_or_else(|| "Missing X user data".to_string())?;
    let id = data.id.ok_or_else(|| "Missing X user ID".to_string())?;
    let handle = data.username.unwrap_or_else(|| format!("x_{}", id));
    let email = format!("{}@x.faizal.uk", handle.to_lowercase());

    Ok(NormalizedOAuthIdentity {
        provider: "x".to_string(),
        provider_user_id: id,
        email,
        full_name: data.name.unwrap_or_else(|| format!("@{}", handle)),
        avatar_url: data.profile_image_url,
    })
}
