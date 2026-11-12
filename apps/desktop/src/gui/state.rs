use crate::config::AgentConfig;
use crate::mesh::protocol::{AiEngineTelemetry, GpuTelemetry, ServiceTelemetry, TelemetryPayload};
use crate::modules::ai_bridge::{AiBridge, OllamaProcessModel};
use crate::modules::chat_storage::{ChatMessageItem, ChatSession, ChatSessionMeta, ChatStorage};
use crate::modules::cluster_ops::{ClusterOps, RemoteNodeInfo, RemoteServiceInfo};
use crate::modules::crash::CrashRecord;
use crate::modules::docker::DockerSupervisor;
use crate::modules::fitness::FitnessScore;
use crate::service::ServiceManager;
use crate::updater::{AgentUpdater, UpdateManifest};
use chrono::Local;
use eframe::egui;
use futures_util::StreamExt;
use serde::Deserialize;
use std::collections::VecDeque;
use std::sync::mpsc::{self, Receiver, Sender};
use std::time::{Duration, Instant};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum TopologyViewMode {
    #[default]
    CanBusHybrid,
    TieredHierarchy,
    FullMesh,
}

impl TopologyViewMode {
    pub const ALL: [TopologyViewMode; 3] = [
        TopologyViewMode::CanBusHybrid,
        TopologyViewMode::TieredHierarchy,
        TopologyViewMode::FullMesh,
    ];

    pub fn label(&self) -> &'static str {
        match self {
            TopologyViewMode::CanBusHybrid => "CAN-Bus Hybrid",
            TopologyViewMode::TieredHierarchy => "Tiered Grid",
            TopologyViewMode::FullMesh => "Full Mesh",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum GuiRoute {
    Overview,
    Telemetry,
    Cluster,
    AiStudio,
    Diagnostics,
}

impl GuiRoute {
    pub const ALL: [GuiRoute; 5] = [
        GuiRoute::Overview,
        GuiRoute::Telemetry,
        GuiRoute::Cluster,
        GuiRoute::AiStudio,
        GuiRoute::Diagnostics,
    ];

    pub fn title(self) -> &'static str {
        match self {
            GuiRoute::Overview => "Overview",
            GuiRoute::Telemetry => "Telemetry",
            GuiRoute::Cluster => "Cluster & Mesh",
            GuiRoute::AiStudio => "AI Studio",
            GuiRoute::Diagnostics => "Diagnostics",
        }
    }

    pub fn short_title(self) -> &'static str {
        match self {
            GuiRoute::Overview => "HUD",
            GuiRoute::Telemetry => "HW",
            GuiRoute::Cluster => "Mesh",
            GuiRoute::AiStudio => "AI",
            GuiRoute::Diagnostics => "Diag",
        }
    }

    pub fn icon(self) -> &'static str {
        match self {
            GuiRoute::Overview => "◈",
            GuiRoute::Telemetry => "▤",
            GuiRoute::Cluster => "⬡",
            GuiRoute::AiStudio => "✦",
            GuiRoute::Diagnostics => "⚡",
        }
    }

    pub fn shortcut_num(self) -> &'static str {
        match self {
            GuiRoute::Overview => "1",
            GuiRoute::Telemetry => "2",
            GuiRoute::Cluster => "3",
            GuiRoute::AiStudio => "4",
            GuiRoute::Diagnostics => "5",
        }
    }
}

#[derive(Debug, Clone)]
pub struct RingBuffer<T> {
    data: VecDeque<T>,
    capacity: usize,
}

impl<T: Clone> RingBuffer<T> {
    pub fn new(capacity: usize) -> Self {
        Self {
            data: VecDeque::with_capacity(capacity),
            capacity: capacity.max(1),
        }
    }

    pub fn push(&mut self, value: T) {
        if self.data.len() >= self.capacity {
            self.data.pop_front();
        }
        self.data.push_back(value);
    }

    pub fn iter(&self) -> impl Iterator<Item = &T> {
        self.data.iter()
    }

    pub fn len(&self) -> usize {
        self.data.len()
    }

    pub fn latest(&self) -> Option<&T> {
        self.data.back()
    }
}

