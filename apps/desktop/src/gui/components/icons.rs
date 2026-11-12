use crate::gui::theme::*;
use eframe::egui;
use egui::{Color32, FontId, Pos2, Rect, Rounding, Sense, Stroke, Vec2};
use std::f32::consts::PI;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum IconKind {
    Overview,
    Telemetry,
    Cluster,
    AiStudio,
    Diagnostics,
    Close,
    Settings,
    Command,
    Refresh,
    Play,
    Stop,
    Download,
    External,
    Plus,
    Trash,
    ChevronLeft,
    ChevronRight,
    Send,
    Sliders,
    Bolt,
}

/// Paints a crisp, DPI-independent vector icon inside `rect`.
pub fn paint_icon(painter: &egui::Painter, rect: Rect, kind: IconKind, color: Color32) {
    let c = rect.center();
    let r = (rect.width().min(rect.height()) * 0.38).max(3.5);
    let stroke = Stroke::new(1.5_f32, color);
    let thin = Stroke::new(1.25_f32, color);

    match kind {
        IconKind::Close => {
            let d = r * 0.85;
            painter.line_segment([Pos2::new(c.x - d, c.y - d), Pos2::new(c.x + d, c.y + d)], stroke);
            painter.line_segment([Pos2::new(c.x + d, c.y - d), Pos2::new(c.x - d, c.y + d)], stroke);
        }
        IconKind::Overview => {
            let gap = 1.6_f32;
            let cell = (r - gap * 0.5).max(2.0);
            let tl = Rect::from_min_size(Pos2::new(c.x - r, c.y - r), Vec2::splat(cell));
            let tr = Rect::from_min_size(Pos2::new(c.x + gap * 0.5, c.y - r), Vec2::splat(cell));
            let bl = Rect::from_min_size(Pos2::new(c.x - r, c.y + gap * 0.5), Vec2::splat(cell));
            let br = Rect::from_min_size(Pos2::new(c.x + gap * 0.5, c.y + gap * 0.5), Vec2::splat(cell));
            for box_r in [tl, tr, bl, br] {
                painter.rect_stroke(box_r, Rounding::same(1.5), thin);
            }
        }
        IconKind::Telemetry => {
            let pts = vec![
                Pos2::new(c.x - r, c.y + r * 0.1),
                Pos2::new(c.x - r * 0.45, c.y + r * 0.1),
                Pos2::new(c.x - r * 0.18, c.y - r * 0.75),
                Pos2::new(c.x + r * 0.18, c.y + r * 0.75),
                Pos2::new(c.x + r * 0.48, c.y - r * 0.1),
                Pos2::new(c.x + r, c.y - r * 0.1),
            ];
            painter.add(egui::Shape::line(pts, stroke));
        }
        IconKind::Cluster => {
            let pts: Vec<Pos2> = (0..6)
                .map(|i| {
                    let a = (i as f32 * 60.0 - 90.0).to_radians();
                    Pos2::new(c.x + a.cos() * r, c.y + a.sin() * r)
                })
                .collect();
            let mut closed = pts.clone();
            closed.push(pts[0]);
            painter.add(egui::Shape::line(closed, thin));
            painter.circle_filled(c, 2.0, color);
        }
        IconKind::AiStudio => {
            let pts = vec![
                Pos2::new(c.x, c.y - r),
                Pos2::new(c.x + r * 0.28, c.y - r * 0.28),
                Pos2::new(c.x + r, c.y),
                Pos2::new(c.x + r * 0.28, c.y + r * 0.28),
                Pos2::new(c.x, c.y + r),
                Pos2::new(c.x - r * 0.28, c.y + r * 0.28),
                Pos2::new(c.x - r, c.y),
                Pos2::new(c.x - r * 0.28, c.y - r * 0.28),
                Pos2::new(c.x, c.y - r),
            ];
            painter.add(egui::Shape::line(pts, thin));
        }
        IconKind::Diagnostics | IconKind::Bolt => {
            let pts = vec![
                Pos2::new(c.x + r * 0.15, c.y - r),
                Pos2::new(c.x - r * 0.55, c.y + r * 0.08),
                Pos2::new(c.x + r * 0.05, c.y + r * 0.08),
                Pos2::new(c.x - r * 0.15, c.y + r),
                Pos2::new(c.x + r * 0.55, c.y - r * 0.08),
                Pos2::new(c.x - r * 0.05, c.y - r * 0.08),
                Pos2::new(c.x + r * 0.15, c.y - r),
            ];
            painter.add(egui::Shape::line(pts, thin));
        }
        IconKind::Settings => {
            painter.circle_stroke(c, r * 0.42, thin);
            for i in 0..8 {
                let a = (i as f32 * 45.0).to_radians();
                let p1 = Pos2::new(c.x + a.cos() * r * 0.65, c.y + a.sin() * r * 0.65);
                let p2 = Pos2::new(c.x + a.cos() * r * 0.98, c.y + a.sin() * r * 0.98);
                painter.line_segment([p1, p2], stroke);
            }
        }
        IconKind::Command => {
            // Magnifying glass / search-command icon
            let glass_c = Pos2::new(c.x - r * 0.15, c.y - r * 0.15);
            painter.circle_stroke(glass_c, r * 0.58, stroke);
            let h1 = Pos2::new(glass_c.x + r * 0.42, glass_c.y + r * 0.42);
            let h2 = Pos2::new(c.x + r * 0.9, c.y + r * 0.9);
            painter.line_segment([h1, h2], stroke);
        }
        IconKind::Refresh => {
            let start = -0.35_f32 * PI;
            let end = 1.35_f32 * PI;
            let arc_pts: Vec<Pos2> = (0..=20)
                .map(|i| {
                    let a = start + (end - start) * (i as f32 / 20.0);
                    Pos2::new(c.x + a.cos() * r * 0.85, c.y + a.sin() * r * 0.85)
                })
                .collect();
            if let Some(&tip) = arc_pts.first() {
                painter.line_segment(
                    [tip, Pos2::new(tip.x - r * 0.35, tip.y - r * 0.05)],
                    thin,
                );
                painter.line_segment(
                    [tip, Pos2::new(tip.x + r * 0.05, tip.y - r * 0.38)],
                    thin,
                );
            }
            painter.add(egui::Shape::line(arc_pts, stroke));
        }
        IconKind::Play => {
            let pts = vec![
                Pos2::new(c.x - r * 0.45, c.y - r * 0.72),
                Pos2::new(c.x + r * 0.72, c.y),
                Pos2::new(c.x - r * 0.45, c.y + r * 0.72),
            ];
            painter.add(egui::Shape::convex_polygon(pts, color, Stroke::NONE));
        }
        IconKind::Stop => {
            let sq = Rect::from_center_size(c, Vec2::splat(r * 1.15));
            painter.rect_filled(sq, Rounding::same(2.0), color);
        }
        IconKind::Download => {
            painter.line_segment(
                [Pos2::new(c.x, c.y - r * 0.85), Pos2::new(c.x, c.y + r * 0.35)],
                stroke,
            );
            painter.line_segment(
                [Pos2::new(c.x - r * 0.45, c.y - r * 0.05), Pos2::new(c.x, c.y + r * 0.4)],
                stroke,
            );
            painter.line_segment(
                [Pos2::new(c.x + r * 0.45, c.y - r * 0.05), Pos2::new(c.x, c.y + r * 0.4)],
                stroke,
            );
            painter.line_segment(
                [Pos2::new(c.x - r * 0.75, c.y + r * 0.8), Pos2::new(c.x + r * 0.75, c.y + r * 0.8)],
                stroke,
            );
        }
        IconKind::External => {
            let box_r = Rect::from_min_max(
                Pos2::new(c.x - r * 0.85, c.y - r * 0.4),
                Pos2::new(c.x + r * 0.4, c.y + r * 0.85),
            );
            painter.rect_stroke(box_r, Rounding::same(2.0), thin);
            let tip = Pos2::new(c.x + r * 0.85, c.y - r * 0.85);
            painter.line_segment([Pos2::new(c.x - r * 0.1, c.y + r * 0.1), tip], stroke);
            painter.line_segment([tip, Pos2::new(tip.x - r * 0.55, tip.y)], stroke);
            painter.line_segment([tip, Pos2::new(tip.x, tip.y + r * 0.55)], stroke);
        }
        IconKind::Plus => {
            let d = r * 0.8;
            painter.line_segment([Pos2::new(c.x - d, c.y), Pos2::new(c.x + d, c.y)], stroke);
            painter.line_segment([Pos2::new(c.x, c.y - d), Pos2::new(c.x, c.y + d)], stroke);
        }
        IconKind::Trash => {
            painter.line_segment(
                [Pos2::new(c.x - r * 0.7, c.y - r * 0.55), Pos2::new(c.x + r * 0.7, c.y - r * 0.55)],
                stroke,
            );
            let body = Rect::from_min_max(
                Pos2::new(c.x - r * 0.5, c.y - r * 0.5),
                Pos2::new(c.x + r * 0.5, c.y + r * 0.85),
            );
            painter.rect_stroke(body, Rounding::same(1.5), thin);
        }
        IconKind::ChevronLeft => {
            painter.line_segment(
                [Pos2::new(c.x + r * 0.35, c.y - r * 0.65), Pos2::new(c.x - r * 0.35, c.y)],
                stroke,
            );
            painter.line_segment(
                [Pos2::new(c.x - r * 0.35, c.y), Pos2::new(c.x + r * 0.35, c.y + r * 0.65)],
                stroke,
            );
        }
        IconKind::ChevronRight => {
            painter.line_segment(
                [Pos2::new(c.x - r * 0.35, c.y - r * 0.65), Pos2::new(c.x + r * 0.35, c.y)],
                stroke,
            );
            painter.line_segment(
                [Pos2::new(c.x + r * 0.35, c.y), Pos2::new(c.x - r * 0.35, c.y + r * 0.65)],
                stroke,
            );
        }
        IconKind::Send => {
            let tip = Pos2::new(c.x, c.y - r * 0.8);
            painter.line_segment([Pos2::new(c.x, c.y + r * 0.8), tip], stroke);
            painter.line_segment([tip, Pos2::new(c.x - r * 0.5, c.y - r * 0.25)], stroke);
            painter.line_segment([tip, Pos2::new(c.x + r * 0.5, c.y - r * 0.25)], stroke);
        }
        IconKind::Sliders => {
            for (dy, knob_x) in [(-r * 0.55, c.x - r * 0.25), (0.0, c.x + r * 0.3), (r * 0.55, c.x - r * 0.1)] {
                painter.line_segment(
                    [Pos2::new(c.x - r * 0.85, c.y + dy), Pos2::new(c.x + r * 0.85, c.y + dy)],
                    thin,
                );
                painter.circle_filled(Pos2::new(knob_x, c.y + dy), 2.1, color);
            }
        }
    }
}

