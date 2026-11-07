use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, AtomicU32, AtomicU64, Ordering};
use std::sync::Arc;
use std::time::Duration;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::{mpsc, RwLock};
use tokio::time::sleep;
use tracing::{debug, error, info, warn};

// ─── Binary Protocol Constants ──────────────────────────────────────────────

const MAGIC: [u8; 2] = [0x4E, 0x58]; // 'N', 'X' (Nexus)
const PROTOCOL_VERSION: u8 = 1;
const HEADER_SIZE: usize = 12;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum MsgType {
    Auth = 0x01,
    AuthAck = 0x02,
    Connect = 0x03,
    Data = 0x04,
    Close = 0x05,
    Ping = 0x06,
    Pong = 0x07,
}

impl TryFrom<u8> for MsgType {
    type Error = String;
    fn try_from(val: u8) -> Result<Self, Self::Error> {
        match val {
            0x01 => Ok(MsgType::Auth),
            0x02 => Ok(MsgType::AuthAck),
            0x03 => Ok(MsgType::Connect),
            0x04 => Ok(MsgType::Data),
            0x05 => Ok(MsgType::Close),
            0x06 => Ok(MsgType::Ping),
            0x07 => Ok(MsgType::Pong),
            other => Err(format!("Unknown MsgType: 0x{:02x}", other)),
        }
    }
}

#[derive(Debug, Clone)]
pub struct TunnelFrame {
    pub msg_type: MsgType,
    pub stream_id: u32,
    pub payload: Vec<u8>,
}

impl TunnelFrame {
    pub fn new(msg_type: MsgType, stream_id: u32, payload: Vec<u8>) -> Self {
        Self {
            msg_type,
            stream_id,
            payload,
        }
    }

    pub fn serialize(&self) -> Vec<u8> {
        let mut buf = Vec::with_capacity(HEADER_SIZE + self.payload.len());
        buf.extend_from_slice(&MAGIC);
        buf.push(PROTOCOL_VERSION);
        buf.push(self.msg_type as u8);
        buf.extend_from_slice(&self.stream_id.to_be_bytes());
        buf.extend_from_slice(&(self.payload.len() as u32).to_be_bytes());
        buf.extend_from_slice(&self.payload);
        buf
    }

    pub async fn read_from<R: AsyncReadExt + Unpin>(
        reader: &mut R,
    ) -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        let mut header = [0u8; HEADER_SIZE];
        reader.read_exact(&mut header).await?;

        if header[0..2] != MAGIC {
            return Err("Invalid frame magic".into());
        }
        if header[2] != PROTOCOL_VERSION {
            return Err(format!("Unsupported protocol version: {}", header[2]).into());
        }

        let msg_type = MsgType::try_from(header[3])?;
        let stream_id = u32::from_be_bytes([header[4], header[5], header[6], header[7]]);
        let length = u32::from_be_bytes([header[8], header[9], header[10], header[11]]) as usize;

        if length > 16 * 1024 * 1024 {
            return Err(format!("Frame payload too large: {} bytes", length).into());
        }

        let mut payload = vec![0u8; length];
        if length > 0 {
            reader.read_exact(&mut payload).await?;
        }

        Ok(Self {
            msg_type,
            stream_id,
            payload,
        })
    }
}

// ─── Tunnel Edge Server (Public Gateway) ────────────────────────────────────

pub struct TunnelServer;

