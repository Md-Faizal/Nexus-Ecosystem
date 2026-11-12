use crate::gui::components::icons::{paint_icon, IconKind};
use crate::gui::state::{DaemonAction, GuiCommand, GuiRoute, GuiState, LogLevel};
use crate::gui::theme::*;
use eframe::egui;
use egui::{Color32, FontId, Frame, Margin, Rounding, Sense, Stroke, Vec2};

#[derive(Clone)]
struct PaletteItem {
    id: &'static str,
    title: String,
    subtitle: String,
    badge: &'static str,
}

pub fn fuzzy_match(query: &str, target: &str) -> bool {
    let q = query.trim().to_lowercase();
    if q.is_empty() {
        return true;
    }
    let t = target.to_lowercase();
    if t.contains(&q) {
        return true;
    }
    let mut q_chars = q.chars().peekable();
    for c in t.chars() {
        if q_chars.peek() == Some(&c) {
            q_chars.next();
            if q_chars.peek().is_none() {
                return true;
            }
        }
    }
    false
}

pub fn render_command_palette(ctx: &egui::Context, state: &mut GuiState) {
    let items = vec![
        PaletteItem {
            id: "nav_overview",
            title: "Go to Overview (Mission Control)".into(),
            subtitle: "Fitness arc, live telemetry sparklines & quick actions".into(),
            badge: "Ctrl+1",
        },
        PaletteItem {
            id: "nav_telemetry",
            title: "Go to Hardware & OS Telemetry".into(),
            subtitle: "Per-core CPU heatmap, disks, network RX/TX & process inspector".into(),
            badge: "Ctrl+2",
        },
        PaletteItem {
            id: "nav_cluster",
            title: "Go to Cluster & Fleet Mesh".into(),
            subtitle: "Tri-node topology, remote services & local Docker containers".into(),
            badge: "Ctrl+3",
        },
        PaletteItem {
            id: "nav_ai",
            title: "Go to Distributed AI Studio".into(),
            subtitle: "Streaming Ollama chat, VRAM residency & saved sessions".into(),
            badge: "Ctrl+4",
        },
        PaletteItem {
            id: "nav_diag",
            title: "Go to Diagnostics & Updates".into(),
            subtitle: "Daemon lifecycle, crash vault, auto-updater & live event logs".into(),
            badge: "Ctrl+5",
        },
        PaletteItem {
            id: "act_refresh_cluster",
            title: "Refresh Fleet Cluster & Latency".into(),
            subtitle: "Poll remote nodes, services, and measure Hub RTT".into(),
            badge: "Action",
        },
        PaletteItem {
            id: "act_refresh_docker",
            title: "Refresh Local Docker Containers".into(),
            subtitle: "Scan Docker Engine for running & stopped containers".into(),
            badge: "Action",
        },
        PaletteItem {
            id: "act_new_chat",
            title: "Start New AI Chat Session".into(),
            subtitle: "Open AI Studio with a fresh conversation".into(),
            badge: "AI",
        },
        PaletteItem {
            id: "act_restart_daemon",
            title: "Restart Nexus Background Daemon".into(),
            subtitle: "Cycle the local telemetry & mesh tunnel service".into(),
            badge: "Daemon",
        },
        PaletteItem {
            id: "act_check_updates",
            title: "Check for Agent Updates".into(),
            subtitle: "Query release manifest for newer Nexus Agent builds".into(),
            badge: "Update",
        },
        PaletteItem {
            id: "act_toggle_cadence",
            title: if state.pref_high_cadence {
                "Switch to Eco Polling (5s)".into()
            } else {
                "Switch to High-Cadence Polling (1s)".into()
            },
            subtitle: "Adjust telemetry sampling interval".into(),
            badge: "Config",
        },
        PaletteItem {
            id: "act_settings",
            title: "Open Preferences & Hub Configuration".into(),
            subtitle: "Configure Hub URL, Admin PIN, Ollama endpoint & feature flags".into(),
            badge: "Ctrl+,",
        },
        PaletteItem {
            id: "act_cloud",
            title: "Open Cloud Admin Console in Browser".into(),
            subtitle: "Launch https://faizal.uk/admin.html".into(),
            badge: "External",
        },
    ];

    let filtered: Vec<PaletteItem> = items
        .into_iter()
        .filter(|it| {
            fuzzy_match(&state.palette_query, &it.title)
                || fuzzy_match(&state.palette_query, &it.subtitle)
        })
        .collect();

    if state.palette_selected >= filtered.len() && !filtered.is_empty() {
        state.palette_selected = filtered.len() - 1;
    }

    let mut execute_id: Option<&'static str> = None;
    let mut close_palette = false;

    ctx.input(|i| {
        if i.key_pressed(egui::Key::Escape) {
            close_palette = true;
        }
        if i.key_pressed(egui::Key::ArrowDown) && !filtered.is_empty() {
            state.palette_selected = (state.palette_selected + 1) % filtered.len();
        }
        if i.key_pressed(egui::Key::ArrowUp) && !filtered.is_empty() {
            state.palette_selected = if state.palette_selected == 0 {
                filtered.len() - 1
            } else {
                state.palette_selected - 1
            };
        }
        if i.key_pressed(egui::Key::Enter) {
            if let Some(item) = filtered.get(state.palette_selected) {
                execute_id = Some(item.id);
            }
        }
    });

    let screen = ctx.screen_rect();
    let fade_t = ctx.animate_bool_with_time(egui::Id::new("cmd_palette_fade"), true, 0.16);
    let scrim_alpha = (185.0 * fade_t).round() as u8;

    egui::Area::new(egui::Id::new("cmd_palette_scrim"))
        .fixed_pos(screen.min)
        .order(egui::Order::Middle)
        .show(ctx, |ui| {
            let resp = ui.allocate_rect(screen, Sense::click());
            ui.painter().rect_filled(
                screen,
                Rounding::ZERO,
                Color32::from_rgba_unmultiplied(4, 4, 8, scrim_alpha),
            );
            if resp.clicked() {
                close_palette = true;
            }
        });

    let modal_w = (screen.width() - 32.0).clamp(320.0, 580.0);
    let y_offset = 64.0 + 14.0 * fade_t;
    egui::Window::new("command_palette_window")
        .order(egui::Order::Foreground)
        .title_bar(false)
        .collapsible(false)
        .resizable(false)
        .anchor(egui::Align2::CENTER_TOP, Vec2::new(0.0, y_offset))
        .fixed_size(Vec2::new(modal_w, 380.0))
        .frame(
            Frame::none()
                .fill(BG_SHEET)
                .stroke(Stroke::new(1.0, BORDER_STRONG))
                .rounding(Rounding::same(14.0))
                .inner_margin(Margin::same(14.0)),
        )
        .show(ctx, |ui| {
            ui.horizontal(|ui| {
                let (icon_r, _) = ui.allocate_exact_size(Vec2::splat(18.0), Sense::hover());
                paint_icon(ui.painter(), icon_r, IconKind::Command, ACCENT_CYAN);
                let resp = ui.add(
                    egui::TextEdit::singleline(&mut state.palette_query)
                        .hint_text("Type a command or jump to a view…")
                        .frame(false)
                        .desired_width(ui.available_width() - 62.0)
                        .font(FontId::proportional(14.0)),
                );
                resp.request_focus();
                kbd_badge(ui, "ESC");
            });

            ui.add_space(8.0);
            let (line_rect, _) =
                ui.allocate_exact_size(Vec2::new(ui.available_width(), 1.0), Sense::hover());
            ui.painter().line_segment(
                [line_rect.left_center(), line_rect.right_center()],
                Stroke::new(1.0, BORDER_SUBTLE),
            );
            ui.add_space(8.0);

            egui::ScrollArea::vertical()
                .max_height(310.0)
                .show(ui, |ui| {
                    if filtered.is_empty() {
                        ui.add_space(24.0);
                        ui.centered_and_justified(|ui| {
                            ui.label(
                                egui::RichText::new("No matching commands")
                                    .font(FontId::proportional(13.0))
                                    .color(TEXT_3),
                            );
                        });
                    } else {
                        for (idx, item) in filtered.iter().enumerate() {
                            let selected = idx == state.palette_selected;
                            let row_w = ui.available_width();
                            let (rect, resp) =
                                ui.allocate_exact_size(Vec2::new(row_w, 46.0), Sense::click());
                            let fill = if selected {
                                Color32::from_rgba_unmultiplied(56, 189, 248, 22)
                            } else if resp.hovered() {
                                Color32::from_rgba_unmultiplied(255, 255, 255, 10)
                            } else {
                                Color32::TRANSPARENT
                            };
                            let stroke = if selected {
                                Stroke::new(1.0, Color32::from_rgba_unmultiplied(56, 189, 248, 80))
                            } else {
                                Stroke::NONE
                            };
                            ui.painter().rect(rect, Rounding::same(8.0), fill, stroke);

                            ui.painter().text(
                                egui::Pos2::new(rect.left() + 12.0, rect.top() + 14.0),
                                egui::Align2::LEFT_CENTER,
                                &item.title,
                                FontId::proportional(12.5),
                                TEXT_1,
                            );
                            ui.painter().text(
                                egui::Pos2::new(rect.left() + 12.0, rect.top() + 32.0),
                                egui::Align2::LEFT_CENTER,
                                &item.subtitle,
                                FontId::proportional(10.5),
                                TEXT_3,
                            );
                            ui.painter().text(
                                egui::Pos2::new(rect.right() - 12.0, rect.center().y),
                                egui::Align2::RIGHT_CENTER,
                                item.badge,
                                FontId::monospace(9.5),
                                if selected { ACCENT_CYAN } else { TEXT_3 },
                            );

                            if resp.clicked() {
                                execute_id = Some(item.id);
                            }
                        }
                    }
                });
        });

    if let Some(id) = execute_id {
        state.show_command_palette = false;
        state.palette_query.clear();
        match id {
            "nav_overview" => state.route = GuiRoute::Overview,
            "nav_telemetry" => state.route = GuiRoute::Telemetry,
            "nav_cluster" => state.route = GuiRoute::Cluster,
            "nav_ai" => state.route = GuiRoute::AiStudio,
            "nav_diag" => state.route = GuiRoute::Diagnostics,
            "act_refresh_cluster" => {
                state.cluster_loading = true;
                let _ = state.cmd_tx.send(GuiCommand::RefreshCluster {
                    hub_url: state.hub_url_input.clone(),
                    pin: state.effective_pin(),
                });
            }
            "act_refresh_docker" => {
                let _ = state.cmd_tx.send(GuiCommand::RefreshDocker);
            }
            "act_new_chat" => {
                state.start_new_chat();
                state.route = GuiRoute::AiStudio;
            }
            "act_restart_daemon" => {
                let _ = state
                    .cmd_tx
                    .send(GuiCommand::RunDaemonAction(DaemonAction::Restart));
                state.set_toast("Restarting Nexus daemon...", LogLevel::Info, "Daemon");
            }
            "act_check_updates" => {
                state.update_checking = true;
                state.route = GuiRoute::Diagnostics;
                let _ = state.cmd_tx.send(GuiCommand::CheckUpdates {
                    manifest_url: "https://faizal.uk/releases/latest.json".to_string(),
                });
            }
            "act_toggle_cadence" => {
                state.pref_high_cadence = !state.pref_high_cadence;
                state.config.active_poll_interval_secs = if state.pref_high_cadence { 1 } else { 5 };
                let _ = state.config.save();
                state.set_toast(
                    if state.pref_high_cadence {
                        "High-cadence 1s polling enabled"
                    } else {
                        "Eco 5s polling enabled"
                    },
                    LogLevel::Success,
                    "Config",
                );
            }
            "act_settings" => state.show_settings = true,
            "act_cloud" => {
                open_url_browser("https://faizal.uk/admin.html");
                state.set_toast("Opening Cloud Admin Console...", LogLevel::Info, "Browser");
            }
            _ => {}
        }
    } else if close_palette {
        state.show_command_palette = false;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fuzzy_match_handles_subsequence_and_substring() {
        assert!(fuzzy_match("", "Anything"));
        assert!(fuzzy_match("ai", "Go to Distributed AI Studio"));
        assert!(fuzzy_match("rst", "Restart Nexus Background Daemon"));
        assert!(!fuzzy_match("xyz", "Overview"));
    }
}

