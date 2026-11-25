use nexus_protocol::MobileReleaseManifest;
use reqwest::Client;
use sha2::{Digest, Sha256};
use std::fs::{self, File, OpenOptions};
use std::io::{Read, Write};
use std::path::Path;
use std::sync::Arc;
use std::time::Instant;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum UpdateError {
    #[error("network error: {0}")]
    Http(#[from] reqwest::Error),
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
    #[error("server returned HTTP {0}")]
    Status(u16),
    #[error("sha256 mismatch: expected {expected}, got {actual}")]
    ChecksumMismatch { expected: String, actual: String },
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct UpdateInfo {
    pub version_code: u32,
    pub version_name: String,
    pub release_notes: String,
    pub apk_url: String,
    pub sha256: String,
    pub mandatory: bool,
    pub published_at: String,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DownloadProgress {
    pub bytes_downloaded: u64,
    pub total_bytes: u64,
    pub speed_bytes_per_sec: u64,
    pub eta_seconds: u64,
}

#[uniffi::export(with_foreign)]
pub trait DownloadProgressListener: Send + Sync {
    fn on_progress(&self, progress: DownloadProgress);
}

pub async fn check_for_update(
    base_url: &str,
    current_version_code: u32,
) -> Result<Option<UpdateInfo>, UpdateError> {
    let client = Client::builder()
        .user_agent("NexusMobile/2.0 (RustUpdater)")
        .build()?;
    let url = format!("{}/releases/mobile-latest.json", base_url.trim_end_matches('/'));
    let resp = client.get(&url).send().await?;
    if !resp.status().is_success() {
        return Err(UpdateError::Status(resp.status().as_u16()));
    }
    let manifest: MobileReleaseManifest = resp.json().await?;
    if manifest.version_code > current_version_code {
        Ok(Some(UpdateInfo {
            version_code: manifest.version_code,
            version_name: manifest.version_name,
            release_notes: manifest.release_notes,
            apk_url: manifest.apk_url,
            sha256: manifest.sha256,
            mandatory: manifest.mandatory,
            published_at: manifest.published_at,
        }))
    } else {
        Ok(None)
    }
}

pub async fn download_and_verify_apk(
    apk_url: &str,
    dest_apk_path: &str,
    expected_sha256: &str,
    listener: Option<Arc<dyn DownloadProgressListener>>,
) -> Result<String, UpdateError> {
    let client = Client::builder()
        .user_agent("NexusMobile/2.0 (RustUpdater)")
        .build()?;

    let tmp_path = format!("{dest_apk_path}.tmp");
    let existing_len = fs::metadata(&tmp_path).map(|m| m.len()).unwrap_or(0);

    let mut req = client.get(apk_url);
    if existing_len > 0 {
        req = req.header("Range", format!("bytes={existing_len}-"));
    }

    let resp = req.send().await?;
    let status = resp.status();
    if !status.is_success() && status.as_u16() != 206 {
        return Err(UpdateError::Status(status.as_u16()));
    }

    let is_partial = status.as_u16() == 206;
    let resume_offset = if is_partial { existing_len } else { 0 };
    let content_len = resp.content_length().unwrap_or(0);
    let total_bytes = if content_len > 0 {
        resume_offset + content_len
    } else {
        0
    };

    let mut file = OpenOptions::new()
        .create(true)
        .write(true)
        .append(is_partial)
        .truncate(!is_partial)
        .open(&tmp_path)?;

    let mut downloaded = resume_offset;
    let start = Instant::now();
    let mut last_emit = Instant::now();
    let mut resp = resp;

    while let Some(chunk) = resp.chunk().await? {
        file.write_all(&chunk)?;
        downloaded += chunk.len() as u64;

        if let Some(cb) = listener.as_ref() {
            if last_emit.elapsed().as_millis() >= 150 || (total_bytes > 0 && downloaded >= total_bytes) {
                let elapsed = start.elapsed().as_secs_f64().max(0.05);
                let session_bytes = downloaded.saturating_sub(resume_offset) as f64;
                let speed = (session_bytes / elapsed) as u64;
                let eta = if speed > 0 && total_bytes > downloaded {
                    (total_bytes - downloaded) / speed
                } else {
                    0
                };
                cb.on_progress(DownloadProgress {
                    bytes_downloaded: downloaded,
                    total_bytes,
                    speed_bytes_per_sec: speed,
                    eta_seconds: eta,
                });
                last_emit = Instant::now();
            }
        }
    }
    file.flush()?;
    drop(file);

    // Mandatory SHA-256 integrity verification before exposing the APK
    let actual_sha256 = compute_file_sha256(Path::new(&tmp_path))?;
    if !actual_sha256.eq_ignore_ascii_case(expected_sha256.trim()) {
        let _ = fs::remove_file(&tmp_path);
        return Err(UpdateError::ChecksumMismatch {
            expected: expected_sha256.to_string(),
            actual: actual_sha256,
        });
    }

    fs::rename(&tmp_path, dest_apk_path)?;
    Ok(dest_apk_path.to_string())
}

pub fn compute_file_sha256(path: &Path) -> Result<String, std::io::Error> {
    let mut file = File::open(path)?;
    let mut hasher = Sha256::new();
    let mut buf = [0u8; 65_536];
    loop {
        let n = file.read(&mut buf)?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Ok(hex::encode(hasher.finalize()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sha256_verification_computes_expected_digest() {
        let tmp = std::env::temp_dir().join("nexus_mobile_sha_test.bin");
        fs::write(&tmp, b"nexus-apk-payload").unwrap();
        let digest = compute_file_sha256(&tmp).unwrap();
        let _ = fs::remove_file(&tmp);

        let expected = hex::encode(Sha256::digest(b"nexus-apk-payload"));
        assert_eq!(digest, expected);
    }
}
