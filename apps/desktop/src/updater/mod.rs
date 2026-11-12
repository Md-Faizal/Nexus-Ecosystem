pub mod rollback;

use serde::Deserialize;
use std::collections::HashMap;
use std::fs;
use std::path::Path;
use std::time::Duration;

#[derive(Debug, Deserialize)]
#[allow(dead_code)]
pub struct ArtifactInfo {
    pub filename: String,
    pub url: String,
    pub sha256: Option<String>,
}

#[derive(Debug, Deserialize)]
#[allow(dead_code)]
pub struct UpdateManifest {
    pub version: String,
    pub release_date: Option<String>,
    pub download_base: Option<String>,
    pub artifacts: HashMap<String, ArtifactInfo>,
}

pub struct AgentUpdater;

impl AgentUpdater {
    pub async fn run_update(manifest_url: &str, check_only: bool, force: bool) -> Result<(), Box<dyn std::error::Error>> {
        Self::run_update_internal(manifest_url, check_only, force, false).await
    }

    async fn run_update_internal(
        manifest_url: &str,
        check_only: bool,
        force: bool,
        silent_bg: bool,
    ) -> Result<(), Box<dyn std::error::Error>> {
        let current_version = crate::AGENT_VERSION;
        if !silent_bg {
            println!("\x1b[1;36mChecking for Nexus Fleet updates from {}...\x1b[0m", manifest_url);
        }

        let cache_buster = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);
        let busted_url = if manifest_url.contains('?') {
            format!("{}&v={}", manifest_url, cache_buster)
        } else {
            format!("{}?v={}", manifest_url, cache_buster)
        };

        let client = reqwest::Client::builder()
            .connect_timeout(Duration::from_secs(15))
            .timeout(Duration::from_secs(180))
            .build()?;

        let res = match client.get(&busted_url).header("Cache-Control", "no-cache").send().await {
            Ok(r) if r.status().is_success() => r,
            Ok(r) => {
                if !silent_bg {
                    println!("\x1b[31mUpdate check returned HTTP status {}.\x1b[0m", r.status());
                }
                return Ok(());
            }
            Err(e) => {
                if !silent_bg {
                    println!("\x1b[31mFailed to reach update manifest at {}: {}\x1b[0m", manifest_url, e);
                }
                return Ok(());
            }
        };

        let text = res.text().await?;
        let clean_text = text.trim_start_matches('\u{feff}').trim();
        let manifest: UpdateManifest = serde_json::from_str(clean_text)?;
        let remote_version = manifest.version.trim();

        let is_newer = Self::is_newer_version(remote_version, current_version);

        if check_only {
            if is_newer {
                println!("\x1b[1;33m⚡ Update available: v{} → v{}\x1b[0m", current_version, remote_version);
                if let Some(date) = manifest.release_date {
                    println!("\x1b[90mRelease Date: {}\x1b[0m", date);
                }
                println!("Run '\x1b[1;32mnexus update\x1b[0m' to download and apply the update.");
            } else {
                println!("\x1b[32m✓ Nexus Fleet Agent is up to date (version {}).\x1b[0m", current_version);
            }
            return Ok(());
        }

        if !is_newer && !force {
            println!("\x1b[32m✓ Nexus Fleet Agent is already up to date (version {}).\x1b[0m", current_version);
            println!("\x1b[90mTip: Run 'nexus update --force' if you wish to re-download the latest release.\x1b[0m");
            return Ok(());
        }

        let current_exe = std::env::current_exe()?;
        let exe_dir = current_exe.parent().unwrap_or_else(|| Path::new("."));

        // Check if destination directory is writable before attempting update
        if !Self::is_directory_writable(exe_dir) {
            if silent_bg {
                return Err(format!(
                    "Directory '{}' requires elevated privileges; skipping silent background update",
                    exe_dir.display()
                ).into());
            }
            #[cfg(target_os = "windows")]
            {
                println!("\x1b[1;33m[!] Administrator privileges required to update files in '{}'.\x1b[0m", exe_dir.display());
                println!("Requesting Windows UAC elevation...");
                return Self::elevate_windows(force);
            }

            #[cfg(not(target_os = "windows"))]
            {
                println!("\x1b[31m[Error] Write access denied to '{}'.\x1b[0m", exe_dir.display());
                println!("Please run the update with elevated privileges: \x1b[1;32msudo nexus update\x1b[0m");
                return Ok(());
            }
        }

