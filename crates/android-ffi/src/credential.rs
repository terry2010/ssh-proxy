//! Android credential store — file-based persistent singleton.
//!
//! Uses `FileCredentialStore` backed by a JSON file in the app's private
//! data directory. The store is initialized once with `init_credential_store`
//! and shared across all JNI calls via a static `OnceLock`.

use std::path::PathBuf;
use std::sync::OnceLock;
use termfast_credential::FileCredentialStore;

static CREDENTIAL_STORE: OnceLock<FileCredentialStore> = OnceLock::new();

/// Initialize the credential store with the app's data directory.
/// Must be called once after `nativeSetDataDir`.
pub fn init_credential_store(data_dir: &str) {
    let path = PathBuf::from(data_dir).join("credentials.json");
    let _ = CREDENTIAL_STORE.set(FileCredentialStore::new(path));
}

/// Get the singleton credential store. Falls back to a temporary in-memory
/// store if `init_credential_store` was never called (e.g. in tests).
pub fn android_credential_store() -> &'static FileCredentialStore {
    CREDENTIAL_STORE.get_or_init(|| {
        // Fallback: use a temp directory (should not happen in production)
        let path = std::env::temp_dir().join("termfast_credentials_fallback.json");
        FileCredentialStore::new(path)
    })
}
