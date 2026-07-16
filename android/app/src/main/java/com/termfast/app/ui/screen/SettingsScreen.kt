package com.termfast.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // VPN section
            SettingsSectionCard(title = "VPN", icon = Icons.Filled.VpnKey) {
                OutlinedTextField(
                    value = settings.vpnMtu.toString(),
                    onValueChange = { update { copy(vpnMtu = it.toIntOrNull() ?: 1400) } },
                    label = { Text("MTU") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(label = "IPv6 路由", checked = settings.ipv6Enabled, onCheckedChange = { update { copy(ipv6Enabled = it) } })
                SwitchRow(label = "路由 ULA (fc00::/7)", checked = settings.routeUla, onCheckedChange = { update { copy(routeUla = it) } })
                SwitchRow(label = "Kill Switch", checked = settings.killSwitchEnabled, onCheckedChange = { update { copy(killSwitchEnabled = it) } })
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
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
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
            }

            // Per-app proxy
            SettingsNavCard(
                icon = Icons.Filled.Apps,
                title = "分应用代理",
                subtitle = "配置哪些 App 走代理",
                onClick = { navController.navigate("per_app_proxy") },
            )

            // Notifications section
            SettingsSectionCard(title = "通知", icon = Icons.Filled.Notifications) {
                NotificationSwitch("连接成功", settings.notify_connect_success) { update { copy(notify_connect_success = it) } }
                NotificationSwitch("断开连接", settings.notify_disconnect) { update { copy(notify_disconnect = it) } }
                NotificationSwitch("认证失败", settings.notify_auth_fail) { update { copy(notify_auth_fail = it) } }
                NotificationSwitch("代理状态变化", settings.notify_proxy_toggle) { update { copy(notify_proxy_toggle = it) } }
                NotificationSwitch("触发器成功", settings.notify_trigger_success) { update { copy(notify_trigger_success = it) } }
                NotificationSwitch("触发器失败", settings.notify_trigger_fail) { update { copy(notify_trigger_fail = it) } }
                NotificationSwitch("IP 变化", settings.notify_ip_change) { update { copy(notify_ip_change = it) } }
            }

            // About section
            SettingsSectionCard(title = "关于", icon = Icons.Filled.Info) {
                InfoRow(label = "版本", value = "0.1.9")
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                InfoRow(label = "开源协议", value = "Apache-2.0")
            }
        }
    }
}

// === SECTION 1 END ===

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsNavCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NotificationSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SwitchRow(label = label, checked = checked, onCheckedChange = onCheckedChange)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}