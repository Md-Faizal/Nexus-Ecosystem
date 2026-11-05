#!/bin/bash
# ==============================================================================
# Cluster Database Cascading Sync: my-mac (Priority 1) -> cos (2) -> pro (3)
# ==============================================================================
set -euo pipefail

MESSAGES_SRC="/home/ubuntu/stack/apps/personal-site/backend/messages.db"
MESSAGES_TMP="/tmp/messages_cluster_sync.db"

if [ -f "$MESSAGES_SRC" ]; then
    # Create hot transactional backup using SQLite API (avoids lock corruption)
    sqlite3 "$MESSAGES_SRC" ".backup '$MESSAGES_TMP'"

    # 1. Cascade to Priority 2: cos (Oracle VCN @ 10.0.0.195)
    rsync -avz --checksum "$MESSAGES_TMP" ubuntu@10.0.0.195:/home/ubuntu/stack/apps/personal-site/backend/messages.db || true

    # 2. Cascade to Priority 3: pro (GCP Auxiliary @ 35.208.86.56)
    rsync -avz --checksum "$MESSAGES_TMP" prometheus:/home/ubuntu/stack/apps/personal-site/backend/messages.db || true

    rm -f "$MESSAGES_TMP"
    echo "[$(date -u +'%Y-%m-%dT%H:%M:%SZ')] [cluster-db-sync] Successfully synchronized messages.db: my-mac -> cos -> pro"
fi
