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
/// Returns Ok(guard) if this is the only active daemon instance.
/// Returns Err(msg) if a daemon instance is already active and running.
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

        let lock_path = std::env::temp_dir().join("nexus-fleet-daemon.lock");
        let file = OpenOptions::new()
            .write(true)
            .create(true)
            .mode(0o644)
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

    // Secondary process inspection
    let mut sys = sysinfo::System::new();
    sys.refresh_processes(sysinfo::ProcessesToUpdate::All, true);
    let current_pid = sysinfo::get_current_pid().ok();
    sys.processes().values().any(|p| {
        let name = p.name().to_string_lossy().to_lowercase();
        (name.contains("nexus-agent") || name == "nexus.exe" || name == "nexus")
            && Some(p.pid()) != current_pid
    })
}
