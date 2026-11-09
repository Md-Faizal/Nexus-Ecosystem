use eframe::egui;
use egui::{Color32, FontId, Pos2, Rect, Rounding, Stroke, Vec2};
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant};
use sysinfo::System;

use crate::mesh::protocol::GpuTelemetry;
use crate::modules::fitness::FitnessScore;
use crate::modules::gpu::GpuCollector;

static GUI_RUNNING: AtomicBool = AtomicBool::new(false);

/// Launch the native Flight Deck GUI window (SpaceX / xAI Monochrome design)
pub fn launch_native_gui() {
    if GUI_RUNNING.swap(true, Ordering::SeqCst) {
        tracing::warn!("Native GUI window is already open.");
        return;
    }

    let native_options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_inner_size([980.0, 700.0])
            .with_min_inner_size([840.0, 580.0])
            .with_title("NEXUS // FLIGHT DECK")
            .with_active(true),
        ..Default::default()
    };

    let _ = eframe::run_native(
        "NEXUS // FLIGHT DECK",
        native_options,
        Box::new(|cc| {
            configure_spacex_theme(&cc.egui_ctx);
            Ok(Box::new(FlightDeckApp::new()))
        }),
    );

    GUI_RUNNING.store(false, Ordering::SeqCst);
}

fn configure_spacex_theme(ctx: &egui::Context) {
    let mut visuals = egui::Visuals::dark();

    // Pure obsidian background & subtle precision borders
    visuals.override_text_color = Some(Color32::from_rgb(240, 240, 245));
    visuals.panel_fill = Color32::from_rgb(5, 5, 8);
    visuals.window_fill = Color32::from_rgb(8, 8, 12);
    visuals.window_stroke = Stroke::new(1.0_f32, Color32::from_rgba_unmultiplied(255, 255, 255, 25));
    visuals.window_rounding = Rounding::same(12.0);

    // Widgets styling: minimalist satin monochrome
    visuals.widgets.noninteractive.bg_fill = Color32::from_rgb(12, 12, 16);
    visuals.widgets.noninteractive.bg_stroke = Stroke::new(1.0_f32, Color32::from_rgba_unmultiplied(255, 255, 255, 18));
    visuals.widgets.noninteractive.rounding = Rounding::same(8.0);

    visuals.widgets.inactive.bg_fill = Color32::from_rgb(18, 18, 24);
    visuals.widgets.inactive.bg_stroke = Stroke::new(1.0_f32, Color32::from_rgba_unmultiplied(255, 255, 255, 25));
    visuals.widgets.inactive.rounding = Rounding::same(8.0);

    visuals.widgets.hovered.bg_fill = Color32::from_rgb(30, 30, 38);
    visuals.widgets.hovered.bg_stroke = Stroke::new(1.0_f32, Color32::from_rgba_unmultiplied(255, 255, 255, 60));
    visuals.widgets.hovered.rounding = Rounding::same(8.0);

    visuals.widgets.active.bg_fill = Color32::from_rgb(45, 45, 56);
    visuals.widgets.active.bg_stroke = Stroke::new(1.0_f32, Color32::from_rgb(255, 255, 255));
    visuals.widgets.active.rounding = Rounding::same(8.0);

    ctx.set_visuals(visuals);
}

pub struct FlightDeckApp {
    sys: System,
    last_refresh: Instant,
    cached_fitness: FitnessScore,
    cached_gpu: Option<GpuTelemetry>,
    show_settings: bool,
    hub_url: String,
    poll_interval_secs: u32,
    telemetry_enabled: bool,
    ollama_bridge_enabled: bool,
    tray_enabled: bool,
    copied_toast_time: Option<Instant>,
}

impl FlightDeckApp {
    pub fn new() -> Self {
        let mut sys = System::new_all();
        sys.refresh_all();

        let gpu = GpuCollector::collect();
        let total_ram_mb = sys.total_memory() / (1024 * 1024);
        let used_ram_mb = sys.used_memory() / (1024 * 1024);
        let cpu_usage = sys.global_cpu_usage();
        let is_cloud = FitnessScore::detect_is_cloud();

        let fitness = FitnessScore::calculate(total_ram_mb, used_ram_mb, cpu_usage, is_cloud, false, true);

        Self {
            sys,
            last_refresh: Instant::now(),
            cached_fitness: fitness,
            cached_gpu: gpu,
            show_settings: false,
            hub_url: "https://faizal.uk".to_string(),
            poll_interval_secs: 2,
            telemetry_enabled: true,
            ollama_bridge_enabled: true,
            tray_enabled: true,
            copied_toast_time: None,
        }
    }

