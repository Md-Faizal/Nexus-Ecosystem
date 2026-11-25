use crate::engine::EngineState;
use futures_util::{SinkExt, StreamExt};
use nexus_protocol::{HeartbeatPayload, MeshEnvelope, MeshMessage, NodeHelloPayload};
use std::collections::HashMap;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use tokio::sync::watch;
use tokio_tungstenite::{connect_async, tungstenite::Message};
use tracing::{info, warn};

pub struct MeshConfig {
    pub ws_url: String,
    pub node_id: String,
    pub hostname: String,
    pub os_version: String,
    pub arch: String,
    pub agent_version: String,
}

fn now_millis() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

pub async fn run_mesh_loop(
    cfg: MeshConfig,
    state: EngineState,
    mut shutdown_rx: watch::Receiver<bool>,
    on_state_change: impl Fn() + Send + Sync,
) {
    let frame_counter = AtomicU64::new(1);
    let mut backoff_secs = 2u64;

    loop {
        if *shutdown_rx.borrow() {
            break;
        }

        info!("Connecting to Nexus Mesh WS: {}", cfg.ws_url);
        match connect_async(&cfg.ws_url).await {
            Ok((ws_stream, _)) => {
                backoff_secs = 2;
                let (mut sink, mut stream) = ws_stream.split();
                state.set_mesh_connected(true).await;
                on_state_change();

                let hello = MeshEnvelope {
                    frame_id: frame_counter.fetch_add(1, Ordering::Relaxed),
                    node_id: cfg.node_id.clone(),
                    timestamp: now_millis(),
                    msg: MeshMessage::NodeHello(NodeHelloPayload {
                        hostname: cfg.hostname.clone(),
                        os: "android".into(),
                        os_version: cfg.os_version.clone(),
                        arch: cfg.arch.clone(),
                        agent_version: cfg.agent_version.clone(),
                        features: HashMap::from([("mesh_node".into(), true)]),
                    }),
                };

                if let Ok(json) = serde_json::to_string(&hello) {
                    if sink.send(Message::Text(json)).await.is_err() {
                        state.set_mesh_connected(false).await;
                        on_state_change();
                        continue;
                    }
                }

                let mut hb_interval = tokio::time::interval(Duration::from_secs(20));
                let start = std::time::Instant::now();

                loop {
                    tokio::select! {
                        _ = shutdown_rx.changed() => {
                            if *shutdown_rx.borrow() {
                                let _ = sink.close().await;
                                state.set_mesh_connected(false).await;
                                on_state_change();
                                return;
                            }
                        }
                        _ = hb_interval.tick() => {
                            let hb = MeshEnvelope {
                                frame_id: frame_counter.fetch_add(1, Ordering::Relaxed),
                                node_id: cfg.node_id.clone(),
                                timestamp: now_millis(),
                                msg: MeshMessage::Heartbeat(HeartbeatPayload {
                                    uptime_secs: start.elapsed().as_secs(),
                                }),
                            };
                            if let Ok(json) = serde_json::to_string(&hb) {
                                if sink.send(Message::Text(json)).await.is_err() {
                                    break;
                                }
                            }
                        }
                        incoming = stream.next() => {
                            match incoming {
                                Some(Ok(Message::Text(txt))) => {
                                    if let Ok(env) = serde_json::from_str::<MeshEnvelope>(&txt) {
                                        if let MeshMessage::HeartbeatAck(_) = env.msg {
                                            // Connection healthy
                                        }
                                    }
                                }
                                Some(Ok(Message::Close(_))) | Some(Err(_)) | None => {
                                    break;
                                }
                                _ => {}
                            }
                        }
                    }
                }

                state.set_mesh_connected(false).await;
                on_state_change();
            }
            Err(e) => {
                warn!("Mesh WS connection failed: {} (retry in {}s)", e, backoff_secs);
            }
        }

        tokio::select! {
            _ = tokio::time::sleep(Duration::from_secs(backoff_secs)) => {}
            _ = shutdown_rx.changed() => {
                if *shutdown_rx.borrow() {
                    break;
                }
            }
        }
        backoff_secs = (backoff_secs * 2).min(60);
    }
}
