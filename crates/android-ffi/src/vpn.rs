//! VPN / tun2proxy integration.
//!
//! On Android, receives the raw TUN fd from `VpnService.establish()` and
//! runs `tun2proxy::general_run_async` to route TUN traffic through the
//! SOCKS5 proxy.

#[cfg(target_os = "android")]
use std::sync::OnceLock;

#[cfg(target_os = "android")]
use tun2proxy::CancellationToken;

/// Global shutdown token for the currently-running tun2proxy session.
#[cfg(target_os = "android")]
static VPN_TOKEN: OnceLock<std::sync::Mutex<Option<CancellationToken>>> = OnceLock::new();

/// Global task handle so we can await completion in stop_tun2proxy.
#[cfg(target_os = "android")]
static VPN_TASK: OnceLock<std::sync::Mutex<Option<tokio::task::JoinHandle<()>>>> = OnceLock::new();

#[cfg(target_os = "android")]
fn vpn_token() -> &'static std::sync::Mutex<Option<CancellationToken>> {
    VPN_TOKEN.get_or_init(|| std::sync::Mutex::new(None))
}

#[cfg(target_os = "android")]
fn vpn_task() -> &'static std::sync::Mutex<Option<tokio::task::JoinHandle<()>>> {
    VPN_TASK.get_or_init(|| std::sync::Mutex::new(None))
}

/// Store the tun2proxy task handle so stop_tun2proxy can await it.
#[cfg(target_os = "android")]
pub fn set_vpn_task(handle: tokio::task::JoinHandle<()>) {
    let mut lock = vpn_task().lock().unwrap();
    // If there's an existing task, abort it
    if let Some(old) = lock.take() {
        old.abort();
    }
    *lock = Some(handle);
}

/// Start tun2proxy with the given TUN fd, forwarding to a SOCKS5 proxy.
#[cfg(target_os = "android")]
pub async fn start_tun2proxy(
    tun_fd: i32,
    mtu: u16,
    socks5_port: u16,
    dns_strategy: &str,
    ipv6_enabled: bool,
) -> std::io::Result<()> {
    use tun2proxy::{Args, ArgProxy, ArgDns};

    // Note: do NOT call stop_tun2proxy() here — nativeStartVpn already calls
    // nativeStopVpn before starting, and calling stop here would deadlock
    // because the current task handle is already stored.

    let shutdown_token = CancellationToken::new();
    {
        let mut lock = vpn_token().lock().unwrap();
        *lock = Some(shutdown_token.clone());
    }

    let proxy_url = format!("socks5://127.0.0.1:{}", socks5_port);
    let proxy = ArgProxy::try_from(proxy_url.as_str())
        .map_err(|e| std::io::Error::other(format!("invalid proxy URL: {e}")))?;
    let dns = match dns_strategy {
        "over-udp" => ArgDns::Direct,
        "none" => ArgDns::Virtual,
        _ => ArgDns::OverTcp,
    };
    let mut args = Args::default();
    args.proxy(proxy)
        .tun_fd(Some(tun_fd))
        .close_fd_on_drop(false)
        .dns(dns)
        .ipv6_enabled(ipv6_enabled);

    tracing::info!(
        "Starting tun2proxy: fd={}, mtu={}, proxy={}, dns={}, ipv6={}",
        tun_fd, mtu, proxy_url, dns_strategy, ipv6_enabled
    );

    // general_run_async blocks until the shutdown token is cancelled
    match tun2proxy::general_run_async(args, mtu, false, shutdown_token).await {
        Ok(sessions) => {
            tracing::info!("tun2proxy exited normally, sessions: {}", sessions);
            Ok(())
        }
        Err(e) => {
            tracing::error!("tun2proxy error: {:?}", e);
            Err(e)
        }
    }
}

/// Stop the currently-running tun2proxy session (if any).
/// Cancels the shutdown token but does NOT wait for the task to finish —
/// the caller should close the TUN fd afterwards, which will cause
/// tun2proxy to exit on its own.
#[cfg(target_os = "android")]
pub async fn stop_tun2proxy() {
    let token = {
        let mut lock = vpn_token().lock().unwrap();
        lock.take()
    };
    if let Some(token) = token {
        token.cancel();
        tracing::info!("tun2proxy shutdown token cancelled");
    }
    // Also abort the task if still running
    let task = {
        let mut lock = vpn_task().lock().unwrap();
        lock.take()
    };
    if let Some(task) = task {
        task.abort();
    }
}

// === Non-Android stubs ===

#[cfg(not(target_os = "android"))]
pub async fn start_tun2proxy(_tun_fd: i32, _mtu: u16, _socks5_port: u16, _dns: &str, _ipv6: bool) -> std::io::Result<()> {
    Err(std::io::Error::other("tun2proxy only available on Android"))
}

#[cfg(not(target_os = "android"))]
pub async fn stop_tun2proxy() {}
