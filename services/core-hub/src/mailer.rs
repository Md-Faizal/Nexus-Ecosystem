use crate::config::AppConfig;
use crate::db::{
    enqueue_mail_spool, get_setting, mark_mail_spool_sent, DbPool,
};
use lettre::message::header::ContentType;
use lettre::transport::smtp::authentication::Credentials;
use lettre::transport::smtp::client::{Tls, TlsParameters};
use lettre::{AsyncSmtpTransport, AsyncTransport, Message, Tokio1Executor};
use tracing::{error, info, warn};

/// Build an SMTP transport pointed at the configured host.
///
/// Priority:
///   1. User-set custom SMTP (notification_email + smtp_password in DB settings)
///   2. Config defaults → stalwart-mail or Brevo relay on port 587
///
/// TLS mode is controlled by `config.smtp_tls`:
///   "tls"      → implicit TLS (port 465)
///   "starttls" → STARTTLS upgrade (port 587)
///   "none"     → plain (only for trusted internal docker networks)
async fn build_transport_for(
    host: &str,
    port: u16,
    user: &str,
    pass: &str,
    tls_mode: &str,
) -> Result<AsyncSmtpTransport<Tokio1Executor>, String> {
    let transport = match tls_mode {
        "tls" => {
            let tls = TlsParameters::builder(host.to_string())
                .build()
                .map_err(|e| e.to_string())?;
            let builder = AsyncSmtpTransport::<Tokio1Executor>::relay(host)
                .map_err(|e| e.to_string())?
                .port(port)
                .tls(Tls::Wrapper(tls));
            if !user.is_empty() {
                builder.credentials(Credentials::new(user.to_string(), pass.to_string())).build()
            } else {
                builder.build()
            }
        }
        "none" => {
            warn!("SMTP_TLS=none: using unencrypted SMTP (internal Docker network only)");
            AsyncSmtpTransport::<Tokio1Executor>::builder_dangerous(host)
                .port(port)
                .build()
        }
        _ => {
            let tls = TlsParameters::builder(host.to_string())
                .build()
                .map_err(|e| e.to_string())?;
            let builder = AsyncSmtpTransport::<Tokio1Executor>::relay(host)
                .map_err(|e| e.to_string())?
                .port(port)
                .tls(Tls::Required(tls));
            if !user.is_empty() {
                builder.credentials(Credentials::new(user.to_string(), pass.to_string())).build()
            } else {
                builder.build()
            }
        }
    };
    Ok(transport)
}

/// Returns (transport, destination_address).
pub async fn build_transport(
    config: &AppConfig,
    db: &DbPool,
) -> Result<(AsyncSmtpTransport<Tokio1Executor>, String), String> {
    // 1. User-configured SMTP override (stored in DB settings)
    let custom_email = get_setting(db, "notification_email").await;
    let custom_pass = get_setting(db, "smtp_password").await;

    if let (Some(email), Some(pass)) = (&custom_email, &custom_pass) {
        if email.contains('@') && !pass.trim().is_empty() {
            let transport = build_transport_for(
                "smtp.gmail.com", 587, email, pass, "starttls"
            ).await?;
            info!("Using custom SMTP via smtp.gmail.com for {}", email);
            return Ok((transport, email.clone()));
        }
    }

    // 2. Default: configured host (Brevo relay or internal Stalwart)
    let transport = build_transport_for(
        &config.smtp_host,
        config.smtp_port,
        &config.smtp_user,
        &config.smtp_pass,
        &config.smtp_tls,
    ).await?;

    let default_dest = get_setting(db, "report_recipient_email")
        .await
        .unwrap_or_else(|| "me@faizal.uk".into());

    info!("Using SMTP relay ({}:{}) — dest={}", config.smtp_host, config.smtp_port, default_dest);
    Ok((transport, default_dest))
}

/// Send email via Resend HTTP REST API over Port 443 (HTTPS)
/// Completely immune to OCI & GCP outbound port 25/587 blocks.
pub async fn send_via_resend(
    api_key: &str,
    sender_name: &str,
    sender_email: &str,
    recipient: &str,
    subject: &str,
    html_body: &str,
) -> Result<String, String> {
    let client = reqwest::Client::new();
    let recipients: Vec<String> = recipient
        .split(|c| c == ',' || c == ';' || c == ' ')
        .map(|s| s.trim().to_string())
        .filter(|s| s.contains('@'))
        .collect();

    if recipients.is_empty() {
        return Err("No valid recipient addresses provided for Resend".into());
    }

    let from_formatted = if !sender_name.trim().is_empty() {
        format!("{} <{}>", sender_name.trim(), sender_email.trim())
    } else {
        sender_email.trim().to_string()
    };

    let payload = serde_json::json!({
        "from": from_formatted,
        "to": recipients,
        "subject": subject,
        "html": html_body
    });

    let res = client
        .post("https://api.resend.com/emails")
        .header("Authorization", format!("Bearer {}", api_key.trim()))
        .header("Content-Type", "application/json")
        .json(&payload)
        .send()
        .await
        .map_err(|e| format!("Resend HTTP request failed: {}", e))?;

    let status = res.status();
    let body_text = res.text().await.unwrap_or_default();

    if status.is_success() {
        info!("Email successfully delivered via Resend API: {}", body_text);
        Ok(body_text)
    } else {
        Err(format!("Resend API error (HTTP {}): {}", status, body_text))
    }
}

