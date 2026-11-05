use crate::models::Project;
use crate::state::SharedState;
use axum::{
    body::Bytes,
    extract::{Multipart, Path, State},
    http::{header, HeaderMap, StatusCode},
    response::IntoResponse,
    Json,
};
use rusqlite::params;
use tracing::error;

pub async fn list_projects(
    State(state): State<SharedState>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    let mut stmt = match conn.prepare(
        "SELECT id, title, description, link, created_at FROM projects ORDER BY created_at DESC",
    ) {
        Ok(s) => s,
        Err(e) => return Json(serde_json::json!({ "success": false, "error": e.to_string(), "projects": [] })),
    };

    let projects = stmt
        .query_map([], |row| {
            Ok(Project {
                id: row.get(0)?,
                title: row.get(1)?,
                description: row.get(2)?,
                link: row.get(3)?,
                created_at: row.get(4)?,
            })
        })
        .map(|rows| rows.filter_map(Result::ok).collect::<Vec<_>>())
        .unwrap_or_default();

    Json(serde_json::json!({
        "success": true,
        "projects": projects
    }))
}

pub async fn get_project_image(
    State(state): State<SharedState>,
    Path(id): Path<i64>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    let img_opt: Result<Option<Vec<u8>>, rusqlite::Error> = conn.query_row(
        "SELECT image_blob FROM projects WHERE id = ?1",
        params![id],
        |row| row.get(0),
    );

    match img_opt {
        Ok(Some(blob)) if !blob.is_empty() => {
            let mut headers = HeaderMap::new();
            headers.insert(header::CONTENT_TYPE, "image/jpeg".parse().unwrap());
            (StatusCode::OK, headers, Bytes::from(blob)).into_response()
        }
        _ => (StatusCode::NOT_FOUND, "Not found").into_response(),
    }
}

pub async fn create_project(
    State(state): State<SharedState>,
    mut multipart: Multipart,
) -> impl IntoResponse {
    let mut title = String::new();
    let mut description = String::new();
    let mut link = String::new();
    let mut image_bytes: Option<Vec<u8>> = None;

    while let Ok(Some(field)) = multipart.next_field().await {
        let field_name = field.name().unwrap_or("").to_string();
        if field_name == "title" {
            if let Ok(text) = field.text().await {
                title = text;
            }
        } else if field_name == "description" {
            if let Ok(text) = field.text().await {
                description = text;
            }
        } else if field_name == "link" {
            if let Ok(text) = field.text().await {
                link = text;
            }
        } else if field_name == "image" {
            if let Ok(bytes) = field.bytes().await {
                if !bytes.is_empty() {
                    image_bytes = Some(bytes.to_vec());
                }
            }
        }
    }

    let conn = state.db.lock().await;
    let res = conn.execute(
        "INSERT INTO projects (title, description, link, image_blob) VALUES (?1, ?2, ?3, ?4)",
        params![title, description, link, image_bytes],
    );

    match res {
        Ok(_) => {
            let last_id = conn.last_insert_rowid();
            Json(serde_json::json!({ "success": true, "id": last_id }))
        }
        Err(e) => {
            error!("Failed to create project: {}", e);
            Json(serde_json::json!({ "success": false, "error": e.to_string() }))
        }
    }
}

pub async fn delete_project(
    State(state): State<SharedState>,
    Path(id): Path<i64>,
) -> impl IntoResponse {
    let conn = state.db.lock().await;
    match conn.execute("DELETE FROM projects WHERE id = ?1", params![id]) {
        Ok(_) => Json(serde_json::json!({ "success": true })),
        Err(e) => Json(serde_json::json!({ "success": false, "error": e.to_string() })),
    }
}
