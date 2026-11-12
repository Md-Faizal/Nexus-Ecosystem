use crate::gui::state::{GuiCommand, GuiState, LogLevel};
use crate::gui::theme::*;
use crate::modules::chat_storage::{ChatMessageItem, ChatStorage};
use chrono::Local;
use eframe::egui;
use egui::{Color32, FontId, Frame, Margin, Rounding, Stroke, Vec2};

pub fn render(ui: &mut egui::Ui, state: &mut GuiState, tier: LayoutTier) {
    let avail_h = ui.available_height().max(380.0);
    let gap = 14.0;

    if tier.is_compact() {
        render_chat_workspace(ui, state, avail_h, tier);
    } else {
        split_columns_weighted(
            ui,
            state,
            0.27,
            gap,
            |left_ui, st| render_session_drawer(left_ui, st, avail_h),
            |right_ui, st| render_chat_workspace(right_ui, st, avail_h, tier),
        );
    }
}

fn render_session_drawer(ui: &mut egui::Ui, state: &mut GuiState, h: f32) {
    card_frame().show(ui, |ui| {
        ui.set_width(ui.available_width());
        ui.set_min_height((h - 42.0).max(240.0));

        ui.horizontal(|ui| {
            ui.label(
                egui::RichText::new("SESSIONS")
                    .font(FontId::monospace(10.0))
                    .color(TEXT_2),
            );
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                if primary_button(ui, "+ New").clicked() {
                    state.start_new_chat();
                    state.set_toast("Started new AI session", LogLevel::Info, "AI Studio");
                }
            });
        });

        ui.add_space(10.0);

        let mut load_id: Option<String> = None;
        let mut delete_id: Option<String> = None;

        egui::ScrollArea::vertical()
            .max_height((h - 110.0).max(180.0))
            .show(ui, |ui| {
                if state.chat_sessions.is_empty() {
                    ui.label(
                        egui::RichText::new("No saved conversations yet.")
                            .font(FontId::proportional(11.5))
                            .color(TEXT_3),
                    );
                } else {
                    for meta in &state.chat_sessions {
                        let active = state.active_chat.id == meta.id;
                        let fill = if active {
                            Color32::from_rgba_unmultiplied(167, 139, 250, 22)
                        } else {
                            BG_TILE
                        };
                        let stroke = if active { ACCENT_VIOLET } else { BORDER_SUBTLE };

                        Frame::none()
                            .fill(fill)
                            .stroke(Stroke::new(1.0_f32, stroke))
                            .rounding(Rounding::same(8.0))
                            .inner_margin(Margin::same(10.0))
                            .show(ui, |ui| {
                                ui.set_width(ui.available_width());
                                ui.horizontal(|ui| {
                                    let title_btn = ui.add(
                                        egui::Button::new(
                                            egui::RichText::new(truncate_label(&meta.title, 18))
                                                .font(FontId::proportional(12.0))
                                                .color(if active { TEXT_1 } else { TEXT_2 }),
                                        )
                                        .frame(false),
                                    );
                                    if title_btn.clicked() {
                                        load_id = Some(meta.id.clone());
                                    }
                                    ui.with_layout(
                                        egui::Layout::right_to_left(egui::Align::Center),
                                        |ui| {
                                            if ui
                                                .add(
                                                    egui::Button::new(
                                                        egui::RichText::new("✕")
                                                            .font(FontId::monospace(10.0))
                                                            .color(TEXT_3),
                                                    )
                                                    .frame(false),
                                                )
                                                .clicked()
                                            {
                                                delete_id = Some(meta.id.clone());
                                            }
                                        },
                                    );
                                });
                                ui.label(
                                    egui::RichText::new(format!(
                                        "{} • {} msgs",
                                        truncate_label(&meta.model, 14),
                                        meta.message_count
                                    ))
                                    .font(FontId::monospace(9.0))
                                    .color(TEXT_3),
                                );
                            });
                        ui.add_space(6.0);
                    }
                }
            });

        if let Some(id) = load_id {
            match ChatStorage::load_session(&id) {
                Ok(sess) => {
                    state.selected_model = sess.model.clone();
                    state.system_prompt_input = sess.system_prompt.clone().unwrap_or_default();
                    state.active_chat = sess;
                }
                Err(e) => state.set_toast(e, LogLevel::Error, "AI Studio"),
            }
        }
        if let Some(id) = delete_id {
            if let Ok(_) = ChatStorage::delete_session(&id) {
                state.refresh_chat_sessions();
                if state.active_chat.id == id {
                    state.start_new_chat();
                }
            }
        }
    });
}

