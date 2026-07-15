# বাংলু লেখক (Desktop Bangla Editor) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the desktop app's two-box converter with a full-page Bangla editor: live in-place Banglish→Bangla conversion at the cursor, Avro-style candidate popup, click-to-fix, .txt files with always-on draft autosave, docx/print-PDF export.

**Architecture:** A pure-Kotlin `EditorState` state machine (no Compose imports, JVM-unit-tested against the real `dictionary.sqlite`) owns the document: committed Bangla text plus one forming word kept as raw Banglish. A Compose `BasicTextField` renders `EditorState.display` and feeds every change back through `EditorState.applyEdit`; async engine refinement lands via a generation-guarded `refine` call. Spec: `docs/superpowers/specs/2026-07-14-desktop-bangla-editor-design.md`.

**Tech Stack:** Kotlin/JVM 17, Compose Desktop (Material3), shared `SmartEngineAdapter` engine, kotlinx-serialization, `java.util.zip` (docx), `java.awt.print` (PDF via OS dialog), Noto Sans Bengali (bundled, OFL).

## Global Constraints

- JVM toolchain 17; jvmTarget 17 (packaged runtime is Temurin 17 — class-file 61, never 65).
- No new runtime dependencies (no POI, no PDF library). Only test-scope deps may be added.
- The keystroke path NEVER does sync SQLite/disk I/O on the UI thread (repo invariant #1): keystrokes show `convertForInstantPreview` synchronously; `convertWord`/`getSuggestions` run on `Dispatchers.Default`.
- WYSIWYG (invariant #2): space commits EXACTLY the Bangla visible in the forming word.
- Learning law (invariant #3): only an explicit candidate pick that differs from what would have been committed calls `onWordSelected(..., explicitChoice = true)`. Passive space commits never learn.
- The ⌘⇧B mini converter, tray menu, and Hotkey registration are untouched.
- Bangla UI strings verbatim from the spec: বাংলু লেখক · নতুন · খুলুন · সেভ · এক্সপোর্ট · সব কপি করুন · প্রিন্ট / PDF (⌘P) · টেক্সট (.txt) · Word (.docx) · আগের লেখা ফিরিয়ে আনা হয়েছে · শব্দ · স্বয়ংক্রিয় সংরক্ষিত ✓ · পূর্ণ অভিধান ✓ · সেভ করুন · বাতিল · সেভ ছাড়াই.
- Colors: bg `0xFF080D16`, page card `0xFF0D1524`, border `0xFF1E293B`, field `0xFF101A2A`, sky `0xFF64D2FF`, sky-soft `0xFFBAE6FD`, green `0xFF4ADE80`, muted `0xFF64748B`.
- Test dictionary: repo-root `dictionary.sqlite` (exists, 150MB). From `desktop-app` tests it is `File("../dictionary.sqlite")`.
- All commands run from the repo root `/Users/mdshahabulalam/myprojects/banlgu/banglu-kmp`.

## File Structure

```
desktop-app/
├── build.gradle.kts                     MODIFY: serialization plugin, test deps
├── src/main/kotlin/com/banglu/desktop/
│   ├── Main.kt                          MODIFY: editor becomes the window; two-box App() removed
│   ├── Hotkey.kt / Paste.kt / Storage.kt  UNCHANGED (FileStorage + findDictionaryFile reused)
│   └── editor/
│       ├── EngineFacade.kt              interface + RealEngineFacade (SmartEngineAdapter wrapper)
│       ├── EditorState.kt               pure-Kotlin document/typing state machine
│       ├── DraftStore.kt                ~/.banglu/draft.json + editor.json (prefs, recents)
│       ├── DocxWriter.kt                minimal hand-written .docx
│       ├── Printer.kt                   ⌘P via java.awt.print (correct Bangla shaping)
│       ├── EditorTheme.kt               bundled Noto Sans Bengali + palette + Bengali digits util
│       └── EditorScreen.kt              Compose UI: page, popup, top bar, status bar, file ops
├── src/main/resources/fonts/            NotoSansBengali-Regular.ttf, -Bold.ttf, OFL.txt
└── src/test/kotlin/com/banglu/desktop/editor/
    ├── TestEngine.kt                    shared fixture: real engine on ../dictionary.sqlite
    ├── EditorStateTest.kt               typing, commits, backspace, দাঁড়ি, digits, popup, undo, fix
    ├── WysiwygPinTest.kt                invariant-#2 pin over a phrase corpus
    ├── DraftStoreTest.kt                draft/prefs round-trips
    └── DocxWriterTest.kt                unzip + XML content assertions
```

---

### Task 1: Build config, EngineFacade, test fixture

**Files:**
- Modify: `desktop-app/build.gradle.kts`
- Create: `desktop-app/src/main/kotlin/com/banglu/desktop/editor/EngineFacade.kt`
- Test: `desktop-app/src/test/kotlin/com/banglu/desktop/editor/TestEngine.kt`
- Test: `desktop-app/src/test/kotlin/com/banglu/desktop/editor/EditorStateTest.kt` (first test only)

**Interfaces:**
- Consumes: `SmartEngineAdapter` (shared): `initializeSync()`, `setPhoneticIndex(store)`, `convertForInstantPreview(word): String`, `convertWord(word): ConversionResult` (`.bengali`), `getSuggestions(input, limit): List<SmartSuggestion>` (`.bengali`), `onWordSelected(phonetic, bengali, learnAsWord, explicitChoice)`; `ReverseTransliterator.reverseWord(bengali): String`; `JvmSqlitePhoneticIndexStore(File)`.
- Produces: `interface EngineFacade { fun instant(raw: String): String; fun convert(raw: String): String; fun suggest(raw: String, limit: Int = 6): List<String>; fun reverse(bangla: String): String; fun selected(raw: String, bangla: String, explicit: Boolean) }`, `object RealEngineFacade : EngineFacade`, and test-side `TestEngine.facade: EngineFacade` (initialized once per JVM). Every later task uses exactly these names.

- [ ] **Step 1: Add the serialization plugin and test dependencies**

In `desktop-app/build.gradle.kts`, change the plugins block (lines 1–5) to:

```kotlin
plugins {
    kotlin("jvm")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    // Storage.kt/DraftStore.kt use @Serializable; without this plugin the
    // serializers are never generated and learned.json writes throw at runtime.
    alias(libs.plugins.kotlin.serialization)
}
```

Append inside the existing `dependencies { }` block:

```kotlin
    testImplementation(kotlin("test"))
```

Append at the end of the file (after `kotlin { jvmToolchain(17) }`):

```kotlin
tasks.withType<Test> { useJUnitPlatform() }
```

- [ ] **Step 2: Write EngineFacade + RealEngineFacade**

Create `desktop-app/src/main/kotlin/com/banglu/desktop/editor/EngineFacade.kt`:

```kotlin
package com.banglu.desktop.editor

import com.banglu.engine.SmartEngineAdapter
import com.banglu.engine.util.ReverseTransliterator

/**
 * The editor's only door to the engine — also the seam the future AI
 * corrector implements (spec §7): anything that can propose text for raw
 * Banglish can drive the editor.
 */
interface EngineFacade {
    /** Sync, rule-only, zero I/O — safe on the UI thread (invariant #1). */
    fun instant(raw: String): String
    /** Full pipeline — dictionary/SQLite; call from Dispatchers.Default only. */
    fun convert(raw: String): String
    fun suggest(raw: String, limit: Int = 6): List<String>
    fun reverse(bangla: String): String
    fun selected(raw: String, bangla: String, explicit: Boolean)
}

object RealEngineFacade : EngineFacade {
    override fun instant(raw: String) = SmartEngineAdapter.convertForInstantPreview(raw)
    override fun convert(raw: String) = SmartEngineAdapter.convertWord(raw).bengali
    override fun suggest(raw: String, limit: Int) =
        SmartEngineAdapter.getSuggestions(raw, limit).map { it.bengali }
    override fun reverse(bangla: String) = ReverseTransliterator.reverseWord(bangla)
    override fun selected(raw: String, bangla: String, explicit: Boolean) =
        SmartEngineAdapter.onWordSelected(raw, bangla, learnAsWord = false, explicitChoice = explicit)
}
```

- [ ] **Step 3: Write the test fixture and a failing smoke test**

Create `desktop-app/src/test/kotlin/com/banglu/desktop/editor/TestEngine.kt`:

```kotlin
package com.banglu.desktop.editor

import com.banglu.engine.JvmSqliteDictionaryLoader
import com.banglu.engine.JvmSqlitePhoneticIndexStore
import com.banglu.engine.SmartEngineAdapter
import java.io.File

/** Real engine on the repo-root dictionary — initialized once per test JVM. */
object TestEngine {
    val facade: EngineFacade by lazy {
        SmartEngineAdapter.initializeSync()
        SmartEngineAdapter.setPhoneticIndex(JvmSqlitePhoneticIndexStore(dbFile()))
        RealEngineFacade
    }

    fun dbFile(): File =
        File("../dictionary.sqlite").takeIf(File::exists)
            ?: JvmSqliteDictionaryLoader.findDictionarySqlite()
}
```

Create `desktop-app/src/test/kotlin/com/banglu/desktop/editor/EditorStateTest.kt`:

```kotlin
package com.banglu.desktop.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorStateTest {
    private val engine = TestEngine.facade

    @Test
    fun engineFacadeConvertsThroughTheRealStore() {
        assertEquals("কেমন", engine.convert("kemon"))
        assertTrue(engine.suggest("kemon").contains("কেমন"))
        assertTrue(engine.instant("kemon").isNotEmpty())
    }
}
```

- [ ] **Step 4: Run the test — verify it fails first, then passes**

Run: `./gradlew :desktop-app:test --tests "com.banglu.desktop.editor.EditorStateTest" 2>&1 | tail -20`
Expected first run: compiles and PASSES (this task's deliverable is scaffolding — the failing-test discipline starts with EditorState in Task 2; here the gate is that the fixture reaches the real store). If it fails with `Serializer for class ... not found`, the plugin from Step 1 is missing; if it fails opening the database, `dictionary.sqlite` is absent at the repo root.

- [ ] **Step 5: Verify the app still builds**

Run: `./gradlew :desktop-app:compileKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add desktop-app/build.gradle.kts desktop-app/src/main/kotlin/com/banglu/desktop/editor/EngineFacade.kt desktop-app/src/test
git commit -m "feat(desktop): S50 — editor scaffolding: EngineFacade, test fixture, serialization plugin"
```

---

### Task 2: EditorState — letters form live, space commits (WYSIWYG)

**Files:**
- Create: `desktop-app/src/main/kotlin/com/banglu/desktop/editor/EditorState.kt`
- Test: `desktop-app/src/test/kotlin/com/banglu/desktop/editor/EditorStateTest.kt`

**Interfaces:**
- Consumes: `EngineFacade` (Task 1).
- Produces: `class EditorState(engine: EngineFacade, banglaDigits: Boolean = true)` with — properties `committed: String`, `commitPos: Int`, `formingRaw: String`, `formingBangla: String`, `candidates: List<String>`, `generation: Long`, `forming: Boolean`, `display: String`, `cursor: Int`, `formingRange: IntRange?`, `popupVisible: Boolean`; functions `applyEdit(newText: String, newCursor: Int)`, `commitForming()`, `refine(raw: String, bangla: String, suggested: List<String>)`, `setAll(text: String, cursorAt: Int = text.length)`. Later tasks add more methods to this same class.

- [ ] **Step 1: Write the failing tests**

Append to `EditorStateTest.kt`:

```kotlin
    private fun newState() = EditorState(engine)

    /** Simulates the UI: each char arrives via applyEdit on the display text. */
    private fun EditorState.type(s: String) {
        for (c in s) applyEdit(
            display.substring(0, cursor) + c + display.substring(cursor),
            cursor + 1
        )
    }

    /** Simulates the async refine landing for the current forming word. */
    private fun EditorState.settle() {
        if (!forming) return
        refine(formingRaw, engine.convert(formingRaw), engine.suggest(formingRaw))
    }

    @Test
    fun lettersFormLiveAndSpaceCommitsTheRefinedWord() {
        val s = newState()
        s.type("kemon")
        assertEquals("kemon", s.formingRaw)
        assertTrue(s.formingBangla.isNotEmpty())          // instant preview visible
        s.settle()
        assertEquals("কেমন", s.formingBangla)              // refined in place
        assertEquals("কেমন", s.display)
        s.type(" ")
        assertEquals("কেমন ", s.committed)                 // WYSIWYG commit
        assertEquals("", s.formingRaw)
    }

    @Test
    fun spaceBeforeRefineCommitsExactlyTheVisiblePreview() {
        val s = newState()
        s.type("kemon")
        val visible = s.formingBangla                      // refine never lands
        s.type(" ")
        assertEquals("$visible ", s.committed)
    }

    @Test
    fun staleRefineIsIgnoredAfterMoreTyping() {
        val s = newState()
        s.type("ke")
        s.refine("ke", "কে", listOf("কে"))                 // late result for old raw…
        s.type("mon")
        s.refine("ke", "কে", listOf("কে"))                 // …must not clobber "kemon"
        assertEquals("kemon", s.formingRaw)
        s.settle()
        assertEquals("কেমন", s.formingBangla)
    }

    @Test
    fun setAllReplacesTheDocument() {
        val s = newState()
        s.type("kemon")
        s.setAll("আমার প্রিয় বন্ধু")
        assertEquals("আমার প্রিয় বন্ধু", s.committed)
        assertEquals("", s.formingRaw)
        assertEquals(s.committed.length, s.cursor)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :desktop-app:test --tests "com.banglu.desktop.editor.EditorStateTest" 2>&1 | tail -10`
Expected: FAIL — `unresolved reference: EditorState`.

- [ ] **Step 3: Implement EditorState (typing core)**

Create `desktop-app/src/main/kotlin/com/banglu/desktop/editor/EditorState.kt`:

```kotlin
package com.banglu.desktop.editor

/**
 * The editor's document + typing state machine. Pure Kotlin, no Compose —
 * the JVM regression wall drives it keystroke-by-keystroke against the real
 * dictionary (spec §8). The UI renders [display] and routes every text-field
 * change through [applyEdit]; async engine results land through [refine].
 *
 * Model: [committed] holds Bangla; at most ONE forming word exists at
 * [commitPos], kept as raw Banglish in [formingRaw] and shown as
 * [formingBangla]. WYSIWYG contract: a commit inserts formingBangla exactly
 * as displayed.
 */
class EditorState(
    private val engine: EngineFacade,
    var banglaDigits: Boolean = true,
) {
    var committed: String = ""
        private set
    var commitPos: Int = 0
        private set
    var formingRaw: String = ""
        private set
    var formingBangla: String = ""
        private set
    var candidates: List<String> = emptyList()
        private set
    var popupDismissed: Boolean = false
        private set

    /** Bumps whenever the forming word changes — the UI keys refine jobs on it. */
    var generation: Long = 0L
        private set

    val forming: Boolean get() = formingRaw.isNotEmpty()
    val display: String
        get() = if (!forming) committed
        else committed.substring(0, commitPos) + formingBangla + committed.substring(commitPos)
    val cursor: Int get() = commitPos + formingBangla.length
    val formingRange: IntRange?
        get() = if (forming) commitPos until (commitPos + formingBangla.length) else null
    val popupVisible: Boolean get() = forming && !popupDismissed && candidates.isNotEmpty()

    /** UI feeds every text-field change here; classifies the edit by diffing. */
    fun applyEdit(newText: String, newCursor: Int) {
        val old = display
        when {
            newText == old && newCursor == cursor -> return
            newText == old -> {                       // pure cursor move
                commitForming()
                commitPos = newCursor.coerceIn(0, committed.length)
            }
            newText.length == old.length + 1 && newCursor == cursor + 1 &&
                newText.startsWith(old.substring(0, cursor)) &&
                newText.endsWith(old.substring(cursor)) ->
                insertChar(newText[cursor])
            newText.length == old.length - 1 && newCursor == cursor - 1 && cursor > 0 &&
                newText == old.removeRange(cursor - 1, cursor) ->
                backspace()
            else -> {                                  // paste / selection replace
                commitFormingInternal()
                committed = newText
                commitPos = newCursor.coerceIn(0, committed.length)
            }
        }
    }

    private fun insertChar(c: Char) {
        when {
            c.isLetter() && c.code < 0x0980 -> {       // roman letters form Banglish
                formingRaw += c
                formingBangla = engine.instant(formingRaw)
                popupDismissed = false
                generation++
            }
            c == ' ' -> {
                if (forming) {
                    commitFormingInternal()
                    insertCommitted(" ")
                } else {
                    insertCommitted(" ")
                }
            }
            else -> {                                   // punctuation, newline, digits (Task 3)
                commitFormingInternal()
                insertCommitted(c.toString())
            }
        }
    }

    private fun backspace() {
        if (forming) {
            formingRaw = formingRaw.dropLast(1)
            formingBangla = if (forming) engine.instant(formingRaw) else ""
            if (!forming) { candidates = emptyList(); popupDismissed = false }
            generation++
        } else if (commitPos > 0) {
            committed = committed.removeRange(commitPos - 1, commitPos)
            commitPos -= 1
        }
    }

    private fun insertCommitted(s: String) {
        committed = committed.substring(0, commitPos) + s + committed.substring(commitPos)
        commitPos += s.length
    }

    /** Commits the VISIBLE Bangla exactly (WYSIWYG, invariant #2). */
    fun commitForming() {
        commitFormingInternal()
    }

    internal fun commitFormingInternal() {
        if (!forming) return
        insertCommitted(formingBangla)
        formingRaw = ""
        formingBangla = ""
        candidates = emptyList()
        popupDismissed = false
        generation++
    }

    /** Async engine result landing; stale results (raw moved on) are dropped. */
    fun refine(raw: String, bangla: String, suggested: List<String>) {
        if (raw != formingRaw) return
        formingBangla = bangla
        candidates = (suggested + raw).distinct().take(7)
    }

    /** Open-file / draft-restore entry point. */
    fun setAll(text: String, cursorAt: Int = text.length) {
        committed = text
        commitPos = cursorAt.coerceIn(0, text.length)
        formingRaw = ""
        formingBangla = ""
        candidates = emptyList()
        popupDismissed = false
        generation++
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :desktop-app:test --tests "com.banglu.desktop.editor.EditorStateTest" 2>&1 | tail -10`
Expected: PASS (all 5 tests).

- [ ] **Step 5: Commit**

```bash
git add desktop-app/src/main/kotlin/com/banglu/desktop/editor/EditorState.kt desktop-app/src/test/kotlin/com/banglu/desktop/editor/EditorStateTest.kt
git commit -m "feat(desktop): S50 — EditorState core: live forming word, WYSIWYG space commit, stale-refine guard"
```

---

### Task 3: EditorState — backspace semantics, দাঁড়ি, digits, punctuation, paste

**Files:**
- Modify: `desktop-app/src/main/kotlin/com/banglu/desktop/editor/EditorState.kt`
- Test: `desktop-app/src/test/kotlin/com/banglu/desktop/editor/EditorStateTest.kt`

**Interfaces:**
- Consumes/Produces: same `EditorState` class; behavior additions only (no new public members).

- [ ] **Step 1: Write the failing tests**

Append to `EditorStateTest.kt`:

```kotlin
    @Test
    fun backspaceEditsTheRawBanglishNotTheOutput() {
        val s = newState()
        s.type("kali")
        s.applyEdit(s.display.dropLast(1), s.cursor - 1)   // one backspace
        assertEquals("kal", s.formingRaw)
        s.settle()
        assertEquals("কাল", s.formingBangla)
    }

    @Test
    fun backspaceOnCommittedTextDeletesOneChar() {
        val s = newState()
        s.setAll("কেমন ")
        s.applyEdit("কেমন", 4)
        assertEquals("কেমন", s.committed)
    }

    @Test
    fun doubleSpaceMakesDari() {
        val s = newState()
        s.type("kemon")
        s.type(" ")
        s.type(" ")
        assertTrue(s.committed.endsWith("। "), "got: '${s.committed}'")
        s.type(" ")                                        // third space: plain space
        assertTrue(s.committed.endsWith("।  "))
    }

    @Test
    fun digitsCommitTheFormingWordAndBecomeBengali() {
        val s = newState()
        s.type("kemon")
        s.settle()
        s.type("5")
        assertEquals("কেমন৫", s.committed)
        s.banglaDigits = false
        s.type("7")
        assertEquals("কেমন৫7", s.committed)
    }

    @Test
    fun punctuationAndNewlineCommitFirst() {
        val s = newState()
        s.type("kemon")
        s.settle()
        s.type(",")
        assertEquals("কেমন,", s.committed)
        s.type("acho")
        s.settle()
        s.type("\n")
        assertTrue(s.committed.endsWith("আছো\n"))
    }

    @Test
    fun pasteCommitsFormingThenAcceptsVerbatim() {
        val s = newState()
        s.type("kemon")
        s.settle()
        val pasted = s.display + " আছো বন্ধু"
        s.applyEdit(pasted, pasted.length)                 // multi-char change
        assertEquals("কেমন আছো বন্ধু", s.committed)
        assertEquals("", s.formingRaw)
    }
```

- [ ] **Step 2: Run tests to verify the new ones fail**

Run: `./gradlew :desktop-app:test --tests "com.banglu.desktop.editor.EditorStateTest" 2>&1 | tail -15`
Expected: `doubleSpaceMakesDari` and `digitsCommitTheFormingWordAndBecomeBengali` FAIL (dari/digit logic missing); backspace/punctuation/paste tests already pass from Task 2.

- [ ] **Step 3: Implement dari and digit handling**

In `EditorState.kt`, replace the `c == ' '` branch of `insertChar` with:

```kotlin
            c == ' ' -> {
                if (forming) {
                    commitFormingInternal()
                    insertCommitted(" ")
                } else if (commitPos >= 1 && committed[commitPos - 1] == ' ' &&
                    (commitPos < 2 || committed[commitPos - 2] !in " ।\n")
                ) {
                    // Double space after a word → দাঁড়ি (spec §4, Android rule)
                    committed = committed.replaceRange(commitPos - 1, commitPos, "। ")
                    commitPos += 1
                } else {
                    insertCommitted(" ")
                }
            }
```

And replace the final `else` branch of `insertChar` with:

```kotlin
            c.isDigit() -> {
                commitFormingInternal()
                insertCommitted(if (banglaDigits) bengaliDigit(c) else c.toString())
            }
            else -> {
                commitFormingInternal()
                insertCommitted(c.toString())
            }
```

Add at the bottom of the file (top level, below the class):

```kotlin
internal fun bengaliDigit(c: Char): String =
    if (c in '0'..'9') ('০' + (c - '0')).toString() else c.toString()
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :desktop-app:test --tests "com.banglu.desktop.editor.EditorStateTest" 2>&1 | tail -10`
Expected: PASS (11 tests).

- [ ] **Step 5: Commit**

```bash
git add desktop-app/src/main/kotlin/com/banglu/desktop/editor/EditorState.kt desktop-app/src/test/kotlin/com/banglu/desktop/editor/EditorStateTest.kt
git commit -m "feat(desktop): S50 — backspace edits raw Banglish, double-space dari, Bengali digits, paste fallback"
```

---

### Task 4: EditorState — candidate popup: pick, learn-law, dismiss

**Files:**
- Modify: `desktop-app/src/main/kotlin/com/banglu/desktop/editor/EditorState.kt`
- Test: `desktop-app/src/test/kotlin/com/banglu/desktop/editor/EditorStateTest.kt`

**Interfaces:**
- Produces (added to `EditorState`): `fun pickCandidate(index: Int)`, `fun dismissPopup()`. Digit keys `'1'..'6'` while `popupVisible` pick candidates instead of inserting digits. `candidates` always ends with the raw Banglish itself (one-keypress inline English).

- [ ] **Step 1: Write the failing tests**

Append to `EditorStateTest.kt`:

```kotlin
    @Test
    fun candidatesIncludeTheRawFormForInlineEnglish() {
        val s = newState()
        s.type("kali")
        s.settle()
        assertTrue(s.popupVisible)
        assertTrue(s.candidates.contains("kali"))
    }

    @Test
    fun digitKeyPicksACandidateWhilePopupVisible() {
        val s = newState()
        s.type("kemon")
        s.settle()
        val second = s.candidates[1]
        s.type("2")
        assertEquals("$second ", s.committed)              // pick commits word + space
        assertEquals("", s.formingRaw)
    }

    @Test
    fun pickingANonPrimaryCandidateTeachesTheEngine() {
        val s = newState()
        s.type("kali")
        s.settle()
        val primary = s.formingBangla
        val other = s.candidates.first { it != primary && it != "kali" }
        s.pickCandidate(s.candidates.indexOf(other))
        assertEquals("$other ", s.committed)
        // The explicit pick becomes the new primary (in-memory preference).
        assertEquals(other, engine.convert("kali"))
    }

    @Test
    fun escapeDismissesThePopupAndDigitsInsertAgain() {
        val s = newState()
        s.type("kemon")
        s.settle()
        s.dismissPopup()
        assertTrue(!s.popupVisible)
        s.type("3")
        assertTrue(s.committed.endsWith("৩"))              // digit, not a pick
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :desktop-app:test --tests "com.banglu.desktop.editor.EditorStateTest" 2>&1 | tail -15`
Expected: FAIL — `unresolved reference: pickCandidate` / `dismissPopup`; digit-pick test inserts ২ instead of picking.

- [ ] **Step 3: Implement pick/dismiss and the digit-pick branch**

In `EditorState.kt`, add ABOVE the `c.isDigit()` branch inside `insertChar` (order matters — pick wins over digit insertion while the popup shows):

```kotlin
            c in '1'..'6' && popupVisible -> pickCandidate(c - '1')
```

Add these methods to the class:

```kotlin
    /**
     * Commits [candidates][index] + a space. Learning law (invariant #3):
     * ONLY a pick that differs from what would have been committed anyway is
     * an explicit choice; picking the engine's own primary teaches nothing.
     */
    fun pickCandidate(index: Int) {
        val choice = candidates.getOrNull(index) ?: return
        if (!forming) return
        if (choice != formingBangla) engine.selected(formingRaw, choice, explicit = true)
        formingBangla = choice
        commitFormingInternal()
        insertCommitted(" ")
    }

    fun dismissPopup() {
        popupDismissed = true
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :desktop-app:test --tests "com.banglu.desktop.editor.EditorStateTest" 2>&1 | tail -10`
Expected: PASS (15 tests). Note: `pickingANonPrimaryCandidateTeachesTheEngine` mutates the singleton engine's in-memory preference for `kali` — it uses its own key and no other test asserts `kali`'s primary, so ordering stays independent.

- [ ] **Step 5: Commit**

```bash
git add desktop-app/src/main/kotlin/com/banglu/desktop/editor/EditorState.kt desktop-app/src/test/kotlin/com/banglu/desktop/editor/EditorStateTest.kt
git commit -m "feat(desktop): S50 — candidate popup: digit picks, raw-form candidate, explicit-choice learning law"
```

---

### Task 5: EditorState — undo/redo snapshots

**Files:**
- Modify: `desktop-app/src/main/kotlin/com/banglu/desktop/editor/EditorState.kt`
- Test: `desktop-app/src/test/kotlin/com/banglu/desktop/editor/EditorStateTest.kt`

**Interfaces:**
- Produces (added to `EditorState`): `fun undo()`, `fun redo()`. Undoing a commit restores the forming word (spec §4).

- [ ] **Step 1: Write the failing tests**

Append to `EditorStateTest.kt`:

```kotlin
    @Test
    fun undoOfACommitRestoresTheFormingWord() {
        val s = newState()
        s.type("kemon")
        s.settle()
        s.type(" ")
        assertEquals("কেমন ", s.committed)
        s.undo()                                           // un-commit
        assertEquals("kemon", s.formingRaw)
        assertEquals("", s.committed)
        s.redo()
        assertEquals("কেমন ", s.committed)
        assertEquals("", s.formingRaw)
    }

    @Test
    fun undoRestoresDeletedText() {
        val s = newState()
        s.setAll("কেমন আছো")
        s.applyEdit("কেমন", 4)                             // selection-delete " আছো"
        assertEquals("কেমন", s.committed)
        s.undo()
        assertEquals("কেমন আছো", s.committed)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :desktop-app:test --tests "com.banglu.desktop.editor.EditorStateTest" 2>&1 | tail -10`
Expected: FAIL — `unresolved reference: undo`.

- [ ] **Step 3: Implement snapshots**

In `EditorState.kt`, add inside the class:

```kotlin
    data class Snapshot(val committed: String, val commitPos: Int, val formingRaw: String)

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()

    private fun pushUndo() {
        undoStack.addLast(Snapshot(committed, commitPos, formingRaw))
        if (undoStack.size > 200) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun restore(s: Snapshot) {
        committed = s.committed
        commitPos = s.commitPos
        formingRaw = s.formingRaw
        formingBangla = if (forming) engine.instant(formingRaw) else ""
        candidates = emptyList()
        popupDismissed = false
        generation++
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(Snapshot(committed, commitPos, formingRaw))
        restore(prev)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(Snapshot(committed, commitPos, formingRaw))
        restore(next)
    }
```

Then add `pushUndo()` as the FIRST line of: the `commitFormingInternal` body (guarded — put it after the `if (!forming) return`), the dari branch, the digit branch, the punctuation `else` branch, the non-forming half of `backspace` (before `committed = ...`), the paste `else` branch of `applyEdit`, and `pickCandidate` (after the null/forming guards). Exactly these seven sites — letter keystrokes and forming-word backspaces are NOT undo points (undo works at word granularity while typing).

The space branch and cursor-move branch call `commitFormingInternal()`, which pushes its own snapshot — do not double-push there.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :desktop-app:test --tests "com.banglu.desktop.editor.EditorStateTest" 2>&1 | tail -10`
Expected: PASS (17 tests).

- [ ] **Step 5: Commit**

```bash
git add desktop-app/src/main/kotlin/com/banglu/desktop/editor/EditorState.kt desktop-app/src/test/kotlin/com/banglu/desktop/editor/EditorStateTest.kt
git commit -m "feat(desktop): S50 — undo/redo snapshots; undoing a commit restores the forming word"
```

---

### Task 6: EditorState — click-to-fix committed words + WYSIWYG pin test

**Files:**
- Modify: `desktop-app/src/main/kotlin/com/banglu/desktop/editor/EditorState.kt`
- Test: `desktop-app/src/test/kotlin/com/banglu/desktop/editor/EditorStateTest.kt`
- Test: Create `desktop-app/src/test/kotlin/com/banglu/desktop/editor/WysiwygPinTest.kt`

**Interfaces:**
- Produces (added to `EditorState`): `fun wordRangeAt(offset: Int): IntRange?` (Bengali-word bounds in `committed`), `fun candidatesForCommitted(range: IntRange): List<String>` (CALL FROM Dispatchers.Default — hits the store via reverse+suggest), `fun replaceCommitted(range: IntRange, replacement: String)` (swaps + learns explicit). This trio is the AI seam's operation set (spec §7).

- [ ] **Step 1: Write the failing tests**

Append to `EditorStateTest.kt`:

```kotlin
    @Test
    fun clickToFixSwapsACommittedWordAndLearns() {
        val s = newState()
        s.setAll("আমি করছি এখন")
        val range = s.wordRangeAt(5)!!                     // inside করছি
        assertEquals("করছি", s.committed.substring(range.first, range.last + 1))
        val cands = s.candidatesForCommitted(range)
        assertTrue(cands.isNotEmpty())
        val other = cands.first { it != "করছি" }
        s.replaceCommitted(range, other)
        assertEquals("আমি $other এখন", s.committed)
        s.undo()
        assertEquals("আমি করছি এখন", s.committed)
    }

    @Test
    fun wordRangeAtReturnsNullOutsideBengaliWords() {
        val s = newState()
        s.setAll("কেমন আছো")
        assertEquals(null, s.wordRangeAt(4))               // the space
        assertEquals(0..3, s.wordRangeAt(2))
    }
```

Create `WysiwygPinTest.kt`:

```kotlin
package com.banglu.desktop.editor

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Invariant #2 pin: what the forming word SHOWS is what space COMMITS —
 * both with the refine landed (normal) and without it (fast typist).
 */
class WysiwygPinTest {
    private val engine = TestEngine.facade

    private val phrases = listOf(
        "kemon acho bondhu",
        "ami bangla likhi",
        "issa korche golpo bolte",
        "bujte parcina keno",
        "kalke dekha hobe",
    )

    private fun run(phrase: String, settle: Boolean): String {
        val s = EditorState(engine)
        val previews = mutableListOf<String>()
        for (word in phrase.split(" ")) {
            for (c in word) s.applyEdit(
                s.display.substring(0, s.cursor) + c + s.display.substring(s.cursor),
                s.cursor + 1
            )
            if (settle) s.refine(s.formingRaw, engine.convert(s.formingRaw), engine.suggest(s.formingRaw))
            previews.add(s.formingBangla)                  // what the user SEES
            s.applyEdit(s.display.substring(0, s.cursor) + " " + s.display.substring(s.cursor), s.cursor + 1)
        }
        assertEquals(previews.joinToString(" ") + " ", s.committed, "phrase: $phrase settle=$settle")
        return s.committed
    }

    @Test fun committedEqualsPreviewsWithRefine() { phrases.forEach { run(it, settle = true) } }
    @Test fun committedEqualsPreviewsWithoutRefine() { phrases.forEach { run(it, settle = false) } }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :desktop-app:test --tests "com.banglu.desktop.editor.*" 2>&1 | tail -15`
Expected: `EditorStateTest` FAILS — `unresolved reference: wordRangeAt`. `WysiwygPinTest` PASSES already (it exercises Task 2 behavior — it is the permanent pin, added here where the state machine is complete).

- [ ] **Step 3: Implement the committed-word trio**

Add to `EditorState.kt` (inside the class):

```kotlin
    private fun Char.isBengali() = this in 'ঀ'..'৿'

    /** Bounds of the Bengali word around [offset] in [committed], else null. */
    fun wordRangeAt(offset: Int): IntRange? {
        if (committed.isEmpty()) return null
        var i = offset.coerceIn(0, committed.length)
        if (i == committed.length || !committed[i].isBengali()) i -= 1
        if (i < 0 || !committed[i].isBengali()) return null
        var start = i
        while (start > 0 && committed[start - 1].isBengali()) start--
        var end = i
        while (end < committed.length - 1 && committed[end + 1].isBengali()) end++
        return start..end
    }

    /** Candidates for an already-committed word. Store I/O — Dispatchers.Default only. */
    fun candidatesForCommitted(range: IntRange): List<String> {
        val word = committed.substring(range.first, range.last + 1)
        val raw = engine.reverse(word)
        return (engine.suggest(raw, 6) + word).distinct()
    }

    /** Swaps a committed word; a different choice is an explicit correction. */
    fun replaceCommitted(range: IntRange, replacement: String) {
        val word = committed.substring(range.first, range.last + 1)
        if (replacement == word) return
        pushUndo()
        engine.selected(engine.reverse(word), replacement, explicit = true)
        committed = committed.replaceRange(range.first, range.last + 1, replacement)
        commitPos = range.first + replacement.length
        generation++
    }
```

- [ ] **Step 4: Run the full desktop test suite**

Run: `./gradlew :desktop-app:test 2>&1 | tail -10`
Expected: PASS (21 tests across 3 classes).

- [ ] **Step 5: Commit**

```bash
git add desktop-app/src/main/kotlin/com/banglu/desktop/editor/EditorState.kt desktop-app/src/test/kotlin/com/banglu/desktop/editor/
git commit -m "feat(desktop): S50 — click-to-fix committed words (the AI seam ops) + WYSIWYG pin tests"
```

---

### Task 7: DraftStore — autosave draft + editor prefs

**Files:**
- Create: `desktop-app/src/main/kotlin/com/banglu/desktop/editor/DraftStore.kt`
- Test: `desktop-app/src/test/kotlin/com/banglu/desktop/editor/DraftStoreTest.kt`

**Interfaces:**
- Produces: `@Serializable data class Draft(val text: String, val cursor: Int, val formingRaw: String, val filePath: String?, val savedText: String?)`; `@Serializable data class EditorPrefs(val recent: List<String> = emptyList(), val banglaDigits: Boolean = true, val winW: Int = 860, val winH: Int = 640)`; `class DraftStore(dir: File)` with `saveDraft(d: Draft)`, `loadDraft(): Draft?`, `clearDraft()`, `savePrefs(p: EditorPrefs)`, `loadPrefs(): EditorPrefs`. Production dir: `File(System.getProperty("user.home"), ".banglu")`.

- [ ] **Step 1: Write the failing tests**

Create `DraftStoreTest.kt`:

```kotlin
package com.banglu.desktop.editor

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DraftStoreTest {
    private fun tempStore() = DraftStore(createTempDirectory("banglu-test").toFile())

    @Test
    fun draftRoundTripsAndClears() {
        val store = tempStore()
        assertNull(store.loadDraft())
        val d = Draft("কেমন আছো ", 9, "bondh", "/tmp/চিঠি.txt", "কেমন আছো ")
        store.saveDraft(d)
        assertEquals(d, store.loadDraft())
        store.clearDraft()
        assertNull(store.loadDraft())
    }

    @Test
    fun prefsRoundTripWithDefaults() {
        val store = tempStore()
        assertEquals(EditorPrefs(), store.loadPrefs())     // defaults when missing
        val p = EditorPrefs(recent = listOf("/a.txt", "/b.txt"), banglaDigits = false, winW = 900, winH = 700)
        store.savePrefs(p)
        assertEquals(p, store.loadPrefs())
    }

    @Test
    fun corruptFilesFallBackSafely() {
        val dir = createTempDirectory("banglu-test").toFile()
        File(dir, "draft.json").writeText("{not json")
        File(dir, "editor.json").writeText("{not json")
        val store = DraftStore(dir)
        assertNull(store.loadDraft())
        assertEquals(EditorPrefs(), store.loadPrefs())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :desktop-app:test --tests "com.banglu.desktop.editor.DraftStoreTest" 2>&1 | tail -10`
Expected: FAIL — `unresolved reference: DraftStore`.

- [ ] **Step 3: Implement DraftStore**

Create `DraftStore.kt`:

```kotlin
package com.banglu.desktop.editor

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Crash-proof persistence (spec §5): [Draft] is the always-on autosave —
 * the full working state including the forming word; [EditorPrefs] is
 * durable UI state. Writes are atomic (tmp + rename) so a crash mid-write
 * never corrupts the previous draft.
 */
@Serializable
data class Draft(
    val text: String,
    val cursor: Int,
    val formingRaw: String,
    val filePath: String?,
    val savedText: String?,
)

@Serializable
data class EditorPrefs(
    val recent: List<String> = emptyList(),
    val banglaDigits: Boolean = true,
    val winW: Int = 860,
    val winH: Int = 640,
)

class DraftStore(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val draftFile = File(dir, "draft.json")
    private val prefsFile = File(dir, "editor.json")

    init { dir.mkdirs() }

    private fun writeAtomic(target: File, content: String) {
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(content)
        tmp.renameTo(target)
    }

    fun saveDraft(d: Draft) = writeAtomic(draftFile, json.encodeToString(d))

    fun loadDraft(): Draft? = draftFile.takeIf(File::exists)?.let {
        runCatching { json.decodeFromString<Draft>(it.readText()) }.getOrNull()
    }

    fun clearDraft() { draftFile.delete() }

    fun savePrefs(p: EditorPrefs) = writeAtomic(prefsFile, json.encodeToString(p))

    fun loadPrefs(): EditorPrefs = prefsFile.takeIf(File::exists)?.let {
        runCatching { json.decodeFromString<EditorPrefs>(it.readText()) }.getOrNull()
    } ?: EditorPrefs()
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :desktop-app:test --tests "com.banglu.desktop.editor.DraftStoreTest" 2>&1 | tail -10`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add desktop-app/src/main/kotlin/com/banglu/desktop/editor/DraftStore.kt desktop-app/src/test/kotlin/com/banglu/desktop/editor/DraftStoreTest.kt
git commit -m "feat(desktop): S50 — DraftStore: atomic crash-proof autosave + editor prefs"
```

---

### Task 8: DocxWriter — minimal hand-written .docx

**Files:**
- Create: `desktop-app/src/main/kotlin/com/banglu/desktop/editor/DocxWriter.kt`
- Test: `desktop-app/src/test/kotlin/com/banglu/desktop/editor/DocxWriterTest.kt`

**Interfaces:**
- Produces: `object DocxWriter { fun write(text: String, out: File) }`. One paragraph per `\n`-separated line; declares Noto Sans Bengali with Nirmala UI fallback; Word does the script shaping (spec §6).

- [ ] **Step 1: Write the failing test**

Create `DocxWriterTest.kt`:

```kotlin
package com.banglu.desktop.editor

import java.io.File
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocxWriterTest {
    @Test
    fun writesAValidDocxContainingTheExactBangla() {
        val out = File(createTempDirectory("banglu-test").toFile(), "চিঠি.docx")
        DocxWriter.write("আমার প্রিয় বন্ধু,\nকেমন আছো? <3 & সব ভালো\n\nইতি", out)
        ZipFile(out).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue("word/document.xml" in names)
            assertTrue("[Content_Types].xml" in names)
            assertTrue("_rels/.rels" in names)
            val doc = zip.getInputStream(zip.getEntry("word/document.xml")).readBytes().decodeToString()
            assertTrue("আমার প্রিয় বন্ধু," in doc)
            assertTrue("&lt;3 &amp; সব ভালো" in doc)        // XML-escaped
            assertTrue("Noto Sans Bengali" in doc)
            // 4 paragraphs (empty line = empty paragraph)
            assertEquals(4, Regex("<w:p>").findAll(doc).count())
            // must parse as XML
            javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(zip.getInputStream(zip.getEntry("word/document.xml")))
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :desktop-app:test --tests "com.banglu.desktop.editor.DocxWriterTest" 2>&1 | tail -10`
Expected: FAIL — `unresolved reference: DocxWriter`.

- [ ] **Step 3: Implement DocxWriter**

Create `DocxWriter.kt`:

```kotlin
package com.banglu.desktop.editor

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal OOXML by hand — deliberately no POI/docx4j (spec §6). A .docx is a
 * zip of three XML parts; Word/Pages/LibreOffice do the Bangla shaping
 * themselves, so conjuncts are correct even without our bundled font.
 * w:cs (complex script) attributes are REQUIRED — without them Word picks a
 * Latin font for Bengali runs and sizes them wrong.
 */
object DocxWriter {

    private const val FONTS =
        """<w:rFonts w:ascii="Noto Sans Bengali" w:hAnsi="Noto Sans Bengali" w:cs="Noto Sans Bengali"/>"""
    private const val RPR = """<w:rPr>$FONTS<w:sz w:val="28"/><w:szCs w:val="28"/><w:cs/></w:rPr>"""

    fun write(text: String, out: File) {
        ZipOutputStream(out.outputStream()).use { zip ->
            zip.put("[Content_Types].xml", CONTENT_TYPES)
            zip.put("_rels/.rels", RELS)
            zip.put("word/document.xml", documentXml(text))
        }
    }

    private fun ZipOutputStream.put(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray())
        closeEntry()
    }

    private fun documentXml(text: String): String {
        val paragraphs = text.split("\n").joinToString("") { line ->
            "<w:p><w:pPr>$RPR</w:pPr>" +
                (if (line.isEmpty()) "" else "<w:r>$RPR<w:t xml:space=\"preserve\">${escape(line)}</w:t></w:r>") +
                "</w:p>"
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""" +
            "<w:body>$paragraphs</w:body></w:document>"
    }

    private fun escape(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

    private val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :desktop-app:test --tests "com.banglu.desktop.editor.DocxWriterTest" 2>&1 | tail -10`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop-app/src/main/kotlin/com/banglu/desktop/editor/DocxWriter.kt desktop-app/src/test/kotlin/com/banglu/desktop/editor/DocxWriterTest.kt
git commit -m "feat(desktop): S50 — hand-written minimal .docx export with complex-script font declarations"
```

---

### Task 9: Fonts, theme, printing

**Files:**
- Create: `desktop-app/src/main/resources/fonts/NotoSansBengali-Regular.ttf`, `NotoSansBengali-Bold.ttf`, `OFL.txt` (downloaded)
- Create: `desktop-app/src/main/kotlin/com/banglu/desktop/editor/EditorTheme.kt`
- Create: `desktop-app/src/main/kotlin/com/banglu/desktop/editor/Printer.kt`

**Interfaces:**
- Produces: `EditorTheme.kt` — `val Bg/PageCard/CardBorder/FieldBg/Sky/SkySoft/Green/Muted: Color` (exact values from Global Constraints), `val BengaliFontFamily: FontFamily` (Compose), `val AwtBengaliFont: java.awt.Font` (for printing), `fun toBengaliDigits(n: Int): String`. `Printer.kt` — `object Printer { fun print(text: String, jobName: String): Boolean }` (returns false when the user cancels the dialog).

- [ ] **Step 1: Download the fonts (OFL-licensed, from the official notofonts release repo)**

```bash
mkdir -p desktop-app/src/main/resources/fonts
curl -fL -o desktop-app/src/main/resources/fonts/NotoSansBengali-Regular.ttf \
  "https://github.com/notofonts/bengali/raw/main/fonts/NotoSansBengali/hinted/ttf/NotoSansBengali-Regular.ttf"
curl -fL -o desktop-app/src/main/resources/fonts/NotoSansBengali-Bold.ttf \
  "https://github.com/notofonts/bengali/raw/main/fonts/NotoSansBengali/hinted/ttf/NotoSansBengali-Bold.ttf"
curl -fL -o desktop-app/src/main/resources/fonts/OFL.txt \
  "https://github.com/notofonts/bengali/raw/main/OFL.txt"
file desktop-app/src/main/resources/fonts/NotoSansBengali-Regular.ttf
```

Expected: `TrueType Font data`, each .ttf > 200KB. If the URLs 404 (repo layout moved), fetch from https://notofonts.github.io/ → Noto Sans Bengali → download the hinted static TTFs, and place the same three files — the file NAMES above are load-bearing (referenced from code).

- [ ] **Step 2: Write EditorTheme**

Create `EditorTheme.kt`:

```kotlin
package com.banglu.desktop.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/** Brand palette (same as Android/web) + the bundled Bangla face (spec §3). */
val Bg = Color(0xFF080D16)
val PageCard = Color(0xFF0D1524)
val CardBorder = Color(0xFF1E293B)
val FieldBg = Color(0xFF101A2A)
val Sky = Color(0xFF64D2FF)
val SkySoft = Color(0xFFBAE6FD)
val Green = Color(0xFF4ADE80)
val Muted = Color(0xFF64748B)

val BengaliFontFamily = FontFamily(
    Font(resource = "fonts/NotoSansBengali-Regular.ttf", weight = FontWeight.Normal),
    Font(resource = "fonts/NotoSansBengali-Bold.ttf", weight = FontWeight.Bold),
)

/** Same face for java.awt printing — Java2D shapes Bengali correctly. */
val AwtBengaliFont: java.awt.Font by lazy {
    object {}.javaClass.getResourceAsStream("/fonts/NotoSansBengali-Regular.ttf")!!.use {
        java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, it)
    }
}

fun toBengaliDigits(n: Int): String =
    n.toString().map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("")
```

- [ ] **Step 3: Write Printer**

Create `Printer.kt`:

```kotlin
package com.banglu.desktop.editor

import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.awt.print.PrinterJob
import java.text.AttributedString

/**
 * প্রিন্ট / PDF (spec §6): the OS print dialog — macOS offers "Save as PDF",
 * Windows "Microsoft Print to PDF". Java2D + LineBreakMeasurer shape Bengali
 * correctly; direct-PDF Java libraries do NOT (no complex-script shaping),
 * which is why there is deliberately no PDF library here.
 */
object Printer {

    fun print(text: String, jobName: String): Boolean {
        val job = PrinterJob.getPrinterJob()
        job.setJobName(jobName)
        job.setPrintable(BanglaPrintable(text, jobName))
        if (!job.printDialog()) return false
        job.print()
        return true
    }

    private class BanglaPrintable(private val text: String, private val header: String) : Printable {
        private val body = AwtBengaliFont.deriveFont(14f)
        private val headerFont = AwtBengaliFont.deriveFont(9f)

        override fun print(g: java.awt.Graphics, pf: PageFormat, pageIndex: Int): Int {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val x = pf.imageableX.toFloat() + 24f
            val width = pf.imageableWidth.toFloat() - 48f
            val top = pf.imageableY.toFloat() + 40f
            val bottom = (pf.imageableY + pf.imageableHeight).toFloat() - 32f

            // Lay out ALL lines once, then draw only this page's slice.
            data class Line(val draw: (Graphics2D, Float) -> Unit, val ascent: Float, val descent: Float)
            val lines = mutableListOf<Line>()
            for (para in text.split("\n")) {
                if (para.isEmpty()) {
                    val lm = g2.getFontMetrics(body)
                    lines.add(Line({ _, _ -> }, lm.ascent.toFloat(), lm.descent + lm.leading.toFloat()))
                    continue
                }
                val attr = AttributedString(para, mapOf(TextAttribute.FONT to body))
                val measurer = LineBreakMeasurer(attr.iterator, g2.fontRenderContext)
                while (measurer.position < para.length) {
                    val layout = measurer.nextLayout(width)
                    lines.add(Line({ g, y -> layout.draw(g, x, y) }, layout.ascent, layout.descent + layout.leading))
                }
            }

            val pageHeight = bottom - top
            var page = 0
            var y = 0f
            var i = 0
            val start = mutableListOf(0)
            while (i < lines.size) {
                val h = lines[i].ascent + lines[i].descent
                if (y + h > pageHeight && y > 0f) { start.add(i); y = 0f; page++ }
                y += h; i++
            }
            if (pageIndex > page) return Printable.NO_SUCH_PAGE

            g2.font = headerFont
            g2.drawString(header, x, pf.imageableY.toFloat() + 18f)

            var drawY = top
            val from = start[pageIndex]
            val to = if (pageIndex + 1 < start.size) start[pageIndex + 1] else lines.size
            for (j in from until to) {
                drawY += lines[j].ascent
                lines[j].draw(g2, drawY)
                drawY += lines[j].descent
            }
            return Printable.PAGE_EXISTS
        }
    }
}
```

- [ ] **Step 4: Verify compilation and the font resource**

Run: `./gradlew :desktop-app:compileKotlin 2>&1 | tail -3 && ls -la desktop-app/src/main/resources/fonts/`
Expected: BUILD SUCCESSFUL; three files present, both .ttf > 200KB.

- [ ] **Step 5: Commit**

```bash
git add desktop-app/src/main/resources/fonts/ desktop-app/src/main/kotlin/com/banglu/desktop/editor/EditorTheme.kt desktop-app/src/main/kotlin/com/banglu/desktop/editor/Printer.kt
git commit -m "feat(desktop): S50 — bundled Noto Sans Bengali (OFL), brand theme, Java2D print/PDF path"
```

---

### Task 10: EditorScreen — the page, popup, top bar, status bar

**Files:**
- Create: `desktop-app/src/main/kotlin/com/banglu/desktop/editor/EditorScreen.kt`

**Interfaces:**
- Consumes: `EditorState`, `EngineFacade`/`RealEngineFacade`, `DraftStore`/`Draft`/`EditorPrefs`, `DocxWriter`, `Printer`, theme values, `FileStorage`, `findDictionaryFile()` (`com.banglu.desktop.Storage.kt`), `JvmSqliteDictionaryLoader`, `JvmSqlitePhoneticIndexStore`, `SmartEngineAdapter.initialize/initializeSync/setPhoneticIndex/configurePersistenceScope`.
- Produces: `@Composable fun FrameWindowScope.EditorScreen()` — the full main-window content, used by `Main.kt` in Task 12. This task builds the typing surface + popup + bars only; the top bar shows just the title and status. Task 11 adds the file/export actions and their buttons into the same file.

No unit tests (Compose UI); the gate is `:desktop-app:run` + the Task 12 packaged build. All EditorState behavior is already test-covered.

- [ ] **Step 1: Implement EditorScreen**

Create `EditorScreen.kt`:

```kotlin
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
import androidx.compose.ui.text.AnnotatedString
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
    var lastPointerUp by remember { mutableStateOf(0L) }

    fun syncFromState(sel: TextRange = TextRange(state.cursor)) {
        fieldValue = TextFieldValue(state.display, sel)
        highlight = 0
    }

    fun closeFix() { fixRange = null; fixCandidates = emptyList() }

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
                                        scope.launch {
                                            val cands = withContext(Dispatchers.Default) {
                                                state.candidatesForCommitted(range)
                                            }
                                            fixRange = range; fixCandidates = cands; highlight = 0
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
```

- [ ] **Step 2: Compile**

Run: `./gradlew :desktop-app:compileKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL. (The popup pixel offsets `+64/+96` compensate for the page padding — they get visually tuned in Step 3.)

- [ ] **Step 3: Smoke-run from the dev tree**

Task 12 wires `EditorScreen` into `Main.kt`; until then, verify it compiles only. If you want an early visual check, temporarily point `Main.kt`'s window body at `EditorScreen { }` and `./gradlew :desktop-app:run` — type `kemon acho`, confirm live forming + popup + space commits — then revert the temporary change (Task 12 does the real wiring with file ops).

- [ ] **Step 4: Commit**

```bash
git add desktop-app/src/main/kotlin/com/banglu/desktop/editor/EditorScreen.kt
git commit -m "feat(desktop): S50 — EditorScreen: live page, forming underline, candidate + fix popups, bars"
```

---

### Task 11: File operations, export menu, shortcuts

**Files:**
- Modify: `desktop-app/src/main/kotlin/com/banglu/desktop/editor/EditorScreen.kt`

**Interfaces:**
- Consumes: `DocxWriter.write(text, out)`, `Printer.print(text, jobName)`, `DraftStore` prefs, `FrameWindowScope.window: ComposeWindow` (for `java.awt.FileDialog` — native dialogs).
- Produces: inside `EditorScreen.kt` — `class FileState { var file: File?; var savedText: String; fun dirty(current: String): Boolean }` plus top-bar actions নতুন/খুলুন/সেভ/এক্সপোর্ট▾ and shortcuts ⌘N ⌘O ⌘S ⇧⌘S ⌘P ⇧⌘C ⌘Z ⇧⌘Z.

- [ ] **Step 1: Add FileState + dialogs + unsaved guard**

Add to `EditorScreen.kt` (top level):

```kotlin
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
```

- [ ] **Step 2: Wire actions and shortcuts into EditorScreen**

Inside `EditorScreen`, after the `drafts` declaration add:

```kotlin
    val fileState = remember { FileState() }
    var fileName by remember { mutableStateOf<String?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }  // unsaved guard
    var exportOpen by remember { mutableStateOf(false) }

    fun refreshTitle() {
        fileName = fileState.file?.name
        dirty = fileState.dirty(state.display)
    }

    fun doSaveAs(): Boolean {
        val f = saveDialog(window, fileState.file?.name ?: "লেখা.txt") ?: return false
        state.commitForming(); syncFromState()
        f.writeText(state.committed)
        fileState.file = f; fileState.savedText = state.committed
        refreshTitle(); return true
    }

    fun doSave(): Boolean {
        val f = fileState.file ?: return doSaveAs()
        state.commitForming(); syncFromState()
        f.writeText(state.committed)
        fileState.savedText = state.committed
        refreshTitle(); return true
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
            state.setAll(f.readText())
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
            DocxWriter.write(state.committed, f)
            status = "Word ফাইল তৈরি ✓"
        }
    }

    fun doPrint() {
        state.commitForming(); syncFromState()
        scope.launch(Dispatchers.Default) { Printer.print(state.committed, fileName ?: "বাংলু লেখক") }
    }
```

Also change `refreshTitle()`-relevant places: call `refreshTitle()` at the end of the `onValueChange` lambda, and change the autosave `LaunchedEffect` to persist the real state:

```kotlin
    LaunchedEffect(fieldValue.text) {
        kotlinx.coroutines.delay(2000)
        drafts.saveDraft(Draft(state.display, state.cursor, state.formingRaw,
            fileState.file?.absolutePath, fileState.savedText.takeIf { fileState.file != null }))
    }
```

And extend the draft-restore `LaunchedEffect` to restore the file association:

```kotlin
        drafts.loadDraft()?.let { d ->
            state.setAll(d.text, d.cursor.coerceIn(0, d.text.length))
            d.filePath?.let { p -> File(p).takeIf(File::exists)?.let { f ->
                fileState.file = f; fileState.savedText = d.savedText ?: f.readText()
            } }
            syncFromState(); refreshTitle()
            if (d.savedText != d.text) restoredBanner = true
        }
```

- [ ] **Step 3: Shortcuts + top bar UI**

Wrap the whole `Column` in the `MaterialTheme` body with a key handler by adding this modifier to the root `Column`:

```kotlin
        Column(Modifier.fillMaxSize().background(Bg).onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || !(e.isMetaPressed || e.isCtrlPressed)) return@onPreviewKeyEvent false
            when {
                e.key == Key.S && e.isShiftPressed -> { doSaveAs(); true }
                e.key == Key.S -> { doSave(); true }
                e.key == Key.O -> { doOpen(); true }
                e.key == Key.N -> { doNew(); true }
                e.key == Key.P -> { doPrint(); true }
                e.key == Key.C && e.isShiftPressed -> { copyAll(); true }
                e.key == Key.Z && e.isShiftPressed -> { state.redo(); syncFromState(); refreshTitle(); true }
                e.key == Key.Z -> { state.undo(); syncFromState(); refreshTitle(); true }
                else -> false
            }
        }) {
```

Replace the `TopBar(status)` call and composable with:

```kotlin
            TopBar(
                status = status, fileName = fileName, dirty = dirty,
                onNew = ::doNew, onOpen = ::doOpen, onSave = { doSave() },
                exportOpen = exportOpen, setExportOpen = { exportOpen = it },
                onExportDocx = ::exportDocx, onExportTxt = { doSaveAs() },
                onPrint = ::doPrint, onCopyAll = ::copyAll,
            )
```

```kotlin
@Composable
private fun TopBar(
    status: String, fileName: String?, dirty: Boolean,
    onNew: () -> Unit, onOpen: () -> Unit, onSave: () -> Unit,
    exportOpen: Boolean, setExportOpen: (Boolean) -> Unit,
    onExportDocx: () -> Unit, onExportTxt: () -> Unit, onPrint: () -> Unit, onCopyAll: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("বাংলু লেখক", color = Sky, fontSize = 17.sp, fontFamily = BengaliFontFamily)
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
```

`status` must become writable from the new actions — it already is (`var status by remember`). Note `import androidx.compose.ui.input.key.isMetaPressed / isCtrlPressed / isShiftPressed` are in `androidx.compose.ui.input.key.*` (already imported).

- [ ] **Step 4: The unsaved-changes dialog**

Add inside the `MaterialTheme` body (after the root `Column`):

```kotlin
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
```

- [ ] **Step 5: Compile and run the full test suite**

Run: `./gradlew :desktop-app:compileKotlin :desktop-app:test 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL, all tests PASS.

- [ ] **Step 6: Commit**

```bash
git add desktop-app/src/main/kotlin/com/banglu/desktop/editor/EditorScreen.kt
git commit -m "feat(desktop): S50 — file ops (native dialogs, unsaved guard, recents), export menu, shortcuts"
```

---

### Task 12: Main.kt integration — the editor becomes the app

**Files:**
- Modify: `desktop-app/src/main/kotlin/com/banglu/desktop/Main.kt`

**Interfaces:**
- Consumes: `FrameWindowScope.EditorScreen()` (Tasks 10–11), `DraftStore`/`EditorPrefs` (window size persistence).
- Produces: the shipped app. The two-box `App()` composable and its now-unused imports are DELETED (File Discipline: modify, never leave dead `_old` code). `MiniConverter`, `Tray`, `Hotkey` registration stay byte-identical.

- [ ] **Step 1: Rewire the main window**

In `Main.kt`:

1. Delete the entire `App()` composable (lines 143–262) and the private color vals (lines 38–45) — the theme now lives in `editor/EditorTheme.kt`. Delete now-unused imports (`LazyRow`, `RoundedCornerShape`, `Toolkit`, `StringSelection`, the engine imports, `Dispatchers/withContext` — keep what `MiniConverter` still uses: check each import compiles). `MiniConverter` references `Bg/Card/FieldBg/CardBorder/Sky/SkySoft/Muted` — change those references to the `editor` package values via `import com.banglu.desktop.editor.*` and rename `Card` → `PageCard` in `MiniConverter`.
2. Replace the main `Window(...) { App() }` block with:

```kotlin
    val prefs = remember { DraftStore(java.io.File(System.getProperty("user.home"), ".banglu")).loadPrefs() }
    val winState = rememberWindowState(width = prefs.winW.dp, height = prefs.winH.dp)
    if (mainVisible) Window(
        onCloseRequest = { mainVisible = false },       // tray keeps us alive; draft autosaves
        title = "বাংলু লেখক",
        state = winState,
    ) {
        EditorScreen()
    }
```

with `import com.banglu.desktop.editor.EditorScreen` and `import com.banglu.desktop.editor.DraftStore`. Also update the tray item `Item("Banglu খুলুন")` → `Item("বাংলু লেখক খুলুন")`.

3. Window-size persistence — after the `Window` block add:

```kotlin
    LaunchedEffect(winState.size) {
        DraftStore(java.io.File(System.getProperty("user.home"), ".banglu"))
            .let { it.savePrefs(it.loadPrefs().copy(
                winW = winState.size.width.value.toInt(),
                winH = winState.size.height.value.toInt())) }
    }
```

- [ ] **Step 2: Compile + full tests**

Run: `./gradlew :desktop-app:compileKotlin :desktop-app:test 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL, all tests PASS.

- [ ] **Step 3: Live run from the dev tree**

Run: `./gradlew :desktop-app:run` (foreground; quit the app to return).
Verify by typing in the window: `kemon acho` forms live and space commits Bangla; double space makes ।; digits `1-6` pick from the popup; Escape then `3` inserts ৩; clicking কেমন opens the fix popup; ⌘S opens the native save dialog; এক্সপোর্ট ▾ → Word writes a .docx that opens; quit + relaunch restores the text.

- [ ] **Step 4: Commit**

```bash
git add desktop-app/src/main/kotlin/com/banglu/desktop/Main.kt
git commit -m "feat(desktop): S50 — বাংলু লেখক IS the app: two-box converter removed, editor wired, window persistence"
```

---

### Task 13: Full suite, package, manual gate, version bump

**Files:**
- Modify: `desktop-app/build.gradle.kts` (packageVersion)

- [ ] **Step 1: Full regression wall**

Run: `./gradlew :shared:jvmTest :shared:testDebugUnitTest :desktop-app:test 2>&1 | tail -10`
Expected: ALL GREEN (470+ shared tests + ~27 desktop tests). Any shared-test regression means the editor's engine usage changed shared behavior — stop and investigate; the editor must be purely additive.

- [ ] **Step 2: Bump the desktop version**

In `desktop-app/build.gradle.kts`: `packageVersion = "1.0.0"` → `packageVersion = "1.1.0"`.

- [ ] **Step 3: Package the DMG**

```bash
./gradlew :desktop-app:packageDmg 2>&1 | tail -5
ls -la desktop-app/build/compose/binaries/main/dmg/
```

Expected: BUILD SUCCESSFUL, `Banglu-1.1.0.dmg` present.

- [ ] **Step 4: Install fresh (the S49 lesson — never reinstall from a stale mounted volume)**

```bash
hdiutil detach /Volumes/Banglu -force 2>/dev/null; rm -rf /Applications/Banglu.app
ditto desktop-app/build/compose/binaries/main/app/Banglu.app /Applications/Banglu.app
xattr -dr com.apple.quarantine /Applications/Banglu.app
rm -rf desktop-app/build/compose/binaries/main/app/Banglu.app   # no second Banglu in Spotlight
open /Applications/Banglu.app
```

- [ ] **Step 5: MANUAL GATE (user) — the spec §8 acceptance walk**

Ask the user to: type a real letter (several sentences with conjunct-heavy words), watch words form live and commit on space; fix one word by clicking it; save as চিঠি.txt; quit from the tray; relaunch — text restored; এক্সপোর্ট → Word and open the .docx in Pages checking conjuncts; ⌘P → Save as PDF and check the PDF. Screenshot-verified before proceeding.

- [ ] **Step 6: Final commit + tag**

```bash
git add desktop-app/build.gradle.kts
git commit -m "release(desktop): S50 — বাংলু লেখক v1.1.0: full-page live Bangla editor"
git push origin main
```

(Tag only if the user wants a desktop tag series; Android `v*` tags are the existing convention — ask before tagging.)

---

## Self-Review Notes

- **Spec coverage:** §1 typing model → Tasks 2–4; §3 layout → Task 10; §4 engine incl. দাঁড়ি/digits/undo/threading → Tasks 2–5; §5 files/autosave/recovery → Tasks 7, 11, 12; §6 export incl. copy-all/print → Tasks 8, 9, 11; §7 AI seam → EngineFacade (Task 1) + committed-word trio (Task 6); §8 testing → Tasks 1–8 tests + Task 13 gate; §9 exclusions respected (no PDF lib, no rich text, no tabs).
- **Known simplifications (deliberate, spec-compatible):** recent-files list is persisted (Task 11) but the ⋯ menu rendering of it and the digit-mode toggle UI are folded into the export/top-bar menus only if time permits — the prefs plumbing exists; wire `banglaDigits` from `EditorPrefs` in Task 12 by passing `drafts.loadPrefs().banglaDigits` into `EditorState` construction if not already done.
- **Type consistency check:** `EditorState` members referenced in Tasks 10–11 (`display`, `cursor`, `formingRange`, `popupVisible`, `candidates`, `applyEdit`, `refine`, `commitForming`, `pickCandidate`, `dismissPopup`, `undo`, `redo`, `setAll`, `wordRangeAt`, `candidatesForCommitted`, `replaceCommitted`, `generation`, `committed`, `formingRaw`, `banglaDigits`) are all defined in Tasks 2–6. `DraftStore`/`Draft`/`EditorPrefs` names match Tasks 7/11/12. Theme values in Task 9 match usages in Tasks 10–11.
