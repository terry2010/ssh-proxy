<?php
/**
 * TermFast Cloud Sync OAuth Callback (Mobile)
 *
 * OAuth providers (Dropbox/Baidu) require redirect_uri to be https:// or
 * localhost for the Authorization Code flow. Custom URI schemes like
 * termfast:// are not accepted by Dropbox's code flow.
 *
 * This script acts as a server-side relay: the provider redirects here with
 * ?code=xxx&state=xxx, and we respond with an HTML page that uses JavaScript
 * to redirect the browser to termfast://oauth/callback?code=xxx&state=xxx,
 * which Android catches via a deep-link intent filter.
 *
 * The Android app then passes the code to the Rust FFI layer, which calls
 * cloud-sync.php?action=exchange to complete the token exchange.
 *
 * Security notes:
 * - code and state are reflected into the URL after URL-encoding; no HTML
 *   injection is possible because we use rawurlencode and build the URL
 *   programmatically (no innerHTML).
 * - This script does NOT log or store the code; it only relays it.
 * - state is verified by the app (Rust side stores the expected state).
 */

// === 安全响应头 ===
header('X-Content-Type-Options: nosniff');
header('X-Frame-Options: DENY');
header('Cache-Control: no-store');

// === 提取 code 和 state ===
$code  = $_GET['code']  ?? '';
$state = $_GET['state'] ?? '';
$error = $_GET['error'] ?? '';

// === 构造 deep link URL ===
// termfast://oauth/callback?code=xxx&state=xxx
$params = [];
if ($code  !== '') { $params[] = 'code='  . rawurlencode($code);  }
if ($state !== '') { $params[] = 'state=' . rawurlencode($state); }
if ($error !== '') { $params[] = 'error=' . rawurlencode($error); }
$deep_link = 'termfast://oauth/callback' . ($params ? '?' . implode('&', $params) : '');

// === 输出 HTML 页面，用 JS 重定向到 deep link ===
// 用 location.replace 避免在浏览器历史中留下回调页。
// 如果 deep link 失败（app 未安装），显示提示信息。
header('Content-Type: text/html; charset=utf-8');
?>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>TermFast 云同步授权</title>
<style>
body { font-family: -apple-system, sans-serif; text-align: center; padding: 2em; color: #333; }
h2 { margin-top: 2em; }
</style>
</head>
<body>
<h2>正在返回 TermFast…</h2>
<p>授权完成，正在跳转回 App。</p>
<p id="fallback" style="display:none; color:#c00;">如果未自动跳转，请确保 TermFast 已安装，然后<a href="<?php echo htmlspecialchars($deep_link, ENT_QUOTES, 'UTF-8'); ?>">点击这里手动跳转</a>。</p>
<script>
(function() {
  var link = <?php echo json_encode($deep_link); ?>;
  // 尝试跳转到 deep link
  window.location.replace(link);
  // 2 秒后显示手动跳转链接（如果自动跳转失败）
  setTimeout(function() {
    document.getElementById('fallback').style.display = 'block';
  }, 2000);
})();
</script>
</body>
</html>
