use eframe::egui;
use egui::{Color32, FontId, Frame, Margin, Rounding, Sense, Stroke, Vec2};

// ─── Obsidian Flight Deck 2.0 Color Tokens ───
pub const BG_VOID: Color32 = Color32::from_rgb(0, 0, 0);
pub const BG_RAIL: Color32 = Color32::from_rgb(7, 7, 10);
pub const BG_CARD: Color32 = Color32::from_rgb(11, 11, 15);
pub const BG_TILE: Color32 = Color32::from_rgb(16, 16, 22);
pub const BG_ROW: Color32 = Color32::from_rgb(14, 14, 19);
pub const BG_ELEVATED: Color32 = Color32::from_rgb(20, 20, 27);
pub const BG_SHEET: Color32 = Color32::from_rgb(9, 9, 12);

pub const BORDER_SUBTLE: Color32 = Color32::from_rgb(26, 26, 33);
pub const BORDER_STRONG: Color32 = Color32::from_rgb(48, 48, 58);
pub const BORDER_FOCUS: Color32 = Color32::from_rgb(86, 86, 104);

pub const TEXT_1: Color32 = Color32::from_rgb(250, 250, 252);
pub const TEXT_2: Color32 = Color32::from_rgb(161, 161, 175);
pub const TEXT_3: Color32 = Color32::from_rgb(92, 92, 106);

// ─── Surgical Semantic Accents ───
pub const ACCENT_EMERALD: Color32 = Color32::from_rgb(16, 185, 129);
pub const ACCENT_CYAN: Color32 = Color32::from_rgb(56, 189, 248);
pub const ACCENT_VIOLET: Color32 = Color32::from_rgb(167, 139, 250);
pub const ACCENT_AMBER: Color32 = Color32::from_rgb(245, 158, 11);
pub const ACCENT_CRIMSON: Color32 = Color32::from_rgb(239, 68, 68);

/// Responsive layout breakpoints computed from viewport width.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LayoutTier {
    /// < 840px: Icon-only rail or bottom bar, 1-column cards, stacked tiles
    Compact,
    /// 840px .. 1260px: Standard rail, 2-column cards, 2x2 tiles
    Medium,
    /// >= 1260px: Full rail, multi-column mission control grids
    Wide,
}

impl LayoutTier {
    pub fn from_width(width: f32) -> Self {
        if width < 840.0 {
            Self::Compact
        } else if width < 1260.0 {
            Self::Medium
        } else {
            Self::Wide
        }
    }

    pub fn is_compact(self) -> bool {
        matches!(self, Self::Compact)
    }
}

pub fn configure_obsidian_theme(ctx: &egui::Context) {
    let mut visuals = egui::Visuals::dark();
    visuals.dark_mode = true;
    visuals.override_text_color = Some(TEXT_1);
    visuals.panel_fill = BG_VOID;
    visuals.window_fill = BG_SHEET;
    visuals.window_stroke = Stroke::new(1.0, BORDER_STRONG);
    visuals.window_rounding = Rounding::same(14.0);
    visuals.extreme_bg_color = BG_VOID;
    visuals.faint_bg_color = BG_TILE;

    visuals.selection.bg_fill = Color32::from_rgba_unmultiplied(56, 189, 248, 42);
    visuals.selection.stroke = Stroke::new(1.0, ACCENT_CYAN);

    visuals.widgets.noninteractive.bg_fill = BG_CARD;
    visuals.widgets.noninteractive.bg_stroke = Stroke::new(1.0, BORDER_SUBTLE);
    visuals.widgets.noninteractive.rounding = Rounding::same(12.0);

    visuals.widgets.inactive.bg_fill = Color32::from_rgba_unmultiplied(255, 255, 255, 10);
    visuals.widgets.inactive.bg_stroke = Stroke::new(1.0, BORDER_SUBTLE);
    visuals.widgets.inactive.rounding = Rounding::same(8.0);

    visuals.widgets.hovered.bg_fill = Color32::from_rgba_unmultiplied(255, 255, 255, 22);
    visuals.widgets.hovered.bg_stroke = Stroke::new(1.0, BORDER_STRONG);
    visuals.widgets.hovered.rounding = Rounding::same(8.0);

    visuals.widgets.active.bg_fill = Color32::from_rgba_unmultiplied(255, 255, 255, 34);
    visuals.widgets.active.bg_stroke = Stroke::new(1.0, TEXT_1);
    visuals.widgets.active.rounding = Rounding::same(8.0);

    ctx.set_visuals(visuals);
}

