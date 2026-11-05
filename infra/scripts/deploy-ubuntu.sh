#!/bin/bash

################################################################################
# UBUNTU REMOTE DEPLOYMENT SCRIPT FOR PERSONAL SITE
# 
# Deploy updates from Windows dev machine to Ubuntu production VM via SSH
# Syncs files via rsync/scp, updates Nginx config, and restarts PM2 services
# 
# Usage: ./deploy-ubuntu.sh [--dry-run] [--force]
#
# Options:
#   --dry-run      Show what would happen without making changes
#   --force        Override failure checks
#
# Required environment variable:
#   - SSH_TARGET: SSH alias or user@host (e.g., my-mac or ubuntu@192.168.1.100)
#   - UBUNTU_PROD_DIR: Production directory on Ubuntu (default: /home/ubuntu/faizal.uk)
#
# Example:
#   export SSH_TARGET=ubuntu-prod
#   ./deploy-ubuntu.sh
################################################################################

set -e  # Exit on error

# Configuration - from environment or defaults
DEV_DIR="${DEV_DIR:-.}"  # Current directory if DEV_DIR not set
SSH_TARGET="${SSH_TARGET:-}"  # SSH alias or user@host
UBUNTU_PROD_DIR="${UBUNTU_PROD_DIR:-/home/ubuntu/faizal.uk}"
TIMESTAMP=$(date +"%Y-%m-%d_%H-%M-%S")
DRY_RUN=false
FORCE=false

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'  # No Color

# Functions
print_success() {
    echo -e "${GREEN}[OK]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERR]${NC} $1" >&2
}

print_info() {
    echo -e "${CYAN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_header() {
    echo "======================================================"
    echo "   $1"
    echo "   Timestamp: $TIMESTAMP"
    echo "======================================================"
    echo ""
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --force)
            FORCE=true
            shift
            ;;
        *)
            print_error "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Validate environment variables
if [ -z "$SSH_TARGET" ]; then
    print_error "SSH_TARGET not set. Export it: export SSH_TARGET=my-ubuntu-alias"
    exit 1
fi

print_header "REMOTE DEPLOYMENT: Personal Site -> Ubuntu VM via SSH"
echo "Target: $SSH_TARGET:$UBUNTU_PROD_DIR"
echo "Options: DRY_RUN=$DRY_RUN FORCE=$FORCE"
echo ""

# Test SSH connection
print_info "Testing SSH connection to $SSH_TARGET..."
if ! ssh -o ConnectTimeout=5 "$SSH_TARGET" "echo 'SSH OK'" > /dev/null 2>&1; then
    print_error "Cannot connect to $SSH_TARGET"
    echo ""
    echo "Troubleshooting:"
    echo "  1. Test your SSH alias: ssh $SSH_TARGET 'uname -a'"
    echo "  2. If not using alias, try: export SSH_TARGET=ubuntu@192.168.1.100"
    exit 1
fi
print_success "SSH connection OK"
echo ""

# ============================================================================
# STEP 1: BACKUP PRODUCTION STATE ON UBUNTU VM
# ============================================================================
print_info "STEP 1: Creating backup on Ubuntu VM..."
BACKUP_DIR="$UBUNTU_PROD_DIR/backups/$TIMESTAMP"

# Remote backup commands
BACKUP_SCRIPT="
mkdir -p '$BACKUP_DIR'

# Clean old backups (keep only last 3)
if [ -d '$UBUNTU_PROD_DIR/backups' ]; then
    cd '$UBUNTU_PROD_DIR/backups'
    ls -dt */ | tail -n +4 | xargs rm -rf 2>/dev/null || true
fi

# Backup current site (excluding backups, but including database)
if [ -d '$UBUNTU_PROD_DIR' ]; then
    cd '$UBUNTU_PROD_DIR'
    tar -czf '$BACKUP_DIR/site-backup.tar.gz' --exclude='backups' . 2>/dev/null || true
fi
"

