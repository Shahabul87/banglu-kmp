package com.banglu.keyboard

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.banglu.engine.SmartEngineAdapter
import com.banglu.engine.types.ConversionResult
import com.banglu.engine.types.ResolutionSource
import com.banglu.engine.types.SmartSuggestion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BangluIMEService : InputMethodService(),
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    // Lifecycle wiring for Compose
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    // ── State ──────────────────────────────────────────────────────────────
    private var buffer = ""
    private val suggestions = mutableStateListOf<SmartSuggestion>()
    private val keyboardMode = mutableStateOf(KeyboardMode.BANGLU)
    private val shiftState = mutableStateOf(ShiftState.OFF)

    // Feature 3.1: Toolbar state
    private val isToolbarExpanded = mutableStateOf(false)

    // Track the letter mode to return to from symbols
    private var letterModeBeforeSymbols = KeyboardMode.BANGLU

    // For double-tap shift detection
    private var lastShiftTapTime = 0L
    private val DOUBLE_TAP_THRESHOLD_MS = 300L

    // Feature 1.1: Double-space → period + space
    private var lastSpaceTime = 0L
    private val DOUBLE_SPACE_THRESHOLD_MS = 300L

    // Feature 4.1: Bengali next-word predictions
    private var lastCommittedBengali = ""

    // Feature 1.3: Context-aware enter key label
    private val enterKeyLabel = mutableStateOf("\u21B5")

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var suggestionJob: Job? = null

    // ── Settings (read from SharedPreferences) ──────────────────────────
    private lateinit var prefs: SharedPreferences
    val hapticEnabled = mutableStateOf(true)
    val soundEnabled = mutableStateOf(true)
    val suggestionsEnabled = mutableStateOf(true)
    val autoCapitalizeEnabled = mutableStateOf(true)
    val doubleSpacePeriodEnabled = mutableStateOf(true)
    val numberRowEnabled = mutableStateOf(true)
    val keyPreviewEnabled = mutableStateOf(true)
    val themeMode = mutableStateOf("auto")
    val keyboardHeightMode = mutableStateOf("normal")

    companion object {
        private const val TAG = "BangluIME"
    }

    /** Debug-only logging — stripped from release builds */
    private fun log(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    /** Safe conversion wrapper — never crashes the keyboard */
    private fun safeConvert(input: String): ConversionResult {
        return try {
            SmartEngineAdapter.convertWord(input)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Conversion failed for '$input'", e)
            ConversionResult(input, 0.0, ResolutionSource.RULE, emptyList())
        }
    }

    /** Safe suggestions wrapper */
    private fun safeSuggestions(input: String, limit: Int = 8): List<SmartSuggestion> {
        return try {
            SmartEngineAdapter.getSuggestions(input, limit)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Suggestions failed for '$input'", e)
            emptyList()
        }
    }

    private fun refreshSuggestionsAsync(input: String) {
        suggestionJob?.cancel()
        if (!suggestionsEnabled.value || input.isEmpty()) {
            suggestions.clear()
            return
        }

        val snapshot = input
        suggestionJob = serviceScope.launch {
            val newSuggestions = withContext(Dispatchers.Default) {
                safeSuggestions(snapshot, 8)
            }
            if (keyboardMode.value == KeyboardMode.BANGLU && buffer == snapshot) {
                suggestions.clear()
                suggestions.addAll(newSuggestions)
            }
        }
    }

    private fun learnCommittedWordAsync(phonetic: String, bengali: String) {
        serviceScope.launch {
            SmartEngineAdapter.onWordSelected(phonetic, bengali)
        }
    }

    /** Reload settings from SharedPreferences */
    private fun reloadSettings() {
        hapticEnabled.value = prefs.getBoolean("haptic_feedback", true)
        soundEnabled.value = prefs.getBoolean("sound_feedback", true)
        suggestionsEnabled.value = prefs.getBoolean("suggestions", true)
        autoCapitalizeEnabled.value = prefs.getBoolean("auto_capitalize", true)
        doubleSpacePeriodEnabled.value = prefs.getBoolean("double_space_period", true)
        numberRowEnabled.value = prefs.getBoolean("number_row", true)
        keyPreviewEnabled.value = prefs.getBoolean("key_preview", true)
        themeMode.value = prefs.getString("theme", "auto") ?: "auto"
        keyboardHeightMode.value = prefs.getString("keyboard_height", "normal") ?: "normal"
        val defaultMode = prefs.getString("default_mode", "banglu") ?: "banglu"
        letterModeBeforeSymbols = if (defaultMode == "english") KeyboardMode.ENGLISH else KeyboardMode.BANGLU
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        prefs = getSharedPreferences("banglu_prefs", Context.MODE_PRIVATE)
        reloadSettings()

        log("onCreate: Initializing SmartEngine...")
        SmartEngineAdapter.initializeSync()
        log("onCreate: Seed dictionary loaded")

        serviceScope.launch {
            try {
                val storage = AndroidStorage(applicationContext)
                val loader = AndroidDictionaryLoader(applicationContext)
                log("onCreate: Loading full dictionary from SQLite...")
                SmartEngineAdapter.initialize(storage, loader)
                log("onCreate: Full dictionary loaded!")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "onCreate: Failed to load full dictionary", e)
            }
        }
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        reloadSettings()

        return try {
            val composeView = ComposeView(this).apply {
                setContent {
                    BangluKeyboardLayout(
                        suggestions = suggestions,
                        keyboardMode = keyboardMode.value,
                        shiftState = shiftState.value,
                        enterLabel = enterKeyLabel.value,
                        isToolbarExpanded = isToolbarExpanded.value,
                        hapticEnabled = hapticEnabled.value,
                        soundEnabled = soundEnabled.value,
                        suggestionsEnabled = suggestionsEnabled.value,
                        numberRowEnabled = numberRowEnabled.value,
                        keyPreviewEnabled = keyPreviewEnabled.value,
                        themePref = themeMode.value,
                        keyboardHeightMode = keyboardHeightMode.value,
                        onKeyPress = { char -> onKeyPress(char) },
                        onTextInput = { text -> onTextInput(text) },
                        onBackspace = { onBackspace() },
                        onBackspaceWord = { onBackspaceWord() },
                        onSpace = { onSpacePress() },
                        onEnter = { onEnterPress() },
                        onShiftTap = { onShiftTap() },
                        onGlobePress = { onGlobePress() },
                        onSymbolsPress = { onSymbolsPress() },
                        onBackToLetters = { onBackToLetters() },
                        onSymbolPageToggle = { onSymbolPageToggle() },
                        onSuggestionClick = { onSuggestionTap(it) },
                        onNumberPress = { char -> onDirectCommit(char) },
                        onPunctuationPress = { char -> onPunctuationPress(char) },
                        onCursorMove = { direction -> onCursorMove(direction) },
                        onDismiss = { requestHideSelf(0) },
                        onSettingsClick = { onSettingsClick() },
                        onToggleToolbar = { isToolbarExpanded.value = !isToolbarExpanded.value },
                        onEmojiClick = { emoji -> onEmojiClick(emoji) },
                        onEmojiOpen = { onEmojiOpen() },
                        onBackFromEmoji = { onBackFromEmoji() }
                    )
                }
            }

            // Wire lifecycle trees for Compose
            window?.window?.decorView?.let { decorView ->
                decorView.setViewTreeLifecycleOwner(this)
                decorView.setViewTreeViewModelStoreOwner(this)
                decorView.setViewTreeSavedStateRegistryOwner(this)
            }

            composeView
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "onCreateInputView: Compose failed, using fallback", e)
            // Minimal fallback view so the keyboard doesn't crash
            View(this)
        }
    }

    /**
     * PERMANENT FIX for Samsung/gesture nav bar overlap.
     * Tell the system exactly where our keyboard content is so it positions
     * the IME window above the navigation bar automatically.
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE
        // Let the system handle positioning — don't override contentTopInsets
        // The key is setting touchableInsets so the nav bar area is excluded
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        reloadSettings()
        buffer = ""
        suggestions.clear()
        lastSpaceTime = 0L

        // Feature 1.3: Set enter key label based on IME action
        enterKeyLabel.value = when (info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)) {
            EditorInfo.IME_ACTION_SEARCH -> "\uD83D\uDD0D"  // magnifying glass
            EditorInfo.IME_ACTION_GO -> "\u2192"             // right arrow
            EditorInfo.IME_ACTION_NEXT -> "\u21E5"           // tab right
            else -> "\u21B5"                                  // return symbol
        }

        // Feature 1.2: Auto-capitalize at start of text field (English mode)
        if (autoCapitalizeEnabled.value && keyboardMode.value == KeyboardMode.ENGLISH) {
            val before = currentInputConnection?.getTextBeforeCursor(1, 0)?.toString()
            if (before.isNullOrEmpty()) {
                shiftState.value = ShiftState.ON
            }
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        buffer = ""
        suggestions.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
    }

    // ── Key Handlers ───────────────────────────────────────────────────────

    private fun onKeyPress(char: Char) {
        when (keyboardMode.value) {
            KeyboardMode.BANGLU -> onBangluKeyPress(char)
            KeyboardMode.ENGLISH -> onEnglishKeyPress(char)
            else -> onDirectCommit(char)
        }
    }

    private fun onTextInput(text: String) {
        if (text.isEmpty()) return
        when (keyboardMode.value) {
            KeyboardMode.BANGLU -> text.forEach { onBangluKeyPress(it) }
            KeyboardMode.ENGLISH -> {
                val ic = currentInputConnection ?: return
                ic.commitText(text, 1)
            }
            else -> text.forEach { onDirectCommit(it) }
        }
    }

    private fun onBangluKeyPress(char: Char) {
        buffer += char
        log("onBangluKeyPress: char='$char', buffer='$buffer'")

        // Auto-unshift after typing a letter (unless caps lock)
        if (shiftState.value == ShiftState.ON && char.isLetter()) {
            shiftState.value = ShiftState.OFF
        }

        val ic = currentInputConnection ?: return
        if (currentInputEditorInfo?.inputType == InputType.TYPE_NULL) {
            ic.commitText(char.toString(), 1)
            return
        }

        val result = safeConvert(buffer)
        log("convert: '$buffer' -> '${result.bengali}' (${result.confidence})")
        ic.setComposingText(result.bengali, 1)

        refreshSuggestionsAsync(buffer)
    }

    private fun onEnglishKeyPress(char: Char) {
        log("onEnglishKeyPress: char='$char'")

        // Auto-unshift after typing a letter (unless caps lock)
        if (shiftState.value == ShiftState.ON && char.isLetter()) {
            shiftState.value = ShiftState.OFF
        }

        val ic = currentInputConnection ?: return
        ic.commitText(char.toString(), 1)

        // Do NOT auto-capitalize here — only after space/enter
        // Auto-capitalizing after every keypress causes uppercase in middle of words
    }

    private fun onDirectCommit(char: Char) {
        log("onDirectCommit: char='$char'")
        // Commit any pending Banglu buffer first
        commitPendingBuffer()

        val ic = currentInputConnection ?: return
        ic.commitText(char.toString(), 1)
    }

    private fun onPunctuationPress(char: Char) {
        log("onPunctuationPress: char='$char'")
        // Commit any pending Banglu buffer first, then commit the punctuation
        commitPendingBuffer()

        val ic = currentInputConnection ?: return
        ic.commitText(char.toString(), 1)
    }

    private fun onBackspace() {
        log("onBackspace: mode=${keyboardMode.value}, buffer='$buffer'")
        val ic = currentInputConnection ?: return

        when (keyboardMode.value) {
            KeyboardMode.BANGLU -> {
                if (buffer.isNotEmpty()) {
                    buffer = buffer.dropLast(1)
                    if (buffer.isEmpty()) {
                        ic.setComposingText("", 0)
                        ic.finishComposingText()
                        suggestions.clear()
                    } else {
                        val result = safeConvert(buffer)
                        ic.setComposingText(result.bengali, 1)
                        refreshSuggestionsAsync(buffer)
                    }
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
            }
            else -> {
                // English and symbol modes: always delete previous character
                ic.deleteSurroundingText(1, 0)
            }
        }
    }

    private fun onSpacePress() {
        log("onSpacePress: mode=${keyboardMode.value}, buffer='$buffer'")
        val ic = currentInputConnection ?: return
        val now = System.currentTimeMillis()

        when (keyboardMode.value) {
            KeyboardMode.BANGLU -> {
                if (buffer.isNotEmpty()) {
                    val result = safeConvert(buffer)
                    log("onSpacePress: committing '${result.bengali}'")
                    ic.commitText(result.bengali + " ", 1)
                    learnCommittedWordAsync(buffer, result.bengali)
                    buffer = ""
                    suggestions.clear()
                    updatePredictions(result.bengali)
                } else {
                    // Feature 1.1: Double-space → Bengali danda + space
                    if (doubleSpacePeriodEnabled.value && now - lastSpaceTime < DOUBLE_SPACE_THRESHOLD_MS) {
                        ic.deleteSurroundingText(1, 0)
                        ic.commitText("\u0964 ", 1)  // Bengali danda (।) + space
                    } else {
                        ic.commitText(" ", 1)
                    }
                }
            }
            else -> {
                // Feature 1.1: Double-space → period + space (English/Symbol modes)
                if (doubleSpacePeriodEnabled.value && now - lastSpaceTime < DOUBLE_SPACE_THRESHOLD_MS) {
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                } else {
                    ic.commitText(" ", 1)
                }
            }
        }

        lastSpaceTime = now

        // Feature 1.2: Auto-capitalize after double-space period
        if (autoCapitalizeEnabled.value && shouldAutoCapitalize() && shiftState.value == ShiftState.OFF) {
            shiftState.value = ShiftState.ON
        }
    }

    private fun onEnterPress() {
        log("onEnterPress: mode=${keyboardMode.value}, buffer='$buffer'")
        val ic = currentInputConnection ?: return

        // Commit any pending buffer
        if (keyboardMode.value == KeyboardMode.BANGLU && buffer.isNotEmpty()) {
            val result = safeConvert(buffer)
            ic.commitText(result.bengali, 1)
            learnCommittedWordAsync(buffer, result.bengali)
            val committedBengali = result.bengali
            buffer = ""
            suggestions.clear()
            updatePredictions(committedBengali)
        }

        // Feature 1.3: Perform the appropriate IME action
        val editorInfo = currentInputEditorInfo
        val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_UNSPECIFIED

        if (action == EditorInfo.IME_ACTION_SEARCH ||
            action == EditorInfo.IME_ACTION_GO ||
            action == EditorInfo.IME_ACTION_NEXT
        ) {
            ic.performEditorAction(action)
        } else {
            // Default: insert newline
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun onSuggestionTap(suggestion: SmartSuggestion) {
        log("onSuggestionTap: '${suggestion.bengali}' (tier=${suggestion.tier})")
        val ic = currentInputConnection ?: return

        if (buffer.isEmpty()) {
            // Feature 4.1: This is a next-word prediction — commit directly with space
            ic.commitText(suggestion.bengali + " ", 1)
            updatePredictions(suggestion.bengali)
        } else {
            // This is a conversion suggestion
            ic.commitText(suggestion.bengali + " ", 1)
            learnCommittedWordAsync(buffer, suggestion.bengali)
            buffer = ""
            suggestions.clear()
            updatePredictions(suggestion.bengali)
        }
    }

    // ── Shift Handling ─────────────────────────────────────────────────────

    private fun onShiftTap() {
        val now = System.currentTimeMillis()
        val timeSinceLastTap = now - lastShiftTapTime
        lastShiftTapTime = now

        when (shiftState.value) {
            ShiftState.OFF -> {
                shiftState.value = ShiftState.ON
            }
            ShiftState.ON -> {
                // Double-tap within threshold -> caps lock
                if (timeSinceLastTap < DOUBLE_TAP_THRESHOLD_MS) {
                    shiftState.value = ShiftState.CAPS_LOCK
                } else {
                    shiftState.value = ShiftState.OFF
                }
            }
            ShiftState.CAPS_LOCK -> {
                shiftState.value = ShiftState.OFF
            }
        }

        log("onShiftTap: shiftState=${shiftState.value}")
    }

    // ── Mode Switching ─────────────────────────────────────────────────────

    private fun onGlobePress() {
        // Commit any pending Banglu buffer
        commitPendingBuffer()

        // Toggle between Banglu and English
        val currentMode = keyboardMode.value
        val newMode = when (currentMode) {
            KeyboardMode.BANGLU -> KeyboardMode.ENGLISH
            KeyboardMode.ENGLISH -> KeyboardMode.BANGLU
            // From symbols or emoji, toggle the underlying letter mode
            KeyboardMode.SYMBOLS_1, KeyboardMode.SYMBOLS_2 -> {
                letterModeBeforeSymbols = if (letterModeBeforeSymbols == KeyboardMode.BANGLU) {
                    KeyboardMode.ENGLISH
                } else {
                    KeyboardMode.BANGLU
                }
                // Stay in symbols mode, the label will update
                currentMode
            }
            KeyboardMode.EMOJI -> {
                // Return to the opposite letter mode
                if (letterModeBeforeSymbols == KeyboardMode.BANGLU) KeyboardMode.ENGLISH
                else KeyboardMode.BANGLU
            }
        }

        keyboardMode.value = newMode
        suggestions.clear()
        log("onGlobePress: mode=$newMode")
    }

    private fun onSymbolsPress() {
        // Commit any pending Banglu buffer
        commitPendingBuffer()

        // Remember current letter mode
        letterModeBeforeSymbols = keyboardMode.value
        keyboardMode.value = KeyboardMode.SYMBOLS_1
        log("onSymbolsPress: entering SYMBOLS_1")
    }

    private fun onBackToLetters() {
        keyboardMode.value = letterModeBeforeSymbols
        log("onBackToLetters: returning to $letterModeBeforeSymbols")
    }

    private fun onSymbolPageToggle() {
        keyboardMode.value = when (keyboardMode.value) {
            KeyboardMode.SYMBOLS_1 -> KeyboardMode.SYMBOLS_2
            KeyboardMode.SYMBOLS_2 -> KeyboardMode.SYMBOLS_1
            else -> keyboardMode.value
        }
        log("onSymbolPageToggle: mode=${keyboardMode.value}")
    }

    // ── Feature 3.1: Toolbar Actions ────────────────────────────────────────

    private fun onSettingsClick() {
        val intent = Intent(this, SettingsActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    // ── Emoji Panel ──────────────────────────────────────────────────────────

    private fun onEmojiClick(emoji: String) {
        val ic = currentInputConnection ?: return
        commitPendingBuffer()
        ic.commitText(emoji, 1)
    }

    private fun onEmojiOpen() {
        commitPendingBuffer()
        // Remember which letter mode we came from (ignore if already in symbols/emoji)
        if (keyboardMode.value == KeyboardMode.BANGLU || keyboardMode.value == KeyboardMode.ENGLISH) {
            letterModeBeforeSymbols = keyboardMode.value
        }
        keyboardMode.value = KeyboardMode.EMOJI
    }

    private fun onBackFromEmoji() {
        keyboardMode.value = letterModeBeforeSymbols
    }

    // ── Feature 4.1: Next-Word Predictions ─────────────────────────────────

    /**
     * After committing a Bengali word, show predicted next words in the suggestion bar.
     * Only shows predictions when the composing buffer is empty and keyboard is in Banglu mode.
     */
    private fun updatePredictions(committedBengali: String) {
        lastCommittedBengali = committedBengali
        if (buffer.isEmpty() && keyboardMode.value == KeyboardMode.BANGLU) {
            suggestionJob?.cancel()
            suggestions.clear()
            val snapshot = committedBengali
            suggestionJob = serviceScope.launch {
                val predictions = withContext(Dispatchers.Default) {
                    SmartEngineAdapter.getNextWordPredictions(snapshot, 6)
                }
                if (buffer.isEmpty() && keyboardMode.value == KeyboardMode.BANGLU && lastCommittedBengali == snapshot) {
                    suggestions.clear()
                    suggestions.addAll(predictions.map { pred ->
                        SmartSuggestion(
                            bengali = pred.bengali,
                            confidence = pred.confidence,
                            source = "prediction",
                            phonetic = "",
                            tier = "prediction"
                        )
                    })
                }
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Feature 1.2: Auto-capitalize after sentence-ending punctuation.
     * Only applies in English mode.
     */
    private fun shouldAutoCapitalize(): Boolean {
        if (keyboardMode.value != KeyboardMode.ENGLISH) return false
        val ic = currentInputConnection ?: return false
        val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: return false
        // Auto-capitalize at: start of field, after ". ", after "! ", after "? ", after newline
        return before.isEmpty()
            || before.endsWith(". ")
            || before.endsWith("! ")
            || before.endsWith("? ")
            || before.endsWith("\n")
            || before.endsWith("\n ")
    }

    /**
     * Feature 1.5: Word-by-word backspace — delete entire previous word.
     */
    private fun onBackspaceWord() {
        val ic = currentInputConnection ?: return

        // In Banglu mode with buffer, clear the whole buffer at once
        if (keyboardMode.value == KeyboardMode.BANGLU && buffer.isNotEmpty()) {
            ic.setComposingText("", 0)
            ic.finishComposingText()
            buffer = ""
            suggestions.clear()
            return
        }

        // Delete word: find previous word boundary
        val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: return
        val trimmed = before.trimEnd()
        val lastSpace = trimmed.lastIndexOf(' ')
        val charsToDelete = if (lastSpace >= 0) before.length - lastSpace else before.length
        if (charsToDelete > 0) {
            ic.deleteSurroundingText(charsToDelete, 0)
        }
    }

    /**
     * Feature 2.1: Swipe spacebar cursor movement.
     * Commits any pending buffer, then sends a DPAD left/right key event.
     */
    private fun onCursorMove(direction: Int) {
        val ic = currentInputConnection ?: return
        // Commit any pending buffer first
        commitPendingBuffer()
        // Move cursor
        val keyCode = if (direction > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun commitPendingBuffer() {
        if (keyboardMode.value == KeyboardMode.BANGLU && buffer.isNotEmpty()) {
            val ic = currentInputConnection ?: return
            val result = safeConvert(buffer)
            ic.commitText(result.bengali, 1)
            SmartEngineAdapter.onWordSelected(buffer, result.bengali)
            buffer = ""
            suggestions.clear()
        }
    }
}
