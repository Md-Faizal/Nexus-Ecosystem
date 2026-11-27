# Nexus Mobile (Android) — Design Language & UI/UX Specification

> **Purpose:** Point any new conversation to this file (`apps/mobile/DESIGN_LANGUAGE.md`) to preserve the exact visual language, motion physics, component rules, and auth/security UX established for Nexus Mobile.

---

## 1. Core Theme & Color System (`NexusTheme.kt`)
- **Theme Modes:** `System`, `Pitch Black` (`#000000`), and `Light` (`#FFFFFF`).
- **Strict OLED & Crisp Light Rule:**
  - Dark mode background and surface MUST be **pitch black (`#000000`)** — never dark gray `surfaceContainer` wash, never Material 3 pastel/pinkish dynamic tints by default (`useDynamicColor = false` by default).
  - Light mode background and surface MUST be **pure white (`#FFFFFF`)**.
- **Palette (`NexusPalette` / `LocalNexusPalette`):**
  - `cardSurface`: `#121316` (dark) / `#F8FAFC` (light)
  - `cardElevated`: `#1A1C20` (dark) / `#FFFFFF` (light)
  - `glassSurface`: `#141519` (dark)
  - `hairlineBorder`: `Color.White.copy(alpha = 0.08f)` (dark)
  - `onlineDot`: `#22C55E` (brand FZ lime accent: `#55FF00`)
  - `warningDot`: `#F59E0B`
  - `dangerAccent`: `#EF4444`
  - `MaterialTheme.colorScheme`:
    - `primary`: `#F8FAFC` (dark) / `#0F172A` (light)
    - `onPrimary`: `#000000` (dark) / `#FFFFFF` (light)
    - `secondary`: `#38BDF8` (dark) / `#0284C7` (light)
    - `onSurfaceVariant`: `#8E95A2` (dark)

---

## 2. Apple Music-Inspired Frosted Glass & Specular Chrome
Reference aesthetic: **Apple Music iOS/Android** floating controls and pill navigation bar.

- **Subtlety First:** Highlights, shimmer, and glass borders MUST be **very subtle** — never overdone or neon-heavy.
- **Single Ring Only:** Use `nexusSpecularBorder(palette.isDark, 0.75f)` (`BorderStroke`) on `Surface`.
  - **NEVER** stack two concentric rings/borders on buttons or cards.
- **No Square Box Shadows Behind Circles:**
  - Circular buttons (`NexusCircularButton`, `NexusCircularBackButton`) MUST apply `.clip(CircleShape)` **before** any sheen, haze, or background modifier so no rectangular/square shadow artifact ever appears behind the circle.
- **Specular Sheen:**
  - Apply `.clip(shape)` before `.nexusSpecularSheen(palette.isDark)` so the top-edge specular highlight stays strictly bounded inside the component shape.
- **Floating Glass (`nexusFloatingGlass`):**
  - Selective frosted glass (`HazeState` + `hazeEffect`, `blurRadius = 26.dp`, `alpha = 0.68f`) strictly for floating dock pills and circular floating orbs — not for full-screen backgrounds.

---

## 3. Edge Fade & Progressive Scrim (`NexusEdgeFade.kt`)
- Wrap scrollable screens in `NexusEdgeFadeContainer` using `LocalNexusEdgeFadeConfig`.
- Top and bottom system areas use `nexusEdgeGradientScrim(containerColor, topToBottom)` — a smooth translucent **opacity fade** matched to the background color (`#000000` or `#FFFFFF`), **without** blurring scrolling text.

---

