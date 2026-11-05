#!/usr/bin/env bash
set -euo pipefail

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_DIR="/opt/backups/backup_${TIMESTAMP}"
ARCHIVE="/opt/backups/stack_backup_${TIMESTAMP}.tar.gz"

echo "==> Creating temporary backup directory: ${BACKUP_DIR}"
mkdir -p "${BACKUP_DIR}/databases" "${BACKUP_DIR}/configs"

echo "==> Snapshotting SQLite databases..."
if [ -f /home/ubuntu/nexus-backend/nexus_portal.db ]; then
    echo "  - Backing up nexus_portal.db..."
    sqlite3 /home/ubuntu/nexus-backend/nexus_portal.db "VACUUM INTO '${BACKUP_DIR}/databases/nexus_portal.db';"
fi

if [ -f /home/ubuntu/cs-panel/panel.db ]; then
    echo "  - Backing up cs-panel panel.db..."
    sqlite3 /home/ubuntu/cs-panel/panel.db "VACUUM INTO '${BACKUP_DIR}/databases/panel.db';"
fi

if [ -f /home/ubuntu/MacOS-Production/database/MacOS.db ]; then
    echo "  - Backing up MacOS.db..."
    sqlite3 /home/ubuntu/MacOS-Production/database/MacOS.db "VACUUM INTO '${BACKUP_DIR}/databases/MacOS.db';"
fi

if [ -f /home/ubuntu/faizal.uk/backend/messages.db ]; then
    echo "  - Backing up messages.db..."
    sqlite3 /home/ubuntu/faizal.uk/backend/messages.db "VACUUM INTO '${BACKUP_DIR}/databases/messages.db';"
fi

echo "==> Backing up Nginx configurations..."
cp -r /etc/nginx/sites-available "${BACKUP_DIR}/configs/nginx-sites"

echo "==> Backing up Stack Compose definitions & Manifests..."
mkdir -p "${BACKUP_DIR}/configs/compose"
find /opt/stack/apps -name "docker-compose*.yml" -exec cp --parents {} "${BACKUP_DIR}/configs/compose/" \; 2>/dev/null || true
cp /opt/stack/docker-compose.yml "${BACKUP_DIR}/configs/compose/" 2>/dev/null || true

echo "==> Creating compressed tarball archive..."
tar -czf "${ARCHIVE}" -C "${BACKUP_DIR}" .
rm -rf "${BACKUP_DIR}"

echo "==> Backup complete: ${ARCHIVE} ($(du -sh "${ARCHIVE}" | cut -f1))"

# Prune old backups, retain only latest 7 days
find /opt/backups/ -name "stack_backup_*.tar.gz" -type f -mtime +7 -delete 2>/dev/null || true
