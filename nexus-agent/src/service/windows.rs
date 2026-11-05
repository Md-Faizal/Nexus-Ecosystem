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
                &format!("\"{}\" run --foreground", exe_str),
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
                &format!("\"{}\" run --foreground", exe_str),
                "/rl",
                "HIGHEST",
                "/f",
            ])
            .output();

        // 3. Launch background worker immediately with detached process flags
        use std::os::windows::process::CommandExt;
        const DETACHED_PROCESS: u32 = 0x00000008;
        const CREATE_NEW_PROCESS_GROUP: u32 = 0x00000200;
        const CREATE_NO_WINDOW: u32 = 0x08000000;

        let _ = Command::new(&current_exe)
            .args(["run", "--foreground"])
            .creation_flags(DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP | CREATE_NO_WINDOW)
            .stdin(std::process::Stdio::null())
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .spawn();
        info!("Registered in Windows Startup and started detached Nexus Agent daemon with Taskbar Tray Icon");
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