/// Square/circular icon-only button with smooth hover background.
pub fn icon_button(
    ui: &mut egui::Ui,
    kind: IconKind,
    size: f32,
    color: Color32,
    tooltip: &str,
) -> egui::Response {
    let (rect, resp) = ui.allocate_exact_size(Vec2::splat(size), Sense::click());
    let hover_t = ui.ctx().animate_bool_with_time(resp.id, resp.hovered(), 0.12);
    let bg = Color32::from_rgba_unmultiplied(255, 255, 255, (8.0 + hover_t * 18.0) as u8);
    let border = if resp.hovered() {
        BORDER_STRONG
    } else {
        BORDER_SUBTLE
    };
    ui.painter()
        .rect(rect, Rounding::same(8.0), bg, Stroke::new(1.0_f32, border));
    paint_icon(ui.painter(), rect.shrink(size * 0.18), kind, if resp.hovered() { TEXT_1 } else { color });
    if !tooltip.is_empty() {
        resp.clone().on_hover_text(tooltip)
    } else {
        resp
    }
}

/// Action button with a crisp left vector icon and label.
pub fn icon_label_button(ui: &mut egui::Ui, kind: IconKind, label: &str) -> egui::Response {
    styled_icon_button(
        ui,
        kind,
        label,
        TEXT_1,
        Color32::from_rgba_unmultiplied(255, 255, 255, 10),
        BORDER_STRONG,
    )
}

