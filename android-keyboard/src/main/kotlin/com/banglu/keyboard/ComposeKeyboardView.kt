package com.banglu.keyboard

import android.content.res.Configuration
import android.view.SoundEffectConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
    keyboardBg = Color(0xFF1C1C1E),
    keyBg = Color(0xFF2C2C2E),
    keyPressed = Color(0xFF3A3A3C),
    specialKeyBg = Color(0xFF3A3A3C),
    keyText = Color(0xFFF8FAFC),
    subText = Color(0xFFA1A1AA),
    suggestionBg = Color(0xFF1C1C1E),
    suggestionHighlight = Color(0xFF0A84FF),
    suggestionChipBg = Color(0xFF2C2C2E)
)

val LightColors = KeyboardColors(
    keyboardBg = Color(0xFFF2F3F7),
    keyBg = Color.White,
    keyPressed = Color(0xFFD8DEE8),
    specialKeyBg = Color(0xFFDCE2EA),
    keyText = Color(0xFF111827),
    subText = Color(0xFF6B7280),
    suggestionBg = Color(0xFFF8FAFF),
    suggestionHighlight = Color(0xFF0A84FF),
    suggestionChipBg = Color(0xFFEAF2FF)
)

val AmoledColors = KeyboardColors(
    keyboardBg = Color(0xFF0B0F16),
    keyBg = Color(0xFF1C2430),
    keyPressed = Color(0xFF324052),
    specialKeyBg = Color(0xFF111827),
    keyText = Color(0xFFF8FAFC),
    subText = Color(0xFF9CA3AF),
    suggestionBg = Color(0xFF0B0F16),
    suggestionHighlight = Color(0xFF0A84FF),
    suggestionChipBg = Color(0xFF172033)
)

val LocalKeyboardColors = compositionLocalOf { DarkColors }

// ── Settings CompositionLocals ──────────────────────────────────────────────────
val LocalHapticEnabled = compositionLocalOf { true }
val LocalSoundEnabled = compositionLocalOf { true }
val LocalKeyPreviewEnabled = compositionLocalOf { true }
val LocalKeyboardHeightScale = compositionLocalOf { 1f }
val LocalKeyboardFontScale = compositionLocalOf { 1.08f }

@Composable
private fun scaledSp(base: Int) = (base * LocalKeyboardFontScale.current).sp

@Composable
private fun scaledSp(base: Float) = (base * LocalKeyboardFontScale.current).sp

@Composable
private fun scaledDp(base: Dp) = base * LocalKeyboardHeightScale.current

@Composable
private fun scaledKeyHeight(base: Dp): Dp = maxOf(base * LocalKeyboardHeightScale.current, MinKeyTouchHeight)

@Composable
private fun currentKeyGapH(): Dp {
    return if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        KeyGapHLandscape
    } else {
        KeyGapH
    }
}

@Composable
private fun middleLetterRowIndent(): Dp {
    return if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 14.dp else 24.dp
}

private data class KeyAlternative(val label: String, val input: String)

// ── Dimensions ───────────────────────────────────────────────────────────────────
private val NumberRowHeight = 40.dp
private val LetterKeyRowHeight = 46.dp
private val BottomKeyRowHeight = 48.dp
private val MinKeyTouchHeight = 46.dp
private val TopStripHeight = 38.dp
private val ToolbarExpandedHeight = 40.dp
private val ToolbarCollapsedHeight = 36.dp
private val KeyGapH = 7.dp
private val KeyGapHLandscape = 5.dp
private val KeyGapV = 3.dp
private val KeyVisualPaddingH = 0.dp
private val KeyVisualPaddingV = 4.dp
private val KeyCorner = 7.dp
private val KeyboardPadding = 6.dp
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

private val EMOJI_SEARCH_ROW_1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
private val EMOJI_SEARCH_ROW_2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
private val EMOJI_SEARCH_ROW_3 = listOf("z", "x", "c", "v", "b", "n", "m")

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
    return key.uppercase()
}

private fun letterKeyInput(key: String, shiftState: ShiftState, useShiftedLetterInput: Boolean): Char {
    val char = key.lowercase().first()
    if (useShiftedLetterInput) {
        return if (shiftState != ShiftState.OFF) char.uppercaseChar() else char
    }

    return if (shiftState != ShiftState.OFF) bangluShiftInput(char) else char
}

private fun bangluShiftInput(char: Char): Char {
    return char.lowercaseChar()
}

private fun displayPhoneticHint(phonetic: String): String {
    return phonetic.map { if (it in 'A'..'Z') it.lowercaseChar() else it }.joinToString("")
}

