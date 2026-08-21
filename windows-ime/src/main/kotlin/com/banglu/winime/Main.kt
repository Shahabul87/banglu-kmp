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
import com.banglu.winime.composer.ComposerEngine
import com.banglu.winime.hook.ForegroundApp
import com.banglu.winime.hook.LowLevelHook
import com.banglu.winime.hook.SendInputInjector
import com.banglu.winime.ui.PreviewWindow
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

/**
 * Task 8 replaces this with a persisted `WinPrefsStore`; it exists now only so
 * the fields it will carry have one agreed shape. Nothing reads it yet — the
 * tray toggles that will are part of that task.
 */
data class WinPrefs(val banglaDigits: Boolean = true, val startOnLogin: Boolean = true)

private const val BOOT_LOADING = "লোড হচ্ছে…"
private const val BOOT_READY = "পূর্ণ অভিধান ✓"
private const val BOOT_FAILED = "অভিধান লোড হয়নি — বাংলা টাইপিং বন্ধ"
private const val HOOK_DOWN = "⚠ কীবোর্ড হুক বসেনি"
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

fun main() = application {
    val ui = remember { UiState() }
    val scope = rememberCoroutineScope()
    val trayState = rememberTrayState()
    val bangluDir = remember { File(System.getProperty("user.home"), ".banglu") }
    val elevation = remember { InjectionWarning(trayState) }
    val controller = remember {
        Controller(
            engine = WinEngine,
            injector = TaggingInjector(SendInputInjector()),
            compat = AppCompat(bangluDir),
            listener = UiListener(ui),
        ).apply { onError = { error -> elevation.report(error) } }
    }
    val hook = remember { LowLevelHook() }

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

    // Rendered once per state, not per recomposition: the tray icon is the only
    // always-visible surface this app has, and the question it exists to answer
    // is "am I typing Bangla right now?" (spec §5).
    val trayIcon = remember(ui.mode, ui.engineReady) { TrayGlyph.painter(ui.mode, ui.engineReady) }
    Tray(
        icon = trayIcon,
        state = trayState,
        tooltip = "বাংলু টাইপার — ${modeLabel(ui.mode)}",
        menu = {
            Item(ui.bootStatus, enabled = false) {}
            // Only a fault once the hook was supposed to be there: before the
            // dictionary lands we have deliberately not installed it, and a
            // warning for that would train the user to ignore this line.
            if (ui.engineReady && !ui.hookInstalled) Item(HOOK_DOWN, enabled = false) {}
            Separator()
            RadioButtonItem(
                text = modeLabel(Mode.BANGLA),
                selected = ui.mode == Mode.BANGLA,
            ) { controller.setModeExternal(Mode.BANGLA) }
            RadioButtonItem(
                text = modeLabel(Mode.ENGLISH),
                selected = ui.mode == Mode.ENGLISH,
            ) { controller.setModeExternal(Mode.ENGLISH) }
            RadioButtonItem(
                text = modeLabel(Mode.OFF),
                selected = ui.mode == Mode.OFF,
            ) { controller.setModeExternal(Mode.OFF) }
            Item("বাংলা/English: Ctrl+Space", enabled = false) {}
            Separator()
            Item("কীবোর্ড আবার চালু করুন", enabled = ui.engineReady) { restartHook() }
            Item("টিউটোরিয়াল (ওয়েব গাইড)") { openUrl(GUIDE_URL) }
            // The dictionary data licenses require an in-app notices surface.
            Item("ওপেন সোর্স লাইসেন্স") { openLicenses() }
            Separator()
            Item("বন্ধ করুন") {
                controller.shutdown()
                hook.stop()
                exitApplication()
            }
        },
    )

    PreviewWindow(
        visible = ui.previewVisible,
        bangla = ui.bangla,
        raw = ui.raw,
        candidates = ui.candidates,
        onPick = { index -> controller.pickCandidate(index) },
    )
}

/**
 * Everything the tray and the preview render. Compose state, therefore written
 * ONLY from the UI thread; the controller's worker and the hook's pump thread
 * both reach it through [EventQueue.invokeLater].
 */
private class UiState {
    var bootStatus by mutableStateOf(BOOT_LOADING)
    var engineReady by mutableStateOf(false)
    var hookInstalled by mutableStateOf(false)
    var mode by mutableStateOf(Mode.BANGLA)
    var bangla by mutableStateOf("")
    var raw by mutableStateOf("")
    var candidates by mutableStateOf(emptyList<String>())

    /** `"" , ""` with no candidates is the composer's "hide" (KeyPorts). */
    val previewVisible: Boolean
        get() = bangla.isNotEmpty() || raw.isNotEmpty() || candidates.isNotEmpty()
}

/**
 * The controller calls this from its worker thread, so every field write hops
 * to the UI thread first — touching Compose state off it is a data race that
 * shows up as a preview frozen on a word the user finished typing.
 */
private class UiListener(private val ui: UiState) : ControllerListener {
    override fun onPreview(bangla: String, raw: String, candidates: List<String>) {
        EventQueue.invokeLater {
            ui.bangla = bangla
            ui.raw = raw
            ui.candidates = candidates
        }
    }

    override fun onModeChanged(mode: Mode) {
        EventQueue.invokeLater { ui.mode = mode }
    }
}

/**
 * The engine seam. Called only from the controller's single worker thread, so
 * it needs no lock of its own — `SmartEngine` is not internally thread-safe,
 * and that single lane IS the guarantee. Do not add a second caller.
 */
private object WinEngine : ComposerEngine {
    override fun convert(raw: String): String =
        SmartEngineAdapter.convertWord(raw).bengali

    override fun suggest(raw: String, limit: Int): List<String> =
        SmartEngineAdapter.getSuggestions(raw, limit).map { it.bengali }

    override fun selected(raw: String, bangla: String) {
        // Reached only for a non-primary pick (S26 law, enforced in Controller).
        SmartEngineAdapter.onWordSelected(
            phonetic = raw,
            bengali = bangla,
            learnAsWord = false,
            explicitChoice = true,
        )
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

private fun openUrl(url: String) {
    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
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
 * `tray.png` remains the packaging icon.
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
