package com.banglu.keyboard

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.banglu.engine.SmartEngineAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Input Method Editor (IME) service for Bengali phonetic typing.
 *
 * Uses the KMP SmartEngine (via SmartEngineAdapter) to convert English phonetic
 * input to Bengali in real time. Manages a composing buffer that shows live
 * Bengali preview while the user types, committing on Space or suggestion tap.
 *
 * Lifecycle:
 * - onCreate: Initialize SmartEngine with seed dictionary (instant)
 * - onCreateInputView: Inflate keyboard + suggestion bar
 * - onStartInputView: Reset buffer for each new input field
 * - onDestroy: Cancel coroutines
 */
class BangluIMEService : InputMethodService() {

    private var keyboardView: BangluKeyboardView? = null
    private var suggestionBar: SuggestionBarView? = null
    private var buffer = ""
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "BangluIME"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Initializing SmartEngine...")
        // Initialize engine with seed dictionary (~4K words, instant)
        SmartEngineAdapter.initializeSync()
        Log.d(TAG, "onCreate: Seed dictionary loaded")

        // Load full 480K dictionary from SQLite in background
        serviceScope.launch {
            try {
                val storage = AndroidStorage(applicationContext)
                val loader = AndroidDictionaryLoader(applicationContext)
                Log.d(TAG, "onCreate: Loading full dictionary from SQLite...")
                SmartEngineAdapter.initialize(storage, loader)
                Log.d(TAG, "onCreate: Full dictionary loaded!")
            } catch (e: Exception) {
                Log.e(TAG, "onCreate: Failed to load full dictionary", e)
                // Engine works with seed data only — graceful degradation
            }
        }
    }

    override fun onCreateInputView(): View {
        val container = layoutInflater.inflate(R.layout.keyboard_container, null)

        suggestionBar = container.findViewById(R.id.suggestion_bar)
        suggestionBar?.onSuggestionClick = { suggestion ->
            onSuggestionTap(suggestion)
        }

        keyboardView = container.findViewById(R.id.keyboard_view)
        keyboardView?.onKeyPress = { key -> onKeyPress(key) }
        keyboardView?.onBackspace = { onBackspace() }
        keyboardView?.onSpace = { onSpacePress() }
        keyboardView?.onEnter = { onEnterPress() }
        keyboardView?.onShift = { keyboardView?.toggleShift() }

        return container
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        buffer = ""
        suggestionBar?.clear()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        buffer = ""
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    /**
     * Handle a character key press: append to buffer, update composing text and suggestions.
     */
    private fun onKeyPress(char: Char) {
        buffer += char
        Log.d(TAG, "onKeyPress: char='$char', buffer='$buffer'")

        val ic = currentInputConnection ?: run {
            Log.w(TAG, "onKeyPress: no InputConnection!")
            return
        }
        val editorInfo = currentInputEditorInfo

        // Fallback for editors that don't support composing text
        if (editorInfo?.inputType == InputType.TYPE_NULL) {
            ic.commitText(char.toString(), 1)
            return
        }

        // Convert phonetic buffer to Bengali and show as composing text
        val result = SmartEngineAdapter.convertWord(buffer)
        Log.d(TAG, "onKeyPress: '$buffer' → '${result.bengali}' (confidence=${result.confidence})")
        ic.setComposingText(result.bengali, 1)

        // Update suggestion bar
        val suggestions = SmartEngineAdapter.getSuggestions(buffer, 6)
        Log.d(TAG, "onKeyPress: ${suggestions.size} suggestions")
        suggestionBar?.showSuggestions(suggestions)
    }

    /**
     * Handle backspace: remove last character from buffer or delete from editor.
     */
    private fun onBackspace() {
        val ic = currentInputConnection ?: return
        if (buffer.isNotEmpty()) {
            buffer = buffer.dropLast(1)
            if (buffer.isEmpty()) {
                ic.finishComposingText()
                suggestionBar?.clear()
            } else {
                val result = SmartEngineAdapter.convertWord(buffer)
                ic.setComposingText(result.bengali, 1)
                val suggestions = SmartEngineAdapter.getSuggestions(buffer, 6)
                suggestionBar?.showSuggestions(suggestions)
            }
        } else {
            // No composing buffer — delete the character before the cursor
            ic.deleteSurroundingText(1, 0)
        }
    }

    /**
     * Handle space: commit current Bengali conversion + space, then reset buffer.
     */
    private fun onSpacePress() {
        Log.d(TAG, "onSpacePress: buffer='$buffer'")
        val ic = currentInputConnection ?: return
        if (buffer.isNotEmpty()) {
            val result = SmartEngineAdapter.convertWord(buffer)
            Log.d(TAG, "onSpacePress: committing '${result.bengali}'")
            ic.finishComposingText()
            ic.commitText(result.bengali + " ", 1)
            SmartEngineAdapter.onWordSelected(buffer, result.bengali)
            buffer = ""
            suggestionBar?.clear()
        } else {
            ic.commitText(" ", 1)
        }
    }

    /**
     * Handle enter: commit current Bengali conversion, then send Enter key event.
     */
    private fun onEnterPress() {
        val ic = currentInputConnection ?: return
        if (buffer.isNotEmpty()) {
            val result = SmartEngineAdapter.convertWord(buffer)
            ic.finishComposingText()
            ic.commitText(result.bengali, 1)
            SmartEngineAdapter.onWordSelected(buffer, result.bengali)
            buffer = ""
            suggestionBar?.clear()
        }
        // Send the Enter key event to the target app
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    /**
     * Handle suggestion tap: commit the selected Bengali word and reset buffer.
     */
    private fun onSuggestionTap(suggestion: com.banglu.engine.types.SmartSuggestion) {
        val ic = currentInputConnection ?: return
        ic.finishComposingText()
        ic.commitText(suggestion.bengali, 1)
        SmartEngineAdapter.onWordSelected(buffer, suggestion.bengali)
        buffer = ""
        suggestionBar?.clear()
    }
}
