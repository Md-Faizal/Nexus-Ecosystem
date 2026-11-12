use crate::gui::theme::*;
use crate::modules::cluster_ops::RemoteNodeInfo;
use eframe::egui;
use egui::{Color32, FontId, Pos2, Rect, Rounding, Sense, Stroke, Vec2};
use std::f32::consts::PI;

pub fn render_fitness_arc(ui: &mut egui::Ui, size: f32, score: u32, tier: &str) {
    let (arc_rect, _) = ui.allocate_exact_size(Vec2::new(size, size), Sense::hover());
    let center = arc_rect.center();
    let radius = (size * 0.43).max(40.0);
    let painter = ui.painter();

    // Subtle concentric orbital ring
    painter.circle_stroke(
        center,
        radius + 10.0,
        Stroke::new(1.0, Color32::from_rgba_unmultiplied(255, 255, 255, 10)),
    );

    let start_angle = 135.0_f32.to_radians();
    let sweep_total = 270.0_f32.to_radians();

    // Degree tick marks around the arc
    for i in 0..=27 {
        let frac = i as f32 / 27.0;
        let angle = start_angle + sweep_total * frac;
        let inner = radius + 4.0;
        let outer = if i % 9 == 0 { radius + 9.0 } else { radius + 6.5 };
        let p1 = Pos2::new(center.x + angle.cos() * inner, center.y + angle.sin() * inner);
        let p2 = Pos2::new(center.x + angle.cos() * outer, center.y + angle.sin() * outer);
        painter.line_segment(
            [p1, p2],
            Stroke::new(1.0, Color32::from_rgba_unmultiplied(255, 255, 255, if i % 9 == 0 { 45 } else { 18 })),
        );
    }

    let track_pts: Vec<Pos2> = (0..=64)
        .map(|i| {
            let a = start_angle + sweep_total * (i as f32 / 64.0);
            Pos2::new(center.x + a.cos() * radius, center.y + a.sin() * radius)
        })
        .collect();
    painter.add(egui::Shape::line(
        track_pts,
        Stroke::new(8.0, Color32::from_rgb(24, 24, 30)),
    ));

    let pct = (score as f32 / 100.0).clamp(0.0, 1.0);
    let arc_color = if score >= 80 {
        ACCENT_EMERALD
    } else if score >= 50 {
        ACCENT_CYAN
    } else {
        ACCENT_AMBER
    };

    if pct > 0.0 {
        let active_pts: Vec<Pos2> = (0..=64)
            .map(|i| {
                let a = start_angle + sweep_total * pct * (i as f32 / 64.0);
                Pos2::new(center.x + a.cos() * radius, center.y + a.sin() * radius)
            })
            .collect();
        painter.add(egui::Shape::line(
            active_pts,
            Stroke::new(
                14.0,
                Color32::from_rgba_unmultiplied(arc_color.r(), arc_color.g(), arc_color.b(), 28),
            ),
        ));
        let active_pts_core: Vec<Pos2> = (0..=64)
            .map(|i| {
                let a = start_angle + sweep_total * pct * (i as f32 / 64.0);
                Pos2::new(center.x + a.cos() * radius, center.y + a.sin() * radius)
            })
            .collect();
        painter.add(egui::Shape::line(
            active_pts_core,
            Stroke::new(7.5, arc_color),
        ));
    }

    painter.text(
        Pos2::new(center.x, center.y - 6.0),
        egui::Align2::CENTER_CENTER,
        format!("{}", score),
        FontId::proportional(size * 0.25),
        TEXT_1,
    );
    painter.text(
        Pos2::new(center.x, center.y + size * 0.14),
        egui::Align2::CENTER_CENTER,
        tier.to_uppercase(),
        FontId::monospace(10.0),
        arc_color,
    );
}

