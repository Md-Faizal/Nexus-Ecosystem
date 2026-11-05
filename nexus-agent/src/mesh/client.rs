use crate::config::AgentConfig;
use crate::mesh::protocol::{MeshFrame, MeshMessage};
use crate::modules::ModuleManager;
use crate::updater::rollback::RollbackUpdater;
use futures_util::{SinkExt, StreamExt};
use std::collections::VecDeque;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};
use tokio::sync::{mpsc, Mutex};
use tokio::time::sleep;
use tokio_tungstenite::connect_async;
use tokio_tungstenite::tungstenite::Message;
use tracing::{error, info, warn};

pub struct MeshClient {
    config: AgentConfig,
    modules: Arc<Mutex<ModuleManager>>,
    offline_buffer: Arc<Mutex<VecDeque<MeshMessage>>>,
    live_mode: Arc<AtomicBool>,
    sequence_id: Arc<AtomicU64>,
}

impl MeshClient {
    pub fn new(config: AgentConfig) -> Self {
        let modules = Arc::new(Mutex::new(ModuleManager::new(&config)));
        Self {
            config,
            modules,
            offline_buffer: Arc::new(Mutex::new(VecDeque::with_capacity(120))),
            live_mode: Arc::new(AtomicBool::new(false)),
            sequence_id: Arc::new(AtomicU64::new(1)),
        }
    }

    pub async fn run(&mut self) {
        info!(
            "Starting Nexus Mesh Agent for node: {} (OS: {}, Arch: {})",
            self.config.node_id, self.config.os, self.config.arch
        );

        let mut backoff = Duration::from_secs(2);
        let max_backoff = Duration::from_secs(30);

        loop {
            for hub_url in self.config.hubs.clone() {
                info!("Attempting connection to mesh hub: {}", hub_url);
                match self.connect_and_serve(&hub_url).await {
                    Ok(_) => {
                        info!("Session with {} terminated gracefully, reconnecting...", hub_url);
                        backoff = Duration::from_secs(2);
                    }
                    Err(e) => {
                        warn!("Mesh connection to {} failed: {}. Trying next hub...", hub_url, e);
                    }
                }
            }

            // Exponential backoff before full rotation cycle
            let jitter_ms = (SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() % 1000) as u64;
            let sleep_dur = backoff + Duration::from_millis(jitter_ms);
            info!("Waiting {:?} before next hub rotation...", sleep_dur);
            sleep(sleep_dur).await;
            backoff = (backoff * 2).min(max_backoff);
        }
    }

    async fn connect_and_serve(&mut self, hub_url: &str) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let (ws_stream, _) = connect_async(hub_url).await?;
        info!("Successfully connected to mesh hub: {}", hub_url);

        let (mut ws_sink, mut ws_read) = ws_stream.split();
        let (outbox_tx, mut outbox_rx) = mpsc::channel::<MeshMessage>(100);

        // Send NodeHello immediately
        let hello = MeshMessage::NodeHello {
            hostname: self.config.hostname.clone(),
            os: self.config.os.clone(),
            os_version: sysinfo::System::os_version().unwrap_or_default(),
            arch: self.config.arch.clone(),
            agent_version: env!("CARGO_PKG_VERSION").to_string(),
            features: self.config.features.clone(),
            local_ips: vec![],
        };
        self.send_frame(&mut ws_sink, hello).await?;

        // Flush any buffered telemetry from offline periods
        {
            let mut buffer = self.offline_buffer.lock().await;
            while let Some(msg) = buffer.pop_front() {
                let _ = self.send_frame(&mut ws_sink, msg).await;
            }
        }

        // Send Initial Telemetry Report Immediately on Connect (Zero-Delay)
        {
            let mut guard = self.modules.lock().await;
            let initial_telemetry = guard.gather_full_telemetry(&self.config).await;
            let _ = self.send_frame(&mut ws_sink, MeshMessage::TelemetryReport(Box::new(initial_telemetry))).await;
        }

        // Background Telemetry & Heartbeat loop
        let telemetry_tx = outbox_tx.clone();
        let modules_clone = self.modules.clone();
        let config_clone = self.config.clone();
        let live_mode_clone = self.live_mode.clone();

