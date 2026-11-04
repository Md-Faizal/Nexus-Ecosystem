use crate::mesh::protocol::ServiceTelemetry;
use std::process::Command;
use tracing::debug;

pub struct DockerSupervisor;

impl DockerSupervisor {
    pub fn collect_containers() -> Vec<ServiceTelemetry> {
        let output = Command::new("docker")
            .args(["ps", "-a", "--format", "{{.Names}}\t{{.Status}}"])
            .output();

        let mut services = Vec::new();

        if let Ok(out) = output {
            if out.status.success() {
                let text = String::from_utf8_lossy(&out.stdout);
                for line in text.lines() {
                    let parts: Vec<&str> = line.split('\t').collect();
                    if !parts.is_empty() {
                        let name = parts[0].to_string();
                        let status_str = parts.get(1).unwrap_or(&"unknown");
                        let status = if status_str.starts_with("Up") {
                            "running"
                        } else {
                            "stopped"
                        };

                        services.push(ServiceTelemetry {
                            name: format!("docker:{}", name),
                            status: status.to_string(),
                            pid: None,
                            memory_mb: None,
                        });
                    }
                }
                debug!("Docker supervisor detected {} containers", services.len());
            }
        }

        services
    }
}

