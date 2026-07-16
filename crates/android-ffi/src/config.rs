//! Android ConfigStorage implementation.
//!
//! On Android, config and runtime state are persisted under the app private
//! directory passed in from Kotlin (`context.getFilesDir()`).

use std::path::PathBuf;
use termfast_core::config::{ConfigManager, RuntimeStateManager};

pub fn config_manager_for_dir(dir: PathBuf) -> anyhow::Result<ConfigManager> {
    let config_path = dir.join("config.json");
    let cm = ConfigManager::load(&config_path)?;
    Ok(cm)
}

pub fn runtime_state_manager_for_dir(dir: PathBuf) -> RuntimeStateManager {
    RuntimeStateManager::new(dir.join("runtime_state.json"))
}
