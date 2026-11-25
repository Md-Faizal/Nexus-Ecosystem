use rusqlite::{params, Connection};
use std::path::Path;
use std::sync::{Arc, Mutex};
use thiserror::Error;

#[derive(Debug, Error)]
pub enum StorageError {
    #[error("sqlite error: {0}")]
    Sqlite(#[from] rusqlite::Error),
    #[error("lock poisoned")]
    LockPoisoned,
}

#[derive(Clone)]
pub struct LocalStore {
    conn: Arc<Mutex<Connection>>,
}

impl LocalStore {
    pub fn open(db_path: &Path) -> Result<Self, StorageError> {
        let conn = Connection::open(db_path)?;
        conn.pragma_update(None, "journal_mode", "WAL")?;
        conn.pragma_update(None, "synchronous", "NORMAL")?;
        conn.execute_batch(
            r#"
            CREATE TABLE IF NOT EXISTS offline_queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                kind TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                created_at_ms INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS kv_cache (
                key TEXT PRIMARY KEY,
                value_json TEXT NOT NULL,
                updated_at_ms INTEGER NOT NULL
            );
            "#,
        )?;
        Ok(Self {
            conn: Arc::new(Mutex::new(conn)),
        })
    }

    pub fn open_in_memory() -> Result<Self, StorageError> {
        Self::open(Path::new(":memory:"))
    }

    pub fn enqueue_event(&self, kind: &str, payload_json: &str, now_ms: i64) -> Result<i64, StorageError> {
        let guard = self.conn.lock().map_err(|_| StorageError::LockPoisoned)?;
        guard.execute(
            "INSERT INTO offline_queue (kind, payload_json, created_at_ms) VALUES (?1, ?2, ?3)",
            params![kind, payload_json, now_ms],
        )?;
        Ok(guard.last_insert_rowid())
    }

    pub fn pending_count(&self) -> Result<u64, StorageError> {
        let guard = self.conn.lock().map_err(|_| StorageError::LockPoisoned)?;
        let count: i64 = guard.query_row("SELECT COUNT(*) FROM offline_queue", [], |r| r.get(0))?;
        Ok(count.max(0) as u64)
    }

    pub fn drain_batch(&self, limit: usize) -> Result<Vec<(i64, String, String)>, StorageError> {
        let guard = self.conn.lock().map_err(|_| StorageError::LockPoisoned)?;
        let mut stmt = guard.prepare(
            "SELECT id, kind, payload_json FROM offline_queue ORDER BY id ASC LIMIT ?1",
        )?;
        let rows = stmt
            .query_map(params![limit as i64], |r| {
                Ok((r.get(0)?, r.get(1)?, r.get(2)?))
            })?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(rows)
    }

    pub fn delete_events(&self, ids: &[i64]) -> Result<(), StorageError> {
        if ids.is_empty() {
            return Ok(());
        }
        let mut guard = self.conn.lock().map_err(|_| StorageError::LockPoisoned)?;
        let tx = guard.transaction()?;
        {
            let mut stmt = tx.prepare("DELETE FROM offline_queue WHERE id = ?1")?;
            for id in ids {
                stmt.execute(params![id])?;
            }
        }
        tx.commit()?;
        Ok(())
    }

    pub fn put_cache(&self, key: &str, value_json: &str, now_ms: i64) -> Result<(), StorageError> {
        let guard = self.conn.lock().map_err(|_| StorageError::LockPoisoned)?;
        guard.execute(
            r#"INSERT INTO kv_cache (key, value_json, updated_at_ms)
               VALUES (?1, ?2, ?3)
               ON CONFLICT(key) DO UPDATE SET
                 value_json = excluded.value_json,
                 updated_at_ms = excluded.updated_at_ms"#,
            params![key, value_json, now_ms],
        )?;
        Ok(())
    }

    pub fn get_cache(&self, key: &str) -> Result<Option<String>, StorageError> {
        let guard = self.conn.lock().map_err(|_| StorageError::LockPoisoned)?;
        let mut stmt = guard.prepare("SELECT value_json FROM kv_cache WHERE key = ?1")?;
        let mut rows = stmt.query(params![key])?;
        if let Some(row) = rows.next()? {
            Ok(Some(row.get(0)?))
        } else {
            Ok(None)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn offline_queue_enqueue_drain_delete() {
        let store = LocalStore::open_in_memory().expect("open memory db");
        assert_eq!(store.pending_count().unwrap(), 0);

        let id1 = store.enqueue_event("telemetry", "{\"battery_pct\":85}", 1000).unwrap();
        let id2 = store.enqueue_event("telemetry", "{\"battery_pct\":84}", 2000).unwrap();
        assert_eq!(store.pending_count().unwrap(), 2);

        let batch = store.drain_batch(10).unwrap();
        assert_eq!(batch.len(), 2);
        assert_eq!(batch[0].0, id1);
        assert_eq!(batch[1].0, id2);

        store.delete_events(&[id1]).unwrap();
        assert_eq!(store.pending_count().unwrap(), 1);
    }

    #[test]
    fn kv_cache_upsert_and_read() {
        let store = LocalStore::open_in_memory().expect("open memory db");
        assert_eq!(store.get_cache("nodes").unwrap(), None);
        store.put_cache("nodes", "[{\"node_id\":\"mac\"}]", 1000).unwrap();
        assert_eq!(store.get_cache("nodes").unwrap().as_deref(), Some("[{\"node_id\":\"mac\"}]"));
        store.put_cache("nodes", "[]", 2000).unwrap();
        assert_eq!(store.get_cache("nodes").unwrap().as_deref(), Some("[]"));
    }
}