// ═══════════════════════════════════════════════════════════════════════════════
// Root Keyboard Composable
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun BangluKeyboardLayout(
    suggestionsProvider: () -> List<SmartSuggestion> = { emptyList() },
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
    themePref: String = "dark",
    keyboardHeightMode: String = "normal",
    keyboardFontSizeMode: String = "large",
    onKeyPress: (Char) -> Unit,
    onTextInput: (String) -> Unit = { text -> text.forEach { onKeyPress(it) } },
    onBackspace: () -> Unit,
    onBackspaceRepeat: (Int) -> Unit = { count -> repeat(count) { onBackspace() } },
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
    onClipboardOpen: () -> Unit = {},
    onClipboardPaste: (String) -> Unit = {},
    onClipboardClear: () -> Unit = {},
    clipboardItemsProvider: () -> List<String> = { emptyList() },
    onVoiceInput: () -> Unit = {},
    onVoiceStop: () -> Unit = {},
    onVoiceCancel: () -> Unit = {},
    onEmojiClick: (String) -> Unit = {},
    onEmojiOpen: () -> Unit = {},
    onStickerOpen: () -> Unit = onEmojiOpen,
    onBackFromEmoji: () -> Unit = {},
    onEmojiSearch: () -> Unit = {},
    emojiInitialCategory: Int = 0,
    recentEmojisProvider: () -> List<String> = { emptyList() }
) {
    // Feature 3.2: Select color scheme based on theme preference
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val systemDark = isSystemInDarkTheme()
    val colors = when (themePref) {
        "light" -> LightColors
        "dark" -> DarkColors
        "amoled" -> AmoledColors
        else -> if (systemDark) DarkColors else LightColors // "auto"
    }
    val baseHeightScale = when (keyboardHeightMode) {
        "compact" -> 0.90f
        "tall" -> 1.10f
        else -> 1.0f
    }
    val heightScale = baseHeightScale * if (isLandscape) 0.78f else 1.0f
    val baseFontScale = when (keyboardFontSizeMode) {
        "small" -> 1.0f
        "large" -> 1.08f
        "extra_large" -> 1.16f
        else -> 1.08f
    }
    val fontScale = baseFontScale * if (isLandscape) 0.94f else 1.0f

    CompositionLocalProvider(
        LocalKeyboardColors provides colors,
        LocalHapticEnabled provides hapticEnabled,
        LocalSoundEnabled provides soundEnabled,
        LocalKeyPreviewEnabled provides keyPreviewEnabled,
        LocalKeyboardHeightScale provides heightScale,
        LocalKeyboardFontScale provides fontScale
    ) {
        val navBottomPadding = WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()
        val bottomSafePadding = if (isLandscape) navBottomPadding else maxOf(navBottomPadding, NavigationFallbackBottomPadding)
        val isVoiceActive = voiceInputState == VoiceInputState.LISTENING ||
            voiceInputState == VoiceInputState.PROCESSING
        val showNumberRow = numberRowEnabled && !isLandscape

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.keyboardBg)
                .padding(horizontal = if (isLandscape) 4.dp else KeyboardPadding)
                .padding(top = if (isLandscape) 1.dp else 3.dp, bottom = bottomSafePadding)
        ) {
            if (voiceInputState != VoiceInputState.IDLE) {
                VoiceStatusPanel(
                    state = voiceInputState,
                    level = voiceInputLevel,
                    onRetry = onVoiceInput,
                    onStop = onVoiceStop,
                    onCancel = onVoiceCancel
                )
                Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))
            }

            when (keyboardMode) {
                KeyboardMode.BANGLU -> {
                    if (!isVoiceActive) {
                        AdaptiveTopStrip(
                            suggestions = if (suggestionsEnabled) suggestionsProvider() else emptyList(),
                            onSuggestionClick = onSuggestionClick,
                            onSettingsClick = onSettingsClick,
                            onEmojiOpen = onEmojiOpen,
                            onStickerOpen = onStickerOpen,
                            onClipboardOpen = onClipboardOpen,
                            onVoiceInput = onVoiceInput,
                            onPunctuationPress = onPunctuationPress,
                            voiceInputState = voiceInputState,
                            onToggleToolbar = onToggleToolbar,
                            isToolbarExpanded = isToolbarExpanded
                        )
                        Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))
                    }
                    if (showNumberRow) {
                        NumberRow(
                            useBanglaDigits = true,
                            onNumberPress = onNumberPress,
                            onSymbolPress = onPunctuationPress
                        )
                        Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))
                    }
                    LetterRows(
                        shiftState = shiftState,
                        useShiftedLetterInput = false,
                        onKeyPress = onKeyPress,
                        onTextInput = onTextInput,
                        onBackspace = onBackspace,
                        onBackspaceRepeat = onBackspaceRepeat,
                        onBackspaceWord = onBackspaceWord,
                        onShiftTap = onShiftTap
                    )
                    Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))
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
                    KeyboardActionBar(
                        onSettingsClick = onSettingsClick,
                        onEmojiOpen = onEmojiOpen,
                        onStickerOpen = onStickerOpen,
                        onClipboardOpen = onClipboardOpen,
                        onVoiceInput = onVoiceInput,
                        onPunctuationPress = onPunctuationPress,
                        voiceInputState = voiceInputState,
                        onToggleToolbar = onToggleToolbar
                    )
                    Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))
                    if (showNumberRow) {
                        NumberRow(
                            useBanglaDigits = false,
                            onNumberPress = onNumberPress,
                            onSymbolPress = onPunctuationPress
                        )
                        Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))
                    }
                    LetterRows(
                        shiftState = shiftState,
                        useShiftedLetterInput = true,
                        onKeyPress = onKeyPress,
                        onTextInput = onTextInput,
                        onBackspace = onBackspace,
                        onBackspaceRepeat = onBackspaceRepeat,
                        onBackspaceWord = onBackspaceWord,
                        onShiftTap = onShiftTap
                    )
                    Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))
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
                    KeyboardActionBar(
                        onSettingsClick = onSettingsClick,
                        onEmojiOpen = onEmojiOpen,
                        onStickerOpen = onStickerOpen,
                        onClipboardOpen = onClipboardOpen,
                        onVoiceInput = onVoiceInput,
                        onPunctuationPress = onPunctuationPress,
                        voiceInputState = voiceInputState,
                        onToggleToolbar = onToggleToolbar
                    )
                    Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))
                    if (showNumberRow) {
                        NumberRow(
                            useBanglaDigits = false,
                            onNumberPress = onNumberPress,
                            onSymbolPress = onPunctuationPress
                        )
                        Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))
                    }
                    SymbolRows(
                        rows = SYMBOLS_1_ROWS,
                        pageLabel = "1/2",
                        onSymbolPress = onPunctuationPress,
                        onBackspace = onBackspace,
                        onBackspaceRepeat = onBackspaceRepeat,
                        onBackspaceWord = onBackspaceWord,
                        onPageToggle = onSymbolPageToggle
                    )
                    Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))
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
                    KeyboardActionBar(
                        onSettingsClick = onSettingsClick,
                        onEmojiOpen = onEmojiOpen,
                        onStickerOpen = onStickerOpen,
                        onClipboardOpen = onClipboardOpen,
                        onVoiceInput = onVoiceInput,
                        onPunctuationPress = onPunctuationPress,
                        voiceInputState = voiceInputState,
                        onToggleToolbar = onToggleToolbar
                    )
                    Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))
                    if (showNumberRow) {
                        NumberRow(
                            useBanglaDigits = false,
                            onNumberPress = onNumberPress,
                            onSymbolPress = onPunctuationPress
                        )
                        Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))
                    }
                    SymbolRows(
                        rows = SYMBOLS_2_ROWS,
                        pageLabel = "2/2",
                        onSymbolPress = onPunctuationPress,
                        onBackspace = onBackspace,
                        onBackspaceRepeat = onBackspaceRepeat,
                        onBackspaceWord = onBackspaceWord,
                        onPageToggle = onSymbolPageToggle
                    )
                    Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))
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
                        initialCategory = emojiInitialCategory,
                        recentEmojisProvider = recentEmojisProvider,
                        onEmojiClick = onEmojiClick,
                        onEmojiSearch = onEmojiSearch,
                        onBackToKeyboard = onBackFromEmoji,
                        onBackspace = onBackspace,
                        onDismiss = onDismiss
                    )
                }
                KeyboardMode.CLIPBOARD -> {
                    ClipboardPanel(
                        colors = colors,
                        itemsProvider = clipboardItemsProvider,
                        onPaste = onClipboardPaste,
                        onClear = onClipboardClear,
                        onBackToKeyboard = onBackToLetters
                    )
                }
            }
        }
    }
}

@Composable
private fun BangluSuggestionHost(
    suggestionsProvider: () -> List<SmartSuggestion>,
    onSuggestionClick: (SmartSuggestion) -> Unit
) {
    val colors = LocalKeyboardColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(scaledDp(TopStripHeight))
            .background(colors.suggestionBg)
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        val currentSuggestions = suggestionsProvider()
        if (currentSuggestions.isNotEmpty()) {
            BangluSuggestionRow(currentSuggestions, onSuggestionClick)
        }
    }
}