        // Determine platform artifact key
        let target_arch = if cfg!(target_arch = "x86_64") {
            "amd64"
        } else if cfg!(target_arch = "aarch64") {
            "arm64"
        } else {
            "amd64"
        };

        let target_os = if cfg!(target_os = "windows") {
            "windows"
        } else if cfg!(target_os = "linux") {
            "linux"
        } else if cfg!(target_os = "macos") {
            "darwin"
        } else {
            "linux"
        };

        let artifact_key = format!("{}-{}", target_os, target_arch);
        let artifact = manifest.artifacts.get(&artifact_key).or_else(|| {
            manifest.artifacts.get("windows-amd64").or_else(|| manifest.artifacts.get("linux-amd64"))
        });

        let download_url = match artifact {
            Some(art) => art.url.clone(),
            None => {
                let fallback_ext = if cfg!(target_os = "windows") { ".exe" } else { "" };
                format!("https://faizal.uk/releases/nexus-{}-{}{}", target_os, target_arch, fallback_ext)
            }
        };

        println!("\x1b[1;33m⚡ Downloading Nexus Fleet Agent v{} for {}...\x1b[0m", remote_version, artifact_key);
        println!("\x1b[90mSource: {}\x1b[0m", download_url);

        let dl_res = client.get(&download_url).send().await?;
        if !dl_res.status().is_success() {
            println!("\x1b[31mFailed to download update (HTTP {}).\x1b[0m", dl_res.status());
            return Ok(());
        }

        use futures_util::StreamExt;
        use std::io::Write;

        let total_size = dl_res.content_length().unwrap_or(0);
        let mut stream = dl_res.bytes_stream();
        let mut new_bytes = Vec::with_capacity(total_size as usize);
        let mut downloaded: u64 = 0;

        while let Some(chunk_res) = stream.next().await {
            let chunk = chunk_res?;
            downloaded += chunk.len() as u64;
            new_bytes.extend_from_slice(&chunk);
            if total_size > 0 {
                let pct = (downloaded as f64 / total_size as f64) * 100.0;
                let mb_down = downloaded as f64 / (1024.0 * 1024.0);
                let mb_total = total_size as f64 / (1024.0 * 1024.0);
                print!("\r\x1b[1;33m⚡ Progress:\x1b[0m {:.2}/{:.2} MB ({:.1}%)   ", mb_down, mb_total, pct);
            } else {
                let mb_down = downloaded as f64 / (1024.0 * 1024.0);
                print!("\r\x1b[1;33m⚡ Progress:\x1b[0m {:.2} MB   ", mb_down);
            }
            let _ = std::io::stdout().flush();
        }
        println!();
        // Validate downloaded binary size & executable magic header
        if new_bytes.len() < 1_000_000 {
            return Err(format!("Downloaded binary too small ({} bytes); aborting update", new_bytes.len()).into());
        }
        #[cfg(target_os = "windows")]
        if !new_bytes.starts_with(b"MZ") {
            return Err("Downloaded payload is not a valid Windows PE executable".into());
        }
        #[cfg(target_os = "linux")]
        if !new_bytes.starts_with(b"\x7fELF") {
            return Err("Downloaded payload is not a valid Linux ELF executable".into());
        }

        let temp_exe = current_exe.with_extension("tmp_update");

        // Write temp binary
        if let Err(e) = fs::write(&temp_exe, &new_bytes) {
            #[cfg(target_os = "windows")]
            if e.kind() == std::io::ErrorKind::PermissionDenied {
                println!("\x1b[1;33m[!] Permission denied writing update. Requesting UAC elevation...\x1b[0m");
                return Self::elevate_windows(force);
            }
            return Err(Box::new(e));
        }

