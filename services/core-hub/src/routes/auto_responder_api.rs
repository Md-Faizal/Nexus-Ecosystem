use crate::db::DbPool;
use crate::mailer::send_auto_reply;
use crate::state::SharedState;
use axum::{extract::State, response::IntoResponse, Json};
use rusqlite::params;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashMap;
use tracing::{error, info};

// ── Config defaults ───────────────────────────────────────────────────────────

fn default_config() -> AutoResponderConfig {
    AutoResponderConfig {
        enabled: "true".into(),
        cooldown_days: "7".into(),
        suppress_self: "true".into(),
        suppress_bulk: "true".into(),
        from_name: "Mohammad Faizal (Auto-Reply)".into(),
        from_email: "me@faizal.uk".into(),
        reply_to: "me@faizal.uk".into(),
        hero_banner_url: "https://faizal.uk/images/mail/hero_aurora_v2.jpg?v=2".into(),
        headline: "Got your message.".into(),
        salutation: "Hi there,".into(),
        body_paragraph: "Thanks so much for reaching out and taking the time to check out my work. This is a quick automated note to let you know your email landed safely in my inbox.".into(),
        response_window: "1–2 DAYS".into(),
        callout_message: "I personally read every message and typically reply within 1 to 2 business days.".into(),
        closing_paragraph: "In the meantime, feel free to browse some of my latest projects and engineering work below.".into(),
        cta_primary_text: "Explore Portfolio →".into(),
        cta_primary_url: "https://faizal.uk".into(),
        cta_secondary_text: "Selected Works".into(),
        cta_secondary_url: "https://faizal.uk/work".into(),
        footer_note: "Made with love & curiosity 🤍".into(),
    }
}

// ── Types ─────────────────────────────────────────────────────────────────────

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct AutoResponderConfig {
    pub enabled: String,
    pub cooldown_days: String,
    pub suppress_self: String,
    pub suppress_bulk: String,
    pub from_name: String,
    pub from_email: String,
    pub reply_to: String,
    pub hero_banner_url: String,
    pub headline: String,
    pub salutation: String,
    pub body_paragraph: String,
    pub response_window: String,
    pub callout_message: String,
    pub closing_paragraph: String,
    pub cta_primary_text: String,
    pub cta_primary_url: String,
    pub cta_secondary_text: String,
    pub cta_secondary_url: String,
    pub footer_note: String,
}

#[derive(Deserialize)]
pub struct UpdateConfigRequest {
    pub settings: HashMap<String, String>,
}

#[derive(Deserialize)]
pub struct SendTestRequest {
    pub to: String,
}

#[derive(Deserialize)]
pub struct ClearCooldownRequest {
    pub email: Option<String>,
}

// ── DB helpers ────────────────────────────────────────────────────────────────

pub async fn load_auto_responder_config(db: &DbPool) -> AutoResponderConfig {
    let defaults = default_config();
    let conn = db.lock().await;

    fn get(conn: &rusqlite::Connection, key: &str, default: &str) -> String {
        conn.query_row(
            "SELECT value FROM settings WHERE key = ?1",
            params![key],
            |r| r.get::<_, String>(0),
        )
        .unwrap_or_else(|_| default.to_string())
    }

    AutoResponderConfig {
        enabled: get(&conn, "auto_responder_enabled", &defaults.enabled),
        cooldown_days: get(&conn, "auto_responder_cooldown_days", &defaults.cooldown_days),
        suppress_self: get(&conn, "auto_responder_suppress_self", &defaults.suppress_self),
        suppress_bulk: get(&conn, "auto_responder_suppress_bulk", &defaults.suppress_bulk),
        from_name: get(&conn, "auto_responder_from_name", &defaults.from_name),
        from_email: get(&conn, "auto_responder_from_email", &defaults.from_email),
        reply_to: get(&conn, "auto_responder_reply_to", &defaults.reply_to),
        hero_banner_url: get(&conn, "auto_responder_hero_banner_url", &defaults.hero_banner_url),
        headline: get(&conn, "auto_responder_headline", &defaults.headline),
        salutation: get(&conn, "auto_responder_salutation", &defaults.salutation),
        body_paragraph: get(&conn, "auto_responder_body_paragraph", &defaults.body_paragraph),
        response_window: get(&conn, "auto_responder_response_window", &defaults.response_window),
        callout_message: get(&conn, "auto_responder_callout_message", &defaults.callout_message),
        closing_paragraph: get(&conn, "auto_responder_closing_paragraph", &defaults.closing_paragraph),
        cta_primary_text: get(&conn, "auto_responder_cta_primary_text", &defaults.cta_primary_text),
        cta_primary_url: get(&conn, "auto_responder_cta_primary_url", &defaults.cta_primary_url),
        cta_secondary_text: get(&conn, "auto_responder_cta_secondary_text", &defaults.cta_secondary_text),
        cta_secondary_url: get(&conn, "auto_responder_cta_secondary_url", &defaults.cta_secondary_url),
        footer_note: get(&conn, "auto_responder_footer_note", &defaults.footer_note),
    }
}

