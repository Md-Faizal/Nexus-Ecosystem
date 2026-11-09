use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FitnessScore {
    pub fitness_pct: f32,        // 0.0 to 100.0%
    pub tier: String,            // "cloud" | "edge"
    pub is_cloud: bool,
    pub ram_headroom_pct: f32,   // 0.0 to 100.0%
    pub cpu_headroom_pct: f32,   // 0.0 to 100.0%
    pub network_health_pct: f32, // 0.0 to 100.0%
    pub services_healthy: bool,
    pub free_ram_mb: u64,
    pub total_ram_mb: u64,
    pub cpu_usage_pct: f32,
    pub role_label: String,      // "👑 Active Coordinator" | "⚡ Warm Quorum Standby" | "🛡️ Edge Worker"
    pub timestamp: u64,
}

impl FitnessScore {
    pub fn calculate(
        total_ram_mb: u64,
        used_ram_mb: u64,
        cpu_usage_pct: f32,
        is_cloud: bool,
        all_clouds_dark: bool,
        services_healthy: bool,
    ) -> Self {
        let free_ram_mb = total_ram_mb.saturating_sub(used_ram_mb);
        let ram_headroom_pct = if total_ram_mb > 0 {
            ((free_ram_mb as f32 / total_ram_mb as f32) * 100.0).clamp(0.0, 100.0)
        } else {
            0.0
        };

        let cpu_headroom_pct = (100.0 - cpu_usage_pct).clamp(0.0, 100.0);
        let network_health_pct = if services_healthy { 100.0 } else { 60.0 };

        // Composite resource score (0 - 100%)
        let resource_score = 0.40 * ram_headroom_pct + 0.35 * cpu_headroom_pct + 0.25 * network_health_pct;

        // Tier Factor: Cloud is heavily prioritized when available.
        // If all cloud nodes are dark (doomsday blackout), edge scales to 1.0!
        let tier_factor = if is_cloud {
            1.0
        } else if all_clouds_dark {
            1.0
        } else {
            0.70
        };

        let fitness_pct = (resource_score * tier_factor).clamp(0.0, 100.0);

        let role_label = if is_cloud && fitness_pct >= 85.0 {
            "👑 Active Cloud Coordinator".to_string()
        } else if is_cloud {
            "⚡ Warm Quorum Standby".to_string()
        } else if all_clouds_dark && fitness_pct >= 70.0 {
            "🛡️ Local Emergency Coordinator".to_string()
        } else {
            "🛡️ Edge Worker Standby".to_string()
        };

        Self {
            fitness_pct,
            tier: if is_cloud { "cloud".into() } else { "edge".into() },
            is_cloud,
            ram_headroom_pct,
            cpu_headroom_pct,
            network_health_pct,
            services_healthy,
            free_ram_mb,
            total_ram_mb,
            cpu_usage_pct,
            role_label,
            timestamp: std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|d| d.as_secs())
                .unwrap_or(0),
        }
    }

    /// Autodetects whether the current node is running in the cloud or local edge
    pub fn detect_is_cloud() -> bool {
        if let Ok(v) = std::env::var("NEXUS_TIER") {
            return v.eq_ignore_ascii_case("cloud");
        }
        if let Ok(v) = std::env::var("NEXUS_IS_CLOUD") {
            return v == "1" || v.eq_ignore_ascii_case("true");
        }

        #[cfg(target_os = "linux")]
        {
            if let Ok(product) = std::fs::read_to_string("/sys/class/dmi/id/product_name") {
                let p = product.to_lowercase();
                if p.contains("kvm") || p.contains("google") || p.contains("oracle") || p.contains("qemu") || p.contains("amazon") {
                    return true;
                }
            }
            if let Ok(vendor) = std::fs::read_to_string("/sys/class/dmi/id/sys_vendor") {
                let v = vendor.to_lowercase();
                if v.contains("google") || v.contains("oracle") || v.contains("qemu") {
                    return true;
                }
            }
        }

        false
    }
}
