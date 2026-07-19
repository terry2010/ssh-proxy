<?php
/**
 * TermFast Cloud Sync Proxy
 * 
 * 极简单文件代理服务器，3个端点：
 *   GET  ?action=auth_url&provider=baidu&redirect_uri=oob
 *   POST ?action=exchange    body: {"provider","code","redirect_uri","code_verifier"}
 *   POST ?action=refresh     body: {"provider","refresh_token"}
 *
 * 服务器持有 app_secret，App 端不持有任何 secret。
 * 服务器只参与 token 交换，不接触用户数据。
 *
 * 部署：
 *   1. 放到服务器 /var/www/html/tools/cloud-sync.php
 *   2. Nginx 配置 fastcgi_param 传入环境变量：
 *        fastcgi_param DROPBOX_APP_KEY "xxx";
 *        fastcgi_param DROPBOX_APP_SECRET "xxx";
 *        fastcgi_param BAIDU_APP_KEY "xxx";
 *        fastcgi_param BAIDU_APP_SECRET "xxx";
 *      或在 PHP-FPM pool 配置 env[...] = "..."
 *   3. 确保 HTTPS（Let's Encrypt 免费证书）
 */

// === 配置：从环境变量读取，不硬编码 ===
$DROPBOX_APP_KEY    = getenv('DROPBOX_APP_KEY') ?: '';
$DROPBOX_APP_SECRET = getenv('DROPBOX_APP_SECRET') ?: '';
$BAIDU_APP_KEY      = getenv('BAIDU_APP_KEY') ?: '';
$BAIDU_APP_SECRET   = getenv('BAIDU_APP_SECRET') ?: '';

// === 安全：CORS — 只允许 Tauri webview origin 调用 ===
$ALLOWED_ORIGIN = 'https://tauri.localhost'; // Tauri webview origin
header("Access-Control-Allow-Origin: $ALLOWED_ORIGIN");
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

// Handle CORS preflight
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(204);
    exit;
}

// === 路由 ===
header('Content-Type: application/json; charset=utf-8');

$action = $_GET['action'] ?? '';
$method = $_SERVER['REQUEST_METHOD'];

try {
    if ($action === 'auth_url' && $method === 'GET') {
        handleAuthUrl();
    } elseif ($action === 'exchange' && $method === 'POST') {
        handleExchange();
    } elseif ($action === 'refresh' && $method === 'POST') {
        handleRefresh();
    } elseif ($action === 'ping' && $method === 'GET') {
        // 健康检查
        echo json_encode(['ok' => true, 'time' => time()]);
    } else {
        http_response_code(404);
        echo json_encode(['error' => 'unknown action']);
    }
} catch (Exception $e) {
    http_response_code(500);
    echo json_encode(['error' => $e->getMessage()]);
}

// === 端点实现 ===

/**
 * GET ?action=auth_url&provider=baidu&redirect_uri=oob
 * 
 * 返回授权 URL。App 本地生成 PKCE code_verifier（Dropbox），
 * 服务器只负责拼 URL（因为 app_key 在服务器上）。
 */
function handleAuthUrl() {
    global $DROPBOX_APP_KEY, $BAIDU_APP_KEY;
    
    $provider = $_GET['provider'] ?? '';
    $redirect_uri = $_GET['redirect_uri'] ?? 'oob';
    
    if ($provider === 'dropbox') {
        // Dropbox PKCE: code_verifier 由 App 本地生成，code_challenge 由 App 算好传过来
        $code_challenge = $_GET['code_challenge'] ?? '';
        if (!$code_challenge) {
            http_response_code(400);
            echo json_encode(['error' => 'missing code_challenge for dropbox']);
            return;
        }
        $url = sprintf(
            'https://www.dropbox.com/oauth2/authorize?client_id=%s&response_type=code&code_challenge=%s&code_challenge_method=S256&token_access_type=offline&redirect_uri=%s',
            urlencode($DROPBOX_APP_KEY),
            urlencode($code_challenge),
            urlencode($redirect_uri)
        );
        echo json_encode(['auth_url' => $url, 'provider' => 'dropbox']);
        
    } elseif ($provider === 'baidu') {
        // 百度 Authorization Code flow（有 refresh_token！不再用 implicit grant）
        $state = bin2hex(random_bytes(16));
        $url = sprintf(
            'https://openapi.baidu.com/oauth/2.0/authorize?response_type=code&client_id=%s&redirect_uri=%s&scope=basic,netdisk&display=mobile&state=%s',
            urlencode($BAIDU_APP_KEY),
            urlencode($redirect_uri),
            $state
        );
        echo json_encode(['auth_url' => $url, 'state' => $state, 'provider' => 'baidu']);
        
    } else {
        http_response_code(400);
        echo json_encode(['error' => 'unknown provider: ' . $provider]);
    }
}

