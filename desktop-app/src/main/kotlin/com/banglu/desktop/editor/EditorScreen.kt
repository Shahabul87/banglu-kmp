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

/** স্পেক §3-§5: the page IS the app. One editor window, no second box. */
@Composable
fun FrameWindowScope.EditorScreen() {
    val engine = remember { RealEngineFacade }
    val state = remember { EditorState(engine) }
    val drafts = remember { DraftStore(File(System.getProperty("user.home"), ".banglu")) }
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
            syncFromState()
            if (d.savedText != d.text) restoredBanner = true
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
        drafts.saveDraft(Draft(state.display, state.cursor, state.formingRaw, filePath = null, savedText = null))
    }

    val forming = state.formingRange

    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = PageCard)) {
        Column(Modifier.fillMaxSize().background(Bg)) {
            TopBar(status)
            if (restoredBanner) Banner("আগের লেখা ফিরিয়ে আনা হয়েছে") { restoredBanner = false }

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
private fun TopBar(status: String) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("বাংলু লেখক", color = Sky, fontSize = 17.sp, fontFamily = BengaliFontFamily)
        Spacer(Modifier.weight(1f))
        Text(status, color = Muted, fontSize = 11.sp, fontFamily = BengaliFontFamily)
    }
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
