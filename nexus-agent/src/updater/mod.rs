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
        let current_version = env!("CARGO_PKG_VERSION");
        println!("\x1b[1;36mChecking for Nexus Fleet updates from {}...\x1b[0m", manifest_url);

        let client = reqwest::Client::builder()
            .timeout(Duration::from_secs(10))
            .build()?;

        let res = match client.get(manifest_url).send().await {
            Ok(r) if r.status().is_success() => r,
            Ok(r) => {
                println!("\x1b[31mUpdate check returned HTTP status {}.\x1b[0m", r.status());
                return Ok(());
            }
            Err(e) => {
                println!("\x1b[31mFailed to reach update manifest at {}: {}\x1b[0m", manifest_url, e);
                return Ok(());
            }
        };

        let manifest = res.json::<UpdateManifest>().await?;
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

        let new_bytes = dl_res.bytes().await?;
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
            // Terminate background agent instances to release file locks on aliases
            let current_pid = std::process::id();
            let _ = std::process::Command::new("taskkill")
                .args(["/F", "/FI", &format!("PID ne {}", current_pid), "/IM", "nexus-agent.exe"])
                .stdout(std::process::Stdio::null())
                .stderr(std::process::Stdio::null())
                .status();
            let _ = std::process::Command::new("taskkill")
                .args(["/F", "/FI", &format!("PID ne {}", current_pid), "/IM", "nexus.exe"])
                .stdout(std::process::Stdio::null())
                .stderr(std::process::Stdio::null())
                .status();
            std::thread::sleep(Duration::from_millis(300));

            let old_exe = current_exe.with_extension("old");
            let _ = fs::remove_file(&old_exe);
            fs::rename(&current_exe, &old_exe)?;
            fs::rename(&temp_exe, &current_exe)?;

            // Also synchronize sibling aliases (nx.exe, nexus-agent.exe)
            if let Some(parent) = current_exe.parent() {
                let aliases = ["nx.exe", "nexus.exe", "nexus-agent.exe"];
                for alias in aliases {
                    let alias_path = parent.join(alias);
                    if alias_path != current_exe && alias_path.exists() {
                        let _ = fs::copy(&current_exe, &alias_path);
                    }
                }
            }

            // Clean up old binary
            let _ = fs::remove_file(&old_exe);

            // Restart background worker if it was active
            let _ = std::process::Command::new(&current_exe).arg("run").spawn();
        }

        #[cfg(not(target_os = "windows"))]
        {
            let backup_exe = current_exe.with_extension("bak");
            let _ = fs::copy(&current_exe, &backup_exe);
            fs::rename(&temp_exe, &current_exe)?;

            // Restart systemd service if available
            let _ = std::process::Command::new("systemctl")
                .args(["try-restart", "nexus-agent"])
                .status();
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
        let exe_str = current_exe.to_string_lossy().to_string();
        let args = if force { "update --force" } else { "update" };

        let ps_cmd = format!(
            "Start-Process -FilePath '{}' -ArgumentList '{}' -Verb RunAs",
            exe_str, args
        );

        let status = std::process::Command::new("powershell")
            .args(["-NoProfile", "-Command", &ps_cmd])
            .status()?;

        if status.success() {
            println!("\x1b[32mUpdate launched in elevated window.\x1b[0m");
        } else {
            println!("\x1b[31mUAC elevation was cancelled or failed.\x1b[0m");
        }
        Ok(())
    }

    fn is_newer_version(remote: &str, current: &str) -> bool {
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
}
