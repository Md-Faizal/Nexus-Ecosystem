use chrono::Utc;
use serde::{Deserialize, Serialize};
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::OnceLock;
use std::time::{SystemTime, UNIX_EPOCH};
use uuid::Uuid;

const MAX_BACKTRACE_CHARS: usize = 8192;
const MAX_SENT_RECORDS: usize = 50;
const CRASH_LOOP_WINDOW_SECS: u64 = 120;
const CRASH_LOOP_MAX_RESTARTS: usize = 5;

static CRASH_COMPONENT: OnceLock<String> = OnceLock::new();
static AUTO_RECOVER_DAEMON: AtomicBool = AtomicBool::new(false);
static HANDLER_ACTIVE: AtomicBool = AtomicBool::new(false);

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CrashRecord {
    pub crash_id: String,
    pub node_id: String,
    pub hostname: String,
    pub agent_version: String,
    pub os: String,
    pub arch: String,
    pub component: String,
    pub kind: String,
    pub message: String,
    pub location: String,
    pub backtrace: String,
    pub occurred_at: String,
    pub recovered: bool,
    pub restart_attempt: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
struct RestartGuard {
    recent_restarts_epoch: Vec<u64>,
}

/// Returns the writable crash directory, preferring the agent config dir with a LOCALAPPDATA/temp fallback.
pub fn crash_root_dir() -> PathBuf {
    let cfg = crate::config::AgentConfig::config_path();
    if let Some(parent) = cfg.parent() {
        let candidate = parent.join("crashes");
        if ensure_writable_dir(&candidate) {
            return candidate;
        }
    }

    #[cfg(target_os = "windows")]
    if let Ok(local) = std::env::var("LOCALAPPDATA") {
        let candidate = PathBuf::from(local).join("NexusAgent").join("crashes");
        if ensure_writable_dir(&candidate) {
            return candidate;
        }
    }

    let fallback = std::env::temp_dir().join("nexus-agent-crashes");
    let _ = fs::create_dir_all(&fallback);
    fallback
}

fn ensure_writable_dir(dir: &Path) -> bool {
    if fs::create_dir_all(dir).is_err() {
        return false;
    }
    let probe = dir.join(".write_probe");
    if fs::write(&probe, b"").is_ok() {
        let _ = fs::remove_file(&probe);
        true
    } else {
        false
    }
}

fn pending_dir() -> PathBuf {
    let dir = crash_root_dir().join("pending");
    let _ = fs::create_dir_all(&dir);
    dir
}

fn sent_dir() -> PathBuf {
    let dir = crash_root_dir().join("sent");
    let _ = fs::create_dir_all(&dir);
    dir
}

fn now_epoch_secs() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

/// Install global panic hook (and Windows SEH filter) for crash capture and optional daemon recovery.
pub fn install_crash_handlers(component: &str, auto_recover_daemon: bool) {
    let _ = CRASH_COMPONENT.set(component.to_string());
    AUTO_RECOVER_DAEMON.store(auto_recover_daemon, Ordering::SeqCst);

    std::panic::set_hook(Box::new(|panic_info| {
        // Re-entrancy guard in case crash handling itself panics
        if HANDLER_ACTIVE.swap(true, Ordering::SeqCst) {
            return;
        }

        let msg = if let Some(s) = panic_info.payload().downcast_ref::<&str>() {
            (*s).to_string()
        } else if let Some(s) = panic_info.payload().downcast_ref::<String>() {
            s.clone()
        } else {
            "unknown panic payload".to_string()
        };

        let location = panic_info
            .location()
            .map(|l| format!("{}:{}:{}", l.file(), l.line(), l.column()))
            .unwrap_or_else(|| "unknown".to_string());

        let bt = std::backtrace::Backtrace::force_capture().to_string();
        let backtrace = truncate_str(&bt, MAX_BACKTRACE_CHARS);

        eprintln!("[NEXUS CRASH] Panic in {} at {}: {}", current_component(), location, msg);

        let should_recover = AUTO_RECOVER_DAEMON.load(Ordering::SeqCst);
        record_and_maybe_recover("panic", &msg, &location, &backtrace, should_recover);

        HANDLER_ACTIVE.store(false, Ordering::SeqCst);
    }));

    #[cfg(target_os = "windows")]
    install_windows_seh_filter();
}

fn current_component() -> String {
    CRASH_COMPONENT
        .get()
        .cloned()
        .unwrap_or_else(|| "unknown".to_string())
}

fn truncate_str(s: &str, max_len: usize) -> String {
    if s.len() <= max_len {
        return s.to_string();
    }
    let mut end = max_len;
    while !s.is_char_boundary(end) && end > 0 {
        end -= 1;
    }
    format!("{}...\n[truncated {} bytes]", &s[..end], s.len() - end)
}

fn record_and_maybe_recover(
    kind: &str,
    message: &str,
    location: &str,
    backtrace: &str,
    should_recover: bool,
) {
    let cfg = crate::config::AgentConfig::load_or_init();
    let root = crash_root_dir();

    let (recovered, restart_attempt) = if should_recover {
        crate::modules::daemon_lock::remove_daemon_state();
        attempt_daemon_recovery(&root)
    } else {
        (false, 0)
    };

    let record = CrashRecord {
        crash_id: Uuid::new_v4().to_string(),
        node_id: cfg.node_id,
        hostname: cfg.hostname,
        agent_version: crate::AGENT_VERSION.to_string(),
        os: cfg.os,
        arch: cfg.arch,
        component: current_component(),
        kind: kind.to_string(),
        message: message.to_string(),
        location: location.to_string(),
        backtrace: backtrace.to_string(),
        occurred_at: Utc::now().to_rfc3339(),
        recovered,
        restart_attempt,
    };

    let _ = persist_crash_record(&root, &record);
}

fn persist_crash_record(root: &Path, record: &CrashRecord) -> std::io::Result<()> {
    let pending = root.join("pending");
    fs::create_dir_all(&pending)?;

    let ts = now_epoch_secs();
    let pid = std::process::id();
    let filename = format!("{}_{}_{}.json", ts, pid, &record.crash_id[..8.min(record.crash_id.len())]);
    let final_path = pending.join(&filename);
    let tmp_path = pending.join(format!("{}.tmp", filename));

    let json = serde_json::to_string_pretty(record)
        .map_err(|e| std::io::Error::new(std::io::ErrorKind::Other, e))?;
    fs::write(&tmp_path, json.as_bytes())?;
    fs::rename(&tmp_path, &final_path)?;

    let log_path = root.join("crash.log");
    if let Ok(mut f) = OpenOptions::new().create(true).append(true).open(&log_path) {
        let _ = writeln!(
            f,
            "[{}] id={} component={} kind={} recovered={} attempt={} loc={} msg={}",
            record.occurred_at,
            record.crash_id,
            record.component,
            record.kind,
            record.recovered,
            record.restart_attempt,
            record.location,
            record.message.replace('\n', " ")
        );
    }
    Ok(())
}

/// Circuit-breaker protected daemon relaunch. Returns `(recovered, attempt_count)`.
fn attempt_daemon_recovery(root: &Path) -> (bool, u32) {
    let guard_path = root.join("restart_guard.json");
    let now = now_epoch_secs();
    let mut guard: RestartGuard = fs::read_to_string(&guard_path)
        .ok()
        .and_then(|s| serde_json::from_str(&s).ok())
        .unwrap_or_default();

    let (allowed, attempt) = evaluate_crash_loop_guard(&mut guard, now);
    if let Ok(s) = serde_json::to_string(&guard) {
        let _ = fs::write(&guard_path, s);
    }

    if !allowed {
        return (false, attempt);
    }

    let launched = spawn_detached_daemon();
    (launched, attempt)
}

fn evaluate_crash_loop_guard(guard: &mut RestartGuard, now_secs: u64) -> (bool, u32) {
    guard
        .recent_restarts_epoch
        .retain(|&ts| now_secs.saturating_sub(ts) <= CRASH_LOOP_WINDOW_SECS);

    if guard.recent_restarts_epoch.len() >= CRASH_LOOP_MAX_RESTARTS {
        return (false, guard.recent_restarts_epoch.len() as u32);
    }

    guard.recent_restarts_epoch.push(now_secs);
    (true, guard.recent_restarts_epoch.len() as u32)
}

fn spawn_detached_daemon() -> bool {
    let Ok(exe) = std::env::current_exe() else {
        return false;
    };

    #[cfg(target_os = "windows")]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NEW_PROCESS_GROUP: u32 = 0x00000200;
        const CREATE_NO_WINDOW: u32 = 0x08000000;
        const CREATE_BREAKAWAY_FROM_JOB: u32 = 0x01000000;

        std::process::Command::new(&exe)
            .args(["run", "--foreground"])
            .creation_flags(CREATE_NEW_PROCESS_GROUP | CREATE_NO_WINDOW | CREATE_BREAKAWAY_FROM_JOB)
            .stdin(std::process::Stdio::null())
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .spawn()
            .or_else(|_| {
                std::process::Command::new(&exe)
                    .args(["run", "--foreground"])
                    .creation_flags(CREATE_NEW_PROCESS_GROUP | CREATE_NO_WINDOW)
                    .stdin(std::process::Stdio::null())
                    .stdout(std::process::Stdio::null())
                    .stderr(std::process::Stdio::null())
                    .spawn()
            })
            .is_ok()
    }

    #[cfg(not(target_os = "windows"))]
    {
        use std::os::unix::process::CommandExt;
        let mut cmd = std::process::Command::new(&exe);
        cmd.args(["run", "--foreground"])
            .stdin(std::process::Stdio::null())
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null());
        unsafe {
            cmd.pre_exec(|| {
                libc::setsid();
                libc::signal(libc::SIGHUP, libc::SIG_IGN);
                Ok(())
            });
        }
        cmd.spawn().is_ok()
    }
}

