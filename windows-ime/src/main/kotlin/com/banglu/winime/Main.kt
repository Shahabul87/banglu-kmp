package com.banglu.winime

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.TrayState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import com.banglu.engine.JvmSqliteDictionaryLoader
import com.banglu.engine.JvmSqlitePhoneticIndexStore
import com.banglu.engine.SmartEngineAdapter
import com.banglu.winime.hook.ForegroundApp
import com.banglu.winime.hook.LowLevelHook
import com.banglu.winime.hook.SendInputInjector
import com.banglu.winime.ui.ControlWindow
import com.banglu.winime.ui.PreviewWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.EventQueue
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * বাংলু টাইপার — the Windows system-wide input tool.
 *
 * This file is the assembly point and nothing else: it boots the engine,
 * constructs the controller with its Win32 ports, installs the keyboard hook,
 * and renders the two surfaces the user ever sees — a tray menu and the
 * caret-anchored preview strip. No typing behaviour lives here (invariant 9),
 * and no JNA type is imported outside `hook/` (the isolation law).
 */

private const val BOOT_LOADING = "লোড হচ্ছে…"
private const val BOOT_READY = "পূর্ণ অভিধান ✓"
private const val BOOT_FAILED = "অভিধান লোড হয়নি — বাংলা টাইপিং বন্ধ"
private const val HOOK_DOWN = "⚠ কীবোর্ড হুক বসেনি"

/**
 * The manual re-arm. Deliberately phrased as a question about the symptom:
 * Windows can drop a hook it judged slow without telling anyone, and in that
 * state [LowLevelHook.isInstalled] still answers true (we hold the handle), so
 * [HOOK_DOWN] never appears and this item is the user's only route back.
 */
private const val REARM_HOOK = "কীবোর্ড কাজ করছে না? আবার চালু করুন"
private const val GUIDE_URL = "https://www.craftsai.org/products/banglu"

/** One hint per offending application, at most this many in a session. */
private const val MAX_WARNED_APPS = 16

private const val TITLE_BLOCKED = "বাংলু টাইপার — লেখা পাঠানো যায়নি"
private const val HINT_BLOCKED =
    "প্রশাসক (administrator) হিসেবে চলা অ্যাপে উইন্ডোজ বাইরের কীবোর্ড ঢুকতে দেয় না। " +
        "ওখানে বাংলা লিখতে বাংলু টাইপারকেও \"Run as administrator\" দিয়ে চালান।"

private const val TITLE_FAULT = "বাংলু টাইপার — সমস্যা হয়েছে"
private const val HINT_FAULT =
    "টাইপ করার সময় একটি সমস্যা হয়েছে, শব্দটি বাদ পড়তে পারে। " +
        "বারবার হলে বাংলু টাইপার বন্ধ করে আবার চালু করুন।"

/**
 * Shown the FIRST time the user hides the control window in a session.
 *
 * Without it, hiding the window looks like quitting: the app is still typing
 * Bangla, but the only thing left on screen is a tray icon Windows files away
 * behind the taskbar's overflow chevron by default. A user who hid the window
 * and could not find it again is the report this exists to answer, so the
 * message names both facts — still running, and how to get back.
 */
private const val TITLE_HIDDEN = "বাংলু টাইপার চলছে"
private const val HINT_HIDDEN =
    "উইন্ডোটি লুকানো হলো, কিন্তু বাংলা টাইপিং চালু আছে। " +
        "ফিরে পেতে টাস্কবারের ডান দিকে (দরকার হলে ^ চিহ্নে ক্লিক করে) বাংলু আইকনে ক্লিক করুন।"

private const val TITLE_STARTUP_TOGGLE_FAILED = "বাংলু টাইপার — সেটিং সংরক্ষণ হয়নি"
private const val HINT_STARTUP_TOGGLE_FAILED =
    "\"লগইনে চালু হবে\" সেটিং পরিবর্তন করা যায়নি। আবার চেষ্টা করুন।"

