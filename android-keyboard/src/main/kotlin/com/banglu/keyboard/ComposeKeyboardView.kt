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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
    keyboardBg = Color(0xFF141414),
    keyBg = Color(0xFF2D2D2D),
    keyPressed = Color(0xFF4B4B4B),
    specialKeyBg = Color(0xFF242424),
    keyText = Color.White,
    subText = Color(0xFFA6A6A6),
    suggestionBg = Color(0xFF1A1A1A),
    suggestionHighlight = Color(0xFF3D5AFE),
    suggestionChipBg = Color(0xFF2A2A2A)
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
    keyBg = Color(0xFF202020),
    keyPressed = Color(0xFF3A3A3A),
    specialKeyBg = Color(0xFF171717),
    keyText = Color.White,
    subText = Color(0xFF777777),
    suggestionBg = Color.Black,
    suggestionHighlight = Color(0xFF3D5AFE),
    suggestionChipBg = Color(0xFF222222)
)

val LocalKeyboardColors = compositionLocalOf { DarkColors }

// ── Settings CompositionLocals ──────────────────────────────────────────────────
val LocalHapticEnabled = compositionLocalOf { true }
val LocalSoundEnabled = compositionLocalOf { true }
val LocalKeyPreviewEnabled = compositionLocalOf { true }
val LocalKeyboardHeightScale = compositionLocalOf { 1f }

private data class KeyAlternative(val label: String, val input: String)

// ── Dimensions ───────────────────────────────────────────────────────────────────
private val NumberRowHeight = 40.dp
private val LetterKeyRowHeight = 42.dp
private val BottomKeyRowHeight = 46.dp
private val SuggestionBarHeight = 34.dp
private val ToolbarExpandedHeight = 36.dp
private val ToolbarCollapsedHeight = 28.dp
private val KeyGapH = 5.dp
private val KeyGapV = 6.dp
private val KeyVisualPaddingH = 0.dp
private val KeyCorner = 8.dp
private val KeyboardPadding = 8.dp
private val NavigationFallbackBottomPadding = 56.dp

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

private val LETTER_ROW_1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
private val LETTER_ROW_2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
private val LETTER_ROW_3 = listOf("z", "x", "c", "v", "b", "n", "m")

private fun letterKeyLabel(key: String, shiftState: ShiftState, useShiftedLetterInput: Boolean): String {
    if (shiftState == ShiftState.OFF) return key.lowercase()

    return if (useShiftedLetterInput) {
        key.uppercase()
    } else {
        bangluShiftLabel(key)
    }
}

private fun bangluShiftLabel(key: String): String {
    return when (key.lowercase().first()) {
        't', 'd', 'r', 'i', 'u' -> key.uppercase()
        else -> key.lowercase()
    }
}

private fun letterKeyInput(key: String, shiftState: ShiftState, useShiftedLetterInput: Boolean): Char {
    val char = key.lowercase().first()
    if (useShiftedLetterInput) {
        return if (shiftState != ShiftState.OFF) char.uppercaseChar() else char
    }

    return if (shiftState != ShiftState.OFF) bangluShiftInput(char) else char
}

private fun bangluShiftInput(char: Char): Char {
    return when (char.lowercaseChar()) {
        't' -> 'T'
        'd' -> 'D'
        'r' -> 'R'
        'i' -> 'I'
        'u' -> 'U'
        else -> char.lowercaseChar()
    }
}

private fun displayPhoneticHint(phonetic: String): String {
    return phonetic.map { if (it in 'A'..'Z') it.lowercaseChar() else it }.joinToString("")
}

