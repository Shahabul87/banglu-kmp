package com.banglu.keyboard

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.banglu.engine.SmartEngineAdapter
import com.banglu.engine.types.SmartSuggestion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
        val container = layoutInflater.inflate(R.layout.keyboard_container, null)

        suggestionBar = container.findViewById(R.id.suggestion_bar)
        suggestionBar?.onSuggestionClick = { suggestion -> onSuggestionTap(suggestion) }

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

    private fun onKeyPress(char: Char) {
        buffer += char
        Log.d(TAG, "onKeyPress: char='$char', buffer='$buffer'")

        val ic = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo

        if (editorInfo?.inputType == InputType.TYPE_NULL) {
            ic.commitText(char.toString(), 1)
            return
        }

        val result = SmartEngineAdapter.convertWord(buffer)
        Log.d(TAG, "convert: '$buffer' → '${result.bengali}' (${result.confidence})")
        ic.setComposingText(result.bengali, 1)

        val suggestions = SmartEngineAdapter.getSuggestions(buffer, 6)
        Log.d(TAG, "suggestions: ${suggestions.size} → ${suggestions.joinToString { it.bengali }}")
        suggestionBar?.showSuggestions(suggestions)
    }

    private fun onBackspace() {
        Log.d(TAG, "onBackspace: buffer='$buffer'")
        val ic = currentInputConnection ?: return

        if (buffer.isNotEmpty()) {
            buffer = buffer.dropLast(1)
            if (buffer.isEmpty()) {
                ic.setComposingText("", 0)
                ic.finishComposingText()
                suggestionBar?.clear()
            } else {
                val result = SmartEngineAdapter.convertWord(buffer)
                ic.setComposingText(result.bengali, 1)
                suggestionBar?.showSuggestions(SmartEngineAdapter.getSuggestions(buffer, 6))
            }
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun onSpacePress() {
        Log.d(TAG, "onSpacePress: buffer='$buffer'")
        val ic = currentInputConnection ?: return

        if (buffer.isNotEmpty()) {
            val result = SmartEngineAdapter.convertWord(buffer)
            Log.d(TAG, "onSpacePress: committing '${result.bengali}'")
            // commitText replaces the current composing text — no need for finishComposingText
            ic.commitText(result.bengali + " ", 1)
            SmartEngineAdapter.onWordSelected(buffer, result.bengali)
            buffer = ""
            suggestionBar?.clear()
        } else {
            ic.commitText(" ", 1)
        }
    }

    private fun onEnterPress() {
        Log.d(TAG, "onEnterPress: buffer='$buffer'")
        val ic = currentInputConnection ?: return

        if (buffer.isNotEmpty()) {
            val result = SmartEngineAdapter.convertWord(buffer)
            ic.commitText(result.bengali, 1)
            SmartEngineAdapter.onWordSelected(buffer, result.bengali)
            buffer = ""
            suggestionBar?.clear()
        }
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    private fun onSuggestionTap(suggestion: SmartSuggestion) {
        Log.d(TAG, "onSuggestionTap: '${suggestion.bengali}'")
        val ic = currentInputConnection ?: return
        // commitText replaces the composing region automatically
        ic.commitText(suggestion.bengali + " ", 1)
        SmartEngineAdapter.onWordSelected(buffer, suggestion.bengali)
        buffer = ""
        suggestionBar?.clear()
    }
}
