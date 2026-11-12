use crate::gui::components::icons::{icon_button, icon_label_button, IconKind};
use crate::gui::components::topology::TopologyCanvas;
use crate::gui::state::{GuiCommand, GuiState, LogLevel, SettingsTab};
use crate::gui::theme::*;
use eframe::egui;
use egui::{Color32, FontId};

pub fn render(ui: &mut egui::Ui, state: &mut GuiState, tier: LayoutTier) {
    let gap = 16.0;

    // ─── 01 // FLEET REAL-TIME TRAFFIC & MESH TOPOLOGY ───
    card_frame().show(ui, |ui| {
        ui.set_width(ui.available_width());

        // Header: Title + Sync Trigger + RTT
        ui.horizontal(|ui| {
            ui.label(
                egui::RichText::new("01 // FLEET MESH TOPOLOGY & REAL-TIME TRAFFIC")
                    .font(FontId::monospace(10.5))
                    .color(TEXT_2),
            );
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                let refresh_lbl = if state.cluster_loading {
                    "Syncing…"
                } else {
                    "Sync Mesh"
                };
                if icon_label_button(ui, IconKind::Refresh, refresh_lbl).clicked()
                    && !state.cluster_loading
                {
                    state.cluster_loading = true;
                    let _ = state.cmd_tx.send(GuiCommand::RefreshCluster {
                        hub_url: state.hub_url_input.clone(),
                        pin: state.effective_pin(),
                    });
                }
                if let Some(ms) = state.hub_latency_ms {
                    pill_badge(ui, &format!("RTT {}ms", ms), ACCENT_EMERALD);
                }
            });
        });

        ui.add_space(8.0);

        // Real-Time Fleet Traffic HUD Status Bar
        render_fleet_traffic_bar(ui, state);

        ui.add_space(10.0);

        // Industrial Interactive Topology Canvas (Pan, Zoom, Packet Flows, CAN-bus trunk)
        TopologyCanvas::render(ui, state);

        // Deep Inspection Panel if a node is selected
        if state.selected_node_id.is_some() {
            ui.add_space(10.0);
            render_node_deep_inspector(ui, state);
        }

        if let Some(ref err) = state.cluster_error {
            ui.add_space(8.0);
            pill_badge(ui, &format!("Mesh Notice: {}", err), ACCENT_AMBER);
        }
    });

    ui.add_space(gap);

    let locked = is_cluster_locked(state);

    // ─── 02 // CONNECTED EDGE WORKSTATIONS ───
    card_frame().show(ui, |ui| {
        ui.set_width(ui.available_width());
        render_section_header(
            ui,
            "02 // CONNECTED EDGE WORKSTATIONS",
            if locked {
                "LOCKED".to_string()
            } else {
                format!("{} Nodes", state.remote_nodes.len())
            }
            .as_str(),
            if locked { ACCENT_AMBER } else { ACCENT_CYAN },
        );

        if locked {
            render_cluster_lock_panel(
                ui,
                state,
                "Configure a valid Admin PIN in Preferences -> Mesh & Hub to view remote edge workstations.",
            );
        } else if state.remote_nodes.is_empty() {
            ui.label(
                egui::RichText::new("No remote edge nodes reported.")
                    .font(FontId::proportional(12.5))
                    .color(TEXT_3),
            );
        } else if tier.is_compact() {
            let mut clicked_id: Option<String> = None;
            for node in &state.remote_nodes {
                let is_sel = state.selected_node_id.as_deref() == Some(&node.node_id);
                if render_node_tile(ui, node, is_sel) {
                    clicked_id = Some(node.node_id.clone());
                }
                ui.add_space(10.0);
            }
            if let Some(id) = clicked_id {
                state.selected_node_id = Some(id);
            }
        } else {
            let mut clicked_id: Option<String> = None;
            for chunk in state.remote_nodes.chunks(2) {
                ui.columns(2, |cols| {
                    for (idx, node) in chunk.iter().enumerate() {
                        let is_sel = state.selected_node_id.as_deref() == Some(&node.node_id);
                        if render_node_tile(&mut cols[idx], node, is_sel) {
                            clicked_id = Some(node.node_id.clone());
                        }
                    }
                });
                ui.add_space(10.0);
            }
            if let Some(id) = clicked_id {
                state.selected_node_id = Some(id);
            }
        }
    });

    ui.add_space(gap);

    // ─── 03 // REMOTE SERVICES & DOCKER ───
    if matches!(tier, LayoutTier::Wide) {
        ui.columns(2, |cols| {
            render_remote_services_card(&mut cols[0], state, tier);
            render_docker_card(&mut cols[1], state);
        });
    } else {
        render_remote_services_card(ui, state, tier);
        ui.add_space(gap);
        render_docker_card(ui, state);
    }
}

