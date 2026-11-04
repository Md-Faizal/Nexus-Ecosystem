use std::fs;
use std::path::PathBuf;
use std::process::Command;
use tracing::{info, warn};

pub struct SystemdInstaller;

impl SystemdInstaller {
    pub fn install(user_mode: bool) -> Result<(), Box<dyn std::error::Error>> {
        let current_exe = std::env::current_exe()?;
        let unit_content = format!(
            r#"[Unit]
Description=Nexus Fleet Telemetry & Management Agent
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
ExecStart={} run
Restart=always
RestartSec=3s
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
"#,
            current_exe.display()
        );

        let unit_path = if user_mode {
            let user_dir = PathBuf::from(std::env::var("HOME")?).join(".config/systemd/user");
            fs::create_dir_all(&user_dir)?;
            user_dir.join("nexus-agent.service")
        } else {
            PathBuf::from("/etc/systemd/system/nexus-agent.service")
        };

        fs::write(&unit_path, unit_content)?;
        info!("Wrote systemd unit file to {:?}", unit_path);

        let systemctl = if user_mode {
            vec!["systemctl", "--user"]
        } else {
            vec!["systemctl"]
        };

        let _ = Command::new(systemctl[0]).args(&systemctl[1..]).arg("daemon-reload").status();
        let _ = Command::new(systemctl[0]).args(&systemctl[1..]).args(["enable", "nexus-agent"]).status();
        let _ = Command::new(systemctl[0]).args(&systemctl[1..]).args(["start", "nexus-agent"]).status();

        info!("Nexus Agent service installed and started via systemd");
        Ok(())
    }

    pub fn uninstall(user_mode: bool) -> Result<(), Box<dyn std::error::Error>> {
        let systemctl = if user_mode {
            vec!["systemctl", "--user"]
        } else {
            vec!["systemctl"]
        };

        let _ = Command::new(systemctl[0]).args(&systemctl[1..]).args(["stop", "nexus-agent"]).status();
        let _ = Command::new(systemctl[0]).args(&systemctl[1..]).args(["disable", "nexus-agent"]).status();

        let unit_path = if user_mode {
            PathBuf::from(std::env::var("HOME")?).join(".config/systemd/user/nexus-agent.service")
        } else {
            PathBuf::from("/etc/systemd/system/nexus-agent.service")
        };

        if unit_path.exists() {
            let _ = fs::remove_file(unit_path);
        }

        let _ = Command::new(systemctl[0]).args(&systemctl[1..]).arg("daemon-reload").status();
        info!("Nexus Agent service uninstalled");
        Ok(())
    }
}

