use eframe::egui;
use egui::{
    Align, Color32, FontId, Frame, Layout, Margin, Pos2, Rect, RichText, Rounding, Sense, Stroke,
    Vec2,
};
use std::f32::consts::{PI, TAU};
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant};
use sysinfo::System;

use crate::config::AgentConfig;
use crate::mesh::protocol::GpuTelemetry;
use crate::modules::fitness::FitnessScore;
use crate::modules::gpu::GpuCollector;

static GUI_RUNNING: AtomicBool = AtomicBool::new(false);

const BG_BLACK: Color32 = Color32::BLACK;
const CARD_BG: Color32 = Color32::from_rgb(10, 10, 13);
const TILE_BG: Color32 = Color32::from_rgb(15, 15, 19);
const ROW_BG: Color32 = Color32::from_rgb(14, 14, 17);
const SHEET_BG: Color32 = Color32::from_rgb(9, 9, 11);
const BORDER_SUBTLE: Color32 = Color32::from_rgb(24, 24, 28);
const BORDER_STRONG: Color32 = Color32::from_rgb(46, 46, 54);
const TEXT_1: Color32 = Color32::WHITE;
const TEXT_2: Color32 = Color32::from_rgb(161, 161, 170);
const TEXT_3: Color32 = Color32::from_rgb(82, 82, 91);

