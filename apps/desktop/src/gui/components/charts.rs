use crate::gui::theme::*;
use crate::modules::cluster_ops::RemoteNodeInfo;
use eframe::egui;
use egui::{Color32, FontId, Pos2, Rect, Rounding, Sense, Stroke, Vec2};
use std::f32::consts::PI;

/// Subdivides a polyline using centripetal-like Catmull-Rom interpolation for silky-smooth curves.
fn catmull_rom_smooth(pts: &[Pos2], subdivisions: usize, min_y: f32, max_y: f32) -> Vec<Pos2> {
    if pts.len() < 3 || subdivisions == 0 {
        return pts.to_vec();
    }
    let mut out = Vec::with_capacity((pts.len() - 1) * subdivisions + 1);
    for i in 0..pts.len() - 1 {
        let p0 = if i == 0 { pts[0] } else { pts[i - 1] };
        let p1 = pts[i];
        let p2 = pts[i + 1];
        let p3 = if i + 2 < pts.len() {
            pts[i + 2]
        } else {
            pts[pts.len() - 1]
        };
        for s in 0..subdivisions {
            let t = s as f32 / subdivisions as f32;
            let t2 = t * t;
            let t3 = t2 * t;
            let x = 0.5
                * ((2.0 * p1.x)
                    + (-p0.x + p2.x) * t
                    + (2.0 * p0.x - 5.0 * p1.x + 4.0 * p2.x - p3.x) * t2
                    + (-p0.x + 3.0 * p1.x - 3.0 * p2.x + p3.x) * t3);
            let y = 0.5
                * ((2.0 * p1.y)
                    + (-p0.y + p2.y) * t
                    + (2.0 * p0.y - 5.0 * p1.y + 4.0 * p2.y - p3.y) * t2
                    + (-p0.y + 3.0 * p1.y - 3.0 * p2.y + p3.y) * t3);
            out.push(Pos2::new(x, y.clamp(min_y, max_y)));
        }
    }
    if let Some(&last) = pts.last() {
        out.push(last);
    }
    out
}