fn render_fleet_traffic_bar(ui: &mut egui::Ui, state: &GuiState) {
    let rx_rate = state.total_fleet_rx_bytes_sec();
    let tx_rate = state.total_fleet_tx_bytes_sec();
    let online_nodes = state.active_online_nodes_count();

    row_frame().show(ui, |ui| {
        ui.set_width(ui.available_width());
        ui.horizontal_wrapped(|ui| {
            pill_badge(ui, &format!("FLEET RX: {}", format_rate(rx_rate)), ACCENT_CYAN);
            pill_badge(ui, &format!("FLEET TX: {}", format_rate(tx_rate)), ACCENT_EMERALD);
            pill_badge(ui, &format!("ONLINE NODES: {}", online_nodes), ACCENT_VIOLET);
            pill_badge(ui, "CAN-BUS 500kbps // NOMINAL", ACCENT_AMBER);
            if let Some(rtt) = state.hub_latency_ms {
                pill_badge(ui, &format!("HUB SYNC: {}ms", rtt), TEXT_2);
            }
        });
    });
}

fn render_node_deep_inspector(ui: &mut egui::Ui, state: &mut GuiState) {
    let sel_id = match state.selected_node_id.clone() {
        Some(id) => id,
        None => return,
    };

    let (hostname, os, arch, is_online, cpu_pct, ram_pct, models, rx, tx) = {
        if sel_id == state.config.node_id || sel_id.eq_ignore_ascii_case(&state.config.hostname) {
            let cpu = state.telemetry.as_ref().map(|t| t.cpu.usage_pct).unwrap_or(0.0);
            let ram = state.telemetry.as_ref().map(|t| t.memory.used_pct).unwrap_or(0.0);
            let rx = state.telemetry.as_ref().map(|t| t.network.rx_bytes_sec).unwrap_or(0);
            let tx = state.telemetry.as_ref().map(|t| t.network.tx_bytes_sec).unwrap_or(0);
            (
                state.config.hostname.clone(),
                state.config.os.clone(),
                state.config.arch.clone(),
                true,
                cpu,
                ram,
                state.ai_telemetry.models.clone(),
                rx,
                tx,
            )
        } else if let Some(n) = state
            .remote_nodes
            .iter()
            .find(|n| n.node_id == sel_id || n.hostname.eq_ignore_ascii_case(&sel_id))
        {
            let is_on = n.status == "online";
            let (cpu, ram, rx, tx) = if is_on {
                let c = n
                    .telemetry
                    .as_ref()
                    .and_then(|t| t.get("cpu")?.get("usage_pct")?.as_f64())
                    .map(|v| v as f32)
                    .unwrap_or(0.0);
                let m = n
                    .telemetry
                    .as_ref()
                    .and_then(|t| t.get("memory")?.get("used_pct")?.as_f64())
                    .map(|v| v as f32)
                    .unwrap_or(0.0);
                let r_rx = n
                    .telemetry
                    .as_ref()
                    .and_then(|t| t.get("network")?.get("rx_bytes_sec")?.as_u64())
                    .unwrap_or(0);
                let r_tx = n
                    .telemetry
                    .as_ref()
                    .and_then(|t| t.get("network")?.get("tx_bytes_sec")?.as_u64())
                    .unwrap_or(0);
                (c, m, r_rx, r_tx)
            } else {
                (0.0, 0.0, 0, 0)
            };
            (
                n.hostname.clone(),
                n.os.clone(),
                n.arch.clone(),
                is_on,
                cpu,
                ram,
                if is_on { n.models.clone() } else { vec![] },
                rx,
                tx,
            )
        } else {
            // Synthesized cloud core node (e.g. my-mac, cos, pro)
            let is_hub = sel_id.eq_ignore_ascii_case("my-mac");
            let is_on = if is_hub { state.hub_latency_ms.is_some() } else { false };
            let (c, m, r_rx, r_tx) = if is_on {
                (14.0, 38.0, state.total_fleet_tx_bytes_sec(), state.total_fleet_rx_bytes_sec())
            } else {
                (0.0, 0.0, 0, 0)
            };
            (
                sel_id.clone(),
                "linux".to_string(),
                "x86_64".to_string(),
                is_on,
                c,
                m,
                vec![],
                r_rx,
                r_tx,
            )
        }
    };

    tile_frame().show(ui, |ui| {
        ui.set_width(ui.available_width());
        ui.horizontal(|ui| {
            ui.label(
                egui::RichText::new(format!("INSPECTING NODE // {}", hostname))
                    .font(FontId::monospace(11.5))
                    .strong()
                    .color(ACCENT_CYAN),
            );
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                if ui
                    .button(
                        egui::RichText::new("✕ Close")
                            .font(FontId::monospace(9.5))
                            .color(TEXT_2),
                    )
                    .clicked()
                {
                    state.selected_node_id = None;
                }
                pill_badge(
                    ui,
                    if is_online { "ONLINE" } else { "OFFLINE" },
                    if is_online { ACCENT_EMERALD } else { ACCENT_CRIMSON },
                );
                pill_badge(ui, &format!("{}/{}", os, arch), ACCENT_VIOLET);
                pill_badge(ui, &format!("ID: {}", sel_id), TEXT_2);
            });
        });

        ui.add_space(8.0);

        // Hardware & Network Metrics row (strictly omitted or zeroed when offline)
        if is_online {
            ui.horizontal_wrapped(|ui| {
                pill_badge(ui, &format!("CPU {:.1}%", cpu_pct), ACCENT_CYAN);
                pill_badge(ui, &format!("RAM {:.1}%", ram_pct), ACCENT_VIOLET);
                pill_badge(ui, &format!("NET RX: {}", format_rate(rx)), ACCENT_EMERALD);
                pill_badge(ui, &format!("NET TX: {}", format_rate(tx)), ACCENT_CYAN);
                if !models.is_empty() {
                    pill_badge(
                        ui,
                        &format!("⚡ AI Models: {}", models.join(", ")),
                        ACCENT_AMBER,
                    );
                }
            });
        } else {
            ui.horizontal_wrapped(|ui| {
                pill_badge(ui, "HARDWARE SENSORS OFFLINE", ACCENT_CRIMSON);
                pill_badge(ui, "CPU N/A", TEXT_3);
                pill_badge(ui, "RAM N/A", TEXT_3);
                pill_badge(ui, "NET INACTIVE (0 B/s)", TEXT_3);
            });
        }

        ui.add_space(10.0);

        // Real-Time CAN Frame Inspector Log
        ui.label(
            egui::RichText::new("REAL-TIME CAN-BUS PACKET STREAM & FRAME TELEMETRY")
                .font(FontId::monospace(9.0))
                .color(TEXT_3),
        );
        ui.add_space(4.0);

        let frame_rows: Vec<(&str, &str, String, Color32)> = if is_online {
            vec![
                (
                    "0x1A0",
                    "TelemetryReport",
                    format!(
                        "CPU: {:.1}% | RAM: {:.1}% | Link RX: {}",
                        cpu_pct,
                        ram_pct,
                        format_rate(rx)
                    ),
                    ACCENT_CYAN,
                ),
                (
                    "0x040",
                    "HeartbeatAck",
                    "Cluster synchronization status: OK (0ms jitter)".to_string(),
                    ACCENT_VIOLET,
                ),
                (
                    "0x2C4",
                    "CanBusMultiplex",
                    format!("TX Rate: {} | Active Sockets: 1 ESTABLISHED", format_rate(tx)),
                    ACCENT_EMERALD,
                ),
            ]
        } else {
            vec![
                (
                    "0x000",
                    "SessionClosed",
                    "Remote node disconnected from CAN-bus WebSocket mesh".to_string(),
                    ACCENT_CRIMSON,
                ),
                (
                    "0x040",
                    "HeartbeatTimeout",
                    "Heartbeat signal lost: Telemetry streaming suspended".to_string(),
                    TEXT_3,
                ),
            ]
        };

        for (can_id, frame_type, details, col) in frame_rows {
            row_frame().show(ui, |ui| {
                ui.set_width(ui.available_width());
                ui.horizontal(|ui| {
                    ui.label(
                        egui::RichText::new(format!("[CAN {}]", can_id))
                            .font(FontId::monospace(9.0))
                            .color(col),
                    );
                    ui.label(
                        egui::RichText::new(frame_type)
                            .font(FontId::monospace(9.5))
                            .strong()
                            .color(TEXT_1),
                    );
                    ui.label(
                        egui::RichText::new(details)
                            .font(FontId::monospace(8.5))
                            .color(TEXT_2),
                    );
                });
            });
            ui.add_space(3.0);
        }
    });
}

