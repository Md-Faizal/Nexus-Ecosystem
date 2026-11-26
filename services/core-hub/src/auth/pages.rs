use axum::{
    extract::Query,
    response::{Html, IntoResponse},
};
use serde::Deserialize;

#[derive(Debug, Deserialize)]
pub struct TokenQuery {
    pub token: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct DeviceQuery {
    pub code: Option<String>,
}

fn escape_html(s: &str) -> String {
    s.replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
        .replace('\'', "&#39;")
}

const SHARED_STYLES: &str = r##"
    :root { color-scheme: dark; }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
        min-height: 100vh;
        background: #000000;
        color: #f8fafc;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Inter, sans-serif;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 20px;
    }
    .card {
        width: 100%;
        max-width: 420px;
        background: #0a0a0c;
        border: 1px solid rgba(255,255,255,0.12);
        border-radius: 24px;
        padding: 32px 26px;
        box-shadow: 0 24px 60px rgba(0,0,0,0.8);
    }
    .brand {
        display: inline-flex;
        align-items: center;
        gap: 10px;
        font-weight: 800;
        letter-spacing: 0.14em;
        font-size: 12px;
        text-transform: uppercase;
        color: #55ff00;
        margin-bottom: 18px;
    }
    .dot { width: 8px; height: 8px; border-radius: 999px; background: #55ff00; box-shadow: 0 0 10px #55ff00; }
    h1 { font-size: 22px; font-weight: 700; margin-bottom: 8px; letter-spacing: -0.02em; }
    p { font-size: 14px; line-height: 1.55; color: #94a3b8; margin-bottom: 20px; }
    label { display: block; font-size: 12px; font-weight: 600; color: #cbd5e1; margin-bottom: 6px; }
    input {
        width: 100%;
        background: #000000;
        border: 1px solid rgba(255,255,255,0.16);
        border-radius: 14px;
        padding: 13px 14px;
        color: #ffffff;
        font-size: 15px;
        outline: none;
        margin-bottom: 16px;
    }
    input:focus { border-color: #55ff00; }
    .btn {
        width: 100%;
        border: none;
        border-radius: 14px;
        padding: 14px 18px;
        font-size: 15px;
        font-weight: 700;
        cursor: pointer;
        text-decoration: none;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        transition: transform 0.12s ease, opacity 0.15s ease;
    }
    .btn:active { transform: scale(0.98); }
    .btn-primary { background: #55ff00; color: #000000; }
    .btn-secondary {
        background: rgba(255,255,255,0.06);
        color: #f8fafc;
        border: 1px solid rgba(255,255,255,0.14);
        margin-top: 10px;
    }
    .status {
        font-size: 13px;
        padding: 12px 14px;
        border-radius: 12px;
        margin-bottom: 16px;
        display: none;
    }
    .status.ok { display: block; background: rgba(85,255,0,0.1); border: 1px solid rgba(85,255,0,0.35); color: #86efac; }
    .status.err { display: block; background: rgba(239,68,68,0.12); border: 1px solid rgba(239,68,68,0.35); color: #fca5a5; }
"##;

pub async fn magic_link_page(Query(q): Query<TokenQuery>) -> impl IntoResponse {
    let token = escape_html(q.token.as_deref().unwrap_or(""));
    let html = r##"<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1" />
  <title>Nexus Auth · Magic Link</title>
  <style>__STYLES__</style>
</head>
<body>
  <div class="card">
    <div class="brand"><span class="dot"></span>NEXUS AUTH</div>
    <h1 id="title">Verifying Magic Link…</h1>
    <p id="subtitle">Confirming your single-use Nexus authentication token.</p>
    <div id="status" class="status"></div>
    <a id="mobileBtn" class="btn btn-primary" style="display:none" href="#">Open in Nexus Mobile</a>
    <a id="adminBtn" class="btn btn-secondary" style="display:none" href="/admin">Continue to Nexus Console</a>
  </div>
  <script>
    (async function() {
      const token = "__TOKEN__";
      const title = document.getElementById("title");
      const sub = document.getElementById("subtitle");
      const status = document.getElementById("status");
      const mobileBtn = document.getElementById("mobileBtn");
      const adminBtn = document.getElementById("adminBtn");
      if (!token) {
        title.textContent = "Invalid Magic Link";
        sub.textContent = "No verification token was found in this URL.";
        status.className = "status err";
        status.textContent = "Missing token parameter.";
        return;
      }
      try {
        const res = await fetch("/api/auth/verify-magic-link?token=" + encodeURIComponent(token));
        const data = await res.json();
        if (!res.ok || !data.success || !data.token) {
          throw new Error(data.error || "Magic link expired or already used.");
        }
        localStorage.setItem("nexus_auth_token", data.token);
        const email = (data.user && data.user.email) ? data.user.email : "";
        title.textContent = "Signed In to Nexus";
        sub.textContent = email ? ("Authenticated as " + email + ".") : "Authentication verified.";
        status.className = "status ok";
        status.textContent = "Session issued. Choose where to continue below.";
        mobileBtn.href = "nexus-mobile://auth-callback?token=" + encodeURIComponent(data.token) + "&email=" + encodeURIComponent(email);
        mobileBtn.style.display = "inline-flex";
        adminBtn.style.display = "inline-flex";
      } catch (err) {
        title.textContent = "Verification Failed";
        sub.textContent = "This magic link could not be verified.";
        status.className = "status err";
        status.textContent = err.message || "Verification error";
        adminBtn.style.display = "inline-flex";
      }
    })();
  </script>
</body>
</html>"##
        .replace("__STYLES__", SHARED_STYLES)
        .replace("__TOKEN__", &token);
    Html(html)
}

pub async fn password_reset_page(Query(q): Query<TokenQuery>) -> impl IntoResponse {
    let token = escape_html(q.token.as_deref().unwrap_or(""));
    let html = r##"<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1" />
  <title>Nexus Auth · Reset Password</title>
  <style>__STYLES__</style>
</head>
<body>
  <div class="card">
    <div class="brand"><span class="dot"></span>NEXUS AUTH</div>
    <h1>Reset Password</h1>
    <p>Choose a new password (minimum 8 characters) for your central Nexus account.</p>
    <div id="status" class="status"></div>
    <form id="resetForm">
      <label for="pw">New Password</label>
      <input id="pw" type="password" minlength="8" required placeholder="At least 8 characters" />
      <label for="pw2">Confirm Password</label>
      <input id="pw2" type="password" minlength="8" required placeholder="Re-enter password" />
      <button type="submit" class="btn btn-primary" id="submitBtn">Update Password</button>
    </form>
    <a class="btn btn-secondary" href="/admin">Back to Nexus Console</a>
  </div>
  <script>
    const token = "__TOKEN__";
    const form = document.getElementById("resetForm");
    const status = document.getElementById("status");
    if (!token) {
      status.className = "status err";
      status.textContent = "Missing reset token in URL.";
      form.style.display = "none";
    }
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const pw = document.getElementById("pw").value;
      const pw2 = document.getElementById("pw2").value;
      if (pw !== pw2) {
        status.className = "status err";
        status.textContent = "Passwords do not match.";
        return;
      }
      const btn = document.getElementById("submitBtn");
      btn.disabled = true;
      btn.textContent = "Updating…";
      try {
        const res = await fetch("/api/auth/password-reset/confirm", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ token, new_password: pw })
        });
        const data = await res.json();
        if (!res.ok || !data.success) throw new Error(data.error || "Failed to reset password");
        status.className = "status ok";
        status.textContent = "Password updated! You can now sign in on Web, macOS, and Mobile.";
        form.style.display = "none";
      } catch (err) {
        status.className = "status err";
        status.textContent = err.message;
        btn.disabled = false;
        btn.textContent = "Update Password";
      }
    });
  </script>
</body>
</html>"##
        .replace("__STYLES__", SHARED_STYLES)
        .replace("__TOKEN__", &token);
    Html(html)
}

pub async fn device_approval_page(Query(q): Query<DeviceQuery>) -> impl IntoResponse {
    let code = escape_html(q.code.as_deref().unwrap_or(""));
    let html = r##"<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1" />
  <title>Nexus Auth · Device Approval</title>
  <style>__STYLES__</style>
</head>
<body>
  <div class="card">
    <div class="brand"><span class="dot"></span>NEXUS DEVICE AUTH</div>
    <h1>Approve Device Code</h1>
    <p>Enter the 8-character onboarding code shown on your mobile device, macOS client, or CLI terminal.</p>
    <div id="status" class="status"></div>
    <form id="deviceForm">
      <label for="userCode">Device Code</label>
      <input id="userCode" type="text" value="__CODE__" required placeholder="XXXX-XXXX" style="text-transform:uppercase;letter-spacing:0.12em;text-align:center;font-weight:700" />
      <button type="submit" class="btn btn-primary" id="approveBtn">Approve Device</button>
    </form>
    <a id="loginBtn" class="btn btn-secondary" href="/admin">Sign In on Nexus Console First</a>
  </div>
  <script>
    const form = document.getElementById("deviceForm");
    const status = document.getElementById("status");
    const token = localStorage.getItem("nexus_auth_token");
    if (!token) {
      status.className = "status err";
      status.textContent = "You are not signed in on this browser yet. Sign in at /admin first, then return here to approve the device.";
    }
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const authToken = localStorage.getItem("nexus_auth_token");
      if (!authToken) {
        window.location.href = "/admin";
        return;
      }
      const userCode = document.getElementById("userCode").value.trim().toUpperCase();
      const btn = document.getElementById("approveBtn");
      btn.disabled = true;
      btn.textContent = "Approving…";
      try {
        const res = await fetch("/api/auth/device/approve", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": "Bearer " + authToken
          },
          body: JSON.stringify({ user_code: userCode })
        });
        const data = await res.json();
        if (!res.ok || !data.success) throw new Error(data.error || "Invalid or expired device code");
        status.className = "status ok";
        status.textContent = "Device approved! Your device will finish signing in automatically.";
        btn.textContent = "Approved";
      } catch (err) {
        status.className = "status err";
        status.textContent = err.message;
        btn.disabled = false;
        btn.textContent = "Approve Device";
      }
    });
  </script>
</body>
</html>"##
        .replace("__STYLES__", SHARED_STYLES)
        .replace("__CODE__", &code);
    Html(html)
}
