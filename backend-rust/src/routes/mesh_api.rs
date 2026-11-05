use crate::SharedState;
use axum::{
    extract::{
        ws::{Message, WebSocket, WebSocketUpgrade},
        Path, State,
    },
    http::StatusCode,
    response::{
        sse::{Event, KeepAlive, Sse},
        IntoResponse, Response,
    },
    Json,
};
use futures_util::{SinkExt, StreamExt};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::convert::Infallible;
use std::sync::Arc;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};
use tokio::sync::{mpsc, oneshot, RwLock};
use tracing::info;

// ─── Fleet Node Session ──────────────────────────────────────────────────────

#[derive(Clone, Serialize)]
pub struct NodeInfo {
    pub node_id: String,
    pub hostname: String,
    pub os: String,
    pub os_version: String,
    pub arch: String,
    pub agent_version: String,
    pub status: String, // "online" | "offline"
    pub last_seen_secs: u64,
    pub features: HashMap<String, bool>,
    pub models: Vec<String>,
    pub telemetry: Option<serde_json::Value>,
}

pub struct ActiveSession {
    pub node_id: String,
    pub hostname: String,
    pub os: String,
    pub os_version: String,
    pub arch: String,
    pub agent_version: String,
    pub features: HashMap<String, bool>,
    pub last_seen: Instant,
    pub models: Vec<String>,
    pub latest_telemetry: Option<serde_json::Value>,
    pub ws_tx: mpsc::Sender<String>,
    pub pending_commands: Arc<RwLock<HashMap<String, oneshot::Sender<CommandResult>>>>,
    pub active_ai_streams: Arc<RwLock<HashMap<String, mpsc::Sender<String>>>>,
}

#[derive(Clone, Serialize, Deserialize)]
pub struct CommandResult {
    pub command_id: String,
    pub success: bool,
    pub exit_code: i32,
    pub stdout: String,
    pub stderr: String,
    pub execution_ms: u64,
}

pub struct FleetHubState {
    pub sessions: RwLock<HashMap<String, ActiveSession>>,
    pub known_nodes: RwLock<HashMap<String, NodeInfo>>,
}

impl FleetHubState {
    pub fn new() -> Self {
        Self {
            sessions: RwLock::new(HashMap::new()),
            known_nodes: RwLock::new(HashMap::new()),
        }
    }
}

// ─── WebSocket Upgrade Handler ───────────────────────────────────────────────

pub async fn handle_mesh_ws(
    ws: WebSocketUpgrade,
    State(state): State<SharedState>,
) -> impl IntoResponse {
    ws.on_upgrade(move |socket| handle_node_connection(socket, state))
}

