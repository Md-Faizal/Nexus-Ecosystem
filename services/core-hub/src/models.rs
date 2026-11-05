use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Message {
    pub id: Option<i64>,
    pub name: String,
    pub email: String,
    pub subject: String,
    pub message: String,
    pub ip_address: Option<String>,
    pub created_at: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct SaveMessageRequest {
    pub name: String,
    pub email: String,
    pub subject: String,
    pub message: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Project {
    pub id: i64,
    pub title: Option<String>,
    pub description: Option<String>,
    pub link: Option<String>,
    pub created_at: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SettingItem {
    pub key: String,
    pub value: String,
}

#[derive(Debug, Deserialize)]
pub struct UpdateSettingRequest {
    pub key: String,
    pub value: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ProfileHistoryItem {
    pub id: i64,
    pub created_at: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct VisitorItem {
    pub id: Option<i64>,
    pub ip: Option<String>,
    pub user_agent: Option<String>,
    pub path: Option<String>,
    pub timestamp: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct TrackVisitRequest {
    pub path: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct AccessLogItem {
    pub id: i64,
    pub ip: Option<String>,
    pub status: Option<String>,
    pub timestamp: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct BannedIpItem {
    pub ip: String,
    pub reason: Option<String>,
    pub timestamp: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct BanIpRequest {
    pub ip: String,
    pub reason: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct VerifyPinRequest {
    pub pin: String,
}

#[derive(Debug, Deserialize)]
pub struct ChangePinRequest {
    pub current_pin: String,
    pub new_pin: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct HealthReportRecord {
    pub id: i64,
    pub timestamp: String,
    pub recipient: Option<String>,
    pub overall_health: Option<String>,
    pub error_count: Option<i64>,
    pub trigger_source: Option<String>,
    pub delivery_status: Option<String>,
    pub details: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct HealthConfigUpdate {
    pub recipient: Option<String>,
    pub enabled: Option<serde_json::Value>,
    pub time: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct SendReportRequest {
    pub recipient: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct ApiResponse<T> {
    pub success: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub data: Option<T>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub message: Option<String>,
}

impl<T> ApiResponse<T> {
    pub fn ok(data: T) -> Self {
        Self {
            success: true,
            data: Some(data),
            error: None,
            message: None,
        }
    }

    pub fn err(msg: impl Into<String>) -> Self {
        Self {
            success: false,
            data: None,
            error: Some(msg.into()),
            message: None,
        }
    }
}
