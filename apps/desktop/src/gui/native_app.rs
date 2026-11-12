use eframe::egui;
use egui::{Align, Color32, FontId, Frame, Layout, Margin, Pos2, Rounding, Sense, Stroke, Vec2};
use std::collections::VecDeque;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant};
use sysinfo::System;

use crate::config::AgentConfig;
use crate::gui::components::{command_palette, settings_modal, sidebar};
use crate::gui::state::{
    spawn_background_worker, GuiCommand, GuiEvent, GuiRoute, GuiState, LogLevel, ProcessEntry,
    ProcessSort, RingBuffer, SettingsTab,
};
use crate::gui::theme::*;
use crate::gui::views;
use crate::mesh::protocol::AiEngineTelemetry;
use crate::modules::chat_storage::{ChatMessageItem, ChatStorage, TurnMetrics};
use crate::modules::fitness::FitnessScore;
use crate::modules::gpu::GpuCollector;
use crate::modules::telemetry::TelemetryCollector;

static GUI_RUNNING: AtomicBool = AtomicBool::new(false);

pub fn launch_native_gui() {
    if GUI_RUNNING.swap(true, Ordering::SeqCst) {
        tracing::warn!("Native GUI window is already open.");
        return;
    }

    let native_options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_inner_size([1280.0, 820.0])
            .with_min_inner_size([640.0, 520.0])
            .with_title("Nexus Flight Deck")
            .with_active(true),
        ..Default::default()
    };

    let run_result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        eframe::run_native(
            "Nexus Flight Deck",
            native_options,
            Box::new(|cc| {
                configure_obsidian_theme(&cc.egui_ctx);
                Ok(Box::new(NodeMonitorApp::new(cc.egui_ctx.clone())))
            }),
        )
    }));

    match run_result {
        Ok(Err(e)) => tracing::error!("Flight Deck GUI exited with error: {}", e),
        Err(_) => tracing::error!("Flight Deck GUI caught an internal panic"),
        Ok(Ok(())) => {}
    }

    GUI_RUNNING.store(false, Ordering::SeqCst);
}

pub struct NodeMonitorApp {
    collector: TelemetryCollector,
    proc_sys: System,
    last_local_poll: Instant,
    last_bg_poll: Instant,
    state: GuiState,
}

