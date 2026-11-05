use crate::config::AppConfig;
use crate::db::{get_setting, DbPool};
use lettre::message::header::ContentType;
use lettre::transport::smtp::authentication::Credentials;
use lettre::transport::smtp::client::{Tls, TlsParameters};
use lettre::{AsyncSmtpTransport, AsyncTransport, Message, Tokio1Executor};
use tracing::{error, info, warn};

/// Build an SMTP transport pointed at the configured host.
///
/// Priority:
///   1. User-set custom SMTP (notification_email + smtp_password in DB settings)
///   2. Config defaults → stalwart-mail Docker container on shared mail_default network
///
/// TLS mode is controlled by `config.smtp_tls`:
///   "tls"      → implicit TLS (port 465)
///   "starttls" → STARTTLS upgrade (port 587) ← default for Stalwart
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
            // Implicit TLS (SMTPS) — port 465
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
            // Plain / unauthenticated — safe only on a trusted private Docker network
            warn!("SMTP_TLS=none: using unencrypted SMTP (internal Docker network only)");
            AsyncSmtpTransport::<Tokio1Executor>::builder_dangerous(host)
                .port(port)
                .build()
        }
        _ => {
            // "starttls" (default) — STARTTLS upgrade on port 587
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
///
/// Checks DB for a user-overridden SMTP first; falls back to the Docker-internal
/// Stalwart relay defined in AppConfig.
pub async fn build_transport(
    config: &AppConfig,
    db: &DbPool,
) -> Result<(AsyncSmtpTransport<Tokio1Executor>, String), String> {
    // ── 1. User-configured SMTP override (stored in DB settings) ─────────────
    let custom_email = get_setting(db, "notification_email").await;
    let custom_pass = get_setting(db, "smtp_password").await;

    if let (Some(email), Some(pass)) = (&custom_email, &custom_pass) {
        if email.contains('@') && !pass.trim().is_empty() {
            // Use Gmail SMTP for user-provided credentials
            let transport = build_transport_for(
                "smtp.gmail.com", 587, email, pass, "starttls"
            ).await?;
            info!("Using custom SMTP via smtp.gmail.com for {}", email);
            return Ok((transport, email.clone()));
        }
    }

    // ── 2. Default: internal Stalwart container via mail_default Docker network ─
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

    info!("Using Stalwart SMTP ({}:{}) — dest={}", config.smtp_host, config.smtp_port, default_dest);
    Ok((transport, default_dest))
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
    let (transport, destination) = match build_transport(config, db).await {
        Ok(t) => t,
        Err(e) => {
            error!("Failed to build mail transport: {}", e);
            return;
        }
    };

    let from_header = format!("\"{}\" <{}>", config.sender_name, config.sender_email);
    let email_subject = format!("New Message: {}", subject);
    let body = format!(
        "From: {} ({})\n\nSubject: {}\n\nMessage:\n{}\n\nIP: {}",
        name, sender_email, subject, content, ip
    );

    let mut builder = match Message::builder()
        .from(match from_header.parse() {
            Ok(f) => f,
            Err(_) => return,
        })
        .reply_to(match format!("\"{}\" <{}>", name, sender_email).parse() {
            Ok(r) => r,
            Err(_) => return,
        })
        .subject(email_subject)
        .header(ContentType::TEXT_PLAIN) {
            builder => builder,
        };

    let mut has_recipient = false;
    for token in destination.split(|c| c == ',' || c == ';' || c == ' ') {
        let t = token.trim();
        if t.contains('@') {
            if let Ok(addr) = t.parse::<lettre::Address>() {
                builder = builder.to(addr.into());
                has_recipient = true;
            }
        }
    }

    if !has_recipient {
        error!("No valid destination address found in: {}", destination);
        return;
    }

    let email = match builder.body(body) {
        Ok(m) => m,
        Err(e) => {
            error!("Failed to build email message: {}", e);
            return;
        }
    };

    match transport.send(email).await {
        Ok(_) => info!("Contact notification sent to {}", destination),
        Err(e) => error!("Failed to send contact notification: {}", e),
    }
}

pub async fn send_report_email(
    config: &AppConfig,
    db: &DbPool,
    recipient: &str,
    subject: &str,
    html_body: String,
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

    let email = builder.body(html_body).map_err(|e| e.to_string())?;

    transport.send(email).await.map_err(|e| e.to_string())?;
    info!("Health report successfully sent to {}", raw_recipients);
    Ok(())
}