// ═══════════════════════════════════════════════════════════════════════════════
// Root Keyboard Composable
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun BangluKeyboardLayout(
    suggestions: List<SmartSuggestion>,
    keyboardMode: KeyboardMode,
    shiftState: ShiftState,
    voiceInputState: VoiceInputState = VoiceInputState.IDLE,
    voiceInputLevel: Float = 0f,
    enterLabel: String = "\u21B5",
    isToolbarExpanded: Boolean = false,
    hapticEnabled: Boolean = true,
    soundEnabled: Boolean = true,
    suggestionsEnabled: Boolean = true,
    numberRowEnabled: Boolean = true,
    keyPreviewEnabled: Boolean = true,
    themePref: String = "auto",
    keyboardHeightMode: String = "normal",
    onKeyPress: (Char) -> Unit,
    onTextInput: (String) -> Unit = { text -> text.forEach { onKeyPress(it) } },
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
    onToggleToolbar: () -> Unit = {},
    onVoiceInput: () -> Unit = {},
    onVoiceStop: () -> Unit = {},
    onVoiceCancel: () -> Unit = {},
    onEmojiClick: (String) -> Unit = {},
    onEmojiOpen: () -> Unit = {},
    onBackFromEmoji: () -> Unit = {}
) {
    // Feature 3.2: Select color scheme based on theme preference
    val systemDark = isSystemInDarkTheme()
    val colors = when (themePref) {
        "light" -> LightColors
        "dark" -> DarkColors
        "amoled" -> AmoledColors
        else -> if (systemDark) DarkColors else LightColors // "auto"
    }
    val heightScale = when (keyboardHeightMode) {
        "compact" -> 0.90f
        "tall" -> 1.10f
        else -> 1.0f
    }

    CompositionLocalProvider(
        LocalKeyboardColors provides colors,
        LocalHapticEnabled provides hapticEnabled,
        LocalSoundEnabled provides soundEnabled,
        LocalKeyPreviewEnabled provides keyPreviewEnabled,
        LocalKeyboardHeightScale provides heightScale
    ) {
        val navBottomPadding = WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()
        val bottomSafePadding = maxOf(navBottomPadding, NavigationFallbackBottomPadding)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.keyboardBg)
                .padding(horizontal = KeyboardPadding)
                .padding(top = 3.dp, bottom = bottomSafePadding)
        ) {
            if (isToolbarExpanded) {
                ToolbarRow(
                    onSettingsClick = onSettingsClick,
                    onEmojiOpen = onEmojiOpen,
                    onVoiceInput = onVoiceInput,
                    voiceInputState = voiceInputState,
                    onToggleToolbar = onToggleToolbar,
                    isExpanded = true
                )
            }

            if (voiceInputState != VoiceInputState.IDLE) {
                VoiceStatusPanel(
                    state = voiceInputState,
                    level = voiceInputLevel,
                    onRetry = onVoiceInput,
                    onStop = onVoiceStop,
                    onCancel = onVoiceCancel
                )
                Spacer(modifier = Modifier.height(KeyGapV))
            }

            when (keyboardMode) {
                KeyboardMode.BANGLU -> {
                    if (suggestionsEnabled) {
                        BangluSuggestionRow(suggestions, onSuggestionClick, onDismiss, onToggleToolbar)
                    } else {
                        MinimalSuggestionBar(onDismiss, onToggleToolbar)
                    }
                    Spacer(modifier = Modifier.height(KeyGapV))
                    if (numberRowEnabled) {
                        NumberRow(
                            onNumberPress = onNumberPress,
                            onSymbolPress = onPunctuationPress
                        )
                        Spacer(modifier = Modifier.height(KeyGapV))
                    }
                    LetterRows(
                        shiftState = shiftState,
                        useShiftedLetterInput = false,
                        onKeyPress = onKeyPress,
                        onTextInput = onTextInput,
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
                    MinimalSuggestionBar(onDismiss, onToggleToolbar)
                    Spacer(modifier = Modifier.height(KeyGapV))
                    if (numberRowEnabled) {
                        NumberRow(
                            onNumberPress = onNumberPress,
                            onSymbolPress = onPunctuationPress
                        )
                        Spacer(modifier = Modifier.height(KeyGapV))
                    }
                    LetterRows(
                        shiftState = shiftState,
                        useShiftedLetterInput = true,
                        onKeyPress = onKeyPress,
                        onTextInput = onTextInput,
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
                    MinimalSuggestionBar(onDismiss, onToggleToolbar)
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
                    MinimalSuggestionBar(onDismiss, onToggleToolbar)
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

                KeyboardMode.EMOJI -> {
                    EmojiPanel(
                        colors = colors,
                        onEmojiClick = onEmojiClick,
                        onBackToKeyboard = onBackFromEmoji,
                        onBackspace = onBackspace,
                        onDismiss = onDismiss
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
    onEmojiOpen: () -> Unit,
    onVoiceInput: () -> Unit,
    voiceInputState: VoiceInputState,
    onToggleToolbar: () -> Unit,
    isExpanded: Boolean
) {
    val colors = LocalKeyboardColors.current
    val height = if (isExpanded) ToolbarExpandedHeight else ToolbarCollapsedHeight
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(colors.suggestionBg)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isExpanded) {
            ToolbarIcon("\uD83D\uDCCB", "Clipboard") { /* clipboard - future */ }
            ToolbarIcon("\uD83D\uDE0A", "Emoji") { onEmojiOpen() }
            ToolbarIcon(
                if (voiceInputState == VoiceInputState.LISTENING) "\uD83D\uDD34" else "\uD83C\uDFA4",
                "Bangla voice typing"
            ) { onVoiceInput() }
            ToolbarIcon("\u2699", "Settings") { onSettingsClick() }
        }
        // Toggle button always visible
        ToolbarIcon(
            if (isExpanded) "\u25B2" else "\u00B7\u00B7\u00B7",
            if (isExpanded) "Collapse toolbar" else "Expand toolbar"
        ) { onToggleToolbar() }
    }
}

@Composable
private fun VoiceStatusPanel(
    state: VoiceInputState,
    level: Float,
    onRetry: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    val animatedLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 120),
        label = "voiceLevel"
    )
    val message = when (state) {
        VoiceInputState.LISTENING -> "বাংলায় বলুন"
        VoiceInputState.PROCESSING -> "ভয়েস লেখা হচ্ছে..."
        VoiceInputState.PERMISSION_REQUIRED -> "মাইক্রোফোন পারমিশন দিন"
        VoiceInputState.UNAVAILABLE -> "ভয়েস সার্ভিস পাওয়া যায়নি"
        VoiceInputState.ERROR -> "আবার চেষ্টা করুন"
        VoiceInputState.IDLE -> ""
    }
    val detail = when (state) {
        VoiceInputState.LISTENING -> "বিরতি দিলেও শুনবে, শেষ হলে থামান"
        VoiceInputState.PROCESSING -> "একটু অপেক্ষা করুন"
        VoiceInputState.PERMISSION_REQUIRED -> "Banglu অ্যাপে অনুমতি চালু করুন"
        VoiceInputState.UNAVAILABLE -> "ডিভাইসে speech service নেই"
        VoiceInputState.ERROR -> "মাইক চেক করে আবার চেষ্টা করুন"
        VoiceInputState.IDLE -> ""
    }
    val isActive = state == VoiceInputState.LISTENING || state == VoiceInputState.PROCESSING
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (state == VoiceInputState.ERROR || state == VoiceInputState.PERMISSION_REQUIRED)
                    Color(0xFF3A2424)
                else
                    Color(0xFF17281D)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(if (state == VoiceInputState.LISTENING) Color(0xFF2E7D32) else colors.suggestionChipBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (state == VoiceInputState.LISTENING) "\uD83C\uDFA4" else "\u2022\u2022\u2022",
                    color = colors.keyText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                Text(
                    text = message,
                    color = colors.keyText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = detail,
                    color = colors.subText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isActive) {
                VoiceActionButton("থামান", onStop)
                Spacer(modifier = Modifier.width(6.dp))
                VoiceActionButton("বাতিল", onCancel)
            } else {
                VoiceActionButton("আবার", onRetry)
            }
        }
        if (isActive) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(18) { index ->
                    val threshold = (index + 1) / 18f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(if (animatedLevel >= threshold) 7.dp else 3.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (animatedLevel >= threshold) Color(0xFF66BB6A) else Color(0xFF344338)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceActionButton(label: String, onClick: () -> Unit) {
    val colors = LocalKeyboardColors.current
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.suggestionChipBg)
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = colors.keyText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun ToolbarIcon(icon: String, accessibilityLabel: String = icon, onClick: () -> Unit) {
    val colors = LocalKeyboardColors.current
    Box(
        modifier = Modifier
            .size(32.dp)
            .semantics { contentDescription = accessibilityLabel }
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
private fun MinimalSuggestionBar(
    onDismiss: () -> Unit,
    onToggleToolbar: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SuggestionBarHeight)
            .background(colors.suggestionBg)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "\u00B7\u00B7\u00B7",
            color = colors.subText,
            fontSize = 18.sp,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { onToggleToolbar() }
                .padding(start = 10.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(36.dp)
                .semantics { contentDescription = "Dismiss keyboard" }
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Text("\u2304", color = colors.subText, fontSize = 19.sp)
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
    onDismiss: () -> Unit,
    onToggleToolbar: () -> Unit
) {
    val colors = LocalKeyboardColors.current

    if (suggestions.isEmpty()) {
        MinimalSuggestionBar(onDismiss, onToggleToolbar)
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SuggestionBarHeight)
            .background(colors.suggestionBg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .semantics { contentDescription = "Expand toolbar" }
                .clip(RoundedCornerShape(10.dp))
                .clickable { onToggleToolbar() },
            contentAlignment = Alignment.Center
        ) {
            Text("\u00B7\u00B7\u00B7", color = colors.subText, fontSize = 18.sp)
        }
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
                        .semantics { contentDescription = suggestion.bengali }
                        .shadow(if (isFirst) 1.dp else 0.dp, RoundedCornerShape(16.dp), clip = false)
                        .clip(RoundedCornerShape(16.dp))
                        .background(chipBg)
                        .clickable { onSuggestionClick(suggestion) }
                        .padding(horizontal = 13.dp, vertical = 2.dp),
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
                            fontWeight = if (isFirst) FontWeight.Medium else FontWeight.Normal,
                            fontStyle = if (isPrediction) FontStyle.Italic else FontStyle.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (suggestion.phonetic.isNotEmpty() && isFirst) {
                            Text(
                                text = displayPhoneticHint(suggestion.phonetic),
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
                .semantics { contentDescription = "Dismiss keyboard" }
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
    val height = NumberRowHeight * LocalKeyboardHeightScale.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KeyGapH)
    ) {
        for (num in '1'..'9') {
            NumberKey(
                number = num,
                modifier = Modifier.weight(1f),
                height = height,
                onNumberPress = onNumberPress,
                onSymbolPress = onSymbolPress
            )
        }
        NumberKey(
            number = '0',
            modifier = Modifier.weight(1f),
            height = height,
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
    useShiftedLetterInput: Boolean,
    onKeyPress: (Char) -> Unit,
    onTextInput: (String) -> Unit,
    onBackspace: () -> Unit,
    onBackspaceWord: () -> Unit = {},
    onShiftTap: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    val keyHeight = LetterKeyRowHeight * LocalKeyboardHeightScale.current

    // Row 1: q w e r t y u i o p
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KeyGapH)
    ) {
        for (key in LETTER_ROW_1) {
            val display = letterKeyLabel(key, shiftState, useShiftedLetterInput)
            val input = letterKeyInput(key, shiftState, useShiftedLetterInput)
            KeyButton(
                label = display,
                modifier = Modifier.weight(1f),
                height = keyHeight,
                bgColor = colors.keyBg,
                longPressOptions = longPressAlternatives(key[0]),
                onTextInput = onTextInput,
                onClick = { onKeyPress(input) }
            )
        }
    }

    Spacer(modifier = Modifier.height(KeyGapV))

    // Row 2: a s d f g h j k l (indented)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(KeyGapH)
    ) {
        for (key in LETTER_ROW_2) {
            val display = letterKeyLabel(key, shiftState, useShiftedLetterInput)
            val input = letterKeyInput(key, shiftState, useShiftedLetterInput)
            KeyButton(
                label = display,
                modifier = Modifier.weight(1f),
                height = keyHeight,
                bgColor = colors.keyBg,
                longPressOptions = longPressAlternatives(key[0]),
                onTextInput = onTextInput,
                onClick = { onKeyPress(input) }
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
            ShiftState.ON -> "\u21E7"
            ShiftState.CAPS_LOCK -> "\u21EA"
        }
        val shiftBg = when (shiftState) {
            ShiftState.OFF -> colors.specialKeyBg
            ShiftState.ON -> colors.suggestionHighlight
            ShiftState.CAPS_LOCK -> colors.suggestionHighlight
        }
        KeyButton(
            label = shiftLabel,
            modifier = Modifier.weight(1.5f),
            height = keyHeight,
            bgColor = shiftBg,
            fontSize = 20,
            onClick = onShiftTap
        )

        for (key in LETTER_ROW_3) {
            val display = letterKeyLabel(key, shiftState, useShiftedLetterInput)
            val input = letterKeyInput(key, shiftState, useShiftedLetterInput)
            KeyButton(
                label = display,
                modifier = Modifier.weight(1f),
                height = keyHeight,
                bgColor = colors.keyBg,
                longPressOptions = longPressAlternatives(key[0]),
                onTextInput = onTextInput,
                onClick = { onKeyPress(input) }
            )
        }

        // Backspace with long-press repeat and word deletion
        BackspaceKey(
            modifier = Modifier.weight(1.5f),
            height = keyHeight,
            onBackspace = onBackspace,
            onBackspaceWord = onBackspaceWord
        )
    }
}

private fun longPressAlternatives(char: Char): List<KeyAlternative> {
    return when (char.lowercaseChar()) {
        't' -> listOf(KeyAlternative("ট", "T"))
        'd' -> listOf(KeyAlternative("ড", "D"))
        'r' -> listOf(KeyAlternative("ড়", "R"))
        's' -> listOf(KeyAlternative("শ", "sh"))
        'i' -> listOf(KeyAlternative("ঈ", "ii"))
        'u' -> listOf(KeyAlternative("ঊ", "uu"))
        else -> emptyList()
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
    val keyHeight = LetterKeyRowHeight * LocalKeyboardHeightScale.current

    // Symbol row 1 (10 keys)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KeyGapH)
    ) {
        for (sym in rows[0]) {
            KeyButton(
                label = sym,
                modifier = Modifier.weight(1f),
                height = keyHeight,
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
                height = keyHeight,
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
            height = keyHeight,
            bgColor = colors.specialKeyBg,
            fontSize = 16,
            onClick = onPageToggle
        )
        for (sym in rows[2]) {
            KeyButton(
                label = sym,
                modifier = Modifier.weight(1f),
                height = keyHeight,
                bgColor = colors.keyBg,
                fontSize = 18,
                onClick = { onSymbolPress(sym[0]) }
            )
        }
        BackspaceKey(
            modifier = Modifier.weight(1.5f),
            height = keyHeight,
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
    val keyHeight = BottomKeyRowHeight * LocalKeyboardHeightScale.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KeyGapH)
    ) {
        // !#1 or ABC
        KeyButton(
            label = leftLabel,
            modifier = Modifier.weight(1.2f),
            height = keyHeight,
            bgColor = colors.specialKeyBg,
            fontSize = 16,
            onClick = onLeftPress
        )

        // Language toggle key (internal switch, NOT system IME switch)
        KeyButton(
            label = globeLabel,
            modifier = Modifier.weight(0.8f),
            height = keyHeight,
            bgColor = colors.specialKeyBg,
            fontSize = 16,
            onClick = onGlobePress
        )

        // Spacebar with swipe-to-move cursor
        SpaceBar(
            label = spaceLabel,
            modifier = Modifier.weight(4f),
            height = keyHeight,
            onClick = onSpace,
            onCursorMove = onCursorMove
        )

        // Period
        KeyButton(
            label = ".",
            modifier = Modifier.weight(0.8f),
            height = keyHeight,
            bgColor = colors.specialKeyBg,
            fontSize = 20,
            onClick = { onPunctuationPress('.') }
        )

        // Enter -- context-aware label (search, send, go, etc.)
        KeyButton(
            label = enterLabel,
            modifier = Modifier.weight(1.5f),
            height = keyHeight,
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
    height: Dp = LetterKeyRowHeight,
    bgColor: Color,
    fontSize: Int = 22,
    accessibilityLabel: String = label,
    longPressOptions: List<KeyAlternative> = emptyList(),
    onTextInput: (String) -> Unit = {},
    onClick: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val hapticOn = LocalHapticEnabled.current
    val soundOn = LocalSoundEnabled.current
    val previewOn = LocalKeyPreviewEnabled.current
    val currentOnClick by rememberUpdatedState(onClick)
    var isPressed by remember { mutableStateOf(false) }
    var showAlternatives by remember { mutableStateOf(false) }

    // Feature 1.4: Scale UP on press for single-character keys (key preview effect)
    val isCharKey = label.length == 1
    val scale by animateFloatAsState(
        targetValue = if (isPressed && previewOn) {
            if (isCharKey) 1.04f else 0.97f
        } else if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 50)
    )
    val keyShape = RoundedCornerShape(KeyCorner)

    Box(
        modifier = modifier
            .height(height)
            .semantics { contentDescription = accessibilityLabel }
            .pointerInput(longPressOptions) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                        }
                    },
                    onTap = {
                        if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (soundOn) view.playSoundEffect(SoundEffectConstants.CLICK)
                        currentOnClick()
                    },
                    onLongPress = if (longPressOptions.isEmpty()) null else {
                        {
                            showAlternatives = true
                            if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = KeyVisualPaddingH)
                .shadow(if (isPressed) 0.dp else 1.dp, keyShape, clip = false)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(keyShape)
                .background(if (isPressed) colors.keyPressed else bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = colors.keyText,
                fontSize = if (isPressed && isCharKey && previewOn) (fontSize + 2).sp else fontSize.sp,
                fontWeight = if (label.length <= 2) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        if (showAlternatives) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, -96),
                properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
                onDismissRequest = { showAlternatives = false }
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.keyBg)
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    longPressOptions.forEach { option ->
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.specialKeyBg)
                                .clickable {
                                    showAlternatives = false
                                    if (soundOn) view.playSoundEffect(SoundEffectConstants.CLICK)
                                    onTextInput(option.input)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option.label,
                                color = colors.keyText,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpaceBar(
    label: String,
    modifier: Modifier = Modifier,
    height: Dp = BottomKeyRowHeight,
    onClick: () -> Unit,
    onCursorMove: (Int) -> Unit = {}
) {
    val colors = LocalKeyboardColors.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val hapticOn = LocalHapticEnabled.current
    val soundOn = LocalSoundEnabled.current
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnCursorMove by rememberUpdatedState(onCursorMove)
    var isPressed by remember { mutableStateOf(false) }
    var isCursorMode by remember { mutableStateOf(false) }
    var lastDragX by remember { mutableFloatStateOf(0f) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(50)
    )
    val keyShape = RoundedCornerShape(KeyCorner)

    Box(
        modifier = modifier
            .height(height)
            .semantics { contentDescription = "Space" }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        isCursorMode = true
                        lastDragX = offset.x
                        if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val dx = change.position.x - lastDragX
                        val threshold = 15.dp.toPx()
                        if (kotlin.math.abs(dx) > threshold) {
                            val direction = if (dx > 0) 1 else -1
                            currentOnCursorMove(direction)
                            lastDragX = change.position.x
                            if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                        if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (soundOn) view.playSoundEffect(SoundEffectConstants.CLICK)
                        try { awaitRelease() } finally { isPressed = false }
                        if (!isCursorMode) currentOnClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = KeyVisualPaddingH)
                .shadow(if (isPressed) 0.dp else 1.dp, keyShape, clip = false)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(keyShape)
                .background(if (isPressed) colors.keyPressed else colors.keyBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isCursorMode) "\u25C4 \u25BA cursor" else label,
                color = if (isCursorMode) colors.keyText else colors.keyText.copy(alpha = 0.68f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Backspace Key with Long-Press Repeat
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun BackspaceKey(
    modifier: Modifier = Modifier,
    height: Dp = LetterKeyRowHeight,
    onBackspace: () -> Unit,
    onBackspaceWord: () -> Unit = {}
) {
    val colors = LocalKeyboardColors.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val hapticOn = LocalHapticEnabled.current
    val soundOn = LocalSoundEnabled.current
    val currentOnBackspace by rememberUpdatedState(onBackspace)
    val currentOnBackspaceWord by rememberUpdatedState(onBackspaceWord)
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 50)
    )
    val coroutineScope = rememberCoroutineScope()
    val keyShape = RoundedCornerShape(KeyCorner)

    Box(
        modifier = modifier
            .height(height)
            .semantics { contentDescription = "Backspace" }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (soundOn) view.playSoundEffect(SoundEffectConstants.CLICK)
                        currentOnBackspace()
                    },
                    onPress = {
                        isPressed = true
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                        }
                    },
                    onLongPress = {
                        if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (soundOn) view.playSoundEffect(SoundEffectConstants.CLICK)
                        currentOnBackspaceWord()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = KeyVisualPaddingH)
                .shadow(if (isPressed) 0.dp else 1.dp, keyShape, clip = false)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(keyShape)
                .background(if (isPressed) colors.keyPressed else colors.specialKeyBg),
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
}

// ═══════════════════════════════════════════════════════════════════════════════
// Number Key with Long-Press -> Symbol
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun NumberKey(
    number: Char,
    modifier: Modifier = Modifier,
    height: Dp = NumberRowHeight,
    onNumberPress: (Char) -> Unit,
    onSymbolPress: (Char) -> Unit
) {
    val colors = LocalKeyboardColors.current
    val symbol = NUMBER_SYMBOL_MAP[number] ?: '!'
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val hapticOn = LocalHapticEnabled.current
    val soundOn = LocalSoundEnabled.current
    val currentOnNumberPress by rememberUpdatedState(onNumberPress)
    val currentOnSymbolPress by rememberUpdatedState(onSymbolPress)
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(50)
    )
    val coroutineScope = rememberCoroutineScope()
    val keyShape = RoundedCornerShape(KeyCorner)

    Box(
        modifier = modifier
            .height(height)
            .semantics { contentDescription = "Number $number, long press for $symbol" }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (soundOn) view.playSoundEffect(SoundEffectConstants.CLICK)

                        val longPressJob = coroutineScope.launch {
                            delay(500)
                            currentOnSymbolPress(symbol)
                            if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }

                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                            if (longPressJob.isActive) {
                                longPressJob.cancel()
                                currentOnNumberPress(number)
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = KeyVisualPaddingH)
                .shadow(if (isPressed) 0.dp else 1.dp, keyShape, clip = false)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(keyShape)
                .background(if (isPressed) colors.keyPressed else colors.keyBg)
                .padding(4.dp)
        ) {
            Text(
                text = number.toString(),
                color = colors.keyText,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            Text(
                text = symbol.toString(),
                color = colors.subText,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Emoji Panel
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EmojiPanel(
    colors: KeyboardColors,
    onEmojiClick: (String) -> Unit,
    onBackToKeyboard: () -> Unit,
    onBackspace: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableIntStateOf(0) }
    val recents = remember { mutableStateListOf<String>() }
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val hapticOn = LocalHapticEnabled.current
    val soundOn = LocalSoundEnabled.current
    val currentCategory = EmojiData.categories.getOrNull(selectedCategory)
    val currentEmojis = if (selectedCategory == 0 && recents.isNotEmpty()) {
        recents
    } else {
        currentCategory?.emojis.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.keyboardBg)
    ) {
        // Compact Samsung-style category rail.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(colors.suggestionBg)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .width(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.specialKeyBg)
                    .clickable { onBackToKeyboard() },
                contentAlignment = Alignment.Center
            ) {
                Text("ABC", color = colors.suggestionHighlight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            LazyRow(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(EmojiData.categories) { index, category ->
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (index == selectedCategory)
                                    colors.suggestionHighlight.copy(alpha = 0.85f)
                                else Color.Transparent
                            )
                            .clickable { selectedCategory = index },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            category.icon,
                            color = if (category.isTextIcon) {
                                if (index == selectedCategory) Color.White else colors.subText
                            } else {
                                Color.Unspecified
                            },
                            fontSize = if (category.isTextIcon) 16.sp else 20.sp,
                            fontWeight = if (category.isTextIcon) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Text("\u25BE", color = colors.subText, fontSize = 22.sp)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier
                .fillMaxWidth()
                .height(282.dp)
                .padding(horizontal = 6.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(currentEmojis.size) { index ->
                val emoji = currentEmojis[index]
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (soundOn) view.playSoundEffect(SoundEffectConstants.CLICK)
                            recents.remove(emoji)
                            recents.add(0, emoji)
                            while (recents.size > 40) recents.removeAt(recents.lastIndex)
                            onEmojiClick(emoji)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 27.sp)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(colors.keyboardBg)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyButton(
                label = "ABC",
                modifier = Modifier.width(74.dp),
                height = 40.dp,
                bgColor = colors.specialKeyBg,
                fontSize = 15,
                onClick = onBackToKeyboard
            )
            Text(
                text = currentCategory?.name.orEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                color = colors.subText,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BackspaceKey(
                modifier = Modifier.width(64.dp),
                onBackspace = onBackspace,
                onBackspaceWord = onBackspace
            )
        }
    }
}
