use crate::AppStateInner;
use std::sync::Arc;
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::{TcpListener, TcpStream};
use tracing::{error, info, warn};

pub async fn start_local_smtp_ingress(
    bind_addr: &str,
    state: Arc<AppStateInner>,
) {
    let listener = match TcpListener::bind(bind_addr).await {
        Ok(l) => {
            info!("Nexus Mail Ingress Gateway listening on {}", bind_addr);
            l
        }
        Err(e) => {
            error!("Failed to bind Mail Ingress Gateway on {}: {}", bind_addr, e);
            return;
        }
    };

    loop {
        match listener.accept().await {
            Ok((stream, peer)) => {
                let state_clone = state.clone();
                tokio::spawn(async move {
                    if let Err(e) = handle_smtp_session(stream, state_clone).await {
                        warn!("SMTP session from {} ended with error: {}", peer, e);
                    }
                });
            }
            Err(e) => {
                error!("Error accepting SMTP connection: {}", e);
            }
        }
    }
}

async fn handle_smtp_session(
    mut stream: TcpStream,
    state: Arc<AppStateInner>,
) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let (reader, mut writer) = stream.split();
    let mut reader = BufReader::new(reader);

    // Initial greeting
    writer.write_all(b"220 nexus-mail-gateway ESMTP ready\r\n").await?;
    writer.flush().await?;

    let mut line = String::new();
    let mut sender = String::new();
    let mut recipients: Vec<String> = Vec::new();
    let mut in_data = false;
    let mut raw_data = String::new();

    loop {
        line.clear();
        let bytes_read = reader.read_line(&mut line).await?;
        if bytes_read == 0 {
            break;
        }

        if in_data {
            if line.trim_end() == "." {
                in_data = false;
                let (subject, text_body) = parse_simple_mime(&raw_data);

                info!(
                    "Mail Gateway: Received message from '{}' with subject '{}' for {} recipient(s)",
                    sender, subject, recipients.len()
                );

                for rcpt in &recipients {
                    let task_id = uuid::Uuid::new_v4().to_string();
                    let subj = if subject.is_empty() {
                        "Relayed Message via Nexus Gateway".to_string()
                    } else {
                        subject.clone()
                    };
                    let text_opt = if text_body.is_empty() { None } else { Some(text_body.as_str()) };

                    if let Err(e) = crate::db::enqueue_mail_spool(
                        &state.db,
                        &task_id,
                        rcpt,
                        &subj,
                        &raw_data,
                        text_opt,
                        "stalwart_mesh_relay",
                        1, // Priority 1 (highest priority)
                        Some("Relayed from Stalwart Tier-2 Mesh Ingress"),
                    ).await {
                        error!("Failed to enqueue relayed mail into spool: {}", e);
                    }
                }

                // Immediately trigger spool drain to best node
                let dispatched = crate::routes::mesh_api::drain_spool_to_best_node(&state).await;
                info!("Mail Gateway: Spool drain triggered, dispatched {} task(s) to mesh", dispatched);

                writer.write_all(b"250 2.0.0 OK: Queued for Nexus Mesh relay\r\n").await?;
                writer.flush().await?;

                recipients.clear();
                raw_data.clear();
                continue;
            }
            raw_data.push_str(&line);
            continue;
        }

        let trimmed = line.trim();
        let upper = trimmed.to_uppercase();

        if upper.starts_with("EHLO") || upper.starts_with("HELO") {
            let ehlo_resp = "250-nexus-mail-gateway Hello [127.0.0.1]\r\n250-SIZE 52428800\r\n250-8BITMIME\r\n250-SMTPUTF8\r\n250 OK\r\n";
            writer.write_all(ehlo_resp.as_bytes()).await?;
            writer.flush().await?;
        } else if upper.starts_with("MAIL FROM:") {
            sender = extract_email(trimmed);
            writer.write_all(b"250 2.1.0 Sender OK\r\n").await?;
            writer.flush().await?;
        } else if upper.starts_with("RCPT TO:") {
            let rcpt = extract_email(trimmed);
            if !rcpt.is_empty() {
                recipients.push(rcpt);
            }
            writer.write_all(b"250 2.1.5 Recipient OK\r\n").await?;
            writer.flush().await?;
        } else if upper == "DATA" {
            in_data = true;
            raw_data.clear();
            writer.write_all(b"354 Start mail input; end with <CRLF>.<CRLF>\r\n").await?;
            writer.flush().await?;
        } else if upper == "RSET" {
            sender.clear();
            recipients.clear();
            raw_data.clear();
            writer.write_all(b"250 2.0.0 OK\r\n").await?;
            writer.flush().await?;
        } else if upper == "NOOP" {
            writer.write_all(b"250 2.0.0 OK\r\n").await?;
            writer.flush().await?;
        } else if upper == "QUIT" {
            writer.write_all(b"221 2.0.0 Bye\r\n").await?;
            writer.flush().await?;
            break;
        } else {
            writer.write_all(b"500 5.5.1 Command unrecognized\r\n").await?;
            writer.flush().await?;
        }
    }

    Ok(())
}

fn extract_email(line: &str) -> String {
    if let Some(start) = line.find('<') {
        if let Some(end) = line[start..].find('>') {
            return line[start + 1..start + end].trim().to_string();
        }
    }
    if let Some(idx) = line.find(':') {
        return line[idx + 1..].trim().to_string();
    }
    line.trim().to_string()
}

fn parse_simple_mime(raw: &str) -> (String, String) {
    let mut subject = String::new();
    let mut body = String::new();
    let mut in_headers = true;

    for line in raw.lines() {
        if in_headers {
            if line.is_empty() {
                in_headers = false;
                continue;
            }
            if line.to_ascii_lowercase().starts_with("subject:") {
                subject = line["subject:".len()..].trim().to_string();
            }
        } else {
            body.push_str(line);
            body.push('\n');
        }
    }

    (subject, body)
}
