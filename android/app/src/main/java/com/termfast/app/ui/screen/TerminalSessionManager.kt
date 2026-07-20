package com.termfast.app.ui.screen

import com.termfast.app.data.RustEvent
import com.termfast.app.data.RustRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Manages terminal sessions per server.
 * Each server can have multiple active sessions.
 * Sessions are reused across screen recompositions — when the user navigates
 * away from the terminal screen and comes back, the same session is restored
 * with its output history.
 */
object TerminalSessionManager {
    private val sessions = mutableMapOf<String, SessionState>()
    private var collectorStarted = false

    // Regex to strip ANSI escape codes:
    // - CSI: \x1b[?...letter (colors, cursor movement, private modes like ?2004h)
    // - OSC: \x1b]...BEL or \x1b]...\x1b\\ (window title: ]0;root)
    // - Other ESC sequences: \x1b + single char
    private val ansiRegex = Regex(
        "\u001B\\[[0-9;?]*[a-zA-Z]" +          // CSI sequences (incl. private ?2004h etc.)
        "|\u001B\\][^\u0007\u001B]*(\u0007|\u001B\\\\)" + // OSC sequences (terminated by BEL or ST)
        "|\u001B[()][0-9A-Za-z]" +            // Charset designation
        "|\u001B[=>]" +                        // Keypad mode
        "|\u001B[@-Z\\-_]"                     // Other 2-char ESC sequences
    )

    fun stripAnsi(text: String): String {
        return ansiRegex.replace(text, "")
    }

    /**
     * Process raw terminal data into display lines.
     * \r (carriage return) is ignored — in real terminals it just moves cursor
     * to start of line, and \r\n is the normal line ending. Stripping \r
     * makes \r\n behave as a simple newline.
     */
    fun processToLines(raw: String): List<String> {
        val clean = stripAnsi(raw).replace("\r", "")
        return clean.split("\n")
    }

    data class SessionState(
        val sessionId: String,
        val serverId: String,
        val output: List<String> = emptyList(),
        val connected: Boolean = false,
        val createdAt: Long = System.currentTimeMillis(),
        val name: String = "",
        val lastLineComplete: Boolean = true,
    )

    @Synchronized
    fun getOrCreateSession(serverId: String): String {
        val sessionId = UUID.randomUUID().toString()
        sessions[sessionId] = SessionState(sessionId = sessionId, serverId = serverId)
        return sessionId
    }

    @Synchronized
    fun getOrCreateSessionById(serverId: String, sessionId: String): String {
        val existing = sessions[sessionId]
        if (existing != null) return sessionId
        sessions[sessionId] = SessionState(sessionId = sessionId, serverId = serverId)
        return sessionId
    }

    @Synchronized
    fun getOutputBySession(sessionId: String): List<String> {
        return sessions[sessionId]?.output ?: emptyList()
    }

    @Synchronized
    fun updateOutputBySession(sessionId: String, output: List<String>) {
        val existing = sessions[sessionId] ?: return
        sessions[sessionId] = existing.copy(output = output)
    }

