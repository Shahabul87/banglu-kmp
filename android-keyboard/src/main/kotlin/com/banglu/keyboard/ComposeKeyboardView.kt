package com.banglu.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.banglu.engine.types.SmartSuggestion

// ── Samsung Dark Theme Colors ──────────────────────────────────────────────────
private val KeyboardBg = Color(0xFF1B1B1B)
private val KeyBg = Color(0xFF2C2C2C)
private val KeyPressed = Color(0xFF4A4A4A)
private val SpecialKeyBg = Color(0xFF3A3A3A)
private val KeyText = Color.White
private val SubText = Color(0xFF888888)
private val SuggestionBg = Color(0xFF1E1E1E)
private val SuggestionHighlight = Color(0xFF3D5AFE)
private val SuggestionChipBg = Color(0xFF333333)

// ── Samsung Dimensions ─────────────────────────────────────────────────────────
private val NumberRowHeight = 42.dp
private val KeyRowHeight = 48.dp
private val SuggestionBarHeight = 40.dp
private val KeyGapH = 4.dp
private val KeyGapV = 4.dp
private val KeyCorner = 10.dp
private val KeyboardPadding = 3.dp

// ── Symbol Layouts ─────────────────────────────────────────────────────────────
private val SYMBOLS_1_ROWS = listOf(
    listOf("+", "\u00D7", "\u00F7", "=", "/", "_", "<", ">", "[", "]"),
    listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")"),
    listOf("-", "'", "\"", ":", ";", ",", "?")
)

private val SYMBOLS_2_ROWS = listOf(
    listOf("`", "~", "\\", "|", "{", "}", "\u20AC", "\u00A3", "\u00A5", "\u20B9"),
    listOf("\u00B0", "\u2022", "\u25CB", "\u25CF", "\u25A1", "\u25A0", "\u2664", "\u2661", "\u2662", "\u2667"),
    listOf("\u2605", "\u2026", "\u00AB", "\u00BB", "\u00A1", "\u00BF")
)

