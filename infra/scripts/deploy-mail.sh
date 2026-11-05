#!/usr/bin/env bash
################################################################################
# DEPLOY STALWART MAIL SERVER TO UBUNTU PRODUCTION VM
#
# Isolated deployment: copies mail/ directory to prod VM, configures
# Nginx proxy for Stalwart Web Admin / JMAP, and launches Stalwart container.
################################################################################

set -e

SSH_TARGET="${SSH_TARGET:-my-mac}"
UBUNTU_PROD_DIR="${UBUNTU_PROD_DIR:-/home/ubuntu/faizal.uk}"
MAIL_TARGET_DIR="$UBUNTU_PROD_DIR/mail"
DEV_MAIL_DIR="$(cd "$(dirname "$0")/../mail" && pwd)"

echo "======================================================"
echo " Deploying Stalwart Mailserver -> $SSH_TARGET:$MAIL_TARGET_DIR"
echo "======================================================"

# 1. Test SSH
echo "[INFO] Testing SSH connection to $SSH_TARGET..."
ssh -o ConnectTimeout=5 "$SSH_TARGET" "echo 'SSH OK'" > /dev/null
echo "[OK] SSH Connected."

# 2. Ensure remote directory exists
ssh "$SSH_TARGET" "mkdir -p '$MAIL_TARGET_DIR/data'"

# 3. Transfer docker compose & config files
echo "[INFO] Uploading Stalwart configuration..."
scp "$DEV_MAIL_DIR/docker-compose.yml" "$SSH_TARGET:$MAIL_TARGET_DIR/docker-compose.yml"
scp "$DEV_MAIL_DIR/admin/nginx-mailadmin.conf" "$SSH_TARGET:$MAIL_TARGET_DIR/nginx-mailadmin.conf"

# 4. Apply remote migration and bring up Stalwart
ssh "$SSH_TARGET" bash -s << 'EOF'
set -e
cd /home/ubuntu/faizal.uk/mail

# Stop old docker-mailserver if running
if docker ps -a --format '{{.Names}}' | grep -q '^mailserver$'; then
    echo "[INFO] Stopping and removing old docker-mailserver container..."
    docker stop mailserver || true
    docker rm mailserver || true
fi

# Clean up custom python mail-admin process from PM2
if pm2 list | grep -q 'mail-admin'; then
    echo "[INFO] Removing old mail-admin PM2 service..."
    pm2 stop mail-admin || true
    pm2 delete mail-admin || true
    pm2 save || true
fi

# Update Nginx config to point to Stalwart Webadmin / JMAP
if [ -f nginx-mailadmin.conf ]; then
    echo "[INFO] Updating Nginx configuration for Stalwart..."
    sudo cp nginx-mailadmin.conf /etc/nginx/sites-available/mailadmin.faizal.uk
    sudo ln -sf /etc/nginx/sites-available/mailadmin.faizal.uk /etc/nginx/sites-enabled/
    sudo nginx -t && sudo systemctl reload nginx
fi

# Start Stalwart Mail Server
echo "[INFO] Starting Stalwart Mail Server..."
docker compose up -d

sleep 3
docker compose ps
EOF

echo "======================================================"
echo " Stalwart Mail Server deployed successfully!"
echo "======================================================"
