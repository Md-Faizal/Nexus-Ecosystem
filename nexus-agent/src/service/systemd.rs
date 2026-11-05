use std::fs;
use std::path::PathBuf;
use std::process::Command;
use tracing::info;

pub struct SystemdInstaller;

impl SystemdInstaller {
    pub fn install(user_mode: bool) -> Result<(), Box<dyn std::error::Error>> {
        let current_exe = std::env::current_exe()?;
        let wanted_target = if user_mode { "default.target" } else { "multi-user.target" };
        let env_home = if user_mode {
            format!("Environment=\"HOME={}\"", std::env::var("HOME").unwrap_or_else(|_| "/root".into()))
        } else {
            "Environment=\"HOME=/root\"".to_string()
        };

        let unit_content = format!(
            r#"[Unit]
Description=Nexus Fleet Telemetry & Management Agent
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
ExecStart={} run
Restart=always
RestartSec=5s
LimitNOFILE=65536
{}
Environment="PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

[Install]
WantedBy={}
"#,
            current_exe.display(),
            env_home,
            wanted_target
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
        let enable_status = Command::new(systemctl[0]).args(&systemctl[1..]).args(["enable", "--now", "nexus-agent"]).status();
        
        match enable_status {
            Ok(s) if s.success() => {
                println!("\x1b[32m✓ Nexus Agent background service successfully enabled and started.\x1b[0m");
            }
            _ => {
                println!("\x1b[33m[!] Notice: systemctl enable returned non-zero. Attempting explicit start...\x1b[0m");
                let _ = Command::new(systemctl[0]).args(&systemctl[1..]).args(["start", "nexus-agent"]).status();
            }
        }

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