    /**
     * Append raw terminal data, correctly merging partial lines across chunks.
     * Terminal data arrives in arbitrary chunks — a prompt may come without
     * a trailing newline, then the echoed input arrives separately. We track
     * whether the last line is "complete" (ended with \n) and merge accordingly.
     */
    @Synchronized
    fun appendTerminalData(sessionId: String, raw: String) {
        val existing = sessions[sessionId] ?: return
        // Strip ANSI codes, drop \r. Then process backspace (\u0008) and
        //   DEL (\u007f) by removing the previous character — this mirrors
        //   what a real terminal does when the server echoes the erase
        //   sequence (\b \b or \b) for canonical-mode line editing.
        val stripped = stripAnsi(raw).replace("\r", "").replace("\u007f", "")
        if (stripped.isEmpty()) return

        // Build the new lines, applying backspace to the current last line
        //   when the chunk starts mid-line.
        val endsWithNl = stripped.endsWith("\n")
        val rawLines = if (endsWithNl) stripped.split("\n").dropLast(1) else stripped.split("\n")

        // Merge first new line with existing partial last line, then apply
        //   backspace processing across the merged content.
        val (firstLine, restLines) = if (!existing.lastLineComplete && existing.output.isNotEmpty() && rawLines.isNotEmpty()) {
            val merged0 = existing.output.last() + rawLines.first()
            merged0 to rawLines.drop(1)
        } else if (rawLines.isNotEmpty()) {
            rawLines.first() to rawLines.drop(1)
        } else {
            "" to emptyList()
        }

        val processedFirst = applyBackspace(firstLine)
        val processedRest = restLines.map { applyBackspace(it) }

        // Rebuild output: keep all but last existing line, then add processed lines.
        val baseOutput = if (!existing.lastLineComplete && existing.output.isNotEmpty()) {
            existing.output.dropLast(1)
        } else {
            existing.output
        }
        val merged = baseOutput + listOf(processedFirst) + processedRest
        sessions[sessionId] = existing.copy(output = merged, lastLineComplete = endsWithNl)
    }

    /**
     * Process backspace (\u0008) in a single line: each \b removes the
     *   preceding character. This handles the server's echo of canonical-mode
     *   line editing (e.g. `\b \b` for backspace).
     */
    private fun applyBackspace(line: String): String {
        val sb = StringBuilder()
        for (ch in line) {
            when (ch) {
                '\u0008' -> { if (sb.isNotEmpty()) sb.deleteCharAt(sb.length - 1) }
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    @Synchronized
    fun isConnectedBySession(sessionId: String): Boolean {
        return sessions[sessionId]?.connected ?: false
    }

    @Synchronized
    fun setConnectedBySession(sessionId: String, connected: Boolean) {
        val existing = sessions[sessionId] ?: return
        sessions[sessionId] = existing.copy(connected = connected)
    }

    @Synchronized
    fun renameSession(sessionId: String, name: String) {
        val existing = sessions[sessionId] ?: return
        sessions[sessionId] = existing.copy(name = name)
    }

    @Synchronized
    fun closeSessionBySessionId(sessionId: String) {
        sessions.remove(sessionId)
    }

    @Synchronized
    fun getSessions(serverId: String): List<SessionState> {
        return sessions.values.filter { it.serverId == serverId }.sortedBy { it.createdAt }
    }

    @Synchronized
    fun hasSessions(serverId: String): Boolean {
        return sessions.values.any { it.serverId == serverId }
    }

    fun disconnectSession(sessionId: String) {
        RustRepository.closeTerminal(sessionId)
        setConnectedBySession(sessionId, false)
    }

    fun reconnectSession(serverId: String, sessionId: String, onResult: (Boolean) -> Unit) {
        RustRepository.closeTerminal(sessionId)
        setConnectedBySession(sessionId, false)
        GlobalScope.launch(Dispatchers.IO) {
            val status = RustRepository.getServerStatus(serverId)
            if (status.status != "connected") {
                val ok = RustRepository.connectServer(serverId)
                if (!ok) {
                    withContext(Dispatchers.Main) { onResult(false) }
                    return@launch
                }
            }
            val ok = RustRepository.openTerminal(serverId, sessionId, 80, 24)
            if (ok) setConnectedBySession(sessionId, true)
            withContext(Dispatchers.Main) { onResult(ok) }
        }
    }

    /**
     * Start a global event collector that keeps session state in sync
     * even when TerminalScreen is not visible. Call once at app startup.
     */
    fun startGlobalCollector() {
        if (collectorStarted) return
        collectorStarted = true
        GlobalScope.launch {
            RustRepository.events.collect { event ->
                when (event) {
                    is RustEvent.TerminalData -> {
                        appendTerminalData(event.session_id, event.data)
                    }
                    is RustEvent.TerminalClosed -> {
                        setConnectedBySession(event.session_id, false)
                    }
                    is RustEvent.TerminalError -> {
                        setConnectedBySession(event.session_id, false)
                    }
                    else -> {}
                }
            }
        }
    }
}
