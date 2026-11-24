use chrono::{DateTime, Utc};
use mdns_sd::{ServiceDaemon, ServiceEvent, ServiceInfo};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::{Arc, RwLock};
use std::time::Duration;
use tracing::{debug, info, warn};

pub const NEXUS_MDNS_SERVICE_TYPE: &str = "_nexus._tcp.local.";

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MdnsPeer {
    pub node_id: String,
    pub hostname: String,
    pub ip_addresses: Vec<String>,
    pub port: u16,
    pub role: String,
    pub version: String,
    pub last_seen: DateTime<Utc>,
}

#[derive(Clone)]
pub struct MdnsDiscoveryManager {
    daemon: ServiceDaemon,
    discovered_peers: Arc<RwLock<HashMap<String, MdnsPeer>>>,
}

impl MdnsDiscoveryManager {
    pub fn new() -> Result<Self, String> {
        let daemon = ServiceDaemon::new().map_err(|e| format!("Failed to create mDNS daemon: {}", e))?;
        Ok(Self {
            daemon,
            discovered_peers: Arc::new(RwLock::new(HashMap::new())),
        })
    }

    /// Broadcast this node's presence on the local Wi-Fi / LAN
    pub fn register_self(
        &self,
        node_id: &str,
        role: &str,
        port: u16,
        version: &str,
    ) -> Result<(), String> {
        let instance_name = format!("{}-{}", role, node_id);
        let hostname = format!("{}.local.", node_id);

        let mut properties = HashMap::new();
        properties.insert("node_id".to_string(), node_id.to_string());
        properties.insert("role".to_string(), role.to_string());
        properties.insert("version".to_string(), version.to_string());
        properties.insert("status".to_string(), "active".to_string());

        let service_info = ServiceInfo::new(
            NEXUS_MDNS_SERVICE_TYPE,
            &instance_name,
            &hostname,
            "",
            port,
            properties,
        )
        .map_err(|e| format!("Failed to construct ServiceInfo: {}", e))?;

        self.daemon
            .register(service_info)
            .map_err(|e| format!("Failed to register mDNS service: {}", e))?;

        info!(
            "🛰️ mDNS Broadcast active: {} on port {} (service: {})",
            instance_name, port, NEXUS_MDNS_SERVICE_TYPE
        );
        Ok(())
    }

    /// Start listening for other Nexus nodes on the local Wi-Fi / LAN
    pub fn start_discovery(&self) -> Result<(), String> {
        let receiver = self
            .daemon
            .browse(NEXUS_MDNS_SERVICE_TYPE)
            .map_err(|e| format!("Failed to browse mDNS services: {}", e))?;

        let peers_store = Arc::clone(&self.discovered_peers);

        tokio::spawn(async move {
            debug!("🛰️ mDNS discovery listener started for {}", NEXUS_MDNS_SERVICE_TYPE);
            while let Ok(event) = receiver.recv_async().await {
                match event {
                    ServiceEvent::ServiceResolved(info) => {
                        let ips: Vec<String> = info
                            .get_addresses()
                            .iter()
                            .map(|ip| ip.to_string())
                            .collect();

                        let node_id = info
                            .get_property_val_str("node_id")
                            .unwrap_or_else(|| info.get_fullname())
                            .to_string();

                        let role = info
                            .get_property_val_str("role")
                            .unwrap_or("worker")
                            .to_string();

                        let version = info
                            .get_property_val_str("version")
                            .unwrap_or("unknown")
                            .to_string();

                        let peer = MdnsPeer {
                            node_id: node_id.clone(),
                            hostname: info.get_hostname().to_string(),
                            ip_addresses: ips,
                            port: info.get_port(),
                            role,
                            version,
                            last_seen: Utc::now(),
                        };

                        info!(
                            "✨ Discovered local Nexus peer on LAN: {} ({}:{})",
                            peer.node_id, peer.hostname, peer.port
                        );

                        if let Ok(mut lock) = peers_store.write() {
                            lock.insert(node_id, peer);
                        }
                    }
                    ServiceEvent::ServiceRemoved(_, fullname) => {
                        info!("👋 Local Nexus peer left LAN: {}", fullname);
                        if let Ok(mut lock) = peers_store.write() {
                            lock.retain(|_, v| !fullname.contains(&v.node_id));
                        }
                    }
                    _ => {}
                }
            }
        });

        Ok(())
    }

    /// Retrieve snapshot of currently known local LAN peers
    pub fn get_peers(&self) -> Vec<MdnsPeer> {
        if let Ok(lock) = self.discovered_peers.read() {
            lock.values().cloned().collect()
        } else {
            Vec::new()
        }
    }

    /// Synchronous one-shot discovery scan for CLI discovery command
    pub async fn scan_local_lan(timeout_secs: u64) -> Vec<MdnsPeer> {
        let manager = match Self::new() {
            Ok(m) => m,
            Err(e) => {
                warn!("mDNS daemon init failed: {}", e);
                return Vec::new();
            }
        };

        if let Err(e) = manager.start_discovery() {
            warn!("Failed to initiate mDNS browse: {}", e);
            return Vec::new();
        }

        tokio::time::sleep(Duration::from_secs(timeout_secs)).await;
        manager.get_peers()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_mdns_peer_struct() {
        let peer = MdnsPeer {
            node_id: "potato-pro".to_string(),
            hostname: "potato-pro.local.".to_string(),
            ip_addresses: vec!["192.168.1.15".to_string()],
            port: 11434,
            role: "ai_worker".to_string(),
            version: "0.1.95".to_string(),
            last_seen: Utc::now(),
        };

        let json = serde_json::to_string(&peer).expect("serialize peer");
        assert!(json.contains("potato-pro"));
        assert!(json.contains("11434"));
    }
}
