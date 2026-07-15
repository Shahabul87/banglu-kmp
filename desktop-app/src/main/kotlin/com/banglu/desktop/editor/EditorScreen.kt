package com.banglu.desktop.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Popup
import com.banglu.desktop.FileStorage
import com.banglu.desktop.findDictionaryFile
import com.banglu.engine.JvmSqliteDictionaryLoader
import com.banglu.engine.JvmSqlitePhoneticIndexStore
import com.banglu.engine.SmartEngineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private class FileState {
    var file: File? = null
    var savedText: String = ""
    fun dirty(current: String) = current != savedText || (file == null && current.isNotEmpty())
}

/** Native file dialogs (java.awt.FileDialog is the macOS-native chooser). */
private fun openDialog(window: java.awt.Frame): File? {
    val d = java.awt.FileDialog(window, "খুলুন", java.awt.FileDialog.LOAD)
    d.isVisible = true
    return d.file?.let { File(d.directory, it) }
}

private fun saveDialog(window: java.awt.Frame, suggested: String): File? {
    val d = java.awt.FileDialog(window, "সেভ", java.awt.FileDialog.SAVE)
    d.file = suggested
    d.isVisible = true
    return d.file?.let {
        val name = if (it.contains('.')) it else "$it.txt"
        File(d.directory, name)
    }
}

/** Quit-path draft flush: Main.kt invokes this before exitApplication (spec §5). */
object DraftFlush {
    @Volatile var flush: (() -> Unit)? = null
}

