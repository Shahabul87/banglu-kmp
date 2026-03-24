package com.banglu.keyboard

import android.view.SoundEffectConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banglu.engine.types.SmartSuggestion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════════════════════════════
// Feature 3.2: Theme Color Schemes
// ══════════════════════════════════════════════════════════════════════════════════

data class KeyboardColors(
    val keyboardBg: Color,
    val keyBg: Color,
    val keyPressed: Color,
    val specialKeyBg: Color,
    val keyText: Color,
    val subText: Color,
    val suggestionBg: Color,
    val suggestionHighlight: Color,
    val suggestionChipBg: Color
)

val DarkColors = KeyboardColors(
    keyboardBg = Color(0xFF1B1B1B),
    keyBg = Color(0xFF2C2C2C),
    keyPressed = Color(0xFF4A4A4A),
    specialKeyBg = Color(0xFF3A3A3A),
    keyText = Color.White,
    subText = Color(0xFF888888),
    suggestionBg = Color(0xFF1E1E1E),
    suggestionHighlight = Color(0xFF3D5AFE),
    suggestionChipBg = Color(0xFF333333)
)

val LightColors = KeyboardColors(
    keyboardBg = Color(0xFFE8E8E8),
    keyBg = Color.White,
    keyPressed = Color(0xFFBDBDBD),
    specialKeyBg = Color(0xFFCCCCCC),
    keyText = Color.Black,
    subText = Color(0xFF666666),
    suggestionBg = Color(0xFFF0F0F0),
    suggestionHighlight = Color(0xFF3D5AFE),
    suggestionChipBg = Color(0xFFDDDDDD)
)

val AmoledColors = KeyboardColors(
    keyboardBg = Color.Black,
    keyBg = Color(0xFF1A1A1A),
    keyPressed = Color(0xFF333333),
    specialKeyBg = Color(0xFF222222),
    keyText = Color.White,
    subText = Color(0xFF777777),
    suggestionBg = Color.Black,
    suggestionHighlight = Color(0xFF3D5AFE),
    suggestionChipBg = Color(0xFF222222)
)

val LocalKeyboardColors = compositionLocalOf { DarkColors }

// ── Dimensions ───────────────────────────────────────────────────────────────────
private val NumberRowHeight = 38.dp
private val KeyRowHeight = 46.dp
private val SuggestionBarHeight = 36.dp
private val ToolbarHeight = 36.dp
private val KeyGapH = 3.dp
private val KeyGapV = 6.dp
private val KeyCorner = 10.dp
private val KeyboardPadding = 4.dp

// ── Symbol Layouts ───────────────────────────────────────────────────────────────
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

// ── Number -> Symbol Long-Press Map ──────────────────────────────────────────────
private val NUMBER_SYMBOL_MAP = mapOf(
    '1' to '!', '2' to '@', '3' to '#', '4' to '$', '5' to '%',
    '6' to '^', '7' to '&', '8' to '*', '9' to '(', '0' to ')'
)

