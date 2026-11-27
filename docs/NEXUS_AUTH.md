# Nexus Central Auth — Complete Architecture & API Reference

**Nexus Central Auth** (`services/core-hub/src/auth/`) is the unified identity provider (IdP), token authority, and security layer for the entire Nexus Ecosystem (**Web Admin**, **Portfolio**, **Android Mobile**, **macOS**, **CLI**, and future **Android TV / Car AI Box** clients).

---

## 1. Authentication Methods & Supported Credentials

### Primary & Passwordless Flows
1. **Email / Username + Password** (`POST /api/auth/login`, `POST /api/auth/register`)
   - Default sign-in method on Web Admin and Android Mobile.
   - Passwords are hashed with **Argon2id**.
2. **6-Digit Email OTP** (`POST /api/auth/otp/send`, `POST /api/auth/otp/verify`)
   - Netflix/Amazon-style secondary option (`"Use Email OTP instead"`).
   - Codes are SHA-256 hashed in `auth_codes`, expire in 10 minutes, and enforce a 5-attempt limit.
   - Supports `allow_create: false` on Sign In: returns `404` (`account_exists: false`) for unregistered emails so clients can prompt **"Create an account"**.
3. **Magic Link** (`POST /api/auth/magic-link`, `GET /api/auth/verify-magic-link`)
   - Single-use 32-byte random token (15-minute TTL) verified via `GET /admin/auth/verify?token=...`.
   - Deep-links into Android Mobile via `nexus-mobile://auth-callback?token=...`.
4. **Password Reset + Instant Login (Spotify-Style)** (`POST /api/auth/password-reset/request`, `POST /api/auth/password-reset/confirm`)
   - Generates **both** a 1-hour `password_reset` token (`/admin/auth/reset?token=...`) and a 15-minute `magic_link` token (`/admin/auth/verify?token=...`).
   - Sends one email with **"Log in immediately without a password →"** and **"Reset Password →"**.
   - Always returns `200 OK` (anti-enumeration).
5. **Security Questions Recovery** (`GET /api/auth/security-questions`, `POST /api/auth/security-questions/setup`, `POST /api/auth/security-questions/recover`)
   - Standard question keys with Argon2-hashed answers for secondary account recovery and PIN re-authentication.
6. **Social OAuth**
   - **Google** (`POST /api/auth/google` — verifies Google ID token via `oauth2.googleapis.com/tokeninfo`)
   - **Meta / Facebook** (`POST /api/auth/meta` — verifies Meta Graph API access token)
   - **X (Twitter)** (`POST /api/auth/x` — backend endpoint verifying X API v2 OAuth2 bearer token)
   - Automatically links external provider IDs in `user_oauth_accounts`.
7. **RFC 8628 Device Flow** (`POST /api/auth/device/code`, `POST /api/auth/device/token`, `POST /api/auth/device/approve`)
   - Issues an 8-character `XXXX-XXXX` user code (10-minute TTL) for **macOS**, **CLI (`nexus auth login`)**, **Android TV**, and **Car AI Box** clients.
   - Approved via `GET /auth/device?code=...` or Web Admin Console -> **Security -> Devices**.
8. **OpenID Connect (OIDC) & OAuth2 Provider** (`oidc.rs`)
   - `GET /api/auth/openid-configuration`, `GET /api/auth/keys` (JWKS)
   - `GET/POST /api/auth/authorize`, `POST /api/auth/token`, `GET/POST /api/auth/userinfo` (also mounted at `/oauth/*`)
   - Lets registered ecosystem apps (`auth_registered_apps`) delegate sign-in and scopes (`user_app_grants`).
9. **Personal Access Tokens (PATs)** (`POST /api/auth/tokens`, `GET /api/auth/tokens`, `DELETE /api/auth/tokens/:id`)
   - Long-lived `nex_live_...` API tokens (SHA-256 hashed in `api_tokens`) accepted by `AuthenticatedUser` middleware alongside JWTs.

---

## 2. Central Synced 4-Digit PIN & Mobile Security Lifecycle
- **Database:** `users.pin_hash TEXT` (Argon2id); exposed in `PublicUserProfile` as `has_pin: bool`.
- **Mandatory PIN Gate:**
  - Unauthenticated users cannot bypass `AuthScreen`.
  - Authenticated users without a local PIN either **verify** their existing central PIN (`POST /api/auth/pin/verify`, caching a salted local copy) or **create** a new 2-step PIN (`POST /api/auth/pin/setup`).
- **Biometric Opt-In (`BiometricOptInScreen.kt`):**
  - Prompts after PIN creation/sync when `!isBiometricEnabled` and `biometricPromptMode == "ask_next_time"`.
  - Options: **Enable Fingerprint Unlock**, **Not Now · Ask Me Next Time** (re-prompts on next PIN unlock), or **Don't ask again on this device**.
  - Manually disabling biometrics in Settings resets `biometricPromptMode` to `"ask_next_time"` (unless `"dont_ask_again"` was chosen), so the opt-in re-appears on the next PIN unlock.