/// Launch the native Flight Deck GUI window (pixel-exact pure-Rust port of dashboard.html)
pub fn launch_native_gui() {
    if GUI_RUNNING.swap(true, Ordering::SeqCst) {
        tracing::warn!("Native GUI window is already open.");
        return;
    }

    let native_options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_inner_size([1200.0, 760.0])
            .with_min_inner_size([900.0, 640.0])
            .with_title("NEXUS // FLIGHT DECK")
            .with_active(true),
        ..Default::default()
    };

    let run_result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        eframe::run_native(
            "NEXUS // FLIGHT DECK",
            native_options,
            Box::new(|cc| {
                configure_obsidian_theme(&cc.egui_ctx);
                Ok(Box::new(FlightDeckApp::new()))
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

fn configure_obsidian_theme(ctx: &egui::Context) {
    let mut visuals = egui::Visuals::dark();
    visuals.dark_mode = true;
    visuals.override_text_color = Some(TEXT_1);
    visuals.panel_fill = BG_BLACK;
    visuals.window_fill = SHEET_BG;
    visuals.window_stroke = Stroke::new(1.0_f32, BORDER_STRONG);
    visuals.window_rounding = Rounding::same(16.0);

    visuals.widgets.noninteractive.bg_fill = CARD_BG;
    visuals.widgets.noninteractive.bg_stroke = Stroke::new(1.0_f32, BORDER_SUBTLE);
    visuals.widgets.noninteractive.rounding = Rounding::same(12.0);

    visuals.widgets.inactive.bg_fill = Color32::from_rgba_unmultiplied(255, 255, 255, 10);
    visuals.widgets.inactive.bg_stroke = Stroke::new(1.0_f32, BORDER_SUBTLE);
    visuals.widgets.inactive.rounding = Rounding::same(8.0);

    visuals.widgets.hovered.bg_fill = Color32::from_rgba_unmultiplied(255, 255, 255, 24);
    visuals.widgets.hovered.bg_stroke = Stroke::new(1.0_f32, BORDER_STRONG);
    visuals.widgets.hovered.rounding = Rounding::same(8.0);

    visuals.widgets.active.bg_fill = Color32::from_rgba_unmultiplied(255, 255, 255, 38);
    visuals.widgets.active.bg_stroke = Stroke::new(1.0_f32, TEXT_1);
    visuals.widgets.active.rounding = Rounding::same(8.0);

    ctx.set_visuals(visuals);
}

pub struct FlightDeckApp {
    sys: System,
    config: AgentConfig,
    last_refresh: Instant,
    cached_fitness: FitnessScore,
    cached_gpu: Option<GpuTelemetry>,
    daemon_active: bool,
    show_settings: bool,
    pref_coordinator: bool,
    pref_high_cadence: bool,
    pref_ai_relay: bool,
    hub_url_input: String,
    toast_msg: Option<(String, Instant)>,
}

impl FlightDeckApp {
    pub fn new() -> Self {
        let mut sys = System::new_all();
        sys.refresh_all();
        let config = AgentConfig::load_or_init();
        let gpu = GpuCollector::collect();

        let total_ram_mb = sys.total_memory() / (1024 * 1024);
        let used_ram_mb = sys.used_memory() / (1024 * 1024);
        let cpu_usage = sys.global_cpu_usage();
        let is_cloud = FitnessScore::detect_is_cloud();
        let fitness = FitnessScore::calculate(
            total_ram_mb,
            used_ram_mb,
            cpu_usage,
            is_cloud,
            false,
            true,
        );

        let hub_url_input = config
            .hubs
            .first()
            .cloned()
            .unwrap_or_else(|| "wss://faizal.uk/api/mesh/ws".to_string());

        Self {
            sys,
            pref_coordinator: config.is_feature_enabled("auto_coordinator"),
            pref_high_cadence: config.active_poll_interval_secs <= 2,
            pref_ai_relay: config.is_feature_enabled("ai_bridge"),
            hub_url_input,
            config,
            last_refresh: Instant::now(),
            cached_fitness: fitness,
            cached_gpu: gpu,
            daemon_active: crate::modules::daemon_lock::is_daemon_running(),
            show_settings: false,
            toast_msg: None,
        }
    }

    fn refresh_metrics(&mut self) {
        if self.last_refresh.elapsed() >= Duration::from_secs(2) {
            self.sys.refresh_cpu_all();
            self.sys.refresh_memory();
            self.cached_gpu = GpuCollector::collect();
            self.daemon_active = crate::modules::daemon_lock::is_daemon_running();

            let total_ram_mb = self.sys.total_memory() / (1024 * 1024);
            let used_ram_mb = self.sys.used_memory() / (1024 * 1024);
            let cpu_usage = self.sys.global_cpu_usage();
            let is_cloud = FitnessScore::detect_is_cloud();

            self.cached_fitness = FitnessScore::calculate(
                total_ram_mb,
                used_ram_mb,
                cpu_usage,
                is_cloud,
                false,
                true,
            );
            self.last_refresh = Instant::now();
        }
    }

    fn set_toast(&mut self, msg: &str) {
        self.toast_msg = Some((msg.to_string(), Instant::now()));
    }

    fn save_preferences(&mut self) {
        self.config
            .features
            .insert("auto_coordinator".to_string(), self.pref_coordinator);
        self.config
            .features
            .insert("ai_bridge".to_string(), self.pref_ai_relay);
        self.config.active_poll_interval_secs = if self.pref_high_cadence { 2 } else { 10 };

        let trimmed = self.hub_url_input.trim().to_string();
        if !trimmed.is_empty() {
            if self.config.hubs.is_empty() {
                self.config.hubs.push(trimmed);
            } else {
                self.config.hubs[0] = trimmed;
            }
        }
        let _ = self.config.save();
        self.show_settings = false;
        self.set_toast("Preferences saved");
    }
}

impl eframe::App for FlightDeckApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        self.refresh_metrics();
        ctx.request_repaint_after(Duration::from_secs(1));

        // ─── Top Architectural Header ───
        egui::TopBottomPanel::top("flight_deck_header")
            .frame(
                Frame::none()
                    .fill(BG_BLACK)
                    .inner_margin(Margin::symmetric(32.0, 18.0))
                    .stroke(Stroke::new(1.0_f32, BORDER_SUBTLE)),
            )
            .show(ctx, |ui| {
                self.render_header(ui);
            });

        // ─── Main Canvas ───
        egui::CentralPanel::default()
            .frame(Frame::none().fill(BG_BLACK).inner_margin(Margin::same(0.0)))
            .show(ctx, |ui| {
                // Subtle top-center ambient satin glow (unmultiplied alpha!)
                let rect = ui.max_rect();
                ui.painter().circle_filled(
                    Pos2::new(rect.center().x, rect.top() - 180.0),
                    420.0,
                    Color32::from_rgba_unmultiplied(255, 255, 255, 7),
                );

                egui::ScrollArea::vertical().show(ui, |ui| {
                    ui.add_space(28.0);
                    let avail = ui.available_width();
                    let content_w = (avail - 64.0).clamp(760.0, 1136.0);
                    let side_pad = ((avail - content_w) * 0.5).max(32.0);

                    ui.horizontal(|ui| {
                        ui.add_space(side_pad);
                        ui.allocate_ui_with_layout(
                            Vec2::new(content_w, 0.0),
                            Layout::top_down(Align::LEFT),
                            |ui| {
                                self.render_main_grid(ui, content_w);
                            },
                        );
                    });
                    ui.add_space(28.0);
                });
            });

        if self.show_settings {
            self.render_preferences_modal(ctx);
        }

        if let Some((msg, since)) = &self.toast_msg {
            if since.elapsed() < Duration::from_secs(3) {
                egui::Area::new(egui::Id::new("toast_area"))
                    .anchor(egui::Align2::CENTER_BOTTOM, Vec2::new(0.0, -24.0))
                    .show(ctx, |ui| {
                        Frame::none()
                            .fill(Color32::from_rgb(24, 24, 27))
                            .stroke(Stroke::new(1.0_f32, BORDER_STRONG))
                            .rounding(Rounding::same(20.0))
                            .inner_margin(Margin::symmetric(16.0, 8.0))
                            .show(ui, |ui| {
                                ui.label(
                                    RichText::new(msg)
                                        .font(FontId::monospace(11.0))
                                        .color(TEXT_1),
                                );
                            });
                    });
            } else {
                self.toast_msg = None;
            }
        }
    }
}

impl FlightDeckApp {
    fn render_header(&mut self, ui: &mut egui::Ui) {
        let hostname = System::host_name()
            .unwrap_or_else(|| self.config.hostname.clone())
            .to_uppercase();
        let tier = self.cached_fitness.tier.to_uppercase();

        ui.horizontal(|ui| {
            // F• Monogram Mark (32x32)
            let (mono_rect, _) = ui.allocate_exact_size(Vec2::new(32.0, 32.0), Sense::hover());
            ui.painter().rect(
                mono_rect,
                Rounding::same(8.0),
                Color32::from_rgb(20, 20, 24),
                Stroke::new(1.0_f32, Color32::from_rgba_unmultiplied(255, 255, 255, 30)),
            );
            ui.painter().text(
                mono_rect.center(),
                egui::Align2::CENTER_CENTER,
                "F•",
                FontId::monospace(13.0),
                TEXT_1,
            );

            ui.add_space(12.0);

            ui.vertical(|ui| {
                ui.spacing_mut().item_spacing.y = 2.0;
                ui.horizontal(|ui| {
                    ui.label(
                        RichText::new("NEXUS FLIGHT DECK")
                            .font(FontId::proportional(13.0))
                            .strong()
                            .color(TEXT_1),
                    );
                    ui.add_space(6.0);
                    let (dot_rect, _) = ui.allocate_exact_size(Vec2::new(10.0, 10.0), Sense::hover());
                    let dot_color = if self.daemon_active { TEXT_1 } else { TEXT_3 };
                    if self.daemon_active {
                        ui.painter().circle_filled(
                            dot_rect.center(),
                            5.0,
                            Color32::from_rgba_unmultiplied(255, 255, 255, 45),
                        );
                    }
                    ui.painter().circle_filled(dot_rect.center(), 2.8, dot_color);
                });
                ui.label(
                    RichText::new(format!("{} // TIER: {}", hostname, tier))
                        .font(FontId::monospace(10.0))
                        .color(TEXT_3),
                );
            });

            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                // Circular 36x36 Settings Button
                let (gear_rect, gear_resp) =
                    ui.allocate_exact_size(Vec2::new(36.0, 36.0), Sense::click());
                let bg = if gear_resp.hovered() {
                    Color32::from_rgba_unmultiplied(255, 255, 255, 28)
                } else {
                    Color32::from_rgba_unmultiplied(255, 255, 255, 12)
                };
                let stroke = if gear_resp.hovered() {
                    BORDER_STRONG
                } else {
                    Color32::from_rgba_unmultiplied(255, 255, 255, 30)
                };
                ui.painter().circle(
                    gear_rect.center(),
                    17.5,
                    bg,
                    Stroke::new(1.0_f32, stroke),
                );
                ui.painter().text(
                    gear_rect.center(),
                    egui::Align2::CENTER_CENTER,
                    "⚙",
                    FontId::proportional(15.0),
                    if gear_resp.hovered() { TEXT_1 } else { TEXT_2 },
                );
                if gear_resp.clicked() {
                    self.show_settings = true;
                }

                ui.add_space(12.0);

                let pill_text = if self.daemon_active {
                    "5-NODE QUORUM ACTIVE"
                } else {
                    "DAEMON STANDBY"
                };
                Frame::none()
                    .fill(Color32::from_rgba_unmultiplied(255, 255, 255, 8))
                    .stroke(Stroke::new(1.0_f32, BORDER_SUBTLE))
                    .rounding(Rounding::same(20.0))
                    .inner_margin(Margin::symmetric(14.0, 6.0))
                    .show(ui, |ui| {
                        ui.label(
                            RichText::new(pill_text)
                                .font(FontId::monospace(10.0))
                                .color(TEXT_2),
                        );
                    });
            });
        });
    }

    fn render_main_grid(&mut self, ui: &mut egui::Ui, total_w: f32) {
        let gap = 24.0;
        let left_w = 350.0_f32.min(total_w * 0.34);
        let right_w = (total_w - left_w - gap).max(380.0);

        ui.horizontal_top(|ui| {
            ui.spacing_mut().item_spacing.x = gap;

            // Left Column: MUST use top_down layout!
            ui.allocate_ui_with_layout(
                Vec2::new(left_w, 0.0),
                Layout::top_down(Align::LEFT),
                |ui| {
                    self.render_fitness_card(ui, left_w);
                },
            );

            // Right Column: MUST use top_down layout!
            ui.allocate_ui_with_layout(
                Vec2::new(right_w, 0.0),
                Layout::top_down(Align::LEFT),
                |ui| {
                    ui.spacing_mut().item_spacing.y = 24.0;
                    self.render_telemetry_matrix_card(ui, right_w);
                    self.render_tricore_card(ui, right_w);
                },
            );
        });
    }

    fn card_frame() -> Frame {
        Frame::none()
            .fill(CARD_BG)
            .stroke(Stroke::new(1.0_f32, BORDER_SUBTLE))
            .rounding(Rounding::same(16.0))
            .inner_margin(Margin::same(24.0))
    }

    fn render_card_header(
        ui: &mut egui::Ui,
        inner_w: f32,
        title: &str,
        right_subtext: &str,
        right_color: Color32,
    ) {
        ui.allocate_ui_with_layout(
            Vec2::new(inner_w, 16.0),
            Layout::left_to_right(Align::Center),
            |ui| {
                ui.label(
                    RichText::new(title)
                        .font(FontId::monospace(10.0))
                        .strong()
                        .color(TEXT_3),
                );
                ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                    ui.label(
                        RichText::new(right_subtext)
                            .font(FontId::monospace(10.0))
                            .color(right_color),
                    );
                });
            },
        );
        ui.add_space(20.0);
    }

    fn render_fitness_card(&self, ui: &mut egui::Ui, card_w: f32) {
        let inner_w = (card_w - 48.0).max(200.0);
        Self::card_frame().show(ui, |ui| {
            ui.set_width(inner_w);
            let tier_label = format!("Tier: {}", capitalize(&self.cached_fitness.tier));
            Self::render_card_header(ui, inner_w, "01 // SYMBIOTE FITNESS", &tier_label, TEXT_3);

            // Centered 220x220 radial gauge
            ui.allocate_ui_with_layout(
                Vec2::new(inner_w, 220.0),
                Layout::top_down(Align::Center),
                |ui| {
                    let (rect, _) =
                        ui.allocate_exact_size(Vec2::new(216.0, 216.0), Sense::hover());
                    let painter = ui.painter();
                    let center = rect.center();
                    let radius = 94.0;

                    painter.circle_stroke(
                        center,
                        radius,
                        Stroke::new(4.0_f32, Color32::from_rgba_unmultiplied(255, 255, 255, 12)),
                    );

                    let pct = (self.cached_fitness.fitness_pct / 100.0).clamp(0.0, 1.0);
                    let sweep = pct * TAU;
                    let steps = 72;
                    let start_angle = -PI / 2.0;

                    if pct > 0.005 {
                        let mut pts = Vec::with_capacity(steps + 1);
                        let active_steps = ((steps as f32) * pct).ceil().max(2.0) as usize;
                        for i in 0..=active_steps {
                            let t = i as f32 / active_steps as f32;
                            let angle = start_angle + t * sweep;
                            pts.push(Pos2::new(
                                center.x + radius * angle.cos(),
                                center.y + radius * angle.sin(),
                            ));
                        }
                        painter.add(egui::Shape::line(
                            pts.clone(),
                            Stroke::new(
                                7.0_f32,
                                Color32::from_rgba_unmultiplied(255, 255, 255, 28),
                            ),
                        ));
                        painter.add(egui::Shape::line(pts, Stroke::new(4.0_f32, TEXT_1)));
                    }

                    painter.text(
                        Pos2::new(center.x, center.y - 8.0),
                        egui::Align2::CENTER_CENTER,
                        format!("{:.1}%", self.cached_fitness.fitness_pct),
                        FontId::proportional(44.0),
                        TEXT_1,
                    );
                    painter.text(
                        Pos2::new(center.x, center.y + 24.0),
                        egui::Align2::CENTER_CENTER,
                        "CONSENSUS SCORE",
                        FontId::monospace(9.0),
                        TEXT_3,
                    );
                },
            );

            ui.add_space(16.0);

            // Role badge box
            Frame::none()
                .fill(Color32::from_rgba_unmultiplied(255, 255, 255, 6))
                .stroke(Stroke::new(1.0_f32, BORDER_SUBTLE))
                .rounding(Rounding::same(8.0))
                .inner_margin(Margin::symmetric(14.0, 10.0))
                .show(ui, |ui| {
                    ui.allocate_ui_with_layout(
                        Vec2::new(inner_w - 28.0, 16.0),
                        Layout::top_down(Align::Center),
                        |ui| {
                            ui.label(
                                RichText::new(&self.cached_fitness.role_label)
                                    .font(FontId::monospace(11.0))
                                    .color(TEXT_2),
                            );
                        },
                    );
                });

            ui.add_space(20.0);

            // Hairline Headroom Bars
            render_hairline_bar(
                ui,
                inner_w,
                "RAM HEADROOM",
                self.cached_fitness.ram_headroom_pct,
            );
            ui.add_space(14.0);
            render_hairline_bar(
                ui,
                inner_w,
                "CPU HEADROOM",
                self.cached_fitness.cpu_headroom_pct,
            );
        });
    }

    fn render_telemetry_matrix_card(&self, ui: &mut egui::Ui, card_w: f32) {
        let inner_w = (card_w - 48.0).max(300.0);
        Self::card_frame().show(ui, |ui| {
            ui.set_width(inner_w);
            let uptime = System::uptime();
            let h = uptime / 3600;
            let m = (uptime % 3600) / 60;
            Self::render_card_header(
                ui,
                inner_w,
                "02 // HOST TELEMETRY MATRIX",
                &format!("Uptime: {}h {}m", h, m),
                TEXT_3,
            );

            let cpu_pct = self.sys.global_cpu_usage();
            let cpu_brand = self
                .sys
                .cpus()
                .first()
                .map(|c| c.brand().trim().to_string())
                .filter(|s| !s.is_empty())
                .unwrap_or_else(|| "Host CPU".to_string());
            let cores = self.sys.cpus().len();

            let total_gb = self.sys.total_memory() as f64 / 1_073_741_824.0;
            let used_gb = self.sys.used_memory() as f64 / 1_073_741_824.0;
            let mem_pct = if total_gb > 0.0 {
                (used_gb / total_gb) * 100.0
            } else {
                0.0
            };

            let (gpu_val, gpu_sub) = if let Some(ref g) = self.cached_gpu {
                (
                    g.name.clone(),
                    format!(
                        "{:.1} GB VRAM • {:.0}% Used",
                        g.vram_total_mb as f64 / 1024.0,
                        g.utilization_pct
                    ),
                )
            } else {
                (
                    "Integrated".to_string(),
                    "No discrete GPU detected".to_string(),
                )
            };

            let hub_host = self.config.primary_hub_host();
            let relay_val = if self.daemon_active {
                "Connected"
            } else {
                "Standby"
            };
            let relay_sub = format!(
                "{} • Daemon {}",
                hub_host,
                if self.daemon_active { "Active" } else { "Offline" }
            );

            let gap = 14.0;
            let tile_w = ((inner_w - gap) * 0.5).max(160.0);

            ui.horizontal(|ui| {
                ui.spacing_mut().item_spacing.x = gap;
                render_tile(
                    ui,
                    tile_w,
                    "PROCESSOR",
                    &format!("{:.1}%", cpu_pct),
                    &format!("{} • {}C", truncate_label(&cpu_brand, 24), cores),
                );
                render_tile(
                    ui,
                    tile_w,
                    "MEMORY",
                    &format!("{:.2} GB", used_gb),
                    &format!("{:.2} GB Total ({:.1}%)", total_gb, mem_pct),
                );
            });
            ui.add_space(gap);
            ui.horizontal(|ui| {
                ui.spacing_mut().item_spacing.x = gap;
                render_tile(
                    ui,
                    tile_w,
                    "GRAPHICS ENGINE",
                    &truncate_label(&gpu_val, 18),
                    &gpu_sub,
                );
                render_tile(ui, tile_w, "CAN-BUS RELAY", relay_val, &relay_sub);
            });
        });
    }

    fn render_tricore_card(&mut self, ui: &mut egui::Ui, card_w: f32) {
        let inner_w = (card_w - 48.0).max(300.0);
        Self::card_frame().show(ui, |ui| {
            ui.set_width(inner_w);
            Self::render_card_header(
                ui,
                inner_w,
                "03 // PROTECTED TRI-CORE PAYLOAD",
                "Survivability: Maximum",
                TEXT_1,
            );

            render_tricore_row(
                ui,
                inner_w,
                "Nexus Fleet Hub & Website",
                "faizal.uk // Rust Axum Core (Port 3002)",
                "PRIMARY ACTIVE",
                true,
            );
            ui.add_space(10.0);
            render_tricore_row(
                ui,
                inner_w,
                "Stalwart Mail Stack",
                "mail.faizal.uk // IMAP, SMTP, JMAP",
                "HOT STANDBY",
                false,
            );
            ui.add_space(10.0);
            render_tricore_row(
                ui,
                inner_w,
                "macOS Business Environment",
                "macos.faizal.uk // Docker Blue-Green",
                "HOT STANDBY",
                false,
            );

            ui.add_space(20.0);

            ui.horizontal(|ui| {
                ui.spacing_mut().item_spacing.x = 12.0;

                if minimal_action_button(ui, "↻ Restart Node").clicked() {
                    if let Ok(exe) = std::env::current_exe() {
                        let _ = std::process::Command::new(exe).arg("restart").spawn();
                        self.set_toast("Restarting Nexus Agent daemon...");
                    }
                }

                if minimal_action_button(ui, "↗ Cloud Admin Console").clicked() {
                    open_url_browser("https://faizal.uk/admin.html");
                    self.set_toast("Opening Cloud Admin Console...");
                }

                if minimal_action_button(ui, "⚙ Preferences").clicked() {
                    self.show_settings = true;
                }
            });
        });
    }

    fn render_preferences_modal(&mut self, ctx: &egui::Context) {
        let screen = ctx.screen_rect();
        egui::Area::new(egui::Id::new("preferences_scrim"))
            .fixed_pos(screen.min)
            .order(egui::Order::Foreground)
            .show(ctx, |ui| {
                let (_, scrim_resp) = ui.allocate_exact_size(screen.size(), Sense::click());
                ui.painter().rect_filled(
                    screen,
                    Rounding::ZERO,
                    Color32::from_rgba_unmultiplied(0, 0, 0, 190),
                );
                if scrim_resp.clicked() {
                    self.show_settings = false;
                }
            });

        egui::Window::new("Node Preferences")
            .title_bar(false)
            .collapsible(false)
            .resizable(false)
            .anchor(egui::Align2::CENTER_CENTER, Vec2::ZERO)
            .fixed_size(Vec2::new(440.0, 370.0))
            .frame(
                Frame::none()
                    .fill(SHEET_BG)
                    .stroke(Stroke::new(
                        1.0_f32,
                        Color32::from_rgba_unmultiplied(255, 255, 255, 32),
                    ))
                    .rounding(Rounding::same(22.0))
                    .inner_margin(Margin::same(28.0)),
            )
            .show(ctx, |ui| {
                ui.horizontal(|ui| {
                    ui.label(
                        RichText::new("Node Preferences")
                            .font(FontId::proportional(15.0))
                            .strong()
                            .color(TEXT_1),
                    );
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        let (close_rect, close_resp) =
                            ui.allocate_exact_size(Vec2::new(28.0, 28.0), Sense::click());
                        ui.painter().circle(
                            close_rect.center(),
                            13.5,
                            Color32::from_rgba_unmultiplied(255, 255, 255, 16),
                            Stroke::new(
                                1.0_f32,
                                Color32::from_rgba_unmultiplied(255, 255, 255, 26),
                            ),
                        );
                        ui.painter().text(
                            close_rect.center(),
                            egui::Align2::CENTER_CENTER,
                            "✕",
                            FontId::proportional(12.0),
                            TEXT_2,
                        );
                        if close_resp.clicked() {
                            self.show_settings = false;
                        }
                    });
                });

                ui.add_space(18.0);
                render_toggle_row(
                    ui,
                    "Autonomous Coordinator",
                    "Self-promote if all cloud instances fail",
                    &mut self.pref_coordinator,
                );
                ui.add_space(12.0);
                render_toggle_row(
                    ui,
                    "High-Cadence Telemetry",
                    "Stream system metrics at 2-second interval",
                    &mut self.pref_high_cadence,
                );
                ui.add_space(12.0);
                render_toggle_row(
                    ui,
                    "Local AI Engine Relay",
                    "Expose local Ollama models to cloud mesh",
                    &mut self.pref_ai_relay,
                );

                ui.add_space(16.0);
                ui.label(
                    RichText::new("Primary CAN-Bus WebSocket Hub")
                        .font(FontId::proportional(11.0))
                        .color(TEXT_2),
                );
                ui.add_space(6.0);
                ui.add_sized(
                    [ui.available_width(), 34.0],
                    egui::TextEdit::singleline(&mut self.hub_url_input)
                        .font(FontId::monospace(11.0)),
                );

                ui.add_space(22.0);
                ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                    let save_btn = egui::Button::new(
                        RichText::new("Apply & Save")
                            .font(FontId::monospace(11.0))
                            .strong()
                            .color(Color32::BLACK),
                    )
                    .fill(TEXT_1)
                    .stroke(Stroke::new(1.0_f32, TEXT_1))
                    .rounding(Rounding::same(8.0))
                    .min_size(Vec2::new(124.0, 34.0));

                    if ui.add(save_btn).clicked() {
                        self.save_preferences();
                    }
                });
            });
    }
}