// ═══════════════════════════════════════════════════════════════════════════════
// Root Keyboard Composable
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun BangluKeyboardLayout(
    suggestions: List<SmartSuggestion>,
    keyboardMode: KeyboardMode,
    shiftState: ShiftState,
    enterLabel: String = "\u21B5",
    isToolbarExpanded: Boolean = false,
    onKeyPress: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBackspaceWord: () -> Unit = {},
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    onShiftTap: () -> Unit,
    onGlobePress: () -> Unit,
    onSymbolsPress: () -> Unit,
    onBackToLetters: () -> Unit,
    onSymbolPageToggle: () -> Unit,
    onSuggestionClick: (SmartSuggestion) -> Unit,
    onNumberPress: (Char) -> Unit,
    onPunctuationPress: (Char) -> Unit,
    onCursorMove: (Int) -> Unit = {},
    onDismiss: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onToggleToolbar: () -> Unit = {}
) {
    // Feature 3.2: Select color scheme based on system dark mode
    val systemDark = isSystemInDarkTheme()
    val colors = if (systemDark) DarkColors else LightColors

    CompositionLocalProvider(LocalKeyboardColors provides colors) {
        // Get nav bar height for bottom padding (permanent fix for Samsung/gesture nav)
        val context = LocalContext.current
        val navBarHeightPx = remember {
            val resourceId = context.resources.getIdentifier(
                "navigation_bar_height", "dimen", "android"
            )
            if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
        }
        val density = context.resources.displayMetrics.density
        val navBarPadding = (navBarHeightPx / density).dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.keyboardBg)
                .padding(horizontal = KeyboardPadding)
                .padding(top = 4.dp, bottom = navBarPadding)
        ) {
            // Feature 3.1: Toolbar row (visible in ALL keyboard modes)
            ToolbarRow(
                onSettingsClick = onSettingsClick,
                onToggleToolbar = onToggleToolbar,
                isExpanded = isToolbarExpanded
            )

            when (keyboardMode) {
                KeyboardMode.BANGLU -> {
                    BangluSuggestionRow(suggestions, onSuggestionClick, onDismiss)
                    Spacer(modifier = Modifier.height(KeyGapV))
                    NumberRow(
                        onNumberPress = onNumberPress,
                        onSymbolPress = onPunctuationPress
                    )
                    Spacer(modifier = Modifier.height(KeyGapV))
                    LetterRows(
                        shiftState = shiftState,
                        onKeyPress = onKeyPress,
                        onBackspace = onBackspace,
                        onBackspaceWord = onBackspaceWord,
                        onShiftTap = onShiftTap
                    )
                    Spacer(modifier = Modifier.height(KeyGapV))
                    BottomRow(
                        leftLabel = "!#1",
                        spaceLabel = "\u09AC\u09BE\u0982\u09B2\u09C1 (BN)",
                        globeLabel = "EN",
                        enterLabel = enterLabel,
                        onLeftPress = onSymbolsPress,
                        onGlobePress = onGlobePress,
                        onSpace = onSpace,
                        onPunctuationPress = onPunctuationPress,
                        onCursorMove = onCursorMove,
                        onEnter = onEnter
                    )
                }
                KeyboardMode.ENGLISH -> {
                    MinimalSuggestionBar(onDismiss)
                    Spacer(modifier = Modifier.height(KeyGapV))
                    NumberRow(
                        onNumberPress = onNumberPress,
                        onSymbolPress = onPunctuationPress
                    )
                    Spacer(modifier = Modifier.height(KeyGapV))
                    LetterRows(
                        shiftState = shiftState,
                        onKeyPress = onKeyPress,
                        onBackspace = onBackspace,
                        onBackspaceWord = onBackspaceWord,
                        onShiftTap = onShiftTap
                    )
                    Spacer(modifier = Modifier.height(KeyGapV))
                    BottomRow(
                        leftLabel = "!#1",
                        spaceLabel = "English (EN)",
                        globeLabel = "BN",
                        enterLabel = enterLabel,
                        onLeftPress = onSymbolsPress,
                        onGlobePress = onGlobePress,
                        onSpace = onSpace,
                        onPunctuationPress = onPunctuationPress,
                        onCursorMove = onCursorMove,
                        onEnter = onEnter
                    )
                }
                KeyboardMode.SYMBOLS_1 -> {
                    MinimalSuggestionBar(onDismiss)
                    Spacer(modifier = Modifier.height(KeyGapV))
                    NumberRow(
                        onNumberPress = onNumberPress,
                        onSymbolPress = onPunctuationPress
                    )
                    Spacer(modifier = Modifier.height(KeyGapV))
                    SymbolRows(
                        rows = SYMBOLS_1_ROWS,
                        pageLabel = "1/2",
                        onSymbolPress = onPunctuationPress,
                        onBackspace = onBackspace,
                        onBackspaceWord = onBackspaceWord,
                        onPageToggle = onSymbolPageToggle
                    )
                    Spacer(modifier = Modifier.height(KeyGapV))
                    BottomRow(
                        leftLabel = "ABC",
                        spaceLabel = "Symbols",
                        globeLabel = "BN",
                        enterLabel = enterLabel,
                        onLeftPress = onBackToLetters,
                        onGlobePress = onGlobePress,
                        onSpace = onSpace,
                        onPunctuationPress = onPunctuationPress,
                        onCursorMove = onCursorMove,
                        onEnter = onEnter
                    )
                }
                KeyboardMode.SYMBOLS_2 -> {
                    MinimalSuggestionBar(onDismiss)
                    Spacer(modifier = Modifier.height(KeyGapV))
                    NumberRow(
                        onNumberPress = onNumberPress,
                        onSymbolPress = onPunctuationPress
                    )
                    Spacer(modifier = Modifier.height(KeyGapV))
                    SymbolRows(
                        rows = SYMBOLS_2_ROWS,
                        pageLabel = "2/2",
                        onSymbolPress = onPunctuationPress,
                        onBackspace = onBackspace,
                        onBackspaceWord = onBackspaceWord,
                        onPageToggle = onSymbolPageToggle
                    )
                    Spacer(modifier = Modifier.height(KeyGapV))
                    BottomRow(
                        leftLabel = "ABC",
                        spaceLabel = "Symbols",
                        globeLabel = "EN",
                        enterLabel = enterLabel,
                        onLeftPress = onBackToLetters,
                        onGlobePress = onGlobePress,
                        onSpace = onSpace,
                        onPunctuationPress = onPunctuationPress,
                        onCursorMove = onCursorMove,
                        onEnter = onEnter
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Feature 3.1: Toolbar Row
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ToolbarRow(
    onSettingsClick: () -> Unit,
    onToggleToolbar: () -> Unit,
    isExpanded: Boolean
) {
    val colors = LocalKeyboardColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ToolbarHeight)
            .background(colors.suggestionBg)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isExpanded) {
            ToolbarIcon("\uD83D\uDCCB") { /* clipboard - future */ }
            ToolbarIcon("\uD83D\uDE0A") { /* emoji - future */ }
            ToolbarIcon("\u2699") { onSettingsClick() }
            ToolbarIcon("\uD83D\uDD90") { /* one-hand - future */ }
        }
        // Toggle button always visible
        ToolbarIcon(if (isExpanded) "\u25B2" else "\u00B7\u00B7\u00B7") { onToggleToolbar() }
    }
}

@Composable
private fun ToolbarIcon(icon: String, onClick: () -> Unit) {
    val colors = LocalKeyboardColors.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(icon, fontSize = 18.sp, color = colors.subText)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Minimal Suggestion Bar (for non-Banglu modes)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun MinimalSuggestionBar(onDismiss: () -> Unit) {
    val colors = LocalKeyboardColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SuggestionBarHeight)
            .background(colors.suggestionBg),
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Text("\u2304", color = colors.subText, fontSize = 20.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Suggestion Bar
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun BangluSuggestionRow(
    suggestions: List<SmartSuggestion>,
    onSuggestionClick: (SmartSuggestion) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalKeyboardColors.current

    if (suggestions.isEmpty()) {
        MinimalSuggestionBar(onDismiss)
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SuggestionBarHeight)
            .background(colors.suggestionBg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier
                .weight(1f)
                .height(SuggestionBarHeight),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(suggestions) { suggestion ->
                val isFirst = suggestion == suggestions.firstOrNull()
                // Feature 4.4: Prediction chips use different styling
                val isPrediction = suggestion.tier == "prediction"
                val chipBg = if (isFirst) colors.suggestionHighlight
                    else if (isPrediction) colors.keyBg
                    else colors.suggestionChipBg
                val chipTextColor = if (isFirst) Color.White else colors.keyText

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(chipBg)
                        .clickable { onSuggestionClick(suggestion) }
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Feature 4.3: Show phonetic hint on primary suggestion
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = suggestion.bengali,
                            color = chipTextColor,
                            fontSize = 15.sp,
                            fontStyle = if (isPrediction) FontStyle.Italic else FontStyle.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (suggestion.phonetic.isNotEmpty() && isFirst) {
                            Text(
                                text = suggestion.phonetic,
                                color = colors.subText,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        // Dismiss button at far right
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Text("\u2304", color = colors.subText, fontSize = 20.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Number Row (always visible)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun NumberRow(
    onNumberPress: (Char) -> Unit,
    onSymbolPress: (Char) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KeyGapH)
    ) {
        for (num in '1'..'9') {
            NumberKey(
                number = num,
                modifier = Modifier.weight(1f),
                onNumberPress = onNumberPress,
                onSymbolPress = onSymbolPress
            )
        }
        NumberKey(
            number = '0',
            modifier = Modifier.weight(1f),
            onNumberPress = onNumberPress,
            onSymbolPress = onSymbolPress
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
    onBackspaceWord: () -> Unit = {},
    onShiftTap: () -> Unit
) {
    val colors = LocalKeyboardColors.current
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
                bgColor = colors.keyBg,
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
                bgColor = colors.keyBg,
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
            ShiftState.OFF -> colors.specialKeyBg
            ShiftState.ON -> colors.suggestionHighlight
            ShiftState.CAPS_LOCK -> colors.suggestionHighlight
        }
        KeyButton(
            label = shiftLabel,
            modifier = Modifier.weight(1.5f),
            height = KeyRowHeight,
            bgColor = shiftBg,
            fontSize = 20,
            onClick = onShiftTap
        )

        for (key in listOf("z", "x", "c", "v", "b", "n", "m")) {
            val display = if (isUpper) key.uppercase() else key
            KeyButton(
                label = display,
                modifier = Modifier.weight(1f),
                height = KeyRowHeight,
                bgColor = colors.keyBg,
                onClick = { onKeyPress(display[0]) }
            )
        }

        // Backspace with long-press repeat and word deletion
        BackspaceKey(
            modifier = Modifier.weight(1.5f),
            onBackspace = onBackspace,
            onBackspaceWord = onBackspaceWord
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
    onBackspaceWord: () -> Unit = {},
    onPageToggle: () -> Unit
) {
    val colors = LocalKeyboardColors.current

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
                bgColor = colors.keyBg,
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
                bgColor = colors.keyBg,
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
            bgColor = colors.specialKeyBg,
            fontSize = 16,
            onClick = onPageToggle
        )
        for (sym in rows[2]) {
            KeyButton(
                label = sym,
                modifier = Modifier.weight(1f),
                height = KeyRowHeight,
                bgColor = colors.keyBg,
                fontSize = 18,
                onClick = { onSymbolPress(sym[0]) }
            )
        }
        BackspaceKey(
            modifier = Modifier.weight(1.5f),
            onBackspace = onBackspace,
            onBackspaceWord = onBackspaceWord
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
    globeLabel: String = "EN",
    enterLabel: String = "\u21B5",
    onLeftPress: () -> Unit,
    onGlobePress: () -> Unit,
    onSpace: () -> Unit,
    onPunctuationPress: (Char) -> Unit,
    onCursorMove: (Int) -> Unit = {},
    onEnter: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KeyGapH)
    ) {
        // !#1 or ABC
        KeyButton(
            label = leftLabel,
            modifier = Modifier.weight(1.2f),
            height = KeyRowHeight,
            bgColor = colors.specialKeyBg,
            fontSize = 16,
            onClick = onLeftPress
        )

        // Language toggle key (internal switch, NOT system IME switch)
        KeyButton(
            label = globeLabel,
            modifier = Modifier.weight(0.8f),
            height = KeyRowHeight,
            bgColor = colors.specialKeyBg,
            fontSize = 16,
            onClick = onGlobePress
        )

        // Spacebar with swipe-to-move cursor
        SpaceBar(
            label = spaceLabel,
            modifier = Modifier.weight(4f),
            onClick = onSpace,
            onCursorMove = onCursorMove
        )

        // Period
        KeyButton(
            label = ".",
            modifier = Modifier.weight(0.8f),
            height = KeyRowHeight,
            bgColor = colors.specialKeyBg,
            fontSize = 20,
            onClick = { onPunctuationPress('.') }
        )

        // Enter -- context-aware label (search, send, go, etc.)
        KeyButton(
            label = enterLabel,
            modifier = Modifier.weight(1.5f),
            height = KeyRowHeight,
            bgColor = colors.specialKeyBg,
            fontSize = 20,
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
    bgColor: Color,
    fontSize: Int = 22,
    onClick: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    var isPressed by remember { mutableStateOf(false) }

    // Feature 1.4: Scale UP on press for single-character keys (key preview effect)
    // Multi-character keys (labels like "!#1", "ABC") scale down as before
    val isCharKey = label.length == 1
    val scale by animateFloatAsState(
        targetValue = if (isPressed) {
            if (isCharKey) 1.15f else 0.95f
        } else 1f,
        animationSpec = tween(durationMillis = 50)
    )

    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(KeyCorner))
            .background(if (isPressed) colors.keyPressed else bgColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        // Feature 3.4: Sound feedback
                        view.playSoundEffect(SoundEffectConstants.CLICK)
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                        }
                        onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = colors.keyText,
            fontSize = if (isPressed && isCharKey) (fontSize + 6).sp else fontSize.sp,
            fontWeight = if (isPressed && isCharKey) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun SpaceBar(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onCursorMove: (Int) -> Unit = {}
) {
    val colors = LocalKeyboardColors.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    var isPressed by remember { mutableStateOf(false) }
    var isCursorMode by remember { mutableStateOf(false) }
    var lastDragX by remember { mutableFloatStateOf(0f) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(50)
    )

    Box(
        modifier = modifier
            .height(KeyRowHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(KeyCorner))
            .background(if (isPressed) colors.keyPressed else colors.keyBg)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        isCursorMode = true
                        lastDragX = offset.x
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val dx = change.position.x - lastDragX
                        // Move cursor every 15dp of horizontal drag
                        val threshold = 15.dp.toPx()
                        if (kotlin.math.abs(dx) > threshold) {
                            val direction = if (dx > 0) 1 else -1
                            onCursorMove(direction)
                            lastDragX = change.position.x
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = { isCursorMode = false },
                    onDragCancel = { isCursorMode = false }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        // Feature 3.4: Sound feedback
                        view.playSoundEffect(SoundEffectConstants.CLICK)
                        try { awaitRelease() } finally { isPressed = false }
                        if (!isCursorMode) onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isCursorMode) "\u25C4 \u25BA cursor" else label,
            color = if (isCursorMode) colors.keyText else colors.subText,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Backspace Key with Long-Press Repeat
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun BackspaceKey(
    modifier: Modifier = Modifier,
    onBackspace: () -> Unit,
    onBackspaceWord: () -> Unit = {}
) {
    val colors = LocalKeyboardColors.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 50)
    )
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .height(KeyRowHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(KeyCorner))
            .background(if (isPressed) colors.keyPressed else colors.specialKeyBg)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        // Feature 3.4: Sound feedback
                        view.playSoundEffect(SoundEffectConstants.CLICK)
                        onBackspace() // First delete immediately
                        val pressStartTime = System.currentTimeMillis()

                        // Feature 1.5: Start repeat after 400ms, switch to word
                        // deletion after 1.5s of continuous holding
                        val repeatJob = coroutineScope.launch {
                            delay(400)
                            while (true) {
                                val elapsed = System.currentTimeMillis() - pressStartTime
                                if (elapsed > 1500) {
                                    // Word-by-word deletion after 1.5s hold
                                    onBackspaceWord()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    delay(100) // Slower pace for word deletion
                                } else {
                                    // Char-by-char deletion
                                    onBackspace()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    delay(50) // 20 chars/sec
                                }
                            }
                        }

                        try {
                            awaitRelease()
                        } finally {
                            repeatJob.cancel()
                            isPressed = false
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\u232B",
            color = colors.keyText,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Number Key with Long-Press -> Symbol
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun NumberKey(
    number: Char,
    modifier: Modifier = Modifier,
    onNumberPress: (Char) -> Unit,
    onSymbolPress: (Char) -> Unit
) {
    val colors = LocalKeyboardColors.current
    val symbol = NUMBER_SYMBOL_MAP[number] ?: '!'
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(50)
    )
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .height(NumberRowHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(KeyCorner))
            .background(if (isPressed) colors.keyPressed else colors.keyBg)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        // Feature 3.4: Sound feedback
                        view.playSoundEffect(SoundEffectConstants.CLICK)

                        // Start long-press timer
                        val longPressJob = coroutineScope.launch {
                            delay(500)
                            // Long press -> symbol
                            onSymbolPress(symbol)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }

                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                            if (longPressJob.isActive) {
                                longPressJob.cancel()
                                // Short tap -> number
                                onNumberPress(number)
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            // Number in center
            Text(
                text = number.toString(),
                color = colors.keyText,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            // Symbol hint in top-right corner
            Text(
                text = symbol.toString(),
                color = colors.subText,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}