impl NodeMonitorApp {
    pub fn new(ctx: egui::Context) -> Self {
        let mut collector = TelemetryCollector::new();
        let initial_payload = collector.collect();
        let mut proc_sys = System::new_all();
        proc_sys.refresh_all();

        let config = AgentConfig::load_or_init();
        let gpu = GpuCollector::collect();

        let total_ram_mb = initial_payload.memory.total_bytes / (1024 * 1024);
        let used_ram_mb = initial_payload.memory.used_bytes / (1024 * 1024);
        let fitness = FitnessScore::calculate(
            total_ram_mb,
            used_ram_mb,
            initial_payload.cpu.usage_pct,
            FitnessScore::detect_is_cloud(),
            false,
            true,
        );

        let hub_url_input = config
            .hubs
            .first()
            .cloned()
            .unwrap_or_else(|| "wss://faizal.uk/api/mesh/ws".to_string());
        let ai_endpoint_input = config.ai_endpoint.clone();
        let pref_coordinator = config.is_feature_enabled("auto_coordinator");
        let pref_high_cadence = config.active_poll_interval_secs <= 2;
        let pref_ai_relay = config.is_feature_enabled("ai_bridge");

        let (cmd_tx, evt_rx) = spawn_background_worker(ctx);

        let chat_sessions = ChatStorage::list_sessions();
        let active_chat = ChatStorage::new_session("local", &config.hostname, "", None, 0.7);
        let crashes = crate::modules::crash::list_recent_crashes(50);

        let mut state = GuiState {
            config,
            route: GuiRoute::Overview,
            sidebar_collapsed: false,
            telemetry: Some(initial_payload.clone()),
            fitness,
            gpu,
            per_core_cpu: proc_sys.cpus().iter().map(|c| c.cpu_usage()).collect(),
            processes: Vec::new(),
            process_search: String::new(),
            process_sort: ProcessSort::Cpu,
            daemon_active: crate::modules::daemon_lock::is_daemon_running(),
            daemon_pid: crate::modules::daemon_lock::read_daemon_state().map(|s| s.pid),
            cpu_history: RingBuffer::new(120),
            ram_history: RingBuffer::new(120),
            gpu_history: RingBuffer::new(120),
            gpu_temp_history: RingBuffer::new(120),
            net_rx_history: RingBuffer::new(120),
            net_tx_history: RingBuffer::new(120),
            hub_latency_history: RingBuffer::new(60),
            remote_nodes: Vec::new(),
            remote_services: Vec::new(),
            cluster_error: None,
            cluster_loading: true,
            hub_latency_ms: None,
            docker_containers: Vec::new(),
            selected_node_id: None,
            ai_telemetry: AiEngineTelemetry {
                engine: "ollama".to_string(),
                endpoint: ai_endpoint_input.clone(),
                models: Vec::new(),
                active_model: None,
                status: "Checking…".to_string(),
            },
            loaded_models: Vec::new(),
            selected_model: String::new(),
            chat_sessions,
            active_chat,
            chat_input: String::new(),
            ai_streaming: false,
            ai_stream_buffer: String::new(),
            ai_stream_started: None,
            show_ai_params: false,
            system_prompt_input: String::new(),
            crashes,
            update_info: None,
            update_checking: false,
            logs: VecDeque::with_capacity(250),
            show_command_palette: false,
            palette_query: String::new(),
            palette_selected: 0,
            show_settings: false,
            settings_tab: SettingsTab::Mesh,
            hub_url_input: hub_url_input.clone(),
            admin_pin_input: String::new(),
            ai_endpoint_input: ai_endpoint_input.clone(),
            pref_coordinator,
            pref_high_cadence,
            pref_ai_relay,
            toast: None,
            cmd_tx: cmd_tx.clone(),
            evt_rx,
        };

        state.push_log(
            LogLevel::Info,
            "FlightDeck",
            format!("Initialized Nexus Flight Deck v{}", crate::AGENT_VERSION),
        );

        // Kick off initial async discovery
        let _ = cmd_tx.send(GuiCommand::RefreshCluster {
            hub_url: hub_url_input,
            pin: state.effective_pin(),
        });
        let _ = cmd_tx.send(GuiCommand::RefreshDocker);
        let _ = cmd_tx.send(GuiCommand::RefreshOllama {
            endpoint: ai_endpoint_input,
        });

        let mut app = Self {
            collector,
            proc_sys,
            last_local_poll: Instant::now() - Duration::from_secs(10),
            last_bg_poll: Instant::now(),
            state,
        };
        app.sample_local_metrics();
        app
    }

    fn sample_local_metrics(&mut self) {
        let interval = if self.state.pref_high_cadence {
            Duration::from_secs(1)
        } else {
            Duration::from_secs(5)
        };
        if self.last_local_poll.elapsed() < interval {
            return;
        }
        self.last_local_poll = Instant::now();

        let payload = self.collector.collect();
        self.proc_sys.refresh_cpu_all();
        self.proc_sys.refresh_processes(sysinfo::ProcessesToUpdate::All, true);

        self.state.per_core_cpu = self
            .proc_sys
            .cpus()
            .iter()
            .map(|c| c.cpu_usage())
            .collect();

        let total_cores = self.proc_sys.cpus().len().max(1) as f32;
        self.state.processes = self
            .proc_sys
            .processes()
            .iter()
            .map(|(pid, proc_)| ProcessEntry {
                pid: pid.as_u32(),
                name: proc_.name().to_string_lossy().to_string(),
                cpu_pct: proc_.cpu_usage() / total_cores,
                memory_mb: proc_.memory() as f64 / (1024.0 * 1024.0),
                status: format!("{:?}", proc_.status()),
            })
            .collect();

        let gpu = GpuCollector::collect();
        let total_ram_mb = payload.memory.total_bytes / (1024 * 1024);
        let used_ram_mb = payload.memory.used_bytes / (1024 * 1024);

        self.state.fitness = FitnessScore::calculate(
            total_ram_mb,
            used_ram_mb,
            payload.cpu.usage_pct,
            FitnessScore::detect_is_cloud(),
            false,
            true,
        );

        self.state.cpu_history.push(payload.cpu.usage_pct);
        self.state.ram_history.push(payload.memory.used_pct);
        self.state
            .net_rx_history
            .push(payload.network.rx_bytes_sec as f32 / 1024.0);
        self.state
            .net_tx_history
            .push(payload.network.tx_bytes_sec as f32 / 1024.0);

        if let Some(ref g) = gpu {
            self.state.gpu_history.push(g.utilization_pct);
            if let Some(temp) = g.temperature_c {
                self.state.gpu_temp_history.push(temp);
            }
        } else {
            self.state.gpu_history.push(0.0);
        }

        self.state.gpu = gpu;
        self.state.telemetry = Some(payload);
        self.state.daemon_active = crate::modules::daemon_lock::is_daemon_running();
        self.state.daemon_pid = crate::modules::daemon_lock::read_daemon_state().map(|s| s.pid);
    }