fn render_hairline_bar(ui: &mut egui::Ui, width: f32, label: &str, pct: f32) {
    ui.allocate_ui_with_layout(
        Vec2::new(width, 16.0),
        Layout::left_to_right(Align::Center),
        |ui| {
            ui.label(RichText::new(label).font(FontId::monospace(10.0)).color(TEXT_3));
            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                ui.label(
                    RichText::new(format!("{:.1}%", pct))
                        .font(FontId::monospace(10.0))
                        .strong()
                        .color(TEXT_1),
                );
            });
        },
    );
    ui.add_space(6.0);
    let (rect, _) = ui.allocate_exact_size(Vec2::new(width, 3.0), Sense::hover());
    ui.painter().rect_filled(
        rect,
        Rounding::same(2.0),
        Color32::from_rgba_unmultiplied(255, 255, 255, 14),
    );
    let fill_w = (width * (pct / 100.0).clamp(0.0, 1.0)).max(2.0);
    let fill_rect = Rect::from_min_size(rect.min, Vec2::new(fill_w, 3.0));
    ui.painter()
        .rect_filled(fill_rect, Rounding::same(2.0), TEXT_1);
}

fn render_tile(ui: &mut egui::Ui, tile_w: f32, label: &str, value: &str, sub: &str) {
    Frame::none()
        .fill(TILE_BG)
        .stroke(Stroke::new(1.0_f32, BORDER_SUBTLE))
        .rounding(Rounding::same(12.0))
        .inner_margin(Margin::same(16.0))
        .show(ui, |ui| {
            ui.allocate_ui_with_layout(
                Vec2::new((tile_w - 34.0).max(120.0), 66.0),
                Layout::top_down(Align::LEFT),
                |ui| {
                    ui.label(RichText::new(label).font(FontId::monospace(9.0)).color(TEXT_3));
                    ui.add_space(6.0);
                    ui.label(
                        RichText::new(value)
                            .font(FontId::proportional(20.0))
                            .color(TEXT_1),
                    );
                    ui.add_space(4.0);
                    ui.label(RichText::new(sub).font(FontId::monospace(10.0)).color(TEXT_2));
                },
            );
        });
}