pub fn render_fitness_arc(ui: &mut egui::Ui, size: f32, score: u32, tier: &str) {
    let (arc_rect, _) = ui.allocate_exact_size(Vec2::new(size, size), Sense::hover());
    let center = arc_rect.center();
    let radius = (size * 0.43).max(40.0);
    let painter = ui.painter();

    let anim_score = ui
        .ctx()
        .animate_value_with_time(ui.make_persistent_id("fitness_arc_score"), score as f32, 0.55);
    let time = ui.input(|i| i.time) as f32;
    let pulse = (time * 2.6).sin() * 0.5 + 0.5;

    // Subtle concentric orbital ring
    painter.circle_stroke(
        center,
        radius + 10.0,
        Stroke::new(
            1.0_f32,
            Color32::from_rgba_unmultiplied(255, 255, 255, (8.0 + pulse * 8.0) as u8),
        ),
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

    let pct = (anim_score / 100.0).clamp(0.0, 1.0);
    let arc_color = if score >= 80 {
        ACCENT_EMERALD
    } else if score >= 50 {
        ACCENT_CYAN
    } else {
        ACCENT_AMBER
    };

    if pct > 0.0 {
        let active_pts: Vec<Pos2> = (0..=72)
            .map(|i| {
                let a = start_angle + sweep_total * pct * (i as f32 / 72.0);
                Pos2::new(center.x + a.cos() * radius, center.y + a.sin() * radius)
            })
            .collect();
        // Soft multi-layer bloom
        painter.add(egui::Shape::line(
            active_pts.clone(),
            Stroke::new(
                18.0_f32,
                Color32::from_rgba_unmultiplied(arc_color.r(), arc_color.g(), arc_color.b(), 16),
            ),
        ));
        painter.add(egui::Shape::line(
            active_pts.clone(),
            Stroke::new(
                12.0_f32,
                Color32::from_rgba_unmultiplied(arc_color.r(), arc_color.g(), arc_color.b(), 34),
            ),
        ));
        painter.add(egui::Shape::line(
            active_pts.clone(),
            Stroke::new(7.5_f32, arc_color),
        ));
        if let Some(&tip) = active_pts.last() {
            painter.circle_filled(
                tip,
                5.0 + pulse * 2.5,
                Color32::from_rgba_unmultiplied(arc_color.r(), arc_color.g(), arc_color.b(), 70),
            );
            painter.circle_filled(tip, 3.8, TEXT_1);
        }
    }

    painter.text(
        Pos2::new(center.x, center.y - 6.0),
        egui::Align2::CENTER_CENTER,
        format!("{}", anim_score.round() as u32),
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

        let last_val = values.last().copied().unwrap_or(0.0);
        let smooth_last = ui.ctx().animate_value_with_time(
            ui.make_persistent_id(("sparkline_tip", title)),
            last_val,
            0.35,
        );

        let step_x = rect.width() / (values.len() - 1).max(1) as f32;
        let raw_pts: Vec<Pos2> = values
            .iter()
            .enumerate()
            .map(|(i, &v)| {
                let val = if i + 1 == values.len() { smooth_last } else { v };
                let x = rect.left() + i as f32 * step_x;
                let norm = (val / peak).clamp(0.0, 1.0);
                let y = rect.bottom() - norm * (rect.height() - 6.0) - 3.0;
                Pos2::new(x, y)
            })
            .collect();

        let pts = catmull_rom_smooth(&raw_pts, 4, rect.top() + 2.0, rect.bottom() - 2.0);

        // Translucent area fill
        let mut mesh = egui::Mesh::default();
        let fill_top = Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 54);
        let fill_bot = Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 2);
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

        // Soft bloom + crisp stroke line
        painter.add(egui::Shape::line(
            pts.clone(),
            Stroke::new(
                5.0_f32,
                Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 32),
            ),
        ));
        painter.add(egui::Shape::line(pts.clone(), Stroke::new(1.85_f32, color)));

        // Pulsing live endpoint dot
        if let Some(&tip) = pts.last() {
            let pulse = ((ui.input(|i| i.time) as f32 * 3.0).sin() * 0.5 + 0.5) * 3.0;
            painter.circle_filled(
                tip,
                4.0 + pulse,
                Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 65),
            );
            painter.circle_filled(tip, 2.6, TEXT_1);
        }

        // Hover crosshair
        if let Some(hover_pos) = resp.hover_pos() {
            let idx = ((hover_pos.x - rect.left()) / step_x)
                .round()
                .clamp(0.0, (values.len() - 1) as f32) as usize;
            if let Some(&pt) = raw_pts.get(idx) {
                painter.line_segment(
                    [Pos2::new(pt.x, rect.top()), Pos2::new(pt.x, rect.bottom())],
                    Stroke::new(1.0_f32, Color32::from_rgba_unmultiplied(255, 255, 255, 50)),
                );
                painter.circle_filled(pt, 3.8, color);
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

    let draw_series = |vals: &[f32], color: Color32, id_tag: &str| {
        if vals.len() < 2 {
            return;
        }
        let last_v = vals.last().copied().unwrap_or(0.0);
        let smooth_last = ui.ctx().animate_value_with_time(
            ui.make_persistent_id(("dual_net", id_tag)),
            last_v,
            0.35,
        );
        let step_x = (rect.width() - 12.0) / (vals.len() - 1).max(1) as f32;
        let raw_pts: Vec<Pos2> = vals
            .iter()
            .enumerate()
            .map(|(i, &v)| {
                let val = if i + 1 == vals.len() { smooth_last } else { v };
                let x = rect.left() + 6.0 + i as f32 * step_x;
                let norm = (val / max_val).clamp(0.0, 1.0);
                let y = rect.bottom() - 6.0 - norm * (rect.height() - 14.0);
                Pos2::new(x, y)
            })
            .collect();
        let pts = catmull_rom_smooth(&raw_pts, 4, rect.top() + 4.0, rect.bottom() - 4.0);
        painter.add(egui::Shape::line(
            pts.clone(),
            Stroke::new(
                4.5_f32,
                Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 28),
            ),
        ));
        painter.add(egui::Shape::line(pts.clone(), Stroke::new(1.8_f32, color)));
        if let Some(&tip) = pts.last() {
            painter.circle_filled(tip, 2.8, TEXT_1);
        }
    };

    draw_series(rx_vals, ACCENT_CYAN, "rx");
    draw_series(tx_vals, ACCENT_VIOLET, "tx");

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
    local_node_id: &str,
    local_hostname: &str,
    local_cpu_pct: f32,
    nodes: &[RemoteNodeInfo],
    hub_latency_ms: Option<u64>,
    selected_node: &mut Option<String>,
) {
    let w = ui.available_width().max(260.0);
    let h = 330.0;
    let (rect, _) = ui.allocate_exact_size(Vec2::new(w, h), Sense::hover());
    let painter = ui.painter_at(rect);

    painter.rect(
        rect,
        Rounding::same(12.0),
        BG_TILE,
        Stroke::new(1.0_f32, BORDER_SUBTLE),
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
    let hub_pos = Pos2::new(center.x, center.y);

    // Core Tri-VM Ring (inner orbit)
    let inner_rx = (w * 0.16).clamp(80.0, 130.0);
    let core_vms = [
        ("my-mac", "Ingress", Pos2::new(center.x - inner_rx, center.y - 42.0), ACCENT_EMERALD),
        ("container-of-souls", "Failover", Pos2::new(center.x + inner_rx, center.y - 42.0), ACCENT_CYAN),
        ("prometheus", "Metrics", Pos2::new(center.x, center.y + 58.0), ACCENT_VIOLET),
    ];

    let time = ui.input(|i| i.time) as f32;

    for (idx, (name, role, pos, color)) in core_vms.iter().enumerate() {
        painter.line_segment(
            [hub_pos, *pos],
            Stroke::new(1.5_f32, Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 95)),
        );
        // Animated telemetry packet pulse along link
        let t = (time * 0.55 + idx as f32 * 0.33).fract();
        let pulse_pos = Pos2::new(
            hub_pos.x + (pos.x - hub_pos.x) * t,
            hub_pos.y + (pos.y - hub_pos.y) * t,
        );
        painter.circle_filled(pulse_pos, 3.0, *color);

        painter.circle_filled(
            *pos,
            21.0,
            Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 26),
        );
        painter.circle_stroke(*pos, 21.0, Stroke::new(1.2_f32, *color));
        painter.text(
            Pos2::new(pos.x, pos.y - 4.0),
            egui::Align2::CENTER_CENTER,
            *name,
            FontId::monospace(9.0),
            TEXT_1,
        );
        painter.text(
            Pos2::new(pos.x, pos.y + 9.0),
            egui::Align2::CENTER_CENTER,
            *role,
            FontId::monospace(8.5),
            *color,
        );
    }

    // Outer Orbit: Deduplicated Local Node + Remote Nodes
    let outer_items =
        build_topology_outer_items(local_node_id, local_hostname, local_cpu_pct, nodes);

    let total_outer = outer_items.len().max(1);
    let safe_bounds = rect.shrink2(Vec2::new(62.0, 24.0));
    for (i, (label, sub, online, node_id_opt, is_this_pc)) in outer_items.iter().enumerate() {
        let angle = (i as f32 / total_outer as f32) * 2.0 * PI - PI * 0.5;
        let rx = (w * 0.41).clamp(120.0, 330.0);
        let ry = (h * 0.39).clamp(112.0, 132.0);
        let pos = Pos2::new(
            (center.x + angle.cos() * rx).clamp(safe_bounds.left(), safe_bounds.right()),
            (center.y + angle.sin() * ry).clamp(safe_bounds.top(), safe_bounds.bottom()),
        );
        let color = if *is_this_pc {
            ACCENT_CYAN
        } else if *online {
            ACCENT_EMERALD
        } else {
            ACCENT_CRIMSON
        };

        painter.line_segment(
            [hub_pos, pos],
            Stroke::new(1.0_f32, Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 65)),
        );

        let node_rect = Rect::from_center_size(pos, Vec2::new(108.0, 34.0));
        let is_sel = node_id_opt.is_some() && *selected_node == *node_id_opt;
        if let Some(ref nid) = node_id_opt {
            let resp = ui.interact(node_rect, ui.id().with(nid), Sense::click());
            if resp.clicked() {
                *selected_node = Some(nid.clone());
            }
        }

        painter.rect(
            node_rect,
            Rounding::same(10.0),
            BG_CARD,
            Stroke::new(if is_sel { 1.8_f32 } else { 1.0_f32 }, color),
        );
        painter.text(
            Pos2::new(node_rect.center().x, node_rect.center().y - 6.0),
            egui::Align2::CENTER_CENTER,
            label,
            FontId::monospace(9.0),
            TEXT_1,
        );
        painter.text(
            Pos2::new(node_rect.center().x, node_rect.center().y + 7.0),
            egui::Align2::CENTER_CENTER,
            sub,
            FontId::monospace(8.0),
            color,
        );
    }

    // Central Hub Node
    let hub_color = if hub_latency_ms.is_some() {
        TEXT_1
    } else {
        ACCENT_AMBER
    };
    painter.circle_filled(hub_pos, 26.0, BG_CARD);
    painter.circle_stroke(hub_pos, 26.0, Stroke::new(1.8_f32, hub_color));
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

