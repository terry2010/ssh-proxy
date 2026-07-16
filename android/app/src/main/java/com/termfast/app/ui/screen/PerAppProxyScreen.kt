package com.termfast.app.ui.screen

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.termfast.app.data.AppSettings
import com.termfast.app.data.SettingsRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppProxyScreen(navController: NavController) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var appSettings by remember { mutableStateOf(settings.load()) }
    var search by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        apps = loadApps(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                apps = loadApps(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val filtered = apps.filter { it.name.contains(search, ignoreCase = true) || it.packageName.contains(search, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Per-app 代理") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("模式", style = MaterialTheme.typography.titleSmall)
            Row {
                FilterChip(
                    selected = appSettings.perAppMode == "blacklist",
                    onClick = { appSettings = appSettings.copy(perAppMode = "blacklist"); settings.save(appSettings) },
                    label = { Text("黑名单（选中不代理）") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = appSettings.perAppMode == "whitelist",
                    onClick = { appSettings = appSettings.copy(perAppMode = "whitelist"); settings.save(appSettings) },
                    label = { Text("白名单（仅选中代理）") }
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("搜索应用") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    val selected = appSettings.perAppPackages.contains(app.packageName)
                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = { Text(app.packageName, style = MaterialTheme.typography.bodySmall) },
                        leadingContent = {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { checked ->
                                    val updated = if (checked) appSettings.perAppPackages + app.packageName
                                    else appSettings.perAppPackages - app.packageName
                                    appSettings = appSettings.copy(perAppPackages = updated)
                                    settings.save(appSettings)
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

private data class AppInfo(val name: String, val packageName: String)

private fun loadApps(context: android.content.Context): List<AppInfo> {
    val pm = context.packageManager
    return pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
        .map { AppInfo(it.loadLabel(pm).toString(), it.packageName) }
        .sortedBy { it.name }
}