/** স্পেক §3-§5: the page IS the app. One editor window, no second box. */
@Composable
fun FrameWindowScope.EditorScreen() {
    val engine = remember { RealEngineFacade }
    val drafts = remember { DraftStore(File(System.getProperty("user.home"), ".banglu")) }
    val state = remember { EditorState(engine, drafts.loadPrefs().banglaDigits) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    var status by remember { mutableStateOf("সিড ইঞ্জিন — পূর্ণ অভিধান লোড হচ্ছে…") }
    var fieldValue by remember { mutableStateOf(TextFieldValue()) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var highlight by remember { mutableStateOf(0) }        // popup keyboard highlight
    var restoredBanner by remember { mutableStateOf(false) }
    // Committed-word fix popup (spec §4): range + candidates, opened by click.
    var fixRange by remember { mutableStateOf<IntRange?>(null) }
    var fixCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var fixToken by remember { mutableStateOf(0L) }
    var lastPointerUp by remember { mutableStateOf(0L) }

    fun syncFromState(sel: TextRange = TextRange(state.cursor)) {
        fieldValue = TextFieldValue(state.display, sel)
        highlight = 0
    }

    fun closeFix() { fixToken++; fixRange = null; fixCandidates = emptyList() }

    val fileState = remember { FileState() }
    var fileName by remember { mutableStateOf<String?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }  // unsaved guard
    var exportOpen by remember { mutableStateOf(false) }
    var tutorialOpen by remember { mutableStateOf(false) }  // শিখুন mode; document untouched behind it

    fun refreshTitle() {
        fileName = fileState.file?.name
        dirty = fileState.dirty(state.display)
    }

    fun doSaveAs(): Boolean {
        val f = saveDialog(window, fileState.file?.name ?: "লেখা.txt") ?: return false
        state.commitForming(); syncFromState()
        return runCatching { f.writeText(state.committed) }.fold(
            onSuccess = {
                fileState.file = f; fileState.savedText = state.committed
                refreshTitle(); status = "সেভ হয়েছে ✓"; true
            },
            onFailure = { e ->
                System.err.println("Banglu save failed: $e")
                status = "সেভ ব্যর্থ হয়েছে"; false
            },
        )
    }

    fun doSave(): Boolean {
        val f = fileState.file ?: return doSaveAs()
        state.commitForming(); syncFromState()
        return runCatching { f.writeText(state.committed) }.fold(
            onSuccess = {
                fileState.savedText = state.committed
                refreshTitle(); status = "সেভ হয়েছে ✓"; true
            },
            onFailure = { e ->
                System.err.println("Banglu save failed: $e")
                status = "সেভ ব্যর্থ হয়েছে"; false
            },
        )
    }

    /** Runs [action] directly when clean; else asks সেভ করুন / সেভ ছাড়াই / বাতিল. */
    fun guarded(action: () -> Unit) {
        if (!fileState.dirty(state.display)) action() else pendingAction = action
    }

    fun doNew() = guarded {
        state.setAll(""); fileState.file = null; fileState.savedText = ""
        syncFromState(); refreshTitle()
    }

    fun doOpen() = guarded {
        openDialog(window)?.let { f ->
            // Normalize CRLF → LF: Windows-authored .txt would otherwise push
            // stray \r into committed text and docx runs.
            val text = runCatching { f.readText().replace("\r\n", "\n") }.getOrElse { e ->
                System.err.println("Banglu open failed: $e")
                status = "ফাইল খোলা যায়নি"
                return@let
            }
            state.setAll(text)
            fileState.file = f; fileState.savedText = state.committed
            syncFromState(); refreshTitle()
            val prefs = drafts.loadPrefs()
            drafts.savePrefs(prefs.copy(recent = (listOf(f.absolutePath) + prefs.recent).distinct().take(8)))
        }
    }

    fun copyAll() {
        state.commitForming(); syncFromState()
        java.awt.Toolkit.getDefaultToolkit().systemClipboard
            .setContents(java.awt.datatransfer.StringSelection(state.committed), null)
        status = "কপি হয়েছে ✓"
    }

    fun exportDocx() {
        saveDialog(window, (fileState.file?.nameWithoutExtension ?: "লেখা") + ".docx")?.let { f ->
            state.commitForming(); syncFromState()
            runCatching { DocxWriter.write(state.committed, f) }.fold(
                onSuccess = { status = "Word ফাইল তৈরি ✓" },
                onFailure = { e ->
                    System.err.println("Banglu docx export failed: $e")
                    status = "এক্সপোর্ট ব্যর্থ হয়েছে"
                },
            )
        }
    }

    fun doPrint() {
        state.commitForming(); syncFromState()
        scope.launch(Dispatchers.Default) { Printer.print(state.committed, fileName ?: "Banglu Editor") }
    }

    // Engine boot — identical init to the old App(), plus the persistence
    // scope (without it learned words were silently never written to disk).
    LaunchedEffect(Unit) {
        SmartEngineAdapter.configurePersistenceScope(scope)
        withContext(Dispatchers.Default) {
            SmartEngineAdapter.initializeSync()
            val db = findDictionaryFile()
            SmartEngineAdapter.setPhoneticIndex(JvmSqlitePhoneticIndexStore(db))
            SmartEngineAdapter.initialize(FileStorage, JvmSqliteDictionaryLoader(db))
        }
        status = "পূর্ণ অভিধান ✓"
    }

    // Draft restore on launch (spec §5) — the text is just THERE, no dialog.
    LaunchedEffect(Unit) {
        drafts.loadDraft()?.let { d ->
            state.setAll(d.text, d.cursor.coerceIn(0, d.text.length))
            d.filePath?.let { p -> File(p).takeIf(File::exists)?.let { f ->
                // Unreadable file → treat as never-saved rather than crash the launch effect.
                fileState.file = f
                fileState.savedText = d.savedText ?: runCatching { f.readText() }.getOrDefault("")
            } }
            syncFromState(); refreshTitle()
            // Empty scratch text restoring "clean" shouldn't surface a banner.
            if (d.text.isNotEmpty() && d.savedText != d.text) restoredBanner = true
        }
    }

    // Async refine loop (invariant #1): keyed on generation; stale results
    // are dropped by EditorState.refine's raw check.
    LaunchedEffect(state.generation) {
        val raw = state.formingRaw
        if (raw.isEmpty()) return@LaunchedEffect
        val (bangla, cands) = withContext(Dispatchers.Default) {
            engine.convert(raw) to engine.suggest(raw)
        }
        state.refine(raw, bangla, cands)
        if (raw == state.formingRaw) syncFromState()
    }

    // Autosave: 2s after the last change (spec §5).
    LaunchedEffect(fieldValue.text) {
        kotlinx.coroutines.delay(2000)
        drafts.saveDraft(Draft(state.display, state.cursor, state.formingRaw,
            fileState.file?.absolutePath, fileState.savedText.takeIf { fileState.file != null }))
    }

    // Quit-path flush (spec §5 — no exit path skips the flush): covers both
    // hide-to-tray (composition dispose) and tray-quit-while-visible (DraftFlush).
    val flushDraft = {
        drafts.saveDraft(Draft(state.display, state.cursor, state.formingRaw,
            fileState.file?.absolutePath, fileState.savedText.takeIf { fileState.file != null }))
    }
    DisposableEffect(Unit) {
        DraftFlush.flush = flushDraft
        onDispose {
            flushDraft()
            DraftFlush.flush = null
        }
    }

    val forming = state.formingRange

    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = PageCard)) {
        Column(Modifier.fillMaxSize().background(Bg).onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || !(e.isMetaPressed || e.isCtrlPressed)) return@onPreviewKeyEvent false
            when {
                e.key == Key.S && e.isShiftPressed -> { doSaveAs(); true }
                e.key == Key.S -> { doSave(); true }
                e.key == Key.O -> { doOpen(); true }
                e.key == Key.N -> { doNew(); true }
                e.key == Key.P -> { doPrint(); true }
                e.key == Key.C && e.isShiftPressed -> { copyAll(); true }
                // Undo/redo may invalidate the fix popup's captured range.
                e.key == Key.Z && e.isShiftPressed -> { closeFix(); state.redo(); syncFromState(); refreshTitle(); true }
                e.key == Key.Z -> { closeFix(); state.undo(); syncFromState(); refreshTitle(); true }
                else -> false
            }
        }) {
            TopBar(
                status = status, fileName = fileName, dirty = dirty,
                onNew = ::doNew, onOpen = ::doOpen, onSave = { doSave() },
                exportOpen = exportOpen, setExportOpen = { exportOpen = it },
                onExportDocx = ::exportDocx, onExportTxt = { doSaveAs() },
                onPrint = ::doPrint, onCopyAll = ::copyAll,
                tutorialOpen = tutorialOpen, onToggleTutorial = { tutorialOpen = !tutorialOpen },
            )
            if (restoredBanner) Banner("আগের লেখা ফিরিয়ে আনা হয়েছে") { restoredBanner = false }

            if (tutorialOpen) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    TutorialView(onClose = {
                        tutorialOpen = false
                        focus.requestFocus()
                    })
                }
                StatusBar(state.committed)
                return@Column
            }

            // The page: manuscript card, centered column (spec §3).
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                Surface(
                    Modifier.fillMaxSize().widthIn(max = 760.dp).align(Alignment.TopCenter),
                    color = PageCard, shape = RoundedCornerShape(14.dp),
                ) {
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = { v ->
                            closeFix()
                            restoredBanner = false
                            if (v.text == state.display && !v.selection.collapsed) {
                                // range selection: keep it (copy must work)
                                state.commitForming()
                                fieldValue = TextFieldValue(state.display, v.selection)
                            } else {
                                val wasClick = System.currentTimeMillis() - lastPointerUp < 300
                                val moveOnly = v.text == state.display && v.selection.collapsed
                                state.applyEdit(v.text, v.selection.end)
                                syncFromState(
                                    if (moveOnly) v.selection else TextRange(state.cursor)
                                )
                                if (wasClick && moveOnly) {
                                    // click inside a committed Bengali word → fix popup
                                    state.wordRangeAt(v.selection.end)?.let { range ->
                                        val token = ++fixToken
                                        scope.launch {
                                            val cands = withContext(Dispatchers.Default) {
                                                state.candidatesForCommitted(range)
                                            }
                                            if (token == fixToken) {
                                                fixRange = range; fixCandidates = cands; highlight = 0
                                            }
                                        }
                                    }
                                }
                            }
                            refreshTitle()
                        },
                        onTextLayout = { layout = it },
                        textStyle = TextStyle(
                            color = Color.White, fontSize = 19.sp, lineHeight = 32.sp,
                            fontFamily = BengaliFontFamily,
                        ),
                        cursorBrush = SolidColor(Sky),
                        visualTransformation = underline(forming),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 40.dp, vertical = 28.dp)
                            .verticalScroll(rememberScrollState())
                            .focusRequester(focus)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val e = awaitPointerEvent(PointerEventPass.Initial)
                                        if (e.type == PointerEventType.Release)
                                            lastPointerUp = System.currentTimeMillis()
                                    }
                                }
                            }
                            .onPreviewKeyEvent { e ->
                                handleKeys(e, state, fixRange, fixCandidates,
                                    highlight, { highlight = it },
                                    onPickForming = { i -> state.pickCandidate(i); syncFromState() },
                                    onPickFix = { i ->
                                        fixRange?.let { r ->
                                            state.replaceCommitted(r, fixCandidates[i])
                                            closeFix(); syncFromState()
                                        }
                                    },
                                    onDismiss = { state.dismissPopup(); closeFix() })
                            },
                    )
                }

                // Candidate popup — forming word (primary) or committed-word fix.
                val popupCands = if (state.popupVisible) state.candidates else fixCandidates
                val anchorOffset = when {
                    state.popupVisible -> state.cursor
                    fixRange != null -> fixRange!!.first
                    else -> -1
                }
                if (popupCands.isNotEmpty() && anchorOffset >= 0) {
                    layout?.let { l ->
                        val rect = l.getCursorRect(anchorOffset.coerceAtMost(l.layoutInput.text.length))
                        Popup(offset = IntOffset(rect.left.toInt() + 64, rect.bottom.toInt() + 96)) {
                            CandidateList(popupCands, highlight) { i ->
                                if (state.popupVisible) { state.pickCandidate(i); syncFromState() }
                                else fixRange?.let { r ->
                                    state.replaceCommitted(r, popupCands[i]); closeFix(); syncFromState()
                                }
                            }
                        }
                    }
                }
            }

            StatusBar(state.committed)
        }

        pendingAction?.let { action ->
            AlertDialog(
                onDismissRequest = { pendingAction = null },
                containerColor = PageCard,
                title = { Text("অসংরক্ষিত লেখা", fontFamily = BengaliFontFamily, color = Color.White) },
                text = { Text("এই লেখাটি এখনো সেভ হয়নি।", fontFamily = BengaliFontFamily, color = SkySoft) },
                confirmButton = {
                    TextButton(onClick = { if (doSave()) { pendingAction = null; action() } }) {
                        Text("সেভ করুন", color = Green, fontFamily = BengaliFontFamily)
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { pendingAction = null; action() }) {
                            Text("সেভ ছাড়াই", color = SkySoft, fontFamily = BengaliFontFamily)
                        }
                        TextButton(onClick = { pendingAction = null }) {
                            Text("বাতিল", color = Muted, fontFamily = BengaliFontFamily)
                        }
                    }
                },
            )
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Sky underline on the forming word (spec §3); offsets are identity. */
private fun underline(range: IntRange?): VisualTransformation = VisualTransformation { text ->
    if (range == null || range.isEmpty() || range.last >= text.length) {
        TransformedText(text, OffsetMapping.Identity)
    } else {
        TransformedText(
            buildAnnotatedString {
                append(text)
                addStyle(
                    SpanStyle(color = SkySoft, textDecoration = TextDecoration.Underline),
                    range.first, range.last + 1,
                )
            },
            OffsetMapping.Identity,
        )
    }
}