- **Hardened PIN Change / Removal (`ReAuthScreen.kt` -> `POST /api/auth/pin/update`):**
  - Changing or removing the PIN requires verifying **6-Digit Email OTP**, **Account Password**, or **Security Question** against `services/core-hub`.

---

## 3. Mailer & Hosted Pages
- **Mailer Identity (`mailer.rs` / `email_flows.rs`):** Auth emails send via `send_auth_email` with display name `"Nexus Auth <reports@faizal.uk>"` (separate from `"System VM Health Reports"`).
- **Hosted Dark-Mode Web Pages (`pages.rs`):**
  - `/admin/auth/verify` (Magic Link + `nexus-mobile://auth-callback`)
  - `/admin/auth/reset` (Link-based password reset form)
  - `/auth/device` (Device code approval page)
- **Nginx Proxy (`infra/scripts/faizal_uk.nginx`):** `/admin/auth/`, `/auth/`, and `/oauth/` are explicitly `proxy_pass`ed to `personal_site_cluster` so they are never intercepted by the Portfolio SPA fallback.

---

## 4. Complete Endpoint & Table Inventory

### Endpoints (`services/core-hub/src/auth/routes.rs`)
- **Identity & OIDC:** `/register`, `/login`, `/me`, `/logout`, `/keys`, `/openid-configuration`, `/authorize`, `/token`, `/userinfo`
- **Passwordless & Recovery:** `/magic-link`, `/verify-magic-link`, `/otp/send`, `/otp/verify`, `/password-reset/request`, `/password-reset/confirm`, `/security-questions`, `/security-questions/setup`, `/security-questions/recover`
- **Central PIN:** `/pin/setup`, `/pin/verify`, `/pin/update`
- **Social OAuth:** `/google`, `/meta`, `/x`
- **Device Flow:** `/device/code`, `/device/token`, `/device/approve`
- **Tokens, Apps, Sessions & Grants:** `/tokens`, `/tokens/:id`, `/apps`, `/apps/:id`, `/sessions`, `/sessions/:id`, `/grants`, `/grants/:app_id`
- **Internal Mesh / JIT Migration:** `/internal/migrate-user`, `/internal/sync-device`, `/internal/sessions`, `/internal/sessions/:id`, `/internal/update-security`, `/internal/security-question`, `/internal/verify-security-answer`, `/internal/device/approve`

### SQLite Tables (`services/core-hub/src/db.rs`)
`users`, `user_oauth_accounts`, `auth_codes`, `device_codes`, `user_devices`, `api_tokens`, `auth_registered_apps`, `user_app_grants`

---

## 5. Planned Roadmap: End-to-End Account Creation & Full Nexus Auth Control

> **Status:** Documented for future implementation.

### 1. Admin Console — Full Nexus Auth Control Page (`apps/web`)
- **Account Directory & Restrictions:** View all Nexus users, roles (`owner`, `admin`, `user`), account status (`active`, `suspended`, `restricted`), force password/PIN resets, and revoke active sessions per user.
- **App Access & Grants Matrix:** Inspect which registered apps (`auth_registered_apps`) each account has used or is permitted to access (`user_app_grants`), toggle allowed scopes, and enforce per-app restrictions.
- **Auth Activity & Security Audit Log:** Track sign-in events (timestamp, IP address, device name, auth method such as Password / Email OTP / Magic Link / Google / Meta / Device Code), failed login attempts, PIN/password changes, and suspicious-activity blocks.

### 2. Designed Welcome Email (`services/core-hub/src/auth/email_flows.rs`)
- Automatically dispatched via `mailer::send_auth_email` (`"Nexus Auth <reports@faizal.uk>"`) whenever a new account is created via `/api/auth/register` or first-time OAuth sign-up.
- **Bespoke Brand Design:** Pitch-Black OLED (`#000000` / `#121316`) + FZ Lime (`#55FF00`) HTML template with a welcome hero, account summary (email/username), quick links to Web Console & Mobile app, and a security checklist (4-digit PIN, security question, passwordless OTP).

### 3. Web & Mobile "Create Account" UI/UX Overhaul
- Upgrade both [NexusAuthModal.jsx](file:///e:/Workspaces/core/nexus-fleet/apps/web/src/shared/NexusAuthModal.jsx) and [AuthScreen.kt](file:///e:/Workspaces/core/nexus-fleet/apps/mobile/android/app/src/main/java/com/nexus/mobile/ui/auth/AuthScreen.kt) from a single-screen form into a guided onboarding flow:
  1. **Identity:** Full Name, Email, and live Username availability check.
  2. **Credentials & Recovery:** Password strength indicator (or explicit passwordless choice) + interactive Security Question selector populated from `GET /api/auth/security-questions`.
  3. **Email Verification:** Inline 6-digit Email OTP confirmation before issuing the full session (followed by mandatory 4-digit PIN + Fingerprint opt-in on Mobile).

