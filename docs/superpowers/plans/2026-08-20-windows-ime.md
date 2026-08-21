# বাংলু টাইপার (Windows IME) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One Kotlin tray app that lets a Windows user type Bangla in any input field (MS Word, browsers, WhatsApp) via the shared Banglu engine — Avro-style hook + inject, full 143MB dictionary, offline.

**Architecture:** Single JVM process. A JNA low-level keyboard hook swallows letters when Bangla mode is on and enqueues them; a single worker thread runs a pure-Kotlin `Composer` (port of the macOS IME's) against the shared engine and injects committed Bangla via `SendInput`. Compose Desktop provides the tray and a caret-anchored preview window. `hook/` is the only package allowed to import JNA/Win32 — everything else is OS-free and JVM-testable on the real dictionary.

**Tech Stack:** Kotlin/JVM 17, Compose Desktop (tray/windows), JNA 5.14 + jna-platform (hook/inject/caret), sqlite-jdbc (via `:shared` jvmMain store), kotlinx-serialization (prefs/learned.json), jpackage MSI on windows-latest CI.

**Spec:** `docs/superpowers/specs/2026-08-20-windows-ime-design.md`

## Global Constraints

- jvmTarget/toolchain **17** (jpackage runtime is Temurin 17 — desktop-app landmine, class-file 65 crash).
- JNA pinned **5.14.0** (`net.java.dev.jna:jna:5.14.0`, `net.java.dev.jna:jna-platform:5.14.0`) — matches desktop-app's aarch64-safe pin.
- **No network dependency anywhere in the module** (spec §6, invariant 12). No HTTP client, no telemetry lib.
- Conversion behavior comes ONLY from `:shared` (invariant 9). This module never re-implements a rule.
- Only `com.banglu.winime.hook` may import `com.sun.jna.*` (spec §3 isolation law). Enforced by a verify task in Task 10.
- Learned-words file format: `{p,b,f,t}` rows in `%USERPROFILE%\.banglu\learned.json`, tmp + atomic `Files.move(REPLACE_EXISTING, ATOMIC_MOVE)` writes (invariant 10; source of truth `desktop-app/.../Storage.kt`).
- Learning gates: `isPlausibleDynamicMapping` never bypassed (invariant 11); learn only on explicit candidate picks; never learn the engine's own primary on plain commit (S26).
- The hook callback may not allocate, log, or call the engine. Classify + enqueue + return.
- All tests run on the REAL dictionary found by `JvmSqliteDictionaryLoader.findDictionarySqlite()` (repo-root `dictionary.sqlite`) — the same wall discipline as `:desktop-app:test`.
- This machine is a Mac: everything must COMPILE here (`./gradlew :windows-ime:build`), pure-JVM tests must PASS here; Win32 runtime behavior is verified only on the user's Windows laptops (Task 11 checklist). Never claim runtime behavior works without that gate.

---

### Task 1: Module scaffold + engine smoke test

**Files:**
- Modify: `settings.gradle.kts` (add `include(":windows-ime")` after `include(":desktop-app")`)
- Create: `windows-ime/build.gradle.kts`
- Create: `windows-ime/src/test/kotlin/com/banglu/winime/TestEngine.kt`
- Create: `windows-ime/src/test/kotlin/com/banglu/winime/EngineSmokeTest.kt`

**Interfaces:**
- Produces: `TestEngine.boot()` — idempotent full-engine boot for every later test class; `TestEngine.storageDir: File` — temp `.banglu` dir used by tests.

- [ ] **Step 1: Write `windows-ime/build.gradle.kts`** (mirror desktop-app's dependency style; read `desktop-app/build.gradle.kts` first):

```kotlin
plugins {
    kotlin("jvm")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    implementation(libs.kotlinx.serialization.json)
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(17) }
tasks.withType<Test> {
    useJUnitPlatform()
    maxHeapSize = "4g"   // full validator + bigrams, same as :shared jvmTest
}
```

- [ ] **Step 2: Add the module to `settings.gradle.kts`**: `include(":windows-ime")` on the line after `include(":desktop-app")`.

- [ ] **Step 3: Write the failing smoke test.** `TestEngine.kt` — copy the desktop boot sequence from `desktop-app/src/main/kotlin/com/banglu/desktop/editor/EditorScreen.kt:219-229` exactly (adapt suspend signatures as found there):

```kotlin
package com.banglu.winime

import com.banglu.engine.JvmSqliteDictionaryLoader
import com.banglu.engine.JvmSqlitePhoneticIndexStore
import com.banglu.engine.SmartEngineAdapter
import kotlinx.coroutines.runBlocking
import java.io.File

/** One shared full-engine boot for the whole test suite (real dictionary). */
object TestEngine {
    val storageDir: File = createTempDir(prefix = "banglu-win-test")
    private var booted = false

    @Synchronized
    fun boot() {
        if (booted) return
        runBlocking {
            SmartEngineAdapter.initializeSync()
            val db = JvmSqliteDictionaryLoader.findDictionarySqlite()
            val store = JvmSqlitePhoneticIndexStore(db)
            check(store.isAvailable) { "dictionary.sqlite rejected: ${db.absolutePath}" }
            SmartEngineAdapter.setPhoneticIndex(store)
            SmartEngineAdapter.initialize(WinStorage(storageDir), JvmSqliteDictionaryLoader(db))
        }
        booted = true
    }
}
```

For THIS task only, `WinStorage(storageDir)` does not exist yet — substitute the desktop pattern with an inline `object : PlatformStorage {}`-style stub is NOT allowed (no stubs in the boot we keep); instead, temporarily pass the in-memory storage used by `:shared` commonTest if one is exported, or write `WinStorage` in Task 2 first and keep this test `@Ignore`d until then. Preferred order if friction appears: implement Task 2's `WinStorage` before running this test — the two tasks may land in one commit.

`EngineSmokeTest.kt`:

```kotlin
package com.banglu.winime

import com.banglu.engine.SmartEngineAdapter
import kotlin.test.Test
import kotlin.test.assertEquals

class EngineSmokeTest {
    @Test
    fun fullEngineConvertsCoreWords() {
        TestEngine.boot()
        assertEquals("আমি", SmartEngineAdapter.convertWord("ami").bengali)
        assertEquals("কেমন", SmartEngineAdapter.convertWord("kmon").bengali)
        assertEquals("ইচ্ছা", SmartEngineAdapter.convertWord("issa").bengali)
    }
}
```

- [ ] **Step 4: Run and iterate to green**: `./gradlew :windows-ime:test --tests '*EngineSmokeTest*'` — expect FAIL first (module wiring/signature mismatches), fix against the real signatures in `EditorScreen.kt`, then PASS.

- [ ] **Step 5: Commit** `feat(windows): scaffold :windows-ime module + full-engine smoke test`.

---

### Task 2: WinStorage — the learned.json host implementation

**Files:**
- Create: `windows-ime/src/main/kotlin/com/banglu/winime/WinStorage.kt`
- Test: `windows-ime/src/test/kotlin/com/banglu/winime/WinStorageTest.kt`

**Interfaces:**
- Produces: `class WinStorage(baseDir: File = File(System.getProperty("user.home"), ".banglu")) : PlatformStorage` — constructor-injectable dir (tests use temp dirs; production default is `%USERPROFILE%\.banglu`).

- [ ] **Step 1:** Read `desktop-app/src/main/kotlin/com/banglu/desktop/Storage.kt` end to end. `WinStorage` is a PORT of `FileStorage` with two changes only: (a) it is a `class` taking `baseDir` (testability), (b) package `com.banglu.winime`. Keep IDENTICAL: the `Row(p,b,f,t)` serialization, tmp + `Files.move(REPLACE_EXISTING, ATOMIC_MOVE)` with the non-atomic fallback, the S78 `removeLearnedWord` semantics (custom formulas `f >= 120` survive), and every other `PlatformStorage` member Storage.kt implements (copy each). Also implement `clearAllLearningData()` if `PlatformStorage` declares it (S128) — mirror the default/desktop behavior.

- [ ] **Step 2: Write failing tests:**

```kotlin
package com.banglu.winime

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class WinStorageTest {
    private fun fresh() = WinStorage(createTempDir(prefix = "ws"))

    @Test fun savesAndReloadsRows() = runBlocking {
        val s = fresh()
        s.saveLearnedWord("jbo", "যাবো", 1)
        val rows = s.getLearnedWords()
        assertEquals(1, rows.size)
        assertEquals("jbo", rows[0].phonetic)
        assertEquals("যাবো", rows[0].bengali)
    }

    @Test fun duplicateSaveBumpsFrequency() = runBlocking {
        val s = fresh()
        s.saveLearnedWord("jbo", "যাবো", 1)
        s.saveLearnedWord("jbo", "যাবো", 1)
        assertEquals(2, s.getLearnedWords().single().frequency)
    }

    @Test fun corruptFileDegradesToEmptyNotCrash() = runBlocking {
        val dir = createTempDir(prefix = "ws")
        java.io.File(dir, "learned.json").writeText("{not json")
        assertTrue(WinStorage(dir).getLearnedWords().isEmpty())
    }
}
```

(Fixture pairs must be plausible — `jbo→যাবো` class, never junk keys: invariant 11 note in CLAUDE.md §3.7.)

- [ ] **Step 3:** Run `./gradlew :windows-ime:test --tests '*WinStorageTest*'` → FAIL → implement → PASS. Then un-ignore/re-run Task 1's smoke test with `WinStorage(TestEngine.storageDir)`.

- [ ] **Step 4: Commit** `feat(windows): WinStorage — learned.json host (desktop FileStorage port, dir-injectable)`.

---

### Task 3: Composer — the typing state machine (macOS port)

**Files:**
- Create: `windows-ime/src/main/kotlin/com/banglu/winime/composer/Composer.kt`
- Test: `windows-ime/src/test/kotlin/com/banglu/winime/composer/ComposerTest.kt`

**Interfaces:**
- Produces (used verbatim by Tasks 4, 7):

```kotlin
package com.banglu.winime.composer

sealed interface ComposerKey {
    data class Letter(val c: Char) : ComposerKey
    data object Space : ComposerKey
    data object Backspace : ComposerKey
    data class Digit(val c: Char) : ComposerKey
    data object Escape : ComposerKey
    data object Enter : ComposerKey
    data object Tab : ComposerKey
    data class Punctuation(val p: String) : ComposerKey
}

sealed interface ComposerAction {
    data class Preview(val bangla: String, val raw: String) : ComposerAction // ""+"" = hide
    data class Commit(val text: String) : ComposerAction                    // inject as unicode
    data class ForwardKey(val key: ComposerKey) : ComposerAction            // re-inject original (Enter/Tab/Backspace)
    data class Candidates(val list: List<String>) : ComposerAction
}

interface ComposerEngine {
    fun convert(raw: String): String
    fun suggest(raw: String, limit: Int = 6): List<String>
}

class Composer(private val engine: ComposerEngine, private val banglaDigits: Boolean = true) {
    val forming: Boolean            // true while a word is being typed
    val pendingSpace: Boolean       // দাঁড়ি model state — Task 4's swallow mirror reads forming||pendingSpace
    var onPick: ((raw: String, bangla: String, wasPrimary: Boolean) -> Unit)?
    fun handle(key: ComposerKey): List<ComposerAction>
    fun pick(index: Int): List<ComposerAction>
    fun focusLost(): List<ComposerAction>
}
```

- [ ] **Step 1:** Read `macos-ime/Sources/BangluCore/Composer.swift` (187 lines) — this task is a line-faithful Kotlin port with THREE deliberate differences, each because a hook app (unlike IMK) sends nothing to the target until commit:
  1. There is no marked text: `setMarked` becomes `Preview(bangla, raw)` consumed only by our own preview window.
  2. `passThrough` becomes `ForwardKey(key)` — the hook swallowed the original event, so "let it through" means Task 4 re-injects it synthetically (ordering law: commits always land before the forwarded key because both go through the same single worker).
  3. `escape` cancels to RAW roman: emits `Commit(raw)` exactly like the Swift version (line 100-104) — the user's typed letters appear as typed.
  Everything else ports 1:1: pending-space দাঁড়ি (space→hold; space,space→`"। "`; letter after pending→`" "`), `dariJustCommitted` alternation, tight punctuation set `{",", "।", "?", "!"}` checked on the MAPPED char (`"." → "।"` first), digits ০-৯ via `0x09E6 + n`, digit-as-candidate-pick when forming (1..6), backspace-on-pendingSpace = `Commit(" ") + ForwardKey(Backspace)`, WYSIWYG `commitForming()` committing the last previewed `formingBangla` without re-converting.

- [ ] **Step 2: Write the failing pin tests** (real engine via `TestEngine.boot()`; `ComposerEngine` adapter over `SmartEngineAdapter.convertWord/getSuggestions`). Minimum pins — each is one test method:

```kotlin
// helper
private fun commits(actions: List<ComposerAction>) =
    actions.filterIsInstance<ComposerAction.Commit>().joinToString("") { it.text }
private fun type(c: Composer, s: String) = s.flatMap { c.handle(ComposerKey.Letter(it)) }

@Test fun spaceCommitsExactlyThePreview() {           // WYSIWYG law
    val c = composer(); type(c, "ami")
    val lastPreview = /* capture last Preview.bangla from type() actions */
    assertEquals(lastPreview, commits(c.handle(ComposerKey.Space)))
    assertEquals("আমি", lastPreview)
}
@Test fun doubleSpaceMakesDari() {                    // ami␣␣ → "আমি। "
    val c = composer(); type(c, "ami"); c.handle(ComposerKey.Space)
    assertEquals("। ", commits(c.handle(ComposerKey.Space)))
}
@Test fun tripleSpaceAlternates() { /* third space after dari → " " (dariJustCommitted flip) */ }
@Test fun letterAfterPendingSpaceReleasesPlainSpace() { /* ami␣k → Commit(" ") then preview for k */ }
@Test fun tightPunctuationSwallowsPendingSpace() {    // ami␣, → "আমি," no space before comma
    val c = composer(); type(c, "ami"); c.handle(ComposerKey.Space)
    assertEquals(",", commits(c.handle(ComposerKey.Punctuation(","))))
}
@Test fun periodMapsToDariAndIsTight() { /* ami␣. → "।" only */ }
@Test fun digitsCommitBengali() { /* not forming: Digit('5') → Commit("৫") */ }
@Test fun digitPicksCandidateWhileForming() { /* type "kmn", Digit('2') → commits candidates[1]; onPick fired */ }
@Test fun backspaceEditsFormingBuffer() { /* "amii"+Backspace previews আমি again */ }
@Test fun backspaceWithPendingSpaceMaterializesIt() { /* → [Commit(" "), ForwardKey(Backspace)] */ }
@Test fun enterCommitsFormingThenForwards() { /* type "ami", Enter → Commit("আমি"), ForwardKey(Enter); pendingSpace=false */ }
@Test fun escapeCancelsToRaw() { /* type "ami", Escape → Commit("ami") */ }
@Test fun focusLostFlushesForming() { /* forming committed, pendingSpace dropped silently */ }
@Test fun pickTeachesOnlyNonPrimary() { /* pick(0)==primary → wasPrimary=true; caller must NOT learn (S26) */ }
```

Write every body out fully in the real file (the sketches above name the behavior; the test file contains complete assertions).

- [ ] **Step 3:** `./gradlew :windows-ime:test --tests '*ComposerTest*'` → FAIL (class missing) → port the Swift file → PASS. Any pin that disagrees with the Swift behavior is a port bug, not a test to edit (invariant 7).

- [ ] **Step 4: Commit** `feat(windows): Composer — pending-space দাঁড়ি typing state machine (macOS S51 port) + pins`.

---

### Task 4: Controller — mode machine, swallow rules, worker queue

**Files:**
- Create: `windows-ime/src/main/kotlin/com/banglu/winime/KeyPorts.kt` (the OS-free ports)
- Create: `windows-ime/src/main/kotlin/com/banglu/winime/Controller.kt`
- Test: `windows-ime/src/test/kotlin/com/banglu/winime/ControllerTest.kt`

**Interfaces:**
- Produces (Task 6 implements `KeySource`/`TextInjector` with JNA; Task 7 renders from `ControllerListener`):

```kotlin
package com.banglu.winime

/** Everything the hook layer reports — already classified, no VK codes here. */
sealed interface RawKey {
    data class Letter(val c: Char) : RawKey          // a-z (lowercased by hook)
    data class Digit(val c: Char) : RawKey           // 0-9
    data class Punct(val p: String) : RawKey         // . , ? !
    data object Space : RawKey
    data object Backspace : RawKey
    data object Enter : RawKey
    data object Tab : RawKey
    data object Escape : RawKey
    data object ToggleHotkey : RawKey                // Ctrl+Space, pre-detected by hook
    data object FocusChanged : RawKey                // foreground window changed
    data class Unmanaged(val vk: Int) : RawKey       // everything else (hook already passed it through)
}

enum class Mode { OFF, BANGLA, ENGLISH }

interface KeySource {                                 // Task 6: JNA hook
    fun start(sink: HookSink)
    fun stop()
}
/** Called ON the hook thread. Must return the swallow decision immediately. */
interface HookSink {
    /** true = swallow (we own this key); false = pass to the app untouched. */
    fun onKey(key: RawKey, foregroundExe: String): Boolean
}
interface TextInjector {                              // Task 6: SendInput
    fun injectText(text: String)                      // KEYEVENTF_UNICODE, tagged
    fun injectKey(key: RawKey)                        // synthetic VK for ForwardKey
}
interface ControllerListener {                        // Task 7: UI
    fun onPreview(bangla: String, raw: String, candidates: List<String>)  // ""/"" = hide
    fun onModeChanged(mode: Mode)
}

class Controller(
    engine: com.banglu.winime.composer.ComposerEngine,
    private val injector: TextInjector,
    private val compat: AppCompat,                    // Task 5
    private val listener: ControllerListener,
) : HookSink {
    @Volatile var mode: Mode = Mode.BANGLA; private set
    fun setModeExternal(m: Mode)                      // tray menu calls this
    fun pickCandidate(index: Int)                     // preview-window click
    fun shutdown()                                    // drain + stop worker
    override fun onKey(key: RawKey, foregroundExe: String): Boolean
}
```

- [ ] **Step 1: Write the failing tests first** — this is the most test-valuable unit in the app. Fakes: `FakeInjector` records `injectText`/`injectKey` calls in order; drive `onKey` directly (no hook). The controller runs a single worker thread; tests call an internal `awaitIdle()` (package-private, CountDownLatch drain) after feeding keys. Pins:

```kotlin
@Test fun banglaTypingEndToEnd() {
    feed("ami", Space)                        // letters swallowed (onKey returned true)
    awaitIdle()
    assertEquals(listOf("আমি"), fake.texts)   // space held (pending), nothing else injected
    feed(Space); awaitIdle()
    assertEquals(listOf("আমি", "। "), fake.texts)
}
@Test fun swallowDecisions() {
    assertTrue(onKey(Letter('a')))            // BANGLA mode: letters swallowed
    assertTrue(onKey(Space))                  // space always swallowed in BANGLA
    assertFalse(onKey(Unmanaged(0x11)))       // ctrl etc: never swallowed
    controller.setModeExternal(Mode.ENGLISH)
    assertFalse(onKey(Letter('a')))           // ENGLISH: pure passthrough
}
@Test fun enterSwallowedOnlyWhileComposerActive() {
    assertFalse(onKey(Enter))                 // idle: native Enter passes
    feed("ami")                                // sets composerActive mirror IN onKey
    assertTrue(onKey(Enter))                  // forming: swallow, worker forwards
    awaitIdle()
    assertEquals(listOf("আমি"), fake.texts)
    assertEquals(listOf(RawKey.Enter), fake.keys)   // ordering: commit BEFORE forwarded Enter
}
@Test fun fastLetterThenEnterNeverRaces() {
    // The optimistic mirror: onKey(Letter) sets composerActive=true ON THE HOOK
    // THREAD before enqueueing, so an Enter arriving before the worker has
    // processed the letter is still swallowed and ordered behind it.
    assertTrue(onKey(Letter('a')))
    assertTrue(onKey(Enter))
    awaitIdle()
    assertTrue(fake.texts.isNotEmpty())       // 'a' converted+committed first
}
@Test fun toggleHotkeyCyclesBanglaEnglish() { /* BANGLA→ENGLISH→BANGLA; listener.onModeChanged fired; forming flushed on leave */ }
@Test fun passthroughAppIsNeverTouched() { /* compat lists "keepass.exe" → onKey(Letter) returns false */ }
@Test fun focusChangeFlushesForming() { /* feed "ami", FocusChanged → "আমি" injected (composer.focusLost) */ }
@Test fun candidatePickInjectsAndLearns() { /* pickCandidate(n) on non-primary → injectText + engine.selected(explicit=true) recorded via fake ComposerEngine */ }
@Test fun offModeUnregistersEverything() { /* setModeExternal(OFF): all keys return false */ }
```

- [ ] **Step 2: Implement `Controller`.** Rules locked by the tests above:
  - `onKey` (hook thread): no allocation-heavy work — pattern-match `RawKey`, read `mode` + `compat.isPassthrough(exe)` + the volatile `composerActive` mirror, set the mirror optimistically for swallowed Letters, `queue.offer(key)`, return the decision. Managed set in BANGLA mode: Letter/Digit/Punct/Space always swallowed; Enter/Tab/Backspace/Escape swallowed only when `composerActive`; `ToggleHotkey` always swallowed; everything else false.
  - Worker (single `Thread(daemon)` draining a `LinkedBlockingQueue`): translate `RawKey`→`ComposerKey`, run `composer.handle`, dispatch actions — `Commit`→`injector.injectText`, `ForwardKey`→`injector.injectKey`, `Preview`/`Candidates`→coalesce into one `listener.onPreview` per handled key. After each handle: `composerActive = composer.forming || composer.pendingSpace`.
  - Mode transitions flush: leaving BANGLA runs `composer.focusLost()` first.
- [ ] **Step 3:** `./gradlew :windows-ime:test --tests '*ControllerTest*'` → green.
- [ ] **Step 4: Commit** `feat(windows): Controller — swallow rules, optimistic composer mirror, single-worker ordering`.

---

### Task 5: AppCompat — per-exe passthrough table

**Files:**
- Create: `windows-ime/src/main/kotlin/com/banglu/winime/AppCompat.kt`
- Test: `windows-ime/src/test/kotlin/com/banglu/winime/AppCompatTest.kt`

**Interfaces:**
- Produces: `class AppCompat(baseDir: File)` with `fun isPassthrough(exeName: String): Boolean`, `fun add(exeName: String)`, `fun remove(exeName: String)`, `val entries: List<String>`.

- [ ] **Step 1: Failing tests:** built-in defaults are passthrough out of the box (`keepass.exe`, `keepassxc.exe`, `1password.exe`, `bitwarden.exe` — case-insensitive match on the exe basename); user additions persist to `<baseDir>/winime-appcompat.json` and survive a new instance; `remove` of a built-in persists as an override; corrupt file → defaults only, no crash.
- [ ] **Step 2: Implement** — kotlinx-serialization list of `{exe, passthrough}` overrides layered over the built-in set; same tmp+move write as WinStorage.
- [ ] **Step 3:** test → green. **Commit** `feat(windows): AppCompat passthrough table with password-manager defaults`.

---

### Task 6: hook/ — the ONLY Win32 package (JNA)

**Files:**
- Create: `windows-ime/src/main/kotlin/com/banglu/winime/hook/LowLevelHook.kt`
- Create: `windows-ime/src/main/kotlin/com/banglu/winime/hook/SendInputInjector.kt`
- Create: `windows-ime/src/main/kotlin/com/banglu/winime/hook/CaretLocator.kt`
- Create: `windows-ime/src/main/kotlin/com/banglu/winime/hook/ForegroundApp.kt`

**Interfaces:**
- Consumes: `KeySource`, `HookSink`, `TextInjector`, `RawKey` from Task 4.
- Produces: `class LowLevelHook : KeySource`; `class SendInputInjector : TextInjector`; `object CaretLocator { fun caretScreenPos(): Pair<Int, Int>? }` (null = caller falls back to cursor); `object ForegroundApp { fun exeName(): String }`.

There is no automated runtime test for this task (Mac dev machine; CI runners have no interactive desktop we trust). The gates are: compiles on Mac, zero imports of `com.sun.jna` outside this package (Task 10 verify task), and the Task 11 laptop checklist.

- [ ] **Step 1: `LowLevelHook`.** Dedicated thread: `SetWindowsHookEx(WH_KEYBOARD_LL, proc, moduleHandle, 0)` + `GetMessage` pump (jna-platform `User32.INSTANCE`, `WinUser.LowLevelKeyboardProc`, `WinUser.KBDLLHOOKSTRUCT`). In the proc:
  - Skip self-injected events: `(kb.flags & LLKHF_INJECTED) != 0` or `kb.dwExtraInfo == BANGLU_MAGIC` → `CallNextHookEx` immediately (`const val BANGLU_MAGIC = 0xBA6C1L` — shared with `SendInputInjector`).
  - Only `WM_KEYDOWN`/`WM_SYSKEYDOWN` are classified; key-ups pass through (except swallowed keys' matching key-ups, which are also swallowed — track the last swallow decision per VK in a 256-entry boolean array, no allocation).
  - Modifier guard: if Ctrl, Alt, or Win is down (`GetAsyncKeyState`), pass through — EXCEPT `Ctrl+Space` → `RawKey.ToggleHotkey`.
  - VK classification table (no allocation: pre-built array of `RawKey?` indexed by VK): `0x41..0x5A`→`Letter(lowercase)`, `0x30..0x39`/numpad→`Digit`, `VK_SPACE`, `VK_BACK`, `VK_RETURN`, `VK_TAB`, `VK_ESCAPE`, `VK_OEM_PERIOD`→`Punct(".")`, `VK_OEM_COMMA`→`Punct(",")`, shift+`VK_OEM_2`→`Punct("?")`, shift+`0x31`→`Punct("!")`. Shifted letters map to the same lowercase `Letter` (engine contract: lowercase in).
  - Decision: `sink.onKey(rawKey, ForegroundApp.exeName())` → true ⇒ return `LRESULT(1)`; false ⇒ `CallNextHookEx`. (`ForegroundApp` caches by HWND so the common case is a field read.)
  - Watchdog: a 5s daemon timer checks the hook handle validity by re-registering if `CallNextHookEx` chain broke (track: if no event seen for 60s AND a probe `GetAsyncKeyState` shows activity, unhook+rehook); expose `var onRearm: (() -> Unit)?` so the tray can show a warning.
  - Foreground watch: `SetWinEventHook(EVENT_SYSTEM_FOREGROUND)` → emit `RawKey.FocusChanged` to the sink (swallow decision ignored).
- [ ] **Step 2: `SendInputInjector`.** `injectText`: for each UTF-16 code unit build two `WinUser.INPUT` (KEYBDINPUT `KEYEVENTF_UNICODE`, then `|KEYEVENTF_KEYUP`), `dwExtraInfo = BANGLU_MAGIC`, one `SendInput` call per string (array batch). `injectKey`: map `RawKey.Enter/Tab/Backspace` to `VK_RETURN/VK_TAB/VK_BACK` down+up pairs, same magic tag.
- [ ] **Step 3: `CaretLocator`.** `GetGUIThreadInfo(GetWindowThreadProcessId(GetForegroundWindow()))` → if `hwndCaret != null`, `ClientToScreen(rcCaret.left, rcCaret.bottom)`; else null. `ForegroundApp.exeName()`: `GetForegroundWindow` → `GetWindowThreadProcessId` → `Kernel32.OpenProcess(QUERY_LIMITED_INFORMATION)` → `QueryFullProcessImageName` → basename lowercase, cached per HWND.
- [ ] **Step 4:** `./gradlew :windows-ime:build` compiles clean on the Mac. **Commit** `feat(windows): JNA hook layer — WH_KEYBOARD_LL, SendInput unicode, caret + foreground helpers`.

---

### Task 7: Tray + preview window + boot (Main.kt)

**Files:**
- Create: `windows-ime/src/main/kotlin/com/banglu/winime/Main.kt`
- Create: `windows-ime/src/main/kotlin/com/banglu/winime/ui/PreviewWindow.kt`
- Create: `windows-ime/src/main/resources/tray.png` (copy `desktop-app/src/main/resources/tray.png`)

**Interfaces:**
- Consumes: `Controller`, `Mode`, `ControllerListener`, `CaretLocator`, `LowLevelHook`, `SendInputInjector`, `AppCompat`, `WinStorage`, `WinPrefs` (Task 8 — at this task's commit, inline a two-field placeholder `data class WinPrefs(val banglaDigits: Boolean = true, val startOnLogin: Boolean = true)` in Main.kt and move it in Task 8).

- [ ] **Step 1: `Main.kt`** — follow `desktop-app/src/main/kotlin/com/banglu/desktop/Main.kt:39-86` structurally (`application { Tray(...) }`):
  - Boot: `LaunchedEffect` runs the EXACT desktop boot fold from `EditorScreen.kt:219-240` (initializeSync → find db → `JvmSqlitePhoneticIndexStore` → check available → `setPhoneticIndex` → `initialize(WinStorage(), JvmSqliteDictionaryLoader(db))`) on `Dispatchers.Default`; state `bootStatus: লোড হচ্ছে… / পূর্ণ অভিধান ✓ / অভিধান লোড হয়নি`. Until success the Controller stays in a `loading` flag where `onKey` always returns false (letters pass through untouched — spec §4.8; wire as `controller.engineReady = true` on success).
  - Tray icon + menu: mode radio (বাংলা / English / বন্ধ) driving `controller.setModeExternal`; status line (bootStatus, disabled item); `ওপেন সোর্স লাইসেন্স` item (same resources-dir lookup as desktop Main.kt:71-79); hotkey hint line `"বাংলা/English: Ctrl+Space"`; quit that calls `controller.shutdown(); hook.stop(); exitApplication()`.
  - Wire: `LowLevelHook().start(controller)` after boot success; `SetWinEventHook` focus events flow as `RawKey.FocusChanged`.
- [ ] **Step 2: `PreviewWindow`** — a Compose `Window(undecorated = true, alwaysOnTop = true, transparent = true, focusable = false, resizable = false)` whose visibility is driven by `ControllerListener.onPreview` (hop to the UI via `java.awt.EventQueue.invokeLater`, same pattern as desktop Main.kt:48). Content: the forming Bangla in `NotoSansBengali` (reuse the bundled font approach from `desktop-app/.../EditorTheme` — copy the font resource), the raw roman small underneath, then up to 5 candidate chips; chip click → `controller.pickCandidate(i)`. Position: on every show, `CaretLocator.caretScreenPos() ?: MouseInfo.getPointerInfo().location` offset (+0, +24), clamped to screen bounds via `window.setLocation`. Also call `window.setFocusableWindowState(false)` in a `LaunchedEffect` so it never steals focus from Word.
- [ ] **Step 3:** `./gradlew :windows-ime:build` green on Mac; `./gradlew :windows-ime:test` still green. **Commit** `feat(windows): tray app + caret-anchored preview window + engine boot states`.

---

### Task 8: Prefs + start-on-login

**Files:**
- Create: `windows-ime/src/main/kotlin/com/banglu/winime/WinPrefs.kt`
- Modify: `windows-ime/src/main/kotlin/com/banglu/winime/Main.kt` (move placeholder; add settings menu items)
- Test: `windows-ime/src/test/kotlin/com/banglu/winime/WinPrefsTest.kt`

**Interfaces:**
- Produces: `class WinPrefsStore(baseDir: File)` with `fun load(): WinPrefs`, `fun save(p: WinPrefs)`; `data class WinPrefs(val banglaDigits: Boolean = true, val startOnLogin: Boolean = true, val mode: String = "BANGLA")`; `object StartupRegistry { fun set(enabled: Boolean, exePath: String) }`.

- [ ] **Step 1: Failing tests** for `WinPrefsStore`: defaults on missing file; round-trip; corrupt file → defaults (same shape as WinStorageTest). No test for `StartupRegistry` (Windows-only).
- [ ] **Step 2: Implement.** Prefs: `<baseDir>/winime-prefs.json`, tmp+move writes. `StartupRegistry`: `ProcessBuilder("reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "BangluTyper", "/t", "REG_SZ", "/d", exePath, "/f")` and `reg delete ... /f` — guarded by `os.name.contains("windows", ignoreCase = true)`; on other OSes it is a no-op. Tray gains: `CheckboxItem("বাংলা সংখ্যা (০-৯)")` → prefs + composer flag (composer is recreated on change, forming flushed first); `CheckboxItem("লগইনে চালু হবে")` → prefs + `StartupRegistry`. Mode persists across restarts via `prefs.mode`.
- [ ] **Step 3:** tests green; build green. **Commit** `feat(windows): prefs store + start-on-login registry toggle`.

---

### Task 9: Packaging — jpackage MSI + dictionary version gate

**Files:**
- Modify: `windows-ime/build.gradle.kts`
- Create: `windows-ime/resources/.gitkeep` (resources dir is gitignored content-wise like desktop-app's; add the same pattern to `.gitignore`: `windows-ime/resources/common/dictionary.sqlite`)

- [ ] **Step 1:** Add the `compose.desktop { application { } }` block — copy `desktop-app/build.gradle.kts:35-74` and adapt: `mainClass = "com.banglu.winime.MainKt"`, `targetFormats(TargetFormat.Msi)` only, `packageName = "BangluTyper"`, `packageVersion = "1.0.0"`, `description = "Type Bangla anywhere on Windows"`, `vendor = "Banglu"`, modules `java.sql`, `java.instrument`, `java.management`, `jdk.unsupported`, `appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))`, windows icon `icons/banglu.ico` (copy from `desktop-app/icons/banglu.ico`), `menu = true; shortcut = true`. Keep the `javaHome` fallback chain exactly as desktop-app's (BANGLU_JDK → dev path takeIf exists → java.home).
- [ ] **Step 2:** Add `verifyPackagedDictionary` — copy `desktop-app/build.gradle.kts:87-131` verbatim (it is self-contained: regex over `DictionaryVersion.kt`, URLClassLoader over sqlite-jdbc+slf4j, LICENSES.md copy), including the `tasks.matching { package*/createDistributable* }.configureEach { dependsOn(...) }` hookup and the top-of-file imports (`java.io.File as JFile`, `java.net.URLClassLoader`, `java.util.Properties` — the KTS `java` extension shadowing trap).
- [ ] **Step 3:** On the Mac: `./gradlew :windows-ime:build` green (packaging tasks are NOT run here — MSI is CI-only). **Commit** `feat(windows): jpackage MSI config + packaged-dictionary version gate`.

---

### Task 10: CI — release workflow + walls + JNA isolation check

**Files:**
- Create: `.github/workflows/windows-ime-release.yml`
- Modify: `.github/workflows/ci.yml` (add `:windows-ime:test` to the wall list at line 66-75)
- Modify: `windows-ime/build.gradle.kts` (add `verifyHookIsolation` task)

- [ ] **Step 1: `verifyHookIsolation`** gradle task (runs on `check`): walk `windows-ime/src/main/kotlin`, fail if any file OUTSIDE `com/banglu/winime/hook/` contains `import com.sun.jna` — the spec §3 isolation law as a build gate, same spirit as `verifyImePrivacyBoundary`:

```kotlin
val verifyHookIsolation by tasks.registering {
    val srcRoot = project.layout.projectDirectory.dir("src/main/kotlin").asFile
    inputs.dir(srcRoot)
    doLast {
        val offenders = srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.path.contains("/hook/") }
            .filter { it.readText().contains("import com.sun.jna") }
            .map { it.relativeTo(srcRoot).path }.toList()
        require(offenders.isEmpty()) {
            "JNA imports outside hook/ (spec isolation law): $offenders"
        }
    }
}
tasks.named("check") { dependsOn(verifyHookIsolation) }
```

- [ ] **Step 2: `windows-ime-release.yml`** — model on `.github/workflows/ci.yml` + `desktop-release.yml`: trigger `push: tags: ['windows-v*']`; runner `windows-latest`; steps: checkout, setup-java temurin 17, `gh release download dictionary --pattern dictionary.sqlite` (S128 pattern, `GH_TOKEN: ${{ github.token }}`), version check against `DictionaryVersion.REQUIRED` (the ci.yml grep, but use PowerShell-safe `Select-String` or run the bash step with `shell: bash` — prefer `shell: bash`, available on windows runners), copy to `windows-ime/resources/common/dictionary.sqlite`, `./gradlew :windows-ime:test :windows-ime:packageMsi`, upload the MSI from `windows-ime/build/compose/binaries/main/msi/` as a workflow artifact AND attach to a GitHub release for the tag (`gh release create "$TAG" --title "বাংলু টাইপার $TAG" <msi>` with `shell: bash`).
- [ ] **Step 3:** Add `:windows-ime:test` and `:windows-ime:verifyHookIsolation` to ci.yml's gradle wall invocation (after `:desktop-app:verifyPackagedDictionary`).
- [ ] **Step 4:** Local gates: `./gradlew :windows-ime:check` green (isolation task passes), full `./gradlew :shared:jvmTest :windows-ime:test` green. **Commit** `ci(windows): windows-v* MSI release workflow + windows-ime walls + hook isolation gate`.

---

### Task 11: Laptop test checklist + README + first tagged build

**Files:**
- Create: `windows-ime/README.md`
- Create: `docs/windows-ime-laptop-checklist.md`

- [ ] **Step 1: `docs/windows-ime-laptop-checklist.md`** — the manual gate the user runs on each Windows laptop, one row per check with a pass/fail column: install MSI (document the SmartScreen "More info → Run anyway" path); tray icon appears with লোড হচ্ছে… → পূর্ণ অভিধান ✓; Ctrl+Space toggles বাংলা/English (tray icon reflects it); type `ami ami` in **Notepad** → `আমি আমি। ` behavior per the দাঁড়ি pins; same in **MS Word**, **Excel**, **Chrome** (Gmail compose + Facebook comment), **WhatsApp Desktop**, **File-Explorer rename box**; preview window follows the caret in Notepad/Word, falls back near the cursor in Chrome; candidate click and Ctrl-digit pick work and the pick is remembered next session (learned.json); backspace mid-word edits the preview; Escape cancels to roman; English mode passes everything including Ctrl+C/Ctrl+V; password manager (if installed) is untouched; **30-minute mixed typing session** — hook never dies (if the tray shows the re-arm warning, note it); Task Manager idle CPU ~0%, RAM noted; reboot → start-on-login works, mode restored.
- [ ] **Step 2: `windows-ime/README.md`** — module map (five packages + isolation law), build commands (`:windows-ime:test`, tag `windows-v*` → CI MSI), the elevated-apps limitation, and a pointer to the spec + this plan.
- [ ] **Step 3:** Commit `docs(windows): laptop gate checklist + module README`, push, then tag the first CI build: `git tag windows-v1.0.0 && git push origin windows-v1.0.0`. Watch the workflow; fix CI-only failures (windows path/shell traps) as fixup commits, re-tag `windows-v1.0.1` if needed. The MSI artifact link goes to the user for the laptop gate. **The feature is NOT done when CI is green — it is done when the user's checklist passes on at least one laptop** (Global Constraints, last bullet).

---

## Self-Review Notes

- **Spec coverage:** §3 module layout → Tasks 1-7 (SettingsWindow.kt from the spec is folded into tray CheckboxItems in Task 8 — v1 settings are two toggles + passthrough list; a dedicated window is YAGNI until the list needs UI, tracked in README); §4 pipeline → Tasks 3-4; §5 UI → Task 7 (টিউটোরিয়াল tray item links to the web guide — included in Task 7's menu list via the hotkey-hint/status pattern); §6 storage/privacy → Tasks 2, 5, 10 (isolation gate); §7 packaging/CI → Tasks 9-10; §8 testing → every task + Task 11; §9 risks → watchdog (Task 6), passthrough (Task 5), caret fallback (Task 7), escape hatch (interfaces in Task 4).
- **Type consistency:** `RawKey`/`HookSink`/`TextInjector`/`ControllerListener` defined once in Task 4 and consumed by name in Tasks 6-7; `ComposerKey`/`ComposerAction`/`ComposerEngine` defined in Task 3, consumed in Task 4.
- **Known intentional deviation:** spec lists `ui/SettingsWindow.kt`; plan ships those settings as tray menu items in v1 (noted above).