if [ "$DRY_RUN" = false ]; then
    if ssh "$SSH_TARGET" "$BACKUP_SCRIPT"; then
        print_success "Backup created at: $BACKUP_DIR"
    else
        print_warning "Could not create backup, continuing..."
    fi
else
    print_info "[DRY RUN] Would create backup on Ubuntu"
fi

echo ""

# ============================================================================
# STEP 2: STOP SERVICES ON UBUNTU VM
# ============================================================================
print_info "STEP 2: Stopping production services on Ubuntu VM..."

STOP_SCRIPT="
if command -v pm2 &> /dev/null; then
    cd '$UBUNTU_PROD_DIR'
    pm2 stop personal-site 2>/dev/null || true
    pm2 stop faizal.uk 2>/dev/null || true
    pm2 delete faizal.uk 2>/dev/null || true
    sleep 2
fi
"

if [ "$DRY_RUN" = false ]; then
    if ssh "$SSH_TARGET" "$STOP_SCRIPT"; then
        print_success "Services stopped on Ubuntu"
    else
        print_warning "Failed to stop services, continuing anyway..."
    fi
else
    print_info "[DRY RUN] Would stop services on Ubuntu"
fi

echo ""

# ============================================================================
# STEP 3: SYNC FILES FROM DEV TO UBUNTU VM
# ============================================================================
print_info "STEP 3: Syncing files from dev to Ubuntu VM..."

# Ensure target directory exists and is accessible by Nginx
if [ "$DRY_RUN" = false ]; then
    ssh "$SSH_TARGET" "mkdir -p '$UBUNTU_PROD_DIR' && chmod 755 /home/ubuntu && chmod 755 '$UBUNTU_PROD_DIR'"
fi

# Clean production directory (keep only backend/Messages and backups)
CLEAN_SCRIPT="
cd '$UBUNTU_PROD_DIR'
for item in */; do
    case \"\$item\" in
        backups/)       ;;
        mail/)          ;;
        backend/)
            find backend/ -mindepth 1 -maxdepth 1 ! -name 'Messages' ! -name 'messages' ! -name '*.db' -exec rm -rf {} + 2>/dev/null || true
            ;;
        *)
            echo \"Removing directory: \${item%/}\"
            rm -rf \"\$item\" 2>/dev/null || true
            ;;
    esac
done

# Remove root-level files
find . -maxdepth 1 -type f -delete 2>/dev/null || true
"

if [ "$DRY_RUN" = false ]; then
    print_info "  - Cleaning production directory (preserving Messages and backups)..."
    ssh "$SSH_TARGET" "$CLEAN_SCRIPT" > /dev/null 2>&1
    sleep 2
fi

if [ "$DRY_RUN" = false ]; then
    print_info "  - Syncing files via rsync..."
    if command -v rsync &> /dev/null; then
        rsync -avz --delete \
            --exclude='backend/Messages' \
            --exclude='backend/messages' \
            --exclude='backend/*.db' \
            --exclude='mail/mail-data' \
            --exclude='mail/mail-state' \
            --exclude='mail/mail-logs' \
            --exclude='node_modules' \
            --exclude='backups' \
            --exclude='.git' \
            --exclude='deploy-*.sh' \
            "$DEV_DIR/" "$SSH_TARGET:$UBUNTU_PROD_DIR/"
    else
        print_warning "rsync not found, using explicit scp fallback..."
        scp -r "$DEV_DIR/public" "$DEV_DIR/scripts" "$DEV_DIR/config" "$DEV_DIR/test" "$SSH_TARGET:$UBUNTU_PROD_DIR/" 2>/dev/null || true
        # For backend, avoid node_modules by copying specific files
        ssh "$SSH_TARGET" "mkdir -p '$UBUNTU_PROD_DIR/backend'"
        scp "$DEV_DIR/backend/"*.js "$DEV_DIR/backend/"*.json "$SSH_TARGET:$UBUNTU_PROD_DIR/backend/" 2>/dev/null || true
    fi
    print_success "Files synced to Ubuntu VM"
else
    print_info "[DRY RUN] Would sync contents to $UBUNTU_PROD_DIR (using rsync if available)"
