use crate::models::{SettingItem, UpdateSettingRequest};
use crate::state::SharedState;
use axum::{extract::State, response::IntoResponse, Json};
use rusqlite::params;
use tracing::info;

pub async fn get_settings(
    State(state): State<SharedState>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    let mut stmt = match conn.prepare("SELECT key, value FROM settings") {
        Ok(s) => s,
        Err(e) => return Json(serde_json::json!({ "success": false, "error": e.to_string() })),
    };

    let settings = stmt
        .query_map([], |row| {
            Ok(SettingItem {
                key: row.get(0)?,
                value: row.get(1)?,
            })
        })
        .map(|rows| rows.filter_map(Result::ok).collect::<Vec<_>>())
        .unwrap_or_default();

    Json(serde_json::json!({
        "success": true,
        "settings": settings
    }))
}

pub async fn update_setting(
    State(state): State<SharedState>,
    Json(payload): Json<UpdateSettingRequest>,
) -> impl IntoResponse {
    info!(
        "Updating setting: {} = {}",
        payload.key,
        if payload.key == "smtp_password" {
            "********"
        } else {
            &payload.value
        }
    );

    let conn = state.db.lock().await;
    match conn.execute(
        "INSERT OR REPLACE INTO settings (key, value) VALUES (?1, ?2)",
        params![payload.key, payload.value],
    ) {
        Ok(_) => Json(serde_json::json!({ "success": true })),
        Err(e) => Json(serde_json::json!({ "success": false, "error": e.to_string() })),
    }
}

pub async fn get_maintenance_status(
    State(state): State<SharedState>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    let val: Option<String> = conn
        .query_row(
            "SELECT value FROM settings WHERE key = 'maintenance_mode'",
            [],
            |r| r.get(0),
        )
        .ok();

    let is_maintenance = val.map(|v| v == "true").unwrap_or(false);
    Json(serde_json::json!({ "maintenance": is_maintenance }))
}

pub async fn get_theme_config(
    State(state): State<SharedState>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    let val: Option<String> = conn
        .query_row(
            "SELECT value FROM settings WHERE key = 'site_theme'",
            [],
            |r| r.get(0),
        )
        .ok();

    let theme = val.unwrap_or_else(|| "classic".into());
    Json(serde_json::json!({ "theme": theme }))
}