        let telemetry_handle = tokio::spawn(async move {
            let mut last_heartbeat = Instant::now();
            loop {
                let is_live = live_mode_clone.load(Ordering::Relaxed);
                let poll_secs = if is_live {
                    config_clone.active_poll_interval_secs
                } else {
                    config_clone.poll_interval_secs
                };

                sleep(Duration::from_secs(poll_secs)).await;

                // Send Telemetry
                let payload = {
                    let mut guard = modules_clone.lock().await;
                    guard.gather_full_telemetry(&config_clone).await
                };

                let telemetry_msg = MeshMessage::TelemetryReport(Box::new(payload));
                if telemetry_tx.send(telemetry_msg).await.is_err() {
                    break;
                }

                // Send Heartbeat every 10 seconds if idle
                if last_heartbeat.elapsed() >= Duration::from_secs(10) {
                    let hb = MeshMessage::Heartbeat {
                        uptime_secs: sysinfo::System::uptime(),
                        cpu_usage_pct: 0.0,
                        memory_used_pct: 0.0,
                    };
                    let _ = telemetry_tx.send(hb).await;
                    last_heartbeat = Instant::now();
                }
            }
        });

        // Loop handling incoming WS messages and outgoing frames
        loop {
            tokio::select! {
                // Outgoing messages
                Some(msg) = outbox_rx.recv() => {
                    if let Err(e) = self.send_frame(&mut ws_sink, msg.clone()).await {
                        warn!("Failed to send frame over WS, queuing in offline buffer: {}", e);
                        let mut buf = self.offline_buffer.lock().await;
                        if buf.len() >= 100 {
                            buf.pop_front();
                        }
                        buf.push_back(msg);
                        break;
                    }
                }

                // Incoming messages from Hub
                Some(ws_msg) = ws_read.next() => {
                    match ws_msg {
                        Ok(Message::Text(text)) => {
                            if let Ok(frame) = serde_json::from_str::<MeshFrame>(&text) {
                                self.handle_incoming_frame(frame, &outbox_tx).await;
                            }
                        }
                        Ok(Message::Ping(data)) => {
                            let _ = ws_sink.send(Message::Pong(data)).await;
                        }
                        Ok(Message::Close(_)) => {
                            info!("Hub sent WebSocket Close frame");
                            break;
                        }
                        Err(e) => {
                            warn!("WebSocket error: {}", e);
                            break;
                        }
                        _ => {}
                    }
                }
            }
        }