impl TunnelServer {
    pub async fn run(
        bind_addr: &str,
        control_addr: &str,
        expected_token: Option<String>,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        info!("============================================================");
        info!("   NEXUS WARFARE REVERSE TUNNEL - CLOUD EDGE GATEWAY");
        info!("   Public Ingress:  {}", bind_addr);
        info!("   Control Link:    {}", control_addr);
        info!(
            "   Authentication:  {}",
            if expected_token.is_some() {
                "Token Protected"
            } else {
                "Open (Any Peer)"
            }
        );
        info!("============================================================");

        let control_listener = TcpListener::bind(control_addr).await?;
        let public_listener = TcpListener::bind(bind_addr).await?;

        let active_control_tx = Arc::new(RwLock::new(None::<mpsc::Sender<TunnelFrame>>));
        let active_streams: Arc<RwLock<HashMap<u32, mpsc::Sender<Vec<u8>>>>> =
            Arc::new(RwLock::new(HashMap::new()));
        let stream_counter = Arc::new(AtomicU32::new(1));
        let total_bytes_forwarded = Arc::new(AtomicU64::new(0));

        // Control Accept Loop
        let ctrl_tx_clone = active_control_tx.clone();
        let streams_clone = active_streams.clone();
        let token_clone = expected_token.clone();
        let bytes_tx = total_bytes_forwarded.clone();

        tokio::spawn(async move {
            loop {
                match control_listener.accept().await {
                    Ok((mut socket, peer_addr)) => {
                        info!("Incoming control link connection from {}", peer_addr);
                        let _ = socket.set_nodelay(true);

                        // Authenticate
                        let auth_res = async {
                            let frame = TunnelFrame::read_from(&mut socket).await?;
                            if frame.msg_type != MsgType::Auth {
                                return Err("First frame must be Auth".into());
                            }
                            if let Some(ref required_token) = token_clone {
                                let received = String::from_utf8_lossy(&frame.payload);
                                if received.trim() != required_token.trim() {
                                    return Err("Authentication token mismatch".into());
                                }
                            }
                            let ack = TunnelFrame::new(MsgType::AuthAck, 0, b"OK".to_vec());
                            socket.write_all(&ack.serialize()).await?;
                            Ok::<(), Box<dyn std::error::Error + Send + Sync>>(())
                        }
                        .await;

                        if let Err(e) = auth_res {
                            warn!("Rejected control link from {}: {}", peer_addr, e);
                            continue;
                        }

                        info!("Control link authenticated successfully with {}", peer_addr);

                        let (mut reader, mut writer) = socket.into_split();
                        let (tx, mut rx) = mpsc::channel::<TunnelFrame>(2048);

                        *ctrl_tx_clone.write().await = Some(tx.clone());

                        // Writer Task
                        let writer_task = tokio::spawn(async move {
                            while let Some(frame) = rx.recv().await {
                                if let Err(e) = writer.write_all(&frame.serialize()).await {
                                    debug!("Control write error: {}", e);
                                    break;
                                }
                            }
                        });

                        // Reader Task
                        let streams_inner = streams_clone.clone();
                        let tx_inner = tx.clone();
                        let bytes_inner = bytes_tx.clone();

                        tokio::spawn(async move {
                            loop {
                                match TunnelFrame::read_from(&mut reader).await {
                                    Ok(frame) => match frame.msg_type {
                                        MsgType::Data => {
                                            bytes_inner.fetch_add(
                                                frame.payload.len() as u64,
                                                Ordering::Relaxed,
                                            );
                                            let map = streams_inner.read().await;
                                            if let Some(ch) = map.get(&frame.stream_id) {
                                                let _ = ch.send(frame.payload).await;
                                            }
                                        }
                                        MsgType::Close => {
                                            let mut map = streams_inner.write().await;
                                            map.remove(&frame.stream_id);
                                        }
                                        MsgType::Ping => {
                                            let pong = TunnelFrame::new(MsgType::Pong, 0, vec![]);
                                            let _ = tx_inner.send(pong).await;
                                        }
                                        _ => {}
                                    },
                                    Err(e) => {
                                        warn!("Control link read terminated: {}", e);
                                        break;
                                    }
                                }
                            }
                        });

                        let _ = writer_task.await;
                        *ctrl_tx_clone.write().await = None;
                        info!("Control link disconnected. Waiting for client reconnect...");
                    }
                    Err(e) => {
                        error!("Control listener accept error: {}", e);
                        sleep(Duration::from_millis(500)).await;
                    }
                }
            }
        });

        // Public Player Ingress Loop
        info!(
            "Public game port listening on {}. Ready for player traffic.",
            bind_addr
        );
        loop {
            match public_listener.accept().await {
                Ok((player_socket, player_addr)) => {
                    let _ = player_socket.set_nodelay(true);
                    let ctrl_tx = active_control_tx.read().await.clone();

                    if let Some(ctrl) = ctrl_tx {
                        let stream_id = stream_counter.fetch_add(1, Ordering::SeqCst);
                        debug!(
                            "Player connected from {} -> Assigned virtual stream #{}",
                            player_addr, stream_id
                        );

                        // Notify client to connect to local Forge server
                        let conn_frame = TunnelFrame::new(MsgType::Connect, stream_id, vec![]);
                        if let Err(e) = ctrl.send(conn_frame).await {
                            warn!("Failed to dispatch connect frame to control link: {}", e);
                            continue;
                        }

                        let (player_rx_stream, player_tx_stream) = player_socket.into_split();
                        let (stream_tx, mut stream_rx) = mpsc::channel::<Vec<u8>>(1024);

                        active_streams.write().await.insert(stream_id, stream_tx);

                        // Player -> Control Link
                        let ctrl_fwd = ctrl.clone();
                        let streams_cleanup = active_streams.clone();
                        let bytes_count = total_bytes_forwarded.clone();

                        tokio::spawn(async move {
                            let mut buf = vec![0u8; 8192];
                            let mut reader = player_rx_stream;
                            loop {
                                match reader.read(&mut buf).await {
                                    Ok(0) => break,
                                    Ok(n) => {
                                        bytes_count.fetch_add(n as u64, Ordering::Relaxed);
                                        let data_frame = TunnelFrame::new(
                                            MsgType::Data,
                                            stream_id,
                                            buf[..n].to_vec(),
                                        );
                                        if ctrl_fwd.send(data_frame).await.is_err() {
                                            break;
                                        }
                                    }
                                    Err(_) => break,
                                }
                            }
                            let close_frame = TunnelFrame::new(MsgType::Close, stream_id, vec![]);
                            let _ = ctrl_fwd.send(close_frame).await;
                            streams_cleanup.write().await.remove(&stream_id);
                        });

                        // Control Link -> Player
                        let streams_cleanup2 = active_streams.clone();
                        tokio::spawn(async move {
                            let mut writer = player_tx_stream;
                            while let Some(chunk) = stream_rx.recv().await {
                                if writer.write_all(&chunk).await.is_err() {
                                    break;
                                }
                            }
                            streams_cleanup2.write().await.remove(&stream_id);
                        });
                    } else {
                        warn!(
                            "Incoming connection from {}, but no backend server is tunneled! Closing.",
                            player_addr
                        );
                    }
                }
                Err(e) => {
                    error!("Public ingress accept error: {}", e);
                    sleep(Duration::from_millis(100)).await;
                }
            }
        }
    }
}