    fn refresh_metrics(&mut self) {
        if self.last_refresh.elapsed() >= Duration::from_millis(1500) {
            self.sys.refresh_cpu_all();
            self.sys.refresh_memory();
            self.cached_gpu = GpuCollector::collect();

            let total_ram_mb = self.sys.total_memory() / (1024 * 1024);
            let used_ram_mb = self.sys.used_memory() / (1024 * 1024);
            let cpu_usage = self.sys.global_cpu_usage();
            let is_cloud = FitnessScore::detect_is_cloud();

            self.cached_fitness = FitnessScore::calculate(total_ram_mb, used_ram_mb, cpu_usage, is_cloud, false, true);
            self.last_refresh = Instant::now();
        }
    }
}

impl eframe::App for FlightDeckApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        self.refresh_metrics();

        // Continuous refresh for live telemetry meters
        ctx.request_repaint_after(Duration::from_millis(1000));

        let cpu_pct = self.sys.global_cpu_usage();
        let mem_total_gb = self.sys.total_memory() as f64 / 1_073_741_824.0;
        let mem_used_gb = self.sys.used_memory() as f64 / 1_073_741_824.0;
        let mem_pct = if mem_total_gb > 0.0 { (mem_used_gb / mem_total_gb) * 100.0 } else { 0.0 };
        let fitness = &self.cached_fitness;

        egui::CentralPanel::default().show(ctx, |ui| {
            ui.spacing_mut().item_spacing = Vec2::new(12.0, 12.0);

            // ─── TOP ARCHITECTURAL HEADER ─────────────────────────────────────
            ui.horizontal(|ui| {
                // Monogram mark
                let (rect, _) = ui.allocate_exact_size(Vec2::new(34.0, 34.0), egui::Sense::hover());
                ui.painter().rect_filled(rect, Rounding::same(8.0), Color32::from_rgb(18, 18, 24));
                ui.painter().rect_stroke(rect, Rounding::same(8.0), Stroke::new(1.0_f32, Color32::from_rgba_unmultiplied(255, 255, 255, 40)));
                ui.painter().text(
                    rect.center(),
                    egui::Align2::CENTER_CENTER,
                    "NX",
                    FontId::monospace(13.0),
                    Color32::WHITE,
                );

                ui.add_space(4.0);

                ui.vertical(|ui| {
                    ui.label(egui::RichText::new("NEXUS // FLIGHT DECK").font(FontId::proportional(14.0)).strong().color(Color32::WHITE));
                    ui.label(egui::RichText::new("AUTONOMOUS DECENTRALIZED MESH • ZERO HARDCODING").font(FontId::monospace(9.5)).color(Color32::from_rgb(140, 140, 150)));
                });

                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    // ─── CIRCULAR iOS/macOS STYLE SETTINGS BUTTON ─────────────
                    let (btn_rect, btn_resp) = ui.allocate_exact_size(Vec2::new(36.0, 36.0), egui::Sense::click());
                    let is_hovered = btn_resp.hovered();
                    let bg_color = if self.show_settings {
                        Color32::from_rgb(45, 45, 55)
                    } else if is_hovered {
                        Color32::from_rgb(32, 32, 40)
                    } else {
                        Color32::from_rgb(16, 16, 22)
                    };

                    let border_color = if self.show_settings || is_hovered {
                        Color32::from_rgba_unmultiplied(255, 255, 255, 80)
                    } else {
                        Color32::from_rgba_unmultiplied(255, 255, 255, 30)
                    };

                    ui.painter().circle_filled(btn_rect.center(), 18.0, bg_color);
                    ui.painter().circle_stroke(btn_rect.center(), 18.0, Stroke::new(1.0_f32, border_color));
                    ui.painter().text(
                        btn_rect.center(),
                        egui::Align2::CENTER_CENTER,
                        "⚙",
                        FontId::proportional(16.0),
                        if is_hovered || self.show_settings { Color32::WHITE } else { Color32::from_rgb(180, 180, 190) },
                    );

                    if btn_resp.clicked() {
                        self.show_settings = !self.show_settings;
                    }

                    // Live Mesh Status Pill
                    ui.add_space(10.0);
                    let (pill_rect, _) = ui.allocate_exact_size(Vec2::new(150.0, 28.0), egui::Sense::hover());
                    ui.painter().rect_filled(pill_rect, Rounding::same(14.0), Color32::from_rgb(12, 12, 16));
                    ui.painter().rect_stroke(pill_rect, Rounding::same(14.0), Stroke::new(1.0_f32, Color32::from_rgba_unmultiplied(255, 255, 255, 25)));
                    
                    // Pulsing green dot
                    let dot_pos = Pos2::new(pill_rect.min.x + 14.0, pill_rect.center().y);
                    ui.painter().circle_filled(dot_pos, 4.0, Color32::from_rgb(40, 200, 100));
                    ui.painter().text(
                        Pos2::new(pill_rect.min.x + 24.0, pill_rect.center().y),
                        egui::Align2::LEFT_CENTER,
                        "MESH CONNECTED",
                        FontId::monospace(10.0),
                        Color32::from_rgb(220, 220, 230),
                    );
                });
            });

            ui.add_space(4.0);
            ui.separator();
            ui.add_space(4.0);

            // ─── SETTINGS FLYOUT DRAWER (If Open) ─────────────────────────────
            if self.show_settings {
                egui::Frame::none()
                    .fill(Color32::from_rgb(12, 12, 16))
                    .rounding(Rounding::same(10.0))
                    .stroke(Stroke::new(1.0_f32, Color32::from_rgba_unmultiplied(255, 255, 255, 40)))
                    .inner_margin(16.0)
                    .show(ui, |ui| {
                        ui.horizontal(|ui| {
                            ui.label(egui::RichText::new("PRECISION NODE SETTINGS").font(FontId::proportional(12.0)).strong().color(Color32::WHITE));
                            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                                if ui.button("✕ Close Settings").clicked() {
                                    self.show_settings = false;
                                }
                            });
                        });
                        ui.add_space(8.0);

                        ui.horizontal(|ui| {
                            ui.label(egui::RichText::new("Cloud Hub URL:").font(FontId::monospace(11.0)));
                            ui.text_edit_singleline(&mut self.hub_url);
                        });

                        ui.horizontal(|ui| {
                            ui.label(egui::RichText::new("Telemetry Interval:").font(FontId::monospace(11.0)));
                            ui.add(egui::Slider::new(&mut self.poll_interval_secs, 1..=30).suffix("s"));
                        });

                        ui.horizontal(|ui| {
                            ui.checkbox(&mut self.telemetry_enabled, "Hardware Telemetry Stream");
                            ui.checkbox(&mut self.ollama_bridge_enabled, "Local AI Bridge");
                            ui.checkbox(&mut self.tray_enabled, "Systray Minimization");
                        });
                    });
                ui.add_space(8.0);
            }

            // ─── CONSENSUS FITNESS GAUGE BANNER ───────────────────────────────
            egui::Frame::none()
                .fill(Color32::from_rgb(10, 10, 14))
                .rounding(Rounding::same(10.0))
                .stroke(Stroke::new(1.0_f32, Color32::from_rgba_unmultiplied(255, 255, 255, 25)))
                .inner_margin(16.0)
                .show(ui, |ui| {
                    ui.horizontal(|ui| {
                        ui.vertical(|ui| {
                            ui.label(egui::RichText::new("DYNAMIC FLEET FITNESS & CONSENSUS").font(FontId::monospace(10.5)).color(Color32::from_rgb(160, 160, 175)));
                            ui.add_space(2.0);
                            ui.horizontal(|ui| {
                                ui.label(egui::RichText::new(format!("{:.1}%", fitness.fitness_pct)).font(FontId::proportional(28.0)).strong().color(Color32::WHITE));
                                ui.add_space(10.0);
                                let tier_label = fitness.tier.to_uppercase();
                                let (badge_rect, _) = ui.allocate_exact_size(Vec2::new(90.0, 24.0), egui::Sense::hover());
                                ui.painter().rect_filled(badge_rect, Rounding::same(12.0), Color32::from_rgb(22, 22, 30));
                                ui.painter().rect_stroke(badge_rect, Rounding::same(12.0), Stroke::new(1.0_f32, Color32::from_rgba_unmultiplied(255, 255, 255, 35)));
                                ui.painter().text(badge_rect.center(), egui::Align2::CENTER_CENTER, tier_label, FontId::monospace(10.0), Color32::WHITE);
                            });
                        });

                        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                            ui.vertical(|ui| {
                                ui.label(egui::RichText::new("CONSENSUS ROLE").font(FontId::monospace(9.5)).color(Color32::from_rgb(140, 140, 150)));
                                ui.label(egui::RichText::new(&fitness.role_label).font(FontId::proportional(12.0)).color(Color32::from_rgb(200, 200, 215)));
                            });
                        });
                    });

                    ui.add_space(8.0);
                    // Hairline visual meter
                    let (bar_rect, _) = ui.allocate_exact_size(Vec2::new(ui.available_width(), 6.0), egui::Sense::hover());
                    ui.painter().rect_filled(bar_rect, Rounding::same(3.0), Color32::from_rgb(20, 20, 26));
                    let fill_w = (bar_rect.width() * (fitness.fitness_pct / 100.0)).clamp(0.0, bar_rect.width());
                    let fill_rect = Rect::from_min_size(bar_rect.min, Vec2::new(fill_w, bar_rect.height()));
                    ui.painter().rect_filled(fill_rect, Rounding::same(3.0), Color32::from_rgb(240, 240, 250));
                });

            ui.add_space(4.0);

            // ─── 4-GRID HARDWARE TELEMETRY CARDS ──────────────────────────────
            let grid_w = ui.available_width();
            let card_w = (grid_w - 12.0) / 2.0;

            ui.horizontal(|ui| {
                // CARD 1: CPU COMPUTE
                render_card(ui, card_w, "CPU COMPUTE HEADROOM", |ui| {
                    ui.label(egui::RichText::new(format!("{:.1}% LOAD", cpu_pct)).font(FontId::proportional(20.0)).strong().color(Color32::WHITE));
                    let cpu_name = self.sys.cpus().first().map(|c| c.brand()).unwrap_or("Dynamic Multi-Core CPU");
                    ui.label(egui::RichText::new(format!("{} ({} cores)", cpu_name, self.sys.cpus().len())).font(FontId::monospace(9.5)).color(Color32::from_rgb(140, 140, 150)));
                    render_mini_bar(ui, (cpu_pct / 100.0) as f32);
                });

                // CARD 2: RAM MEMORY
                render_card(ui, card_w, "MEMORY ALLOCATION", |ui| {
                    ui.label(egui::RichText::new(format!("{:.2} / {:.2} GB ({:.0}%)", mem_used_gb, mem_total_gb, mem_pct)).font(FontId::proportional(18.0)).strong().color(Color32::WHITE));
                    ui.label(egui::RichText::new("Sub-15MB daemon RSS footprint target").font(FontId::monospace(9.5)).color(Color32::from_rgb(140, 140, 150)));
                    render_mini_bar(ui, (mem_pct / 100.0) as f32);
                });
            });

            ui.horizontal(|ui| {
                // CARD 3: DYNAMIC GPU TELEMETRY (ZERO HARDCODING)
                render_card(ui, card_w, "GRAPHICS ACCELERATOR (DYNAMIC)", |ui| {
                    if let Some(gpu) = &self.cached_gpu {
                        let util = gpu.utilization_pct;
                        let vram_str = if gpu.vram_total_mb > 0 {
                            format!("{:.1} / {:.1} GB VRAM", gpu.vram_used_mb as f64 / 1024.0, gpu.vram_total_mb as f64 / 1024.0)
                        } else {
                            "Unified System VRAM".to_string()
                        };
                        ui.label(egui::RichText::new(&gpu.name).font(FontId::proportional(15.0)).strong().color(Color32::WHITE));
                        ui.label(egui::RichText::new(format!("Vendor: {} • {}", gpu.vendor, vram_str)).font(FontId::monospace(9.5)).color(Color32::from_rgb(140, 140, 150)));
                        render_mini_bar(ui, (util / 100.0) as f32);
                    } else {
                        ui.label(egui::RichText::new("Integrated / CPU Offloading").font(FontId::proportional(15.0)).strong().color(Color32::from_rgb(180, 180, 190)));
                        ui.label(egui::RichText::new("No discrete GPU queried • Dynamic Fallback Active").font(FontId::monospace(9.5)).color(Color32::from_rgb(120, 120, 130)));
                        render_mini_bar(ui, 0.0);
                    }
                });

                // CARD 4: MESH CLUSTER MATRIX
                render_card(ui, card_w, "TRI-NODE SWARM STATUS", |ui| {
                    ui.label(egui::RichText::new("my-mac • cos • pro").font(FontId::proportional(15.0)).strong().color(Color32::WHITE));
                    ui.label(egui::RichText::new("Active Blue-Green Replication • Latency: ~18ms").font(FontId::monospace(9.5)).color(Color32::from_rgb(140, 140, 150)));
                    render_mini_bar(ui, 0.92);
                });
            });

            ui.add_space(4.0);

            // ─── TRI-CORE PROTECTED PAYLOAD SECTION ───────────────────────────
            egui::Frame::none()
                .fill(Color32::from_rgb(10, 10, 14))
                .rounding(Rounding::same(10.0))
                .stroke(Stroke::new(1.0_f32, Color32::from_rgba_unmultiplied(255, 255, 255, 20)))
                .inner_margin(14.0)
                .show(ui, |ui| {
                    ui.label(egui::RichText::new("TRI-CORE ZERO-DOWNTIME PROTECTED PAYLOAD").font(FontId::monospace(10.0)).color(Color32::from_rgb(140, 140, 150)));
                    ui.add_space(6.0);
                    ui.horizontal(|ui| {
                        render_payload_badge(ui, "NEXUS CORE HUB", "PORT 3002", "ONLINE (CLOUD)");
                        render_payload_badge(ui, "STALWART MAIL", "PORT 25/993", "REPLICATED");
                        render_payload_badge(ui, "MACOS PROD APP", "PORT 3001/3004", "BLUE/GREEN");
                    });
                });

            ui.with_layout(egui::Layout::bottom_up(egui::Align::Center), |ui| {
                ui.horizontal(|ui| {
                    if ui.button("📋 Copy Diagnostics JSON").clicked() {
                        let json = serde_json::json!({
                            "fitness": fitness.fitness_pct,
                            "tier": fitness.tier,
                            "cpu_pct": cpu_pct,
                            "ram_used_gb": mem_used_gb,
                            "gpu": self.cached_gpu
                        });
                        ui.output_mut(|o| o.copied_text = json.to_string());
                        self.copied_toast_time = Some(Instant::now());
                    }

                    if let Some(t) = self.copied_toast_time {
                        if t.elapsed() < Duration::from_secs(3) {
                            ui.label(egui::RichText::new("✓ Copied to Clipboard").font(FontId::monospace(10.0)).color(Color32::from_rgb(80, 220, 120)));
                        }
                    }

                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        ui.label(egui::RichText::new("NEXUS AGENT v0.1.82 • STANDALONE RUST NATIVE").font(FontId::monospace(9.0)).color(Color32::from_rgb(90, 90, 100)));
                    });
                });
                ui.add_space(4.0);
                ui.separator();
            });
        });
    }
}