@Composable
private fun AdaptiveTopStrip(
    suggestions: List<SmartSuggestion>,
    onSuggestionClick: (SmartSuggestion) -> Unit,
    onSettingsClick: () -> Unit,
    onEmojiOpen: () -> Unit,
    onStickerOpen: () -> Unit,
    onClipboardOpen: () -> Unit,
    onVoiceInput: () -> Unit,
    onPunctuationPress: (Char) -> Unit,
    voiceInputState: VoiceInputState,
    onToggleToolbar: () -> Unit,
    isToolbarExpanded: Boolean,
) {
    val colors = LocalKeyboardColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(scaledDp(TopStripHeight))
            .background(colors.suggestionBg)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center
    ) {
        if (isToolbarExpanded) {
            ToolbarRow(
                onSettingsClick = onSettingsClick,
                onEmojiOpen = onEmojiOpen,
                onStickerOpen = onStickerOpen,
                onClipboardOpen = onClipboardOpen,
                onVoiceInput = onVoiceInput,
                voiceInputState = voiceInputState,
                onToggleToolbar = onToggleToolbar,
                isExpanded = true
            )
        } else if (suggestions.isNotEmpty()) {
            // Punctuation predictions fill the strip on an idle buffer, which
            // used to make the mic and toolbar unreachable — keep both pinned
            // at the strip's end whenever the user is not mid-word.
            val idlePunctuationBar = suggestions.all { it.tier == "punctuation" }
            if (idlePunctuationBar) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        BangluSuggestionRow(suggestions, onSuggestionClick)
                    }
                    CompactMicToolbarIcon(
                        active = voiceInputState == VoiceInputState.LISTENING,
                        modifier = Modifier.width(52.dp),
                        onClick = onVoiceInput
                    )
                    CompactIconSlot(
                        "More tools",
                        modifier = Modifier.width(44.dp),
                        onClick = onToggleToolbar
                    ) {
                        IconDots(Modifier.size(21.dp), it)
                    }
                }
            } else {
                BangluSuggestionRow(suggestions, onSuggestionClick)
            }
        } else {
            KeyboardActionBar(
                onSettingsClick = onSettingsClick,
                onEmojiOpen = onEmojiOpen,
                onStickerOpen = onStickerOpen,
                onClipboardOpen = onClipboardOpen,
                onVoiceInput = onVoiceInput,
                onPunctuationPress = onPunctuationPress,
                voiceInputState = voiceInputState,
                onToggleToolbar = onToggleToolbar
            )
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
    onStickerOpen: () -> Unit,
    onClipboardOpen: () -> Unit,
    onVoiceInput: () -> Unit,
    voiceInputState: VoiceInputState,
    onToggleToolbar: () -> Unit,
    isExpanded: Boolean
) {
    val colors = LocalKeyboardColors.current
    val height = scaledDp(if (isExpanded) ToolbarExpandedHeight else ToolbarCollapsedHeight)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(colors.suggestionBg)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isExpanded) {
            ToolbarIconSlot("Clipboard", modifier = Modifier.weight(1f), onClick = onClipboardOpen) {
                IconClipboard(Modifier.size(22.dp), it)
            }
            ToolbarIconSlot("Emoji", modifier = Modifier.weight(1f), onClick = onEmojiOpen) {
                IconEmoji(Modifier.size(22.dp), it)
            }
            ToolbarIconSlot("Stickers", modifier = Modifier.weight(1f), onClick = onStickerOpen) {
                IconSticker(Modifier.size(22.dp), it)
            }
            ToolbarIconSlot(
                "Bangla voice typing",
                highlighted = true,
                active = voiceInputState == VoiceInputState.LISTENING || voiceInputState == VoiceInputState.PROCESSING,
                modifier = Modifier.weight(1f),
                onClick = onVoiceInput
            ) {
                MicGlyph(Modifier.size(21.dp), it)
            }
            ToolbarIconSlot("Settings", modifier = Modifier.weight(1f), onClick = onSettingsClick) {
                IconGear(Modifier.size(22.dp), it)
            }
        }
        // Toggle button always visible
        ToolbarIconSlot(
            if (isExpanded) "Collapse toolbar" else "Expand toolbar",
            modifier = Modifier.weight(1f),
            onClick = onToggleToolbar
        ) {
            if (isExpanded) IconChevronDown(Modifier.size(22.dp), it)
            else IconDots(Modifier.size(22.dp), it)
        }
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
    val configuration = LocalConfiguration.current
    val compact = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val message = when (state) {
        VoiceInputState.LISTENING -> "\u09ac\u09be\u0982\u09b2\u09be\u09df \u09ac\u09b2\u09c1\u09a8"
        VoiceInputState.PROCESSING -> "\u09b2\u09c7\u0996\u09be \u09b9\u099a\u09cd\u099b\u09c7\u2026"
        VoiceInputState.STOPPED -> "\u09ad\u09df\u09c7\u09b8 \u09a5\u09be\u09ae\u09be\u09a8\u09cb \u09b9\u09df\u09c7\u099b\u09c7"
        VoiceInputState.PERMISSION_REQUIRED -> "\u09ae\u09be\u0987\u0995\u09cd\u09b0\u09cb\u09ab\u09cb\u09a8 \u09aa\u09be\u09b0\u09ae\u09bf\u09b6\u09a8 \u09a6\u09bf\u09a8"
        VoiceInputState.UNAVAILABLE -> "\u09ad\u09df\u09c7\u09b8 \u09b8\u09be\u09b0\u09cd\u09ad\u09bf\u09b8 \u09aa\u09be\u0993\u09df\u09be \u09af\u09be\u09df\u09a8\u09bf"
        VoiceInputState.ERROR -> "\u0986\u09ac\u09be\u09b0 \u099a\u09c7\u09b7\u09cd\u099f\u09be \u0995\u09b0\u09c1\u09a8"
        VoiceInputState.IDLE -> ""
    }
    val detail = when (state) {
        VoiceInputState.STOPPED -> "\u0986\u09ac\u09be\u09b0 \u09ac\u09b2\u09a4\u09c7 \u09b0\u09bf\u099f\u09cd\u09b0\u09be\u0987 \u099a\u09be\u09aa\u09c1\u09a8"
        VoiceInputState.PERMISSION_REQUIRED -> "Banglu \u0985\u09cd\u09af\u09be\u09aa\u09c7 \u0985\u09a8\u09c1\u09ae\u09a4\u09bf \u099a\u09be\u09b2\u09c1 \u0995\u09b0\u09c1\u09a8"
        VoiceInputState.UNAVAILABLE -> "\u09a1\u09bf\u09ad\u09be\u0987\u09b8\u09c7 speech service \u09a8\u09c7\u0987"
        VoiceInputState.ERROR -> "\u09ae\u09be\u0987\u0995 \u099a\u09c7\u0995 \u0995\u09b0\u09c7 \u0986\u09ac\u09be\u09b0 \u099a\u09c7\u09b7\u09cd\u099f\u09be \u0995\u09b0\u09c1\u09a8"
        else -> ""
    }
    val isActive = state == VoiceInputState.LISTENING || state == VoiceInputState.PROCESSING
    val isTrouble = state == VoiceInputState.ERROR ||
        state == VoiceInputState.PERMISSION_REQUIRED ||
        state == VoiceInputState.UNAVAILABLE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.suggestionChipBg)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = listOf(message, detail).filter { it.isNotBlank() }.joinToString(". ")
            }
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MicBadge(
            active = state == VoiceInputState.LISTENING,
            level = level,
            modifier = Modifier.size(if (compact) 36.dp else 48.dp),
            idleInk = if (isTrouble) BangluVoiceAccent else colors.keyText,
            idleBg = colors.keyBg
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (compact) 8.dp else 12.dp, end = 8.dp)
        ) {
            Text(
                text = message,
                color = if (isTrouble) BangluVoiceAccent else colors.keyText,
                fontSize = scaledSp(if (compact) 13 else 15),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isActive) {
                Spacer(modifier = Modifier.height(if (compact) 2.dp else 4.dp))
                CenterWaveform(
                    level = if (state == VoiceInputState.LISTENING) level else 0f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 12.dp else 18.dp)
                )
            } else if (detail.isNotBlank() && !compact) {
                Text(
                    text = detail,
                    color = colors.subText,
                    fontSize = scaledSp(12),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        val buttonSize = if (compact) 30.dp else 38.dp
        if (isActive) {
            VoiceRoundButton("\u09a5\u09be\u09ae\u09be\u09a8", buttonSize, filled = true, onClick = onStop) {
                IconStop(Modifier.size(buttonSize * 0.5f), it)
            }
            Spacer(modifier = Modifier.width(if (compact) 6.dp else 8.dp))
            VoiceRoundButton("\u09ac\u09be\u09a4\u09bf\u09b2", buttonSize, filled = false, onClick = onCancel) {
                IconClose(Modifier.size(buttonSize * 0.52f), it)
            }
        } else if (state == VoiceInputState.STOPPED) {
            VoiceRoundButton("\u0986\u09ac\u09be\u09b0", buttonSize, filled = true, onClick = onRetry) {
                IconRetry(Modifier.size(buttonSize * 0.52f), it)
            }
            Spacer(modifier = Modifier.width(if (compact) 6.dp else 8.dp))
            VoiceRoundButton("\u09ac\u09be\u09a4\u09bf\u09b2", buttonSize, filled = false, onClick = onCancel) {
                IconClose(Modifier.size(buttonSize * 0.52f), it)
            }
        } else {
            VoiceRoundButton("\u0986\u09ac\u09be\u09b0", buttonSize, filled = true, onClick = onRetry) {
                IconRetry(Modifier.size(buttonSize * 0.52f), it)
            }
        }
    }
}

