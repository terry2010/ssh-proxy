package com.termfast.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// === SECTION 1: Data model ===

data class KeyDef(
    val label: String,
    val output: String? = null,
    val shifted: String? = null,
    val isFunctional: Boolean = false,
    val weight: Float = 1f,
    val showHint: Boolean = false,
    val highlight: Boolean = false,  // bright/light colored button
)

val KEY_ESC = KeyDef(label = "ESC", output = "\u001b", isFunctional = true)
val KEY_TAB = KeyDef(label = "Tab", output = "\t", isFunctional = true)
val KEY_BKSP = KeyDef(label = "⌫", output = "\u007f", isFunctional = true, weight = 1.5f)
val KEY_ENTER = KeyDef(label = "⏎", output = "\r", isFunctional = true, weight = 1.5f, highlight = true)
val KEY_CTRL = KeyDef(label = "CTRL", isFunctional = true, highlight = true)
val KEY_SHIFT = KeyDef(label = "SHIFT", isFunctional = true, weight = 1.5f, highlight = true)
val KEY_CAPS = KeyDef(label = "Caps", isFunctional = true)
val KEY_ALT = KeyDef(label = "ALT", isFunctional = true, highlight = true)
val KEY_LEFT = KeyDef(label = "←", output = "\u001b[D", isFunctional = true)
val KEY_DOWN = KeyDef(label = "↓", output = "\u001b[B", isFunctional = true)
val KEY_UP = KeyDef(label = "↑", output = "\u001b[A", isFunctional = true)
val KEY_RIGHT = KeyDef(label = "→", output = "\u001b[C", isFunctional = true)
val KEY_HOME = KeyDef(label = "HOME", output = "\u001b[H", isFunctional = true)
val KEY_END = KeyDef(label = "END", output = "\u001b[F", isFunctional = true)
val KEY_SPACE = KeyDef(label = "space", output = " ", isFunctional = true, weight = 2f, highlight = true)

// === SECTION 1 END ===

// === SECTION 2: Layout ===

// Top function row: ESC, Caps, Home, End, Space, ←, ↓, ↑, →, ⌫
private val funcRow = listOf(
    KEY_ESC, KEY_CAPS,
    KEY_HOME, KEY_END, KEY_SPACE,
    KEY_LEFT, KEY_DOWN, KEY_UP, KEY_RIGHT,
    KeyDef(label = "⌫", output = "\u007f", isFunctional = true),
)

// Row 1: number/symbol row (13 keys, no backspace)
private val row1 = listOf(
    KeyDef("`", "`", "~", showHint = true), KeyDef("1", "1", "!", showHint = true),
    KeyDef("2", "2", "@", showHint = true), KeyDef("3", "3", "#", showHint = true),
    KeyDef("4", "4", "$", showHint = true), KeyDef("5", "5", "%", showHint = true),
    KeyDef("6", "6", "^", showHint = true), KeyDef("7", "7", "&", showHint = true),
    KeyDef("8", "8", "*", showHint = true), KeyDef("9", "9", "(", showHint = true),
    KeyDef("0", "0", ")", showHint = true), KeyDef("-", "-", "_", showHint = true),
    KeyDef("=", "=", "+", showHint = true),
)
// Row 2: QWERTY top + brackets (14 keys)
private val row2 = listOf(
    KEY_TAB,
    KeyDef("q", "q", "Q"), KeyDef("w", "w", "W"), KeyDef("e", "e", "E"),
    KeyDef("r", "r", "R"), KeyDef("t", "t", "T"), KeyDef("y", "y", "Y"),
    KeyDef("u", "u", "U"), KeyDef("i", "i", "I"), KeyDef("o", "o", "O"),
    KeyDef("p", "p", "P"),
    KeyDef("[", "[", "{", showHint = true), KeyDef("]", "]", "}", showHint = true),
)
// Row 3: Shift + QWERTY home + backslash + enter
private val row3 = listOf(
    KEY_SHIFT,
    KeyDef("a", "a", "A"), KeyDef("s", "s", "S"), KeyDef("d", "d", "D"),
    KeyDef("f", "f", "F"), KeyDef("g", "g", "G"), KeyDef("h", "h", "H"),
    KeyDef("j", "j", "J"), KeyDef("k", "k", "K"), KeyDef("l", "l", "L"),
    KeyDef(";", ";", ":", showHint = true), KeyDef("'", "'", "\"", showHint = true),
    KeyDef("\\", "\\", "|", showHint = true), KEY_ENTER,
)
// Row 4: Ctrl + Alt + QWERTY bottom
private val row4 = listOf(
    KEY_CTRL, KEY_ALT,
    KeyDef("z", "z", "Z"), KeyDef("x", "x", "X"), KeyDef("c", "c", "C"),
    KeyDef("v", "v", "V"), KeyDef("b", "b", "B"), KeyDef("n", "n", "N"),
    KeyDef("m", "m", "M"), KeyDef(",", ",", "<", showHint = true),
    KeyDef(".", ".", ">", showHint = true), KeyDef("/", "/", "?", showHint = true),
)
private val allRows = listOf(funcRow, row1, row2, row3, row4)

