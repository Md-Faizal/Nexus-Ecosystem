pub mod auth;
pub mod engine;
pub mod mesh;
pub mod storage;
pub mod updater;

use auth::{AuthenticatedSession, FleetNodeView, HubApiClient, PairingCodeInfo};
use engine::{EngineSnapshot, EngineState, LanPeer};
use mesh::{run_mesh_loop, MeshConfig};
use std::path::Path;
use std::sync::{Arc, Mutex};
use storage::LocalStore;
use tokio::runtime::Runtime;
use tokio::sync::watch;
use updater::{check_for_update, download_and_verify_apk, DownloadProgressListener, UpdateInfo};

uniffi::setup_scaffolding!();

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CoreError {
    #[error("{msg}")]
    Message { msg: String },
}

#[uniffi::export(with_foreign)]
pub trait CoreEventListener: Send + Sync {
    fn on_snapshot_changed(&self, snapshot: EngineSnapshot);
}

#[derive(uniffi::Object)]
pub struct NexusMobileCore {
    runtime: Runtime,
    state: EngineState,
    api: Arc<HubApiClient>,
    store: LocalStore,
    base_url: String,
    device_id: String,
    hostname: String,
    os_version: String,
    arch: String,
    agent_version: String,
    listener: Arc<Mutex<Option<Arc<dyn CoreEventListener>>>>,
    mesh_shutdown: Mutex<Option<watch::Sender<bool>>>,
}

#[uniffi::export]
impl NexusMobileCore {
    #[uniffi::constructor]
    pub fn new(
        db_path: String,
        base_url: String,
        device_id: String,
        hostname: String,
        os_version: String,
        arch: String,
        agent_version: String,
        is_authenticated: bool,
    ) -> Result<Self, CoreError> {
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .worker_threads(2)
            .enable_all()
            .build()
            .map_err(|e| CoreError::Message {
                msg: format!("tokio init failed: {e}"),
            })?;

        let store = LocalStore::open(Path::new(&db_path)).map_err(|e| CoreError::Message {
            msg: format!("sqlite open failed: {e}"),
        })?;

        let pending = store.pending_count().unwrap_or(0);
        let state = EngineState::new(device_id.clone(), is_authenticated);
        runtime.block_on(state.set_pending_count(pending));

        Ok(Self {
            runtime,
            state,
            api: Arc::new(HubApiClient::new(base_url.clone())),
            store,
            base_url,
            device_id,
            hostname,
            os_version,
            arch,
            agent_version,
            listener: Arc::new(Mutex::new(None)),
            mesh_shutdown: Mutex::new(None),
        })
    }

    pub fn set_listener(&self, listener: Arc<dyn CoreEventListener>) {
        if let Ok(mut g) = self.listener.lock() {
            *g = Some(listener);
        }
        self.emit_snapshot();
    }

    pub fn current_snapshot(&self) -> EngineSnapshot {
        self.runtime.block_on(self.state.snapshot())
    }

    pub fn start_mesh(&self) {
        let mut guard = match self.mesh_shutdown.lock() {
            Ok(g) => g,
            Err(_) => return,
        };
        if guard.is_some() {
            return;
        }
        let (tx, rx) = watch::channel(false);
        *guard = Some(tx);

        let ws_url = format!(
            "{}/api/mesh/ws",
            self.base_url
                .trim_end_matches('/')
                .replacen("https://", "wss://", 1)
                .replacen("http://", "ws://", 1)
        );
        let cfg = MeshConfig {
            ws_url,
            node_id: self.device_id.clone(),
            hostname: self.hostname.clone(),
            os_version: self.os_version.clone(),
            arch: self.arch.clone(),
            agent_version: self.agent_version.clone(),
        };
        let state = self.state.clone();
        let listener = self.listener.clone();
        let handle = self.runtime.handle().clone();

        self.runtime.spawn(async move {
            run_mesh_loop(cfg, state.clone(), rx, move || {
                let l = listener.lock().ok().and_then(|g| g.clone());
                if let Some(cb) = l {
                    let s = state.clone();
                    handle.spawn(async move {
                        let snap = s.snapshot().await;
                        cb.on_snapshot_changed(snap);
                    });
                }
            })
            .await;
        });
    }

    pub fn stop_mesh(&self) {
        if let Ok(mut g) = self.mesh_shutdown.lock() {
            if let Some(tx) = g.take() {
                let _ = tx.send(true);
            }
        }
    }

    pub fn on_lan_peer_found(&self, service_name: String, host: String, port: u16) {
        let snap = self.runtime.block_on(self.state.upsert_lan_peer(LanPeer {
            service_name,
            host,
            port,
        }));
        self.notify(snap);
    }

    pub fn on_lan_peer_lost(&self, service_name: String) {
        let snap = self.runtime.block_on(self.state.remove_lan_peer(&service_name));
        self.notify(snap);
    }

    pub fn set_authenticated(&self, authed: bool) {
        let snap = self.runtime.block_on(self.state.set_authenticated(authed));
        self.notify(snap);
    }

    pub async fn request_pairing_code(&self) -> Result<PairingCodeInfo, CoreError> {
        self.api
            .request_device_code(&self.device_id)
            .await
            .map_err(|e| CoreError::Message { msg: e.to_string() })
    }

    pub async fn poll_pairing_token(
        &self,
        device_code: String,
    ) -> Result<Option<AuthenticatedSession>, CoreError> {
        let session = self
            .api
            .poll_device_token(&device_code)
            .await
            .map_err(|e| CoreError::Message { msg: e.to_string() })?;
        if session.is_some() {
            self.set_authenticated(true);
        }
        Ok(session)
    }

    pub fn hash_local_pin(&self, pin: String, salt: String) -> String {
        auth::hash_pin(&pin, &salt)
    }

    pub fn verify_local_pin(&self, pin: String, salt: String, expected_hash: String) -> bool {
        auth::verify_pin(&pin, &salt, &expected_hash)
    }

    pub async fn check_for_update(
        &self,
        current_version_code: u32,
    ) -> Result<Option<UpdateInfo>, CoreError> {
        check_for_update(&self.base_url, current_version_code)
            .await
            .map_err(|e| CoreError::Message { msg: e.to_string() })
    }

    pub async fn download_update_apk(
        &self,
        apk_url: String,
        dest_apk_path: String,
        expected_sha256: String,
        listener: Option<Arc<dyn DownloadProgressListener>>,
    ) -> Result<String, CoreError> {
        download_and_verify_apk(&apk_url, &dest_apk_path, &expected_sha256, listener)
            .await
            .map_err(|e| CoreError::Message { msg: e.to_string() })
    }

    pub async fn fetch_fleet_nodes(
        &self,
        access_token: Option<String>,
    ) -> Result<Vec<FleetNodeView>, CoreError> {
        match self.api.fetch_fleet_nodes(access_token.as_deref()).await {
            Ok(nodes) => {
                if let Ok(json) = serde_json::to_string(&nodes.iter().map(|n| &n.node_id).collect::<Vec<_>>()) {
                    let _ = self.store.put_cache("last_fleet_nodes", &json, 0);
                }
                Ok(nodes)
            }
            Err(e) => Err(CoreError::Message { msg: e.to_string() }),
        }
    }
}

impl NexusMobileCore {
    fn emit_snapshot(&self) {
        let snap = self.current_snapshot();
        self.notify(snap);
    }

    fn notify(&self, snap: EngineSnapshot) {
        if let Ok(g) = self.listener.lock() {
            if let Some(cb) = g.as_ref() {
                cb.on_snapshot_changed(snap);
            }
        }
    }
}
