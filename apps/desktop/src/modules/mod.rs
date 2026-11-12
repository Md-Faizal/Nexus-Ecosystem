pub mod ai_bridge;
pub mod banner;
pub mod chat_renderer;
pub mod chat_storage;
pub mod cluster_ops;
pub mod crash;
pub mod daemon_lock;
pub mod docker;
pub mod fitness;
pub mod gpu;
pub mod helpbook;
pub mod minecraft;
pub mod operator_console;
pub mod telemetry;
pub mod tray;
pub mod tunnel;

use crate::config::AgentConfig;
use crate::mesh::protocol::TelemetryPayload;
use ai_bridge::AiBridge;
use docker::DockerSupervisor;
use gpu::GpuCollector;
use telemetry::TelemetryCollector;

pub struct ModuleManager {
    telemetry_collector: TelemetryCollector,
    ai_bridge: AiBridge,
}

impl ModuleManager {
    pub fn new(config: &AgentConfig) -> Self {
        Self {
            telemetry_collector: TelemetryCollector::new(),
            ai_bridge: AiBridge::new(&config.ai_endpoint),
        }
    }

    pub fn ai_bridge(&self) -> &AiBridge {
        &self.ai_bridge
    }

    pub async fn gather_full_telemetry(&mut self, config: &AgentConfig) -> TelemetryPayload {
        let mut payload = self.telemetry_collector.collect();

        // 1. GPU Module (if enabled)
        if config.is_feature_enabled("gpu") {
            payload.gpu = GpuCollector::collect();
        }

        // 2. Docker Module (if enabled)
        if config.is_feature_enabled("docker") {
            let mut docker_containers = DockerSupervisor::collect_containers();
            payload.services.append(&mut docker_containers);
        }

        // 3. AI Engine Module (if enabled)
        if config.is_feature_enabled("ai_bridge") {
            let ai_telemetry = self.ai_bridge.check_models().await;
            payload.ai_engine = Some(ai_telemetry);
        }

        payload
    }
}

