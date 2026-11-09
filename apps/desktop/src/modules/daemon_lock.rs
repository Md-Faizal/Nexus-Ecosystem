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
            if std::path::Path::new("/run").exists() {
                std::path::PathBuf::from("/run/nexus-fleet-daemon.lock")
            } else {
                std::path::PathBuf::from("/var/run/nexus-fleet-daemon.lock")
            }
        } else {
            let runtime_dir = std::env::var("XDG_RUNTIME_DIR")
                .map(std::path::PathBuf::from)
                .unwrap_or_else(|_| std::env::temp_dir());
            runtime_dir.join(format!("nexus-daemon-{}.lock", uid))
        };

        if let Some(parent) = lock_path.parent() {
            let _ = std::fs::create_dir_all(parent);
        }

        let file = OpenOptions::new()
            .read(true)
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

use std::time::{SystemTime, UNIX_EPOCH};
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct DaemonState {
    pub pid: u32,
    pub last_heartbeat: u64,
    pub agent_version: String,
}

pub fn write_daemon_state(pid: u32) {
    let now = SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0);
    let state = DaemonState {
        pid,
        last_heartbeat: now,
        agent_version: crate::AGENT_VERSION.to_string(),
    };
    if let Ok(json) = serde_json::to_string_pretty(&state) {
        let _ = std::fs::write(crate::config::AgentConfig::state_path(), json);
    }
}

pub fn touch_daemon_heartbeat() {
    let now = SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0);
    let path = crate::config::AgentConfig::state_path();
    if let Ok(content) = std::fs::read_to_string(&path) {
        if let Ok(mut state) = serde_json::from_str::<DaemonState>(&content) {
            state.last_heartbeat = now;
            if let Ok(json) = serde_json::to_string_pretty(&state) {
                let _ = std::fs::write(&path, json);
            }
            return;
        }
    }
    write_daemon_state(std::process::id());
}

pub fn remove_daemon_state() {
    let _ = std::fs::remove_file(crate::config::AgentConfig::state_path());
}

pub fn read_daemon_state() -> Option<DaemonState> {
    let path = crate::config::AgentConfig::state_path();
    if path.exists() {
        if let Ok(content) = std::fs::read_to_string(&path) {
            if let Ok(state) = serde_json::from_str::<DaemonState>(&content) {
                return Some(state);
            }
        }
    }
    None
}

/// Checks whether the daemon is actively running on this host
pub fn is_daemon_running() -> bool {
    // 0. Check daemon state file with freshness threshold (60 seconds)
    if let Some(state) = read_daemon_state() {
        let now = SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0);
        if now.saturating_sub(state.last_heartbeat) < 60 {
            let mut sys = sysinfo::System::new();
            sys.refresh_processes(sysinfo::ProcessesToUpdate::All, true);
            let target_pid = sysinfo::Pid::from_u32(state.pid);
            if sys.process(target_pid).is_some() {
                return true;
            }
        }
    }

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
        // 2. Direct systemd user query (with fallback XDG_RUNTIME_DIR)
        let uid = unsafe { libc::geteuid() };
        let user_run_dir = format!("/run/user/{}", uid);
        let mut cmd = std::process::Command::new("systemctl");
        cmd.args(["--user", "is-active", "--quiet", "nexus-agent.service"]);
        if std::env::var("XDG_RUNTIME_DIR").is_err() && std::path::Path::new(&user_run_dir).exists() {
            cmd.env("XDG_RUNTIME_DIR", &user_run_dir);
        }
        if let Ok(status) = cmd.status() {
            if status.success() {
                return true;
            }
        }
    }

    #[cfg(target_os = "macos")]
    {
        // Check if loaded and running in launchd
        if let Ok(out) = std::process::Command::new("launchctl")
            .args(["list", "uk.faizal.nexus-agent"])
            .output()
        {
            if out.status.success() {
                let text = String::from_utf8_lossy(&out.stdout);
                if text.lines().any(|l| {
                    let mut parts = l.split_whitespace();
                    if let Some(pid_str) = parts.next() {
                        pid_str.parse::<u32>().is_ok()
                    } else {
                        false
                    }
                }) {
                    return true;
                }
            }
        }
    }

    #[cfg(not(target_os = "windows"))]
    {
        // 3. Check flock lock files
        use std::fs::OpenOptions;
        use std::os::unix::io::AsRawFd;

        let uid = unsafe { libc::geteuid() };
        let lock_candidates = [
            std::path::PathBuf::from("/run/nexus-fleet-daemon.lock"),
            std::path::PathBuf::from("/var/run/nexus-fleet-daemon.lock"),
            std::path::PathBuf::from(format!("/run/user/{}/nexus-daemon-{}.lock", uid, uid)),
            std::env::temp_dir().join(format!("nexus-daemon-{}.lock", uid)),
        ];

        for lock_path in &lock_candidates {
            if lock_path.exists() {
                if let Ok(file) = OpenOptions::new().read(true).write(true).open(lock_path) {
                    let fd = file.as_raw_fd();
                    let ret = unsafe { libc::flock(fd, libc::LOCK_EX | libc::LOCK_NB) };
                    if ret != 0 {
                        // File is currently held by an active daemon instance
                        return true;
                    } else {
                        // Stale lock file, unlock immediately
                        unsafe { libc::flock(fd, libc::LOCK_UN) };
                    }
                }
            }
        }
    }

    // 4. Process inspection fallback: must have 'run' in its arguments
    let mut sys = sysinfo::System::new();
    sys.refresh_processes(sysinfo::ProcessesToUpdate::All, true);
    let current_pid = sysinfo::get_current_pid().ok();
    sys.processes().values().any(|p| {
        if Some(p.pid()) == current_pid {
            return false;
        }
        let name = p.name().to_string_lossy().to_lowercase();
        let is_nexus = name.contains("nexus-agent") || name == "nexus.exe" || name == "nexus" || name == "nx.exe" || name == "nx";
        if !is_nexus {
            return false;
        }
        p.cmd().iter().any(|arg| {
            let s = arg.to_string_lossy();
            s == "run" || s == "--run"
        })
    })
}