fun main() = application {
    val bangluDir = remember { File(System.getProperty("user.home"), ".banglu") }
    val prefsStore = remember { WinPrefsStore(bangluDir) }
    // Read once at composition start: this is the ONE state restore point.
    // Everything after this reads/writes through updatePrefs, never prefsStore
    // directly, so a click on one toggle can never clobber another toggle's
    // concurrent write (mode persistence runs on the worker thread; the
    // checkbox clicks run on the UI thread).
    val initialPrefs = remember { prefsStore.load() }
    val prefsLock = remember { Any() }
    fun updatePrefs(mutate: (WinPrefs) -> WinPrefs) {
        synchronized(prefsLock) { prefsStore.save(mutate(prefsStore.load())) }
    }
    // OFF must be honoured on restart (spec): an unknown/corrupt mode string
    // degrades to BANGLA rather than crashing the tray at startup.
    val restoredMode = remember { runCatching { Mode.valueOf(initialPrefs.mode) }.getOrDefault(Mode.BANGLA) }
    // Armed ONLY when the restore below will actually fire onModeChanged:
    // Controller's own boot-time default is BANGLA, and setModeExternal is a
    // no-op when `from == to`, so restoring to BANGLA never calls the
    // listener at all. Arming unconditionally would leave this flag "stuck
    // true" in that (default, most common) case and wrongly suppress the
    // user's actual FIRST real mode switch later in the session. Cleared on
    // the one callback the restore call produces (self-clearing, not
    // time-based) — never suppresses a later real switch, even one that
    // happens to land back on the same mode string.
    val restoringMode = remember { java.util.concurrent.atomic.AtomicBoolean(restoredMode != Mode.BANGLA) }

    val ui = remember { UiState(restoredMode) }
    val scope = rememberCoroutineScope()
    val trayState = rememberTrayState()
    var digitsChecked by remember { mutableStateOf(initialPrefs.banglaDigits) }
    var startOnLoginChecked by remember { mutableStateOf(initialPrefs.startOnLogin) }
    val elevation = remember { InjectionWarning(trayState) }
    val controller = remember {
        Controller(
            engine = AdapterComposerEngine,
            injector = TaggingInjector(SendInputInjector()),
            compat = AppCompat(bangluDir),
            listener = UiListener(ui) { mode ->
                if (!restoringMode.getAndSet(false)) {
                    updatePrefs { it.copy(mode = mode.name) }
                }
            },
        ).apply {
            onError = { error -> elevation.report(error) }
            banglaDigits = initialPrefs.banglaDigits
            // Restores mode incl. OFF; the hook honours it as soon as it
            // installs, and OFF makes onKey a pure passthrough regardless of
            // whether the hook is up yet.
            setModeExternal(restoredMode)
        }
    }
    val hook = remember { LowLevelHook() }

    /**
     * Brings the control window back, whether it is hidden or merely buried.
     * Safe from any thread — the tray's action listener is not guaranteed to
     * be the AWT event thread.
     */
    fun showControlWindow() {
        EventQueue.invokeLater {
            ui.controlVisible = true
            ui.controlShowRequests++
        }
    }

    /**
     * Hides it, and says so — once. A balloon on every hide would be noise;
     * never saying it at all is how the user got stranded.
     */
    val hideHintShown = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    fun hideControlWindow() {
        ui.controlVisible = false
        if (hideHintShown.compareAndSet(false, true)) {
            trayState.sendNotification(
                Notification(title = TITLE_HIDDEN, message = HINT_HIDDEN, type = Notification.Type.Info)
            )
        }
    }

    /** The one real exit: drain the worker, unhook, then leave. */
    fun quitApp() {
        controller.shutdown()
        hook.stop()
        exitApplication()
    }

    // ── In-app update ────────────────────────────────────────────────────────
    // Constructed with a directory and a reporting callback and NOTHING else:
    // it has no reference to the engine, the composer, the controller or
    // storage, and `verifyUpdaterIsolation` fails the build if that changes.
    // Every write it makes lands on Compose state through EventQueue, the same
    // rule the controller's worker and the hook's pump thread already follow.
    //
    // NULL in the Microsoft Store edition, where the updater does not exist in
    // the package at all (EditionPorts.kt). Every use below is gated on
    // `Edition.hasUpdater` rather than on the null itself, so the Store build
    // draws no update surface whatsoever — no row, no menu item, no preference
    // — instead of an inert one.
    var autoUpdateChecked by remember { mutableStateOf(initialPrefs.autoUpdate) }
    // The once-per-version balloon ledger, seeded from disk so a login-time
    // restart cannot re-announce a version the user was already told about.
    val announcedUpdate = remember {
        java.util.concurrent.atomic.AtomicReference(initialPrefs.updateNoticeVersion)
    }
    val updates = remember {
        Edition.updateGateway(File(bangluDir, "updates")) { state: UpdateStatus ->
            EventQueue.invokeLater {
                ui.updateLine = state.line
                ui.updateActionable = state.actionable
                ui.updateBusy = state.busy
                // The "come look" signal (UpdateNotice): the window shows the
                // real offer; this balloon just makes sure a user who never
                // opens the window still hears about it — once per version.
                val offered = state.offeredVersion
                if (UpdateNotice.shouldAnnounce(offered, announcedUpdate.get())) {
                    announcedUpdate.set(offered)
                    updatePrefs { it.copy(updateNoticeVersion = offered!!) }
                    trayState.sendNotification(
                        Notification(
                            title = UpdateNotice.TITLE,
                            message = UpdateNotice.message(offered!!),
                            type = Notification.Type.Info,
                        )
                    )
                }
            }
        }
    }

    /** Never on the UI thread, never on the typing path — IO, always. */
    fun checkForUpdates(manual: Boolean) {
        val gateway = updates ?: return
        scope.launch(Dispatchers.IO) { gateway.check(manual) }
    }

    /**
     * Download → verify → hand to msiexec → quit. The quit is not politeness:
     * Windows Installer cannot replace an executable the running app holds
     * open, and a file it cannot replace is exactly what makes it schedule the
     * work for next boot and ask for a restart. Leaving promptly is what keeps
     * the upgrade in-place and reboot-free.
     */
    fun installUpdate() {
        val gateway = updates ?: return
        scope.launch(Dispatchers.IO) {
            gateway.install { EventQueue.invokeLater { quitApp() } }
        }
    }

    /** Publishes the hook's own truth; called from the pump thread as well. */
    fun publishHookState() {
        val installed = hook.isInstalled
        EventQueue.invokeLater { ui.hookInstalled = installed }
    }

    /**
     * Blocking — call from a background dispatcher only. `start()` waits for
     * the first install attempt, so the callbacks MUST be assigned first:
     * `onHookLost` is edge-triggered, and a boot-time failure assigned to
     * afterwards is a failure nobody ever hears about. `isInstalled` read after
     * the call is the persistent half of the same signal.
     */
    fun startHook() {
        hook.onHookLost = { cause ->
            System.err.println(
                "Banglu keyboard hook install failed: " +
                    (cause?.toString() ?: "SetWindowsHookEx returned NULL")
            )
            publishHookState()
        }
        hook.onRearm = { publishHookState() }
        hook.start(controller)
        publishHookState()
    }

    /**
     * The manual way back. The watchdog deliberately re-installs only a hook it
     * KNOWS failed to install — Windows can silently drop a hook it judged slow
     * and reports that to nobody, and re-arming a healthy hook would eat
     * keystrokes. So when the keyboard goes dead, this is the recovery.
     */
    fun restartHook() {
        scope.launch(Dispatchers.Default) {
            hook.stop()
            startHook()
        }
    }

    // S187: the latency readout — polled, not pushed, so the worker never
    // touches Compose state on the keystroke path.
    LaunchedEffect(ui.controlVisible, ui.engineReady) {
        while (ui.controlVisible && ui.engineReady) {
            val l = controller.latencySummary()
            ui.latencyLine = if (l.samples == 0) "" else
                "টাইপিং: গড় %.1f ms · সর্বোচ্চ %.0f ms (শেষ %d কি) · ওয়ার্ম-আপ %d শব্দ".format(l.medianMs, l.maxMs, l.samples, controller.warmedWords)
            kotlinx.coroutines.delay(2_000)
        }
    }

    LaunchedEffect(Unit) {
        // Without a persistence scope the learned words are never written to
        // disk (the desktop editor shipped that bug once).
        SmartEngineAdapter.configurePersistenceScope(scope)
        val boot = withContext(Dispatchers.Default) {
            runCatching { bootFullDictionary(bangluDir) }
        }
        boot.fold(
            onSuccess = {
                // On the UI thread (the LaunchedEffect resumes on the
                // composition dispatcher), after the store is attached: this is
                // the ONE place `engineReady` is ever set. Until it flips, every
                // key passes through untouched (spec §4.8) — the keyboard is
                // inert rather than half-converting with seed data.
                ui.bootStatus = BOOT_READY
                ui.engineReady = true
                controller.engineReady = true
                // Only now is there any reason to intercept keystrokes.
                scope.launch(Dispatchers.Default) { startHook() }
                // S187: warm the store and the JIT on the engine lane, one
                // word per turn, so the first real keystrokes are not the
                // ones paying the cold cost.
                controller.warmUp(WarmUpWords.list)
            },
            onFailure = { error ->
                System.err.println("Banglu dictionary boot failed: $error")
                // `engineReady` stays false ON PURPOSE. A seed-only engine would
                // quietly produce worse Bangla than the user expects; saying so
                // in the tray and staying out of the way is the honest failure.
                ui.bootStatus = BOOT_FAILED
            },
        )
    }

    // Its own effect, deliberately not folded into the boot fold above: an
    // update is the fix for a dictionary that failed to load, so the check
    // must still run when booting FAILED. Nothing here can block typing —
    // the work happens on Dispatchers.IO and its only output is one line of
    // Compose state.
    LaunchedEffect(Unit) {
        val gateway = updates
        if (gateway != null && initialPrefs.autoUpdate) {
            withContext(Dispatchers.IO) { gateway.check(manual = false) }
        }
    }

    // Rendered once per state, not per recomposition: the tray icon is the only
    // always-visible surface this app has, and the question it exists to answer
    // is "am I typing Bangla right now?" (spec §5).
    val trayIcon = remember(ui.mode, ui.engineReady) { TrayGlyph.painter(ui.mode, ui.engineReady) }
    Tray(
        icon = trayIcon,
        state = trayState,
        tooltip = "বাংলু টাইপার — ${modeLabel(ui.mode)}",
        // The tray icon IS the way back to the window on Windows, and clicking
        // it is the first thing anyone tries. The menu item below stays as the
        // discoverable duplicate, but it is not the primary route.
        onAction = { showControlWindow() },
        menu = {
            Item(ui.bootStatus, enabled = false) {}
            // Only a fault once the hook was supposed to be there: before the
            // dictionary lands we have deliberately not installed it, and a
            // warning for that would train the user to ignore this line.
            if (ui.engineReady && !ui.hookInstalled) Item(HOOK_DOWN, enabled = false) {}
            Separator()
            // Plain Items with a ✓ prefix, NOT RadioButtonItem: Compose maps a
            // tray menu onto java.awt.PopupMenu, whose Menu has no radio item —
            // it throws UnsupportedOperationException at first composition and
            // the app dies before drawing anything ("Failed to launch JVM").
            // Found the first time this ever ran on Windows; the mark is drawn
            // in the label instead.
            Item(modeMenuLabel(Mode.BANGLA, ui.mode)) { controller.setModeExternal(Mode.BANGLA) }
            Item(modeMenuLabel(Mode.ENGLISH, ui.mode)) { controller.setModeExternal(Mode.ENGLISH) }
            Item(modeMenuLabel(Mode.OFF, ui.mode)) { controller.setModeExternal(Mode.OFF) }
            Item("বাংলা/English: Ctrl+Space", enabled = false) {}
            Separator()
            Item("বাংলু উইন্ডো দেখান") { showControlWindow() }
            Separator()
            CheckboxItem(text = "বাংলা সংখ্যা (০-৯)", checked = digitsChecked) { checked ->
                digitsChecked = checked
                controller.banglaDigits = checked
                updatePrefs { it.copy(banglaDigits = checked) }
            }
            // A real toggle only where the app can actually deliver it. In the
            // Microsoft Store edition `startOnLogin` is null — a packaged app's
            // start-on-login is a manifest StartupTask that Windows owns — so
            // the tray states where the setting lives instead of offering a
            // switch that would do nothing. Exactly one of these two branches
            // renders; never both, never neither (EditionPorts).
            val startupControl = Edition.startOnLogin
            if (startupControl != null) {
                CheckboxItem(text = "লগইনে চালু হবে", checked = startOnLoginChecked) { checked ->
                    applyStartOnLoginChange(scope, trayState, startupControl, checked) {
                        startOnLoginChecked = checked
                        updatePrefs { it.copy(startOnLogin = checked) }
                    }
                }
            } else {
                Edition.startupNote?.let { note -> Item(note, enabled = false) {} }
            }
            if (Edition.hasUpdater) {
                // Governs the AUTOMATIC startup check only. "আপডেট দেখুন" below
                // works regardless — switching this off must mean "stop looking
                // on your own", never "refuse to look when I ask".
                CheckboxItem(text = "স্বয়ংক্রিয় আপডেট", checked = autoUpdateChecked) { checked ->
                    autoUpdateChecked = checked
                    updatePrefs { it.copy(autoUpdate = checked) }
                }
            }
            Separator()
            if (Edition.hasUpdater) {
                Item("আপডেট দেখুন", enabled = !ui.updateBusy) { checkForUpdates(manual = true) }
            }
            // Names the SYMPTOM, not the mechanism. `HOOK_DOWN` above only
            // appears when Windows told us the install failed; a hook Windows
            // silently dropped still leaves us holding a handle, so the tray
            // reads healthy while the keyboard is dead. In that case — the one
            // this item exists for — this label is the only thing on screen
            // that tells the user what to do.
            Item(REARM_HOOK, enabled = ui.engineReady) { restartHook() }
            Item("টিউটোরিয়াল (ওয়েব গাইড)") { openUrl(GUIDE_URL) }
            // The dictionary data licenses require an in-app notices surface.
            Item("ওপেন সোর্স লাইসেন্স") { openLicenses() }
            Separator()
            Item("বন্ধ করুন") { quitApp() }
        },
    )

    // The discoverable surface. Opens on launch: a Windows user cannot be
    // expected to find a tray icon that Windows itself hides by default.
    ControlWindow(
        visible = ui.controlVisible,
        showRequests = ui.controlShowRequests,
        mode = ui.mode,
        bootStatus = ui.bootStatus,
        hookHealthy = ui.hookInstalled,
        engineReady = ui.engineReady,
        updateLine = ui.updateLine,
        updateActionable = ui.updateActionable,
        updateBusy = ui.updateBusy,
        // Which build this is, in the one place a user will find it when we
        // ask them "which version are you running?" — the two editions differ
        // in ways (updater, start-on-login) that a bug report cannot be read
        // without knowing which one is installed.
        versionLine = EditionInfo.line,
        latencyLine = ui.latencyLine,
        onUpdate = { installUpdate() },
        onMode = { controller.setModeExternal(it) },
        onHide = { hideControlWindow() },
        onQuit = { quitApp() },
    )

    PreviewWindow(
        visible = ui.previewVisible,
        candidates = ui.candidates,
        predictions = ui.predictions,
        onPick = { index -> controller.pickCandidate(index) },
    )
}