// ── Template renderer ─────────────────────────────────────────────────────────

const EMBEDDED_HTML_TEMPLATE: &str =
    include_str!("../../../mail/responder/templates/acknowledgement.html");
const EMBEDDED_TXT_TEMPLATE: &str =
    include_str!("../../../mail/responder/templates/acknowledgement.txt");

pub fn render_email_html(config: &AutoResponderConfig, recipient_email: &str) -> String {
    let base = load_base_html_template();
    apply_substitutions(&base, config, recipient_email)
}

pub fn render_email_txt(config: &AutoResponderConfig, recipient_email: &str) -> String {
    let base = load_base_txt_template();
    apply_txt_substitutions(&base, config, recipient_email)
}

fn load_base_html_template() -> String {
    for candidate in &[
        "/app/templates/acknowledgement.html",
        "/home/ubuntu/stack/apps/personal-site/mail/responder/templates/acknowledgement.html",
        "services/mail/responder/templates/acknowledgement.html",
        "templates/acknowledgement.html",
    ] {
        if let Ok(c) = std::fs::read_to_string(candidate) {
            if !c.trim().is_empty() {
                return c;
            }
        }
    }
    EMBEDDED_HTML_TEMPLATE.to_string()
}

fn load_base_txt_template() -> String {
    for candidate in &[
        "/app/templates/acknowledgement.txt",
        "/home/ubuntu/stack/apps/personal-site/mail/responder/templates/acknowledgement.txt",
        "services/mail/responder/templates/acknowledgement.txt",
        "templates/acknowledgement.txt",
    ] {
        if let Ok(c) = std::fs::read_to_string(candidate) {
            if !c.trim().is_empty() {
                return c;
            }
        }
    }
    EMBEDDED_TXT_TEMPLATE.to_string()
}

fn apply_substitutions(html: &str, config: &AutoResponderConfig, recipient_email: &str) -> String {
    let mut out = html.to_string();

    // Recipient email (all variants)
    for placeholder in &[
        "{{ recipient_email }}",
        "{{recipient_email}}",
        "%7B%7B+recipient_email+%7D%7D",
        "%7B%7Brecipient_email%7D%7D",
        "%7B%7B%20recipient_email%20%7D%7D",
    ] {
        out = out.replace(placeholder, recipient_email);
    }

    // Content substitutions
    out = out.replace("{{headline}}", &config.headline);
    out = out.replace("{{salutation}}", &config.salutation);
    out = out.replace("{{body_paragraph}}", &config.body_paragraph);
    out = out.replace("{{response_window}}", &config.response_window);
    out = out.replace("{{callout_message}}", &config.callout_message);
    out = out.replace("{{closing_paragraph}}", &config.closing_paragraph);
    out = out.replace("{{cta_primary_text}}", &config.cta_primary_text);
    out = out.replace("{{cta_primary_url}}", &config.cta_primary_url);
    out = out.replace("{{cta_secondary_text}}", &config.cta_secondary_text);
    out = out.replace("{{cta_secondary_url}}", &config.cta_secondary_url);
    out = out.replace("{{footer_note}}", &config.footer_note);
    out = out.replace("{{hero_banner_url}}", &config.hero_banner_url);

    out
}

fn apply_txt_substitutions(txt: &str, config: &AutoResponderConfig, recipient_email: &str) -> String {
    let mut out = txt.to_string();
    for placeholder in &[
        "{{ recipient_email }}",
        "{{recipient_email}}",
        "%7B%7B+recipient_email+%7D%7D",
    ] {
        out = out.replace(placeholder, recipient_email);
    }
    out = out.replace("{{headline}}", &config.headline);
    out = out.replace("{{salutation}}", &config.salutation);
    out = out.replace("{{body_paragraph}}", &config.body_paragraph);
    out = out.replace("{{response_window}}", &config.response_window);
    out = out.replace("{{footer_note}}", &config.footer_note);
    out
}

// ── Route handlers ────────────────────────────────────────────────────────────

/// GET /api/admin/auto-responder/config
pub async fn get_config(State(state): State<SharedState>) -> impl IntoResponse {
    let config = load_auto_responder_config(&state.db).await;
    Json(serde_json::json!({ "success": true, "config": config }))
}

