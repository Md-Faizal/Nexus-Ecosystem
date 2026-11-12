use eframe::egui::{Align2, Color32, FontId, Painter, Pos2, Rect, Rounding, Stroke, Vec2};
use crate::gui::theme::*;

pub struct BusLink {
    pub from: Pos2,
    pub to: Pos2,
    pub color: Color32,
    pub thickness: f32,
    pub is_active: bool,
    pub tx_bytes_sec: u64,
    pub rx_bytes_sec: u64,
    pub latency_ms: Option<u64>,
}

pub fn render_can_backbone_trunk(
    painter: &Painter,
    left_x: f32,
    right_x: f32,
    y: f32,
    bus_name: &str,
) {
    let wire_spacing = 3.5;

    // CAN_H line (High-speed differential)
    painter.line_segment(
        [Pos2::new(left_x, y - wire_spacing), Pos2::new(right_x, y - wire_spacing)],
        Stroke::new(1.4_f32, Color32::from_rgba_unmultiplied(56, 189, 248, 140)), // ACCENT_CYAN
    );

    // CAN_L line (Differential pair return)
    painter.line_segment(
        [Pos2::new(left_x, y + wire_spacing), Pos2::new(right_x, y + wire_spacing)],
        Stroke::new(1.4_f32, Color32::from_rgba_unmultiplied(16, 185, 129, 130)), // ACCENT_EMERALD
    );

    // Left 120Ω Termination Resistor
    render_termination_resistor(painter, Pos2::new(left_x, y), true);

    // Right 120Ω Termination Resistor
    render_termination_resistor(painter, Pos2::new(right_x, y), false);

    // Bus Center Brand Marker
    let center_x = (left_x + right_x) * 0.5;
    let badge_rect = Rect::from_center_size(Pos2::new(center_x, y), Vec2::new(130.0, 16.0));
    painter.rect(
        badge_rect,
        Rounding::same(4.0),
        BG_TILE,
        Stroke::new(1.0_f32, BORDER_STRONG),
    );
    painter.text(
        badge_rect.center(),
        Align2::CENTER_CENTER,
        bus_name,
        FontId::monospace(8.5),
        TEXT_2,
    );
}

fn render_termination_resistor(painter: &Painter, pos: Pos2, is_left: bool) {
    let w = 24.0;
    let h = 18.0;
    let rect = Rect::from_center_size(pos, Vec2::new(w, h));

    painter.rect(
        rect,
        Rounding::same(3.0),
        BG_CARD,
        Stroke::new(1.0_f32, ACCENT_AMBER),
    );

    painter.text(
        rect.center(),
        Align2::CENTER_CENTER,
        "120Ω",
        FontId::monospace(7.5),
        ACCENT_AMBER,
    );

    let tag_pos = if is_left {
        Pos2::new(pos.x - 18.0, pos.y)
    } else {
        Pos2::new(pos.x + 18.0, pos.y)
    };

    painter.text(
        tag_pos,
        if is_left { Align2::RIGHT_CENTER } else { Align2::LEFT_CENTER },
        "TERM",
        FontId::monospace(7.0),
        TEXT_3,
    );
}

pub fn render_can_drop_cable(
    painter: &Painter,
    trunk_x: f32,
    trunk_y: f32,
    node_pos: Pos2,
    color: Color32,
    show_metric: bool,
    tx_sec: u64,
    rx_sec: u64,
) {
    let wire_col = Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 90);

    // Tee junction on trunk
    painter.circle_filled(Pos2::new(trunk_x, trunk_y), 3.0, color);

    // Routed orthogonal or diagonal drop
    painter.line_segment(
        [Pos2::new(trunk_x, trunk_y), node_pos],
        Stroke::new(1.2_f32, wire_col),
    );

    if show_metric && (tx_sec > 0 || rx_sec > 0) {
        let mid = Pos2::new((trunk_x + node_pos.x) * 0.5, (trunk_y + node_pos.y) * 0.5);
        let text = format!("▲{} ▼{}", format_rate(tx_sec), format_rate(rx_sec));
        let rect = Rect::from_center_size(mid, Vec2::new(74.0, 13.0));
        painter.rect(
            rect,
            Rounding::same(3.0),
            BG_VOID,
            Stroke::new(0.8_f32, BORDER_SUBTLE),
        );
        painter.text(
            rect.center(),
            Align2::CENTER_CENTER,
            text,
            FontId::monospace(7.5),
            TEXT_2,
        );
    }
}

pub fn render_mesh_link(
    painter: &Painter,
    from: Pos2,
    to: Pos2,
    color: Color32,
    thickness: f32,
    show_metric: bool,
    latency_ms: Option<u64>,
    tx_sec: u64,
    rx_sec: u64,
) {
    let stroke_col = Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 75);
    painter.line_segment([from, to], Stroke::new(thickness, stroke_col));

    if show_metric {
        let mid = Pos2::new((from.x + to.x) * 0.5, (from.y + to.y) * 0.5);
        let label = if let Some(ms) = latency_ms {
            format!("{}ms", ms)
        } else if tx_sec > 0 || rx_sec > 0 {
            format!("▲{} ▼{}", format_rate(tx_sec), format_rate(rx_sec))
        } else {
            String::new()
        };

        if !label.is_empty() {
            let rect = Rect::from_center_size(mid, Vec2::new(48.0, 13.0));
            painter.rect(
                rect,
                Rounding::same(3.0),
                BG_VOID,
                Stroke::new(0.8_f32, BORDER_SUBTLE),
            );
            painter.text(
                rect.center(),
                Align2::CENTER_CENTER,
                label,
                FontId::monospace(7.5),
                color,
            );
        }
    }
}

fn format_rate(bytes: u64) -> String {
    if bytes >= 1024 * 1024 {
        format!("{:.1}M", bytes as f32 / (1024.0 * 1024.0))
    } else if bytes >= 1024 {
        format!("{:.0}K", bytes as f32 / 1024.0)
    } else {
        format!("{}B", bytes)
    }
}