async fn handle_node_connection(socket: WebSocket, state: SharedState) {
    let (mut sink, mut stream) = socket.split();
    let (tx, mut rx) = mpsc::channel::<String>(100);

    // Forward outgoing messages over WebSocket
    let mut sink_task = tokio::spawn(async move {
        while let Some(msg) = rx.recv().await {
            if sink.send(Message::Text(msg.into())).await.is_err() {
                break;
            }
        }
    });

    let mut current_node_id: Option<String> = None;
    let pending_commands = Arc::new(RwLock::new(HashMap::<String, oneshot::Sender<CommandResult>>::new()));
    let active_ai_streams = Arc::new(RwLock::new(HashMap::<String, mpsc::Sender<String>>::new()));

    while let Some(Ok(msg)) = stream.next().await {
        match msg {
            Message::Text(text) => {
                if let Ok(frame) = serde_json::from_str::<serde_json::Value>(&text) {
                    let node_id = frame.get("node_id").and_then(|v| v.as_str()).unwrap_or("unknown").to_string();
                    let msg_obj = frame.get("msg").cloned().unwrap_or_default();
                    let msg_type = msg_obj.get("type").and_then(|v| v.as_str()).unwrap_or("");
                    let payload = msg_obj.get("payload").cloned().unwrap_or_default();

                    current_node_id = Some(node_id.clone());

                    match msg_type {
                        "node_hello" => {
                            let hostname = payload.get("hostname").and_then(|v| v.as_str()).unwrap_or("unknown").to_string();
                            let os = payload.get("os").and_then(|v| v.as_str()).unwrap_or("unknown").to_string();
                            let os_ver = payload.get("os_version").and_then(|v| v.as_str()).unwrap_or("").to_string();
                            let arch = payload.get("arch").and_then(|v| v.as_str()).unwrap_or("unknown").to_string();
                            let agent_ver = payload.get("agent_version").and_then(|v| v.as_str()).unwrap_or("0.1.0").to_string();
                            let features: HashMap<String, bool> = serde_json::from_value(payload.get("features").cloned().unwrap_or(serde_json::json!({}))).unwrap_or_default();

                            info!("Fleet Node Connected: {} ({}, {})", node_id, hostname, os);

                            let session = ActiveSession {
                                node_id: node_id.clone(),
                                hostname: hostname.clone(),
                                os: os.clone(),
                                os_version: os_ver.clone(),
                                arch: arch.clone(),
                                agent_version: agent_ver.clone(),
                                features: features.clone(),
                                last_seen: Instant::now(),
                                models: vec![],
                                latest_telemetry: None,
                                ws_tx: tx.clone(),
                                pending_commands: pending_commands.clone(),
                                active_ai_streams: active_ai_streams.clone(),
                            };

                            let mut sessions = state.fleet.sessions.write().await;
                            sessions.insert(node_id.clone(), session);

                            let mut known = state.fleet.known_nodes.write().await;
                            known.insert(
                                node_id.clone(),
                                NodeInfo {
                                    node_id: node_id.clone(),
                                    hostname,
                                    os,
                                    os_version: os_ver,
                                    arch,
                                    agent_version: agent_ver,
                                    status: "online".into(),
                                    last_seen_secs: 0,
                                    features,
                                    models: vec![],
                                    telemetry: None,
                                },
                            );
                        }

                        "heartbeat" => {
                            let mut sessions = state.fleet.sessions.write().await;
                            if let Some(session) = sessions.get_mut(&node_id) {
                                session.last_seen = Instant::now();
                            }

                            // Reply HeartbeatAck
                            let ack = serde_json::json!({
                                "frame_id": 0,
                                "node_id": "hub",
                                "timestamp": SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() as u64,
                                "msg": {
                                    "type": "heartbeat_ack",
                                    "payload": {
                                        "live_mode": false,
                                        "server_time": SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs()
                                    }
                                }
                            });
                            let _ = tx.send(ack.to_string()).await;
                        }

                        "telemetry_report" => {
                            let mut sessions = state.fleet.sessions.write().await;
                            if let Some(session) = sessions.get_mut(&node_id) {
                                session.last_seen = Instant::now();
                                session.latest_telemetry = Some(payload.clone());

                                if let Some(ai) = payload.get("ai_engine") {
                                    if let Some(models) = ai.get("models").and_then(|m| m.as_array()) {
                                        session.models = models.iter().filter_map(|v| v.as_str().map(|s| s.to_string())).collect();
                                    }
                                }
                            }

                            let mut known = state.fleet.known_nodes.write().await;
                            if let Some(k) = known.get_mut(&node_id) {
                                k.telemetry = Some(payload);
                                k.status = "online".into();
                                k.last_seen_secs = 0;
                            }
                        }

                        "command_ack" => {
                            if let Ok(ack) = serde_json::from_value::<CommandResult>(payload) {
                                let mut pending = pending_commands.write().await;
                                if let Some(sender) = pending.remove(&ack.command_id) {
                                    let _ = sender.send(ack);
                                }
                            }
                        }

                        "ai_stream_chunk" => {
                            let req_id = payload.get("request_id").and_then(|v| v.as_str()).unwrap_or("");
                            let token = payload.get("token").and_then(|v| v.as_str()).unwrap_or("");
                            let done = payload.get("done").and_then(|v| v.as_bool()).unwrap_or(false);

                            let streams = active_ai_streams.read().await;
                            if let Some(stream_tx) = streams.get(req_id) {
                                let _ = stream_tx.send(token.to_string()).await;
                                if done {
                                    drop(streams);
                                    let mut streams_mut = active_ai_streams.write().await;
                                    streams_mut.remove(req_id);
                                }
                            }
                        }

                        _ => {}
                    }
                }
            }
            Message::Close(_) => break,
            _ => {}
        }
    }

    if let Some(node_id) = current_node_id {
        info!("Fleet Node Disconnected: {}", node_id);
        let mut sessions = state.fleet.sessions.write().await;
        sessions.remove(&node_id);

        let mut known = state.fleet.known_nodes.write().await;
        if let Some(k) = known.get_mut(&node_id) {
            k.status = "offline".into();
        }
    }

    sink_task.abort();
}

