package com.banglu.keyboard

import android.content.res.Configuration
import android.view.SoundEffectConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.banglu.engine.glide.GlidePoint
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Brush
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
private fun scaledSp(base: Int) =
    KeyLabelScale.systemIndependentSp(base * LocalKeyboardFontScale.current, LocalDensity.current.fontScale).sp

@Composable
private fun scaledSp(base: Float) =
    KeyLabelScale.systemIndependentSp(base * LocalKeyboardFontScale.current, LocalDensity.current.fontScale).sp

@Composable
private fun scaledDp(base: Dp) = base * LocalKeyboardHeightScale.current

@Composable
private fun scaledKeyHeight(base: Dp): Dp {
    // S117: the touch floor must be orientation-aware. Landscape only has
    // 360dp of height on a 20:9 phone — the portrait 48dp floor swallowed the
    // 0.78 landscape scale (49dp * 0.78 = 38.2dp -> clamped back to 48dp) and
    // the keyboard ate 62% of the screen. 38dp rows are the Gboard-class
    // landscape norm; keys there are ~2x wider than portrait, so the touch
    // target area stays comfortably above portrait keys.
    val floor = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        MinKeyTouchHeightLandscape
    } else {
        MinKeyTouchHeight
    }
    return maxOf(base * LocalKeyboardHeightScale.current, floor)
}

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
    val config = LocalConfiguration.current
    // S168 (audit P3-6): proportional — a fixed 24dp ate a whole key's worth
    // of width on 320dp phones. 5.8% = the 24dp the 411dp design assumed.
    return if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) 14.dp
    else (config.screenWidthDp * 0.058f).dp
}

private data class KeyAlternative(val label: String, val input: String)

// ── Dimensions ───────────────────────────────────────────────────────────────────
// S21 seam fix: KeyGapV folded INTO each row's touch cell (row heights +3dp,
// visual inset +1.5dp) so rows tile edge-to-edge with no dead strips between
// them — the vertical twin of S13's hitPaddingH. Keys render identically.
private val NumberRowHeight = 43.dp
private val LetterKeyRowHeight = 49.dp
private val BottomKeyRowHeight = 51.dp
private val MinKeyTouchHeight = 48.dp
private val MinKeyTouchHeightLandscape = 38.dp
private val TopStripHeight = 38.dp
private val ToolbarExpandedHeight = 40.dp
private val ToolbarCollapsedHeight = 36.dp
private val KeyGapH = 7.dp
private val KeyGapHLandscape = 5.dp
private val KeyGapV = 0.dp
private val KeyVisualPaddingH = 0.dp
private val KeyVisualPaddingV = 5.5.dp
private val KeyCorner = 7.dp
private val KeyboardPadding = 6.dp

/** S68: a light 30-50ms tap must still show a visible press flash — below
 *  this floor the highlight cleared within 2-3 frames and light taps felt
 *  unregistered (testers pressed again, harder). */
private const val MIN_PRESS_FLASH_MS = 90L

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
    // S167: shift in Bangla mode types a raw CAPITAL English letter (the
    // service commits the forming Bangla word first). It was a deliberate
    // lowercase no-op before — capitals were simply impossible in BN mode.
    return char.uppercaseChar()
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
    // S94: provider — the 15Hz RMS updates repaint only the voice panel,
    // never the whole keyboard tree.
    voiceInputLevelProvider: () -> Float = { 0f },
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
    /** S122: phone-class number field — the numpad shows plus/star/hash. */
    numberPadPhone: Boolean = false,
    /** S122: the running dictation is an English session — the voice panel
     *  prompt must say so instead of "বাংলায় বলুন". */
    voiceEnglishSession: Boolean = false,
    /** S136 (F-015): a one-line dismissable notice above the strip (e.g. the
     *  dictionary could not be provisioned). Null = nothing shown. */
    noticeText: String? = null,
    onNoticeDismiss: () -> Unit = {},
    onKeyPress: (Char) -> Unit,
    /** S99: probabilistic touch targeting — letter presses report position. */
    onLetterTouch: ((Char, Char?, Char?, Float) -> Unit)? = null,
    /** S163: glide typing — service-owned shared state; null = no glide. */
    glide: GlideUiState? = null,
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
    /** S183: line moves (DPAD up/down) from the cursor pad. */
    onCursorMoveVertical: (Int) -> Unit = {},
    /** S183: hold on ← / → opens the cursor pad (KeyboardMode.CURSOR). */
    onCursorPadOpen: () -> Unit = {},
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
        // Font-only scaling — key hit boxes never change, so the layout
        // system is untouched at every size.
        "extra_small" -> 0.92f
        "small" -> 1.0f
        "large" -> 1.08f
        "extra_large" -> 1.16f
        else -> 1.08f
    }
    val fontScale = baseFontScale * if (isLandscape) 0.94f else 1.0f

    val spaceRolloverPolicy = remember { SpaceRolloverPolicy() }
    val currentOnSpace by rememberUpdatedState(onSpace)
    CompositionLocalProvider(
        LocalSpaceRollover provides spaceRolloverPolicy,
        // S168 (audit P2-4): the keyboard is a fixed LTR artefact — an RTL
        // system locale must not mirror the rows or the glide grid.
        LocalLayoutDirection provides LayoutDirection.Ltr,
        LocalKeyboardColors provides colors,
        LocalHapticEnabled provides hapticEnabled,
        LocalSoundEnabled provides soundEnabled,
        LocalKeyPreviewEnabled provides keyPreviewEnabled,
        LocalKeyboardHeightScale provides heightScale,
        LocalKeyboardFontScale provides fontScale
    ) {
        // S30: trust the real inset. The old maxOf(inset, 56dp) fallback padded
        // a dead 56dp strip under the bottom row on every device whose IME
        // window already sits above the nav bar (3-button phones report a 0
        // inset there) — testers saw it as "empty space below the keyboard".
        // Gesture-nav phones report the gesture-pill inset here and still get
        // exactly the padding they need.
        val navBottomPadding = WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()
        // S166 (iQOO Neo 9): gestures + "hide indicator bar" report a ZERO
        // nav inset — the bottom row sat in the system gesture zone and the
        // OEM's floating IME-switch globe overlaid the !#1 key. Policy:
        // max(nav, tappableElement) with a 14dp floor ONLY on gesture nav.
        val tappableBottomPadding = WindowInsets.tappableElement
            .asPaddingValues()
            .calculateBottomPadding()
        val navigationMode = run {
            val resolver = LocalContext.current.contentResolver
            remember(resolver) {
                runCatching {
                    android.provider.Settings.Secure.getInt(resolver, "navigation_mode", 0)
                }.getOrDefault(0)
            }
        }
        val bottomSafePadding = GestureNavInsetPolicy.bottomPaddingDp(
            navInsetDp = navBottomPadding.value,
            tappableInsetDp = tappableBottomPadding.value,
            navigationMode = navigationMode,
            landscape = isLandscape,
        ).dp
        val isVoiceActive = voiceInputState == VoiceInputState.LISTENING ||
            voiceInputState == VoiceInputState.PROCESSING
        val showNumberRow = numberRowEnabled && !isLandscape

        // S168 (audit P3-5): the emoji/clipboard panels take the SAME height
        // as the letter layout they replace, so the host app's content never
        // jumps when a panel opens. Measured from the live letters layout.
        var lettersContentHeightPx by remember { mutableIntStateOf(0) }
        val lettersMode = keyboardMode == KeyboardMode.BANGLU || keyboardMode == KeyboardMode.ENGLISH
        val panelHeight: Dp? = if (lettersContentHeightPx > 0) {
            with(LocalDensity.current) { lettersContentHeightPx.toDp() }
        } else null
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.keyboardBg)
                .padding(horizontal = if (isLandscape) 4.dp else KeyboardPadding)
                .padding(top = if (isLandscape) 1.dp else 3.dp, bottom = bottomSafePadding)
                .onSizeChanged { if (lettersMode) lettersContentHeightPx = it.height }
                // S194: a held spacebar commits the instant ANY other finger
                // lands (Samsung/Gboard press order). The Initial pass runs
                // root-first, so the space goes out before the letter's own
                // down handler fires.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press &&
                                event.changes.count { it.pressed } >= 2 &&
                                spaceRolloverPolicy.onOtherPointerDown()
                            ) {
                                currentOnSpace()
                            }
                        }
                    }
                }
        ) {
            if (noticeText != null) {
                KeyboardNoticeRow(text = noticeText, onDismiss = onNoticeDismiss)
                Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))
            }
            if (voiceInputState != VoiceInputState.IDLE) {
                VoiceStatusPanel(
                    state = voiceInputState,
                    levelProvider = voiceInputLevelProvider,
                    englishSession = voiceEnglishSession,
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
                            suggestionsProvider = suggestionsProvider,
                            suggestionsEnabled = suggestionsEnabled,
                            onSuggestionClick = onSuggestionClick,
                            onSettingsClick = onSettingsClick,
                            onEmojiOpen = onEmojiOpen,
                            onStickerOpen = onStickerOpen,
                            onClipboardOpen = onClipboardOpen,
                            onVoiceInput = onVoiceInput,
                            onPunctuationPress = onPunctuationPress,
                            onCursorMove = onCursorMove,
                            onCursorPadOpen = onCursorPadOpen,
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
                        onShiftTap = onShiftTap,
                        onLetterTouch = onLetterTouch,
                        glide = glide
                    )
                    Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))
                    BottomRow(
                        leftLabel = "!#1",
                        spaceLabel = "\u09AC\u09BE\u0982\u09B2\u09C1 (BN)",
                        globeLabel = "EN",
                        enterLabel = enterLabel,
                        periodLabel = "\u0964",
                        onLeftPress = onSymbolsPress,
                        onGlobePress = onGlobePress,
                        onSpace = onSpace,
                        onPunctuationPress = onPunctuationPress,
                        onCursorMove = onCursorMove,
                        onEnter = onEnter
                    )
                }
                KeyboardMode.ENGLISH -> {
                    // S96: EN mode gets the SAME adaptive strip as Bangla —
                    // the old hardcoded action bar could never show the
                    // English completion/prediction chips.
                    AdaptiveTopStrip(
                        suggestionsProvider = suggestionsProvider,
                        suggestionsEnabled = suggestionsEnabled,
                        onSuggestionClick = onSuggestionClick,
                        onSettingsClick = onSettingsClick,
                        onEmojiOpen = onEmojiOpen,
                        onStickerOpen = onStickerOpen,
                        onClipboardOpen = onClipboardOpen,
                        onVoiceInput = onVoiceInput,
                        onPunctuationPress = onPunctuationPress,
                        onCursorMove = onCursorMove,
                        onCursorPadOpen = onCursorPadOpen,
                        voiceInputState = voiceInputState,
                        onToggleToolbar = onToggleToolbar,
                        isToolbarExpanded = isToolbarExpanded
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
                        onShiftTap = onShiftTap,
                        onLetterTouch = onLetterTouch,
                        glide = glide
                    )
                    Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))
                    BottomRow(
                        leftLabel = "!#1",
                        spaceLabel = "English (EN)",
                        globeLabel = "BN",
                        enterLabel = enterLabel,
                        englishAccent = true,
                        onLeftPress = onSymbolsPress,
                        onGlobePress = onGlobePress,
                        onSpace = onSpace,
                        onPunctuationPress = onPunctuationPress,
                        onCursorMove = onCursorMove,
                        onEnter = onEnter
                    )
                }
                KeyboardMode.SYMBOLS_1 -> {
                    // S98: identity chips (sham@ -> @gmail.com) must show
                    // right where the '@' was typed — the symbols layer gets
                    // the same adaptive strip (falls back to the action bar
                    // whenever no suggestions exist).
                    AdaptiveTopStrip(
                        suggestionsProvider = suggestionsProvider,
                        suggestionsEnabled = suggestionsEnabled,
                        onSuggestionClick = onSuggestionClick,
                        onSettingsClick = onSettingsClick,
                        onEmojiOpen = onEmojiOpen,
                        onStickerOpen = onStickerOpen,
                        onClipboardOpen = onClipboardOpen,
                        onVoiceInput = onVoiceInput,
                        onPunctuationPress = onPunctuationPress,
                        onCursorMove = onCursorMove,
                        onCursorPadOpen = onCursorPadOpen,
                        voiceInputState = voiceInputState,
                        onToggleToolbar = onToggleToolbar,
                        isToolbarExpanded = isToolbarExpanded
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
                    // S98: same adaptive strip as SYMBOLS_1.
                    AdaptiveTopStrip(
                        suggestionsProvider = suggestionsProvider,
                        suggestionsEnabled = suggestionsEnabled,
                        onSuggestionClick = onSuggestionClick,
                        onSettingsClick = onSettingsClick,
                        onEmojiOpen = onEmojiOpen,
                        onStickerOpen = onStickerOpen,
                        onClipboardOpen = onClipboardOpen,
                        onVoiceInput = onVoiceInput,
                        onPunctuationPress = onPunctuationPress,
                        onCursorMove = onCursorMove,
                        onCursorPadOpen = onCursorPadOpen,
                        voiceInputState = voiceInputState,
                        onToggleToolbar = onToggleToolbar,
                        isToolbarExpanded = isToolbarExpanded
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

                KeyboardMode.CURSOR -> {
                    CursorPadPanel(
                        colors = colors,
                        panelHeight = panelHeight,
                        onLeft = { onCursorMove(-1) },
                        onRight = { onCursorMove(1) },
                        onUp = { onCursorMoveVertical(-1) },
                        onDown = { onCursorMoveVertical(1) },
                        onBackToLetters = onBackToLetters
                    )
                }
                KeyboardMode.EMOJI -> {
                    EmojiPanel(
                        colors = colors,
                        panelHeight = panelHeight,
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
                        panelHeight = panelHeight,
                        itemsProvider = clipboardItemsProvider,
                        onPaste = onClipboardPaste,
                        onClear = onClipboardClear,
                        onBackToKeyboard = onBackToLetters
                    )
                }
                KeyboardMode.NUMBER -> {
                    // S122: dedicated numeric keypad for number/phone/PIN
                    // fields — every stock keyboard shows one; Banglu used to
                    // present full QWERTY here.
                    NumberPadLayout(
                        phone = numberPadPhone,
                        enterLabel = enterLabel,
                        onKeyPress = onKeyPress,
                        onBackspace = onBackspace,
                        onBackspaceRepeat = onBackspaceRepeat,
                        onBackspaceWord = onBackspaceWord,
                        onEnter = onEnter,
                        onBackToLetters = onBackToLetters
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
    // S94: a PROVIDER, not a list — the snapshot-state read happens inside
    // THIS composable's restart scope, so a per-keystroke strip update
    // recomposes only this row instead of the whole keyboard (the read used
    // to sit in the root scope; the 48-tap burst measured p95 27ms / 51%
    // missed 120Hz deadlines against Samsung's 19ms / 6.3%).
    suggestionsProvider: () -> List<SmartSuggestion>,
    suggestionsEnabled: Boolean,
    onSuggestionClick: (SmartSuggestion) -> Unit,
    onSettingsClick: () -> Unit,
    onEmojiOpen: () -> Unit,
    onStickerOpen: () -> Unit,
    onClipboardOpen: () -> Unit,
    onVoiceInput: () -> Unit,
    onPunctuationPress: (Char) -> Unit,
    onCursorMove: (Int) -> Unit,
    onCursorPadOpen: () -> Unit,
    voiceInputState: VoiceInputState,
    onToggleToolbar: () -> Unit,
    isToolbarExpanded: Boolean,
) {
    val colors = LocalKeyboardColors.current
    // The one snapshot-state read that changes per keystroke — deliberately
    // inside this scope (see the parameter doc).
    val suggestions = if (suggestionsEnabled) suggestionsProvider() else emptyList()
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
                onCursorPadOpen = onCursorPadOpen,
                voiceInputState = voiceInputState,
                onToggleToolbar = onToggleToolbar,
                isExpanded = true
            )
        } else if (suggestions.isNotEmpty()) {
            // Idle-strip chips (punctuation bar, Bangla next-word predictions,
            // S96 English predictions) fill the strip whenever the buffer is
            // empty — which made the mic and toolbar unreachable for most of
            // an EN-mode session (S122 report). The rule: no chip carries a
            // word-in-progress (phonetic prefix) -> the user is BETWEEN words
            // -> pin mic + tools at the strip's end, Gboard-style. Mid-word
            // conversion/completion chips keep the full width.
            val idleStrip = suggestions.none { it.phonetic.isNotEmpty() }
            if (idleStrip) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        BangluSuggestionRow(suggestions, onSuggestionClick)
                        // S168 (audit P3-4): soft edge where chips run under the pinned mic.
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(18.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, LocalKeyboardColors.current.suggestionBg)
                                    )
                                )
                        )
                    }
                    MicEmojiSlot(
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
                onPhrasesOpen = onStickerOpen,
                onClipboardOpen = onClipboardOpen,
                onVoiceInput = onVoiceInput,
                onCursorMove = onCursorMove,
                onCursorPadOpen = onCursorPadOpen,
                voiceInputState = voiceInputState,
                onToggleToolbar = onToggleToolbar
            )
        }
    }
}

