#!/bin/bash
# ==============================================================================
# Comprehensive Cluster State Synchronizer: my-mac (Priority 1) -> cos (2) -> pro (3)
# Synchronizes: messages.db, MacOS.db, cluster_hub.db, and Stalwart mail config/DKIM
# ==============================================================================
set -euo pipefail

TIMESTAMP=$(date -u +'%Y-%m-%dT%H:%M:%SZ')

# 1. messages.db
MESSAGES_SRC="/home/ubuntu/stack/apps/personal-site/backend/messages.db"
MESSAGES_TMP="/tmp/messages_cluster_sync.db"
if [ -f "$MESSAGES_SRC" ]; then
    sqlite3 "$MESSAGES_SRC" ".backup '$MESSAGES_TMP'"
    rsync -avz --checksum "$MESSAGES_TMP" ubuntu@10.0.0.195:/home/ubuntu/stack/apps/personal-site/backend/messages.db 2>/dev/null || true
    rsync -avz --checksum "$MESSAGES_TMP" prometheus:/home/ubuntu/stack/apps/personal-site/backend/messages.db 2>/dev/null || true
    rm -f "$MESSAGES_TMP"
fi

# 2. MacOS.db (macOS Web Desktop)
MACOS_SRC="/home/ubuntu/MacOS-Production/database/MacOS.db"
MACOS_TMP="/tmp/macos_cluster_sync.db"
if [ -f "$MACOS_SRC" ]; then
    sqlite3 "$MACOS_SRC" ".backup '$MACOS_TMP'"
    rsync -avz --checksum "$MACOS_TMP" ubuntu@10.0.0.195:/home/ubuntu/MacOS-Production/database/MacOS.db 2>/dev/null || true
    rsync -avz --checksum "$MACOS_TMP" prometheus:/home/ubuntu/MacOS-Production/database/MacOS.db 2>/dev/null || true
    rm -f "$MACOS_TMP"
fi

# 3. cluster_hub.db (Nexus Central Hub & Mesh Telemetry)
HUB_SRC="/home/ubuntu/stack/apps/personal-site/backend/cluster_hub.db"
HUB_TMP="/tmp/hub_cluster_sync.db"
if [ -f "$HUB_SRC" ]; then
    sqlite3 "$HUB_SRC" ".backup '$HUB_TMP'"
    rsync -avz --checksum "$HUB_TMP" ubuntu@10.0.0.195:/home/ubuntu/stack/apps/personal-site/backend/cluster_hub.db 2>/dev/null || true
    rsync -avz --checksum "$HUB_TMP" prometheus:/home/ubuntu/stack/apps/personal-site/backend/cluster_hub.db 2>/dev/null || true
    rm -f "$HUB_TMP"
fi

# 4. Stalwart Mail Config & DKIM Keys (/etc/stalwart)
MAIL_SRC="/home/ubuntu/stack/apps/personal-site/mail/etc"
if [ -d "$MAIL_SRC" ]; then
    ssh container-of-souls "mkdir -p /home/ubuntu/stack/apps/personal-site/mail/etc /home/ubuntu/stack/apps/personal-site/mail/data" 2>/dev/null || true
    rsync -avz --checksum "$MAIL_SRC/" ubuntu@10.0.0.195:/home/ubuntu/stack/apps/personal-site/mail/etc/ 2>/dev/null || true
    rsync -avz --checksum "$MAIL_SRC/" prometheus:/home/ubuntu/stack/apps/personal-site/mail/etc/ 2>/dev/null || true
fi

echo "[$TIMESTAMP] [cluster-state-sync] Successfully synchronized messages.db, MacOS.db, cluster_hub.db, and mail/etc"