// ─── REST Endpoints for Admin Panel ──────────────────────────────────────────

pub async fn list_nodes(State(state): State<SharedState>) -> Json<Vec<NodeInfo>> {
    let mut known = state.fleet.known_nodes.read().await.clone();
    let sessions = state.fleet.sessions.read().await;

    let mut result = Vec::new();
    for (id, mut node) in known.drain() {
        if let Some(sess) = sessions.get(&id) {
            node.status = "online".into();
            node.last_seen_secs = sess.last_seen.elapsed().as_secs();
            node.models = sess.models.clone();
            node.telemetry = sess.latest_telemetry.clone();
        } else {
            node.status = "offline".into();
        }
        result.push(node);
    }

    // Sort: online first, then by hostname
    result.sort_by(|a, b| {
        b.status.cmp(&a.status).then_with(|| a.hostname.cmp(&b.hostname))
    });

    Json(result)
}

#[derive(Deserialize)]
pub struct CommandPayload {
    pub action: String,
    #[serde(default)]
    pub args: Vec<String>,
}

pub async fn dispatch_command(
    State(state): State<SharedState>,
    Path(node_id): Path<String>,
    Json(payload): Json<CommandPayload>,
) -> impl IntoResponse {
    let sessions = state.fleet.sessions.read().await;
    let session = match sessions.get(&node_id) {
        Some(s) => s,
        None => return (StatusCode::NOT_FOUND, Json(serde_json::json!({"error": "Node not connected"}))),
    };

    let command_id = uuid::Uuid::new_v4().to_string();
    let (tx, rx) = oneshot::channel();

    {
        let mut pending = session.pending_commands.write().await;
        pending.insert(command_id.clone(), tx);
    }

    let frame = serde_json::json!({
        "frame_id": 1,
        "node_id": "hub",
        "timestamp": SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() as u64,
        "msg": {
            "type": "command_dispatch",
            "payload": {
                "command_id": command_id.clone(),
                "action": payload.action,
                "args": payload.args,
                "nonce": uuid::Uuid::new_v4().to_string(),
            }
        }
    });

    if session.ws_tx.send(frame.to_string()).await.is_err() {
        return (StatusCode::BAD_GATEWAY, Json(serde_json::json!({"error": "Failed to dispatch command"})));
    }

    match tokio::time::timeout(Duration::from_secs(8), rx).await {
        Ok(Ok(res)) => (StatusCode::OK, Json(serde_json::to_value(res).unwrap())),
        _ => (StatusCode::GATEWAY_TIMEOUT, Json(serde_json::json!({"error": "Command execution timed out"}))),
    }
}

#[derive(Deserialize)]
pub struct FeatureUpdatePayload {
    pub features: HashMap<String, bool>,
    pub poll_interval_secs: Option<u64>,
}

