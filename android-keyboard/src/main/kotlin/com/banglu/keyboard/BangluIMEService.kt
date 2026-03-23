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

    // State
    private var buffer = ""
    private val suggestions = mutableStateListOf<SmartSuggestion>()
    private val isShifted = mutableStateOf(false)
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
                    isShifted = isShifted.value,
                    onKeyPress = { char -> onKeyPress(char) },
                    onBackspace = { onBackspace() },
                    onSpace = { onSpacePress() },
                    onEnter = { onEnterPress() },
                    onShift = { isShifted.value = !isShifted.value },
                    onSuggestionClick = { onSuggestionTap(it) }
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

    private fun onKeyPress(char: Char) {
        buffer += char
        Log.d(TAG, "onKeyPress: char='$char', buffer='$buffer'")

        // Auto-unshift after typing a letter
        if (isShifted.value && char.isLetter()) {
            isShifted.value = false
        }

        val ic = currentInputConnection ?: return
        if (currentInputEditorInfo?.inputType == InputType.TYPE_NULL) {
            ic.commitText(char.toString(), 1)
            return
        }

        val result = SmartEngineAdapter.convertWord(buffer)
        Log.d(TAG, "convert: '$buffer' → '${result.bengali}' (${result.confidence})")
        ic.setComposingText(result.bengali, 1)

        val newSuggestions = SmartEngineAdapter.getSuggestions(buffer, 8)
        suggestions.clear()
        suggestions.addAll(newSuggestions)
    }

    private fun onBackspace() {
        Log.d(TAG, "onBackspace: buffer='$buffer'")
        val ic = currentInputConnection ?: return

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

    private fun onSpacePress() {
        Log.d(TAG, "onSpacePress: buffer='$buffer'")
        val ic = currentInputConnection ?: return

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

    private fun onEnterPress() {
        Log.d(TAG, "onEnterPress: buffer='$buffer'")
        val ic = currentInputConnection ?: return

        if (buffer.isNotEmpty()) {
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
}
