use crate::SharedState;
use axum::{
    extract::{
        ws::{Message, WebSocket, WebSocketUpgrade},
        Path, Query, State,
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
    #[serde(default)]
    pub fitness: Option<serde_json::Value>,
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
    let sink_task = tokio::spawn(async move {
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

                            let db = state.db.clone();
                            let nid_db = node_id.clone();
                            let host_db = hostname.clone();
                            let os_db = os.clone();
                            let os_ver_db = os_ver.clone();
                            let arch_db = arch.clone();
                            let agent_ver_db = agent_ver.clone();
                            tokio::spawn(async move {
                                let conn = db.lock().await;
                                let _ = conn.execute(
                                    r#"INSERT INTO fleet_nodes_history (node_id, hostname, os, os_version, arch, agent_version, status, last_seen)
                                       VALUES (?1, ?2, ?3, ?4, ?5, ?6, 'online', CURRENT_TIMESTAMP)
                                       ON CONFLICT(node_id) DO UPDATE SET
                                           hostname = excluded.hostname,
                                           os = excluded.os,
                                           os_version = excluded.os_version,
                                           arch = excluded.arch,
                                           agent_version = excluded.agent_version,
                                           status = 'online',
                                           last_seen = CURRENT_TIMESTAMP"#,
                                    rusqlite::params![nid_db, host_db, os_db, os_ver_db, arch_db, agent_ver_db],
                                );
                            });

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
                                    fitness: None,
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
                            let mut models_for_db = Vec::new();
                            let mut sessions = state.fleet.sessions.write().await;
                            if let Some(session) = sessions.get_mut(&node_id) {
                                session.last_seen = Instant::now();
                                session.latest_telemetry = Some(payload.clone());

                                if let Some(ai) = payload.get("ai_engine") {
                                    if let Some(models) = ai.get("models").and_then(|m| m.as_array()) {
                                        session.models = models.iter().filter_map(|v| v.as_str().map(|s| s.to_string())).collect();
                                        models_for_db = session.models.clone();
                                    }
                                }
                            }

                            let db = state.db.clone();
                            let nid_db = node_id.clone();
                            let tel_str = payload.to_string();
                            let models_str = serde_json::to_string(&models_for_db).unwrap_or_default();
                            tokio::spawn(async move {
                                let conn = db.lock().await;
                                let _ = conn.execute(
                                    "UPDATE fleet_nodes_history SET last_telemetry = ?1, last_models = ?2, status = 'online', last_seen = CURRENT_TIMESTAMP WHERE node_id = ?3",
                                    rusqlite::params![tel_str, models_str, nid_db],
                                );
                            });

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

                        "crash_report" => {
                            let crash_id = payload.get("crash_id").and_then(|v| v.as_str()).unwrap_or("").to_string();
                            if !crash_id.is_empty() {
                                let hostname = payload.get("hostname").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                let agent_version = payload.get("agent_version").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                let os = payload.get("os").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                let arch = payload.get("arch").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                let component = payload.get("component").and_then(|v| v.as_str()).unwrap_or("unknown").to_string();
                                let kind = payload.get("kind").and_then(|v| v.as_str()).unwrap_or("panic").to_string();
                                let message = payload.get("message").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                let location = payload.get("location").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                let backtrace = payload.get("backtrace").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                let occurred_at = payload.get("occurred_at").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                let recovered = if payload.get("recovered").and_then(|v| v.as_bool()).unwrap_or(false) { 1i64 } else { 0i64 };
                                let restart_attempt = payload.get("restart_attempt").and_then(|v| v.as_i64()).unwrap_or(0);

                                let db = state.db.clone();
                                let nid_db = node_id.clone();
                                tokio::spawn(async move {
                                    let conn = db.lock().await;
                                    let _ = conn.execute(
                                        "INSERT OR IGNORE INTO fleet_crash_reports (crash_id, node_id, hostname, agent_version, os, arch, component, kind, message, location, backtrace, recovered, restart_attempt, occurred_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)",
                                        rusqlite::params![
                                            crash_id, nid_db, hostname, agent_version, os, arch, component, kind, message, location, backtrace, recovered, restart_attempt, occurred_at
                                        ],
                                    );
                                });
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

        let db = state.db.clone();
        let nid_db = node_id.clone();
        tokio::spawn(async move {
            let conn = db.lock().await;
            let _ = conn.execute(
                "UPDATE fleet_nodes_history SET status = 'offline', last_seen = CURRENT_TIMESTAMP WHERE node_id = ?1",
                rusqlite::params![nid_db],
            );
        });
    }

    sink_task.abort();
}

// ─── REST Endpoints for Admin Panel ──────────────────────────────────────────

pub async fn list_nodes(State(state): State<SharedState>) -> Json<Vec<NodeInfo>> {
    let sessions = state.fleet.sessions.read().await;
    let mut node_map: HashMap<String, NodeInfo> = HashMap::new();

    // 1. Fetch persisted node history from SQLite
    let conn = state.db.lock().await;
    if let Ok(mut stmt) = conn.prepare("SELECT node_id, hostname, os, os_version, arch, agent_version, status, last_seen, last_telemetry, last_models FROM fleet_nodes_history") {
        if let Ok(rows) = stmt.query_map([], |row| {
            let nid: String = row.get(0)?;
            let host: String = row.get(1)?;
            let os: String = row.get(2)?;
            let os_ver: String = row.get(3)?;
            let arch: String = row.get(4)?;
            let agent_ver: String = row.get(5)?;
            let _status: String = row.get(6)?;
            let _last_seen_str: String = row.get(7)?;
            let last_tel_str: Option<String> = row.get(8)?;
            let last_models_str: Option<String> = row.get(9)?;

            let telemetry: Option<serde_json::Value> = last_tel_str.and_then(|s| serde_json::from_str(&s).ok());
            let models: Vec<String> = last_models_str.and_then(|s| serde_json::from_str(&s).ok()).unwrap_or_default();

            Ok(NodeInfo {
                node_id: nid,
                hostname: host,
                os,
                os_version: os_ver,
                arch,
                agent_version: agent_ver,
                status: "offline".into(),
                last_seen_secs: 999999,
                features: HashMap::new(),
                models,
                telemetry,
                fitness: None,
            })
        }) {
            for r in rows.flatten() {
                node_map.insert(r.node_id.clone(), r);
            }
        }
    }
    drop(conn);

    // 2. Overlay live sessions from in-memory mesh state
    let known = state.fleet.known_nodes.read().await.clone();
    for (id, mut node) in known {
        if let Some(sess) = sessions.get(&id) {
            node.status = "online".into();
            node.last_seen_secs = sess.last_seen.elapsed().as_secs();
            node.models = sess.models.clone();
            node.telemetry = sess.latest_telemetry.clone();
        } else {
            node.status = "offline".into();
        }
        node_map.insert(id, node);
    }

    let mut result: Vec<NodeInfo> = node_map.into_values().collect();

    // 3. Attach Dynamic Fitness Percentage Score to each node
    for node in &mut result {
        let is_cloud = {
            let h = node.hostname.to_lowercase();
            let os = node.os.to_lowercase();
            h.contains("mac") || h.contains("cos") || h.contains("pro") || h.contains("oracle") || h.contains("gcp") || (os.contains("linux") && !h.contains("potato"))
        };

        let (cpu_usage, free_ram_pct) = if let Some(ref tel) = node.telemetry {
            let cpu = tel.get("cpu").and_then(|c| c.get("usage_pct")).and_then(|v| v.as_f64()).unwrap_or(15.0) as f32;
            let mem = tel.get("memory");
            let free_pct = if let Some(m) = mem {
                let total = m.get("total_mb").and_then(|v| v.as_f64()).unwrap_or(8000.0) as f32;
                let used = m.get("used_mb").and_then(|v| v.as_f64()).unwrap_or(4000.0) as f32;
                if total > 0.0 { ((total - used) / total * 100.0).clamp(0.0, 100.0) } else { 50.0 }
            } else {
                50.0
            };
            (cpu, free_pct)
        } else {
            (10.0, 60.0)
        };

        let cpu_headroom_pct = (100.0 - cpu_usage).clamp(0.0, 100.0);
        let network_health = if node.status == "online" { 100.0 } else { 0.0 };
        let resource_score = 0.40 * free_ram_pct + 0.35 * cpu_headroom_pct + 0.25 * network_health;
        let tier_factor = if is_cloud { 1.0 } else { 0.70 };
        let fitness_pct = (resource_score * tier_factor).clamp(0.0, 100.0);

        let role_label = if node.status != "online" {
            "○ Offline Node"
        } else if is_cloud && fitness_pct >= 85.0 {
            "👑 Active Cloud Coordinator"
        } else if is_cloud {
            "⚡ Warm Quorum Standby"
        } else {
            "🛡️ Edge Worker Standby"
        };

        node.fitness = Some(serde_json::json!({
            "fitness_pct": (fitness_pct * 10.0).round() / 10.0,
            "tier": if is_cloud { "cloud" } else { "edge" },
            "is_cloud": is_cloud,
            "ram_headroom_pct": (free_ram_pct * 10.0).round() / 10.0,
            "cpu_headroom_pct": (cpu_headroom_pct * 10.0).round() / 10.0,
            "role_label": role_label,
            "services_healthy": node.status == "online"
        }));
    }

    // Sort: online first, then by highest fitness percentage score
    result.sort_by(|a, b| {
        let score_a = a.fitness.as_ref().and_then(|f| f.get("fitness_pct")).and_then(|v| v.as_f64()).unwrap_or(0.0);
        let score_b = b.fitness.as_ref().and_then(|f| f.get("fitness_pct")).and_then(|v| v.as_f64()).unwrap_or(0.0);
        b.status.cmp(&a.status).then_with(|| score_b.partial_cmp(&score_a).unwrap_or(std::cmp::Ordering::Equal))
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
    let (tx, rx) = mpsc::channel::<String>(100);

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

// ─── Remote Model Pull ────────────────────────────────────────────────────────

#[derive(Deserialize)]
pub struct ModelPullPayload {
    pub model: String,
}

pub async fn pull_model(
    State(state): State<SharedState>,
    Path(node_id): Path<String>,
    Json(payload): Json<ModelPullPayload>,
) -> impl IntoResponse {
    let cmd = CommandPayload {
        action: "ollama_pull".into(),
        args: vec![payload.model],
    };
    dispatch_command(State(state), Path(node_id), Json(cmd)).await
}

// ─── Fleet Chat Messages (Device-to-Device & Global) ──────────────────────────

#[derive(Debug, Serialize, Deserialize)]
pub struct ChatMessageRecord {
    pub id: i64,
    pub chat_id: String,
    pub sender_id: String,
    pub sender_name: String,
    pub recipient_id: Option<String>,
    pub content: String,
    pub message_type: String,
    pub created_at: String,
}

#[derive(Debug, Deserialize)]
pub struct SendChatMessagePayload {
    pub chat_id: Option<String>,
    pub sender_id: Option<String>,
    pub sender_name: Option<String>,
    pub recipient_id: Option<String>,
    pub content: String,
    pub message_type: Option<String>,
}

pub async fn list_chat_messages(
    State(state): State<SharedState>,
    Query(params): Query<HashMap<String, String>>,
) -> Json<Vec<ChatMessageRecord>> {
    let chat_id = params.get("chat_id").cloned().unwrap_or_else(|| "global".into());
    let limit: i64 = params.get("limit").and_then(|v| v.parse().ok()).unwrap_or(50);

    let conn = state.db.lock().await;
    let mut stmt = conn
        .prepare(
            "SELECT id, chat_id, sender_id, sender_name, recipient_id, content, message_type, created_at 
             FROM fleet_chat_messages 
             WHERE chat_id = ?1 
             ORDER BY id DESC LIMIT ?2",
        )
        .unwrap();

    let rows = stmt
        .query_map(rusqlite::params![chat_id, limit], |row| {
            Ok(ChatMessageRecord {
                id: row.get(0)?,
                chat_id: row.get(1)?,
                sender_id: row.get(2)?,
                sender_name: row.get(3)?,
                recipient_id: row.get(4)?,
                content: row.get(5)?,
                message_type: row.get(6)?,
                created_at: row.get(7)?,
            })
        })
        .unwrap();

    let mut list = Vec::new();
    for r in rows.flatten() {
        list.push(r);
    }
    list.reverse();
    Json(list)
}

pub async fn send_chat_message(
    State(state): State<SharedState>,
    Json(payload): Json<SendChatMessagePayload>,
) -> impl IntoResponse {
    let chat_id = payload.chat_id.unwrap_or_else(|| "global".into());
    let sender_id = payload.sender_id.unwrap_or_else(|| "operator".into());
    let sender_name = payload.sender_name.unwrap_or_else(|| "Faizal (Operator)".into());
    let msg_type = payload.message_type.unwrap_or_else(|| "text".into());

    let conn = state.db.lock().await;
    let res = conn.execute(
        "INSERT INTO fleet_chat_messages (chat_id, sender_id, sender_name, recipient_id, content, message_type)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
        rusqlite::params![chat_id, sender_id, sender_name, payload.recipient_id, payload.content, msg_type],
    );
    let msg_id = conn.last_insert_rowid();
    drop(conn);

    if res.is_ok() {
        // Broadcast over WebSocket mesh to all active fleet nodes
        let frame = serde_json::json!({
            "frame_id": 10,
            "node_id": "hub",
            "timestamp": SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() as u64,
            "msg": {
                "type": "fleet_chat_message",
                "payload": {
                    "id": msg_id,
                    "chat_id": chat_id,
                    "sender_id": sender_id,
                    "sender_name": sender_name,
                    "recipient_id": payload.recipient_id,
                    "content": payload.content,
                    "message_type": msg_type
                }
            }
        });
        let frame_str = frame.to_string();
        let sessions = state.fleet.sessions.read().await;
        for session in sessions.values() {
            let _ = session.ws_tx.send(frame_str.clone()).await;
        }

        (StatusCode::CREATED, Json(serde_json::json!({ "success": true, "id": msg_id }))).into_response()
    } else {
        (StatusCode::INTERNAL_SERVER_ERROR, "Failed to persist chat message").into_response()
    }
}

// ─── Fleet AI Conversations (Persistent Sessions) ─────────────────────────────

#[derive(Debug, Serialize, Deserialize)]
pub struct AiConversationRecord {
    pub id: String,
    pub title: String,
    pub target_node_id: String,
    pub model: String,
    pub system_prompt: Option<String>,
    pub temperature: f64,
    pub created_at: String,
    pub updated_at: String,
    pub message_count: Option<i64>,
}

#[derive(Debug, Deserialize)]
pub struct CreateAiConversationPayload {
    pub title: Option<String>,
    pub target_node_id: String,
    pub model: String,
    pub system_prompt: Option<String>,
    pub temperature: Option<f64>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct AiMessageRecord {
    pub id: i64,
    pub conversation_id: String,
    pub role: String,
    pub content: String,
    pub node_id: Option<String>,
    pub created_at: String,
}

#[derive(Debug, Deserialize)]
pub struct SendAiMessagePayload {
    pub content: String,
    pub model: Option<String>,
    pub system_prompt: Option<String>,
    pub temperature: Option<f32>,
}

pub async fn list_ai_conversations(
    State(state): State<SharedState>,
) -> Json<Vec<AiConversationRecord>> {
    let conn = state.db.lock().await;
    let mut stmt = conn
        .prepare(
            "SELECT c.id, c.title, c.target_node_id, c.model, c.system_prompt, c.temperature, c.created_at, c.updated_at,
                    (SELECT COUNT(*) FROM fleet_ai_messages m WHERE m.conversation_id = c.id) as msg_count
             FROM fleet_ai_conversations c 
             ORDER BY c.updated_at DESC",
        )
        .unwrap();

    let rows = stmt
        .query_map([], |row| {
            Ok(AiConversationRecord {
                id: row.get(0)?,
                title: row.get(1)?,
                target_node_id: row.get(2)?,
                model: row.get(3)?,
                system_prompt: row.get(4)?,
                temperature: row.get(5)?,
                created_at: row.get(6)?,
                updated_at: row.get(7)?,
                message_count: row.get(8)?,
            })
        })
        .unwrap();

    let mut list = Vec::new();
    for r in rows.flatten() {
        list.push(r);
    }
    Json(list)
}

pub async fn create_ai_conversation(
    State(state): State<SharedState>,
    Json(payload): Json<CreateAiConversationPayload>,
) -> impl IntoResponse {
    let id = uuid::Uuid::new_v4().to_string();
    let title = payload.title.unwrap_or_else(|| "New Conversation".into());
    let temp = payload.temperature.unwrap_or(0.7);

    let conn = state.db.lock().await;
    let res = conn.execute(
        "INSERT INTO fleet_ai_conversations (id, title, target_node_id, model, system_prompt, temperature)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
        rusqlite::params![id, title, payload.target_node_id, payload.model, payload.system_prompt, temp],
    );

    if res.is_ok() {
        (
            StatusCode::CREATED,
            Json(serde_json::json!({
                "id": id,
                "title": title,
                "target_node_id": payload.target_node_id,
                "model": payload.model
            })),
        )
            .into_response()
    } else {
        (StatusCode::INTERNAL_SERVER_ERROR, "Failed to create conversation").into_response()
    }
}

pub async fn get_ai_conversation_messages(
    State(state): State<SharedState>,
    Path(id): Path<String>,
) -> Json<Vec<AiMessageRecord>> {
    let conn = state.db.lock().await;
    let mut stmt = conn
        .prepare(
            "SELECT id, conversation_id, role, content, node_id, created_at 
             FROM fleet_ai_messages 
             WHERE conversation_id = ?1 
             ORDER BY id ASC",
        )
        .unwrap();

    let rows = stmt
        .query_map(rusqlite::params![id], |row| {
            Ok(AiMessageRecord {
                id: row.get(0)?,
                conversation_id: row.get(1)?,
                role: row.get(2)?,
                content: row.get(3)?,
                node_id: row.get(4)?,
                created_at: row.get(5)?,
            })
        })
        .unwrap();

    let mut list = Vec::new();
    for r in rows.flatten() {
        list.push(r);
    }
    Json(list)
}

pub async fn send_ai_conversation_message(
    State(state): State<SharedState>,
    Path(id): Path<String>,
    Json(payload): Json<SendAiMessagePayload>,
) -> Response {
    let conn = state.db.lock().await;
    let conv_info: Option<(String, String, Option<String>, f64)> = conn
        .query_row(
            "SELECT target_node_id, model, system_prompt, temperature FROM fleet_ai_conversations WHERE id = ?1",
            rusqlite::params![id],
            |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?, row.get(3)?)),
        )
        .ok();

    if conv_info.is_none() {
        return (StatusCode::NOT_FOUND, "Conversation not found").into_response();
    }
    let (target_node_id, default_model, default_system, default_temp) = conv_info.unwrap();

    // 1. Insert user message into SQLite
    let _ = conn.execute(
        "INSERT INTO fleet_ai_messages (conversation_id, role, content, node_id) VALUES (?1, 'user', ?2, ?3)",
        rusqlite::params![id, payload.content, target_node_id],
    );
    let _ = conn.execute(
        "UPDATE fleet_ai_conversations SET updated_at = CURRENT_TIMESTAMP WHERE id = ?1",
        rusqlite::params![id],
    );
    drop(conn);

    let model = payload.model.unwrap_or(default_model);
    let sys_prompt = payload.system_prompt.or(default_system);
    let temp = payload.temperature.unwrap_or(default_temp as f32);

    let sessions = state.fleet.sessions.read().await;
    let session = match sessions.get(&target_node_id) {
        Some(s) => s,
        None => return (StatusCode::NOT_FOUND, "Target compute node is offline").into_response(),
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
                "model": model,
                "prompt": payload.content,
                "system_prompt": sys_prompt,
                "temperature": temp,
            }
        }
    });

    if session.ws_tx.send(frame.to_string()).await.is_err() {
        return (StatusCode::BAD_GATEWAY, "Failed to send prompt to target node").into_response();
    }

    // Spawn stream collector that accumulates tokens and writes assistant response to SQLite
    let db = state.db.clone();
    let conv_id = id.clone();
    let node_id_for_db = target_node_id.clone();
    let (sse_tx, sse_rx) = mpsc::channel::<String>(100);

    tokio::spawn(async move {
        let mut full_response = String::new();
        while let Some(chunk) = rx.recv().await {
            full_response.push_str(&chunk);
            let _ = sse_tx.send(chunk).await;
        }

        // When stream terminates, persist accumulated assistant response
        if !full_response.is_empty() {
            let conn = db.lock().await;
            let _ = conn.execute(
                "INSERT INTO fleet_ai_messages (conversation_id, role, content, node_id) VALUES (?1, 'assistant', ?2, ?3)",
                rusqlite::params![conv_id, full_response, node_id_for_db],
            );
            let _ = conn.execute(
                "UPDATE fleet_ai_conversations SET updated_at = CURRENT_TIMESTAMP WHERE id = ?1",
                rusqlite::params![conv_id],
            );
        }
    });

    let stream = tokio_stream::wrappers::ReceiverStream::new(sse_rx)
        .map(|chunk| Ok::<_, Infallible>(Event::default().data(chunk)));

    Sse::new(stream).keep_alive(KeepAlive::default()).into_response()
}

pub async fn delete_ai_conversation(
    State(state): State<SharedState>,
    Path(id): Path<String>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    let _ = conn.execute("DELETE FROM fleet_ai_messages WHERE conversation_id = ?1", rusqlite::params![id]);
    let res = conn.execute("DELETE FROM fleet_ai_conversations WHERE id = ?1", rusqlite::params![id]);

    if res.is_ok() {
        (StatusCode::OK, Json(serde_json::json!({ "success": true }))).into_response()
    } else {
        (StatusCode::INTERNAL_SERVER_ERROR, "Failed to delete conversation").into_response()
    }
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
  mkdir -p "$INSTALL_DIR"
  curl -fsSL "$BIN_URL" -o "$INSTALL_DIR/nexus-agent"
  chmod +x "$INSTALL_DIR/nexus-agent"
  ln -sf "$INSTALL_DIR/nexus-agent" "$INSTALL_DIR/nexus"
  ln -sf "$INSTALL_DIR/nexus-agent" "$INSTALL_DIR/nx"
  "$INSTALL_DIR/nexus" service install || "$INSTALL_DIR/nexus" run || true
  echo "Nexus Agent successfully installed and running as a system-wide service!"
else
  INSTALL_DIR="$HOME/.local/bin"
  mkdir -p "$INSTALL_DIR"
  curl -fsSL "$BIN_URL" -o "$INSTALL_DIR/nexus-agent"
  chmod +x "$INSTALL_DIR/nexus-agent"
  ln -sf "$INSTALL_DIR/nexus-agent" "$INSTALL_DIR/nexus"
  ln -sf "$INSTALL_DIR/nexus-agent" "$INSTALL_DIR/nx"
  
  # Ensure ~/.local/bin is in PATH for future shells
  for rc in "$HOME/.bashrc" "$HOME/.zshrc" "$HOME/.profile"; do
    if [ -f "$rc" ] && ! grep -q "\.local/bin" "$rc"; then
      echo 'export PATH="$HOME/.local/bin:$PATH"' >> "$rc"
    fi
  done
  export PATH="$INSTALL_DIR:$PATH"

  # On Linux, ensure linger is enabled so user service runs headlessly even after logout
  if [ "$OS" = "linux" ]; then
    loginctl enable-linger "$USER" 2>/dev/null || true
  fi
  
  # Install service or fall back to detached daemon
  "$INSTALL_DIR/nexus" service install --user || "$INSTALL_DIR/nexus" run || true
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
    let script = r#"# Nexus Fleet Agent - Universal Zero-Token 1-Command Installer (Windows)
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
try { cmd /c 'schtasks /delete /tn "NexusAgent" /f >nul 2>&1' } catch {}
if ($IsAdmin) {
    & $DestExe service install
} else {
    # Non-admin startup registry registration
    try {
        Set-ItemProperty -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run" -Name "NexusAgent" -Value "`"$NexusAlias`" run --silent" -Force
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

#[derive(Deserialize)]
pub struct CrashQuery {
    pub node_id: Option<String>,
    pub limit: Option<usize>,
}

pub async fn get_mesh_crashes(
    State(state): State<SharedState>,
    Query(query): Query<CrashQuery>,
) -> Json<serde_json::Value> {
    let limit = query.limit.unwrap_or(50).min(200) as i64;
    let conn = state.db.lock().await;

    let mut crashes = Vec::new();
    if let Some(ref nid) = query.node_id {
        if let Ok(mut stmt) = conn.prepare(
            "SELECT crash_id, node_id, hostname, agent_version, os, arch, component, kind, message, location, backtrace, recovered, restart_attempt, occurred_at, received_at FROM fleet_crash_reports WHERE node_id = ?1 ORDER BY id DESC LIMIT ?2",
        ) {
            if let Ok(rows) = stmt.query_map(rusqlite::params![nid, limit], map_crash_row) {
                for r in rows.flatten() {
                    crashes.push(r);
                }
            }
        }
    } else if let Ok(mut stmt) = conn.prepare(
        "SELECT crash_id, node_id, hostname, agent_version, os, arch, component, kind, message, location, backtrace, recovered, restart_attempt, occurred_at, received_at FROM fleet_crash_reports ORDER BY id DESC LIMIT ?1",
    ) {
        if let Ok(rows) = stmt.query_map(rusqlite::params![limit], map_crash_row) {
            for r in rows.flatten() {
                crashes.push(r);
            }
        }
    }

    let total = crashes.len();
    Json(serde_json::json!({
        "crashes": crashes,
        "total": total
    }))
}

fn map_crash_row(row: &rusqlite::Row<'_>) -> rusqlite::Result<serde_json::Value> {
    Ok(serde_json::json!({
        "crash_id": row.get::<_, String>(0)?,
        "node_id": row.get::<_, String>(1)?,
        "hostname": row.get::<_, Option<String>>(2)?,
        "agent_version": row.get::<_, Option<String>>(3)?,
        "os": row.get::<_, Option<String>>(4)?,
        "arch": row.get::<_, Option<String>>(5)?,
        "component": row.get::<_, Option<String>>(6)?,
        "kind": row.get::<_, Option<String>>(7)?,
        "message": row.get::<_, String>(8)?,
        "location": row.get::<_, Option<String>>(9)?,
        "backtrace": row.get::<_, Option<String>>(10)?,
        "recovered": row.get::<_, i64>(11)? != 0,
        "restart_attempt": row.get::<_, i64>(12)?,
        "occurred_at": row.get::<_, String>(13)?,
        "received_at": row.get::<_, Option<String>>(14)?,
    }))
}