    fn poll_background_events(&mut self) {
        while let Ok(evt) = self.state.evt_rx.try_recv() {
            match evt {
                GuiEvent::ClusterUpdated {
                    nodes,
                    services,
                    latency_ms,
                } => {
                    self.state.cluster_loading = false;
                    self.state.hub_latency_ms = latency_ms;
                    if let Some(ms) = latency_ms {
                        self.state.hub_latency_history.push(ms as f32);
                    }
                    match nodes {
                        Ok(n) => {
                            self.state.remote_nodes = n;
                            self.state.cluster_error = None;
                        }
                        Err(e) => self.state.cluster_error = Some(e),
                    }
                    if let Ok(s) = services {
                        self.state.remote_services = s;
                    }
                }
                GuiEvent::DockerUpdated(containers) => {
                    self.state.docker_containers = containers;
                }
                GuiEvent::OllamaUpdated { telemetry, loaded } => {
                    if self.state.selected_model.is_empty() {
                        if let Some(first) = telemetry.models.first() {
                            self.state.selected_model = first.clone();
                        }
                    }
                    self.state.ai_telemetry = telemetry;
                    self.state.loaded_models = loaded;
                }
                GuiEvent::AiStreamToken(tok) => {
                    self.state.ai_stream_buffer.push_str(&tok);
                }
                GuiEvent::AiStreamDone {
                    eval_count,
                    elapsed_secs,
                } => {
                    self.state.ai_streaming = false;
                    let content = std::mem::take(&mut self.state.ai_stream_buffer);
                    let tok_per_sec = if elapsed_secs > 0.01 && eval_count > 0 {
                        eval_count as f64 / elapsed_secs
                    } else {
                        0.0
                    };
                    self.state.active_chat.messages.push(ChatMessageItem {
                        role: "assistant".to_string(),
                        content,
                        timestamp: chrono::Local::now().timestamp() as u64,
                        metrics: Some(TurnMetrics {
                            tok_per_sec,
                            total_tokens: eval_count,
                            elapsed_secs,
                        }),
                    });
                    if let Ok(_) = ChatStorage::save_session(&self.state.active_chat) {
                        self.state.refresh_chat_sessions();
                    }
                }
                GuiEvent::AiStreamError(err) => {
                    self.state.ai_streaming = false;
                    self.state.ai_stream_buffer.clear();
                    self.state.set_toast(err, LogLevel::Error, "AI Studio");
                }
                GuiEvent::UpdateChecked(res) => {
                    self.state.update_checking = false;
                    match res {
                        Ok(info) => {
                            if let Some(ref i) = info {
                                let msg = if i.update_available {
                                    format!("Update available: v{}", i.latest_version)
                                } else {
                                    format!("Up to date (v{})", i.current_version)
                                };
                                self.state.set_toast(msg, LogLevel::Info, "Updater");
                            }
                            self.state.update_info = info;
                        }
                        Err(e) => {
                            self.state.set_toast(e, LogLevel::Error, "Updater");
                        }
                    }
                }
                GuiEvent::Toast { msg, level, source } => {
                    self.state.set_toast(msg, level, &source);
                }
            }
        }

        // Periodic background sync every 15s
        if self.last_bg_poll.elapsed() >= Duration::from_secs(15) {
            self.last_bg_poll = Instant::now();
            let _ = self.state.cmd_tx.send(GuiCommand::RefreshCluster {
                hub_url: self.state.hub_url_input.clone(),
                pin: self.state.effective_pin(),
            });
            let _ = self.state.cmd_tx.send(GuiCommand::RefreshDocker);
        }
    }