fn build_topology_outer_items(
    local_node_id: &str,
    local_hostname: &str,
    local_cpu_pct: f32,
    nodes: &[RemoteNodeInfo],
) -> Vec<(String, String, bool, Option<String>, bool)> {
    let mut outer_items: Vec<(String, String, bool, Option<String>, bool)> = Vec::new();
    let mut local_seen_in_remote = false;

    for n in nodes.iter().take(10) {
        let is_this_pc = (!local_node_id.is_empty() && n.node_id == local_node_id)
            || (!local_hostname.is_empty() && n.hostname.eq_ignore_ascii_case(local_hostname));
        if is_this_pc {
            if local_seen_in_remote {
                continue;
            }
            local_seen_in_remote = true;
        }
        let cpu = n
            .telemetry
            .as_ref()
            .and_then(|t| t.get("cpu"))
            .and_then(|c| c.get("usage_pct"))
            .and_then(|v| v.as_f64())
            .map(|v| format!("{} • {:.0}%", n.os, v))
            .unwrap_or_else(|| n.os.clone());
        let label = if is_this_pc {
            format!("{} (THIS)", truncate_label(&n.hostname, 10))
        } else {
            truncate_label(&n.hostname, 12)
        };
        outer_items.push((
            label,
            cpu,
            n.status == "online",
            Some(n.node_id.clone()),
            is_this_pc,
        ));
    }

    if !local_seen_in_remote {
        outer_items.insert(
            0,
            (
                format!("{} (THIS)", truncate_label(local_hostname, 10)),
                format!("CPU {:.0}%", local_cpu_pct),
                true,
                None,
                true,
            ),
        );
    }

    outer_items
}

