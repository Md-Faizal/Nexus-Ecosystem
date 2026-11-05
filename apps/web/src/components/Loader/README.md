# FZ Monogram Luxury Loader — Complete Architecture & Reference Guide

## 1. Overview & System Lifecycle
The Loader is a 4-stage cinematic entrance experience engineered for **60/120 FPS performance**, sub-pixel vector precision, fluid cross-device responsiveness (Mobile, Tablet, Desktop, Ultrawide), and zero layout shift on initial load.

---

## 2. Directory Structure & Chronological Flow

```
src/components/Loader/
├── 1.LoaderBackground.jsx    # Stage 1: Cyber HUD, chevrons, percentage counter & progress bar
├── 2.LoaderSweep.jsx         # Stage 2: 5 -> 3 -> 1 Mechanical card sweep choreography
├── 3.LoaderLogo.jsx          # Stage 3: Animated FZ Vector Monogram reveal inside solid card
├── 4.ZPortalTransition.jsx   # Stage 4: Camera plunge & Z-portal punch through to Home Hero
├── Loader.jsx                # Master lifecycle orchestrator & Dev HUD scrubber interface
├── loaderConfig.js           # Configuration toggles, theme visual tokens & palettes
└── README.md                 # System documentation & developer guide (this file)
```

---

## 3. Stage-by-Stage Breakdown

### Stage 1: Cyber HUD Loader (`1.LoaderBackground.jsx`)
- **Visuals**: Pitch-black canvas with 4-corner chamfered HUD reticles (acute brackets + 3x3 green dot matrices), dual-mode responsive laser chevrons (`< >`) with velvet crimson facet shading, and sequential white-hot aerodynamic pulses.
- **Center Stack**: Top neon green FZ Monogram vector emblem, large Chakra Petch percentage counter (`p%`), clean linear progress bar (`linear-gradient(90deg, #39ff14 0%, #55ff00 100%)`), traveling leading-edge lens flare hotspot (`left: ${p}%`), and `"LOADING EXPERIENCE..."` typography in Share Tech Mono.
- **Micro-Engine Parity**: Synchronized with `index.html` static HTML preloader for 0ms First Contentful Paint (FCP) and zero frame-shift when React hydrates.

### Stage 2: 5 → 3 → 1 Mechanical Card Sweep (`2.LoaderSweep.jsx`)
- **Pacing**: 1.45s paced choreography.
  - *Sub-Stage 1 (0ms–650ms)*: 5 alternating horizontal bars sweep in from the left and right.
  - *Sub-Stage 2 (650ms–1050ms)*: Bars 2 & 4 merge into Bar 3 forming 3 distinct panels (20%, 60%, 20%).
  - *Sub-Stage 3 (1050ms–1450ms)*: Center panel merges with Bars 1 & 5 into 1 unified solid card.
  - *Sub-Stage 4 (1450ms–2000ms)*: Dual-origin corner stroke trace locks into a glowing white frame (`rx="clamp(20px, 2.5vw, 32px)"`) with a hero dwell.

### Stage 3: FZ Monogram Vector Reveal (`3.LoaderLogo.jsx`)
- **Visuals**: Vector outline trace (DrawSVG simulation) + fill opacity reveals for the FZ Monogram.
- **Layers**:
  1. Wing Down (`#55ff00`)
  2. Wing Up (`#55ff00`)
  3. Center Dark Core Z-Polygon (`#050507`)
  4. White Accent Z-Path (`#ffffff`)

### Stage 4: Z-Portal Camera Plunge (`4.ZPortalTransition.jsx`)
- **Mechanism**: Mathematical SVG `polygon()` inverse cutout mask applied via `clip-path`.
- **Vector Seam Protection**: Vertices are micro-dilated by `1.018x` (+1.2px) to prevent sub-pixel antialiasing edge leaks.
- **Plunge Animation**: Snappy 0.95s logarithmic power curve camera dive from 1.0x to 9.5x scale, matching the exact size and position of the Home Hero Monogram for an unnoticeable transition into the main site.

---

## 4. Configuration & Toggles (`loaderConfig.js`)

You can customize the loader's behavior and aesthetic directly in `loaderConfig.js`:

```javascript
export const LOADER_CONFIG = {
  /**
   * Stage 1 background behavior during Stage 2 & 3:
   *  - 'Y': Keeps chevrons & HUD corners active in the background framing the sweep card.
   *  - 'N': Fades out Stage 1 background to pure black with a 0.92x scale disappear effect.
   */
  keepBackgroundActiveDuringSweep: 'N', // 'Y' | 'N'
};

export const LOADER_THEME = {
  accent: '#ff0055',
  cardGradient: 'linear-gradient(135deg, #070309 0%, #ff0055 50%, #0a0410 100%)',
  background: '#000000',
  neonGreen: '#55ff00',
  neonGreenBright: '#39ff14',
  neonRed: '#ff0055',
  neonRedHot: '#ff1475',
  neonRedDeep: '#b3003b',
  textWhite: '#ffffff',
  textGreen: '#55ff00',
  borderColor: '#ffffff',
  cardGlow: '0 0 25px rgba(255, 255, 255, 0.7), 0 0 45px rgba(255, 0, 85, 0.45), inset 0 0 20px rgba(255, 255, 255, 0.35)',
  borderGlow: '0 0 10px rgba(255, 0, 85, 0.65)',
  monogramWings: '#55ff00',
  monogramCore: '#050507',
  monogramAccent: '#ffffff',
  monogramNeon: '#55ff00'
};
```

---

## 5. Responsive Engine & Viewport Handling

| Viewport | Stage 1 Layout | Stage 2 Card Dimensions |
| :--- | :--- | :--- |
| **Mobile** (`<768px`) | Dedicated `750x1334` SVG (`.loader-svg-mobile`) with chevrons anchored at $x=20\text{px}-300\text{px}$ and $x=450\text{px}-730\text{px}$. | `inset-[clamp(8px,2vw,16px)]` (`N`) / `inset-[clamp(10px,2vw,18px)]` (`Y`). |
| **Desktop & Tablet** (`>=768px`) | Widescreen `1920x1080` SVG (`.loader-svg-desktop`) with outer wing chevrons. | `inset-[1.5vw]` (`N`) / `inset-[2.5vw]` (`Y`). |

- **Zero-Glitch Guarantee**: Explicit media query CSS rules are declared in both `index.html` inline `<style>` and `1.LoaderBackground.jsx` `<style>` to ensure deterministic rendering at FCP frame 0 before external CSS or JS loads.

---

## 6. Asset Preloader Integration (`src/utils/assetPreloader.js`)
- **Sequential Counting**: Monotonically increments every integer digit (`1, 2, 3... 99, 100`) without skipping percentages.
- **Velocity Limit**: Increment capped at ~1.1% per frame, guaranteeing a minimum smooth dwell even with warm browser caches.
