use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::fs;
use std::path::PathBuf;
use tracing::{info, warn};
use uuid::Uuid;

pub const DEFAULT_PRIMARY_HUB: &str = "wss://faizal.uk/api/mesh/ws";
pub const DEFAULT_BACKUP_HUBS: &[&str] = &[
    "wss://cos.faizal.uk/api/mesh/ws",
    "wss://pro.faizal.uk/api/mesh/ws",
];

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AgentConfig {
    pub node_id: String,
    pub hostname: String,
    pub os: String,
    pub arch: String,
    pub hubs: Vec<String>,
    pub poll_interval_secs: u64,
    pub active_poll_interval_secs: u64,
    pub token: Option<String>,
    pub ai_endpoint: String,
    pub features: HashMap<String, bool>,
}

impl Default for AgentConfig {
    fn default() -> Self {
        let hostname = sysinfo::System::host_name().unwrap_or_else(|| "unknown-host".to_string());
        let os = std::env::consts::OS.to_string();
        let arch = std::env::consts::ARCH.to_string();
        
        // Auto-generate stable node ID: os-hostname-short_uuid
        let short_uuid = &Uuid::new_v4().to_string()[..8];
        let sanitized_host = hostname.to_lowercase().replace(' ', "-");
        let node_id = format!("{}-{}-{}", os, sanitized_host, short_uuid);

        let mut hubs = vec![DEFAULT_PRIMARY_HUB.to_string()];
        for backup in DEFAULT_BACKUP_HUBS {
            hubs.push(backup.to_string());
        }

        let mut features = HashMap::new();
        features.insert("telemetry".to_string(), true);
        features.insert("gpu".to_string(), true);
        features.insert("ai_bridge".to_string(), true);
        features.insert("docker".to_string(), true);
        features.insert("auto_update".to_string(), true);
        features.insert("mail_relay".to_string(), true);

        Self {
            node_id,
            hostname,
            os,
            arch,
            hubs,
            poll_interval_secs: 15,
            active_poll_interval_secs: 2,
            token: None,
            ai_endpoint: "http://127.0.0.1:11434".to_string(),
            features,
        }
    }
}

impl AgentConfig {
    pub fn state_path() -> PathBuf {
        let cfg = Self::config_path();
        let dir = cfg.parent().unwrap_or_else(|| std::path::Path::new("."));
        dir.join("daemon.state")
    }

    pub fn primary_hub_host(&self) -> String {
        if let Some(first) = self.hubs.first() {
            if let Ok(url) = reqwest::Url::parse(first) {
                if let Some(host) = url.host_str() {
                    return host.to_string();
                }
            }
        }
        "faizal.uk".to_string()
    }

    pub fn config_path() -> PathBuf {
        #[cfg(target_os = "windows")]
        {
            if let Ok(progdata) = std::env::var("ProgramData") {
                let dir = PathBuf::from(progdata).join("NexusAgent");
                let p = dir.join("config.json");
                if p.exists() {
                    return p;
                }
                if fs::create_dir_all(&dir).is_ok() {
                    return p;
                }
            }
            if let Ok(appdata) = std::env::var("LOCALAPPDATA") {
                let dir = PathBuf::from(appdata).join("NexusAgent");
                let _ = fs::create_dir_all(&dir);
                return dir.join("config.json");
            }
            PathBuf::from("nexus-agent-config.json")
        }

        #[cfg(not(target_os = "windows"))]
        {
            let system_cfg = PathBuf::from("/etc/nexus-agent/config.json");
            if system_cfg.exists() {
                return system_cfg;
            }

            if unsafe { libc::geteuid() } == 0 {
                let dir = PathBuf::from("/etc/nexus-agent");
                let _ = fs::create_dir_all(&dir);
                return dir.join("config.json");
            }
            if let Ok(home) = std::env::var("HOME") {
                let dir = PathBuf::from(home).join(".config").join("nexus-agent");
                let _ = fs::create_dir_all(&dir);
                return dir.join("config.json");
            }
            PathBuf::from("nexus-agent-config.json")
        }
    }

    pub fn load_or_init() -> Self {
        let path = Self::config_path();
        if path.exists() {
            if let Ok(content) = fs::read_to_string(&path) {
                if let Ok(config) = serde_json::from_str::<AgentConfig>(&content) {
                    info!("Loaded configuration from {:?}", path);
                    return config;
                }
            }
            warn!("Failed to parse config at {:?}, generating new default", path);
        }

        let config = Self::default();
        if let Ok(serialized) = serde_json::to_string_pretty(&config) {
            if let Some(parent) = path.parent() {
                let _ = fs::create_dir_all(parent);
            }
            if let Err(e) = fs::write(&path, serialized) {
                warn!("Could not persist config to {:?}: {}", path, e);
            } else {
                #[cfg(not(target_os = "windows"))]
                {
                    use std::os::unix::fs::PermissionsExt;
                    let _ = fs::set_permissions(&path, fs::Permissions::from_mode(0o644));
                }
                info!("Initialized new node identity config at {:?}", path);
            }
        }
        config
    }

    pub fn save(&self) -> Result<(), std::io::Error> {
        let path = Self::config_path();
        if let Some(parent) = path.parent() {
            let _ = fs::create_dir_all(parent);
        }
        let serialized = serde_json::to_string_pretty(self)
            .map_err(|e| std::io::Error::new(std::io::ErrorKind::Other, e.to_string()))?;

        if let Err(e) = fs::write(&path, &serialized) {
            #[cfg(not(target_os = "windows"))]
            if e.kind() == std::io::ErrorKind::PermissionDenied {
                // If system-wide config is not writable by current user, save in user config
                if let Ok(home) = std::env::var("HOME") {
                    let user_dir = PathBuf::from(home).join(".config").join("nexus-agent");
                    let _ = fs::create_dir_all(&user_dir);
                    let user_path = user_dir.join("config.json");
                    return fs::write(user_path, serialized);
                }
            }
            return Err(e);
        }

        #[cfg(not(target_os = "windows"))]
        {
            use std::os::unix::fs::PermissionsExt;
            let _ = fs::set_permissions(&path, fs::Permissions::from_mode(0o666));
        }

        Ok(())
    }

    pub fn is_feature_enabled(&self, feature: &str) -> bool {
        // Telemetry, AI bridge, GPU monitor, and auto update are default-ON features
        let default_val = matches!(feature, "telemetry" | "gpu" | "ai_bridge" | "auto_update");
        self.features.get(feature).copied().unwrap_or(default_val)
    }
}

