use crate::db::{
    is_mail_responder_cooldown, log_mail_responder_event, record_mail_responder_reply,
};
use crate::mailer::send_auto_reply;
use crate::state::SharedState;
use rustls_pki_types::ServerName;
use std::sync::Arc;
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::TcpStream;
use tokio_rustls::{rustls, TlsConnector};
use tracing::{error, info, warn};

// Default embedded templates as guaranteed fallback
const EMBEDDED_HTML_TEMPLATE: &str = include_str!("../../mail/responder/templates/acknowledgement.html");
const EMBEDDED_TXT_TEMPLATE: &str = include_str!("../../mail/responder/templates/acknowledgement.txt");

#[derive(Debug)]
struct DangerSkipCertVerifier;

impl rustls::client::danger::ServerCertVerifier for DangerSkipCertVerifier {
    fn verify_server_cert(
        &self,
        _end_entity: &rustls_pki_types::CertificateDer<'_>,
        _intermediates: &[rustls_pki_types::CertificateDer<'_>],
        _server_name: &rustls_pki_types::ServerName<'_>,
        _ocsp_response: &[u8],
        _now: rustls_pki_types::UnixTime,
    ) -> Result<rustls::client::danger::ServerCertVerified, rustls::Error> {
        Ok(rustls::client::danger::ServerCertVerified::assertion())
    }

    fn verify_tls12_signature(
        &self,
        _message: &[u8],
        _cert: &rustls_pki_types::CertificateDer<'_>,
        _dss: &rustls::DigitallySignedStruct,
    ) -> Result<rustls::client::danger::HandshakeSignatureValid, rustls::Error> {
        Ok(rustls::client::danger::HandshakeSignatureValid::assertion())
    }

    fn verify_tls13_signature(
        &self,
        _message: &[u8],
        _cert: &rustls_pki_types::CertificateDer<'_>,
        _dss: &rustls::DigitallySignedStruct,
    ) -> Result<rustls::client::danger::HandshakeSignatureValid, rustls::Error> {
        Ok(rustls::client::danger::HandshakeSignatureValid::assertion())
    }

    fn supported_verify_schemes(&self) -> Vec<rustls::SignatureScheme> {
        vec![
            rustls::SignatureScheme::RSA_PKCS1_SHA256,
            rustls::SignatureScheme::RSA_PSS_SHA256,
            rustls::SignatureScheme::ECDSA_NISTP256_SHA256,
            rustls::SignatureScheme::ED25519,
        ]
    }
}

pub fn start_auto_responder(state: SharedState) {
    let _ = rustls::crypto::ring::default_provider().install_default();

    tokio::spawn(async move {
        info!("🦀 Native Rust Auto-Responder: Initialized background engine for me@faizal.uk");
        // Initial pause to allow database and mailserver to settle
        tokio::time::sleep(std::time::Duration::from_secs(8)).await;

        loop {
            if let Err(e) = check_inbox_and_reply(&state).await {
                // Network or connection errors logged at debug/warn
                warn!("Rust Auto-Responder cycle notice: {}", e);
            }
            tokio::time::sleep(std::time::Duration::from_secs(15)).await;
        }
    });
}

async fn check_inbox_and_reply(
    state: &SharedState,
) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let client_config = rustls::ClientConfig::builder()
        .dangerous()
        .with_custom_certificate_verifier(Arc::new(DangerSkipCertVerifier))
        .with_no_client_auth();

    let connector = TlsConnector::from(Arc::new(client_config));
    let stream = TcpStream::connect(("127.0.0.1", 993)).await?;
    let domain = ServerName::try_from("mail.faizal.uk").unwrap();
    let tls_stream = connector.connect(domain, stream).await?;

    let (reader, mut writer) = tokio::io::split(tls_stream);
    let mut reader = BufReader::new(reader);

    // 1. Read IMAP Greeting
    let mut line = String::new();
    reader.read_line(&mut line).await?;
    if !line.starts_with("* OK") {
        return Err(format!("Unexpected IMAP greeting: {}", line).into());
    }

    // 2. Authenticate
    writer
        .write_all(b"A01 LOGIN me@faizal.uk FaizalMail2026!\r\n")
        .await?;
    writer.flush().await?;

    let mut logged_in = false;
    loop {
        line.clear();
        if reader.read_line(&mut line).await? == 0 {
            break;
        }
        if line.starts_with("A01 OK") {
            logged_in = true;
            break;
        } else if line.starts_with("A01 NO") || line.starts_with("A01 BAD") {
            return Err(format!("IMAP Login failed: {}", line).into());
        }
    }
    if !logged_in {
        return Err("IMAP session closed during login".into());
    }

    // 3. SELECT INBOX
    writer.write_all(b"A02 SELECT INBOX\r\n").await?;
    writer.flush().await?;