/// Send email via standard SMTP transport (Brevo / Stalwart / Gmail)
pub async fn send_via_smtp(
    config: &AppConfig,
    db: &DbPool,
    recipient: &str,
    subject: &str,
    html_body: &str,
) -> Result<(), String> {
    let (transport, default_dest) = build_transport(config, db).await?;
    let raw_recipients = if !recipient.trim().is_empty() {
        recipient.trim()
    } else {
        &default_dest
    };

    let from_header = format!("\"{}\" <{}>", config.sender_name, config.sender_email);
    let mut builder = Message::builder()
        .from(from_header.parse().map_err(|e: lettre::address::AddressError| e.to_string())?)
        .subject(subject)
        .header(ContentType::TEXT_HTML);

    let mut has_recipient = false;
    for token in raw_recipients.split(|c| c == ',' || c == ';' || c == ' ') {
        let t = token.trim();
        if t.contains('@') {
            if let Ok(addr) = t.parse::<lettre::Address>() {
                builder = builder.to(addr.into());
                has_recipient = true;
            }
        }
    }

    if !has_recipient {
        return Err(format!("No valid recipient addresses in '{}'", raw_recipients));
    }

    let email = builder.body(html_body.to_string()).map_err(|e| e.to_string())?;

    transport.send(email).await.map_err(|e| e.to_string())?;
    info!("Email successfully delivered via SMTP to {}", raw_recipients);
    Ok(())
}

/// Dispatch email through the Nexus Resilient Multi-Tier Engine:
///   Tier 1A: Resend Cloud HTTPS API (Port 443 - zero block risk)
///   Tier 1B: Primary SMTP Relay (Brevo / Stalwart port 587 STARTTLS)
///   Tier 2:  Doomsday Local Mesh Spool (SQLite buffer + Reverse WS Drain)
pub async fn dispatch_resilient_email(
    config: &AppConfig,
    db: &DbPool,
    recipient: &str,
    subject: &str,
    html_body: String,
    message_type: &str,
    priority: i32,
) -> Result<(), String> {
    let mail_id = uuid::Uuid::new_v4().to_string();

    // ── Tier 1A: Resend Cloud HTTPS API (Port 443) ───────────────────────────
    let resend_key = if !config.resend_api_key.trim().is_empty() {
        Some(config.resend_api_key.clone())
    } else {
        get_setting(db, "resend_api_key").await.filter(|k| !k.trim().is_empty())
    };

    let mut cloud_errors = Vec::new();

    if let Some(ref api_key) = resend_key {
        match send_via_resend(
            api_key,
            &config.sender_name,
            &config.sender_email,
            recipient,
            subject,
            &html_body,
        )
        .await
        {
            Ok(_) => {
                let _ = mark_mail_spool_sent(db, &mail_id, "resend").await;
                return Ok(());
            }
            Err(e) => {
                warn!("Tier 1A (Resend) failed: {}. Falling back to Tier 1B (Brevo/SMTP)...", e);
                cloud_errors.push(format!("Resend: {}", e));
            }
        }
    } else {
        cloud_errors.push("Resend: unconfigured API key".into());
    }

    // ── Tier 1B: Primary SMTP Relay (Brevo port 587) ─────────────────────────
    match send_via_smtp(config, db, recipient, subject, &html_body).await {
        Ok(_) => {
            let _ = mark_mail_spool_sent(db, &mail_id, "brevo_smtp").await;
            return Ok(());
        }
        Err(e) => {
            warn!("Tier 1B (SMTP Relay) failed: {}. Falling back to Tier 2 Doomsday Local Mesh Spool...", e);
            cloud_errors.push(format!("SMTP: {}", e));
            let combined_err = cloud_errors.join(" | ");

            // ── Tier 2: Doomsday Local Mesh Spool (SQLite Buffer) ────────────
            let _ = enqueue_mail_spool(
                db,
                &mail_id,
                recipient,
                subject,
                &html_body,
                None,
                message_type,
                priority,
                Some(&combined_err),
            )
            .await;

            info!("Message '{}' safely buffered into SQLite mail_spool for local node mesh drain", mail_id);
            Ok(())
        }
    }
}

pub async fn send_contact_notification(
    config: &AppConfig,
    db: &DbPool,
    name: &str,
    sender_email: &str,
    subject: &str,
    content: &str,
    ip: &str,
) {
    let destination = get_setting(db, "report_recipient_email")
        .await
        .unwrap_or_else(|| "me@faizal.uk".into());

    let email_subject = format!("New Contact Message: {}", subject);
    let html_body = format!(
        r#"<div style="font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif;background:#0d0e12;color:#f3f4f6;padding:24px;border-radius:12px;border:1px solid #262626;">
            <h2 style="color:#89ff00;margin-top:0;">📬 New Message Received</h2>
            <p><strong>From:</strong> {} (&lt;{}&gt;)</p>
            <p><strong>Subject:</strong> {}</p>
            <p><strong>IP Address:</strong> {}</p>
            <hr style="border:0;border-top:1px solid #262626;margin:16px 0;" />
            <div style="background:#16181d;padding:16px;border-radius:8px;white-space:pre-wrap;line-height:1.5;">{}</div>
        </div>"#,
        name, sender_email, subject, ip, content
    );

    if let Err(e) = dispatch_resilient_email(
        config,
        db,
        &destination,
        &email_subject,
        html_body,
        "contact",
        2,
    )
    .await
    {
        error!("Failed to dispatch contact notification: {}", e);
    }
}

pub async fn send_report_email(
    config: &AppConfig,
    db: &DbPool,
    recipient: &str,
    subject: &str,
    html_body: String,
) -> Result<(), String> {
    dispatch_resilient_email(config, db, recipient, subject, html_body, "health_report", 3).await
}