/// Load all pending (un-uploaded) crash records sorted oldest-first.
pub fn load_pending_crashes() -> Vec<(PathBuf, CrashRecord)> {
    let dir = pending_dir();
    let mut items = Vec::new();
    if let Ok(entries) = fs::read_dir(&dir) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.extension().and_then(|e| e.to_str()) != Some("json") {
                continue;
            }
            if let Ok(raw) = fs::read_to_string(&path) {
                if let Ok(rec) = serde_json::from_str::<CrashRecord>(&raw) {
                    items.push((path, rec));
                }
            }
        }
    }
    items.sort_by(|a, b| a.1.occurred_at.cmp(&b.1.occurred_at));
    items
}

/// Move an uploaded crash record from `pending/` to `sent/` and prune old `sent/` files.
pub fn mark_crash_uploaded(pending_path: &Path) {
    let sent = sent_dir();
    if let Some(name) = pending_path.file_name() {
        let dest = sent.join(name);
        if fs::rename(pending_path, &dest).is_err() {
            let _ = fs::remove_file(pending_path);
        }
    }
    prune_dir(&sent, MAX_SENT_RECORDS);
}

fn prune_dir(dir: &Path, keep: usize) {
    let Ok(entries) = fs::read_dir(dir) else {
        return;
    };
    let mut files: Vec<(std::time::SystemTime, PathBuf)> = entries
        .flatten()
        .filter_map(|e| {
            let p = e.path();
            if p.extension().and_then(|x| x.to_str()) != Some("json") {
                return None;
            }
            let modified = e
                .metadata()
                .ok()
                .and_then(|m| m.modified().ok())
                .unwrap_or(UNIX_EPOCH);
            Some((modified, p))
        })
        .collect();

    if files.len() <= keep {
        return;
    }
    files.sort_by(|a, b| a.0.cmp(&b.0));
    let remove_count = files.len() - keep;
    for (_, path) in files.into_iter().take(remove_count) {
        let _ = fs::remove_file(path);
    }
}