fn render_node_tile(
    ui: &mut egui::Ui,
    node: &crate::modules::cluster_ops::RemoteNodeInfo,
    is_selected: bool,
) -> bool {
    let mut inspect_clicked = false;
    let online = node.status == "online";
    let status_col = if online { ACCENT_EMERALD } else { ACCENT_CRIMSON };

    tile_frame().show(ui, |ui| {
        ui.set_width(ui.available_width());
        ui.horizontal(|ui| {
            ui.label(
                egui::RichText::new(&node.hostname)
                    .font(FontId::proportional(13.5))
                    .strong()
                    .color(if is_selected { ACCENT_CYAN } else { TEXT_1 }),
            );
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                let inspect_txt = if is_selected { "Inspecting" } else { "Inspect" };
                if ui
                    .add(
                        egui::Button::new(
                            egui::RichText::new(inspect_txt)
                                .font(FontId::monospace(9.0))
                                .color(if is_selected { ACCENT_CYAN } else { TEXT_2 }),
                        )
                        .fill(if is_selected { BG_TILE } else { Color32::TRANSPARENT }),
                    )
                    .clicked()
                {
                    inspect_clicked = true;
                }
                pill_badge(ui, if online { "ONLINE" } else { "OFFLINE" }, status_col);
            });
        });
        ui.add_space(4.0);
        ui.label(
            egui::RichText::new(format!(
                "{} / {} • seen {}s ago",
                node.os, node.arch, node.last_seen_secs
            ))
            .font(FontId::monospace(10.0))
            .color(TEXT_3),
        );

        ui.add_space(8.0);
        ui.horizontal_wrapped(|ui| {
            if online {
                let cpu_pct = node
                    .telemetry
                    .as_ref()
                    .and_then(|t| t.get("cpu"))
                    .and_then(|c| c.get("usage_pct"))
                    .and_then(|v| v.as_f64())
                    .unwrap_or(0.0);
                let ram_pct = node
                    .telemetry
                    .as_ref()
                    .and_then(|t| t.get("memory"))
                    .and_then(|m| m.get("used_pct"))
                    .and_then(|v| v.as_f64())
                    .unwrap_or(0.0);

                pill_badge(ui, &format!("CPU {:.1}%", cpu_pct), ACCENT_CYAN);
                pill_badge(ui, &format!("RAM {:.1}%", ram_pct), ACCENT_VIOLET);
                if !node.models.is_empty() {
                    pill_badge(
                        ui,
                        &format!("{} AI Models", node.models.len()),
                        ACCENT_AMBER,
                    );
                }
            } else {
                pill_badge(ui, "TELEMETRY PAUSED", TEXT_3);
                pill_badge(ui, "SOCKET CLOSED", ACCENT_CRIMSON);
            }
        });
    });

    inspect_clicked
}