pub async fn update_node_features(
    State(state): State<SharedState>,
    Path(node_id): Path<String>,
    Json(payload): Json<FeatureUpdatePayload>,
) -> impl IntoResponse {
    let sessions = state.fleet.sessions.read().await;
    let session = match sessions.get(&node_id) {
        Some(s) => s,
        None => return (StatusCode::NOT_FOUND, Json(serde_json::json!({"error": "Node not connected"}))),
    };

    let frame = serde_json::json!({
        "frame_id": 2,
        "node_id": "hub",
        "timestamp": SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() as u64,
        "msg": {
            "type": "config_sync",
            "payload": {
                "features": payload.features,
                "poll_interval_secs": payload.poll_interval_secs
            }
        }
    });

    let _ = session.ws_tx.send(frame.to_string()).await;
    (StatusCode::OK, Json(serde_json::json!({"status": "updated"})))
}

// ─── AI Streaming Endpoint (SSE) ─────────────────────────────────────────────

#[derive(Deserialize)]
pub struct AiPromptRequest {
    pub model: String,
    pub prompt: String,
    pub system_prompt: Option<String>,
    pub temperature: Option<f32>,
}

pub async fn stream_ai_prompt(
    State(state): State<SharedState>,
    Path(node_id): Path<String>,
    Json(payload): Json<AiPromptRequest>,
) -> Response {
    let sessions = state.fleet.sessions.read().await;
    let session = match sessions.get(&node_id) {
        Some(s) => s,
        None => return (StatusCode::NOT_FOUND, "Node offline").into_response(),
    };

    let request_id = uuid::Uuid::new_v4().to_string();
    let (tx, mut rx) = mpsc::channel::<String>(100);

    {
        let mut streams = session.active_ai_streams.write().await;
        streams.insert(request_id.clone(), tx);
    }

    let frame = serde_json::json!({
        "frame_id": 3,
        "node_id": "hub",
        "timestamp": SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() as u64,
        "msg": {
            "type": "ai_prompt",
            "payload": {
                "request_id": request_id,
                "model": payload.model,
                "prompt": payload.prompt,
                "system_prompt": payload.system_prompt,
                "temperature": payload.temperature,
            }
        }
    });

    if session.ws_tx.send(frame.to_string()).await.is_err() {
        return (StatusCode::BAD_GATEWAY, "Failed to send prompt to node").into_response();
    }

    let stream = tokio_stream::wrappers::ReceiverStream::new(rx)
        .map(|chunk| Ok::<_, Infallible>(Event::default().data(chunk)));

    Sse::new(stream).keep_alive(KeepAlive::default()).into_response()
}

#[derive(Deserialize)]
pub struct ModelVramActionPayload {
    pub model: String,
    pub keep_alive: Option<String>,
}

pub async fn load_model_vram(
    State(state): State<SharedState>,
    Path(node_id): Path<String>,
    Json(payload): Json<ModelVramActionPayload>,
) -> impl IntoResponse {
    let dur = payload.keep_alive.unwrap_or_else(|| "15m".into());
    let cmd = CommandPayload {
        action: "ollama_load".into(),
        args: vec![payload.model, dur],
    };
    dispatch_command(State(state), Path(node_id), Json(cmd)).await
}

pub async fn unload_model_vram(
    State(state): State<SharedState>,
    Path(node_id): Path<String>,
    Json(payload): Json<ModelVramActionPayload>,
) -> impl IntoResponse {
    let cmd = CommandPayload {
        action: "ollama_unload".into(),
        args: vec![payload.model],
    };
    dispatch_command(State(state), Path(node_id), Json(cmd)).await
}

pub async fn get_vram_processes(
    State(state): State<SharedState>,
    Path(node_id): Path<String>,
) -> impl IntoResponse {
    let cmd = CommandPayload {
        action: "ollama_ps".into(),
        args: vec![],
    };
    dispatch_command(State(state), Path(node_id), Json(cmd)).await
}

// ─── Public 1-Command Installers ─────────────────────────────────────────────

pub async fn serve_install_sh() -> impl IntoResponse {
    let script = r#"#!/usr/bin/env bash
# Nexus Fleet Agent — Universal Zero-Token 1-Command Installer (Linux & macOS)
set -euo pipefail

HUB_URL="${NEXUS_HUB:-https://faizal.uk}"
OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
ARCH="$(uname -m)"

case "$ARCH" in
  x86_64) ARCH_TAG="amd64" ;;
  aarch64|arm64) ARCH_TAG="arm64" ;;
  *) echo "Unsupported architecture: $ARCH"; exit 1 ;;
