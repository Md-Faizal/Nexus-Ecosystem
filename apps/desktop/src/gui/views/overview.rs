use crate::gui::components::charts::{render_area_sparkline, render_fitness_arc};
use crate::gui::components::icons::{icon_label_button, IconKind};
use crate::gui::state::{DaemonAction, GuiCommand, GuiRoute, GuiState, LogLevel};
use crate::gui::theme::*;
use eframe::egui;
use egui::{FontId, Vec2};
use sysinfo::System;

pub fn render(ui: &mut egui::Ui, state: &mut GuiState, tier: LayoutTier) {
    let gap = 16.0;

    // ─── Top Hero: Fitness Arc + Live Telemetry Sparklines ───
    if tier.is_compact() {
        render_fitness_card(ui, state);
        ui.add_space(gap);
        render_live_sparklines_card(ui, state, tier);
    } else {
        split_columns_weighted(
            ui,
            state,
            0.35,
            gap,
            |left_ui, st| render_fitness_card(left_ui, st),
            |right_ui, st| render_live_sparklines_card(right_ui, st, tier),
        );
    }

    ui.add_space(gap);

    // ─── System Vitals Matrix ───
    render_vitals_card(ui, state, tier);

    ui.add_space(gap);

    // ─── Tri-Core Cluster & Quick Actions ───
    render_tricore_actions_card(ui, state, tier);
}

fn render_fitness_card(ui: &mut egui::Ui, state: &GuiState) {
    card_frame().show(ui, |ui| {
        ui.set_width(ui.available_width());
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
                164.0,
                state.fitness.fitness_pct.round() as u32,
                &state.fitness.tier,
            );
        });

        ui.add_space(12.0);
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
            "CPU LOAD",
            &format!("{:.1}%", cpu_pct),
            cpu_pct,
            ACCENT_CYAN,
        );
        ui.add_space(10.0);
        render_hairline_bar(
            ui,
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
        render_hairline_bar(ui, "GPU COMPUTE", &gpu_right_lbl, gpu_pct, ACCENT_EMERALD);
    });
}

fn render_live_sparklines_card(ui: &mut egui::Ui, state: &GuiState, tier: LayoutTier) {
    card_frame().show(ui, |ui| {
        ui.set_width(ui.available_width());
        let cadence = if state.pref_high_cadence {
            "1s LIVE"
        } else {
            "5s ECO"
        };
        render_section_header(ui, "02 // REAL-TIME TELEMETRY STREAM", cadence, ACCENT_CYAN);

        let cpu_vals = state.cpu_history.as_vec();
        let ram_vals = state.ram_history.as_vec();
        let rx_vals = state.net_rx_history.as_vec();
        let gpu_vals = state.gpu_history.as_vec();

        let cur_cpu = cpu_vals.last().copied().unwrap_or(0.0);
        let cur_ram = ram_vals.last().copied().unwrap_or(0.0);
        let cur_rx = rx_vals.last().copied().unwrap_or(0.0);
        let cur_gpu = gpu_vals.last().copied().unwrap_or(0.0);

        if tier.is_compact() {
            render_area_sparkline(
                ui,
                "CPU UTILIZATION",
                &format!("{:.1}%", cur_cpu),
                &cpu_vals,
                Some(100.0),
                ACCENT_CYAN,
                72.0,
                "%",
            );
            ui.add_space(10.0);
            render_area_sparkline(
                ui,
                "MEMORY USAGE",
                &format!("{:.1}%", cur_ram),
                &ram_vals,
                Some(100.0),
                ACCENT_VIOLET,
                72.0,
                "%",
            );
            ui.add_space(10.0);
            render_area_sparkline(
                ui,
                "NETWORK INGRESS",
                &format!("{:.1} KB/s", cur_rx),
                &rx_vals,
                None,
                ACCENT_EMERALD,
                72.0,
                " KB/s",
            );
        } else {
            ui.columns(2, |cols| {
                render_area_sparkline(
                    &mut cols[0],
                    "CPU UTILIZATION",
                    &format!("{:.1}%", cur_cpu),
                    &cpu_vals,
                    Some(100.0),
                    ACCENT_CYAN,
                    78.0,
                    "%",
                );
                render_area_sparkline(
                    &mut cols[1],
                    "MEMORY USAGE",
                    &format!("{:.1}%", cur_ram),
                    &ram_vals,
                    Some(100.0),
                    ACCENT_VIOLET,
                    78.0,
                    "%",
                );
            });
            ui.add_space(12.0);
            ui.columns(2, |cols| {
                render_area_sparkline(
                    &mut cols[0],
                    "NETWORK INGRESS",
                    &format!("{:.1} KB/s", cur_rx),
                    &rx_vals,
                    None,
                    ACCENT_EMERALD,
                    78.0,
                    " KB/s",
                );
                render_area_sparkline(
                    &mut cols[1],
                    "GPU UTILIZATION",
                    &format!("{:.0}%", cur_gpu),
                    &gpu_vals,
                    Some(100.0),
                    ACCENT_AMBER,
                    78.0,
                    "%",
                );
            });
        }
    });
}

