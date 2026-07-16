package com.termfast.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.termfast.app.data.RustEvent
import com.termfast.app.data.RustRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(navController: NavController, serverId: String) {
    val repo = remember { RustRepository }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sessionId = remember { UUID.randomUUID().toString() }
    val listState = rememberLazyListState()

    // Terminal output lines
    var outputLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var connected by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var inputText by remember { mutableStateOf("") }

    // Collect terminal events
    LaunchedEffect(sessionId) {
        RustRepository.events.collect { event ->
            when (event) {
                is RustEvent.TerminalData -> {
                    if (event.session_id == sessionId) {
                        val newLines = event.data.split("\n")
                        outputLines = outputLines + newLines
                        // Auto-scroll to bottom
                        if (outputLines.isNotEmpty()) {
                            listState.animateScrollToItem(outputLines.size - 1)
                        }
                    }
                }
                is RustEvent.TerminalClosed -> {
                    if (event.session_id == sessionId) {
                        connected = false
                        connecting = false
                        outputLines = outputLines + "\n[连接已关闭]"
                    }
                }
                is RustEvent.TerminalError -> {
                    if (event.session_id == sessionId) {
                        errorMsg = event.error
                        connecting = false
                        connected = false
                        outputLines = outputLines + "\n[错误: ${event.error}]"
                    }
                }
                else -> {}
            }
        }
    }

    // Open terminal session on screen entry
    LaunchedEffect(serverId, sessionId) {
        scope.launch {
            withContext(Dispatchers.IO) {
                // Ensure SSH is connected first
                val status = repo.getServerStatus(serverId)
                if (status.status != "connected") {
                    // Try to connect
                    val ok = repo.connectServer(serverId)
                    if (!ok) {
                        withContext(Dispatchers.Main) {
                            errorMsg = "无法连接到 SSH 服务器，请检查服务器配置"
                            connecting = false
                        }
                        return@withContext
                    }
                }
                // Open PTY terminal (80x24 default)
                val ok = repo.openTerminal(serverId, sessionId, 80, 24)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        connected = true
                        connecting = false
                    } else {
                        errorMsg = "无法打开终端会话"
                        connecting = false
                    }
                }
            }
        }
    }

    // Close terminal on dispose
    DisposableEffect(sessionId) {
        onDispose {
            repo.closeTerminal(sessionId)
        }
    }

    val terminalBg = Color(0xFF1E1E2E)
    val terminalFg = Color(0xFFCDD6F4)
    val terminalGreen = Color(0xFFA6E3A1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "SSH 终端",
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = terminalBg,
                    titleContentColor = terminalFg,
                    navigationIconContentColor = terminalFg,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(terminalBg)
        ) {
            // Terminal output area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(terminalBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (connecting) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = terminalGreen,
                        )
                        Text(
                            "正在连接终端...",
                            color = terminalFg,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                        )
                    }
                } else if (errorMsg != null && outputLines.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "⚠ $errorMsg",
                            color = MaterialTheme.colorScheme.error,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "请先在服务器详情页启动 VPN 或代理",
                            color = terminalFg.copy(alpha = 0.6f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(outputLines) { line ->
                            Text(
                                line,
                                color = terminalFg,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // Input bar
            if (connected) {
                TerminalInputBar(
                    text = inputText,
                    onTextChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotEmpty()) {
                            val cmd = inputText + "\n"
                            repo.writeTerminal(sessionId, cmd)
                            inputText = ""
                        }
                    },
                    terminalBg = terminalBg,
                    terminalFg = terminalFg,
                )
            }
        }
    }
}

// === SECTION 1 END ===

@Composable
private fun TerminalInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    terminalBg: Color,
    terminalFg: Color,
) {
    val inputBg = Color(0xFF181825)
    val inputBorder = Color(0xFF45475A)
    val accentColor = Color(0xFF89B4FA)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(inputBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Prompt symbol
        Text(
            "$ ",
            color = accentColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        // Input field
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    "输入命令...",
                    color = terminalFg.copy(alpha = 0.4f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = terminalFg,
                unfocusedTextColor = terminalFg,
                focusedBorderColor = accentColor,
                unfocusedBorderColor = inputBorder,
                cursorColor = accentColor,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            trailingIcon = null,
        )
        // Send button
        IconButton(
            onClick = onSend,
            enabled = text.isNotEmpty(),
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (text.isNotEmpty()) accentColor else inputBorder),
        ) {
            Icon(
                Icons.Filled.Send,
                contentDescription = "发送",
                tint = if (text.isNotEmpty()) inputBg else terminalFg.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}