use crate::gui::components::charts::{render_area_sparkline, render_fitness_arc};
use crate::gui::state::{DaemonAction, GuiCommand, GuiRoute, GuiState, LogLevel};
use crate::gui::theme::*;
use eframe::egui;
use egui::{FontId, Vec2};
use sysinfo::System;

pub fn render(ui: &mut egui::Ui, state: &mut GuiState, tier: LayoutTier) {
    let avail_w = ui.available_width().max(300.0);
    let gap = 16.0;

    // ─── Top Hero: Fitness Arc + Live Telemetry Sparklines ───
    if tier.is_compact() {
        render_fitness_card(ui, state, avail_w);
        ui.add_space(gap);
        render_live_sparklines_card(ui, state, avail_w, tier);
    } else {
        let left_w = (avail_w * 0.36).clamp(280.0, 390.0);
        let right_w = (avail_w - left_w - gap).max(300.0);
        ui.horizontal_top(|ui| {
            ui.spacing_mut().item_spacing.x = gap;
            render_fitness_card(ui, state, left_w);
            render_live_sparklines_card(ui, state, right_w, tier);
        });
    }

    ui.add_space(gap);

    // ─── System Vitals Matrix ───
    render_vitals_card(ui, state, avail_w, tier);

    ui.add_space(gap);

    // ─── Tri-Core Cluster & Quick Actions ───
    render_tricore_actions_card(ui, state, avail_w, tier);
}

fn render_fitness_card(ui: &mut egui::Ui, state: &GuiState, card_w: f32) {
    let inner_w = (card_w - 40.0).max(220.0);
    card_frame().show(ui, |ui| {
        ui.set_width(inner_w);
        render_section_header(
            ui,
            "01 // COMPUTE FITNESS",
            &state.fitness.tier.to_uppercase(),
            if state.fitness.fitness_pct >= 80.0 {
                ACCENT_EMERALD
            } else {
                ACCENT_CYAN
            },
        );

        ui.vertical_centered(|ui| {
            render_fitness_arc(
                ui,
                168.0,
                state.fitness.fitness_pct.round() as u32,
                &state.fitness.tier,
            );
        });

        ui.add_space(10.0);
        let ram_pct = state
            .telemetry
            .as_ref()
            .map(|t| t.memory.used_pct)
            .unwrap_or(0.0);
        let cpu_pct = state
            .telemetry
            .as_ref()
            .map(|t| t.cpu.usage_pct)
            .unwrap_or(0.0);
        let gpu_pct = state
            .gpu
            .as_ref()
            .map(|g| g.utilization_pct)
            .unwrap_or(0.0);

        render_hairline_bar(
            ui,
            inner_w,
            "CPU LOAD",
            &format!("{:.1}%", cpu_pct),
            cpu_pct,
            ACCENT_CYAN,
        );
        ui.add_space(10.0);
        render_hairline_bar(
            ui,
            inner_w,
            "MEMORY PRESSURE",
            &format!("{:.1}%", ram_pct),
            ram_pct,
            ACCENT_VIOLET,
        );
        ui.add_space(10.0);
        let gpu_right_lbl = if state.gpu.is_some() {
            format!("{:.0}%", gpu_pct)
        } else {
            "N/A".to_string()
        };
        render_hairline_bar(
            ui,
            inner_w,
            "GPU COMPUTE",
            &gpu_right_lbl,
            gpu_pct,
            ACCENT_EMERALD,
        );
    });
}

