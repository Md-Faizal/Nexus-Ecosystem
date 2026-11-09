use crate::mesh::protocol::GpuTelemetry;
use std::process::Command;
use tracing::debug;

pub struct GpuCollector;

impl GpuCollector {
    pub fn collect() -> Option<GpuTelemetry> {
        // 1. Try NVIDIA via nvidia-smi
        if let Some(gpu) = Self::collect_nvidia() {
            return Some(gpu);
        }

        // 2. Try Windows generic GPU detection (Intel Iris/UHD, AMD Radeon, Qualcomm)
        #[cfg(target_os = "windows")]
        {
            if let Some(gpu) = Self::collect_windows_generic() {
                return Some(gpu);
            }
        }

        // 3. Try macOS Apple Silicon
        #[cfg(target_os = "macos")]
        {
            if let Some(gpu) = Self::collect_apple_silicon() {
                return Some(gpu);
            }
        }

        // 4. Fallback Linux sysfs AMD/Intel/ROCm
        #[cfg(target_os = "linux")]
        {
            if let Some(gpu) = Self::collect_linux_drm() {
                return Some(gpu);
            }
        }

        None
    }

    fn collect_nvidia() -> Option<GpuTelemetry> {
        let output = Command::new("nvidia-smi")
            .args([
                "--query-gpu=name,utilization.gpu,memory.total,memory.used,temperature.gpu,power.draw",
                "--format=csv,noheader,nounits",
            ])
            .output()
            .ok()?;

        if !output.status.success() {
            return None;
        }

        let text = String::from_utf8_lossy(&output.stdout);
        let first_line = text.lines().next()?;
        let parts: Vec<&str> = first_line.split(',').map(|s| s.trim()).collect();

        if parts.len() >= 4 {
            let name = parts[0].to_string();
            let util_pct = parts[1].parse::<f32>().unwrap_or(0.0);
            let vram_total = parts[2].parse::<u64>().unwrap_or(0);
            let vram_used = parts[3].parse::<u64>().unwrap_or(0);
            let temp = parts.get(4).and_then(|t| t.parse::<f32>().ok());
            let power = parts.get(5).and_then(|p| p.parse::<f32>().ok());

            debug!("Detected NVIDIA GPU: {} (VRAM: {}MB / {}MB)", name, vram_used, vram_total);

            return Some(GpuTelemetry {
                vendor: "NVIDIA".to_string(),
                name,
                utilization_pct: util_pct,
                vram_total_mb: vram_total,
                vram_used_mb: vram_used,
                temperature_c: temp,
                power_watts: power,
            });
        }

        None
    }

    #[cfg(target_os = "windows")]
    fn collect_windows_generic() -> Option<GpuTelemetry> {
        static CACHED_WIN_GPU: std::sync::OnceLock<Option<GpuTelemetry>> = std::sync::OnceLock::new();
        CACHED_WIN_GPU.get_or_init(|| {
            let output = Command::new("powershell")
                .args([
                    "-NoProfile",
                    "-NonInteractive",
                    "-Command",
                    "Get-CimInstance Win32_VideoController | Select-Object -First 1 Name, AdapterRAM | ConvertTo-Json",
                ])
                .output()
                .ok()?;

            if !output.status.success() {
                return None;
            }

            let text = String::from_utf8_lossy(&output.stdout);
            let val: serde_json::Value = serde_json::from_str(text.trim()).ok()?;
            let name = val.get("Name")?.as_str()?.to_string();
            let adapter_ram = val.get("AdapterRAM").and_then(|v| v.as_u64()).unwrap_or(0);
            let vram_total_mb = adapter_ram / (1024 * 1024);

            let vendor = if name.contains("NVIDIA") || name.contains("GeForce") {
                "NVIDIA"
            } else if name.contains("Intel") || name.contains("Iris") || name.contains("UHD") {
                "Intel"
            } else if name.contains("AMD") || name.contains("Radeon") {
                "AMD"
            } else if name.contains("Qualcomm") || name.contains("Snapdragon") {
                "Qualcomm"
            } else {
                "Generic"
            };

            debug!("Detected Windows Display Controller: {} [{}] (VRAM: {}MB)", name, vendor, vram_total_mb);

            Some(GpuTelemetry {
                vendor: vendor.to_string(),
                name,
                utilization_pct: 0.0,
                vram_total_mb,
                vram_used_mb: 0,
                temperature_c: None,
                power_watts: None,
            })
        }).clone()
    }

    #[cfg(target_os = "macos")]
    fn collect_apple_silicon() -> Option<GpuTelemetry> {
        static CACHED_GPU: std::sync::OnceLock<Option<GpuTelemetry>> = std::sync::OnceLock::new();
        CACHED_GPU.get_or_init(|| {
            let output = Command::new("system_profiler")
                .args(["SPDisplaysDataType"])
                .output()
                .ok()?;

            if output.status.success() {
                let text = String::from_utf8_lossy(&output.stdout);
                for line in text.lines() {
                    if line.contains("Chipset Model:") || line.contains("Apple M") {
                        let name = line.split(':').nth(1).unwrap_or("Apple Silicon GPU").trim().to_string();
                        return Some(GpuTelemetry {
                            vendor: "Apple".to_string(),
                            name,
                            utilization_pct: 0.0,
                            vram_total_mb: 0, // Unified memory
                            vram_used_mb: 0,
                            temperature_c: None,
                            power_watts: None,
                        });
                    }
                }
            }
            None
        }).clone()
    }

    #[cfg(target_os = "linux")]
    fn collect_linux_drm() -> Option<GpuTelemetry> {
        // Try rocm-smi for AMD first
        if let Ok(output) = Command::new("rocm-smi").args(["--showuse", "--csv"]).output() {
            if output.status.success() {
                return Some(GpuTelemetry {
                    vendor: "AMD".to_string(),
                    name: "AMD Radeon GPU (ROCm)".to_string(),
                    utilization_pct: 0.0,
                    vram_total_mb: 0,
                    vram_used_mb: 0,
                    temperature_c: None,
                    power_watts: None,
                });
            }
        }

        // Check for AMD / Intel in sysfs (/sys/class/drm/card*/device/vendor)
        for card_idx in 0..4 {
            let vendor_path = format!("/sys/class/drm/card{}/device/vendor", card_idx);
            if let Ok(vendor_hex) = std::fs::read_to_string(&vendor_path) {
                let vendor = match vendor_hex.trim() {
                    "0x1002" => "AMD",
                    "0x8086" => "Intel",
                    "0x10de" => "NVIDIA",
                    _ => continue,
                };

                return Some(GpuTelemetry {
                    vendor: vendor.to_string(),
                    name: format!("{} Graphics Accelerator (card{})", vendor, card_idx),
                    utilization_pct: 0.0,
                    vram_total_mb: 0,
                    vram_used_mb: 0,
                    temperature_c: None,
                    power_watts: None,
                });
            }
        }
        None
    }
}
