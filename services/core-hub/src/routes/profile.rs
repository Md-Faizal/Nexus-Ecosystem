use crate::models::ProfileHistoryItem;
use crate::state::SharedState;
use axum::{
    body::Bytes,
    extract::{Multipart, Path, State},
    http::{header, HeaderMap, StatusCode},
    response::{IntoResponse, Redirect},
    Json,
};
use rusqlite::params;
use tracing::error;

const FALLBACK_AVATAR: &str =
    "https://images.unsplash.com/photo-1560250097-0b93528c311a?q=80&w=240&auto=format&fit=crop";

pub async fn get_current_profile_pic(
    State(state): State<SharedState>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;

    // Look for current_profile_id or profile_picture_id
    let pic_id_res: Result<String, rusqlite::Error> = conn.query_row(
        "SELECT value FROM settings WHERE key IN ('current_profile_id', 'profile_picture_id') AND value != '' ORDER BY (key = 'current_profile_id') DESC LIMIT 1",
        [],
        |row| row.get(0),
    );

    if let Ok(pic_id) = pic_id_res {
        if let Ok(id_num) = pic_id.parse::<i64>() {
            let blob_res: Result<Vec<u8>, rusqlite::Error> = conn.query_row(
                "SELECT value FROM profile_pictures WHERE id = ?1",
                params![id_num],
                |row| row.get(0),
            );

            if let Ok(blob) = blob_res {
                if !blob.is_empty() {
                    let mut headers = HeaderMap::new();
                    headers.insert(header::CONTENT_TYPE, "image/jpeg".parse().unwrap());
                    return (StatusCode::OK, headers, Bytes::from(blob)).into_response();
                }
            }
        }
    }

    // Try latest picture from table
    let latest_blob: Result<Vec<u8>, rusqlite::Error> = conn.query_row(
        "SELECT value FROM profile_pictures ORDER BY created_at DESC LIMIT 1",
        [],
        |row| row.get(0),
    );

    if let Ok(blob) = latest_blob {
        if !blob.is_empty() {
            let mut headers = HeaderMap::new();
            headers.insert(header::CONTENT_TYPE, "image/jpeg".parse().unwrap());
            return (StatusCode::OK, headers, Bytes::from(blob)).into_response();
        }
    }

    Redirect::temporary(FALLBACK_AVATAR).into_response()
}

pub async fn get_profile_pic_by_id(
    State(state): State<SharedState>,
    Path(id): Path<i64>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    let blob_res: Result<Vec<u8>, rusqlite::Error> = conn.query_row(
        "SELECT value FROM profile_pictures WHERE id = ?1",
        params![id],
        |row| row.get(0),
    );

    match blob_res {
        Ok(blob) if !blob.is_empty() => {
            let mut headers = HeaderMap::new();
            headers.insert(header::CONTENT_TYPE, "image/jpeg".parse().unwrap());
            (StatusCode::OK, headers, Bytes::from(blob)).into_response()
        }
        _ => (StatusCode::NOT_FOUND, "Not found").into_response(),
    }
}

pub async fn upload_profile_pic(
    State(state): State<SharedState>,
    mut multipart: Multipart,
) -> impl IntoResponse {
    let mut image_bytes: Option<Vec<u8>> = None;

    while let Ok(Some(field)) = multipart.next_field().await {
        let name = field.name().unwrap_or("").to_string();
        if name == "profilePic" || name == "file" || name == "image" {
            if let Ok(bytes) = field.bytes().await {
                if !bytes.is_empty() {
                    image_bytes = Some(bytes.to_vec());
                    break;
                }
            }
        }
    }

    let bytes = match image_bytes {
        Some(b) => b,
        None => return Json(serde_json::json!({ "success": false, "error": "No file uploaded." })),
    };

    let conn = state.db.lock().await;
    let ins_res = conn.execute(
        "INSERT INTO profile_pictures (value) VALUES (?1)",
        params![bytes],
    );

    match ins_res {
        Ok(_) => {
            let new_id = conn.last_insert_rowid();
            let _ = conn.execute(
                "INSERT OR REPLACE INTO settings (key, value) VALUES ('current_profile_id', ?1)",
                params![new_id.to_string()],
            );
            let _ = conn.execute(
                "INSERT OR REPLACE INTO settings (key, value) VALUES ('profile_picture_id', ?1)",
                params![new_id.to_string()],
            );

            Json(serde_json::json!({
                "success": true,
                "message": "Profile picture updated and saved to history.",
                "id": new_id
            }))
        }
        Err(e) => {
            error!("Error uploading profile picture: {}", e);
            Json(serde_json::json!({ "success": false, "error": "Database error" }))
        }
    }
}

pub async fn get_profile_history(
    State(state): State<SharedState>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    let mut stmt = match conn.prepare(
        "SELECT id, created_at FROM profile_pictures ORDER BY created_at DESC",
    ) {
        Ok(s) => s,
        Err(e) => return Json(serde_json::json!({ "success": false, "error": e.to_string() })),
    };

    let history = stmt
        .query_map([], |row| {
            Ok(ProfileHistoryItem {
                id: row.get(0)?,
                created_at: row.get(1)?,
            })
        })
        .map(|rows| rows.filter_map(Result::ok).collect::<Vec<_>>())
        .unwrap_or_default();

    Json(serde_json::json!({
        "success": true,
        "history": history
    }))
}

pub async fn select_profile_pic(
    State(state): State<SharedState>,
    Path(id): Path<i64>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    let _ = conn.execute(
        "INSERT OR REPLACE INTO settings (key, value) VALUES ('current_profile_id', ?1)",
        params![id.to_string()],
    );
    let _ = conn.execute(
        "INSERT OR REPLACE INTO settings (key, value) VALUES ('profile_picture_id', ?1)",
        params![id.to_string()],
    );
    Json(serde_json::json!({ "success": true }))
}

pub async fn delete_profile_pic(
    State(state): State<SharedState>,
    Path(id): Path<i64>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    match conn.execute("DELETE FROM profile_pictures WHERE id = ?1", params![id]) {
        Ok(_) => Json(serde_json::json!({ "success": true })),
        Err(e) => Json(serde_json::json!({ "success": false, "error": e.to_string() })),
    }
}