fn render_tricore_row(
    ui: &mut egui::Ui,
    row_w: f32,
    name: &str,
    tag: &str,
    state_label: &str,
    primary: bool,
) {
    Frame::none()
        .fill(ROW_BG)
        .stroke(Stroke::new(1.0_f32, BORDER_SUBTLE))
        .rounding(Rounding::same(10.0))
        .inner_margin(Margin::symmetric(18.0, 12.0))
        .show(ui, |ui| {
            ui.allocate_ui_with_layout(
                Vec2::new((row_w - 36.0).max(200.0), 34.0),
                Layout::left_to_right(Align::Center),
                |ui| {
                    ui.vertical(|ui| {
                        ui.spacing_mut().item_spacing.y = 2.0;
                        ui.label(
                            RichText::new(name)
                                .font(FontId::proportional(12.5))
                                .strong()
                                .color(TEXT_1),
                        );
                        ui.label(RichText::new(tag).font(FontId::monospace(9.5)).color(TEXT_3));
                    });

                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        let (bg, fg, border) = if primary {
                            (
                                Color32::from_rgba_unmultiplied(255, 255, 255, 16),
                                TEXT_1,
                                Color32::from_rgba_unmultiplied(255, 255, 255, 30),
                            )
                        } else {
                            (
                                Color32::from_rgba_unmultiplied(255, 255, 255, 8),
                                TEXT_2,
                                BORDER_SUBTLE,
                            )
                        };
                        Frame::none()
                            .fill(bg)
                            .stroke(Stroke::new(1.0_f32, border))
                            .rounding(Rounding::same(20.0))
                            .inner_margin(Margin::symmetric(10.0, 4.0))
                            .show(ui, |ui| {
                                ui.label(
                                    RichText::new(state_label)
                                        .font(FontId::monospace(9.0))
                                        .color(fg),
                                );
                            });
                    });
                },
            );
        });
}