/**
 * Everything the tray and the preview render. Compose state, therefore written
 * ONLY from the UI thread; the controller's worker and the hook's pump thread
 * both reach it through [EventQueue.invokeLater].
 */
private class UiState(initialMode: Mode = Mode.BANGLA) {
    var bootStatus by mutableStateOf(BOOT_LOADING)
    var engineReady by mutableStateOf(false)
    var hookInstalled by mutableStateOf(false)
    var mode by mutableStateOf(initialMode)
    var candidates by mutableStateOf(emptyList<String>())

    /** S187: "টাইপিং: গড় 0.4 ms · সর্বোচ্চ 12 ms" — refreshed while the control window is open. */
    var latencyLine by mutableStateOf("")

    /** S130: true while [candidates] holds next-word predictions (click-only chips). */
    var predictions by mutableStateOf(false)

    /**
     * The control window is visible on launch and stays reachable from the
     * tray. A tray-only app is undiscoverable on Windows: new icons are
     * hidden behind the taskbar's overflow chevron, so a first-time user
     * sees nothing at all and cannot tell whether their keyboard is on.
     */
    var controlVisible by mutableStateOf(true)

    /**
     * Incremented on every "show the window" request, including one made while
     * it is already open. A show that finds `controlVisible` already true would
     * otherwise be a silent no-op — the same "I clicked and nothing happened"
     * the tray click exists to fix — so the window keys its bring-to-front on
     * this counter instead of on visibility.
     */
    var controlShowRequests by mutableStateOf(0)

