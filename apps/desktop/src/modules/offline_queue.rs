use serde::{Deserialize, Serialize};
use std::fs::{File, OpenOptions};
use std::io::{BufRead, BufReader, Write};
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

/// Represents an offline action or telemetry payload queued for store-and-forward dispatch.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct QueuedMessage {
    pub id: String,
    pub message_type: String,
    pub payload: serde_json::Value,
    pub timestamp: u64,
    pub retry_count: u32,
    pub max_retries: u32,
}

/// Offline DTN Queue providing persistent local write-ahead storage for fleet telemetry
/// and agent commands during network disconnections.
pub struct OfflineQueue {
    wal_path: PathBuf,
    items: Vec<QueuedMessage>,
}

impl OfflineQueue {
    /// Initializes or recovers the offline queue from a local WAL file path.
    pub fn new<P: AsRef<Path>>(wal_path: P) -> Self {
        let path = wal_path.as_ref().to_path_buf();
        let items = Self::load_from_disk(&path);
        Self {
            wal_path: path,
            items,
        }
    }

    /// Load existing messages from the WAL file.
    fn load_from_disk(path: &Path) -> Vec<QueuedMessage> {
        let mut messages = Vec::new();
        if !path.exists() {
            return messages;
        }

        if let Ok(file) = File::open(path) {
            let reader = BufReader::new(file);
            for line in reader.lines().flatten() {
                let trimmed = line.trim();
                if trimmed.is_empty() {
                    continue;
                }
                if let Ok(msg) = serde_json::from_str::<QueuedMessage>(trimmed) {
                    messages.push(msg);
                }
            }
        }
        messages
    }

    /// Persist all active queue items back to the WAL file.
    fn sync_to_disk(&self) -> Result<(), std::io::Error> {
        if let Some(parent) = self.wal_path.parent() {
            std::fs::create_dir_all(parent)?;
        }

        let mut file = OpenOptions::new()
            .create(true)
            .write(true)
            .truncate(true)
            .open(&self.wal_path)?;

        for item in &self.items {
            let serialized = serde_json::to_string(item).map_err(|e| {
                std::io::Error::new(std::io::ErrorKind::InvalidData, e.to_string())
            })?;
            writeln!(file, "{}", serialized)?;
        }
        file.flush()?;
        Ok(())
    }

    /// Enqueue a new payload for store-and-forward delivery.
    pub fn enqueue(
        &mut self,
        message_type: &str,
        payload: serde_json::Value,
    ) -> Result<String, std::io::Error> {
        let timestamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();

        let id = format!("{}_{}", timestamp, self.items.len() + 1);

        let msg = QueuedMessage {
            id: id.clone(),
            message_type: message_type.to_string(),
            payload,
            timestamp,
            retry_count: 0,
            max_retries: 5,
        };

        // Append to in-memory store
        self.items.push(msg.clone());

        // Fast append to WAL file
        if let Some(parent) = self.wal_path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let mut file = OpenOptions::new()
            .create(true)
            .append(true)
            .open(&self.wal_path)?;

        let serialized = serde_json::to_string(&msg).map_err(|e| {
            std::io::Error::new(std::io::ErrorKind::InvalidData, e.to_string())
        })?;
        writeln!(file, "{}", serialized)?;
        file.flush()?;

        Ok(id)
    }

    /// Retrieve pending queued messages ready for dispatch.
    pub fn pending_messages(&self) -> &[QueuedMessage] {
        &self.items
    }

    /// Number of items currently queued.
    pub fn len(&self) -> usize {
        self.items.len()
    }

    /// Is queue empty.
    pub fn is_empty(&self) -> bool {
        self.items.is_empty()
    }

    /// Mark successfully transmitted messages and remove them from disk WAL.
    pub fn acknowledge(&mut self, ids: &[String]) -> Result<usize, std::io::Error> {
        let initial_len = self.items.len();
        self.items.retain(|item| !ids.contains(&item.id));
        let removed = initial_len - self.items.len();

        if removed > 0 {
            self.sync_to_disk()?;
        }
        Ok(removed)
    }

    /// Record a failed delivery attempt for a message ID. Purges if max_retries exceeded.
    pub fn record_failure(&mut self, id: &str) -> Result<bool, std::io::Error> {
        let mut should_sync = false;
        let mut purged = false;

        self.items.retain_mut(|item| {
            if item.id == id {
                item.retry_count += 1;
                should_sync = true;
                if item.retry_count >= item.max_retries {
                    purged = true;
                    return false; // Drop message after exceeding retries
                }
            }
            true
        });

        if should_sync {
            self.sync_to_disk()?;
        }

        Ok(!purged)
    }

    /// Clear all messages from memory and disk.
    pub fn clear(&mut self) -> Result<(), std::io::Error> {
        self.items.clear();
        self.sync_to_disk()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_offline_queue_enqueue_and_drain() {
        let temp_dir = std::env::temp_dir().join("nexus_test_dtn_queue");
        let _ = std::fs::remove_dir_all(&temp_dir);
        let wal_path = temp_dir.join("offline_queue.wal");

        let mut queue = OfflineQueue::new(&wal_path);
        assert_eq!(queue.len(), 0);

        let id1 = queue
            .enqueue("telemetry", serde_json::json!({"cpu": 12.5}))
            .expect("enqueue 1 failed");
        let id2 = queue
            .enqueue("command_ack", serde_json::json!({"cmd": "restart", "code": 0}))
            .expect("enqueue 2 failed");

        assert_eq!(queue.len(), 2);
        assert_eq!(queue.pending_messages()[0].id, id1);
        assert_eq!(queue.pending_messages()[1].id, id2);

        // Simulate crash & recovery by re-initializing from disk WAL
        let mut recovered_queue = OfflineQueue::new(&wal_path);
        assert_eq!(recovered_queue.len(), 2);

        // Acknowledge first message
        let drained = recovered_queue.acknowledge(&[id1]).expect("ack failed");
        assert_eq!(drained, 1);
        assert_eq!(recovered_queue.len(), 1);
        assert_eq!(recovered_queue.pending_messages()[0].id, id2);

        // Clean up
        let _ = std::fs::remove_dir_all(&temp_dir);
    }
}
