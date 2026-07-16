package com.termfast.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.termfast.app.data.RustRepository
import com.termfast.app.data.RustEvent
import kotlinx.serialization.json.jsonObject

data class LogEntry(val timestamp: String, val level: String, val tag: String, val message: String)

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { padding ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无日志",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(logs) { entry ->
                    LogEntryItem(entry)
                }
            }
        }
    }
}

@Composable
private fun LogEntryItem(entry: LogEntry) {
    val levelColor = when (entry.level) {
        "\"error\"" -> MaterialTheme.colorScheme.error
        "\"warn\"" -> MaterialTheme.colorScheme.tertiary
        "\"info\"" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bgColor = when (entry.level) {
        "\"error\"" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        "\"warn\"" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.level.removeSurrounding("\"").uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = levelColor,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                entry.tag.removeSurrounding("\""),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            entry.message.removeSurrounding("\""),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}