    /**
     * The updater's entire footprint on the UI. Empty line = say nothing,
     * which is what an automatic check that found nothing (or failed) leaves
     * behind: an update must never nag, and must never interrupt typing.
     */
    var updateLine by mutableStateOf("")
    var updateActionable by mutableStateOf(false)
    var updateBusy by mutableStateOf(false)

    /**
     * Visible if and only if there is something to show. An empty candidate
     * list is the composer's "hide" (KeyPorts), and because the popup renders
     * chips and nothing else, a list-driven predicate is the ONLY one that
     * cannot put an empty box on the user's screen — deriving it from the
     * forming word would.
     */
    val previewVisible: Boolean get() = candidates.isNotEmpty()
}

/**
 * The controller calls this from its worker thread, so every field write hops
 * to the UI thread first — touching Compose state off it is a data race that
 * shows up as a preview frozen on a word the user finished typing.
 */
private class UiListener(
    private val ui: UiState,
    /** Task 8: fired on every mode change regardless of origin (tray click
     * or the Ctrl+Space hotkey) — `switchMode` always routes through here,
     * so this is the one place mode persistence needs to hook in. */
    private val onModeChangedExtra: (Mode) -> Unit = {},
) : ControllerListener {
    override fun onCandidates(candidates: List<String>, predictions: Boolean) {
        EventQueue.invokeLater {
            ui.candidates = candidates
            ui.predictions = predictions
        }
    }

    override fun onModeChanged(mode: Mode) {
        EventQueue.invokeLater { ui.mode = mode }
        onModeChangedExtra(mode)
    }
}