esac

BIN_URL="$HUB_URL/releases/nexus-agent-${OS}-${ARCH_TAG}?v=$(date +%s)"
echo "=== Installing Nexus Fleet Agent ($OS-$ARCH_TAG) ==="
echo "Downloading binary from $BIN_URL..."

if [ "$(id -u)" -eq 0 ]; then
  INSTALL_DIR="/usr/local/bin"
  curl -fsSL "$BIN_URL" -o "$INSTALL_DIR/nexus-agent"
  chmod +x "$INSTALL_DIR/nexus-agent"
  ln -sf "$INSTALL_DIR/nexus-agent" "$INSTALL_DIR/nexus"
  ln -sf "$INSTALL_DIR/nexus-agent" "$INSTALL_DIR/nx"
  "$INSTALL_DIR/nexus" service install
  echo "Nexus Agent successfully installed and running as a system-wide service!"
else
  INSTALL_DIR="$HOME/.local/bin"
  mkdir -p "$INSTALL_DIR"
  curl -fsSL "$BIN_URL" -o "$INSTALL_DIR/nexus-agent"
  chmod +x "$INSTALL_DIR/nexus-agent"
  ln -sf "$INSTALL_DIR/nexus-agent" "$INSTALL_DIR/nexus"
  ln -sf "$INSTALL_DIR/nexus-agent" "$INSTALL_DIR/nx"
  
  # Ensure linger is enabled so user service runs headlessly even after logout
  loginctl enable-linger "$USER" 2>/dev/null || true
  
  "$INSTALL_DIR/nexus" service install --user
  echo "Nexus Agent successfully installed and running in User Mode for $USER!"
  echo "Tip: Run 'nexus elevate' or 'sudo nexus service install' anytime to upgrade to a system service."
fi

echo "Telemetry is actively streaming to $HUB_URL."
echo "You can now run 'nexus' or 'nx' directly in your terminal:"
echo "  nexus run       - Start background telemetry daemon (if stopped)"
echo "  nexus status    - View live node health and telemetry"
echo "  nexus cluster   - Inspect 3-VM cluster (my-mac, cos, pro)"
echo "  nexus services  - View running containers, web engines, and games"
echo "  nexus chat      - Start interactive AI chat & slash console"
echo "Your node will automatically appear on https://faizal.uk/admin.html immediately."
"#;

    (
        [("content-type", "application/x-sh")],
        script,
    )
}