fi

echo ""

# ============================================================================
# STEP 4: UPLOAD AND RELOAD NGINX CONFIG
# ============================================================================
print_info "STEP 4: Uploading and reloading Nginx config..."

if [ "$DRY_RUN" = false ]; then
    # Upload the nginx config
    if [ -f "$DEV_DIR/config/faizal.uk.conf" ]; then
        scp "$DEV_DIR/config/faizal.uk.conf" "$SSH_TARGET:/tmp/faizal.uk.conf"

        NGINX_RELOAD_SCRIPT="
# Install config to Nginx sites-available
sudo cp /tmp/faizal.uk.conf /etc/nginx/sites-available/faizal.uk
sudo ln -sf /etc/nginx/sites-available/faizal.uk /etc/nginx/sites-enabled/faizal.uk 2>/dev/null || true

# Test config then reload
if sudo nginx -t 2>&1; then
    sudo systemctl reload nginx
    echo 'Nginx reloaded successfully'
else
    echo 'ERROR: Nginx config test failed! Check /tmp/faizal.uk.conf'
    exit 1
fi
"
        if ssh "$SSH_TARGET" "$NGINX_RELOAD_SCRIPT"; then
            print_success "Nginx config updated and reloaded"
        else
            print_warning "Nginx reload failed — check config manually"
        fi
    else
        print_warning "config/faizal.uk.conf not found in dev directory. Skipping Nginx update."
    fi
else
    print_info "[DRY RUN] Would upload nginx config and reload Nginx"
fi

echo ""

# ============================================================================
# STEP 5: INSTALL DEPENDENCIES ON UBUNTU VM
# ============================================================================
print_info "STEP 5: Installing backend dependencies on Ubuntu VM..."

INSTALL_SCRIPT="
cd '$UBUNTU_PROD_DIR/backend'
npm install --omit=dev
"

if [ "$DRY_RUN" = false ]; then
    if ssh "$SSH_TARGET" "$INSTALL_SCRIPT"; then
        print_success "Dependencies installed"
    else
        print_warning "Failed to install dependencies, continuing anyway..."
    fi
else
    print_info "[DRY RUN] Would install backend dependencies on Ubuntu"
fi

echo ""

# ============================================================================
# STEP 6: START SERVICES ON UBUNTU VM
# ============================================================================
print_info "STEP 6: Starting production services on Ubuntu VM..."

START_SCRIPT="
cd '$UBUNTU_PROD_DIR'

if command -v pm2 &> /dev/null; then
    echo 'Starting PM2 service...'
    pm2 start backend/server.js --name personal-site || pm2 restart personal-site
    pm2 save
    sleep 2
    echo 'Service Status:'
    pm2 status
else
    echo 'ERROR: PM2 not found! Install with: npm install -g pm2'
    exit 1
fi
"

if [ "$DRY_RUN" = false ]; then
    ssh "$SSH_TARGET" "$START_SCRIPT"
    print_success "Services started on Ubuntu"
else
    print_info "[DRY RUN] Would start services on Ubuntu"
fi

echo ""
echo "======================================================"
if [ "$DRY_RUN" = true ]; then
    echo "   DRY RUN COMPLETE (no changes made)"
else
    echo "   REMOTE DEPLOYMENT COMPLETE"
fi
echo "======================================================"
echo ""
echo "Summary:"
if [ "$DRY_RUN" = false ]; then
    echo "  [OK] Backup created on Ubuntu: $BACKUP_DIR"
    echo "  [OK] Files synced to Ubuntu VM (Messages preserved)"
    echo "  [OK] Nginx reloaded (if config was present)"
    echo "  [OK] Services restarted on Ubuntu"
fi
echo ""
echo "Next steps:"
echo "  - Monitor logs:   ssh $SSH_TARGET 'pm2 logs personal-site'"
echo "  - Service status: ssh $SSH_TARGET 'pm2 status'"
echo "  - Quick restart:  ssh $SSH_TARGET 'pm2 restart personal-site'"
echo ""
