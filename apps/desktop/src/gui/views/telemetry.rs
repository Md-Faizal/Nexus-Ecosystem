use crate::gui::components::charts::{
    render_area_sparkline, render_core_heatmap, render_dual_network_chart,
};
use crate::gui::state::{GuiState, ProcessSort};
use crate::gui::theme::*;
use eframe::egui;
use egui::{FontId, Vec2};

pub fn render(ui: &mut egui::Ui, state: &mut GuiState, tier: LayoutTier) {
    let avail_w = ui.available_width().max(300.0);
    let gap = 16.0;

    // ─── CPU Cores & Memory Breakdown ───
    card_frame().show(ui, |ui| {
        let inner_w = (avail_w - 40.0).max(220.0);
        ui.set_width(inner_w);
        let load_str = state
            .telemetry
            .as_ref()
            .map(|t| {
                format!(
                    "Load {:.2} / {:.2} / {:.2}",
                    t.cpu.load_avg[0], t.cpu.load_avg[1], t.cpu.load_avg[2]
                )
            })
            .unwrap_or_else(|| "Sampling…".into());
        render_section_header(ui, "01 // LOGICAL CORE HEATMAP", &load_str, ACCENT_CYAN);
        render_core_heatmap(ui, &state.per_core_cpu);
    });

    ui.add_space(gap);

    // ─── Network & Storage (2-col on Wide/Medium, 1-col on Compact) ───
    if tier.is_compact() {
        render_network_card(ui, state, avail_w);
        ui.add_space(gap);
        render_storage_card(ui, state, avail_w);
    } else {
        let half_w = ((avail_w - gap) * 0.5).max(260.0);
        ui.horizontal_top(|ui| {
            ui.spacing_mut().item_spacing.x = gap;
            render_network_card(ui, state, half_w);
            render_storage_card(ui, state, half_w);
        });
    }

    ui.add_space(gap);

    // ─── GPU & Thermals ───
    if state.gpu.is_some()
        || state
            .telemetry
            .as_ref()
            .and_then(|t| t.thermals.as_ref())
            .and_then(|th| th.cpu_temp_c)
            .is_some()
    {
        render_gpu_thermals_card(ui, state, avail_w, tier);
        ui.add_space(gap);
    }

    // ─── Live Process Inspector ───
    render_processes_card(ui, state, avail_w, tier);
}

fn render_network_card(ui: &mut egui::Ui, state: &GuiState, card_w: f32) {
    let inner_w = (card_w - 40.0).max(200.0);
    card_frame().show(ui, |ui| {
        ui.set_width(inner_w);
        let (rx_rate, tx_rate, total_rx_gb, total_tx_gb) = state
            .telemetry
            .as_ref()
            .map(|t| {
                (
                    t.network.rx_bytes_sec as f64 / 1024.0,
                    t.network.tx_bytes_sec as f64 / 1024.0,
                    t.network.total_rx_bytes as f64 / 1_073_741_824.0,
                    t.network.total_tx_bytes as f64 / 1_073_741_824.0,
                )
            })
            .unwrap_or((0.0, 0.0, 0.0, 0.0));

        render_section_header(
            ui,
            "02 // NETWORK THROUGHPUT",
            &format!("↓{:.1} ↑{:.1} KB/s", rx_rate, tx_rate),
            ACCENT_CYAN,
        );

        let rx_vals = state.net_rx_history.as_vec();
        let tx_vals = state.net_tx_history.as_vec();
        render_dual_network_chart(ui, &rx_vals, &tx_vals, 130.0);

        ui.add_space(10.0);
        ui.horizontal(|ui| {
            pill_badge(ui, &format!("RX {:.2} GB", total_rx_gb), ACCENT_CYAN);
            pill_badge(ui, &format!("TX {:.2} GB", total_tx_gb), ACCENT_VIOLET);
        });
    });
}

fn render_storage_card(ui: &mut egui::Ui, state: &GuiState, card_w: f32) {
    let inner_w = (card_w - 40.0).max(200.0);
    card_frame().show(ui, |ui| {
        ui.set_width(inner_w);
        let disks = state
            .telemetry
            .as_ref()
            .map(|t| t.disks.as_slice())
            .unwrap_or(&[]);
        render_section_header(
            ui,
            "03 // STORAGE & MOUNTS",
            &format!("{} Volumes", disks.len()),
            TEXT_2,
        );

        if disks.is_empty() {
            ui.label(egui::RichText::new("No mounted disks detected").color(TEXT_3));
        } else {
            for d in disks.iter().take(6) {
                let used_gb = d.used_bytes as f64 / 1_073_741_824.0;
                let total_gb = d.total_bytes as f64 / 1_073_741_824.0;
                let color = if d.used_pct > 90.0 {
                    ACCENT_CRIMSON
                } else if d.used_pct > 75.0 {
                    ACCENT_AMBER
                } else {
                    ACCENT_EMERALD
                };
                render_hairline_bar(
                    ui,
                    inner_w,
                    &format!("{} ({})", truncate_label(&d.mount_point, 18), d.fs_type),
                    &format!("{:.1} / {:.1} GB ({:.0}%)", used_gb, total_gb, d.used_pct),
                    d.used_pct,
                    color,
                );
                ui.add_space(10.0);
            }
        }
    });
}

