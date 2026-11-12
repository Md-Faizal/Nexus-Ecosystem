use crate::gui::state::{DaemonAction, GuiCommand, GuiState, LogLevel};
use crate::gui::theme::*;
use eframe::egui;
use egui::{FontId, Vec2};

pub fn render(ui: &mut egui::Ui, state: &mut GuiState, tier: LayoutTier) {
    let gap = 16.0;

    if tier.is_compact() {
        render_daemon_updater_card(ui, state);
        ui.add_space(gap);
        render_crash_vault_card(ui, state);
    } else {
        ui.columns(2, |cols| {
            render_daemon_updater_card(&mut cols[0], state);
            render_crash_vault_card(&mut cols[1], state);
        });
    }

    ui.add_space(gap);
    render_event_log_card(ui, state);
}

fn render_daemon_updater_card(ui: &mut egui::Ui, state: &mut GuiState) {
    card_frame().show(ui, |ui| {
        ui.set_width(ui.available_width());
        render_section_header(
            ui,
            "01 // DAEMON & AUTO-UPDATER",
            &format!("v{}", crate::AGENT_VERSION),
            ACCENT_CYAN,
        );

        row_frame().show(ui, |ui| {
            ui.set_width(ui.available_width());
            ui.horizontal(|ui| {
                ui.vertical(|ui| {
                    ui.label(
                        egui::RichText::new("Background Telemetry Daemon")
                            .font(FontId::proportional(13.0))
                            .strong()
                            .color(TEXT_1),
                    );
                    ui.label(
                        egui::RichText::new(if let Some(pid) = state.daemon_pid {
                            format!("Active • PID {}", pid)
                        } else {
                            "Inactive • Standby mode".to_string()
                        })
                        .font(FontId::monospace(10.0))
                        .color(TEXT_3),
                    );
                });
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    pill_badge(
                        ui,
                        if state.daemon_active { "RUNNING" } else { "STOPPED" },
                        if state.daemon_active {
                            ACCENT_EMERALD
                        } else {
                            ACCENT_AMBER
                        },
                    );
                });
            });
        });

        ui.add_space(10.0);
        ui.horizontal_wrapped(|ui| {
            ui.spacing_mut().item_spacing = Vec2::new(8.0, 8.0);
            if action_button(ui, "▶ Start").clicked() {
                let _ = state
                    .cmd_tx
                    .send(GuiCommand::RunDaemonAction(DaemonAction::Start));
            }
            if action_button(ui, "↻ Restart").clicked() {
                let _ = state
                    .cmd_tx
                    .send(GuiCommand::RunDaemonAction(DaemonAction::Restart));
            }
            if action_button(ui, "■ Stop").clicked() {
                let _ = state
                    .cmd_tx
                    .send(GuiCommand::RunDaemonAction(DaemonAction::Stop));
            }
            if action_button(ui, "⚙ Install OS Service").clicked() {
                let _ = state
                    .cmd_tx
                    .send(GuiCommand::RunDaemonAction(DaemonAction::InstallService));
            }
            if action_button(ui, "✕ Uninstall Service").clicked() {
                let _ = state
                    .cmd_tx
                    .send(GuiCommand::RunDaemonAction(DaemonAction::UninstallService));
            }
        });

        ui.add_space(16.0);
        render_section_header(
            ui,
            "RELEASE CHANNEL",
            if state.update_checking { "CHECKING…" } else { "PRODUCTION" },
            TEXT_2,
        );

        let manifest_url = "https://faizal.uk/releases/latest.json".to_string();
        ui.horizontal_wrapped(|ui| {
            if action_button(ui, "↻ Check for Updates").clicked() && !state.update_checking {
                state.update_checking = true;
                let _ = state.cmd_tx.send(GuiCommand::CheckUpdates {
                    manifest_url: manifest_url.clone(),
                });
            }

            if let Some(ref info) = state.update_info {
                if info.update_available {
                    pill_badge(
                        ui,
                        &format!("v{} AVAILABLE", info.latest_version),
                        ACCENT_EMERALD,
                    );
                    if primary_button(ui, "⬇ Install Update").clicked() {
                        let _ = state.cmd_tx.send(GuiCommand::InstallUpdate { manifest_url });
                        state.set_toast("Downloading and applying update…", LogLevel::Info, "Updater");
                    }
                } else {
                    pill_badge(ui, "UP TO DATE", ACCENT_EMERALD);
                }
            }
        });
    });
}