/// List recent local crash records (both pending and sent), newest-first, tagged with `(uploaded, record)`.
pub fn list_recent_crashes(limit: usize) -> Vec<(bool, CrashRecord)> {
    let mut results = Vec::new();
    for (_, rec) in load_pending_crashes() {
        results.push((false, rec));
    }
    if let Ok(entries) = fs::read_dir(sent_dir()) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.extension().and_then(|e| e.to_str()) != Some("json") {
                continue;
            }
            if let Ok(raw) = fs::read_to_string(&path) {
                if let Ok(rec) = serde_json::from_str::<CrashRecord>(&raw) {
                    results.push((true, rec));
                }
            }
        }
    }
    results.sort_by(|a, b| b.1.occurred_at.cmp(&a.1.occurred_at));
    results.truncate(limit);
    results
}

/// Clear all local crash records (`pending/`, `sent/`, `crash.log`, `restart_guard.json`).
pub fn clear_local_crashes() -> std::io::Result<usize> {
    let mut removed = 0;
    for dir in [pending_dir(), sent_dir()] {
        if let Ok(entries) = fs::read_dir(&dir) {
            for entry in entries.flatten() {
                if fs::remove_file(entry.path()).is_ok() {
                    removed += 1;
                }
            }
        }
    }
    let root = crash_root_dir();
    let _ = fs::remove_file(root.join("crash.log"));
    let _ = fs::remove_file(root.join("restart_guard.json"));
    Ok(removed)
}

