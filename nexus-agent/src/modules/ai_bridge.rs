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

    #[allow(dead_code)]
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

    pub async fn stream_to_stdout(
        &self,
        model: &str,
        prompt: &str,
    ) -> Result<(), Box<dyn std::error::Error>> {
        use std::io::Write;
        let url = format!("{}/api/chat", self.endpoint);
        let body = json!({
            "model": model,
            "messages": [{"role": "user", "content": prompt}],
            "stream": true,
        });

        let res = self.client.post(&url).json(&body).send().await?;
        if !res.status().is_success() {
            eprintln!("\x1b[31m[Error]\x1b[0m Ollama returned HTTP {}", res.status());
            return Ok(());
        }

        let mut stream = res.bytes_stream();
        let mut stdout = std::io::stdout();

        while let Some(chunk_res) = stream.next().await {
            if let Ok(bytes) = chunk_res {
                if let Ok(chunk) = serde_json::from_slice::<OllamaChatChunk>(&bytes) {
                    if let Some(msg) = chunk.message {
                        print!("{}", msg.content);
                        let _ = stdout.flush();
                    }
                    if chunk.done.unwrap_or(false) {
                        break;
                    }
                }
            }
        }
        println!();
        Ok(())
    }

    pub async fn run_interactive_repl(
        &self,
        model_override: Option<String>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        use std::io::{self, BufRead, Write};

        let telemetry = self.check_models().await;
        let model = model_override
            .or_else(|| telemetry.models.first().cloned())
            .unwrap_or_else(|| "llama3.2:3b".to_string());

        println!("\x1b[1;36m===============================================================\x1b[0m");
        println!("\x1b[1;32m ⚡ Nexus Local AI Terminal (Claude / Gemini CLI Style) \x1b[0m");
        println!("\x1b[90m Active Model:\x1b[0m \x1b[1;33m{}\x1b[0m \x1b[90m| Endpoint: {}\x1b[0m", model, self.endpoint);
        println!("\x1b[90m Commands: '/models' to list, '/exit' or Ctrl+C to quit\x1b[0m");
        println!("\x1b[1;36m===============================================================\x1b[0m\n");

        let stdin = io::stdin();
        let mut handle = stdin.lock();

        loop {
            print!("\x1b[1;35m>> \x1b[0m");
            let _ = io::stdout().flush();

            let mut input = String::new();
            if handle.read_line(&mut input)? == 0 {
                break;
            }

            let trimmed = input.trim();
            if trimmed.is_empty() {
                continue;
            }

            if trimmed == "/exit" || trimmed == "exit" || trimmed == "quit" {
                println!("\x1b[90mExiting Nexus AI Terminal. Goodbye!\x1b[0m");
                break;
            }

            if trimmed == "/models" {
                let latest = self.check_models().await;
                println!("\x1b[1mInstalled Models:\x1b[0m");
                if latest.models.is_empty() {
                    println!("  \x1b[90m(No models found on Ollama at {})\x1b[0m", self.endpoint);
                } else {
                    for m in latest.models {
                        println!("  - \x1b[32m{}\x1b[0m", m);
                    }
                }
                println!();
                continue;
            }

            print!("\x1b[36m");
            let _ = io::stdout().flush();
            let _ = self.stream_to_stdout(&model, trimmed).await;
            print!("\x1b[0m\n");
        }

        Ok(())
    }
}