/// POST /api/admin/auto-responder/config
pub async fn update_config(
    State(state): State<SharedState>,
    Json(payload): Json<UpdateConfigRequest>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    for (key, value) in &payload.settings {
        // Only allow auto_responder_* keys to be upserted via this endpoint
        if !key.starts_with("auto_responder_") {
            continue;
        }
        if let Err(e) = conn.execute(
            "INSERT OR REPLACE INTO settings (key, value) VALUES (?1, ?2)",
            params![key, value],
        ) {
            error!("Failed to update setting {}: {}", key, e);
            return Json(serde_json::json!({ "success": false, "error": e.to_string() }));
        }
    }
    info!("Auto-responder config updated: {} keys", payload.settings.len());
    Json(serde_json::json!({ "success": true }))
}

/// GET /api/admin/auto-responder/preview-html?email=...
pub async fn get_preview_html(
    State(state): State<SharedState>,
    axum::extract::Query(params): axum::extract::Query<HashMap<String, String>>,
) -> impl IntoResponse {
    let config = load_auto_responder_config(&state.db).await;
    let email = params
        .get("email")
        .cloned()
        .unwrap_or_else(|| "preview@example.com".into());
    let html = render_email_html(&config, &email);
    axum::response::Html(html)
}

/// POST /api/admin/auto-responder/test
pub async fn send_test(
    State(state): State<SharedState>,
    Json(payload): Json<SendTestRequest>,
) -> impl IntoResponse {
    let config = load_auto_responder_config(&state.db).await;
    let html = render_email_html(&config, &payload.to);
    let txt = render_email_txt(&config, &payload.to);

    info!("Admin: sending test auto-reply to {}", payload.to);

    match send_auto_reply(
        &payload.to,
        "Admin Test",
        &config.from_name,
        &config.from_email,
        &config.reply_to,
        None,
        &format!("Re: Test — {}", config.headline),
        &html,
        &txt,
    )
    .await
    {
        Ok(_) => Json(serde_json::json!({ "success": true, "message": format!("Test auto-reply sent to {}", payload.to) })),
        Err(e) => Json(serde_json::json!({ "success": false, "error": e })),
    }
}

/// GET /api/admin/auto-responder/logs
pub async fn get_logs(State(state): State<SharedState>) -> impl IntoResponse {
    let conn = state.db.lock().await;

    let mut stmt = match conn.prepare(
        r#"SELECT
            l.id,
            l.message_id,
            l.sender_email,
            l.subject,
            l.action,
            l.timestamp,
            c.last_replied_at,
            c.reply_count
        FROM mail_responder_logs l
        LEFT JOIN mail_responder_cooldown c ON c.sender_email = l.sender_email
        ORDER BY l.id DESC
        LIMIT 100"#,
    ) {
        Ok(s) => s,
        Err(e) => return Json(serde_json::json!({ "success": false, "error": e.to_string() })),
    };

    let rows: Vec<Value> = stmt
        .query_map([], |row| {
            Ok(serde_json::json!({
                "id": row.get::<_, i64>(0).unwrap_or(0),
                "message_id": row.get::<_, Option<String>>(1).unwrap_or_default(),
                "sender_email": row.get::<_, String>(2).unwrap_or_default(),
                "subject": row.get::<_, String>(3).unwrap_or_default(),
                "action": row.get::<_, String>(4).unwrap_or_default(),
                "created_at": row.get::<_, String>(5).unwrap_or_default(),
                "last_replied_at": row.get::<_, Option<String>>(6).unwrap_or_default(),
                "reply_count": row.get::<_, Option<i64>>(7).unwrap_or_default(),
            }))
        })
        .map(|rows| rows.filter_map(Result::ok).collect())
        .unwrap_or_default();

    Json(serde_json::json!({ "success": true, "logs": rows }))
}

/// POST /api/admin/auto-responder/clear-cooldown
pub async fn clear_cooldown(
    State(state): State<SharedState>,
    Json(payload): Json<ClearCooldownRequest>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;

    let result = if let Some(ref email) = payload.email {
        info!("Admin: clearing cooldown for {}", email);
        conn.execute(
            "DELETE FROM mail_responder_cooldown WHERE sender_email = ?1",
            params![email],
        )
    } else {
        info!("Admin: clearing ALL cooldowns");
        conn.execute("DELETE FROM mail_responder_cooldown", [])
    };

    match result {
        Ok(affected) => Json(serde_json::json!({ "success": true, "cleared": affected })),
        Err(e) => Json(serde_json::json!({ "success": false, "error": e.to_string() })),
    }
}
