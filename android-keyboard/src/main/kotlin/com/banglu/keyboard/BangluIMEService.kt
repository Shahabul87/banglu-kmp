package com.banglu.keyboard

import android.Manifest
import android.app.ActivityManager
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Build
import android.os.StrictMode
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
    private val clipboardHistory = mutableStateListOf<String>()
    private val keyboardMode = mutableStateOf(KeyboardMode.BANGLU)
    private val shiftState = mutableStateOf(ShiftState.OFF)

    // Feature 3.1: Toolbar state
    private val isToolbarExpanded = mutableStateOf(false)
    private val voiceInputState = mutableStateOf(VoiceInputState.IDLE)
    private val voiceInputLevel = mutableStateOf(0f)
    private val emojiInitialCategory = mutableStateOf(0)

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

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        recordFailureEvent("coroutine_exception", throwable)
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + coroutineExceptionHandler)
    private val strictModePenaltyExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "BangluImePolicy").apply { isDaemon = true }
    }
    private var previousUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null
    private var suggestionJob: Job? = null
    private var commitConversionJob: Job? = null
    private var composingInput = ""
    private var composingResult: ConversionResult? = null
    private var cachedCommitInput = ""
    private var cachedCommitResult: ConversionResult? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var imeSessionVisible = false
    private var voiceCancelRequested = false
    private var voiceStopRequested = false
    private var voiceDictationActive = false
    private var currentVoiceSessionCommitLength = 0
    private var lastVoiceCommitLength = 0
    private var voiceHasLiveComposing = false
    private var voiceBaseText = ""
    private var voiceCommittedText = ""
    private var voiceCurrentPartial = ""
    private var voiceLiveCommittedPartial = ""
    private var voiceLiveCommitLength = 0
    private var voiceLastLivePartialUpdateAt = 0L
    private var voiceLastSpeechEndedAt = 0L
    private var voiceInsertionCursor: Int? = null
    private var voicePartialCommitJob: Job? = null
    private var voiceRestartJob: Job? = null
    private var voiceResetJob: Job? = null
    private var voiceLastAutoCommittedPartial: String? = null
    private var voicePreferOfflineForSession = false
    private var rawCommitInputMode = false
    private var privateInputMode = false
    private var lastCommittedTextLength = 0
    private var lastAutoCorrectOriginal = ""
    private var lastAutoCorrectReplacement = ""
    private var lastAutoCorrectPhonetic = ""
    private val recentEmojis = mutableStateListOf<String>()
    private var loadedDictionaryLiteMode: Boolean? = null
    private var recentEmojisLoaded = false
    private var clipboardHistoryLoaded = false
    private var sessionBangluKeyCount = 0
    private var sessionRawCommitKeyCount = 0
    private var sessionBangluWordCommitCount = 0
    private var sessionSuggestionTapCount = 0
    private var sessionPredictionTapCount = 0
    private var sessionPredictionImpressionCount = 0
    private var sessionPredictionChipCount = 0
    private var sessionAutoCorrectUndoCount = 0
    private var sessionEmojiCommitCount = 0
    private var sessionStickerCommitCount = 0
    private var sessionGifStickerCommitCount = 0
    private var sessionGifBinaryCommitCount = 0
    private var sessionGifFallbackCommitCount = 0
    private var sessionExpressionSearchCount = 0

    // ── Settings (read from SharedPreferences) ──────────────────────────
    private lateinit var prefs: SharedPreferences
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        reloadSettings()
        if (key == "auth_user_id" || key == "auth_email" || key == "subscription_plan" || key == "lite_mode") {
            reloadUserLearningAsync()
        }
    }
    val hapticEnabled = mutableStateOf(true)
    val soundEnabled = mutableStateOf(true)
    val suggestionsEnabled = mutableStateOf(true)
    val autoCapitalizeEnabled = mutableStateOf(true)
    val doubleSpacePeriodEnabled = mutableStateOf(true)
    val numberRowEnabled = mutableStateOf(true)
    val keyPreviewEnabled = mutableStateOf(true)
    val typingLearningEnabled = mutableStateOf(true)
    val personalDictionaryEnabled = mutableStateOf(true)
    val liteModeEnabled = mutableStateOf(false)
    val themeMode = mutableStateOf("dark")
    val keyboardHeightMode = mutableStateOf("normal")
    val keyboardFontSizeMode = mutableStateOf("large")

    companion object {
        private const val TAG = "BangluIME"
        private const val VOICE_LANGUAGE = "bn-BD"
        private const val VOICE_COMPLETE_SILENCE_MS = 3_500
        private const val VOICE_POSSIBLY_COMPLETE_SILENCE_MS = 1_800
        private const val VOICE_RESTART_DELAY_MS = 250L
        private const val VOICE_ERROR_RESTART_DELAY_MS = 650L
        private const val VOICE_COMMA_PAUSE_MS = 1_400L
        private const val VOICE_DARI_PAUSE_MS = 2_800L
        private const val VOICE_FINAL_PUNCTUATION_PAUSE_MS = 3_200L
        private const val VOICE_DELETE_SOURCE = "voice_delete"
        private const val PUNCTUATION_SOURCE = "gap_punctuation"
        private const val PREF_VOICE_DISCLOSURE_ACCEPTED = "voice_disclosure_accepted"
        private const val PREF_VOICE_TYPING_ENABLED = "voice_typing_enabled"
        private const val PREF_VOICE_OFFLINE_PREFERRED = "voice_offline_preferred"
        private const val PREF_RECENT_EMOJIS = "recent_emojis"
        private const val PREF_CLIPBOARD_HISTORY = "clipboard_history"
        private const val AUTOCORRECT_UNDO_SOURCE = "autocorrect_undo"
        private const val MAX_RECENT_EMOJIS = 40
        private const val MAX_CLIPBOARD_HISTORY = 12
        private const val MAX_CLIPBOARD_ITEM_CHARS = 1_000
        private const val GIF_MIME_TYPE = "image/gif"
        private const val IMAGE_MIME_WILDCARD = "image/*"
        private const val GIF_CACHE_DIR = "gif"
        private val GAP_PUNCTUATION_MARKS = listOf("\u0964", ",", "?", "!", "\u0983", ":")
    }

    /** Debug-only logging — stripped from release builds */
    private fun log(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    private fun recordImeEvent(event: String) {
        if (!::prefs.isInitialized) return
        val now = System.currentTimeMillis()
        val countKey = "diag_ime_${event}_count"
        prefs.edit()
            .putLong("diag_ime_last_${event}_at", now)
            .putInt(countKey, prefs.getInt(countKey, 0) + 1)
            .apply()
        log("ime: $event buffer=${buffer.length} voice=${voiceInputState.value}")
    }

    private fun recordFailureEvent(event: String, throwable: Throwable? = null) {
        if (!::prefs.isInitialized) return
        val now = System.currentTimeMillis()
        val countKey = "diag_failure_${event}_count"
        prefs.edit()
            .putLong("diag_failure_last_${event}_at", now)
            .putString("diag_failure_last_${event}_type", throwable?.javaClass?.simpleName.orEmpty())
            .putInt(countKey, prefs.getInt(countKey, 0) + 1)
            .apply()
        if (BuildConfig.DEBUG && throwable != null) {
            Log.e(TAG, "failure: $event", throwable)
        }
    }

    private fun recordImeCount(event: String, amount: Int) {
        if (!::prefs.isInitialized || amount <= 0) return
        val now = System.currentTimeMillis()
        val countKey = "diag_ime_${event}_count"
        prefs.edit()
            .putLong("diag_ime_last_${event}_at", now)
            .putInt(countKey, prefs.getInt(countKey, 0) + amount)
            .apply()
    }

    private fun flushImeSessionTelemetry() {
        recordImeCount("banglu_key", sessionBangluKeyCount)
        recordImeCount("raw_commit_key", sessionRawCommitKeyCount)
        recordImeCount("banglu_word_commit", sessionBangluWordCommitCount)
        recordImeCount("suggestion_tap", sessionSuggestionTapCount)
        recordImeCount("prediction_tap", sessionPredictionTapCount)
        recordImeCount("prediction_impression", sessionPredictionImpressionCount)
        recordImeCount("prediction_chip_shown", sessionPredictionChipCount)
        recordImeCount("autocorrect_undo", sessionAutoCorrectUndoCount)
        recordImeCount("emoji_commit", sessionEmojiCommitCount)
        recordImeCount("sticker_commit", sessionStickerCommitCount)
        recordImeCount("gif_sticker_commit", sessionGifStickerCommitCount)
        recordImeCount("gif_binary_commit", sessionGifBinaryCommitCount)
        recordImeCount("gif_fallback_commit", sessionGifFallbackCommitCount)
        recordImeCount("expression_search", sessionExpressionSearchCount)
        sessionBangluKeyCount = 0
        sessionRawCommitKeyCount = 0
        sessionBangluWordCommitCount = 0
        sessionSuggestionTapCount = 0
        sessionPredictionTapCount = 0
        sessionPredictionImpressionCount = 0
        sessionPredictionChipCount = 0
        sessionAutoCorrectUndoCount = 0
        sessionEmojiCommitCount = 0
        sessionStickerCommitCount = 0
        sessionGifStickerCommitCount = 0
        sessionGifBinaryCommitCount = 0
        sessionGifFallbackCommitCount = 0
        sessionExpressionSearchCount = 0
    }

    private fun recordLatencyEvent(event: String, elapsedMs: Long) {
        if (!::prefs.isInitialized) return
        val countKey = "diag_latency_${event}_count"
        val totalKey = "diag_latency_${event}_total_ms"
        val maxKey = "diag_latency_${event}_max_ms"
        val count = prefs.getInt(countKey, 0) + 1
        val total = prefs.getLong(totalKey, 0L) + elapsedMs
        val max = maxOf(prefs.getLong(maxKey, 0L), elapsedMs)
        prefs.edit()
            .putInt(countKey, count)
            .putLong(totalKey, total)
            .putLong(maxKey, max)
            .putLong("diag_latency_last_${event}_ms", elapsedMs)
            .apply()
        if (elapsedMs > 32L) log("latency: $event ${elapsedMs}ms")
    }

    private fun installCrashDiagnostics() {
        if (previousUncaughtExceptionHandler != null) return
        previousUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            recordFailureEvent("uncaught_${thread.name.take(24)}", throwable)
            previousUncaughtExceptionHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun installImeRuntimePolicy() {
        val threadPolicyBuilder = StrictMode.ThreadPolicy.Builder()
            .detectNetwork()
            .penaltyLog()
        val vmPolicyBuilder = StrictMode.VmPolicy.Builder()
            .detectActivityLeaks()
            .detectLeakedClosableObjects()
            .detectLeakedRegistrationObjects()
            .penaltyLog()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            threadPolicyBuilder.penaltyListener(strictModePenaltyExecutor) { violation ->
                recordFailureEvent("strict_thread_${violation.javaClass.simpleName.take(32)}", violation)
            }
            vmPolicyBuilder.penaltyListener(strictModePenaltyExecutor) { violation ->
                recordFailureEvent("strict_vm_${violation.javaClass.simpleName.take(32)}", violation)
            }
        }

        StrictMode.setThreadPolicy(threadPolicyBuilder.build())
        StrictMode.setVmPolicy(vmPolicyBuilder.build())
        recordImeEvent("runtime_policy_installed")
    }

    private fun configureInputSafety(info: EditorInfo?) {
        rawCommitInputMode = shouldUseRawCommitMode(info)
        privateInputMode = shouldDisablePersonalLearning(info)
        if (privateInputMode || rawCommitInputMode) {
            suggestions.clear()
            suggestionJob?.cancel()
            suggestionJob = null
        }
        log(
            "inputSafety: raw=$rawCommitInputMode private=$privateInputMode " +
                "inputType=${info?.inputType} imeOptions=${info?.imeOptions}"
        )
    }

    private fun shouldUseRawCommitMode(info: EditorInfo?): Boolean {
        val inputType = info?.inputType ?: return false
        val typeClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return inputType == InputType.TYPE_NULL ||
            typeClass == InputType.TYPE_CLASS_NUMBER ||
            typeClass == InputType.TYPE_CLASS_PHONE ||
            typeClass == InputType.TYPE_CLASS_DATETIME ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_URI ||
            isPasswordInput(inputType) ||
            isOneTimeCodeInput(info)
    }

    private fun shouldDisablePersonalLearning(info: EditorInfo?): Boolean {
        val inputType = info?.inputType ?: return false
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val noPersonalizedLearning =
            (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        return shouldUseRawCommitMode(info) ||
            noPersonalizedLearning ||
            isPasswordInput(inputType) ||
            isOneTimeCodeInput(info) ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_URI
    }

    private fun isPasswordInput(inputType: Int): Boolean {
        val typeClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return (typeClass == InputType.TYPE_CLASS_TEXT &&
            (
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                )
            ) ||
            (typeClass == InputType.TYPE_CLASS_NUMBER &&
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    private fun isOneTimeCodeInput(info: EditorInfo?): Boolean {
        val hint = info?.hintText?.toString()?.lowercase().orEmpty()
        val privateOptions = info?.privateImeOptions?.lowercase().orEmpty()
        return hint.contains("otp") ||
            hint.contains("one time") ||
            hint.contains("one-time") ||
            hint.contains("verification code") ||
            hint.contains("security code") ||
            hint.contains("code") ||
            privateOptions.contains("otp") ||
            privateOptions.contains("one_time_code") ||
            privateOptions.contains("sms_otp")
    }

    private fun suggestionsAllowedForCurrentInput(): Boolean {
        return imeSessionVisible &&
            suggestionsEnabled.value &&
            !privateInputMode &&
            !rawCommitInputMode
    }

    /** Safe conversion wrapper — never crashes the keyboard */
    private fun safeConvert(input: String): ConversionResult {
        val start = System.nanoTime()
        return try {
            SmartEngineAdapter.convertWord(input)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Conversion failed for '$input'", e)
            ConversionResult(input, 0.0, ResolutionSource.RULE, emptyList())
        } finally {
            recordLatencyEvent("convert", (System.nanoTime() - start) / 1_000_000)
        }
    }

    /** Conservative conversion for live composing text while the word is incomplete. */
    private fun safeComposingConvert(input: String): ConversionResult {
        val start = System.nanoTime()
        return try {
            SmartEngineAdapter.convertForComposing(input)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Composing conversion failed for '$input'", e)
            ConversionResult(input, 0.0, ResolutionSource.RULE, emptyList())
        } finally {
            recordLatencyEvent("compose", (System.nanoTime() - start) / 1_000_000)
        }
    }

    /** Safe suggestions wrapper */
    private fun safeSuggestions(input: String, limit: Int = 8): List<SmartSuggestion> {
        val start = System.nanoTime()
        return try {
            SmartEngineAdapter.getSuggestionsWithContext(input, listOf(lastCommittedBengali), limit)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Suggestions failed for '$input'", e)
            emptyList()
        } finally {
            recordLatencyEvent("suggestions", (System.nanoTime() - start) / 1_000_000)
        }
    }

    private fun safeConvertWithContext(input: String): ConversionResult {
        val start = System.nanoTime()
        return try {
            SmartEngineAdapter.convertWordWithContext(input, listOf(lastCommittedBengali))
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Context conversion failed for '$input'", e)
            safeConvert(input)
        } finally {
            recordLatencyEvent("context_convert", (System.nanoTime() - start) / 1_000_000)
        }
    }

    private fun resetShiftState() {
        shiftState.value = ShiftState.OFF
        lastShiftTapTime = 0L
    }

    private fun collapseTransientKeyboardUi() {
        if (keyboardMode.value == KeyboardMode.SYMBOLS_1 ||
            keyboardMode.value == KeyboardMode.SYMBOLS_2 ||
            keyboardMode.value == KeyboardMode.EMOJI ||
            keyboardMode.value == KeyboardMode.CLIPBOARD
        ) {
            keyboardMode.value = letterModeBeforeSymbols
        }
        isToolbarExpanded.value = false
        emojiInitialCategory.value = 0
    }

    private fun refreshSuggestionsAsync(input: String) {
        suggestionJob?.cancel()
        if (!suggestionsAllowedForCurrentInput() || input.isEmpty()) {
            suggestions.clear()
            return
        }

        val snapshot = input
        suggestionJob = serviceScope.launch {
            delay(70)
            if (keyboardMode.value != KeyboardMode.BANGLU || buffer != snapshot) return@launch
            val newSuggestions = withContext(Dispatchers.Default) {
                safeSuggestions(snapshot, 8)
            }
            if (keyboardMode.value == KeyboardMode.BANGLU && buffer == snapshot) {
                suggestions.clear()
                suggestions.addAll(newSuggestions)
            }
        }
    }

    private fun prepareCommitConversionAsync(input: String) {
        commitConversionJob?.cancel()
        if (input.isEmpty() || rawCommitInputMode) {
            cachedCommitInput = ""
            cachedCommitResult = null
            return
        }

        val snapshot = input
        commitConversionJob = serviceScope.launch {
            val result = withContext(Dispatchers.Default) {
                safeConvertWithContext(snapshot)
            }
            if (keyboardMode.value == KeyboardMode.BANGLU && buffer == snapshot) {
                cachedCommitInput = snapshot
                cachedCommitResult = result
            }
        }
    }

    private fun learnCommittedWordAsync(phonetic: String, bengali: String) {
        if (privateInputMode || rawCommitInputMode) return
        serviceScope.launch {
            withContext(Dispatchers.Default) {
                SmartEngineAdapter.onWordSelected(phonetic, bengali)
            }
        }
    }

    /** Reload settings from SharedPreferences */
    private fun reloadSettings() {
        val feedbackMode = prefs.getString("key_feedback_mode", null)
            ?: when {
                prefs.getBoolean("sound_feedback", true) && prefs.getBoolean("haptic_feedback", true) -> "both"
                prefs.getBoolean("sound_feedback", true) -> "sound"
                prefs.getBoolean("haptic_feedback", true) -> "vibration"
                else -> "silent"
            }
        hapticEnabled.value = feedbackMode == "both" || feedbackMode == "vibration"
        soundEnabled.value = feedbackMode == "both" || feedbackMode == "sound"
        suggestionsEnabled.value = prefs.getBoolean("suggestions", true)
        autoCapitalizeEnabled.value = prefs.getBoolean("auto_capitalize", true)
        doubleSpacePeriodEnabled.value = prefs.getBoolean("double_space_period", true)
        numberRowEnabled.value = prefs.getBoolean("number_row", true)
        keyPreviewEnabled.value = prefs.getBoolean("key_preview", true)
        typingLearningEnabled.value = prefs.getBoolean("typing_learning", true)
        personalDictionaryEnabled.value = prefs.getBoolean("personal_dictionary", true)
        liteModeEnabled.value = prefs.getBoolean("lite_mode", true)
        themeMode.value = prefs.getString("theme", "dark") ?: "dark"
        keyboardHeightMode.value = prefs.getString("keyboard_height", "normal") ?: "normal"
        keyboardFontSizeMode.value = prefs.getString("keyboard_font_size", "large") ?: "large"
        val defaultMode = prefs.getString("default_mode", "banglu") ?: "banglu"
        letterModeBeforeSymbols = if (defaultMode == "english") KeyboardMode.ENGLISH else KeyboardMode.BANGLU
        SmartEngineAdapter.configureLearning(
            enabled = typingLearningEnabled.value,
            personalDictionary = personalDictionaryEnabled.value
        )
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        prefs = getSharedPreferences("banglu_prefs", Context.MODE_PRIVATE)
        installCrashDiagnostics()
        installImeRuntimePolicy()
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        reloadSettings()
        SmartEngineAdapter.configurePersistenceScope(serviceScope)

        log("onCreate: Initializing SmartEngine...")
        SmartEngineAdapter.initializeSync()
        log("onCreate: Seed dictionary loaded")

        serviceScope.launch {
            try {
                val storage = AndroidStorage(applicationContext)
                val dictionaryLoader = createDictionaryLoader()
                log("onCreate: Loading learned words...")
                SmartEngineAdapter.initialize(storage, loader = dictionaryLoader)
                loadedDictionaryLiteMode = shouldUseLiteDictionary()
                log("onCreate: Learned words loaded")
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) Log.e(TAG, "onCreate: Failed to load learned words", t)
            }
        }
    }

    private fun reloadUserLearningAsync() {
        serviceScope.launch {
            try {
                val liteMode = shouldUseLiteDictionary()
                if (loadedDictionaryLiteMode != null && loadedDictionaryLiteMode != liteMode) {
                    SmartEngineAdapter.reset()
                    SmartEngineAdapter.configurePersistenceScope(serviceScope)
                    SmartEngineAdapter.initializeSync()
                    loadedDictionaryLiteMode = null
                }
                SmartEngineAdapter.initialize(
                    AndroidStorage(applicationContext),
                    loader = createDictionaryLoader()
                )
                loadedDictionaryLiteMode = liteMode
                log("reloadUserLearning: active profile preferences loaded")
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) Log.e(TAG, "reloadUserLearning: failed", t)
            }
        }
    }

    private fun createDictionaryLoader(): AndroidDictionaryLoader {
        val liteMode = shouldUseLiteDictionary()
        return AndroidDictionaryLoader(
            context = applicationContext,
            loadFullWordList = !liteMode,
            loadExtendedEntries = !liteMode,
            loadFrequencyScores = !liteMode,
            loadDisambiguationData = !liteMode,
            loadBigramData = !liteMode
        )
    }

    private fun shouldUseLiteDictionary(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRamDevice = activityManager?.isLowRamDevice == true
        val memoryClass = activityManager?.memoryClass ?: Int.MAX_VALUE
        return liteModeEnabled.value || lowRamDevice || memoryClass <= 256
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        reloadSettings()

        return try {
            val composeView = ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    BangluKeyboardLayout(
                        suggestionsProvider = { suggestions.toList() },
                        keyboardMode = keyboardMode.value,
                        shiftState = shiftState.value,
                        voiceInputState = voiceInputState.value,
                        voiceInputLevel = voiceInputLevel.value,
                        enterLabel = enterKeyLabel.value,
                        isToolbarExpanded = isToolbarExpanded.value,
                        hapticEnabled = hapticEnabled.value,
                        soundEnabled = soundEnabled.value,
                        suggestionsEnabled = suggestionsEnabled.value,
                        numberRowEnabled = numberRowEnabled.value,
                        keyPreviewEnabled = keyPreviewEnabled.value,
                        themePref = themeMode.value,
                        keyboardHeightMode = keyboardHeightMode.value,
                        keyboardFontSizeMode = keyboardFontSizeMode.value,
                        onKeyPress = { char -> onKeyPress(char) },
                        onTextInput = { text -> onTextInput(text) },
                        onBackspace = { onBackspace() },
                        onBackspaceRepeat = { count -> onBackspaceRepeat(count) },
                        onBackspaceWord = { onBackspaceWord() },
                        onSpace = { onSpacePress() },
                        onEnter = { onEnterPress() },
                        onShiftTap = { onShiftTap() },
                        onGlobePress = { onGlobePress() },
                        onSymbolsPress = { onSymbolsPress() },
                        onBackToLetters = { onBackToLetters() },
                        onSymbolPageToggle = { onSymbolPageToggle() },
                        onSuggestionClick = { onSuggestionTap(it) },
                        onNumberPress = { char -> onNumberPress(char) },
                        onPunctuationPress = { char -> onPunctuationPress(char) },
                        onCursorMove = { direction -> onCursorMove(direction) },
                        onDismiss = { requestHideSelf(0) },
                        onSettingsClick = { onSettingsClick() },
                        onToggleToolbar = { isToolbarExpanded.value = !isToolbarExpanded.value },
                        onClipboardOpen = { onClipboardOpen() },
                        onClipboardPaste = { text -> onClipboardPaste(text) },
                        onClipboardClear = { clearClipboardHistory() },
                        clipboardItemsProvider = { clipboardHistory.toList() },
                        onVoiceInput = { onVoiceInput() },
                        onVoiceStop = { stopVoiceInput(cancel = false) },
                        onVoiceCancel = { stopVoiceInput(cancel = true) },
                        onEmojiClick = { emoji -> onEmojiClick(emoji) },
                        onEmojiOpen = { onEmojiOpen() },
                        onStickerOpen = { onStickerOpen() },
                        onBackFromEmoji = { onBackFromEmoji() },
                        onEmojiSearch = { onEmojiSearch() },
                        emojiInitialCategory = emojiInitialCategory.value,
                        recentEmojisProvider = { recentEmojis.toList() }
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

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        imeSessionVisible = true
        recordImeEvent("start_input_view")
        reloadSettings()
        configureInputSafety(info)
        buffer = ""
        suggestions.clear()
        collapseTransientKeyboardUi()
        clearCommitCaches()
        clearAutoCorrectUndoState()
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

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd,
        )

        if (buffer.isEmpty()) return

        val composingSpanIsKnown = candidatesStart >= 0 && candidatesEnd >= candidatesStart
        val selectionInsideComposingSpan = composingSpanIsKnown &&
            newSelStart >= candidatesStart &&
            newSelEnd <= candidatesEnd

        if (!selectionInsideComposingSpan) {
            // The user moved the cursor or the app changed selection outside our active
            // composing word. Keeping the old phonetic buffer would inject the next key at
            // the wrong cursor position, so finalize the visible composition and reset IME state.
            currentInputConnection?.finishComposingText()
            buffer = ""
            suggestions.clear()
            clearCommitCaches()
            clearAutoCorrectUndoState()
            lastSpaceTime = 0L
            recordImeEvent("selection_left_composing_span")
        }
    }

    override fun onFinishInput() {
        cleanupImeSession("finish_input", cancelVoice = true)
        super.onFinishInput()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        cleanupImeSession("finish_input_view", cancelVoice = true)
        super.onFinishInputView(finishingInput)
    }

    override fun onWindowHidden() {
        cleanupImeSession("window_hidden", cancelVoice = true)
        super.onWindowHidden()
    }

    override fun onDestroy() {
        cleanupImeSession("destroy", cancelVoice = true)
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }
        previousUncaughtExceptionHandler?.let { Thread.setDefaultUncaughtExceptionHandler(it) }
        previousUncaughtExceptionHandler = null
        SmartEngineAdapter.configurePersistenceScope(null)
        releaseSpeechRecognizer()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        strictModePenaltyExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun cleanupImeSession(reason: String, cancelVoice: Boolean) {
        flushImeSessionTelemetry()
        recordImeEvent(reason)
        imeSessionVisible = false
        collapseTransientKeyboardUi()
        suggestionJob?.cancel()
        suggestionJob = null
        suggestions.clear()
        buffer = ""
        clearCommitCaches()
        lastSpaceTime = 0L
        resetShiftState()
        if (cancelVoice) {
            stopVoiceInput(cancel = true)
            releaseSpeechRecognizer()
        } else {
            voiceRestartJob?.cancel()
            voiceRestartJob = null
            voicePartialCommitJob?.cancel()
            voicePartialCommitJob = null
            voiceResetJob?.cancel()
            voiceResetJob = null
        }
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
            KeyboardMode.BANGLU -> {
                if (text.any { it in '\u0980'..'\u09FF' }) {
                    commitPendingBuffer()
                    currentInputConnection?.commitText(text, 1)
                    lastCommittedTextLength = text.length
                } else {
                    text.forEach { onBangluKeyPress(it) }
                }
            }
            KeyboardMode.ENGLISH -> {
                val ic = currentInputConnection ?: return
                ic.commitText(text, 1)
                lastCommittedTextLength = text.length
            }
            else -> text.forEach { onDirectCommit(it) }
        }
    }

    private fun onBangluKeyPress(char: Char) {
        clearVoiceUndoState()
        val ic = currentInputConnection ?: return

        if (rawCommitInputMode) {
            ic.commitText(char.toString(), 1)
            sessionRawCommitKeyCount++
            suggestions.clear()
            if (shiftState.value == ShiftState.ON && char.isLetter()) {
                shiftState.value = ShiftState.OFF
            }
            return
        }

        buffer += char
        sessionBangluKeyCount++

        // Auto-unshift after typing a letter (unless caps lock)
        if (shiftState.value == ShiftState.ON && char.isLetter()) {
            shiftState.value = ShiftState.OFF
        }

        val result = safeComposingConvert(buffer)
        composingInput = buffer
        composingResult = result
        ic.setComposingText(result.bengali, 1)

        refreshSuggestionsAsync(buffer)
        prepareCommitConversionAsync(buffer)
    }

    private fun onEnglishKeyPress(char: Char) {
        log("onEnglishKeyPress: char='$char'")
        clearVoiceUndoState()

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
        clearVoiceUndoState()
        // Commit any pending Banglu buffer first
        commitPendingBuffer()

        val ic = currentInputConnection ?: return
        ic.commitText(char.toString(), 1)
    }

    private fun onNumberPress(char: Char) {
        val output = if (keyboardMode.value == KeyboardMode.BANGLU) toBanglaDigit(char) else char
        onDirectCommit(output)
    }

    private fun toBanglaDigit(char: Char): Char {
        return when (char) {
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
            else -> char
        }
    }

    private fun onPunctuationPress(char: Char) {
        log("onPunctuationPress: char='$char'")
        clearVoiceUndoState()
        // Commit any pending Banglu buffer first, then commit the punctuation
        commitPendingBuffer()

        val ic = currentInputConnection ?: return
        val output = if (keyboardMode.value == KeyboardMode.BANGLU && char == '.') '\u0964' else char
        if (keyboardMode.value == KeyboardMode.BANGLU && isBanglaTightPunctuation(output)) {
            deleteSingleSpaceBeforeCursor(ic)
        }
        ic.commitText(output.toString(), 1)
        lastCommittedTextLength = 1
        showGapPunctuationSuggestions()
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
                        clearCommitCaches()
                    } else {
                        val result = safeComposingConvert(buffer)
                        composingInput = buffer
                        composingResult = result
                        ic.setComposingText(result.bengali, 1)
                        refreshSuggestionsAsync(buffer)
                        prepareCommitConversionAsync(buffer)
                    }
                } else {
                    deletePreviousGraphemes(ic)
                }
            }
            else -> {
                // Delete the previous user-visible character. This keeps emoji and Bengali
                // combining clusters intact instead of deleting one UTF-16 code unit.
                deletePreviousGraphemes(ic)
            }
        }
    }

    private fun onBackspaceRepeat(count: Int) {
        val safeCount = count.coerceIn(1, 48)
        val ic = currentInputConnection ?: return

        if (lastAutoCorrectOriginal.isNotEmpty()) {
            clearAutoCorrectUndoState()
        }

        if (keyboardMode.value == KeyboardMode.BANGLU && buffer.isNotEmpty()) {
            val dropCount = safeCount.coerceAtMost(buffer.length)
            buffer = buffer.dropLast(dropCount)
            if (buffer.isEmpty()) {
                ic.setComposingText("", 0)
                ic.finishComposingText()
                suggestions.clear()
                clearCommitCaches()
            } else {
                val result = safeComposingConvert(buffer)
                composingInput = buffer
                composingResult = result
                ic.setComposingText(result.bengali, 1)
                refreshSuggestionsAsync(buffer)
                prepareCommitConversionAsync(buffer)
            }
            return
        }

        ic.finishComposingText()
        suggestions.clear()
        deletePreviousGraphemes(ic, safeCount)
    }

    private fun deletePreviousGraphemes(ic: InputConnection, clusterCount: Int = 1): Boolean {
        val count = clusterCount.coerceAtLeast(1)
        val before = ic.getTextBeforeCursor(512, 0)?.toString().orEmpty()
        if (before.isEmpty()) return false

        var boundary = before.length
        repeat(count) {
            boundary = previousUserVisibleClusterBoundary(before, boundary)
            if (boundary <= 0) return@repeat
        }

        val deleteLength = (before.length - boundary).coerceAtLeast(1)
        ic.deleteSurroundingText(deleteLength, 0)
        lastCommittedTextLength = 0
        return true
    }

    private fun previousUserVisibleClusterBoundary(text: String, fromIndex: Int = text.length): Int {
        if (text.isEmpty() || fromIndex <= 0) return 0

        var index = fromIndex.coerceAtMost(text.length)
        index = Character.offsetByCodePoints(text, index, -1)

        while (index > 0) {
            val cp = text.codePointAt(index)
            if (!isTrailingClusterCodePoint(cp)) break
            index = Character.offsetByCodePoints(text, index, -1)
        }

        var start = index
        while (start > 0) {
            val prev = Character.codePointBefore(text, start)
            val current = text.codePointAt(start)
            val prevIsVirama = prev == 0x09CD
            val currentIsJoiner = current == 0x200D || current == 0x200C
            if (!prevIsVirama && !currentIsJoiner) break
            start = Character.offsetByCodePoints(text, start, -1)
            while (start > 0 && isTrailingClusterCodePoint(text.codePointAt(start))) {
                start = Character.offsetByCodePoints(text, start, -1)
            }
        }

        return start.coerceAtLeast(0)
    }

    private fun isTrailingClusterCodePoint(cp: Int): Boolean {
        return cp == 0x09BC || // nukta
            cp == 0x09CD || // virama
            cp == 0x200D ||
            cp == 0x200C ||
            cp == 0xFE0F ||
            cp in 0x0981..0x0983 ||
            cp in 0x09BE..0x09C4 ||
            cp in 0x09C7..0x09C8 ||
            cp in 0x09CB..0x09CC ||
            cp in 0x1F3FB..0x1F3FF
    }

    private fun onSpacePress() {
        log("onSpacePress: mode=${keyboardMode.value}, buffer='$buffer'")
        clearVoiceUndoState()
        val ic = currentInputConnection ?: return
        val now = System.currentTimeMillis()

        when (keyboardMode.value) {
            KeyboardMode.BANGLU -> {
                if (rawCommitInputMode) {
                    ic.commitText(" ", 1)
                } else if (buffer.isNotEmpty()) {
                    commitBufferedWordFast(ic, appendText = " ")
                } else {
                    // Feature 1.1: Double-space → Bengali danda + space
                    if (doubleSpacePeriodEnabled.value && now - lastSpaceTime < DOUBLE_SPACE_THRESHOLD_MS) {
                        ic.deleteSurroundingText(1, 0)
                        ic.commitText("\u0964 ", 1)  // Bengali danda (।) + space
                        lastCommittedTextLength = 2
                    } else {
                        val previousWord = lastCommittedBengali.ifEmpty { lastBengaliWordBeforeCursor(ic) }
                        ic.commitText(" ", 1)
                        lastCommittedTextLength = 1
                        if (previousWord.isNotEmpty()) {
                            updatePredictions(previousWord)
                        } else {
                            showGapPunctuationSuggestions()
                        }
                    }
                }
            }
            else -> {
                // Feature 1.1: Double-space → period + space (English/Symbol modes)
                if (doubleSpacePeriodEnabled.value && now - lastSpaceTime < DOUBLE_SPACE_THRESHOLD_MS) {
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                    lastCommittedTextLength = 2
                } else {
                    ic.commitText(" ", 1)
                    lastCommittedTextLength = 1
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
        clearVoiceUndoState()
        val ic = currentInputConnection ?: return

        // Commit any pending buffer
        if (keyboardMode.value == KeyboardMode.BANGLU && buffer.isNotEmpty() && !rawCommitInputMode) {
            commitBufferedWordFast(ic, appendText = "")
        }

        // Feature 1.3: Perform the appropriate IME action
        val editorInfo = currentInputEditorInfo
        val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_UNSPECIFIED

        if (action == EditorInfo.IME_ACTION_SEARCH ||
            action == EditorInfo.IME_ACTION_GO ||
            action == EditorInfo.IME_ACTION_NEXT ||
            action == EditorInfo.IME_ACTION_SEND ||
            action == EditorInfo.IME_ACTION_DONE
        ) {
            ic.performEditorAction(action)
        } else {
            // Default: insert newline
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
        clearEditorStateAfterAction(ic)
    }

    private fun clearEditorStateAfterAction(ic: InputConnection) {
        ic.finishComposingText()
        buffer = ""
        suggestions.clear()
        clearCommitCaches()
        clearAutoCorrectUndoState()
        clearVoiceUndoState()
        voiceInsertionCursor = currentCursorPosition()
        lastCommittedTextLength = 0
        lastSpaceTime = 0L
    }

    private fun onSuggestionTap(suggestion: SmartSuggestion) {
        log("onSuggestionTap: '${suggestion.bengali}' (tier=${suggestion.tier})")
        if (suggestion.source == VOICE_DELETE_SOURCE || suggestion.tier == "voice_action") {
            deleteLastVoiceCommit()
            return
        }
        if (suggestion.source == PUNCTUATION_SOURCE || suggestion.tier == "punctuation") {
            commitGapPunctuation(suggestion.bengali.firstOrNull() ?: return)
            return
        }
        if (suggestion.source == AUTOCORRECT_UNDO_SOURCE || suggestion.tier == "autocorrect_undo") {
            sessionAutoCorrectUndoCount++
            undoLastAutoCorrect()
            return
        }
        if (privateInputMode || rawCommitInputMode) return

        val ic = currentInputConnection ?: return

        if (buffer.isEmpty()) {
            // Feature 4.1: This is a next-word prediction — commit directly with space
            sessionSuggestionTapCount++
            sessionPredictionTapCount++
            ic.commitText(suggestion.bengali + " ", 1)
            lastCommittedTextLength = suggestion.bengali.length + 1
            updatePredictions(suggestion.bengali)
        } else {
            // This is a conversion suggestion
            sessionSuggestionTapCount++
            ic.commitText(suggestion.bengali + " ", 1)
            learnCommittedWordAsync(buffer, suggestion.bengali)
            lastCommittedTextLength = suggestion.bengali.length + 1
            buffer = ""
            suggestions.clear()
            clearCommitCaches()
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
            KeyboardMode.CLIPBOARD -> letterModeBeforeSymbols
        }

        keyboardMode.value = newMode
        resetShiftState()
        suggestions.clear()
        log("onGlobePress: mode=$newMode")
    }

    private fun onSymbolsPress() {
        // Commit any pending Banglu buffer
        commitPendingBuffer()

        // Remember current letter mode
        letterModeBeforeSymbols = keyboardMode.value
        keyboardMode.value = KeyboardMode.SYMBOLS_1
        resetShiftState()
        log("onSymbolsPress: entering SYMBOLS_1")
    }

    private fun onBackToLetters() {
        keyboardMode.value = letterModeBeforeSymbols
        resetShiftState()
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

    // ── Bengali Voice Typing ────────────────────────────────────────────────

    private fun onVoiceInput() {
        log("onVoiceInput: state=${voiceInputState.value}")
        if (privateInputMode || rawCommitInputMode) {
            log("onVoiceInput: blocked for private/raw input field")
            voiceInputState.value = VoiceInputState.UNAVAILABLE
            resetVoiceStateSoon()
            return
        }
        if (!prefs.getBoolean(PREF_VOICE_TYPING_ENABLED, true)) {
            log("onVoiceInput: disabled in settings")
            voiceInputState.value = VoiceInputState.UNAVAILABLE
            resetVoiceStateSoon()
            return
        }
        if (voiceInputState.value == VoiceInputState.LISTENING || voiceInputState.value == VoiceInputState.PROCESSING) {
            stopVoiceInput(cancel = false)
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            log("onVoiceInput: RECORD_AUDIO permission missing")
            voiceInputState.value = VoiceInputState.PERMISSION_REQUIRED
            val intent = Intent(this, VoicePermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            resetVoiceStateSoon()
            return
        }

        if (!prefs.getBoolean(PREF_VOICE_DISCLOSURE_ACCEPTED, false)) {
            log("onVoiceInput: voice disclosure not accepted")
            voiceInputState.value = VoiceInputState.PERMISSION_REQUIRED
            val intent = Intent(this, VoicePermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            resetVoiceStateSoon()
            return
        }

        voicePreferOfflineForSession = prefs.getBoolean(PREF_VOICE_OFFLINE_PREFERRED, false) || !isNetworkAvailable()
        if (voicePreferOfflineForSession) {
            log("onVoiceInput: no network, trying offline-preferred recognizer")
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            log("onVoiceInput: SpeechRecognizer unavailable")
            voiceInputState.value = VoiceInputState.UNAVAILABLE
            resetVoiceStateSoon()
            return
        }

        commitPendingBuffer()
        voiceInsertionCursor = currentCursorPosition()
        suggestions.clear()
        voiceInputState.value = VoiceInputState.PROCESSING
        voiceInputLevel.value = 0f
        voiceCancelRequested = false
        voiceStopRequested = false
        voiceDictationActive = true
        currentVoiceSessionCommitLength = 0
        voiceHasLiveComposing = false
        voiceBaseText = ""
        voiceCommittedText = ""
        voiceCurrentPartial = ""
        voiceLiveCommittedPartial = ""
        voiceLiveCommitLength = 0
        voiceLastLivePartialUpdateAt = 0L
        voiceLastSpeechEndedAt = 0L
        voicePartialCommitJob?.cancel()
        voicePartialCommitJob = null
        voiceLastAutoCommittedPartial = null

        startVoiceRecognition()
    }

    private fun startVoiceRecognition() {
        val recognizer = speechRecognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also {
            speechRecognizer = it
            it.setRecognitionListener(createVoiceRecognitionListener())
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, VOICE_LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, VOICE_LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, voicePreferOfflineForSession || !isNetworkAvailable())
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, VOICE_COMPLETE_SILENCE_MS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, VOICE_POSSIBLY_COMPLETE_SILENCE_MS)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "বাংলায় বলুন")
        }

        try {
            log("onVoiceInput: startListening $VOICE_LANGUAGE offline=${voicePreferOfflineForSession || !isNetworkAvailable()}")
            voiceInputState.value = VoiceInputState.PROCESSING
            voiceInputLevel.value = 0f
            recognizer.startListening(intent)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Voice typing failed to start", e)
            voiceDictationActive = false
            voiceInputState.value = VoiceInputState.ERROR
            resetVoiceStateSoon()
        }
    }

    private fun createVoiceRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                log("voice: ready")
                voiceInputState.value = VoiceInputState.LISTENING
            }

            override fun onBeginningOfSpeech() {
                log("voice: beginning")
                voicePartialCommitJob?.cancel()
                commitVoicePartialForMeasuredPause()
                voiceInputState.value = VoiceInputState.LISTENING
            }

            override fun onRmsChanged(rmsdB: Float) {
                voiceInputLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                log("voice: end")
                voiceLastSpeechEndedAt = System.currentTimeMillis()
                voiceInputState.value = if (voiceStopRequested) VoiceInputState.STOPPED else VoiceInputState.PROCESSING
                voiceInputLevel.value = 0f
                scheduleVoicePartialCommitAfterPause()
            }

            override fun onError(error: Int) {
                log("voice: error=$error")
                voicePartialCommitJob?.cancel()
                voiceInputLevel.value = 0f
                if (voiceStopRequested && !voiceCancelRequested) {
                    voiceDictationActive = false
                    voiceInputState.value = VoiceInputState.STOPPED
                    finishVoiceComposingText()
                    showVoiceDeleteAction()
                    return
                }
                if (
                    voiceDictationActive &&
                    !voiceStopRequested &&
                    !voiceCancelRequested &&
                    isRestartableVoiceError(error)
                ) {
                    val partial = voiceCurrentPartial.trim()
                    if (partial.isNotEmpty()) {
                        log("voice: committing live partial before restart error=$error text='$partial'")
                        voiceLastAutoCommittedPartial = partial
                        commitVoiceFinalText(partial, " ")
                    }
                    restartVoiceRecognitionSoon(afterError = true)
                    return
                }

                voiceDictationActive = false
                voiceInputState.value = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceInputState.PERMISSION_REQUIRED
                    SpeechRecognizer.ERROR_CLIENT -> VoiceInputState.IDLE
                    else -> VoiceInputState.ERROR
                }
                if (voiceInputState.value != VoiceInputState.IDLE) resetVoiceStateSoon()
            }

            override fun onResults(results: Bundle?) {
                voicePartialCommitJob?.cancel()
                if (voiceCancelRequested) {
                    log("voice: ignored canceled result")
                    suggestions.clear()
                    voiceInputLevel.value = 0f
                    voiceDictationActive = false
                    voiceHasLiveComposing = false
                    voiceCurrentPartial = ""
                    clearVoiceComposingText()
                    voiceInputState.value = VoiceInputState.IDLE
                    return
                }

                val phrases = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    .orEmpty()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                val best = chooseVoiceResult(phrases)?.let { stripAutoCommittedVoicePrefix(it) }
                if (best == null) {
                    log("voice: empty results")
                    voiceInputState.value = VoiceInputState.ERROR
                    resetVoiceStateSoon()
                    return
                }

                log("voice: result='$best'")
                if (best.isEmpty()) {
                    log("voice: result already committed by pause timer")
                    voiceLastAutoCommittedPartial = null
                    voiceCurrentPartial = ""
                    deleteVoiceLivePartial()
                    finishVoiceComposingText()
                } else {
                    commitVoiceFinalText(best, voiceFinalPunctuation())
                }
                voiceInputLevel.value = 0f
                if (voiceStopRequested) {
                    voiceDictationActive = false
                    voiceInputState.value = VoiceInputState.STOPPED
                    finalizeVoiceComposingText()
                    showVoiceDeleteAction()
                    return
                }
                if (voiceDictationActive && !voiceStopRequested) {
                    suggestions.clear()
                    finishVoiceComposingText()
                    restartVoiceRecognitionSoon()
                } else {
                    voiceDictationActive = false
                    voiceInputState.value = VoiceInputState.IDLE
                    finishVoiceComposingText()
                    showVoiceDeleteAction()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    ?.let { stripAutoCommittedVoicePrefix(it) }
                    .orEmpty()
                if (partial.isNotEmpty()) {
                    log("voice: partial='$partial'")
                    suggestions.clear()
                    voiceCurrentPartial = partial
                    renderVoicePartialIncrementally(partial)
                    if (voiceInputState.value == VoiceInputState.PROCESSING && voiceLastSpeechEndedAt > 0L) {
                        scheduleVoicePartialCommitAfterPause()
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
    }

    private fun stripAutoCommittedVoicePrefix(text: String): String {
        val committedPartial = voiceLastAutoCommittedPartial?.trim().orEmpty()
        val cleanText = text.trim()
        if (committedPartial.isEmpty()) return cleanText
        if (cleanText == committedPartial) return ""
        return if (cleanText.startsWith("$committedPartial ")) {
            cleanText.removePrefix(committedPartial).trimStart()
        } else {
            cleanText
        }
    }

    private fun isRestartableVoiceError(error: Int): Boolean {
        if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
            if (!voicePreferOfflineForSession) {
                voicePreferOfflineForSession = true
                log("voice: network error, retrying once with offline preference")
                return true
            }
            return false
        }
        return error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
            error == SpeechRecognizer.ERROR_CLIENT ||
            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
            error == SpeechRecognizer.ERROR_SERVER ||
            error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
            error == SpeechRecognizer.ERROR_NETWORK ||
            error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
    }

    private fun stopVoiceInput(cancel: Boolean) {
        voiceRestartJob?.cancel()
        voiceRestartJob = null
        voiceResetJob?.cancel()
        voiceResetJob = null
        val recognizer = speechRecognizer
        if (recognizer == null) {
            voiceInputLevel.value = 0f
            voiceInputState.value = if (cancel) VoiceInputState.IDLE else VoiceInputState.STOPPED
            voiceDictationActive = false
            voiceHasLiveComposing = false
            return
        }
        log("stopVoiceInput: cancel=$cancel state=${voiceInputState.value}")
        voicePartialCommitJob?.cancel()
        voicePartialCommitJob = null
        voiceCancelRequested = cancel
        voiceStopRequested = !cancel
        if (cancel) voiceDictationActive = false
        if (cancel) {
            deleteVoiceLivePartial()
            voiceCurrentPartial = ""
            clearVoiceComposingText()
            voiceHasLiveComposing = false
        }
        if (cancel) recognizer.cancel() else recognizer.stopListening()
        voiceInputLevel.value = 0f
        if (cancel) suggestions.clear()
        if (voiceInputState.value != VoiceInputState.IDLE) {
            voiceInputState.value = if (cancel) VoiceInputState.IDLE else VoiceInputState.STOPPED
        }
    }

    private fun releaseSpeechRecognizer() {
        val recognizer = speechRecognizer ?: return
        try {
            recognizer.cancel()
        } catch (_: Exception) {
            // Best-effort cleanup; recognizer may already be disconnected.
        }
        try {
            recognizer.destroy()
        } catch (_: Exception) {
            // Best-effort cleanup; recognizer may already be destroyed.
        }
        speechRecognizer = null
    }

    private fun commitVoiceSegment(segment: String) {
        val cleanSegment = segment.trim()
        if (cleanSegment.isEmpty()) return
        commitVoiceFinalText(cleanSegment, voiceFinalPunctuation())
    }

    private fun scheduleVoicePartialCommitAfterPause() {
        val snapshot = voiceCurrentPartial.trim()
        if (snapshot.isEmpty()) return
        voicePartialCommitJob?.cancel()
        val elapsed = (System.currentTimeMillis() - voiceLastSpeechEndedAt).coerceAtLeast(0L)
        val delayMs = (VOICE_FINAL_PUNCTUATION_PAUSE_MS - elapsed).coerceAtLeast(0L)
        voicePartialCommitJob = serviceScope.launch {
            delay(delayMs)
            if (
                imeSessionVisible &&
                voiceDictationActive &&
                !voiceStopRequested &&
                !voiceCancelRequested &&
                voiceCurrentPartial.trim() == snapshot
            ) {
                log("voice: pause commit terminal='$snapshot'")
                voiceLastAutoCommittedPartial = snapshot
                commitVoiceFinalText(snapshot, "\u0964")
                suggestions.clear()
            }
        }
    }

    private fun commitVoicePartialForMeasuredPause() {
        val partial = voiceCurrentPartial.trim()
        if (partial.isEmpty() || voiceLastSpeechEndedAt <= 0L) return
        val pauseMs = (System.currentTimeMillis() - voiceLastSpeechEndedAt).coerceAtLeast(0L)
        if (pauseMs < VOICE_COMMA_PAUSE_MS) return
        val mark = if (pauseMs >= VOICE_DARI_PAUSE_MS) "\u0964" else ","
        log("voice: measured pause commit mark='$mark' pauseMs=$pauseMs text='$partial'")
        voiceLastAutoCommittedPartial = partial
        commitVoiceFinalText(partial, mark)
        suggestions.clear()
    }

    private fun renderVoicePartialIncrementally(partial: String) {
        val cleanPartial = normalizeVoiceSegment(partial)
        if (cleanPartial.isEmpty()) return

        voiceCurrentPartial = cleanPartial
        commitVoiceLivePartialIncrementally(cleanPartial)
    }

    private fun commitVoiceLivePartialIncrementally(partial: String) {
        val previous = voiceLiveCommittedPartial
        when {
            previous.isEmpty() -> {
                commitVoiceTextAtInsertion(partial)
                voiceLiveCommittedPartial = partial
                voiceLiveCommitLength = partial.length
            }
            partial == previous || previous.startsWith(partial) -> {
                // Recognition engines sometimes send a shorter interim hypothesis.
                // Do not delete visible text for those temporary regressions.
                return
            }
            partial.startsWith(previous) -> {
                val suffix = partial.removePrefix(previous)
                commitVoiceTextAtInsertion(suffix)
                voiceLiveCommittedPartial = partial
                voiceLiveCommitLength = partial.length
            }
            replaceVoiceLiveText(partial) -> {
                voiceLiveCommittedPartial = partial
                voiceLiveCommitLength = partial.length
            }
            else -> {
                // If the cursor moved or the host app changed the text, avoid destructive
                // replacement. The final result will append only if it extends the live text.
                log("voice: skip non-prefix live revision previous='$previous' partial='$partial'")
            }
        }
        voiceHasLiveComposing = false
        voiceLastLivePartialUpdateAt = System.currentTimeMillis()
    }

    private fun commitVoiceTextAtInsertion(text: String) {
        if (text.isEmpty()) return
        val ic = currentInputConnection ?: return
        moveVoiceCursorToInsertionPoint(ic)
        ic.finishComposingText()
        ic.commitText(text, 1)
        ic.finishComposingText()
        voiceInsertionCursor = voiceInsertionCursor?.plus(text.length)
        currentVoiceSessionCommitLength += text.length
        lastVoiceCommitLength = currentVoiceSessionCommitLength
    }

    private fun replaceVoiceLiveText(replacement: String): Boolean {
        val ic = currentInputConnection ?: return false
        val previous = voiceLiveCommittedPartial
        if (previous.isEmpty()) return false

        moveVoiceCursorToInsertionPoint(ic)
        val before = ic.getTextBeforeCursor(previous.length, 0)?.toString().orEmpty()
        if (before != previous) return false

        ic.finishComposingText()
        ic.deleteSurroundingText(previous.length, 0)
        voiceInsertionCursor = voiceInsertionCursor?.minus(previous.length)?.coerceAtLeast(0)
        currentVoiceSessionCommitLength = (currentVoiceSessionCommitLength - previous.length).coerceAtLeast(0)
        ic.commitText(replacement, 1)
        ic.finishComposingText()
        voiceInsertionCursor = voiceInsertionCursor?.plus(replacement.length)
        currentVoiceSessionCommitLength += replacement.length
        lastVoiceCommitLength = currentVoiceSessionCommitLength
        return true
    }

    private fun deleteVoiceLivePartial() {
        val ic = currentInputConnection ?: return
        if (voiceHasLiveComposing) {
            ic.setComposingText("", 0)
            ic.finishComposingText()
            voiceHasLiveComposing = false
            voiceLiveCommittedPartial = ""
            voiceLiveCommitLength = 0
            voiceLastLivePartialUpdateAt = 0L
            return
        }
        voiceLiveCommittedPartial = ""
        voiceLiveCommitLength = 0
        voiceLastLivePartialUpdateAt = 0L
    }

    private fun commitVoiceFinalText(segment: String, punctuation: String) {
        val command = handleVoiceCommand(segment)
        if (command) return

        val cleanSegment = normalizeVoiceSegment(segment)
        if (cleanSegment.isEmpty()) return

        val committed = punctuateVoiceSegment(cleanSegment, punctuation)
        val ic = currentInputConnection ?: return

        val livePartial = voiceLiveCommittedPartial
        if (livePartial.isNotEmpty()) {
            when {
                committed.startsWith(livePartial) -> {
                    commitVoiceTextAtInsertion(committed.removePrefix(livePartial))
                }
                replaceVoiceLiveText(committed) -> {
                    // Replaced the visible interim text with the final transcript.
                }
                else -> {
                    log("voice: final does not match live partial, appending final safely")
                    commitVoiceTextAtInsertion(committed)
                }
            }
        } else {
            moveVoiceCursorToInsertionPoint(ic)
            deleteVoiceLivePartial()
            commitVoiceTextAtInsertion(committed)
        }

        voiceCommittedText += committed
        voiceCurrentPartial = ""
        voiceLiveCommittedPartial = ""
        voiceLiveCommitLength = 0
        voiceLastLivePartialUpdateAt = 0L
        voiceHasLiveComposing = false
        lastVoiceCommitLength = currentVoiceSessionCommitLength
    }

    private fun currentCursorPosition(): Int? {
        val ic = currentInputConnection ?: return null
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return null
        return extracted.selectionEnd.coerceAtLeast(0)
    }

    private fun moveVoiceCursorToInsertionPoint(ic: InputConnection) {
        val cursor = voiceInsertionCursor ?: return
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
        val textLength = extracted.text?.length ?: return
        val safeCursor = cursor.coerceIn(0, textLength)
        ic.setSelection(safeCursor, safeCursor)
    }

    private fun punctuateVoiceSegment(text: String, punctuation: String): String {
        val trimmed = text.trimEnd()
        if (trimmed.isEmpty()) return if (punctuation == " ") " " else "$punctuation "
        val hasEnding = trimmed.endsWith("।") ||
            trimmed.endsWith(".") ||
            trimmed.endsWith("?") ||
            trimmed.endsWith("!") ||
            trimmed.endsWith("\u0965")
        return when {
            hasEnding -> "$trimmed "
            punctuation == " " -> "$trimmed "
            else -> "$trimmed$punctuation "
        }
    }

    private fun chooseVoiceResult(phrases: List<String>): String? {
        if (phrases.isEmpty()) return null
        val normalized = phrases.map { normalizeVoiceSegment(it) }.filter { it.isNotEmpty() }
        val best = normalized.firstOrNull { it.any { ch -> isBengaliChar(ch) } } ?: normalized.firstOrNull()
        showVoiceAlternatives(normalized.distinct().dropWhile { it == best }.take(4))
        return best
    }

    private fun showVoiceAlternatives(alternatives: List<String>) {
        suggestions.clear()
        alternatives.forEach { alt ->
            suggestions.add(
                SmartSuggestion(
                    bengali = alt,
                    confidence = 0.82,
                    source = "voice_alternative",
                    phonetic = "",
                    tier = "voice_alternative"
                )
            )
        }
    }

    private fun voiceFinalPunctuation(): String {
        val pauseMs = if (voiceLastSpeechEndedAt > 0L) {
            (System.currentTimeMillis() - voiceLastSpeechEndedAt).coerceAtLeast(0L)
        } else {
            0L
        }
        return if (voiceStopRequested || pauseMs >= VOICE_FINAL_PUNCTUATION_PAUSE_MS) "\u0964" else " "
    }

    private fun normalizeVoiceSegment(segment: String): String {
        return segment
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace(" ।", "।")
            .replace(" ,", ",")
            .replace(" ?", "?")
            .replace(" !", "!")
            .let { normalizeVoiceLatinTokens(it) }
    }

    private fun normalizeVoiceLatinTokens(text: String): String {
        if (text.none { it in 'A'..'Z' || it in 'a'..'z' }) return text
        return text.split(Regex("(\\s+)"))
            .joinToString("") { token ->
                if (token.all { it.isWhitespace() }) token else normalizeVoiceToken(token)
            }
    }

    private fun normalizeVoiceToken(token: String): String {
        val core = token.trim { !it.isLetterOrDigit() }
        if (core.isEmpty() || core.any { isBengaliChar(it) } || core.any { it.isDigit() }) return token
        val converted = try { SmartEngineAdapter.convertWord(core.lowercase(Locale.ROOT)).bengali } catch (_: Exception) { core }
        if (converted == core || converted.any { it in 'A'..'Z' || it in 'a'..'z' }) return token
        return token.replace(core, converted)
    }

    private fun handleVoiceCommand(segment: String): Boolean {
        val clean = segment.trim().lowercase(Locale.ROOT)
        if (clean.isEmpty()) return false
        val ic = currentInputConnection ?: return false
        return when (clean) {
            "দাঁড়ি", "দাড়ি", "দারি", "full stop", "period" -> {
                ic.commitText("। ", 1)
                true
            }
            "কমা", "comma" -> {
                ic.commitText(", ", 1)
                true
            }
            "প্রশ্ন", "প্রশ্নবোধক", "question mark" -> {
                ic.commitText("? ", 1)
                true
            }
            "নতুন লাইন", "new line", "newline" -> {
                ic.commitText("\n", 1)
                true
            }
            "মুছে দাও", "ডিলিট", "delete", "delete last", "delete last word" -> {
                deletePreviousGraphemeCluster(ic)
                true
            }
            else -> false
        }
    }

    private fun deletePreviousGraphemeCluster(ic: InputConnection) {
        val before = ic.getTextBeforeCursor(16, 0)?.toString().orEmpty()
        if (before.isEmpty()) return
        val start = previousUserVisibleClusterBoundary(before)
        ic.deleteSurroundingText((before.length - start).coerceAtLeast(1), 0)
    }

    private fun isBengaliChar(ch: Char): Boolean = ch in '\u0980'..'\u09FF'

    private fun isNetworkAvailable(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun clearVoiceComposingText() {
        val ic = currentInputConnection ?: return
        if (voiceHasLiveComposing) {
            ic.setComposingText("", 0)
        }
        ic.finishComposingText()
        voiceHasLiveComposing = false
    }

    private fun finishVoiceComposingText() {
        currentInputConnection?.finishComposingText()
        voiceHasLiveComposing = false
    }

    private fun renderVoiceComposing() {
        val text = voiceCurrentPartial
        if (text.isEmpty()) {
            clearVoiceComposingText()
            voiceHasLiveComposing = false
            return
        }
        voiceHasLiveComposing = false
        voiceLiveCommittedPartial = ""
        voiceLiveCommitLength = 0
        voiceLastLivePartialUpdateAt = System.currentTimeMillis()
    }

    private fun finalizeVoiceComposingText() {
        if (!voiceHasLiveComposing) return
        currentInputConnection?.finishComposingText()
        voiceHasLiveComposing = false
        showVoiceDeleteAction()
    }

    private fun restartVoiceRecognitionSoon(afterError: Boolean = false) {
        voiceInputState.value = VoiceInputState.LISTENING
        voiceInputLevel.value = 0f
        voiceRestartJob?.cancel()
        voiceRestartJob = serviceScope.launch {
            delay(if (afterError) VOICE_ERROR_RESTART_DELAY_MS else VOICE_RESTART_DELAY_MS)
            if (
                imeSessionVisible &&
                voiceDictationActive &&
                !voiceStopRequested &&
                !voiceCancelRequested
            ) {
                if (afterError) {
                    releaseSpeechRecognizer()
                } else {
                    finishVoiceComposingText()
                }
                startVoiceRecognition()
            }
        }
    }

    private fun showVoiceDeleteAction() {
        if (lastVoiceCommitLength <= 0) return
        suggestions.clear()
        suggestions.add(
            SmartSuggestion(
                bengali = "ভয়েস মুছুন",
                confidence = 1.0,
                source = VOICE_DELETE_SOURCE,
                phonetic = "",
                tier = "voice_action"
            )
        )
    }

    private fun showGapPunctuationSuggestions() {
        if (!suggestionsAllowedForCurrentInput() || keyboardMode.value != KeyboardMode.BANGLU || buffer.isNotEmpty()) {
            return
        }
        if (suggestions.any { it.tier == "prediction" || it.tier == "autocorrect_undo" }) {
            appendGapPunctuationSuggestions()
            return
        }
        suggestionJob?.cancel()
        suggestions.clear()
        suggestions.addAll(gapPunctuationSuggestions())
    }

    private fun appendGapPunctuationSuggestions() {
        val existing = suggestions.map { it.bengali }.toSet()
        suggestions.addAll(gapPunctuationSuggestions().filter { it.bengali !in existing })
    }

    private fun gapPunctuationSuggestions(): List<SmartSuggestion> {
        return GAP_PUNCTUATION_MARKS.map { mark ->
            SmartSuggestion(
                bengali = mark,
                confidence = 1.0,
                source = PUNCTUATION_SOURCE,
                phonetic = "",
                tier = "punctuation"
            )
        }
    }

    private fun commitGapPunctuation(mark: Char) {
        val ic = currentInputConnection ?: return
        commitPendingBuffer()
        deleteSingleSpaceBeforeCursor(ic)
        val needsTrailingSpace = mark != '\n'
        ic.commitText(if (needsTrailingSpace) "$mark " else mark.toString(), 1)
        showGapPunctuationSuggestions()
    }

    private fun deleteSingleSpaceBeforeCursor(ic: InputConnection) {
        val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        if (before == " ") {
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun isBanglaTightPunctuation(mark: Char): Boolean {
        return mark == '\u0964' || mark == '\u0965' || mark == ',' || mark == '?' || mark == '!' || mark == ':' || mark == '\u0983'
    }

    private fun deleteLastVoiceCommit() {
        val ic = currentInputConnection ?: return
        if (lastVoiceCommitLength <= 0) return
        if (voiceHasLiveComposing) {
            clearVoiceComposingText()
            voiceCurrentPartial = ""
            voiceHasLiveComposing = false
        }
        deleteVoiceLivePartial()
        clearVoiceComposingText()
        ic.deleteSurroundingText(lastVoiceCommitLength, 0)
        voiceCurrentPartial = ""
        voiceLiveCommittedPartial = ""
        voiceLiveCommitLength = 0
        voiceLastLivePartialUpdateAt = 0L
        voiceCommittedText = ""
        voiceLastAutoCommittedPartial = null
        lastVoiceCommitLength = 0
        currentVoiceSessionCommitLength = 0
        suggestions.clear()
    }

    private fun clearVoiceUndoState() {
        if (lastVoiceCommitLength <= 0) return
        lastVoiceCommitLength = 0
        currentVoiceSessionCommitLength = 0
        voiceBaseText = ""
        voiceCommittedText = ""
        voiceCurrentPartial = ""
        voiceLiveCommittedPartial = ""
        voiceLiveCommitLength = 0
        voiceLastLivePartialUpdateAt = 0L
        voiceLastAutoCommittedPartial = null
        if (suggestions.any { it.source == VOICE_DELETE_SOURCE || it.tier == "voice_action" }) {
            suggestions.clear()
        }
    }

    private fun resetVoiceStateSoon() {
        voiceResetJob?.cancel()
        voiceResetJob = serviceScope.launch {
            delay(1800)
            if (
                imeSessionVisible && (
                    voiceInputState.value == VoiceInputState.ERROR ||
                        voiceInputState.value == VoiceInputState.PERMISSION_REQUIRED ||
                        voiceInputState.value == VoiceInputState.UNAVAILABLE
                    )
            ) {
                voiceInputState.value = VoiceInputState.IDLE
            }
        }
    }

    // ── Emoji Panel ──────────────────────────────────────────────────────────

    private fun onEmojiClick(emoji: String) {
        val ic = currentInputConnection ?: return
        commitPendingBuffer()
        rememberRecentEmoji(emoji)

        EmojiData.gifStickerFor(emoji)?.let { gifSticker ->
            sessionEmojiCommitCount++
            sessionStickerCommitCount++
            sessionGifStickerCommitCount++

            if (commitGifSticker(ic, gifSticker)) {
                lastCommittedTextLength = 0
                sessionGifBinaryCommitCount++
            } else {
                ic.commitText(gifSticker.fallbackText, 1)
                lastCommittedTextLength = gifSticker.fallbackText.length
                sessionGifFallbackCommitCount++
            }
            return
        }

        ic.commitText(emoji, 1)
        lastCommittedTextLength = emoji.length
        sessionEmojiCommitCount++
        if (EmojiData.isTextSticker(emoji)) sessionStickerCommitCount++
    }

    private fun commitGifSticker(
        ic: InputConnection,
        gifSticker: EmojiData.GifSticker
    ): Boolean {
        val editorInfo = currentInputEditorInfo ?: return false
        if (!supportsGifContent(editorInfo)) {
            recordImeEvent("gif_unsupported_host")
            return false
        }

        return try {
            val gifFile = cachedBundledGif(gifSticker)
            val contentUri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                gifFile
            )
            val description = ClipDescription(gifSticker.text, arrayOf(GIF_MIME_TYPE))
            val contentInfo = InputContentInfoCompat(contentUri, description, null)
            val targetPackage = editorInfo.packageName
            if (!targetPackage.isNullOrBlank()) {
                grantUriPermission(
                    targetPackage,
                    contentUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            val committed = InputConnectionCompat.commitContent(
                ic,
                editorInfo,
                contentInfo,
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                Bundle()
            )
            if (committed) {
                recordImeEvent("gif_binary_commit")
            } else {
                recordImeEvent("gif_rejected_host")
            }
            committed
        } catch (e: Exception) {
            recordFailureEvent("gif_commit", e)
            false
        }
    }

    private fun supportsGifContent(editorInfo: EditorInfo): Boolean {
        val supportedMimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo)
        val supported = supportedMimeTypes.any { mimeType ->
            mimeType.equals(GIF_MIME_TYPE, ignoreCase = true) ||
                mimeType.equals(IMAGE_MIME_WILDCARD, ignoreCase = true)
        }
        log(
            "gifContentSupport: supported=$supported " +
                "mimeTypes=${supportedMimeTypes.joinToString()} " +
                "package=${editorInfo.packageName} privateImeOptions=${editorInfo.privateImeOptions}"
        )
        return supported
    }

    private fun cachedBundledGif(gifSticker: EmojiData.GifSticker): File {
        val gifDir = File(cacheDir, GIF_CACHE_DIR).apply { mkdirs() }
        val gifFile = File(gifDir, "${gifSticker.assetName}_v7.gif")
        if (!gifFile.exists() || gifFile.length() == 0L) {
            val bytes = ReactionGifFactory.build(gifSticker.assetName)
            gifFile.outputStream().use { output -> output.write(bytes) }
        }
        return gifFile
    }

    private fun onEmojiSearch() {
        sessionExpressionSearchCount++
    }

    private fun onEmojiOpen() {
        ensureRecentEmojisLoaded()
        openEmojiPanel(initialCategory = 0)
    }

    private fun onStickerOpen() {
        ensureRecentEmojisLoaded()
        val stickerCategory = EmojiData.categories.indexOfFirst { it.name == "Stickers" }
            .takeIf { it >= 0 }
            ?: 1
        openEmojiPanel(initialCategory = stickerCategory)
    }

    private fun openEmojiPanel(initialCategory: Int) {
        commitPendingBuffer()
        // Remember which letter mode we came from (ignore if already in symbols/emoji)
        if (keyboardMode.value == KeyboardMode.BANGLU || keyboardMode.value == KeyboardMode.ENGLISH) {
            letterModeBeforeSymbols = keyboardMode.value
        }
        emojiInitialCategory.value = initialCategory
        keyboardMode.value = KeyboardMode.EMOJI
        resetShiftState()
    }

    private fun onClipboardOpen() {
        commitPendingBuffer()
        if (keyboardMode.value == KeyboardMode.BANGLU || keyboardMode.value == KeyboardMode.ENGLISH) {
            letterModeBeforeSymbols = keyboardMode.value
        }
        ensureClipboardHistoryLoaded()
        captureCurrentSystemClipboard()
        keyboardMode.value = KeyboardMode.CLIPBOARD
        resetShiftState()
    }

    private fun onClipboardPaste(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val ic = currentInputConnection ?: return
        commitPendingBuffer()
        ic.commitText(clean, 1)
        lastCommittedTextLength = clean.length
        rememberClipboardItem(clean)
        suggestions.clear()
        keyboardMode.value = letterModeBeforeSymbols
        resetShiftState()
    }

    private fun onBackFromEmoji() {
        keyboardMode.value = letterModeBeforeSymbols
        resetShiftState()
    }

    // ── Feature 4.1: Next-Word Predictions ─────────────────────────────────

    /**
     * After committing a Bengali word, show predicted next words in the suggestion bar.
     * Only shows predictions when the composing buffer is empty and keyboard is in Banglu mode.
     */
    private fun updatePredictions(committedBengali: String) {
        lastCommittedBengali = committedBengali
        if (suggestionsAllowedForCurrentInput() && buffer.isEmpty() && keyboardMode.value == KeyboardMode.BANGLU) {
            suggestionJob?.cancel()
            suggestions.clear()
            val snapshot = committedBengali
            suggestionJob = serviceScope.launch {
                val predictions = withContext(Dispatchers.Default) {
                    SmartEngineAdapter.getNextWordPredictions(snapshot, 4)
                }
                if (
                    suggestionsAllowedForCurrentInput() &&
                    buffer.isEmpty() &&
                    keyboardMode.value == KeyboardMode.BANGLU &&
                    lastCommittedBengali == snapshot
                ) {
                    suggestions.clear()
                    val undo = autoCorrectUndoSuggestion()
                    if (undo != null) suggestions.add(undo)
                    val predictionSuggestions = predictions
                        .map { pred ->
                            SmartSuggestion(
                                bengali = pred.bengali,
                                confidence = pred.confidence,
                                source = "prediction",
                                phonetic = "",
                                tier = "prediction"
                            )
                        }
                    suggestions.addAll(predictionSuggestions)
                    if (predictionSuggestions.isNotEmpty()) {
                        appendGapPunctuationSuggestions()
                        sessionPredictionImpressionCount++
                        sessionPredictionChipCount += predictionSuggestions.size
                    }
                    if (suggestions.isEmpty()) showGapPunctuationSuggestions()
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

    private fun lastBengaliWordBeforeCursor(ic: InputConnection): String {
        val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty().trimEnd()
        if (before.isEmpty()) return ""
        val token = before
            .split(Regex("\\s+"))
            .lastOrNull()
            .orEmpty()
            .trim { ch -> !isBengaliChar(ch) }
        return if (token.isNotEmpty() && token.any { isBengaliChar(it) }) token else ""
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
            clearCommitCaches()
            return
        }

        if (lastVoiceCommitLength > 0) {
            deleteLastVoiceCommit()
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
        if (moveCursorBySelection(ic, direction)) return
        moveCursorByKeyEvent(ic, direction)
    }

    private fun moveCursorBySelection(ic: InputConnection, direction: Int): Boolean {
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return false
        val text = extracted.text?.toString() ?: return false
        val current = extracted.selectionEnd.coerceIn(0, text.length)
        val target = if (direction > 0) {
            if (current >= text.length) return true
            Character.offsetByCodePoints(text, current, 1)
        } else {
            if (current <= 0) return true
            Character.offsetByCodePoints(text, current, -1)
        }.coerceIn(0, text.length)
        return ic.setSelection(target, target)
    }

    private fun moveCursorByKeyEvent(ic: InputConnection, direction: Int) {
        val keyCode = if (direction > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun commitBufferedWordFast(ic: InputConnection, appendText: String) {
        val phonetic = buffer
        if (phonetic.isEmpty()) return

        val visibleBeforeCommit = composingResult?.takeIf { composingInput == phonetic }?.bengali
        val result = when {
            cachedCommitInput == phonetic && cachedCommitResult != null -> cachedCommitResult!!
            else -> safeConvertWithContext(phonetic)
        }
        log("commitBufferedWordFast: committing '${result.bengali}' cached=${cachedCommitInput == phonetic}")
        ic.commitText(result.bengali + appendText, 1)
        lastCommittedTextLength = result.bengali.length + appendText.length
        sessionBangluWordCommitCount++
        maybeOfferAutoCorrectUndo(phonetic, visibleBeforeCommit, result.bengali, appendText)
        learnCommittedWordAsync(phonetic, result.bengali)
        buffer = ""
        suggestions.clear()
        clearCommitCaches()
        if (appendText.contains(" ")) {
            updatePredictions(result.bengali)
        } else {
            updatePredictions(result.bengali)
        }
    }

    private fun maybeOfferAutoCorrectUndo(
        phonetic: String,
        visibleBeforeCommit: String?,
        committed: String,
        appendText: String
    ) {
        val original = visibleBeforeCommit.orEmpty()
        if (original.isNotEmpty() && original != committed && appendText.contains(" ")) {
            lastAutoCorrectOriginal = original
            lastAutoCorrectReplacement = committed
            lastAutoCorrectPhonetic = phonetic
            recordImeEvent("autocorrect_offer")
        } else {
            clearAutoCorrectUndoState()
        }
    }

    private fun autoCorrectUndoSuggestion(): SmartSuggestion? {
        if (lastAutoCorrectOriginal.isEmpty()) return null
        return SmartSuggestion(
            bengali = "↶ $lastAutoCorrectOriginal",
            confidence = 1.0,
            source = AUTOCORRECT_UNDO_SOURCE,
            phonetic = lastAutoCorrectPhonetic,
            tier = "autocorrect_undo"
        )
    }

    private fun undoLastAutoCorrect() {
        val ic = currentInputConnection ?: return
        val original = lastAutoCorrectOriginal
        if (original.isEmpty() || lastCommittedTextLength <= 0) return
        ic.deleteSurroundingText(lastCommittedTextLength, 0)
        ic.commitText("$original ", 1)
        lastCommittedTextLength = original.length + 1
        lastCommittedBengali = original
        clearAutoCorrectUndoState()
        suggestions.clear()
        recordImeEvent("autocorrect_undo")
        showGapPunctuationSuggestions()
    }

    private fun clearAutoCorrectUndoState() {
        lastAutoCorrectOriginal = ""
        lastAutoCorrectReplacement = ""
        lastAutoCorrectPhonetic = ""
    }

    private fun loadRecentEmojis() {
        if (!::prefs.isInitialized) return
        recentEmojis.clear()
        prefs.getString(PREF_RECENT_EMOJIS, "")
            .orEmpty()
            .split("|")
            .filter { it.isNotBlank() }
            .take(MAX_RECENT_EMOJIS)
            .forEach { recentEmojis.add(it) }
        recentEmojisLoaded = true
    }

    private fun ensureRecentEmojisLoaded() {
        if (!recentEmojisLoaded) loadRecentEmojis()
    }

    private fun rememberRecentEmoji(emoji: String) {
        ensureRecentEmojisLoaded()
        recentEmojis.remove(emoji)
        recentEmojis.add(0, emoji)
        while (recentEmojis.size > MAX_RECENT_EMOJIS) recentEmojis.removeAt(recentEmojis.lastIndex)
        prefs.edit().putString(PREF_RECENT_EMOJIS, recentEmojis.joinToString("|")).apply()
    }

    private fun captureCurrentSystemClipboard() {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = manager.primaryClip ?: return
        if (clip.itemCount <= 0) return
        val text = clip.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (text.isNotBlank()) rememberClipboardItem(text)
    }

    private fun loadClipboardHistory() {
        if (!::prefs.isInitialized) return
        clipboardHistory.clear()
        prefs.getString(PREF_CLIPBOARD_HISTORY, "")
            .orEmpty()
            .split("|")
            .mapNotNull { encoded ->
                if (encoded.isBlank()) return@mapNotNull null
                runCatching {
                    String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
                }.getOrNull()
            }
            .filter { it.isNotBlank() }
            .take(MAX_CLIPBOARD_HISTORY)
            .forEach { clipboardHistory.add(it) }
        clipboardHistoryLoaded = true
    }

    private fun ensureClipboardHistoryLoaded() {
        if (!clipboardHistoryLoaded) loadClipboardHistory()
    }

    private fun rememberClipboardItem(text: String) {
        ensureClipboardHistoryLoaded()
        val clean = text.trim().take(MAX_CLIPBOARD_ITEM_CHARS)
        if (clean.isBlank()) return
        clipboardHistory.remove(clean)
        clipboardHistory.add(0, clean)
        while (clipboardHistory.size > MAX_CLIPBOARD_HISTORY) {
            clipboardHistory.removeAt(clipboardHistory.lastIndex)
        }
        persistClipboardHistory()
    }

    private fun clearClipboardHistory() {
        ensureClipboardHistoryLoaded()
        clipboardHistory.clear()
        persistClipboardHistory()
    }

    private fun persistClipboardHistory() {
        if (!::prefs.isInitialized) return
        val encoded = clipboardHistory.joinToString("|") {
            Base64.encodeToString(it.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
        prefs.edit().putString(PREF_CLIPBOARD_HISTORY, encoded).apply()
    }

    private fun clearCommitCaches() {
        commitConversionJob?.cancel()
        commitConversionJob = null
        composingInput = ""
        composingResult = null
        cachedCommitInput = ""
        cachedCommitResult = null
    }

    private fun commitPendingBuffer() {
        if (keyboardMode.value == KeyboardMode.BANGLU && buffer.isNotEmpty() && !rawCommitInputMode) {
            val ic = currentInputConnection ?: return
            commitBufferedWordFast(ic, appendText = "")
        } else if (buffer.isNotEmpty()) {
            buffer = ""
            suggestions.clear()
            clearCommitCaches()
        }
    }

}