pub fn accent_icon_button(
    ui: &mut egui::Ui,
    kind: IconKind,
    label: &str,
    color: Color32,
) -> egui::Response {
    let fill = Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 24);
    let stroke = Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 90);
    styled_icon_button(ui, kind, label, color, fill, stroke)
}

pub fn primary_icon_button(ui: &mut egui::Ui, kind: IconKind, label: &str) -> egui::Response {
    styled_icon_button(ui, kind, label, BG_VOID, TEXT_1, TEXT_1)
}

fn styled_icon_button(
    ui: &mut egui::Ui,
    kind: IconKind,
    label: &str,
    fg: Color32,
    bg: Color32,
    border: Color32,
) -> egui::Response {
    let font = FontId::proportional(12.0);
    let galley = ui.painter().layout_no_wrap(label.to_string(), font, fg);
    let icon_sz = 14.0_f32;
    let pad_x = 12.0_f32;
    let gap = 7.0_f32;
    let total_w = pad_x * 2.0 + icon_sz + gap + galley.size().x;
    let (rect, resp) = ui.allocate_exact_size(Vec2::new(total_w, 30.0), Sense::click());

    let hover_t = ui.ctx().animate_bool_with_time(resp.id, resp.hovered(), 0.12);
    let final_bg = if bg == TEXT_1 {
        TEXT_1
    } else {
        Color32::from_rgba_unmultiplied(
            bg.r(),
            bg.g(),
            bg.b(),
            (bg.a() as f32 + hover_t * 16.0).min(255.0) as u8,
        )
    };
    ui.painter()
        .rect(rect, Rounding::same(8.0), final_bg, Stroke::new(1.0_f32, border));

    let icon_rect = Rect::from_center_size(
        Pos2::new(rect.left() + pad_x + icon_sz * 0.5, rect.center().y),
        Vec2::splat(icon_sz),
    );
    paint_icon(ui.painter(), icon_rect, kind, fg);

    let text_pos = Pos2::new(
        rect.left() + pad_x + icon_sz + gap,
        rect.center().y - galley.size().y * 0.5,
    );
    ui.painter().galley(text_pos, galley, fg);
    resp
}

