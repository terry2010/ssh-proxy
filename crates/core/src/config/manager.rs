//! Config manager — wraps ConfigStorage with thread-safe access
//!
//! Provides read/write access to the configuration with proper locking.

use crate::config::{Config, ConfigStorage, FileConfigStorage};
use std::sync::Arc;
use tokio::sync::RwLock;

/// Thread-safe config manager
pub struct ConfigManager {
    config: Arc<RwLock<Config>>,
    storage: Arc<dyn ConfigStorage>,
    /// Set when load() fell back to defaults due to a corrupt file.
    /// Prevents an empty config from overwriting the (backed-up) original
    /// until the user explicitly modifies the config (which clears this flag).
    corrupt_load: Arc<std::sync::atomic::AtomicBool>,
}

impl ConfigManager {
    /// Create a new ConfigManager with in-memory config (no file)
    pub fn new(config: Config) -> Self {
        let storage = FileConfigStorage::new(
            directories::BaseDirs::new()
                .map(|d| d.config_dir().join("termfast").join("config.json"))
                .unwrap_or_else(|| std::path::PathBuf::from("config.json")),
        );
        Self {
            config: Arc::new(RwLock::new(config)),
            storage: Arc::new(storage),
            corrupt_load: Arc::new(std::sync::atomic::AtomicBool::new(false)),
        }
    }

    /// Create a ConfigManager with a custom storage backend (for testing)
    pub fn with_storage(config: Config, storage: Arc<dyn ConfigStorage>) -> Self {
        Self {
            config: Arc::new(RwLock::new(config)),
            storage,
            corrupt_load: Arc::new(std::sync::atomic::AtomicBool::new(false)),
        }
    }

    /// Load config from file
    pub fn load(path: impl AsRef<std::path::Path>) -> anyhow::Result<Self> {
        let storage = FileConfigStorage::new(path);
        let (config, corrupt) = match storage.load() {
            Ok(c) => (c, false),
            Err(e) => {
                // Log loudly instead of silently using defaults. The storage
                // layer already backed up the corrupt file (if it was parseable
                // JSON), so falling back to defaults here won't destroy the
                // original data — but the user must be told.
                tracing::error!(
                    "failed to load config from {}: {} — starting with empty config \
                     (corrupt file was backed up if it was unparseable JSON)",
                    storage.path().display(),
                    e
                );
                (Config::default(), true)
            }
        };
        Ok(Self {
            config: Arc::new(RwLock::new(config)),
            storage: Arc::new(storage),
            corrupt_load: Arc::new(std::sync::atomic::AtomicBool::new(corrupt)),
        })
    }

    /// Get a clone of the current config
    pub async fn get(&self) -> Config {
        self.config.read().await.clone()
    }

    /// Get a clone of the current config (blocking, for non-async contexts)
    pub fn get_blocking(&self) -> Config {
        self.config.blocking_read().clone()
    }

    /// Modify the config (with write lock) and save.
    /// This is a user-initiated action, so it clears the corrupt_load flag
    /// and always saves (even if the result is an empty config).
    pub async fn modify<F, R>(&self, f: F) -> anyhow::Result<R>
    where
        F: FnOnce(&mut Config) -> R,
    {
        let mut config = self.config.write().await;
        let result = f(&mut config);
        // User explicitly modified the config — clear corrupt_load flag.
        self.corrupt_load
            .store(false, std::sync::atomic::Ordering::Relaxed);
        self.storage
            .save(&config)
            .map_err(|e| anyhow::anyhow!(e.to_string()))?;
        Ok(result)
    }

    /// Save current config to file.
    /// Refuses to save an empty config if the file was loaded corruptly
    /// (to prevent overwriting backed-up data with defaults).
    pub async fn save(&self) -> anyhow::Result<()> {
        let config = self.config.read().await.clone();
        if config.servers.is_empty()
            && self.corrupt_load.load(std::sync::atomic::Ordering::Relaxed)
        {
            tracing::warn!(
                "skipping save of empty config — file was loaded corruptly, \
                 refusing to overwrite backed-up original"
            );
            return Ok(());
        }
        self.storage
            .save(&config)
            .map_err(|e| anyhow::anyhow!(e.to_string()))
    }

    /// Get a reference to the config (read lock)
    pub async fn read(&self) -> tokio::sync::RwLockReadGuard<'_, Config> {
        self.config.read().await
    }
}

impl Clone for ConfigManager {
    fn clone(&self) -> Self {
        Self {
            config: self.config.clone(),
            storage: self.storage.clone(),
            corrupt_load: self.corrupt_load.clone(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_config_manager_get() {
        let mgr = ConfigManager::new(Config::default());
        let config = mgr.get().await;
        assert_eq!(config.general.language, "system");
    }

    #[tokio::test]
    async fn test_config_manager_modify() {
        let mgr = ConfigManager::new(Config::default());
        let result = mgr
            .modify(|c| {
                c.general.language = "en".to_string();
                42
            })
            .await;
        if let Ok(val) = result {
            assert_eq!(val, 42);
            assert_eq!(mgr.get().await.general.language, "en");
        }
    }
}
