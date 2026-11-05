#!/usr/bin/env bash
# Nexus Fleet Agent — Universal Zero-Token 1-Command Installer (Linux & macOS)
set -euo pipefail

HUB_URL="${NEXUS_HUB:-https://faizal.uk}"
OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
ARCH="$(uname -m)"

case "$ARCH" in
  x86_64) ARCH_TAG="amd64" ;;
  aarch64|arm64) ARCH_TAG="arm64" ;;
  *) echo "Unsupported architecture: $ARCH"; exit 1 ;;
esac

BIN_URL="$HUB_URL/releases/nexus-agent-${OS}-${ARCH_TAG}?v=$(date +%s)"
echo "=== Installing Nexus Fleet Agent ($OS-$ARCH_TAG) ==="
echo "Downloading binary from $BIN_URL..."

# Stop any running instances first to release handles
if command -v systemctl >/dev/null 2>&1; then
  systemctl stop nexus-agent 2>/dev/null || true
  systemctl --user stop nexus-agent 2>/dev/null || true
fi
pkill -f "nexus-agent" 2>/dev/null || true
pkill -f "nexus run" 2>/dev/null || true

# Download to a temporary file first (avoids ETXTBSY 'Text file busy' / curl error 23 on running binaries)
TMP_BIN="$(mktemp /tmp/nexus-agent.XXXXXX)"
curl -fsSL "$BIN_URL" -o "$TMP_BIN"
chmod +x "$TMP_BIN"

if [ "$(id -u)" -eq 0 ]; then
  INSTALL_DIR="/usr/local/bin"
  mkdir -p "$INSTALL_DIR"
  mv -f "$TMP_BIN" "$INSTALL_DIR/nexus-agent"
  chmod +x "$INSTALL_DIR/nexus-agent"
  ln -sf "$INSTALL_DIR/nexus-agent" "$INSTALL_DIR/nexus"
  ln -sf "$INSTALL_DIR/nexus-agent" "$INSTALL_DIR/nx"
  "$INSTALL_DIR/nexus" service install
  echo "Nexus Agent successfully installed and running as a system-wide service!"
else
  INSTALL_DIR="$HOME/.local/bin"
  mkdir -p "$INSTALL_DIR"
  mv -f "$TMP_BIN" "$INSTALL_DIR/nexus-agent"
  chmod +x "$INSTALL_DIR/nexus-agent"
  ln -sf "$INSTALL_DIR/nexus-agent" "$INSTALL_DIR/nexus"
  ln -sf "$INSTALL_DIR/nexus-agent" "$INSTALL_DIR/nx"
  
  # Ensure linger is enabled so user service runs headlessly even after logout
  loginctl enable-linger "$USER" 2>/dev/null || true
  
  "$INSTALL_DIR/nexus" service install --user
  echo "Nexus Agent successfully installed and running in User Mode for $USER!"
  echo "Tip: Run 'nexus elevate' or 'sudo nexus service install' anytime to upgrade to a system service."
fi

echo "Telemetry is actively streaming to $HUB_URL."
echo "You can now run 'nexus' or 'nx' directly in your terminal:"
echo "  nexus run       - Start background telemetry daemon (if stopped)"
echo "  nexus status    - View live node health and telemetry"
echo "  nexus cluster   - Inspect 3-VM cluster (my-mac, cos, pro)"
echo "  nexus services  - View running containers, web engines, and games"
echo "  nexus chat      - Start interactive AI chat & slash console"
echo "Your node will automatically appear on https://faizal.uk/admin.html immediately."
