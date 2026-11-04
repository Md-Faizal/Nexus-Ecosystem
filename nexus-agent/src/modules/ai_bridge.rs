use crate::mesh::protocol::{AiEngineTelemetry, MeshMessage};
use futures_util::StreamExt;
use reqwest::Client;
use serde::{Deserialize, Serialize};
use serde_json::json;
use std::time::Duration;
use tokio::sync::mpsc;
use tracing::{error, info, warn};

#[derive(Clone)]
pub struct AiBridge {
    client: Client,
    endpoint: String,
}

#[derive(Debug, Deserialize)]
struct OllamaTagsResponse {
    models: Option<Vec<OllamaModel>>,
}

#[derive(Debug, Deserialize)]
struct OllamaModel {
    name: String,
}

#[derive(Debug, Deserialize)]
struct OllamaChatChunk {
    message: Option<OllamaChatMessage>,
    done: Option<bool>,
}

#[derive(Debug, Deserialize, Serialize)]
struct OllamaChatMessage {
    role: String,
    content: String,
}

impl AiBridge {
    pub fn new(endpoint: &str) -> Self {
        let client = Client::builder()
            .timeout(Duration::from_secs(300))
            .build()
            .unwrap_or_default();

        Self {
            client,
            endpoint: endpoint.trim_end_matches('/').to_string(),
        }
    }

    pub async fn check_models(&self) -> AiEngineTelemetry {
        let url = format!("{}/api/tags", self.endpoint);
        let resp = self.client.get(&url).timeout(Duration::from_secs(2)).send().await;

        match resp {
            Ok(res) if res.status().is_success() => {
                if let Ok(data) = res.json::<OllamaTagsResponse>().await {
                    let models: Vec<String> = data
                        .models
                        .unwrap_or_default()
                        .into_iter()
                        .map(|m| m.name)
                        .collect();

                    let status = if models.is_empty() {
                        "idle (no models loaded)".to_string()
                    } else {
                        format!("ready ({} models available)", models.len())
                    };

                    AiEngineTelemetry {
                        engine: "ollama".to_string(),
                        endpoint: self.endpoint.clone(),
                        models,
                        active_model: None,
                        status,
                    }
                } else {
                    AiEngineTelemetry {
                        engine: "ollama".to_string(),
                        endpoint: self.endpoint.clone(),
                        models: vec![],
                        active_model: None,
                        status: "online (parse error)".to_string(),
                    }
                }
            }
            _ => AiEngineTelemetry {
                engine: "ollama".to_string(),
                endpoint: self.endpoint.clone(),
                models: vec![],
                active_model: None,
                status: "offline".to_string(),
            },
        }
    }

    pub async fn stream_prompt(
        &self,
        request_id: String,
        model: String,
        prompt: String,
        system_prompt: Option<String>,
        temperature: Option<f32>,
        outbox_tx: mpsc::Sender<MeshMessage>,
    ) {
        let url = format!("{}/api/chat", self.endpoint);

        let mut messages = Vec::new();
        if let Some(sys) = system_prompt {
            messages.push(json!({"role": "system", "content": sys}));
        }
        messages.push(json!({"role": "user", "content": prompt}));

        let mut body = json!({
            "model": model,
            "messages": messages,
            "stream": true,
        });

        if let Some(temp) = temperature {
            body["options"] = json!({ "temperature": temp });
        }

        info!("Dispatching streaming prompt to local Ollama for request: {}", request_id);

        let req = self.client.post(&url).json(&body).send().await;

        match req {
            Ok(res) => {
                let mut stream = res.bytes_stream();
                while let Some(chunk_res) = stream.next().await {
                    match chunk_res {
                        Ok(bytes) => {
                            if let Ok(chunk) = serde_json::from_slice::<OllamaChatChunk>(&bytes) {
                                let token = chunk.message.map(|m| m.content).unwrap_or_default();
                                let is_done = chunk.done.unwrap_or(false);

                                let _ = outbox_tx
                                    .send(MeshMessage::AiStreamChunk {
                                        request_id: request_id.clone(),
                                        token,
                                        done: is_done,
                                        error: None,
                                    })
                                    .await;

                                if is_done {
                                    break;
                                }
                            }
                        }
                        Err(e) => {
                            error!("Stream read error: {}", e);
                            let _ = outbox_tx
                                .send(MeshMessage::AiStreamChunk {
                                    request_id: request_id.clone(),
                                    token: "".to_string(),
                                    done: true,
                                    error: Some(e.to_string()),
                                })
                                .await;
                            break;
                        }
                    }
                }
            }
            Err(e) => {
                warn!("Failed to connect to local Ollama: {}", e);
                let _ = outbox_tx
                    .send(MeshMessage::AiStreamChunk {
                        request_id: request_id.clone(),
                        token: "".to_string(),
                        done: true,
                        error: Some(format!("Ollama unreachable on {}: {}", self.endpoint, e)),
                    })
                    .await;
            }
        }
    }

    pub async fn unload_model(&self, model: &str) -> Result<(), String> {
        let url = format!("{}/api/generate", self.endpoint);
        let body = json!({
            "model": model,
            "keep_alive": 0,
        });

        self.client
            .post(&url)
            .json(&body)
            .send()
            .await
            .map_err(|e| e.to_string())?;

        info!("Unloaded model '{}' from VRAM successfully", model);
        Ok(())
    }
}

