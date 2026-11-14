use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MeshFrame {
    pub frame_id: u64,
    pub node_id: String,
    pub timestamp: u64,
    pub msg: MeshMessage,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", content = "payload")]
#[serde(rename_all = "snake_case")]
pub enum MeshMessage {
    // ─── Connection Lifecycle ───
    NodeHello {
        hostname: String,
        os: String,
        os_version: String,
        arch: String,
        agent_version: String,
        features: HashMap<String, bool>,
        local_ips: Vec<String>,
    },
    Heartbeat {
        uptime_secs: u64,
        cpu_usage_pct: f32,
        memory_used_pct: f32,
    },
    HeartbeatAck {
        live_mode: bool, // true if user is actively watching dashboard -> throttle up
        server_time: u64,
    },
    HandshakeHealthy {
        version: String,
    },

    // ─── Telemetry ───
    TelemetryReport(Box<TelemetryPayload>),

    // ─── Remote Control & Commands ───
    CommandDispatch {
        command_id: String,
        action: String,
        args: Vec<String>,
        nonce: String,
    },
    CommandAck {
        command_id: String,
        success: bool,
        exit_code: i32,
        stdout: String,
        stderr: String,
        execution_ms: u64,
    },

    // ─── Local AI (Ollama) Tunnel ───
    AiDiscovery {
        engine: String,
        models: Vec<String>,
        vram_total_mb: u64,
        vram_used_mb: u64,
        status: String,
    },
    AiPrompt {
        request_id: String,
        model: String,
        prompt: String,
        system_prompt: Option<String>,
        temperature: Option<f32>,
    },
    AiStreamChunk {
        request_id: String,
        token: String,
        done: bool,
        error: Option<String>,
    },

    // ─── Dynamic Remote Config & Upgrades ───
    ConfigSync {
        features: HashMap<String, bool>,
        poll_interval_secs: Option<u64>,
    },
    UpgradeTrigger {
        target_version: String,
        download_url: String,
        sha256: String,
    },

    // ─── Crash Record Telemetry ───
    CrashReport(CrashReportPayload),

    // ─── Out-of-Band Mail Relay & Doomsday Drain ───
    MailDispatchTask {
        task_id: String,
        recipient: String,
        subject: String,
        html_body: String,
        text_body: Option<String>,
    },
    MailDispatchAck {
        task_id: String,
        success: bool,
        error: Option<String>,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CrashReportPayload {
    pub crash_id: String,
    pub hostname: String,
    pub agent_version: String,
    pub os: String,
    pub arch: String,
    pub component: String,
    pub kind: String,
    pub message: String,
    pub location: String,
    pub backtrace: String,
    pub occurred_at: String,
    pub recovered: bool,
    pub restart_attempt: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct TelemetryPayload {
    pub hostname: String,
    pub os_label: String,
    pub uptime_secs: u64,
    pub cpu: CpuTelemetry,
    pub memory: MemoryTelemetry,
    pub disks: Vec<DiskTelemetry>,
    pub network: NetworkTelemetry,
    pub gpu: Option<GpuTelemetry>,
    pub ai_engine: Option<AiEngineTelemetry>,
    pub thermals: Option<ThermalTelemetry>,
    pub services: Vec<ServiceTelemetry>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct CpuTelemetry {
    pub model: String,
    pub physical_cores: usize,
    pub logical_cores: usize,
    pub usage_pct: f32,
    pub load_avg: [f64; 3], // 1m, 5m, 15m
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct MemoryTelemetry {
    pub total_bytes: u64,
    pub used_bytes: u64,
    pub free_bytes: u64,
    pub available_bytes: u64,
    pub used_pct: f32,
    pub swap_total_bytes: u64,
    pub swap_used_bytes: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct DiskTelemetry {
    pub name: String,
    pub mount_point: String,
    pub fs_type: String,
    pub total_bytes: u64,
    pub available_bytes: u64,
    pub used_bytes: u64,
    pub used_pct: f32,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct NetworkTelemetry {
    pub primary_ip: String,
    pub rx_bytes_sec: u64,
    pub tx_bytes_sec: u64,
    pub total_rx_bytes: u64,
    pub total_tx_bytes: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct GpuTelemetry {
    pub vendor: String, // "NVIDIA", "AMD", "Apple", "Intel"
    pub name: String,
    pub utilization_pct: f32,
    pub vram_total_mb: u64,
    pub vram_used_mb: u64,
    pub temperature_c: Option<f32>,
    pub power_watts: Option<f32>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct AiEngineTelemetry {
    pub engine: String, // "ollama"
    pub endpoint: String,
    pub models: Vec<String>,
    pub active_model: Option<String>,
    pub status: String, // "ready", "generating", "idle", "offline"
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct ThermalTelemetry {
    pub cpu_temp_c: Option<f32>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct ServiceTelemetry {
    pub name: String,
    pub status: String, // "running", "stopped", "failed"
    pub pid: Option<u32>,
    pub memory_mb: Option<f64>,
}