#[cfg(test)]
mod tests {
    use super::*;

    fn mk_node(id: &str, hostname: &str) -> RemoteNodeInfo {
        RemoteNodeInfo {
            node_id: id.to_string(),
            hostname: hostname.to_string(),
            os: "windows".to_string(),
            arch: "x86_64".to_string(),
            status: "online".to_string(),
            last_seen_secs: 2,
            telemetry: None,
            models: vec![],
        }
    }

    #[test]
    fn topology_deduplicates_this_pc_when_pin_unset_and_set() {
        // PIN unset -> remote nodes empty -> local PC appears exactly once
        let items_no_pin = build_topology_outer_items("win-1", "POTATO-PRO", 12.0, &[]);
        assert_eq!(items_no_pin.len(), 1);
        assert!(items_no_pin[0].4);
        assert!(items_no_pin[0].0.contains("(THIS)"));

        // PIN set -> remote nodes include local PC + peer (+ stale duplicate) -> local PC still appears once
        let remote = vec![
            mk_node("win-1", "POTATO-PRO"),
            mk_node("lin-2", "rida-260-P0"),
            mk_node("win-stale", "potato-pro"),
        ];
        let items_with_pin = build_topology_outer_items("win-1", "POTATO-PRO", 42.0, &remote);
        let this_count = items_with_pin.iter().filter(|item| item.4).count();
        assert_eq!(this_count, 1);
        assert_eq!(items_with_pin.len(), 2);
    }
}