/** S136 (F-015): dismissable status line — polite live region for TalkBack. */
@Composable
private fun KeyboardNoticeRow(text: String, onDismiss: () -> Unit) {
    val colors = LocalKeyboardColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.suggestionChipBg)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = colors.keyText,
            fontSize = scaledSp(13),
            lineHeight = scaledSp(17),
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Dismiss notice"
                }
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "\u2715", color = colors.subText, fontSize = scaledSp(15))
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
    onCursorPadOpen: () -> Unit,
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
            ToolbarIconSlot("Phrases", modifier = Modifier.weight(1f), onClick = onStickerOpen) {
                IconPhrases(Modifier.size(22.dp), it)
            }
            // S183: the ← → slots exist only on the empty-strip action bar;
            // after a space the strip holds prediction chips, so the pad must
            // also be reachable from the tools row.
            ToolbarIconSlot("Cursor pad", modifier = Modifier.weight(1f), onClick = onCursorPadOpen) {
                IconCursorPad(Modifier.size(22.dp), it)
            }
            MicEmojiSlot(
                active = voiceInputState == VoiceInputState.LISTENING || voiceInputState == VoiceInputState.PROCESSING,
                modifier = Modifier.weight(1f),
                onClick = onVoiceInput
            )
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
    levelProvider: () -> Float,
    englishSession: Boolean = false,
    onRetry: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    // S94: the snapshot read lives here — RMS ticks stay panel-local.
    val level = levelProvider()
    val configuration = LocalConfiguration.current
    val compact = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val message = when (state) {
        VoiceInputState.LISTENING ->
            if (englishSession) "Speak in English" else "\u09ac\u09be\u0982\u09b2\u09be\u09df \u09ac\u09b2\u09c1\u09a8"
        VoiceInputState.PROCESSING -> "\u09b2\u09c7\u0996\u09be \u09b9\u099a\u09cd\u099b\u09c7\u2026"
        VoiceInputState.STOPPED -> "\u09ad\u09df\u09c7\u09b8 \u09a5\u09be\u09ae\u09be\u09a8\u09cb \u09b9\u09df\u09c7\u099b\u09c7"
        VoiceInputState.PERMISSION_REQUIRED -> "\u09ae\u09be\u0987\u0995\u09cd\u09b0\u09cb\u09ab\u09cb\u09a8 \u09aa\u09be\u09b0\u09ae\u09bf\u09b6\u09a8 \u09a6\u09bf\u09a8"
        VoiceInputState.UNAVAILABLE -> "\u09ad\u09df\u09c7\u09b8 \u09b8\u09be\u09b0\u09cd\u09ad\u09bf\u09b8 \u09aa\u09be\u0993\u09df\u09be \u09af\u09be\u09df\u09a8\u09bf"
        VoiceInputState.ERROR -> "\u0986\u09ac\u09be\u09b0 \u099a\u09c7\u09b7\u09cd\u099f\u09be \u0995\u09b0\u09c1\u09a8"
        // S55 (F-ANDROID-006): watchdog fired, no recognizer callback arrived
        VoiceInputState.WATCHDOG_TIMEOUT -> "\u09ad\u09df\u09c7\u09b8 \u099a\u09be\u09b2\u09c1 \u09b9\u09b2\u09cb \u09a8\u09be"
        // S55 (F-ANDROID-006): offline Bangla speech pack missing, no network
        VoiceInputState.OFFLINE_PACK_MISSING -> "\u0985\u09ab\u09b2\u09be\u0987\u09a8\u09c7 \u09ac\u09be\u0982\u09b2\u09be \u09ad\u09df\u09c7\u09b8 \u09aa\u09cd\u09af\u09be\u0995 \u09a8\u09c7\u0987"
        // S55 (review follow-up): busy-retry cap exceeded
        VoiceInputState.BUSY_GIVEUP -> "\u09ad\u09df\u09c7\u09b8 \u098f\u0996\u09a8 \u09ac\u09cd\u09af\u09b8\u09cd\u09a4"
        // S133: whole session heard nothing \u2014 the mic never delivered.
        VoiceInputState.MIC_SILENT -> "\u0995\u09bf\u099b\u09c1 \u09b6\u09cb\u09a8\u09be \u09af\u09be\u09af\u09bc\u09a8\u09bf"
        VoiceInputState.IDLE -> ""
    }
    val detail = when (state) {
        VoiceInputState.STOPPED -> "\u0986\u09ac\u09be\u09b0 \u09ac\u09b2\u09a4\u09c7 \u09b0\u09bf\u099f\u09cd\u09b0\u09be\u0987 \u099a\u09be\u09aa\u09c1\u09a8"
        // S106: the permission screen launch can be silently BLOCKED on
        // MIUI/ColorOS — the panel itself must carry the path.
        VoiceInputState.PERMISSION_REQUIRED ->
            "আবার চাপুন — না খুললে: সেটিংস \u2192 অ্যাপস \u2192 Banglu \u2192 Permissions \u2192 Microphone"
        VoiceInputState.UNAVAILABLE -> "ডিভাইসে Google Speech Services নেই বা বন্ধ আছে"
        VoiceInputState.ERROR -> "\u09ae\u09be\u0987\u0995 \u099a\u09c7\u0995 \u0995\u09b0\u09c7 \u0986\u09ac\u09be\u09b0 \u099a\u09c7\u09b7\u09cd\u099f\u09be \u0995\u09b0\u09c1\u09a8"
        // S106: watchdog exhausted — point at the device-level check.
        VoiceInputState.WATCHDOG_TIMEOUT -> "সাড়া নেই — Gboard-এ ভয়েস কাজ করে কি না দেখুন"
        VoiceInputState.OFFLINE_PACK_MISSING -> "\u0987\u09a8\u09cd\u099f\u09be\u09b0\u09a8\u09c7\u099f \u099a\u09be\u09b2\u09c1 \u0995\u09b0\u09c1\u09a8 \u09ac\u09be Google \u0985\u09cd\u09af\u09be\u09aa \u09a5\u09c7\u0995\u09c7 \u09ac\u09be\u0982\u09b2\u09be \u09aa\u09cd\u09af\u09be\u0995 \u09a8\u09be\u09ae\u09be\u09a8"
        VoiceInputState.BUSY_GIVEUP -> "\u098f\u0995\u099f\u09c1 \u09aa\u09b0\u09c7 \u099a\u09c7\u09b7\u09cd\u099f\u09be \u0995\u09b0\u09c1\u09a8"
        // S133: point at the two real causes \u2014 another app holding the mic,
        // or the phone's own voice input being broken.
        VoiceInputState.MIC_SILENT -> "\u09ae\u09be\u0987\u0995\u09cd\u09b0\u09cb\u09ab\u09cb\u09a8 \u0995\u09bf \u0985\u09a8\u09cd\u09af \u0985\u09cd\u09af\u09be\u09aa \u09ac\u09cd\u09af\u09ac\u09b9\u09be\u09b0 \u0995\u09b0\u099b\u09c7? \u09ac\u09a8\u09cd\u09a7 \u0995\u09b0\u09c7 \u0986\u09ac\u09be\u09b0 \u099a\u09c7\u09b7\u09cd\u099f\u09be \u0995\u09b0\u09c1\u09a8"
        else -> ""
    }
    val isActive = state == VoiceInputState.LISTENING || state == VoiceInputState.PROCESSING
    val isTrouble = state == VoiceInputState.ERROR ||
        state == VoiceInputState.PERMISSION_REQUIRED ||
        state == VoiceInputState.UNAVAILABLE ||
        state == VoiceInputState.BUSY_GIVEUP ||
        state == VoiceInputState.WATCHDOG_TIMEOUT ||
        state == VoiceInputState.OFFLINE_PACK_MISSING ||
        state == VoiceInputState.MIC_SILENT
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
                    // S106: trouble instructions need the room — one wrapped
                    // line was why nobody could read the fix path.
                    maxLines = if (isTrouble) 2 else 1,
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
            if (isTrouble) {
                // S106: sticky panels need an explicit dismiss.
                Spacer(modifier = Modifier.width(if (compact) 6.dp else 8.dp))
                VoiceRoundButton("\u09ac\u09be\u09a4\u09bf\u09b2", buttonSize, filled = false, onClick = onCancel) {
                    IconClose(Modifier.size(buttonSize * 0.52f), it)
                }
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
    activeStateDescription: String = "Active",
    cornerRadius: Dp = 10.dp,
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
                if (active) stateDescription = activeStateDescription
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(cornerRadius))
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
    onPhrasesOpen: () -> Unit,
    onClipboardOpen: () -> Unit,
    onVoiceInput: () -> Unit,
    onCursorMove: (Int) -> Unit,
    onCursorPadOpen: () -> Unit,
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
        // S160 (user-approved mock \u0995): the redundant \u09a6\u09be\u0981\u09dc\u09bf slot and the
        // sticker shortcut (still in the expanded toolbar) make way for
        // cursor arrows \u2014 tap steps one position, hold repeats.
        // S186 (user: "place it after emoji … remove the other two left and
        // right icons because we provide the same functionality on that
        // panel, keep settings"): ONE cursor-pad slot opens the S183 4-way
        // pad; the S160 ← → slots are retired (the pad has them, larger).
        CompactIconSlot("Cursor pad", modifier = Modifier.weight(1f), onClick = onCursorPadOpen) {
            IconCursorPad(Modifier.size(22.dp), it)
        }
        // S186 (user, on the বাক্য tab: "bring this section to the front tools
        // bar, we have one space left"): the phrases tab of the emoji panel
        // (সালাম ও শুভেচ্ছা …) opens in one tap.
        CompactIconSlot("Phrases", modifier = Modifier.weight(1f), onClick = onPhrasesOpen) {
            IconPhrases(Modifier.size(22.dp), it)
        }
        CompactIconSlot("Clipboard", modifier = Modifier.weight(1f), onClick = onClipboardOpen) {
            IconClipboard(Modifier.size(21.dp), it)
        }
        MicEmojiSlot(
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

/** S160 (mock ক): the tools bar wears the mock's studio-mic emoji — plain at
 *  rest, voice-accent ring only while listening. */
@Composable
private fun MicEmojiSlot(
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // S168 (audit P3-1): built on CompactIconSlot so the mic exposes the SAME
    // single Button node (label + click together) as every other slot.
    CompactIconSlot(
        accessibilityLabel = "Bangla voice typing",
        active = active,
        modifier = modifier,
        activeStateDescription = "Listening",
        cornerRadius = 18.dp,
        onClick = onClick
    ) {
        // Drawn, not a Text node: a Text child (even with cleared semantics)
        // made Compose hang the label on a separate a11y node from the click.
        val measurer = rememberTextMeasurer()
        val style = TextStyle(fontSize = scaledSp(19))
        Canvas(modifier = Modifier.size(36.dp)) {
            val layout = measurer.measure("\uD83C\uDFA4", style)
            drawText(
                layout,
                topLeft = Offset(
                    (size.width - layout.size.width) / 2f,
                    (size.height - layout.size.height) / 2f
                )
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
            // S168 (audit P3-1): ONE merged node — with the emoji Text as a
            // separate semantics node the label and the click action landed on
            // different a11y nodes (TalkBack saw an unlabeled button).
            .semantics(mergeDescendants = true) {
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

/**
 * S183: the cursor-control pad — replaces the letter rows (never covers the
 * editor) while the caret is moved. Header like the clipboard panel (ABC
 * back + title), then a 3×3 pad: ▲ ▼ ◀ ▶ tap one step, hold repeats;
 * the centre "শেষ" also returns to the letters. Sized like the other
 * panels (S168) so the keyboard never jumps.
 */
@Composable
private fun CursorPadPanel(
    colors: KeyboardColors,
    panelHeight: Dp?,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onBackToLetters: () -> Unit
) {
    val total = panelHeight ?: 260.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = total)
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
                onClick = onBackToLetters
            )
            Text(
                text = "কার্সর সরান",
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                color = colors.keyText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = "ধরে রাখলে দ্রুত",
                color = colors.subText,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
        val gap = 8.dp
        val keyH = ((total - 42.dp - 16.dp - gap * 2) / 3).coerceIn(48.dp, 64.dp)
        val keyW = 96.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(total - 42.dp - 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
                CursorPadKey(0f, "Move cursor up", colors, keyW, keyH, onUp)
                Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                    CursorPadKey(270f, "Move cursor left", colors, keyW, keyH, onLeft)
                    Box(
                        modifier = Modifier
                            .width(keyW)
                            .height(keyH)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.specialKeyBg)
                            .clickable(onClick = onBackToLetters)
                            .semantics { role = Role.Button; contentDescription = "Done, back to keyboard" },
                        contentAlignment = Alignment.Center
                    ) { Text("শেষ", color = colors.keyText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                    CursorPadKey(90f, "Move cursor right", colors, keyW, keyH, onRight)
                }
                CursorPadKey(180f, "Move cursor down", colors, keyW, keyH, onDown)
            }
        }
    }
}

@Composable
private fun CursorPadKey(degrees: Float, label: String, colors: KeyboardColors, keyW: Dp, keyH: Dp, onStep: () -> Unit) {
    val currentOnStep = rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .width(keyW)
            .height(keyH)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.keyBg)
            .semantics { role = Role.Button; contentDescription = label; onClick { currentOnStep.value(); true } }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    currentOnStep.value()
                    val holdJob = scope.launch {
                        kotlinx.coroutines.delay(350)
                        while (true) { currentOnStep.value(); kotlinx.coroutines.delay(60) }
                    }
                    tryAwaitRelease()
                    holdJob.cancel()
                })
            },
        contentAlignment = Alignment.Center
    ) { IconArrowHead(Modifier.size(28.dp), colors.keyText, degrees) }
}