impl RingBuffer<f32> {
    pub fn as_vec(&self) -> Vec<f32> {
        self.data.iter().copied().collect()
    }

    pub fn min_max(&self) -> (f32, f32) {
        let mut min = f32::INFINITY;
        let mut max = f32::NEG_INFINITY;
        for &v in &self.data {
            if v < min {
                min = v;
            }
            if v > max {
                max = v;
            }
        }
        if min.is_infinite() {
            (0.0, 100.0)
        } else {
            (min, max)
        }
    }
}

#[derive(Debug, Clone)]
pub struct ProcessEntry {
    pub pid: u32,
    pub name: String,
    pub cpu_pct: f32,
    pub memory_mb: f64,
    pub status: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ProcessSort {
    Cpu,
    Memory,
    Name,
}

#[derive(Debug, Clone)]
pub struct GuiLogEntry {
    pub timestamp: String,
    pub level: LogLevel,
    pub source: String,
    pub message: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LogLevel {
    Info,
    Success,
    Warn,
    Error,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DaemonAction {
    Start,
    Stop,
    Restart,
    InstallService,
    UninstallService,
}

#[derive(Debug, Clone)]
pub enum GuiCommand {
    RefreshCluster {
        hub_url: String,
        pin: Option<String>,
    },
    DispatchServiceAction {
        hub_url: String,
        pin: Option<String>,
        service_id: String,
        action: String,
    },
    RefreshDocker,
    DockerAction {
        container: String,
        action: String,
    },
    RefreshOllama {
        endpoint: String,
    },
    LoadOllamaModel {
        endpoint: String,
        model: String,
    },
    UnloadOllamaModel {
        endpoint: String,
        model: String,
    },
    StreamAiChat {
        endpoint: String,
        model: String,
        messages: Vec<ChatMessageItem>,
        system_prompt: Option<String>,
        temperature: f32,
    },
    CheckUpdates {
        manifest_url: String,
    },
    InstallUpdate {
        manifest_url: String,
    },
    ForceUpdate {
        manifest_url: String,
    },
    RunDaemonAction(DaemonAction),
}

#[derive(Debug, Clone)]
pub enum GuiEvent {
    ClusterUpdated {
        nodes: Result<Vec<RemoteNodeInfo>, String>,
        services: Result<Vec<RemoteServiceInfo>, String>,
        latency_ms: Option<u64>,
    },
    DockerUpdated(Vec<ServiceTelemetry>),
    OllamaUpdated {
        telemetry: AiEngineTelemetry,
        loaded: Vec<OllamaProcessModel>,
    },
    AiStreamToken(String),
    AiStreamDone {
        eval_count: u64,
        elapsed_secs: f64,
    },
    AiStreamError(String),
    UpdateChecked(Result<Option<UpdateManifestInfo>, String>),
    Toast {
        msg: String,
        level: LogLevel,
        source: String,
    },
}

#[derive(Debug, Clone)]
pub struct UpdateManifestInfo {
    pub latest_version: String,
    pub current_version: String,
    pub release_date: Option<String>,
    pub update_available: bool,
}

#[derive(Debug, Deserialize)]
struct OllamaStreamChunk {
    message: Option<OllamaStreamMsg>,
    done: Option<bool>,
    eval_count: Option<u64>,
}

#[derive(Debug, Deserialize)]
struct OllamaStreamMsg {
    content: String,
}

pub struct GuiState {
    pub config: AgentConfig,
    pub route: GuiRoute,
    pub sidebar_collapsed: bool,

    // Live local telemetry
    pub telemetry: Option<TelemetryPayload>,
    pub fitness: FitnessScore,
    pub gpu: Option<GpuTelemetry>,
    pub per_core_cpu: Vec<f32>,
    pub processes: Vec<ProcessEntry>,
    pub process_search: String,
    pub process_sort: ProcessSort,
    pub daemon_active: bool,
    pub daemon_pid: Option<u32>,

    // Ring buffers (120 samples)
    pub cpu_history: RingBuffer<f32>,
    pub ram_history: RingBuffer<f32>,
    pub gpu_history: RingBuffer<f32>,
    pub gpu_temp_history: RingBuffer<f32>,
    pub net_rx_history: RingBuffer<f32>,
    pub net_tx_history: RingBuffer<f32>,
    pub hub_latency_history: RingBuffer<f32>,

    // Cluster & Docker
    pub remote_nodes: Vec<RemoteNodeInfo>,
    pub remote_services: Vec<RemoteServiceInfo>,
    pub cluster_error: Option<String>,
    pub cluster_loading: bool,
    pub hub_latency_ms: Option<u64>,
    pub docker_containers: Vec<ServiceTelemetry>,
    pub selected_node_id: Option<String>,

    // Topology & Fleet Mesh Controls
    pub topology_zoom: f32,
    pub topology_pan: eframe::egui::Vec2,
    pub topology_view_mode: TopologyViewMode,
    pub topology_show_packets: bool,
    pub topology_show_link_metrics: bool,
    pub topology_filter: String,
    pub topology_frozen: bool,

    // AI Studio
    pub ai_telemetry: AiEngineTelemetry,
    pub loaded_models: Vec<OllamaProcessModel>,
    pub selected_model: String,
    pub chat_sessions: Vec<ChatSessionMeta>,
    pub active_chat: ChatSession,
    pub chat_input: String,
    pub ai_streaming: bool,
    pub ai_stream_buffer: String,
    pub ai_stream_started: Option<Instant>,
    pub show_ai_params: bool,
    pub system_prompt_input: String,

    // Diagnostics & Updates
    pub crashes: Vec<(bool, CrashRecord)>,
    pub update_info: Option<UpdateManifestInfo>,
    pub update_checking: bool,
    pub update_schedule_hours: u64,
    pub last_update_check: Option<Instant>,
    pub logs: VecDeque<GuiLogEntry>,

    // Overlays & Preferences
    pub show_command_palette: bool,
    pub palette_query: String,
    pub palette_selected: usize,
    pub show_settings: bool,
    pub settings_tab: SettingsTab,
    pub hub_url_input: String,
    pub admin_pin_input: String,
    pub ai_endpoint_input: String,
    pub pref_coordinator: bool,
    pub pref_high_cadence: bool,
    pub pref_ai_relay: bool,
    pub toast: Option<(String, LogLevel, Instant)>,

    // Async bridge
    pub cmd_tx: Sender<GuiCommand>,
    pub evt_rx: Receiver<GuiEvent>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SettingsTab {
    Mesh,
    Telemetry,
    Ai,
}

impl GuiState {
    pub fn total_fleet_rx_bytes_sec(&self) -> u64 {
        let mut total = self.telemetry.as_ref().map(|t| t.network.rx_bytes_sec).unwrap_or(0);
        for node in &self.remote_nodes {
            if let Some(rx) = node.telemetry.as_ref()
                .and_then(|t| t.get("network")?.get("rx_bytes_sec")?.as_u64()) {
                total += rx;
            }
        }
        total
    }

    pub fn total_fleet_tx_bytes_sec(&self) -> u64 {
        let mut total = self.telemetry.as_ref().map(|t| t.network.tx_bytes_sec).unwrap_or(0);
        for node in &self.remote_nodes {
            if let Some(tx) = node.telemetry.as_ref()
                .and_then(|t| t.get("network")?.get("tx_bytes_sec")?.as_u64()) {
                total += tx;
            }
        }
        total
    }

    pub fn active_online_nodes_count(&self) -> usize {
        1 + self.remote_nodes.iter().filter(|n| n.status == "online").count()
    }

    pub fn push_log(&mut self, level: LogLevel, source: &str, message: impl Into<String>) {
        if self.logs.len() >= 250 {
            self.logs.pop_front();
        }
        self.logs.push_back(GuiLogEntry {
            timestamp: Local::now().format("%H:%M:%S").to_string(),
            level,
            source: source.to_string(),
            message: message.into(),
        });
    }

    pub fn set_toast(&mut self, msg: impl Into<String>, level: LogLevel, source: &str) {
        let m = msg.into();
        self.push_log(level, source, m.clone());
        self.toast = Some((m, level, Instant::now()));
    }

    pub fn effective_pin(&self) -> Option<String> {
        resolve_effective_pin(&self.admin_pin_input, self.config.token.as_deref())
    }

    pub fn refresh_chat_sessions(&mut self) {
        self.chat_sessions = ChatStorage::list_sessions();
    }

    pub fn start_new_chat(&mut self) {
        let model = if self.selected_model.is_empty() {
            self.ai_telemetry
                .models
                .first()
                .cloned()
                .unwrap_or_else(|| "llama3.2".to_string())
        } else {
            self.selected_model.clone()
        };
        let sys_prompt = if self.system_prompt_input.trim().is_empty() {
            None
        } else {
            Some(self.system_prompt_input.trim().to_string())
        };
        self.active_chat = ChatStorage::new_session(
            "local",
            &self.config.hostname,
            &model,
            sys_prompt,
            self.active_chat.temperature,
        );
    }

    pub fn refresh_crashes(&mut self) {
        self.crashes = crate::modules::crash::list_recent_crashes(50);
    }
}

pub fn spawn_background_worker(ctx: egui::Context) -> (Sender<GuiCommand>, Receiver<GuiEvent>) {
    let (cmd_tx, cmd_rx) = mpsc::channel::<GuiCommand>();
    let (evt_tx, evt_rx) = mpsc::channel::<GuiEvent>();

    std::thread::Builder::new()
        .name("nexus-gui-worker".to_string())
        .spawn(move || {
            let rt = match tokio::runtime::Builder::new_multi_thread()
                .worker_threads(3)
                .enable_all()
                .build()
            {
                Ok(rt) => rt,
                Err(e) => {
                    let _ = evt_tx.send(GuiEvent::Toast {
                        msg: format!("Failed to start async runtime: {}", e),
                        level: LogLevel::Error,
                        source: "Worker".to_string(),
                    });
                    return;
                }
            };

            rt.block_on(async move {
                while let Ok(cmd) = cmd_rx.recv() {
                    let tx = evt_tx.clone();
                    let ui_ctx = ctx.clone();

                    tokio::spawn(async move {
                        handle_command(cmd, &tx).await;
                        ui_ctx.request_repaint();
                    });
                }
            });
        })
        .expect("Failed to spawn GUI worker thread");

    (cmd_tx, evt_rx)
}

async fn handle_command(cmd: GuiCommand, tx: &Sender<GuiEvent>) {
    match cmd {
        GuiCommand::RefreshCluster { hub_url, pin } => {
            if let Some(pin_val) = pin.as_deref() {
                let (nodes, services, latency_ms) = tokio::join!(
                    ClusterOps::fetch_nodes(&hub_url, Some(pin_val)),
                    ClusterOps::fetch_services(&hub_url, Some(pin_val)),
                    ClusterOps::measure_hub_latency(&hub_url)
                );
                let _ = tx.send(GuiEvent::ClusterUpdated {
                    nodes,
                    services,
                    latency_ms,
                });
            } else {
                let latency_ms = ClusterOps::measure_hub_latency(&hub_url).await;
                let _ = tx.send(GuiEvent::ClusterUpdated {
                    nodes: Err("Admin PIN required to unlock cluster nodes.".to_string()),
                    services: Err("Admin PIN required to unlock cluster services.".to_string()),
                    latency_ms,
                });
            }
        }

        GuiCommand::DispatchServiceAction {
            hub_url,
            pin,
            service_id,
            action,
        } => {
            if pin.is_none() {
                let _ = tx.send(GuiEvent::Toast {
                    msg: "Cluster service action locked: configure Admin PIN in Preferences."
                        .to_string(),
                    level: LogLevel::Warn,
                    source: "Cluster".to_string(),
                });
                return;
            }
            match ClusterOps::dispatch_service_action(&hub_url, pin.as_deref(), &service_id, &action).await {
                Ok(msg) => {
                    let _ = tx.send(GuiEvent::Toast {
                        msg,
                        level: LogLevel::Success,
                        source: "Cluster".to_string(),
                    });
                    let (nodes, services, latency_ms) = tokio::join!(
                        ClusterOps::fetch_nodes(&hub_url, pin.as_deref()),
                        ClusterOps::fetch_services(&hub_url, pin.as_deref()),
                        ClusterOps::measure_hub_latency(&hub_url)
                    );
                    let _ = tx.send(GuiEvent::ClusterUpdated {
                        nodes,
                        services,
                        latency_ms,
                    });
                }
                Err(e) => {
                    let _ = tx.send(GuiEvent::Toast {
                        msg: format!("Service action failed: {}", e),
                        level: LogLevel::Error,
                        source: "Cluster".to_string(),
                    });
                }
            }
        }

        GuiCommand::RefreshDocker => {
            let containers = tokio::task::spawn_blocking(DockerSupervisor::collect_containers)
                .await
                .unwrap_or_default();
            let _ = tx.send(GuiEvent::DockerUpdated(containers));
        }

        GuiCommand::DockerAction { container, action } => {
            let clean_name = container.strip_prefix("docker:").unwrap_or(&container).to_string();
            let act = action.clone();
            let res = tokio::task::spawn_blocking(move || {
                std::process::Command::new("docker")
                    .args([&act, &clean_name])
                    .output()
            })
            .await;

            match res {
                Ok(Ok(out)) if out.status.success() => {
                    let _ = tx.send(GuiEvent::Toast {
                        msg: format!("Docker {} succeeded on {}", action, container),
                        level: LogLevel::Success,
                        source: "Docker".to_string(),
                    });
                }
                Ok(Ok(out)) => {
                    let err = String::from_utf8_lossy(&out.stderr).trim().to_string();
                    let _ = tx.send(GuiEvent::Toast {
                        msg: format!("Docker {} failed: {}", action, err),
                        level: LogLevel::Error,
                        source: "Docker".to_string(),
                    });
                }
                _ => {
                    let _ = tx.send(GuiEvent::Toast {
                        msg: format!("Failed to invoke docker {}", action),
                        level: LogLevel::Error,
                        source: "Docker".to_string(),
                    });
                }
            }

            let containers = tokio::task::spawn_blocking(DockerSupervisor::collect_containers)
                .await
                .unwrap_or_default();
            let _ = tx.send(GuiEvent::DockerUpdated(containers));
        }

        GuiCommand::RefreshOllama { endpoint } => {
            let bridge = AiBridge::new(&endpoint);
            let (telemetry, loaded) = tokio::join!(bridge.check_models(), bridge.poll_loaded_models());
            let _ = tx.send(GuiEvent::OllamaUpdated { telemetry, loaded });
        }

        GuiCommand::LoadOllamaModel { endpoint, model } => {
            let bridge = AiBridge::new(&endpoint);
            match bridge.load_model(&model, Some("15m")).await {
                Ok(()) => {
                    let _ = tx.send(GuiEvent::Toast {
                        msg: format!("Loaded {} into VRAM", model),
                        level: LogLevel::Success,
                        source: "Ollama".to_string(),
                    });
                }
                Err(e) => {
                    let _ = tx.send(GuiEvent::Toast {
                        msg: format!("Load failed: {}", e),
                        level: LogLevel::Error,
                        source: "Ollama".to_string(),
                    });
                }
            }
            let (telemetry, loaded) = tokio::join!(bridge.check_models(), bridge.poll_loaded_models());
            let _ = tx.send(GuiEvent::OllamaUpdated { telemetry, loaded });
        }

        GuiCommand::UnloadOllamaModel { endpoint, model } => {
            let bridge = AiBridge::new(&endpoint);
            match bridge.unload_model(&model).await {
                Ok(()) => {
                    let _ = tx.send(GuiEvent::Toast {
                        msg: format!("Unloaded {} from VRAM", model),
                        level: LogLevel::Info,
                        source: "Ollama".to_string(),
                    });
                }
                Err(e) => {
                    let _ = tx.send(GuiEvent::Toast {
                        msg: format!("Unload failed: {}", e),
                        level: LogLevel::Error,
                        source: "Ollama".to_string(),
                    });
                }
            }
            let (telemetry, loaded) = tokio::join!(bridge.check_models(), bridge.poll_loaded_models());
            let _ = tx.send(GuiEvent::OllamaUpdated { telemetry, loaded });
        }

        GuiCommand::StreamAiChat {
            endpoint,
            model,
            messages,
            system_prompt,
            temperature,
        } => {
            let url = format!("{}/api/chat", endpoint.trim_end_matches('/'));
            let mut api_messages = Vec::new();
            if let Some(sys) = system_prompt.filter(|s| !s.trim().is_empty()) {
                api_messages.push(serde_json::json!({ "role": "system", "content": sys }));
            }
            for m in &messages {
                api_messages.push(serde_json::json!({ "role": m.role, "content": m.content }));
            }

            let body = serde_json::json!({
                "model": model,
                "messages": api_messages,
                "stream": true,
                "options": { "temperature": temperature }
            });

            let client = reqwest::Client::builder()
                .timeout(Duration::from_secs(300))
                .build()
                .unwrap_or_default();

            let started = Instant::now();
            match client.post(&url).json(&body).send().await {
                Ok(res) if res.status().is_success() => {
                    let mut stream = res.bytes_stream();
                    let mut eval_count = 0u64;
                    let mut line_buf = Vec::<u8>::new();

                    while let Some(chunk_res) = stream.next().await {
                        match chunk_res {
                            Ok(bytes) => {
                                line_buf.extend_from_slice(&bytes);
                                while let Some(pos) = line_buf.iter().position(|&b| b == b'\n') {
                                    let line: Vec<u8> = line_buf.drain(..=pos).collect();
                                    if let Ok(chunk) = serde_json::from_slice::<OllamaStreamChunk>(&line) {
                                        if let Some(msg) = chunk.message {
                                            if !msg.content.is_empty() {
                                                let _ = tx.send(GuiEvent::AiStreamToken(msg.content));
                                            }
                                        }
                                        if let Some(ec) = chunk.eval_count {
                                            eval_count = ec;
                                        }
                                        if chunk.done.unwrap_or(false) {
                                            let _ = tx.send(GuiEvent::AiStreamDone {
                                                eval_count,
                                                elapsed_secs: started.elapsed().as_secs_f64(),
                                            });
                                            return;
                                        }
                                    }
                                }
                            }
                            Err(e) => {
                                let _ = tx.send(GuiEvent::AiStreamError(format!("Stream error: {}", e)));
                                return;
                            }
                        }
                    }
                    let _ = tx.send(GuiEvent::AiStreamDone {
                        eval_count,
                        elapsed_secs: started.elapsed().as_secs_f64(),
                    });
                }
                Ok(res) => {
                    let _ = tx.send(GuiEvent::AiStreamError(format!("Ollama HTTP {}", res.status())));
                }
                Err(e) => {
                    let _ = tx.send(GuiEvent::AiStreamError(format!("Ollama unreachable: {}", e)));
                }
            }
        }

        GuiCommand::CheckUpdates { manifest_url } => {
            let client = reqwest::Client::builder()
                .timeout(Duration::from_secs(10))
                .build()
                .unwrap_or_default();
            let res = client
                .get(&manifest_url)
                .header("Cache-Control", "no-cache")
                .send()
                .await;

            match res {
                Ok(r) if r.status().is_success() => match r.json::<UpdateManifest>().await {
                    Ok(m) => {
                        let cur = crate::AGENT_VERSION.to_string();
                        let avail = m.version.trim_start_matches('v') != cur.trim_start_matches('v');
                        let _ = tx.send(GuiEvent::UpdateChecked(Ok(Some(UpdateManifestInfo {
                            latest_version: m.version,
                            current_version: cur,
                            release_date: m.release_date,
                            update_available: avail,
                        }))));
                    }
                    Err(e) => {
                        let _ = tx.send(GuiEvent::UpdateChecked(Err(format!("Manifest parse error: {}", e))));
                    }
                },
                Ok(r) => {
                    let _ = tx.send(GuiEvent::UpdateChecked(Err(format!("Update server HTTP {}", r.status()))));
                }
                Err(e) => {
                    let _ = tx.send(GuiEvent::UpdateChecked(Err(format!("Network error: {}", e))));
                }
            }
        }

        GuiCommand::InstallUpdate { manifest_url } => {
            match AgentUpdater::run_update(&manifest_url, false, false).await {
                Ok(()) => {
                    let _ = tx.send(GuiEvent::Toast {
                        msg: "Agent updated successfully! Restart to apply.".to_string(),
                        level: LogLevel::Success,
                        source: "Updater".to_string(),
                    });
                }
                Err(e) => {
                    let _ = tx.send(GuiEvent::Toast {
                        msg: format!("Update failed: {}", e),
                        level: LogLevel::Error,
                        source: "Updater".to_string(),
                    });
                }
            }
        }

        GuiCommand::ForceUpdate { manifest_url } => {
            match AgentUpdater::run_update(&manifest_url, false, true).await {
                Ok(()) => {
                    let _ = tx.send(GuiEvent::Toast {
                        msg: "Force update completed! Restart to apply.".to_string(),
                        level: LogLevel::Success,
                        source: "Updater".to_string(),
                    });
                }
                Err(e) => {
                    let _ = tx.send(GuiEvent::Toast {
                        msg: format!("Force update failed: {}", e),
                        level: LogLevel::Error,
                        source: "Updater".to_string(),
                    });
                }
            }
        }

        GuiCommand::RunDaemonAction(action) => {
            let res = tokio::task::spawn_blocking(move || match action {
                DaemonAction::Start => ServiceManager::start(false)
                    .map(|_| "Daemon started".to_string())
                    .map_err(|e| e.to_string()),
                DaemonAction::Stop => ServiceManager::stop(false)
                    .map(|_| "Daemon stopped".to_string())
                    .map_err(|e| e.to_string()),
                DaemonAction::Restart => ServiceManager::restart(false)
                    .map(|_| "Daemon restarted".to_string())
                    .map_err(|e| e.to_string()),
                DaemonAction::InstallService => ServiceManager::install(false)
                    .map(|_| "OS background service installed".to_string())
                    .map_err(|e| e.to_string()),
                DaemonAction::UninstallService => ServiceManager::uninstall(false)
                    .map(|_| "OS background service uninstalled".to_string())
                    .map_err(|e| e.to_string()),
            })
            .await;

            match res {
                Ok(Ok(msg)) => {
                    let _ = tx.send(GuiEvent::Toast {
                        msg,
                        level: LogLevel::Success,
                        source: "Daemon".to_string(),
                    });
                }
                Ok(Err(e)) => {
                    let _ = tx.send(GuiEvent::Toast {
                        msg: e,
                        level: LogLevel::Error,
                        source: "Daemon".to_string(),
                    });
                }
                Err(e) => {
                    let _ = tx.send(GuiEvent::Toast {
                        msg: format!("Task join error: {}", e),
                        level: LogLevel::Error,
                        source: "Daemon".to_string(),
                    });
                }
            }
        }
    }
}

pub fn resolve_effective_pin(admin_pin_input: &str, config_token: Option<&str>) -> Option<String> {
    let trimmed = admin_pin_input.trim();
    if !trimmed.is_empty() {
        return Some(trimmed.to_string());
    }
    if let Some(tok) = config_token {
        let t = tok.trim();
        if !t.is_empty() {
            return Some(t.to_string());
        }
    }
    std::env::var("NEXUS_ADMIN_PIN")
        .or_else(|_| std::env::var("FAIZAL_ADMIN_PIN"))
        .ok()
        .filter(|s| !s.trim().is_empty())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ring_buffer_respects_capacity_and_min_max() {
        let mut rb = RingBuffer::new(3);
        rb.push(10.0);
        rb.push(50.0);
        rb.push(25.0);
        rb.push(80.0);
        assert_eq!(rb.len(), 3);
        assert_eq!(rb.as_vec(), vec![50.0, 25.0, 80.0]);
        assert_eq!(rb.min_max(), (25.0, 80.0));
        assert_eq!(rb.latest(), Some(&80.0));
    }

    #[test]
    fn effective_pin_is_none_without_configured_secret() {
        if std::env::var("NEXUS_ADMIN_PIN").is_err() && std::env::var("FAIZAL_ADMIN_PIN").is_err() {
            assert_eq!(resolve_effective_pin("", None), None);
            assert_eq!(resolve_effective_pin("   ", Some("  ")), None);
        }
        assert_eq!(resolve_effective_pin("", Some("9988")).as_deref(), Some("9988"));
        assert_eq!(
            resolve_effective_pin("4455", Some("9988")).as_deref(),
            Some("4455")
        );
    }
}

