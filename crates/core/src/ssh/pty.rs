//! SSH PTY — interactive terminal sessions
//!
//! Opens a PTY channel on an existing SSH connection and requests a shell.
//! Used by the terminal feature in the GUI.

use crate::error::{Error, Result};
use russh::client;
use russh::Channel;

/// Open a session channel, request a PTY, then request a shell.
///
/// Returns the channel ready for bidirectional data flow.
/// The caller is responsible for reading `ChannelMsg::Data` from the channel
/// and writing input via `data_bytes()`.
///
/// This follows the canonical russh interactive pattern (see russh's
/// `client_exec_interactive` example): `request_pty` is sent with
/// `want_reply = false` so the server applies the PTY silently, then
/// `request_shell` is sent with `want_reply = true`. Terminal modes are
/// passed as empty so the server uses its defaults (ECHO, ICANON, etc.) —
/// this is the safest choice across server implementations.
///
/// A PTY is **required** for a usable interactive terminal: without one the
/// remote shell runs non-interactively (no prompt, no echo) and — critically
/// — stdout is fully buffered because it is not a tty, so command output is
/// never flushed and the terminal appears as a dead black box.
pub async fn open_pty_shell(
    handle: &client::Handle<super::client::SshHandler>,
    cols: u32,
    rows: u32,
) -> Result<Channel<client::Msg>> {
    let channel = handle
        .channel_open_session()
        .await
        .map_err(|e| Error::Ssh(format!("failed to open session channel: {}", e)))?;

    let term = "xterm-256color";
    tracing::info!("requesting PTY: cols={}, rows={}", cols, rows);
    channel
        .request_pty(false, term, cols, rows, 0, 0, &[])
        .await
        .map_err(|e| Error::Ssh(format!("failed to request PTY: {}", e)))?;

    tracing::info!("PTY requested, now requesting shell...");
    channel
        .request_shell(true)
        .await
        .map_err(|e| Error::Ssh(format!("failed to request shell: {}", e)))?;

    tracing::info!("shell requested with PTY, channel ready (id={})", channel.id());
    Ok(channel)
}

/// Open a session channel and execute an interactive shell command.
/// Fallback for servers that don't allow request_shell.
pub async fn open_shell_via_exec(
    handle: &client::Handle<super::client::SshHandler>,
) -> Result<Channel<client::Msg>> {
    let channel = handle
        .channel_open_session()
        .await
        .map_err(|e| Error::Ssh(format!("failed to open session channel: {}", e)))?;

    tracing::info!("requesting shell via exec(bash -i)...");
    channel
        .exec(true, "bash -i 2>&1 || sh -i 2>&1 || /bin/bash -i 2>&1")
        .await
        .map_err(|e| Error::Ssh(format!("failed to exec shell: {}", e)))?;

    tracing::info!("shell exec requested, channel ready (id={})", channel.id());
    Ok(channel)
}

/// Send a window-change request to resize the PTY.
pub async fn resize_pty(
    channel: &Channel<client::Msg>,
    cols: u32,
    rows: u32,
) -> Result<()> {
    channel
        .window_change(cols, rows, 0, 0)
        .await
        .map_err(|e| Error::Ssh(format!("failed to resize PTY: {}", e)))
}

// === SECTION 1 END ===

#[cfg(test)]
mod tests {
    // PTY operations require a real SSH server with PTY support.
    // No unit tests here — the helper functions are thin wrappers around
    // russh channel methods. Integration testing requires a live SSH server.
}
