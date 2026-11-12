use crate::gui::components::icons::{icon_button, paint_icon, IconKind};
use crate::gui::state::{DaemonAction, GuiCommand, GuiRoute, GuiState, LogLevel};
use crate::gui::theme::*;
use eframe::egui;
use egui::{Color32, FontId, Frame, Margin, Rect, Rounding, Sense, Stroke, Vec2};

fn route_icon(route: GuiRoute) -> IconKind {
    match route {
        GuiRoute::Overview => IconKind::Overview,
        GuiRoute::Telemetry => IconKind::Telemetry,
        GuiRoute::Cluster => IconKind::Cluster,
        GuiRoute::AiStudio => IconKind::AiStudio,
        GuiRoute::Diagnostics => IconKind::Diagnostics,
    }
}

pub fn render_sidebar(ctx: &egui::Context, state: &mut GuiState, is_compact_viewport: bool) {
    if is_compact_viewport {
        // Ultra-responsive bottom navigation bar on narrow viewports (< 840px)
        egui::TopBottomPanel::bottom("compact_nav_bar")
            .frame(
                Frame::none()
                    .fill(BG_RAIL)
                    .stroke(Stroke::new(1.0, BORDER_SUBTLE))
                    .inner_margin(Margin::symmetric(10.0, 8.0)),
            )
            .show(ctx, |ui| {
                ui.horizontal(|ui| {
                    let count = GuiRoute::ALL.len() as f32;
                    let item_w = ((ui.available_width() - 8.0 * (count - 1.0)) / count).max(52.0);
                    ui.spacing_mut().item_spacing.x = 8.0;
                    for route in GuiRoute::ALL {
                        let active = state.route == route;
                        let fill = if active {
                            Color32::from_rgba_unmultiplied(255, 255, 255, 18)
                        } else {
                            Color32::TRANSPARENT
                        };
                        let stroke = if active { BORDER_STRONG } else { Color32::TRANSPARENT };
                        let (btn_rect, resp) =
                            ui.allocate_exact_size(Vec2::new(item_w, 38.0), Sense::click());
                        ui.painter()
                            .rect(btn_rect, Rounding::same(8.0), fill, Stroke::new(1.0_f32, stroke));
                        let icon_r = Rect::from_center_size(
                            egui::Pos2::new(btn_rect.center().x, btn_rect.top() + 13.0),
                            Vec2::splat(14.0),
                        );
                        paint_icon(
                            ui.painter(),
                            icon_r,
                            route_icon(route),
                            if active { ACCENT_CYAN } else { TEXT_2 },
                        );
                        ui.painter().text(
                            egui::Pos2::new(btn_rect.center().x, btn_rect.bottom() - 9.0),
                            egui::Align2::CENTER_CENTER,
                            route.short_title(),
                            FontId::proportional(10.0),
                            if active { TEXT_1 } else { TEXT_2 },
                        );
                        if resp.clicked() {
                            state.route = route;
                        }
                    }
                });
            });
        return;
    }

    let rail_w = if state.sidebar_collapsed { 66.0 } else { 216.0 };
    egui::SidePanel::left("flight_deck_sidebar")
        .resizable(false)
        .exact_width(rail_w)
        .frame(
            Frame::none()
                .fill(BG_RAIL)
                .stroke(Stroke::new(1.0, BORDER_SUBTLE))
                .inner_margin(Margin::symmetric(12.0, 16.0)),
        )
        .show(ctx, |ui| {
            // Collapse toggle row
            ui.horizontal(|ui| {
                if !state.sidebar_collapsed {
                    ui.label(
                        egui::RichText::new("NAVIGATION")
                            .font(FontId::monospace(9.5))
                            .color(TEXT_3),
                    );
                }
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    let toggle_icon = if state.sidebar_collapsed {
                        IconKind::ChevronRight
                    } else {
                        IconKind::ChevronLeft
                    };
                    if icon_button(ui, toggle_icon, 24.0, TEXT_2, "Toggle sidebar").clicked() {
                        state.sidebar_collapsed = !state.sidebar_collapsed;
                    }
                });
            });

            ui.add_space(12.0);

            for route in GuiRoute::ALL {
                let active = state.route == route;
                let btn_w = ui.available_width();
                let (rect, resp) = ui.allocate_exact_size(Vec2::new(btn_w, 36.0), Sense::click());
                let hover_t = ui.ctx().animate_bool_with_time(resp.id, resp.hovered() && !active, 0.14);
                let active_t = ui.ctx().animate_bool_with_time(resp.id.with("active"), active, 0.18);
                let bg_alpha = (active_t * 18.0 + hover_t * 10.0).round() as u8;
                let bg = Color32::from_rgba_unmultiplied(255, 255, 255, bg_alpha);
                let stroke_col = if active {
                    BORDER_STRONG
                } else if resp.hovered() {
                    BORDER_SUBTLE
                } else {
                    Color32::TRANSPARENT
                };

                ui.painter()
                    .rect(rect, Rounding::same(9.0), bg, Stroke::new(1.0_f32, stroke_col));

                if active {
                    let bar = egui::Rect::from_min_size(
                        egui::Pos2::new(rect.left() + 2.0, rect.top() + 8.0),
                        Vec2::new(2.5, rect.height() - 16.0),
                    );
                    ui.painter()
                        .rect_filled(bar, Rounding::same(2.0), ACCENT_CYAN);
                }

                if state.sidebar_collapsed {
                    let icon_r = Rect::from_center_size(rect.center(), Vec2::splat(16.0));
                    paint_icon(
                        ui.painter(),
                        icon_r,
                        route_icon(route),
                        if active { ACCENT_CYAN } else { TEXT_2 },
                    );
                    resp.clone().on_hover_text(format!("{} (Ctrl+{})", route.title(), route.shortcut_num()));
                } else {
                    let icon_r = Rect::from_center_size(
                        egui::Pos2::new(rect.left() + 20.0, rect.center().y),
                        Vec2::splat(15.0),
                    );
                    paint_icon(
                        ui.painter(),
                        icon_r,
                        route_icon(route),
                        if active { ACCENT_CYAN } else { TEXT_2 },
                    );
                    ui.painter().text(
                        egui::Pos2::new(rect.left() + 36.0, rect.center().y),
                        egui::Align2::LEFT_CENTER,
                        route.title(),
                        FontId::proportional(12.5),
                        if active { TEXT_1 } else { TEXT_2 },
                    );
                    ui.painter().text(
                        egui::Pos2::new(rect.right() - 10.0, rect.center().y),
                        egui::Align2::RIGHT_CENTER,
                        format!("^{}", route.shortcut_num()),
                        FontId::monospace(9.5),
                        TEXT_3,
                    );
                }

                if resp.clicked() {
                    state.route = route;
                }
                ui.add_space(4.0);
            }

            // Bottom Daemon Lifecycle Card
            ui.with_layout(egui::Layout::bottom_up(egui::Align::LEFT), |ui| {
                if state.sidebar_collapsed {
                    let color = if state.daemon_active {
                        ACCENT_EMERALD
                    } else {
                        ACCENT_AMBER
                    };
                    let (r, resp) = ui.allocate_exact_size(Vec2::new(40.0, 32.0), Sense::click());
                    ui.painter().circle_filled(r.center(), 5.0, color);
                    if resp.clicked() {
                        state.route = GuiRoute::Diagnostics;
                    }
                    resp.on_hover_text(if state.daemon_active {
                        "Daemon Online"
                    } else {
                        "Daemon Standby"
                    });
                } else {
                    Frame::none()
                        .fill(BG_TILE)
                        .stroke(Stroke::new(1.0_f32, BORDER_SUBTLE))
                        .rounding(Rounding::same(10.0))
                        .inner_margin(Margin::same(12.0))
                        .show(ui, |ui| {
                            ui.with_layout(egui::Layout::top_down(egui::Align::LEFT), |ui| {
                                ui.set_width(ui.available_width());
                                ui.horizontal(|ui| {
                                    let dot_col = if state.daemon_active {
                                        ACCENT_EMERALD
                                    } else {
                                        ACCENT_AMBER
                                    };
                                    let (r, _) =
                                        ui.allocate_exact_size(Vec2::new(8.0, 8.0), Sense::hover());
                                    ui.painter().circle_filled(r.center(), 3.2, dot_col);
                                    ui.label(
                                        egui::RichText::new(if state.daemon_active {
                                            "DAEMON ONLINE"
                                        } else {
                                            "DAEMON STANDBY"
                                        })
                                        .font(FontId::monospace(9.5))
                                        .color(dot_col),
                                    );
                                });
                                if let Some(pid) = state.daemon_pid {
                                    ui.add_space(4.0);
                                    ui.label(
                                        egui::RichText::new(format!("PID {}", pid))
                                            .font(FontId::monospace(9.5))
                                            .color(TEXT_3),
                                    );
                                }
                                ui.add_space(8.0);
                                ui.horizontal(|ui| {
                                    let act_label =
                                        if state.daemon_active { "Restart" } else { "Start" };
                                    if action_button(ui, act_label).clicked() {
                                        let action = if state.daemon_active {
                                            DaemonAction::Restart
                                        } else {
                                            DaemonAction::Start
                                        };
                                        let _ = state.cmd_tx.send(GuiCommand::RunDaemonAction(action));
                                        state.set_toast(
                                            format!(
                                                "Dispatching daemon {}...",
                                                act_label.to_lowercase()
                                            ),
                                            LogLevel::Info,
                                            "Daemon",
                                        );
                                    }
                                    if state.daemon_active && action_button(ui, "Stop").clicked() {
                                        let _ = state
                                            .cmd_tx
                                            .send(GuiCommand::RunDaemonAction(DaemonAction::Stop));
                                        state.set_toast(
                                            "Stopping daemon...",
                                            LogLevel::Warn,
                                            "Daemon",
                                        );
                                    }
                                });
                            });
                        });
                }
            });
        });
}

