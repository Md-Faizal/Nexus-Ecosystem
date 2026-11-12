use crate::gui::state::{GuiCommand, GuiState, LogLevel, SettingsTab};
use crate::gui::theme::*;
use eframe::egui;
use egui::{Color32, FontId, Frame, Margin, Rounding, Sense, Stroke, Vec2};

pub fn render_settings_modal(ctx: &egui::Context, state: &mut GuiState) {
    let screen = ctx.screen_rect();
    let mut close = false;

    ctx.input(|i| {
        if i.key_pressed(egui::Key::Escape) {
            close = true;
        }
    });

    egui::Area::new(egui::Id::new("preferences_scrim"))
        .fixed_pos(screen.min)
        .order(egui::Order::Foreground)
        .show(ctx, |ui| {
            let resp = ui.allocate_rect(screen, Sense::click());
            ui.painter().rect_filled(
                screen,
                Rounding::ZERO,
                Color32::from_rgba_unmultiplied(0, 0, 0, 185),
            );
            if resp.clicked() {
                close = true;
            }
        });

    let modal_w = (screen.width() - 32.0).clamp(320.0, 540.0);
    egui::Window::new("settings_modal_window")
        .title_bar(false)
        .collapsible(false)
        .resizable(false)
        .anchor(egui::Align2::CENTER_CENTER, Vec2::ZERO)
        .fixed_size(Vec2::new(modal_w, 460.0))
        .frame(
            Frame::none()
                .fill(BG_SHEET)
                .stroke(Stroke::new(1.0, BORDER_STRONG))
                .rounding(Rounding::same(16.0))
                .inner_margin(Margin::same(22.0)),
        )
        .show(ctx, |ui| {
            ui.horizontal(|ui| {
                ui.vertical(|ui| {
                    ui.label(
                        egui::RichText::new("NODE PREFERENCES")
                            .font(FontId::proportional(15.0))
                            .strong()
                            .color(TEXT_1),
                    );
                    ui.label(
                        egui::RichText::new(format!("Node ID: {}", state.config.node_id))
                            .font(FontId::monospace(10.0))
                            .color(TEXT_3),
                    );
                });
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    if action_button(ui, "✕").clicked() {
                        close = true;
                    }
                });
            });

            ui.add_space(14.0);

            // Tab selector
            ui.horizontal(|ui| {
                for (tab, label) in [
                    (SettingsTab::Mesh, "Mesh & Hub"),
                    (SettingsTab::Telemetry, "Telemetry"),
                    (SettingsTab::Ai, "AI Engine"),
                ] {
                    let active = state.settings_tab == tab;
                    let resp = ui.add(
                        egui::Button::new(
                            egui::RichText::new(label)
                                .font(FontId::proportional(12.0))
                                .color(if active { TEXT_1 } else { TEXT_2 }),
                        )
                        .fill(if active {
                            Color32::from_rgba_unmultiplied(255, 255, 255, 22)
                        } else {
                            Color32::TRANSPARENT
                        })
                        .stroke(Stroke::new(
                            1.0,
                            if active { BORDER_STRONG } else { BORDER_SUBTLE },
                        ))
                        .rounding(Rounding::same(8.0))
                        .min_size(Vec2::new(90.0, 28.0)),
                    );
                    if resp.clicked() {
                        state.settings_tab = tab;
                    }
                }
            });

            ui.add_space(16.0);

            egui::ScrollArea::vertical()
                .max_height(280.0)
                .show(ui, |ui| match state.settings_tab {
                    SettingsTab::Mesh => {
                        ui.label(
                            egui::RichText::new("PRIMARY MESH HUB WEBSOCKET")
                                .font(FontId::monospace(10.0))
                                .color(TEXT_3),
                        );
                        ui.add_space(4.0);
                        ui.add(
                            egui::TextEdit::singleline(&mut state.hub_url_input)
                                .desired_width(ui.available_width())
                                .font(FontId::monospace(12.0)),
                        );
                        ui.add_space(12.0);
                        ui.label(
                            egui::RichText::new("CLUSTER ADMIN PIN (OPTIONAL)")
                                .font(FontId::monospace(10.0))
                                .color(TEXT_3),
                        );
                        ui.add_space(4.0);
                        ui.add(
                            egui::TextEdit::singleline(&mut state.admin_pin_input)
                                .password(true)
                                .hint_text("e.g. 2004 (or uses NEXUS_ADMIN_PIN env)")
                                .desired_width(ui.available_width())
                                .font(FontId::monospace(12.0)),
                        );
                        ui.add_space(14.0);
                        render_toggle_row(
                            ui,
                            "Auto-Failover Coordinator",
                            "Participate in Tri-Core quorum leader election",
                            &mut state.pref_coordinator,
                        );
                    }
                    SettingsTab::Telemetry => {
                        render_toggle_row(
                            ui,
                            "High-Cadence Telemetry (1s)",
                            "Sample hardware metrics every 1s instead of 5s",
                            &mut state.pref_high_cadence,
                        );
                        ui.add_space(10.0);
                        let mut docker_on = state.config.is_feature_enabled("docker");
                        render_toggle_row(
                            ui,
                            "Docker Container Supervisor",
                            "Include local Docker containers in telemetry",
                            &mut docker_on,
                        );
                        state.config.features.insert("docker".to_string(), docker_on);
                        ui.add_space(10.0);
                        let mut auto_upd = state.config.is_feature_enabled("auto_update");
                        render_toggle_row(
                            ui,
                            "Background Auto-Updater",
                            "Periodically check faizal.uk for new releases",
                            &mut auto_upd,
                        );
                        state
                            .config
                            .features
                            .insert("auto_update".to_string(), auto_upd);
                    }
                    SettingsTab::Ai => {
                        ui.label(
                            egui::RichText::new("LOCAL OLLAMA ENDPOINT")
                                .font(FontId::monospace(10.0))
                                .color(TEXT_3),
                        );
                        ui.add_space(4.0);
                        ui.add(
                            egui::TextEdit::singleline(&mut state.ai_endpoint_input)
                                .desired_width(ui.available_width())
                                .font(FontId::monospace(12.0)),
                        );
                        ui.add_space(12.0);
                        render_toggle_row(
                            ui,
                            "Distributed AI Relay",
                            "Expose local Ollama models to the Nexus Fleet mesh",
                            &mut state.pref_ai_relay,
                        );
                    }
                });

            ui.with_layout(egui::Layout::bottom_up(egui::Align::RIGHT), |ui| {
                ui.horizontal(|ui| {
                    if primary_button(ui, "Save & Apply").clicked() {
                        let hub = state.hub_url_input.trim().to_string();
                        if !hub.is_empty() {
                            if state.config.hubs.is_empty() {
                                state.config.hubs.push(hub);
                            } else {
                                state.config.hubs[0] = hub;
                            }
                        }
                        let ep = state.ai_endpoint_input.trim().to_string();
                        if !ep.is_empty() {
                            state.config.ai_endpoint = ep;
                        }
                        state.config.active_poll_interval_secs =
                            if state.pref_high_cadence { 1 } else { 5 };
                        state
                            .config
                            .features
                            .insert("auto_coordinator".to_string(), state.pref_coordinator);
                        state
                            .config
                            .features
                            .insert("ai_bridge".to_string(), state.pref_ai_relay);
                        state.config.save();

                        let _ = state.cmd_tx.send(GuiCommand::RefreshCluster {
                            hub_url: state.hub_url_input.clone(),
                            pin: state.effective_pin(),
                        });
                        let _ = state.cmd_tx.send(GuiCommand::RefreshOllama {
                            endpoint: state.ai_endpoint_input.clone(),
                        });
                        state.set_toast(
                            "Configuration saved to disk",
                            LogLevel::Success,
                            "Config",
                        );
                        close = true;
                    }
                    if action_button(ui, "Cancel").clicked() {
                        close = true;
                    }
                });
            });
        });

    if close {
        state.show_settings = false;
    }
}