    loop {
        line.clear();
        if reader.read_line(&mut line).await? == 0 {
            break;
        }
        if line.starts_with("A02 OK") {
            break;
        } else if line.starts_with("A02 NO") || line.starts_with("A02 BAD") {
            return Err(format!("IMAP SELECT failed: {}", line).into());
        }
    }

    // 4. SEARCH UNSEEN
    writer.write_all(b"A03 SEARCH UNSEEN\r\n").await?;
    writer.flush().await?;

    let mut unseen_ids = Vec::new();
    loop {
        line.clear();
        if reader.read_line(&mut line).await? == 0 {
            break;
        }
        if line.starts_with("* SEARCH") {
            for token in line["* SEARCH".len()..].split_whitespace() {
                if let Ok(id) = token.parse::<u32>() {
                    unseen_ids.push(id);
                }
            }
        } else if line.starts_with("A03 OK") {
            break;
        } else if line.starts_with("A03 NO") || line.starts_with("A03 BAD") {
            return Err(format!("IMAP SEARCH failed: {}", line).into());
        }
    }

    if unseen_ids.is_empty() {
        let _ = writer.write_all(b"A99 LOGOUT\r\n").await;
        let _ = writer.flush().await;
        return Ok(());
    }

    info!(
        "🦀 Rust Auto-Responder: Found {} unseen message(s) in me@faizal.uk inbox",
        unseen_ids.len()
    );

    // Load templates (priority: dynamic file -> embedded fallback)
    let template_html = load_html_template();
    let template_txt = load_txt_template();

    // 5. Process each unseen message
    for id in unseen_ids {
        let tag = format!("F{:03}", id % 900);
        let cmd = format!(
            "{} FETCH {} (BODY.PEEK[HEADER.FIELDS (FROM SUBJECT MESSAGE-ID IN-REPLY-TO REFERENCES AUTO-SUBMITTED PRECEDENCE LIST-ID LIST-UNSUBSCRIBE X-AUTOREPLY)])\r\n",
            tag, id
        );
        writer.write_all(cmd.as_bytes()).await?;
        writer.flush().await?;

        let mut header_lines = Vec::new();
        loop {
            line.clear();
            if reader.read_line(&mut line).await? == 0 {
                break;
            }
            if line.starts_with(&tag) {
                break;
            }
            header_lines.push(line.clone());
        }

        let headers = parse_headers(&header_lines);
        let sender_email = headers.from_email.to_lowercase();
        let sender_name = headers.from_name;
        let subject = headers.subject;
        let msg_id = headers.message_id.unwrap_or_default();

        if sender_email.is_empty() {
            continue;
        }

        // Check Suppression
        let (suppressed, reason) = evaluate_suppression(&headers, &sender_email, state).await;
        if suppressed {
            info!(
                "⏩ Rust Auto-Responder: Suppressed reply to '{}' (Reason: {})",
                sender_email, reason
            );
            let _ = log_mail_responder_event(
                &state.db,
                &msg_id,
                &sender_email,
                &subject,
                &format!("DROPPED: {}", reason),
            )
            .await;

            // Mark seen to prevent re-processing
            let store_tag = format!("S{:03}", id % 900);
            let store_cmd = format!("{} STORE {} +FLAGS (\\Seen)\r\n", store_tag, id);
            let _ = writer.write_all(store_cmd.as_bytes()).await;
            let _ = writer.flush().await;
            continue;
        }

        info!(
            "⚡ Rust Auto-Responder: Processing auto-reply for '{}' (Subject: '{}')",
            sender_email, subject
        );

        // Personalize template with actual recipient email
        let mut html = template_html.clone();
        let mut txt = template_txt.clone();
        for placeholder in &[
            "{{ recipient_email }}",
            "{{recipient_email}}",
            "%7B%7B+recipient_email+%7D%7D",
            "%7B%7Brecipient_email%7D%7D",
            "%7B%7B%20recipient_email%20%7D%7D",
        ] {
            html = html.replace(placeholder, &sender_email);
            txt = txt.replace(placeholder, &sender_email);
        }

        // Dispatch via Lettre SMTP
        match send_auto_reply(
            &sender_email,
            &sender_name,
            if msg_id.is_empty() { None } else { Some(&msg_id) },
            &subject,
            &html,
            &txt,
        )
        .await
        {
            Ok(_) => {
                let _ = record_mail_responder_reply(&state.db, &sender_email).await;
                let _ = log_mail_responder_event(
                    &state.db,
                    &msg_id,
                    &sender_email,
                    &subject,
                    "REPLIED",
                )
                .await;
            }
            Err(e) => {
                error!(
                    "❌ Rust Auto-Responder: Failed to dispatch reply to {}: {}",
                    sender_email, e
                );
            }
        }

        // Mark message as \Seen in IMAP
        let store_tag = format!("S{:03}", id % 900);
        let store_cmd = format!("{} STORE {} +FLAGS (\\Seen)\r\n", store_tag, id);
        let _ = writer.write_all(store_cmd.as_bytes()).await;
        let _ = writer.flush().await;
    }

