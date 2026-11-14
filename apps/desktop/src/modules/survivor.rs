use serde::{Deserialize, Serialize};
use std::fs;
use std::path::PathBuf;
use std::process::Command;
use std::time::{SystemTime, UNIX_EPOCH};
use tracing::{info, warn, error};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SurvivorConfig {
    pub opt_in: bool,
    pub hot_standby: bool,
    pub sync_interval_secs: u64,
    pub cloudflared_token: Option<String>,
    pub last_synced_at: Option<u64>,
}

impl Default for SurvivorConfig {
    fn default() -> Self {
        Self {
            opt_in: false,
            hot_standby: false,
            sync_interval_secs: 120,
            cloudflared_token: None,
            last_synced_at: None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SurvivorStatus {
    pub opt_in: bool,
    pub hot_standby: bool,
    pub last_synced_secs_ago: Option<u64>,
    pub macos_db_size_bytes: u64,
    pub messages_db_size_bytes: u64,
    pub stalwart_config_ready: bool,
    pub docker_ready: bool,
    pub active_survivor: bool,
}

pub struct SurvivorSupervisor;

impl SurvivorSupervisor {
    pub fn config_path() -> PathBuf {
        let home = std::env::var("USERPROFILE")
            .or_else(|_| std::env::var("HOME"))
            .unwrap_or_else(|_| ".".into());
        PathBuf::from(home).join(".nexus").join("survivor.json")
    }

    pub fn survivor_data_dir() -> PathBuf {
        let home = std::env::var("USERPROFILE")
            .or_else(|_| std::env::var("HOME"))
            .unwrap_or_else(|_| ".".into());
        PathBuf::from(home).join(".nexus").join("survivor").join("data")
    }

    pub fn load_config() -> SurvivorConfig {
        let path = Self::config_path();
        if path.exists() {
            if let Ok(data) = fs::read_to_string(&path) {
                if let Ok(cfg) = serde_json::from_str(&data) {
                    return cfg;
                }
            }
        }
        SurvivorConfig::default()
    }

    pub fn save_config(cfg: &SurvivorConfig) -> Result<(), String> {
        let path = Self::config_path();
        if let Some(parent) = path.parent() {
            let _ = fs::create_dir_all(parent);
        }
        let data = serde_json::to_string_pretty(cfg).map_err(|e| e.to_string())?;
        fs::write(&path, data).map_err(|e| e.to_string())?;
        Ok(())
    }

    pub fn get_status() -> SurvivorStatus {
        let cfg = Self::load_config();
        let data_dir = Self::survivor_data_dir();

        let macos_db = data_dir.join("macos").join("MacOS.db");
        let messages_db = data_dir.join("backend").join("messages.db");
        let mail_etc = data_dir.join("mail").join("config.json");

        let macos_size = fs::metadata(&macos_db).map(|m| m.len()).unwrap_or(0);
        let messages_size = fs::metadata(&messages_db).map(|m| m.len()).unwrap_or(0);
        let mail_ready = mail_etc.exists();

        let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs();
        let last_synced_secs_ago = cfg.last_synced_at.map(|t| now.saturating_sub(t));

        let docker_check = Command::new("docker").arg("info").output();
        let docker_ready = docker_check.map(|o| o.status.success()).unwrap_or(false);

        SurvivorStatus {
            opt_in: cfg.opt_in,
            hot_standby: cfg.hot_standby,
            last_synced_secs_ago,
            macos_db_size_bytes: macos_size,
            messages_db_size_bytes: messages_size,
            stalwart_config_ready: mail_ready,
            docker_ready,
            active_survivor: false,
        }
    }

    pub async fn sync_from_hub(hub_http: &str, pin: &str) -> Result<String, String> {
        let base_url = hub_http.trim_end_matches('/').replace("wss://", "https://").replace("ws://", "http://");
        let client = reqwest::Client::builder()
            .timeout(std::time::Duration::from_secs(15))
            .build()
            .map_err(|e| e.to_string())?;

        let data_dir = Self::survivor_data_dir();
        fs::create_dir_all(data_dir.join("macos")).map_err(|e| e.to_string())?;
        fs::create_dir_all(data_dir.join("backend")).map_err(|e| e.to_string())?;
        fs::create_dir_all(data_dir.join("mail")).map_err(|e| e.to_string())?;

        // 1. Snapshot messages.db
        let msg_url = format!("{}/api/snapshot/messages.db", base_url);
        let mut req = client.get(&msg_url);
        if !pin.is_empty() {
            req = req.header("x-admin-pin", pin);
        }
        if let Ok(resp) = req.send().await {
            if resp.status().is_success() {
                if let Ok(bytes) = resp.bytes().await {
                    let _ = fs::write(data_dir.join("backend").join("messages.db"), &bytes);
                    info!("Survivor Sync: Successfully downloaded messages.db ({} bytes)", bytes.len());
                }
            }
        }

        // 2. Snapshot MacOS.db
        let macos_url = format!("{}/api/snapshot/MacOS.db", base_url);
        let mut req = client.get(&macos_url);
        if !pin.is_empty() {
            req = req.header("x-admin-pin", pin);
        }
        if let Ok(resp) = req.send().await {
            if resp.status().is_success() {
                if let Ok(bytes) = resp.bytes().await {
                    let _ = fs::write(data_dir.join("macos").join("MacOS.db"), &bytes);
                    info!("Survivor Sync: Successfully downloaded MacOS.db ({} bytes)", bytes.len());
                }
            }
        }

        let mut cfg = Self::load_config();
        let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs();
        cfg.last_synced_at = Some(now);
        let _ = Self::save_config(&cfg);

        Ok(format!("Snapshots mirrored successfully to {:?}", data_dir))
    }

    pub fn start_local_survivor_stack() -> Result<(), String> {
        info!("Survivor: Triggering local Docker compose spin-up for core stack");
        let data_dir = Self::survivor_data_dir();
        
        let compose_content = format!(r#"
services:
  personal-site-rust:
    image: personal-site:latest
    container_name: survivor-personal-site
    restart: unless-stopped
    ports:
      - "3002:3002"
    volumes:
      - "{}/backend/messages.db:/app/messages.db"
    environment:
      - PORT=3002
      - NODE_ROLE=edge_survivor

  macos-desktop:
    image: macos-server:latest
    container_name: survivor-macos-desktop
    restart: unless-stopped
    ports:
      - "3001:3001"
    volumes:
      - "{}/macos/MacOS.db:/app/database/MacOS.db"

  stalwart-mail:
    image: stalwartlabs/stalwart:latest
    container_name: survivor-stalwart-mail
    restart: unless-stopped
    ports:
      - "587:587"
      - "465:465"
      - "993:993"
"#, data_dir.display(), data_dir.display());

        let compose_path = data_dir.join("docker-compose.yml");
        fs::write(&compose_path, compose_content).map_err(|e| e.to_string())?;

        let out = Command::new("docker")
            .args(["compose", "-f", compose_path.to_str().unwrap(), "up", "-d"])
            .output()
            .map_err(|e| e.to_string())?;

        if !out.status.success() {
            return Err(String::from_utf8_lossy(&out.stderr).to_string());
        }

        info!("Survivor: Local Docker stack successfully started in survivor mode");
        Ok(())
    }

    pub fn stop_local_survivor_stack() -> Result<(), String> {
        let compose_path = Self::survivor_data_dir().join("docker-compose.yml");
        if compose_path.exists() {
            let _ = Command::new("docker")
                .args(["compose", "-f", compose_path.to_str().unwrap(), "down"])
                .output();
        }
        info!("Survivor: Local Docker stack stopped");
        Ok(())
    }
}
