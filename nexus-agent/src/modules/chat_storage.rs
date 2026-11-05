use chrono::Local;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TurnMetrics {
    pub tok_per_sec: f64,
    pub total_tokens: u64,
    pub elapsed_secs: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChatMessageItem {
    pub role: String, // "user", "assistant", "system"
    pub content: String,
    pub timestamp: u64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub metrics: Option<TurnMetrics>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChatSession {
    pub id: String,
    pub title: String,
    pub target_node_id: String,
    pub target_hostname: String,
    pub model: String,
    pub system_prompt: Option<String>,
    pub temperature: f32,
    pub messages: Vec<ChatMessageItem>,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChatSessionMeta {
    pub id: String,
    pub title: String,
    pub target_hostname: String,
    pub model: String,
    pub message_count: usize,
    pub updated_at: String,
    pub file_path: PathBuf,
}

pub struct ChatStorage;

impl ChatStorage {
    /// Resolves the local chat directory (~/.nexus/chats or %USERPROFILE%/.nexus/chats)
    pub fn chats_dir() -> PathBuf {
        let base = if cfg!(target_os = "windows") {
            std::env::var("USERPROFILE")
                .map(PathBuf::from)
                .unwrap_or_else(|_| PathBuf::from("."))
        } else {
            std::env::var("HOME")
                .map(PathBuf::from)
                .unwrap_or_else(|_| PathBuf::from("."))
        };

        let dir = base.join(".nexus").join("chats");
        let _ = fs::create_dir_all(&dir);
        dir
    }

    /// Creates a new empty chat session with an auto-generated ID
    pub fn new_session(
        target_node_id: &str,
        target_hostname: &str,
        model: &str,
        system_prompt: Option<String>,
        temperature: f32,
    ) -> ChatSession {
        let now = Local::now();
        let timestamp_str = now.format("%Y%m%d_%H%M%S").to_string();
        let sanitized_host = target_hostname.to_lowercase().replace(' ', "-");
        let id = format!("{}_{}", timestamp_str, sanitized_host);

        ChatSession {
            id,
            title: "New Conversation".to_string(),
            target_node_id: target_node_id.to_string(),
            target_hostname: target_hostname.to_string(),
            model: model.to_string(),
            system_prompt,
            temperature,
            messages: Vec::new(),
            created_at: now.format("%Y-%m-%d %H:%M:%S").to_string(),
            updated_at: now.format("%Y-%m-%d %H:%M:%S").to_string(),
        }
    }

    /// Saves a session to local JSON file
    pub fn save_session(session: &ChatSession) -> Result<PathBuf, String> {
        let dir = Self::chats_dir();
        let path = dir.join(format!("{}.json", session.id));
        let json = serde_json::to_string_pretty(session)
            .map_err(|e| format!("Serialization error: {}", e))?;
        fs::write(&path, json).map_err(|e| format!("File write error {}: {}", path.display(), e))?;
        Ok(path)
    }

    /// Loads a session by ID or index
    pub fn load_session(id_or_index: &str) -> Result<ChatSession, String> {
        let sessions = Self::list_sessions();

        // Check if numeric index
        if let Ok(idx) = id_or_index.parse::<usize>() {
            if idx > 0 && idx <= sessions.len() {
                let meta = &sessions[idx - 1];
                let content = fs::read_to_string(&meta.file_path)
                    .map_err(|e| format!("Failed to read {}: {}", meta.file_path.display(), e))?;
                return serde_json::from_str::<ChatSession>(&content)
                    .map_err(|e| format!("Invalid JSON in {}: {}", meta.file_path.display(), e));
            } else {
                return Err(format!("Index {} out of range (1 - {})", idx, sessions.len()));
            }
        }

        // Search by exact ID or prefix
        for meta in &sessions {
            if meta.id == id_or_index || meta.id.starts_with(id_or_index) {
                let content = fs::read_to_string(&meta.file_path)
                    .map_err(|e| format!("Failed to read {}: {}", meta.file_path.display(), e))?;
                return serde_json::from_str::<ChatSession>(&content)
                    .map_err(|e| format!("Invalid JSON: {}", e));
            }
        }

        Err(format!("Conversation session '{}' not found", id_or_index))
    }

    /// Lists all saved chat sessions sorted newest first
    pub fn list_sessions() -> Vec<ChatSessionMeta> {
        let dir = Self::chats_dir();
        let mut list = Vec::new();

        if let Ok(entries) = fs::read_dir(dir) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.extension().and_then(|e| e.to_str()) == Some("json") {
                    if let Ok(content) = fs::read_to_string(&path) {
                        if let Ok(session) = serde_json::from_str::<ChatSession>(&content) {
                            list.push(ChatSessionMeta {
                                id: session.id,
                                title: session.title,
                                target_hostname: session.target_hostname,
                                model: session.model,
                                message_count: session.messages.len(),
                                updated_at: session.updated_at,
                                file_path: path,
                            });
                        }
                    }
                }
            }
        }

        list.sort_by(|a, b| b.updated_at.cmp(&a.updated_at));
        list
    }

    /// Deletes a session by ID or index
    pub fn delete_session(id_or_index: &str) -> Result<String, String> {
        let sessions = Self::list_sessions();

        let target_meta = if let Ok(idx) = id_or_index.parse::<usize>() {
            if idx > 0 && idx <= sessions.len() {
                sessions[idx - 1].clone()
            } else {
                return Err(format!("Index {} out of range", idx));
            }
        } else {
            sessions
                .into_iter()
                .find(|m| m.id == id_or_index || m.id.starts_with(id_or_index))
                .ok_or_else(|| format!("Session '{}' not found", id_or_index))?
        };

        fs::remove_file(&target_meta.file_path)
            .map_err(|e| format!("Failed to remove file: {}", e))?;
        Ok(target_meta.id)
    }

    /// Exports session to a beautifully formatted Markdown transcript
    pub fn export_to_markdown(session: &ChatSession, out_path: &Path) -> Result<(), String> {
        let mut md = String::new();
        md.push_str(&format!("# Nexus AI Transcript: {}\n\n", session.title));
        md.push_str(&format!("- **Session ID:** `{}`\n", session.id));
        md.push_str(&format!("- **Target Compute:** `{}` ({})\n", session.target_hostname, session.target_node_id));
        md.push_str(&format!("- **Model:** `{}`\n", session.model));
        md.push_str(&format!("- **Created At:** {}\n", session.created_at));
        md.push_str(&format!("- **Updated At:** {}\n\n", session.updated_at));
        md.push_str("---\n\n");

        if let Some(sys) = &session.system_prompt {
            md.push_str(&format!("> **System Prompt:** {}\n\n", sys));
        }

        for msg in &session.messages {
            let role_badge = if msg.role == "user" { "👤 User" } else { "🤖 Nexus Assistant" };
            md.push_str(&format!("### {}\n\n{}\n\n", role_badge, msg.content));

            if let Some(metrics) = &msg.metrics {
                md.push_str(&format!(
                    "<sub>⚡ {:.1} tok/s • {} tokens • {:.2}s via {}</sub>\n\n",
                    metrics.tok_per_sec, metrics.total_tokens, metrics.elapsed_secs, session.target_hostname
                ));
            }
        }

        fs::write(out_path, md).map_err(|e| format!("Export error: {}", e))?;
        Ok(())
    }
}