fn render_live_sparklines_card(
    ui: &mut egui::Ui,
    state: &GuiState,
    card_w: f32,
    tier: LayoutTier,
) {
    let inner_w = (card_w - 40.0).max(220.0);
    card_frame().show(ui, |ui| {
        ui.set_width(inner_w);
        let cadence = if state.pref_high_cadence { "1s LIVE" } else { "5s ECO" };
        render_section_header(ui, "02 // REAL-TIME TELEMETRY STREAM", cadence, ACCENT_CYAN);

        let cpu_vals = state.cpu_history.as_vec();
        let ram_vals = state.ram_history.as_vec();
        let rx_vals = state.net_rx_history.as_vec();
        let gpu_vals = state.gpu_history.as_vec();

        let cur_cpu = cpu_vals.last().copied().unwrap_or(0.0);
        let cur_ram = ram_vals.last().copied().unwrap_or(0.0);
        let cur_rx = rx_vals.last().copied().unwrap_or(0.0);
        let cur_gpu = gpu_vals.last().copied().unwrap_or(0.0);

        let gap = 12.0;
        let cols = if tier.is_compact() { 1 } else { 2 };
        let cell_w = if cols == 1 {
            inner_w
        } else {
            ((inner_w - gap) * 0.5).max(140.0)
        };

        if cols == 1 {
            render_area_sparkline(
                ui,
                "CPU UTILIZATION",
                &format!("{:.1}%", cur_cpu),
                &cpu_vals,
                Some(100.0),
                ACCENT_CYAN,
                74.0,
                "%",
            );
            ui.add_space(gap);
            render_area_sparkline(
                ui,
                "MEMORY USAGE",
                &format!("{:.1}%", cur_ram),
                &ram_vals,
                Some(100.0),
                ACCENT_VIOLET,
                74.0,
                "%",
            );
            ui.add_space(gap);
            render_area_sparkline(
                ui,
                "NETWORK INGRESS",
                &format!("{:.1} KB/s", cur_rx),
                &rx_vals,
                None,
                ACCENT_EMERALD,
                74.0,
                " KB/s",
            );
        } else {
            ui.horizontal(|ui| {
                ui.spacing_mut().item_spacing.x = gap;
                ui.allocate_ui(Vec2::new(cell_w, 120.0), |ui| {
                    render_area_sparkline(
                        ui,
                        "CPU UTILIZATION",
                        &format!("{:.1}%", cur_cpu),
                        &cpu_vals,
                        Some(100.0),
                        ACCENT_CYAN,
                        82.0,
                        "%",
                    );
                });
                ui.allocate_ui(Vec2::new(cell_w, 120.0), |ui| {
                    render_area_sparkline(
                        ui,
                        "MEMORY USAGE",
                        &format!("{:.1}%", cur_ram),
                        &ram_vals,
                        Some(100.0),
                        ACCENT_VIOLET,
                        82.0,
                        "%",
                    );
                });
            });
            ui.add_space(gap);
            ui.horizontal(|ui| {
                ui.spacing_mut().item_spacing.x = gap;
                ui.allocate_ui(Vec2::new(cell_w, 120.0), |ui| {
                    render_area_sparkline(
                        ui,
                        "NETWORK INGRESS",
                        &format!("{:.1} KB/s", cur_rx),
                        &rx_vals,
                        None,
                        ACCENT_EMERALD,
                        82.0,
                        " KB/s",
                    );
                });
                ui.allocate_ui(Vec2::new(cell_w, 120.0), |ui| {
                    render_area_sparkline(
                        ui,
                        "GPU UTILIZATION",
                        &format!("{:.0}%", cur_gpu),
                        &gpu_vals,
                        Some(100.0),
                        ACCENT_AMBER,
                        82.0,
                        "%",
                    );
                });
            });
        }
    });
}

fn render_vitals_card(ui: &mut egui::Ui, state: &GuiState, card_w: f32, tier: LayoutTier) {
    let inner_w = (card_w - 40.0).max(220.0);
    card_frame().show(ui, |ui| {
        ui.set_width(inner_w);
        let uptime = System::uptime();
        let h = uptime / 3600;
        let m = (uptime % 3600) / 60;
        render_section_header(
            ui,
            "03 // SYSTEM VITALS MATRIX",
            &format!("Uptime {}h {}m", h, m),
            TEXT_2,
        );

        let (cpu_val, cpu_sub) = if let Some(ref t) = state.telemetry {
            (
                format!("{:.1}%", t.cpu.usage_pct),
                format!(
                    "{} • {}C/{}T",
                    truncate_label(&t.cpu.model, 22),
                    t.cpu.physical_cores,
                    t.cpu.logical_cores
                ),
            )
        } else {
            ("—".into(), "Collecting…".into())
        };

        let (mem_val, mem_sub) = if let Some(ref t) = state.telemetry {
            let used_gb = t.memory.used_bytes as f64 / 1_073_741_824.0;
            let total_gb = t.memory.total_bytes as f64 / 1_073_741_824.0;
            (
                format!("{:.2} GB", used_gb),
                format!("{:.1} GB Total ({:.1}%)", total_gb, t.memory.used_pct),
            )
        } else {
            ("—".into(), "Collecting…".into())
        };

        let (gpu_val, gpu_sub) = if let Some(ref g) = state.gpu {
            (
                truncate_label(&g.name, 20),
                format!(
                    "{:.1} GB VRAM • {:.0}°C",
                    g.vram_total_mb as f64 / 1024.0,
                    g.temperature_c.unwrap_or(0.0)
                ),
            )
        } else {
            ("Integrated".into(), "No discrete GPU detected".into())
        };

        let relay_val = if let Some(ms) = state.hub_latency_ms {
            format!("{} ms", ms)
        } else if state.daemon_active {
            "Connected".into()
        } else {
            "Standby".into()
        };
        let relay_sub = format!(
            "{} • {} nodes",
            state.config.primary_hub_host(),
            state.remote_nodes.len()
        );

        let gap = 12.0;
        let cols = match tier {
            LayoutTier::Compact => 1,
            LayoutTier::Medium => 2,
            LayoutTier::Wide => 4,
        };
        let tile_w = (inner_w - gap * (cols - 1) as f32) / cols as f32;

        let tiles = [
            ("PROCESSOR", cpu_val, cpu_sub, ACCENT_CYAN),
            ("MEMORY", mem_val, mem_sub, ACCENT_VIOLET),
            ("GRAPHICS ENGINE", gpu_val, gpu_sub, ACCENT_AMBER),
            ("HUB LATENCY", relay_val, relay_sub, ACCENT_EMERALD),
        ];

        for chunk in tiles.chunks(cols) {
            ui.horizontal(|ui| {
                ui.spacing_mut().item_spacing.x = gap;
                for (lbl, val, sub, acc) in chunk {
                    render_metric_tile(ui, tile_w, lbl, val, sub, *acc);
                }
            });
            ui.add_space(gap);
        }
    });
}