/** Popup keys: ↑/↓ move, Enter picks, Esc dismisses. Consumed only while a popup shows. */
private fun handleKeys(
    e: KeyEvent,
    state: EditorState,
    fixRange: IntRange?,
    fixCandidates: List<String>,
    highlight: Int,
    setHighlight: (Int) -> Unit,
    onPickForming: (Int) -> Unit,
    onPickFix: (Int) -> Unit,
    onDismiss: () -> Unit,
): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val formingPopup = state.popupVisible
    val fixPopup = fixRange != null && fixCandidates.isNotEmpty()
    if (!formingPopup && !fixPopup) return false
    val size = if (formingPopup) state.candidates.size else fixCandidates.size
    return when (e.key) {
        Key.DirectionDown -> { setHighlight((highlight + 1) % size); true }
        Key.DirectionUp -> { setHighlight((highlight - 1 + size) % size); true }
        Key.Enter -> { if (formingPopup) onPickForming(highlight) else onPickFix(highlight); true }
        Key.Escape -> { onDismiss(); true }
        else -> false
    }
}

@Composable
private fun CandidateList(cands: List<String>, highlight: Int, onPick: (Int) -> Unit) {
    Surface(
        color = FieldBg, shape = RoundedCornerShape(10.dp), shadowElevation = 8.dp,
        modifier = Modifier.border(1.dp, CardBorder, RoundedCornerShape(10.dp)),
    ) {
        Column(Modifier.padding(6.dp)) {
            cands.take(6).forEachIndexed { i, c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (i == highlight) Sky.copy(alpha = .15f) else Color.Transparent,
                            RoundedCornerShape(6.dp),
                        )
                        .clickable { onPick(i) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(toBengaliDigits(i + 1), color = Muted, fontSize = 12.sp,
                        modifier = Modifier.padding(end = 10.dp))
                    Text(c, color = SkySoft, fontSize = 16.sp, fontFamily = BengaliFontFamily)
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    status: String, fileName: String?, dirty: Boolean,
    onNew: () -> Unit, onOpen: () -> Unit, onSave: () -> Unit,
    exportOpen: Boolean, setExportOpen: (Boolean) -> Unit,
    onExportDocx: () -> Unit, onExportTxt: () -> Unit, onPrint: () -> Unit, onCopyAll: () -> Unit,
    tutorialOpen: Boolean, onToggleTutorial: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Banglu Editor", color = Sky, fontSize = 17.sp, fontFamily = BengaliFontFamily)
        BarAction("নতুন", onNew); BarAction("খুলুন", onOpen); BarAction("সেভ", onSave)
        Box {
            BarAction("এক্সপোর্ট ▾") { setExportOpen(true) }
            DropdownMenu(expanded = exportOpen, onDismissRequest = { setExportOpen(false) }) {
                DropdownMenuItem(text = { MenuLabel("Word (.docx)") },
                    onClick = { setExportOpen(false); onExportDocx() })
                DropdownMenuItem(text = { MenuLabel("টেক্সট (.txt)") },
                    onClick = { setExportOpen(false); onExportTxt() })
                DropdownMenuItem(text = { MenuLabel("প্রিন্ট / PDF (⌘P)") },
                    onClick = { setExportOpen(false); onPrint() })
                DropdownMenuItem(text = { MenuLabel("সব কপি করুন (⇧⌘C)") },
                    onClick = { setExportOpen(false); onCopyAll() })
            }
        }
        BarAction(if (tutorialOpen) "✕ লেখায় ফিরুন" else "শিখুন", onToggleTutorial)
        Spacer(Modifier.weight(1f))
        Text(
            (fileName ?: "নতুন লেখা") + if (dirty) " ●" else "",
            color = if (dirty) SkySoft else Muted, fontSize = 12.sp, fontFamily = BengaliFontFamily,
        )
        Text(status, color = Muted, fontSize = 11.sp, fontFamily = BengaliFontFamily,
            modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun BarAction(label: String, onClick: () -> Unit) {
    Text(label, color = SkySoft, fontSize = 13.sp, fontFamily = BengaliFontFamily,
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp))
}

@Composable
private fun MenuLabel(text: String) {
    Text(text, fontSize = 13.sp, fontFamily = BengaliFontFamily)
}

@Composable
private fun Banner(text: String, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Sky.copy(alpha = .08f)).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = SkySoft, fontSize = 12.sp, fontFamily = BengaliFontFamily)
        Spacer(Modifier.weight(1f))
        Text("✕", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onDismiss))
    }
}

@Composable
private fun StatusBar(committed: String) {
    val words = committed.split(Regex("\\s+")).count { token -> token.any { it in 'ঀ'..'৿' } }
    Row(
        Modifier.fillMaxWidth().height(28.dp).background(PageCard).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${toBengaliDigits(words)} শব্দ · স্বয়ংক্রিয় সংরক্ষিত ✓", color = Muted, fontSize = 11.sp,
            fontFamily = BengaliFontFamily)
        Spacer(Modifier.weight(1f))
        Text("গ্লোবাল হটকি ⌘⇧B", color = Muted, fontSize = 11.sp, fontFamily = BengaliFontFamily)
    }
}