    let _ = writer.write_all(b"A99 LOGOUT\r\n").await;
    let _ = writer.flush().await;
    Ok(())
}

#[derive(Default)]
struct ParsedHeaders {
    from_email: String,
    from_name: String,
    subject: String,
    message_id: Option<String>,
    in_reply_to: Option<String>,
    auto_submitted: Option<String>,
    precedence: Option<String>,
    is_list: bool,
}

fn parse_headers(lines: &[String]) -> ParsedHeaders {
    let mut parsed = ParsedHeaders::default();

    for line in lines {
        let trimmed = line.trim();
        let lower = trimmed.to_ascii_lowercase();

        if lower.starts_with("from:") {
            let val = trimmed["from:".len()..].trim();
            if let Some(start) = val.find('<') {
                if let Some(end) = val[start..].find('>') {
                    parsed.from_email = val[start + 1..start + end].trim().to_string();
                    parsed.from_name = val[..start].trim().trim_matches('"').to_string();
                }
            }
            if parsed.from_email.is_empty() {
                parsed.from_email = val.trim_matches('"').to_string();
            }
        } else if lower.starts_with("subject:") {
            parsed.subject = trimmed["subject:".len()..].trim().to_string();
        } else if lower.starts_with("message-id:") {
            parsed.message_id = Some(trimmed["message-id:".len()..].trim().to_string());
        } else if lower.starts_with("in-reply-to:") {
            parsed.in_reply_to = Some(trimmed["in-reply-to:".len()..].trim().to_string());
        } else if lower.starts_with("auto-submitted:") {
            parsed.auto_submitted = Some(trimmed["auto-submitted:".len()..].trim().to_string());
        } else if lower.starts_with("precedence:") {
            parsed.precedence = Some(trimmed["precedence:".len()..].trim().to_string());
        } else if lower.starts_with("list-id:")
            || lower.starts_with("list-unsubscribe:")
            || lower.starts_with("x-autoreply:")
        {
            parsed.is_list = true;
        }
    }

    parsed
}

async fn evaluate_suppression(
    headers: &ParsedHeaders,
    sender: &str,
    state: &SharedState,
) -> (bool, &'static str) {
    let sender_lower = sender.to_lowercase();

    // 1. Never reply to self
    if sender_lower == "me@faizal.uk" || sender_lower.ends_with("@faizal.uk") {
        return (true, "Self or internal domain sender");
    }

    // 2. Drop common automated bot patterns
    for pattern in &[
        "noreply",
        "no-reply",
        "mailer-daemon",
        "postmaster",
        "bounce",
        "notifications",
        "cron",
        "reports",
        "alerts",
    ] {
        if sender_lower.contains(pattern) {
            return (true, "Automated or system notification sender");
        }
    }

    // 3. RFC 3834 Loop Prevention
    if let Some(ref auto) = headers.auto_submitted {
        if !auto.eq_ignore_ascii_case("no") {
            return (true, "Auto-Submitted header present");
        }
    }

    if let Some(ref prec) = headers.precedence {
        let prec_lower = prec.to_lowercase();
        if prec_lower.contains("bulk")
            || prec_lower.contains("list")
            || prec_lower.contains("junk")
            || prec_lower.contains("auto_reply")
        {
            return (true, "Precedence bulk/list/junk/auto_reply");
        }
    }

    if headers.is_list {
        return (true, "Mailing list or newsletter header detected");
    }

    // 4. Do not reply to threads with in-reply-to (prevents ping-pong loops)
    if headers.in_reply_to.is_some() {
        return (true, "Existing thread in-reply-to present");
    }

    // 5. Cooldown Check (7 days)
    if is_mail_responder_cooldown(&state.db, &sender_lower, 7).await {
        return (true, "Within 7-day cooldown window");
    }

    (false, "Approved")
}

fn load_html_template() -> String {
    for candidate in &[
        "/app/templates/acknowledgement.html",
        "/home/ubuntu/stack/apps/personal-site/mail/responder/templates/acknowledgement.html",
        "services/mail/responder/templates/acknowledgement.html",
        "templates/acknowledgement.html",
    ] {
        if let Ok(content) = std::fs::read_to_string(candidate) {
            if !content.trim().is_empty() {
                return content;
            }
        }
    }
    EMBEDDED_HTML_TEMPLATE.to_string()
}

fn load_txt_template() -> String {
    for candidate in &[
        "/app/templates/acknowledgement.txt",
        "/home/ubuntu/stack/apps/personal-site/mail/responder/templates/acknowledgement.txt",
        "services/mail/responder/templates/acknowledgement.txt",
        "templates/acknowledgement.txt",
    ] {
        if let Ok(content) = std::fs::read_to_string(candidate) {
            if !content.trim().is_empty() {
                return content;
            }
        }
    }
    EMBEDDED_TXT_TEMPLATE.to_string()
}