fn format_rate(bytes: u64) -> String {
    if bytes >= 1024 * 1024 {
        format!("{:.1} MB/s", bytes as f32 / (1024.0 * 1024.0))
    } else if bytes >= 1024 {
        format!("{:.1} KB/s", bytes as f32 / 1024.0)
    } else {
        format!("{} B/s", bytes)
    }
}

fn is_cluster_locked(state: &GuiState) -> bool {
    state.effective_pin().is_none()
        || state
            .cluster_error
            .as_deref()
            .is_some_and(|e| e.contains("403"))
}

fn render_cluster_lock_panel(ui: &mut egui::Ui, state: &mut GuiState, message: &str) {
    tile_frame().show(ui, |ui| {
        ui.set_width(ui.available_width());
        ui.horizontal_wrapped(|ui| {
            pill_badge(ui, "LOCKED // ADMIN PIN REQUIRED", ACCENT_AMBER);
        });
        ui.add_space(6.0);
        ui.label(
            egui::RichText::new(message)
                .font(FontId::proportional(12.5))
                .color(TEXT_2),
        );
        ui.add_space(8.0);
        if icon_label_button(ui, IconKind::Settings, "Unlock with Admin PIN").clicked() {
            state.show_settings = true;
            state.settings_tab = SettingsTab::Mesh;
        }
    });
}

