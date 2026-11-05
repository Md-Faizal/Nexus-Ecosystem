use crate::mesh::protocol::{AiEngineTelemetry, MeshMessage};
use crate::modules::banner;
use crate::modules::helpbook;
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

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct OllamaProcessModel {
    pub name: String,
    pub model: String,
    pub size: Option<u64>,
    pub size_vram: Option<u64>,
    pub digest: Option<String>,
    pub details: Option<OllamaModelDetails>,
    pub expires_at: Option<String>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct OllamaModelDetails {
    pub parent_model: Option<String>,
    pub format: Option<String>,
    pub family: Option<String>,
    pub parameter_size: Option<String>,
    pub quantization_level: Option<String>,
}

#[derive(Debug, Deserialize)]
struct OllamaPsResponse {
    models: Option<Vec<OllamaProcessModel>>,
}

#[derive(Debug, Deserialize)]
struct OllamaChatChunk {
    message: Option<OllamaChatMessage>,
    done: Option<bool>,
    eval_count: Option<u64>,
    #[allow(dead_code)]
    eval_duration: Option<u64>,
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

    /// Query Ollama GET /api/ps to see which models are actively kept in VRAM / RAM
    pub async fn poll_loaded_models(&self) -> Vec<OllamaProcessModel> {
        let url = format!("{}/api/ps", self.endpoint);
        let resp = self.client.get(&url).timeout(Duration::from_secs(2)).send().await;

        if let Ok(res) = resp {
            if res.status().is_success() {
                if let Ok(data) = res.json::<OllamaPsResponse>().await {
                    return data.models.unwrap_or_default();
                }
            }
        }
        vec![]
    }

    /// Pre-load model weights into VRAM without generating text (POST /api/generate)
    pub async fn load_model(&self, model: &str, keep_alive: Option<&str>) -> Result<(), String> {
        let url = format!("{}/api/generate", self.endpoint);
        let ka = keep_alive.unwrap_or("15m");
        let body = json!({
            "model": model,
            "keep_alive": ka,
        });

        let res = self.client
            .post(&url)
            .json(&body)
            .send()
            .await
            .map_err(|e| e.to_string())?;

        if res.status().is_success() {
            info!("Model '{}' pre-loaded into VRAM with keep_alive={}", model, ka);
            Ok(())
        } else {
            Err(format!("Ollama returned status {}", res.status()))
        }
    }

    /// Evict model weights from VRAM immediately by setting keep_alive: 0
    pub async fn unload_model(&self, model: &str) -> Result<(), String> {
        let url = format!("{}/api/generate", self.endpoint);
        let body = json!({
            "model": model,
            "keep_alive": 0,
        });

        let res = self.client
            .post(&url)
            .json(&body)
            .send()
            .await
            .map_err(|e| e.to_string())?;

        if res.status().is_success() {
            info!("Unloaded model '{}' from VRAM successfully", model);
            Ok(())
        } else {
            Err(format!("Ollama returned status {}", res.status()))
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

    pub async fn stream_to_stdout(
        &self,
        model: &str,
        prompt: &str,
        system_prompt: Option<&str>,
        temperature: Option<f32>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        use std::io::Write;
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

        let start_time = std::time::Instant::now();
        let res = self.client.post(&url).json(&body).send().await?;
        if !res.status().is_success() {
            eprintln!("\x1b[31m[Error]\x1b[0m Ollama returned HTTP {}", res.status());
            return Ok(());
        }

        let mut stream = res.bytes_stream();
        let mut stdout = std::io::stdout();
        let mut eval_count = 0u64;

        while let Some(chunk_res) = stream.next().await {
            if let Ok(bytes) = chunk_res {
                if let Ok(chunk) = serde_json::from_slice::<OllamaChatChunk>(&bytes) {
                    if let Some(msg) = chunk.message {
                        print!("{}", msg.content);
                        let _ = stdout.flush();
                    }
                    if let Some(cnt) = chunk.eval_count {
                        eval_count = cnt;
                    }
                    if chunk.done.unwrap_or(false) {
                        break;
                    }
                }
            }
        }
        println!();

        let elapsed = start_time.elapsed();
        if eval_count > 0 && elapsed.as_secs_f64() > 0.0 {
            let tok_sec = eval_count as f64 / elapsed.as_secs_f64();
            println!("\x1b[90m[{:.1} tok/s • {} tokens • {:.2}s]\x1b[0m\n", tok_sec, eval_count, elapsed.as_secs_f64());
        }

        Ok(())
    }

    pub async fn run_interactive_repl(
        &self,
        model_override: Option<String>,
        hub_url: Option<String>,
        pin: Option<String>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        use std::io::{self, BufRead, Write};

        let telemetry = self.check_models().await;
        let mut active_model = model_override
            .or_else(|| telemetry.models.first().cloned())
            .unwrap_or_else(|| "llama3.2:3b".to_string());

        let mut system_prompt: Option<String> = None;
        let mut temperature: Option<f32> = Some(0.7);
        let hub = hub_url.unwrap_or_else(|| "wss://faizal.uk/api/mesh/ws".into());

        // Print Retro ASCII Gradient Banner & Welcome Box
        banner::print_banner();
        banner::print_welcome_box();

        let loaded = self.poll_loaded_models().await;
        let vram_info = if let Some(m) = loaded.iter().find(|p| p.name == active_model || p.model == active_model) {
            let mb = m.size_vram.unwrap_or(0) as f64 / 1_048_576.0;
            format!("VRAM: {:.1} MB (Active)", mb)
        } else {
            "VRAM: Inactive (Cold)".to_string()
        };

        banner::print_prompt_box(&active_model, &vram_info);
        println!();

        let stdin = io::stdin();
        let mut handle = stdin.lock();

        loop {
            print!("\x1b[38;2;255;110;180m❯ \x1b[0m");
            let _ = io::stdout().flush();

            let mut input = String::new();
            if handle.read_line(&mut input)? == 0 {
                break;
            }

            let trimmed = input.trim();
            if trimmed.is_empty() {
                continue;
            }

            // Slash Commands
            if trimmed == "/bye" || trimmed == "/exit" || trimmed == "exit" || trimmed == "quit" {
                println!("\x1b[90mSession ended. Have a great day!\x1b[0m");
                break;
            }

            if trimmed == "/help" || trimmed == "/?" {
                helpbook::print_manual();
                continue;
            }

            if trimmed == "/cluster" || trimmed == "/nodes" {
                crate::modules::cluster_ops::ClusterOps::print_cluster_nodes(&hub, pin.as_deref()).await;
                println!();
                continue;
            }

            if trimmed == "/services" || trimmed == "/apps" || trimmed == "/games" {
                crate::modules::cluster_ops::ClusterOps::print_services(&hub, pin.as_deref()).await;
                println!();
                continue;
            }

            if trimmed.starts_with("/restart ") {
                let s_id = trimmed.trim_start_matches("/restart ").trim();
                crate::modules::cluster_ops::ClusterOps::control_service(&hub, pin.as_deref(), s_id, "restart").await;
                println!();
                continue;
            }

            if trimmed == "/health" {
                crate::modules::cluster_ops::ClusterOps::print_health_report(&hub, pin.as_deref()).await;
                println!();
                continue;
            }

            if trimmed == "/clear" {
                print!("\x1b[2J\x1b[1;1H");
                let _ = io::stdout().flush();
                banner::print_prompt_box(&active_model, "Screen cleared");
                continue;
            }

            if trimmed == "/models" {
                let latest = self.check_models().await;
                println!("\x1b[1;36m=== Local Installed Ollama Models ===\x1b[0m");
                if latest.models.is_empty() {
                    println!("  \x1b[90m(No models found on Ollama at {})\x1b[0m", self.endpoint);
                } else {
                    for m in latest.models {
                        let indicator = if m == active_model { " \x1b[1;33m(active)\x1b[0m" } else { "" };
                        println!("  • \x1b[32m{}\x1b[0m{}", m, indicator);
                    }
                }
                println!();
                continue;
            }

            if trimmed == "/ps" || trimmed == "/vram" {
                let procs = self.poll_loaded_models().await;
                println!("\x1b[1;36m=== Models Currently in VRAM / RAM (Ollama /api/ps) ===\x1b[0m");
                if procs.is_empty() {
                    println!("  \x1b[90m(No models currently loaded in VRAM. Cold start required)\x1b[0m");
                } else {
                    for p in procs {
                        let total_mb = p.size.unwrap_or(0) as f64 / 1_048_576.0;
                        let vram_mb = p.size_vram.unwrap_or(0) as f64 / 1_048_576.0;
                        let exp = p.expires_at.as_deref().unwrap_or("never");
                        println!("  • \x1b[1;32m{}\x1b[0m: VRAM: \x1b[33m{:.1} MB\x1b[0m / Total: {:.1} MB | Expires: {}", p.name, vram_mb, total_mb, exp);
                    }
                }
                println!();
                continue;
            }

            if trimmed.starts_with("/load ") {
                let parts: Vec<&str> = trimmed.split_whitespace().collect();
                if parts.len() < 2 {
                    println!("\x1b[31mUsage: /load <model_name> [duration, e.g. 15m, 1h, -1]\x1b[0m\n");
                    continue;
                }
                let target_model = parts[1];
                let dur = if parts.len() > 2 { Some(parts[2]) } else { Some("15m") };
                print!("\x1b[90mPre-loading '{}' into VRAM (keep_alive: {})... \x1b[0m", target_model, dur.unwrap());
                let _ = io::stdout().flush();
                match self.load_model(target_model, dur).await {
                    Ok(_) => {
                        println!("\x1b[32mDone! Model is warm in VRAM.\x1b[0m\n");
                        active_model = target_model.to_string();
                    }
                    Err(e) => println!("\x1b[31mFailed: {}\x1b[0m\n", e),
                }
                continue;
            }

            if trimmed.starts_with("/unload ") {
                let parts: Vec<&str> = trimmed.split_whitespace().collect();
                if parts.len() < 2 {
                    println!("\x1b[31mUsage: /unload <model_name>\x1b[0m\n");
                    continue;
                }
                let target_model = parts[1];
                print!("\x1b[90mUnloading '{}' from VRAM (keep_alive: 0)... \x1b[0m", target_model);
                let _ = io::stdout().flush();
                match self.unload_model(target_model).await {
                    Ok(_) => println!("\x1b[32mDone! Memory freed.\x1b[0m\n"),
                    Err(e) => println!("\x1b[31mFailed: {}\x1b[0m\n", e),
                }
                continue;
            }

            if trimmed.starts_with("/set model ") {
                let m = trimmed.trim_start_matches("/set model ").trim();
                active_model = m.to_string();
                println!("\x1b[32mActive model switched to: {}\x1b[0m\n", active_model);
                continue;
            }

            if trimmed.starts_with("/set temp ") {
                let val_str = trimmed.trim_start_matches("/set temp ").trim();
                if let Ok(t) = val_str.parse::<f32>() {
                    temperature = Some(t);
                    println!("\x1b[32mTemperature set to: {:.2}\x1b[0m\n", t);
                } else {
                    println!("\x1b[31mInvalid temperature value.\x1b[0m\n");
                }
                continue;
            }

            if trimmed.starts_with("/system ") {
                let sys = trimmed.trim_start_matches("/system ").trim();
                if sys.is_empty() {
                    system_prompt = None;
                    println!("\x1b[32mSystem prompt cleared.\x1b[0m\n");
                } else {
                    system_prompt = Some(sys.to_string());
                    println!("\x1b[32mSystem prompt updated.\x1b[0m\n");
                }
                continue;
            }

            if trimmed == "/status" {
                let info = self.check_models().await;
                println!("\x1b[1;36m=== Nexus AI Engine Status ===\x1b[0m");
                println!("  Engine:   Ollama");
                println!("  Endpoint: {}", self.endpoint);
                println!("  Status:   {}", info.status);
                println!("  Active:   {}", active_model);
                println!();
                continue;
            }

            // Normal prompt execution
            print!("\x1b[38;2;130;200;255m");
            let _ = io::stdout().flush();
            let _ = self.stream_to_stdout(
                &active_model,
                trimmed,
                system_prompt.as_deref(),
                temperature,
            ).await;
            print!("\x1b[0m");
        }

        Ok(())
    }
}
