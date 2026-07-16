package com.termfast.app.ui.screen

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.termfast.app.data.RustRepository
import com.termfast.app.data.ServerConfig
import com.termfast.app.data.ServerStatus
import com.termfast.app.data.SettingsRepository
import com.termfast.app.service.SshVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun ServerListScreen(navController: NavController) {
    val repo = remember { RustRepository }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    var servers by remember { mutableStateOf<List<ServerConfig>>(emptyList()) }
    var statuses by remember { mutableStateOf<Map<String, ServerStatus>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var vpnRunning by remember { mutableStateOf(SshVpnService.isRunning(context)) }
    var vpnStarting by remember { mutableStateOf(SshVpnService.isStarting(context)) }
    var pendingVpnServer by remember { mutableStateOf<ServerConfig?>(null) }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingVpnServer?.let { server ->
                val settings = settingsRepo.load()
                val socks5Port = server.proxy?.socks5_port ?: 1080
                SshVpnService.start(context, server.id, settings, socks5Port)
                vpnRunning = true
            }
        }
        pendingVpnServer = null
    }

    fun startVpn(server: ServerConfig) {
        val prepare = VpnService.prepare(context)
        if (prepare != null) {
            pendingVpnServer = server
            vpnLauncher.launch(prepare)
        } else {
            val settings = settingsRepo.load()
            val socks5Port = server.proxy?.socks5_port ?: 1080
            SshVpnService.start(context, server.id, settings, socks5Port)
            vpnRunning = true
        }
    }

    fun refresh() {
        scope.launch {
            withContext(Dispatchers.IO) {
                val list = repo.listServers()
                val st = list.associate { it.id to repo.getServerStatus(it.id) }
                val vpn = SshVpnService.isRunning(context)
                val starting = SshVpnService.isStarting(context)
                withContext(Dispatchers.Main) {
                    servers = list
                    statuses = st
                    vpnRunning = vpn
                    vpnStarting = starting
                    loading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // Poll VPN state every 500ms so UI updates when startup completes
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            val running = SshVpnService.isRunning(context)
            val starting = SshVpnService.isStarting(context)
            if (running != vpnRunning || starting != vpnStarting) {
                vpnRunning = running
                vpnStarting = starting
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vpnRunning = SshVpnService.isRunning(context)
                vpnStarting = SshVpnService.isStarting(context)
                refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("server_add") }) {
                Icon(Icons.Filled.Add, contentDescription = "添加服务器")
            }
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (servers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有服务器", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("点击右下角 + 添加", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(servers, key = { it.id }) { server ->
                    var testResult by remember { mutableStateOf<String?>(null) }
                    var testing by remember { mutableStateOf(false) }
                    ServerCard(
                        server = server,
                        status = statuses[server.id],
                        vpnRunning = vpnRunning,
                        vpnStarting = vpnStarting,
                        testResult = testResult,
                        testing = testing,
                        onVpnToggle = {
                            if (vpnRunning || vpnStarting) {
                                SshVpnService.stop(context)
                                vpnRunning = false
                                vpnStarting = false
                            } else {
                                vpnStarting = true
                                startVpn(server)
                            }
                        },
                        onTest = {
                            scope.launch {
                                testing = true
                                testResult = null
                                withContext(Dispatchers.IO) {
                                    try {
                                        val testUrl = server.test_url.ifBlank { "https://google.com" }
                                        val conn = URL(testUrl).openConnection() as HttpURLConnection
                                        conn.connectTimeout = 5000
                                        conn.readTimeout = 5000
                                        conn.instanceFollowRedirects = false
                                        conn.requestMethod = "GET"
                                        val code = conn.responseCode
                                        val latency = System.currentTimeMillis()
                                        testResult = if (code in 200..399) {
                                            "✓ $code (${System.currentTimeMillis() - latency}ms)"
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
                        onClick = { navController.navigate("server_detail/${server.id}") },
                        onDelete = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    repo.removeServer(server.id)
                                }
                                refresh()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerCard(
    server: ServerConfig,
    status: ServerStatus?,
    vpnRunning: Boolean,
    vpnStarting: Boolean,
    testResult: String?,
    testing: Boolean,
    onVpnToggle: () -> Unit,
    onTest: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(server.name.ifBlank { server.ssh.host }, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onTest, enabled = !testing) {
                    if (testing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Speed, contentDescription = "测试")
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("${server.ssh.host}:${server.ssh.port}", style = MaterialTheme.typography.bodySmall)
            if (status?.exit_ip != null) {
                Text("出口 IP: ${status.exit_ip}", style = MaterialTheme.typography.bodySmall)
            }
            if (testResult != null) {
                Text(testResult, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onVpnToggle,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    enabled = !vpnStarting
                ) {
                    if (vpnStarting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(if (vpnRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(if (vpnStarting) "连接中" else if (vpnRunning) "停止 VPN" else "启动 VPN")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                }
            }
        }
    }
}