@Composable
private fun ClipboardPanel(
    colors: KeyboardColors,
    panelHeight: Dp?,
    itemsProvider: () -> List<String>,
    onPaste: (String) -> Unit,
    onClear: () -> Unit,
    onBackToKeyboard: () -> Unit
) {
    val items = itemsProvider()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = panelHeight ?: 260.dp)
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

    // S133 (field report: "they need to press the words many times but still
    // words not selected"): the strip used to mutate UNDER the finger — an
    // async refine landing mid-press replaced the chips and snapped the list
    // to item 0, which cancels the in-progress tap gesture and can dispose
    // the very chip being pressed. Gboard's rule, adopted: the strip does
    // not update while a touch is active. [frozen] pins the rendered list
    // from finger-down to finger-up; the pointer observation is pass-through
    // (Initial pass, nothing consumed), so chip clicks behave exactly as
    // before — they just fire on chips that hold still.
    val latestSuggestions = rememberUpdatedState(suggestions)
    var frozen by remember { mutableStateOf<List<SmartSuggestion>?>(null) }
    val shown = frozen ?: suggestions
    // S160 (field report: "swipe left springs back"): unfreezing after a
    // SWIPE used to refire this effect and snap to item 0, undoing the
    // user's scroll. Snap only when a genuinely new ranking arrives (and no
    // finger is down) — a mere finger-up over unchanged chips keeps the
    // scroll position the user chose.
    var lastSnapped by remember { mutableStateOf<List<SmartSuggestion>?>(null) }
    LaunchedEffect(shown, frozen == null) {
        if (frozen == null && shown != lastSnapped) {
            stripState.scrollToItem(0)
            lastSnapped = shown
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(scaledDp(TopStripHeight))
            .background(colors.suggestionBg)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    frozen = latestSuggestions.value
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            if (event.changes.none { it.pressed }) break
                        }
                    } finally {
                        frozen = null
                    }
                }
            },
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
            // S162 (tester proposal, mock variant ঙ): the typed roman is its
            // own leading ghost chip, every chip is single-line, and the blue
            // commit highlight belongs to the first REAL suggestion — never a
            // ghost. The S117/S152 two-line hint retires with this layout.
            // S168 (audit P0-1): a duplicate LazyRow key is an IME-process
            // crash (seen on device 2026-08-31) — dedupe at the strip itself.
            val uniqueShown = StripKeyPolicy.uniqueByKey(shown)
            val firstReal = uniqueShown.firstOrNull { !TypedChipPolicy.isGhostTier(it.tier) }
            // S169 (perf trace 2026-09-02): SLOT identity, not content identity.
            // Content keys made every keystroke tear down and rebuild 5-8 chip
            // subtrees (Pending.keyMap + UiApplier.dispatchChanges were 17% and
            // 12% of main-thread samples; measure spikes 7-10 ms). With the
            // default index key the six chip nodes persist and only their text
            // changes. Duplicate keys are impossible by construction, so the
            // dedupe above is now purely cosmetic insurance.
            items(uniqueShown.size) { index ->
                val suggestion = uniqueShown[index]
                val isGhost = TypedChipPolicy.isGhostTier(suggestion.tier)
                val isFirst = suggestion == firstReal
                // Feature 4.4: Prediction chips use different styling
                val isPrediction = suggestion.tier == "prediction"
                val isPunctuation = suggestion.tier == "punctuation"
                // S186 (user, screenshot of the EN idle strip): the blue
                // highlight means "space commits this"; a next-word prediction
                // is tap-only, so no prediction chip carries it (BN and EN).
                val chipBg = if (isGhost) Color.Transparent
                    else if (isFirst && !isPunctuation && !isPrediction) colors.suggestionHighlight
                    else if (isPrediction) colors.keyBg
                    else colors.suggestionChipBg
                val chipTextColor = if (isGhost) colors.keyText.copy(alpha = 0.92f)
                    else if (isFirst && !isPunctuation && !isPrediction) Color.White
                    else colors.keyText

                Box(
                    modifier = Modifier
                        .semantics {
                            role = Role.Button
                            contentDescription =
                                if (suggestion.tier == TypedChipPolicy.TYPED_ROMAN_TIER)
                                    "Keep typed text ${suggestion.bengali}"
                                else "Suggestion ${suggestion.bengali}"
                            if (isFirst) stateDescription = "Primary suggestion"
                        }
                        .shadow(if (isFirst) 1.dp else 0.dp, RoundedCornerShape(16.dp), clip = false)
                        .clip(RoundedCornerShape(16.dp))
                        .background(chipBg)
                        .then(
                            if (isGhost)
                                Modifier.border(
                                    1.5.dp,
                                    colors.keyText.copy(alpha = 0.35f),
                                    RoundedCornerShape(16.dp)
                                )
                            else Modifier
                        )
                        .clickable { onSuggestionClick(suggestion) }
                        .padding(horizontal = 13.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = suggestion.bengali,
                        color = chipTextColor,
                        fontSize = if (isPunctuation) scaledSp(17)
                            else if (isGhost) scaledSp(13)
                            else if (isFirst) scaledSp(16.5f)
                            else scaledSp(15),
                        fontFamily = if (suggestion.tier == TypedChipPolicy.TYPED_ROMAN_TIER) RomanMono else null,
                        fontWeight = if (isFirst) FontWeight.Medium else FontWeight.Normal,
                        // S186: predictions are upright — the muted key background already marks them; italics read oddly on English words.
                        fontStyle = FontStyle.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
    // S13: no spacedBy dead strips — the visual gap lives inside each cell.
    val hitPad = currentKeyGapH() / 2
    Row(modifier = Modifier.fillMaxWidth()) {
        for (num in '1'..'9') {
            NumberKey(
                number = num,
                displayNumber = if (useBanglaDigits) banglaDigitLabel(num) else num,
                modifier = Modifier.weight(1f),
                height = height,
                hitPaddingH = hitPad,
                onNumberPress = onNumberPress,
                onSymbolPress = onSymbolPress
            )
        }
        NumberKey(
            number = '0',
            displayNumber = if (useBanglaDigits) banglaDigitLabel('0') else '0',
            modifier = Modifier.weight(1f),
            height = height,
            hitPaddingH = hitPad,
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
    onShiftTap: () -> Unit,
    /** S99: (tapped, leftNeighbor, rightNeighbor, xFraction) — when set,
     *  letter keys report press position for probabilistic targeting. */
    onLetterTouch: ((Char, Char?, Char?, Float) -> Unit)? = null,
    /** S163: glide typing state; null disables the observer entirely. */
    glide: GlideUiState? = null
) {
    if (glide == null) {
        LetterRowsContent(
            shiftState, useShiftedLetterInput, onKeyPress, onTextInput,
            onBackspace, onBackspaceRepeat, onBackspaceWord, onShiftTap, onLetterTouch
        )
        return
    }
    GlideLetterRows(glide) {
        LetterRowsContent(
            shiftState, useShiftedLetterInput, onKeyPress, onTextInput,
            onBackspace, onBackspaceRepeat, onBackspaceWord, onShiftTap, onLetterTouch
        )
    }
}

/**
 * S163: wraps the three letter rows with (a) an Initial-pass pointer
 * OBSERVER feeding GlideInput — keys keep owning their gestures exactly as
 * before (S13 commit-on-down, S15 no stale waits, S32/S68 untouched) — and
 * (b) the trail overlay. LocalGlideActive lets KeyButton suppress its
 * long-press popup while a glide is running.
 */
@Composable
private fun GlideLetterRows(glide: GlideUiState, content: @Composable () -> Unit) {
    val colors = LocalKeyboardColors.current
    val glideInput = remember { GlideInput() }

    // Fail flash: keep the red trail briefly, then clear.
    LaunchedEffect(glide.failFlash.value) {
        if (glide.failFlash.value) {
            delay(150)
            glide.trail.clear()
            glide.failFlash.value = false
        }
    }

    CompositionLocalProvider(LocalGlideActive provides { glide.active.value }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(glide) {
                    awaitPointerEventScope {
                        var tracking = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val keyW = size.width / 10f
                            val rowH = size.height / 3f
                            if (keyW <= 0f || rowH <= 0f) continue
                            when {
                                event.changes.size > 1 -> {
                                    // Multi-touch typists: second finger kills the glide.
                                    if (tracking) {
                                        glideInput.cancel()
                                        glide.active.value = false
                                        glide.trail.clear()
                                        tracking = false
                                    }
                                }
                                event.type == PointerEventType.Press -> {
                                    val pos = event.changes.first().position
                                    val gx = pos.x / keyW
                                    val gy = pos.y / rowH
                                    // Row 3's shift/backspace zones are not letters.
                                    val onLetter = gy < 2f || (gx in 1.5f..8.5f)
                                    tracking = glide.enabledProvider()
                                    glideInput.begin(tracking && onLetter, GlidePoint(gx, gy))
                                }
                                event.type == PointerEventType.Move && tracking -> {
                                    val pos = event.changes.first().position
                                    val wasGlide = glideInput.isGlide
                                    glideInput.move(GlidePoint(pos.x / keyW, pos.y / rowH))
                                    if (glideInput.isGlide) {
                                        if (!wasGlide) {
                                            glide.active.value = true
                                            glide.trail.clear()
                                        }
                                        glide.trail.add(GlidePoint(pos.x / keyW, pos.y / rowH))
                                        if (glide.trail.size > 64) glide.trail.removeAt(0)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                                event.type == PointerEventType.Release && tracking -> {
                                    val wasGlide = glideInput.isGlide
                                    val path = glideInput.finish()
                                    glide.active.value = false
                                    tracking = false
                                    if (wasGlide && path.size >= 4) {
                                        event.changes.forEach { it.consume() }
                                        glide.onComplete(path)
                                    } else {
                                        glide.trail.clear()
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            Column { content() }
            val trailColor = colors.suggestionHighlight
            Canvas(modifier = Modifier.matchParentSize()) {
                val trail = glide.trail
                if (trail.size < 2) return@Canvas
                val keyW = size.width / 10f
                val rowH = size.height / 3f
                val color = if (glide.failFlash.value) Color(0xFFE0524D) else trailColor
                for (i in 1 until trail.size) {
                    // Newer segments brighter — a fading comet, no allocation.
                    val alpha = 0.15f + 0.75f * (i.toFloat() / trail.size)
                    drawLine(
                        color = color.copy(alpha = alpha),
                        start = Offset(trail[i - 1].x * keyW, trail[i - 1].y * rowH),
                        end = Offset(trail[i].x * keyW, trail[i].y * rowH),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

/** S163: while a glide is running, key popups must not fire. */
private val LocalGlideActive = compositionLocalOf<() -> Boolean> { { false } }

/** S194: the keyboard-wide spacebar rollover state (see SpaceRolloverPolicy). */
private val LocalSpaceRollover = compositionLocalOf { SpaceRolloverPolicy() }

@Composable
private fun LetterRowsContent(
    shiftState: ShiftState,
    useShiftedLetterInput: Boolean,
    onKeyPress: (Char) -> Unit,
    onTextInput: (String) -> Unit,
    onBackspace: () -> Unit,
    onBackspaceRepeat: (Int) -> Unit,
    onBackspaceWord: () -> Unit,
    onShiftTap: () -> Unit,
    onLetterTouch: ((Char, Char?, Char?, Float) -> Unit)?
) {
    val colors = LocalKeyboardColors.current
    val keyHeight = scaledKeyHeight(LetterKeyRowHeight)

    // Row 1: q w e r t y u i o p — S11: no spacedBy dead strips; the visual
    // gap lives inside each cell so every pixel of the row hits a key.
    val letterHitPad = currentKeyGapH() / 2
    Row(modifier = Modifier.fillMaxWidth()) {
        for ((index, key) in LETTER_ROW_1.withIndex()) {
            val display = letterKeyLabel(key, shiftState, useShiftedLetterInput)
            val input = letterKeyInput(key, shiftState, useShiftedLetterInput)
            val left = LETTER_ROW_1.getOrNull(index - 1)
                ?.let { letterKeyInput(it, shiftState, useShiftedLetterInput) }
            val right = LETTER_ROW_1.getOrNull(index + 1)
                ?.let { letterKeyInput(it, shiftState, useShiftedLetterInput) }
            KeyButton(
                label = display,
                modifier = Modifier.weight(1f),
                height = keyHeight,
                bgColor = colors.keyBg,
                longPressOptions = longPressAlternatives(key[0], useShiftedLetterInput),
                onTextInput = onTextInput,
                hitPaddingH = letterHitPad,
                onReplaceLast = { alt -> onBackspace(); onTextInput(alt) },
                onClickAt = onLetterTouch?.let { cb -> { frac -> cb(input, left, right, frac) } },
                onClick = { onKeyPress(input) }
            )
        }
    }

    Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))

    // Row 2: a s d f g h j k l (indented). S68: the indent used to be Row
    // .padding — OUTSIDE every touch cell, leaving a dead strip ~2/3 of a
    // key wide hugging 'a' and 'l' (testers: "a needs a hard press" — their
    // slightly-off-center taps landed on nothing). The indent is now folded
    // INTO the edge keys' touch cells (extra weight + asymmetric hit
    // padding): pixel-identical rendering, but the whole row is touchable
    // edge-to-edge like rows 1 and 3.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val indent = middleLetterRowIndent()
        val baseKeyWidth = (maxWidth - indent * 2) / LETTER_ROW_2.size
        val edgeWeight = (baseKeyWidth + indent) / baseKeyWidth
        Row(modifier = Modifier.fillMaxWidth()) {
            for ((index, key) in LETTER_ROW_2.withIndex()) {
                val isFirst = index == 0
                val isLast = index == LETTER_ROW_2.size - 1
                val display = letterKeyLabel(key, shiftState, useShiftedLetterInput)
                val input = letterKeyInput(key, shiftState, useShiftedLetterInput)
                val left = LETTER_ROW_2.getOrNull(index - 1)
                    ?.let { letterKeyInput(it, shiftState, useShiftedLetterInput) }
                val right = LETTER_ROW_2.getOrNull(index + 1)
                    ?.let { letterKeyInput(it, shiftState, useShiftedLetterInput) }
                KeyButton(
                    label = display,
                    modifier = Modifier.weight(if (isFirst || isLast) edgeWeight else 1f),
                    height = keyHeight,
                    bgColor = colors.keyBg,
                    longPressOptions = longPressAlternatives(key[0], useShiftedLetterInput),
                    onTextInput = onTextInput,
                    hitPaddingH = letterHitPad,
                    hitPaddingStart = if (isFirst) letterHitPad + indent else null,
                    hitPaddingEnd = if (isLast) letterHitPad + indent else null,
                    onReplaceLast = { alt -> onBackspace(); onTextInput(alt) },
                    onClickAt = onLetterTouch?.let { cb -> { frac -> cb(input, left, right, frac) } },
                    onClick = { onKeyPress(input) }
                )
            }
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

        for ((index, key) in LETTER_ROW_3.withIndex()) {
            val display = letterKeyLabel(key, shiftState, useShiftedLetterInput)
            val input = letterKeyInput(key, shiftState, useShiftedLetterInput)
            val left = LETTER_ROW_3.getOrNull(index - 1)
                ?.let { letterKeyInput(it, shiftState, useShiftedLetterInput) }
            val right = LETTER_ROW_3.getOrNull(index + 1)
                ?.let { letterKeyInput(it, shiftState, useShiftedLetterInput) }
            KeyButton(
                label = display,
                modifier = Modifier.weight(1f),
                height = keyHeight,
                bgColor = colors.keyBg,
                longPressOptions = longPressAlternatives(key[0], useShiftedLetterInput),
                onTextInput = onTextInput,
                hitPaddingH = letterHitPad,
                onReplaceLast = { alt -> onBackspace(); onTextInput(alt) },
                onClickAt = onLetterTouch?.let { cb -> { frac -> cb(input, left, right, frac) } },
                onClick = { onKeyPress(input) }
            )
        }

        // Backspace with long-press repeat and word deletion.
        // S13: gap goes INSIDE the touch cell (hitPaddingH) — the old outer
        // .padding() shrank the touchable area, leaving a dead margin around
        // the second-most-pressed key.
        BackspaceKey(
            modifier = Modifier.weight(1.5f),
            height = keyHeight,
            hitPaddingH = letterHitPad,
            onBackspace = onBackspace,
            onBackspaceRepeat = onBackspaceRepeat,
            onBackspaceWord = onBackspaceWord
        )
    }
}

private val EMPTY_KEY_ALTERNATIVES = emptyList<KeyAlternative>()
private val LONG_PRESS_ALTERNATIVES = mapOf(
    // S184 (user: "press and hold c … they should get chandra bindu above that
    // letter"): "^" is the engine's chandrabindu marker — it joins the roman
    // buffer, so cha + hold-c + d previews and commits চাঁদ, and inside a
    // committed word the S174 mid-word plan carries it (BackspaceResume).
    'c' to listOf(KeyAlternative("ঁ", "^")),
    't' to listOf(KeyAlternative("ট", "ট")),
    'd' to listOf(KeyAlternative("ড", "ড")),
    'r' to listOf(KeyAlternative("ড়", "ড়")),
    's' to listOf(KeyAlternative("শ", "sh")),
    'i' to listOf(KeyAlternative("ঈ", "ii")),
    'u' to listOf(KeyAlternative("ঊ", "uu"))
)

private fun longPressAlternatives(char: Char, englishLayout: Boolean = false): List<KeyAlternative> {
    val alts = LONG_PRESS_ALTERNATIVES[char.lowercaseChar()] ?: EMPTY_KEY_ALTERNATIVES
    // S184: chandrabindu is a Bangla-only sign — the EN layout keeps c plain.
    return if (englishLayout) alts.filter { it.input != "^" } else alts
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
    // S13: no spacedBy dead strips — the visual gap lives inside each cell.
    val hitPad = currentKeyGapH() / 2

    // Symbol row 1 (10 keys)
    Row(modifier = Modifier.fillMaxWidth()) {
        for (sym in rows[0]) {
            KeyButton(
                label = sym,
                modifier = Modifier.weight(1f),
                height = keyHeight,
                bgColor = colors.keyBg,
                fontSize = 18,
                hitPaddingH = hitPad,
                onClick = { onSymbolPress(sym[0]) }
            )
        }
    }

    Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))

    // Symbol row 2 (10 keys)
    Row(modifier = Modifier.fillMaxWidth()) {
        for (sym in rows[1]) {
            KeyButton(
                label = sym,
                modifier = Modifier.weight(1f),
                height = keyHeight,
                bgColor = colors.keyBg,
                fontSize = 18,
                hitPaddingH = hitPad,
                onClick = { onSymbolPress(sym[0]) }
            )
        }
    }

    Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))

    // Symbol row 3: [page toggle] symbols... [backspace]
    Row(modifier = Modifier.fillMaxWidth()) {
        KeyButton(
            label = pageLabel,
            modifier = Modifier.weight(1.5f),
            height = keyHeight,
            bgColor = colors.specialKeyBg,
            fontSize = 16,
            hitPaddingH = hitPad,
            onClick = onPageToggle
        )
        for (sym in rows[2]) {
            KeyButton(
                label = sym,
                modifier = Modifier.weight(1f),
                height = keyHeight,
                bgColor = colors.keyBg,
                fontSize = 18,
                hitPaddingH = hitPad,
                onClick = { onSymbolPress(sym[0]) }
            )
        }
        BackspaceKey(
            modifier = Modifier.weight(1.5f),
            height = keyHeight,
            hitPaddingH = hitPad,
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
    periodLabel: String = ".",
    /** S67: English mode must be visually unmistakable \u2014 testers mis-tapped
     *  the toggle (it sits beside the comma) and then typed "Bengali" into a
     *  raw-English keyboard that looks identical to the Bangla one. */
    englishAccent: Boolean = false,
    onLeftPress: () -> Unit,
    onGlobePress: () -> Unit,
    onSpace: () -> Unit,
    onPunctuationPress: (Char) -> Unit,
    onCursorMove: (Int) -> Unit = {},
    onEnter: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    val keyHeight = scaledKeyHeight(BottomKeyRowHeight)
    // S13: no spacedBy dead strips in the highest-traffic row — the 7dp gaps
    // around the spacebar were dead gutters exactly where thumbs land.
    val hitPad = currentKeyGapH() / 2
    Row(modifier = Modifier.fillMaxWidth()) {
        // !#1 or ABC
        KeyButton(
            label = leftLabel,
            modifier = Modifier.weight(1.2f),
            height = keyHeight,
            bgColor = colors.specialKeyBg,
            fontSize = 16,
            hitPaddingH = hitPad,
            onClick = onLeftPress
        )

        // Language toggle key (internal switch, NOT system IME switch)
        KeyButton(
            label = globeLabel,
            modifier = Modifier.weight(0.8f),
            height = keyHeight,
            bgColor = if (englishAccent) colors.suggestionHighlight.copy(alpha = 0.35f) else colors.specialKeyBg,
            fontSize = 16,
            hitPaddingH = hitPad,
            onClick = onGlobePress
        )

        // Comma — mirrors the period on the right (top tester request: the
        // highest-frequency punctuation after danda deserves a dedicated key)
        KeyButton(
            label = ",",
            modifier = Modifier.weight(0.8f),
            height = keyHeight,
            bgColor = colors.specialKeyBg,
            fontSize = 20,
            hitPaddingH = hitPad,
            onClick = { onPunctuationPress(',') }
        )

        // Spacebar with swipe-to-move cursor
        SpaceBar(
            label = spaceLabel,
            modifier = Modifier.weight(3.4f),
            height = keyHeight,
            hitPaddingH = hitPad,
            accent = englishAccent,
            onClick = onSpace,
            onCursorMove = onCursorMove
        )

        // Period — the label must show what actually commits: দাঁড়ি (।) in
        // Banglu mode, full stop everywhere else (tester report 2026-07-13).
        KeyButton(
            label = periodLabel,
            modifier = Modifier.weight(0.8f),
            height = keyHeight,
            bgColor = colors.specialKeyBg,
            fontSize = 20,
            hitPaddingH = hitPad,
            onClick = { onPunctuationPress('.') }
        )

        // Enter -- context-aware label (search, next, go, etc.)
        EnterActionKey(
            label = enterLabel,
            modifier = Modifier.weight(1.5f),
            height = keyHeight,
            bgColor = colors.specialKeyBg,
            hitPaddingH = hitPad,
            onClick = onEnter
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// S122: Numeric keypad (number / phone / PIN fields)
// ═══════════════════════════════════════════════════════════════════════════════

/** Stock-keyboard numpad: 3-wide digit grid + an action column. Number
 *  fields get . , - ; phone fields get + * # . Digits commit ASCII (apps
 *  parsing number fields expect 0-9, never ০-৯). */
@Composable
private fun NumberPadLayout(
    phone: Boolean,
    enterLabel: String,
    onKeyPress: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBackspaceRepeat: (Int) -> Unit,
    onBackspaceWord: () -> Unit,
    onEnter: () -> Unit,
    onBackToLetters: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    val keyHeight = scaledKeyHeight(LetterKeyRowHeight)
    val hitPad = currentKeyGapH() / 2

    @Composable
    fun digit(label: String, modifier: Modifier) {
        KeyButton(
            label = label,
            modifier = modifier,
            height = keyHeight,
            bgColor = colors.keyBg,
            fontSize = 26,
            hitPaddingH = hitPad,
            onClick = { onKeyPress(label.first()) }
        )
    }

    @Composable
    fun special(label: String, modifier: Modifier, onClick: () -> Unit) {
        KeyButton(
            label = label,
            modifier = modifier,
            height = keyHeight,
            bgColor = colors.specialKeyBg,
            fontSize = 18,
            hitPaddingH = hitPad,
            onClick = onClick
        )
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        digit("1", Modifier.weight(1f)); digit("2", Modifier.weight(1f)); digit("3", Modifier.weight(1f))
        BackspaceKey(
            modifier = Modifier.weight(1f),
            height = keyHeight,
            hitPaddingH = hitPad,
            onBackspace = onBackspace,
            onBackspaceRepeat = onBackspaceRepeat,
            onBackspaceWord = onBackspaceWord
        )
    }
    Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))
    Row(modifier = Modifier.fillMaxWidth()) {
        digit("4", Modifier.weight(1f)); digit("5", Modifier.weight(1f)); digit("6", Modifier.weight(1f))
        special(if (phone) "+" else "-", Modifier.weight(1f)) { onKeyPress(if (phone) '+' else '-') }
    }
    Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))
    Row(modifier = Modifier.fillMaxWidth()) {
        digit("7", Modifier.weight(1f)); digit("8", Modifier.weight(1f)); digit("9", Modifier.weight(1f))
        special(if (phone) "*" else ",", Modifier.weight(1f)) { onKeyPress(if (phone) '*' else ',') }
    }
    Spacer(modifier = Modifier.height(scaledDp(KeyGapV)))
    Row(modifier = Modifier.fillMaxWidth()) {
        special("ABC", Modifier.weight(1f)) { onBackToLetters() }
        special(if (phone) "#" else ".", Modifier.weight(1f)) { onKeyPress(if (phone) '#' else '.') }
        digit("0", Modifier.weight(1f))
        EnterActionKey(
            label = enterLabel,
            modifier = Modifier.weight(1f),
            height = keyHeight,
            bgColor = colors.specialKeyBg,
            hitPaddingH = hitPad,
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
    hitPaddingH: Dp = 0.dp,
    onClick: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val hapticOn by rememberUpdatedState(LocalHapticEnabled.current)
    val soundOn by rememberUpdatedState(LocalSoundEnabled.current)
    val currentOnClick by rememberUpdatedState(onClick)
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 50)
    )
    val keyShape = RoundedCornerShape(KeyCorner)
    val isHorizontalArrow = label == "\u2192" || label == "\u21E5"
    val isReturnArrow = label == "\u21B5"
    val isSearch = label == "\uD83D\uDD0D"

    Box(
        modifier = modifier
            .height(height)
            .semantics {
                role = Role.Button
                contentDescription = "Enter"
                // S135 (F-005): keys handle touch through raw pointerInput,
                // which exposes NO accessibility action — TalkBack / Switch
                // Access announced every key but could not press one. The
                // semantic click is the assistive path; touch is unchanged.
                onClick { currentOnClick(); true }
            }
            .pointerInput(label) {
                // S13: commit on DOWN like letter keys \u2014 detectTapGestures
                // fired on UP and cancelled on slide-out, dropping sloppy taps.
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    isPressed = true
                    if (hapticOn) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    if (soundOn) view.playSoundEffect(SoundEffectConstants.CLICK)
                    currentOnClick()
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.all { !it.pressed }) break
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
            // S117: the arrow glyphs and their optical-centering offsets were
            // tuned for the 51dp portrait row. Landscape rows are ~0.78x that,
            // and the fixed -12dp lift pushed the clipped \u21B5 out of the key.
            // Scale both by the ACTUAL key height so the glyph stays centered
            // at every row height (portrait normal keeps ratio = 1, unchanged).
            val heightRatio = height / BottomKeyRowHeight
            // S186 (user: "the search icon is not at the centre of the key"):
            // a canvas magnifier in the strip's stroke grammar, not the emoji.
            if (isSearch) {
                IconSearch(Modifier.size(30.dp * heightRatio), colors.keyText)
            } else Text(
                text = label,
                color = colors.keyText,
                fontSize = scaledSp(
                    when {
                        isHorizontalArrow -> 32
                        isReturnArrow -> 32
                        else -> 30
                    } * heightRatio
                ),
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.offset(
                    x = when (label) {
                        "\u21E5" -> (-1).dp
                        "\u21B5" -> (-5).dp * heightRatio
                        else -> 0.dp
                    },
                    y = when (label) {
                        "\u2192", "\u21E5" -> (-1).dp
                        "\u21B5" -> (-12).dp * heightRatio
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
    /** S68: asymmetric overrides so an edge key can absorb a row indent into
     *  its touch cell (row 2's 'a'/'l') while rendering identically. Null =
     *  use [hitPaddingH]. */
    hitPaddingStart: Dp? = null,
    hitPaddingEnd: Dp? = null,
    /** S11: with commit-on-press the base character is already committed when
     *  the long-press popup opens; selecting an alternative must REPLACE it. */
    onReplaceLast: ((String) -> Unit)? = null,
    /** S99: position-aware press — receives the horizontal press fraction
     *  (0..1) inside the key so the service can run probabilistic touch
     *  targeting. Null = plain [onClick]. */
    onClickAt: ((Float) -> Unit)? = null,
    onClick: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val hapticOn by rememberUpdatedState(LocalHapticEnabled.current)
    val soundOn by rememberUpdatedState(LocalSoundEnabled.current)
    val previewOn = LocalKeyPreviewEnabled.current
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnClickAt by rememberUpdatedState(onClickAt)
    // S163: a running glide suppresses the long-press popup (the finger is
    // travelling, not holding); everything else about the key is untouched.
    val glideActiveNow by rememberUpdatedState(LocalGlideActive.current)
    var isPressed by remember { mutableStateOf(false) }
    var showAlternatives by remember { mutableStateOf(false) }

    // S68: minimum-duration press flash. A light 30-50ms tap cleared
    // isPressed within 2-3 frames — the highlight was invisible, so light
    // taps FELT unregistered even though the character committed (testers
    // then pressed again, harder). The visual state latches for ≥90ms.
    var pressVisual by remember { mutableStateOf(false) }
    var pressedAtMs by remember { mutableStateOf(0L) }
    LaunchedEffect(isPressed) {
        if (isPressed) {
            pressedAtMs = System.currentTimeMillis()
            pressVisual = true
        } else if (pressVisual) {
            val held = System.currentTimeMillis() - pressedAtMs
            if (held < MIN_PRESS_FLASH_MS) delay(MIN_PRESS_FLASH_MS - held)
            pressVisual = false
        }
    }

    // Feature 1.4: Scale UP on press for single-character keys (key preview effect)
    val isCharKey = label.length == 1
    val scale by animateFloatAsState(
        targetValue = if (pressVisual && previewOn) {
            if (isCharKey) 1.04f else 0.97f
        } else if (pressVisual) 0.97f else 1f,
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
                // S135 (F-005): assistive activation (see EnterActionKey).
                // Position-aware keys get the key centre; nothing was
                // committed on press, so alternatives INSERT (never replace).
                onClick {
                    val clickAt = currentOnClickAt
                    if (clickAt != null) clickAt(0.5f) else currentOnClick()
                    true
                }
                if (longPressOptions.isNotEmpty()) {
                    customActions = longPressOptions.map { option ->
                        CustomAccessibilityAction("Insert ${option.label}") {
                            onTextInput(option.input)
                            true
                        }
                    }
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
                    // S93 (tester: "key touch not smooth like Samsung"): the
                    // platform KEYBOARD_TAP effect — the same crisp tick OEM
                    // keyboards use — replaced Compose's TextHandleMove (the
                    // weakest haptic constant) at every key-press site; the
                    // cursor-drag ticks keep TextHandleMove deliberately.
                    if (hapticOn) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    if (soundOn) view.playSoundEffect(SoundEffectConstants.CLICK)
                    // S99: hand the press position to the targeting layer when
                    // the key opted in; geometry-only keys stay as-is.
                    val clickAt = currentOnClickAt
                    if (clickAt != null && size.width > 0) {
                        clickAt((down.position.x / size.width).coerceIn(0f, 1f))
                    } else {
                        currentOnClick()
                    }
                    var longPressed = false
                    var released = false
                    if (longPressOptions.isNotEmpty()) {
                        try {
                            // S68: 1.5x the system threshold — deliberate slow
                            // pressers were opening the variants popup by
                            // accident, and the popup then ate their next tap.
                            withTimeout(viewConfiguration.longPressTimeoutMillis * 3 / 2) {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                    if (event.changes.all { !it.pressed }) {
                                        released = true
                                        return@withTimeout
                                    }
                                }
                            }
                        } catch (_: PointerEventTimeoutCancellationException) {
                            // S15: longPressed=true always — never re-enter the
                            // release-wait after this timeout. S163: mid-glide the
                            // popup itself is suppressed (finger is travelling).
                            longPressed = true
                            if (!glideActiveNow()) {
                                showAlternatives = true
                                if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    }
                    if (!longPressed && !released) {
                        // Slide-tolerant release wait: pointer capture keeps the
                        // gesture on this key even if the finger drifts.
                        //
                        // S15: guarded by `released` — the long-press wait above
                        // already ends when the finger lifts, and re-entering
                        // this loop afterwards left a stale awaitPointerEvent()
                        // that CONSUMED the next tap's DOWN on this key. Every
                        // second press of t/d/r/s/i/u was silently eaten
                        // (shipped in 1.5.1 with commit-on-press; felt as
                        // "keys skip when typing fast").
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
                .padding(
                    start = (hitPaddingStart ?: hitPaddingH) + KeyVisualPaddingH,
                    end = (hitPaddingEnd ?: hitPaddingH) + KeyVisualPaddingH,
                    top = KeyVisualPaddingV,
                    bottom = KeyVisualPaddingV
                )
                .shadow(if (pressVisual) 0.dp else 1.5.dp, keyShape, clip = false)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(keyShape)
                .background(if (pressVisual) colors.keyPressed else bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = colors.keyText,
            fontSize = if (pressVisual && isCharKey && previewOn) scaledSp(fontSize + 2) else scaledSp(fontSize),
                fontWeight = if (label.length <= 2) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        if (showAlternatives) {
            // S168 (audit P2-1): lift by the key's own height in dp — the old
            // -96 raw px sat the popup ON the pressed key at 3x+ densities.
            val popupLift = with(LocalDensity.current) { -(height + 10.dp).roundToPx() }
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, popupLift),
                // S68: focusable=false — a focusable popup is its own window
                // and CONSUMED the tap that dismissed it, so the user's next
                // keystroke after an (often accidental) long-press vanished.
                // Non-focusable popups get ACTION_OUTSIDE: the outside tap
                // dismisses AND still reaches the key underneath.
                properties = PopupProperties(focusable = false, dismissOnBackPress = true, dismissOnClickOutside = true),
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
    hitPaddingH: Dp = 0.dp,
    /** S67: bold accent styling while the keyboard is in raw-English mode. */
    accent: Boolean = false,
    onClick: () -> Unit,
    onCursorMove: (Int) -> Unit = {}
) {
    val colors = LocalKeyboardColors.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val hapticOn by rememberUpdatedState(LocalHapticEnabled.current)
    val soundOn by rememberUpdatedState(LocalSoundEnabled.current)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnCursorMove by rememberUpdatedState(onCursorMove)
    val rollover = LocalSpaceRollover.current
    var isPressed by remember { mutableStateOf(false) }
    var isCursorMode by remember { mutableStateOf(false) }
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
                // S135 (F-005): assistive activation + cursor moves as actions.
                onClick { currentOnClick(); true }
                customActions = listOf(
                    CustomAccessibilityAction("Move cursor left") { currentOnCursorMove(-1); true },
                    CustomAccessibilityAction("Move cursor right") { currentOnCursorMove(1); true }
                )
            }
            .pointerInput(Unit) {
                // S13: single gesture owner. The old detectDragGestures +
                // detectTapGestures pair let ~8dp of natural thumb roll start a
                // "cursor drag" that swallowed the space entirely (buzz but no
                // space -> felt as "space needs a hard/double press"). Now only
                // a deliberate 28dp horizontal pull engages cursor mode; any
                // smaller drift — and any vertical slide — is still a space.
                val cursorEngagePx = 28.dp.toPx()
                val stepPx = 14.dp.toPx()
                // S32: distance alone misfired — a fast thumb FLICK on space
                // covers 28dp+ of incidental travel and the tap was swallowed
                // as a zero-move "cursor drag" (felt as "space needs a hard
                // press"). A real cursor drag is a sustained pull, so require
                // the finger to also be down for a beat before engaging.
                val cursorEngageMinMs = 120L
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    isPressed = true
                    rollover.onSpaceDown()
                    if (hapticOn) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    if (soundOn) view.playSoundEffect(SoundEffectConstants.CLICK)
                    val downTime = down.uptimeMillis
                    var totalDx = 0f
                    var cursorMode = false
                    var remainder = 0f
                    var moves = 0
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        val deltaX = change?.positionChange()?.x ?: 0f
                        val heldMs = (change?.uptimeMillis ?: downTime) - downTime
                        event.changes.forEach { it.consume() }
                        totalDx += deltaX
                        // S194: a space that already went out (another finger
                        // landed) can no longer become a cursor drag.
                        if (!cursorMode && rollover.canEngageCursor &&
                            kotlin.math.abs(totalDx) >= cursorEngagePx && heldMs >= cursorEngageMinMs
                        ) {
                            cursorMode = true
                            isCursorMode = true
                            rollover.onCursorModeEngaged()
                            remainder = 0f
                            if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (cursorMode) {
                            remainder += deltaX
                            while (kotlin.math.abs(remainder) >= stepPx) {
                                val direction = if (remainder > 0) 1 else -1
                                currentOnCursorMove(direction)
                                moves++
                                remainder -= direction * stepPx
                                if (hapticOn && moves % 3 == 0) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                        if (event.changes.all { !it.pressed }) break
                    }
                    isPressed = false
                    isCursorMode = false
                    // S194: the policy owns the release — a plain tap commits
                    // here, a drag or an early (rollover) commit does nothing.
                    if (rollover.onSpaceUp() == SpaceRolloverPolicy.Release.COMMIT) currentOnClick()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = hitPaddingH + KeyVisualPaddingH, vertical = KeyVisualPaddingV)
                .shadow(if (isPressed) 0.dp else 1.5.dp, keyShape, clip = false)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(keyShape)
                .background(if (isPressed) colors.keyPressed else colors.keyBg)
                .then(
                    if (accent) Modifier.border(1.5.dp, colors.suggestionHighlight, keyShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isCursorMode) "\u25C4 \u25BA cursor" else label,
                color = when {
                    isCursorMode -> colors.keyText
                    accent -> colors.suggestionHighlight
                    else -> colors.keyText.copy(alpha = 0.68f)
                },
                fontSize = scaledSp(13),
                fontWeight = if (accent) FontWeight.Bold else FontWeight.Medium,
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
    hitPaddingH: Dp = 0.dp,
    onBackspace: () -> Unit,
    onBackspaceRepeat: (Int) -> Unit = { count -> repeat(count) { onBackspace() } },
    onBackspaceWord: () -> Unit = {}
) {
    val colors = LocalKeyboardColors.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val hapticOn by rememberUpdatedState(LocalHapticEnabled.current)
    val soundOn by rememberUpdatedState(LocalSoundEnabled.current)
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
                // S135 (F-005): assistive activation; word delete as an action.
                onClick { currentOnBackspace(); true }
                customActions = listOf(
                    CustomAccessibilityAction("Delete previous word") { currentOnBackspaceWord(); true }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (hapticOn) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
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
                .padding(horizontal = hitPaddingH + KeyVisualPaddingH, vertical = KeyVisualPaddingV)
                .shadow(if (isPressed) 0.dp else 1.5.dp, keyShape, clip = false)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(keyShape)
                .background(if (isPressed) colors.keyPressed else colors.specialKeyBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "\u232B",
                color = colors.keyText,
                fontSize = scaledSp(19),
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
    hitPaddingH: Dp = 0.dp,
    onNumberPress: (Char) -> Unit,
    onSymbolPress: (Char) -> Unit
) {
    val colors = LocalKeyboardColors.current
    val symbol = NUMBER_SYMBOL_MAP[number] ?: '!'
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val hapticOn by rememberUpdatedState(LocalHapticEnabled.current)
    val soundOn by rememberUpdatedState(LocalSoundEnabled.current)
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
                // S135 (F-005): assistive activation; the symbol as an action.
                onClick { currentOnNumberPress(number); true }
                customActions = listOf(
                    CustomAccessibilityAction("Type $symbol") { currentOnSymbolPress(symbol); true }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        if (hapticOn) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
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
                .padding(horizontal = hitPaddingH + KeyVisualPaddingH, vertical = KeyVisualPaddingV)
                .shadow(if (isPressed) 0.dp else 1.5.dp, keyShape, clip = false)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(keyShape)
                .background(if (isPressed) colors.keyPressed else colors.keyBg)
                .padding(4.dp)
        ) {
            Text(
                text = displayNumber.toString(),
                color = colors.keyText,
                fontSize = scaledSp(17),
                modifier = Modifier.align(Alignment.Center)
            )
            Text(
                text = symbol.toString(),
                color = colors.subText,
                fontSize = scaledSp(9),
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
    panelHeight: Dp?,
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
    val hapticOn by rememberUpdatedState(LocalHapticEnabled.current)
    val soundOn by rememberUpdatedState(LocalSoundEnabled.current)
    // S57: drop stale recents from older builds (removed sticker texts
    // rendered as broken emoji cells).
    val recentEmojis = recentEmojisProvider().filter { EmojiData.isKnownEmoji(it) }
    val searching = searchQuery.isNotBlank()
    val showingPhrases = !searching && selectedCategory == EmojiData.PHRASES_CATEGORY_INDEX
    val currentEmojis = when {
        searching -> remember(searchQuery) { EmojiData.search(searchQuery) }
        selectedCategory == 0 && recentEmojis.isNotEmpty() -> recentEmojis
        else -> EmojiData.categories.getOrNull(selectedCategory)?.emojis.orEmpty()
    }

    val commitItem: (String) -> Unit = { item ->
        if (hapticOn) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        if (soundOn) view.playSoundEffect(SoundEffectConstants.CLICK)
        onEmojiClick(item)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.keyboardBg)
    ) {
        // S57: single search pill row — the old top category rail is gone; the
        // panel has exactly two chrome rows like WhatsApp/Gboard.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.suggestionChipBg)
                    .clickable { searchActive = true }
                    .padding(horizontal = 14.dp)
                    .semantics {
                        contentDescription = "Search emoji. Tap to type with keyboard search keys"
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("\uD83D\uDD0D", fontSize = scaledSp(14), color = Color.Unspecified)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = searchQuery.ifBlank {
                        if (searchActive) "নিচের কী দিয়ে লিখুন" else "খুঁজুন: hasi, love, দোয়া…"
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
                    .padding(start = 6.dp)
                    .size(36.dp)
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    when {
                        searchActive -> 132.dp
                        // search row 48dp + tab row 48dp frame the grid
                        panelHeight != null -> (panelHeight - 96.dp).coerceIn(180.dp, 420.dp)
                        else -> 286.dp
                    }
                )
                .padding(horizontal = 8.dp)
        ) {
            when {
                searching && currentEmojis.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "\"$searchQuery\" পাওয়া যায়নি",
                            color = colors.keyText,
                            fontSize = scaledSp(15),
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "লিখে দেখুন: hasi, mon kharap, dua, eid, love",
                            color = colors.subText,
                            fontSize = scaledSp(13),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                showingPhrases -> PhraseGrid(colors = colors, onPhraseClick = commitItem)
                else -> EmojiGrid(
                    emojis = currentEmojis,
                    colors = colors,
                    onItemClick = commitItem
                )
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
                    .background(colors.suggestionBg)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KeyButton(
                    label = "ABC",
                    modifier = Modifier.width(56.dp),
                    height = 40.dp,
                    bgColor = colors.specialKeyBg,
                    fontSize = 14,
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
                        .padding(horizontal = 4.dp)
                )
                BackspaceKey(
                    modifier = Modifier.width(52.dp),
                    onBackspace = onBackspace,
                    onBackspaceWord = onBackspace
                )
            }
        }
    }
}

/** S57: emoji grid (search results may mix in phrase cards at half-row span). */
@Composable
private fun EmojiGrid(
    emojis: List<String>,
    colors: KeyboardColors,
    onItemClick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(
            count = emojis.size,
            span = { index -> GridItemSpan(if (BanglaPhrases.isPhrase(emojis[index])) 4 else 1) }
        ) { index ->
            val item = emojis[index]
            if (BanglaPhrases.isPhrase(item)) {
                PhraseCard(text = item, colors = colors, onClick = { onItemClick(item) })
            } else {
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .semantics {
                            role = Role.Button
                            contentDescription = "Emoji $item"
                        }
                        .clickable { onItemClick(item) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        item,
                        fontSize = 28.sp,
                        color = Color.Unspecified,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** S57b: accordion everyday-phrase tab (বাক্য) — every section is a
 *  tappable dropdown row, so all 9 categories are reachable without
 *  scrolling through the full phrase list. One section open at a time. */
@Composable
private fun PhraseGrid(
    colors: KeyboardColors,
    onPhraseClick: (String) -> Unit
) {
    var expandedSection by remember { mutableIntStateOf(0) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BanglaPhrases.sections.forEachIndexed { index, section ->
            val expanded = index == expandedSection
            item(span = { GridItemSpan(2) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (expanded) colors.suggestionHighlight.copy(alpha = 0.16f)
                            else colors.suggestionChipBg
                        )
                        .semantics {
                            role = Role.Button
                            contentDescription = "Phrase section ${section.title}"
                            stateDescription = if (expanded) "Expanded" else "Collapsed"
                        }
                        .clickable {
                            expandedSection = if (expanded) -1 else index
                        }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = section.title,
                        color = if (expanded) colors.suggestionHighlight else colors.keyText,
                        fontSize = scaledSp(14),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${section.phrases.size}",
                        color = colors.subText,
                        fontSize = scaledSp(12),
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Text(
                        text = if (expanded) "\u25BE" else "\u25B8",
                        color = if (expanded) colors.suggestionHighlight else colors.subText,
                        fontSize = scaledSp(16)
                    )
                }
            }
            if (expanded) {
                section.phrases.forEach { phrase ->
                    item {
                        PhraseCard(
                            text = phrase.text,
                            colors = colors,
                            onClick = { onPhraseClick(phrase.text) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhraseCard(
    text: String,
    colors: KeyboardColors,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(44.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.suggestionChipBg)
            .semantics {
                role = Role.Button
                contentDescription = "Phrase $text"
            }
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
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
private fun EmojiBottomCategoryRail(
    selectedCategory: Int,
    onCategorySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalKeyboardColors.current
    LazyRow(
        modifier = modifier.fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(EmojiData.categories) { index, category ->
            val selected = selectedCategory == index
            Box(
                modifier = Modifier
                    .width(if (category.name == "\u09AC\u09BE\u0995\u09CD\u09AF") 52.dp else 38.dp)
                    .fillMaxHeight()
                    .semantics {
                        role = Role.Button
                        contentDescription = "Emoji category ${category.name}"
                        if (selected) stateDescription = "Selected"
                    }
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (selected) colors.suggestionHighlight.copy(alpha = 0.16f)
                        else Color.Transparent
                    )
                    .clickable { onCategorySelected(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.icon,
                    color = if (category.isTextIcon) {
                        if (selected) colors.suggestionHighlight else colors.subText
                    } else {
                        Color.Unspecified
                    },
                    fontSize = if (category.isTextIcon) scaledSp(14) else scaledSp(20),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .height(3.dp)
                            .width(20.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.suggestionHighlight)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmojiSearchKeyboard(
    query: String,
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onBackToKeyboard: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    // S21: gaps live INSIDE each touch cell (S13 pattern) — no spacedBy dead
    // strips between emoji-search keys, no dead seams between its rows.
    val keyHeight = 44.dp
    val hitPad = currentKeyGapH() / 2

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.keyboardBg)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        EmojiSearchKeyRow(EMOJI_SEARCH_ROW_1, keyHeight, onKey)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            EMOJI_SEARCH_ROW_2.forEach { key ->
                KeyButton(
                    label = key,
                    modifier = Modifier.weight(1f),
                    height = keyHeight,
                    bgColor = colors.keyBg,
                    fontSize = 18,
                    hitPaddingH = hitPad,
                    onClick = { onKey(key) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyButton(
                label = "ABC",
                modifier = Modifier.weight(1.55f),
                height = keyHeight,
                bgColor = colors.specialKeyBg,
                fontSize = 15,
                accessibilityLabel = "Back to keyboard",
                hitPaddingH = hitPad,
                onClick = onBackToKeyboard
            )
            EMOJI_SEARCH_ROW_3.forEach { key ->
                KeyButton(
                    label = key,
                    modifier = Modifier.weight(1f),
                    height = keyHeight,
                    bgColor = colors.keyBg,
                    fontSize = 18,
                    hitPaddingH = hitPad,
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
                hitPaddingH = hitPad,
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
    val hitPad = currentKeyGapH() / 2
    Row(modifier = Modifier.fillMaxWidth()) {
        keys.forEach { key ->
            KeyButton(
                label = key,
                modifier = Modifier.weight(1f),
                height = keyHeight,
                bgColor = colors.keyBg,
                fontSize = 18,
                hitPaddingH = hitPad,
                onClick = { onKey(key) }
            )
        }
    }
}