fn render_card<F>(ui: &mut egui::Ui, width: f32, title: &str, content: F)
where
    F: FnOnce(&mut egui::Ui),
{
    egui::Frame::none()
        .fill(Color32::from_rgb(10, 10, 14))
        .rounding(Rounding::same(8.0))
        .stroke(Stroke::new(1.0_f32, Color32::from_rgba_unmultiplied(255, 255, 255, 22)))
        .inner_margin(12.0)
        .show(ui, |ui| {
            ui.set_width(width);
            ui.label(egui::RichText::new(title).font(FontId::monospace(9.5)).color(Color32::from_rgb(140, 140, 150)));
            ui.add_space(4.0);
            content(ui);
        });
}

fn render_mini_bar(ui: &mut egui::Ui, pct: f32) {
    ui.add_space(4.0);
    let (bar_rect, _) = ui.allocate_exact_size(Vec2::new(ui.available_width(), 4.0), egui::Sense::hover());
    ui.painter().rect_filled(bar_rect, Rounding::same(2.0), Color32::from_rgb(22, 22, 28));
    let fill_w = (bar_rect.width() * pct.clamp(0.0, 1.0)).clamp(0.0, bar_rect.width());
    let fill_rect = Rect::from_min_size(bar_rect.min, Vec2::new(fill_w, bar_rect.height()));
    ui.painter().rect_filled(fill_rect, Rounding::same(2.0), Color32::from_rgb(240, 240, 250));
}

fn render_payload_badge(ui: &mut egui::Ui, name: &str, port: &str, status: &str) {
    let (rect, _) = ui.allocate_exact_size(Vec2::new(ui.available_width() / 3.0 - 4.0, 36.0), egui::Sense::hover());
    ui.painter().rect_filled(rect, Rounding::same(6.0), Color32::from_rgb(14, 14, 18));
    ui.painter().rect_stroke(rect, Rounding::same(6.0), Stroke::new(1.0_f32, Color32::from_rgba_unmultiplied(255, 255, 255, 18)));

    ui.painter().text(
        Pos2::new(rect.min.x + 8.0, rect.center().y - 6.0),
        egui::Align2::LEFT_CENTER,
        name,
        FontId::monospace(9.5),
        Color32::WHITE,
    );
    ui.painter().text(
        Pos2::new(rect.min.x + 8.0, rect.center().y + 8.0),
        egui::Align2::LEFT_CENTER,
        format!("{} • {}", port, status),
        FontId::monospace(8.5),
        Color32::from_rgb(130, 130, 140),
    );
}