pub fn render_area_sparkline(
    ui: &mut egui::Ui,
    title: &str,
    current_text: &str,
    values: &[f32],
    max_scale: Option<f32>,
    color: Color32,
    height: f32,
    unit: &str,
) {
    tile_frame().show(ui, |ui| {
        ui.set_width(ui.available_width());
        ui.horizontal(|ui| {
            ui.label(
                egui::RichText::new(title)
                    .font(FontId::monospace(9.5))
                    .color(TEXT_3),
            );
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                ui.label(
                    egui::RichText::new(current_text)
                        .font(FontId::monospace(11.5))
                        .strong()
                        .color(color),
                );
            });
        });
        ui.add_space(6.0);

        let chart_w = ui.available_width().max(80.0);
        let (rect, resp) = ui.allocate_exact_size(Vec2::new(chart_w, height), Sense::hover());
        let painter = ui.painter_at(rect);

        // Subtle horizontal reference grid
        for frac in [0.25, 0.5, 0.75] {
            let y = rect.bottom() - rect.height() * frac;
            painter.line_segment(
                [Pos2::new(rect.left(), y), Pos2::new(rect.right(), y)],
                Stroke::new(1.0, Color32::from_rgba_unmultiplied(255, 255, 255, 8)),
            );
        }

        if values.len() < 2 {
            painter.text(
                rect.center(),
                egui::Align2::CENTER_CENTER,
                "Sampling…",
                FontId::monospace(9.5),
                TEXT_3,
            );
            return;
        }

        let peak = max_scale.unwrap_or_else(|| {
            values
                .iter()
                .copied()
                .fold(1.0_f32, f32::max)
                .max(1.0)
                * 1.15
        });

        let step_x = rect.width() / (values.len() - 1).max(1) as f32;
        let pts: Vec<Pos2> = values
            .iter()
            .enumerate()
            .map(|(i, &v)| {
                let x = rect.left() + i as f32 * step_x;
                let norm = (v / peak).clamp(0.0, 1.0);
                let y = rect.bottom() - norm * (rect.height() - 4.0) - 2.0;
                Pos2::new(x, y)
            })
            .collect();

        // Translucent area fill
        let mut mesh = egui::Mesh::default();
        let fill_top = Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 48);
        let fill_bot = Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 4);
        for (i, &p) in pts.iter().enumerate() {
            let base = Pos2::new(p.x, rect.bottom());
            mesh.colored_vertex(p, fill_top);
            mesh.colored_vertex(base, fill_bot);
            if i > 0 {
                let idx = (i as u32 - 1) * 2;
                mesh.add_triangle(idx, idx + 1, idx + 2);
                mesh.add_triangle(idx + 1, idx + 3, idx + 2);
            }
        }
        painter.add(egui::Shape::mesh(mesh));

        // Crisp stroke line
        painter.add(egui::Shape::line(pts.clone(), Stroke::new(1.75, color)));

        // Hover crosshair
        if let Some(hover_pos) = resp.hover_pos() {
            let idx = ((hover_pos.x - rect.left()) / step_x)
                .round()
                .clamp(0.0, (values.len() - 1) as f32) as usize;
            if let Some(&pt) = pts.get(idx) {
                painter.line_segment(
                    [Pos2::new(pt.x, rect.top()), Pos2::new(pt.x, rect.bottom())],
                    Stroke::new(1.0, Color32::from_rgba_unmultiplied(255, 255, 255, 50)),
                );
                painter.circle_filled(pt, 3.5, color);
                painter.text(
                    Pos2::new((pt.x + 6.0).min(rect.right() - 46.0), rect.top() + 2.0),
                    egui::Align2::LEFT_TOP,
                    format!("{:.1}{}", values[idx], unit),
                    FontId::monospace(9.5),
                    TEXT_1,
                );
            }
        }
    });
}

