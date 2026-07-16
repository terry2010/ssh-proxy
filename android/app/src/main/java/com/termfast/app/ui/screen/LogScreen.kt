package com.termfast.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.termfast.app.data.RustRepository
import com.termfast.app.data.RustEvent
import kotlinx.serialization.json.jsonObject

data class LogEntry(val timestamp: String, val level: String, val tag: String, val message: String)

@Composable
fun LogScreen() {
    val repo = remember { RustRepository }
    val logEvents by repo.logBuffer.collectAsState()

    val logs = logEvents.map { event ->
        val obj = event.entry.jsonObject
        LogEntry(
            timestamp = obj["timestamp"]?.toString() ?: "",
            level = obj["level"]?.toString() ?: "",
            tag = obj["tag"]?.toString() ?: "",
            message = obj["message"]?.toString() ?: "",
        )
    }

    if (logs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("暂无日志", style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(logs) { entry ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = when (entry.level) {
                        "\"error\"" -> MaterialTheme.colorScheme.errorContainer
                        "\"warn\"" -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.surface
                    }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("[$entry.level] $entry.tag", style = MaterialTheme.typography.labelSmall)
                        Text(entry.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
