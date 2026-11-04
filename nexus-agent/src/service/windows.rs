use std::process::Command;
use tracing::info;

pub struct WindowsInstaller;

impl WindowsInstaller {
    pub fn install() -> Result<(), Box<dyn std::error::Error>> {
        let current_exe = std::env::current_exe()?;
        let exe_str = current_exe.to_string_lossy().to_string();

        info!("Registering Windows auto-start for: {}", exe_str);

        // 1. Attempt registering as a Windows Service via sc.exe
        let sc_create = Command::new("sc.exe")
            .args([
                "create",
                "NexusAgent",
                &format!("binPath= \"{}\" run", exe_str),
                "start=",
                "auto",
                "DisplayName=",
                "Nexus Fleet Telemetry & Management Agent",
            ])
            .output();

        if let Ok(res) = sc_create {
            if res.status.success() {
                // Set automatic recovery on crash (restart after 2000ms)
                let _ = Command::new("sc.exe")
                    .args(["failure", "NexusAgent", "reset=", "0", "actions=", "restart/2000"])
                    .output();

                let _ = Command::new("sc.exe").args(["start", "NexusAgent"]).output();
                info!("Registered and started as native Windows Service 'NexusAgent'");
                return Ok(());
            }
        }

        // 2. Fallback to Scheduled Task (runs on user logon with highest privileges)
        let sch_create = Command::new("schtasks")
            .args([
                "/create",
                "/sc",
                "onlogon",
                "/tn",
                "NexusAgent",
                "/tr",
                &format!("\"{}\" run", exe_str),
                "/rl",
                "HIGHEST",
                "/f",
            ])
            .output()?;

        if sch_create.status.success() {
            let _ = Command::new("schtasks").args(["/run", "/tn", "NexusAgent"]).output();
            info!("Registered and started as Scheduled Task 'NexusAgent'");
            return Ok(());
        }

        Err("Failed to register Windows Service or Scheduled Task".into())
    }

    pub fn uninstall() -> Result<(), Box<dyn std::error::Error>> {
        let _ = Command::new("sc.exe").args(["stop", "NexusAgent"]).output();
        let _ = Command::new("sc.exe").args(["delete", "NexusAgent"]).output();
        let _ = Command::new("schtasks").args(["/delete", "/tn", "NexusAgent", "/f"]).output();
        info!("Nexus Agent Windows Service and Task removed");
        Ok(())
    }
}

