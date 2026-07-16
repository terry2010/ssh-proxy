package com.termfast.app.ui.screen

import android.app.Activity
import android.content.Context
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.termfast.app.data.RustRepository
import com.termfast.app.data.ServerConfig
import com.termfast.app.data.SettingsRepository
import com.termfast.app.service.SshVpnService
import com.termfast.app.service.SshVpnTileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDetailScreen(navController: NavController, serverId: String) {
    val context = LocalContext.current
    val repo = remember { RustRepository }
    val settingsRepo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("disconnected") }
    var exitIp by remember { mutableStateOf<String?>(null) }
    var proxyRunning by remember { mutableStateOf(false) }
    var vpnRunning by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var serverConfig by remember { mutableStateOf<ServerConfig?>(null) }

    fun doStartVpn() {
        val settings = settingsRepo.load()
        val socks5Port = serverConfig?.proxy?.socks5_port ?: 1080
        SshVpnService.start(context, serverId, settings, socks5Port)
        SshVpnTileService.setLastServerId(context, serverId)
        vpnRunning = true
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            doStartVpn()
        }
    }

    fun toggleVpn() {
        if (vpnRunning) {
            SshVpnService.stop(context)
            vpnRunning = false
        } else {
            val prepare = VpnService.prepare(context)
            if (prepare != null) {
                vpnLauncher.launch(prepare)
            } else {
                doStartVpn()
            }
        }
    }

    LaunchedEffect(serverId) {
        withContext(Dispatchers.IO) {
            val s = repo.getServerStatus(serverId)
            val cfg = repo.getConfig()?.servers?.find { it.id == serverId }
            val pr = repo.isProxyRunning(serverId)
            val vpn = SshVpnService.isRunning(context)
            withContext(Dispatchers.Main) {
                status = s.status
                exitIp = s.exit_ip
                serverConfig = cfg
                vpnRunning = vpn
                proxyRunning = pr
            }
        }
    }

    // Refresh status when screen resumes (e.g. navigating back from list)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, serverId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val s = repo.getServerStatus(serverId)
                        val pr = repo.isProxyRunning(serverId)
                        val vpn = SshVpnService.isRunning(context)
                        withContext(Dispatchers.Main) {
                            status = s.status
                            exitIp = s.exit_ip
                            vpnRunning = vpn
                            proxyRunning = pr
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("服务器详情") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("server_edit/$serverId") }) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("概览") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("代理") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("触发器") })
            }
            when (tab) {
                0 -> OverviewTab(
                    serverId = serverId,
                    status = status,
                    exitIp = exitIp,
                    proxyRunning = proxyRunning,
                    vpnRunning = vpnRunning,
                    onVpnToggle = { toggleVpn() }
                )
                1 -> ProxyTab(
                    serverId = serverId,
                    serverConfig = serverConfig,
                    proxyRunning = proxyRunning,
                    onToggle = { run ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                if (run) {
                                    repo.startProxy(serverId, serverConfig?.proxy?.socks5_port ?: 1080, 0, 0)
                                } else {
                                    repo.stopProxy(serverId)
                                }
                            }
                            withContext(Dispatchers.Main) {
                                proxyRunning = run
                            }
                        }
                    },
                    onSaveTestUrl = { url ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                serverConfig?.let { cfg ->
                                    repo.saveServer(cfg.copy(test_url = url))
                                }
                            }
                        }
                    }
                )
                2 -> TriggerTab(
                    serverId = serverId,
                    onEdit = { t ->
                        navController.navigate("trigger_edit/${serverId}/${t.id}")
                    }
                )
            }
        }
    }
}

@Composable
private fun OverviewTab(
    serverId: String,
    status: String,
    exitIp: String?,
    proxyRunning: Boolean,
    vpnRunning: Boolean,
    onVpnToggle: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onVpnToggle,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(if (vpnRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (vpnRunning) "停止 VPN" else "启动 VPN")
        }
        ListItem(
            headlineContent = { Text("SSH 状态") },
            supportingContent = { Text(status) }
        )
        if (exitIp != null) {
            ListItem(
                headlineContent = { Text("出口 IP") },
                supportingContent = { Text(exitIp) }
            )
        }
        ListItem(
            headlineContent = { Text("代理") },
            supportingContent = { Text(if (proxyRunning) "运行中" else "已停止") }
        )
        ListItem(
            headlineContent = { Text("VPN") },
            supportingContent = { Text(if (vpnRunning) "运行中" else "已停止") }
        )
    }
}

@Composable
private fun ProxyTab(
    serverId: String,
    serverConfig: ServerConfig?,
    proxyRunning: Boolean,
    onToggle: (Boolean) -> Unit,
    onSaveTestUrl: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var socks5Port by remember { mutableStateOf("1080") }
    var testUrl by remember(serverConfig?.id) {
        mutableStateOf(serverConfig?.test_url ?: "https://google.com")
    }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = socks5Port, onValueChange = { socks5Port = it },
            label = { Text("SOCKS5 端口") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onToggle(!proxyRunning) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (proxyRunning) "停止代理" else "启动代理")
        }
        Text(
            "注意：此功能仅启动本机 SOCKS5 代理端口，不会启动 VPN。如需 VPN 上网，请使用「启动 VPN」按钮。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        if (proxyRunning) {
            Text("代理运行中，端口 $socks5Port", style = MaterialTheme.typography.bodyMedium)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text("测试地址", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = testUrl,
            onValueChange = { testUrl = it },
            label = { Text("URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        testing = true
                        testResult = null
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val conn = java.net.URL(testUrl.ifBlank { "https://google.com" })
                                    .openConnection() as java.net.HttpURLConnection
                                conn.connectTimeout = 5000
                                conn.readTimeout = 5000
                                conn.instanceFollowRedirects = false
                                conn.requestMethod = "GET"
                                val code = conn.responseCode
                                val start = System.currentTimeMillis()
                                testResult = if (code in 200..399) {
                                    "✓ $code (${System.currentTimeMillis() - start}ms)"
                                } else {
                                    "✗ HTTP $code"
                                }
                                conn.disconnect()
                            } catch (e: Exception) {
                                testResult = "✗ ${e.message ?: "失败"}"
                            }
                        }
                        testing = false
                    }
                },
                enabled = !testing,
                modifier = Modifier.weight(1f),
            ) {
                if (testing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("测试")
                }
            }
            OutlinedButton(
                onClick = { onSaveTestUrl(testUrl.ifBlank { "https://google.com" }) },
                modifier = Modifier.weight(1f),
            ) {
                Text("保存")
            }
        }
        if (testResult != null) {
            Text(testResult!!, style = MaterialTheme.typography.bodySmall)
        }
    }
}
