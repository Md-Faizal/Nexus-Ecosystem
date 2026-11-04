use std::process::Command;
use tracing::info;

pub struct WindowsInstaller;

impl WindowsInstaller {
    pub fn install() -> Result<(), Box<dyn std::error::Error>> {
        let current_exe = std::env::current_exe()?;
        let exe_str = current_exe.to_string_lossy().to_string();

        info!("Registering Windows auto-start for: {}", exe_str);

        // 1. Register in Current User Run Key (Appears in Task Manager -> Startup Apps & runs in desktop session for taskbar tray icon)
        let _ = Command::new("reg.exe")
            .args([
                "add",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                "/v",
                "NexusAgent",
                "/t",
                "REG_SZ",
                "/d",
                &format!("\"{}\" run", exe_str),
                "/f",
            ])
            .output();

        // 2. Register as Scheduled Task with HIGHEST privileges so it runs reliably on logon
        let _sch_create = Command::new("schtasks")
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
            .output();

        // 3. Launch background worker immediately
        let _ = Command::new(&current_exe).arg("run").spawn();
        info!("Registered in Windows Startup and started Nexus Agent background worker with Taskbar Tray Icon");
        Ok(())
    }

    pub fn uninstall() -> Result<(), Box<dyn std::error::Error>> {
        let _ = Command::new("reg.exe")
            .args([
                "delete",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                "/v",
                "NexusAgent",
                "/f",
            ])
            .output();
        let _ = Command::new("schtasks").args(["/delete", "/tn", "NexusAgent", "/f"]).output();
        let _ = Command::new("taskkill.exe").args(["/F", "/IM", "nexus-agent.exe"]).output();
        info!("Nexus Agent Windows Startup Registry and Task removed");
        Ok(())
    }
}

