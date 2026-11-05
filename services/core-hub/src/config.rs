use std::path::PathBuf;

#[derive(Clone, Debug)]
pub struct AppConfig {
    pub port: u16,
    pub db_path: PathBuf,
    pub static_dir: PathBuf,
    pub public_dir: PathBuf,
    pub admin_pin: String,
    // ── SMTP (routed through stalwart-mail Docker container) ─────────────────
    pub smtp_host: String,
    pub smtp_port: u16,
    pub smtp_user: String,
    pub smtp_pass: String,
    /// "starttls" | "tls" | "none"
    pub smtp_tls: String,
    pub sender_name: String,
    pub sender_email: String,
    // ── Cluster Topology & Role ──────────────────────────────────────────────
    pub node_role: String,
    pub node_name: String,
}

impl AppConfig {
    pub fn is_standby(&self) -> bool {
        let hostname = sysinfo::System::host_name().unwrap_or_default().to_lowercase();
        self.node_role.eq_ignore_ascii_case("standby")
            || self.node_role.eq_ignore_ascii_case("failsafe")
            || self.node_name.to_lowercase().contains("container-of-souls")
            || self.node_name.to_lowercase().contains("prometheus")
            || self.node_name.to_lowercase().contains("pro")
            || hostname.contains("container-of-souls")
            || hostname.contains("prometheus")
    }

    pub fn is_primary(&self) -> bool {
        !self.is_standby()
    }

    pub fn from_env() -> Self {
        let port = std::env::var("PORT")
            .ok()
            .and_then(|p| p.parse().ok())
            .unwrap_or(3002);

        // ── Path resolution (env-var first, then sensible fallbacks) ──────────
        let db_path = PathBuf::from(
            std::env::var("DB_PATH").unwrap_or_else(|_| {
                if PathBuf::from("/app/cluster_hub.db").exists() {
                    "/app/cluster_hub.db".into()
                } else if PathBuf::from("/app/messages.db").exists() {
                    "/app/messages.db".into()
                } else if PathBuf::from("../backend/cluster_hub.db").exists() {
                    "../backend/cluster_hub.db".into()
                } else if PathBuf::from("../backend/messages.db").exists() {
                    "../backend/messages.db".into()
                } else if PathBuf::from("cluster_hub.db").exists() {
                    "cluster_hub.db".into()
                } else {
                    "cluster_hub.db".into()
                }
            }),
        );

        let static_dir = PathBuf::from(
            std::env::var("STATIC_DIR").unwrap_or_else(|_| {
                if PathBuf::from("/app/frontend/dist").exists() {
                    "/app/frontend/dist".into()
                } else if PathBuf::from("../../apps/web/dist").exists() {
                    "../../apps/web/dist".into()
                } else {
                    "../frontend/dist".into()
                }
            }),
        );

        let public_dir = PathBuf::from(
            std::env::var("PUBLIC_DIR").unwrap_or_else(|_| {
                if PathBuf::from("/app/public").exists() {
                    "/app/public".into()
                } else if PathBuf::from("../../public").exists() {
                    "../../public".into()
                } else {
                    "../public".into()
                }
            }),
        );

        let node_role = std::env::var("NODE_ROLE").unwrap_or_else(|_| "primary".into());
        let host_name = sysinfo::System::host_name().unwrap_or_default();
        let node_name = std::env::var("NODE_NAME").unwrap_or_else(|_| {
            if host_name.contains("container-of-souls") {
                "container-of-souls".into()
            } else if host_name.contains("prometheus") {
                "prometheus".into()
            } else {
                "my-mac".into()
            }
        });

        Self {
            port,
            db_path,
            static_dir,
            public_dir,
            admin_pin: std::env::var("ADMIN_PIN").unwrap_or_else(|_| "2004".into()),
            // ── SMTP defaults point to the stalwart-mail Docker container ────
            // Docker DNS resolves "stalwart-mail" on the shared mail_default network
            smtp_host: std::env::var("SMTP_HOST")
                .unwrap_or_else(|_| "stalwart-mail".into()),
            smtp_port: std::env::var("SMTP_PORT")
                .ok()
                .and_then(|p| p.parse().ok())
                .unwrap_or(587),
            smtp_user: std::env::var("SMTP_USER")
                .unwrap_or_else(|_| "reports@faizal.uk".into()),
            smtp_pass: std::env::var("SMTP_PASS")
                .unwrap_or_default(),
            smtp_tls: std::env::var("SMTP_TLS")
                .unwrap_or_else(|_| "starttls".into()),
            sender_name: std::env::var("SENDER_NAME")
                .unwrap_or_else(|_| "Faizal VM Health Monitor".into()),
            sender_email: std::env::var("SENDER_EMAIL")
                .unwrap_or_else(|_| "reports@faizal.uk".into()),
            node_role,
            node_name,
        }
    }
}
