package com.banglu.keyboard

import android.Manifest
import android.app.ActivityManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Build
import android.os.StrictMode
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
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
import com.banglu.engine.glide.GlideDecoder
import com.banglu.engine.glide.GlideLexicon
import com.banglu.engine.glide.GlidePoint
import com.banglu.engine.types.ConversionResult
import com.banglu.engine.types.ResolutionSource
import com.banglu.engine.types.SmartSuggestion
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.font.createFontFamilyResolver
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
    private val clipboardHistory = mutableStateListOf<ClipboardHistoryPolicy.Entry>()
    private val keyboardMode = mutableStateOf(KeyboardMode.BANGLU)
    private val shiftState = mutableStateOf(ShiftState.OFF)

    // Feature 3.1: Toolbar state
    private val isToolbarExpanded = mutableStateOf(false)
    private val voiceInputState = mutableStateOf(VoiceInputState.IDLE)
    private val voiceInputLevel = mutableStateOf(0f)
    private val emojiInitialCategory = mutableStateOf(0)

    // S95: the user's CURRENT letter-mode choice (what transient layers
    // return to). Only the globe toggle and the new-app reset may change it —
    // settings reloads must NOT (that clobber was the EN snap-back bug).
    private var letterModeBeforeSymbols = KeyboardMode.BANGLU

    // S95: the settings default, consulted ONLY when entering a different app.
    private var defaultLetterMode = KeyboardMode.BANGLU

    // For double-tap shift detection
    private var lastShiftTapTime = 0L
    private val DOUBLE_TAP_THRESHOLD_MS = 300L

    // Feature 1.1: Double-space → period + space
    private var lastSpaceTime = 0L
    private val DOUBLE_SPACE_THRESHOLD_MS = 300L

    // Feature 4.1: Bengali next-word predictions
    private var lastCommittedBengali = ""

    // S20: the word before lastCommittedBengali — trigram context. Follows
    // the same never-reset convention; adjacency safety comes from the
    // evidence gating in the engine (promotion needs an OBSERVED triple).
    private var secondLastCommittedBengali = ""

    // Feature 1.3: Context-aware enter key label
    private val enterKeyLabel = mutableStateOf("\u21B5")

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        recordFailureEvent("coroutine_exception", throwable)
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + coroutineExceptionHandler)

    /** S75 (production audit): ONE serialized lane for every keystroke-path
     *  engine call (composing, suggestions, commit prep, reconcile,
     *  predictions, voice token refines) AND for learning mutations. A
     *  cancelled conversion job cannot stop its synchronous CPU/SQLite work
     *  mid-flight — on the shared Default pool, rapid typing left several
     *  stale conversions competing with the newest key for cores (uneven
     *  smoothness, CPU spikes, GC churn) and racing the engine's unguarded
     *  recursion flags. On a single lane at most ONE engine call runs at a
     *  time; jobs cancelled before their turn never run at all (natural
     *  conflation), and learning serializes with conversions. Engine
     *  REBUILDS deliberately stay off this lane — the adapter's engine swap
     *  is atomic, and queueing conversions behind a seconds-long rebuild
     *  would freeze typing. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val engineLane = Dispatchers.Default.limitedParallelism(1)
    private val strictModePenaltyExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "BangluImePolicy").apply { isDaemon = true }
    }
    private var previousUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null
    private var suggestionJob: Job? = null
    private var commitConversionJob: Job? = null
    private var composingJob: Job? = null
    private var composingInput = ""
    private var composingResult: ConversionResult? = null
    /** Exactly what setComposingText last put on screen (echo or refined). */
    private var composingVisibleText = ""
    private var cachedCommitInput = ""
    private var cachedCommitResult: ConversionResult? = null
    /** S32: bumped whenever the edit context changes (new field, cursor jump,
     *  session teardown) — a pending fast-commit reconcile from an older
     *  context must never touch the editor. */
    private var imeTextSessionToken = 0
    /** S34: commit learning is unsound until the dictionary load completes. */
    @Volatile
    private var dictionaryReadyForLearning = false
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
    /** S107: absolute caret positions our own voice writes are about to
     *  produce; onUpdateSelection consumes these against VoiceAnchorPolicy so
     *  only genuine USER cursor moves re-anchor dictation. */
    private val voiceExpectedSelections = ArrayDeque<Int>()
    private var voicePartialCommitJob: Job? = null
    private var voiceRestartJob: Job? = null
    /** S120: cumulative committed-transcript carry + cross-restart
     *  probation — the dedup law is pure and unit-pinned in
     *  VoiceCarryPolicy(Test); the service only delegates. */
    private val voiceCarry = VoiceCarryPolicy()
    private var voicePreferOfflineForSession = false
    /** S122: dictation language is captured per session — EN mode dictates
     *  English; restarts of the same dictation keep the language. */
    private var voiceSessionLanguage = VOICE_LANGUAGE
    private val voiceSessionEnglish: Boolean get() = voiceSessionLanguage.startsWith("en")
    /** S122: current field is a phone-class number field (numpad shows +*#). */
    private val numberPadPhone = mutableStateOf(false)
    /** S55: guards the ONE network-class (ERROR_NETWORK/_TIMEOUT/SERVER/
     *  _DISCONNECTED) offline retry per dictation session — see
     *  VoiceSessionPolicy. Reset at the top of every fresh onVoiceInput(). */
    private var voiceOfflineRetryUsed = false
    /** S73: generation stamp for recognizer instances — bumped on create and
     *  on release; listeners reject callbacks whose stamp is stale. */
    private var recognizerGeneration = 0
    /** S76 (audit): stopListening() was called and the terminal
     *  onResults/onError has not arrived yet. Android's contract forbids
     *  another startListening() on that instance until it does — a quick
     *  Retry now destroys the awaiting instance and starts fresh. */
    private var voiceAwaitingTerminal = false
    /** S73: deadline-based watchdog state (single ticker coroutine instead
     *  of cancel+relaunch per recognizer callback). 0 = disarmed. */
    @Volatile
    private var voiceWatchdogDeadlineAt = 0L
    /** S73: last mic-level UI write — RMS callbacks are throttled to ~15Hz. */
    private var lastRmsUiUpdateAt = 0L
    /** S69: one PLAIN same-mode retry for network-class errors before the
     *  offline ladder step. Reset with the other session counters. */
    private var voiceNetworkRetryUsed = false
    /** S69: the current offline preference was forced by the error ladder
     *  (not the user's setting) — lets a pack-missing error walk back online
     *  instead of dead-ending. */
    private var voiceOfflineForcedBySession = false

    /** S133: true once ANY speech reached this dictation session (beginning-
     *  of-speech, a partial, or a result). The silence cap on a session that
     *  heard nothing shows MIC_SILENT instead of stopping "gracefully". */
    private var voiceHeardSpeechThisSession = false
    /** S137: the pause commit ended this session on purpose (stopListening)
     *  — its terminal callback (empty results / NO_MATCH) is a clean end,
     *  not a failure: restart fresh, count nothing. */
    private var voiceSessionClosedAfterPause = false
    private var voiceIdleStopJob: Job? = null
    private var voiceLastSpeechBeganAt = 0L
    /** S137: onBeginningOfSpeech seen since the last non-empty hypothesis. */
    private var voiceSpeechRestartedSinceHypothesis = false
    /** S137 deferred punctuation: the last committed segment ends with a
     *  plain space and still owes its mark — a দাঁড়ি once the pause reaches
     *  VOICE_DARI_PAUSE_MS, a comma if speech resumes after VOICE_COMMA_PAUSE_MS,
     *  nothing on a user stop (S42). */
    private var voicePunctuationPending = false
    private var voicePunctuationJob: Job? = null
    private var voicePunctuationEpoch = 0
    /** S55 (F-ANDROID-006): fires if startListening() gets no
     *  RecognitionListener callback at all within the timeout — the
     *  recognizer is dead, not slow. Disarmed by every callback. */
    private var voiceWatchdogJob: Job? = null
    /** S55: bumped each time a NEW partial arrives; the async token-refine
     *  pass only applies its result if this is still the current generation
     *  (buffer-guard, same idiom as updateComposingAsync's `buffer == snapshot`). */
    private var voicePartialGeneration = 0
    private var voiceTokenRefineJob: Job? = null
    private var rawCommitInputMode = false

    /** S56: URI fields (browser omnibox) — conversion stays ON but দাঁড়ি
     *  behaviors (double-space danda, '.'→।) are suppressed so URLs stay
     *  typeable; personal learning stays off via shouldDisablePersonalLearning. */
    private var uriInputMode = false
    private var privateInputMode = false
    // S168 (audit P2-7): URI / no-personalized-learning fields keep the strip
    // but never learn (InputPrivacyPolicy).
    private var learningSuppressedInputMode = false
    private var lastCommittedTextLength = 0
    private var lastAutoCorrectOriginal = ""
    // S97: English corrections undo differently (teach-the-word semantics).
    private var lastAutoCorrectWasEnglish = false

    // S98: identity assist field classification (see configureInputSafety).
    private var emailInputMode = false
    private var sensitiveInputMode = false

    // S99: the EN-mode word prefix, shadow-tracked in memory so the touch
    // resolver has SYNC context on the keystroke path (an InputConnection
    // read there would be IPC on the hot path — S28 law). Cleared on every
    // separator/field/cursor event; staleness only ever costs a skipped
    // flip, never a wrong one, because the thresholds demand strong evidence.
    private var englishWordPrefix = ""
    // S168 (audit P1-1): the host's selection as last reported — a RANGE
    // selection changes what backspace must do (SelectionEditPolicy).
    private var editorSelStart = -1
    private var editorSelEnd = -1

    /**
     * S99: probabilistic touch targeting. A letter press near a key boundary
     * is resolved by the language model (BN roman buffer / EN word prefix as
     * context); center presses and raw/sensitive fields stay geometric.
     */
    private fun onLetterTouch(char: Char, left: Char?, right: Char?, xFraction: Float) {
        val resolved = if (rawCommitInputMode || privateInputMode) {
            char
        } else when (keyboardMode.value) {
            KeyboardMode.ENGLISH -> com.banglu.engine.touch.TouchTargetModel.resolve(
                englishWordPrefix, char, left, right, xFraction, english = true
            )
            KeyboardMode.BANGLU -> com.banglu.engine.touch.TouchTargetModel.resolve(
                buffer, char, left, right, xFraction, english = false
            )
            else -> char
        }
        onKeyPress(resolved)
    }

    // ── S94: referentially STABLE compose callbacks ──────────────────────
    // The old inline lambdas in setContent were recreated on every root
    // recomposition (they capture the service), which made every child
    // composable's args "changed" and defeated Compose skipping — one shift
    // auto-unshift re-executed the whole key tree. Fields are created once.
    private val kbSuggestionsProvider: () -> List<SmartSuggestion> = { suggestions.toList() }
    private val kbVoiceLevelProvider: () -> Float = { voiceInputLevel.value }
    /** S136 (F-003): in a password/OTP/no-learning field the panel offers
     *  ONLY the current system clip as a one-shot paste — stored history is
     *  neither shown nor grown there. Never persisted. */
    private var clipboardTransientItem: String? = null
    private val kbOnNoticeDismiss: () -> Unit = { dismissDictionaryNotice() }
    private val kbClipboardItemsProvider: () -> List<String> = {
        // S138 (F-003): stored history is shown ONLY in ordinary text fields
        // with history switched on; every private field (password, OTP,
        // email, URI, number/phone, no-learning) sees just the one-shot clip.
        if (clipboardFieldIsPrivate || !clipboardHistoryEnabled.value) listOfNotNull(clipboardTransientItem)
        else clipboardHistory.map { it.text }
    }
    private val kbRecentEmojisProvider: () -> List<String> = { recentEmojis.toList() }
    private val kbOnKeyPress: (Char) -> Unit = { onKeyPress(it) }
    private val kbOnLetterTouch: (Char, Char?, Char?, Float) -> Unit =
        { c, l, r, f -> onLetterTouch(c, l, r, f) }
    private val kbOnTextInput: (String) -> Unit = { onTextInput(it) }
    private val kbOnBackspace: () -> Unit = { onBackspace() }
    private val kbOnBackspaceRepeat: (Int) -> Unit = { onBackspaceRepeat(it) }
    private val kbOnBackspaceWord: () -> Unit = { onBackspaceWord() }
    private val kbOnSpace: () -> Unit = { onSpacePress() }
    private val kbOnEnter: () -> Unit = { onEnterPress() }
    private val kbOnShiftTap: () -> Unit = { onShiftTap() }
    private val kbOnGlobePress: () -> Unit = { onGlobePress() }
    private val kbOnSymbolsPress: () -> Unit = { onSymbolsPress() }
    private val kbOnBackToLetters: () -> Unit = { onBackToLetters() }
    private val kbOnSymbolPageToggle: () -> Unit = { onSymbolPageToggle() }
    private val kbOnSuggestionClick: (SmartSuggestion) -> Unit = { onSuggestionTap(it) }

    // ── S163: glide typing ─────────────────────────────────────────────────
    val glideTypingEnabled = mutableStateOf(true)
    @Volatile private var glideLexiconStoreField: GlideLexiconStore? = null
    @Volatile private var glideDecoderBn: GlideDecoder? = null
    @Volatile private var glideDecoderEn: GlideDecoder? = null
    /** The word the last glide committed — the strip's glide_alt chips swap it. */
    private var lastGlideCommit: String? = null
    private val kbGlideState: GlideUiState = GlideUiState(
        enabledProvider = {
            glideTypingEnabled.value && suggestionsEnabled.value &&
                !privateInputMode && !rawCommitInputMode && !sensitiveInputMode &&
                voiceInputState.value == VoiceInputState.IDLE &&
                (keyboardMode.value == KeyboardMode.BANGLU || keyboardMode.value == KeyboardMode.ENGLISH)
        },
        onComplete = { onGlideComplete(it) }
    )

    private fun glideLexiconStore(): GlideLexiconStore =
        glideLexiconStoreField ?: GlideLexiconStore(filesDir, liteModeEnabled.value)
            .also { glideLexiconStoreField = it }

    /**
     * S168 (audit P1-3): the first glide after a process start used to pay the
     * lexicon load (3-5 s measured on an S22) at decode time. Warm both
     * lexicons on IO once the dictionary is published — off the engine lane,
     * so typing latency is untouched. Memory pressure still drops them
     * (onTrimMemory); the next glide reloads from the disk cache.
     */
    private fun warmGlideLexicons() {
        if (!glideTypingEnabled.value) return
        serviceScope.launch(Dispatchers.IO) {
            try {
                val store = glideLexiconStore()
                store.banglaLexicon()
                store.englishLexicon()
            } catch (e: Throwable) {
                recordEngineFailure("glide_warm", e)
            }
        }
    }
    private val kbOnNumberPress: (Char) -> Unit = { onNumberPress(it) }
    private val kbOnPunctuationPress: (Char) -> Unit = { onPunctuationPress(it) }
    private val kbOnCursorMove: (Int) -> Unit = { onCursorMove(it) }
    private val kbOnDismiss: () -> Unit = { requestHideSelf(0) }
    private val kbOnSettingsClick: () -> Unit = { onSettingsClick() }
    private val kbOnToggleToolbar: () -> Unit = { isToolbarExpanded.value = !isToolbarExpanded.value }
    private val kbOnClipboardOpen: () -> Unit = { onClipboardOpen() }
    private val kbOnClipboardPaste: (String) -> Unit = { onClipboardPaste(it) }
    private val kbOnClipboardClear: () -> Unit = { clearClipboardHistory() }
    private val kbOnVoiceInput: () -> Unit = { onVoiceInput() }
    private val kbOnVoiceStop: () -> Unit = { stopVoiceInput(cancel = false) }
    private val kbOnVoiceCancel: () -> Unit = { stopVoiceInput(cancel = true) }
    private val kbOnEmojiClick: (String) -> Unit = { onEmojiClick(it) }
    private val kbOnEmojiOpen: () -> Unit = { onEmojiOpen() }
    private val kbOnStickerOpen: () -> Unit = { onStickerOpen() }
    private val kbOnBackFromEmoji: () -> Unit = { onBackFromEmoji() }
    private val kbOnEmojiSearch: () -> Unit = { onEmojiSearch() }
    private var lastAutoCorrectReplacement = ""
    private var lastAutoCorrectPhonetic = ""
    private val recentEmojis = mutableStateListOf<String>()
    private var loadedDictionaryLiteMode: Boolean? = null
    private var phoneticIndexStore: SqlitePhoneticIndexStore? = null
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
    private var sessionExpressionSearchCount = 0

    // ── Settings (read from SharedPreferences) ──────────────────────────
    private lateinit var prefs: SharedPreferences
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        // S108: diagnostics counters live in the same prefs file; a
        // session-end flush writes ~20 diag_* keys and each one used to
        // trigger a full reloadSettings() on main. Telemetry never changes a
        // setting.
        if (key != null && key.startsWith("diag_")) return@OnSharedPreferenceChangeListener
        reloadSettings()
        // S135 (F-001): the settings screen erases learned data through
        // BangluPrefsProvider in THIS process and stamps this key — the live
        // engine (dictionary-backed learned words, bigrams, preference maps)
        // is rebuilt clean here, so the keyboard forgets immediately.
        if (key == "auth_user_id" || key == "auth_email" || key == "subscription_plan" || key == "lite_mode" ||
            key == BangluPrefsProvider.KEY_LEARNING_ERASED_AT
        ) {
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
    /** S135 (F-004): dedicated saved-email identity switch (see settings). */
    val identityAssistEnabled = mutableStateOf(true)
    /** S138 (F-003): clipboard HISTORY is opt-in. Off (default) → the panel
     *  offers only the current system clip as a one-shot; nothing persists. */
    val clipboardHistoryEnabled = mutableStateOf(false)
    /** S136 (F-015): user-visible reason the full dictionary is not serving
     *  (low storage / copy failure); null when nothing is wrong. */
    val dictionaryNotice = mutableStateOf<String?>(null)
    private var dictionaryNoticeDismissed = false
    private var dictionaryRetryAtMs = 0L
    val liteModeEnabled = mutableStateOf(false)
    val themeMode = mutableStateOf("dark")
    val keyboardHeightMode = mutableStateOf("normal")
    val keyboardFontSizeMode = mutableStateOf("large")

    companion object {
        private const val TAG = "BangluIME"
        private const val VOICE_LANGUAGE = "bn-BD"
        private const val VOICE_COMPLETE_SILENCE_MS = 5_000
        private const val VOICE_POSSIBLY_COMPLETE_SILENCE_MS = 2_800
        private const val VOICE_RESTART_DELAY_MS = 250L
        private const val VOICE_ERROR_RESTART_DELAY_MS = 650L
        /** S69: terminal voice errors stay readable for 6s (was 1.8s — chips
         *  vanished before anyone could read them, so testers reported
         *  "voice does nothing" instead of the actual error text). */
        private const val VOICE_ERROR_CHIP_MS = 6000L
        /** S73: watchdog ticker period — one coroutine checking a deadline. */
        private const val VOICE_WATCHDOG_TICK_MS = 1000L
        /** S55 (F-ANDROID-006): if no RecognitionListener callback arrives
         *  within this window after startListening(), assume the recognizer
         *  is dead (battery-restricted service, stolen recognition slot, a
         *  disabled/crashing OEM stub) and reset instead of hanging. */
        private const val VOICE_WATCHDOG_TIMEOUT_MS = 6_000L

        /** S56 (F-ONDEVICE-001): rolling liveness deadline. On real hardware
         *  the Google recognizer can deliver ONE early callback (disarming the
         *  start watchdog) and then wedge silently forever — mic closed after
         *  75 ms while the chip still said "বাংলায় বলুন". Every callback
         *  re-arms this deadline; silence past it = wedged binding. Generous
         *  (12 s) because final-result computation on a slow network can
         *  legitimately pause callbacks for several seconds. */
        private const val VOICE_LIVENESS_TIMEOUT_MS = 12_000L

        /** Wedge recoveries per dictation session before giving up honestly. */
        private const val VOICE_MAX_WEDGE_RESTARTS = 2
        private const val VOICE_COMMA_PAUSE_MS = 1_400L
        private const val VOICE_DARI_PAUSE_MS = 2_800L
        private const val VOICE_FINAL_PUNCTUATION_PAUSE_MS = 3_200L
        /** S137: a session is ended this long after speech stops if no new
         *  speech has begun — BEFORE the recognizer's own ~2.8s endpoint,
         *  after which it degrades (late lumps, empty hypotheses). */
        private const val VOICE_IDLE_SESSION_END_MS = 1_500L
        private const val VOICE_DELETE_SOURCE = "voice_delete"
        private const val PUNCTUATION_SOURCE = "gap_punctuation"
        private const val PREF_VOICE_DISCLOSURE_ACCEPTED = "voice_disclosure_accepted"

        /** Consecutive no-speech listen cycles before dictation auto-stops. */
        // S137: with one session per utterance a silent session is ~5s, so
        // six of them ≈ 30s of tolerated silence before a graceful stop.
        private const val VOICE_MAX_FRUITLESS_RESTARTS = 6

        /** S55 (review follow-up): consecutive ERROR_CLIENT/ERROR_RECOGNIZER_BUSY
         *  restarts before giving up with BUSY_GIVEUP instead of looping. */
        private const val VOICE_MAX_BUSY_RESTARTS = 2

        /** In-app signal from [VoicePermissionActivity]: disclosure accepted —
         *  resume dictation without a second mic tap. */
        const val ACTION_VOICE_DISCLOSURE_ACCEPTED = "com.banglu.keyboard.VOICE_DISCLOSURE_ACCEPTED"
        private const val PREF_VOICE_TYPING_ENABLED = "voice_typing_enabled"
        /** S72: >0 forces lite dictionary for that many more cold starts. */
        private const val PREF_FORCED_LITE_LAUNCHES = "forced_lite_launches"
        private const val PREF_FORCED_LITE_VERSION = "forced_lite_version"
        /** S76: timestamp of the newest LOW_MEMORY exit already reacted to. */
        private const val PREF_LAST_LOW_MEMORY_EXIT_TS = "last_low_memory_exit_ts"
        private const val PREF_VOICE_OFFLINE_PREFERRED = "voice_offline_preferred"
        private const val PREF_RECENT_EMOJIS = "recent_emojis"
        /** S139: separate from the Boolean switch key — see PrefsMigrations. */
        private const val PREF_CLIPBOARD_HISTORY = PrefsMigrations.CLIPBOARD_ENTRIES_KEY
        private const val AUTOCORRECT_UNDO_SOURCE = "autocorrect_undo"

        /** S96: strip chips produced by the English typing suite. */
        private const val ENGLISH_WORD_SOURCE = "english_word"
        private const val EMOJI_WORD_SOURCE = "emoji_word"

        /** S98: identity-assist chips (email fills / domain completions). */
        private const val IDENTITY_FILL_SOURCE = "identity_fill"
        /** S138 (F-012): how long onDestroy waits for cancelled jobs to unwind. */
        private const val TEARDOWN_JOIN_TIMEOUT_MS = 300L
        /** S136 (F-015): minimum spacing between automatic dictionary retries. */
        private const val DICTIONARY_RETRY_INTERVAL_MS = 5L * 60L * 1000L
        private const val MAX_RECENT_EMOJIS = 40
        private val GAP_PUNCTUATION_MARKS = listOf("\u0964", ",", "?", "!", "\u0983", ":")
    }

    /** Debug-only logging — stripped from release builds */
    private fun log(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    private fun recordImeEvent(event: String) {
        if (!::prefs.isInitialized) return
        val now = System.currentTimeMillis()
        log("ime: $event buffer=${buffer.length} voice=${voiceInputState.value}")
        // S44 (audit finding): the first prefs access blocks on the file load —
        // keep even that one-time cost off the IME main thread.
        serviceScope.launch(Dispatchers.IO) {
            val countKey = "diag_ime_${event}_count"
            prefs.edit()
                .putLong("diag_ime_last_${event}_at", now)
                .putInt(countKey, prefs.getInt(countKey, 0) + 1)
                .apply()
        }
    }

    private fun recordFailureEvent(event: String, throwable: Throwable? = null, durable: Boolean = false) {
        if (!::prefs.isInitialized) return
        val now = System.currentTimeMillis()
        val countKey = "diag_failure_${event}_count"
        val editor = prefs.edit()
            .putLong("diag_failure_last_${event}_at", now)
            .putString("diag_failure_last_${event}_type", throwable?.javaClass?.simpleName.orEmpty())
            .putString("diag_failure_last_${event}_where", throwable?.let { stackFingerprint(it) }.orEmpty())
            .putInt(countKey, prefs.getInt(countKey, 0) + 1)
        // S136 (F-017): an uncaught exception kills the process right after
        // this call — apply() would be lost; commit() is the only record.
        if (durable) editor.commit() else editor.apply()
        if (BuildConfig.DEBUG && throwable != null) {
            Log.e(TAG, "failure: $event", throwable)
        }
    }

    /** S136 (F-017): the first Banglu frame of the stack (class:line) — a
     *  crash fingerprint that never contains typed text. */
    private fun stackFingerprint(t: Throwable): String {
        val frame = t.stackTrace.firstOrNull { it.className.startsWith("com.banglu") } ?: t.stackTrace.firstOrNull()
        return frame?.let { "${it.className.substringAfterLast('.')}:${it.lineNumber}" }.orEmpty().take(48)
    }

    /**
     * S136 (F-017): fold the OS's record of how our keyboard process last
     * died (crash, native crash, ANR, low memory) into the local Diagnostics
     * counters. Runs once per process start, off main; scans only exits
     * newer than the last scan, only for the keyboard process.
     */
    private fun recordProcessExitReasons() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !::prefs.isInitialized) return
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val since = prefs.getLong("diag_exit_scanned_at", 0L)
        val exits = runCatching { activityManager.getHistoricalProcessExitReasons(packageName, 0, 8) }
            .getOrNull() ?: return
        var newest = since
        val editor = prefs.edit()
        for (exit in exits) {
            if (exit.timestamp <= since || exit.processName != packageName) continue
            newest = maxOf(newest, exit.timestamp)
            val reason = when (exit.reason) {
                android.app.ApplicationExitInfo.REASON_CRASH -> "crash"
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash_native"
                android.app.ApplicationExitInfo.REASON_ANR -> "anr"
                android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
                android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive_resource"
                else -> continue
            }
            val countKey = "diag_exit_${reason}_count"
            editor.putInt(countKey, prefs.getInt(countKey, 0) + 1)
                .putLong("diag_exit_last_${reason}_at", exit.timestamp)
        }
        editor.putLong("diag_exit_scanned_at", newest).apply()
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
        sessionExpressionSearchCount = 0
    }

    // S14: latency telemetry accumulates in memory — the previous version did
    // four SharedPreferences writes per event (~12 per keystroke) on the main
    // thread. Flushed every 64 events and when the input view closes.
    private val latencyCounts = HashMap<String, Int>()
    private val latencyTotals = HashMap<String, Long>()
    private val latencyMaxes = HashMap<String, Long>()
    private val latencyLasts = HashMap<String, Long>()
    private var latencyPendingEvents = 0

    private fun recordLatencyEvent(event: String, elapsedMs: Long) {
        val shouldFlush: Boolean
        synchronized(latencyCounts) {
            latencyCounts[event] = (latencyCounts[event] ?: 0) + 1
            latencyTotals[event] = (latencyTotals[event] ?: 0L) + elapsedMs
            latencyMaxes[event] = maxOf(latencyMaxes[event] ?: 0L, elapsedMs)
            latencyLasts[event] = elapsedMs
            latencyPendingEvents++
            shouldFlush = latencyPendingEvents >= 64
        }
        if (elapsedMs > 32L) log("latency: $event ${elapsedMs}ms")
        if (shouldFlush) flushLatencyTelemetry()
    }

    private fun flushLatencyTelemetry() {
        if (!::prefs.isInitialized) return
        val counts: Map<String, Int>
        val totals: Map<String, Long>
        val maxes: Map<String, Long>
        val lasts: Map<String, Long>
        synchronized(latencyCounts) {
            if (latencyPendingEvents == 0) return
            counts = HashMap(latencyCounts)
            totals = HashMap(latencyTotals)
            maxes = HashMap(latencyMaxes)
            lasts = HashMap(latencyLasts)
            latencyCounts.clear()
            latencyTotals.clear()
            latencyMaxes.clear()
            latencyLasts.clear()
            latencyPendingEvents = 0
        }
        val editor = prefs.edit()
        for ((event, count) in counts) {
            val countKey = "diag_latency_${event}_count"
            val totalKey = "diag_latency_${event}_total_ms"
            val maxKey = "diag_latency_${event}_max_ms"
            editor.putInt(countKey, prefs.getInt(countKey, 0) + count)
            editor.putLong(totalKey, prefs.getLong(totalKey, 0L) + (totals[event] ?: 0L))
            editor.putLong(maxKey, maxOf(prefs.getLong(maxKey, 0L), maxes[event] ?: 0L))
            // S136 (F-017): the Diagnostics screen reads this key; it was
            // never written before (always showed 0ms).
            lasts[event]?.let { editor.putLong("diag_latency_last_${event}_ms", it) }
        }
        editor.apply()
    }

    private fun installCrashDiagnostics() {
        if (previousUncaughtExceptionHandler != null) return
        previousUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            recordFailureEvent("uncaught_${thread.name.take(24)}", throwable, durable = true)
            previousUncaughtExceptionHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun installImeRuntimePolicy() {
        val threadPolicyBuilder = StrictMode.ThreadPolicy.Builder()
            .detectNetwork()
            .penaltyLog()
        // S28: main-thread disk I/O is exactly the "keys stuck on budget
        // devices" bug class (per-keystroke SQLite lookups froze typing on
        // slow eMMC). Debug-only so telemetry/prefs noise never penalizes
        // release users.
        if (BuildConfig.DEBUG) {
            threadPolicyBuilder.detectDiskReads().detectDiskWrites()
        }
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
        uriInputMode = isUriInput(info)
        val privacy = InputPrivacyPolicy.resolve(
            sensitive = isSensitiveInput(info),
            noPersonalizedLearning =
                ((info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0,
            uri = isUriInput(info)
        )
        privateInputMode = !privacy.showSuggestions
        learningSuppressedInputMode = !privacy.learn
        // S98: identity assist runs in email fields and normal text, but the
        // SENSITIVE set — passwords, OTP, no-personalized-learning — is
        // excluded absolutely; nothing identity-related may fire there.
        val inputType = info?.inputType ?: 0
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        emailInputMode = (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
            (
                variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                )
        sensitiveInputMode = isPasswordInput(inputType) ||
            isOneTimeCodeInput(info) ||
            ((info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        if (privateInputMode || rawCommitInputMode) {
            suggestions.clear()
            suggestionJob?.cancel()
            composingJob?.cancel()
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
        // S56 (tester, Chrome omnibox 0x80011): TYPE_TEXT_VARIATION_URI is
        // deliberately NOT in this list anymore — the browser address bar is
        // where Bengali users search the most, and Gboard's transliteration
        // converts there too. URI fields instead get uriInputMode: conversion
        // ON, but no দাঁড়ি behaviors and (unchanged) no personal learning.
        return inputType == InputType.TYPE_NULL ||
            typeClass == InputType.TYPE_CLASS_NUMBER ||
            typeClass == InputType.TYPE_CLASS_PHONE ||
            typeClass == InputType.TYPE_CLASS_DATETIME ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            isPasswordInput(inputType) ||
            isOneTimeCodeInput(info)
    }

    private fun isUriInput(info: EditorInfo?): Boolean {
        val inputType = info?.inputType ?: return false
        return (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
            (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_URI
    }

    /** S168: the fully private set — no chips, no glide, no voice, no learning.
     *  (IME_FLAG_NO_PERSONALIZED_LEARNING and URI fields are handled by
     *  InputPrivacyPolicy as learning-only restrictions.) */
    private fun isSensitiveInput(info: EditorInfo?): Boolean {
        val inputType = info?.inputType ?: return false
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return shouldUseRawCommitMode(info) ||
            isPasswordInput(inputType) ||
            isOneTimeCodeInput(info) ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
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
        // S70: the bare hint.contains("code") overreached — any field hinted
        // "Promo code" / "Referral code" / "Postal code" silently entered
        // raw-commit mode (no Bengali conversion, no suggestions), which a
        // tester experiences as "the keyboard randomly types English". Only
        // OTP-shaped signals remain; pure NUMBER-class OTP fields are already
        // raw-commit via shouldUseRawCommitMode's input-class checks.
        return hint.contains("otp") ||
            hint.contains("one time") ||
            hint.contains("one-time") ||
            hint.contains("verification code") ||
            hint.contains("security code") ||
            hint.contains("sms code") ||
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
        } catch (e: Throwable) {
            recordEngineFailure("convert", e)
            if (BuildConfig.DEBUG) Log.e(TAG, "Conversion failed for '$input'", e)
            ConversionResult(input, 0.0, ResolutionSource.RULE, emptyList())
        } finally {
            recordLatencyEvent("convert", (System.nanoTime() - start) / 1_000_000)
        }
    }

    /** S77 (tester: "suggestions stop, only English letters show"): engine
     *  exceptions were swallowed by the safe wrappers with DEBUG-only logs —
     *  a failure STORM (the pre-S66 bigram/cache races) was completely
     *  invisible in release builds, which is why tester reports could only
     *  describe symptoms. Counts always; logs at most once a minute; never
     *  includes what the user typed. */
    private var engineFailureCount = 0
    private var lastEngineFailureLogAt = 0L
    private fun recordEngineFailure(where: String, e: Throwable) {
        engineFailureCount++
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastEngineFailureLogAt >= 60_000) {
            lastEngineFailureLogAt = now
            // S108: exception CLASS only — a library exception message could
            // in principle embed input-derived text, and this log survives
            // release builds.
            Log.w(
                TAG,
                "engine failure #$engineFailureCount at $where: ${e::class.java.simpleName}"
            )
            recordImeEvent("engine_failure_${where}_${e::class.java.simpleName}")
        }
    }

    /**
     * S28: per-keystroke composing update. The full composing conversion does
     * SQLite store lookups and dictionary-trie walks — running it on the UI
     * thread froze typing on slow-flash budget devices (Moto G10 Power "keys
     * stuck" report). The pressed key must appear INSTANTLY, so: a rule-only
     * echo (microseconds, zero I/O) goes into the editor synchronously, and
     * the refined conversion lands asynchronously, replacing the echo only if
     * the buffer is still the same. Rapid bursts self-coalesce via job cancel.
     */
    private fun updateComposingAsync(ic: InputConnection) {
        val snapshot = buffer
        val instant = try {
            SmartEngineAdapter.convertForInstantPreview(snapshot)
        } catch (e: Throwable) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Instant preview failed for '$snapshot'", e)
            snapshot
        }
        composingInput = snapshot
        composingResult = null
        composingVisibleText = instant
        ic.setComposingText(instant, 1)

        composingJob?.cancel()
        composingJob = serviceScope.launch {
            // S77: refine failed → keep the Bangla instant echo on screen.
            val result = withContext(engineLane) { safeComposingConvert(snapshot) } ?: return@launch
            if (keyboardMode.value == KeyboardMode.BANGLU && buffer == snapshot) {
                composingInput = snapshot
                composingResult = result
                composingVisibleText = result.bengali
                currentInputConnection?.setComposingText(result.bengali, 1)
            }
        }
    }

    /** Conservative conversion for live composing text while the word is incomplete. */
    /** S77: returns null on engine failure — the caller must then KEEP the
     *  rule-only instant preview. The old fallback returned the raw Latin
     *  input, and the async refine painted it over the Bangla echo: during
     *  the pre-S66 exception storms the editor showed "only English
     *  letters" while Space (whose fallback chain ends in the rule
     *  transliteration) still committed Bangla — the tester report exactly. */
    private fun safeComposingConvert(input: String): ConversionResult? {
        val start = System.nanoTime()
        return try {
            SmartEngineAdapter.convertForComposing(
                input, lastCommittedBengali, secondLastCommittedBengali
            )
        } catch (e: Throwable) {
            recordEngineFailure("compose", e)
            if (BuildConfig.DEBUG) Log.e(TAG, "Composing conversion failed for '$input'", e)
            null
        } finally {
            recordLatencyEvent("compose", (System.nanoTime() - start) / 1_000_000)
        }
    }

    /** Safe suggestions wrapper */
    private fun safeSuggestions(input: String, limit: Int = 8): List<SmartSuggestion> {
        val start = System.nanoTime()
        return try {
            SmartEngineAdapter.getSuggestionsWithContext(
                input, listOf(secondLastCommittedBengali, lastCommittedBengali), limit
            )
        } catch (e: Throwable) {
            recordEngineFailure("suggestions", e)
            if (BuildConfig.DEBUG) Log.e(TAG, "Suggestions failed for '$input'", e)
            emptyList()
        } finally {
            recordLatencyEvent("suggestions", (System.nanoTime() - start) / 1_000_000)
        }
    }

    /** S108: [prev2]/[prev1] default to the live fields, but the fast-commit
     *  reconcile passes a snapshot captured BEFORE updatePredictions runs —
     *  otherwise the authoritative conversion reranked against its own
     *  just-committed preview instead of the two true previous words, and a
     *  fast commit could resolve differently from a cached commit. */
    private fun safeConvertWithContext(
        input: String,
        prev2: String = secondLastCommittedBengali,
        prev1: String = lastCommittedBengali,
    ): ConversionResult {
        val start = System.nanoTime()
        return try {
            SmartEngineAdapter.convertWordWithContext(
                input, listOf(prev2, prev1)
            )
        } catch (e: Throwable) {
            recordEngineFailure("context_convert", e)
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
        keyboardMode.value = LanguageModePolicy.collapseTransient(keyboardMode.value, letterModeBeforeSymbols)
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
            val newSuggestions = withContext(engineLane) {
                safeSuggestions(snapshot, 8)
            }
            if (keyboardMode.value == KeyboardMode.BANGLU && buffer == snapshot) {
                suggestions.clear()
                // S162: the typed roman leads as a ghost chip (tap = keep the
                // English literal); the blue commit highlight moves to the
                // first real suggestion in the view.
                suggestions.addAll(TypedChipPolicy.decorateBanglaStrip(snapshot, newSuggestions))
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "sugg '$snapshot': ${newSuggestions.joinToString { "${it.bengali}/${it.source}" }}")
                }
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
            val result = withContext(engineLane) {
                safeConvertWithContext(snapshot)
            }
            if (keyboardMode.value == KeyboardMode.BANGLU && buffer == snapshot) {
                cachedCommitInput = snapshot
                cachedCommitResult = result
            }
        }
    }

    private fun learnCommittedWordAsync(
        phonetic: String,
        bengali: String,
        learnAsWord: Boolean = false,
        explicitChoice: Boolean = false
    ) {
        if (privateInputMode || rawCommitInputMode || learningSuppressedInputMode) return
        // S34: no commit learning while the dictionary is still loading. The
        // seed engine's raw fallbacks (kmon -> ক্মন) were being learned as
        // personal words during the few-second load window and then shadowed
        // the store's resolution on that device forever.
        if (!dictionaryReadyForLearning) return
        serviceScope.launch {
            // S75: learning mutates the dictionary — same lane as conversions.
            withContext(engineLane) {
                SmartEngineAdapter.onWordSelected(phonetic, bengali, learnAsWord, explicitChoice)
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
        identityAssistEnabled.value = prefs.getBoolean("identity_assist", false)
        clipboardHistoryEnabled.value = prefs.getBoolean(PrefsMigrations.CLIPBOARD_ENABLED_KEY, false)
        if (!clipboardHistoryEnabled.value) {
            // Switched off (or never on): nothing may remain in memory (now)
            // or on disk — the durable commit runs OFF the main thread
            // (reloadSettings is the preference listener, on Main; a commit
            // here was a StrictMode DiskWriteViolation) and is recorded if
            // it fails.
            if (clipboardHistory.isNotEmpty()) clipboardHistory.clear()
            if (prefs.contains(PREF_CLIPBOARD_HISTORY)) {
                serviceScope.launch(Dispatchers.IO) {
                    if (!prefs.edit().remove(PREF_CLIPBOARD_HISTORY).commit()) {
                        recordFailureEvent("clipboard_history_purge_failed")
                    }
                }
            }
        }
        // Full dictionary by default (fresh installs get predictions + context
        // reranking + strong gates). shouldUseLiteDictionary() still forces lite
        // on low-RAM devices, and every heavy loader degrades gracefully on OOM.
        liteModeEnabled.value = prefs.getBoolean("lite_mode", false)
        glideTypingEnabled.value = prefs.getBoolean("glide_typing_enabled", true)
        themeMode.value = prefs.getString("theme", "dark") ?: "dark"
        keyboardHeightMode.value = prefs.getString("keyboard_height", "normal") ?: "normal"
        keyboardFontSizeMode.value = prefs.getString("keyboard_font_size", "large") ?: "large"
        val defaultMode = prefs.getString("default_mode", "banglu") ?: "banglu"
        // S95: settings feed the DEFAULT only — never the user's live choice
        // (reloadSettings runs on every keyboard show; writing
        // letterModeBeforeSymbols here snapped deliberate EN sessions back to
        // Bengali on every transient-layer return).
        defaultLetterMode = if (defaultMode == "english") KeyboardMode.ENGLISH else KeyboardMode.BANGLU
        SmartEngineAdapter.configureLearning(
            enabled = typingLearningEnabled.value,
            personalDictionary = personalDictionaryEnabled.value,
            identityAssist = identityAssistEnabled.value
        )
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        prefs = getSharedPreferences("banglu_prefs", Context.MODE_PRIVATE)
        // S168 (audit P3-7): the strip's roman font (JetBrains Mono) loads
        // with Compose's Blocking strategy on first use — that was a
        // main-thread TTF read on the FIRST keystroke. Warm it off-thread.
        serviceScope.launch(Dispatchers.IO) {
            runCatching { createFontFamilyResolver(applicationContext).preload(RomanMono) }
        }
        // S139: MUST precede every typed read of the clipboard keys.
        PrefsMigrations.migrate(prefs)
        // S136 (F-013): non-Bengali clusters (emoji ZWJ families, flags,
        // keycaps) are segmented by the platform's ICU grapheme rules; the
        // Bengali rules in BackspaceResume are untouched.
        BackspaceResume.nonBengaliClusterBreaker = { text, from ->
            val iterator = android.icu.text.BreakIterator.getCharacterInstance()
            iterator.setText(text)
            val boundary = iterator.preceding(from)
            if (boundary == android.icu.text.BreakIterator.DONE) 0 else boundary
        }
        serviceScope.launch(Dispatchers.IO) { recordProcessExitReasons() }
        // S136 (F-004): identity assist is opt-in. A user who never chose
        // (pref absent — every pre-1.5.83 install) is set to OFF and any
        // addresses remembered under the old default are deleted, so nothing
        // is retained without a decision.
        if (!prefs.contains("identity_assist")) {
            serviceScope.launch(Dispatchers.IO) {
                // S138: the preference is written only AFTER the purge is
                // durable — a failed purge leaves it absent, so the next
                // start retries instead of silently keeping old addresses.
                val purged = runCatching { AndroidStorage(applicationContext).clearIdentityUserDataDurably() }
                    .getOrDefault(false)
                // S140: write the DEFAULT only if the user has not decided
                // meanwhile (they may have switched it on in Settings during
                // this purge) — a decision is never overwritten.
                if (purged && !prefs.contains("identity_assist")) {
                    prefs.edit().putBoolean("identity_assist", false).apply()
                }
            }
        } else if (!prefs.getBoolean("identity_assist", false)) {
            // S139 (F-004): INVARIANT "identity off ⇒ no saved addresses" is
            // re-established at every start — covers a switch-off whose
            // purge failed, and "reset all settings".
            serviceScope.launch(Dispatchers.IO) {
                runCatching {
                    val storage = AndroidStorage(applicationContext)
                    if (!storage.loadIdentityUserData().isNullOrBlank()) storage.clearIdentityUserDataDurably()
                }
            }
        }
        // S76: Android 14+ no longer delivers the imminent-kill trim levels
        // (deprecated, not sent since API 34), so onTrimMemory alone can
        // never degrade on modern devices. The reliable signal is the
        // PREVIOUS death: if the OS recently killed this process for
        // memory while in full mode, come up lite.
        // S101: a new build changes the memory envelope the forced-lite state
        // was measured against — stale counters from an older build must not
        // keep a capable device lite (the 1.5.58 guard bug left flagships
        // stuck in a lite loop; installing the fixed build heals immediately).
        if (MemoryPressurePolicy.shouldResetForcedLiteState(
                prefs.getInt(PREF_FORCED_LITE_VERSION, 0), BuildConfig.VERSION_CODE
            )
        ) {
            prefs.edit()
                .putInt(PREF_FORCED_LITE_VERSION, BuildConfig.VERSION_CODE)
                .putInt(PREF_FORCED_LITE_LAUNCHES, 0)
                .apply()
        }
        maybeArmForcedLiteFromExitHistory()
        // S72/S76: consume one forced-lite launch per cold start — after
        // FORCED_LITE_LAUNCHES starts, full mode is retried automatically.
        // The value is captured BEFORE the decrement (the old order made a
        // 5-launch grant behave as 4) and served from a field for the rest
        // of this launch.
        val forcedLite = prefs.getInt(PREF_FORCED_LITE_LAUNCHES, 0)
        forcedLiteActiveThisLaunch = forcedLite > 0
        if (forcedLite > 0) prefs.edit().putInt(PREF_FORCED_LITE_LAUNCHES, forcedLite - 1).apply()
        installCrashDiagnostics()
        installImeRuntimePolicy()
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            voiceDisclosureReceiver,
            android.content.IntentFilter(ACTION_VOICE_DISCLOSURE_ACCEPTED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        reloadSettings()
        SmartEngineAdapter.configurePersistenceScope(serviceScope)

        log("onCreate: Initializing SmartEngine...")
        initialEngineLoadJob = serviceScope.launch {
            try {
                // S29: the seed-dictionary build took ~650ms ON the main thread
                // here — on 2GB devices the IME process is killed and recreated
                // constantly, so EVERY keyboard open froze that long before the
                // view could even render (41 skipped frames measured). Built on
                // Default instead: the view shows immediately; a keystroke
                // landing inside the window echoes the raw buffer (instant
                // preview falls back) and the async composing refine corrects
                // it the moment seeds land.
                withContext(Dispatchers.Default) { SmartEngineAdapter.initializeSync() }
                log("onCreate: Seed dictionary loaded")
                val storage = withContext(Dispatchers.IO) { AndroidStorage(applicationContext) }
                val dictionaryLoader = createDictionaryLoader()
                log("onCreate: Loading learned words...")
                // Pre-initialize attach: if the db file already exists (warm start), attach
                // the store NOW so SmartEngine skips buildCorpusPhoneticIndex during initialize().
                val preAttached = attachPhoneticIndexStore(dictionaryLoader, preInitialize = true)
                SmartEngineAdapter.initialize(storage, loader = dictionaryLoader)
                // Post-initialize attach: only needed on first install when the db file was
                // copied by the loader's first run above (preAttached == false).
                if (!preAttached) attachPhoneticIndexStore(dictionaryLoader, preInitialize = false)
                loadedDictionaryLiteMode = shouldUseLiteDictionary()
                dictionaryReadyForLearning = true
                warmGlideLexicons()
                log("onCreate: Learned words loaded")
                degradeIfNoHeapHeadroom()
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) Log.e(TAG, "onCreate: Failed to load learned words", t)
            }
        }
    }

    /** S76: single-flight guard for engine rebuilds. Both flags are only
     *  touched on the Main-dispatched serviceScope, so plain vars suffice.
     *  A rebuild requested while one is running (pressure degrade racing a
     *  preference flip) sets the pending flag; the running rebuild loops
     *  once more with the LATEST profile instead of racing it. */
    private var engineRebuildInFlight = false
    private var engineRebuildPending = false
    /** S135: the cold-start load; a rebuild request (erase, profile flip)
     *  that lands while it is still running waits for it instead of racing
     *  two initialize() calls over the same storage snapshot. */
    private var initialEngineLoadJob: Job? = null

    private fun reloadUserLearningAsync() {
        // S44 (audit finding): the rebuild serves the seed engine for seconds —
        // learning must close for the whole window, not just the first boot.
        dictionaryReadyForLearning = false
        serviceScope.launch {
            initialEngineLoadJob?.join()
            if (engineRebuildInFlight) {
                engineRebuildPending = true
                return@launch
            }
            engineRebuildInFlight = true
            try {
                do {
                    engineRebuildPending = false
                    val liteMode = shouldUseLiteDictionary()
                    if (loadedDictionaryLiteMode != null && loadedDictionaryLiteMode != liteMode) {
                        SmartEngineAdapter.reset()
                        SmartEngineAdapter.configurePersistenceScope(serviceScope)
                        // S168 (audit P2-6): reset() re-enables every learning
                        // switch; re-apply the user's choices immediately, not
                        // on the next keyboard show.
                        SmartEngineAdapter.configureLearning(
                            enabled = typingLearningEnabled.value,
                            personalDictionary = personalDictionaryEnabled.value,
                            identityAssist = identityAssistEnabled.value
                        )
                        // S76 (audit): the seed build is ~650ms — never on Main.
                        withContext(Dispatchers.Default) { SmartEngineAdapter.initializeSync() }
                        loadedDictionaryLiteMode = null
                    }
                    val dictionaryLoader = createDictionaryLoader()
                    // Pre-initialize attach: skip corpus-index build when db already present.
                    val preAttached = attachPhoneticIndexStore(dictionaryLoader, preInitialize = true)
                    SmartEngineAdapter.initialize(
                        AndroidStorage(applicationContext),
                        loader = dictionaryLoader
                    )
                    if (!preAttached) attachPhoneticIndexStore(dictionaryLoader, preInitialize = false)
                    loadedDictionaryLiteMode = liteMode
                    dictionaryReadyForLearning = true
                    warmGlideLexicons()
                    log("reloadUserLearning: active profile preferences loaded")
                } while (engineRebuildPending)
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) Log.e(TAG, "reloadUserLearning: failed", t)
            } finally {
                engineRebuildInFlight = false
            }
        }
    }

    /**
     * Engine v3: attach the sqlite phonetic index store.
     *
     * When [preInitialize] is true this is a best-effort pre-attach: it checks whether
     * the db file is already present (fast path, no copy needed) and attaches it BEFORE
     * [SmartEngineAdapter.initialize] so the engine skips the ~480 K-word runtime corpus
     * index build (SmartEngine checks `phoneticIndex == null` at load time).
     *
     * When [preInitialize] is false (post-initialize fallback) the loader's first
     * [initialize] call will have already triggered the asset copy, so the file is
     * expected to exist now.
     *
     * In both cases:
     * - The new store is probed first. Only when it [isAvailable] is the old store
     *   closed and replaced. If the new store fails but an old available store is still
     *   attached, the old one is kept (logged).
     * - A warm-up [lookupExact] is fired on [Dispatchers.IO] after a successful attach
     *   to pre-fault the SQLite page cache.
     *
     * [loader.ensureDatabaseFile] is called inside [withContext(Dispatchers.IO)] because
     * it may perform a ~104 MB asset copy on first install.
     *
     * @return true if a store was successfully attached (new or kept), false otherwise.
     */
    private suspend fun attachPhoneticIndexStore(
        loader: AndroidDictionaryLoader,
        preInitialize: Boolean
    ): Boolean {
        val dbFile = withContext(Dispatchers.IO) { loader.ensureDatabaseFile() }
        if (preInitialize && dbFile == null) {
            // First install: db not yet present; skip pre-attach.
            return false
        }
        if (dbFile == null) {
            // S70: unconditional — a detached store silently downgrades every
            // conversion to seed-only (heavy English fallback, "keyboard got
            // dumb after the update") and DEBUG-only logging made that
            // undiagnosable from tester bug reports.
            Log.w(TAG, "attachPhoneticIndexStore: db file unavailable (copy failed or version mismatch)")
            recordImeEvent("dictionary_attach_failed_no_db")
            val kept = phoneticIndexStore?.isAvailable == true  // keep existing if available
            if (!kept) publishDictionaryStatus(loader.provisionFailure() ?: AndroidDictionaryLoader.FAILURE_COPY)
            return kept
        }

        // S29: the store constructor opens the db and runs a version probe —
        // real disk I/O that was landing on the main thread (StrictMode
        // DiskReadViolation x4 at cold start). Construct on IO.
        val newStore = withContext(Dispatchers.IO) {
            SqlitePhoneticIndexStore(dbFile).also { store ->
                // S139: the extended-data probe is a lazy SQLite query; the
                // first caller used to be the main thread (StrictMode
                // DiskReadViolation in the instrumentation log). Warm it here.
                if (store.isAvailable) store.hasExtendedData()
            }
        }
        if (!newStore.isAvailable) {
            withContext(Dispatchers.IO) { newStore.close() }
            val kept = phoneticIndexStore?.isAvailable == true
            Log.w(TAG, "attachPhoneticIndexStore: new store failed (keepingExisting=$kept)")
            recordImeEvent(if (kept) "dictionary_attach_failed_kept_old" else "dictionary_attach_failed_none")
            // S139 (F-015): a file that exists but cannot serve (wrong
            // version after a failed refresh, corrupt) must not be silent.
            if (!kept) publishDictionaryStatus(loader.provisionFailure() ?: AndroidDictionaryLoader.FAILURE_STORE)
            return kept
        }

        // New store is good — swap in, then close the old connection off-main.
        val oldStore = phoneticIndexStore
        phoneticIndexStore = newStore
        SmartEngineAdapter.setPhoneticIndex(newStore)
        publishDictionaryStatus(null)
        if (oldStore != null) withContext(Dispatchers.IO) { oldStore.close() }

        // Warm-up: pre-fault index pages so the first real keystroke is fast.
        serviceScope.launch(Dispatchers.IO) {
            newStore.lookupExact("ami")
        }

        return true
    }

    /**
     * S136 (F-015): the dictionary state is no longer silent. A failure
     * shows a dismissable notice in the keyboard, is written to
     * `dictionary_status` for the Settings screen, and is retried
     * automatically (bounded) so freeing storage heals the keyboard without
     * a reinstall.
     */
    private fun publishDictionaryStatus(failure: String?) {
        val status = when {
            failure == null && shouldUseLiteDictionary() -> "lite"
            failure == null -> "full"
            else -> "seed_only:$failure"
        }
        if (::prefs.isInitialized) prefs.edit().putString("dictionary_status", status).apply()
        val message = when {
            failure == null -> null
            failure.startsWith(AndroidDictionaryLoader.FAILURE_LOW_SPACE) -> {
                val neededMb = failure.substringAfter(':', "").toLongOrNull() ?: 0L
                "অভিধান লোড হয়নি — ফোনে অন্তত $neededMb MB জায়গা খালি করুন; কীবোর্ড নিজে থেকেই আবার চেষ্টা করবে"
            }
            else -> "অভিধান কপি করা যায়নি — কীবোর্ড আপাতত ছোট শব্দভাণ্ডারে চলছে; ফোন রিস্টার্ট করে আবার চেষ্টা করুন"
        }
        serviceScope.launch {
            if (message == null) dictionaryNoticeDismissed = false
            dictionaryNotice.value = if (dictionaryNoticeDismissed) null else message
        }
        if (failure != null) dictionaryRetryAtMs = System.currentTimeMillis() + DICTIONARY_RETRY_INTERVAL_MS
    }

    private fun dismissDictionaryNotice() {
        dictionaryNoticeDismissed = true
        dictionaryNotice.value = null
    }

    /** Retry provisioning when the keyboard closes, at most once per interval. */
    private fun maybeRetryDictionaryProvisioning() {
        if (!::prefs.isInitialized) return
        val status = prefs.getString("dictionary_status", null) ?: return
        if (!status.startsWith("seed_only")) return
        val now = System.currentTimeMillis()
        if (now < dictionaryRetryAtMs) return
        dictionaryRetryAtMs = now + DICTIONARY_RETRY_INTERVAL_MS
        reloadUserLearningAsync()
    }

    private fun createDictionaryLoader(): AndroidDictionaryLoader {
        val liteMode = shouldUseLiteDictionary()
        return AndroidDictionaryLoader(
            context = applicationContext,
            loadFullWordList = !liteMode,
            // S102: the extended dictionary is served from the sqlite store
            // (SqlitePhoneticIndexStore extended queries) — never materialized
            // into the trie on Android. This was the largest full-mode heap
            // structure (~70-90MB); the engine skips the trie load whenever
            // the attached store has extended data, and on the first-install
            // window (store not yet attached) a null here prevents a
            // double-materialization when the store lands moments later.
            loadExtendedEntries = false,
            loadFrequencyScores = !liteMode,
            loadDisambiguationData = !liteMode,
            loadBigramData = !liteMode
        )
    }

    private fun shouldUseLiteDictionary(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRamDevice = activityManager?.isLowRamDevice == true
        val memoryClass = activityManager?.memoryClass ?: Int.MAX_VALUE
        // < 256 (not <=): modern flagships (S22/Pixel class) report exactly 256m
        // heapgrowthlimit and must qualify for full mode.
        // S72/S76: a recent memory-pressure degrade (trim signal, exit
        // history, or post-load guard) forces lite for this launch. Served
        // from a field captured at onCreate so the launch counter's
        // decrement can't race the reads. The tester Samsung's exit history
        // showed repeated OS LOW_MEMORY kills of the full-mode process
        // (~172MB heap on a 256m limit).
        if (forcedLiteActiveThisLaunch) return true
        return liteModeEnabled.value || lowRamDevice || memoryClass < 256
    }

    /** S72/S76: true while this launch must run the lite profile. */
    private var forcedLiteActiveThisLaunch = false

    /** S76: check ApplicationExitInfo (API 30+) for a recent LOW_MEMORY kill
     *  of the IME process that we have not reacted to yet. */
    private fun maybeArmForcedLiteFromExitHistory() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return
        if (prefs.getInt(PREF_FORCED_LITE_LAUNCHES, 0) > 0) return
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
            val lastHandled = prefs.getLong(PREF_LAST_LOW_MEMORY_EXIT_TS, 0L)
            val kill = am.getHistoricalProcessExitReasons(packageName, 0, 8).firstOrNull { exit ->
                exit.processName == packageName && // IME process, not :ui
                    MemoryPressurePolicy.isRecentLowMemoryExit(
                        exit.reason, exit.timestamp, lastHandled, System.currentTimeMillis()
                    )
            } ?: return
            // commit(), not apply(): this is the exact state that must
            // survive the next kill.
            prefs.edit()
                .putInt(PREF_FORCED_LITE_LAUNCHES, MemoryPressurePolicy.FORCED_LITE_LAUNCHES)
                .putLong(PREF_LAST_LOW_MEMORY_EXIT_TS, kill.timestamp)
                .commit()
            recordImeEvent("memory_degrade_from_exit_history")
            Log.w(TAG, "Previous process death was LOW_MEMORY — starting in lite mode")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "exit-history check failed", e)
        }
    }

    /** S72: react to real OS memory pressure instead of waiting to be
     *  LOW_MEMORY-killed (which users experience as the keyboard vanishing
     *  and restarting mid-chat). */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val alreadyLite = shouldUseLiteDictionary()
        when (MemoryPressurePolicy.onTrim(level, alreadyLite)) {
            MemoryPressurePolicy.Action.DEGRADE_TO_LITE -> degradeToLiteForMemoryPressure("trim_level_$level")
            MemoryPressurePolicy.Action.CLEAR_CACHES -> SmartEngineAdapter.clearTransientCaches()
            MemoryPressurePolicy.Action.NONE -> return
        }
        // S163: the glide templates (~3.6MB) are droppable — cache files
        // survive, the next glide rebuilds from disk.
        glideLexiconStoreField?.dropForMemoryPressure()
        glideDecoderBn = null
        glideDecoderEn = null
    }

    /** S72: shed the full dictionary under genuine kill pressure — rebuilds
     *  the engine in lite mode (store-backed conversions keep working) and
     *  arms the forced-lite counter so the next cold starts come up lite. */
    private fun degradeToLiteForMemoryPressure(reason: String) {
        if (!::prefs.isInitialized) return
        if (forcedLiteActiveThisLaunch) return // already degraded
        forcedLiteActiveThisLaunch = true
        // S76: commit(), not apply() — this write races an imminent kill.
        prefs.edit().putInt(PREF_FORCED_LITE_LAUNCHES, MemoryPressurePolicy.FORCED_LITE_LAUNCHES).commit()
        recordImeEvent("memory_degrade_to_lite_$reason")
        Log.w(TAG, "Memory pressure ($reason): degrading dictionary profile to lite")
        SmartEngineAdapter.clearTransientCaches()
        reloadUserLearningAsync()
    }

    /** S72: adaptive post-load guard — full profile just loaded; if the heap
     *  is already nearly exhausted, this device cannot sustain it.
     *
     *  S101: the naive reading right after a bulk load counts 50-80MB of
     *  un-collected load debris (cursor strings, staging maps, the raw word
     *  list) — on 256MB flagships that pushed a healthy ~150MB retained
     *  profile past the 80% line and armed a forced-lite loop users saw as
     *  wrong conversions. The guard now confirms with a GC before degrading:
     *  a cheap pre-check keeps healthy launches GC-free, and only a reading
     *  that stays high AFTER collection (genuine retention) degrades. */
    private suspend fun degradeIfNoHeapHeadroom() {
        val runtime = Runtime.getRuntime()
        val preUsed = runtime.totalMemory() - runtime.freeMemory()
        if (!MemoryPressurePolicy.shouldDegradeAfterLoad(preUsed, runtime.maxMemory(), shouldUseLiteDictionary())) {
            return
        }
        withContext(Dispatchers.Default) { runtime.gc() }
        val used = runtime.totalMemory() - runtime.freeMemory()
        if (MemoryPressurePolicy.shouldDegradeAfterLoad(used, runtime.maxMemory(), shouldUseLiteDictionary())) {
            degradeToLiteForMemoryPressure("post_load_headroom")
        }
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
                        suggestionsProvider = kbSuggestionsProvider,
                        keyboardMode = keyboardMode.value,
                        shiftState = shiftState.value,
                        voiceInputState = voiceInputState.value,
                        voiceInputLevelProvider = kbVoiceLevelProvider,
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
                        onKeyPress = kbOnKeyPress,
                        onLetterTouch = kbOnLetterTouch,
                        glide = kbGlideState,
                        onTextInput = kbOnTextInput,
                        onBackspace = kbOnBackspace,
                        onBackspaceRepeat = kbOnBackspaceRepeat,
                        onBackspaceWord = kbOnBackspaceWord,
                        onSpace = kbOnSpace,
                        onEnter = kbOnEnter,
                        onShiftTap = kbOnShiftTap,
                        onGlobePress = kbOnGlobePress,
                        onSymbolsPress = kbOnSymbolsPress,
                        onBackToLetters = kbOnBackToLetters,
                        onSymbolPageToggle = kbOnSymbolPageToggle,
                        onSuggestionClick = kbOnSuggestionClick,
                        onNumberPress = kbOnNumberPress,
                        onPunctuationPress = kbOnPunctuationPress,
                        onCursorMove = kbOnCursorMove,
                        onDismiss = kbOnDismiss,
                        onSettingsClick = kbOnSettingsClick,
                        onToggleToolbar = kbOnToggleToolbar,
                        onClipboardOpen = kbOnClipboardOpen,
                        onClipboardPaste = kbOnClipboardPaste,
                        onClipboardClear = kbOnClipboardClear,
                        clipboardItemsProvider = kbClipboardItemsProvider,
                        onVoiceInput = kbOnVoiceInput,
                        onVoiceStop = kbOnVoiceStop,
                        onVoiceCancel = kbOnVoiceCancel,
                        onEmojiClick = kbOnEmojiClick,
                        onEmojiOpen = kbOnEmojiOpen,
                        onStickerOpen = kbOnStickerOpen,
                        onBackFromEmoji = kbOnBackFromEmoji,
                        onEmojiSearch = kbOnEmojiSearch,
                        emojiInitialCategory = emojiInitialCategory.value,
                        recentEmojisProvider = kbRecentEmojisProvider,
                        numberPadPhone = numberPadPhone.value,
                        voiceEnglishSession = voiceInputState.value != VoiceInputState.IDLE &&
                            voiceSessionEnglish,
                        noticeText = dictionaryNotice.value,
                        onNoticeDismiss = kbOnNoticeDismiss
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
        resumeComposeLifecycle()
        imeSessionVisible = true
        imeTextSessionToken++
        recordImeEvent("start_input_view")
        reloadSettings()
        configureInputSafety(info)
        editorSelStart = info?.initialSelStart ?: -1
        editorSelEnd = info?.initialSelEnd ?: -1
        // S136 (F-003): expiry is ACTIVE — every keyboard show drops entries
        // older than an hour from memory and disk, not just the panel open.
        clipboardTransientItem = null
        if (clipboardHistoryLoaded) pruneClipboardHistory()
        buffer = ""
        englishWordPrefix = ""   // S99
        suggestions.clear()
        // S67/S76/S95: same app -> the user's mode choice is sacred; new app
        // -> reset to the settings default (see LanguageModePolicy).
        val inputPackage = info?.packageName
        val samePackage = inputPackage == lastInputPackage
        lastInputPackage = inputPackage
        val modeResult = LanguageModePolicy.onFieldStart(
            samePackage = samePackage,
            current = keyboardMode.value,
            letterMode = letterModeBeforeSymbols,
            defaultMode = defaultLetterMode,
        )
        keyboardMode.value = modeResult.mode
        letterModeBeforeSymbols = modeResult.letterMode
        collapseTransientKeyboardUi()
        // S122: number/phone/PIN/datetime fields get the numeric keypad —
        // stock-keyboard behavior; ABC exits to the letter mode above. Set
        // AFTER collapseTransientKeyboardUi, which folds non-letter modes
        // back to letters and would undo this.
        val startTypeClass = (info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
        val numberField = startTypeClass == InputType.TYPE_CLASS_NUMBER ||
            startTypeClass == InputType.TYPE_CLASS_PHONE ||
            startTypeClass == InputType.TYPE_CLASS_DATETIME
        numberPadPhone.value = startTypeClass == InputType.TYPE_CLASS_PHONE
        if (numberField) keyboardMode.value = KeyboardMode.NUMBER
        clearCommitCaches()
        // S96: an EN-mode field starts with the completion/prediction strip.
        if (keyboardMode.value == KeyboardMode.ENGLISH) refreshEnglishSuggestionsAsync()
        // S98: an email field greets the user with their saved addresses
        // (both language modes; sensitive fields never reach here).
        else if (emailInputMode) maybeShowIdentityAssist()
        if (pendingVoiceStart) {
            pendingVoiceStart = false
            log("voice: starting deferred dictation after disclosure")
            onVoiceInput()
        }
        clearAutoCorrectUndoState()
        lastSpaceTime = 0L

        // Feature 1.3: Set enter key label based on IME action
        val optsForLabel = info?.imeOptions ?: 0
        enterKeyLabel.value = if ((optsForLabel and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            "\u21B5" // S22: editor wants newline from enter — label must say so
        } else when (optsForLabel and EditorInfo.IME_MASK_ACTION) {
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
        editorSelStart = newSelStart
        editorSelEnd = newSelEnd

        // S107 (tester: "place cursor in the middle and start voice typing —
        // not working"): during dictation a selection change we did not
        // produce ourselves is an intentional user move — follow it instead
        // of yanking the caret back to the stale session-start anchor.
        when (val decision = VoiceAnchorPolicy.onSelectionChanged(
            dictationActive = voiceDictationActive,
            expectedSelections = voiceExpectedSelections.toList(),
            anchor = voiceInsertionCursor,
            newSelStart = newSelStart,
            newSelEnd = newSelEnd,
            liveSegmentActive = voiceLiveCommittedPartial.isNotEmpty(),
        )) {
            is VoiceAnchorPolicy.Decision.ConsumeExpected ->
                repeat(decision.dropCount) { voiceExpectedSelections.removeFirstOrNull() }
            is VoiceAnchorPolicy.Decision.Reanchor -> reanchorVoiceInsertion(decision.position)
            VoiceAnchorPolicy.Decision.Ignore -> Unit
        }

        if (buffer.isEmpty()) return

        val composingSpanIsKnown = candidatesStart >= 0 && candidatesEnd >= candidatesStart
        val selectionInsideComposingSpan = composingSpanIsKnown &&
            newSelStart >= candidatesStart &&
            newSelEnd <= candidatesEnd

        if (!selectionInsideComposingSpan) {
            // The user moved the cursor or the app changed selection outside our active
            // composing word. Keeping the old phonetic buffer would inject the next key at
            // the wrong cursor position, so finalize the visible composition and reset IME state.
            imeTextSessionToken++
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
        flushLatencyTelemetry()
        pauseComposeLifecycle()
        maybeRetryDictionaryProvisioning()
        super.onFinishInputView(finishingInput)
    }

    override fun onWindowHidden() {
        cleanupImeSession("window_hidden", cancelVoice = true)
        pauseComposeLifecycle()
        super.onWindowHidden()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        resumeComposeLifecycle()
    }

    // S136 (F-012): the Compose lifecycle now follows the real shown/hidden
    // state — STARTED/RESUMED only while the keyboard is on screen. Compose
    // pauses its frame clock below STARTED, so a hidden keyboard recomposes
    // nothing; every pending state change applies on the next show. Guards
    // keep the registry monotonic (a PAUSE from CREATED would move UP).
    private fun pauseComposeLifecycle() {
        val state = lifecycleRegistry.currentState
        if (state.isAtLeast(Lifecycle.State.RESUMED)) lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    private fun resumeComposeLifecycle() {
        val state = lifecycleRegistry.currentState
        if (state == Lifecycle.State.DESTROYED || state == Lifecycle.State.INITIALIZED) return
        if (!state.isAtLeast(Lifecycle.State.STARTED)) lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        if (!lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /** S76: package of the last edited field — the language mode resets on
     *  APP change only (null-start forces a reset on the first field). */
    private var lastInputPackage: String? = null

    /** First-run flow: the disclosure activity still has focus when its accept
     *  broadcast lands, so dictation must start when the keyboard next shows. */
    private var pendingVoiceStart = false

    /** The exact string the last voice segment inserted (text + punctuation).
     *  The delete chip removes THIS segment only, after verifying the editor
     *  still ends with it — never a blind session-length wipe. */
    private var voiceLastSegmentText: String = ""

    /** Production guard: consecutive fruitless listen cycles (no speech heard).
     *  The continuous-dictation loop must not keep the mic hot forever when the
     *  user walked away — after the cap we stop gracefully to STOPPED. */
    private var voiceFruitlessRestarts = 0

    /** S55 (review follow-up): consecutive ERROR_CLIENT/ERROR_RECOGNIZER_BUSY
     *  cycles. Unlike the watchdog (armed for a recognizer that never calls
     *  back at all), a stuck-busy recognizer DOES call back every time — so
     *  only a counted cap stops a device with a permanently stolen
     *  recognition slot from destroy+recreate looping forever. Reset at
     *  session start and on any real progress (onBeginningOfSpeech). */
    private var voiceBusyRestarts = 0

    /** S56: consecutive silent-wedge recoveries (see VOICE_LIVENESS_TIMEOUT_MS). */
    private var voiceWedgeRestarts = 0

    private val voiceDisclosureReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_VOICE_DISCLOSURE_ACCEPTED) return
            log("voice: disclosure accepted — resuming dictation")
            if (isInputViewShown) onVoiceInput() else pendingVoiceStart = true
        }
    }

    override fun onDestroy() {
        cleanupImeSession("destroy", cancelVoice = true)
        flushLatencyTelemetry()
        try { unregisterReceiver(voiceDisclosureReceiver) } catch (_: Exception) { /* not registered */ }
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }
        previousUncaughtExceptionHandler?.let { Thread.setDefaultUncaughtExceptionHandler(it) }
        previousUncaughtExceptionHandler = null
        SmartEngineAdapter.configurePersistenceScope(null)
        releaseSpeechRecognizer()
        pauseComposeLifecycle()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        // S136/S138 (F-012): cancel every job FIRST and wait (bounded) for
        // them to unwind, THEN close the sqlite store and drop the engine —
        // an in-flight initialize() or lookup must never run against a
        // closed store or publish a full engine into a dead service.
        initialEngineLoadJob?.cancel()
        val serviceJob = serviceScope.coroutineContext[Job]
        serviceScope.cancel()
        if (serviceJob != null) {
            val joined = kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(TEARDOWN_JOIN_TIMEOUT_MS) { serviceJob.join() }
            }
            // S139 (F-012): a job still running past the bound is recorded —
            // the store is closed regardless (queries after close fail soft),
            // but the event is visible in Diagnostics instead of silent.
            if (joined == null) recordFailureEvent("teardown_join_timeout", durable = true)
        }
        // Engine v3: close the sqlite connection only — do NOT setPhoneticIndex(null)
        // (null-detach leaves corpus lookups empty; see SmartEngine.setPhoneticIndex KDoc).
        phoneticIndexStore?.close()
        phoneticIndexStore = null
        store.clear()
        SmartEngineAdapter.reset()
        strictModePenaltyExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun cleanupImeSession(reason: String, cancelVoice: Boolean) {
        flushImeSessionTelemetry()
        recordImeEvent(reason)
        imeSessionVisible = false
        imeTextSessionToken++
        collapseTransientKeyboardUi()
        suggestionJob?.cancel()
        composingJob?.cancel()
        suggestionJob = null
        suggestions.clear()
        buffer = ""
        clearCommitCaches()
        lastSpaceTime = 0L
        resetShiftState()
        if (cancelVoice) {
            stopVoiceInput(cancel = true)
            releaseSpeechRecognizer()
            voiceExpectedSelections.clear()
        } else {
            voiceRestartJob?.cancel()
            voiceRestartJob = null
            voicePartialCommitJob?.cancel()
            voicePartialCommitJob = null
            voiceTokenRefineJob?.cancel()
            voiceTokenRefineJob = null
            cancelVoiceIdleAndPunctuationJobs()
        }
    }

    // ── Key Handlers ───────────────────────────────────────────────────────

    private fun onKeyPress(char: Char) {
        dismissVoiceTroubleOnUserAction()
        when (keyboardMode.value) {
            KeyboardMode.BANGLU -> onBangluKeyPress(char)
            KeyboardMode.ENGLISH -> onEnglishKeyPress(char)
            else -> onDirectCommit(char)
        }
    }

    private fun onTextInput(text: String) {
        dismissVoiceTroubleOnUserAction()
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
        // Typing must surface suggestions: an expanded toolbar would otherwise
        // take strip precedence and silently hide them (found in live device test).
        isToolbarExpanded.value = false
        val ic = currentInputConnection ?: return

        // S167 (user report: "trying to capital english in the editor i can
        // not" — shift was a deliberate input no-op in Bangla mode): a
        // SHIFTED letter is a raw capital English insert. The Bangla word in
        // progress commits first (its visible preview, the S32 contract),
        // then the capital lands as literal text; single-shift auto-releases,
        // caps lock holds. Lowercase letters keep composing Bangla as ever.
        if (char in 'A'..'Z' && !rawCommitInputMode) {
            commitPendingBuffer()
            ic.commitText(char.toString(), 1)
            lastCommittedTextLength = 1
            suggestions.clear()
            if (shiftState.value == ShiftState.ON) shiftState.value = ShiftState.OFF
            return
        }

        if (rawCommitInputMode) {
            ic.commitText(char.toString(), 1)
            sessionRawCommitKeyCount++
            suggestions.clear()
            // S98: raw fields include email fields — identity chips are the
            // one suggestion class that belongs there (BN mode included).
            maybeShowIdentityAssist()
            if (shiftState.value == ShiftState.ON && char.isLetter()) {
                shiftState.value = ShiftState.OFF
            }
            return
        }

        // S109: first letter of a word typed with the cursor directly after
        // Bengali word text = the user is EDITING that word (tap-in mid-word
        // to add a kar, or continuing a committed word with no space). Seed
        // the buffer with the prefix's roman key so the whole word
        // re-converts (dobaa class: দ|বা + 'a' -> দাবা, not দআবা). One
        // IC read per word START only — the per-keystroke path stays on the
        // shadow buffer (S28 law).
        if (buffer.isEmpty() && char.isLetter()) tryResumeComposingBeforeTyping(ic)
        buffer += char
        sessionBangluKeyCount++

        // Auto-unshift after typing a letter (unless caps lock)
        if (shiftState.value == ShiftState.ON && char.isLetter()) {
            shiftState.value = ShiftState.OFF
        }

        updateComposingAsync(ic)

        refreshSuggestionsAsync(buffer)
        prepareCommitConversionAsync(buffer)
    }

    /**
     * S109: see [BackspaceResume.planForTyping]. No-op when resume doesn't
     * apply — the caller then composes fresh from the typed letter
     * (previous behavior).
     */
    private fun tryResumeComposingBeforeTyping(ic: InputConnection) {
        if (rawCommitInputMode || uriInputMode || sensitiveInputMode) return
        if (keyboardMode.value != KeyboardMode.BANGLU) return
        val before = ic.getTextBeforeCursor(32, 0)?.toString().orEmpty()
        val plan = BackspaceResume.planForTyping(
            textBeforeCursor = before,
            reverse = { com.banglu.engine.util.ReverseTransliterator.reverseWord(it) },
            instantPreview = { SmartEngineAdapter.convertForInstantPreview(it) },
        ) ?: return
        ic.beginBatchEdit()
        ic.deleteSurroundingText(plan.deleteLength, 0)
        buffer = plan.romanBuffer
        ic.setComposingText(plan.visibleFragment, 1)
        ic.endBatchEdit()
        composingInput = plan.romanBuffer
        composingResult = null
        composingVisibleText = plan.visibleFragment
    }

    private fun onEnglishKeyPress(char: Char) {
        log("onEnglishKeyPress: char='$char'")
        clearVoiceUndoState()

        // Auto-unshift after typing a letter (unless caps lock)
        if (shiftState.value == ShiftState.ON && char.isLetter()) {
            shiftState.value = ShiftState.OFF
        }

        val ic = currentInputConnection ?: return
        // S97: typing again closes the undo window for the last correction.
        if (lastAutoCorrectWasEnglish) clearAutoCorrectUndoState()
        ic.commitText(char.toString(), 1)
        // S99: shadow the word prefix for the sync touch resolver.
        englishWordPrefix =
            if (char.isLetter()) englishWordPrefix + char.lowercaseChar() else ""

        // Do NOT auto-capitalize here — only after space/enter
        // Auto-capitalizing after every keypress causes uppercase in middle of words

        // S96: live word completions while typing English.
        refreshEnglishSuggestionsAsync()
    }

    // ── S96: English typing suite (completions, predictions, learning) ────

    private fun isEnglishWordChar(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c == '\''

    /**
     * (word-in-progress, previous completed word) parsed from the text
     * before the cursor. The prefix is the trailing letter run; the previous
     * word is the letter run before the separators before it.
     */
    private fun englishContextFrom(before: String): Pair<String, String?> {
        var i = before.length
        while (i > 0 && isEnglishWordChar(before[i - 1])) i--
        val prefix = before.substring(i)
        var j = i
        while (j > 0 && !isEnglishWordChar(before[j - 1])) j--
        var k = j
        while (k > 0 && isEnglishWordChar(before[k - 1])) k--
        val prev = before.substring(k, j).takeIf { it.isNotBlank() }
        return prefix to prev
    }

    private fun refreshEnglishSuggestionsAsync() {
        // S98: an @-token takes precedence — domain completions replace the
        // word suggestions while an email address is being typed.
        if (maybeShowIdentityAssist()) return
        suggestionJob?.cancel()
        if (!suggestionsAllowedForCurrentInput()) {
            suggestions.clear()
            return
        }
        val before = currentInputConnection?.getTextBeforeCursor(48, 0)?.toString().orEmpty()
        val (prefix, prev) = englishContextFrom(before)
        suggestionJob = serviceScope.launch {
            val items = withContext(engineLane) {
                SmartEngineAdapter.ensureEnglishLearningLoaded()
                if (prefix.isEmpty()) SmartEngineAdapter.englishPredictions(prev, 3)
                else SmartEngineAdapter.englishCompletions(prefix, 3)
            }
            // S167 (user: "in english mode no need to mirror bengali —
            // people might feel bored"): the S162 Bangla-mirror ghost chip
            // is REMOVED. English mode shows English only.
            if (keyboardMode.value == KeyboardMode.ENGLISH) {
                suggestions.clear()
                // S97: a fresh correction leads with its undo chip.
                autoCorrectUndoSuggestion()?.let { suggestions.add(it) }
                items.forEach { w ->
                    suggestions.add(SmartSuggestion(w, 0.9, ENGLISH_WORD_SOURCE, prefix, "en"))
                }
                // S122: inline emoji suggestions — typed word matches an
                // emoji keyword (love -> ❤️), Gboard/Samsung behavior. The
                // 3-script keyword data has existed since S57; this wires it
                // into the strip.
                if (prefix.length >= 2) {
                    EmojiKeywords.suggestFor(prefix).forEach { e ->
                        suggestions.add(SmartSuggestion(e, 0.9, EMOJI_WORD_SOURCE, prefix, "emoji"))
                    }
                }
            }
        }
    }

    private fun onEnglishSuggestionTap(suggestion: SmartSuggestion) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(48, 0)?.toString().orEmpty()
        val (prefix, prev) = englishContextFrom(before)
        ic.beginBatchEdit()
        if (prefix.isNotEmpty()) ic.deleteSurroundingText(prefix.length, 0)
        ic.commitText(suggestion.bengali + " ", 1)
        ic.endBatchEdit()
        lastCommittedTextLength = suggestion.bengali.length + 1
        sessionSuggestionTapCount++
        englishWordPrefix = ""   // S99: chip finished the word
        recordEnglishCommitAsync(suggestion.bengali, prev)
        refreshEnglishSuggestionsAsync()
    }

    /** A finished English word (space/punctuation/chip) — learn it off-lane. */
    private fun recordEnglishCommitAsync(word: String, prev: String?) {
        if (privateInputMode || rawCommitInputMode || learningSuppressedInputMode) return
        if (word.isBlank()) return
        serviceScope.launch(engineLane) {
            SmartEngineAdapter.ensureEnglishLearningLoaded()
            SmartEngineAdapter.recordEnglishCommit(word, prev)
        }
    }

    // ── S98: identity assist (emails/usernames — NEVER passwords) ────────

    /** Sensitive fields (password/OTP/no-learning) are excluded absolutely.
     *  This gate covers the STATIC part of identity assist — completing a
     *  typed "@gm" to gmail.com from the built-in domain list, which stores
     *  nothing. */
    private fun identityAssistAllowed(): Boolean =
        imeSessionVisible && suggestionsEnabled.value && !sensitiveInputMode

    /** S136 (F-004): the MEMORY part — remembering addresses and offering
     *  saved ones — needs the dedicated switch (default OFF) plus the
     *  personal dictionary. */
    private fun identityMemoryAllowed(): Boolean =
        identityAssistAllowed() && personalDictionaryEnabled.value && identityAssistEnabled.value

    /** The whitespace-delimited token directly before the cursor. */
    private fun identityTokenBeforeCursor(ic: InputConnection): String {
        val before = ic.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        return before.takeLastWhile { !it.isWhitespace() }
    }

    /**
     * Shows identity chips when they apply: domain completions for an
     * @-containing token (any keyboard mode), or the saved addresses in an
     * empty email field. Returns true when identity took the strip.
     */
    private fun maybeShowIdentityAssist(): Boolean {
        if (!identityAssistAllowed()) return false
        val ic = currentInputConnection ?: return false
        val token = identityTokenBeforeCursor(ic)
        val wantsDomains = token.contains('@')
        val wantsSavedFills = emailInputMode && token.isEmpty() && identityMemoryAllowed()
        if (!wantsDomains && !wantsSavedFills) return false
        suggestionJob?.cancel()
        suggestionJob = serviceScope.launch {
            val items = withContext(engineLane) {
                SmartEngineAdapter.ensureIdentityLoaded()
                if (wantsDomains) {
                    if (SmartEngineAdapter.identityIsEmailLikeToken(token)) {
                        SmartEngineAdapter.identityDomainSuggestions(token, 3)
                    } else emptyList()
                } else {
                    SmartEngineAdapter.identitySavedFills(3)
                }
            }
            if (items.isNotEmpty()) {
                suggestions.clear()
                items.forEach { fill ->
                    suggestions.add(SmartSuggestion(fill, 0.95, IDENTITY_FILL_SOURCE, token, "identity"))
                }
            }
        }
        return true
    }

    /** Chip tap: replace the current token with the chosen identity. */
    private fun onIdentityFillTap(suggestion: SmartSuggestion) {
        if (!identityAssistAllowed()) return
        val ic = currentInputConnection ?: return
        val token = identityTokenBeforeCursor(ic)
        ic.beginBatchEdit()
        if (token.isNotEmpty()) ic.deleteSurroundingText(token.length, 0)
        // No trailing space — form fields validate the exact address.
        ic.commitText(suggestion.bengali, 1)
        ic.endBatchEdit()
        lastCommittedTextLength = suggestion.bengali.length
        sessionSuggestionTapCount++
        recordIdentityAsync(suggestion.bengali)
        suggestions.clear()
    }

    /** A committed token that may be a complete email — learn it off-lane. */
    private fun recordIdentityAsync(token: String) {
        // S136 (F-004): remember addresses ONLY from email fields (what the
        // privacy policy says) and only with the switch on.
        if (!identityMemoryAllowed() || !emailInputMode) return
        if (!token.contains('@')) return
        serviceScope.launch(engineLane) {
            SmartEngineAdapter.ensureIdentityLoaded()
            SmartEngineAdapter.recordIdentity(token)
        }
    }

    private fun onDirectCommit(char: Char) {
        log("onDirectCommit: char='$char'")
        clearVoiceUndoState()
        // Commit any pending Banglu buffer first
        commitPendingBuffer()

        val ic = currentInputConnection ?: return
        ic.commitText(char.toString(), 1)
        // S98: '@' from the symbols layer starts an email token in any mode.
        if (char == '@' || emailInputMode) maybeShowIdentityAssist()
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
        // S96: punctuation also finishes an English word \u2014 learn it first.
        if (keyboardMode.value == KeyboardMode.ENGLISH) {
            val before = ic.getTextBeforeCursor(48, 0)?.toString().orEmpty()
            val (word, prev) = englishContextFrom(before)
            if (word.isNotEmpty()) recordEnglishCommitAsync(word, prev)
        }
        val output = if (keyboardMode.value == KeyboardMode.BANGLU && char == '.' && !uriInputMode) '\u0964' else char
        if (keyboardMode.value == KeyboardMode.BANGLU && isBanglaTightPunctuation(output)) {
            deleteSingleSpaceBeforeCursor(ic)
        }
        ic.commitText(output.toString(), 1)
        lastCommittedTextLength = 1
        englishWordPrefix = ""   // S99: separator ends the word
        // S98: '@' (long-press or symbols) starts an email token; a '.'
        // mid-@-token continues one (sham@gmail<.>com).
        if (output == '@' || (identityAssistAllowed() &&
                identityTokenBeforeCursor(ic).contains('@'))
        ) {
            if (maybeShowIdentityAssist()) return
        }
        showGapPunctuationSuggestions()
    }

    private fun onBackspace() {
        dismissVoiceTroubleOnUserAction()
        log("onBackspace: mode=${keyboardMode.value}, buffer='$buffer'")
        val ic = currentInputConnection ?: return
        if (deleteEditorSelectionIfAny(ic)) return

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
                        updateComposingAsync(ic)
                        refreshSuggestionsAsync(buffer)
                        prepareCommitConversionAsync(buffer)
                    }
                } else {
                    // S88 (tester: abaro -> backspace -> retype o gave আবারও):
                    // backspace into a committed Bengali word resumes roman
                    // composition on the remaining fragment so the next
                    // letters re-convert the WHOLE word. Falls back to plain
                    // grapheme deletion whenever the fragment doesn't
                    // round-trip losslessly.
                    if (!tryResumeComposingOnBackspace(ic)) {
                        deletePreviousGraphemes(ic)
                    }
                }
            }
            else -> {
                // Delete the previous user-visible character. This keeps emoji and Bengali
                // combining clusters intact instead of deleting one UTF-16 code unit.
                deletePreviousGraphemes(ic)
                // S96: keep the completion strip in sync while editing English.
                if (keyboardMode.value == KeyboardMode.ENGLISH) {
                    englishWordPrefix = englishWordPrefix.dropLast(1)   // S99
                    refreshEnglishSuggestionsAsync()
                } else if (rawCommitInputMode || emailInputMode) {
                    // S98: identity chips track deletions in raw/email fields.
                    suggestions.clear()
                    maybeShowIdentityAssist()
                }
            }
        }
    }

    /**
     * S88: see [BackspaceResume]. Returns false when resume doesn't apply —
     * the caller then deletes a plain grapheme (previous behavior).
     */
    private fun tryResumeComposingOnBackspace(ic: InputConnection): Boolean {
        if (rawCommitInputMode || uriInputMode) return false
        val before = ic.getTextBeforeCursor(48, 0)?.toString().orEmpty()
        val plan = BackspaceResume.plan(
            textBeforeCursor = before,
            reverse = { com.banglu.engine.util.ReverseTransliterator.reverseWord(it) },
            instantPreview = { SmartEngineAdapter.convertForInstantPreview(it) },
        ) ?: return false
        ic.beginBatchEdit()
        ic.deleteSurroundingText(plan.deleteLength, 0)
        buffer = plan.romanBuffer
        ic.setComposingText(plan.visibleFragment, 1)
        ic.endBatchEdit()
        composingInput = plan.romanBuffer
        composingResult = null
        composingVisibleText = plan.visibleFragment
        refreshSuggestionsAsync(buffer)
        prepareCommitConversionAsync(buffer)
        return true
    }

    private fun onBackspaceRepeat(count: Int) {
        val safeCount = count.coerceIn(1, 48)
        val ic = currentInputConnection ?: return
        if (deleteEditorSelectionIfAny(ic)) return

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
                updateComposingAsync(ic)
                refreshSuggestionsAsync(buffer)
                prepareCommitConversionAsync(buffer)
            }
            return
        }

        ic.finishComposingText()
        suggestions.clear()
        deletePreviousGraphemes(ic, safeCount)
    }

    /**
     * S168 (audit P1-1): a range selection is deleted as a RANGE. Returns true
     * when it handled the backspace; false means no range selection exists and
     * the normal delete-before-cursor paths apply.
     */
    private fun deleteEditorSelectionIfAny(ic: InputConnection): Boolean {
        if (SelectionEditPolicy.backspacePlan(editorSelStart, editorSelEnd) !=
            SelectionEditPolicy.BackspacePlan.DELETE_SELECTION) return false
        ic.beginBatchEdit()
        if (buffer.isNotEmpty()) {
            ic.finishComposingText()
            buffer = ""
            suggestionJob?.cancel()
            composingJob?.cancel()
            suggestions.clear()
            clearCommitCaches()
        }
        ic.commitText("", 1)
        ic.endBatchEdit()
        // Collapse locally so a delayed onUpdateSelection cannot re-trigger.
        val caret = minOf(editorSelStart, editorSelEnd)
        editorSelStart = caret
        editorSelEnd = caret
        lastCommittedTextLength = 0
        englishWordPrefix = ""
        clearAutoCorrectUndoState()
        if (keyboardMode.value == KeyboardMode.ENGLISH) refreshEnglishSuggestionsAsync()
        return true
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

    // S88: single-sourced in BackspaceResume so the resume plan and deletion
    // share ONE cluster-boundary definition (no drifted copies).
    private fun previousUserVisibleClusterBoundary(text: String, fromIndex: Int = text.length): Int =
        BackspaceResume.previousUserVisibleClusterBoundary(text, fromIndex)

    private fun onSpacePress() {
        log("onSpacePress: mode=${keyboardMode.value}, buffer='$buffer'")
        clearVoiceUndoState()
        val ic = currentInputConnection ?: return
        val now = System.currentTimeMillis()

        when (keyboardMode.value) {
            KeyboardMode.BANGLU -> {
                if (rawCommitInputMode) {
                    // S98: a space in a raw field (email fields included)
                    // finishes any email token — learn it first.
                    if (identityAssistAllowed()) {
                        val token = identityTokenBeforeCursor(ic)
                        if (token.contains('@')) recordIdentityAsync(token)
                    }
                    ic.commitText(" ", 1)
                } else if (buffer.isNotEmpty()) {
                    commitBufferedWordFast(ic, appendText = " ")
                } else {
                    // Feature 1.1: Double-space → Bengali danda + space
                    // (S56: never in URI fields — a danda corrupts URLs/queries)
                    if (doubleSpacePeriodEnabled.value && !uriInputMode &&
                        DoubleSpacePolicy.replacesTrailingSpace(now - lastSpaceTime < DOUBLE_SPACE_THRESHOLD_MS) {
                            ic.getTextBeforeCursor(1, 0)
                        }
                    ) {
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
                // S98: space also finishes an email token in any mode.
                if (identityAssistAllowed()) {
                    val token = identityTokenBeforeCursor(ic)
                    if (token.contains('@')) recordIdentityAsync(token)
                }
                // S96: space finishes an English word — learn it (with its
                // previous word) BEFORE the separator lands. S97: mistyped
                // unknown words auto-correct here, with an undo chip.
                if (keyboardMode.value == KeyboardMode.ENGLISH) {
                    val before = ic.getTextBeforeCursor(48, 0)?.toString().orEmpty()
                    val (word, prev) = englishContextFrom(before)
                    if (word.isNotEmpty()) {
                        val correction = if (suggestionsAllowedForCurrentInput()) {
                            SmartEngineAdapter.englishAutocorrect(word)
                        } else null
                        if (correction != null && correction != word) {
                            ic.beginBatchEdit()
                            ic.deleteSurroundingText(word.length, 0)
                            ic.commitText("$correction ", 1)
                            ic.endBatchEdit()
                            lastCommittedTextLength = correction.length + 1
                            lastAutoCorrectOriginal = word
                            lastAutoCorrectReplacement = correction
                            lastAutoCorrectPhonetic = word
                            lastAutoCorrectWasEnglish = true
                            recordImeEvent("autocorrect_offer")
                            recordEnglishCommitAsync(correction, prev)
                            lastSpaceTime = now
                            refreshEnglishSuggestionsAsync()
                            return
                        }
                        recordEnglishCommitAsync(word, prev)
                    }
                }
                // Feature 1.1: Double-space → period + space (English/Symbol modes)
                if (doubleSpacePeriodEnabled.value &&
                    DoubleSpacePolicy.replacesTrailingSpace(now - lastSpaceTime < DOUBLE_SPACE_THRESHOLD_MS) {
                        ic.getTextBeforeCursor(1, 0)
                    }
                ) {
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                    lastCommittedTextLength = 2
                } else {
                    ic.commitText(" ", 1)
                    lastCommittedTextLength = 1
                }
                // S96: between words the strip switches to next-word
                // predictions (personal bigrams first, common starters after).
                if (keyboardMode.value == KeyboardMode.ENGLISH) {
                    englishWordPrefix = ""   // S99: word finished
                    refreshEnglishSuggestionsAsync()
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

        // S98: enter/next/done is how email fields usually finish — learn a
        // completed address before the action fires.
        if (identityAssistAllowed()) {
            val token = identityTokenBeforeCursor(ic)
            if (token.contains('@')) recordIdentityAsync(token)
        }

        englishWordPrefix = ""   // S99: enter ends the word

        // Commit any pending buffer
        if (keyboardMode.value == KeyboardMode.BANGLU && buffer.isNotEmpty() && !rawCommitInputMode) {
            commitBufferedWordFast(ic, appendText = "")
        }

        // Feature 1.3 / S22: perform the IME action ONLY when the editor
        // wants enter to trigger it. Messaging apps (WhatsApp, Messages)
        // declare an action (SEND) but set IME_FLAG_NO_ENTER_ACTION —
        // "enter inserts a newline; sending is my own button". We ignored
        // the flag, fired performEditorAction(SEND), the app ignored THAT,
        // and the enter key read as completely dead.
        val editorInfo = currentInputEditorInfo
        val imeOptions = editorInfo?.imeOptions ?: 0
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        val enterPerformsAction =
            (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0 &&
                action in setOf(
                    EditorInfo.IME_ACTION_SEARCH,
                    EditorInfo.IME_ACTION_GO,
                    EditorInfo.IME_ACTION_NEXT,
                    EditorInfo.IME_ACTION_SEND,
                    EditorInfo.IME_ACTION_DONE
                )

        if (enterPerformsAction) {
            ic.performEditorAction(action)
        } else {
            // Newline. commitText is the reliable path for multiline editors;
            // key events remain for fields that only listen for KEYCODE_ENTER.
            val multiline = (editorInfo?.inputType ?: 0) and
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
            if (multiline) {
                ic.commitText("\n", 1)
            } else {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
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

    // ── S163: glide completion ─────────────────────────────────────────────

    private class GlideOutcome(val roman: String, val text: String)

    private fun glideDecoderFor(bangla: Boolean, lex: GlideLexicon): GlideDecoder =
        if (bangla) {
            glideDecoderBn?.takeIf { it.lexicon === lex } ?: GlideDecoder(lex).also { glideDecoderBn = it }
        } else {
            glideDecoderEn?.takeIf { it.lexicon === lex } ?: GlideDecoder(lex).also { glideDecoderEn = it }
        }

    /**
     * Finger lifted after an armed glide. Decode + (BN) convert on the
     * engine lane; commit back on the main scope. The commit REPLACES the
     * whole composing word (the glide's own first press is already in it —
     * commit-on-down); no candidate above the floor commits NOTHING and the
     * view flashes the trail red. Top-1 learning is passive (S26 law); only
     * an alternate-chip tap records an explicit choice.
     */
    private fun onGlideComplete(points: List<GlidePoint>) {
        val mode = keyboardMode.value
        val bangla = mode == KeyboardMode.BANGLU
        if (!bangla && mode != KeyboardMode.ENGLISH) {
            kbGlideState.trail.clear()
            return
        }
        // S163b: commit-on-down already resolved the glide's first key — a
        // strong prior the decoder rewards (never a hard filter).
        val firstKeyChar: Char? = (
            if (bangla) buffer.lastOrNull()
            else currentInputConnection?.getTextBeforeCursor(1, 0)?.lastOrNull()
            )?.lowercaseChar()?.takeIf { it in 'a'..'z' }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "glide: ${points.size} pts first=$firstKeyChar " +
                "p0=(${points.firstOrNull()?.x},${points.firstOrNull()?.y}) " +
                "pn=(${points.lastOrNull()?.x},${points.lastOrNull()?.y})")
        }
        // S168 (audit P1-2): snapshot what the editor looked like at finger-lift;
        // a result that lands after more typing or a session change is dropped.
        val sessionThen = imeTextSessionToken
        val typedThen = if (bangla) buffer else englishWordPrefix
        val liftedAtNs = System.nanoTime()
        serviceScope.launch {
            // S168 (audit P1-3): the lexicon load/build (seconds on a cold
            // process) runs on IO — it must never occupy the engine lane that
            // every keystroke's conversion waits on.
            val lex = try {
                withContext(Dispatchers.IO) {
                    val store = glideLexiconStore()
                    if (bangla) store.banglaLexicon() else store.englishLexicon()
                }
            } catch (e: Throwable) {
                recordEngineFailure("glide_lexicon", e)
                null
            }
            val lexReadyNs = System.nanoTime()
            val outcomes: List<GlideOutcome>? = if (lex == null) null else withContext(engineLane) {
                try {
                    val cands = glideDecoderFor(bangla, lex).decode(points, firstKey = firstKeyChar)
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "glide: lex=${lex.size} cands=${cands.joinToString { "${it.word}@${"%.2f".format(it.score)}" }}")
                    }
                    if (bangla) {
                        // Alias-inclusive lexicon (S163b): sibling romans of
                        // one Bengali word crowd the candidates — dedupe on
                        // the CONVERTED word so chips never repeat.
                        cands.mapNotNull { c ->
                            val res = safeConvert(c.word)
                            if (res.bengali.any { it in 'ঀ'..'৿' })
                                GlideOutcome(c.word, res.bengali)
                            else null
                        }.distinctBy { it.text }
                    } else {
                        cands.map { GlideOutcome("", it.word) }
                    }
                } catch (e: Throwable) {
                    recordEngineFailure("glide", e)
                    null
                }
            }
            // Perf telemetry only (no text): lexicon wait vs decode+convert.
            // Measured 2026-09-02 on SM-S901W perf build: lexicon 0-1ms
            // (warmed), decode 23-115ms.
            if (BuildConfig.DEBUG) Log.d(TAG, "glide timing: lexicon=${(lexReadyNs - liftedAtNs) / 1_000_000}ms " +
                "decode=${(System.nanoTime() - lexReadyNs) / 1_000_000}ms points=${points.size}")
            if (keyboardMode.value != mode) {
                kbGlideState.trail.clear()
                return@launch
            }
            val typedNow = if (bangla) buffer else englishWordPrefix
            if (!GlideCommitPolicy.resultStillApplies(sessionThen, imeTextSessionToken, typedThen, typedNow)) {
                kbGlideState.trail.clear()
                return@launch
            }
            if (outcomes.isNullOrEmpty()) {
                // No confident word: commit nothing, flash the trail red.
                kbGlideState.failFlash.value = true
                return@launch
            }
            val ic = currentInputConnection ?: run { kbGlideState.trail.clear(); return@launch }
            val top = outcomes.first()
            val plan = GlideCommitPolicy.planCommit(
                if (bangla) GlideMode.BANGLA else GlideMode.ENGLISH,
                editorCharsFromFirstKey = 1,
                word = top.text
            )
            ic.beginBatchEdit()
            if (plan.eraseEditorChars > 0) ic.deleteSurroundingText(plan.eraseEditorChars, 0)
            ic.commitText(plan.commitText, 1)
            ic.endBatchEdit()
            lastCommittedTextLength = plan.commitText.length
            lastGlideCommit = top.text
            if (bangla) {
                buffer = ""
                suggestionJob?.cancel()
                clearCommitCaches()
                learnCommittedWordAsync(top.roman, top.text, explicitChoice = false)
                recordNextWordPairLearning(top.text)
            } else {
                englishWordPrefix = ""
                recordEnglishCommitAsync(top.text, null)
            }
            // The alternates ARE the strip until the user moves on — calling
            // updatePredictions here would clobber the swap window (caught on
            // device: the chips flashed and became next-word predictions).
            // The next keystroke/space repopulates predictions as usual.
            suggestions.clear()
            outcomes.drop(1).take(5).forEach { alt ->
                suggestions.add(GlideCommitPolicy.altChip(alt.roman, alt.text))
            }
            kbGlideState.trail.clear()
        }
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
        // S98: identity chips live in email fields too, which are raw-commit
        // by design — handle them BEFORE the raw/private guard (they can
        // only exist when the sensitive-field gate already allowed them).
        if (suggestion.source == IDENTITY_FILL_SOURCE) {
            onIdentityFillTap(suggestion)
            return
        }
        if (privateInputMode || rawCommitInputMode) return

        // S96: English chips replace the word being typed (or commit a
        // prediction) and feed the English learning store.
        if (suggestion.source == ENGLISH_WORD_SOURCE) {
            onEnglishSuggestionTap(suggestion)
            return
        }

        // S122: emoji chip replaces the typed keyword with the emoji itself
        // (Gboard semantics — "love" + ❤️-tap leaves just ❤️).
        if (suggestion.source == EMOJI_WORD_SOURCE) {
            val emojiIc = currentInputConnection ?: return
            val beforeEmoji = emojiIc.getTextBeforeCursor(48, 0)?.toString().orEmpty()
            val (emojiPrefix, _) = englishContextFrom(beforeEmoji)
            emojiIc.beginBatchEdit()
            if (emojiPrefix.isNotEmpty()) emojiIc.deleteSurroundingText(emojiPrefix.length, 0)
            emojiIc.commitText(suggestion.bengali, 1)
            emojiIc.endBatchEdit()
            lastCommittedTextLength = suggestion.bengali.length
            sessionSuggestionTapCount++
            englishWordPrefix = ""
            rememberRecentEmoji(suggestion.bengali)
            refreshEnglishSuggestionsAsync()
            return
        }

        // S167: the S162 EN-mode Bangla-mirror chip was removed on user
        // verdict ("no need to mirror bengali") — its tap branch went with it.

        val ic = currentInputConnection ?: return

        // S163: glide alternate — swap the just-glided word, but only while
        // the editor still ends with it (S32-style guard against staleness).
        if (suggestion.source == GlideCommitPolicy.GLIDE_ALT_SOURCE) {
            val committed = lastGlideCommit ?: return
            val before = ic.getTextBeforeCursor(committed.length + 1, 0)?.toString().orEmpty()
            if (before != "$committed ") return
            val (del, text) = GlideCommitPolicy.swapLengths(committed, suggestion.bengali)
            ic.beginBatchEdit()
            ic.deleteSurroundingText(del, 0)
            ic.commitText(text, 1)
            ic.endBatchEdit()
            lastCommittedTextLength = text.length
            lastGlideCommit = suggestion.bengali
            sessionSuggestionTapCount++
            // The explicit pick teaches (BN chips carry the roman; EN don't).
            if (suggestion.phonetic.isNotEmpty()) {
                learnCommittedWordAsync(suggestion.phonetic, suggestion.bengali, explicitChoice = true)
            }
            return
        }

        // S162: the ghost chip IS the typed roman — commit the literal, learn
        // nothing (roman→roman is not a mapping) and keep it out of the
        // Bengali n-grams.
        if (suggestion.source == TypedChipPolicy.TYPED_ROMAN_SOURCE) {
            sessionSuggestionTapCount++
            ic.commitText(suggestion.bengali + " ", 1)
            lastCommittedTextLength = suggestion.bengali.length + 1
            buffer = ""
            suggestions.clear()
            clearCommitCaches()
            return
        }

        if (buffer.isEmpty()) {
            // Feature 4.1: This is a next-word prediction — commit directly with space
            sessionSuggestionTapCount++
            sessionPredictionTapCount++
            ic.commitText(suggestion.bengali + " ", 1)
            lastCommittedTextLength = suggestion.bengali.length + 1
            recordNextWordPairLearning(suggestion.bengali)
            updatePredictions(suggestion.bengali)
        } else {
            // This is a conversion suggestion
            sessionSuggestionTapCount++
            ic.commitText(suggestion.bengali + " ", 1)
            learnCommittedWordAsync(buffer, suggestion.bengali, explicitChoice = true)
            lastCommittedTextLength = suggestion.bengali.length + 1
            buffer = ""
            suggestions.clear()
            clearCommitCaches()
            recordNextWordPairLearning(suggestion.bengali)
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

        // S95: the toggle must ALSO update the tracked letter mode, or every
        // transient-layer return snaps a deliberate EN session back to
        // Bengali (the tester's exact complaint). Decision table lives in
        // LanguageModePolicy with its own pins.
        val modeResult = LanguageModePolicy.globeToggle(keyboardMode.value, letterModeBeforeSymbols)
        letterModeBeforeSymbols = modeResult.letterMode
        keyboardMode.value = modeResult.mode
        resetShiftState()
        englishWordPrefix = ""   // S99: mode change resets the word context
        suggestions.clear()
        // S96: entering EN mode surfaces predictions right away.
        if (modeResult.mode == KeyboardMode.ENGLISH) refreshEnglishSuggestionsAsync()
        log("onGlobePress: mode=${modeResult.mode}")
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
        // S98/S96: keep the strip coherent after the mode change — an
        // in-progress @-token keeps its identity chips, EN mode refreshes
        // its completions.
        if (!maybeShowIdentityAssist() && keyboardMode.value == KeyboardMode.ENGLISH) {
            refreshEnglishSuggestionsAsync()
        }
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
            return
        }
        if (!prefs.getBoolean(PREF_VOICE_TYPING_ENABLED, true)) {
            log("onVoiceInput: disabled in settings")
            voiceInputState.value = VoiceInputState.UNAVAILABLE
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
            return
        }

        if (!prefs.getBoolean(PREF_VOICE_DISCLOSURE_ACCEPTED, false)) {
            log("onVoiceInput: voice disclosure not accepted")
            voiceInputState.value = VoiceInputState.PERMISSION_REQUIRED
            val intent = Intent(this, VoicePermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            return
        }

        // S55 (F-ANDROID-006): the first attempt is ALWAYS the default
        // (online-capable) recognizer unless the user explicitly opted into
        // offline in Settings. Deciding this from ConnectivityManager was the
        // gamble that caused airplane-mode-shaped failures on devices that
        // silently degrade instead of throwing without ACCESS_NETWORK_STATE
        // (removed for privacy — see AndroidManifest.xml) — the recognizer's
        // own onError now drives any offline fallback (see VoiceSessionPolicy).
        voicePreferOfflineForSession = prefs.getBoolean(PREF_VOICE_OFFLINE_PREFERRED, false)
        voiceOfflineRetryUsed = false
        voiceNetworkRetryUsed = false
        voiceOfflineForcedBySession = false

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            log("onVoiceInput: SpeechRecognizer unavailable")
            voiceInputState.value = VoiceInputState.UNAVAILABLE
            return
        }

        commitPendingBuffer()
        voiceInsertionCursor = currentCursorPosition()
        voiceExpectedSelections.clear()
        suggestions.clear()
        voiceInputState.value = VoiceInputState.PROCESSING
        voiceInputLevel.value = 0f
        voiceCancelRequested = false
        voiceStopRequested = false
        // S122: EN mode dictates English; the language is per-session so
        // mid-dictation mode flips can't switch recognizers underneath.
        voiceSessionLanguage =
            if (keyboardMode.value == KeyboardMode.ENGLISH) "en-US" else VOICE_LANGUAGE
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
        voiceCarry.closeTranscript()
        voiceFruitlessRestarts = 0
        voiceBusyRestarts = 0
        voiceWedgeRestarts = 0
        voiceHeardSpeechThisSession = false
        voiceSessionClosedAfterPause = false
        voiceLastSpeechBeganAt = 0L
        cancelVoiceIdleAndPunctuationJobs()
        voiceTokenRefineJob?.cancel()
        voiceTokenRefineJob = null
        voicePartialGeneration++

        startVoiceRecognition()
    }

    private fun startVoiceRecognition() {
        // S120 (tester: "voice typing is very unstable, words repeated"): the
        // old unconditional carry reset here was S56's answer to the swallow
        // risk, but it created the OPPOSITE bug on flaky networks — every
        // error restart committed the live partial, the reset forgot it, and
        // the new session re-heard the same speech and stamped it AGAIN
        // ("ভালোবাসো | ভালোবাসো তুমি | ভালোবাসো তুমি | …" once per error
        // cycle). The carry now survives error restarts under PROBATION
        // (see VoiceCarryPolicy.reconcile): overlap strips, divergence
        // kills the carry — S56's genuinely-new-speech case still wins.
        // Clean restarts (after a final result) and fresh onVoiceInput()
        // sessions still reset the carry at their own sites.
        // S92 (tester: "voice typing is slow after some uses"): NEVER reuse a
        // recognizer instance across sessions. One long-lived client binding
        // accumulates per-session state inside the platform RecognitionService
        // and session starts get progressively slower; every reuse bug in this
        // file's history (S69 wedged restart, S76 outstanding-terminal
        // contract) traced to instance reuse. A fresh bind costs ~100-300ms,
        // absorbed by the existing PROCESSING state and settle delays. This
        // also subsumes the old S76 awaiting-terminal special case — the
        // generation bump in release makes any late callbacks inert (S73).
        if (speechRecognizer != null) releaseSpeechRecognizer()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
            speechRecognizer = it
            // S73: every recognizer instance gets a generation stamp; its
            // listener rejects callbacks once the instance is released, so a
            // destroyed recognizer's late callbacks can never mutate a newer
            // session (production-audit finding).
            recognizerGeneration++
            it.setRecognitionListener(createVoiceRecognitionListener(recognizerGeneration))
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceSessionLanguage)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, voiceSessionLanguage)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, voicePreferOfflineForSession)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, VOICE_COMPLETE_SILENCE_MS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, VOICE_POSSIBLY_COMPLETE_SILENCE_MS)
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                if (voiceSessionEnglish) "Speak in English" else "বাংলায় বলুন"
            )
            // S69: several Google-app/OEM recognizer builds return
            // ERROR_CLIENT or silently drop results without the calling
            // package for attribution — AOSP LatinIME sets it too.
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }

        try {
            log("onVoiceInput: startListening $voiceSessionLanguage offline=$voicePreferOfflineForSession")
            voiceSessionClosedAfterPause = false
            voiceInputState.value = VoiceInputState.PROCESSING
            voiceInputLevel.value = 0f
            armVoiceWatchdog()
            recognizer.startListening(intent)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Voice typing failed to start", e)
            disarmVoiceWatchdog()
            voiceDictationActive = false
            voiceInputState.value = VoiceInputState.ERROR
        }
    }

    /** S55 (F-ANDROID-006): arms a timeout that fires if `startListening()`
     *  gets NO RecognitionListener callback at all — the class of failure the
     *  audit reproduced (a bound RecognitionService that never responds).
     *  Every callback in [createVoiceRecognitionListener] disarms this. */
    private fun armVoiceWatchdog() {
        // S76 (audit): elapsedRealtime — wall clock jumps with NTP/user edits.
        voiceWatchdogDeadlineAt = android.os.SystemClock.elapsedRealtime() + VOICE_WATCHDOG_TIMEOUT_MS
        ensureVoiceWatchdogTicker()
    }

    private fun disarmVoiceWatchdog() {
        voiceWatchdogDeadlineAt = 0L
        voiceWatchdogJob?.cancel()
        voiceWatchdogJob = null
    }

    /** S56: mid-session callbacks re-arm a rolling liveness deadline instead
     *  of just disarming — a session that goes permanently silent after one
     *  early callback (F-ONDEVICE-001) is a wedged recognizer binding, not a
     *  healthy listening state.
     *  S73: deadline WRITE only. The old implementation cancelled and
     *  relaunched a coroutine on every onRmsChanged (10-20/s for the whole
     *  dictation, all on the IME main thread) — one ticker now checks the
     *  deadline once a second instead. */
    private fun pokeVoiceWatchdog() {
        if (voiceWatchdogDeadlineAt != 0L) {
            voiceWatchdogDeadlineAt = android.os.SystemClock.elapsedRealtime() + VOICE_LIVENESS_TIMEOUT_MS
        }
    }

    private fun ensureVoiceWatchdogTicker() {
        if (voiceWatchdogJob?.isActive == true) return
        voiceWatchdogJob = serviceScope.launch {
            while (isActive) {
                delay(VOICE_WATCHDOG_TICK_MS)
                val deadline = voiceWatchdogDeadlineAt
                if (deadline == 0L) break
                if (android.os.SystemClock.elapsedRealtime() >= deadline) {
                    voiceWatchdogDeadlineAt = 0L
                    log("voice: watchdog deadline passed — recognizer silent")
                    onVoiceWatchdogTimeout()
                    break
                }
            }
        }
    }

    private fun onVoiceWatchdogTimeout() {
        // S56 (F-ONDEVICE-001): a wedged binding is recoverable — destroy and
        // recreate the recognizer (fresh service binding un-wedges Google's
        // side) up to the cap, keeping any live partial. Only after the cap
        // does the session end with a visible message.
        if (voiceDictationActive && !voiceStopRequested && !voiceCancelRequested &&
            voiceWedgeRestarts < VOICE_MAX_WEDGE_RESTARTS
        ) {
            voiceWedgeRestarts++
            log("voice: wedge recovery $voiceWedgeRestarts/$VOICE_MAX_WEDGE_RESTARTS — recreating recognizer")
            commitLiveVoicePartialBeforeRestart(error = -1)
            releaseSpeechRecognizer()
            restartVoiceRecognitionSoon(afterError = true)
            return
        }
        voicePartialCommitJob?.cancel()
        voiceRestartJob?.cancel()
        voiceRestartJob = null
        // S55 (review follow-up): a token-refine job from a partial that was
        // still rendering when the watchdog fired would otherwise become an
        // orphaned coroutine that could still patch text after teardown.
        voiceTokenRefineJob?.cancel()
        voiceTokenRefineJob = null
        releaseSpeechRecognizer()
        voiceDictationActive = false
        voiceStopRequested = false
        voiceCancelRequested = false
        voiceHasLiveComposing = false
        voiceInputLevel.value = 0f
        finishVoiceComposingText()
        when (val action = VoiceSessionPolicy.onWatchdogTimeout()) {
            is VoiceSessionPolicy.VoiceAction.ShowMessage -> voiceInputState.value = action.state
            else -> voiceInputState.value = VoiceInputState.WATCHDOG_TIMEOUT
        }
    }

    private fun createVoiceRecognitionListener(generation: Int): RecognitionListener {
        return object : RecognitionListener {
            /** S73: true once this listener's recognizer instance has been
             *  released — its late callbacks must not touch newer sessions. */
            private fun isStale(): Boolean {
                val stale = generation != recognizerGeneration
                if (stale) log("voice: ignoring callback from released recognizer (gen $generation != $recognizerGeneration)")
                return stale
            }

            override fun onReadyForSpeech(params: Bundle?) {
                if (isStale()) return
                pokeVoiceWatchdog()
                log("voice: ready")
                voiceInputState.value = VoiceInputState.LISTENING
            }

            override fun onBeginningOfSpeech() {
                if (isStale()) return
                pokeVoiceWatchdog()
                log("voice: beginning")
                voiceLastSpeechBeganAt = System.currentTimeMillis()
                voiceSpeechRestartedSinceHypothesis = true
                voiceIdleStopJob?.cancel()
                voiceIdleStopJob = null
                voicePunctuationEpoch++
                resolveDeferredVoicePunctuationOnSpeech()
                voiceHeardSpeechThisSession = true
                voiceFruitlessRestarts = 0
                voiceBusyRestarts = 0
                voiceWedgeRestarts = 0
                // S137: one dictation is now MANY sessions — the S69 network
                // ladder's "once per dictation" budget killed dictation after
                // two transient start failures an hour apart. A session that
                // hears speech proves the path healthy: reset the budget.
                voiceNetworkRetryUsed = false
                voiceOfflineRetryUsed = false
                voicePartialCommitJob?.cancel()
                commitVoicePartialForMeasuredPause()
                voiceInputState.value = VoiceInputState.LISTENING
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (isStale()) return
                pokeVoiceWatchdog()
                // S73: RMS fires 10-20x/s — throttle the Compose state write
                // (each one recomposes the mic level UI) to ~15Hz.
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastRmsUiUpdateAt >= 66) {
                    lastRmsUiUpdateAt = now
                    voiceInputLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                }
            }
            override fun onBufferReceived(buffer: ByteArray?) {
                if (isStale()) return
                pokeVoiceWatchdog()
            }
            override fun onEndOfSpeech() {
                if (isStale()) return
                pokeVoiceWatchdog()
                log("voice: end")
                voiceLastSpeechEndedAt = System.currentTimeMillis()
                voiceInputState.value = if (voiceStopRequested) VoiceInputState.STOPPED else VoiceInputState.PROCESSING
                voiceInputLevel.value = 0f
                scheduleVoicePartialCommitAfterPause()
                scheduleVoiceIdleSessionEnd()
            }

            override fun onError(error: Int) {
                if (isStale()) return
                handleVoiceRecognizerError(error)
            }

            override fun onResults(results: Bundle?) {
                if (isStale()) return
                voiceAwaitingTerminal = false
                disarmVoiceWatchdog()
                voicePartialCommitJob?.cancel()
                voiceIdleStopJob?.cancel()
                voiceIdleStopJob = null
                // S73 (production audit): a delayed Latin-token partial refine
                // could land AFTER the final commit and re-append the old
                // partial — the results path never invalidated it (stop,
                // error, and watchdog paths did). Cancel + bump the partial
                // generation so any in-flight refine's guard fails.
                voiceTokenRefineJob?.cancel()
                voiceTokenRefineJob = null
                voicePartialGeneration++
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

                if (BuildConfig.DEBUG) log("voice: raw results n=${phrases.size} first='${phrases.firstOrNull().orEmpty()}'")
                val best = chooseVoiceResult(phrases)?.let { chosen ->
                    val reconciled = voiceCarry.reconcile(chosen)
                    if (reconciled.recognizerReset) closeLiveVoiceSegmentForRecognizerReset()
                    reconciled.owed
                }
                if (voiceSessionClosedAfterPause) {
                    if (best == null || best.isEmpty()) {
                        restartAfterDeliberateSessionEnd("results")
                        return
                    }
                    if (!voiceStopRequested && voiceDictationActive) {
                        // The idle stop ended the session with the utterance
                        // still live: commit its final with a plain space and
                        // let the pause decide the mark (S137 deferred
                        // punctuation), then a fresh session.
                        voiceSessionClosedAfterPause = false
                        voiceHeardSpeechThisSession = true
                        log("voice: idle-stop final='$best' — screen text sealed, punctuation deferred")
                        // The screen already holds the incrementally committed
                        // partials; a final that still owes words is appended
                        // through the same live-diff as a partial, then sealed.
                        commitVoiceLivePartialIncrementally(best)
                        sealLiveVoiceRegion(" ")
                        armDeferredVoicePunctuation()
                        voiceInputLevel.value = 0f
                        suggestions.clear()
                        voiceCarry.closeTranscript()
                        finishVoiceComposingText()
                        restartVoiceRecognitionSoon()
                        return
                    }
                    voiceSessionClosedAfterPause = false
                }
                if (best == null) {
                    // S137 (field trace): Google's speech service ends a
                    // session after ~5s of silence with an EMPTY result list
                    // whenever the last segment was already delivered through
                    // partials — that is the NORMAL end of a paused dictation,
                    // not a failure. The old code turned it into the ERROR
                    // chip and killed the session ("if I pause it cannot pick
                    // voice"). It now walks the same no-speech ladder as
                    // ERROR_NO_MATCH: restart while the user is dictating,
                    // graceful stop after the silence cap.
                    log("voice: empty results — treated as no-speech (session ended on silence)")
                    handleVoiceRecognizerError(SpeechRecognizer.ERROR_NO_MATCH)
                    return
                }

                log("voice: result='$best'")
                voiceHeardSpeechThisSession = true
                if (best.isEmpty()) {
                    log("voice: result already committed by pause timer")
                    voiceCarry.closeTranscript()
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
                    // S92: session over — drop the binding now instead of
                    // holding a stale instance until the next dictation.
                    releaseSpeechRecognizer()
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
                    releaseSpeechRecognizer()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (isStale()) return
                pokeVoiceWatchdog()
                val rawPartial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                // S137: an EMPTY hypothesis while text is live = the recognizer
                // dropped its transcript and is starting over (trace 17:04:01:
                // 'বারবার' → '' → 'এটা …'); treat the next hypothesis like one
                // after a new beginning-of-speech.
                if (rawPartial.isEmpty() && voiceLiveCommittedPartial.isNotEmpty()) {
                    voiceSpeechRestartedSinceHypothesis = true
                }
                val reconciled = if (rawPartial.isEmpty()) null else {
                    voiceCarry.reconcile(rawPartial, speechRestarted = voiceSpeechRestartedSinceHypothesis)
                        .also { voiceSpeechRestartedSinceHypothesis = false }
                }
                val partial = reconciled?.owed.orEmpty()
                if (BuildConfig.DEBUG) log("voice: raw partial='$rawPartial' -> owed='$partial' reset=${reconciled?.recognizerReset == true}")
                // S137: the recognizer started a FRESH hypothesis (its own
                // endpoint after a pause). Whatever the previous hypothesis
                // left on screen is final now — close it before the new
                // segment is rendered, or the new words would be diffed
                // against the old sentence and appended again on every
                // revision (the growing-repeat screenshot).
                if (reconciled?.recognizerReset == true) closeLiveVoiceSegmentForRecognizerReset()
                if (partial.isNotEmpty()) {
                    voiceHeardSpeechThisSession = true
                    log("voice: partial='$partial'")
                    suggestions.clear()
                    renderVoicePartialIncrementally(partial)
                    if (voiceInputState.value == VoiceInputState.PROCESSING && voiceLastSpeechEndedAt > 0L) {
                        scheduleVoicePartialCommitAfterPause()
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
    }

    /** S137: the recognizer-error ladder, shared by onError and the
     *  empty-results end of a paused session (see onResults). */
    private fun handleVoiceRecognizerError(error: Int) {
        voiceAwaitingTerminal = false
        voiceIdleStopJob?.cancel()
        voiceIdleStopJob = null
        disarmVoiceWatchdog()
        log("voice: error=$error")
        voicePartialCommitJob?.cancel()
        voiceInputLevel.value = 0f
        if (voiceCancelRequested) {
            // stopVoiceInput(cancel=true) already fully reset the UI
            // state and text synchronously; a late error callback for
            // an already-canceled session must not override it.
            log("voice: error=$error after cancel, ignoring")
            return
        }
        if (voiceStopRequested) {
            voiceDictationActive = false
            voiceInputState.value = VoiceInputState.STOPPED
            finishVoiceComposingText()
            showVoiceDeleteAction()
            return
        }
        if (!voiceDictationActive) {
            // Stray callback after the session already finished on its
            // own (e.g. a late error racing a just-completed onResults)
            // — nothing to restart or report, the UI already moved on.
            log("voice: error=$error after session already ended, ignoring")
            return
        }
        if (voiceSessionClosedAfterPause &&
            (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                error == SpeechRecognizer.ERROR_CLIENT)
        ) {
            restartAfterDeliberateSessionEnd("error=$error")
            return
        }

        // S55 (F-ANDROID-006): every branch below now comes from
        // VoiceSessionPolicy's pure decision table (unit-pinned in
        // VoiceSessionPolicyTest) instead of the old isNetworkAvailable()
        // gamble — never leaves a listening chip that cannot deliver.
        val action = VoiceSessionPolicy.onError(
            error = error,
            networkRetryUsed = voiceNetworkRetryUsed,
            offlineRetryUsed = voiceOfflineRetryUsed,
            offlineForcedBySession = voiceOfflineForcedBySession,
            fruitlessRestarts = voiceFruitlessRestarts,
            maxFruitlessRestarts = VOICE_MAX_FRUITLESS_RESTARTS,
            busyRestarts = voiceBusyRestarts,
            maxBusyRestarts = VOICE_MAX_BUSY_RESTARTS,
            heardSpeechThisSession = voiceHeardSpeechThisSession
        )
        when (action) {
            VoiceSessionPolicy.VoiceAction.RetryOffline -> {
                voiceOfflineRetryUsed = true
                voicePreferOfflineForSession = true
                voiceOfflineForcedBySession = true
                log("voice: network-class error=$error, retrying once with offline preference")
                commitLiveVoicePartialBeforeRestart(error)
                restartVoiceRecognitionSoon(afterError = true)
            }
            VoiceSessionPolicy.VoiceAction.RetryOnline -> {
                // S69: the ladder's own offline retry hit a missing
                // bn-BD pack — walk back to the online recognizer.
                voicePreferOfflineForSession = false
                voiceOfflineForcedBySession = false
                log("voice: offline pack missing after forced-offline retry (error=$error), returning to online")
                commitLiveVoicePartialBeforeRestart(error)
                restartVoiceRecognitionSoon(afterError = true)
            }
            VoiceSessionPolicy.VoiceAction.RestartSameMode -> {
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    voiceFruitlessRestarts++
                } else if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    voiceBusyRestarts++
                } else if (VoiceSessionPolicy.isNetworkClassError(error)) {
                    // S69: this restart consumed the one plain
                    // network retry; the next network error goes to
                    // the offline ladder step.
                    voiceNetworkRetryUsed = true
                    log("voice: network-class error=$error, plain online retry first")
                }
                commitLiveVoicePartialBeforeRestart(error)
                restartVoiceRecognitionSoon(afterError = true)
            }
            VoiceSessionPolicy.VoiceAction.GracefulStop -> {
                log("voice: graceful stop after error=$error")
                voiceFruitlessRestarts = 0
                voiceDictationActive = false
                voiceInputState.value = VoiceInputState.STOPPED
                finishVoiceComposingText()
            }
            is VoiceSessionPolicy.VoiceAction.ShowMessage -> {
                if (action.state == VoiceInputState.BUSY_GIVEUP) {
                    log("voice: busy-retry cap ($VOICE_MAX_BUSY_RESTARTS) exceeded, giving up")
                }
                voiceBusyRestarts = 0
                voiceDictationActive = false
                voiceInputState.value = action.state
                // S69: mid-session permission loss gets the SAME
                // remediation as the pre-flight gate — launch the
                // permission flow instead of a chip that just fades.
                if (action.state == VoiceInputState.PERMISSION_REQUIRED) {
                    startActivity(
                        Intent(this@BangluIMEService, VoicePermissionActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                // S106: terminal give-up messages are sticky — they
                // stay until the user acts (S69's 6s chip window was
                // still short enough to miss).
            }
        }
    }

    /**
     * S137: the recognizer finalized the previous utterance on its own and
     * started a fresh hypothesis. Close what that utterance left on screen
     * as a committed segment — punctuated by the measured pause like the
     * pause-timer commit would have — WITHOUT extending the carry (the carry
     * was just reset; the old hypothesis is history). Also cancels the
     * pending pause commit so it cannot fire on a snapshot that no longer
     * matches (the 2.8s-vs-3.2s race that left the old sentence live).
     */
    private fun closeLiveVoiceSegmentForRecognizerReset() {
        voicePartialCommitJob?.cancel()
        voicePartialCommitJob = null
        if (voiceLiveCommittedPartial.isEmpty() && voiceCurrentPartial.isEmpty()) {
            deleteVoiceLivePartial()
            return
        }
        val pauseMs = if (voiceLastSpeechEndedAt > 0L) {
            (System.currentTimeMillis() - voiceLastSpeechEndedAt).coerceAtLeast(0L)
        } else 0L
        val mark = when {
            pauseMs >= VOICE_DARI_PAUSE_MS -> if (voiceSessionEnglish) "." else "\u0964"
            pauseMs >= VOICE_COMMA_PAUSE_MS -> ","
            else -> " "
        }
        log("voice: recognizer reset — sealing live segment mark='$mark' pauseMs=$pauseMs live='$voiceLiveCommittedPartial'")
        sealLiveVoiceRegion(mark)
        suggestions.clear()
    }

    /**
     * S137: close the live region WITHOUT re-committing any text. Every
     * partial was already committed incrementally, so the only work is a
     * trailing space plus the mark. Re-running the final through
     * commitVoiceFinalText re-appended the hypothesis whenever the live
     * region had diverged from it (trace 17:04:01 — Google swapped 'বারবার'
     * for 'এটা…' mid-session and the screen held both; the old seal then
     * wrote 'এটা বোঝা যাচ্ছে না' a second time).
     */
    private fun sealLiveVoiceRegion(mark: String) {
        voicePartialGeneration++
        voiceTokenRefineJob?.cancel()
        voiceTokenRefineJob = null
        val ic = currentInputConnection
        if (ic != null && voiceLiveCommittedPartial.isNotEmpty()) {
            moveVoiceCursorToInsertionPoint(ic)
            val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
            if (before.isNotEmpty() && !before.last().isWhitespace()) commitVoiceTextAtInsertion(" ")
            if (mark != " ") stampDeferredVoicePunctuation(mark)
        }
        voiceCommittedText += voiceLiveCommittedPartial
        voiceCurrentPartial = ""
        voiceLiveCommittedPartial = ""
        voiceLiveCommitLength = 0
        voiceLastLivePartialUpdateAt = 0L
        voiceHasLiveComposing = false
    }

    /** S55: shared by every VoiceSessionPolicy retry/restart action — a
     *  partial already visible on screen must not vanish just because the
     *  recognizer is about to be torn down and recreated. */
    private fun commitLiveVoicePartialBeforeRestart(error: Int) {
        val partial = voiceCurrentPartial.trim()
        if (partial.isNotEmpty()) {
            log("voice: committing live partial before restart error=$error text='$partial'")
            voiceCarry.append(partial)
            commitVoiceFinalText(partial, " ")
        }
    }

    private fun stopVoiceInput(cancel: Boolean) {
        disarmVoiceWatchdog()
        cancelVoiceIdleAndPunctuationJobs()
        voiceTokenRefineJob?.cancel()
        voiceTokenRefineJob = null
        voiceRestartJob?.cancel()
        voiceRestartJob = null
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
        if (cancel) recognizer.cancel() else {
            recognizer.stopListening()
            voiceAwaitingTerminal = true
        }
        voiceInputLevel.value = 0f
        if (cancel) suggestions.clear()
        if (voiceInputState.value != VoiceInputState.IDLE) {
            voiceInputState.value = if (cancel) VoiceInputState.IDLE else VoiceInputState.STOPPED
        }
    }

    private fun releaseSpeechRecognizer() {
        disarmVoiceWatchdog()
        // S73: invalidate the released instance's listener — its in-flight
        // callbacks must not mutate whatever session comes next.
        recognizerGeneration++
        // S76: a destroyed instance can never deliver its terminal callback.
        voiceAwaitingTerminal = false
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
                voiceCarry.append(snapshot)
                commitVoiceFinalText(snapshot, "\u0964")
                suggestions.clear()
                endVoiceSessionAfterPause()
            }
        }
    }

    /**
     * S137 (field trace, Google speech service 2026-07): ONE SESSION PER
     * UTTERANCE. Inside a long session the recognizer streams the first
     * utterance instantly, then degrades for every later one — the third
     * came 3.7s late as a lump, the fourth (4.6s of speech) produced no
     * hypothesis at all, others flapped 3→0→3 words. That is the field
     * report "sometimes it picks, sometimes not, I have to speak loud". So
     * when the 3.2s pause commit fires with no new speech under way, the
     * session is ended deliberately; its terminal callback restarts a
     * fresh recognizer, and every utterance gets first-utterance service.
     */
    private fun endVoiceSessionAfterPause() {
        val recognizer = speechRecognizer ?: return
        if (!voiceDictationActive || voiceStopRequested || voiceCancelRequested) return
        voiceSessionClosedAfterPause = true
        voiceAwaitingTerminal = true
        log("voice: pause commit — ending this session for a fresh one")
        try {
            recognizer.stopListening()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "stopListening after pause commit failed", e)
            voiceSessionClosedAfterPause = false
        }
    }

    /** S137: the deliberately ended session reported its end — restart
     *  clean (no carry, no fruitless/busy counting). */
    private fun restartAfterDeliberateSessionEnd(source: String) {
        voiceSessionClosedAfterPause = false
        log("voice: session ended by pause commit ($source) — fresh session")
        // S137: Google answers stopListening() with an EMPTY final — the
        // text on screen IS the final. Seal it (trailing space only; it is
        // already committed) and let the pause decide its mark, or the
        // sentence would end with no দাঁড়ি at all.
        if (voiceLiveCommittedPartial.isNotEmpty() || voiceCurrentPartial.isNotEmpty()) {
            sealLiveVoiceRegion(" ")
            armDeferredVoicePunctuation()
        }
        voiceCarry.closeTranscript()
        voiceCurrentPartial = ""
        deleteVoiceLivePartial()
        finishVoiceComposingText()
        restartVoiceRecognitionSoon()
    }

    /**
     * S137: end the session [VOICE_IDLE_SESSION_END_MS] after speech stops
     * if no new speech has begun — before the recognizer's own endpoint,
     * so the next utterance gets a fresh session (see
     * [endVoiceSessionAfterPause] for the field evidence).
     */
    private fun scheduleVoiceIdleSessionEnd() {
        voiceIdleStopJob?.cancel()
        val endedAt = voiceLastSpeechEndedAt
        voiceIdleStopJob = serviceScope.launch {
            delay(VOICE_IDLE_SESSION_END_MS)
            if (
                imeSessionVisible && voiceDictationActive &&
                !voiceStopRequested && !voiceCancelRequested &&
                voiceLastSpeechEndedAt == endedAt &&
                voiceLastSpeechBeganAt <= endedAt &&
                !voiceSessionClosedAfterPause
            ) {
                log("voice: idle ${VOICE_IDLE_SESSION_END_MS}ms after speech — ending session early")
                endVoiceSessionAfterPause()
            }
        }
    }

    private fun cancelVoiceIdleAndPunctuationJobs() {
        voiceIdleStopJob?.cancel()
        voiceIdleStopJob = null
        voicePunctuationJob?.cancel()
        voicePunctuationJob = null
        voicePunctuationPending = false
    }

    /** S137 deferred punctuation: the segment was committed with a trailing
     *  space; a দাঁড়ি lands once the pause reaches VOICE_DARI_PAUSE_MS with
     *  no new speech (a comma is decided at the next onBeginningOfSpeech). */
    private fun armDeferredVoicePunctuation() {
        voicePunctuationJob?.cancel()
        voicePunctuationPending = true
        val epoch = voicePunctuationEpoch
        val elapsed = (System.currentTimeMillis() - voiceLastSpeechEndedAt).coerceAtLeast(0L)
        voicePunctuationJob = serviceScope.launch {
            delay((VOICE_DARI_PAUSE_MS - elapsed).coerceAtLeast(0L))
            if (voicePunctuationPending && voicePunctuationEpoch == epoch &&
                imeSessionVisible && voiceDictationActive && !voiceStopRequested && !voiceCancelRequested
            ) {
                voicePunctuationPending = false
                stampDeferredVoicePunctuation(if (voiceSessionEnglish) "." else "\u0964")
            }
        }
    }

    /** New speech began: the pause length is known — comma or দাঁড়ি. */
    private fun resolveDeferredVoicePunctuationOnSpeech() {
        if (!voicePunctuationPending) return
        voicePunctuationPending = false
        voicePunctuationJob?.cancel()
        voicePunctuationJob = null
        val pauseMs = (System.currentTimeMillis() - voiceLastSpeechEndedAt).coerceAtLeast(0L)
        val mark = when {
            pauseMs >= VOICE_DARI_PAUSE_MS -> if (voiceSessionEnglish) "." else "\u0964"
            pauseMs >= VOICE_COMMA_PAUSE_MS -> ","
            else -> return
        }
        stampDeferredVoicePunctuation(mark)
    }

    /** Replace the trailing space of the last committed segment with
     *  "[mark] " — equality-guarded so a sent/edited field is never touched. */
    private fun stampDeferredVoicePunctuation(mark: String) {
        val ic = currentInputConnection ?: return
        moveVoiceCursorToInsertionPoint(ic)
        val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        if (!before.endsWith(" ")) return
        val prev = before.dropLast(1).lastOrNull() ?: return
        if (prev.isWhitespace() || prev in "\u0964\u0965.?!,") return
        log("voice: deferred punctuation '$mark'")
        ic.beginBatchEdit()
        ic.deleteSurroundingText(1, 0)
        ic.commitText("$mark ", 1)
        ic.endBatchEdit()
        voiceInsertionCursor = voiceInsertionCursor?.plus(mark.length)
        expectVoiceSelection(voiceInsertionCursor)
        currentVoiceSessionCommitLength += mark.length
        lastVoiceCommitLength = currentVoiceSessionCommitLength
    }

    private fun commitVoicePartialForMeasuredPause() {
        val partial = voiceCurrentPartial.trim()
        if (partial.isEmpty() || voiceLastSpeechEndedAt <= 0L) return
        val pauseMs = (System.currentTimeMillis() - voiceLastSpeechEndedAt).coerceAtLeast(0L)
        if (pauseMs < VOICE_COMMA_PAUSE_MS) return
        val mark = when {
            pauseMs >= VOICE_DARI_PAUSE_MS -> if (voiceSessionEnglish) "." else "\u0964"
            else -> ","
        }
        log("voice: measured pause commit mark='$mark' pauseMs=$pauseMs text='$partial'")
        voiceCarry.append(partial)
        commitVoiceFinalText(partial, mark)
        suggestions.clear()
    }

    /**
     * S55 (F-ANDROID-006 / trace §4): renders the RAW recognizer partial
     * instantly using the same zero-I/O rule-only preview the keystroke path
     * uses (never a synchronous SQLite/dictionary lookup on the callback
     * thread), then — only if the partial actually contains a Latin token —
     * kicks off ONE coalesced async job that computes the dictionary-quality
     * conversion off Dispatchers.Default and re-renders if this is still the
     * current partial (buffer-guard via [voicePartialGeneration], exactly the
     * `buffer == snapshot` idiom updateComposingAsync uses at :508-546). The
     * correction reuses [commitVoiceLivePartialIncrementally]'s existing
     * diff/replace logic — no new text-patching machinery needed.
     */
    private fun renderVoicePartialIncrementally(rawPartial: String) {
        voicePartialGeneration++
        val generation = voicePartialGeneration

        val instantPartial = normalizeVoiceSegment(rawPartial, useInstantPreview = true)
        if (instantPartial.isEmpty()) return

        voiceCurrentPartial = instantPartial
        commitVoiceLivePartialIncrementally(instantPartial)

        if (voiceSessionEnglish || !containsLatinToken(rawPartial)) return
        val sessionToken = imeTextSessionToken
        voiceTokenRefineJob?.cancel()
        voiceTokenRefineJob = serviceScope.launch {
            val refinedPartial = try {
                withContext(engineLane) { normalizeVoiceSegment(rawPartial, useInstantPreview = false) }
            } catch (e: Throwable) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Voice partial refine failed for '$rawPartial'", e)
                return@launch
            }
            if (
                imeSessionVisible && voiceDictationActive &&
                sessionToken == imeTextSessionToken &&
                voicePartialGeneration == generation &&
                refinedPartial.isNotEmpty() && refinedPartial != voiceCurrentPartial
            ) {
                voiceCurrentPartial = refinedPartial
                commitVoiceLivePartialIncrementally(refinedPartial)
            }
        }
    }

    private fun commitVoiceLivePartialIncrementally(partial: String) {
        val previous = voiceLiveCommittedPartial
        // S56: word-level revision law lives in VoicePartialDiff (unit-pinned).
        // A non-prefix hypothesis deletes AT MOST the diverging word tail; a
        // fresh segment (no common word) appends and deletes nothing — the old
        // whole-region replace erased entire sentences after long dictation.
        val patch = VoicePartialDiff.diff(previous, partial) ?: return
        when {
            patch.deleteCount == 0 -> {
                commitVoiceTextAtInsertion(patch.insert, ensureBoundary = previous.isEmpty())
                voiceLiveCommittedPartial = patch.newLiveText
                voiceLiveCommitLength = patch.newLiveText.length
            }
            replaceVoiceLiveTail(patch) -> {
                voiceLiveCommittedPartial = patch.newLiveText
                voiceLiveCommitLength = patch.newLiveText.length
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

    /** S56: bounded tail replacement — deletes exactly [VoicePartialDiff.Patch.deleteCount]
     *  chars (the diverging word tail), equality-guarded against the editor
     *  text so a moved cursor or host-app edit can never be clobbered. */
    private fun replaceVoiceLiveTail(patch: VoicePartialDiff.Patch): Boolean {
        val ic = currentInputConnection ?: return false
        val previous = voiceLiveCommittedPartial
        if (patch.deleteCount <= 0 || patch.deleteCount > previous.length) return false

        moveVoiceCursorToInsertionPoint(ic)
        val tail = previous.takeLast(patch.deleteCount)
        val before = ic.getTextBeforeCursor(patch.deleteCount, 0)?.toString().orEmpty()
        if (before != tail) return false

        ic.finishComposingText()
        ic.deleteSurroundingText(patch.deleteCount, 0)
        voiceInsertionCursor = voiceInsertionCursor?.minus(patch.deleteCount)?.coerceAtLeast(0)
        expectVoiceSelection(voiceInsertionCursor)
        currentVoiceSessionCommitLength =
            (currentVoiceSessionCommitLength - patch.deleteCount).coerceAtLeast(0)
        if (patch.insert.isNotEmpty()) {
            ic.commitText(patch.insert, 1)
            ic.finishComposingText()
            voiceInsertionCursor = voiceInsertionCursor?.plus(patch.insert.length)
            expectVoiceSelection(voiceInsertionCursor)
            currentVoiceSessionCommitLength += patch.insert.length
        }
        lastVoiceCommitLength = currentVoiceSessionCommitLength
        return true
    }

    private fun commitVoiceTextAtInsertion(text: String, ensureBoundary: Boolean = false) {
        if (text.isEmpty()) return
        val ic = currentInputConnection ?: return
        moveVoiceCursorToInsertionPoint(ic)
        ic.finishComposingText()
        // S43: NEW voice segments were landing glued to the previous text
        // (তারপরইউটিউব…) whenever the earlier segment ended without a
        // trailing space. Segment starts guarantee a boundary; mid-segment
        // suffix continuations never pass ensureBoundary.
        val before = if (ensureBoundary) ic.getTextBeforeCursor(1, 0)?.toString().orEmpty() else ""
        val out = if (
            ensureBoundary && before.isNotEmpty() &&
            !before.last().isWhitespace() && !text.first().isWhitespace()
        ) " $text" else text
        ic.commitText(out, 1)
        ic.finishComposingText()
        voiceInsertionCursor = voiceInsertionCursor?.plus(out.length)
        expectVoiceSelection(voiceInsertionCursor)
        currentVoiceSessionCommitLength += out.length
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
        expectVoiceSelection(voiceInsertionCursor)
        currentVoiceSessionCommitLength = (currentVoiceSessionCommitLength - previous.length).coerceAtLeast(0)
        ic.commitText(replacement, 1)
        ic.finishComposingText()
        voiceInsertionCursor = voiceInsertionCursor?.plus(replacement.length)
        expectVoiceSelection(voiceInsertionCursor)
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
        // S73: a final commit supersedes any in-flight partial refine — bump
        // the generation so a slow refine's guard fails instead of
        // re-appending the last partial after this commit (covers the
        // pause-commit path; onResults also cancels the job directly).
        // S76 (audit): bumped BEFORE the voice-command early return too — a
        // spoken command ends the segment just as finally as text does.
        voicePartialGeneration++

        val command = handleVoiceCommand(segment)
        if (command) return

        val cleanSegment = normalizeVoiceSegment(segment, useInstantPreview = true)
        if (cleanSegment.isEmpty()) return

        val ic = currentInputConnection ?: return
        // S107: sentence punctuation (দাঁড়ি/comma) is an end-of-message
        // affordance — with the insertion point mid-text (cursor-placed edit)
        // a pause mark would be stamped into the middle of an existing
        // sentence. Every commit path funnels through here, so gate once.
        moveVoiceCursorToInsertionPoint(ic)
        val textAfterInsertion = ic.getTextAfterCursor(4, 0)?.toString().orEmpty()
        val effectivePunctuation =
            VoiceAnchorPolicy.punctuationForInsertion(punctuation, textAfterInsertion.isNotBlank())
        val committed = punctuateVoiceSegment(cleanSegment, effectivePunctuation)

        val livePartial = voiceLiveCommittedPartial
        if (livePartial.isNotEmpty()) {
            // S76 (tester: a দাঁড়ি appears in the EMPTY field after sending a
            // dictated message): this commit can arrive from the 3.2s pause
            // timer AFTER the user already hit Send — the field is empty (or
            // holds new text), yet the append path would still stamp the
            // trailing "। " into it. If the editor no longer ends with the
            // live dictation text, the user has moved on: drop the commit
            // and clear the live-tracking state instead of writing anything.
            moveVoiceCursorToInsertionPoint(ic)
            val editorTail = ic.getTextBeforeCursor(livePartial.length, 0)?.toString().orEmpty()
            if (editorTail != livePartial) {
                log("voice: final commit dropped — editor no longer ends with the live partial (field sent/cleared)")
                voiceCurrentPartial = ""
                voiceLiveCommittedPartial = ""
                voiceLiveCommitLength = 0
                voiceLastLivePartialUpdateAt = 0L
                voiceHasLiveComposing = false
                return
            }
            // S56: same word-level revision law as the partial renderer — a
            // final transcript scoped to the recognizer's LAST segment must
            // never replace (and thereby erase) the whole live sentence.
            val patch = VoicePartialDiff.diff(livePartial, committed)
            when {
                patch == null -> {
                    // Identical, or a final SHORTER than what's on screen —
                    // keep the screen text (data preservation beats transcript
                    // fidelity for already-visible words).
                }
                patch.deleteCount == 0 -> {
                    if (patch.insert.isNotEmpty()) commitVoiceTextAtInsertion(patch.insert)
                }
                replaceVoiceLiveTail(patch) -> {
                    // Tail-replaced the diverging words with the final form.
                }
                else -> {
                    log("voice: final does not match live partial, appending final safely")
                    commitVoiceTextAtInsertion(committed, ensureBoundary = true)
                }
            }
        } else {
            moveVoiceCursorToInsertionPoint(ic)
            deleteVoiceLivePartial()
            commitVoiceTextAtInsertion(committed, ensureBoundary = true)
        }

        voiceCommittedText += committed
        voiceCurrentPartial = ""
        voiceLiveCommittedPartial = ""
        voiceLiveCommitLength = 0
        voiceLastLivePartialUpdateAt = 0L
        voiceHasLiveComposing = false
        voiceLastSegmentText = committed
        lastVoiceCommitLength = committed.length

        // S55 (trace §4): the instant preview above is zero-I/O and may be
        // lower quality than the dictionary-backed conversion (same trade-off
        // as the composing preview vs. space-commit). Refine off-thread and
        // patch the just-committed text — but ONLY if the editor still ends
        // with exactly what we committed (same data-loss guard as
        // deleteLastVoiceCommit): further speech, an app switch, or the user
        // editing the text must never be silently overwritten.
        if (!voiceSessionEnglish && containsLatinToken(segment)) {
            val expectedCommitted = committed
            val sessionToken = imeTextSessionToken
            serviceScope.launch {
                val refinedSegment = try {
                    withContext(engineLane) { normalizeVoiceSegment(segment, useInstantPreview = false) }
                } catch (e: Throwable) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Voice final refine failed for '$segment'", e)
                    return@launch
                }
                if (refinedSegment.isEmpty() || refinedSegment == cleanSegment) return@launch
                val refinedCommitted = punctuateVoiceSegment(refinedSegment, effectivePunctuation)
                if (refinedCommitted == expectedCommitted) return@launch
                // S55 (review follow-up): same guard set renderVoicePartialIncrementally's
                // refine uses (imeSessionVisible + voiceDictationActive + session
                // token) — a session that ended or moved on must not still be
                // patched by a stale correction.
                if (!imeSessionVisible || !voiceDictationActive || sessionToken != imeTextSessionToken) {
                    log("voice: final refine skipped — IME session ended or changed")
                    return@launch
                }
                val patchIc = currentInputConnection ?: return@launch
                val before = patchIc.getTextBeforeCursor(expectedCommitted.length, 0)?.toString().orEmpty()
                if (before != expectedCommitted) {
                    log("voice: final refine skipped — editor no longer ends with the committed segment")
                    return@launch
                }
                patchIc.deleteSurroundingText(expectedCommitted.length, 0)
                // S107: the anchor tracked the instant commit's length — a
                // refined replacement of different length drifted it, so the
                // NEXT segment's setSelection landed inside the text.
                voiceInsertionCursor = voiceInsertionCursor
                    ?.minus(expectedCommitted.length)?.coerceAtLeast(0)
                expectVoiceSelection(voiceInsertionCursor)
                patchIc.commitText(refinedCommitted, 1)
                voiceInsertionCursor = voiceInsertionCursor?.plus(refinedCommitted.length)
                expectVoiceSelection(voiceInsertionCursor)
                if (voiceLastSegmentText == expectedCommitted) {
                    voiceLastSegmentText = refinedCommitted
                    lastVoiceCommitLength = refinedCommitted.length
                }
            }
        }
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
        // S107: a no-op move produces no onUpdateSelection callback — pushing
        // an expected position for it would leave a guaranteed-stale entry.
        if (extracted.selectionStart == safeCursor && extracted.selectionEnd == safeCursor) return
        ic.setSelection(safeCursor, safeCursor)
        expectVoiceSelection(safeCursor)
    }

    /** S107: record the caret position a voice write of ours is about to
     *  produce, so VoiceAnchorPolicy can tell it apart from a user move. */
    private fun expectVoiceSelection(position: Int?) {
        position ?: return
        if (!voiceDictationActive) return
        voiceExpectedSelections.addLast(position)
        while (voiceExpectedSelections.size > 16) voiceExpectedSelections.removeFirst()
    }

    /** S107: the user intentionally moved the caret during dictation — the
     *  words already on screen stay where they are, the current segment is
     *  closed as auto-committed (so later hypotheses strip it instead of
     *  re-inserting it), and new speech flows to the new position. */
    private fun reanchorVoiceInsertion(position: Int) {
        log("voice: re-anchoring dictation to $position (user cursor move)")
        voicePartialGeneration++
        voicePartialCommitJob?.cancel()
        voicePartialCommitJob = null
        voiceTokenRefineJob?.cancel()
        voiceTokenRefineJob = null
        voiceCarry.append(voiceCurrentPartial)
        voiceCurrentPartial = ""
        voiceLiveCommittedPartial = ""
        voiceLiveCommitLength = 0
        voiceHasLiveComposing = false
        voiceExpectedSelections.clear()
        voiceInsertionCursor = position
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
        // S55: instant preview only — this just picks/displays alternatives,
        // the chosen phrase is fully (re-)normalized by commitVoiceFinalText.
        val normalized = phrases.map { normalizeVoiceSegment(it, useInstantPreview = true) }.filter { it.isNotEmpty() }
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
        // Same ladder as the measured-pause commit: the recognizer can finalize
        // a segment on its own 1.8s possibly-complete silence, and that path
        // must not silently drop the comma/dari the pause has earned.
        // S42: a user-requested STOP must not stamp a dari on the sentence —
        // testers got । after every dictation. Only a genuinely long
        // pause earns sentence-final punctuation now.
        return when {
            pauseMs >= VOICE_DARI_PAUSE_MS -> if (voiceSessionEnglish) "." else "\u0964"
            pauseMs >= VOICE_COMMA_PAUSE_MS -> ","
            else -> " "
        }
    }

    /**
     * @param useInstantPreview S55 (F-ANDROID-006 / trace §4): true = rule-only,
     *   zero-I/O ([SmartEngineAdapter.convertForInstantPreview], the same
     *   function the keystroke path uses for its sync echo) — safe to call on
     *   the RecognitionListener's callback thread with no dictionary/SQLite
     *   work. false = the full dictionary-backed [SmartEngineAdapter.convertWord]
     *   — callers MUST run this off the main thread (Dispatchers.Default);
     *   see renderVoicePartialIncrementally / commitVoiceFinalText's async
     *   refine passes, which are the only false-callers.
     */
    private fun normalizeVoiceSegment(segment: String, useInstantPreview: Boolean): String {
        return segment
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace(" ।", "।")
            .replace(" ,", ",")
            .replace(" ?", "?")
            .replace(" !", "!")
            .let { if (voiceSessionEnglish) it else normalizeVoiceLatinTokens(it, useInstantPreview) }
    }

    // S71: token normalization extracted to VoiceTextNormalizer (unit-pinned).
    // The old inline version used text.split(Regex("(\\s+)")).joinToString("")
    // — JS split semantics; Kotlin discards the captured delimiters, so ONE
    // Latin token in a segment glued the entire sentence (tester screenshot:
    // এটাকেনহচ্ছেএটাকেনবারবার…), and the glued text then re-appended as a
    // "fresh segment" — the duplication half of the same report.
    private fun normalizeVoiceLatinTokens(text: String, useInstantPreview: Boolean): String =
        VoiceTextNormalizer.normalizeLatinTokens(text) { lower ->
            if (useInstantPreview) {
                SmartEngineAdapter.convertForInstantPreview(lower)
            } else {
                SmartEngineAdapter.convertWord(lower).bengali
            }
        }

    private fun containsLatinToken(text: String): Boolean = VoiceTextNormalizer.containsLatinToken(text)

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
        // S120: a CLEAN restart follows a final result — that transcript is
        // closed and committed, so the next session starts with no carry
        // (the original S56 contract). An ERROR restart interrupted a live
        // utterance mid-flight: the carry (everything this utterance already
        // committed) survives, probationary, so the re-heard overlap strips
        // instead of duplicating.
        if (afterError) voiceCarry.armProbation() else voiceCarry.closeTranscript()
        voiceInputState.value = VoiceInputState.LISTENING
        voiceInputLevel.value = 0f
        voiceRestartJob?.cancel()
        voiceRestartJob = serviceScope.launch {
            // S69: unbind the recognizer FIRST, then let the delay give
            // Google's RecognitionService time to settle before rebinding.
            // The old order (delay → release+create in the same main-thread
            // frame) matched the on-device audit's "session 2 cancelled at
            // 0.6s, session 3 dead mic" signature — the classic 'voice works
            // once, then never again'. S92: unconditional — sessions never
            // reuse instances anymore, so every restart gets the full settle
            // window, not just error restarts.
            releaseSpeechRecognizer()
            // S137: the settle window is NOT optional — a restart with no
            // delay after destroy() made Google's service answer the new
            // session with ERROR_SERVER_DISCONNECTED (trace 17:03:30, x3).
            delay(if (afterError) VOICE_ERROR_RESTART_DELAY_MS else VOICE_RESTART_DELAY_MS)
            if (
                imeSessionVisible &&
                voiceDictationActive &&
                !voiceStopRequested &&
                !voiceCancelRequested
            ) {
                if (!afterError) {
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
        composingJob?.cancel()
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
        // Data-loss guard: only delete when the editor still ends with the
        // exact segment voice inserted. A long multi-pause dictation must
        // never vanish to one mistap, and host-app edits invalidate the undo.
        val segment = voiceLastSegmentText
        if (segment.isNotEmpty()) {
            val before = ic.getTextBeforeCursor(segment.length, 0)?.toString().orEmpty()
            if (before != segment) {
                log("voice: delete chip skipped — editor no longer ends with the last segment")
                suggestions.clear()
                lastVoiceCommitLength = 0
                voiceLastSegmentText = ""
                return
            }
        }
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
        voiceCarry.closeTranscript()
        lastVoiceCommitLength = 0
        voiceLastSegmentText = ""
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
        voiceCarry.closeTranscript()
        if (suggestions.any { it.source == VOICE_DELETE_SOURCE || it.tier == "voice_action" }) {
            suggestions.clear()
        }
    }

    /** S106 (tester: "voice not working, no error message"): trouble panels
     *  are STICKY. The old 1.8s auto-wipe meant the explanation vanished
     *  before anyone read it — every silent-failure report traces to it.
     *  A trouble panel now stays until the user acts: retry, dismiss, any
     *  keypress, or the IME session ending (cleanupImeSession). */
    private fun isVoiceTroubleState(s: VoiceInputState): Boolean =
        s == VoiceInputState.ERROR ||
            s == VoiceInputState.PERMISSION_REQUIRED ||
            s == VoiceInputState.UNAVAILABLE ||
            s == VoiceInputState.WATCHDOG_TIMEOUT ||
            s == VoiceInputState.OFFLINE_PACK_MISSING ||
            s == VoiceInputState.BUSY_GIVEUP

    private fun dismissVoiceTroubleOnUserAction() {
        if (!voiceDictationActive && isVoiceTroubleState(voiceInputState.value)) {
            voiceInputState.value = VoiceInputState.IDLE
        }
    }

    // ── Emoji Panel ──────────────────────────────────────────────────────────

    private fun onEmojiClick(emoji: String) {
        val ic = currentInputConnection ?: return
        commitPendingBuffer()
        val isPhrase = BanglaPhrases.isPhrase(emoji)
        // S57: quick phrases are full sentences, not emoji — they don't enter
        // the recent-emoji row.
        if (!isPhrase) rememberRecentEmoji(emoji)

        ic.commitText(emoji, 1)
        lastCommittedTextLength = emoji.length
        sessionEmojiCommitCount++
        if (isPhrase) sessionStickerCommitCount++
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
        openEmojiPanel(initialCategory = EmojiData.PHRASES_CATEGORY_INDEX)
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
        clipboardTransientItem = null
        if (clipboardFieldIsPrivate || !clipboardHistoryEnabled.value) {
            // S138: private field or history off — stored history is neither
            // loaded nor shown; only the current clip, one-shot, never a
            // clip the source app flagged sensitive.
            clipboardTransientItem = readSystemClipboard()?.takeIf { !it.second }?.first
        } else {
            ensureClipboardHistoryLoaded()
            pruneClipboardHistory()
            captureCurrentSystemClipboard()
        }
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
    private fun updatePredictions(committedBengali: String, replacesLast: Boolean = false) {
        if (!replacesLast && committedBengali != lastCommittedBengali) {
            secondLastCommittedBengali = lastCommittedBengali
        }
        lastCommittedBengali = committedBengali
        if (suggestionsAllowedForCurrentInput() && buffer.isEmpty() && keyboardMode.value == KeyboardMode.BANGLU) {
            // S70: predictions are Bengali-only by design — for a non-Bengali
            // commit (English word + দাঁড়ি) the async round-trip is guaranteed
            // empty, and its clear→refill made the strip visibly flicker and
            // swap identity right after the dot (tester: "dot after english
            // shows a color change"). Settle on the punctuation bar directly.
            if (committedBengali.none { isBengaliChar(it) }) {
                suggestionJob?.cancel()
                composingJob?.cancel()
                if (suggestions.none { it.tier == "punctuation" }) showGapPunctuationSuggestions()
                return
            }
            suggestionJob?.cancel()
            composingJob?.cancel()
            suggestions.clear()
            val snapshot = committedBengali
            val prev2Snapshot = secondLastCommittedBengali
            suggestionJob = serviceScope.launch {
                val predictions = withContext(engineLane) {
                    SmartEngineAdapter.getNextWordPredictions(prev2Snapshot, snapshot, 4)
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
        if (deleteEditorSelectionIfAny(ic)) return

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

        // Delete word: find previous word boundary (S136: any whitespace —
        // tab, newline, NBSP — not only the ASCII space).
        val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: return
        val trimmed = before.trimEnd()
        val lastSpace = trimmed.indexOfLast { it.isWhitespace() }
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

    /**
     * S168 (audit P2-5): steps by user-visible cluster from a 32-char window
     * on the relevant side of the caret (CursorStepPolicy) — no whole-document
     * extraction per 60 ms hold tick, and no caret parked inside কি / ক্ষ.
     * Needs the caret position the host last reported; a range selection or
     * an unknown caret falls back to the DPAD key event.
     */
    private fun moveCursorBySelection(ic: InputConnection, direction: Int): Boolean {
        val caret = editorSelEnd
        if (caret < 0 || editorSelStart != editorSelEnd) return false
        val step = if (direction > 0) {
            val after = ic.getTextAfterCursor(CursorStepPolicy.WINDOW_CHARS, 0)?.toString() ?: return false
            CursorStepPolicy.rightStep(after)
        } else {
            val before = ic.getTextBeforeCursor(CursorStepPolicy.WINDOW_CHARS, 0)?.toString() ?: return false
            CursorStepPolicy.leftStep(before)
        }
        if (step == 0) return true
        val target = (if (direction > 0) caret + step else caret - step).coerceAtLeast(0)
        // Optimistic: hold-repeat ticks arrive faster than onUpdateSelection.
        editorSelStart = target
        editorSelEnd = target
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
        val cached = cachedCommitResult?.takeIf { cachedCommitInput == phonetic }
        if (cached != null) {
            log("commitBufferedWordFast: committing '${cached.bengali}' cached=true")
            ic.commitText(cached.bengali + appendText, 1)
            lastCommittedTextLength = cached.bengali.length + appendText.length
            sessionBangluWordCommitCount++
            maybeOfferAutoCorrectUndo(phonetic, visibleBeforeCommit, cached.bengali, appendText)
            learnCommittedWordAsync(
                phonetic,
                cached.bengali,
                learnAsWord = cached.source == ResolutionSource.CLEAN_TRANSLITERATION
            )
            buffer = ""
            suggestions.clear()
            clearCommitCaches()
            recordNextWordPairLearning(cached.bengali)
            updatePredictions(cached.bengali)
            return
        }

        // S32: no prepared conversion — the space landed inside the conversion
        // window (fast typing on a slow device, exactly the case that used to
        // run the full SQLite+trie conversion ON the UI thread and made the
        // spacebar feel dead). Commit what is on screen RIGHT NOW, then let
        // the authoritative conversion reconcile off the UI thread: it may
        // replace the word only while the editor still ends with exactly what
        // we committed and no new word has started.
        val committedNow = visibleBeforeCommit
            ?: composingVisibleText.takeIf { composingInput == phonetic && it.isNotEmpty() }
            ?: runCatching { SmartEngineAdapter.convertForInstantPreview(phonetic) }.getOrDefault(phonetic)
        log("commitBufferedWordFast: fast-committing visible '$committedNow', reconcile pending")
        ic.commitText(committedNow + appendText, 1)
        lastCommittedTextLength = committedNow.length + appendText.length
        sessionBangluWordCommitCount++
        buffer = ""
        suggestions.clear()
        clearCommitCaches()
        // Pair learning needs the word BEFORE this one; updatePredictions
        // overwrites it, so capture first and record in the reconcile step.
        val previousWord = lastCommittedBengali
        // S108: the conversion context too — updatePredictions mutates both
        // fields before the reconcile coroutine runs its conversion.
        val contextPrev2 = secondLastCommittedBengali
        val sessionToken = imeTextSessionToken
        // Context/predictions use the visible word immediately; the reconcile
        // below re-runs them with the authoritative word if it differs.
        updatePredictions(committedNow)
        serviceScope.launch {
            val result = withContext(engineLane) {
                safeConvertWithContext(phonetic, prev2 = contextPrev2, prev1 = previousWord)
            }
            reconcileFastCommit(phonetic, committedNow, previousWord, sessionToken, result, appendText)
        }
    }

    /**
     * S32 second half: the authoritative conversion finished after the word was
     * already committed from its on-screen preview. Runs on the main thread.
     * Replacement is triple-guarded: still in Banglu mode, no new word being
     * composed, and the editor text still ends with exactly what we committed —
     * any user action in between (next word, backspace, danda double-space,
     * field switch, cursor jump) makes at least one guard fail and the visible
     * text stays untouched.
     */
    private fun reconcileFastCommit(
        phonetic: String,
        committedNow: String,
        previousWord: String,
        sessionToken: Int,
        result: ConversionResult,
        appendText: String
    ) {
        var finalWord = committedNow
        if (result.bengali != committedNow) {
            val ic = currentInputConnection
            val expected = committedNow + appendText
            // S70: a punctuation key right after the fast commit appends দাঁড়ি/
            // comma to the editor before this reconcile lands, so the plain
            // endsWith(expected) guard could NEVER match dot-terminated words
            // — they permanently kept the preview spelling while
            // space-terminated words self-corrected. Tolerate exactly one
            // trailing tight-punctuation character and preserve it.
            val before = ic?.getTextBeforeCursor(expected.length + 4, 0)?.toString()
            val trailing = when {
                before == null -> null
                before.endsWith(expected) -> ""
                before.length > expected.length &&
                    isBanglaTightPunctuation(before.last()) &&
                    before.dropLast(1).endsWith(expected) -> before.last().toString()
                else -> null
            }
            if (ic != null &&
                sessionToken == imeTextSessionToken &&
                buffer.isEmpty() &&
                keyboardMode.value == KeyboardMode.BANGLU &&
                !rawCommitInputMode &&
                trailing != null
            ) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(expected.length + trailing.length, 0)
                ic.commitText(result.bengali + appendText + trailing, 1)
                ic.endBatchEdit()
                lastCommittedTextLength = result.bengali.length + appendText.length + trailing.length
                maybeOfferAutoCorrectUndo(phonetic, committedNow, result.bengali, appendText)
                finalWord = result.bengali
                log("reconcileFastCommit: '$committedNow' -> '${result.bengali}' (trailing='$trailing')")
            }
        }
        // Learning always uses the ENGINE result, never a preview the engine
        // didn't rank first — recording a passively committed preview as a
        // user preference is exactly the S26 poisoning bug.
        learnCommittedWordAsync(
            phonetic,
            result.bengali,
            learnAsWord = result.source == ResolutionSource.CLEAN_TRANSLITERATION
        )
        recordNextWordPairLearning(finalWord, previousWord)
        // replacesLast: committedNow was a transient preview of the SAME word,
        // so the two-word context must not shift it into the second slot.
        if (finalWord != committedNow) updatePredictions(finalWord, replacesLast = true)
    }

    /**
     * Feature 4.1b: learn the (previous, committed) Bengali pair for
     * personalized next-word prediction. Must run after commitText and BEFORE
     * updatePredictions (which overwrites lastCommittedBengali).
     *
     * lastCommittedBengali is never reset on cursor jumps or field switches,
     * so the editor text itself is the adjacency oracle: record only when the
     * text before the cursor actually ends with "previous committed".
     */
    private fun recordNextWordPairLearning(committed: String, previousOverride: String? = null) {
        if (privateInputMode || rawCommitInputMode || learningSuppressedInputMode) return
        val previous = previousOverride ?: lastCommittedBengali
        if (previous.isEmpty() || committed.isEmpty()) return
        if (!previous.any { isBengaliChar(it) } || !committed.any { isBengaliChar(it) }) return

        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(previous.length + committed.length + 8, 0)
            ?.toString()?.trimEnd() ?: return
        if (!before.endsWith(committed)) return
        val head = before.removeSuffix(committed)
        // The committed word must stand alone (space/punct before it, not glyphs).
        if (head.isEmpty() || isBengaliChar(head.last())) return
        val beforePrevious = head.trimEnd()
        if (!beforePrevious.endsWith(previous)) return
        val boundaryIndex = beforePrevious.length - previous.length - 1
        if (boundaryIndex >= 0 && isBengaliChar(beforePrevious[boundaryIndex])) return

        serviceScope.launch {
            // S75: bigram recording mutates engine state — conversions lane.
            withContext(engineLane) {
                SmartEngineAdapter.recordNextWordUsage(previous, committed)
            }
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
        val wasEnglish = lastAutoCorrectWasEnglish
        ic.deleteSurroundingText(lastCommittedTextLength, 0)
        ic.commitText("$original ", 1)
        lastCommittedTextLength = original.length + 1
        lastCommittedBengali = original
        clearAutoCorrectUndoState()
        suggestions.clear()
        recordImeEvent("autocorrect_undo")
        if (wasEnglish) {
            // S97: undoing an English correction TEACHES the typed word —
            // one recorded use makes it a known personal word, and known
            // words are never corrected again (engine contract).
            recordEnglishCommitAsync(original, null)
            refreshEnglishSuggestionsAsync()
        } else {
            showGapPunctuationSuggestions()
        }
    }

    private fun clearAutoCorrectUndoState() {
        lastAutoCorrectOriginal = ""
        lastAutoCorrectReplacement = ""
        lastAutoCorrectPhonetic = ""
        lastAutoCorrectWasEnglish = false
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

    /** The current system clip as (text, flaggedSensitive), or null. */
    private fun readSystemClipboard(): Pair<String, Boolean>? {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        // S135 (F-003): the source app's sensitivity marker (password
        // managers, OTP autofill) is read BEFORE the clip text is touched.
        val clipIsSensitive = clip.description?.extras
            ?.getBoolean(ClipboardHistoryPolicy.EXTRA_IS_SENSITIVE, false) == true
        val text = clip.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        return text to clipIsSensitive
    }

    /** S136 (F-003): a private field of ANY kind (password, OTP, email, URI,
     *  number/phone, no-personalized-learning) never feeds history. */
    private val clipboardFieldIsPrivate: Boolean get() = privateInputMode || sensitiveInputMode

    private fun captureCurrentSystemClipboard() {
        if (clipboardFieldIsPrivate) return
        val (text, clipIsSensitive) = readSystemClipboard() ?: return
        if (!ClipboardHistoryPolicy.shouldRemember(clipboardFieldIsPrivate, clipIsSensitive)) return
        rememberClipboardItem(text)
    }

    private fun loadClipboardHistory() {
        if (!::prefs.isInitialized) return
        val raw = prefs.getString(PREF_CLIPBOARD_HISTORY, null)
        val entries = ClipboardHistoryPolicy.decode(raw, System.currentTimeMillis())
        clipboardHistory.clear()
        clipboardHistory.addAll(entries)
        clipboardHistoryLoaded = true
        // S136 (F-003): pre-S135 items carried no timestamp and were re-dated
        // "now" on EVERY load — an undated blob could live forever. Write the
        // dated form back at once so they expire exactly one hour from this
        // load; this also drops already-expired items from disk.
        if (ClipboardHistoryPolicy.encode(entries) != raw.orEmpty()) persistClipboardHistory()
    }

    private fun ensureClipboardHistoryLoaded() {
        if (!clipboardHistoryLoaded) loadClipboardHistory()
    }

    /** S135: entries older than [ClipboardHistoryPolicy.RETENTION_MS] die on
     *  every panel open, not just on process restart. */
    private fun pruneClipboardHistory() {
        val pruned = ClipboardHistoryPolicy.prune(clipboardHistory.toList(), System.currentTimeMillis())
        if (pruned.size == clipboardHistory.size) return
        clipboardHistory.clear()
        clipboardHistory.addAll(pruned)
        persistClipboardHistory()
    }

    private fun rememberClipboardItem(text: String) {
        if (!clipboardHistoryEnabled.value) return
        ensureClipboardHistoryLoaded()
        // S135/S136 (F-003): a paste INTO a private field still works; the
        // field just never feeds history.
        if (!ClipboardHistoryPolicy.shouldRemember(clipboardFieldIsPrivate, clipIsSensitive = false)) return
        val next = ClipboardHistoryPolicy.remember(clipboardHistory.toList(), text, System.currentTimeMillis())
        if (next == clipboardHistory.toList()) return
        clipboardHistory.clear()
        clipboardHistory.addAll(next)
        persistClipboardHistory()
    }

    private fun clearClipboardHistory() {
        ensureClipboardHistoryLoaded()
        clipboardHistory.clear()
        persistClipboardHistory()
    }

    private fun persistClipboardHistory() {
        if (!::prefs.isInitialized) return
        prefs.edit()
            .putString(PREF_CLIPBOARD_HISTORY, ClipboardHistoryPolicy.encode(clipboardHistory.toList()))
            .apply()
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