fn render_gpu_thermals_card(ui: &mut egui::Ui, state: &GuiState, card_w: f32, tier: LayoutTier) {
    let inner_w = (card_w - 40.0).max(220.0);
    card_frame().show(ui, |ui| {
        ui.set_width(inner_w);
        render_section_header(ui, "04 // GPU & THERMAL SENSORS", "LIVE", ACCENT_AMBER);

        if let Some(ref g) = state.gpu {
            let vram_pct = if g.vram_total_mb > 0 {
                (g.vram_used_mb as f32 / g.vram_total_mb as f32) * 100.0
            } else {
                0.0
            };
            ui.horizontal_wrapped(|ui| {
                pill_badge(ui, &g.name, ACCENT_AMBER);
                pill_badge(
                    ui,
                    &format!("VRAM {} / {} MB", g.vram_used_mb, g.vram_total_mb),
                    ACCENT_VIOLET,
                );
                if let Some(t) = g.temperature_c {
                    pill_badge(ui, &format!("{:.0}°C", t), ACCENT_CRIMSON);
                }
            });
            ui.add_space(10.0);
            let temp_vals = state.gpu_temp_history.as_vec();
            let gap = 12.0;
            if !tier.is_compact() {
                let half = ((inner_w - gap) * 0.5).max(160.0);
                ui.horizontal(|ui| {
                    ui.spacing_mut().item_spacing.x = gap;
                    ui.allocate_ui(Vec2::new(half, 100.0), |ui| {
                        render_hairline_bar(
                            ui,
                            half,
                            "VRAM ALLOCATION",
                            &format!("{:.1}%", vram_pct),
                            vram_pct,
                            ACCENT_VIOLET,
                        );
                    });
                    ui.allocate_ui(Vec2::new(half, 100.0), |ui| {
                        render_area_sparkline(
                            ui,
                            "GPU TEMPERATURE",
                            &format!("{:.0}°C", g.temperature_c.unwrap_or(0.0)),
                            &temp_vals,
                            Some(100.0),
                            ACCENT_AMBER,
                            56.0,
                            "°C",
                        );
                    });
                });
            } else {
                render_hairline_bar(
                    ui,
                    inner_w,
                    "VRAM ALLOCATION",
                    &format!("{:.1}%", vram_pct),
                    vram_pct,
                    ACCENT_VIOLET,
                );
            }
        }

        if let Some(cpu_temp) = state
            .telemetry
            .as_ref()
            .and_then(|t| t.thermals.as_ref())
            .and_then(|th| th.cpu_temp_c)
        {
            ui.add_space(8.0);
            let col = if cpu_temp > 80.0 {
                ACCENT_CRIMSON
            } else if cpu_temp > 65.0 {
                ACCENT_AMBER
            } else {
                ACCENT_EMERALD
            };
            ui.horizontal_wrapped(|ui| {
                pill_badge(ui, &format!("CPU Package: {:.0}°C", cpu_temp), col);
            });
        }
    });
}

fn render_processes_card(ui: &mut egui::Ui, state: &mut GuiState, card_w: f32, tier: LayoutTier) {
    let inner_w = (card_w - 40.0).max(220.0);
    card_frame().show(ui, |ui| {
        ui.set_width(inner_w);
        render_section_header(
            ui,
            "05 // LIVE PROCESS INSPECTOR",
            &format!("{} Active", state.processes.len()),
            TEXT_2,
        );

        ui.horizontal_wrapped(|ui| {
            ui.add(
                egui::TextEdit::singleline(&mut state.process_search)
                    .hint_text("Filter process name or PID…")
                    .desired_width(if tier.is_compact() {
                        inner_w
                    } else {
                        240.0
                    }),
            );
            ui.add_space(8.0);
            for (sort, label) in [
                (ProcessSort::Cpu, "Sort: CPU%"),
                (ProcessSort::Memory, "Sort: RAM"),
                (ProcessSort::Name, "Sort: Name"),
            ] {
                let active = state.process_sort == sort;
                let resp = if active {
                    accent_button(ui, label, ACCENT_CYAN)
                } else {
                    action_button(ui, label)
                };
                if resp.clicked() {
                    state.process_sort = sort;
                }
            }
        });

        ui.add_space(10.0);

        let q = state.process_search.trim().to_lowercase();
        let mut filtered: Vec<_> = state
            .processes
            .iter()
            .filter(|p| {
                q.is_empty()
                    || p.name.to_lowercase().contains(&q)
                    || p.pid.to_string().contains(&q)
            })
            .cloned()
            .collect();

        match state.process_sort {
            ProcessSort::Cpu => filtered.sort_by(|a, b| {
                b.cpu_pct
                    .partial_cmp(&a.cpu_pct)
                    .unwrap_or(std::cmp::Ordering::Equal)
            }),
            ProcessSort::Memory => filtered.sort_by(|a, b| {
                b.memory_mb
                    .partial_cmp(&a.memory_mb)
                    .unwrap_or(std::cmp::Ordering::Equal)
            }),
            ProcessSort::Name => filtered.sort_by(|a, b| a.name.cmp(&b.name)),
        }

        for p in filtered.iter().take(18) {
            row_frame().show(ui, |ui| {
                ui.set_width((inner_w - 28.0).max(160.0));
                ui.horizontal(|ui| {
                    ui.label(
                        egui::RichText::new(format!("{:<6}", p.pid))
                            .font(FontId::monospace(10.5))
                            .color(TEXT_3),
                    );
                    ui.label(
                        egui::RichText::new(truncate_label(&p.name, if tier.is_compact() { 18 } else { 32 }))
                            .font(FontId::proportional(12.0))
                            .strong()
                            .color(TEXT_1),
                    );
                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        pill_badge(
                            ui,
                            &format!("{:.1} MB", p.memory_mb),
                            ACCENT_VIOLET,
                        );
                        pill_badge(
                            ui,
                            &format!("{:.1}% CPU", p.cpu_pct),
                            if p.cpu_pct > 50.0 {
                                ACCENT_AMBER
                            } else {
                                ACCENT_CYAN
                            },
                        );
                    });
                });
            });
            ui.add_space(4.0);
        }
    });
}