## 4. Floating Bottom Navigation Dock (`NexusFloatingDock.kt`)
- **Positioning:** Sits **low** near the bottom navigation bar inset (matching Apple Music's compact bottom placement, not floating high up the screen).
- **Symmetric Collapse & Expand Morph:**
  - Driven by a continuous `expandProgress` animation (`NestedScrollConnection` collapses on scroll down, expands on scroll up or tap):
  - **Collapse:** Main pill smoothly collapses into a compact left circular orb/pill while the center page/status indicator pill appears.
  - **Expand:** Exact opposite animation — the left small bubble smoothly expands back into the full centered navigation bar while the indicator pill disappears.
- **Profile Orb Interactions:**
  - Short press opens `ProfileScreen`.
  - Long press opens quick-action menu (Profile, Settings, Updates, Lock Now, Sign Out).
- **Dynamic Status Banner:** Supports an inline `DockStatusBanner` (e.g. `"New Nexus Mobile update ready"` -> `"Install"`).

---

## 5. FZ Portal Intro Splash (`FzPortalSplash.kt`)
- **Always Pitch-Black (`#000000`):** Irrespective of Light or Dark mode across the app, the splash background is always pitch black.
- **FZ Lime (`#55FF00`):** Uses the original single lime color (`#55FF00`), NOT twin-color.
- **Opaque Center at Start:** The Z center starts **filled (`#050507`)**, NOT transparent/see-through from frame 0; it only opens up as the portal transition begins.
- **Twitter/X-Style Dramatic Pullback Bounce:**
  - Scales down slightly (`~0.84f`) for ~0.1s first, then rapidly springs forward to open the portal into the main UI.

---

## 6. Lock Screen (`LockScreen.kt`)
- Matches the same Light / Pitch-Black OLED + `nexusSpecularBorder(palette.isDark, 0.75f)` + `nexusSpecularSheen(palette.isDark)` language.
- 4-digit PIN dots with spring fill/shake animations.
- Circular keypad buttons with `0.90f` press scale bounce, single specular ring, and zero square shadow artifacts.
- Supports optional biometric fingerprint button, exponential lockout timer, and Sign Out action.

---

## 7. Authentication, Onboarding & Security Flow Rules
- **Central Auth (`services/core-hub`):**
  - All mobile auth flows talk to `https://faizal.uk/api/auth/*`:
    - **Password-First Default (`POST /api/auth/login`):** Both Mobile and Web Admin open directly on Username/Email + Password (no cluttered top tab bars or filler copy like `"Nexus Identity"`).
    - **Netflix/Amazon-Style Secondary OTP (`POST /api/auth/otp/send` + `verify`):** Clean `"Use Email OTP instead"` button below Sign In; carries over any typed email, auto-submits on 6th digit, includes a `60s` resend cooldown, and prompts `"Create Account"` if `account_exists: false`.
    - **Spotify-Style Reset & Instant Login Email (`POST /api/auth/password-reset/request`):** Sends a single email with both **"Log in immediately without a password"** (one-time Magic Link -> `/admin/auth/verify` / `nexus-mobile://auth-callback`) and **"Reset Password"** (`/admin/auth/reset`).
    - **Create Account / Onboarding (`POST /api/auth/register` + optional Security Question)**
    - **Device Code (`POST /api/auth/device/*` & `/auth/device`):** Kept on backend & Web Admin for macOS / TV / Car AI Box onboarding (QR/camera approval in Mobile to be added later).
    - **Social OAuth:** **Google** (`/api/auth/google`) and **Meta** (`/api/auth/meta`) only (no X).
    - **Subtle Footer:** `"Powered by Nexus Central Auth"` with `#55FF00` status dot.
- **Mandatory Post-Login Security Gate (`MainActivity.kt`):**
  1. Unauthenticated users cannot enter the app (`AuthScreen`).
  2. Authenticated users **must** have a 4-digit PIN:
     - If `hasCentralPin == true` on a new device without local PIN cache, verify central PIN via `/api/auth/pin/verify` and cache locally.
     - If `hasCentralPin == false`, force 2-step PIN creation (`LockScreen`) which syncs to `/api/auth/pin/setup` and caches locally in `EncryptedSharedPreferences`.
  3. After PIN setup (when biometrics are available and `biometricPromptMode == "ask_next_time"`), show `BiometricOptInScreen`:
     - **Enable Fingerprint Unlock**
     - **Not Now · Ask Me Next Time** (`"ask_next_time"` — prompts again on next login)
     - **Don't ask again on this device** (`"dont_ask_again"`)
- **Hardened PIN Change / Removal (`ReAuthScreen.kt`):**
  - Changing or removing the PIN from `SettingsScreen` or `ProfileScreen` is gated by `ReAuthScreen` (requiring **6-Digit Email OTP**, **Account Password**, or **Security Question** verified by `POST /api/auth/pin/update`).

---

## 8. Build & Deployment Constraints
- **Local Pre-Verification:**
  - Android: requires Temurin JDK 17 (`JAVA_HOME`) and Android SDK (`ANDROID_HOME`); run `.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon` in `apps/mobile/android`.
  - Rust: `cargo test -p personal-site-rust -p nexus-protocol -p nexus-mobile-core`.
  - Web Admin: `npm run build:admin` in `apps/web`.
- **Production Builds:** **NEVER** compile on cluster VMs (`my-mac`, `cos`, `pro`). Always push to GitHub so GitHub Actions CI builds and deploys release artifacts.
