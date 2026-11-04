use std::fs;
use std::path::PathBuf;
use std::process::Command;
use tracing::info;

pub struct LaunchdInstaller;

impl LaunchdInstaller {
    pub fn install(user_mode: bool) -> Result<(), Box<dyn std::error::Error>> {
        let current_exe = std::env::current_exe()?;
        let plist_content = format!(
            r#"<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>uk.faizal.nexus-agent</string>
    <key>ProgramArguments</key>
    <array>
        <string>{}</string>
        <string>run</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>StandardErrorPath</key>
    <string>/tmp/nexus-agent.err</string>
    <key>StandardOutPath</key>
    <string>/tmp/nexus-agent.out</string>
</dict>
</plist>
"#,
            current_exe.display()
        );

        let plist_path = if user_mode {
            let dir = PathBuf::from(std::env::var("HOME")?).join("Library/LaunchAgents");
            fs::create_dir_all(&dir)?;
            dir.join("uk.faizal.nexus-agent.plist")
        } else {
            let dir = PathBuf::from("/Library/LaunchDaemons");
            fs::create_dir_all(&dir)?;
            dir.join("uk.faizal.nexus-agent.plist")
        };

        fs::write(&plist_path, plist_content)?;
        let _ = Command::new("launchctl").arg("load").arg("-w").arg(&plist_path).status();

        info!("Installed and loaded macOS launchd daemon at {:?}", plist_path);
        Ok(())
    }

    pub fn uninstall(user_mode: bool) -> Result<(), Box<dyn std::error::Error>> {
        let plist_path = if user_mode {
            PathBuf::from(std::env::var("HOME")?).join("Library/LaunchAgents/uk.faizal.nexus-agent.plist")
        } else {
            PathBuf::from("/Library/LaunchDaemons/uk.faizal.nexus-agent.plist")
        };

        if plist_path.exists() {
            let _ = Command::new("launchctl").arg("unload").arg(&plist_path).status();
            let _ = fs::remove_file(plist_path);
        }

        info!("Uninstalled macOS launchd daemon");
        Ok(())
    }
}