// ═══════════════════════════════════════════════════════════════════════════════
// Root Keyboard Composable
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun BangluKeyboardLayout(
    suggestions: List<SmartSuggestion>,
    keyboardMode: KeyboardMode,
    shiftState: ShiftState,
    onKeyPress: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    onShiftTap: () -> Unit,
    onGlobePress: () -> Unit,
    onSymbolsPress: () -> Unit,
    onBackToLetters: () -> Unit,
    onSymbolPageToggle: () -> Unit,
    onSuggestionClick: (SmartSuggestion) -> Unit,
    onNumberPress: (Char) -> Unit,
    onPunctuationPress: (Char) -> Unit
) {
    // Get nav bar height for bottom padding (permanent fix for Samsung/gesture nav)
    val context = LocalContext.current
    val navBarHeightPx = remember {
        val resourceId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }
    val density = context.resources.displayMetrics.density
    val navBarPadding = (navBarHeightPx / density).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KeyboardBg)
            .padding(horizontal = KeyboardPadding)
            .padding(top = 4.dp, bottom = navBarPadding)  // Bottom padding = nav bar height
    ) {
        when (keyboardMode) {
            KeyboardMode.BANGLU -> {
                BangluSuggestionRow(suggestions, onSuggestionClick)
                Spacer(modifier = Modifier.height(KeyGapV))
                NumberRow(onNumberPress)
                Spacer(modifier = Modifier.height(KeyGapV))
                LetterRows(
                    shiftState = shiftState,
                    onKeyPress = onKeyPress,
                    onBackspace = onBackspace,
                    onShiftTap = onShiftTap
                )
                Spacer(modifier = Modifier.height(KeyGapV))
                BottomRow(
                    leftLabel = "!#1",
                    spaceLabel = "\u09AC\u09BE\u0982\u09B2\u09C1 (BN)",
                    onLeftPress = onSymbolsPress,
                    onGlobePress = onGlobePress,
                    onSpace = onSpace,
                    onPunctuationPress = onPunctuationPress,
                    onEnter = onEnter
                )
            }
            KeyboardMode.ENGLISH -> {
                // Minimal suggestion bar (empty spacer for consistent height)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SuggestionBarHeight)
                        .background(SuggestionBg)
                )
                Spacer(modifier = Modifier.height(KeyGapV))
                NumberRow(onNumberPress)
                Spacer(modifier = Modifier.height(KeyGapV))
                LetterRows(
                    shiftState = shiftState,
                    onKeyPress = onKeyPress,
                    onBackspace = onBackspace,
                    onShiftTap = onShiftTap
                )
                Spacer(modifier = Modifier.height(KeyGapV))
                BottomRow(
                    leftLabel = "!#1",
                    spaceLabel = "English (EN)",
                    onLeftPress = onSymbolsPress,
                    onGlobePress = onGlobePress,
                    onSpace = onSpace,
                    onPunctuationPress = onPunctuationPress,
                    onEnter = onEnter
                )
            }
            KeyboardMode.SYMBOLS_1 -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SuggestionBarHeight)
                        .background(SuggestionBg)
                )
                Spacer(modifier = Modifier.height(KeyGapV))
                NumberRow(onNumberPress)
                Spacer(modifier = Modifier.height(KeyGapV))
                SymbolRows(
                    rows = SYMBOLS_1_ROWS,
                    pageLabel = "1/2",
                    onSymbolPress = onPunctuationPress,
                    onBackspace = onBackspace,
                    onPageToggle = onSymbolPageToggle
                )
                Spacer(modifier = Modifier.height(KeyGapV))
                BottomRow(
                    leftLabel = "ABC",
                    spaceLabel = if (shiftState != ShiftState.OFF) "English (EN)" else "\u09AC\u09BE\u0982\u09B2\u09C1 (BN)",
                    onLeftPress = onBackToLetters,
                    onGlobePress = onGlobePress,
                    onSpace = onSpace,
                    onPunctuationPress = onPunctuationPress,
                    onEnter = onEnter
                )
            }
            KeyboardMode.SYMBOLS_2 -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SuggestionBarHeight)
                        .background(SuggestionBg)
                )
                Spacer(modifier = Modifier.height(KeyGapV))
                NumberRow(onNumberPress)
                Spacer(modifier = Modifier.height(KeyGapV))
                SymbolRows(
                    rows = SYMBOLS_2_ROWS,
                    pageLabel = "2/2",
                    onSymbolPress = onPunctuationPress,
                    onBackspace = onBackspace,
                    onPageToggle = onSymbolPageToggle
                )
                Spacer(modifier = Modifier.height(KeyGapV))
                BottomRow(
                    leftLabel = "ABC",
                    spaceLabel = if (shiftState != ShiftState.OFF) "English (EN)" else "\u09AC\u09BE\u0982\u09B2\u09C1 (BN)",
                    onLeftPress = onBackToLetters,
                    onGlobePress = onGlobePress,
                    onSpace = onSpace,
                    onPunctuationPress = onPunctuationPress,
                    onEnter = onEnter
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Suggestion Bar
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun BangluSuggestionRow(
    suggestions: List<SmartSuggestion>,
    onSuggestionClick: (SmartSuggestion) -> Unit
) {
    if (suggestions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SuggestionBarHeight)
                .background(SuggestionBg)
        )
        return
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(SuggestionBarHeight)
            .background(SuggestionBg),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(suggestions) { suggestion ->
            val isFirst = suggestion == suggestions.firstOrNull()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isFirst) SuggestionHighlight else SuggestionChipBg)
                    .clickable { onSuggestionClick(suggestion) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = suggestion.bengali,
                    color = KeyText,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Number Row (always visible)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun NumberRow(onNumberPress: (Char) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KeyGapH)
    ) {
        for (num in '1'..'9') {
            KeyButton(
                label = num.toString(),
                modifier = Modifier.weight(1f),
                height = NumberRowHeight,
                bgColor = KeyBg,
                fontSize = 16,
                onClick = { onNumberPress(num) }
            )
        }
        KeyButton(
            label = "0",
            modifier = Modifier.weight(1f),
            height = NumberRowHeight,
            bgColor = KeyBg,
            fontSize = 16,
            onClick = { onNumberPress('0') }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// QWERTY Letter Rows
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LetterRows(
    shiftState: ShiftState,
    onKeyPress: (Char) -> Unit,
    onBackspace: () -> Unit,
    onShiftTap: () -> Unit
) {
    val isUpper = shiftState != ShiftState.OFF

    // Row 1: q w e r t y u i o p
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KeyGapH)
    ) {
        for (key in listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")) {
            val display = if (isUpper) key.uppercase() else key
            KeyButton(
                label = display,
                modifier = Modifier.weight(1f),
                height = KeyRowHeight,
                bgColor = KeyBg,
                onClick = { onKeyPress(display[0]) }
            )
        }
    }

    Spacer(modifier = Modifier.height(KeyGapV))

    // Row 2: a s d f g h j k l (indented)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(KeyGapH)
    ) {
        for (key in listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")) {
            val display = if (isUpper) key.uppercase() else key
            KeyButton(
                label = display,
                modifier = Modifier.weight(1f),
                height = KeyRowHeight,
                bgColor = KeyBg,
                onClick = { onKeyPress(display[0]) }
            )
        }
    }

    Spacer(modifier = Modifier.height(KeyGapV))

    // Row 3: Shift z x c v b n m Backspace
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KeyGapH)
    ) {
        // Shift key with visual state
        val shiftLabel = when (shiftState) {
            ShiftState.OFF -> "\u21E7"
            ShiftState.ON -> "\u2B06"        // Filled up arrow
            ShiftState.CAPS_LOCK -> "\u21EA"  // Caps lock symbol
        }
        val shiftBg = when (shiftState) {
            ShiftState.OFF -> SpecialKeyBg
            ShiftState.ON -> SuggestionHighlight
            ShiftState.CAPS_LOCK -> SuggestionHighlight
        }
        KeyButton(
            label = shiftLabel,
            modifier = Modifier.weight(1.5f),
            height = KeyRowHeight,
            bgColor = shiftBg,
            fontSize = 18,
            onClick = onShiftTap
        )

        for (key in listOf("z", "x", "c", "v", "b", "n", "m")) {
            val display = if (isUpper) key.uppercase() else key
            KeyButton(
                label = display,
                modifier = Modifier.weight(1f),
                height = KeyRowHeight,
                bgColor = KeyBg,
                onClick = { onKeyPress(display[0]) }
            )
        }

        // Backspace
        KeyButton(
            label = "\u232B",
            modifier = Modifier.weight(1.5f),
            height = KeyRowHeight,
            bgColor = SpecialKeyBg,
            fontSize = 18,
            onClick = onBackspace
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Symbol Rows
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SymbolRows(
    rows: List<List<String>>,
    pageLabel: String,
    onSymbolPress: (Char) -> Unit,
    onBackspace: () -> Unit,
    onPageToggle: () -> Unit
) {
    // Symbol row 1 (10 keys)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KeyGapH)
    ) {
        for (sym in rows[0]) {
            KeyButton(
                label = sym,
                modifier = Modifier.weight(1f),
                height = KeyRowHeight,
                bgColor = KeyBg,
                fontSize = 18,
                onClick = { onSymbolPress(sym[0]) }
            )
        }
    }

    Spacer(modifier = Modifier.height(KeyGapV))

    // Symbol row 2 (10 keys)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KeyGapH)
    ) {
        for (sym in rows[1]) {
            KeyButton(
                label = sym,
                modifier = Modifier.weight(1f),
                height = KeyRowHeight,
                bgColor = KeyBg,
                fontSize = 18,
                onClick = { onSymbolPress(sym[0]) }
            )
        }
    }

    Spacer(modifier = Modifier.height(KeyGapV))

    // Symbol row 3: [page toggle] symbols... [backspace]
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KeyGapH)
    ) {
        KeyButton(
            label = pageLabel,
            modifier = Modifier.weight(1.5f),
            height = KeyRowHeight,
            bgColor = SpecialKeyBg,
            fontSize = 14,
            onClick = onPageToggle
        )
        for (sym in rows[2]) {
            KeyButton(
                label = sym,
                modifier = Modifier.weight(1f),
                height = KeyRowHeight,
                bgColor = KeyBg,
                fontSize = 18,
                onClick = { onSymbolPress(sym[0]) }
            )
        }
        KeyButton(
            label = "\u232B",
            modifier = Modifier.weight(1.5f),
            height = KeyRowHeight,
            bgColor = SpecialKeyBg,
            fontSize = 18,
            onClick = onBackspace
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Bottom Row (common to all modes)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun BottomRow(
    leftLabel: String,
    spaceLabel: String,
    onLeftPress: () -> Unit,
    onGlobePress: () -> Unit,
    onSpace: () -> Unit,
    onPunctuationPress: (Char) -> Unit,
    onEnter: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KeyGapH)
    ) {
        // !#1 or ABC
        KeyButton(
            label = leftLabel,
            modifier = Modifier.weight(1.2f),
            height = KeyRowHeight,
            bgColor = SpecialKeyBg,
            fontSize = 14,
            onClick = onLeftPress
        )

        // Globe key
        KeyButton(
            label = "\uD83C\uDF10",
            modifier = Modifier.weight(0.8f),
            height = KeyRowHeight,
            bgColor = SpecialKeyBg,
            fontSize = 18,
            onClick = onGlobePress
        )

        // Spacebar
        SpaceBar(
            label = spaceLabel,
            modifier = Modifier.weight(4f),
            onClick = onSpace
        )

        // Period
        KeyButton(
            label = ".",
            modifier = Modifier.weight(0.8f),
            height = KeyRowHeight,
            bgColor = SpecialKeyBg,
            fontSize = 18,
            onClick = { onPunctuationPress('.') }
        )

        // Enter
        KeyButton(
            label = "\u21B5",
            modifier = Modifier.weight(1.5f),
            height = KeyRowHeight,
            bgColor = SpecialKeyBg,
            fontSize = 18,
            onClick = onEnter
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Individual Key Composables
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun KeyButton(
    label: String,
    modifier: Modifier = Modifier,
    height: Dp = KeyRowHeight,
    bgColor: Color = KeyBg,
    fontSize: Int = 20,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(KeyCorner))
            .background(if (isPressed) KeyPressed else bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = KeyText,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SpaceBar(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .height(KeyRowHeight)
            .clip(RoundedCornerShape(KeyCorner))
            .background(if (isPressed) KeyPressed else KeyBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = SubText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}