fn render_vitals_card(ui: &mut egui::Ui, state: &GuiState, tier: LayoutTier) {
    card_frame().show(ui, |ui| {
        ui.set_width(ui.available_width());
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
                    truncate_label(&t.cpu.model, 20),
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
                truncate_label(&g.name, 18),
                format!(
                    "{:.1} GB VRAM • {:.0}°C",
                    g.vram_total_mb as f64 / 1024.0,
                    g.temperature_c.unwrap_or(0.0)
                ),
            )
        } else {
            ("Integrated".into(), "No discrete GPU".into())
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

        let tiles = [
            ("PROCESSOR", cpu_val, cpu_sub, ACCENT_CYAN),
            ("MEMORY", mem_val, mem_sub, ACCENT_VIOLET),
            ("GRAPHICS ENGINE", gpu_val, gpu_sub, ACCENT_AMBER),
            ("HUB LATENCY", relay_val, relay_sub, ACCENT_EMERALD),
        ];

        match tier {
            LayoutTier::Compact => {
                for (i, (lbl, val, sub, acc)) in tiles.iter().enumerate() {
                    render_metric_tile(ui, lbl, val, sub, *acc);
                    if i + 1 < tiles.len() {
                        ui.add_space(10.0);
                    }
                }
            }
            LayoutTier::Medium => {
                ui.columns(2, |cols| {
                    render_metric_tile(&mut cols[0], tiles[0].0, &tiles[0].1, &tiles[0].2, tiles[0].3);
                    render_metric_tile(&mut cols[1], tiles[1].0, &tiles[1].1, &tiles[1].2, tiles[1].3);
                });
                ui.add_space(10.0);
                ui.columns(2, |cols| {
                    render_metric_tile(&mut cols[0], tiles[2].0, &tiles[2].1, &tiles[2].2, tiles[2].3);
                    render_metric_tile(&mut cols[1], tiles[3].0, &tiles[3].1, &tiles[3].2, tiles[3].3);
                });
            }
            LayoutTier::Wide => {
                ui.columns(4, |cols| {
                    for (idx, (lbl, val, sub, acc)) in tiles.iter().enumerate() {
                        render_metric_tile(&mut cols[idx], lbl, val, sub, *acc);
                    }
                });
            }
        }
    });
}

fn render_tricore_actions_card(ui: &mut egui::Ui, state: &mut GuiState, tier: LayoutTier) {
    card_frame().show(ui, |ui| {
        ui.set_width(ui.available_width());
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
                ui.set_width(ui.available_width());
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

            if icon_label_button(ui, IconKind::Refresh, "Restart Daemon").clicked() {
                let _ = state
                    .cmd_tx
                    .send(GuiCommand::RunDaemonAction(DaemonAction::Restart));
                state.set_toast("Restarting Nexus daemon...", LogLevel::Info, "Daemon");
            }
            if icon_label_button(ui, IconKind::Cluster, "Inspect Fleet").clicked() {
                state.route = GuiRoute::Cluster;
            }
            if icon_label_button(ui, IconKind::AiStudio, "Launch AI Studio").clicked() {
                state.route = GuiRoute::AiStudio;
            }
            if icon_label_button(ui, IconKind::External, "Cloud Admin").clicked() {
                open_url_browser("https://faizal.uk/admin.html");
                state.set_toast("Opening Cloud Admin Console...", LogLevel::Info, "Browser");
            }
            if icon_label_button(ui, IconKind::Command, "Command Palette").clicked() {
                state.show_command_palette = true;
            }
        });
    });
}