// ─── Tunnel Client Worker (Local Bare-Metal Server) ─────────────────────────

pub struct TunnelClient;

impl TunnelClient {
    pub async fn run(
        edge_addr: &str,
        target_addr: &str,
        token: Option<String>,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        info!("============================================================");
        info!("   NEXUS WARFARE REVERSE TUNNEL - BARE-METAL CLIENT");
        info!("   Edge Gateway:    {}", edge_addr);
        info!("   Local Server:    {}", target_addr);
        info!("============================================================");

        let mut backoff = Duration::from_secs(1);
        let max_backoff = Duration::from_secs(15);

        loop {
            info!("Attempting connection to Cloud Edge: {}", edge_addr);
            match TcpStream::connect(edge_addr).await {
                Ok(mut control_socket) => {
                    let _ = control_socket.set_nodelay(true);
                    info!("TCP connection established. Sending authentication...");

                    let auth_token = token.clone().unwrap_or_else(|| "nexus-fleet".to_string());
                    let auth_frame =
                        TunnelFrame::new(MsgType::Auth, 0, auth_token.into_bytes());

                    if let Err(e) = control_socket.write_all(&auth_frame.serialize()).await {
                        warn!("Failed to send Auth frame: {}", e);
                        sleep(backoff).await;
                        continue;
                    }

                    match TunnelFrame::read_from(&mut control_socket).await {
                        Ok(ack) if ack.msg_type == MsgType::AuthAck => {
                            info!("✅ Authenticated successfully with Cloud Edge: {}", edge_addr);
                            backoff = Duration::from_secs(1);
                        }
                        Ok(other) => {
                            warn!("Unexpected response from edge: {:?}", other);
                            sleep(backoff).await;
                            continue;
                        }
                        Err(e) => {
                            warn!("Auth response error: {}", e);
                            sleep(backoff).await;
                            continue;
                        }
                    }

                    let (mut reader, mut writer) = control_socket.into_split();
                    let (tx_to_edge, mut rx_from_local) = mpsc::channel::<TunnelFrame>(2048);
                    let active_local_streams: Arc<RwLock<HashMap<u32, mpsc::Sender<Vec<u8>>>>> =
                        Arc::new(RwLock::new(HashMap::new()));

                    // Writer Task: Outbox to Edge
                    let writer_task = tokio::spawn(async move {
                        while let Some(frame) = rx_from_local.recv().await {
                            if let Err(e) = writer.write_all(&frame.serialize()).await {
                                debug!("Write to edge failed: {}", e);
                                break;
                            }
                        }
                    });

                    // Heartbeat Pinger
                    let tx_ping = tx_to_edge.clone();
                    let is_active = Arc::new(AtomicBool::new(true));
                    let is_active_clone = is_active.clone();

                    let pinger_task = tokio::spawn(async move {
                        while is_active_clone.load(Ordering::Relaxed) {
                            sleep(Duration::from_secs(10)).await;
                            let ping_frame = TunnelFrame::new(MsgType::Ping, 0, vec![]);
                            if tx_ping.send(ping_frame).await.is_err() {
                                break;
                            }
                        }
                    });

                    // Reader Task: Incoming from Edge
                    let local_streams = active_local_streams.clone();
                    let target_clone = target_addr.to_string();
                    let tx_reply = tx_to_edge.clone();

                    loop {
                        match TunnelFrame::read_from(&mut reader).await {
                            Ok(frame) => match frame.msg_type {
                                MsgType::Connect => {
                                    let stream_id = frame.stream_id;
                                    let target_str = target_clone.clone();
                                    let tx_fwd = tx_reply.clone();
                                    let streams_ref = local_streams.clone();

                                    debug!("Connecting to local Forge server for stream #{}", stream_id);

                                    tokio::spawn(async move {
                                        match TcpStream::connect(&target_str).await {
                                            Ok(local_socket) => {
                                                let _ = local_socket.set_nodelay(true);
                                                let (local_rx, local_tx) =
                                                    local_socket.into_split();
                                                let (chan_tx, mut chan_rx) =
                                                    mpsc::channel::<Vec<u8>>(1024);

                                                streams_ref
                                                    .write()
                                                    .await
                                                    .insert(stream_id, chan_tx);

                                                // Local -> Edge
                                                let tx_fwd_inner = tx_fwd.clone();
                                                let streams_clean = streams_ref.clone();
                                                tokio::spawn(async move {
                                                    let mut buf = vec![0u8; 8192];
                                                    let mut r = local_rx;
                                                    loop {
                                                        match r.read(&mut buf).await {
                                                            Ok(0) => break,
                                                            Ok(n) => {
                                                                let f = TunnelFrame::new(
                                                                    MsgType::Data,
                                                                    stream_id,
                                                                    buf[..n].to_vec(),
                                                                );
                                                                if tx_fwd_inner.send(f).await.is_err() {
                                                                    break;
                                                                }
                                                            }
                                                            Err(_) => break,
                                                        }
                                                    }
                                                    let close = TunnelFrame::new(
                                                        MsgType::Close,
                                                        stream_id,
                                                        vec![],
                                                    );
                                                    let _ = tx_fwd_inner.send(close).await;
                                                    streams_clean.write().await.remove(&stream_id);
                                                });

                                                // Edge -> Local
                                                let streams_clean2 = streams_ref.clone();
                                                tokio::spawn(async move {
                                                    let mut w = local_tx;
                                                    while let Some(chunk) = chan_rx.recv().await {
                                                        if w.write_all(&chunk).await.is_err() {
                                                            break;
                                                        }
                                                    }
                                                    streams_clean2.write().await.remove(&stream_id);
                                                });
                                            }
                                            Err(e) => {
                                                error!(
                                                    "Failed to connect to local game server {}: {}",
                                                    target_str, e
                                                );
                                                let close = TunnelFrame::new(
                                                    MsgType::Close,
                                                    stream_id,
                                                    vec![],
                                                );
                                                let _ = tx_fwd.send(close).await;
                                            }
                                        }
                                    });
                                }
                                MsgType::Data => {
                                    let map = local_streams.read().await;
                                    if let Some(ch) = map.get(&frame.stream_id) {
                                        let _ = ch.send(frame.payload).await;
                                    }
                                }
                                MsgType::Close => {
                                    let mut map = local_streams.write().await;
                                    map.remove(&frame.stream_id);
                                }
                                MsgType::Pong => {
                                    debug!("Heartbeat Pong received from Edge Gateway");
                                }
                                _ => {}
                            },
                            Err(e) => {
                                warn!("Connection to Cloud Edge lost: {}. Reconnecting...", e);
                                break;
                            }
                        }
                    }

                    is_active.store(false, Ordering::Relaxed);
                    let _ = writer_task.await;
                    let _ = pinger_task.await;
                }
                Err(e) => {
                    warn!(
                        "Could not connect to Cloud Edge ({}): {}. Retrying in {:?}...",
                        edge_addr, e, backoff
                    );
                    sleep(backoff).await;
                    backoff = (backoff * 2).min(max_backoff);
                }
            }
        }
    }
}
