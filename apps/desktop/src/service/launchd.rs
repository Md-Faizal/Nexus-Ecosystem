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
        <string>--foreground</string>
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
        println!("\x1b[32m✓ Nexus Agent macOS launchd daemon installed and loaded.\x1b[0m");
        Ok(())
    }

    pub fn start(_user_mode: bool) -> Result<(), Box<dyn std::error::Error>> {
        let _ = Command::new("launchctl").args(["start", "uk.faizal.nexus-agent"]).status();
        println!("\x1b[32m✓ Nexus Agent launchd service started.\x1b[0m");
        Ok(())
    }

    pub fn stop(_user_mode: bool) -> Result<(), Box<dyn std::error::Error>> {
        let _ = Command::new("launchctl").args(["stop", "uk.faizal.nexus-agent"]).status();
        println!("\x1b[32m✓ Nexus Agent launchd service stopped.\x1b[0m");
        Ok(())
    }

    pub fn restart(user_mode: bool) -> Result<(), Box<dyn std::error::Error>> {
        let _ = Self::stop(user_mode);
        std::thread::sleep(std::time::Duration::from_millis(300));
        Self::start(user_mode)
    }

    pub fn status(_user_mode: bool) -> Result<(), Box<dyn std::error::Error>> {
        let output = Command::new("launchctl").args(["list", "uk.faizal.nexus-agent"]).output();
        match output {
            Ok(out) if out.status.success() => {
                let text = String::from_utf8_lossy(&out.stdout);
                println!("\x1b[1;36m=== macOS launchd status for uk.faizal.nexus-agent ===\x1b[0m");
                println!("{}", text);
            }
            _ => {
                println!("\x1b[33m○ Service uk.faizal.nexus-agent is not currently registered in launchd.\x1b[0m");
            }
        }
        Ok(())
    }

    pub fn uninstall(user_mode: bool) -> Result<(), Box<dyn std::error::Error>> {
        let plist_path = if user_mode {
            PathBuf::from(std::env::var("HOME")?).join("Library/LaunchAgents/uk.faizal.nexus-agent.plist")
        } else {
            PathBuf::from("/Library/LaunchDaemons/uk.faizal.nexus-agent.plist")
        };

        if plist_path.exists() {
            let _ = Command::new("launchctl").arg("unload").arg("-w").arg(&plist_path).status();
            let _ = fs::remove_file(plist_path);
        }

        info!("Uninstalled macOS launchd daemon");
        println!("\x1b[32m✓ Nexus Agent macOS launchd daemon uninstalled.\x1b[0m");
        Ok(())
    }
}