/** Marks a throwable that came out of the injector rather than the engine. */
private class InjectionFailure(cause: Throwable) : RuntimeException(cause)

/**
 * Tags injector failures on their way out. `Controller.onError` reports ANY
 * throwable a job raises — an engine fault included — and the two need opposite
 * advice: "Windows blocks outside keyboards in administrator windows" is wrong
 * and actively misleading for a corrupt-dictionary throw. Only the injector can
 * produce the first kind, so tagging here is the honest discriminator.
 *
 * Behaviourally transparent: the controller still sees a throwable from the
 * same call and runs its existing recovery.
 */
private class TaggingInjector(private val delegate: TextInjector) : TextInjector {
    override fun injectText(text: String) {
        try {
            delegate.injectText(text)
        } catch (t: Throwable) {
            throw InjectionFailure(t)
        }
    }

    override fun injectBackspaces(count: Int) {
        try {
            delegate.injectBackspaces(count)
        } catch (t: Throwable) {
            throw InjectionFailure(t)
        }
    }

    override fun injectKey(key: RawKey) {
        try {
            delegate.injectKey(key)
        } catch (t: Throwable) {
            throw InjectionFailure(t)
        }
    }

    override fun injectVirtualKey(vk: Int, shift: Boolean) {
        try {
            delegate.injectVirtualKey(vk, shift)
        } catch (t: Throwable) {
            throw InjectionFailure(t)
        }
    }
}