pub fn card_frame() -> Frame {
    Frame::none()
        .fill(BG_CARD)
        .stroke(Stroke::new(1.0, BORDER_SUBTLE))
        .rounding(Rounding::same(14.0))
        .inner_margin(Margin::same(20.0))
}

pub fn tile_frame() -> Frame {
    Frame::none()
        .fill(BG_TILE)
        .stroke(Stroke::new(1.0, BORDER_SUBTLE))
        .rounding(Rounding::same(10.0))
        .inner_margin(Margin::symmetric(14.0, 12.0))
}

pub fn row_frame() -> Frame {
    Frame::none()
        .fill(BG_ROW)
        .stroke(Stroke::new(1.0, BORDER_SUBTLE))
        .rounding(Rounding::same(10.0))
        .inner_margin(Margin::symmetric(14.0, 10.0))
}

pub fn render_section_header(
    ui: &mut egui::Ui,
    title: &str,
    right_badge: &str,
    badge_color: Color32,
) {
    ui.horizontal(|ui| {
        ui.label(
            egui::RichText::new(title)
                .font(FontId::monospace(10.5))
                .color(TEXT_2),
        );
        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
            ui.label(
                egui::RichText::new(right_badge)
                    .font(FontId::monospace(10.5))
                    .color(badge_color),
            );
        });
    });
    ui.add_space(6.0);
    let (line_rect, _) = ui.allocate_exact_size(Vec2::new(ui.available_width(), 1.0), Sense::hover());
    ui.painter().line_segment(
        [line_rect.left_center(), line_rect.right_center()],
        Stroke::new(1.0, BORDER_SUBTLE),
    );
    ui.add_space(12.0);
}

pub fn pill_badge(ui: &mut egui::Ui, text: &str, color: Color32) {
    let fill = Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 20);
    let stroke = Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 70);
    Frame::none()
        .fill(fill)
        .stroke(Stroke::new(1.0, stroke))
        .rounding(Rounding::same(20.0))
        .inner_margin(Margin::symmetric(9.0, 3.0))
        .show(ui, |ui| {
            ui.label(
                egui::RichText::new(text)
                    .font(FontId::monospace(9.5))
                    .color(color),
            );
        });
}

pub fn kbd_badge(ui: &mut egui::Ui, keys: &str) {
    Frame::none()
        .fill(Color32::from_rgba_unmultiplied(255, 255, 255, 10))
        .stroke(Stroke::new(1.0, BORDER_STRONG))
        .rounding(Rounding::same(5.0))
        .inner_margin(Margin::symmetric(5.0, 1.5))
        .show(ui, |ui| {
            ui.label(
                egui::RichText::new(keys)
                    .font(FontId::monospace(9.0))
                    .color(TEXT_2),
            );
        });
}

pub fn action_button(ui: &mut egui::Ui, label: &str) -> egui::Response {
    ui.add(
        egui::Button::new(
            egui::RichText::new(label)
                .font(FontId::proportional(12.0))
                .color(TEXT_1),
        )
        .fill(Color32::from_rgba_unmultiplied(255, 255, 255, 10))
        .stroke(Stroke::new(1.0, BORDER_STRONG))
        .rounding(Rounding::same(8.0))
        .min_size(Vec2::new(0.0, 30.0)),
    )
}

pub fn primary_button(ui: &mut egui::Ui, label: &str) -> egui::Response {
    ui.add(
        egui::Button::new(
            egui::RichText::new(label)
                .font(FontId::proportional(12.0))
                .strong()
                .color(BG_VOID),
        )
        .fill(TEXT_1)
        .stroke(Stroke::new(1.0, TEXT_1))
        .rounding(Rounding::same(8.0))
        .min_size(Vec2::new(0.0, 30.0)),
    )
}

pub fn accent_button(ui: &mut egui::Ui, label: &str, color: Color32) -> egui::Response {
    let fill = Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 26);
    let stroke = Color32::from_rgba_unmultiplied(color.r(), color.g(), color.b(), 95);
    ui.add(
        egui::Button::new(
            egui::RichText::new(label)
                .font(FontId::proportional(11.5))
                .color(color),
        )
        .fill(fill)
        .stroke(Stroke::new(1.0, stroke))
        .rounding(Rounding::same(8.0))
        .min_size(Vec2::new(0.0, 28.0)),
    )
}

