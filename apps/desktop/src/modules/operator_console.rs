use crate::modules::ai_bridge::AiBridge;
use crate::modules::banner;
use crate::modules::chat_renderer::{ChatRenderer, StreamFormatter};
use crate::modules::chat_storage::{ChatMessageItem, ChatStorage, TurnMetrics};
use futures_util::StreamExt;
use reqwest::header::{HeaderMap, HeaderValue, AUTHORIZATION};
use reqwest::Client;
use serde::{Deserialize, Serialize};
use serde_json::json;
use std::collections::HashMap;
use std::io::{self, BufRead, Write};
use std::path::PathBuf;
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
    Ai {
        node: Option<String>,
        model: Option<String>,
        resume_id: Option<String>,
    },
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

    pub fn set_pin(&mut self, pin: &str) {
        self.pin = Some(pin.to_string());
        let mut headers = HeaderMap::new();
        if let Ok(val) = HeaderValue::from_str(&format!("Bearer {}", pin)) {
            headers.insert(AUTHORIZATION, val);
        }
        if let Ok(val) = HeaderValue::from_str(pin) {
            headers.insert("x-admin-pin", val);
        }
        self.client = Client::builder()
            .default_headers(headers)
            .timeout(Duration::from_secs(300))
            .build()
            .unwrap_or_default();
    }

    pub async fn run(
        &mut self,
        mode: Option<String>,
        node: Option<String>,
        model: Option<String>,
        local: bool,
        resume: Option<String>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        banner::print_chat_banner();

        let mut current_mode = if local {
            ConsoleMode::Local(model)
        } else {
            match mode.as_deref() {
                Some("chat") | Some("1") => ConsoleMode::Chat,
                Some("ai") | Some("2") => ConsoleMode::Ai {
                    node,
                    model,
                    resume_id: resume,
                },
                Some("local") | Some("3") => ConsoleMode::Local(model),
                _ if node.is_some() || model.is_some() || resume.is_some() => {
                    ConsoleMode::Ai {
                        node,
                        model,
                        resume_id: resume,
                    }
                }
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
                        "3" => ConsoleMode::Local(model),
                        _ => ConsoleMode::Ai {
                            node,
                            model,
                            resume_id: resume,
                        },
                    }
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
                ConsoleMode::Ai { node, model, resume_id } => {
                    match self.run_ai_studio(node, model, resume_id).await? {
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
    pub async fn run_fleet_chat(&mut self) -> Result<Option<ConsoleMode>, Box<dyn std::error::Error>> {
        let mut active_channel = "global".to_string();

        println!("\x1b[1;35m╭─ 💬 NEXUS CHAT CONSOLE ───────────────────────────────────────────────────╮\x1b[0m");
        println!("│ Channel: \x1b[1;32m#{}\x1b[0m | Hub: \x1b[36m{}\x1b[0m", active_channel, self.http_hub);
        if self.pin.is_none() {
            println!("│ \x1b[1;33m⚠️  No Admin PIN provided. Hub access restricted (HTTP 403 Forbidden).     \x1b[0m│");
            println!("│ \x1b[90mTip: Type '/pin 2004' (or run 'nexus --pin 2004 chat') to unlock.          \x1b[0m│");
        }
        println!("│ Type a message and press Enter. Available commands:                      │");
        println!("│   \x1b[1;33m/join <#channel or @node>\x1b[0m  Switch active channel                         │");
        println!("│   \x1b[1;33m/channels\x1b[0m                  List #global & online fleet devices           │");
        println!("│   \x1b[1;33m/pin <PIN>\x1b[0m                 Authenticate / unlock cluster chat (e.g. 2004)│");
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
                return Ok(Some(ConsoleMode::Ai {
                    node: None,
                    model: None,
                    resume_id: None,
                }));
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

            if trimmed.starts_with("/pin ") || trimmed.starts_with("/auth ") {
                let p = trimmed.split_whitespace().nth(1).unwrap_or("").trim();
                if !p.is_empty() {
                    self.set_pin(p);
                    let mut cfg = crate::config::AgentConfig::load_or_init();
                    cfg.token = Some(p.to_string());
                    let _ = cfg.save();
                    println!("\x1b[1;32m✓ Admin PIN authenticated and saved. Access granted!\x1b[0m\n");
                    self.display_chat_history(&active_channel).await;
                    continue;
                }
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
                Ok(res) if res.status() == reqwest::StatusCode::FORBIDDEN => {
                    println!("\x1b[31m[Failed to send message: HTTP 403 Forbidden — Admin PIN required]\x1b[0m");
                    println!("\x1b[1;33mTip: Type '/pin 2004' (or your cluster PIN) here to authenticate and retry.\x1b[0m");
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
            Ok(res) if res.status() == reqwest::StatusCode::FORBIDDEN => {
                println!("\x1b[33m[Chat history locked (HTTP 403 Forbidden). Type '/pin 2004' to unlock]\x1b[0m\n");
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
    pub async fn run_ai_studio(
        &mut self,
        node_override: Option<String>,
        model_override: Option<String>,
        resume_id: Option<String>,
    ) -> Result<Option<ConsoleMode>, Box<dyn std::error::Error>> {
        // 1. Fetch nodes from mesh
        let mut nodes = match self.fetch_nodes().await {
            Ok(n) => n,
            Err(e) => {
                println!("\x1b[33m⚠️ Central Hub unreachable ({}). Falling back to Standalone Local AI.\x1b[0m\n", e);
                return Ok(Some(ConsoleMode::Local(model_override)));
            }
        };

        if nodes.is_empty() {
            println!("\x1b[33m⚠️ No active nodes reported by Central Hub. Falling back to Local AI.\x1b[0m\n");
            return Ok(Some(ConsoleMode::Local(model_override)));
        }

        // 2. Compute Target Node Selection
        let mut target_node = if let Some(ref req_node) = node_override {
            // Find by index, node_id, or hostname
            if let Ok(idx) = req_node.parse::<usize>() {
                if idx > 0 && idx <= nodes.len() {
                    nodes[idx - 1].clone()
                } else {
                    self.select_node_interactive(&nodes, 0)
                }
            } else if let Some(found) = nodes.iter().find(|n| {
                n.node_id.eq_ignore_ascii_case(req_node) || n.hostname.eq_ignore_ascii_case(req_node)
            }) {
                found.clone()
            } else {
                println!("\x1b[33mRequested node '{}' not found in fleet.\x1b[0m", req_node);
                self.select_node_interactive(&nodes, 0)
            }
        } else if nodes.len() == 1 {
            nodes[0].clone()
        } else {
            // Pick default: first online with models, or first online
            let def_idx = nodes
                .iter()
                .position(|n| n.status == "online" && !n.models.is_empty())
                .or_else(|| nodes.iter().position(|n| n.status == "online"))
                .unwrap_or(0);
            self.select_node_interactive(&nodes, def_idx)
        };

        // 3. Model Selection
        let mut active_model = if let Some(ref req_model) = model_override {
            req_model.clone()
        } else {
            self.select_model_interactive(&target_node, None)
        };

        // 4. Session Initialization (Local ChatStorage + Hub Sync)
        let mut session = if let Some(ref res_id) = resume_id {
            match ChatStorage::load_session(res_id) {
                Ok(s) => {
                    println!("\x1b[1;32m✓ Restored local session: \"{}\" ({})\x1b[0m", s.title, s.id);
                    active_model = s.model.clone();
                    if let Some(matching) = nodes.iter().find(|n| n.node_id == s.target_node_id) {
                        target_node = matching.clone();
                    }
                    s
                }
                Err(_) => {
                    // Try cloud conversation
                    match self.resume_conversation(res_id).await {
                        Ok(_) => {
                            let mut s = ChatStorage::new_session(&target_node.node_id, &target_node.hostname, &active_model, None, 0.7);
                            s.title = format!("Cloud Session {}", res_id);
                            s
                        }
                        Err(e) => {
                            println!("\x1b[33mCould not resume session '{}': {}. Starting new session.\x1b[0m", res_id, e);
                            ChatStorage::new_session(&target_node.node_id, &target_node.hostname, &active_model, None, 0.7)
                        }
                    }
                }
            }
        } else {
            ChatStorage::new_session(&target_node.node_id, &target_node.hostname, &active_model, None, 0.7)
        };

        let mut active_conversation_id: Option<String> = None;

        // 5. Render Top Session Header
        let vram_badge = self.extract_vram_info(&target_node);
        let initial_tokens: usize = session.messages.iter().map(|m| m.content.len() / 4).sum();
        ChatRenderer::print_session_header(
            &target_node.hostname,
            &active_model,
            vram_badge.as_deref(),
            initial_tokens,
            &session.title,
        );

        if !session.messages.is_empty() {
            println!("\x1b[90m─── Restored Conversation History ({} messages) ───\x1b[0m", session.messages.len());
            for msg in &session.messages {
                if msg.role == "user" {
                    println!("\x1b[38;2;100;149;237mUser ❯\x1b[0m {}", msg.content);
                } else if msg.role == "assistant" {
                    println!("\x1b[38;2;130;200;255mAssistant:\x1b[0m {}", msg.content);
                }
            }
            println!("\x1b[90m────────────────────────────────────────────────────\x1b[0m\n");
        }

        let stdin = io::stdin();
        let mut handle = stdin.lock();

        loop {
            print!("\x1b[38;2;100;149;237m[{}] \x1b[38;2;0;220;180m❯ \x1b[0m", target_node.hostname);
            let _ = io::stdout().flush();

            let mut input = String::new();
            if handle.read_line(&mut input)? == 0 {
                let _ = ChatStorage::save_session(&session);
                break;
            }

            let trimmed = input.trim();
            if trimmed.is_empty() {
                continue;
            }

            // Exit commands
            if trimmed == "/exit" || trimmed == "/bye" || trimmed == "exit" || trimmed == "quit" {
                let _ = ChatStorage::save_session(&session);
                println!("\x1b[90mSession autosaved. Have a great day!\x1b[0m");
                break;
            }

            // Slash Help
            if trimmed == "/help" || trimmed == "/?" {
                ChatRenderer::print_slash_help();
                continue;
            }

            // Mode switches
            if trimmed == "/mode chat" {
                let _ = ChatStorage::save_session(&session);
                return Ok(Some(ConsoleMode::Chat));
            }

            if trimmed == "/mode local" {
                let _ = ChatStorage::save_session(&session);
                return Ok(Some(ConsoleMode::Local(Some(active_model))));
            }

            // Auth / PIN
            if trimmed.starts_with("/pin ") || trimmed.starts_with("/auth ") {
                let p = trimmed.split_whitespace().nth(1).unwrap_or("").trim();
                if !p.is_empty() {
                    self.set_pin(p);
                    let mut cfg = crate::config::AgentConfig::load_or_init();
                    cfg.token = Some(p.to_string());
                    let _ = cfg.save();
                    println!("\x1b[1;32m✓ Admin PIN authenticated and saved. Access granted!\x1b[0m\n");
                }
                continue;
            }

            // System prompt
            if trimmed.starts_with("/system") {
                let sys = trimmed.trim_start_matches("/system").trim();
                if sys.is_empty() {
                    if let Some(ref current) = session.system_prompt {
                        println!("\x1b[36mCurrent system prompt:\x1b[0m {}\n", current);
                    } else {
                        println!("\x1b[90m(No system prompt set. Using default)\x1b[0m\n");
                    }
                } else if sys == "clear" || sys == "reset" || sys == "none" {
                    session.system_prompt = None;
                    println!("\x1b[32m✓ System prompt cleared.\x1b[0m\n");
                } else {
                    session.system_prompt = Some(sys.to_string());
                    println!("\x1b[32m✓ System prompt updated.\x1b[0m\n");
                }
                let _ = ChatStorage::save_session(&session);
                continue;
            }

            // Temperature
            if trimmed.starts_with("/temp") {
                let t_str = trimmed.trim_start_matches("/temp").trim();
                if t_str.is_empty() {
                    println!("\x1b[36mCurrent temperature:\x1b[0m {:.2}\n", session.temperature);
                } else if let Ok(t) = t_str.parse::<f32>() {
                    session.temperature = t;
                    println!("\x1b[32m✓ Temperature set to {:.2}.\x1b[0m\n", t);
                    let _ = ChatStorage::save_session(&session);
                } else {
                    println!("\x1b[31mInvalid temperature value (use e.g. 0.7).\x1b[0m\n");
                }
                continue;
            }

            // List Nodes / Devices
            if trimmed == "/nodes" || trimmed == "/devices" || trimmed == "/cluster" {
                if let Ok(refreshed) = self.fetch_nodes().await {
                    nodes = refreshed;
                    self.print_node_fleet_table(&nodes);
                    if let Some(updated) = nodes.iter().find(|n| n.node_id == target_node.node_id) {
                        target_node = updated.clone();
                    }
                }
                continue;
            }

            // Minecraft Server Telemetry
            if trimmed == "/mc" || trimmed == "/mc status" || trimmed == "/minecraft" {
                crate::modules::minecraft::MinecraftManager::status(None);
                println!();
                continue;
            }

            // Switch Node
            if trimmed == "/node" || trimmed == "/switch" {
                if let Ok(refreshed) = self.fetch_nodes().await {
                    nodes = refreshed;
                    let cur_idx = nodes.iter().position(|n| n.node_id == target_node.node_id).unwrap_or(0);
                    target_node = self.select_node_interactive(&nodes, cur_idx);
                    if !target_node.models.is_empty() && !target_node.models.contains(&active_model) {
                        active_model = target_node.models[0].clone();
                        session.model = active_model.clone();
                    }
                    session.target_node_id = target_node.node_id.clone();
                    session.target_hostname = target_node.hostname.clone();
                    let vram = self.extract_vram_info(&target_node);
                    let cur_tokens: usize = session.messages.iter().map(|m| m.content.len() / 4).sum();
                    ChatRenderer::print_session_header(
                        &target_node.hostname,
                        &active_model,
                        vram.as_deref(),
                        cur_tokens,
                        &session.title,
                    );
                    let _ = ChatStorage::save_session(&session);
                }
                continue;
            }

            if trimmed.starts_with("/switch ") || trimmed.starts_with("/node ") {
                let target = if trimmed.starts_with("/switch ") {
                    trimmed.trim_start_matches("/switch ").trim()
                } else {
                    trimmed.trim_start_matches("/node ").trim()
                };
                if let Ok(refreshed) = self.fetch_nodes().await {
                    nodes = refreshed;
                    let found = if let Ok(idx) = target.parse::<usize>() {
                        if idx > 0 && idx <= nodes.len() {
                            Some(nodes[idx - 1].clone())
                        } else {
                            None
                        }
                    } else {
                        nodes.iter().find(|n| {
                            n.node_id.eq_ignore_ascii_case(target) || n.hostname.eq_ignore_ascii_case(target)
                        }).cloned()
                    };

                    match found {
                        Some(node) => {
                            target_node = node;
                            println!("\x1b[32mTarget compute node switched to: {} ({})\x1b[0m", target_node.hostname, target_node.node_id);
                            if !target_node.models.is_empty() && !target_node.models.contains(&active_model) {
                                active_model = target_node.models[0].clone();
                                session.model = active_model.clone();
                            }
                            session.target_node_id = target_node.node_id.clone();
                            session.target_hostname = target_node.hostname.clone();
                            let vram = self.extract_vram_info(&target_node);
                            let cur_tokens: usize = session.messages.iter().map(|m| m.content.len() / 4).sum();
                            ChatRenderer::print_session_header(
                                &target_node.hostname,
                                &active_model,
                                vram.as_deref(),
                                cur_tokens,
                                &session.title,
                            );
                            let _ = ChatStorage::save_session(&session);
                        }
                        None => println!("\x1b[31mNode '{}' not found in fleet. Run '/nodes' to list.\x1b[0m\n", target),
                    }
                }
                continue;
            }

            // Model Switch
            if trimmed == "/model" || trimmed == "/models" {
                active_model = self.select_model_interactive(&target_node, Some(&active_model));
                session.model = active_model.clone();
                let vram = self.extract_vram_info(&target_node);
                let cur_tokens: usize = session.messages.iter().map(|m| m.content.len() / 4).sum();
                ChatRenderer::print_session_header(
                    &target_node.hostname,
                    &active_model,
                    vram.as_deref(),
                    cur_tokens,
                    &session.title,
                );
                let _ = ChatStorage::save_session(&session);
                continue;
            }

            if trimmed.starts_with("/model ") || trimmed.starts_with("/set model ") {
                let m = if trimmed.starts_with("/model ") {
                    trimmed.trim_start_matches("/model ").trim()
                } else {
                    trimmed.trim_start_matches("/set model ").trim()
                };
                let chosen = if let Ok(idx) = m.parse::<usize>() {
                    if idx > 0 && idx <= target_node.models.len() {
                        target_node.models[idx - 1].clone()
                    } else {
                        m.to_string()
                    }
                } else {
                    m.to_string()
                };
                active_model = chosen;
                session.model = active_model.clone();
                let vram = self.extract_vram_info(&target_node);
                let cur_tokens: usize = session.messages.iter().map(|m| m.content.len() / 4).sum();
                ChatRenderer::print_session_header(
                    &target_node.hostname,
                    &active_model,
                    vram.as_deref(),
                    cur_tokens,
                    &session.title,
                );
                let _ = ChatStorage::save_session(&session);
                continue;
            }

            // Stop / Unload VRAM
            if trimmed == "/stop" || trimmed == "/unload" || trimmed.starts_with("/stop ") || trimmed.starts_with("/unload ") {
                let target_m = if trimmed.starts_with("/stop ") {
                    trimmed.trim_start_matches("/stop ").trim()
                } else if trimmed.starts_with("/unload ") {
                    trimmed.trim_start_matches("/unload ").trim()
                } else {
                    &active_model
                };
                self.unload_model_on_node(&target_node, target_m).await;
                continue;
            }

            // Inspect VRAM (ps)
            if trimmed == "/ps" || trimmed == "/vram" {
                self.inspect_node_ps(&target_node).await;
                continue;
            }

            // Pull Model
            if trimmed.starts_with("/pull ") {
                let m = trimmed.trim_start_matches("/pull ").trim();
                self.pull_model_on_node(&target_node, m).await;
                continue;
            }

            // Pre-load Model into VRAM
            if trimmed.starts_with("/load ") && !trimmed.starts_with("/load chat") && !trimmed.starts_with("/load session") {
                let parts: Vec<&str> = trimmed.split_whitespace().collect();
                if parts.len() >= 2 {
                    let target_m = parts[1];
                    let dur = if parts.len() > 2 { parts[2] } else { "15m" };
                    self.load_model_on_node(&target_node, target_m, dur).await;
                    active_model = target_m.to_string();
                    session.model = active_model.clone();
                    continue;
                }
            }

            // Clear Context / New Conversation
            if trimmed == "/clear" || trimmed == "/reset" || trimmed == "/new" {
                let _ = ChatStorage::save_session(&session);
                session.messages.clear();
                session.title = "New Conversation".to_string();
                active_conversation_id = None;
                print!("\x1b[2J\x1b[1;1H");
                let _ = io::stdout().flush();
                let vram = self.extract_vram_info(&target_node);
                ChatRenderer::print_session_header(
                    &target_node.hostname,
                    &active_model,
                    vram.as_deref(),
                    0,
                    &session.title,
                );
                println!("\x1b[1;32m✓ Context memory wiped. Model '{}' remains warm in VRAM.\x1b[0m\n", active_model);
                continue;
            }

            // Save Session
            if trimmed == "/save" || trimmed.starts_with("/save ") {
                let title = trimmed.trim_start_matches("/save").trim();
                if !title.is_empty() {
                    session.title = title.to_string();
                }
                session.updated_at = chrono::Local::now().format("%Y-%m-%d %H:%M:%S").to_string();
                match ChatStorage::save_session(&session) {
                    Ok(path) => {
                        println!("\x1b[1;32m✓ Session saved: \"{}\" ({})\x1b[0m\n", session.title, path.display());
                    }
                    Err(e) => println!("\x1b[31mFailed to save session: {}\x1b[0m\n", e),
                }
                continue;
            }

            // History / List saved sessions
            if trimmed == "/history" || trimmed == "/chats" || trimmed == "/sessions" {
                let sessions = ChatStorage::list_sessions();
                println!("\x1b[1;36m╭─ SAVED CHAT SESSIONS ────────────────────────────────────────────────────────╮\x1b[0m");
                if sessions.is_empty() {
                    println!("│  \x1b[90m(No local sessions found)\x1b[0m");
                } else {
                    for (i, s) in sessions.iter().enumerate() {
                        let short_id = if s.id.len() > 16 { &s.id[..16] } else { &s.id };
                        println!(
                            "│  \x1b[1;33m[{}]\x1b[0m \x1b[1;32m{:<28}\x1b[0m • {} • {} msgs • {}",
                            i + 1,
                            s.title,
                            s.target_hostname,
                            s.message_count,
                            s.updated_at
                        );
                        println!("│      \x1b[90mID: {} | Model: {}\x1b[0m", short_id, s.model);
                    }
                }
                println!("\x1b[1;36m╰──────────────────────────────────────────────────────────────────────────────╯\x1b[0m");
                println!("\x1b[90mUse '/resume <#|ID>' to resume, or '/delete <#|ID>' to remove.\x1b[0m\n");
                continue;
            }

            // Load / Resume Session
            if trimmed == "/load" || trimmed == "/resume" {
                let sessions = ChatStorage::list_sessions();
                if sessions.is_empty() {
                    println!("\x1b[90m(No saved sessions found)\x1b[0m\n");
                    continue;
                }
                println!("\x1b[1;36m╭─ SELECT SESSION TO RESUME ───────────────────────────────────────────────────╮\x1b[0m");
                for (i, s) in sessions.iter().enumerate() {
                    println!("│  \x1b[1;32m[{}]\x1b[0m \x1b[1m{:<28}\x1b[0m • {} • {} msgs • {}", i + 1, s.title, s.target_hostname, s.message_count, s.updated_at);
                }
                println!("\x1b[1;36m╰──────────────────────────────────────────────────────────────────────────────╯\x1b[0m");
                print!("\x1b[1;35mSelect session [1-{}] (press Enter to cancel): \x1b[0m", sessions.len());
                let _ = io::stdout().flush();
                let mut choice = String::new();
                let stdin = io::stdin();
                let _ = stdin.lock().read_line(&mut choice);
                let trimmed_c = choice.trim();
                if !trimmed_c.is_empty() {
                    match ChatStorage::load_session(trimmed_c) {
                        Ok(loaded) => {
                            session = loaded;
                            active_model = session.model.clone();
                            if let Some(matching) = nodes.iter().find(|n| n.node_id == session.target_node_id) {
                                target_node = matching.clone();
                            }
                            print!("\x1b[2J\x1b[1;1H");
                            let _ = io::stdout().flush();
                            let vram = self.extract_vram_info(&target_node);
                            let cur_tokens: usize = session.messages.iter().map(|m| m.content.len() / 4).sum();
                            ChatRenderer::print_session_header(
                                &target_node.hostname,
                                &active_model,
                                vram.as_deref(),
                                cur_tokens,
                                &session.title,
                            );
                            println!("\x1b[90m─── Restored Conversation History ({} messages) ───\x1b[0m", session.messages.len());
                            for msg in &session.messages {
                                if msg.role == "user" {
                                    println!("\x1b[38;2;100;149;237mUser ❯\x1b[0m {}", msg.content);
                                } else if msg.role == "assistant" {
                                    println!("\x1b[38;2;130;200;255mAssistant:\x1b[0m {}\n", msg.content);
                                }
                            }
                            println!("\x1b[90m────────────────────────────────────────────────────\x1b[0m\n");
                        }
                        Err(e) => println!("\x1b[31mFailed to load session: {}\x1b[0m\n", e),
                    }
                }
                continue;
            }

            if trimmed.starts_with("/load ") || trimmed.starts_with("/resume ") {
                let target_id = if trimmed.starts_with("/load ") {
                    trimmed.trim_start_matches("/load ").trim()
                } else {
                    trimmed.trim_start_matches("/resume ").trim()
                };
                match ChatStorage::load_session(target_id) {
                    Ok(loaded) => {
                        session = loaded;
                        active_model = session.model.clone();
                        if let Some(matching) = nodes.iter().find(|n| n.node_id == session.target_node_id) {
                            target_node = matching.clone();
                        }
                        print!("\x1b[2J\x1b[1;1H");
                        let _ = io::stdout().flush();
                        let vram = self.extract_vram_info(&target_node);
                        let cur_tokens: usize = session.messages.iter().map(|m| m.content.len() / 4).sum();
                        ChatRenderer::print_session_header(
                            &target_node.hostname,
                            &active_model,
                            vram.as_deref(),
                            cur_tokens,
                            &session.title,
                        );
                        println!("\x1b[90m─── Restored Conversation History ({} messages) ───\x1b[0m", session.messages.len());
                        for msg in &session.messages {
                            if msg.role == "user" {
                                println!("\x1b[38;2;100;149;237mUser ❯\x1b[0m {}", msg.content);
                            } else if msg.role == "assistant" {
                                println!("\x1b[38;2;130;200;255mAssistant:\x1b[0m {}\n", msg.content);
                            }
                        }
                        println!("\x1b[90m────────────────────────────────────────────────────\x1b[0m\n");
                    }
                    Err(_) => {
                        // Try cloud conversation
                        match self.resume_conversation(target_id).await {
                            Ok(loaded_id) => {
                                active_conversation_id = Some(loaded_id);
                                println!("\x1b[32mResumed cloud conversation {}.\x1b[0m\n", active_conversation_id.as_deref().unwrap());
                            }
                            Err(e) => println!("\x1b[31mFailed to load conversation: {}\x1b[0m\n", e),
                        }
                    }
                }
                continue;
            }

            // Delete Session
            if trimmed.starts_with("/delete ") || trimmed.starts_with("/rm ") {
                let target_id = if trimmed.starts_with("/delete ") {
                    trimmed.trim_start_matches("/delete ").trim()
                } else {
                    trimmed.trim_start_matches("/rm ").trim()
                };
                match ChatStorage::delete_session(target_id) {
                    Ok(deleted) => println!("\x1b[1;32m✓ Session '{}' deleted successfully.\x1b[0m\n", deleted),
                    Err(e) => println!("\x1b[31mFailed to delete session: {}\x1b[0m\n", e),
                }
                continue;
            }

            // Export to Markdown
            if trimmed == "/export" || trimmed.starts_with("/export ") {
                let filename = trimmed.trim_start_matches("/export").trim();
                let out_path = if filename.is_empty() {
                    let dir = ChatStorage::chats_dir().join("exports");
                    let _ = std::fs::create_dir_all(&dir);
                    dir.join(format!("{}.md", session.id))
                } else {
                    PathBuf::from(filename)
                };
                match ChatStorage::export_to_markdown(&session, &out_path) {
                    Ok(_) => println!("\x1b[1;32m✓ Conversation exported to: {}\x1b[0m\n", out_path.display()),
                    Err(e) => println!("\x1b[31mFailed to export markdown: {}\x1b[0m\n", e),
                }
                continue;
            }

            // Copy last assistant response
            if trimmed == "/copy" {
                if let Some(last_assistant) = session.messages.iter().rev().find(|m| m.role == "assistant") {
                    match ChatRenderer::copy_to_clipboard(&last_assistant.content) {
                        Ok(_) => println!("\x1b[1;32m✓ Last assistant response copied to clipboard!\x1b[0m\n"),
                        Err(e) => println!("\x1b[31mClipboard error: {}\x1b[0m\n", e),
                    }
                } else {
                    println!("\x1b[33mNo assistant responses in current session to copy.\x1b[0m\n");
                }
                continue;
            }

            // ─── EXECUTE PROMPT ──────────────────────────────────────────────────
            if target_node.status != "online" {
                println!("\x1b[31m❌ Cannot send prompt: target node '{}' is OFFLINE.\x1b[0m", target_node.hostname);
                println!("\x1b[90mPlease run '/nodes' and use '/switch <node>' to choose an online node.\x1b[0m\n");
                continue;
            }

            if target_node.models.is_empty() {
                println!("\x1b[33m⚠️ Target node '{}' has NO models installed!\x1b[0m", target_node.hostname);
                println!("\x1b[90mRun '/pull <model>' (e.g. '/pull qwen2.5-coder:1.5b') or '/switch <node>' to proceed.\x1b[0m\n");
                continue;
            }

            // Set session title from first prompt if default
            if session.title == "New Conversation" {
                let first_title = if trimmed.len() > 36 {
                    format!("{}...", &trimmed[..36])
                } else {
                    trimmed.to_string()
                };
                session.title = first_title;
            }

            // Ensure cloud conversation exists for cross-device sync
            if active_conversation_id.is_none() {
                let conv_url = format!("{}/api/fleet/ai/conversations", self.http_hub);
                let create_payload = json!({
                    "title": session.title,
                    "target_node_id": target_node.node_id,
                    "model": active_model,
                    "system_prompt": session.system_prompt,
                    "temperature": session.temperature
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

            let msg_url = format!("{}/api/fleet/ai/conversations/{}/messages", self.http_hub, conv_id);
            let msg_payload = json!({
                "content": trimmed,
                "model": active_model,
                "system_prompt": session.system_prompt,
                "temperature": session.temperature
            });

            let start_time = Instant::now();
            let mut formatter = StreamFormatter::new();
            let mut assistant_response = String::new();
            let mut token_count = 0u64;

            let resp = self.client.post(&msg_url).json(&msg_payload).send().await;

            match resp {
                Ok(res) if res.status().is_success() => {
                    let mut stream = res.bytes_stream();

                    while let Some(chunk_res) = stream.next().await {
                        if let Ok(bytes) = chunk_res {
                            if let Ok(chunk_str) = std::str::from_utf8(&bytes) {
                                for line in chunk_str.lines() {
                                    if line.starts_with("data: ") {
                                        let data = &line[6..];
                                        if data == "[DONE]" {
                                            break;
                                        }
                                        formatter.feed_chunk(data);
                                        assistant_response.push_str(data);
                                        token_count += 1;
                                    }
                                }
                            }
                        }
                    }
                    formatter.finish();

                    let elapsed = start_time.elapsed().as_secs_f64();
                    let tok_sec = if elapsed > 0.0 { token_count as f64 / elapsed } else { 0.0 };
                    ChatRenderer::print_metrics_footer(tok_sec, token_count, elapsed, &target_node.hostname, &active_model);

                    // Record turn into local session
                    let now_ts = std::time::SystemTime::now()
                        .duration_since(std::time::UNIX_EPOCH)
                        .unwrap_or_default()
                        .as_secs();

                    session.messages.push(ChatMessageItem {
                        role: "user".to_string(),
                        content: trimmed.to_string(),
                        timestamp: now_ts,
                        metrics: None,
                    });

                    session.messages.push(ChatMessageItem {
                        role: "assistant".to_string(),
                        content: assistant_response,
                        timestamp: now_ts,
                        metrics: Some(TurnMetrics {
                            tok_per_sec: tok_sec,
                            total_tokens: token_count,
                            elapsed_secs: elapsed,
                        }),
                    });

                    session.updated_at = chrono::Local::now().format("%Y-%m-%d %H:%M:%S").to_string();

                    // Autosave turn to disk
                    let _ = ChatStorage::save_session(&session);
                }
                Ok(res) => {
                    println!("\x1b[31m[Hub returned HTTP {}]\x1b[0m\n", res.status());
                }
                Err(e) => {
                    println!("\x1b[31m[Streaming network error: {}]\x1b[0m\n", e);
                }
            }
        }

        Ok(None)
    }

    fn select_node_interactive(&self, nodes: &[NodeInfo], default_idx: usize) -> NodeInfo {
        self.print_node_fleet_table(nodes);
        let def = if default_idx < nodes.len() { default_idx } else { 0 };
        let def_node = &nodes[def];

        print!(
            "\x1b[1;35mSelect compute node [1-{}] (press Enter for [{}] - {}): \x1b[0m",
            nodes.len(),
            def + 1,
            def_node.hostname
        );
        let _ = io::stdout().flush();

        let mut choice = String::new();
        let stdin = io::stdin();
        let _ = stdin.lock().read_line(&mut choice);
        let trimmed = choice.trim();

        if trimmed.is_empty() {
            return def_node.clone();
        }

        if let Ok(idx) = trimmed.parse::<usize>() {
            if idx > 0 && idx <= nodes.len() {
                return nodes[idx - 1].clone();
            }
        }

        for n in nodes {
            if n.hostname.eq_ignore_ascii_case(trimmed) || n.node_id.eq_ignore_ascii_case(trimmed) {
                return n.clone();
            }
        }

        println!("\x1b[33mChoice '{}' not recognized, defaulting to [{}] {}.\x1b[0m", trimmed, def + 1, def_node.hostname);
        def_node.clone()
    }

    fn select_model_interactive(&self, node: &NodeInfo, current_model: Option<&str>) -> String {
        if node.models.is_empty() {
            println!("\x1b[33m⚠️ No models installed on {}.\x1b[0m", node.hostname);
            println!("\x1b[90mEnter a model name to run (e.g. qwen2.5-coder:7b), or press Enter for default:\x1b[0m");
            print!("\x1b[1;35mModel name (default: qwen2.5-coder:7b): \x1b[0m");
            let _ = io::stdout().flush();
            let mut choice = String::new();
            let stdin = io::stdin();
            let _ = stdin.lock().read_line(&mut choice);
            let trimmed = choice.trim();
            if trimmed.is_empty() {
                return "qwen2.5-coder:7b".to_string();
            }
            return trimmed.to_string();
        }

        if node.models.len() == 1 {
            return node.models[0].clone();
        }

        println!("\x1b[1;36m╭─ SELECT MODEL ON {:<54} ╮\x1b[0m", node.hostname);
        let mut def_idx = 0;
        for (i, m) in node.models.iter().enumerate() {
            let is_current = current_model.map(|cm| cm == m).unwrap_or(i == 0);
            if is_current {
                def_idx = i;
            }
            let badge = if is_current {
                " \x1b[1;33m[Active]\x1b[0m"
            } else if i == 0 {
                " \x1b[1;32m[Recommended]\x1b[0m"
            } else {
                ""
            };
            println!("│  \x1b[1;32m[{}]\x1b[0m \x1b[1m{:<40}\x1b[0m{}", i + 1, m, badge);
        }
        println!("\x1b[1;36m╰─────────────────────────────────────────────────────────────────────────────╯\x1b[0m");
        print!(
            "\x1b[1;35mSelect model [1-{}] (press Enter for [{}] - {}): \x1b[0m",
            node.models.len(),
            def_idx + 1,
            node.models[def_idx]
        );
        let _ = io::stdout().flush();

        let mut choice = String::new();
        let stdin = io::stdin();
        let _ = stdin.lock().read_line(&mut choice);
        let trimmed = choice.trim();

        if trimmed.is_empty() {
            return node.models[def_idx].clone();
        }

        if let Ok(idx) = trimmed.parse::<usize>() {
            if idx > 0 && idx <= node.models.len() {
                return node.models[idx - 1].clone();
            }
        }

        for m in &node.models {
            if m.eq_ignore_ascii_case(trimmed) {
                return m.clone();
            }
        }

        trimmed.to_string()
    }

    fn extract_vram_info(&self, node: &NodeInfo) -> Option<String> {
        if let Some(tel) = &node.telemetry {
            // Check GPU (object or array)
            if let Some(g) = tel.get("gpu") {
                if !g.is_null() {
                    let (g_name, v_used, v_total) = if let Some(arr) = g.as_array() {
                        if let Some(first) = arr.first() {
                            (
                                first.get("name").and_then(|v| v.as_str()).unwrap_or("GPU"),
                                first.get("vram_used_mb").and_then(|v| v.as_f64()).unwrap_or(0.0) / 1024.0,
                                first.get("vram_total_mb").and_then(|v| v.as_f64()).unwrap_or(0.0) / 1024.0,
                            )
                        } else {
                            ("GPU", 0.0, 0.0)
                        }
                    } else {
                        (
                            g.get("name").and_then(|v| v.as_str()).unwrap_or("GPU"),
                            g.get("vram_used_mb").and_then(|v| v.as_f64()).unwrap_or(0.0) / 1024.0,
                            g.get("vram_total_mb").and_then(|v| v.as_f64()).unwrap_or(0.0) / 1024.0,
                        )
                    };
                    if v_total > 0.0 {
                        return Some(format!("{} ({:.1}/{:.1} GB VRAM)", g_name, v_used, v_total));
                    }
                }
            }

            // Check RAM (bytes or mb)
            if let Some(mem) = tel.get("memory") {
                let total = mem.get("total_bytes").and_then(|v| v.as_f64()).map(|b| b / 1_073_741_824.0)
                    .or_else(|| mem.get("total_mb").and_then(|v| v.as_f64()).map(|m| m / 1024.0))
                    .unwrap_or(0.0);
                let used = mem.get("used_bytes").and_then(|v| v.as_f64()).map(|b| b / 1_073_741_824.0)
                    .or_else(|| mem.get("used_mb").and_then(|v| v.as_f64()).map(|m| m / 1024.0))
                    .unwrap_or(0.0);
                if total > 0.0 {
                    return Some(format!("RAM: {:.1}/{:.1} GB", used, total));
                }
            }
        }
        None
    }

    async fn unload_model_on_node(&self, node: &NodeInfo, model: &str) {
        println!("\x1b[90mEvicting '{}' from VRAM on '{}'...\x1b[0m", model, node.hostname);
        if node.node_id == "local" || node.node_id == self.local_node_id {
            match self.local_ai.unload_model(model).await {
                Ok(_) => println!("\x1b[1;32m✓ Model '{}' evicted from VRAM on {}. GPU memory freed.\x1b[0m\n", model, node.hostname),
                Err(e) => println!("\x1b[31mUnload failed: {}\x1b[0m\n", e),
            }
            return;
        }

        let url = format!("{}/api/fleet/nodes/{}/ai/unload", self.http_hub, node.node_id);
        let payload = json!({ "model": model });
        let resp = self.client.post(&url).json(&payload).send().await;
        match resp {
            Ok(res) if res.status().is_success() => {
                println!("\x1b[1;32m✓ Model '{}' evicted from VRAM on {}. GPU memory freed.\x1b[0m\n", model, node.hostname);
            }
            _ => {
                let url_legacy = format!("{}/api/fleet/nodes/{}/unload", self.http_hub, node.node_id);
                match self.client.post(&url_legacy).json(&payload).send().await {
                    Ok(res) if res.status().is_success() => {
                        println!("\x1b[1;32m✓ Model '{}' evicted from VRAM on {}. GPU memory freed.\x1b[0m\n", model, node.hostname);
                    }
                    Ok(res) => println!("\x1b[31mUnload failed with HTTP {}\x1b[0m\n", res.status()),
                    Err(e) => println!("\x1b[31mNetwork error: {}\x1b[0m\n", e),
                }
            }
        }
    }

    async fn inspect_node_ps(&self, node: &NodeInfo) {
        println!("\x1b[1;36m╭─ MODELS CURRENTLY LOADED IN VRAM / RAM ({}) ────────────────────────╮\x1b[0m", node.hostname);
        if node.node_id == "local" || node.node_id == self.local_node_id {
            let procs = self.local_ai.poll_loaded_models().await;
            if procs.is_empty() {
                println!("│  \x1b[90m(No models actively loaded in VRAM. Cold start required)\x1b[0m");
            } else {
                for p in procs {
                    let total_mb = p.size.unwrap_or(0) as f64 / 1_048_576.0;
                    let vram_mb = p.size_vram.unwrap_or(0) as f64 / 1_048_576.0;
                    let expires = p.expires_at.as_deref().unwrap_or("Never");
                    println!("│  ● \x1b[1;32m{:<24}\x1b[0m VRAM: \x1b[1;33m{:.1} MB\x1b[0m / Total: {:.1} MB • Expires: {}", p.name, vram_mb, total_mb, expires);
                }
            }
        } else {
            let url = format!("{}/api/fleet/nodes/{}/ai/ps", self.http_hub, node.node_id);
            match self.client.get(&url).send().await {
                Ok(res) if res.status().is_success() => {
                    if let Ok(procs) = res.json::<Vec<serde_json::Value>>().await {
                        if procs.is_empty() {
                            println!("│  \x1b[90m(No models actively loaded in VRAM. Cold start required)\x1b[0m");
                        } else {
                            for p in procs {
                                let name = p.get("name").and_then(|v| v.as_str()).unwrap_or("unknown");
                                let total_mb = p.get("size").and_then(|v| v.as_f64()).unwrap_or(0.0) / 1_048_576.0;
                                let vram_mb = p.get("size_vram").and_then(|v| v.as_f64()).unwrap_or(0.0) / 1_048_576.0;
                                let expires = p.get("expires_at").and_then(|v| v.as_str()).unwrap_or("Never");
                                println!("│  ● \x1b[1;32m{:<24}\x1b[0m VRAM: \x1b[1;33m{:.1} MB\x1b[0m / Total: {:.1} MB • Expires: {}", name, vram_mb, total_mb, expires);
                            }
                        }
                    } else {
                        println!("│  \x1b[31mFailed to parse process list from node.\x1b[0m");
                    }
                }
                Ok(res) => println!("│  \x1b[31mNode returned HTTP {}\x1b[0m", res.status()),
                Err(e) => println!("│  \x1b[31mNetwork error: {}\x1b[0m", e),
            }
        }
        println!("\x1b[1;36m╰─────────────────────────────────────────────────────────────────────────────╯\x1b[0m\n");
    }

    async fn pull_model_on_node(&self, node: &NodeInfo, model: &str) {
        println!("\x1b[90mDispatching remote pull for '{}' on node '{}'...\x1b[0m", model, node.hostname);
        if node.node_id == "local" || node.node_id == self.local_node_id {
            match self.local_ai.pull_model(model).await {
                Ok(_) => println!("\x1b[1;32m✓ Model pull initiated successfully on {}.\x1b[0m\n", node.hostname),
                Err(e) => println!("\x1b[31mPull error: {}\x1b[0m\n", e),
            }
            return;
        }

        let url = format!("{}/api/fleet/nodes/{}/ai/pull", self.http_hub, node.node_id);
        let payload = json!({ "model": model });
        match self.client.post(&url).json(&payload).send().await {
            Ok(res) if res.status().is_success() => {
                println!("\x1b[1;32m✓ Model pull initiated successfully on {}.\x1b[0m\n", node.hostname);
            }
            _ => {
                let url_legacy = format!("{}/api/fleet/nodes/{}/pull", self.http_hub, node.node_id);
                match self.client.post(&url_legacy).json(&payload).send().await {
                    Ok(res) if res.status().is_success() => {
                        println!("\x1b[1;32m✓ Model pull initiated successfully on {}.\x1b[0m\n", node.hostname);
                    }
                    Ok(res) => println!("\x1b[31mPull request returned status {}\x1b[0m\n", res.status()),
                    Err(e) => println!("\x1b[31mNetwork error: {}\x1b[0m\n", e),
                }
            }
        }
    }

    async fn load_model_on_node(&self, node: &NodeInfo, model: &str, keep_alive: &str) {
        println!("\x1b[90mPre-loading '{}' into VRAM on '{}' (keep_alive: {})...\x1b[0m", model, node.hostname, keep_alive);
        if node.node_id == "local" || node.node_id == self.local_node_id {
            match self.local_ai.load_model(model, Some(keep_alive)).await {
                Ok(_) => println!("\x1b[1;32m✓ Model '{}' is now warm in VRAM on {}.\x1b[0m\n", model, node.hostname),
                Err(e) => println!("\x1b[31mLoad error: {}\x1b[0m\n", e),
            }
            return;
        }

        let url = format!("{}/api/fleet/nodes/{}/ai/load", self.http_hub, node.node_id);
        let payload = json!({ "model": model, "keep_alive": keep_alive });
        match self.client.post(&url).json(&payload).send().await {
            Ok(res) if res.status().is_success() => {
                println!("\x1b[1;32m✓ Model '{}' is now warm in VRAM on {}.\x1b[0m\n", model, node.hostname);
            }
            _ => {
                let url_legacy = format!("{}/api/fleet/nodes/{}/load", self.http_hub, node.node_id);
                match self.client.post(&url_legacy).json(&payload).send().await {
                    Ok(res) if res.status().is_success() => {
                        println!("\x1b[1;32m✓ Model '{}' is now warm in VRAM on {}.\x1b[0m\n", model, node.hostname);
                    }
                    Ok(res) => println!("\x1b[31mLoad failed with HTTP {}\x1b[0m\n", res.status()),
                    Err(e) => println!("\x1b[31mNetwork error: {}\x1b[0m\n", e),
                }
            }
        }
    }

    async fn fetch_nodes(&self) -> Result<Vec<NodeInfo>, String> {
        let url = format!("{}/api/fleet/nodes", self.http_hub);
        let res = self.client.get(&url).send().await.map_err(|e| e.to_string())?;
        if res.status().is_success() {
            let mut list = res.json::<Vec<NodeInfo>>().await.map_err(|e| e.to_string())?;
            for n in &mut list {
                // Populate models from telemetry if top-level models is empty
                if n.models.is_empty() {
                    if let Some(ai) = n.telemetry.as_ref().and_then(|t| t.get("ai_engine")) {
                        if let Some(arr) = ai.get("models").and_then(|m| m.as_array()) {
                            n.models = arr.iter().filter_map(|v| v.as_str().map(|s| s.to_string())).collect();
                        }
                    }
                }
            }
            Ok(list)
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
                        let total = mem.get("total_bytes").and_then(|v| v.as_f64()).map(|b| b / 1_073_741_824.0)
                            .or_else(|| mem.get("total_mb").and_then(|v| v.as_f64()).map(|m| m / 1024.0))
                            .unwrap_or(0.0);
                        let used = mem.get("used_bytes").and_then(|v| v.as_f64()).map(|b| b / 1_073_741_824.0)
                            .or_else(|| mem.get("used_mb").and_then(|v| v.as_f64()).map(|m| m / 1024.0))
                            .unwrap_or(0.0);
                        let free = total - used;
                        if total > 0.0 {
                            ram_info = format!("RAM: {:.1}/{:.1} GB ({:.1} GB free)", used, total, free);
                        }
                    }
                    if let Some(g) = tel.get("gpu") {
                        if !g.is_null() {
                            let (g_name, v_used, v_total) = if let Some(arr) = g.as_array() {
                                if let Some(first) = arr.first() {
                                    (
                                        first.get("name").and_then(|v| v.as_str()).unwrap_or("GPU"),
                                        first.get("vram_used_mb").and_then(|v| v.as_f64()).unwrap_or(0.0) / 1024.0,
                                        first.get("vram_total_mb").and_then(|v| v.as_f64()).unwrap_or(0.0) / 1024.0,
                                    )
                                } else {
                                    ("GPU", 0.0, 0.0)
                                }
                            } else {
                                (
                                    g.get("name").and_then(|v| v.as_str()).unwrap_or("GPU"),
                                    g.get("vram_used_mb").and_then(|v| v.as_f64()).unwrap_or(0.0) / 1024.0,
                                    g.get("vram_total_mb").and_then(|v| v.as_f64()).unwrap_or(0.0) / 1024.0,
                                )
                            };
                            if v_total > 0.0 {
                                vram_info = format!(" | GPU: {} ({:.1}/{:.1} GB VRAM)", g_name, v_used, v_total);
                            }
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
