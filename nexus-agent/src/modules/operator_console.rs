use crate::modules::ai_bridge::AiBridge;
use crate::modules::banner;
use crate::modules::helpbook;
use futures_util::StreamExt;
use reqwest::header::{HeaderMap, HeaderValue, AUTHORIZATION};
use reqwest::Client;
use serde::{Deserialize, Serialize};
use serde_json::json;
use std::collections::HashMap;
use std::io::{self, BufRead, Write};
use std::time::{Duration, Instant};

pub fn get_http_hub_url(hub_ws_url: &str) -> String {
    let mut url = hub_ws_url.to_string();
    if url.starts_with("wss://") {
        url = url.replacen("wss://", "https://", 1);
    } else if url.starts_with("ws://") {
        url = url.replacen("ws://", "http://", 1);
    }
    if let Some(pos) = url.find("/api/mesh/ws") {
        url.truncate(pos);
    }
    url.trim_end_matches('/').to_string()
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct NodeInfo {
    pub node_id: String,
    pub hostname: String,
    pub os: String,
    pub os_version: String,
    pub arch: String,
    pub agent_version: String,
    pub status: String,
    pub last_seen_secs: u64,
    pub features: HashMap<String, bool>,
    pub models: Vec<String>,
    pub telemetry: Option<serde_json::Value>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct ChatMessageRecord {
    pub id: i64,
    pub chat_id: String,
    pub sender_id: String,
    pub sender_name: String,
    pub recipient_id: Option<String>,
    pub content: String,
    pub message_type: String,
    pub created_at: String,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct AiConversationRecord {
    pub id: String,
    pub title: String,
    pub target_node_id: String,
    pub model: String,
    pub system_prompt: Option<String>,
    pub temperature: f64,
    pub created_at: String,
    pub updated_at: String,
    pub message_count: Option<i64>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct AiMessageRecord {
    pub id: i64,
    pub conversation_id: String,
    pub role: String,
    pub content: String,
    pub node_id: Option<String>,
    pub created_at: String,
}

#[derive(Debug, Clone)]
pub enum ConsoleMode {
    Chat,
    Ai(Option<String>),
    Local(Option<String>),
}

pub struct OperatorConsole {
    http_hub: String,
    pin: Option<String>,
    client: Client,
    local_ai: AiBridge,
    local_hostname: String,
    local_node_id: String,
}

impl OperatorConsole {
    pub fn new(hub_ws_url: &str, pin: Option<&str>, local_ai_endpoint: &str, node_id: &str, hostname: &str) -> Self {
        let http_hub = get_http_hub_url(hub_ws_url);
        let mut headers = HeaderMap::new();
        if let Some(p) = pin {
            if let Ok(val) = HeaderValue::from_str(&format!("Bearer {}", p)) {
                headers.insert(AUTHORIZATION, val);
            }
            if let Ok(val) = HeaderValue::from_str(p) {
                headers.insert("x-admin-pin", val);
            }
        }

        let client = Client::builder()
            .default_headers(headers)
            .timeout(Duration::from_secs(300))
            .build()
            .unwrap_or_default();

        Self {
            http_hub,
            pin: pin.map(|s| s.to_string()),
            client,
            local_ai: AiBridge::new(local_ai_endpoint),
            local_hostname: hostname.to_string(),
            local_node_id: node_id.to_string(),
        }
    }

    pub async fn run(
        &self,
        mode: Option<String>,
        model_override: Option<String>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        banner::print_banner();

        let mut current_mode = match mode.as_deref() {
            Some("chat") | Some("1") => ConsoleMode::Chat,
            Some("ai") | Some("2") => ConsoleMode::Ai(model_override),
            Some("local") | Some("3") => ConsoleMode::Local(model_override),
            _ => {
                println!("\x1b[1;36m╭─ SELECT CONSOLE MODE ────────────────────────────────────────────────────────╮\x1b[0m");
                println!("│  \x1b[1;32m[1]\x1b[0m 💬 \x1b[1mFleet Chat\x1b[0m (Global Broadcast & Device-to-Device Messaging)          │");
                println!("│  \x1b[1;32m[2]\x1b[0m 🤖 \x1b[1mDistributed Nexus AI Studio\x1b[0m (Fleet Compute Engine) \x1b[1;33m[Default]\x1b[0m         │");
                println!("│  \x1b[1;32m[3]\x1b[0m 🖥️ \x1b[1mStandalone Local Terminal AI\x1b[0m (Offline / Local Ollama)               │");
                println!("\x1b[1;36m╰──────────────────────────────────────────────────────────────────────────────╯\x1b[0m");
                print!("\x1b[1;35mSelect mode [1-3] (press Enter for [2]): \x1b[0m");
                let _ = io::stdout().flush();

                let mut choice = String::new();
                let stdin = io::stdin();
                stdin.lock().read_line(&mut choice)?;
                let choice = choice.trim();

                match choice {
                    "1" => ConsoleMode::Chat,
                    "3" => ConsoleMode::Local(model_override),
                    _ => ConsoleMode::Ai(model_override),
                }
            }
        };

        loop {
            match current_mode {
                ConsoleMode::Chat => {
                    match self.run_fleet_chat().await? {
                        Some(next) => current_mode = next,
                        None => break,
                    }
                }
                ConsoleMode::Ai(model) => {
                    match self.run_ai_studio(model).await? {
                        Some(next) => current_mode = next,
                        None => break,
                    }
                }
                ConsoleMode::Local(model) => {
                    self.local_ai.run_interactive_repl(model, Some(self.http_hub.clone()), self.pin.clone()).await?;
                    break;
                }
            }
        }

        Ok(())
    }

    // ─── MODE 1: FLEET CHAT ────────────────────────────────────────────────────
    pub async fn run_fleet_chat(&self) -> Result<Option<ConsoleMode>, Box<dyn std::error::Error>> {
        let mut active_channel = "global".to_string();

        println!("\x1b[1;35m╭─ 💬 FLEET CHAT CONSOLE ───────────────────────────────────────────────────╮\x1b[0m");
        println!("│ Channel: \x1b[1;32m#{}\x1b[0m | Hub: \x1b[36m{}\x1b[0m", active_channel, self.http_hub);
        println!("│ Type a message and press Enter. Available commands:                      │");
        println!("│   \x1b[1;33m/join <#channel or @node>\x1b[0m  Switch active channel                         │");
        println!("│   \x1b[1;33m/channels\x1b[0m                  List #global & online fleet devices           │");
        println!("│   \x1b[1;33m/refresh\x1b[0m                   Fetch latest message updates                  │");
        println!("│   \x1b[1;33m/mode ai\x1b[0m                   Switch to Distributed AI Studio               │");
        println!("│   \x1b[1;33m/clear\x1b[0m                     Clear terminal screen                         │");
        println!("│   \x1b[1;33m/exit\x1b[0m                      Quit console                                  │");
        println!("\x1b[1;35m╰───────────────────────────────────────────────────────────────────────────╯\x1b[0m\n");

        self.display_chat_history(&active_channel).await;

        let stdin = io::stdin();
        let mut handle = stdin.lock();

        loop {
            let prompt_label = if active_channel == "global" {
                "#global".to_string()
            } else {
                format!("@{}", active_channel)
            };

            print!("\x1b[38;2;130;200;255m[{}] ❯ \x1b[0m", prompt_label);
            let _ = io::stdout().flush();

            let mut input = String::new();
            if handle.read_line(&mut input)? == 0 {
                break;
            }

            let trimmed = input.trim();
            if trimmed.is_empty() {
                continue;
            }

            if trimmed == "/exit" || trimmed == "/bye" || trimmed == "exit" || trimmed == "quit" {
                println!("\x1b[90mSession ended.\x1b[0m");
                break;
            }

            if trimmed == "/mode ai" {
                return Ok(Some(ConsoleMode::Ai(None)));
            }

            if trimmed == "/mode local" {
                return Ok(Some(ConsoleMode::Local(None)));
            }

            if trimmed == "/clear" {
                print!("\x1b[2J\x1b[1;1H");
                let _ = io::stdout().flush();
                continue;
            }

            if trimmed == "/refresh" {
                self.display_chat_history(&active_channel).await;
                continue;
            }

            if trimmed.starts_with("/join ") {
                let chan = trimmed.trim_start_matches("/join ").trim().trim_start_matches('#').trim_start_matches('@');
                active_channel = chan.to_string();
                println!("\x1b[32mSwitched to channel: #{}\x1b[0m\n", active_channel);
                self.display_chat_history(&active_channel).await;
                continue;
            }

            if trimmed == "/channels" || trimmed == "/rooms" {
                self.print_available_channels().await;
                continue;
            }

            // Send Chat Message
            let is_dm = active_channel != "global";
            let recipient_id = if is_dm { Some(active_channel.clone()) } else { None };

            let payload = json!({
                "chat_id": active_channel,
                "sender_id": self.local_node_id,
                "sender_name": self.local_hostname,
                "recipient_id": recipient_id,
                "content": trimmed,
                "message_type": "text"
            });

            let url = format!("{}/api/fleet/chat/messages", self.http_hub);
            match self.client.post(&url).json(&payload).send().await {
                Ok(res) if res.status().is_success() => {
                    let now = chrono::Local::now().format("%H:%M:%S");
                    println!("\x1b[90m[{}] \x1b[1;32m<{}>\x1b[0m {}", now, self.local_hostname, trimmed);
                }
                Ok(res) => {
                    println!("\x1b[31m[Failed to send message: HTTP {}]\x1b[0m", res.status());
                }
                Err(e) => {
                    println!("\x1b[31m[Network error sending message: {}]\x1b[0m", e);
                }
            }
        }

        Ok(None)
    }

    async fn display_chat_history(&self, chat_id: &str) {
        let url = format!("{}/api/fleet/chat/messages?chat_id={}&limit=20", self.http_hub, chat_id);
        match self.client.get(&url).send().await {
            Ok(res) if res.status().is_success() => {
                if let Ok(messages) = res.json::<Vec<ChatMessageRecord>>().await {
                    if messages.is_empty() {
                        println!("\x1b[90m(No messages in channel '#{}'. Start the conversation!)\x1b[0m\n", chat_id);
                    } else {
                        for m in messages {
                            let time = if m.created_at.len() >= 19 {
                                &m.created_at[11..19]
                            } else {
                                &m.created_at
                            };
                            let sender_color = if m.sender_id == self.local_node_id {
                                "\x1b[1;32m"
                            } else {
                                "\x1b[1;36m"
                            };
                            println!("\x1b[90m[{}]\x1b[0m {}<{}>\x1b[0m {}", time, sender_color, m.sender_name, m.content);
                        }
                        println!();
                    }
                }
            }
            _ => {
                println!("\x1b[90m(Unable to fetch message history from hub)\x1b[0m\n");
            }
        }
    }

    async fn print_available_channels(&self) {
        let url = format!("{}/api/fleet/nodes", self.http_hub);
        println!("\x1b[1;36m=== Available Fleet Chat Channels ===\x1b[0m");
        println!("  • \x1b[1;32m#global\x1b[0m  (Broadcast to all nodes and admin consoles)");

        if let Ok(res) = self.client.get(&url).send().await {
            if let Ok(nodes) = res.json::<Vec<NodeInfo>>().await {
                for n in nodes {
                    let status = if n.status == "online" { "\x1b[32m● ONLINE\x1b[0m" } else { "\x1b[90m○ OFFLINE\x1b[0m" };
                    println!("  • \x1b[1;33m@{:<20}\x1b[0m [Node: {} | {}]", n.hostname, n.node_id, status);
                }
            }
        }
        println!("\x1b[90mUse '/join <#channel or @node>' to switch channels.\x1b[0m\n");
    }

    // ─── MODE 2: DISTRIBUTED NEXUS AI STUDIO ───────────────────────────────────
    pub async fn run_ai_studio(&self, initial_model: Option<String>) -> Result<Option<ConsoleMode>, Box<dyn std::error::Error>> {
        println!("\x1b[1;35mConnecting to Nexus Central Hub:\x1b[0m {}\n", self.http_hub);

        let nodes = match self.fetch_nodes().await {
            Ok(n) => n,
            Err(e) => {
                println!("\x1b[33m⚠️ Central Hub unreachable ({}). Falling back to Standalone Local AI.\x1b[0m\n", e);
                return Ok(Some(ConsoleMode::Local(initial_model)));
            }
        };

        self.print_node_fleet_table(&nodes);

        // Pick initial target node: first online node with models, or first online node
        let mut target_node = nodes.iter().find(|n| n.status == "online" && !n.models.is_empty())
            .or_else(|| nodes.iter().find(|n| n.status == "online"))
            .cloned()
            .unwrap_or_else(|| {
                nodes.first().cloned().unwrap_or(NodeInfo {
                    node_id: "local".into(),
                    hostname: "localhost".into(),
                    os: "unknown".into(),
                    os_version: "".into(),
                    arch: "".into(),
                    agent_version: "".into(),
                    status: "offline".into(),
                    last_seen_secs: 0,
                    features: HashMap::new(),
                    models: vec![],
                    telemetry: None,
                })
            });

        let mut active_model = initial_model
            .or_else(|| target_node.models.first().cloned())
            .unwrap_or_else(|| "qwen2.5-coder:7b".into());

        let mut active_conversation_id: Option<String> = None;
        let mut system_prompt: Option<String> = None;
        let mut temperature: Option<f32> = Some(0.7);

        self.print_target_status_bar(&target_node, &active_model);

        let stdin = io::stdin();
        let mut handle = stdin.lock();

        loop {
            print!("\x1b[38;2;255;110;180m[{}] ❯ \x1b[0m", target_node.hostname);
            let _ = io::stdout().flush();

            let mut input = String::new();
            if handle.read_line(&mut input)? == 0 {
                break;
            }

            let trimmed = input.trim();
            if trimmed.is_empty() {
                continue;
            }

            if trimmed == "/exit" || trimmed == "/bye" || trimmed == "exit" || trimmed == "quit" {
                println!("\x1b[90mSession ended. Have a great day!\x1b[0m");
                break;
            }

            if trimmed == "/help" || trimmed == "/?" {
                helpbook::print_manual();
                continue;
            }

            if trimmed == "/mode chat" {
                return Ok(Some(ConsoleMode::Chat));
            }

            if trimmed == "/mode local" {
                return Ok(Some(ConsoleMode::Local(Some(active_model))));
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

            if trimmed.starts_with("/temp ") {
                let t_str = trimmed.trim_start_matches("/temp ").trim();
                if let Ok(t) = t_str.parse::<f32>() {
                    temperature = Some(t);
                    println!("\x1b[32mTemperature set to {:.2}.\x1b[0m\n", t);
                } else {
                    println!("\x1b[31mInvalid temperature value.\x1b[0m\n");
                }
                continue;
            }

            if trimmed == "/nodes" || trimmed == "/devices" || trimmed == "/cluster" {
                if let Ok(refreshed) = self.fetch_nodes().await {
                    self.print_node_fleet_table(&refreshed);
                    if let Some(updated) = refreshed.iter().find(|n| n.node_id == target_node.node_id) {
                        target_node = updated.clone();
                    }
                }
                continue;
            }

            if trimmed.starts_with("/switch ") {
                let target = trimmed.trim_start_matches("/switch ").trim();
                if let Ok(refreshed) = self.fetch_nodes().await {
                    let found = if let Ok(idx) = target.parse::<usize>() {
                        if idx > 0 && idx <= refreshed.len() {
                            Some(refreshed[idx - 1].clone())
                        } else {
                            None
                        }
                    } else {
                        refreshed.iter().find(|n| n.node_id == target || n.hostname.to_lowercase() == target.to_lowercase()).cloned()
                    };

                    match found {
                        Some(node) => {
                            target_node = node;
                            println!("\x1b[32mTarget compute node switched to: {} ({})\x1b[0m", target_node.hostname, target_node.node_id);
                            if !target_node.models.is_empty() && !target_node.models.contains(&active_model) {
                                active_model = target_node.models.first().unwrap().clone();
                            }
                            self.print_target_status_bar(&target_node, &active_model);
                        }
                        None => println!("\x1b[31mNode '{}' not found in fleet. Run '/nodes' to list.\x1b[0m\n", target),
                    }
                }
                continue;
            }

            if trimmed.starts_with("/model ") || trimmed.starts_with("/set model ") {
                let m = if trimmed.starts_with("/model ") {
                    trimmed.trim_start_matches("/model ").trim()
                } else {
                    trimmed.trim_start_matches("/set model ").trim()
                };
                active_model = m.to_string();
                println!("\x1b[32mActive model switched to: {}\x1b[0m\n", active_model);
                continue;
            }

            if trimmed.starts_with("/pull ") {
                let m = trimmed.trim_start_matches("/pull ").trim();
                println!("\x1b[90mDispatching remote pull for '{}' on node '{}'...\x1b[0m", m, target_node.hostname);
                let url = format!("{}/api/fleet/nodes/{}/pull", self.http_hub, target_node.node_id);
                let payload = json!({ "model": m });
                match self.client.post(&url).json(&payload).send().await {
                    Ok(res) if res.status().is_success() => {
                        println!("\x1b[32mModel pull initiated successfully on {}.\x1b[0m\n", target_node.hostname);
                    }
                    Ok(res) => println!("\x1b[31mPull request returned status {}\x1b[0m\n", res.status()),
                    Err(e) => println!("\x1b[31mNetwork error: {}\x1b[0m\n", e),
                }
                continue;
            }

            if trimmed.starts_with("/load ") {
                let parts: Vec<&str> = trimmed.split_whitespace().collect();
                if parts.len() < 2 {
                    println!("\x1b[31mUsage: /load <model_name> [duration, e.g. 15m, 1h, -1]\x1b[0m\n");
                    continue;
                }
                let target_m = parts[1];
                let dur = if parts.len() > 2 { parts[2] } else { "15m" };
                println!("\x1b[90mPre-loading '{}' into VRAM on '{}' (keep_alive: {})...\x1b[0m", target_m, target_node.hostname, dur);
                let url = format!("{}/api/fleet/nodes/{}/load", self.http_hub, target_node.node_id);
                let payload = json!({ "model": target_m, "keep_alive": dur });
                match self.client.post(&url).json(&payload).send().await {
                    Ok(res) if res.status().is_success() => {
                        println!("\x1b[32mDone! Model is warm in VRAM on {}.\x1b[0m\n", target_node.hostname);
                        active_model = target_m.to_string();
                    }
                    Ok(res) => println!("\x1b[31mLoad failed with HTTP {}\x1b[0m\n", res.status()),
                    Err(e) => println!("\x1b[31mNetwork error: {}\x1b[0m\n", e),
                }
                continue;
            }

            if trimmed.starts_with("/unload ") {
                let parts: Vec<&str> = trimmed.split_whitespace().collect();
                if parts.len() < 2 {
                    println!("\x1b[31mUsage: /unload <model_name>\x1b[0m\n");
                    continue;
                }
                let target_m = parts[1];
                println!("\x1b[90mUnloading '{}' from VRAM on '{}'...\x1b[0m", target_m, target_node.hostname);
                let url = format!("{}/api/fleet/nodes/{}/unload", self.http_hub, target_node.node_id);
                let payload = json!({ "model": target_m });
                match self.client.post(&url).json(&payload).send().await {
                    Ok(res) if res.status().is_success() => println!("\x1b[32mDone! VRAM memory freed on {}.\x1b[0m\n", target_node.hostname),
                    Ok(res) => println!("\x1b[31mUnload failed with HTTP {}\x1b[0m\n", res.status()),
                    Err(e) => println!("\x1b[31mNetwork error: {}\x1b[0m\n", e),
                }
                continue;
            }

            if trimmed == "/history" {
                self.list_conversations().await;
                continue;
            }

            if trimmed.starts_with("/resume ") {
                let conv_id = trimmed.trim_start_matches("/resume ").trim();
                match self.resume_conversation(conv_id).await {
                    Ok(loaded_id) => {
                        active_conversation_id = Some(loaded_id);
                        println!("\x1b[32mResumed conversation {}.\x1b[0m\n", active_conversation_id.as_deref().unwrap());
                    }
                    Err(e) => println!("\x1b[31mFailed to resume conversation: {}\x1b[0m\n", e),
                }
                continue;
            }

            if trimmed == "/clear" || trimmed == "/new" {
                active_conversation_id = None;
                print!("\x1b[2J\x1b[1;1H");
                let _ = io::stdout().flush();
                println!("\x1b[32mStarted new conversation session.\x1b[0m\n");
                self.print_target_status_bar(&target_node, &active_model);
                continue;
            }

            // ─── EXECUTE DISTRIBUTED PROMPT VIA CENTRAL HUB & SQLITE ───
            // 1. Guard against offline target
            if target_node.status != "online" {
                println!("\x1b[31m❌ Cannot send prompt: target node '{}' is OFFLINE.\x1b[0m", target_node.hostname);
                println!("\x1b[90mPlease run '/nodes' and use '/switch <node>' to choose an online node.\x1b[0m\n");
                continue;
            }

            // 2. Guard against 0 models
            if target_node.models.is_empty() {
                println!("\x1b[33m⚠️ Target node '{}' has NO models installed!\x1b[0m", target_node.hostname);
                println!("\x1b[90mRun '/pull <model>' (e.g. '/pull qwen2.5-coder:1.5b') or '/switch <node>' to proceed.\x1b[0m\n");
                continue;
            }

            // Ensure conversation exists in SQLite
            if active_conversation_id.is_none() {
                let conv_url = format!("{}/api/fleet/ai/conversations", self.http_hub);
                let title = if trimmed.len() > 32 {
                    format!("{}...", &trimmed[..32])
                } else {
                    trimmed.to_string()
                };

                let create_payload = json!({
                    "title": title,
                    "target_node_id": target_node.node_id,
                    "model": active_model,
                    "system_prompt": system_prompt,
                    "temperature": temperature.unwrap_or(0.7)
                });

                if let Ok(res) = self.client.post(&conv_url).json(&create_payload).send().await {
                    if let Ok(created) = res.json::<serde_json::Value>().await {
                        if let Some(id_str) = created.get("id").and_then(|v| v.as_str()) {
                            active_conversation_id = Some(id_str.to_string());
                        }
                    }
                }
            }

            let conv_id = match &active_conversation_id {
                Some(id) => id.clone(),
                None => {
                    println!("\x1b[31mError creating conversation on central hub.\x1b[0m\n");
                    continue;
                }
            };

            // Stream response via SSE from /api/fleet/ai/conversations/:id/messages
            print!("\x1b[38;2;130;200;255m");
            let _ = io::stdout().flush();

            let msg_url = format!("{}/api/fleet/ai/conversations/{}/messages", self.http_hub, conv_id);
            let msg_payload = json!({
                "content": trimmed,
                "model": active_model,
                "system_prompt": system_prompt,
                "temperature": temperature
            });

            let start_time = Instant::now();
            let resp = self.client.post(&msg_url).json(&msg_payload).send().await;

            match resp {
                Ok(res) if res.status().is_success() => {
                    let mut stream = res.bytes_stream();
                    let mut token_count = 0u64;

                    while let Some(chunk_res) = stream.next().await {
                        if let Ok(bytes) = chunk_res {
                            if let Ok(chunk_str) = std::str::from_utf8(&bytes) {
                                for line in chunk_str.lines() {
                                    if line.starts_with("data: ") {
                                        let data = &line[6..];
                                        if data == "[DONE]" {
                                            break;
                                        }
                                        print!("{}", data);
                                        let _ = io::stdout().flush();
                                        token_count += 1;
                                    }
                                }
                            }
                        }
                    }
                    print!("\x1b[0m\n");

                    let elapsed = start_time.elapsed().as_secs_f64();
                    if token_count > 0 && elapsed > 0.0 {
                        let tok_sec = token_count as f64 / elapsed;
                        println!("\x1b[90m[{:.1} tok/s • {} tokens • {:.2}s via {} ({})]\x1b[0m\n", tok_sec, token_count, elapsed, target_node.hostname, active_model);
                    }
                }
                Ok(res) => {
                    print!("\x1b[0m");
                    println!("\x1b[31m[Hub returned HTTP {}]\x1b[0m\n", res.status());
                }
                Err(e) => {
                    print!("\x1b[0m");
                    println!("\x1b[31m[Streaming error: {}]\x1b[0m\n", e);
                }
            }
        }

        Ok(None)
    }

    async fn fetch_nodes(&self) -> Result<Vec<NodeInfo>, String> {
        let url = format!("{}/api/fleet/nodes", self.http_hub);
        let res = self.client.get(&url).send().await.map_err(|e| e.to_string())?;
        if res.status().is_success() {
            res.json::<Vec<NodeInfo>>().await.map_err(|e| e.to_string())
        } else {
            Err(format!("HTTP {}", res.status()))
        }
    }

    fn print_node_fleet_table(&self, nodes: &[NodeInfo]) {
        println!("\x1b[1;36m╭─ AVAILABLE FLEET COMPUTE NODES ──────────────────────────────────────────────╮\x1b[0m");
        if nodes.is_empty() {
            println!("│  \x1b[90m(No nodes connected to mesh)\x1b[0m");
        } else {
            for (idx, n) in nodes.iter().enumerate() {
                let status_bullet = if n.status == "online" {
                    "\x1b[1;32m● ONLINE\x1b[0m"
                } else {
                    "\x1b[90m○ OFFLINE\x1b[0m"
                };

                let mut ram_info = "RAM: N/A".to_string();
                let mut vram_info = "".to_string();

                if let Some(tel) = &n.telemetry {
                    if let Some(mem) = tel.get("memory") {
                        let total = mem.get("total_mb").and_then(|v| v.as_f64()).unwrap_or(0.0) / 1024.0;
                        let used = mem.get("used_mb").and_then(|v| v.as_f64()).unwrap_or(0.0) / 1024.0;
                        let free = total - used;
                        ram_info = format!("RAM: {:.1}/{:.1} GB ({:.1} GB free)", used, total, free);
                    }
                    if let Some(gpus) = tel.get("gpu").and_then(|v| v.as_array()) {
                        if let Some(g) = gpus.first() {
                            let g_name = g.get("name").and_then(|v| v.as_str()).unwrap_or("GPU");
                            let v_total = g.get("vram_total_mb").and_then(|v| v.as_f64()).unwrap_or(0.0) / 1024.0;
                            let v_used = g.get("vram_used_mb").and_then(|v| v.as_f64()).unwrap_or(0.0) / 1024.0;
                            let v_free = v_total - v_used;
                            vram_info = format!(" | GPU: {} ({:.1}/{:.1} GB VRAM, {:.1} GB free)", g_name, v_used, v_total, v_free);
                        }
                    }
                }

                let models_str = if n.models.is_empty() {
                    "\x1b[90m(none installed)\x1b[0m".to_string()
                } else {
                    format!("\x1b[32m{}\x1b[0m", n.models.join(", "))
                };

                println!("│  \x1b[1;33m[{}]\x1b[0m {} \x1b[1m{:<18}\x1b[0m [{}]", idx + 1, status_bullet, n.hostname, n.os);
                println!("│      ├─ {}{}", ram_info, vram_info);
                println!("│      └─ Models: {}", models_str);
            }
        }
        println!("\x1b[1;36m╰──────────────────────────────────────────────────────────────────────────────╯\x1b[0m\n");
    }

    fn print_target_status_bar(&self, node: &NodeInfo, model: &str) {
        println!("\x1b[1;32m╭─ ACTIVE COMPUTE TARGET ──────────────────────────────────────────────────────╮\x1b[0m");
        println!("│  Node:  \x1b[1m{}\x1b[0m ({}) | Status: {}", node.hostname, node.node_id, if node.status == "online" { "\x1b[32mONLINE\x1b[0m" } else { "\x1b[31mOFFLINE\x1b[0m" });
        println!("│  Model: \x1b[1;33m{}\x1b[0m", model);

        // Edge Case Checks:
        if node.models.is_empty() {
            println!("│  \x1b[1;33m⚠️  NOTICE: No models installed on this node!\x1b[0m");
            println!("│     Run '\x1b[1;32m/pull <model>\x1b[0m' (e.g. '/pull qwen2.5-coder:1.5b') or '\x1b[1;32m/switch <node>\x1b[0m'");
        }

        if let Some(tel) = &node.telemetry {
            if let Some(mem) = tel.get("memory") {
                let total = mem.get("total_mb").and_then(|v| v.as_f64()).unwrap_or(0.0) / 1024.0;
                let used = mem.get("used_mb").and_then(|v| v.as_f64()).unwrap_or(0.0) / 1024.0;
                let free = total - used;
                let pct = if total > 0.0 { (used / total) * 100.0 } else { 0.0 };

                if free < 2.0 || pct > 85.0 {
                    println!("│  \x1b[1;33m⚠️  RESOURCE SAFETY ALERT: Only {:.1} GB free RAM ({:.0}% used)!\x1b[0m", free, pct);
                    println!("│     Loading large models may trigger host freezing or OOM kill.");
                }
            }
        }
        println!("\x1b[1;32m╰──────────────────────────────────────────────────────────────────────────────╯\x1b[0m\n");
    }

    async fn list_conversations(&self) {
        let url = format!("{}/api/fleet/ai/conversations", self.http_hub);
        println!("\x1b[1;36m=== Stored AI Conversations (SQLite faizal.uk) ===\x1b[0m");
        match self.client.get(&url).send().await {
            Ok(res) if res.status().is_success() => {
                if let Ok(convs) = res.json::<Vec<AiConversationRecord>>().await {
                    if convs.is_empty() {
                        println!("  \x1b[90m(No stored conversations found)\x1b[0m");
                    } else {
                        for (i, c) in convs.iter().enumerate() {
                            let msgs = c.message_count.unwrap_or(0);
                            let time = if c.updated_at.len() >= 19 { &c.updated_at[..19] } else { &c.updated_at };
                            println!("  [{}] \x1b[1;32m{:<28}\x1b[0m | Node: {:<16} | {} msgs | {}", i + 1, c.title, c.target_node_id, msgs, time);
                            println!("      ID: \x1b[90m{}\x1b[0m", c.id);
                        }
                        println!("\x1b[90mUse '/resume <id>' to load a conversation session.\x1b[0m\n");
                    }
                }
            }
            _ => println!("  \x1b[31mFailed to fetch conversations from hub.\x1b[0m\n"),
        }
    }

    async fn resume_conversation(&self, id_or_index: &str) -> Result<String, String> {
        let conv_id = if let Ok(idx) = id_or_index.parse::<usize>() {
            let url = format!("{}/api/fleet/ai/conversations", self.http_hub);
            let res = self.client.get(&url).send().await.map_err(|e| e.to_string())?;
            let list = res.json::<Vec<AiConversationRecord>>().await.map_err(|e| e.to_string())?;
            if idx > 0 && idx <= list.len() {
                list[idx - 1].id.clone()
            } else {
                return Err("Index out of range".into());
            }
        } else {
            id_or_index.to_string()
        };

        let msg_url = format!("{}/api/fleet/ai/conversations/{}/messages", self.http_hub, conv_id);
        let res = self.client.get(&msg_url).send().await.map_err(|e| e.to_string())?;
        if res.status().is_success() {
            let messages = res.json::<Vec<AiMessageRecord>>().await.map_err(|e| e.to_string())?;
            println!("\x1b[1;36m=== Conversation Transcript (ID: {}) ===\x1b[0m", conv_id);
            for m in messages {
                if m.role == "user" {
                    println!("\x1b[1;32mUser:\x1b[0m {}", m.content);
                } else {
                    println!("\x1b[1;35mAssistant:\x1b[0m {}\n", m.content);
                }
            }
            Ok(conv_id)
        } else {
            Err(format!("HTTP {}", res.status()))
        }
    }
}
