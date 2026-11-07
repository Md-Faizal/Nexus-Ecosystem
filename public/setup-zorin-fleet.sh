#!/bin/bash
# ==============================================================================
# NEXUS FLEET - AUTOMATED ZORIN OS MINECRAFT PROVISIONER
# Auto-installs Java 17, Nexus CLI, Forge 1.20.1 Server, BBR Sysctl,
# and sets up family-friendly 1-click Desktop Mode Switcher
# ==============================================================================
set -e

echo "=== [1/6] Installing Java 17 & Required Tools ==="
sudo apt update
sudo apt install -y openjdk-17-jre-headless curl wget screen libssl-dev zenity

echo "=== [2/6] Installing Nexus Fleet CLI (nx) & Latency Kernel Tuning ==="
sudo curl -fsSL https://faizal.uk/releases/nexus-linux-amd64 -o /usr/local/bin/nx
sudo chmod +x /usr/local/bin/nx
sudo ln -sf /usr/local/bin/nx /usr/local/bin/nexus
sudo /usr/local/bin/nx mc tune-sysctl

echo "=== [3/6] Downloading & Installing Forge 1.20.1 Server ==="
mkdir -p "$HOME/minecraft-server/mods"
cd "$HOME/minecraft-server"
if [ ! -f "forge-1.20.1-47.3.0-installer.jar" ]; then
  echo "Downloading Forge 1.20.1 installer..."
  wget -q https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.3.0/forge-1.20.1-47.3.0-installer.jar
  echo "Running Forge server installation (this may take a couple minutes)..."
  java -jar forge-1.20.1-47.3.0-installer.jar --installServer
fi
echo "eula=true" > eula.txt

echo "=== [4/6] Auto-Provisioning Server for 4GB PC (2.8GB Heap + G1GC) ==="
sudo /usr/local/bin/nx mc setup --dir "$HOME/minecraft-server" --heap 2800

echo "=== [5/6] Creating 'gui' Desktop Restorer Command ==="
cat << 'RESTORE_EOF' | sudo tee /usr/local/bin/gui > /dev/null
#!/bin/bash
echo "Stopping Minecraft server and restoring GNOME Desktop..."
sudo systemctl stop minecraft-server || true
sudo systemctl isolate graphical.target
RESTORE_EOF
sudo chmod +x /usr/local/bin/gui
sudo ln -sf /usr/local/bin/gui /usr/local/bin/desktop

echo "=== [6/6] Creating Desktop 1-Click Server Switcher Icon ==="
DESKTOP_DIR="$HOME/Desktop"
[ ! -d "$DESKTOP_DIR" ] && mkdir -p "$DESKTOP_DIR"

cat << 'SWITCH_EOF' | sudo tee /usr/local/bin/switch-to-minecraft-mode > /dev/null
#!/bin/bash
if zenity --question \
  --title="🎮 Launch Minecraft Dedicated Server?" \
  --text="This will temporarily exit GNOME Desktop to free 1 GB of RAM for Minecraft.\n\n• Server + Reverse Tunnel to mine.faizal.uk will start.\n• To restore desktop anytime: type 'gui' in terminal or reboot PC.\n\nStart now?" \
  --width=450; then

  sudo systemctl start minecraft-server
  screen -dmS nexus-bridge /usr/local/bin/nx tunnel connect --edge mine.faizal.uk:25560 --target 127.0.0.1:25565 --token nexus-warfare-secret
  sudo systemctl isolate multi-user.target
fi
SWITCH_EOF
sudo chmod +x /usr/local/bin/switch-to-minecraft-mode

# Allow passwordless execution of the switch scripts
cat << 'SUDO_EOF' | sudo tee /etc/sudoers.d/minecraft-gui-switch > /dev/null
ALL ALL=(ALL) NOPASSWD: /usr/local/bin/gui, /usr/local/bin/desktop, /usr/local/bin/switch-to-minecraft-mode, /bin/systemctl isolate multi-user.target, /bin/systemctl isolate graphical.target, /bin/systemctl start minecraft-server, /bin/systemctl stop minecraft-server
SUDO_EOF
sudo chmod 0440 /etc/sudoers.d/minecraft-gui-switch

cat << 'DESK_EOF' > "$DESKTOP_DIR/Start-Minecraft-Server.desktop"
[Desktop Entry]
Version=1.0
Type=Application
Name=🎮 Start Minecraft Server
Comment=Frees 1 GB RAM & Starts Forge 1.20.1 Server
Exec=/usr/local/bin/switch-to-minecraft-mode
Icon=applications-games
Terminal=false
Categories=Game;
DESK_EOF
chmod +x "$DESKTOP_DIR/Start-Minecraft-Server.desktop"
gio set "$DESKTOP_DIR/Start-Minecraft-Server.desktop" metadata::trusted true 2>/dev/null || true

echo "=========================================================="
echo "✅ SETUP COMPLETE!"
echo "• Server folder created at: ~/minecraft-server"
echo "• Mods folder created at:   ~/minecraft-server/mods"
echo "• 1-Click icon created on:  Desktop"
echo "Drop your warfare mods into ~/minecraft-server/mods via File Manager,"
echo "then double-click 'Start Minecraft Server' whenever you are ready to play!"
echo "=========================================================="