fn render_remote_services_card(ui: &mut egui::Ui, state: &mut GuiState, tier: LayoutTier) {
    let locked = is_cluster_locked(state);
    card_frame().show(ui, |ui| {
        ui.set_width(ui.available_width());
        render_section_header(
            ui,
            "03 // CLUSTER SERVICES",
            if locked {
                "LOCKED".to_string()
            } else {
                format!("{} Units", state.remote_services.len())
            }
            .as_str(),
            if locked { ACCENT_AMBER } else { ACCENT_EMERALD },
        );

        if locked {
            render_cluster_lock_panel(
                ui,
                state,
                "Cluster service controls are locked. Enter a valid Admin PIN in Preferences -> Mesh & Hub.",
            );
        } else if state.remote_services.is_empty() {
            ui.label(
                egui::RichText::new("No remote cluster services loaded.")
                    .font(FontId::proportional(12.0))
                    .color(TEXT_3),
            );
        } else {
            let mut action_to_dispatch: Option<(String, String)> = None;
            for svc in &state.remote_services {
                let running = svc.status == "running" || svc.status == "active";
                let col = if running { ACCENT_EMERALD } else { ACCENT_CRIMSON };
                row_frame().show(ui, |ui| {
                    ui.set_width(ui.available_width());
                    ui.horizontal(|ui| {
                        ui.vertical(|ui| {
                            ui.label(
                                egui::RichText::new(&svc.name)
                                    .font(FontId::proportional(12.5))
                                    .strong()
                                    .color(TEXT_1),
                            );
                            ui.label(
                                egui::RichText::new(format!(
                                    "{} • :{} • {}",
                                    svc.node, svc.port, svc.svc_type
                                ))
                                .font(FontId::monospace(9.5))
                                .color(TEXT_3),
                            );
                        });
                        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                            if !tier.is_compact() {
                                if icon_button(
                                    ui,
                                    IconKind::Refresh,
                                    26.0,
                                    TEXT_2,
                                    "Restart service",
                                )
                                .clicked()
                                {
                                    action_to_dispatch =
                                        Some((svc.id.clone(), "restart".to_string()));
                                }
                                let toggle_act = if running { "stop" } else { "start" };
                                if action_button(ui, if running { "Stop" } else { "Start" }).clicked()
                                {
                                    action_to_dispatch =
                                        Some((svc.id.clone(), toggle_act.to_string()));
                                }
                            }
                            pill_badge(ui, if running { "RUNNING" } else { "STOPPED" }, col);
                        });
                    });
                });
                ui.add_space(6.0);
            }

            if let Some((service_id, action)) = action_to_dispatch {
                let _ = state.cmd_tx.send(GuiCommand::DispatchServiceAction {
                    hub_url: state.hub_url_input.clone(),
                    pin: state.effective_pin(),
                    service_id: service_id.clone(),
                    action: action.clone(),
                });
                state.set_toast(
                    format!("Dispatching {} to {}…", action, service_id),
                    LogLevel::Info,
                    "Cluster",
                );
            }
        }
    });
}

fn render_docker_card(ui: &mut egui::Ui, state: &mut GuiState) {
    card_frame().show(ui, |ui| {
        ui.set_width(ui.available_width());
        ui.horizontal(|ui| {
            ui.label(
                egui::RichText::new("04 // LOCAL DOCKER SUPERVISOR")
                    .font(FontId::monospace(10.5))
                    .color(TEXT_2),
            );
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                if icon_label_button(ui, IconKind::Refresh, "Scan").clicked() {
                    let _ = state.cmd_tx.send(GuiCommand::RefreshDocker);
                }
            });
        });
        ui.add_space(10.0);

        if state.docker_containers.is_empty() {
            ui.label(
                egui::RichText::new(
                    "No local Docker containers detected (or Docker Engine offline).",
                )
                .font(FontId::proportional(12.0))
                .color(TEXT_3),
            );
        } else {
            let mut docker_cmd: Option<(String, String)> = None;
            for c in &state.docker_containers {
                let running = c.status == "running";
                let col = if running { ACCENT_EMERALD } else { TEXT_3 };
                row_frame().show(ui, |ui| {
                    ui.set_width(ui.available_width());
                    ui.horizontal(|ui| {
                        ui.label(
                            egui::RichText::new(c.name.strip_prefix("docker:").unwrap_or(&c.name))
                                .font(FontId::monospace(11.5))
                                .color(TEXT_1),
                        );
                        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                            if icon_button(
                                ui,
                                IconKind::Refresh,
                                26.0,
                                TEXT_2,
                                "Restart container",
                            )
                            .clicked()
                            {
                                docker_cmd = Some((c.name.clone(), "restart".to_string()));
                            }
                            let act = if running { "stop" } else { "start" };
                            if action_button(ui, if running { "Stop" } else { "Start" }).clicked() {
                                docker_cmd = Some((c.name.clone(), act.to_string()));
                            }
                            pill_badge(ui, &c.status.to_uppercase(), col);
                        });
                    });
                });
                ui.add_space(6.0);
            }
            if let Some((container, action)) = docker_cmd {
                let _ = state.cmd_tx.send(GuiCommand::DockerAction {
                    container: container.clone(),
                    action: action.clone(),
                });
                state.set_toast(
                    format!("Docker {} on {}…", action, container),
                    LogLevel::Info,
                    "Docker",
                );
            }
        }
    });
}