    fn handle_shortcuts(&mut self, ctx: &egui::Context) {
        ctx.input(|i| {
            if i.modifiers.ctrl && i.key_pressed(egui::Key::K) {
                self.state.show_command_palette = !self.state.show_command_palette;
                self.state.palette_query.clear();
                self.state.palette_selected = 0;
            }
            if i.modifiers.ctrl && i.key_pressed(egui::Key::Comma) {
                self.state.show_settings = !self.state.show_settings;
            }
            if i.modifiers.ctrl && i.key_pressed(egui::Key::Num1) {
                self.state.route = GuiRoute::Overview;
            }
            if i.modifiers.ctrl && i.key_pressed(egui::Key::Num2) {
                self.state.route = GuiRoute::Telemetry;
            }
            if i.modifiers.ctrl && i.key_pressed(egui::Key::Num3) {
                self.state.route = GuiRoute::Cluster;
            }
            if i.modifiers.ctrl && i.key_pressed(egui::Key::Num4) {
                self.state.route = GuiRoute::AiStudio;
            }
            if i.modifiers.ctrl && i.key_pressed(egui::Key::Num5) {
                self.state.route = GuiRoute::Diagnostics;
            }
        });
    }

    fn render_header(&mut self, ui: &mut egui::Ui, is_compact_viewport: bool) {
        let hostname = System::host_name()
            .unwrap_or_else(|| self.state.config.hostname.clone())
            .to_uppercase();
        let tier_label = self.state.fitness.tier.to_uppercase();

        ui.horizontal(|ui| {
            // F• Monogram Mark
            let (mono_rect, _) = ui.allocate_exact_size(Vec2::new(32.0, 32.0), Sense::hover());
            ui.painter().rect(
                mono_rect,
                Rounding::same(8.0),
                BG_ELEVATED,
                Stroke::new(1.0, BORDER_STRONG),
            );
            ui.painter().text(
                mono_rect.center(),
                egui::Align2::CENTER_CENTER,
                "F•",
                FontId::monospace(13.0),
                TEXT_1,
            );

            ui.add_space(10.0);

            ui.vertical(|ui| {
                ui.spacing_mut().item_spacing.y = 1.0;
                ui.label(
                    egui::RichText::new("NEXUS FLIGHT DECK")
                        .font(FontId::proportional(13.0))
                        .strong()
                        .color(TEXT_1),
                );
                ui.label(
                    egui::RichText::new(format!(
                        "{} • {} • v{}",
                        hostname,
                        tier_label,
                        crate::AGENT_VERSION
                    ))
                    .font(FontId::monospace(9.5))
                    .color(TEXT_3),
                );
            });

            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                // Settings Gear
                let (gear_rect, gear_resp) =
                    ui.allocate_exact_size(Vec2::new(32.0, 32.0), Sense::click());
                ui.painter().circle(
                    gear_rect.center(),
                    15.5,
                    if gear_resp.hovered() {
                        Color32::from_rgba_unmultiplied(255, 255, 255, 24)
                    } else {
                        Color32::from_rgba_unmultiplied(255, 255, 255, 10)
                    },
                    Stroke::new(1.0, BORDER_STRONG),
                );
                ui.painter().text(
                    gear_rect.center(),
                    egui::Align2::CENTER_CENTER,
                    "⚙",
                    FontId::proportional(14.0),
                    TEXT_2,
                );
                if gear_resp.clicked() {
                    self.state.show_settings = true;
                }

                ui.add_space(8.0);

                // Command Palette Trigger Pill
                let cmd_lbl = if is_compact_viewport {
                    "⌘"
                } else {
                    "⌘ Search / Commands  ^K"
                };
                if action_button(ui, cmd_lbl).clicked() {
                    self.state.show_command_palette = true;
                    self.state.palette_query.clear();
                    self.state.palette_selected = 0;
                }

                if !is_compact_viewport {
                    ui.add_space(8.0);
                    if let Some(ms) = self.state.hub_latency_ms {
                        pill_badge(ui, &format!("HUB {}ms", ms), ACCENT_EMERALD);
                    } else {
                        pill_badge(ui, "LOCAL", TEXT_3);
                    }
                }
            });
        });
    }
}