pub fn render_metric_tile(
    ui: &mut egui::Ui,
    width: f32,
    label: &str,
    value: &str,
    sub: &str,
    accent: Color32,
) {
    tile_frame().show(ui, |ui| {
        ui.set_width((width - 28.0).max(100.0));
        ui.horizontal(|ui| {
            let (dot_rect, _) = ui.allocate_exact_size(Vec2::new(7.0, 7.0), Sense::hover());
            ui.painter().circle_filled(dot_rect.center(), 2.8, accent);
            ui.label(
                egui::RichText::new(label)
                    .font(FontId::monospace(9.5))
                    .color(TEXT_3),
            );
        });
        ui.add_space(4.0);
        ui.label(
            egui::RichText::new(value)
                .font(FontId::proportional(20.0))
                .strong()
                .color(TEXT_1),
        );
        ui.add_space(2.0);
        ui.label(
            egui::RichText::new(sub)
                .font(FontId::monospace(10.0))
                .color(TEXT_2),
        );
    });
}

pub fn render_hairline_bar(
    ui: &mut egui::Ui,
    width: f32,
    label: &str,
    right_label: &str,
    pct: f32,
    color: Color32,
) {
    let clamped = pct.clamp(0.0, 100.0);
    ui.vertical(|ui| {
        ui.set_width(width);
        ui.horizontal(|ui| {
            ui.label(
                egui::RichText::new(label)
                    .font(FontId::monospace(10.0))
                    .color(TEXT_2),
            );
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                ui.label(
                    egui::RichText::new(right_label)
                        .font(FontId::monospace(10.0))
                        .color(TEXT_1),
                );
            });
        });
        ui.add_space(4.0);
        let (bar_rect, _) = ui.allocate_exact_size(Vec2::new(width, 4.0), Sense::hover());
        ui.painter()
            .rect_filled(bar_rect, Rounding::same(2.0), Color32::from_rgb(26, 26, 33));
        if clamped > 0.0 {
            let fill_w = (bar_rect.width() * (clamped / 100.0)).max(3.0);
            let fill_rect = egui::Rect::from_min_size(bar_rect.min, Vec2::new(fill_w, 4.0));
            ui.painter()
                .rect_filled(fill_rect, Rounding::same(2.0), color);
        }
    });
}

pub fn render_toggle_row(ui: &mut egui::Ui, title: &str, subtitle: &str, value: &mut bool) {
    row_frame().show(ui, |ui| {
        ui.horizontal(|ui| {
            ui.vertical(|ui| {
                ui.label(
                    egui::RichText::new(title)
                        .font(FontId::proportional(12.5))
                        .strong()
                        .color(TEXT_1),
                );
                ui.label(
                    egui::RichText::new(subtitle)
                        .font(FontId::proportional(11.0))
                        .color(TEXT_2),
                );
            });

            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                let (rect, resp) = ui.allocate_exact_size(Vec2::new(40.0, 22.0), Sense::click());
                if resp.clicked() {
                    *value = !*value;
                }
                let bg = if *value {
                    ACCENT_EMERALD
                } else {
                    Color32::from_rgb(38, 38, 46)
                };
                ui.painter().rect_filled(rect, Rounding::same(11.0), bg);
                let knob_x = if *value {
                    rect.right() - 11.0
                } else {
                    rect.left() + 11.0
                };
                ui.painter().circle_filled(
                    egui::Pos2::new(knob_x, rect.center().y),
                    8.0,
                    TEXT_1,
                );
            });
        });
    });
}

pub fn truncate_label(s: &str, max_chars: usize) -> String {
    if s.chars().count() <= max_chars {
        s.to_string()
    } else {
        let mut out: String = s.chars().take(max_chars.saturating_sub(1)).collect();
        out.push('…');
        out
    }
}

pub fn open_url_browser(url: &str) {
    #[cfg(target_os = "windows")]
    {
        let _ = std::process::Command::new("cmd")
            .args(["/C", "start", "", url])
            .spawn();
    }
    #[cfg(target_os = "macos")]
    {
        let _ = std::process::Command::new("open").arg(url).spawn();
    }
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        let _ = std::process::Command::new("xdg-open").arg(url).spawn();
    }
}