fn render_tricore_actions_card(
    ui: &mut egui::Ui,
    state: &mut GuiState,
    card_w: f32,
    tier: LayoutTier,
) {
    let inner_w = (card_w - 40.0).max(220.0);
    card_frame().show(ui, |ui| {
        ui.set_width(inner_w);
        render_section_header(
            ui,
            "04 // TRI-CORE CLUSTER & QUICK COMMANDS",
            if state.hub_latency_ms.is_some() {
                "MESH LINKED"
            } else {
                "LOCAL MODE"
            },
            if state.hub_latency_ms.is_some() {
                ACCENT_EMERALD
            } else {
                TEXT_3
            },
        );

        let rows = [
            (
                "Nexus Fleet Hub & Website",
                "faizal.uk // Rust Axum Core (Port 3002)",
                "PRIMARY ACTIVE",
                true,
            ),
            (
                "Stalwart Mail Stack",
                "mail.faizal.uk // IMAP, SMTP, JMAP",
                "HOT STANDBY",
                false,
            ),
            (
                "macOS Business Environment",
                "macos.faizal.uk // Docker Blue-Green",
                "HOT STANDBY",
                false,
            ),
        ];

        for (title, sub, badge, primary) in rows {
            row_frame().show(ui, |ui| {
                ui.set_width((inner_w - 28.0).max(160.0));
                ui.horizontal(|ui| {
                    let dot_color = if primary { ACCENT_EMERALD } else { TEXT_3 };
                    let (r, _) = ui.allocate_exact_size(Vec2::new(8.0, 8.0), egui::Sense::hover());
                    ui.painter().circle_filled(r.center(), 3.0, dot_color);
                    ui.vertical(|ui| {
                        ui.label(
                            egui::RichText::new(title)
                                .font(FontId::proportional(13.0))
                                .strong()
                                .color(TEXT_1),
                        );
                        if !tier.is_compact() {
                            ui.label(
                                egui::RichText::new(sub)
                                    .font(FontId::monospace(10.0))
                                    .color(TEXT_3),
                            );
                        }
                    });
                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        pill_badge(ui, badge, dot_color);
                    });
                });
            });
            ui.add_space(8.0);
        }

        ui.add_space(8.0);
        ui.horizontal_wrapped(|ui| {
            ui.spacing_mut().item_spacing = Vec2::new(10.0, 8.0);

            if action_button(ui, "↻ Restart Daemon").clicked() {
                let _ = state
                    .cmd_tx
                    .send(GuiCommand::RunDaemonAction(DaemonAction::Restart));
                state.set_toast("Restarting Nexus daemon...", LogLevel::Info, "Daemon");
            }
            if action_button(ui, "⬡ Inspect Fleet").clicked() {
                state.route = GuiRoute::Cluster;
            }
            if action_button(ui, "✦ Launch AI Studio").clicked() {
                state.route = GuiRoute::AiStudio;
            }
            if action_button(ui, "↗ Cloud Admin").clicked() {
                open_url_browser("https://faizal.uk/admin.html");
                state.set_toast("Opening Cloud Admin Console...", LogLevel::Info, "Browser");
            }
            if action_button(ui, "⌘ Command Palette").clicked() {
                state.show_command_palette = true;
            }
        });
    });
}