/**
 * `SendInput` inserts zero events into a window running elevated, so the
 * injector throws on EVERY keystroke typed there. Reporting each one would put
 * a tray balloon on screen per letter — worse than the fault it describes. One
 * hint per application, then silence.
 */
private class InjectionWarning(private val tray: TrayState) {
    private val warnedApps = ConcurrentHashMap.newKeySet<String>()

    /** Called on the controller's worker thread. */
    fun report(error: Throwable) {
        val exe = ForegroundApp.exeName()
        if (warnedApps.size >= MAX_WARNED_APPS) return
        if (!warnedApps.add(exe)) return
        System.err.println("Banglu worker error in '$exe': $error")
        // Only an injector failure means "run Banglu as administrator". Saying
        // that about an engine fault sends the user to relaunch elevated and
        // stay just as broken.
        val blocked = error is InjectionFailure
        val title = if (blocked) TITLE_BLOCKED else TITLE_FAULT
        val message = if (blocked) HINT_BLOCKED else HINT_FAULT
        EventQueue.invokeLater {
            tray.sendNotification(
                Notification(title = title, message = message, type = Notification.Type.Warning)
            )
        }
    }
}

/**
 * The desktop editor's boot fold, unchanged in shape: seeds first so typing is
 * never blocked on the store, then the full sqlite, then the loader. Any
 * failure — missing file, wrong dictionary version, unreadable table — throws
 * out of here and the caller keeps the keyboard inert.
 */
