use chrono::Utc;
use serde::Deserialize;
use std::collections::HashMap;
use std::fs;
use std::path::Path;
use std::process::Command;
use std::sync::Arc;
use std::time::{Duration, Instant};
use tokio::sync::Mutex;
use tokio::time::sleep;
use tracing::{error, info, warn};

#[derive(Debug, Clone, Deserialize)]
pub struct AppTarget {
    pub name: String,
    pub active_port: u16,
    pub standby_port: u16,
    pub active_slot: String,
    pub standby_slot: String,
    pub health_path: String,
    pub nginx_upstream_conf: String,
    pub compose_file: String,
    pub container_prefix: String,
    #[serde(default)]
    pub bridge_interface: Option<String>,
    #[serde(default)]
    pub bridge_gateway: Option<String>,
}

#[derive(Debug, Clone)]
pub struct AppState {
    pub consecutive_failures: u32,
    pub consecutive_successes: u32,
    pub active_slot: String,
    pub last_recreate: Option<Instant>,
    pub circuit_breaker_active: bool,
}

impl AppState {
    pub fn new(initial_slot: String) -> Self {
        Self {
            consecutive_failures: 0,
            consecutive_successes: 0,
            active_slot: initial_slot,
            last_recreate: None,
            circuit_breaker_active: false,
        }
    }
}

pub struct VanguardEngine {
    targets: Vec<AppTarget>,
    client: reqwest::Client,
    states: Arc<Mutex<HashMap<String, AppState>>>,
    failure_threshold: u32,
    success_threshold: u32,
    recreate_cooldown: Duration,
    poll_interval: Duration,
}

impl VanguardEngine {
    pub fn new(targets: Vec<AppTarget>) -> Self {
        let client = reqwest::Client::builder()
            .timeout(Duration::from_secs(4))
            .build()
            .expect("Failed to initialize reqwest client");

        let mut initial_states = HashMap::new();
        for target in &targets {
            let active = detect_active_slot_from_nginx(&target.nginx_upstream_conf, target.green_port());
            initial_states.insert(target.name.clone(), AppState::new(active));
        }

        Self {
            targets,
            client,
            states: Arc::new(Mutex::new(initial_states)),
            failure_threshold: 3,
            success_threshold: 2,
            recreate_cooldown: Duration::from_secs(300), // 5 minute circuit breaker
            poll_interval: Duration::from_secs(45),
        }
    }

    pub async fn run(&self) {
        info!("Vanguard Watchdog Engine started. Monitoring {} applications every {:?}.", self.targets.len(), self.poll_interval);

        loop {
            for target in &self.targets {
                self.process_target(target).await;
            }
            sleep(self.poll_interval).await;
        }
    }

    async fn process_target(&self, target: &AppTarget) {
        // 1. Verify bridge network health if interface defined
        if let (Some(bridge), Some(gw)) = (&target.bridge_interface, &target.bridge_gateway) {
            ensure_bridge_network(bridge, gw);
        }

        let mut states_guard = self.states.lock().await;
        let state = states_guard.get_mut(&target.name).expect("State must exist");

        let active_slot = state.active_slot.clone();
        let standby_slot = if active_slot == target.active_slot {
            target.standby_slot.clone()
        } else {
            target.active_slot.clone()
        };

        let active_port = get_port_for_slot(target, &active_slot);
        let standby_port = get_port_for_slot(target, &standby_slot);

        // Check active slot
        if self.probe_endpoint(active_port, &target.health_path).await {
            if state.consecutive_failures > 0 {
                info!("[{}] Active slot '{}' on :{} has recovered.", target.name, active_slot, active_port);
            }
            state.consecutive_failures = 0;
            state.circuit_breaker_active = false;
            return;
        }

        state.consecutive_failures += 1;
        warn!(
            "[{}] Active slot '{}' on :{} failed health check #{}/{}",
            target.name, active_slot, active_port, state.consecutive_failures, self.failure_threshold
        );

        if state.consecutive_failures < self.failure_threshold {
            return;
        }

        // Active failed threshold times -> Check standby slot
        if self.probe_endpoint(standby_port, &target.health_path).await {
            state.consecutive_successes += 1;
            info!(
                "[{}] Standby slot '{}' on :{} is healthy and stable ({}/{}).",
                target.name, standby_slot, standby_port, state.consecutive_successes, self.success_threshold
            );

            if state.consecutive_successes >= self.success_threshold {
                info!("[{}] Failing over from slot '{}' to '{}'.", target.name, active_slot, standby_slot);
                switch_nginx_upstream(target, &standby_slot, standby_port);
                state.active_slot = standby_slot;
                state.consecutive_failures = 0;
                state.consecutive_successes = 0;
                state.circuit_breaker_active = false;
            }
            return;
        }

        // Both active and standby failed -> Evaluate recovery with circuit breaker
        state.consecutive_successes = 0;
        let can_recreate = match state.last_recreate {
            None => true,
            Some(last) => last.elapsed() >= self.recreate_cooldown,
        };

        if !can_recreate {
            if !state.circuit_breaker_active {
                error!(
                    "[{}] Circuit breaker TRIP: Container recreate on cooldown ({:?}). Suppressing restart loops to protect CPU.",
                    target.name, self.recreate_cooldown
                );
                state.circuit_breaker_active = true;
            }
            return;
        }

        // Trigger container recovery
        info!("[{}] Attempting recovery of active slot '{}'...", target.name, active_slot);
        state.last_recreate = Some(Instant::now());
        recreate_container(&target.compose_file, &format!("{}-{}", target.container_prefix, active_slot));

        sleep(Duration::from_secs(4)).await;

        if self.probe_endpoint(active_port, &target.health_path).await {
            info!("[{}] Successfully recovered active slot '{}'.", target.name, active_slot);
            state.consecutive_failures = 0;
            state.circuit_breaker_active = false;
            return;
        }

        error!(
            "[{}] CRITICAL: Slot '{}' could not be recovered. Awaiting operator inspection.",
            target.name, active_slot
        );
    }

