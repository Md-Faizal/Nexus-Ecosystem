#[cfg(target_os = "windows")]
use windows_sys::Win32::Foundation::{CloseHandle, GetLastError, ERROR_ALREADY_EXISTS, HANDLE};
#[cfg(target_os = "windows")]
use windows_sys::Win32::System::Threading::{CreateMutexW, OpenMutexW};

#[cfg(target_os = "windows")]
const SYNCHRONIZE_ACCESS: u32 = 0x00100000;

pub struct DaemonLockGuard {
    #[cfg(target_os = "windows")]
    handle: HANDLE,
    #[cfg(not(target_os = "windows"))]
    _file: Option<std::fs::File>,
}

#[cfg(target_os = "windows")]
impl Drop for DaemonLockGuard {
    fn drop(&mut self) {
        if self.handle != std::ptr::null_mut() {
            unsafe {
                CloseHandle(self.handle);
            }
        }
    }
}

#[cfg(not(target_os = "windows"))]
impl Drop for DaemonLockGuard {
    fn drop(&mut self) {
        // Automatically released on file descriptor close
    }
}

/// Attempts to acquire an exclusive single-instance lock for the background daemon.
pub fn try_acquire_daemon_lock() -> Result<DaemonLockGuard, String> {
    #[cfg(target_os = "windows")]
    {
        let wide_name: Vec<u16> = "Local\\NexusFleetAgentDaemonMutex\0"
            .encode_utf16()
            .collect();

        unsafe {
            let handle = CreateMutexW(std::ptr::null(), 1, wide_name.as_ptr());
            if handle == std::ptr::null_mut() {
                return Err("Failed to query operating system synchronization primitive.".into());
            }

            if GetLastError() == ERROR_ALREADY_EXISTS {
                CloseHandle(handle);
                return Err("Another Nexus Fleet Daemon instance is already active.".into());
            }

            Ok(DaemonLockGuard { handle })
        }
    }

    #[cfg(not(target_os = "windows"))]
    {
        use std::fs::OpenOptions;
        use std::os::unix::fs::OpenOptionsExt;
        use std::os::unix::io::AsRawFd;

        let uid = unsafe { libc::geteuid() };
        let lock_path = if uid == 0 {
            std::path::PathBuf::from("/run/nexus-fleet-daemon.lock")
        } else {
            let runtime_dir = std::env::var("XDG_RUNTIME_DIR")
                .map(std::path::PathBuf::from)
                .unwrap_or_else(|_| std::env::temp_dir());
            runtime_dir.join(format!("nexus-daemon-{}.lock", uid))
        };

        let file = OpenOptions::new()
            .write(true)
            .create(true)
            .mode(0o600)
            .open(&lock_path)
            .map_err(|e| format!("Could not open lock file {}: {}", lock_path.display(), e))?;

        let fd = file.as_raw_fd();
        let ret = unsafe { libc::flock(fd, libc::LOCK_EX | libc::LOCK_NB) };
        if ret != 0 {
            return Err("Another Nexus Fleet Daemon instance is already active on this host.".into());
        }

        Ok(DaemonLockGuard { _file: Some(file) })
    }
}

/// Checks whether the daemon is actively running on this host
pub fn is_daemon_running() -> bool {
    #[cfg(target_os = "windows")]
    {
        let wide_name: Vec<u16> = "Local\\NexusFleetAgentDaemonMutex\0"
            .encode_utf16()
            .collect();
        unsafe {
            let handle = OpenMutexW(SYNCHRONIZE_ACCESS, 0, wide_name.as_ptr());
            if handle != std::ptr::null_mut() {
                CloseHandle(handle);
                return true;
            }
        }
    }

    #[cfg(target_os = "linux")]
    {
        // 1. Direct systemd system query
        if let Ok(status) = std::process::Command::new("systemctl")
            .args(["is-active", "--quiet", "nexus-agent.service"])
            .status()
        {
            if status.success() {
                return true;
            }
        }
        // 2. Direct systemd user query
        if let Ok(status) = std::process::Command::new("systemctl")
            .args(["--user", "is-active", "--quiet", "nexus-agent.service"])
            .status()
        {
            if status.success() {
                return true;
            }
        }
    }

    // 3. Process inspection: must have 'run' in its arguments
    let mut sys = sysinfo::System::new();
    sys.refresh_processes(sysinfo::ProcessesToUpdate::All, true);
    let current_pid = sysinfo::get_current_pid().ok();
    sys.processes().values().any(|p| {
        if Some(p.pid()) == current_pid {
            return false;
        }
        let name = p.name().to_string_lossy().to_lowercase();
        let is_nexus = name.contains("nexus-agent") || name == "nexus.exe" || name == "nexus";
        if !is_nexus {
            return false;
        }
        p.cmd().iter().any(|arg| {
            let s = arg.to_string_lossy();
            s == "run" || s == "--run"
        })
    })
}