// === SECTION 2 END ===

// === SECTION 3: Main Composable with pointer tracking ===

@Composable
fun TerminalKeyboard(
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // Landscape only: show "move keyboard to other side" button on the left
    //   of the switch-row. null = portrait, button hidden.
    onTogglePosition: (() -> Unit)? = null,
    keyboardOnLeft: Boolean = false,
    // "Switch to system keyboard" button in the switch row. null = hidden.
    onToggleSystemKeyboard: (() -> Unit)? = null,
) {
    var shiftActive by remember { mutableStateOf(false) }
    var capsLock by remember { mutableStateOf(false) }
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    val showShifted = shiftActive || capsLock

    // Pressed key tracking: Pair<rowIndex, colIndex> or null
    var pressedPos by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // Key bounds: map of "row,col" -> Rect (relative to keyboard container)
    val keyBounds = remember { mutableStateMapOf<String, Rect>() }
    var containerOffset by remember { mutableStateOf(Offset.Zero) }

    // Remember latest state for use in pointer coroutine
    val currentShift by rememberUpdatedState(shiftActive)
    val currentCaps by rememberUpdatedState(capsLock)
    val currentCtrl by rememberUpdatedState(ctrlActive)
    val currentAlt by rememberUpdatedState(altActive)
    val currentShowShifted by rememberUpdatedState(showShifted)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnKey by rememberUpdatedState(onKey)

    val keyBg = Color(0xFF181825)
    val keyBorder = Color(0xFF45475A)
    val accentColor = Color(0xFF89B4FA)
    val keyFg = Color(0xFFCDD6F4)
    val funcFg = Color(0xFFA6ADC8)
    val pressedBg = Color(0xFFFFFFFF)
    val pressedFg = Color(0xFF000000)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { containerOffset = it.positionInRoot() }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull() ?: continue
                        val localPos = change.position

                        // Find key at position
                        val found = keyBounds.entries.firstOrNull { (_, rect) ->
                            rect.contains(localPos)
                        }
                        val foundKey = found?.key
                        val parts = foundKey?.split(",")
                        val foundPos = parts?.let { Pair(it[0].toInt(), it[1].toInt()) }

                        if (change.pressed) {
                            // Finger down or dragging — update pressed key
                            if (foundPos != pressedPos) {
                                pressedPos = foundPos
                            }
                        } else {
                            // Finger released — send key at release position
                            pressedPos = null
                            if (foundPos != null && currentEnabled) {
                                val key = allRows[foundPos.first].getOrNull(foundPos.second)
                                if (key != null) {
                                    handleKeyRelease(
                                        key = key,
                                        showShifted = currentShowShifted,
                                        shiftActive = currentShift,
                                        ctrlActive = currentCtrl,
                                        altActive = currentAlt,
                                        onKey = currentOnKey,
                                        onShiftToggle = { shiftActive = !shiftActive },
                                        onCapsToggle = { capsLock = !capsLock },
                                        onCtrlToggle = { ctrlActive = !ctrlActive },
                                        onAltToggle = { altActive = !altActive },
                                        onShiftConsumed = { shiftActive = false },
                                        onCtrlConsumed = { ctrlActive = false },
                                        onAltConsumed = { altActive = false },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            .background(keyBg)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Switch row: landscape has "move side" button on the left +
            //   "switch system keyboard" button on the right (SpaceBetween).
            //   Portrait keeps only the right button (End).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (onTogglePosition != null) Arrangement.SpaceBetween else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: move keyboard to other side (landscape only)
                if (onTogglePosition != null) {
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(keyBorder)
                            .clickable { onTogglePosition() }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (keyboardOnLeft) "→ 右移" else "← 左移",
                            color = funcFg,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
                // Right: toggle system/custom keyboard
                if (onToggleSystemKeyboard != null) {
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(keyBorder)
                            .clickable { onToggleSystemKeyboard() }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "⌨ 切换系统键盘",
                            color = funcFg,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }

            // Custom keyboard rows
            allRows.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.forEachIndexed { colIndex, key ->
                        val isPressed = pressedPos == Pair(rowIndex, colIndex)
                        val isModifierActive = when (key.label) {
                            "SHIFT" -> shiftActive
                            "Caps" -> capsLock
                            "CTRL" -> ctrlActive
                            "ALT" -> altActive
                            else -> false
                        }
                        KeyboardKey(
                            key = key,
                            showShifted = showShifted,
                            isPressed = isPressed,
                            isModifierActive = isModifierActive,
                            enabled = enabled,
                            isFuncRow = rowIndex == 0,
                            keyBg = keyBg,
                            keyBorder = keyBorder,
                            accentColor = accentColor,
                            keyFg = keyFg,
                            funcFg = funcFg,
                            pressedBg = pressedBg,
                            pressedFg = pressedFg,
                            modifier = Modifier
                                .weight(key.weight)
                                .onGloballyPositioned { coords ->
                                    val pos = coords.positionInRoot() - containerOffset
                                    val s = coords.size
                                    keyBounds["$rowIndex,$colIndex"] = Rect(
                                        pos.x, pos.y,
                                        pos.x + s.width, pos.y + s.height
                                    )
                                },
                        )
                    }
                }
            }
        }

        // Floating preview — shown above keyboard when a key is pressed
        val pressedKey = pressedPos?.let { allRows[it.first].getOrNull(it.second) }
        if (pressedKey != null && !pressedKey.isFunctional) {
            val displayLabel = if (showShifted && pressedKey.shifted != null) {
                pressedKey.shifted!!
            } else {
                pressedKey.label
            }
            val bounds = keyBounds["${pressedPos!!.first},${pressedPos!!.second}"]
            val density = LocalDensity.current
            FloatingKeyPreview(
                label = displayLabel,
                keyCenterX = bounds?.center?.x ?: 0f,
                density = density,
            )
        }
    }
}

private fun handleKeyRelease(
    key: KeyDef,
    showShifted: Boolean,
    shiftActive: Boolean,
    ctrlActive: Boolean,
    altActive: Boolean,
    onKey: (String) -> Unit,
    onShiftToggle: () -> Unit,
    onCapsToggle: () -> Unit,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    onShiftConsumed: () -> Unit,
    onCtrlConsumed: () -> Unit,
    onAltConsumed: () -> Unit,
) {
    when (key.label) {
        "SHIFT" -> onShiftToggle()
        "Caps" -> onCapsToggle()
        "CTRL" -> onCtrlToggle()
        "ALT" -> onAltToggle()
        else -> {
            val output = if (showShifted && key.shifted != null) key.shifted!! else key.output
            if (output != null) {
                if (ctrlActive && output.length == 1 && output[0].isLetter()) {
                    val ctrlChar = (output[0].lowercaseChar().code - 'a'.code + 1).toChar()
                    onKey(ctrlChar.toString())
                    onCtrlConsumed()
                    if (altActive) onAltConsumed()
                } else if (altActive && output.length == 1) {
                    onKey("\u001b$output")
                    onAltConsumed()
                    if (shiftActive) onShiftConsumed()
                } else {
                    onKey(output)
                    if (shiftActive) onShiftConsumed()
                }
            }
        }
    }
}

// === SECTION 3 END ===

// === SECTION 4: Key rendering + floating preview ===

@Composable
private fun KeyboardKey(
    key: KeyDef,
    showShifted: Boolean,
    isPressed: Boolean,
    isModifierActive: Boolean,
    enabled: Boolean,
    isFuncRow: Boolean,
    keyBg: Color,
    keyBorder: Color,
    accentColor: Color,
    keyFg: Color,
    funcFg: Color,
    pressedBg: Color,
    pressedFg: Color,
    modifier: Modifier = Modifier,
) {
    val highlightBg = Color(0xFF313244)
    val highlightFg = Color(0xFFCDD6F4)
    val bg = when {
        isPressed -> pressedBg
        isModifierActive -> accentColor
        key.highlight -> highlightBg
        else -> keyBg
    }
    val fg = when {
        isPressed -> pressedFg
        isModifierActive -> keyBg
        key.highlight -> highlightFg
        key.isFunctional -> funcFg
        else -> keyFg
    }
    val borderColor = when {
        isPressed -> pressedBg
        isModifierActive -> accentColor
        key.highlight -> highlightFg.copy(alpha = 0.3f)
        else -> keyBorder
    }

    Box(
        modifier = modifier
            .height(if (isFuncRow) 36.dp else 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(0.5.dp, borderColor, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (key.isFunctional) {
            // Functional key: single label
            Text(
                key.label,
                color = fg.copy(alpha = if (enabled) 1f else 0.4f),
                fontSize = if (isFuncRow) 9.sp else 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        } else if (key.showHint && key.shifted != null) {
            // Dual-label key with superscript hint (number row, symbols)
            val mainLabel = if (showShifted) key.shifted!! else key.label
            val hintLabel = if (showShifted) key.label else key.shifted!!
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    hintLabel,
                    color = fg.copy(alpha = if (enabled) 0.4f else 0.15f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 4.dp),
                )
                Text(
                    mainLabel,
                    color = fg.copy(alpha = if (enabled) 1f else 0.4f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        } else {
            // Regular letter key
            val displayLabel = if (showShifted && key.shifted != null) key.shifted!! else key.label
            Text(
                displayLabel,
                color = fg.copy(alpha = if (enabled) 1f else 0.4f),
                fontSize = 14.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FloatingKeyPreview(
    label: String,
    keyCenterX: Float,
    density: androidx.compose.ui.unit.Density,
) {
    // Width adapts to label length; minimum 56dp, wider for long labels like "space"
    val previewWidthDp = if (label.length > 3) (56 + (label.length - 3) * 12).dp else 56.dp
    val previewWidth = with(density) { previewWidthDp.toPx() }
    val previewHeight = with(density) { 72.dp.toPx() }
    val left = (keyCenterX - previewWidth / 2).coerceAtLeast(0f)
    val top = -previewHeight - with(density) { 8.dp.toPx() }

    Box(
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()) }
            .size(width = previewWidthDp, height = 72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x66FFFFFF))
            .border(1.dp, Color(0x88FFFFFF), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = if (label.length > 3) 24.sp else 32.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// === SECTION 4 END ===