    async fn probe_endpoint(&self, port: u16, path: &str) -> bool {
        let url = format!("http://127.0.0.1:{}{}", port, path);
        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status().is_success() {
                    if let Ok(text) = resp.text().await {
                        return text.contains("healthy") || text.contains("ok") || text.contains("\"status\":\"ok\"") || text.contains("200");
                    }
                    return true;
                }
                false
            }
            Err(_) => false,
        }
    }
}

impl AppTarget {
    pub fn green_port(&self) -> u16 {
        self.standby_port
    }
}

fn detect_active_slot_from_nginx(conf_path: &str, green_port: u16) -> String {
    if let Ok(content) = fs::read_to_string(conf_path) {
        if content.contains(&format!("127.0.0.1:{}", green_port)) {
            return "green".to_string();
        }
    }
    "blue".to_string()
}

fn get_port_for_slot(target: &AppTarget, slot: &str) -> u16 {
    if slot == target.standby_slot {
        target.standby_port
    } else {
        target.active_port
    }
}

fn ensure_bridge_network(bridge: &str, gateway: &str) {
    if Path::new(&format!("/sys/class/net/{}", bridge)).exists() {
        // Quick check via ip addr
        let output = Command::new("ip")
            .args(["-4", "addr", "show", "dev", bridge])
            .output();

        if let Ok(out) = output {
            let stdout = String::from_utf8_lossy(&out.stdout);
            if !stdout.contains("inet ") {
                warn!("[Network-Guard] Docker bridge '{}' missing IPv4. Healing to {}...", bridge, gateway);
                let _ = Command::new("ip").args(["addr", "add", gateway, "dev", bridge]).output();
                let _ = Command::new("ip").args(["link", "set", bridge, "up"]).output();
            }
        }
    }
}

fn switch_nginx_upstream(target: &AppTarget, slot: &str, port: u16) {
    let conf = format!(
        "# Vanguard-Watchdog Managed Upstream\nupstream {} {{\n    server 127.0.0.1:{} max_fails=2 fail_timeout=5s;\n    keepalive 32;\n}}\n",
        match target.name.as_str() {
            "bloomberg-terminal" => "bloomberg_terminal_active",
            _ => "macos_backend",
        },
        port
    );

    let tmp_path = format!("/tmp/upstream_{}.conf", target.name);
    let _ = fs::write(&tmp_path, conf);
    let _ = Command::new("cp").args([&tmp_path, &target.nginx_upstream_conf]).output();
    let _ = Command::new("nginx").arg("-s").arg("reload").output();
    info!("[{}] Switched upstream to '{}' on port {}", target.name, slot, port);
}

fn recreate_container(compose_file: &str, service_name: &str) {
    let _ = Command::new("docker")
        .args([
            "compose",
            "-f",
            compose_file,
            "up",
            "-d",
            "--force-recreate",
            "--no-deps",
            service_name,
        ])
        .output();
}

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "vanguard_watchdog=info".into()),
        )
        .init();

    info!("Initializing Vanguard Rust Watchdog...");

    // Default monitored targets
    let targets = vec![
        AppTarget {
            name: "macos-server".into(),
            active_port: 3001,
            standby_port: 3004,
            active_slot: "blue".into(),
            standby_slot: "green".into(),
            health_path: "/api/health".into(),
            nginx_upstream_conf: "/etc/nginx/conf.d/upstream_macos.conf".into(),
            compose_file: "/home/ubuntu/MacOS-Production/docker-compose.yml".into(),
            container_prefix: "macos-server".into(),
            bridge_interface: Some("br-2be5a056e665".into()),
            bridge_gateway: Some("172.18.0.1/16".into()),
        },
    ];

    let engine = VanguardEngine::new(targets);
    engine.run().await;
}