        telemetry_handle.abort();
        Ok(())
    }

    async fn send_frame<S>(&self, sink: &mut S, msg: MeshMessage) -> Result<(), Box<dyn std::error::Error + Send + Sync>>
    where
        S: SinkExt<Message> + Unpin,
        <S as futures_util::Sink<Message>>::Error: std::error::Error + Send + Sync + 'static,
    {
        let frame = MeshFrame {
            frame_id: self.sequence_id.fetch_add(1, Ordering::Relaxed),
            node_id: self.config.node_id.clone(),
            timestamp: SystemTime::now().duration_since(UNIX_EPOCH)?.as_millis() as u64,
            msg,
        };

        let json = serde_json::to_string(&frame)?;
        sink.send(Message::Text(json.into())).await?;
        Ok(())
    }

    async fn handle_incoming_frame(&mut self, frame: MeshFrame, outbox_tx: &mpsc::Sender<MeshMessage>) {
        match frame.msg {
            MeshMessage::HeartbeatAck { live_mode, .. } => {
                self.live_mode.store(live_mode, Ordering::Relaxed);
            }

            MeshMessage::CommandDispatch { command_id, action, args, .. } => {
                info!("Executing remote command: {} (args: {:?})", action, args);
                let (success, code, stdout, stderr, ms) = self.execute_command(&action, &args).await;

                let ack = MeshMessage::CommandAck {
                    command_id,
                    success,
                    exit_code: code,
                    stdout,
                    stderr,
                    execution_ms: ms,
                };
                let _ = outbox_tx.send(ack).await;
            }

            MeshMessage::AiPrompt { request_id, model, prompt, system_prompt, temperature } => {
                let ai = {
                    let guard = self.modules.lock().await;
                    guard.ai_bridge().clone()
                };
                let tx = outbox_tx.clone();
                tokio::spawn(async move {
                    ai.stream_prompt(request_id, model, prompt, system_prompt, temperature, tx).await;
                });
            }

            MeshMessage::ConfigSync { features, poll_interval_secs } => {
                info!("Received dynamic remote feature config update: {:?}", features);
                self.config.features = features;
                if let Some(interval) = poll_interval_secs {
                    self.config.poll_interval_secs = interval;
                }
                let _ = self.config.save();
            }

            MeshMessage::UpgradeTrigger { target_version, download_url, sha256 } => {
                info!("Received upgrade trigger to version: {}", target_version);
                tokio::spawn(async move {
                    if let Err(e) = RollbackUpdater::perform_upgrade(&download_url, &sha256).await {
                        error!("Auto-update failed: {}", e);
                    }
                });
            }

            _ => {}
        }
    }

    async fn execute_command(&self, action: &str, args: &[String]) -> (bool, i32, String, String, u64) {
        let start = Instant::now();

        // ─── Native Ollama VRAM & Lifecycle Actions ───
        if action == "ollama_load" {
            let model = args.first().cloned().unwrap_or_default();
            let dur = args.get(1).map(|s| s.as_str());
            let ai = {
                let guard = self.modules.lock().await;
                guard.ai_bridge().clone()
            };
            return match ai.load_model(&model, dur).await {
                Ok(_) => (true, 0, format!("Model '{}' pre-loaded into VRAM successfully", model), "".into(), start.elapsed().as_millis() as u64),
                Err(e) => (false, 1, "".into(), e, start.elapsed().as_millis() as u64),
            };
        }

        if action == "ollama_unload" {
            let model = args.first().cloned().unwrap_or_default();
            let ai = {
                let guard = self.modules.lock().await;
                guard.ai_bridge().clone()
            };
            return match ai.unload_model(&model).await {
                Ok(_) => (true, 0, format!("Model '{}' evicted from VRAM successfully", model), "".into(), start.elapsed().as_millis() as u64),
                Err(e) => (false, 1, "".into(), e, start.elapsed().as_millis() as u64),
            };
        }

        if action == "ollama_pull" {
            let model = args.first().cloned().unwrap_or_default();
            let ai = {
                let guard = self.modules.lock().await;
                guard.ai_bridge().clone()
            };
            return match ai.pull_model(&model).await {
                Ok(_) => (true, 0, format!("Model '{}' pulled successfully", model), "".into(), start.elapsed().as_millis() as u64),
                Err(e) => (false, 1, "".into(), e, start.elapsed().as_millis() as u64),
            };
        }

        if action == "ollama_ps" {
            let ai = {
                let guard = self.modules.lock().await;
                guard.ai_bridge().clone()
            };
            let procs = ai.poll_loaded_models().await;
            let json = serde_json::to_string(&procs).unwrap_or_else(|_| "[]".into());
            return (true, 0, json, "".into(), start.elapsed().as_millis() as u64);
        }

        let (cmd, cmd_args): (&str, Vec<&str>) = match action {
            "reboot" => {
                #[cfg(target_os = "windows")]
                { ("shutdown", vec!["/r", "/t", "5"]) }
                #[cfg(target_os = "macos")]
                { ("shutdown", vec!["-r", "+1"]) }
                #[cfg(target_os = "linux")]
                { ("systemctl", vec!["reboot"]) }
                #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
                { ("reboot", vec![]) }
            }
            "shutdown" => {
                #[cfg(target_os = "windows")]
                { ("shutdown", vec!["/s", "/t", "5"]) }
                #[cfg(target_os = "macos")]
                { ("shutdown", vec!["-h", "+1"]) }
                #[cfg(target_os = "linux")]
                { ("systemctl", vec!["poweroff"]) }
                #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
                { ("poweroff", vec![]) }
            }
            "sleep" => {
                #[cfg(target_os = "windows")]
                { ("rundll32.exe", vec!["powrprof.dll,SetSuspendState"]) }
                #[cfg(target_os = "macos")]
                { ("pmset", vec!["sleepnow"]) }
                #[cfg(target_os = "linux")]
                { ("systemctl", vec!["suspend"]) }
                #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
                { ("true", vec![]) }
            }
            "restart_service" => {
                let s_name = args.first().map(|s| s.as_str()).unwrap_or("");
                #[cfg(target_os = "windows")]
                { ("powershell", vec!["-Command", "Restart-Service", s_name]) }
                #[cfg(target_os = "macos")]
                { ("launchctl", vec!["kickstart", "-k", s_name]) }
                #[cfg(target_os = "linux")]
                { ("systemctl", vec!["restart", s_name]) }
                #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
                { ("true", vec![]) }
            }
            "docker_restart" => {
                let c_name = args.first().map(|s| s.as_str()).unwrap_or("");
                ("docker", vec!["restart", c_name])
            }
            "docker_prune" => {
                ("docker", vec!["system", "prune", "-f"])
            }
            "clear_cache" => {
                #[cfg(target_os = "linux")]
                { ("sync", vec![]) }
                #[cfg(not(target_os = "linux"))]
                { ("echo", vec!["Caches cleared"]) }
            }
            _ => {
                return (false, -1, "".into(), format!("Action '{}' is not in allowlist", action), 0);
            }
        };

        let output = tokio::process::Command::new(cmd)
            .args(&cmd_args)
            .output()
            .await;

        let elapsed = start.elapsed().as_millis() as u64;

        match output {
            Ok(out) => {
                let stdout = String::from_utf8_lossy(&out.stdout).to_string();
                let stderr = String::from_utf8_lossy(&out.stderr).to_string();
                (out.status.success(), out.status.code().unwrap_or(0), stdout, stderr, elapsed)
            }
            Err(e) => (false, -1, "".into(), e.to_string(), elapsed),
        }
    }
}

