use rusqlite::{params, Connection, Result};
use std::path::Path;
use std::sync::Arc;
use tokio::sync::Mutex;
use tracing::{error, info};

pub type DbPool = Arc<Mutex<Connection>>;

pub fn open_database(path: &Path) -> Result<DbPool> {
    if let Some(parent) = path.parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    let conn = Connection::open(path)?;
    init_schema(&conn)?;
    info!("Connected to SQLite database at {:?}", path);
    Ok(Arc::new(Mutex::new(conn)))
}

fn init_schema(conn: &Connection) -> Result<()> {
    conn.execute_batch(
        r#"
        CREATE TABLE IF NOT EXISTS messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            email TEXT NOT NULL,
            subject TEXT NOT NULL,
            message TEXT NOT NULL,
            ip_address TEXT,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS settings (
            key TEXT PRIMARY KEY,
            value TEXT
        );

        CREATE TABLE IF NOT EXISTS profile_pictures (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            value BLOB,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS visitors (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ip TEXT,
            user_agent TEXT,
            path TEXT,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS projects (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT,
            description TEXT,
            image_blob BLOB,
            link TEXT,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS access_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ip TEXT,
            status TEXT,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS banned_ips (
            ip TEXT PRIMARY KEY,
            reason TEXT,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS health_reports (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
            recipient TEXT,
            overall_health TEXT,
            error_count INTEGER,
            trigger_source TEXT,
            delivery_status TEXT,
            details TEXT
        );

        CREATE TABLE IF NOT EXISTS cluster_nodes (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            role TEXT NOT NULL DEFAULT 'PRIMARY',
            provider TEXT NOT NULL DEFAULT 'Custom',
            ip_internal TEXT NOT NULL,
            ip_public TEXT,
            ssh_target TEXT,
            status TEXT DEFAULT 'ONLINE',
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS cluster_services (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            description TEXT,
            category TEXT NOT NULL DEFAULT 'core',
            group_label TEXT NOT NULL DEFAULT 'General',
            node_target TEXT NOT NULL DEFAULT 'all',
            port INTEGER,
            control_type TEXT NOT NULL DEFAULT 'docker',
            serving_state TEXT NOT NULL DEFAULT 'active',
            traffic_route TEXT,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS system_metrics_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
            node_id TEXT NOT NULL,
            cpu_percent REAL NOT NULL,
            ram_percent REAL NOT NULL,
            ram_used_bytes INTEGER NOT NULL,
            ram_total_bytes INTEGER NOT NULL,
            disk_percent REAL NOT NULL,
            disk_used_bytes INTEGER NOT NULL DEFAULT 0,
            disk_total_bytes INTEGER NOT NULL DEFAULT 0
        );

        CREATE TABLE IF NOT EXISTS http_request_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
            status_code INTEGER NOT NULL,
            path TEXT,
            ip TEXT
        );

        CREATE TABLE IF NOT EXISTS fleet_chat_messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            chat_id TEXT NOT NULL DEFAULT 'global',
            sender_id TEXT NOT NULL,
            sender_name TEXT NOT NULL,
            recipient_id TEXT,
            content TEXT NOT NULL,
            message_type TEXT NOT NULL DEFAULT 'text',
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS fleet_ai_conversations (
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            target_node_id TEXT NOT NULL,
            model TEXT NOT NULL,
            system_prompt TEXT,
            temperature REAL DEFAULT 0.7,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS fleet_ai_messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            conversation_id TEXT NOT NULL,
            role TEXT NOT NULL,
            content TEXT NOT NULL,
            node_id TEXT,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS fleet_nodes_history (
            node_id TEXT PRIMARY KEY,
            hostname TEXT NOT NULL,
            os TEXT NOT NULL,
            os_version TEXT,
            arch TEXT NOT NULL,
            agent_version TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'offline',
            last_seen DATETIME DEFAULT CURRENT_TIMESTAMP,
            last_telemetry TEXT,
            last_models TEXT
        );

        CREATE TABLE IF NOT EXISTS fleet_crash_reports (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            crash_id TEXT UNIQUE NOT NULL,
            node_id TEXT NOT NULL,
            hostname TEXT,
            agent_version TEXT,
            os TEXT,
            arch TEXT,
            component TEXT,
            kind TEXT,
            message TEXT NOT NULL,
            location TEXT,
            backtrace TEXT,
            recovered INTEGER NOT NULL DEFAULT 0,
            restart_attempt INTEGER NOT NULL DEFAULT 0,
            occurred_at TEXT NOT NULL,
            received_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        -- Resilient Out-of-Band Mail Spool (Store-and-Forward DTN Buffer)
        CREATE TABLE IF NOT EXISTS mail_spool (
            id TEXT PRIMARY KEY,
            recipient TEXT NOT NULL,
            subject TEXT NOT NULL,
            html_body TEXT NOT NULL,
            text_body TEXT,
            message_type TEXT NOT NULL DEFAULT 'general',
            status TEXT NOT NULL DEFAULT 'pending',
            priority INTEGER NOT NULL DEFAULT 5,
            attempts INTEGER NOT NULL DEFAULT 0,
            max_attempts INTEGER NOT NULL DEFAULT 5,
            last_error TEXT,
            dispatched_by TEXT,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            scheduled_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            sent_at DATETIME
        );

        -- Nexus Auth Core Tables
        CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY,
            email TEXT UNIQUE NOT NULL,
            username TEXT UNIQUE,
            full_name TEXT NOT NULL,
            avatar_url TEXT,
            password_hash TEXT,
            security_question TEXT,
            security_answer_hash TEXT,
            role TEXT NOT NULL DEFAULT 'admin',
            status TEXT NOT NULL DEFAULT 'active',
            token_version INTEGER NOT NULL DEFAULT 1,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS user_oauth_accounts (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            provider TEXT NOT NULL,
            provider_user_id TEXT NOT NULL,
            provider_email TEXT,
            provider_picture TEXT,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(provider, provider_user_id)
        );

        CREATE TABLE IF NOT EXISTS auth_codes (
            id TEXT PRIMARY KEY,
            email TEXT NOT NULL,
            code_hash TEXT NOT NULL,
            code_type TEXT NOT NULL,
            metadata TEXT,
            attempts INTEGER DEFAULT 0,
            expires_at DATETIME NOT NULL,
            used_at DATETIME,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS user_devices (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            device_name TEXT NOT NULL,
            device_type TEXT NOT NULL,
            client_app TEXT NOT NULL,
            ip_address TEXT,
            last_seen DATETIME DEFAULT CURRENT_TIMESTAMP,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS refresh_tokens (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            device_id TEXT REFERENCES user_devices(id) ON DELETE SET NULL,
            family_id TEXT NOT NULL,
            token_hash TEXT NOT NULL,
            is_revoked INTEGER DEFAULT 0,
            expires_at DATETIME NOT NULL,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS api_tokens (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            name TEXT NOT NULL,
            token_prefix TEXT NOT NULL,
            token_hash TEXT NOT NULL,
            scopes TEXT NOT NULL DEFAULT '["*"]',
            last_used_at DATETIME,
            expires_at DATETIME,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS device_codes (
            device_code TEXT PRIMARY KEY,
            user_code TEXT UNIQUE NOT NULL,
            user_id TEXT REFERENCES users(id),
            status TEXT NOT NULL DEFAULT 'pending',
            expires_at DATETIME NOT NULL,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS auth_audit_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT,
            event TEXT NOT NULL,
            ip_address TEXT,
            user_agent TEXT,
            status TEXT NOT NULL,
            details TEXT,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        -- Ecosystem Applications Registry (Where Nexus Account can be used)
        CREATE TABLE IF NOT EXISTS auth_registered_apps (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            slug TEXT UNIQUE NOT NULL,
            description TEXT,
            client_secret_hash TEXT,
            allowed_redirect_uris TEXT NOT NULL DEFAULT '[]',
            allowed_scopes TEXT NOT NULL DEFAULT '["profile:read"]',
            icon_url TEXT,
            is_first_party INTEGER NOT NULL DEFAULT 1,
            is_enabled INTEGER NOT NULL DEFAULT 1,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        -- User Delegated Permissions & Grants per App
        CREATE TABLE IF NOT EXISTS auth_user_grants (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            app_id TEXT NOT NULL REFERENCES auth_registered_apps(id) ON DELETE CASCADE,
            granted_scopes TEXT NOT NULL DEFAULT '["profile:read"]',
            is_revoked INTEGER NOT NULL DEFAULT 0,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            last_used_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(user_id, app_id)
        );

        CREATE INDEX IF NOT EXISTS idx_metrics_hist_ts ON system_metrics_history(timestamp);
        CREATE INDEX IF NOT EXISTS idx_http_req_ts ON http_request_logs(timestamp);
        CREATE INDEX IF NOT EXISTS idx_fleet_chat_id ON fleet_chat_messages(chat_id, created_at);
        CREATE INDEX IF NOT EXISTS idx_fleet_ai_conv_id ON fleet_ai_messages(conversation_id);
        CREATE INDEX IF NOT EXISTS idx_fleet_crash_node_ts ON fleet_crash_reports(node_id, occurred_at);
        CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
        CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
        CREATE INDEX IF NOT EXISTS idx_auth_codes_lookup ON auth_codes(email, code_type, used_at);
        CREATE INDEX IF NOT EXISTS idx_api_tokens_hash ON api_tokens(token_hash);
        CREATE INDEX IF NOT EXISTS idx_device_codes_user ON device_codes(user_code, status);
        CREATE INDEX IF NOT EXISTS idx_registered_apps_slug ON auth_registered_apps(slug);
        CREATE INDEX IF NOT EXISTS idx_user_grants_lookup ON auth_user_grants(user_id, app_id, is_revoked);
        CREATE INDEX IF NOT EXISTS idx_mail_spool_pending ON mail_spool (status, priority, scheduled_at);
        "#,
    )?;

    // Seed default settings if missing
    let defaults = [
        ("maintenance_mode", "false"),
        ("notification_email", ""),
        ("site_theme", "classic"),
        ("report_recipient_email", "me@faizal.uk"),
        ("report_enabled", "true"),
        ("report_time", "08:00"),
        ("resend_api_key", ""),
        ("mail_active_tier", "tier1"),
        ("mail_preferred_cloud", "resend"),
    ];

    for (k, v) in defaults {
        let _ = conn.execute(
            "INSERT OR IGNORE INTO settings (key, value) VALUES (?1, ?2)",
            params![k, v],
        );
    }

    // Seed initial projects if table is empty
    let project_count: i64 = conn.query_row(
        "SELECT COUNT(*) FROM projects",
        [],
        |row| row.get(0),
    ).unwrap_or(0);

    if project_count == 0 {
        let initial_projects = [
            ("Heritage Manufacturing Operations", "Overseeing daily production at UCA — maintaining traditional ayurvedic methods while ensuring modern quality standards.", "https://faizal.uk"),
            ("Digital Modernization", "Introduced inventory management systems, production tracking, and data analytics to streamline business operations.", "https://faizal.uk"),
            ("Market Expansion & Branding", "Developed new packaging and branding strategies to expand UCA’s reach while staying true to its legacy.", "https://faizal.uk"),
        ];

        for (title, desc, link) in initial_projects {
            let _ = conn.execute(
                "INSERT INTO projects (title, description, link) VALUES (?1, ?2, ?3)",
                params![title, desc, link],
            );
        }
    }

    // Seed default first-party ecosystem applications if empty
    let app_count: i64 = conn.query_row(
        "SELECT COUNT(*) FROM auth_registered_apps",
        [],
        |row| row.get(0),
    ).unwrap_or(0);

    if app_count == 0 {
        let initial_apps = [
            ("app_macos_suite", "macOS Business Suite", "macos-suite", "Production billing, orders, ERP and inventory operations.", "[\"https://mac.faizal.uk/callback\", \"http://localhost:5173/callback\"]", "[\"profile:read\", \"business:read\", \"business:write\"]", 1),
            ("app_nexus_cli", "Nexus Fleet CLI Agent", "nexus-cli", "Autonomous node telemetry daemon, AI compute bridge and remote console.", "[\"http://localhost:3002/callback\"]", "[\"profile:read\", \"fleet:read\", \"fleet:control\", \"ai:compute\"]", 1),
            ("app_zentra", "Zentra Analytics Dashboard", "zentra-analytics", "Deep business intelligence, revenue metrics, and customer pattern analysis.", "[\"https://zentra.faizal.uk/callback\"]", "[\"profile:read\", \"business:read\"]", 1),
            ("app_wife_mobile", "Wife Mobile Companion", "wife-companion", "Secure mobile interface for live orders, notifications, and store status.", "[\"nexus-mobile://auth-callback\"]", "[\"profile:read\", \"business:read\"]", 1),
            ("app_minecraft", "Minecraft Cloud Gateway", "minecraft-portal", "Distributed server orchestrator and edge tunnel management console.", "[\"https://minecraft.faizal.uk/callback\"]", "[\"profile:read\", \"minecraft:admin\"]", 1),
            ("app_web_admin", "Nexus Master Admin Console", "web-admin", "Central fleet commanding, IP firewalling, and identity governance.", "[\"https://faizal.uk/admin\", \"http://localhost:5173/admin\"]", "[\"*\"]", 1),
        ];

        for (id, name, slug, desc, uris, scopes, first_party) in initial_apps {
            let _ = conn.execute(
                r#"
                INSERT INTO auth_registered_apps (id, name, slug, description, allowed_redirect_uris, allowed_scopes, is_first_party, is_enabled)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, 1)
                "#,
                params![id, name, slug, desc, uris, scopes, first_party],
            );
        }
    }

    Ok(())
}

pub async fn is_ip_banned(db: &DbPool, ip: &str) -> bool {
    let conn = db.lock().await;
    let mut stmt = match conn.prepare("SELECT 1 FROM banned_ips WHERE ip = ?1") {
        Ok(s) => s,
        Err(_) => return false,
    };
    stmt.exists(params![ip]).unwrap_or(false)
}

pub async fn track_visitor(db: &DbPool, ip: &str, user_agent: &str, path: &str) {
    let conn = db.lock().await;
    if let Err(e) = conn.execute(
        "INSERT INTO visitors (ip, user_agent, path) VALUES (?1, ?2, ?3)",
        params![ip, user_agent, path],
    ) {
        error!("Visitor tracking error: {}", e);
    }
}

pub async fn get_setting(db: &DbPool, key: &str) -> Option<String> {
    let conn = db.lock().await;
    conn.query_row(
        "SELECT value FROM settings WHERE key = ?1",
        params![key],
        |row| row.get::<_, String>(0),
    ).ok()
}

pub async fn set_setting(db: &DbPool, key: &str, value: &str) -> Result<()> {
    let conn = db.lock().await;
    conn.execute(
        "INSERT OR REPLACE INTO settings (key, value) VALUES (?1, ?2)",
        params![key, value],
    )?;
    Ok(())
}

// ─── Resilient Out-of-Band Mail Spool (Store-and-Forward DTN Buffer) ──────────

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct SpoolEntry {
    pub id: String,
    pub recipient: String,
    pub subject: String,
    pub html_body: String,
    pub text_body: Option<String>,
    pub message_type: String,
    pub status: String,
    pub priority: i32,
    pub attempts: i32,
    pub max_attempts: i32,
    pub last_error: Option<String>,
    pub dispatched_by: Option<String>,
    pub created_at: Option<String>,
    pub sent_at: Option<String>,
}

pub async fn enqueue_mail_spool(
    db: &DbPool,
    id: &str,
    recipient: &str,
    subject: &str,
    html_body: &str,
    text_body: Option<&str>,
    message_type: &str,
    priority: i32,
    last_error: Option<&str>,
) -> Result<()> {
    let conn = db.lock().await;
    conn.execute(
        r#"
        INSERT OR REPLACE INTO mail_spool (id, recipient, subject, html_body, text_body, message_type, status, priority, attempts, max_attempts, last_error, created_at, scheduled_at)
        VALUES (?1, ?2, ?3, ?4, ?5, ?6, 'pending', ?7, 0, 5, ?8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        "#,
        params![id, recipient, subject, html_body, text_body, message_type, priority, last_error],
    )?;
    Ok(())
}

pub async fn mark_mail_spool_sent(
    db: &DbPool,
    id: &str,
    dispatched_by: &str,
) -> Result<()> {
    let conn = db.lock().await;
    conn.execute(
        "UPDATE mail_spool SET status = 'sent', sent_at = CURRENT_TIMESTAMP, dispatched_by = ?1 WHERE id = ?2",
        params![dispatched_by, id],
    )?;
    Ok(())
}

pub async fn mark_mail_spool_relaying(
    db: &DbPool,
    id: &str,
    node_id: &str,
) -> Result<()> {
    let conn = db.lock().await;
    conn.execute(
        "UPDATE mail_spool SET status = 'relaying', dispatched_by = ?1, attempts = attempts + 1 WHERE id = ?2",
        params![node_id, id],
    )?;
    Ok(())
}

pub async fn mark_mail_spool_failed(
    db: &DbPool,
    id: &str,
    error: &str,
) -> Result<()> {
    let conn = db.lock().await;
    conn.execute(
        r#"
        UPDATE mail_spool 
        SET status = CASE WHEN attempts + 1 >= max_attempts THEN 'failed' ELSE 'pending' END,
            attempts = attempts + 1,
            last_error = ?1,
            scheduled_at = datetime('now', '+2 minutes')
        WHERE id = ?2
        "#,
        params![error, id],
    )?;
    Ok(())
}

pub async fn get_pending_spool(db: &DbPool, limit: usize) -> Result<Vec<SpoolEntry>> {
    let conn = db.lock().await;
    let mut stmt = conn.prepare(
        r#"
        SELECT id, recipient, subject, html_body, text_body, message_type, status, priority, attempts, max_attempts, last_error, dispatched_by, created_at, sent_at
        FROM mail_spool
        WHERE status = 'pending' AND scheduled_at <= CURRENT_TIMESTAMP
        ORDER BY priority ASC, created_at ASC
        LIMIT ?1
        "#,
    )?;

    let rows = stmt.query_map(params![limit as i64], |row| {
        Ok(SpoolEntry {
            id: row.get(0)?,
            recipient: row.get(1)?,
            subject: row.get(2)?,
            html_body: row.get(3)?,
            text_body: row.get(4)?,
            message_type: row.get(5)?,
            status: row.get(6)?,
            priority: row.get(7)?,
            attempts: row.get(8)?,
            max_attempts: row.get(9)?,
            last_error: row.get(10)?,
            dispatched_by: row.get(11)?,
            created_at: row.get(12)?,
            sent_at: row.get(13)?,
        })
    })?;

    let mut entries = Vec::new();
    for r in rows {
        if let Ok(entry) = r {
            entries.push(entry);
        }
    }
    Ok(entries)
}

pub async fn get_node_relay_success_rate(db: &DbPool, node_id: &str) -> f32 {
    let conn = db.lock().await;
    let total: i64 = conn.query_row(
        "SELECT COUNT(*) FROM mail_spool WHERE dispatched_by = ?1",
        params![node_id],
        |r| r.get(0),
    ).unwrap_or(0);

    if total == 0 {
        return 85.0; // Baseline default for unproven nodes
    }

    let sent: i64 = conn.query_row(
        "SELECT COUNT(*) FROM mail_spool WHERE dispatched_by = ?1 AND status = 'sent'",
        params![node_id],
        |r| r.get(0),
    ).unwrap_or(0);

    (sent as f32 / total as f32 * 100.0).clamp(0.0, 100.0)
}

pub async fn get_spool_stats(db: &DbPool) -> (i64, i64, i64) {
    let conn = db.lock().await;
    let pending: i64 = conn.query_row(
        "SELECT COUNT(*) FROM mail_spool WHERE status = 'pending' OR status = 'relaying'",
        [],
        |r| r.get(0),
    ).unwrap_or(0);

    let sent: i64 = conn.query_row(
        "SELECT COUNT(*) FROM mail_spool WHERE status = 'sent'",
        [],
        |r| r.get(0),
    ).unwrap_or(0);

    let failed: i64 = conn.query_row(
        "SELECT COUNT(*) FROM mail_spool WHERE status = 'failed'",
        [],
        |r| r.get(0),
    ).unwrap_or(0);

    (pending, sent, failed)
}