        #[cfg(target_os = "windows")]
        {
            let zone_path = format!("{}:Zone.Identifier", temp_exe.display());
            let _ = fs::remove_file(zone_path);
        }

        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let mut perms = fs::metadata(&temp_exe)?.permissions();
            perms.set_mode(0o755);
            fs::set_permissions(&temp_exe, perms)?;
        }

        // Atomic swap
        #[cfg(target_os = "windows")]
        {
            use std::os::windows::process::CommandExt;
            const CREATE_NEW_PROCESS_GROUP: u32 = 0x00000200;
            const CREATE_NO_WINDOW: u32 = 0x08000000;
            const CREATE_BREAKAWAY_FROM_JOB: u32 = 0x01000000;

            // Terminate other background agent instances silently to release file locks on aliases
            let current_pid = std::process::id();
            for img in ["nexus-agent.exe", "nexus.exe", "nx.exe"] {
                let _ = std::process::Command::new("taskkill")
                    .args(["/F", "/FI", &format!("PID ne {}", current_pid), "/IM", img])
                    .creation_flags(CREATE_NO_WINDOW)
                    .stdout(std::process::Stdio::null())
                    .stderr(std::process::Stdio::null())
                    .status();
            }

            // Wait for OS to fully release mutexes and file locks
            std::thread::sleep(Duration::from_millis(800));
            crate::modules::daemon_lock::remove_daemon_state();

            let old_exe = current_exe.with_extension("old");
            let _ = fs::remove_file(&old_exe);
            fs::rename(&current_exe, &old_exe)?;
            fs::rename(&temp_exe, &current_exe)?;

            // Also synchronize sibling aliases (nx.exe, nexus.exe, nexus-agent.exe)
            if let Some(parent) = current_exe.parent() {
                let aliases = ["nx.exe", "nexus.exe", "nexus-agent.exe"];
                for alias in aliases {
                    let alias_path = parent.join(alias);
                    if alias_path != current_exe {
                        let _ = fs::remove_file(&alias_path);
                        let _ = fs::copy(&current_exe, &alias_path);
                    }
                }
            }

            // Clean up old binary
            let _ = fs::remove_file(&old_exe);

            // Restart background worker cleanly with --wait-lock so it takes over as soon as this process exits
            let _ = std::process::Command::new(&current_exe)
                .args(["run", "--foreground", "--wait-lock"])
                .creation_flags(CREATE_NEW_PROCESS_GROUP | CREATE_NO_WINDOW | CREATE_BREAKAWAY_FROM_JOB)
                .stdin(std::process::Stdio::null())
                .stdout(std::process::Stdio::null())
                .stderr(std::process::Stdio::null())
                .spawn()
                .or_else(|_| {
                    std::process::Command::new(&current_exe)
                        .args(["run", "--foreground", "--wait-lock"])
                        .creation_flags(CREATE_NEW_PROCESS_GROUP | CREATE_NO_WINDOW)
                        .stdin(std::process::Stdio::null())
                        .stdout(std::process::Stdio::null())
                        .stderr(std::process::Stdio::null())
                        .spawn()
                });
        }

        #[cfg(not(target_os = "windows"))]
        {
            let backup_exe = current_exe.with_extension("bak");
            let _ = fs::copy(&current_exe, &backup_exe);
            fs::rename(&temp_exe, &current_exe)?;

            #[cfg(target_os = "linux")]
            {
                let _ = std::process::Command::new("systemctl")
                    .args(["try-restart", "nexus-agent"])
                    .status();
                let _ = std::process::Command::new("systemctl")
                    .args(["--user", "try-restart", "nexus-agent"])
                    .status();
            }

            #[cfg(target_os = "macos")]
            {
                let _ = std::process::Command::new("launchctl")
                    .args(["stop", "uk.faizal.nexus-agent"])
                    .status();
                std::thread::sleep(Duration::from_millis(300));
                let _ = std::process::Command::new("launchctl")
                    .args(["start", "uk.faizal.nexus-agent"])
                    .status();
            }
        }

        println!("\x1b[1;32m✓ Successfully updated Nexus Fleet Agent to version {}!\x1b[0m", remote_version);
        println!("Run '\x1b[1;36mnexus status\x1b[0m' or '\x1b[1;36mnx status\x1b[0m' to verify.");

        Ok(())
    }

    fn is_directory_writable(dir: &Path) -> bool {
        let test_file = dir.join(format!(".nexus_write_test_{}", std::process::id()));
        match fs::File::create(&test_file) {
            Ok(file) => {
                drop(file);
                let _ = fs::remove_file(&test_file);
                true
            }
            Err(_) => false,
        }
    }

    #[cfg(target_os = "windows")]
    fn elevate_windows(force: bool) -> Result<(), Box<dyn std::error::Error>> {
        let current_exe = std::env::current_exe()?;
        let target_dir = current_exe.parent().unwrap_or_else(|| Path::new("."));
        let target_dir_str = target_dir.to_string_lossy().to_string();

        let _ = force;
        // Construct script: set $TargetDir and execute installer
        let script = format!(
            "$TargetDir = '{}'; irm https://faizal.uk/install.ps1 | iex",
            target_dir_str.replace('\'', "''")
        );

        // Encode as UTF-16LE Base64 so PowerShell -EncodedCommand executes it without any quote or parenthesis parsing bugs
        let utf16: Vec<u8> = script
            .encode_utf16()
            .flat_map(|u| u.to_le_bytes())
            .collect();

        const B64_CHARS: &[u8] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        let mut b64 = String::with_capacity(utf16.len() * 4 / 3 + 4);
        for chunk in utf16.chunks(3) {
            let b0 = chunk[0];
            let b1 = if chunk.len() > 1 { chunk[1] } else { 0 };
            let b2 = if chunk.len() > 2 { chunk[2] } else { 0 };

            b64.push(B64_CHARS[(b0 >> 2) as usize] as char);
            b64.push(B64_CHARS[(((b0 & 0x03) << 4) | (b1 >> 4)) as usize] as char);
            if chunk.len() > 1 {
                b64.push(B64_CHARS[(((b1 & 0x0f) << 2) | (b2 >> 6)) as usize] as char);
            } else {
                b64.push('=');
            }
            if chunk.len() > 2 {
                b64.push(B64_CHARS[(b2 & 0x3f) as usize] as char);
            } else {
                b64.push('=');
            }
        }

        let ps_cmd = format!(
            "Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile -ExecutionPolicy Bypass -EncodedCommand {}'",
            b64
        );

        let status = std::process::Command::new("powershell")
            .args(["-NoProfile", "-Command", &ps_cmd])
            .status()?;

        if status.success() {
            println!("\x1b[32mUpdate launched in elevated PowerShell window.\x1b[0m");
        } else {
            println!("\x1b[31mUAC elevation was cancelled or failed.\x1b[0m");
        }
        Ok(())
    }

    pub fn is_newer_version(remote: &str, current: &str) -> bool {
        let r_parts: Vec<u32> = remote.split('.').filter_map(|s| s.parse().ok()).collect();
        let c_parts: Vec<u32> = current.split('.').filter_map(|s| s.parse().ok()).collect();

        for (r, c) in r_parts.iter().zip(c_parts.iter()) {
            if r > c {
                return true;
            } else if r < c {
                return false;
            }
        }
        r_parts.len() > c_parts.len()
    }

    /// Periodic background auto-update check executed silently by the running daemon
    pub async fn run_auto_update_cycle() {
        let ts = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);
        let manifest_url = format!("https://faizal.uk/releases/latest.json?v={}", ts);
        let client = match reqwest::Client::builder()
            .timeout(Duration::from_secs(15))
            .build()
        {
            Ok(c) => c,
            Err(_) => return,
        };

        let res = match client.get(&manifest_url).header("Cache-Control", "no-cache").send().await {
            Ok(r) if r.status().is_success() => r,
            _ => return,
        };

        let text = match res.text().await {
            Ok(t) => t,
            Err(_) => return,
        };
        let clean = text.trim_start_matches('\u{feff}').trim();
        let manifest: UpdateManifest = match serde_json::from_str(clean) {
            Ok(m) => m,
            Err(_) => return,
        };

        let remote_version = manifest.version.trim();
        let current_version = crate::AGENT_VERSION.trim();

        if Self::is_newer_version(remote_version, current_version) {
            tracing::info!(
                "[Auto-Update] Detected newer Nexus Agent release: v{} (current is v{}). Starting background upgrade...",
                remote_version,
                current_version
            );
            if let Err(e) = Self::run_update_internal(&manifest_url, false, false, true).await {
                tracing::warn!("[Auto-Update] Silent background update failed: {}", e);
            } else {
                tracing::info!("[Auto-Update] Successfully upgraded to v{}. Exiting old daemon instance...", remote_version);
                std::process::exit(0);
            }
        }
    }
}
