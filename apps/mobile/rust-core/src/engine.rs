use std::collections::HashMap;
use std::sync::Arc;
use tokio::sync::RwLock;

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum ConnectionMode {
    Offline,
    LocalLan,
    CentralCloud,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct LanPeer {
    pub service_name: String,
    pub host: String,
    pub port: u16,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct EngineSnapshot {
    pub mode: ConnectionMode,
    pub is_authenticated: bool,
    pub mesh_connected: bool,
    pub lan_peer_count: u32,
    pub primary_lan_host: Option<String>,
    pub pending_offline_events: u64,
    pub device_id: String,
}

#[derive(Clone)]
pub struct EngineState {
    inner: Arc<RwLock<InnerState>>,
}

struct InnerState {
    device_id: String,
    is_authenticated: bool,
    mesh_connected: bool,
    lan_peers: HashMap<String, LanPeer>,
    pending_offline_events: u64,
}

impl EngineState {
    pub fn new(device_id: String, is_authenticated: bool) -> Self {
        Self {
            inner: Arc::new(RwLock::new(InnerState {
                device_id,
                is_authenticated,
                mesh_connected: false,
                lan_peers: HashMap::new(),
                pending_offline_events: 0,
            })),
        }
    }

    pub async fn snapshot(&self) -> EngineSnapshot {
        let g = self.inner.read().await;
        let lan_peer_count = g.lan_peers.len() as u32;
        let primary_lan_host = g.lan_peers.values().next().map(|p| p.host.clone());
        let mode = compute_mode(lan_peer_count, g.mesh_connected, g.is_authenticated);
        EngineSnapshot {
            mode,
            is_authenticated: g.is_authenticated,
            mesh_connected: g.mesh_connected,
            lan_peer_count,
            primary_lan_host,
            pending_offline_events: g.pending_offline_events,
            device_id: g.device_id.clone(),
        }
    }

    pub async fn set_authenticated(&self, authed: bool) -> EngineSnapshot {
        {
            let mut g = self.inner.write().await;
            g.is_authenticated = authed;
        }
        self.snapshot().await
    }

    pub async fn set_mesh_connected(&self, connected: bool) -> EngineSnapshot {
        {
            let mut g = self.inner.write().await;
            g.mesh_connected = connected;
        }
        self.snapshot().await
    }

    pub async fn upsert_lan_peer(&self, peer: LanPeer) -> EngineSnapshot {
        {
            let mut g = self.inner.write().await;
            g.lan_peers.insert(peer.service_name.clone(), peer);
        }
        self.snapshot().await
    }

    pub async fn remove_lan_peer(&self, service_name: &str) -> EngineSnapshot {
        {
            let mut g = self.inner.write().await;
            g.lan_peers.remove(service_name);
        }
        self.snapshot().await
    }

    pub async fn set_pending_count(&self, count: u64) -> EngineSnapshot {
        {
            let mut g = self.inner.write().await;
            g.pending_offline_events = count;
        }
        self.snapshot().await
    }
}

fn compute_mode(lan_peers: u32, mesh_connected: bool, is_authenticated: bool) -> ConnectionMode {
    if lan_peers > 0 {
        ConnectionMode::LocalLan
    } else if mesh_connected || is_authenticated {
        ConnectionMode::CentralCloud
    } else {
        ConnectionMode::Offline
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn failover_transitions_correctly() {
        let state = EngineState::new("dev-1".into(), false);
        assert_eq!(state.snapshot().await.mode, ConnectionMode::Offline);

        let snap = state.set_authenticated(true).await;
        assert_eq!(snap.mode, ConnectionMode::CentralCloud);

        let snap = state
            .upsert_lan_peer(LanPeer {
                service_name: "nexus-desktop".into(),
                host: "192.168.1.42".into(),
                port: 8443,
            })
            .await;
        assert_eq!(snap.mode, ConnectionMode::LocalLan);
        assert_eq!(snap.primary_lan_host.as_deref(), Some("192.168.1.42"));

        let snap = state.remove_lan_peer("nexus-desktop").await;
        assert_eq!(snap.mode, ConnectionMode::CentralCloud);
    }
}
