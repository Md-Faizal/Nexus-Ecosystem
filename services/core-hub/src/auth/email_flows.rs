use crate::config::AppConfig;
use crate::db::DbPool;
use crate::mailer;

pub fn render_otp_email(otp_code: &str) -> String {
    format!(
        r#"<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>Nexus Fleet Verification Code</title>
  <style>
    body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #08090a; color: #f3f4f6; margin: 0; padding: 40px 20px; }}
    .card {{ max-width: 480px; margin: 0 auto; background: #121316; border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 16px; padding: 36px 32px; box-shadow: 0 20px 40px rgba(0,0,0,0.5); }}
    .logo {{ font-size: 20px; font-weight: 700; letter-spacing: 2px; color: #89ff00; text-transform: uppercase; margin-bottom: 24px; }}
    h1 {{ font-size: 22px; margin: 0 0 12px; font-weight: 600; color: #ffffff; }}
    p {{ color: #9ca3af; font-size: 14px; line-height: 1.6; margin: 0 0 24px; }}
    .code-box {{ background: #1a1c22; border: 1px solid #89ff00; border-radius: 12px; padding: 18px 24px; text-align: center; margin: 24px 0; }}
    .otp {{ font-family: 'SF Mono', Consolas, 'Liberation Mono', Menlo, Courier, monospace; font-size: 36px; font-weight: 700; letter-spacing: 8px; color: #89ff00; margin: 0; }}
    .footer {{ font-size: 12px; color: #6b7280; text-align: center; margin-top: 32px; border-top: 1px solid rgba(255,255,255,0.06); padding-top: 20px; }}
  </style>
</head>
<body>
  <div class="card">
    <div class="logo">🌌 Nexus Identity</div>
    <h1>Your Verification Code</h1>
    <p>Use the 6-digit code below to securely authenticate your session across the Nexus Fleet ecosystem. This code will expire in <strong>10 minutes</strong>.</p>
    <div class="code-box">
      <div class="otp">{}</div>
    </div>
    <p>If you did not initiate this request, no action is required. Your account remains completely secure.</p>
    <div class="footer">
      Nexus Fleet System · Autonomous Cockroach Resilience · faizal.uk
    </div>
  </div>
</body>
</html>"#,
        otp_code
    )
}

pub fn render_magic_link_email(verify_url: &str) -> String {
    format!(
        r#"<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>Nexus Fleet Magic Link</title>
  <style>
    body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #08090a; color: #f3f4f6; margin: 0; padding: 40px 20px; }}
    .card {{ max-width: 480px; margin: 0 auto; background: #121316; border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 16px; padding: 36px 32px; box-shadow: 0 20px 40px rgba(0,0,0,0.5); }}
    .logo {{ font-size: 20px; font-weight: 700; letter-spacing: 2px; color: #00e5ff; text-transform: uppercase; margin-bottom: 24px; }}
    h1 {{ font-size: 22px; margin: 0 0 12px; font-weight: 600; color: #ffffff; }}
    p {{ color: #9ca3af; font-size: 14px; line-height: 1.6; margin: 0 0 24px; }}
    .btn {{ display: block; width: 100%; box-sizing: border-box; text-align: center; background: #00e5ff; color: #08090a; text-decoration: none; font-weight: 700; font-size: 15px; padding: 14px 20px; border-radius: 10px; margin: 28px 0; }}
    .link-alt {{ font-size: 12px; color: #6b7280; word-break: break-all; margin-top: 16px; }}
    .footer {{ font-size: 12px; color: #6b7280; text-align: center; margin-top: 32px; border-top: 1px solid rgba(255,255,255,0.06); padding-top: 20px; }}
  </style>
</head>
<body>
  <div class="card">
    <div class="logo">🌌 Nexus Identity</div>
    <h1>Sign In with Magic Link</h1>
    <p>Click the button below to instantly authenticate into your Nexus account without a password. This link expires in <strong>15 minutes</strong>.</p>
    <a href="{}" class="btn" target="_blank">Authenticate Session →</a>
    <div class="link-alt">
      Or copy & paste this link directly:<br/>
      <a href="{}" style="color: #00e5ff;">{}</a>
    </div>
    <div class="footer">
      Nexus Fleet System · Autonomous Cockroach Resilience · faizal.uk
    </div>
  </div>
</body>
</html>"#,
        verify_url, verify_url, verify_url
    )
}

pub fn render_password_reset_email(reset_url: &str) -> String {
    format!(
        r#"<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>Reset Nexus Password</title>
  <style>
    body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #08090a; color: #f3f4f6; margin: 0; padding: 40px 20px; }}
    .card {{ max-width: 480px; margin: 0 auto; background: #121316; border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 16px; padding: 36px 32px; box-shadow: 0 20px 40px rgba(0,0,0,0.5); }}
    .logo {{ font-size: 20px; font-weight: 700; letter-spacing: 2px; color: #ff0055; text-transform: uppercase; margin-bottom: 24px; }}
    h1 {{ font-size: 22px; margin: 0 0 12px; font-weight: 600; color: #ffffff; }}
    p {{ color: #9ca3af; font-size: 14px; line-height: 1.6; margin: 0 0 24px; }}
    .btn {{ display: block; width: 100%; box-sizing: border-box; text-align: center; background: #ff0055; color: #ffffff; text-decoration: none; font-weight: 700; font-size: 15px; padding: 14px 20px; border-radius: 10px; margin: 28px 0; }}
    .link-alt {{ font-size: 12px; color: #6b7280; word-break: break-all; margin-top: 16px; }}
    .footer {{ font-size: 12px; color: #6b7280; text-align: center; margin-top: 32px; border-top: 1px solid rgba(255,255,255,0.06); padding-top: 20px; }}
  </style>
</head>
<body>
  <div class="card">
    <div class="logo">🌌 Nexus Identity</div>
    <h1>Reset Account Password</h1>
    <p>A request was received to reset your Nexus Fleet account password. Click the button below to choose a new password. This link is valid for <strong>1 hour</strong>.</p>
    <a href="{}" class="btn" target="_blank">Reset Password →</a>
    <div class="link-alt">
      Or copy & paste this link directly:<br/>
      <a href="{}" style="color: #ff0055;">{}</a>
    </div>
    <div class="footer">
      Nexus Fleet System · Autonomous Cockroach Resilience · faizal.uk
    </div>
  </div>
</body>
</html>"#,
        reset_url, reset_url, reset_url
    )
}

pub async fn send_otp_email(
    config: &AppConfig,
    db: &DbPool,
    recipient: &str,
    code: &str,
) -> Result<(), String> {
    let subject = format!("Nexus Security Code: {}", code);
    let html = render_otp_email(code);
    mailer::send_report_email(config, db, recipient, &subject, html).await
}

pub async fn send_magic_link_email(
    config: &AppConfig,
    db: &DbPool,
    recipient: &str,
    verify_url: &str,
) -> Result<(), String> {
    let subject = "Sign In to Nexus Fleet (Magic Link)".to_string();
    let html = render_magic_link_email(verify_url);
    mailer::send_report_email(config, db, recipient, &subject, html).await
}

pub async fn send_reset_password_email(
    config: &AppConfig,
    db: &DbPool,
    recipient: &str,
    reset_url: &str,
) -> Result<(), String> {
    let subject = "Reset Your Nexus Password".to_string();
    let html = render_password_reset_email(reset_url);
    mailer::send_report_email(config, db, recipient, &subject, html).await
}

