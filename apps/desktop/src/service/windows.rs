use std::process::Command;
use tracing::info;

pub struct WindowsInstaller;

impl WindowsInstaller {
    pub fn install() -> Result<(), Box<dyn std::error::Error>> {
        use std::os::windows::process::CommandExt;
        const CREATE_NEW_PROCESS_GROUP: u32 = 0x00000200;
        const CREATE_NO_WINDOW: u32 = 0x08000000;
        const CREATE_BREAKAWAY_FROM_JOB: u32 = 0x01000000;

        let current_exe = std::env::current_exe()?;
        let exe_str = current_exe.to_string_lossy().to_string();

        info!("Registering Windows auto-start for: {}", exe_str);

        // 1. Remove any legacy elevated Scheduled Task so it does not open an elevated console window on logon
        let _ = Command::new("schtasks")
            .args(["/delete", "/tn", "NexusAgent", "/f"])
            .creation_flags(CREATE_NO_WINDOW)
            .output();

        // 2. Register in Current User Run Key using `run --silent` so it detaches immediately into the background
        //    with no console window and runs in the user's desktop session for the taskbar tray icon.
        let _ = Command::new("reg.exe")
            .args([
                "add",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                "/v",
                "NexusAgent",
                "/t",
                "REG_SZ",
                "/d",
                &format!("\"{}\" run --silent", exe_str),
                "/f",
            ])
            .creation_flags(CREATE_NO_WINDOW)
            .output();

        // 3. Launch background worker immediately with detached process flags if not already running
        if !crate::modules::daemon_lock::is_daemon_running() {
            let _ = Command::new(&current_exe)
                .args(["run", "--foreground"])
                .creation_flags(CREATE_NEW_PROCESS_GROUP | CREATE_NO_WINDOW | CREATE_BREAKAWAY_FROM_JOB)
                .stdin(std::process::Stdio::null())
                .stdout(std::process::Stdio::null())
                .stderr(std::process::Stdio::null())
                .spawn()
                .or_else(|_| {
                    Command::new(&current_exe)
                        .args(["run", "--foreground"])
                        .creation_flags(CREATE_NEW_PROCESS_GROUP | CREATE_NO_WINDOW)
                        .stdin(std::process::Stdio::null())
                        .stdout(std::process::Stdio::null())
                        .stderr(std::process::Stdio::null())
                        .spawn()
                });
        }
        info!("Registered in Windows Startup and started detached Nexus Agent daemon with Taskbar Tray Icon");
        Ok(())
    }

    pub fn start() -> Result<(), Box<dyn std::error::Error>> {
        let current_exe = std::env::current_exe()?;
        use std::os::windows::process::CommandExt;
        const CREATE_NEW_PROCESS_GROUP: u32 = 0x00000200;
        const CREATE_NO_WINDOW: u32 = 0x08000000;
        const CREATE_BREAKAWAY_FROM_JOB: u32 = 0x01000000;

        let _ = Command::new(&current_exe)
            .args(["run", "--foreground", "--wait-lock"])
            .creation_flags(CREATE_NEW_PROCESS_GROUP | CREATE_NO_WINDOW | CREATE_BREAKAWAY_FROM_JOB)
            .stdin(std::process::Stdio::null())
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .spawn()
            .or_else(|_| {
                Command::new(&current_exe)
                    .args(["run", "--foreground", "--wait-lock"])
                    .creation_flags(CREATE_NEW_PROCESS_GROUP | CREATE_NO_WINDOW)
                    .stdin(std::process::Stdio::null())
                    .stdout(std::process::Stdio::null())
                    .stderr(std::process::Stdio::null())
                    .spawn()
            })?;
        println!("\x1b[32m✓ Nexus Agent background service started.\x1b[0m");
        Ok(())
    }

    fn kill_daemon_processes_only() {
        use std::collections::BTreeSet;
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x08000000;

        let self_pid = std::process::id();
        let mut target_pids = BTreeSet::new();

        if let Some(state) = crate::modules::daemon_lock::read_daemon_state() {
            if state.pid != self_pid {
                target_pids.insert(state.pid);
            }
        }

        let mut sys = sysinfo::System::new();
        sys.refresh_processes(sysinfo::ProcessesToUpdate::All, true);
        for (pid, proc_) in sys.processes() {
            let pid_u32 = pid.as_u32();
            if pid_u32 == self_pid {
                continue;
            }
            let name = proc_.name().to_string_lossy().to_ascii_lowercase();
            let is_nexus_bin = matches!(
                name.as_str(),
                "nexus.exe" | "nexus-agent.exe" | "nx.exe"
            );
            if is_nexus_bin {
                let has_run_arg = proc_
                    .cmd()
                    .iter()
                    .any(|arg| arg.to_string_lossy() == "run");
                if has_run_arg {
                    target_pids.insert(pid_u32);
                }
            }
        }

        for pid in target_pids {
            let _ = Command::new("taskkill.exe")
                .args(["/F", "/PID", &pid.to_string()])
                .creation_flags(CREATE_NO_WINDOW)
                .output();
        }
        crate::modules::daemon_lock::remove_daemon_state();
    }

    pub fn stop() -> Result<(), Box<dyn std::error::Error>> {
        Self::kill_daemon_processes_only();
        println!("\x1b[32m✓ Nexus Agent background service stopped.\x1b[0m");
        Ok(())
    }

    pub fn restart() -> Result<(), Box<dyn std::error::Error>> {
        Self::kill_daemon_processes_only();
        std::thread::sleep(std::time::Duration::from_millis(600));
        Self::start()
    }

    pub fn status() -> Result<(), Box<dyn std::error::Error>> {
        let is_running = crate::modules::daemon_lock::is_daemon_running();
        if is_running {
            println!("\x1b[32m● Nexus Agent Service is RUNNING in background.\x1b[0m");
        } else {
            println!("\x1b[31m○ Nexus Agent Service is STOPPED.\x1b[0m");
            println!("\x1b[90mRun 'nexus run' or 'nexus service start' to start it.\x1b[0m");
        }
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
        let _ = Command::new("taskkill.exe").args(["/F", "/IM", "nexus.exe"]).output();
        info!("Nexus Agent Windows Startup Registry and Task removed");
        Ok(())
    }
}