/**
 * POST ?action=exchange
 * body: {"provider":"baidu","code":"xxx","redirect_uri":"oob","code_verifier":"xxx"}
 *
 * 服务器用 app_secret + code 换 token，返回给 App。
 * 百度 Authorization Code flow 返回 access_token + refresh_token（10年有效）。
 */
function handleExchange() {
    global $DROPBOX_APP_KEY, $DROPBOX_APP_SECRET, $BAIDU_APP_KEY, $BAIDU_APP_SECRET;
    
    $body = json_decode(file_get_contents('php://input'), true);
    if (!$body) {
        http_response_code(400);
        echo json_encode(['error' => 'invalid JSON body']);
        return;
    }
    
    $provider = $body['provider'] ?? '';
    $code = $body['code'] ?? '';
    $redirect_uri = $body['redirect_uri'] ?? 'oob';
    $code_verifier = $body['code_verifier'] ?? '';
    $state = $body['state'] ?? '';

    if (!$code) {
        http_response_code(400);
        echo json_encode(['error' => 'missing code']);
        return;
    }
    
    if ($provider === 'dropbox') {
        $params = http_build_query([
            'grant_type' => 'authorization_code',
            'code' => $code,
            'code_verifier' => $code_verifier,
            'client_id' => $DROPBOX_APP_KEY,
            'client_secret' => $DROPBOX_APP_SECRET,
            'redirect_uri' => $redirect_uri,
        ]);
        echo httpPost('https://api.dropboxapi.com/oauth2/token', $params);
        
    } elseif ($provider === 'baidu') {
        // Baidu doesn't validate state server-side in token exchange,
        // but we include it for client-side CSRF checking
        $params = http_build_query([
            'grant_type' => 'authorization_code',
            'code' => $code,
            'client_id' => $BAIDU_APP_KEY,
            'client_secret' => $BAIDU_APP_SECRET,
            'redirect_uri' => $redirect_uri,
        ]);
        echo httpPost('https://openapi.baidu.com/oauth/2.0/token', $params);
        
    } else {
        http_response_code(400);
        echo json_encode(['error' => 'unknown provider: ' . $provider]);
    }
}

/**
 * POST ?action=refresh
 * body: {"provider":"baidu","refresh_token":"xxx"}
 *
 * 用 refresh_token 换新的 access_token。
 * 百度 refresh_token 有效期 10 年，可实现自动续期。
 */
function handleRefresh() {
    global $DROPBOX_APP_KEY, $DROPBOX_APP_SECRET, $BAIDU_APP_KEY, $BAIDU_APP_SECRET;
    
    $body = json_decode(file_get_contents('php://input'), true);
    if (!$body) {
        http_response_code(400);
        echo json_encode(['error' => 'invalid JSON body']);
        return;
    }
    
    $provider = $body['provider'] ?? '';
    $refresh_token = $body['refresh_token'] ?? '';
    
    if (!$refresh_token) {
        http_response_code(400);
        echo json_encode(['error' => 'missing refresh_token']);
        return;
    }
    
    if ($provider === 'dropbox') {
        $params = http_build_query([
            'grant_type' => 'refresh_token',
            'refresh_token' => $refresh_token,
            'client_id' => $DROPBOX_APP_KEY,
            'client_secret' => $DROPBOX_APP_SECRET,
        ]);
        echo httpPost('https://api.dropboxapi.com/oauth2/token', $params);
        
    } elseif ($provider === 'baidu') {
        $params = http_build_query([
            'grant_type' => 'refresh_token',
            'refresh_token' => $refresh_token,
            'client_id' => $BAIDU_APP_KEY,
            'client_secret' => $BAIDU_APP_SECRET,
        ]);
        echo httpPost('https://openapi.baidu.com/oauth/2.0/token', $params);
        
    } else {
        http_response_code(400);
        echo json_encode(['error' => 'unknown provider: ' . $provider]);
    }
}

// === 工具函数 ===

/**
 * 发送 POST 请求，返回响应体。
 */
function httpPost($url, $params) {
    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_POST => true,
        CURLOPT_POSTFIELDS => $params,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_TIMEOUT => 15,
        CURLOPT_HTTPHEADER => [
            'Content-Type: application/x-www-form-urlencoded',
            'Accept: application/json',
        ],
    ]);
    
    $resp = curl_exec($ch);
    $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $err = curl_error($ch);
    curl_close($ch);
    
    if ($err) {
        http_response_code(502);
        return json_encode(['error' => 'upstream error: ' . $err]);
    }
    
    if ($code >= 400) {
        http_response_code($code);
    }
    
    return $resp ?: json_encode(['error' => 'empty response']);
}
