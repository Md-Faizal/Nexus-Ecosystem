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

        CREATE INDEX IF NOT EXISTS idx_metrics_hist_ts ON system_metrics_history(timestamp);
        CREATE INDEX IF NOT EXISTS idx_http_req_ts ON http_request_logs(timestamp);
        CREATE INDEX IF NOT EXISTS idx_fleet_chat_id ON fleet_chat_messages(chat_id, created_at);
        CREATE INDEX IF NOT EXISTS idx_fleet_ai_conv_id ON fleet_ai_messages(conversation_id);
        CREATE INDEX IF NOT EXISTS idx_fleet_crash_node_ts ON fleet_crash_reports(node_id, occurred_at);
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
