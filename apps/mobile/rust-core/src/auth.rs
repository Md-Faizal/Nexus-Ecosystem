use nexus_protocol::{
    DeviceCodeRequest, DeviceCodeResponse, DeviceTokenRequest, DeviceTokenResponse,
    FleetNodeSummary, NexusUserProfile,
};
use reqwest::Client;
use sha2::{Digest, Sha256};
use subtle::ConstantTimeEq;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum AuthError {
    #[error("network error: {0}")]
    Http(#[from] reqwest::Error),
    #[error("hub returned status {0}: {1}")]
    Api(u16, String),
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct PairingCodeInfo {
    pub device_code: String,
    pub user_code: String,
    pub verification_uri: String,
    pub expires_in_secs: u64,
    pub interval_secs: u64,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct AuthenticatedSession {
    pub token: String,
    pub user_id: String,
    pub email: String,
    pub display_name: String,
    pub role: String,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct FleetNodeView {
    pub node_id: String,
    pub hostname: String,
    pub os: String,
    pub arch: String,
    pub agent_version: String,
    pub status: String,
    pub last_seen_secs: u64,
}

impl From<FleetNodeSummary> for FleetNodeView {
    fn from(n: FleetNodeSummary) -> Self {
        Self {
            node_id: n.node_id,
            hostname: n.hostname,
            os: n.os,
            arch: n.arch,
            agent_version: n.agent_version,
            status: n.status,
            last_seen_secs: n.last_seen_secs,
        }
    }
}

/// Key-stretched salted SHA-256 PIN hash (10,000 rounds) for local device unlock.
pub fn hash_pin(pin: &str, salt: &str) -> String {
    let mut state = Sha256::new();
    state.update(b"nexus-mobile-pin-v1:");
    state.update(salt.as_bytes());
    state.update(b":");
    state.update(pin.as_bytes());
    let mut digest = state.finalize();

    for _ in 0..10_000 {
        let mut h = Sha256::new();
        h.update(digest);
        h.update(salt.as_bytes());
        digest = h.finalize();
    }
    hex::encode(digest)
}

/// Constant-time PIN verification.
pub fn verify_pin(pin: &str, salt: &str, expected_hex: &str) -> bool {
    let computed = hash_pin(pin, salt);
    computed.as_bytes().ct_eq(expected_hex.as_bytes()).into()
}

pub struct HubApiClient {
    http: Client,
    base_url: String,
}

impl HubApiClient {
    pub fn new(base_url: String) -> Self {
        let http = Client::builder()
            .user_agent("NexusMobile/2.0 (Rust)")
            .build()
            .expect("reqwest rustls client");
        Self {
            http,
            base_url: base_url.trim_end_matches('/').to_string(),
        }
    }

    pub async fn request_device_code(&self, device_id: &str) -> Result<PairingCodeInfo, AuthError> {
        let req = DeviceCodeRequest {
            client_id: "nexus_mobile_companion".into(),
            device_id: device_id.into(),
            scope: "profile:read fleet:read".into(),
        };
        let resp = self
            .http
            .post(format!("{}/api/auth/device/code", self.base_url))
            .json(&req)
            .send()
            .await?;
        let status = resp.status();
        if !status.is_success() {
            let body = resp.text().await.unwrap_or_default();
            return Err(AuthError::Api(status.as_u16(), body));
        }
        let parsed: DeviceCodeResponse = resp.json().await?;
        Ok(PairingCodeInfo {
            device_code: parsed.device_code,
            user_code: parsed.user_code,
            verification_uri: parsed.verification_uri,
            expires_in_secs: parsed.expires_in,
            interval_secs: parsed.interval,
        })
    }

    pub async fn poll_device_token(
        &self,
        device_code: &str,
    ) -> Result<Option<AuthenticatedSession>, AuthError> {
        let req = DeviceTokenRequest {
            grant_type: "urn:ietf:params:oauth:grant-type:device_code".into(),
            device_code: device_code.into(),
            client_id: "nexus_mobile_companion".into(),
        };
        let resp = self
            .http
            .post(format!("{}/api/auth/device/token", self.base_url))
            .json(&req)
            .send()
            .await?;
        let status = resp.status();
        if status.is_success() {
            let parsed: DeviceTokenResponse = resp.json().await?;
            if let (Some(token), Some(user)) = (parsed.token, parsed.user) {
                let display_name = user
                    .full_name
                    .clone()
                    .or_else(|| user.username.clone())
                    .unwrap_or_else(|| user.email.clone());
                return Ok(Some(AuthenticatedSession {
                    token,
                    user_id: user.id,
                    email: user.email,
                    display_name,
                    role: user.role,
                }));
            }
            return Ok(None);
        }
        if status.as_u16() == 400 {
            let body = resp.text().await.unwrap_or_default();
            if body.contains("authorization_pending") || body.contains("slow_down") {
                return Ok(None);
            }
            return Err(AuthError::Api(400, body));
        }
        let body = resp.text().await.unwrap_or_default();
        Err(AuthError::Api(status.as_u16(), body))
    }

    pub async fn fetch_me(&self, access_token: &str) -> Result<NexusUserProfile, AuthError> {
        let resp = self
            .http
            .get(format!("{}/api/auth/me", self.base_url))
            .bearer_auth(access_token)
            .send()
            .await?;
        let status = resp.status();
        if !status.is_success() {
            let body = resp.text().await.unwrap_or_default();
            return Err(AuthError::Api(status.as_u16(), body));
        }
        Ok(resp.json().await?)
    }

    pub async fn fetch_fleet_nodes(
        &self,
        access_token: Option<&str>,
    ) -> Result<Vec<FleetNodeView>, AuthError> {
        let mut req = self.http.get(format!("{}/api/fleet/nodes", self.base_url));
        if let Some(tok) = access_token {
            req = req.bearer_auth(tok);
        }
        let resp = req.send().await?;
        let status = resp.status();
        if !status.is_success() {
            let body = resp.text().await.unwrap_or_default();
            return Err(AuthError::Api(status.as_u16(), body));
        }
        let nodes: Vec<FleetNodeSummary> = resp.json().await?;
        Ok(nodes.into_iter().map(FleetNodeView::from).collect())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pin_hash_and_constant_time_verify() {
        let hash = hash_pin("2580", "device-salt-123");
        assert!(verify_pin("2580", "device-salt-123", &hash));
        assert!(!verify_pin("2581", "device-salt-123", &hash));
        assert!(!verify_pin("2580", "other-salt", &hash));
    }
}