pub fn render_dual_network_chart(
    ui: &mut egui::Ui,
    rx_vals: &[f32],
    tx_vals: &[f32],
    height: f32,
) {
    let chart_w = ui.available_width().max(120.0);
    let (rect, _) = ui.allocate_exact_size(Vec2::new(chart_w, height), Sense::hover());
    let painter = ui.painter_at(rect);

    painter.rect(
        rect,
        Rounding::same(8.0),
        BG_TILE,
        Stroke::new(1.0, BORDER_SUBTLE),
    );

    for frac in [0.25, 0.5, 0.75] {
        let y = rect.bottom() - rect.height() * frac;
        painter.line_segment(
            [Pos2::new(rect.left() + 6.0, y), Pos2::new(rect.right() - 6.0, y)],
            Stroke::new(1.0, Color32::from_rgba_unmultiplied(255, 255, 255, 10)),
        );
    }

    let max_val = rx_vals
        .iter()
        .chain(tx_vals.iter())
        .copied()
        .fold(10.0_f32, f32::max)
        * 1.15;

    let draw_series = |vals: &[f32], color: Color32| {
        if vals.len() < 2 {
            return;
        }
        let step_x = (rect.width() - 12.0) / (vals.len() - 1).max(1) as f32;
        let pts: Vec<Pos2> = vals
            .iter()
            .enumerate()
            .map(|(i, &v)| {
                let x = rect.left() + 6.0 + i as f32 * step_x;
                let norm = (v / max_val).clamp(0.0, 1.0);
                let y = rect.bottom() - 6.0 - norm * (rect.height() - 14.0);
                Pos2::new(x, y)
            })
            .collect();
        painter.add(egui::Shape::line(pts, Stroke::new(1.8, color)));
    };

    draw_series(rx_vals, ACCENT_CYAN);
    draw_series(tx_vals, ACCENT_VIOLET);

    painter.text(
        Pos2::new(rect.left() + 10.0, rect.top() + 8.0),
        egui::Align2::LEFT_TOP,
        format!("Peak {:.1} KB/s", max_val / 1.15),
        FontId::monospace(9.5),
        TEXT_3,
    );
}

pub fn render_core_heatmap(ui: &mut egui::Ui, cores: &[f32]) {
    if cores.is_empty() {
        ui.label(egui::RichText::new("No CPU core metrics available").color(TEXT_3));
        return;
    }

    let avail = ui.available_width();
    let cols = if avail < 420.0 {
        4
    } else if avail < 760.0 {
        8
    } else {
        12
    };
    let gap = 6.0;
    let cell_w = ((avail - gap * (cols - 1) as f32) / cols as f32).max(34.0);

    for chunk in cores.chunks(cols) {
        ui.horizontal(|ui| {
            ui.spacing_mut().item_spacing.x = gap;
            for (idx_in_chunk, &pct) in chunk.iter().enumerate() {
                let _ = idx_in_chunk;
                let color = if pct > 85.0 {
                    ACCENT_CRIMSON
                } else if pct > 60.0 {
                    ACCENT_AMBER
                } else if pct > 25.0 {
                    ACCENT_CYAN
                } else {
                    ACCENT_EMERALD
                };

                let (rect, _) = ui.allocate_exact_size(Vec2::new(cell_w, 30.0), Sense::hover());
                let alpha = (20.0 + (pct / 100.0) * 120.0).clamp(18.0, 140.0) as u8;
                ui.painter().rect(
                    rect,
                    Rounding::same(6.0),
                    Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), alpha),
                    Stroke::new(1.0, Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 70)),
                );
                ui.painter().text(
                    rect.center(),
                    egui::Align2::CENTER_CENTER,
                    format!("{:.0}%", pct),
                    FontId::monospace(9.5),
                    TEXT_1,
                );
            }
        });
        ui.add_space(4.0);
    }
}

