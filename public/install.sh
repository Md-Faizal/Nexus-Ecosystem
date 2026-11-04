#!/usr/bin/env bash
# Nexus Fleet Agent — Universal Zero-Token 1-Command Installer (Linux & macOS)
set -euo pipefail

HUB_URL="${NEXUS_HUB:-https://faizal.uk}"
INSTALL_DIR="/usr/local/bin"

echo "=== Installing Nexus Fleet Agent ==="
OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
ARCH="$(uname -m)"

case "$ARCH" in
  x86_64) ARCH_TAG="amd64" ;;
  aarch64|arm64) ARCH_TAG="arm64" ;;
  *) echo "Unsupported architecture: $ARCH"; exit 1 ;;
esac

BIN_URL="$HUB_URL/releases/nexus-agent-${OS}-${ARCH_TAG}?v=$(date +%s)"
echo "Downloading Nexus Agent from $BIN_URL..."

if [ "$(id -u)" -eq 0 ]; then
  curl -fsSL "$BIN_URL" -o "$INSTALL_DIR/nexus-agent"
  chmod +x "$INSTALL_DIR/nexus-agent"
  nexus-agent service install
  echo "Nexus Agent successfully installed and running as a system service!"
else
  mkdir -p "$HOME/.local/bin"
  curl -fsSL "$BIN_URL" -o "$HOME/.local/bin/nexus-agent"
  chmod +x "$HOME/.local/bin/nexus-agent"
  "$HOME/.local/bin/nexus-agent" service install --user
  echo "Nexus Agent successfully installed and running for user $USER!"
fi

echo "The node will automatically appear on your Personal Site Admin Panel within 5 seconds."

