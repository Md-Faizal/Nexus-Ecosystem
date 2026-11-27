# Nexus Mobile (Android) — Design Language, Rationale & Implementation Guide

> **How to use this file:** Reference `apps/mobile/DESIGN_LANGUAGE.md` in any future conversation so new screens, modals, and animations match the exact visual language, motion physics, and design intent of Nexus Mobile.

---

## 1. Design Philosophy & Why Every Rule Exists

The visual language of Nexus Mobile is built around two strict references:
1. **Apple Music (iOS / Android)** for **frosted glass chrome, floating bottom navigation, edge fades, and circular controls**.
2. **Twitter / X** for the **pitch-black dramatic portal intro**.

### Core Principles (User-Specified Rules)
- **Subtlety Over Flash:** Specular highlights, borders, and shimmer MUST be understated (like Apple Music's thin rim highlight) — never overdone, loud, or neon-flooded.
- **Single Ring Only:** Circular buttons and pills have **one** subtle `0.75.dp` specular border. Never stack two concentric rings/borders.
- **Zero Square Shadow Artifacts:** Circular buttons (`NexusCircularButton`, `NexusCircularBackButton`, keypad keys) must NEVER show a faint rectangular/square box shadow behind the circle. Always `.clip(CircleShape)` **before** applying background, haze, or sheen modifiers.
- **True Pitch-Black OLED & Pure Light:** Dark mode is `#000000` (not dark gray Material 3 `surfaceContainer`), and Light mode is `#FFFFFF` (never Material 3 pastel/pinkish dynamic tints).

---

## 2. Current App Theme (`NexusTheme.kt`)

| Token | Pitch Black (`isDark = true`) | Light (`isDark = false`) | Where Used |
|---|---|---|---|
| `colorScheme.background` / `surface` | `#000000` | `#FFFFFF` | Root screen backgrounds |
| `palette.cardSurface` | `#121316` | `#F8FAFC` | Cards, auth container, sheets |
| `palette.cardElevated` | `#1A1C20` | `#FFFFFF` | Input fields, nested tiles |
| `palette.glassSurface` | `#141519` | `#F8FAFC` | Floating dock & circular orbs |
| `palette.hairlineBorder` | `White @ 8%` | `Slate @ 8%` | Subtle dividers & resting input borders |
| `colorScheme.primary` / `onPrimary` | `#F8FAFC` / `#000000` | `#0F172A` / `#FFFFFF` | Primary monochrome actions |
| `colorScheme.secondary` | `#38BDF8` | `#0284C7` | Info accents, active badges |
| `palette.onlineDot` / Brand Accent | `#22C55E` / `#55FF00` (FZ Lime) | `#22C55E` / `#55FF00` | Online dots, FZ monogram, primary auth CTA |
| `palette.dangerAccent` | `#EF4444` | `#EF4444` | Errors, destructive actions |

- **`useDynamicColor` defaults to `false`** so Android wallpaper theming never injects off-brand pastel tints.
- **Specular Border (`nexusSpecularBorder(palette.isDark, 0.75f)`):** Vertical gradient `BorderStroke` (`White 14% -> 5% -> 8%` in dark mode) mimicking light catching the top edge of glass.
- **Specular Sheen (`nexusSpecularSheen(palette.isDark)`):** Subtle top-edge highlight; MUST be preceded by `.clip(shape)`.

---

## 3. Component-by-Component Breakdown (How & Why)

### A. FZ Portal Intro Splash (`FzPortalSplash.kt`)
- **Why:** Inspired by the Twitter/X launch animation, adapted for the FZ monogram.
- **Rules & Implementation:**
  - **Always Pitch Black (`#000000`):** Irrespective of system Light or Dark mode across the rest of the app, the splash background is always `#000000`.
  - **Single FZ Lime (`#55FF00`):** Both wings use `#55FF00` (not twin-color).
  - **Opaque Center at Start:** The Z center starts filled (`#050507`) rather than transparent/see-through from frame 0, and only opens up as the portal transition expands.
  - **Dramatic Pullback Bounce:** Scales down slightly (`0.84f`) for `~0.1s` first, then springs forward rapidly to reveal the app underneath.

### B. Circular Buttons (`NexusCircularButton.kt`)
- **Why:** Apple Music's top-left back / top-right action buttons float fixed in the corner over scrolling content with a lightweight frosted mask and zero outer square halo.
- **How:** Placed in an outer `Box` overlay (`Alignment.TopEnd` / `Alignment.TopStart` with `.statusBarsPadding()`) above `NexusEdgeFadeContainer`, using `Surface(shape = CircleShape, border = nexusSpecularBorder(palette.isDark, 0.75f))`, `.clip(CircleShape)`, lightweight `nexusFloatingGlass` (`10.dp` blur, `0.40f` alpha), and `nexusSpecularSheen(palette.isDark)`. Header text below reserves `padding(end = 52.dp)`. No elevation shadow box.

### C. Edge Fades & Lightweight Frosted Mask (`NexusEdgeFade.kt`, `NexusTheme.kt`)
- **Why:** Scrolling content should melt smoothly behind the status bar and floating dock without blurring/distorting text, and floating chrome should softly tint underlying colors (not heavy opaque blur).
- **How:**
  - `NexusEdgeFadeContainer` + `nexusEdgeGradientScrim` uses `topExtraHeight = 46.dp`, `bottomExtraHeight = 44.dp`, `maxAlpha = 0.94f`, and `triggerOffsetPx = 40`.
  - `nexusFloatingGlass` uses a lightweight frosted mask (`blurRadius = 10.dp`, `backgroundColor = containerColor.copy(alpha = 0.40f)`, `tint = HazeTint(containerColor.copy(alpha = 0.08f))`).

### D. Floating Bottom Navigation Dock (`NexusFloatingDock.kt`)
- **Why:** Matches Apple Music's compact floating pill bar that sits **low** near the bottom edge and reacts to scroll.
- **How:**
  - Positioned close to the bottom navigation bar inset (not floating high up).
  - Driven by a continuous `expandProgress` float (`NestedScrollConnection` collapses on scroll down, expands on scroll up or tap):
    - **Collapse:** Main pill morphs down into a small circular orb on the **left** while a compact center status/page indicator pill appears.
    - **Expand:** Symmetric reverse — the left small orb expands smoothly back into the centered navigation bar while the indicator pill disappears.
  - **Profile Orb:** Short press opens `ProfileScreen`; long press opens quick menu (Profile, Settings, Updates, Lock Now, Sign Out).
  - **Dynamic Banner:** Supports inline `DockStatusBanner` (e.g. `"New Nexus Mobile update ready"` -> `"Install"`).

### E. Lock Screen (`LockScreen.kt`)
- **Why:** Security lock should feel native to the same OLED / Glass language, not a generic Android PIN dialog.
- **How:** 4-digit PIN dots with spring fill + shake on mismatch, circular keypad buttons with `0.90f` press scale bounce, single `0.75.dp` specular border, optional biometric fingerprint trigger, lockout timer, and Sign Out button.

### F. Login, Biometric Opt-In & Re-Auth (`AuthScreen.kt`, `BiometricOptInScreen.kt`, `ReAuthScreen.kt`)
- **Why:** Clean, zero-clutter login modeled after **Netflix / Amazon / Spotify**:
  - **Password-first default** with crisp `"Sign in"` title (no filler phrases like `"Nexus Identity"` or `"Central auth across Web, macOS & Mobile"`).
  - Full neon-green (`#55FF00`) FZ monogram orb at top with a subtle radial lime halo.
  - Bespoke `NexusAuthInput` fields (`14.dp` radius, `cardElevated` fill, animated focus border) inside a `nexusSpecularBorder(0.75f)` + `nexusSpecularSheen` card.
  - **"Use Email OTP instead"** button below `"Sign In"` (carries over typed email, auto-submits on 6th digit, 60s resend cooldown, prompts `"Create Account"` if unregistered).
  - **Google & Meta** only (no X).
  - Subtle **"Powered by Nexus Central Auth"** footer with `#55FF00` dot.
  - Mandatory post-login 4-digit PIN (synced centrally) -> `BiometricOptInScreen` (`Enable Fingerprint Unlock`, `Not Now · Ask Me Next Time`, `Don't ask again on this device`) -> `ReAuthScreen` required before changing/removing PIN.