impl eframe::App for NodeMonitorApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        self.sample_local_metrics();
        self.poll_background_events();
        self.handle_shortcuts(ctx);

        let poll_secs = if self.state.pref_high_cadence { 1 } else { 2 };
        ctx.request_repaint_after(Duration::from_secs(poll_secs));

        let viewport_w = ctx.screen_rect().width();
        let is_compact_viewport = viewport_w < 860.0;

        // ─── Top Architectural Header ───
        egui::TopBottomPanel::top("flight_deck_header")
            .frame(
                Frame::none()
                    .fill(BG_VOID)
                    .inner_margin(Margin::symmetric(
                        if is_compact_viewport { 16.0 } else { 24.0 },
                        14.0,
                    ))
                    .stroke(Stroke::new(1.0_f32, BORDER_SUBTLE)),
            )
            .show(ctx, |ui| {
                self.render_header(ui, is_compact_viewport);
            });

        // ─── Responsive Navigation Rail / Bottom Bar ───
        sidebar::render_sidebar(ctx, &mut self.state, is_compact_viewport);

        // ─── Main Content Canvas ───
        egui::CentralPanel::default()
            .frame(Frame::none().fill(BG_VOID).inner_margin(Margin::ZERO))
            .show(ctx, |ui| {
                // Subtle top ambient satin glow
                let rect = ui.max_rect();
                ui.painter().circle_filled(
                    Pos2::new(rect.center().x, rect.top() - 200.0),
                    440.0,
                    Color32::from_rgba_unmultiplied(255, 255, 255, 6),
                );

                let pad = if is_compact_viewport { 16.0 } else { 24.0 };
                egui::ScrollArea::vertical()
                    .auto_shrink([false, false])
                    .show(ui, |ui| {
                        Frame::none()
                            .inner_margin(Margin::symmetric(pad, pad))
                            .show(ui, |ui| {
                                let content_tier = LayoutTier::from_width(ui.available_width());
                                match self.state.route {
                                    GuiRoute::Overview => {
                                        views::overview::render(ui, &mut self.state, content_tier);
                                    }
                                    GuiRoute::Telemetry => {
                                        views::telemetry::render(ui, &mut self.state, content_tier);
                                    }
                                    GuiRoute::Cluster => {
                                        views::cluster::render(ui, &mut self.state, content_tier);
                                    }
                                    GuiRoute::AiStudio => {
                                        views::ai_studio::render(ui, &mut self.state, content_tier);
                                    }
                                    GuiRoute::Diagnostics => {
                                        views::diagnostics::render(ui, &mut self.state, content_tier);
                                    }
                                }
                            });
                    });
            });

        // ─── Overlays ───
        if self.state.show_command_palette {
            command_palette::render_command_palette(ctx, &mut self.state);
        }
        if self.state.show_settings {
            settings_modal::render_settings_modal(ctx, &mut self.state);
        }

        // ─── Semantic Toast Banner ───
        if let Some((msg, level, since)) = &self.state.toast {
            if since.elapsed() < Duration::from_secs(3) {
                let border_col = match level {
                    LogLevel::Info => ACCENT_CYAN,
                    LogLevel::Success => ACCENT_EMERALD,
                    LogLevel::Warn => ACCENT_AMBER,
                    LogLevel::Error => ACCENT_CRIMSON,
                };
                egui::Area::new(egui::Id::new("toast_area"))
                    .anchor(egui::Align2::CENTER_BOTTOM, Vec2::new(0.0, -24.0))
                    .order(egui::Order::Tooltip)
                    .show(ctx, |ui| {
                        Frame::none()
                            .fill(BG_ELEVATED)
                            .stroke(Stroke::new(1.0, border_col))
                            .rounding(Rounding::same(20.0))
                            .inner_margin(Margin::symmetric(16.0, 8.0))
                            .show(ui, |ui| {
                                ui.label(
                                    egui::RichText::new(msg)
                                        .font(FontId::monospace(11.0))
                                        .color(TEXT_1),
                                );
                            });
                    });
            } else {
                self.state.toast = None;
            }
        }
    }
}