private suspend fun bootFullDictionary(storageDir: File) {
    SmartEngineAdapter.initializeSync()
    val db = findDictionaryFile()
    val store = JvmSqlitePhoneticIndexStore(db)
    check(store.isAvailable) {
        "dictionary.sqlite rejected (missing table or wrong version): ${db.absolutePath}"
    }
    SmartEngineAdapter.setPhoneticIndex(store)
    SmartEngineAdapter.initialize(WinStorage(storageDir), JvmSqliteDictionaryLoader(db))
}

/** Installer resources -> %USERPROFILE%\.banglu -> repo dev path. */
private fun findDictionaryFile(): File {
    System.getProperty("compose.application.resources.dir")?.let {
        File(it, "dictionary.sqlite").takeIf(File::exists)?.let { f -> return f }
    }
    File(System.getProperty("user.home"), ".banglu/dictionary.sqlite")
        .takeIf(File::exists)?.let { return it }
    return JvmSqliteDictionaryLoader.findDictionarySqlite()
}

private fun modeLabel(mode: Mode): String = when (mode) {
    Mode.BANGLA -> "বাংলা"
    Mode.ENGLISH -> "English"
    Mode.OFF -> "বন্ধ"
}

/** Selection drawn into the text: AWT tray menus have no radio item. */
private fun modeMenuLabel(mode: Mode, current: Mode): String =
    (if (mode == current) "✓ " else "     ") + modeLabel(mode)

private fun openUrl(url: String) {
    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
}

/**
 * Runs [StartOnLoginControl.set] off the Compose-for-Desktop/AWT event thread
 * (review finding, Task 8 round 2): the MSI edition's implementation is a real
 * synchronous `reg.exe` launch, bounded by its own timeout but still
 * potentially seconds long on registry virtualization/AV interception/Group
 * Policy, and that thread also services the whole tray menu and the preview
 * window — a stuck `reg.exe` inline in the checkbox callback would freeze both.
 *
 * `onSuccess` (the checkbox flip + the pref write) runs ONLY on a confirmed
 * success; any failure — including a dev run where there is no single
 * meaningful executable to point a Run key at — leaves the checkbox and the
 * pref exactly as they were and surfaces a tray warning instead of silently
 * claiming the setting was applied.
 */
private fun applyStartOnLoginChange(
    scope: CoroutineScope,
    trayState: TrayState,
    control: StartOnLoginControl,
    enabled: Boolean,
    onSuccess: () -> Unit,
) {
    scope.launch(Dispatchers.IO) {
        val ok = control.set(enabled)
        EventQueue.invokeLater {
            if (ok) {
                onSuccess()
            } else {
                trayState.sendNotification(
                    Notification(
                        title = TITLE_STARTUP_TOGGLE_FAILED,
                        message = HINT_STARTUP_TOGGLE_FAILED,
                        type = Notification.Type.Warning,
                    )
                )
            }
        }
    }
}

/**
 * The notices surface. It must open something real in every run, not only a
 * packaged one: the dictionary data licences require an in-app notices surface,
 * and the bundled Noto Sans Bengali ships under the OFL, whose §2 requires the
 * licence to travel with the font.
 */
private fun openLicenses() {
    runCatching {
        val notices = packagedNotices() ?: repoNotices() ?: extractedFontLicense() ?: return@runCatching
        val desktop = java.awt.Desktop.getDesktop()
        try {
            desktop.open(notices)
        } catch (_: Exception) {
            // Windows refuses to open a file type nothing is registered for,
            // and a clean install frequently has no handler for `.md`. Plain
            // text always has one, so the notices still reach the user.
            asPlainText(notices)?.let { desktop.open(it) }
        }
    }
}

private fun asPlainText(source: File): File? = runCatching {
    val target = File(System.getProperty("java.io.tmpdir"), "banglu-typer-licenses.txt")
    target.writeText(source.readText())
    target
}.getOrNull()

/** Beside the installed app image (jpackage `appResourcesRootDir`). */
private fun packagedNotices(): File? =
    System.getProperty("compose.application.resources.dir")
        ?.let { File(it, "LICENSES.md") }
        ?.takeIf { it.exists() }

/** A dev run started from the repository root. */
private fun repoNotices(): File? =
    File("windows-ime/resources/common/LICENSES.md").takeIf { it.exists() }