fn render_chat_workspace(
    ui: &mut egui::Ui,
    state: &mut GuiState,
    h: f32,
    tier: LayoutTier,
) {
    card_frame().show(ui, |ui| {
        ui.set_width(ui.available_width());
        ui.set_min_height((h - 42.0).max(320.0));

        // ─── Top Model & Engine Bar ───
        ui.horizontal_wrapped(|ui| {
            ui.spacing_mut().item_spacing = Vec2::new(8.0, 6.0);
            ui.label(
                egui::RichText::new("✦ AI STUDIO")
                    .font(FontId::monospace(10.5))
                    .color(ACCENT_VIOLET),
            );

            let models = &state.ai_telemetry.models;
            let current_lbl = if state.selected_model.is_empty() {
                "Select Model".to_string()
            } else {
                state.selected_model.clone()
            };

            egui::ComboBox::from_id_salt("ai_model_combo")
                .selected_text(truncate_label(&current_lbl, 24))
                .show_ui(ui, |ui| {
                    if models.is_empty() {
                        ui.label("No Ollama models found");
                    } else {
                        for m in models {
                            ui.selectable_value(&mut state.selected_model, m.clone(), m);
                        }
                    }
                });

            if action_button(ui, "↻ Models").clicked() {
                let _ = state.cmd_tx.send(GuiCommand::RefreshOllama {
                    endpoint: state.ai_endpoint_input.clone(),
                });
            }

            if !state.selected_model.is_empty() {
                let is_loaded = state
                    .loaded_models
                    .iter()
                    .any(|m| m.name == state.selected_model);
                if is_loaded {
                    if accent_button(ui, "Unload VRAM", ACCENT_AMBER).clicked() {
                        let _ = state.cmd_tx.send(GuiCommand::UnloadOllamaModel {
                            endpoint: state.ai_endpoint_input.clone(),
                            model: state.selected_model.clone(),
                        });
                    }
                } else if accent_button(ui, "Warm Load", ACCENT_EMERALD).clicked() {
                    let _ = state.cmd_tx.send(GuiCommand::LoadOllamaModel {
                        endpoint: state.ai_endpoint_input.clone(),
                        model: state.selected_model.clone(),
                    });
                }
            }

            if action_button(ui, "⚙ Params").clicked() {
                state.show_ai_params = !state.show_ai_params;
            }

            if tier.is_compact() && action_button(ui, "+ New").clicked() {
                state.start_new_chat();
            }
        });

        // VRAM residency pills
        if !state.loaded_models.is_empty() {
            ui.add_space(6.0);
            ui.horizontal_wrapped(|ui| {
                for lm in &state.loaded_models {
                    let vram_gb = lm.size_vram.unwrap_or(0) as f64 / 1_073_741_824.0;
                    pill_badge(
                        ui,
                        &format!("VRAM: {} ({:.2} GB)", lm.name, vram_gb),
                        ACCENT_EMERALD,
                    );
                }
            });
        } else {
            ui.add_space(4.0);
            ui.label(
                egui::RichText::new(format!("Engine: {}", state.ai_telemetry.status))
                    .font(FontId::monospace(9.5))
                    .color(TEXT_3),
            );
        }

        if state.show_ai_params {
            ui.add_space(8.0);
            tile_frame().show(ui, |ui| {
                ui.set_width(ui.available_width());
                ui.horizontal(|ui| {
                    ui.label(
                        egui::RichText::new("TEMPERATURE")
                            .font(FontId::monospace(9.5))
                            .color(TEXT_3),
                    );
                    ui.add(egui::Slider::new(
                        &mut state.active_chat.temperature,
                        0.0..=1.5,
                    ));
                });
                ui.add_space(6.0);
                ui.label(
                    egui::RichText::new("SYSTEM PROMPT")
                        .font(FontId::monospace(9.5))
                        .color(TEXT_3),
                );
                ui.add(
                    egui::TextEdit::multiline(&mut state.system_prompt_input)
                        .desired_rows(2)
                        .desired_width(ui.available_width())
                        .hint_text("Optional system instructions…"),
                );
            });
        }

        ui.add_space(10.0);

        // ─── Message Stream ───
        let chat_h = (h - 235.0).max(150.0);
        egui::ScrollArea::vertical()
            .id_salt("ai_chat_scroll")
            .max_height(chat_h)
            .stick_to_bottom(true)
            .show(ui, |ui| {
                if state.active_chat.messages.is_empty() && !state.ai_streaming {
                    ui.add_space(36.0);
                    ui.vertical_centered(|ui| {
                        ui.label(
                            egui::RichText::new("NEXUS DISTRIBUTED AI STUDIO")
                                .font(FontId::monospace(12.0))
                                .color(TEXT_2),
                        );
                        ui.add_space(4.0);
                        ui.label(
                            egui::RichText::new("Select a local Ollama model above and send a prompt.")
                                .font(FontId::proportional(12.0))
                                .color(TEXT_3),
                        );
                    });
                }

                for msg in &state.active_chat.messages {
                    render_message_bubble(ui, msg);
                    ui.add_space(8.0);
                }

                if state.ai_streaming {
                    let streaming_item = ChatMessageItem {
                        role: "assistant".to_string(),
                        content: if state.ai_stream_buffer.is_empty() {
                            "Thinking…".to_string()
                        } else {
                            state.ai_stream_buffer.clone()
                        },
                        timestamp: 0,
                        metrics: None,
                    };
                    render_message_bubble(ui, &streaming_item);
                }
            });

        ui.add_space(10.0);

        // ─── Prompt Input Bar ───
        let mut trigger_send = false;
        ui.horizontal(|ui| {
            let input_w = (ui.available_width() - 94.0).max(120.0);
            let resp = ui.add(
                egui::TextEdit::multiline(&mut state.chat_input)
                    .desired_rows(2)
                    .desired_width(input_w)
                    .hint_text("Ask Nexus AI… (Ctrl+Enter to send)"),
            );
            if resp.has_focus()
                && ui.input(|i| i.modifiers.ctrl && i.key_pressed(egui::Key::Enter))
            {
                trigger_send = true;
            }

            ui.vertical(|ui| {
                let send_lbl = if state.ai_streaming { "…" } else { "Send ↑" };
                if primary_button(ui, send_lbl).clicked() && !state.ai_streaming {
                    trigger_send = true;
                }
                if !state.active_chat.messages.is_empty() && action_button(ui, "Export").clicked() {
                    let out = ChatStorage::chats_dir()
                        .join(format!("{}.md", state.active_chat.id));
                    match ChatStorage::export_to_markdown(&state.active_chat, &out) {
                        Ok(()) => state.set_toast(
                            format!("Exported to {}", out.display()),
                            LogLevel::Success,
                            "AI Studio",
                        ),
                        Err(e) => state.set_toast(e, LogLevel::Error, "AI Studio"),
                    }
                }
            });
        });

        if trigger_send && !state.ai_streaming {
            let prompt = state.chat_input.trim().to_string();
            if !prompt.is_empty() {
                if state.selected_model.is_empty() {
                    state.set_toast(
                        "Select an Ollama model first (or start Ollama)",
                        LogLevel::Warn,
                        "AI Studio",
                    );
                } else {
                    if state.active_chat.messages.is_empty() {
                        state.active_chat.title = truncate_label(&prompt, 32);
                    }
                    state.active_chat.model = state.selected_model.clone();
                    state.active_chat.updated_at = Local::now().format("%Y-%m-%d %H:%M:%S").to_string();
                    state.active_chat.messages.push(ChatMessageItem {
                        role: "user".to_string(),
                        content: prompt,
                        timestamp: Local::now().timestamp() as u64,
                        metrics: None,
                    });
                    state.chat_input.clear();
                    state.ai_stream_buffer.clear();
                    state.ai_streaming = true;
                    state.ai_stream_started = Some(std::time::Instant::now());

                    let sys = if state.system_prompt_input.trim().is_empty() {
                        None
                    } else {
                        Some(state.system_prompt_input.trim().to_string())
                    };
                    let _ = state.cmd_tx.send(GuiCommand::StreamAiChat {
                        endpoint: state.ai_endpoint_input.clone(),
                        model: state.selected_model.clone(),
                        messages: state.active_chat.messages.clone(),
                        system_prompt: sys,
                        temperature: state.active_chat.temperature,
                    });
                }
            }
        }
    });
}