#[cfg(target_os = "windows")]
fn install_windows_seh_filter() {
    use windows_sys::Win32::System::Diagnostics::Debug::{
        SetUnhandledExceptionFilter, EXCEPTION_POINTERS,
    };

    unsafe extern "system" fn seh_filter(exception_info: *const EXCEPTION_POINTERS) -> i32 {
        if HANDLER_ACTIVE.swap(true, Ordering::SeqCst) {
            return 0; // EXCEPTION_CONTINUE_SEARCH
        }
        let code = if !exception_info.is_null() && !(*exception_info).ExceptionRecord.is_null() {
            (*(*exception_info).ExceptionRecord).ExceptionCode as u32
        } else {
            0
        };
        let addr = if !exception_info.is_null() && !(*exception_info).ExceptionRecord.is_null() {
            (*(*exception_info).ExceptionRecord).ExceptionAddress as usize
        } else {
            0
        };
        let msg = format!("Windows unhandled exception 0x{:08X} at 0x{:X}", code, addr);
        let should_recover = AUTO_RECOVER_DAEMON.load(Ordering::SeqCst);
        record_and_maybe_recover("unhandled_exception", &msg, "native_seh", "", should_recover);
        0 // EXCEPTION_CONTINUE_SEARCH
    }

    unsafe {
        SetUnhandledExceptionFilter(Some(seh_filter));
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn crash_loop_guard_trips_after_max_restarts() {
        let mut guard = RestartGuard::default();
        let base = 1_700_000_000;
        for i in 0..CRASH_LOOP_MAX_RESTARTS {
            let (allowed, attempt) = evaluate_crash_loop_guard(&mut guard, base + i as u64);
            assert!(allowed);
            assert_eq!(attempt, (i + 1) as u32);
        }
        let (allowed, attempt) = evaluate_crash_loop_guard(&mut guard, base + 10);
        assert!(!allowed);
        assert_eq!(attempt, CRASH_LOOP_MAX_RESTARTS as u32);

        // Outside window, restarts should be allowed again
        let (allowed_later, attempt_later) =
            evaluate_crash_loop_guard(&mut guard, base + CRASH_LOOP_WINDOW_SECS + 20);
        assert!(allowed_later);
        assert_eq!(attempt_later, 1);
    }

    #[test]
    fn persist_and_prune_crash_records() {
        let temp = std::env::temp_dir().join(format!("nexus_crash_test_{}", Uuid::new_v4()));
        let rec = CrashRecord {
            crash_id: Uuid::new_v4().to_string(),
            node_id: "node-test".into(),
            hostname: "host-test".into(),
            agent_version: "0.1.105".into(),
            os: "windows".into(),
            arch: "x86_64".into(),
            component: "gui".into(),
            kind: "panic".into(),
            message: "test panic".into(),
            location: "main.rs:10:1".into(),
            backtrace: "bt".into(),
            occurred_at: Utc::now().to_rfc3339(),
            recovered: false,
            restart_attempt: 0,
        };
        persist_crash_record(&temp, &rec).unwrap();
        let pending = temp.join("pending");
        assert_eq!(fs::read_dir(&pending).unwrap().count(), 1);
        assert!(temp.join("crash.log").exists());
        let _ = fs::remove_dir_all(&temp);
    }
}