pub fn render_topology_canvas(
    ui: &mut egui::Ui,
    nodes: &[RemoteNodeInfo],
    hub_latency_ms: Option<u64>,
    selected_node: &mut Option<String>,
) {
    let w = ui.available_width().max(260.0);
    let h = 230.0;
    let (rect, _) = ui.allocate_exact_size(Vec2::new(w, h), Sense::hover());
    let painter = ui.painter_at(rect);

    painter.rect(
        rect,
        Rounding::same(12.0),
        BG_TILE,
        Stroke::new(1.0, BORDER_SUBTLE),
    );

    // Subtle grid dots
    let step = 24.0;
    let mut gx = rect.left() + 16.0;
    while gx < rect.right() - 10.0 {
        let mut gy = rect.top() + 16.0;
        while gy < rect.bottom() - 10.0 {
            painter.circle_filled(
                Pos2::new(gx, gy),
                0.9,
                Color32::from_rgba_unmultiplied(255, 255, 255, 16),
            );
            gy += step;
        }
        gx += step;
    }

    let center = rect.center();
    let hub_pos = Pos2::new(center.x, center.y - 10.0);

    // Core Tri-VM Ring
    let core_vms = [
        ("my-mac", "Ingress", Pos2::new(center.x - (w * 0.28).min(180.0), center.y - 45.0), ACCENT_EMERALD),
        ("container-of-souls", "Failover", Pos2::new(center.x + (w * 0.28).min(180.0), center.y - 45.0), ACCENT_CYAN),
        ("prometheus", "Metrics", Pos2::new(center.x, center.y + 62.0), ACCENT_VIOLET),
    ];

    for (name, role, pos, color) in core_vms {
        painter.line_segment(
            [hub_pos, pos],
            Stroke::new(1.5, Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 95)),
        );
        painter.circle_filled(
            pos,
            22.0,
            Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 26),
        );
        painter.circle_stroke(pos, 22.0, Stroke::new(1.2, color));
        painter.text(
            Pos2::new(pos.x, pos.y - 4.0),
            egui::Align2::CENTER_CENTER,
            name,
            FontId::monospace(9.5),
            TEXT_1,
        );
        painter.text(
            Pos2::new(pos.x, pos.y + 9.0),
            egui::Align2::CENTER_CENTER,
            role,
            FontId::monospace(8.5),
            color,
        );
    }

    // Edge nodes orbiting outer radius
    let edge_count = nodes.len().min(8);
    for (i, node) in nodes.iter().take(edge_count).enumerate() {
        let angle = (i as f32 / edge_count.max(1) as f32) * 2.0 * PI - PI * 0.5;
        let rx = (w * 0.40).min(290.0);
        let ry = 82.0;
        let pos = Pos2::new(center.x + angle.cos() * rx, center.y + angle.sin() * ry);
        let online = node.status == "online";
        let color = if online { ACCENT_EMERALD } else { ACCENT_CRIMSON };

        painter.line_segment(
            [hub_pos, pos],
            Stroke::new(1.0, Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 55)),
        );

        let node_rect = Rect::from_center_size(pos, Vec2::new(86.0, 26.0));
        let is_sel = selected_node.as_deref() == Some(&node.node_id);
        let resp = ui.interact(node_rect, ui.id().with(&node.node_id), Sense::click());
        if resp.clicked() {
            *selected_node = Some(node.node_id.clone());
        }

        painter.rect(
            node_rect,
            Rounding::same(13.0),
            BG_CARD,
            Stroke::new(if is_sel { 1.5 } else { 1.0 }, color),
        );
        painter.text(
            node_rect.center(),
            egui::Align2::CENTER_CENTER,
            truncate_label(&node.hostname, 11),
            FontId::monospace(9.0),
            TEXT_1,
        );
    }

    // Central Hub Node
    let hub_color = if hub_latency_ms.is_some() {
        TEXT_1
    } else {
        ACCENT_AMBER
    };
    painter.circle_filled(hub_pos, 26.0, BG_CARD);
    painter.circle_stroke(hub_pos, 26.0, Stroke::new(1.8, hub_color));
    painter.text(
        Pos2::new(hub_pos.x, hub_pos.y - 4.0),
        egui::Align2::CENTER_CENTER,
        "CAN-BUS",
        FontId::monospace(9.5),
        TEXT_1,
    );
    let lat_str = hub_latency_ms
        .map(|ms| format!("{}ms", ms))
        .unwrap_or_else(|| "STANDBY".to_string());
    painter.text(
        Pos2::new(hub_pos.x, hub_pos.y + 9.0),
        egui::Align2::CENTER_CENTER,
        lat_str,
        FontId::monospace(8.5),
        ACCENT_CYAN,
    );
}

