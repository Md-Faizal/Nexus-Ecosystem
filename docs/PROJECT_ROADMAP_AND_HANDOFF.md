# Infrastructure Handoff & Modernization Roadmap

**Machine Target**: Oracle Cloud VM (`my-mac`)  
**OS**: Ubuntu 24.04 LTS (1 vCPU, 954 MB RAM, 48 GB Disk)  
**Host IP**: Accessible via SSH alias `my-mac`  

---

## 1. High-Level Objectives (The Big Picture)

1. **Storage Stability**: Keep root disk space safe (was critically low at 1.4 GB; now healthy with 13-20 GB free). Avoid bloat from caches, unused snaps, and build artifacts.
2. **RAM & Performance Optimization (Rust + Docker)**:
   - System baseline memory is constrained (~954 MB total RAM).
   - Migrate workloads from heavy PM2 / Node daemons to lightweight, compiled Rust micro-services and memory-capped Docker containers.
   - Goal: Drop baseline memory usage from ~600 MB down to <380 MB.
3. **Industrial Standard Structure & 100% Portability**:
   - Establish `/opt/stack/` directory standard.
   - Completely decouple code (Git repositories) from state (databases, configs, uploads).
   - Provide 1-click zero-downtime backup (`backup.sh`) and restore (`restore.sh`) so the entire VM or parts of it can be cloned to a new VM in under 3 minutes.

---

## 2. Current Status & Progress Summary

### Completed Milestones ✅
1. **Emergency Disk Space Recovery**:
   - Purged ~14 GB of stale VS Code server revisions (`~/.vscode-server/cli/servers`).
   - Purged ~12 GB of unneeded desktop GUI snaps (`firefox`, `thunderbird`, `chromium`, `cups`, `gnome-46`, `mesa-2404`).
   - Cleaned build and download caches (`~/.cache`, `/var/lib/snapd/cache`).
   - **Result**: Free space jumped from **1.4 GB (3%) to 18–20 GB (37% free)**.
2. **Personal Site (`faizal.uk`) Migrated to Rust**:
   - Replaced PM2 Node.js process with a standalone compiled Rust Axum binary (`personal-site-rust` container).
   - Memory dropped from **~50 MB &rarr; 4.58 MB RAM**.
   - Connected directly to Stalwart ESMTP on `127.0.0.1:25`.
3. **Backup & Restore System Operational**:
   - [`/opt/stack/ops/scripts/backup.sh`](file:///opt/stack/ops/scripts/backup.sh): Takes transactional `VACUUM INTO` snapshots of live SQLite databases (`nexus_portal.db`, `panel.db`, `MacOS.db`, `messages.db`), backs up Nginx configs and compose manifests into an **13 MB `.tar.gz` bundle** in `/opt/backups/`.
   - [`/opt/stack/ops/scripts/restore.sh`](file:///opt/stack/ops/scripts/restore.sh): 1-command restore script with safety rollbacks.
   - Nightly backup automated via crontab (`0 3 * * * /opt/stack/ops/scripts/backup.sh >> /var/log/stack_backup.log 2>&1`).
4. **MacOS Desktop Service Emergency Restoration**:
   - Restored displaced files back to `/home/ubuntu/MacOS-Production/`.
   - `macos-server-blue` container verified online and healthy on `https://mac.faizal.uk` (port 3001).
5. **Unified Master Layout at `/home/ubuntu/stack/`**:
   - Physically consolidated all apps, games, compose definitions, and operations scripts into one single master directory: `/home/ubuntu/stack/`.
   - Eliminated confusing symlink bridges and purged `/opt/stack`.
   - Full IDE and SSH write permissions without `sudo` friction.
6. **PM2 Daemon Completely Retired**:
   - Migrated `cs-panel` to native systemd unit (`cs-panel.service`).
   - Migrated `mc-manager` to native systemd unit (`mc-manager.service`).
   - `nexus-backend` is stopped but remains manually startable from `/home/ubuntu/stack/apps/nexus/` for continued use during the gradual Rust migration.
   - Executed `pm2 kill` and disabled `pm2-ubuntu`.
   - **Reclaimed ~55 MB+ RAM** previously consumed by PM2 master god daemon, agent, and logrotate.

---

## 3. Current Live Services State

All active production services are verified healthy:
- **`https://mac.faizal.uk`** &rarr; `macos-server-blue` Docker container (port 3001, HTTP 200 OK)
- **`https://faizal.uk`** &rarr; `personal-site-rust` Docker container (port 3002, HTTP 200 OK)
- **`nexus.faizal.uk`** &rarr; **Stopped, available to start** (`nexus-backend.service` runs the Node.js app in `/home/ubuntu/stack/apps/nexus`)
- **`http://cs.faizal.uk`** &rarr; `cs-panel.service` under systemd (port 3005, HTTP 200 OK)
- **`https://mine.faizal.uk`** &rarr; `mc-manager.service` under systemd (port 5000, HTTP 200 OK)
- **Mail Server** &rarr; `stalwartlabs/stalwart:latest` Docker container (ports 25, 8080, healthy)

---

## 4. Current Master Layout (`/home/ubuntu/stack`)

```
/home/ubuntu/stack/
├── docker-compose.yml               # Central compose orchestrator
├── apps/
│   ├── personal-site/               # Faizal Portfolio (Rust container)
│   ├── macos-desktop/               # Web MacOS Desktop (Docker container)
│   ├── nexus/                       # Nexus Portal (Node.js backend; manually startable during migration)
│   ├── cs-panel/                    # CS 1.6 Web Management Panel (systemd)
│   └── personal-site/mail/          # Stalwart Mail Server (Docker container)
├── games/
│   ├── cs16/                        # Counter-Strike 1.6 Dedicated Server
│   └── minecraft/                   # Bedrock Minecraft Server & Web Manager (systemd)
└── ops/
    ├── scripts/                     # backup.sh & restore.sh
    └── nginx/                       # Virtual hosts backups
```

---

## 4. Work Completed & Next Milestones

### Completed Actions in Section 4 ✅
- **Step 1**: `/opt/stack` layout structured with symlinks (`macos-desktop`, `cs-panel`, `nexus`, `mailserver`, `cs16`, `minecraft`).
- **Step 2**: Reconciled and optimized backup bundle (`backup.sh`) to include `messages.db` and exclude node_modules (clean 13 MB tarball).
- **Step 3**: Replaced PM2 processes with lightweight, auto-restarting systemd units:
  - `nexus-backend.service` (port 3010)
  - `cs-panel.service` (port 3005)
  - `mc-manager.service` (port 5000)
- **Step 4**: Retired PM2 permanently (`pm2 kill`, disabled startup service).
- **Step 5**: Scheduled nightly backups at 03:00 via crontab with log output to `/var/log/stack_backup.log`.

### Future Opportunities
1. **Progressively migrate application and game backends to Rust**:
   - Keep each existing backend available while it is migrated; all application and game backends will move to Rust incrementally, with Nexus as one of several services in that migration.
2. **Offsite Cloud Sync for Backups**:
   - Configure `rclone` with an S3 or Google Drive bucket to mirror `/opt/backups/*.tar.gz` offsite automatically after each nightly run.
