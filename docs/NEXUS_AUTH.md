# Nexus Central Auth — Architecture & Flow Reference

Nexus Central Auth (`services/core-hub/src/auth/`) is the single source of truth for identity, sessions, synced security PINs, and device onboarding across **Web Admin (`apps/web`)**, **Android Mobile (`apps/mobile/android`)**, and **CLI / macOS / TV / Car AI Box** clients.

---

## 1. Core Design Principles
- **Password-First Sign In:** Web Admin (`NexusAuthModal.jsx`) and Mobile (`AuthScreen.kt`) open directly on **Username / Email + Password**.
- **Netflix / Amazon-Style Secondary Email OTP:** Below `"Sign In"`, users can tap **"Use Email OTP instead"** (carrying over any typed email). On Sign In (`allow_create: false`), unregistered emails return `404` with `account_exists: false` so the UI prompts **"Create an account"** instead of silently creating empty accounts.
- **Spotify-Style Password Reset + Instant Login:** `POST /api/auth/password-reset/request` generates **both** a 1-hour password reset token and a 15-minute single-use Magic Link token, delivering one email with:
  1. **"Log in immediately without a password →"** (`/admin/auth/verify?token=...`, which also deep-links to `nexus-mobile://auth-callback?token=...`)
  2. **"Reset Password →"** (`/admin/auth/reset?token=...`)
- **Social OAuth:** **Google** (`POST /api/auth/google`) and **Meta** (`POST /api/auth/meta`).
- **Sender Identity:** Auth emails (OTP, Magic Link, Password Reset) are sent via `mailer::send_auth_email` as `"Nexus Auth <reports@faizal.uk>"` (distinct from `"System VM Health Reports"`).

---

## 2. Hosted Auth Web Pages & Nginx Routing
All three user-facing auth URLs are served directly by `services/core-hub/src/auth/pages.rs` and mounted in `services/core-hub/src/lib.rs` before the portfolio SPA fallback:
- `GET /admin/auth/verify?token=...` — Verifies Magic Link, stores `nexus_auth_token` in `localStorage`, and offers **"Open in Nexus Mobile"** (`nexus-mobile://auth-callback`) + **"Continue to Nexus Console"**.
- `GET /admin/auth/reset?token=...` — Dark-mode form that submits `POST /api/auth/password-reset/confirm`.
- `GET /auth/device?code=...` — Device Code approval page (`POST /api/auth/device/approve`) for CLI, macOS, Android TV, and Car AI Box onboarding.

> **Nginx Requirement (`infra/scripts/faizal_uk.nginx`):** `/admin/auth/`, `/auth/`, and `/oauth/` MUST be explicitly `proxy_pass`ed to `personal_site_cluster` so Nginx does not catch them with `try_files $uri $uri/ /index.html;`.

---

## 3. Synced 4-Digit PIN & Mobile Security Lifecycle
The `users` SQLite table stores `pin_hash TEXT` (Argon2id), exposed as `has_pin: bool` in `PublicUserProfile`.

1. **Mandatory Post-Login PIN Gate (`MainActivity.kt`):**
   - If `!hasPin` locally and `hasCentralPin == true` (new device): user verifies their synced PIN once against `POST /api/auth/pin/verify`, which caches the salted hash locally in `EncryptedSharedPreferences`.
   - If `hasCentralPin == false`: user is forced through 2-step PIN creation, which saves locally and syncs to `POST /api/auth/pin/setup`.
2. **Biometric Opt-In (`BiometricOptInScreen.kt`):**
   - Shown immediately after PIN creation/sync when `!isBiometricEnabled` and `biometricPromptMode == "ask_next_time"`.
   - Choices: **"Enable Fingerprint Unlock"**, **"Not Now · Ask Me Next Time"** (`"ask_next_time"`), or **"Don't ask again on this device"** (`"dont_ask_again"`).
3. **Hardened PIN Change / Removal (`ReAuthScreen.kt`):**
   - Changing or removing the PIN requires verifying **6-Digit Email OTP**, **Account Password**, or **Security Question** against `POST /api/auth/pin/update`.

---

## 4. Key Endpoints (`services/core-hub/src/auth/routes.rs`)
| Endpoint | Purpose |
|---|---|
| `POST /api/auth/register` | Create Nexus account (email, full_name, optional password/username) |
| `POST /api/auth/login` | Email/username + password sign in |
| `GET /api/auth/me` | Fetch `PublicUserProfile` (includes `has_pin`, `has_password`, `has_security_question`) |
| `POST /api/auth/otp/send` & `/verify` | 6-digit Email OTP (`allow_create` supported) |
| `POST /api/auth/magic-link` & `GET /verify-magic-link` | Single-use Magic Link tokens |
| `POST /api/auth/password-reset/request` & `/confirm` | Dual Magic Link + Password Reset email & confirmation |
| `POST /api/auth/pin/setup`, `/verify`, `/update` | Central synced 4-digit PIN management (update/remove requires re-auth) |
| `POST /api/auth/google`, `/meta` | Verified OAuth token sign in |
| `POST /api/auth/device/code`, `/token`, `/approve` | RFC 8628 Device Flow for macOS, CLI, TV, Car AI Box |
| `GET /api/auth/sessions`, `DELETE /api/auth/sessions/:id` | Active device/session management |