/** Circular voice-bar control: filled terracotta for the primary action,
 *  hairline ghost for the secondary. */
@Composable
private fun VoiceRoundButton(
    label: String,
    size: Dp,
    filled: Boolean,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit
) {
    val colors = LocalKeyboardColors.current
    Box(
        modifier = Modifier
            .size(size)
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .clip(RoundedCornerShape(size / 2))
            .background(if (filled) BangluVoiceAccent else Color.Transparent)
            .then(
                if (filled) Modifier
                else Modifier.border(1.dp, colors.subText.copy(alpha = 0.55f), RoundedCornerShape(size / 2))
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        icon(if (filled) Color.White else colors.keyText)
    }
}

@Composable
private fun ToolbarIconSlot(
    accessibilityLabel: String,
    highlighted: Boolean = false,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit
) {
    val colors = LocalKeyboardColors.current
    val shape = if (highlighted) RoundedCornerShape(18.dp) else RoundedCornerShape(10.dp)
    val background = when {
        highlighted && active -> BangluVoiceAccent
        highlighted -> BangluVoiceAccent.copy(alpha = 0.16f)
        else -> Color.Transparent
    }
    val tint = when {
        highlighted && active -> Color.White
        highlighted -> BangluVoiceAccent
        else -> colors.subText
    }
    Box(
        modifier = modifier
            .height(scaledDp(ToolbarExpandedHeight))
            .semantics {
                role = Role.Button
                contentDescription = accessibilityLabel
                if (active) stateDescription = "Active"
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (highlighted) 38.dp else 34.dp)
                .clip(shape)
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            icon(tint)
        }
    }
}

@Composable
private fun CompactIconSlot(
    accessibilityLabel: String,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit
) {
    val colors = LocalKeyboardColors.current
    Box(
        modifier = modifier
            .height(scaledDp(ToolbarCollapsedHeight))
            .semantics {
                role = Role.Button
                contentDescription = accessibilityLabel
                if (active) stateDescription = "Active"
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (active) BangluVoiceAccent else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            icon(if (active) Color.White else colors.subText)
        }
    }
}