pub async fn serve_install_ps1() -> impl IntoResponse {
    let script = r#"# Nexus Fleet Agent — Universal Zero-Token 1-Command Installer (Windows)
$ErrorActionPreference = "Stop"

$HubUrl = if ($env:NEXUS_HUB) { $env:NEXUS_HUB } else { "https://faizal.uk" }
$IsAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if ($IsAdmin) {
    $TargetDir = "$env:ProgramFiles\NexusAgent"
    Write-Host "=== Installing Nexus Fleet Agent (System Administrator Mode) ===" -ForegroundColor Cyan
} else {
    $TargetDir = "$env:LOCALAPPDATA\Programs\NexusAgent"
    Write-Host "=== Installing Nexus Fleet Agent (User Mode) ===" -ForegroundColor Yellow
    Write-Host "Note: Installing without admin privileges. Run 'nexus elevate' anytime to upgrade." -ForegroundColor Gray
}

New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null

$CacheBuster = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$BinUrl = "$HubUrl/releases/nexus-agent-windows-amd64.exe?v=$CacheBuster"
$DestExe = "$TargetDir\nexus-agent.exe"
$NexusAlias = "$TargetDir\nexus.exe"
$NxAlias = "$TargetDir\nx.exe"

Write-Host "Downloading Nexus Agent from $BinUrl..." -ForegroundColor Gray
Stop-Process -Name "nexus-agent" -Force -ErrorAction SilentlyContinue
Stop-Process -Name "nexus" -Force -ErrorAction SilentlyContinue
Start-Sleep -Milliseconds 250

if (Get-Command curl.exe -ErrorAction SilentlyContinue) {
    curl.exe -fsSL "$BinUrl" -o "$DestExe"
} else {
    Invoke-WebRequest -Uri $BinUrl -OutFile $DestExe -UseBasicParsing
}

# Unblock downloaded binary from Windows Mark-of-the-Web / Application Control
Unblock-File -Path $DestExe -ErrorAction SilentlyContinue
if (Test-Path "$DestExe:Zone.Identifier") {
    Remove-Item "$DestExe:Zone.Identifier" -ErrorAction SilentlyContinue
}

if (-not (Test-Path $DestExe) -or (Get-Item $DestExe).Length -lt 100000) {
    Write-Error "Downloaded binary appears invalid or incomplete."
    exit 1
}

# Create ergonomic 'nexus.exe' and 'nx.exe' shims/copies
Copy-Item -Path $DestExe -Destination $NexusAlias -Force -ErrorAction SilentlyContinue
Copy-Item -Path $DestExe -Destination $NxAlias -Force -ErrorAction SilentlyContinue
Unblock-File -Path $NexusAlias -ErrorAction SilentlyContinue
Unblock-File -Path $NxAlias -ErrorAction SilentlyContinue

Write-Host "Configuring system PATH environment variable..." -ForegroundColor Gray
if ($IsAdmin) {
    try {
        $MachinePath = [Environment]::GetEnvironmentVariable("Path", [System.EnvironmentVariableTarget]::Machine)
        if ($MachinePath -notlike "*$TargetDir*") {
            [Environment]::SetEnvironmentVariable("Path", "$MachinePath;$TargetDir", [System.EnvironmentVariableTarget]::Machine)
        }
    } catch {}
}

try {
    $UserPath = [Environment]::GetEnvironmentVariable("Path", [System.EnvironmentVariableTarget]::User)
    if ($UserPath -notlike "*$TargetDir*") {
        [Environment]::SetEnvironmentVariable("Path", "$UserPath;$TargetDir", [System.EnvironmentVariableTarget]::User)
    }
} catch {}

if ($env:Path -notlike "*$TargetDir*") {
    $env:Path = "$env:Path;$TargetDir"
}

Write-Host "Registering Nexus background service and startup daemon..." -ForegroundColor Gray
if ($IsAdmin) {
    & $DestExe service install
} else {
    # Non-admin startup registry registration
    try {
        Set-ItemProperty -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run" -Name "NexusAgent" -Value "`"$NexusAlias`" run" -Force
    } catch {}
    # Launch hidden background worker immediately so telemetry is active
    Start-Process "$NexusAlias" -ArgumentList "run" -WindowStyle Hidden
}

Write-Host "Nexus Agent successfully installed and running!" -ForegroundColor Green
Write-Host "Telemetry is streaming to $HubUrl." -ForegroundColor Green
Write-Host "You can now run 'nexus' or 'nx' directly in any terminal (CMD / PowerShell):" -ForegroundColor Cyan
Write-Host "  nexus run       - Start background telemetry daemon (if stopped)" -ForegroundColor White
Write-Host "  nexus status    - View live system health and telemetry" -ForegroundColor White
Write-Host "  nexus cluster   - Inspect the 3-VM cluster (my-mac, cos, pro)" -ForegroundColor White
Write-Host "  nexus services  - View running containers, web engines, and games" -ForegroundColor White
Write-Host "  nexus chat      - Interactive local AI session" -ForegroundColor White
if (-not $IsAdmin) {
    Write-Host "  nexus elevate   - Upgrade to Administrator System Service at any time" -ForegroundColor Yellow
}
Write-Host "Your PC will appear on your Personal Site Admin Panel (https://faizal.uk/admin.html) immediately." -ForegroundColor Cyan
"#;

    (
        [("content-type", "text/plain; charset=utf-8")],
        script,
    )
}
