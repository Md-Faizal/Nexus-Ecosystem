#!/usr/bin/env bash
# ==============================================================================
# Nexus Fleet - Offsite Cloud Backup Mirror & Remote Retention Manager
# Hooked post-run after infra/scripts/backup_refined.sh
# ==============================================================================
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/opt/backups}"
RCLONE_REMOTE="${RCLONE_REMOTE:-nexus-cloud:nexus-backups}"
BW_LIMIT="${BW_LIMIT:-15M}" # 15MB/s bandwidth cap to prevent production link saturation
LOG_TAG="nexus-offsite-backup"

log() {
    echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] [$LOG_TAG] $*"
}

# 1. Locate the most recent backup archive
LATEST_BACKUP=$(find "${BACKUP_DIR}" -maxdepth 1 -name "stack_backup_*.tar.gz" -type f -printf '%T@ %p\n' 2>/dev/null | sort -n | tail -1 | cut -f2- -d" ")

if [ -z "${LATEST_BACKUP:-}" ] || [ ! -f "${LATEST_BACKUP}" ]; then
    log "ERROR: No stack_backup_*.tar.gz found in ${BACKUP_DIR} to replicate offsite."
    exit 1
fi

ARCHIVE_NAME=$(basename "${LATEST_BACKUP}")
SHA_FILE="${LATEST_BACKUP}.sha256"

log "==> Processing latest backup archive: ${ARCHIVE_NAME}"

# 2. Compute SHA256 integrity checksum if not already present
if [ ! -f "${SHA_FILE}" ]; then
    log "--> Generating SHA256 checksum for ${ARCHIVE_NAME}..."
    sha256sum "${LATEST_BACKUP}" > "${SHA_FILE}"
fi

# 3. Verify rclone installation
if ! command -v rclone &> /dev/null; then
    log "WARNING: rclone binary not found in PATH. Skipping remote mirror upload."
    log "To enable offsite backups, install rclone and configure remote '${RCLONE_REMOTE}'."
    exit 0
fi

# 4. Offsite Cloud Replication
log "--> Syncing ${ARCHIVE_NAME} to remote storage: ${RCLONE_REMOTE}/archives/..."
rclone copy "${LATEST_BACKUP}" "${RCLONE_REMOTE}/archives/" \
    --checksum \
    --fast-list \
    --bwlimit "${BW_LIMIT}" \
    --stats 10s \
    --retries 3

log "--> Syncing checksum ${SHA_FILE} to remote..."
rclone copy "${SHA_FILE}" "${RCLONE_REMOTE}/archives/" \
    --fast-list \
    --retries 3

log "--> Offsite transfer complete."

# 5. Remote Retention Management
# Keep:
# - Last 30 days of archives
# - Automated cleanup of older archives
log "--> Applying remote retention policy (purging remote files older than 30 days)..."
rclone delete "${RCLONE_REMOTE}/archives/" \
    --min-age 30d \
    --rmdirs 2>/dev/null || true

log "==> Offsite replication and lifecycle management completed successfully."
