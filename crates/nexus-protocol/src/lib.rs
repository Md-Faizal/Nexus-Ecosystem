use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// Outer WebSocket frame envelope used on `/api/mesh/ws`.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct MeshEnvelope {
    pub frame_id: u64,
    pub node_id: String,
    pub timestamp: u64,
    pub msg: MeshMessage,
}

/// Tagged message payload matching `services/core-hub/src/routes/mesh_api.rs`.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(tag = "type", content = "payload", rename_all = "snake_case")]
pub enum MeshMessage {
    NodeHello(NodeHelloPayload),
    Heartbeat(HeartbeatPayload),
    HeartbeatAck(HeartbeatAckPayload),
    TelemetryReport(TelemetryReportPayload),
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct NodeHelloPayload {
    pub hostname: String,
    pub os: String,
    pub os_version: String,
    pub arch: String,
    pub agent_version: String,
    #[serde(default)]
    pub features: HashMap<String, bool>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Default)]
pub struct HeartbeatPayload {
    #[serde(default)]
    pub uptime_secs: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct HeartbeatAckPayload {
    pub live_mode: bool,
    pub server_time: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Default)]
pub struct TelemetryReportPayload {
    pub battery_pct: Option<u8>,
    pub is_charging: Option<bool>,
    pub network_type: Option<String>,
    pub mem_used_mb: Option<u64>,
    pub mem_total_mb: Option<u64>,
}

/// Summary of a fleet node returned by `/fleet/nodes`.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct FleetNodeSummary {
    pub node_id: String,
    pub hostname: String,
    pub os: String,
    pub os_version: String,
    pub arch: String,
    pub agent_version: String,
    pub status: String,
    pub last_seen_secs: u64,
}

/// RFC 8628 Device Authorization Grant request.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeviceCodeRequest {
    pub client_id: String,
    pub device_id: String,
    pub scope: String,
}

/// RFC 8628 Device Authorization Grant response.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeviceCodeResponse {
    pub device_code: String,
    pub user_code: String,
    pub verification_uri: String,
    #[serde(default = "default_expires_in")]
    pub expires_in: u64,
    #[serde(default = "default_interval")]
    pub interval: u64,
}

fn default_expires_in() -> u64 {
    300
}

fn default_interval() -> u64 {
    5
}

/// RFC 8628 token polling request.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeviceTokenRequest {
    pub grant_type: String,
    pub device_code: String,
    pub client_id: String,
}

/// Public user profile returned by Nexus Auth (`/api/auth/me`, `/api/auth/device/token`).
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct NexusUserProfile {
    pub id: String,
    pub email: String,
    pub username: Option<String>,
    pub full_name: Option<String>,
    pub avatar_url: Option<String>,
    pub role: String,
}

/// Token response on successful RFC 8628 pairing from `services/core-hub`.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeviceTokenResponse {
    pub success: bool,
    pub token: Option<String>,
    pub user: Option<NexusUserProfile>,
}

/// Self-hosted Android OTA release manifest hosted at `/releases/mobile-latest.json`.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct MobileReleaseManifest {
    pub version_code: u32,
    pub version_name: String,
    pub release_notes: String,
    pub apk_url: String,
    pub sha256: String,
    #[serde(default)]
    pub mandatory: bool,
    #[serde(default)]
    pub published_at: String,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn roundtrip_node_hello_matches_hub_schema() {
        let env = MeshEnvelope {
            frame_id: 1,
            node_id: "nexus-mobile-test".into(),
            timestamp: 1700000000000,
            msg: MeshMessage::NodeHello(NodeHelloPayload {
                hostname: "Pixel-9-Pro".into(),
                os: "android".into(),
                os_version: "15".into(),
                arch: "aarch64".into(),
                agent_version: "2.0.0".into(),
                features: HashMap::from([("mesh_node".into(), true)]),
            }),
        };

        let json = serde_json::to_value(&env).expect("serialize");
        assert_eq!(json["node_id"], "nexus-mobile-test");
        assert_eq!(json["msg"]["type"], "node_hello");
        assert_eq!(json["msg"]["payload"]["os"], "android");

        let decoded: MeshEnvelope = serde_json::from_value(json).expect("deserialize");
        assert_eq!(decoded, env);
    }

    #[test]
    fn parse_hub_heartbeat_ack() {
        let raw = r#"{
            "frame_id": 0,
            "node_id": "hub",
            "timestamp": 1700000001000,
            "msg": {
                "type": "heartbeat_ack",
                "payload": {
                    "live_mode": false,
                    "server_time": 1700000001
                }
            }
        }"#;
        let env: MeshEnvelope = serde_json::from_str(raw).expect("parse heartbeat_ack");
        assert!(matches!(
            env.msg,
            MeshMessage::HeartbeatAck(HeartbeatAckPayload {
                live_mode: false,
                server_time: 1700000001
            })
        ));
    }
}