@Composable
private fun ToolbarIcon(
    icon: String,
    accessibilityLabel: String = icon,
    highlighted: Boolean = false,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    val shape = if (highlighted) RoundedCornerShape(18.dp) else RoundedCornerShape(8.dp)
    val background = when {
        highlighted && active -> Color(0xFF1FA463)
        highlighted -> Color(0xFF263B30)
        else -> Color.Transparent
    }
    val textColor = when {
        highlighted -> Color.White
        else -> colors.subText
    }
    Box(
        modifier = modifier
            .height(scaledDp(ToolbarExpandedHeight))
            .semantics {
                role = Role.Button
                contentDescription = accessibilityLabel
                if (active) stateDescription = "Active"
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (highlighted) 36.dp else 32.dp)
                .clip(shape)
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                icon,
                fontSize = if (highlighted) scaledSp(19) else scaledSp(18),
                color = textColor,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Quick Action Bar
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun KeyboardActionBar(
    onSettingsClick: () -> Unit,
    onEmojiOpen: () -> Unit,
    onStickerOpen: () -> Unit,
    onClipboardOpen: () -> Unit,
    onVoiceInput: () -> Unit,
    onPunctuationPress: (Char) -> Unit,
    voiceInputState: VoiceInputState,
    onToggleToolbar: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(scaledDp(TopStripHeight))
            .background(colors.suggestionBg)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactIconSlot("Emoji", modifier = Modifier.weight(1f), onClick = onEmojiOpen) {
            IconEmoji(Modifier.size(21.dp), it)
        }
        CompactIconSlot("Stickers", modifier = Modifier.weight(1f), onClick = onStickerOpen) {
            IconSticker(Modifier.size(21.dp), it)
        }
        CompactToolbarIcon("\u0964", "Insert dari", modifier = Modifier.weight(1f)) { onPunctuationPress('\u0964') }
        CompactIconSlot("Clipboard", modifier = Modifier.weight(1f), onClick = onClipboardOpen) {
            IconClipboard(Modifier.size(21.dp), it)
        }
        CompactMicToolbarIcon(
            active = voiceInputState == VoiceInputState.LISTENING || voiceInputState == VoiceInputState.PROCESSING,
            onClick = onVoiceInput,
            modifier = Modifier.weight(1f)
        )
        CompactIconSlot("Settings", modifier = Modifier.weight(1f), onClick = onSettingsClick) {
            IconGear(Modifier.size(21.dp), it)
        }
        CompactIconSlot("More tools", modifier = Modifier.weight(1f), onClick = onToggleToolbar) {
            IconDots(Modifier.size(21.dp), it)
        }
    }
}

@Composable
private fun CompactToolbarIcon(
    label: String,
    accessibilityLabel: String,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    Box(
        modifier = modifier
            .height(scaledDp(ToolbarCollapsedHeight))
            .semantics {
                role = Role.Button
                contentDescription = accessibilityLabel
                if (active) stateDescription = "Active"
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (active) Color(0xFF263B30) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (active) Color.White else colors.subText,
                fontSize = if (label.length > 1 && label.all { it.isDigit() || it.isLetter() }) scaledSp(13) else scaledSp(18),
                fontWeight = if (active || label == "123" || label == "ABC") FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CompactMicToolbarIcon(
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    Box(
        modifier = modifier
            .height(scaledDp(ToolbarCollapsedHeight))
            .semantics {
                role = Role.Button
                contentDescription = "Bangla voice typing"
                if (active) stateDescription = "Listening"
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (active) BangluVoiceAccent else BangluVoiceAccent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            MicGlyph(
                modifier = Modifier.size(20.dp),
                color = if (active) Color.White else BangluVoiceAccent
            )
        }
    }
}

@Composable
private fun ClipboardPanel(
    colors: KeyboardColors,
    itemsProvider: () -> List<String>,
    onPaste: (String) -> Unit,
    onClear: () -> Unit,
    onBackToKeyboard: () -> Unit
) {
    val items = itemsProvider()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp)
            .background(colors.keyboardBg)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyButton(
                label = "ABC",
                modifier = Modifier.width(74.dp),
                height = 38.dp,
                bgColor = colors.specialKeyBg,
                fontSize = 15,
                accessibilityLabel = "Back to keyboard",
                onClick = onBackToKeyboard
            )
            Text(
                text = "Clipboard",
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                color = colors.keyText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            KeyButton(
                label = "Clear",
                modifier = Modifier.width(84.dp),
                height = 38.dp,
                bgColor = colors.specialKeyBg,
                fontSize = 14,
                accessibilityLabel = "Clear clipboard history",
                onClick = onClear
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.keyBg)
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No clipboard history",
                    color = colors.subText,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items.size) { index ->
                    val item = items[index]
                    ClipboardHistoryItem(
                        text = item,
                        colors = colors,
                        onPaste = { onPaste(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipboardHistoryItem(
    text: String,
    colors: KeyboardColors,
    onPaste: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.keyBg)
            .clickable { onPaste() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text.replace('\n', ' '),
            color = colors.keyText,
            fontSize = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MicGlyph(
    modifier: Modifier = Modifier,
    color: Color
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = (w * 0.095f).coerceAtLeast(2f)
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)

        // Microphone capsule.
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.32f, h * 0.04f),
            size = Size(w * 0.36f, h * 0.58f),
            cornerRadius = CornerRadius(w * 0.20f, w * 0.20f),
            style = stroke
        )

        // Outer U-shaped mic stand.
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.34f),
            size = Size(w * 0.56f, h * 0.48f),
            style = stroke
        )

        // Side sound bars.
        drawLine(
            color = color,
            start = Offset(w * 0.10f, h * 0.35f),
            end = Offset(w * 0.10f, h * 0.47f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.22f, h * 0.30f),
            end = Offset(w * 0.22f, h * 0.48f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.78f, h * 0.30f),
            end = Offset(w * 0.78f, h * 0.48f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.90f, h * 0.35f),
            end = Offset(w * 0.90f, h * 0.47f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Stem and base.
        drawLine(
            color = color,
            start = Offset(w * 0.50f, h * 0.76f),
            end = Offset(w * 0.50f, h * 0.88f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.28f, h * 0.94f),
            end = Offset(w * 0.72f, h * 0.94f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
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
    val colors = LocalKeyboardColors.current
    // Stable item keys make LazyRow anchor scroll to chips that persist
    // across keystrokes (continuation chips keep identical keys), rendering
    // fresh top-ranked chips off-screen left. Every list update is a new
    // ranking — snap back so rank 1 is always visible.
    val stripState = rememberLazyListState()
    LaunchedEffect(suggestions) { stripState.scrollToItem(0) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(scaledDp(TopStripHeight))
            .background(colors.suggestionBg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            state = stripState,
            modifier = Modifier
                .fillMaxWidth()
                .height(scaledDp(TopStripHeight)),
            contentPadding = PaddingValues(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(suggestions, key = { "${it.bengali}|${it.source}|${it.tier}" }) { suggestion ->
                val isFirst = suggestion == suggestions.firstOrNull()
                // Feature 4.4: Prediction chips use different styling
                val isPrediction = suggestion.tier == "prediction"
                val isPunctuation = suggestion.tier == "punctuation"
                val chipBg = if (isFirst && !isPunctuation) colors.suggestionHighlight
                    else if (isPrediction) colors.keyBg
                    else colors.suggestionChipBg
                val chipTextColor = if (isFirst && !isPunctuation) Color.White else colors.keyText

                Box(
                    modifier = Modifier
                        .semantics {
                            role = Role.Button
                            contentDescription = "Suggestion ${suggestion.bengali}"
                            if (isFirst) stateDescription = "Primary suggestion"
                        }
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
                            fontSize = if (isPunctuation) scaledSp(17) else scaledSp(15),
                            fontWeight = if (isFirst) FontWeight.Medium else FontWeight.Normal,
                            fontStyle = if (isPrediction) FontStyle.Italic else FontStyle.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (suggestion.phonetic.isNotEmpty() && isFirst) {
                            Text(
                                text = displayPhoneticHint(suggestion.phonetic),
                                color = colors.subText,
                                fontSize = scaledSp(9),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PunctuationSuggestionRow(
    onPunctuationPress: (Char) -> Unit
) {
    val colors = LocalKeyboardColors.current
    val punctuation = listOf(
        "\u0964" to '\u0964',
        "," to ',',
        "?" to '?',
        "!" to '!',
        "\u0983" to '\u0983',
        ":" to ':'
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(scaledDp(TopStripHeight))
            .background(colors.suggestionBg),
        contentPadding = PaddingValues(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(punctuation) { item ->
            val (label, char) = item
            Box(
                modifier = Modifier
                    .semantics {
                        role = Role.Button
                        contentDescription = "Insert $label"
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.suggestionChipBg)
                    .clickable { onPunctuationPress(char) }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = colors.keyText,
                    fontSize = scaledSp(16),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Number Row (always visible)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun NumberRow(
    useBanglaDigits: Boolean,
    onNumberPress: (Char) -> Unit,
    onSymbolPress: (Char) -> Unit
) {
    val height = scaledKeyHeight(NumberRowHeight)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(currentKeyGapH())
    ) {
        for (num in '1'..'9') {
            NumberKey(
                number = num,
                displayNumber = if (useBanglaDigits) banglaDigitLabel(num) else num,
                modifier = Modifier.weight(1f),
                height = height,
                onNumberPress = onNumberPress,
                onSymbolPress = onSymbolPress
            )
        }
        NumberKey(
            number = '0',
            displayNumber = if (useBanglaDigits) banglaDigitLabel('0') else '0',
            modifier = Modifier.weight(1f),
            height = height,
            onNumberPress = onNumberPress,
            onSymbolPress = onSymbolPress
        )
    }
}

private fun banglaDigitLabel(number: Char): Char {
    return when (number) {
        '0' -> '\u09E6'
        '1' -> '\u09E7'
        '2' -> '\u09E8'
        '3' -> '\u09E9'
        '4' -> '\u09EA'
        '5' -> '\u09EB'
        '6' -> '\u09EC'
        '7' -> '\u09ED'
        '8' -> '\u09EE'
        '9' -> '\u09EF'
        else -> number
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
    onBackspaceRepeat: (Int) -> Unit = { count -> repeat(count) { onBackspace() } },
    onBackspaceWord: () -> Unit = {},
    onShiftTap: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    val keyHeight = scaledKeyHeight(LetterKeyRowHeight)

    // Row 1: q w e r t y u i o p — S11: no spacedBy dead strips; the visual
    // gap lives inside each cell so every pixel of the row hits a key.
    val letterHitPad = currentKeyGapH() / 2
    Row(modifier = Modifier.fillMaxWidth()) {
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
                hitPaddingH = letterHitPad,
                onReplaceLast = { alt -> onBackspace(); onTextInput(alt) },
                onClick = { onKeyPress(input) }
            )
        }
    }

    Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))

    // Row 2: a s d f g h j k l (indented)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = middleLetterRowIndent())
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
                hitPaddingH = letterHitPad,
                onReplaceLast = { alt -> onBackspace(); onTextInput(alt) },
                onClick = { onKeyPress(input) }
            )
        }
    }

    Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))

    // Row 3: Shift z x c v b n m Backspace
    Row(modifier = Modifier.fillMaxWidth()) {
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
            hitPaddingH = letterHitPad,
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
                hitPaddingH = letterHitPad,
                onReplaceLast = { alt -> onBackspace(); onTextInput(alt) },
                onClick = { onKeyPress(input) }
            )
        }

        // Backspace with long-press repeat and word deletion
        BackspaceKey(
            modifier = Modifier
                .weight(1.5f)
                .padding(horizontal = letterHitPad),
            height = keyHeight,
            onBackspace = onBackspace,
            onBackspaceRepeat = onBackspaceRepeat,
            onBackspaceWord = onBackspaceWord
        )
    }
}

private val EMPTY_KEY_ALTERNATIVES = emptyList<KeyAlternative>()
private val LONG_PRESS_ALTERNATIVES = mapOf(
    't' to listOf(KeyAlternative("ট", "ট")),
    'd' to listOf(KeyAlternative("ড", "ড")),
    'r' to listOf(KeyAlternative("ড়", "ড়")),
    's' to listOf(KeyAlternative("শ", "sh")),
    'i' to listOf(KeyAlternative("ঈ", "ii")),
    'u' to listOf(KeyAlternative("ঊ", "uu"))
)

private fun longPressAlternatives(char: Char): List<KeyAlternative> {
    return LONG_PRESS_ALTERNATIVES[char.lowercaseChar()] ?: EMPTY_KEY_ALTERNATIVES
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
    onBackspaceRepeat: (Int) -> Unit = { count -> repeat(count) { onBackspace() } },
    onBackspaceWord: () -> Unit = {},
    onPageToggle: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    val keyHeight = scaledKeyHeight(LetterKeyRowHeight)

    // Symbol row 1 (10 keys)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(currentKeyGapH())
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

    Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))

    // Symbol row 2 (10 keys)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(currentKeyGapH())
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

    Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))

    // Symbol row 3: [page toggle] symbols... [backspace]
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(currentKeyGapH())
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
            onBackspaceRepeat = onBackspaceRepeat,
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
    val keyHeight = scaledKeyHeight(BottomKeyRowHeight)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(currentKeyGapH())
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

        // Enter -- context-aware label (search, next, go, etc.)
        EnterActionKey(
            label = enterLabel,
            modifier = Modifier.weight(1.5f),
            height = keyHeight,
            bgColor = colors.specialKeyBg,
            onClick = onEnter
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Individual Key Composables
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EnterActionKey(
    label: String,
    modifier: Modifier = Modifier,
    height: Dp = BottomKeyRowHeight,
    bgColor: Color,
    onClick: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val hapticOn = LocalHapticEnabled.current
    val soundOn = LocalSoundEnabled.current
    val currentOnClick by rememberUpdatedState(onClick)
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 50)
    )
    val keyShape = RoundedCornerShape(KeyCorner)
    val isHorizontalArrow = label == "\u2192" || label == "\u21E5"
    val isReturnArrow = label == "\u21B5"

    Box(
        modifier = modifier
            .height(height)
            .semantics {
                role = Role.Button
                contentDescription = "Enter"
            }
            .pointerInput(label) {
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
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = KeyVisualPaddingH, vertical = KeyVisualPaddingV)
                .shadow(if (isPressed) 0.dp else 1.5.dp, keyShape, clip = false)
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
                fontSize = scaledSp(
                    when {
                        isHorizontalArrow -> 32
                        isReturnArrow -> 32
                        else -> 30
                    }
                ),
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.offset(
                    x = when (label) {
                        "\u21E5" -> (-1).dp
                        "\u21B5" -> (-5).dp
                        else -> 0.dp
                    },
                    y = when (label) {
                        "\u2192", "\u21E5" -> (-1).dp
                        "\u21B5" -> (-12).dp
                        else -> 0.dp
                    }
                )
            )
        }
    }
}

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
    /** S11: keeps the visual key gap INSIDE the touch cell so rows tile
     *  edge-to-edge with no dead strips between keys. */
    hitPaddingH: Dp = 0.dp,
    /** S11: with commit-on-press the base character is already committed when
     *  the long-press popup opens; selecting an alternative must REPLACE it. */
    onReplaceLast: ((String) -> Unit)? = null,
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
            .semantics {
                role = Role.Button
                contentDescription = if (longPressOptions.isEmpty()) {
                    accessibilityLabel
                } else {
                    "$accessibilityLabel. Long press for alternatives"
                }
            }
            .pointerInput(longPressOptions) {
                // S11 key accuracy: commit on pointer DOWN, not on tap-release.
                // detectTapGestures fired onTap at finger-UP and cancelled on
                // slide-out, so fast typing produced release-order transposition
                // ("the" -> "hte") and slightly-sliding taps dropped entirely —
                // both perceived as "I pressed one key and got another".
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    isPressed = true
                    if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (soundOn) view.playSoundEffect(SoundEffectConstants.CLICK)
                    currentOnClick()
                    var longPressed = false
                    if (longPressOptions.isNotEmpty()) {
                        try {
                            withTimeout(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                    if (event.changes.all { !it.pressed }) return@withTimeout
                                }
                            }
                        } catch (_: PointerEventTimeoutCancellationException) {
                            longPressed = true
                            showAlternatives = true
                            if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                    if (!longPressed) {
                        // Slide-tolerant release wait: pointer capture keeps the
                        // gesture on this key even if the finger drifts.
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                            if (event.changes.all { !it.pressed }) break
                        }
                    }
                    isPressed = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = hitPaddingH + KeyVisualPaddingH, vertical = KeyVisualPaddingV)
                .shadow(if (isPressed) 0.dp else 1.5.dp, keyShape, clip = false)
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
            fontSize = if (isPressed && isCharKey && previewOn) scaledSp(fontSize + 2) else scaledSp(fontSize),
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
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Insert ${option.label}"
                                }
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.specialKeyBg)
                                .clickable {
                                    showAlternatives = false
                                    if (soundOn) view.playSoundEffect(SoundEffectConstants.CLICK)
                                    (onReplaceLast ?: onTextInput)(option.input)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option.label,
                                color = colors.keyText,
                                fontSize = scaledSp(22),
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
    var cursorDragUsed by remember { mutableStateOf(false) }
    var lastCursorDragEndedAt by remember { mutableStateOf(0L) }
    var dragRemainderX by remember { mutableFloatStateOf(0f) }
    var cursorMoveCount by remember { mutableIntStateOf(0) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(50)
    )
    val keyShape = RoundedCornerShape(KeyCorner)

    Box(
        modifier = modifier
            .height(height)
            .semantics {
                role = Role.Button
                contentDescription = "Spacebar. Drag left or right to move cursor"
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isCursorMode = true
                        cursorDragUsed = true
                        dragRemainderX = 0f
                        cursorMoveCount = 0
                        if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragRemainderX += dragAmount.x
                        val threshold = 14.dp.toPx()
                        while (kotlin.math.abs(dragRemainderX) >= threshold) {
                            val direction = if (dragRemainderX > 0) 1 else -1
                            currentOnCursorMove(direction)
                            cursorMoveCount++
                            dragRemainderX -= direction * threshold
                            if (hapticOn && cursorMoveCount % 3 == 0) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    },
                    onDragEnd = {
                        isCursorMode = false
                        dragRemainderX = 0f
                        cursorMoveCount = 0
                        lastCursorDragEndedAt = System.currentTimeMillis()
                        cursorDragUsed = false
                    },
                    onDragCancel = {
                        isCursorMode = false
                        dragRemainderX = 0f
                        cursorMoveCount = 0
                        lastCursorDragEndedAt = System.currentTimeMillis()
                        cursorDragUsed = false
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (soundOn) view.playSoundEffect(SoundEffectConstants.CLICK)
                        try { awaitRelease() } finally { isPressed = false }
                        val justDragged = System.currentTimeMillis() - lastCursorDragEndedAt < 120L
                        if (!cursorDragUsed && !justDragged) currentOnClick()
                        cursorDragUsed = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = KeyVisualPaddingH, vertical = KeyVisualPaddingV)
                .shadow(if (isPressed) 0.dp else 1.5.dp, keyShape, clip = false)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(keyShape)
                .background(if (isPressed) colors.keyPressed else colors.keyBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isCursorMode) "\u25C4 \u25BA cursor" else label,
                color = if (isCursorMode) colors.keyText else colors.keyText.copy(alpha = 0.68f),
                fontSize = scaledSp(13),
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
    onBackspaceRepeat: (Int) -> Unit = { count -> repeat(count) { onBackspace() } },
    onBackspaceWord: () -> Unit = {}
) {
    val colors = LocalKeyboardColors.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val hapticOn = LocalHapticEnabled.current
    val soundOn = LocalSoundEnabled.current
    val currentOnBackspace by rememberUpdatedState(onBackspace)
    val currentOnBackspaceRepeat by rememberUpdatedState(onBackspaceRepeat)
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
            .semantics {
                role = Role.Button
                contentDescription = "Backspace. Hold to delete faster"
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (soundOn) view.playSoundEffect(SoundEffectConstants.CLICK)
                        isPressed = true
                        currentOnBackspace()
                        val repeatJob = coroutineScope.launch {
                            delay(230)
                            var cycle = 0
                            while (true) {
                                cycle++
                                if (cycle > 32) {
                                    currentOnBackspaceWord()
                                } else {
                                    val batch = if (cycle > 20) 3 else 1
                                    currentOnBackspaceRepeat(batch)
                                }
                                if (hapticOn && cycle % 8 == 0) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                delay(
                                    when {
                                        cycle > 32 -> 110
                                        cycle > 20 -> 38
                                        else -> 56
                                    }.toLong()
                                )
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = KeyVisualPaddingH, vertical = KeyVisualPaddingV)
                .shadow(if (isPressed) 0.dp else 1.5.dp, keyShape, clip = false)
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
    displayNumber: Char = number,
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
            .semantics {
                role = Role.Button
                contentDescription = "Number $displayNumber, long press for $symbol"
            }
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
                .padding(horizontal = KeyVisualPaddingH, vertical = KeyVisualPaddingV)
                .shadow(if (isPressed) 0.dp else 1.5.dp, keyShape, clip = false)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(keyShape)
                .background(if (isPressed) colors.keyPressed else colors.keyBg)
                .padding(4.dp)
        ) {
            Text(
                text = displayNumber.toString(),
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
    initialCategory: Int,
    recentEmojisProvider: () -> List<String>,
    onEmojiClick: (String) -> Unit,
    onEmojiSearch: () -> Unit,
    onBackToKeyboard: () -> Unit,
    onBackspace: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember(initialCategory) {
        mutableIntStateOf(initialCategory.coerceIn(0, EmojiData.categories.lastIndex.coerceAtLeast(0)))
    }
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var searchRecorded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val hapticOn = LocalHapticEnabled.current
    val soundOn = LocalSoundEnabled.current
    val recentEmojis = recentEmojisProvider()
    val currentCategory = EmojiData.categories.getOrNull(selectedCategory)
    val currentEmojis = if (searchQuery.isNotBlank()) {
        EmojiData.search(searchQuery)
    } else if (selectedCategory == 0 && recentEmojis.isNotEmpty()) {
        recentEmojis
    } else {
        currentCategory?.emojis.orEmpty()
    }
    val showingGifStickers = currentEmojis.any { EmojiData.isGifSticker(it) }
    val showingTextStickers = !showingGifStickers && currentEmojis.any { EmojiData.isTextSticker(it) }
    val showingExpressionCards = showingGifStickers || showingTextStickers

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
                    .semantics {
                        role = Role.Button
                        contentDescription = "Back to keyboard"
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.specialKeyBg)
                    .clickable { onBackToKeyboard() },
                contentAlignment = Alignment.Center
            ) {
                Text("ABC", color = colors.suggestionHighlight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(8.dp))

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
                            .semantics {
                                role = Role.Button
                                contentDescription = "Emoji category ${category.name}"
                                if (index == selectedCategory) stateDescription = "Selected"
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (index == selectedCategory)
                                    colors.suggestionHighlight.copy(alpha = 0.85f)
                                else Color.Transparent
                            )
                            .clickable {
                                selectedCategory = index
                                searchQuery = ""
                                searchActive = false
                                searchRecorded = false
                            },
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
                    .semantics {
                        role = Role.Button
                        contentDescription = "Hide emoji panel"
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Text("\u25BE", color = colors.subText, fontSize = 22.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.suggestionChipBg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable { searchActive = true }
                .semantics {
                    contentDescription = "Search emoji. Tap to type with keyboard search keys"
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = searchQuery.ifBlank {
                    if (searchActive) "Type with the keys below" else "Search emoji, stickers, GIF"
                },
                color = if (searchQuery.isBlank()) colors.subText else colors.keyText,
                fontSize = scaledSp(14),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (searchQuery.isNotBlank()) {
                Text(
                    "\u00D7",
                    color = colors.subText,
                    fontSize = scaledSp(20),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Clear search"
                        }
                        .clickable {
                            searchQuery = ""
                            searchRecorded = false
                        }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (searchActive) 132.dp else 242.dp)
                .padding(horizontal = 6.dp)
        ) {
            if (searchQuery.isNotBlank() && currentEmojis.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "\"$searchQuery\" পাওয়া যায়নি",
                        color = colors.keyText,
                        fontSize = scaledSp(15),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "লিখে দেখুন: hasi, rag, love, birthday",
                        color = colors.subText,
                        fontSize = scaledSp(13),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (showingExpressionCards) 2 else 8),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (showingExpressionCards) 8.dp else 2.dp),
                    verticalArrangement = Arrangement.spacedBy(if (showingExpressionCards) 8.dp else 2.dp)
                ) {
                    items(currentEmojis.size) { index ->
                        val emoji = currentEmojis[index]
                        Box(
                            modifier = Modifier
                                .then(
                                    if (showingExpressionCards) {
                                        Modifier
                                            .height(if (EmojiData.isGifSticker(emoji)) 102.dp else 52.dp)
                                            .fillMaxWidth()
                                    } else {
                                        Modifier.aspectRatio(1f)
                                    }
                                )
                                .semantics {
                                    role = Role.Button
                                    contentDescription = when {
                                        EmojiData.isGifSticker(emoji) -> "GIF $emoji"
                                        showingExpressionCards -> "Sticker $emoji"
                                        else -> "Emoji $emoji"
                                    }
                                }
                                .clickable {
                                    if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (soundOn) view.playSoundEffect(SoundEffectConstants.CLICK)
                                    onEmojiClick(emoji)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                EmojiData.isGifSticker(emoji) -> GifStickerCard(emoji, colors)
                                showingExpressionCards -> TextStickerCard(emoji, colors)
                                else -> EmojiCell(emoji, colors)
                            }
                        }
                    }
                }
            }
        }

        if (searchActive) {
            EmojiSearchKeyboard(
                query = searchQuery,
                onKey = { key ->
                    if (searchQuery.length < 24) {
                        searchQuery += key
                        if (!searchRecorded) {
                            searchRecorded = true
                            onEmojiSearch()
                        }
                    }
                },
                onBackspace = {
                    searchQuery = searchQuery.dropLast(1)
                    if (searchQuery.isBlank()) searchRecorded = false
                },
                onBackToKeyboard = onBackToKeyboard
            )
        } else {
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
                    modifier = Modifier.width(64.dp),
                    height = 40.dp,
                    bgColor = colors.specialKeyBg,
                    fontSize = 15,
                    onClick = onBackToKeyboard
                )
                EmojiBottomCategoryRail(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { index ->
                        selectedCategory = index
                        searchQuery = ""
                        searchActive = false
                        searchRecorded = false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                )
                BackspaceKey(
                    modifier = Modifier.width(56.dp),
                    onBackspace = onBackspace,
                    onBackspaceWord = onBackspace
                )
            }
        }
    }
}

private val BottomEmojiCategoryNames = listOf(
    "Frequent",
    "Smileys",
    "Animals",
    "Food",
    "Activities",
    "Travel",
    "Objects",
    "Symbols",
    "Flags"
)

private fun bottomEmojiCategories(): List<Pair<Int, EmojiData.EmojiCategory>> {
    return BottomEmojiCategoryNames.mapNotNull { name ->
        val index = EmojiData.categories.indexOfFirst { it.name == name }
        if (index >= 0) index to EmojiData.categories[index] else null
    }
}

@Composable
private fun EmojiBottomCategoryRail(
    selectedCategory: Int,
    onCategorySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalKeyboardColors.current
    val categories = remember { bottomEmojiCategories() }
    Row(
        modifier = modifier
            .fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        categories.forEach { (index, category) ->
            val selected = selectedCategory == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics {
                        role = Role.Button
                        contentDescription = "Emoji category ${category.name}"
                        if (selected) stateDescription = "Selected"
                    }
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { onCategorySelected(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (category.name == "Frequent") "\u25F7" else category.icon,
                    color = if (category.isTextIcon || category.name == "Frequent") {
                        if (selected) colors.suggestionHighlight else colors.subText
                    } else {
                        Color.Unspecified
                    },
                    fontSize = if (category.isTextIcon) scaledSp(18) else scaledSp(20),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .height(3.dp)
                            .width(22.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.suggestionHighlight)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmojiCell(
    emoji: String,
    colors: KeyboardColors
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.keyBg.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            emoji,
            fontSize = 27.sp,
            color = Color.Unspecified,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TextStickerCard(
    sticker: String,
    colors: KeyboardColors
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.suggestionChipBg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            sticker,
            color = colors.keyText,
            fontSize = scaledSp(14),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GifStickerCard(
    sticker: String,
    colors: KeyboardColors
) {
    val gif = EmojiData.gifStickerFor(sticker)
    val preview = gifPreviewToken(gif?.fallbackText.orEmpty())
    val label = sticker
        .replace("GIF", "")
        .replace(Regex("\\s+"), " ")
        .trim()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.suggestionChipBg)
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.keyBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = preview.ifBlank { "\u25B6" },
                    fontSize = 30.sp,
                    color = Color.Unspecified,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                color = colors.keyText,
                fontSize = scaledSp(13),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(7.dp))
                .background(colors.suggestionHighlight.copy(alpha = 0.92f))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(
                "GIF",
                color = Color.White,
                fontSize = scaledSp(10),
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

private fun gifPreviewToken(fallbackText: String): String {
    return fallbackText
        .trim()
        .split(Regex("\\s+"))
        .lastOrNull()
        .orEmpty()
        .ifBlank { "\u25B6" }
}

@Composable
private fun EmojiSearchKeyboard(
    query: String,
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onBackToKeyboard: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    val keyHeight = 40.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.keyboardBg)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        EmojiSearchKeyRow(EMOJI_SEARCH_ROW_1, keyHeight, onKey)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(currentKeyGapH())
        ) {
            EMOJI_SEARCH_ROW_2.forEach { key ->
                KeyButton(
                    label = key,
                    modifier = Modifier.weight(1f),
                    height = keyHeight,
                    bgColor = colors.keyBg,
                    fontSize = 18,
                    onClick = { onKey(key) }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(currentKeyGapH()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyButton(
                label = "ABC",
                modifier = Modifier.weight(1.55f),
                height = keyHeight,
                bgColor = colors.specialKeyBg,
                fontSize = 15,
                accessibilityLabel = "Back to keyboard",
                onClick = onBackToKeyboard
            )
            EMOJI_SEARCH_ROW_3.forEach { key ->
                KeyButton(
                    label = key,
                    modifier = Modifier.weight(1f),
                    height = keyHeight,
                    bgColor = colors.keyBg,
                    fontSize = 18,
                    onClick = { onKey(key) }
                )
            }
            KeyButton(
                label = "\u232B",
                modifier = Modifier.weight(1.55f),
                height = keyHeight,
                bgColor = colors.specialKeyBg,
                fontSize = 18,
                accessibilityLabel = if (query.isBlank()) "Backspace search" else "Delete ${query.last()} from search",
                onClick = onBackspace
            )
        }
    }
}

@Composable
private fun EmojiSearchKeyRow(
    keys: List<String>,
    keyHeight: Dp,
    onKey: (String) -> Unit
) {
    val colors = LocalKeyboardColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(currentKeyGapH())
    ) {
        keys.forEach { key ->
            KeyButton(
                label = key,
                modifier = Modifier.weight(1f),
                height = keyHeight,
                bgColor = colors.keyBg,
                fontSize = 18,
                onClick = { onKey(key) }
            )
        }
    }
}
