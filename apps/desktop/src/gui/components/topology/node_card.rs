use eframe::egui::{Align2, Color32, FontId, Painter, Pos2, Rect, Rounding, Sense, Stroke, Ui, Vec2};
use crate::gui::theme::*;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TopologyRole {
    Hub,
    Failover,
    Quorum,
    AiCompute,
    Workstation,
    Ambient,
}

impl TopologyRole {
    pub fn badge_label(&self) -> &'static str {
        match self {
            TopologyRole::Hub => "INGRESS HUB",
            TopologyRole::Failover => "FAILOVER",
            TopologyRole::Quorum => "QUORUM",
            TopologyRole::AiCompute => "AI COMPUTE",
            TopologyRole::Workstation => "WORKSTATION",
            TopologyRole::Ambient => "AMBIENT EDGE",
        }
    }

    pub fn accent_color(&self) -> Color32 {
        match self {
            TopologyRole::Hub => ACCENT_EMERALD,
            TopologyRole::Failover => ACCENT_CYAN,
            TopologyRole::Quorum => ACCENT_VIOLET,
            TopologyRole::AiCompute => ACCENT_AMBER,
            TopologyRole::Workstation => ACCENT_CYAN,
            TopologyRole::Ambient => Color32::from_rgb(148, 163, 184),
        }
    }
}

pub struct RenderNodeParams<'a> {
    pub id: &'a str,
    pub hostname: &'a str,
    pub role: TopologyRole,
    pub os: &'a str,
    pub arch: &'a str,
    pub is_online: bool,
    pub is_this_pc: bool,
    pub cpu_pct: f32,
    pub ram_pct: f32,
    pub vram_pct: Option<f32>,
    pub models_count: usize,
    pub rx_bytes_sec: u64,
    pub tx_bytes_sec: u64,
    pub pos: Pos2,
    pub is_selected: bool,
}

pub fn render_node_micro_hud(
    ui: &mut Ui,
    painter: &Painter,
    node: &RenderNodeParams,
    selected_node: &mut Option<String>,
) {
    let w = if node.role == TopologyRole::Hub { 144.0 } else { 136.0 };
    let h = if node.role == TopologyRole::Hub { 68.0 } else { 62.0 };
    let rect = Rect::from_center_size(node.pos, Vec2::new(w, h));

    // Handle clicks
    let resp = ui.interact(rect, ui.id().with(node.id), Sense::click());
    if resp.clicked() {
        *selected_node = Some(node.id.to_string());
    }

    let is_hovered = resp.hovered();
    let status_color = if !node.is_online {
        ACCENT_CRIMSON
    } else if node.cpu_pct > 85.0 {
        ACCENT_AMBER
    } else {
        node.role.accent_color()
    };

    // Selection outer glow
    if node.is_selected {
        painter.rect(
            rect.expand(3.0),
            Rounding::same(12.0),
            Color32::from_rgba_unmultiplied(status_color.r(), status_color.g(), status_color.b(), 35),
            Stroke::NONE,
        );
    }

    // Node background card
    painter.rect(
        rect,
        Rounding::same(9.0),
        if is_hovered { BG_ELEVATED } else { BG_CARD },
        Stroke::new(
            if node.is_selected {
                2.0_f32
            } else if is_hovered {
                1.5_f32
            } else {
                1.0_f32
            },
            if node.is_selected {
                status_color
            } else if is_hovered {
                BORDER_STRONG
            } else {
                BORDER_SUBTLE
            },
        ),
    );

    // Left status strip
    let strip_rect = Rect::from_min_size(rect.min, Vec2::new(3.5, h));
    painter.rect_filled(
        strip_rect,
        Rounding {
            nw: 9.0,
            sw: 9.0,
            ne: 0.0,
            se: 0.0,
        },
        status_color,
    );

    // Header: Role Badge + Status Dot
    let header_y = rect.top() + 10.0;
    let badge_text = if node.is_this_pc {
        format!("{} • THIS", node.role.badge_label())
    } else {
        node.role.badge_label().to_string()
    };
    painter.text(
        Pos2::new(rect.left() + 12.0, header_y),
        Align2::LEFT_CENTER,
        badge_text,
        FontId::monospace(7.5),
        status_color,
    );

    // Status beacon dot
    let dot_pos = Pos2::new(rect.right() - 10.0, header_y);
    painter.circle_filled(
        dot_pos,
        3.0,
        if node.is_online { ACCENT_EMERALD } else { ACCENT_CRIMSON },
    );

    // Hostname
    let name_y = rect.top() + 25.0;
    let display_name = if node.hostname.len() > 14 {
        format!("{}…", &node.hostname[..13])
    } else {
        node.hostname.to_string()
    };
    painter.text(
        Pos2::new(rect.left() + 12.0, name_y),
        Align2::LEFT_CENTER,
        display_name,
        FontId::proportional(11.5),
        TEXT_1,
    );

    // Mini Gauges: CPU & RAM Bars
    let bar_y = rect.top() + 41.0;
    let bar_w = (w - 24.0) * 0.46;
    let bar_h = 3.5;

    // CPU Bar
    render_mini_gauge(
        painter,
        Pos2::new(rect.left() + 12.0, bar_y),
        bar_w,
        bar_h,
        "CPU",
        node.cpu_pct / 100.0,
        ACCENT_CYAN,
    );

    // RAM Bar
    render_mini_gauge(
        painter,
        Pos2::new(rect.left() + 16.0 + bar_w, bar_y),
        bar_w,
        bar_h,
        "RAM",
        node.ram_pct / 100.0,
        ACCENT_VIOLET,
    );

    // Footer: Subtitle / Model pill / Network indicator
    let footer_y = rect.top() + 52.0;
    if node.models_count > 0 {
        painter.text(
            Pos2::new(rect.left() + 12.0, footer_y),
            Align2::LEFT_CENTER,
            format!("⚡ {} Models Loaded", node.models_count),
            FontId::monospace(7.5),
            ACCENT_AMBER,
        );
    } else {
        painter.text(
            Pos2::new(rect.left() + 12.0, footer_y),
            Align2::LEFT_CENTER,
            format!("{}/{}", node.os, node.arch),
            FontId::monospace(7.5),
            TEXT_3,
        );
    }
}

fn render_mini_gauge(
    painter: &Painter,
    pos: Pos2,
    width: f32,
    height: f32,
    label: &str,
    fill_ratio: f32,
    color: Color32,
) {
    let track_rect = Rect::from_min_size(pos, Vec2::new(width, height));
    painter.rect_filled(track_rect, Rounding::same(1.5), Color32::from_rgb(24, 24, 32));

    let fill_w = (width * fill_ratio.clamp(0.0, 1.0)).max(1.0);
    let fill_rect = Rect::from_min_size(pos, Vec2::new(fill_w, height));
    painter.rect_filled(fill_rect, Rounding::same(1.5), color);

    // Tiny numeric label above bar
    painter.text(
        Pos2::new(pos.x, pos.y - 4.5),
        Align2::LEFT_CENTER,
        format!("{} {:.0}%", label, fill_ratio * 100.0),
        FontId::monospace(6.5),
        TEXT_2,
    );
}