fn render_crash_vault_card(ui: &mut egui::Ui, state: &mut GuiState) {
    card_frame().show(ui, |ui| {
        ui.set_width(ui.available_width());
        ui.horizontal(|ui| {
            ui.label(
                egui::RichText::new("02 // CRASH VAULT")
                    .font(FontId::monospace(10.5))
                    .color(TEXT_2),
            );
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                if !state.crashes.is_empty() && accent_button(ui, "Clear All", ACCENT_CRIMSON).clicked() {
                    match crate::modules::crash::clear_local_crashes() {
                        Ok(n) => {
                            state.refresh_crashes();
                            state.set_toast(
                                format!("Cleared {} crash records", n),
                                LogLevel::Success,
                                "CrashVault",
                            );
                        }
                        Err(e) => state.set_toast(e.to_string(), LogLevel::Error, "CrashVault"),
                    }
                }
                if action_button(ui, "↻").clicked() {
                    state.refresh_crashes();
                }
            });
        });

        ui.add_space(10.0);
        if state.crashes.is_empty() {
            row_frame().show(ui, |ui| {
                ui.set_width(ui.available_width());
                ui.horizontal(|ui| {
                    pill_badge(ui, "CLEAN", ACCENT_EMERALD);
                    ui.label(
                        egui::RichText::new("Zero panic or crash records in local vault.")
                            .font(FontId::proportional(12.0))
                            .color(TEXT_2),
                    );
                });
            });
        } else {
            egui::ScrollArea::vertical()
                .id_salt("crash_vault_scroll")
                .max_height(210.0)
                .show(ui, |ui| {
                    for (uploaded, rec) in state.crashes.iter().take(12) {
                        row_frame().show(ui, |ui| {
                            ui.set_width(ui.available_width());
                            ui.horizontal(|ui| {
                                pill_badge(
                                    ui,
                                    &rec.component.to_uppercase(),
                                    ACCENT_CRIMSON,
                                );
                                ui.label(
                                    egui::RichText::new(&rec.occurred_at)
                                        .font(FontId::monospace(9.5))
                                        .color(TEXT_3),
                                );
                                ui.with_layout(
                                    egui::Layout::right_to_left(egui::Align::Center),
                                    |ui| {
                                        pill_badge(
                                            ui,
                                            if *uploaded { "UPLOADED" } else { "PENDING" },
                                            if *uploaded { ACCENT_EMERALD } else { ACCENT_AMBER },
                                        );
                                    },
                                );
                            });
                            ui.add_space(4.0);
                            ui.label(
                                egui::RichText::new(truncate_label(&rec.message, 120))
                                    .font(FontId::monospace(10.0))
                                    .color(TEXT_2),
                            );
                        });
                        ui.add_space(6.0);
                    }
                });
        }
    });
}

fn render_event_log_card(ui: &mut egui::Ui, state: &mut GuiState) {
    card_frame().show(ui, |ui| {
        ui.set_width(ui.available_width());
        ui.horizontal(|ui| {
            ui.label(
                egui::RichText::new("03 // FLIGHT RECORDER EVENT LOG")
                    .font(FontId::monospace(10.5))
                    .color(TEXT_2),
            );
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                if action_button(ui, "Clear Log").clicked() {
                    state.logs.clear();
                }
            });
        });
        ui.add_space(10.0);

        egui::ScrollArea::vertical()
            .id_salt("event_log_scroll")
            .max_height(220.0)
            .stick_to_bottom(true)
            .show(ui, |ui| {
                if state.logs.is_empty() {
                    ui.label(egui::RichText::new("No events recorded yet.").color(TEXT_3));
                } else {
                    for entry in &state.logs {
                        let col = match entry.level {
                            LogLevel::Info => ACCENT_CYAN,
                            LogLevel::Success => ACCENT_EMERALD,
                            LogLevel::Warn => ACCENT_AMBER,
                            LogLevel::Error => ACCENT_CRIMSON,
                        };
                        ui.horizontal(|ui| {
                            ui.label(
                                egui::RichText::new(&entry.timestamp)
                                    .font(FontId::monospace(10.0))
                                    .color(TEXT_3),
                            );
                            ui.label(
                                egui::RichText::new(format!("[{}]", entry.source))
                                    .font(FontId::monospace(10.0))
                                    .color(col),
                            );
                            ui.label(
                                egui::RichText::new(&entry.message)
                                    .font(FontId::monospace(10.5))
                                    .color(TEXT_1),
                            );
                        });
                    }
                }
            });
    });
}
