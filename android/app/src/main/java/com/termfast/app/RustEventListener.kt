package com.termfast.app

/**
 * Callback interface for events from the Rust core.
 * Called from native threads; callers must dispatch to main thread if needed.
 */
interface RustEventListener {
    /**
     * @param eventJson JSON string with a "type" tag, e.g.
     *   {"type":"server:status_changed","server_id":"...","status":"connected"}
     */
    fun onEvent(eventJson: String)
}