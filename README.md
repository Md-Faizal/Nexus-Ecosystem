# Nexus Fleet & Ecosystem Monorepo

> **Universal High-Reliability Technology Infrastructure, Autonomous Fleet Mesh & Distributed Applications**  
> Primary Hub: [faizal.uk](https://faizal.uk) · Central CAN-Bus Mesh: `wss://faizal.uk/api/mesh/ws`

---

## 🏛️ Monorepo Architecture

This repository is organized as an industrial-grade, domain-driven monorepo housing multi-platform client applications, core distributed backend services, and automated cloud infrastructure.

```
.
├── apps/                               # Client-Facing Applications (User Interfaces)
│   ├── web/                            # React 19 + Tailwind 4 + Three.js Web & Admin Console
│   ├── mobile/                         # Native Android Client (Jetpack Compose, Kotlin, M3 Expressive)
│   └── desktop/                        # Multiplatform Rust Agent & System Tray Daemon (Windows/Linux/macOS)
│
├── services/                           # Backend Services & Micro-Daemons
│   ├── core-hub/                       # Rust Axum Central REST API & CAN-Bus WebSocket Mesh Controller
│   ├── watchdog/                       # Autonomous Failover & Process Health Watchdog (Rust)
│   └── mail/                           # ESMTP Stalwart Mail Stack & Automated Synchronization
│
├── infra/                              # DevOps, Cloud Orchestration & Infrastructure
│   ├── config/                         # Virtual host & Nginx configuration definitions
│   └── scripts/                        # Cloud failover runners, OCI allocators, and backup automation
│
├── assets/                             # Master Design & Asset Registry
│   └── design/                         # Master 3D GLB models, Blender source files, Monograms, SVGs, Typography
│
├── public/                             # Public Web Distribution Hub & 1-Command Fleet Installers
│   └── releases/                       # Automated multi-platform agent releases & update manifests
│
├── docs/                               # Master Architectural Context & Technical Roadmaps
│   ├── NEXUS_MASTER_CONTEXT_AND_ROADMAP.md # Fleet CAN-bus architecture & Cockroach resilience doctrine
│   └── PROJECT_ROADMAP_AND_HANDOFF.md      # Infrastructure status & VM migration diary
│
└── archive/                            # Retired legacy services, prototypes, and historical tests
```

---

## 🚀 Quick Start Guide

### 1. Web Application (`apps/web`)
```bash
cd apps/web
npm install
npm run dev
```

### 2. Android Mobile Application (`apps/mobile`)
Open `apps/mobile` in Android Studio or compile via Gradle:
```bash
cd apps/mobile
./gradlew assembleDebug
```

### 3. Core Rust Backend (`services/core-hub`)
```bash
cd services/core-hub
cargo run
```

### 4. Desktop Agent Daemon (`apps/desktop`)
```bash
cd apps/desktop
cargo run
```

---

## 🛡️ Autonomous "Cockroach" Resilience Charter
All services adhere to the self-healing, zero-crash Stark network doctrine:
* **Zero External Dependencies**: Standalone compiled Rust micro-binaries with thin LTO (<5 MB RSS).
* **Automotive CAN-Bus Multiplexing**: Bidirectional WebSocket mesh linking cloud cores, compute nodes, and mobile clients with sub-millisecond priority interrupts.
* **Instant Failover Quorum**: Active Edge (`my-mac`) automatically cascades state to Standby (`cos`) and Quorum (`pro`).