fn minimal_action_button(ui: &mut egui::Ui, text: &str) -> egui::Response {
    let btn = egui::Button::new(RichText::new(text).font(FontId::monospace(11.0)).color(TEXT_2))
        .fill(Color32::from_rgba_unmultiplied(255, 255, 255, 10))
        .stroke(Stroke::new(1.0_f32, BORDER_SUBTLE))
        .rounding(Rounding::same(8.0))
        .min_size(Vec2::new(0.0, 34.0));
    ui.add(btn)
}

fn render_toggle_row(ui: &mut egui::Ui, title: &str, subtitle: &str, value: &mut bool) {
    ui.horizontal(|ui| {
        ui.vertical(|ui| {
            ui.spacing_mut().item_spacing.y = 2.0;
            ui.label(
                RichText::new(title)
                    .font(FontId::proportional(12.5))
                    .color(TEXT_1),
            );
            ui.label(
                RichText::new(subtitle)
                    .font(FontId::monospace(9.5))
                    .color(TEXT_3),
            );
        });
        ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
            let (rect, resp) = ui.allocate_exact_size(Vec2::new(40.0, 22.0), Sense::click());
            if resp.clicked() {
                *value = !*value;
            }
            let bg = if *value {
                TEXT_1
            } else {
                Color32::from_rgba_unmultiplied(255, 255, 255, 26)
            };
            let knob_col = if *value { Color32::BLACK } else { TEXT_2 };
            let knob_x = if *value {
                rect.right() - 11.0
            } else {
                rect.left() + 11.0
            };
            ui.painter()
                .rect_filled(rect, Rounding::same(11.0), bg);
            ui.painter()
                .circle_filled(Pos2::new(knob_x, rect.center().y), 8.0, knob_col);
        });
    });
}

fn open_url_browser(url: &str) {
    #[cfg(target_os = "windows")]
    {
        let _ = std::process::Command::new("cmd")
            .args(["/c", "start", "", url])
            .spawn();
    }
    #[cfg(target_os = "macos")]
    {
        let _ = std::process::Command::new("open").arg(url).spawn();
    }
    #[cfg(target_os = "linux")]
    {
        let _ = std::process::Command::new("xdg-open").arg(url).spawn();
    }
}

fn capitalize(s: &str) -> String {
    let mut c = s.chars();
    match c.next() {
        None => String::new(),
        Some(f) => f.to_uppercase().collect::<String>() + c.as_str(),
    }
}

fn truncate_label(s: &str, max_chars: usize) -> String {
    if s.chars().count() <= max_chars {
        s.to_string()
    } else {
        let mut out: String = s.chars().take(max_chars.saturating_sub(1)).collect();
        out.push('…');
        out
    }
}