/**
 * Last resort: the font licence always travels on the classpath beside the
 * fonts themselves, so extracting it gives the user something real even when
 * neither the packaged nor the repo copy of the full notices is reachable.
 */
private fun extractedFontLicense(): File? {
    val target = File(System.getProperty("java.io.tmpdir"), "banglu-typer-font-license.txt")
    if (!target.exists()) {
        val stream = Bundled.stream("/fonts/OFL.txt") ?: return null
        stream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
    }
    return target.takeIf { it.exists() }
}

/** Classpath handle for the resources this module bundles. */
private object Bundled {
    fun stream(path: String): InputStream? = Bundled::class.java.getResourceAsStream(path)

    /**
     * The same face the preview window renders with, for Java2D. Null when the
     * resource is unreadable — the tray glyph then falls back to the platform
     * sans-serif, which on Windows still has Bangla coverage (Nirmala UI).
     */
    val bengaliFont: Font? by lazy {
        runCatching {
            stream("/fonts/NotoSansBengali-Regular.ttf")?.use {
                Font.createFont(Font.TRUETYPE_FONT, it)
            }
        }.getOrNull()
    }
}

/**
 * The live tray indicator, drawn per state instead of shipped as art: বাংলা,
 * English, বন্ধ, each dimmed while the dictionary is still loading or failed.
 * This module therefore ships no tray bitmap at all; the installer/Start-menu
 * icon is `windows-ime/icons/banglu.ico`, set in the packaging config.
 */
private object TrayGlyph {
    private const val SIZE = 32
    private const val NOT_READY_ALPHA = 0.45f
    private const val RING_INSET = 9f
    private const val RING_STROKE = 3f

    private val BanglaFill = Color(0x64, 0xD2, 0xFF)
    private val EnglishFill = Color(0x94, 0xA3, 0xB8)
    private val OffFill = Color(0x33, 0x41, 0x55)
    private val DarkInk = Color(0x08, 0x0D, 0x16)
    private val LightInk = Color(0x94, 0xA3, 0xB8)

    fun painter(mode: Mode, ready: Boolean): Painter {
        val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
            )
            // Same shape, visibly not ready: "still loading" must never look
            // like "typing Bangla".
            if (!ready) {
                g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, NOT_READY_ALPHA)
            }
            val fill = when (mode) {
                Mode.BANGLA -> BanglaFill
                Mode.ENGLISH -> EnglishFill
                Mode.OFF -> OffFill
            }
            // A filled badge rather than a bare glyph: the Windows taskbar is
            // dark by default and light in the light theme, and a single-colour
            // letter disappears against one of the two.
            g.color = fill
            g.fill(RoundRectangle2D.Float(1f, 1f, SIZE - 2f, SIZE - 2f, 9f, 9f))
            g.color = if (mode == Mode.OFF) LightInk else DarkInk
            if (mode == Mode.OFF) {
                // Drawn, not typed: a "○" character rendered at tray size is a
                // hairline that vanishes against the badge (verified by
                // rendering both at a true 16px).
                g.stroke = BasicStroke(RING_STROKE)
                g.draw(Ellipse2D.Float(RING_INSET, RING_INSET, SIZE - 2 * RING_INSET, SIZE - 2 * RING_INSET))
            } else {
                val glyph = if (mode == Mode.BANGLA) "অ" else "A"
                g.font = glyphFont(mode)
                drawCentered(g, glyph)
            }
        } finally {
            g.dispose()
        }
        return image.toPainter()
    }

    private fun glyphFont(mode: Mode): Font {
        val bengali = Bundled.bengaliFont
        return if (mode == Mode.BANGLA && bengali != null) {
            bengali.deriveFont(Font.PLAIN, 18f)
        } else {
            Font(Font.SANS_SERIF, Font.BOLD, 16)
        }
    }

    /**
     * Centred on the glyph's own ink, not on the font box: Bangla's matra bar
     * makes ascent/descent wildly asymmetric, and a baseline placed from font
     * metrics hangs অ off the bottom of the badge.
     */
    private fun drawCentered(g: Graphics2D, text: String) {
        val bounds = g.font.createGlyphVector(g.fontRenderContext, text).visualBounds
        val x = ((SIZE - bounds.width) / 2 - bounds.x).toFloat()
        val y = ((SIZE - bounds.height) / 2 - bounds.y).toFloat()
        g.drawString(text, x, y)
    }
}
