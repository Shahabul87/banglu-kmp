package com.banglu.keyboard

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
import com.banglu.engine.types.SmartSuggestion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

    // Track the letter mode to return to from symbols
    private var letterModeBeforeSymbols = KeyboardMode.BANGLU

    // For double-tap shift detection
    private var lastShiftTapTime = 0L
    private val DOUBLE_TAP_THRESHOLD_MS = 300L

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "BangluIME"
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        Log.d(TAG, "onCreate: Initializing SmartEngine...")
        SmartEngineAdapter.initializeSync()
        Log.d(TAG, "onCreate: Seed dictionary loaded")

        serviceScope.launch {
            try {
                val storage = AndroidStorage(applicationContext)
                val loader = AndroidDictionaryLoader(applicationContext)
                Log.d(TAG, "onCreate: Loading full dictionary from SQLite...")
                SmartEngineAdapter.initialize(storage, loader)
                Log.d(TAG, "onCreate: Full dictionary loaded!")
            } catch (e: Exception) {
                Log.e(TAG, "onCreate: Failed to load full dictionary", e)
            }
        }
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val composeView = ComposeView(this).apply {
            setContent {
                BangluKeyboardLayout(
                    suggestions = suggestions,
                    keyboardMode = keyboardMode.value,
                    shiftState = shiftState.value,
                    onKeyPress = { char -> onKeyPress(char) },
                    onBackspace = { onBackspace() },
                    onSpace = { onSpacePress() },
                    onEnter = { onEnterPress() },
                    onShiftTap = { onShiftTap() },
                    onGlobePress = { onGlobePress() },
                    onSymbolsPress = { onSymbolsPress() },
                    onBackToLetters = { onBackToLetters() },
                    onSymbolPageToggle = { onSymbolPageToggle() },
                    onSuggestionClick = { onSuggestionTap(it) },
                    onNumberPress = { char -> onDirectCommit(char) },
                    onPunctuationPress = { char -> onPunctuationPress(char) }
                )
            }
        }

        // Wire lifecycle trees for Compose
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        return composeView
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
        buffer = ""
        suggestions.clear()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        buffer = ""
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

    private fun onBangluKeyPress(char: Char) {
        buffer += char
        Log.d(TAG, "onBangluKeyPress: char='$char', buffer='$buffer'")

        // Auto-unshift after typing a letter (unless caps lock)
        if (shiftState.value == ShiftState.ON && char.isLetter()) {
            shiftState.value = ShiftState.OFF
        }

        val ic = currentInputConnection ?: return
        if (currentInputEditorInfo?.inputType == InputType.TYPE_NULL) {
            ic.commitText(char.toString(), 1)
            return
        }

        val result = SmartEngineAdapter.convertWord(buffer)
        Log.d(TAG, "convert: '$buffer' -> '${result.bengali}' (${result.confidence})")
        ic.setComposingText(result.bengali, 1)

        val newSuggestions = SmartEngineAdapter.getSuggestions(buffer, 8)
        suggestions.clear()
        suggestions.addAll(newSuggestions)
    }

    private fun onEnglishKeyPress(char: Char) {
        Log.d(TAG, "onEnglishKeyPress: char='$char'")

        // Auto-unshift after typing a letter (unless caps lock)
        if (shiftState.value == ShiftState.ON && char.isLetter()) {
            shiftState.value = ShiftState.OFF
        }

        val ic = currentInputConnection ?: return
        ic.commitText(char.toString(), 1)
    }

    private fun onDirectCommit(char: Char) {
        Log.d(TAG, "onDirectCommit: char='$char'")
        // Commit any pending Banglu buffer first
        commitPendingBuffer()

        val ic = currentInputConnection ?: return
        ic.commitText(char.toString(), 1)
    }

    private fun onPunctuationPress(char: Char) {
        Log.d(TAG, "onPunctuationPress: char='$char'")
        // Commit any pending Banglu buffer first, then commit the punctuation
        commitPendingBuffer()

        val ic = currentInputConnection ?: return
        ic.commitText(char.toString(), 1)
    }

    private fun onBackspace() {
        Log.d(TAG, "onBackspace: mode=${keyboardMode.value}, buffer='$buffer'")
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
                        val result = SmartEngineAdapter.convertWord(buffer)
                        ic.setComposingText(result.bengali, 1)
                        val newSuggestions = SmartEngineAdapter.getSuggestions(buffer, 8)
                        suggestions.clear()
                        suggestions.addAll(newSuggestions)
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
        Log.d(TAG, "onSpacePress: mode=${keyboardMode.value}, buffer='$buffer'")
        val ic = currentInputConnection ?: return

        when (keyboardMode.value) {
            KeyboardMode.BANGLU -> {
                if (buffer.isNotEmpty()) {
                    val result = SmartEngineAdapter.convertWord(buffer)
                    Log.d(TAG, "onSpacePress: committing '${result.bengali}'")
                    ic.commitText(result.bengali + " ", 1)
                    SmartEngineAdapter.onWordSelected(buffer, result.bengali)
                    buffer = ""
                    suggestions.clear()
                } else {
                    ic.commitText(" ", 1)
                }
            }
            else -> {
                ic.commitText(" ", 1)
            }
        }
    }

    private fun onEnterPress() {
        Log.d(TAG, "onEnterPress: mode=${keyboardMode.value}, buffer='$buffer'")
        val ic = currentInputConnection ?: return

        // Commit any pending buffer
        if (keyboardMode.value == KeyboardMode.BANGLU && buffer.isNotEmpty()) {
            val result = SmartEngineAdapter.convertWord(buffer)
            ic.commitText(result.bengali, 1)
            SmartEngineAdapter.onWordSelected(buffer, result.bengali)
            buffer = ""
            suggestions.clear()
        }

        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    private fun onSuggestionTap(suggestion: SmartSuggestion) {
        Log.d(TAG, "onSuggestionTap: '${suggestion.bengali}'")
        val ic = currentInputConnection ?: return
        ic.commitText(suggestion.bengali + " ", 1)
        SmartEngineAdapter.onWordSelected(buffer, suggestion.bengali)
        buffer = ""
        suggestions.clear()
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

        Log.d(TAG, "onShiftTap: shiftState=${shiftState.value}")
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
            // From symbols, toggle the underlying letter mode
            KeyboardMode.SYMBOLS_1, KeyboardMode.SYMBOLS_2 -> {
                letterModeBeforeSymbols = if (letterModeBeforeSymbols == KeyboardMode.BANGLU) {
                    KeyboardMode.ENGLISH
                } else {
                    KeyboardMode.BANGLU
                }
                // Stay in symbols mode, the label will update
                currentMode
            }
        }

        keyboardMode.value = newMode
        suggestions.clear()
        Log.d(TAG, "onGlobePress: mode=$newMode")
    }

    private fun onSymbolsPress() {
        // Commit any pending Banglu buffer
        commitPendingBuffer()

        // Remember current letter mode
        letterModeBeforeSymbols = keyboardMode.value
        keyboardMode.value = KeyboardMode.SYMBOLS_1
        Log.d(TAG, "onSymbolsPress: entering SYMBOLS_1")
    }

    private fun onBackToLetters() {
        keyboardMode.value = letterModeBeforeSymbols
        Log.d(TAG, "onBackToLetters: returning to $letterModeBeforeSymbols")
    }

    private fun onSymbolPageToggle() {
        keyboardMode.value = when (keyboardMode.value) {
            KeyboardMode.SYMBOLS_1 -> KeyboardMode.SYMBOLS_2
            KeyboardMode.SYMBOLS_2 -> KeyboardMode.SYMBOLS_1
            else -> keyboardMode.value
        }
        Log.d(TAG, "onSymbolPageToggle: mode=${keyboardMode.value}")
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun commitPendingBuffer() {
        if (keyboardMode.value == KeyboardMode.BANGLU && buffer.isNotEmpty()) {
            val ic = currentInputConnection ?: return
            val result = SmartEngineAdapter.convertWord(buffer)
            ic.commitText(result.bengali, 1)
            SmartEngineAdapter.onWordSelected(buffer, result.bengali)
            buffer = ""
            suggestions.clear()
        }
    }
}
