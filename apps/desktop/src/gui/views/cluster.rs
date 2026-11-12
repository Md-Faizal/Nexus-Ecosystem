use crate::gui::components::charts::render_topology_canvas;
use crate::gui::components::icons::{icon_button, icon_label_button, IconKind};
use crate::gui::state::{GuiCommand, GuiState, LogLevel, SettingsTab};
use crate::gui::theme::*;
use eframe::egui;
use egui::FontId;

pub fn render(ui: &mut egui::Ui, state: &mut GuiState, tier: LayoutTier) {
    let gap = 16.0;

    // ─── Topology Canvas ───
    card_frame().show(ui, |ui| {
        ui.set_width(ui.available_width());
        ui.horizontal(|ui| {
            ui.label(
                egui::RichText::new("01 // FLEET MESH TOPOLOGY")
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
        ui.add_space(10.0);

        let local_cpu = state
            .telemetry
            .as_ref()
            .map(|t| t.cpu.usage_pct)
            .unwrap_or(0.0);
        render_topology_canvas(
            ui,
            &state.config.node_id,
            &state.config.hostname,
            local_cpu,
            &state.remote_nodes,
            state.hub_latency_ms,
            &mut state.selected_node_id,
        );

        if let Some(ref sel_id) = state.selected_node_id {
            if let Some(node) = state.remote_nodes.iter().find(|n| &n.node_id == sel_id) {
                ui.add_space(10.0);
                row_frame().show(ui, |ui| {
                    ui.set_width(ui.available_width());
                    ui.horizontal_wrapped(|ui| {
                        pill_badge(ui, &format!("SELECTED: {}", node.hostname), ACCENT_CYAN);
                        pill_badge(ui, &format!("ID: {}", node.node_id), TEXT_2);
                        pill_badge(ui, &format!("{}/{}", node.os, node.arch), ACCENT_VIOLET);
                        if !node.models.is_empty() {
                            pill_badge(
                                ui,
                                &format!("Models: {}", node.models.join(", ")),
                                ACCENT_AMBER,
                            );
                        }
                    });
                });
            }
        }

        if let Some(ref err) = state.cluster_error {
            ui.add_space(8.0);
            pill_badge(ui, &format!("Mesh Notice: {}", err), ACCENT_AMBER);
        }
    });

    ui.add_space(gap);

    let locked = is_cluster_locked(state);

    // ─── Edge Workstations ───
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
            for node in &state.remote_nodes {
                render_node_tile(ui, node);
                ui.add_space(10.0);
            }
        } else {
            for chunk in state.remote_nodes.chunks(2) {
                ui.columns(2, |cols| {
                    for (idx, node) in chunk.iter().enumerate() {
                        render_node_tile(&mut cols[idx], node);
                    }
                });
                ui.add_space(10.0);
            }
        }
    });

    ui.add_space(gap);

    // ─── Remote Services & Local Docker ───
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

fn render_node_tile(ui: &mut egui::Ui, node: &crate::modules::cluster_ops::RemoteNodeInfo) {
    let online = node.status == "online";
    let status_col = if online { ACCENT_EMERALD } else { ACCENT_CRIMSON };
    tile_frame().show(ui, |ui| {
        ui.set_width(ui.available_width());
        ui.horizontal(|ui| {
            ui.label(
                egui::RichText::new(&node.hostname)
                    .font(FontId::proportional(13.5))
                    .strong()
                    .color(TEXT_1),
            );
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
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

        ui.add_space(8.0);
        ui.horizontal_wrapped(|ui| {
            pill_badge(ui, &format!("CPU {:.1}%", cpu_pct), ACCENT_CYAN);
            pill_badge(ui, &format!("RAM {:.1}%", ram_pct), ACCENT_VIOLET);
            if !node.models.is_empty() {
                pill_badge(
                    ui,
                    &format!("{} AI Models", node.models.len()),
                    ACCENT_AMBER,
                );
            }
        });
    });
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
