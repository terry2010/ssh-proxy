package com.termfast.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.termfast.app.data.AppSettings
import com.termfast.app.data.SettingsRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    var settings by remember { mutableStateOf(repo.load()) }

    fun update(block: AppSettings.() -> AppSettings) {
        val s = settings.block()
        settings = s
        repo.save(s)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("通用", style = MaterialTheme.typography.titleMedium)
        ListItem(
            headlineContent = { Text("主题") },
            supportingContent = { Text(settings.theme) }
        )
        Divider()
        Text("VPN", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = settings.vpnMtu.toString(),
            onValueChange = { update { copy(vpnMtu = it.toIntOrNull() ?: 1400) } },
            label = { Text("MTU") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = settings.ipv6Enabled, onCheckedChange = { update { copy(ipv6Enabled = it) } })
            Text("IPv6 路由")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = settings.routeUla, onCheckedChange = { update { copy(routeUla = it) } })
            Text("路由 ULA (fc00::/7)")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = settings.killSwitchEnabled, onCheckedChange = { update { copy(killSwitchEnabled = it) } })
            Text("Kill Switch")
        }
        var dnsExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = dnsExpanded,
            onExpandedChange = { dnsExpanded = it }
        ) {
            OutlinedTextField(
                value = settings.dnsStrategy,
                onValueChange = {},
                readOnly = true,
                label = { Text("DNS 策略") },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = dnsExpanded, onDismissRequest = { dnsExpanded = false }) {
                listOf("over-tcp", "over-udp", "none").forEach { strategy ->
                    DropdownMenuItem(
                        text = { Text(strategy) },
                        onClick = {
                            update { copy(dnsStrategy = strategy) }
                            dnsExpanded = false
                        }
                    )
                }
            }
        }
        ListItem(
            headlineContent = { Text("Per-app 代理") },
            supportingContent = { Text("配置应用黑白名单") },
            modifier = Modifier.clickable { navController.navigate("per_app_proxy") },
        )
        Divider()
        Text("通知", style = MaterialTheme.typography.titleMedium)
        NotificationSwitch("连接成功", settings.notify_connect_success) { update { copy(notify_connect_success = it) } }
        NotificationSwitch("断开连接", settings.notify_disconnect) { update { copy(notify_disconnect = it) } }
        NotificationSwitch("认证失败", settings.notify_auth_fail) { update { copy(notify_auth_fail = it) } }
        NotificationSwitch("代理状态变化", settings.notify_proxy_toggle) { update { copy(notify_proxy_toggle = it) } }
        NotificationSwitch("触发器成功", settings.notify_trigger_success) { update { copy(notify_trigger_success = it) } }
        NotificationSwitch("触发器失败", settings.notify_trigger_fail) { update { copy(notify_trigger_fail = it) } }
        NotificationSwitch("IP 变化", settings.notify_ip_change) { update { copy(notify_ip_change = it) } }
        Divider()
        Text("关于", style = MaterialTheme.typography.titleMedium)
        ListItem(
            headlineContent = { Text("版本") },
            supportingContent = { Text("0.1.0") }
        )
        ListItem(
            headlineContent = { Text("开源协议") },
            supportingContent = { Text("Apache-2.0") }
        )
    }
}

@Composable
private fun NotificationSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}