fn render_message_bubble(ui: &mut egui::Ui, msg: &ChatMessageItem) {
    let is_user = msg.role == "user";
    let fill = if is_user {
        Color32::from_rgba_unmultiplied(56, 189, 248, 16)
    } else {
        BG_TILE
    };
    let stroke = if is_user {
        Color32::from_rgba_unmultiplied(56, 189, 248, 55)
    } else {
        BORDER_SUBTLE
    };

    Frame::none()
        .fill(fill)
        .stroke(Stroke::new(1.0_f32, stroke))
        .rounding(Rounding::same(10.0))
        .inner_margin(Margin::same(12.0))
        .show(ui, |ui| {
            ui.set_width(ui.available_width());
            ui.horizontal(|ui| {
                ui.label(
                    egui::RichText::new(if is_user { "OPERATOR" } else { "NEXUS AI" })
                        .font(FontId::monospace(9.5))
                        .color(if is_user { ACCENT_CYAN } else { ACCENT_VIOLET }),
                );
                if let Some(ref m) = msg.metrics {
                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        ui.label(
                            egui::RichText::new(format!(
                                "{:.1} tok/s • {} tok • {:.1}s",
                                m.tok_per_sec, m.total_tokens, m.elapsed_secs
                            ))
                            .font(FontId::monospace(9.0))
                            .color(TEXT_3),
                        );
                    });
                }
            });
            ui.add_space(4.0);
            ui.label(
                egui::RichText::new(&msg.content)
                    .font(FontId::proportional(13.0))
                    .color(TEXT_1),
            );
        });
}

