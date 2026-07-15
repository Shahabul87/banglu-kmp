# বাংলু ইনপুট মেথড (macOS Input Method) Implementation Plan — S51

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A real macOS input method — Banglu in the system input-source menu, converting Banglish→Bangla live as marked text inside any app, with candidates, দাঁড়ি, learning shared with the desktop editor, ad-hoc signed for the developer's Mac.

**Architecture:** A Swift SPM package (`macos-ime/`) with two targets: `BangluCore` (pure logic — JSC engine host, Composer state machine, learned-words store; fully unit-tested headlessly) and `BangluIME` (the thin IMK executable). The engine is the shared Kotlin engine compiled to JS (`:shared:jsBrowserProductionLibraryDistribution`, esbuild-bundled as IIFE) plus `shared/banglu-slim.json`, hosted in JavaScriptCore. The `.app` bundle is assembled by a Makefile (no Xcode — this machine has CommandLineTools only; `swift build` + script bundling + `codesign --sign -`). Spec: `docs/superpowers/specs/2026-07-15-macos-input-method-design.md`.

**Tech Stack:** Swift 6 (SPM, XCTest), InputMethodKit, JavaScriptCore, Kotlin/JS (existing `shared` js target), esbuild (via `npx`, same as browser-extension/build.sh), make.

## Global Constraints

- No full Xcode on this machine (`xcodebuild` unavailable) — everything builds with `swift build` / `swift test` / `make`. Never add an `.xcodeproj`.
- The engine is the REAL shared engine: any conversion behavior lives in `shared` (Kotlin), never re-implemented in Swift. Swift may only orchestrate.
- WYSIWYG invariant: space commits EXACTLY the Bangla shown as marked text.
- Learning law (invariant #3): a pick teaches ONLY when it differs from what would have been committed anyway; the engine's first choice teaches nothing.
- দাঁড়ি via the pending-space model (IMK cannot edit committed text): space after a word commits the word and HOLDS the space; second space → commit `। `; a letter → commit `" "` then form; tight punctuation (`,` `।` `?` `!`) → swallow the held space; Enter/Tab/focus-loss → drop it.
- Files shared with the editor, exact formats: `~/.banglu/learned.json` rows `{p, b, f, t}`; `~/.banglu/editor.json` gains key `learningEnabled` (default true).
- Privacy: the IME has no network entitlement and never opens a socket.
- Distribution v1: ad-hoc signing (`codesign --sign -`), install to `~/Library/Input Methods/`. No Developer ID work.
- Bangla UI strings verbatim: বাংলু এডিটর খুলুন · শিখুন — টিউটোরিয়াল · শেখা চালু/বন্ধ.
- Bundle ID `com.banglu.inputmethod`; connection name `Banglu_Connection`; controller class `BangluInputController`.
- JS access path (proven by the extension): `(ns.com ?? ns).banglu.engine.BangluWebEngine`; bundle built `--format=iife --global-name=BangluNS`.
- All commands run from the repo root `/Users/mdshahabulalam/myprojects/banlgu/banglu-kmp` unless a step says otherwise.
- Commit style: `feat(macos-ime): S51 — <what>`; never `git add -A`; never push (the controller pushes after the user's manual gate).

## File Structure

```
shared/src/jsMain/kotlin/com/banglu/engine/BangluWebEngine.kt   MODIFY: + applyLearnedWords, recordPick
shared/src/jsTest/kotlin/com/banglu/engine/S51LearningJsTest.kt CREATE
desktop-app/src/main/kotlin/com/banglu/desktop/Main.kt           MODIFY: --tutorial launch arg
desktop-app/src/main/kotlin/com/banglu/desktop/editor/DraftStore.kt    MODIFY: learningEnabled pref
desktop-app/src/main/kotlin/com/banglu/desktop/editor/EditorScreen.kt  MODIFY: honor learningEnabled + tutorial arg
macos-ime/
├── Package.swift               BangluCore (lib) + BangluIME (exe) + BangluCoreTests
├── Makefile                    engine / build / bundle / install / uninstall / test
├── scripts/build-engine.sh     gradle JS build + esbuild IIFE bundle + slim copy
├── Resources/
│   ├── Info.plist              IME plist (connection name, controller class, bn, icon)
│   ├── appcompat.json          bundle-id → "plain" overrides (starts empty)
│   └── (built, gitignored)     banglu-engine.bundle.js, banglu-slim.json, menuicon.png
├── Sources/BangluCore/
│   ├── EngineJS.swift          JSC host implementing BangluEngine
│   ├── Composer.swift          event → [Action] state machine (spec §4)
│   ├── LearnedStore.swift      learned.json + editor.json (atomic writes)
│   └── AppCompat.swift         full/plain mode table
├── Sources/BangluIME/
│   ├── main.swift              IMKServer + NSApplication.run
│   ├── BangluInputController.swift  IMK glue: events→Composer, actions→client, menu
│   └── CandidateUI.swift       protocol + IMKCandidates impl + NSPanel fallback
└── Tests/BangluCoreTests/
    ├── FakeEngine.swift        deterministic engine for candidate/learning tests
    ├── ComposerTests.swift
    ├── ComposerParityTests.swift   real JSC engine, WYSIWYG pins
    └── LearnedStoreTests.swift
```

---

### Task 1: JS engine learning exports (`shared`)

**Files:**
- Modify: `shared/src/jsMain/kotlin/com/banglu/engine/BangluWebEngine.kt`
- Test: `shared/src/jsTest/kotlin/com/banglu/engine/S51LearningJsTest.kt`

**Interfaces:**
- Consumes: `SmartEngine.addWord(phonetic: String, bengali: String, frequency: Int)` and `SmartEngine.clearCache()` (both exist — the adapter's learn path calls exactly these).
- Produces (JS-visible on `BangluWebEngine`): `applyLearnedWords(json: String)` — accepts the editor's learned.json content (array of `{p,b,f,t}`, unknown keys ignored) and loads every row into the engine; `recordPick(raw: String, bangla: String)` — teaches one explicit pick at frequency 94. Task 2's `EngineJS.swift` calls both through JSC.

- [ ] **Step 1: Write the failing JS test**

Create `shared/src/jsTest/kotlin/com/banglu/engine/S51LearningJsTest.kt`:

```kotlin
package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/** S51: the macOS IME loads learned.json through these exports. */
class S51LearningJsTest {
    @Test
    fun appliedLearnedWordsWinTheirKey() {
        BangluWebEngine.initSeed()
        // zqx is an impossible romanization — rules produce junk for it.
        BangluWebEngine.applyLearnedWords(
            """[{"p":"zqx","b":"জাদু","f":94,"t":1752537600000}]"""
        )
        assertEquals("জাদু", BangluWebEngine.convert("zqx"))
    }

    @Test
    fun recordPickTeachesTheKey() {
        BangluWebEngine.initSeed()
        BangluWebEngine.recordPick("zqxw", "পরী")
        assertEquals("পরী", BangluWebEngine.convert("zqxw"))
    }

    @Test
    fun malformedLearnedJsonIsIgnored() {
        BangluWebEngine.initSeed()
        BangluWebEngine.applyLearnedWords("{not json")   // must not throw
        BangluWebEngine.applyLearnedWords("""[{"only":"junk"}]""")
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :shared:jsNodeTest --tests "com.banglu.engine.S51LearningJsTest" 2>&1 | tail -10`
(If the task name differs, `./gradlew :shared:tasks --all | grep -i jstest` lists it — the S45 jsTest suite already runs somewhere; use that task.)
Expected: FAIL — `applyLearnedWords` unresolved.

- [ ] **Step 3: Implement the exports**

In `BangluWebEngine.kt`, add inside the object (after `instantPreview`):

```kotlin
    /**
     * S51: load the editor's ~/.banglu/learned.json (rows {p,b,f,t}; unknown
     * keys ignored, malformed input ignored — the IME must never crash on a
     * user-editable file).
     */
    fun applyLearnedWords(json: String) {
        initSeed()
        val rows = try {
            lenientJson.decodeFromString<List<LearnedRow>>(json)
        } catch (_: Throwable) {
            return
        }
        var applied = false
        for (r in rows) {
            val key = r.p.trim().lowercase()
            val bengali = r.b.trim()
            if (key.isEmpty() || bengali.isEmpty()) continue
            engine.addWord(key, bengali, r.f)
            applied = true
        }
        if (applied) engine.clearCache()
    }

    /** S51: one explicit candidate pick (same frequency the adapter uses). */
    fun recordPick(raw: String, bangla: String) {
        initSeed()
        val key = raw.trim().lowercase()
        val bengali = bangla.trim()
        if (key.isEmpty() || bengali.isEmpty()) return
        engine.addWord(key, bengali, 94)
        engine.clearCache()
    }
```

And add at file bottom (next to the other @Serializable rows):

```kotlin
private val lenientJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class LearnedRow(val p: String = "", val b: String = "", val f: Int = 94)
```

- [ ] **Step 4: Run to verify it passes, then the existing JS wall**

Run: `./gradlew :shared:jsNodeTest 2>&1 | tail -5`
Expected: PASS including the pre-existing S45 parity tests (the new exports must not disturb them).

- [ ] **Step 5: Rebuild the JS library artifact (Task 2 consumes it)**

Run: `./gradlew :shared:jsBrowserProductionLibraryDistribution 2>&1 | tail -3 && ls shared/build/dist/js/productionLibrary/banglu-engine.js`
Expected: BUILD SUCCESSFUL, file listed.

- [ ] **Step 6: Commit**

```bash
git add shared/src/jsMain/kotlin/com/banglu/engine/BangluWebEngine.kt shared/src/jsTest/kotlin/com/banglu/engine/S51LearningJsTest.kt
git commit -m "feat(shared): S51 — JS engine learning exports for the macOS IME (applyLearnedWords, recordPick)"
```

---

### Task 2: SPM scaffold + EngineJS (JavaScriptCore host)

**Files:**
- Create: `macos-ime/Package.swift`, `macos-ime/scripts/build-engine.sh`, `macos-ime/Sources/BangluCore/EngineJS.swift`, `macos-ime/Sources/BangluIME/main.swift` (placeholder main so the exe target compiles)
- Test: `macos-ime/Tests/BangluCoreTests/EngineJSTests.swift`

**Interfaces:**
- Produces: `public protocol BangluEngine { func convert(_ raw: String) -> String; func suggestions(_ raw: String, limit: Int) -> [String]; func recordPick(raw: String, bangla: String) }` and `public final class EngineJS: BangluEngine` with `init(bundleJS: URL, slimJSON: URL?) throws` plus `func applyLearnedWords(json: String)`. Every later task uses exactly these names. Also `TestSupport.engineArtifacts()` in tests resolving artifact paths from env `BANGLU_JS_BUNDLE` / `BANGLU_SLIM_JSON`.

- [ ] **Step 1: Scaffold the package**

Create `macos-ime/Package.swift`:

```swift
// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "BangluIME",
    platforms: [.macOS(.v13)],
    targets: [
        .target(name: "BangluCore"),
        .executableTarget(
            name: "BangluIME",
            dependencies: ["BangluCore"],
            linkerSettings: [
                .linkedFramework("InputMethodKit"),
                .linkedFramework("Cocoa"),
            ]
        ),
        .testTarget(name: "BangluCoreTests", dependencies: ["BangluCore"]),
    ]
)
```

Create `macos-ime/Sources/BangluIME/main.swift` (placeholder until Task 6):

```swift
import Foundation
print("BangluIME placeholder — replaced in the IMK glue task")
```

Create `macos-ime/scripts/build-engine.sh`:

```bash
#!/bin/sh
# S51: builds the JS engine bundle the IME hosts in JavaScriptCore.
# Same pipeline as browser-extension/build.sh, but IIFE for JSC (no modules).
set -e
cd "$(dirname "$0")/../.."
./gradlew :shared:jsBrowserProductionLibraryDistribution
mkdir -p macos-ime/Resources/built
npx --yes esbuild shared/build/dist/js/productionLibrary/banglu-engine.js \
  --bundle --format=iife --global-name=BangluNS --minify \
  --outfile=macos-ime/Resources/built/banglu-engine.bundle.js
cp shared/banglu-slim.json macos-ime/Resources/built/banglu-slim.json
echo "engine bundle: $(du -h macos-ime/Resources/built/banglu-engine.bundle.js | cut -f1)"
echo "slim dict:     $(du -h macos-ime/Resources/built/banglu-slim.json | cut -f1)"
```

Run: `chmod +x macos-ime/scripts/build-engine.sh && ./macos-ime/scripts/build-engine.sh`
Expected: both files reported. Add `macos-ime/Resources/built/` to `.gitignore` (build artifacts, 17MB+).

- [ ] **Step 2: Write the failing engine test**

Create `macos-ime/Tests/BangluCoreTests/EngineJSTests.swift`:

```swift
import XCTest
@testable import BangluCore

enum TestSupport {
    static func engineArtifacts() throws -> (bundle: URL, slim: URL) {
        let env = ProcessInfo.processInfo.environment
        let root = URL(fileURLWithPath: #filePath)          // …/Tests/BangluCoreTests/…
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let bundle = env["BANGLU_JS_BUNDLE"].map(URL.init(fileURLWithPath:))
            ?? root.appendingPathComponent("Resources/built/banglu-engine.bundle.js")
        let slim = env["BANGLU_SLIM_JSON"].map(URL.init(fileURLWithPath:))
            ?? root.appendingPathComponent("Resources/built/banglu-slim.json")
        guard FileManager.default.fileExists(atPath: bundle.path) else {
            throw XCTSkip("engine bundle missing — run macos-ime/scripts/build-engine.sh first")
        }
        return (bundle, slim)
    }

    static let shared: EngineJS = {
        let a = try! engineArtifacts()
        return try! EngineJS(bundleJS: a.bundle, slimJSON: a.slim)
    }()
}

final class EngineJSTests: XCTestCase {
    func testConvertsThroughTheRealSlimDictionary() {
        XCTAssertEqual(TestSupport.shared.convert("kemon"), "কেমন")
        XCTAssertTrue(TestSupport.shared.suggestions("kemon", limit: 6).contains("কেমন"))
    }

    func testRecordPickTeaches() {
        TestSupport.shared.recordPick(raw: "zqxv", bangla: "ডানা")
        XCTAssertEqual(TestSupport.shared.convert("zqxv"), "ডানা")
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `cd macos-ime && swift test 2>&1 | tail -10`
Expected: FAIL — `EngineJS` not found.

- [ ] **Step 4: Implement EngineJS**

Create `macos-ime/Sources/BangluCore/EngineJS.swift`:

```swift
import Foundation
import JavaScriptCore

/// The IME's only door to conversion — same seam as the editor's EngineFacade.
public protocol BangluEngine {
    func convert(_ raw: String) -> String
    func suggestions(_ raw: String, limit: Int) -> [String]
    func recordPick(raw: String, bangla: String)
}

public enum EngineJSError: Error { case loadFailed(String) }

/// Hosts the shared Kotlin engine (compiled to JS) in JavaScriptCore.
/// The bundle is IIFE with global `BangluNS`; the engine object lives at
/// `(BangluNS.com ?? BangluNS).banglu.engine.BangluWebEngine` — the exact
/// access path the Chrome extension uses (browser-extension/background.js).
public final class EngineJS: BangluEngine {
    private let context: JSContext
    private let engine: JSValue

    public init(bundleJS: URL, slimJSON: URL?) throws {
        guard let ctx = JSContext() else { throw EngineJSError.loadFailed("JSContext") }
        var jsError: String?
        ctx.exceptionHandler = { _, ex in jsError = ex?.toString() }
        context = ctx

        let source = try String(contentsOf: bundleJS, encoding: .utf8)
        ctx.evaluateScript(source, withSourceURL: bundleJS)
        if let e = jsError { throw EngineJSError.loadFailed("bundle eval: \(e)") }

        guard let ns = ctx.objectForKeyedSubscript("BangluNS"), !ns.isUndefined else {
            throw EngineJSError.loadFailed("global BangluNS missing")
        }
        let com = ns.objectForKeyedSubscript("com")
        let root = (com?.isUndefined == false) ? com! : ns
        guard
            let eng = root.objectForKeyedSubscript("banglu")?
                .objectForKeyedSubscript("engine")?
                .objectForKeyedSubscript("BangluWebEngine"),
            !eng.isUndefined
        else { throw EngineJSError.loadFailed("BangluWebEngine not exported") }
        engine = eng

        engine.invokeMethod("initSeed", withArguments: [])
        if let slim = slimJSON, FileManager.default.fileExists(atPath: slim.path) {
            let json = try String(contentsOf: slim, encoding: .utf8)
            engine.invokeMethod("attachSlimDictionary", withArguments: [json])
        }
        if let e = jsError { throw EngineJSError.loadFailed("engine init: \(e)") }
    }

    public func convert(_ raw: String) -> String {
        engine.invokeMethod("convert", withArguments: [raw])?.toString() ?? raw
    }

    public func suggestions(_ raw: String, limit: Int) -> [String] {
        let v = engine.invokeMethod("suggestions", withArguments: [raw, limit])
        return (v?.toArray() as? [String]) ?? []
    }

    public func recordPick(raw: String, bangla: String) {
        engine.invokeMethod("recordPick", withArguments: [raw, bangla])
    }

    public func applyLearnedWords(json: String) {
        engine.invokeMethod("applyLearnedWords", withArguments: [json])
    }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `cd macos-ime && swift test 2>&1 | tail -6`
Expected: PASS (2 tests; the slim-dictionary parse makes the first run take ~5–10s).

- [ ] **Step 6: Commit**

```bash
git add macos-ime/Package.swift macos-ime/scripts/build-engine.sh macos-ime/Sources macos-ime/Tests .gitignore
git commit -m "feat(macos-ime): S51 — SPM scaffold + EngineJS: shared engine hosted in JavaScriptCore"
```

---

### Task 3: Composer — forming, WYSIWYG commit, pending-space দাঁড়ি, backspace, Escape

**Files:**
- Create: `macos-ime/Sources/BangluCore/Composer.swift`
- Test: `macos-ime/Tests/BangluCoreTests/ComposerTests.swift`, `macos-ime/Tests/BangluCoreTests/FakeEngine.swift`, `macos-ime/Tests/BangluCoreTests/ComposerParityTests.swift`

**Interfaces:**
- Consumes: `BangluEngine` (Task 2).
- Produces:

```swift
public enum ComposerAction: Equatable {
    case setMarked(String)      // "" clears the composing region
    case commit(String)         // insertText into the host
    case passThrough            // give the original event back to the host
    case updateCandidates([String])   // [] hides the panel
}
public enum ComposerKey: Equatable {
    case letter(Character)      // a-z A-Z
    case space
    case backspace
    case digit(Character)       // "0"..."9"
    case escape
    case returnKey, tab
    case punctuation(String)    // any other printable
    case arrowUp, arrowDown     // candidate navigation (Task 4 consumes)
}
public final class Composer {
    public init(engine: BangluEngine, banglaDigits: Bool = true, plainMode: Bool = false)
    public private(set) var formingRaw: String
    public private(set) var candidates: [String]
    public private(set) var highlight: Int
    public func handle(_ key: ComposerKey) -> [ComposerAction]
    public func focusLost() -> [ComposerAction]     // commitComposition path
    public func pick(_ index: Int) -> [ComposerAction]  // Task 4
    public var onPick: ((_ raw: String, _ bangla: String, _ wasPrimary: Bool) -> Void)?  // Task 5 wires learning
}
```

Behavior contract this task implements (spec §4 + the pending-space model): letters form (marked text = live conversion; in `plainMode` marked stays `""` but candidates still update — the render difference, nothing else); space commits the conversion and holds a pending space; second space commits `। ` (third space is plain — pending cleared after dari); a letter after pending space commits `" "` first; backspace in-forming edits raw (empty→clear marked+candidates), backspace idle passes through (and clears pending by committing `" "` first — the user sees the space they typed, then deletes as expected); Escape while forming commits the RAW text (inline English); Return/Tab commit forming (and drop pending) then pass through; focusLost commits the visible conversion (drops pending).

- [ ] **Step 1: Write the failing tests**

Create `macos-ime/Tests/BangluCoreTests/FakeEngine.swift`:

```swift
import Foundation
@testable import BangluCore

/// Deterministic engine: convert = uppercase + "…", suggestions fixed.
final class FakeEngine: BangluEngine {
    var picks: [(raw: String, bangla: String)] = []
    var table: [String: (primary: String, alts: [String])] = [:]
    func convert(_ raw: String) -> String { table[raw]?.primary ?? "<\(raw)>" }
    func suggestions(_ raw: String, limit: Int) -> [String] {
        let t = table[raw] ?? ("<\(raw)>", [])
        return Array(([t.primary] + t.alts).prefix(limit))
    }
    func recordPick(raw: String, bangla: String) { picks.append((raw, bangla)) }
}
```

Create `macos-ime/Tests/BangluCoreTests/ComposerTests.swift`:

```swift
import XCTest
@testable import BangluCore

final class ComposerTests: XCTestCase {
    private func composer(_ engine: BangluEngine = FakeEngine()) -> Composer {
        Composer(engine: engine)
    }
    private func type(_ c: Composer, _ s: String) -> [ComposerAction] {
        var out: [ComposerAction] = []
        for ch in s {
            if ch == " " { out += c.handle(.space) }
            else if ch.isLetter { out += c.handle(.letter(ch)) }
        }
        return out
    }

    func testLettersFormAndSpaceCommitsWYSIWYG() {
        let e = FakeEngine(); e.table["ami"] = ("আমি", ["আমই"])
        let c = composer(e)
        _ = c.handle(.letter("a")); _ = c.handle(.letter("m"))
        let last = c.handle(.letter("i"))
        XCTAssertTrue(last.contains(.setMarked("আমি")))
        let commit = c.handle(.space)
        XCTAssertTrue(commit.contains(.commit("আমি")))       // word, no space yet
        XCTAssertTrue(commit.contains(.setMarked("")))
        XCTAssertEqual(c.formingRaw, "")
    }

    func testPendingSpaceDari() {
        let e = FakeEngine(); e.table["kemon"] = ("কেমন", [])
        let c = composer(e)
        _ = type(c, "kemon")
        _ = c.handle(.space)                                  // commit কেমন, hold space
        let dari = c.handle(.space)                           // second space
        XCTAssertTrue(dari.contains(.commit("। ")))
        let third = c.handle(.space)                          // third: plain space held→
        XCTAssertFalse(third.contains(.commit("। ")))         // no second dari
    }

    func testLetterAfterPendingSpaceReleasesIt() {
        let e = FakeEngine(); e.table["ami"] = ("আমি", []); e.table["k"] = ("ক", [])
        let c = composer(e)
        _ = type(c, "ami")
        _ = c.handle(.space)
        let next = c.handle(.letter("k"))
        XCTAssertTrue(next.contains(.commit(" ")))            // held space released
        XCTAssertTrue(next.contains(.setMarked("ক")))         // new word forming
    }

    func testTightPunctuationSwallowsPendingSpace() {
        let e = FakeEngine(); e.table["kotha"] = ("কথা", [])
        let c = composer(e)
        _ = type(c, "kotha")
        _ = c.handle(.space)
        let comma = c.handle(.punctuation(","))
        XCTAssertTrue(comma.contains(.commit(",")))
        XCTAssertFalse(comma.contains(.commit(" ")))          // space swallowed
    }

    func testBackspaceEditsRawThenPassesThrough() {
        let e = FakeEngine(); e.table["kal"] = ("কাল", []); e.table["kali"] = ("কালি", [])
        let c = composer(e)
        _ = type(c, "kali")
        let bs = c.handle(.backspace)
        XCTAssertTrue(bs.contains(.setMarked("কাল")))
        XCTAssertEqual(c.formingRaw, "kal")
        _ = c.handle(.backspace); _ = c.handle(.backspace); _ = c.handle(.backspace)
        XCTAssertEqual(c.formingRaw, "")
        let idle = c.handle(.backspace)
        XCTAssertTrue(idle.contains(.passThrough))            // nothing forming
    }

    func testEscapeCommitsRawEnglish() {
        let e = FakeEngine(); e.table["kali"] = ("কালি", [])
        let c = composer(e)
        _ = type(c, "kali")
        let esc = c.handle(.escape)
        XCTAssertTrue(esc.contains(.commit("kali")))          // inline English
        XCTAssertTrue(esc.contains(.setMarked("")))
    }

    func testReturnCommitsFormingThenPassesThrough() {
        let e = FakeEngine(); e.table["hobe"] = ("হবে", [])
        let c = composer(e)
        _ = type(c, "hobe")
        let ret = c.handle(.returnKey)
        XCTAssertTrue(ret.contains(.commit("হবে")))
        XCTAssertTrue(ret.contains(.passThrough))             // Enter still sends
    }

    func testFocusLostCommitsVisibleWord() {
        let e = FakeEngine(); e.table["adh"] = ("আধ", [])
        let c = composer(e)
        _ = type(c, "adh")
        let out = c.focusLost()
        XCTAssertTrue(out.contains(.commit("আধ")))
        XCTAssertEqual(c.formingRaw, "")
    }

    func testPlainModeSuppressesMarkedTextOnly() {
        let e = FakeEngine(); e.table["ami"] = ("আমি", ["আমই"])
        let c = Composer(engine: e, plainMode: true)
        let a = c.handle(.letter("a"))
        XCTAssertFalse(a.contains { if case .setMarked(let s) = $0 { return !s.isEmpty } else { return false } })
        _ = c.handle(.letter("m")); _ = c.handle(.letter("i"))
        XCTAssertFalse(c.candidates.isEmpty)                  // preview lives in the panel
        let commit = c.handle(.space)
        XCTAssertTrue(commit.contains(.commit("আমি")))        // identical commits
    }
}
```

Create `macos-ime/Tests/BangluCoreTests/ComposerParityTests.swift` (real engine — the WYSIWYG pin):

```swift
import XCTest
@testable import BangluCore

/// Invariant #2 on the real JSC engine: what the marked text SHOWS is what
/// space COMMITS. Same phrases as the editor's WysiwygPinTest.
final class ComposerParityTests: XCTestCase {
    private let phrases = [
        "kemon acho bondhu",
        "ami bangla likhi",
        "issa korche golpo bolte",
        "bujte parcina keno",
        "kalke dekha hobe",
    ]

    func testCommittedEqualsShownMarkedText() throws {
        let engine = TestSupport.shared
        for phrase in phrases {
            let c = Composer(engine: engine)
            var committed = ""
            var lastMarked = ""
            for ch in phrase {
                let actions: [ComposerAction] =
                    ch == " " ? c.handle(.space) : c.handle(.letter(ch))
                for a in actions {
                    if case .setMarked(let m) = a, !m.isEmpty { lastMarked = m }
                    if case .commit(let t) = a {
                        if t != " " && t != "। " {
                            XCTAssertEqual(t, lastMarked, "phrase: \(phrase)")
                        }
                        committed += t
                    }
                }
            }
            _ = c.handle(.space)   // flush last word
            XCTAssertFalse(committed.isEmpty)
        }
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd macos-ime && swift test 2>&1 | tail -8`
Expected: FAIL — `Composer` not found.

- [ ] **Step 3: Implement Composer**

Create `macos-ime/Sources/BangluCore/Composer.swift`:

```swift
import Foundation

public enum ComposerAction: Equatable {
    case setMarked(String)
    case commit(String)
    case passThrough
    case updateCandidates([String])
}

public enum ComposerKey: Equatable {
    case letter(Character)
    case space
    case backspace
    case digit(Character)
    case escape
    case returnKey, tab
    case punctuation(String)
    case arrowUp, arrowDown
}

/// The IME's typing state machine — the editor's EditorState contract
/// re-expressed for IMK (spec §4). Pure logic, no IMK imports.
///
/// দাঁড়ি uses the pending-space model: IMK cannot edit committed text, so a
/// space after a word commits the word and HOLDS the space; what arrives
/// next decides whether it becomes " ", "। ", or is swallowed (tight
/// punctuation).
public final class Composer {
    private let engine: BangluEngine
    private let banglaDigits: Bool
    private let plainMode: Bool

    public private(set) var formingRaw = ""
    public private(set) var candidates: [String] = []
    public private(set) var highlight = 0
    private var formingBangla = ""
    private var pendingSpace = false
    private var dariJustCommitted = false

    /// Task 5 wires learning: called on every candidate pick.
    public var onPick: ((_ raw: String, _ bangla: String, _ wasPrimary: Bool) -> Void)?

    private static let tightPunctuation: Set<String> = [",", "।", "?", "!"]

    public init(engine: BangluEngine, banglaDigits: Bool = true, plainMode: Bool = false) {
        self.engine = engine
        self.banglaDigits = banglaDigits
        self.plainMode = plainMode
    }

    public var forming: Bool { !formingRaw.isEmpty }

    public func handle(_ key: ComposerKey) -> [ComposerAction] {
        switch key {
        case .letter(let c):
            var out = releasePendingSpace()
            formingRaw.append(c)
            refresh(&out)
            return out

        case .space:
            if forming {
                var out = commitForming()
                pendingSpace = true
                return out
            }
            if pendingSpace {
                pendingSpace = false
                if dariJustCommitted { dariJustCommitted = false; return [.commit(" ")] }
                dariJustCommitted = true
                return [.commit("। ")]
            }
            dariJustCommitted = false
            return [.commit(" ")]

        case .backspace:
            if forming {
                formingRaw.removeLast()
                var out: [ComposerAction] = []
                refresh(&out)
                return out
            }
            if pendingSpace {
                // The user saw themselves type a space; make it real, then
                // let the host's own backspace delete it.
                pendingSpace = false
                return [.commit(" "), .passThrough]
            }
            return [.passThrough]

        case .digit(let d):
            if forming, !candidates.isEmpty, let n = d.wholeNumberValue,
               (1...6).contains(n), n - 1 < candidates.count {
                return pick(n - 1)
            }
            var out = forming ? commitForming() : releasePendingSpace()
            out.append(.commit(banglaDigits ? bengaliDigit(d) : String(d)))
            return out

        case .escape:
            guard forming else { return [.passThrough] }
            let raw = formingRaw
            clearForming()
            return [.setMarked(""), .commit(raw), .updateCandidates([])]

        case .returnKey, .tab:
            var out = forming ? commitForming() : []
            pendingSpace = false
            out.append(.passThrough)
            return out

        case .punctuation(let p):
            var out: [ComposerAction] = forming ? commitForming() : []
            if pendingSpace {
                pendingSpace = false
                if !Composer.tightPunctuation.contains(p) { out.append(.commit(" ")) }
            }
            out.append(.commit(p == "." ? "।" : p))
            dariJustCommitted = (p == ".")
            return out

        case .arrowUp, .arrowDown:
            guard forming, !candidates.isEmpty else { return [.passThrough] }
            let delta = (key == .arrowDown) ? 1 : -1
            highlight = (highlight + delta + candidates.count) % candidates.count
            return []   // panel re-renders from `highlight`
        }
    }

    public func focusLost() -> [ComposerAction] {
        pendingSpace = false
        guard forming else { return [] }
        return commitForming()
    }

    public func pick(_ index: Int) -> [ComposerAction] {
        guard forming, index >= 0, index < candidates.count else { return [] }
        let choice = candidates[index]
        let wasPrimary = (choice == formingBangla)
        onPick?(formingRaw, choice, wasPrimary)
        formingBangla = choice
        var out = commitForming()
        pendingSpace = true
        return out
    }

    // MARK: - internals

    private func refresh(_ out: inout [ComposerAction]) {
        if formingRaw.isEmpty {
            clearForming()
            out.append(.setMarked(""))
            out.append(.updateCandidates([]))
            return
        }
        formingBangla = engine.convert(formingRaw)
        var list = engine.suggestions(formingRaw, limit: 6)
        if !list.contains(formingRaw) { list.append(formingRaw) }   // raw = inline English
        candidates = list
        highlight = 0
        out.append(.setMarked(plainMode ? "" : formingBangla))
        out.append(.updateCandidates(candidates))
    }

    /// WYSIWYG: commits exactly formingBangla — never re-converts.
    private func commitForming() -> [ComposerAction] {
        let text = formingBangla
        clearForming()
        dariJustCommitted = false
        return [.setMarked(""), .commit(text), .updateCandidates([])]
    }

    private func releasePendingSpace() -> [ComposerAction] {
        guard pendingSpace else { return [] }
        pendingSpace = false
        dariJustCommitted = false
        return [.commit(" ")]
    }

    private func clearForming() {
        formingRaw = ""
        formingBangla = ""
        candidates = []
        highlight = 0
    }
}

func bengaliDigit(_ c: Character) -> String {
    guard let n = c.wholeNumberValue, (0...9).contains(n) else { return String(c) }
    return String(Character(UnicodeScalar(0x09E6 + n)!))
}
```

- [ ] **Step 4: Run to verify all pass**

Run: `cd macos-ime && swift test 2>&1 | tail -6`
Expected: PASS (EngineJS 2 + Composer 9 + parity 1 = 12 tests).

- [ ] **Step 5: Commit**

```bash
git add macos-ime/Sources/BangluCore/Composer.swift macos-ime/Tests/BangluCoreTests/
git commit -m "feat(macos-ime): S51 — Composer: live forming, WYSIWYG commits, pending-space dari, Escape-English"
```

---

### Task 4: Composer — digit picks + learning law tests

**Files:**
- Test: `macos-ime/Tests/BangluCoreTests/ComposerTests.swift` (append)

(The `pick`/`digit` code shipped in Task 3 — this task is the dedicated verification gate for candidate behavior and the learning law hook, kept separate so a reviewer can reject candidate semantics without rejecting the core machine.)

**Interfaces:**
- Consumes: `Composer.pick(_:)`, `Composer.onPick`, `ComposerKey.digit` (Task 3).
- Produces: verified candidate/pick contract for Task 5 (learning wiring) and Task 6 (controller).

- [ ] **Step 1: Write the failing tests** (append to `ComposerTests.swift`)

```swift
    func testDigitPicksCandidateAndReportsNonPrimary() {
        let e = FakeEngine(); e.table["taka"] = ("টাকা", ["তাকা"])
        let c = composer(e)
        var reported: (String, String, Bool)?
        c.onPick = { reported = ($0, $1, $2) }
        _ = type(c, "taka")
        XCTAssertEqual(c.candidates, ["টাকা", "তাকা", "taka"])
        let out = c.handle(.digit("2"))                       // pick তাকা
        XCTAssertTrue(out.contains(.commit("তাকা")))
        XCTAssertEqual(reported?.0, "taka")
        XCTAssertEqual(reported?.1, "তাকা")
        XCTAssertEqual(reported?.2, false)                    // non-primary
    }

    func testPickingPrimaryReportsWasPrimary() {
        let e = FakeEngine(); e.table["ami"] = ("আমি", ["আমই"])
        let c = composer(e)
        var wasPrimary: Bool?
        c.onPick = { wasPrimary = $2 }
        _ = type(c, "ami")
        _ = c.handle(.digit("1"))
        XCTAssertEqual(wasPrimary, true)                      // must teach nothing
    }

    func testOutOfRangeDigitTypesBengaliNumeral() {
        let e = FakeEngine(); e.table["k"] = ("ক", [])        // 2 candidates: ক + raw k
        let c = composer(e)
        _ = c.handle(.letter("k"))
        XCTAssertEqual(c.candidates.count, 2)
        let out = c.handle(.digit("5"))                       // beyond list → digit
        XCTAssertTrue(out.contains(.commit("ক")))             // forming committed first
        XCTAssertTrue(out.contains(.commit("৫")))
    }

    func testIdleDigitTypesBengaliNumeral() {
        let c = composer()
        let out = c.handle(.digit("3"))
        XCTAssertTrue(out.contains(.commit("৩")))
    }

    func testArrowsMoveHighlightAndWrap() {
        let e = FakeEngine(); e.table["dan"] = ("দান", ["ডান"])
        let c = composer(e)
        _ = type(c, "dan")                                    // 3 rows incl raw
        _ = c.handle(.arrowDown); XCTAssertEqual(c.highlight, 1)
        _ = c.handle(.arrowDown); XCTAssertEqual(c.highlight, 2)
        _ = c.handle(.arrowDown); XCTAssertEqual(c.highlight, 0)
        _ = c.handle(.arrowUp);   XCTAssertEqual(c.highlight, 2)
    }

    func testPickAfterArrowsCommitsHighlighted() {
        let e = FakeEngine(); e.table["pore"] = ("পরে", ["পড়ে"])
        let c = composer(e)
        _ = type(c, "pore")
        _ = c.handle(.arrowDown)
        let out = c.pick(c.highlight)
        XCTAssertTrue(out.contains(.commit("পড়ে")))
        XCTAssertTrue(c.formingRaw.isEmpty)
    }
```

- [ ] **Step 2: Run — most should pass (Task 3 shipped the logic); fix any that fail**

Run: `cd macos-ime && swift test 2>&1 | tail -8`
Expected: PASS (18 tests). If `testOutOfRangeDigitTypesBengaliNumeral` fails, the digit branch's range check (`n - 1 < candidates.count`) is wrong — fix in `Composer.swift`, do not weaken the test.

- [ ] **Step 3: Commit**

```bash
git add macos-ime/Tests/BangluCoreTests/ComposerTests.swift
git commit -m "test(macos-ime): S51 — candidate picks, learning-law reporting, digit fall-through, arrow wrap"
```

---

### Task 5: LearnedStore + AppCompat (files shared with the editor)

**Files:**
- Create: `macos-ime/Sources/BangluCore/LearnedStore.swift`, `macos-ime/Sources/BangluCore/AppCompat.swift`, `macos-ime/Resources/appcompat.json`
- Test: `macos-ime/Tests/BangluCoreTests/LearnedStoreTests.swift`

**Interfaces:**
- Consumes: `EngineJS.applyLearnedWords(json:)`, `EngineJS.recordPick` (Task 2), `Composer.onPick` (Task 3/4).
- Produces:

```swift
public final class LearnedStore {
    public init(directory: URL)   // production: ~/.banglu
    public func learnedJSON() -> String?          // raw file content for applyLearnedWords
    public func recordPick(raw: String, bangla: String)   // upsert row, atomic write
    public var learningEnabled: Bool { get }      // reads editor.json, default true
    public func setLearningEnabled(_ on: Bool)    // writes editor.json (other keys preserved)
}
public struct AppCompat {
    public init(tableURL: URL?)
    public func mode(forBundleID id: String?) -> Mode   // .full | .plain
}
```

- [ ] **Step 1: Write the failing tests**

Create `macos-ime/Tests/BangluCoreTests/LearnedStoreTests.swift`:

```swift
import XCTest
@testable import BangluCore

final class LearnedStoreTests: XCTestCase {
    private func tempDir() -> URL {
        let d = FileManager.default.temporaryDirectory
            .appendingPathComponent("banglu-test-\(UUID().uuidString)")
        try! FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }

    func testRecordPickWritesEditorCompatibleRow() throws {
        let store = LearnedStore(directory: tempDir())
        store.recordPick(raw: "Korsi ", bangla: "করছি")
        let data = try Data(contentsOf: store.learnedFileURL)
        let rows = try JSONSerialization.jsonObject(with: data) as! [[String: Any]]
        XCTAssertEqual(rows.count, 1)
        XCTAssertEqual(rows[0]["p"] as? String, "korsi")     // trimmed + lowercased
        XCTAssertEqual(rows[0]["b"] as? String, "করছি")
        XCTAssertEqual(rows[0]["f"] as? Int, 94)
        XCTAssertNotNil(rows[0]["t"])                        // editor Row(p,b,f,t) shape
    }

    func testRepeatPickBumpsFrequencyNotDuplicates() throws {
        let store = LearnedStore(directory: tempDir())
        store.recordPick(raw: "korsi", bangla: "করছি")
        store.recordPick(raw: "korsi", bangla: "করছি")
        let rows = try JSONSerialization.jsonObject(
            with: Data(contentsOf: store.learnedFileURL)) as! [[String: Any]]
        XCTAssertEqual(rows.count, 1)
        XCTAssertEqual(rows[0]["f"] as? Int, 95)             // maxOf(f+1, 94) like the editor
    }

    func testMergesRowsWrittenByAnotherProcess() throws {
        let dir = tempDir()
        let store = LearnedStore(directory: dir)
        store.recordPick(raw: "a", bangla: "আ")
        // The editor writes a row between our reads:
        let external = """
        [{"p":"a","b":"আ","f":94,"t":1},{"p":"editor","b":"এডিটর","f":94,"t":2}]
        """
        try external.data(using: .utf8)!.write(to: store.learnedFileURL)
        store.recordPick(raw: "b", bangla: "ব")
        let rows = try JSONSerialization.jsonObject(
            with: Data(contentsOf: store.learnedFileURL)) as! [[String: Any]]
        XCTAssertEqual(rows.count, 3)                        // read-fresh preserved "editor"
    }

    func testLearningEnabledDefaultsTrueAndPersistsWithoutClobbering() throws {
        let dir = tempDir()
        // editor.json already has editor prefs — they must survive our write.
        try """
        {"recent":["/x.txt"],"banglaDigits":false,"winW":900,"winH":700}
        """.data(using: .utf8)!.write(to: dir.appendingPathComponent("editor.json"))
        let store = LearnedStore(directory: dir)
        XCTAssertTrue(store.learningEnabled)
        store.setLearningEnabled(false)
        XCTAssertFalse(LearnedStore(directory: dir).learningEnabled)
        let prefs = try JSONSerialization.jsonObject(
            with: Data(contentsOf: dir.appendingPathComponent("editor.json"))) as! [String: Any]
        XCTAssertEqual(prefs["winW"] as? Int, 900)           // untouched
        XCTAssertEqual(prefs["banglaDigits"] as? Bool, false)
    }

    func testCorruptFilesFallBackSafely() throws {
        let dir = tempDir()
        try "{not json".data(using: .utf8)!.write(to: dir.appendingPathComponent("learned.json"))
        let store = LearnedStore(directory: dir)
        XCTAssertNil(store.learnedJSON() == "{not json" ? nil : store.learnedJSON())
        store.recordPick(raw: "x", bangla: "ক্স")            // must not crash; rewrites clean
        let rows = try JSONSerialization.jsonObject(
            with: Data(contentsOf: store.learnedFileURL)) as! [[String: Any]]
        XCTAssertEqual(rows.count, 1)
    }

    func testAppCompatModes() throws {
        let dir = tempDir()
        let table = dir.appendingPathComponent("appcompat.json")
        try """
        {"plain": ["com.tinyspeck.slackmacgap"]}
        """.data(using: .utf8)!.write(to: table)
        let compat = AppCompat(tableURL: table)
        XCTAssertEqual(compat.mode(forBundleID: "com.apple.Notes"), .full)
        XCTAssertEqual(compat.mode(forBundleID: "com.tinyspeck.slackmacgap"), .plain)
        XCTAssertEqual(compat.mode(forBundleID: nil), .full)
        XCTAssertEqual(AppCompat(tableURL: nil).mode(forBundleID: "anything"), .full)
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd macos-ime && swift test 2>&1 | tail -6`
Expected: FAIL — `LearnedStore` not found.

- [ ] **Step 3: Implement**

Create `macos-ime/Sources/BangluCore/LearnedStore.swift`:

```swift
import Foundation

/// One brain shared with the desktop editor (spec §7): learned.json rows
/// {p,b,f,t} and editor.json prefs, in ~/.banglu. Writes are read-fresh →
/// update → tmp → atomic replace, the editor's proven pattern.
public final class LearnedStore {
    public let learnedFileURL: URL
    private let prefsFileURL: URL
    private let directory: URL

    public init(directory: URL) {
        self.directory = directory
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        learnedFileURL = directory.appendingPathComponent("learned.json")
        prefsFileURL = directory.appendingPathComponent("editor.json")
    }

    /// Raw file content for EngineJS.applyLearnedWords (which tolerates junk).
    public func learnedJSON() -> String? {
        try? String(contentsOf: learnedFileURL, encoding: .utf8)
    }

    public func recordPick(raw: String, bangla: String) {
        let p = raw.trimmingCharacters(in: .whitespaces).lowercased()
        let b = bangla.trimmingCharacters(in: .whitespaces)
        guard !p.isEmpty, !b.isEmpty else { return }

        var rows = readRows()
        let now = Int(Date().timeIntervalSince1970 * 1000)
        if let i = rows.firstIndex(where: { $0["p"] as? String == p && $0["b"] as? String == b }) {
            let old = rows[i]["f"] as? Int ?? 94
            rows[i]["f"] = max(old + 1, 94)                  // editor's bump rule
            rows[i]["t"] = now
        } else {
            rows.append(["p": p, "b": b, "f": 94, "t": now])
        }
        writeAtomic(rows: rows)
    }

    public var learningEnabled: Bool {
        (readPrefs()["learningEnabled"] as? Bool) ?? true
    }

    public func setLearningEnabled(_ on: Bool) {
        var prefs = readPrefs()
        prefs["learningEnabled"] = on
        writeAtomicJSON(object: prefs, to: prefsFileURL)
    }

    // MARK: - internals

    private func readRows() -> [[String: Any]] {
        guard let data = try? Data(contentsOf: learnedFileURL),
              let rows = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]
        else { return [] }
        return rows
    }

    private func readPrefs() -> [String: Any] {
        guard let data = try? Data(contentsOf: prefsFileURL),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return [:] }
        return obj
    }

    private func writeAtomic(rows: [[String: Any]]) {
        writeAtomicJSON(object: rows, to: learnedFileURL)
    }

    private func writeAtomicJSON(object: Any, to url: URL) {
        guard let data = try? JSONSerialization.data(withJSONObject: object) else { return }
        let tmp = directory.appendingPathComponent(url.lastPathComponent + ".tmp")
        do {
            try data.write(to: tmp)
            _ = try FileManager.default.replaceItemAt(url, withItemAt: tmp)
        } catch {
            FileHandle.standardError.write(Data("Banglu IME write failed: \(error)\n".utf8))
        }
    }
}
```

Create `macos-ime/Sources/BangluCore/AppCompat.swift`:

```swift
import Foundation

/// Per-app render mode (spec §6): `full` = marked text; `plain` = no marked
/// text, preview lives only in the candidate panel, commits via insertText.
public struct AppCompat {
    public enum Mode { case full, plain }
    private let plainIDs: Set<String>

    public init(tableURL: URL?) {
        guard let url = tableURL,
              let data = try? Data(contentsOf: url),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let list = obj["plain"] as? [String]
        else { plainIDs = []; return }
        plainIDs = Set(list)
    }

    public func mode(forBundleID id: String?) -> Mode {
        guard let id else { return .full }
        return plainIDs.contains(id) ? .plain : .full
    }
}
```

Create `macos-ime/Resources/appcompat.json` (starts empty — the manual gate fills it):

```json
{"plain": []}
```

- [ ] **Step 4: Run to verify all pass**

Run: `cd macos-ime && swift test 2>&1 | tail -6`
Expected: PASS (25 tests).

- [ ] **Step 5: Commit**

```bash
git add macos-ime/Sources/BangluCore/LearnedStore.swift macos-ime/Sources/BangluCore/AppCompat.swift macos-ime/Resources/appcompat.json macos-ime/Tests/BangluCoreTests/LearnedStoreTests.swift
git commit -m "feat(macos-ime): S51 — LearnedStore (editor-shared brain, atomic) + AppCompat plain-mode table"
```

---

### Task 6: IMK glue — controller, server, Info.plist

**Files:**
- Create: `macos-ime/Sources/BangluIME/BangluInputController.swift`, `macos-ime/Resources/Info.plist`
- Modify: `macos-ime/Sources/BangluIME/main.swift` (replace placeholder)

**Interfaces:**
- Consumes: `Composer`, `EngineJS`, `LearnedStore`, `AppCompat` (Tasks 2–5); `CandidateUI` protocol arrives in Task 7 — this task uses a no-op `NullCandidateUI` placeholder that Task 7 replaces.
- Produces: `@objc(BangluInputController) class BangluInputController: IMKInputController` — the class named in Info.plist; `AppState` singleton owning EngineJS/LearnedStore (one engine per process, shared by all controller instances).

No unit tests (IMK needs a live input session) — gates: `swift build` clean; behavior verified at Tasks 8–9 on the real system.

- [ ] **Step 1: main.swift**

Replace `macos-ime/Sources/BangluIME/main.swift`:

```swift
import Cocoa
import InputMethodKit
import BangluCore

/// Process-wide singletons: ONE JSC engine + store, shared by every
/// controller instance (IMK creates one controller per client app).
enum AppState {
    static let store = LearnedStore(
        directory: FileManager.default.homeDirectoryForCurrentUser
            .appendingPathComponent(".banglu"))
    static let compat = AppCompat(
        tableURL: Bundle.main.url(forResource: "appcompat", withExtension: "json"))
    static let engine: EngineJS? = {
        guard
            let bundleJS = Bundle.main.url(forResource: "banglu-engine.bundle", withExtension: "js"),
            let slim = Bundle.main.url(forResource: "banglu-slim", withExtension: "json")
        else { return nil }
        let e = try? EngineJS(bundleJS: bundleJS, slimJSON: slim)
        if let json = store.learnedJSON() { e?.applyLearnedWords(json: json) }
        return e
    }()

    /// Re-read editor learnings on every input-source activation (spec §7).
    static func reloadLearned() {
        if let json = store.learnedJSON() { engine?.applyLearnedWords(json: json) }
    }
}

guard let connectionName = Bundle.main.infoDictionary?["InputMethodConnectionName"] as? String
else { fatalError("InputMethodConnectionName missing from Info.plist") }

// Retained for the process lifetime — macOS connects clients through it.
let server = IMKServer(name: connectionName, bundleIdentifier: Bundle.main.bundleIdentifier!)
NSApplication.shared.run()
```

- [ ] **Step 2: Info.plist**

Create `macos-ime/Resources/Info.plist`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key><string>bn</string>
    <key>CFBundleExecutable</key><string>BangluIME</string>
    <key>CFBundleIdentifier</key><string>com.banglu.inputmethod</string>
    <key>CFBundleName</key><string>Banglu</string>
    <key>CFBundleDisplayName</key><string>বাংলু</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleShortVersionString</key><string>1.0.0</string>
    <key>CFBundleVersion</key><string>1</string>
    <key>LSBackgroundOnly</key><true/>
    <key>LSMinimumSystemVersion</key><string>13.0</string>
    <key>InputMethodConnectionName</key><string>Banglu_Connection</string>
    <key>InputMethodServerControllerClass</key><string>BangluInputController</string>
    <key>TISInputSourceID</key><string>com.banglu.inputmethod</string>
    <key>TISIntendedLanguage</key><string>bn</string>
    <key>tsInputMethodCharacterRepertoireKey</key>
    <array><string>Beng</string></array>
    <key>tsInputMethodIconFileKey</key><string>menuicon.png</string>
</dict>
</plist>
```

- [ ] **Step 3: The controller**

Create `macos-ime/Sources/BangluIME/BangluInputController.swift`:

```swift
import Cocoa
import InputMethodKit
import BangluCore

/// IMK glue: NSEvents → ComposerKey, ComposerActions → the client app.
/// All conversion logic lives in Composer/EngineJS — this file only routes.
@objc(BangluInputController)
class BangluInputController: IMKInputController {
    private var composer: Composer?
    private var candidateUI: CandidateUI = NullCandidateUI()

    override func activateServer(_ sender: Any!) {
        super.activateServer(sender)
        AppState.reloadLearned()
        let bundleID = (sender as? IMKTextInput)?.bundleIdentifier()
        let plain = AppState.compat.mode(forBundleID: bundleID) == .plain
        guard let engine = AppState.engine else { return }
        let c = Composer(engine: engine, plainMode: plain)
        c.onPick = { raw, bangla, wasPrimary in
            // Invariant #3: the engine's own first choice teaches nothing.
            guard !wasPrimary, AppState.store.learningEnabled else { return }
            AppState.store.recordPick(raw: raw, bangla: bangla)
            engine.recordPick(raw: raw, bangla: bangla)
        }
        composer = c
    }

    override func deactivateServer(_ sender: Any!) {
        flush(to: sender)
        candidateUI.hide()
        super.deactivateServer(sender)
    }

    override func commitComposition(_ sender: Any!) {
        flush(to: sender)
    }

    override func recognizedEvents(_ sender: Any!) -> Int {
        Int(NSEvent.EventTypeMask.keyDown.rawValue)
    }

    override func handle(_ event: NSEvent!, client sender: Any!) -> Bool {
        guard let composer, let event, event.type == .keyDown else { return false }
        // Never eat shortcuts.
        if event.modifierFlags.contains(.command) || event.modifierFlags.contains(.control) {
            if composer.forming { apply(composer.focusLost(), to: sender) }
            return false
        }
        guard let key = composerKey(for: event, composerActive: composer.forming) else {
            if composer.forming { apply(composer.focusLost(), to: sender) }
            return false
        }
        let actions = composer.handle(key)
        return apply(actions, to: sender)
    }

    override func menu() -> NSMenu! {
        let menu = NSMenu()
        menu.addItem(withTitle: "বাংলু এডিটর খুলুন",
                     action: #selector(openEditor), keyEquivalent: "").target = self
        menu.addItem(withTitle: "শিখুন — টিউটোরিয়াল",
                     action: #selector(openTutorial), keyEquivalent: "").target = self
        let learn = menu.addItem(withTitle: "শেখা চালু/বন্ধ",
                                 action: #selector(toggleLearning), keyEquivalent: "")
        learn.target = self
        learn.state = AppState.store.learningEnabled ? .on : .off
        return menu
    }

    @objc private func openEditor() {
        NSWorkspace.shared.open(URL(fileURLWithPath: "/Applications/Banglu.app"))
    }

    @objc private func openTutorial() {
        let cfg = NSWorkspace.OpenConfiguration()
        cfg.arguments = ["--tutorial"]
        NSWorkspace.shared.openApplication(
            at: URL(fileURLWithPath: "/Applications/Banglu.app"),
            configuration: cfg, completionHandler: nil)
    }

    @objc private func toggleLearning() {
        AppState.store.setLearningEnabled(!AppState.store.learningEnabled)
    }

    // MARK: - routing

    private func composerKey(for event: NSEvent, composerActive: Bool) -> ComposerKey? {
        switch event.keyCode {
        case 51: return .backspace
        case 53: return .escape
        case 36: return .returnKey
        case 48: return .tab
        case 125: return composerActive ? .arrowDown : nil
        case 126: return composerActive ? .arrowUp : nil
        default: break
        }
        guard let chars = event.characters, chars.count == 1, let ch = chars.first else {
            return nil
        }
        if ch == " " { return .space }
        if ch.isLetter, ch.isASCII { return .letter(ch) }
        if ch.isNumber, ch.isASCII { return .digit(ch) }
        if ch.isNewline { return .returnKey }
        // Any other printable = punctuation; control chars fall through.
        if !ch.isASCII || ch.asciiValue.map({ $0 >= 32 }) == true {
            return .punctuation(String(ch))
        }
        return nil
    }

    @discardableResult
    private func apply(_ actions: [ComposerAction], to sender: Any!) -> Bool {
        guard let client = sender as? IMKTextInput else { return false }
        var handled = !actions.isEmpty
        for action in actions {
            switch action {
            case .setMarked(let text):
                let attrs: [NSAttributedString.Key: Any] =
                    [.underlineStyle: NSUnderlineStyle.single.rawValue]
                client.setMarkedText(
                    NSAttributedString(string: text, attributes: attrs),
                    selectionRange: NSRange(location: text.utf16.count, length: 0),
                    replacementRange: NSRange(location: NSNotFound, length: 0))
            case .commit(let text):
                client.insertText(text,
                    replacementRange: NSRange(location: NSNotFound, length: 0))
            case .updateCandidates(let list):
                if list.isEmpty { candidateUI.hide() }
                else { candidateUI.show(candidates: list, highlight: composer?.highlight ?? 0,
                                        client: client) }
            case .passThrough:
                handled = false
            }
        }
        return handled
    }

    private func flush(to sender: Any!) {
        guard let composer else { return }
        apply(composer.focusLost(), to: sender)
        candidateUI.hide()
    }
}

/// Task 7 replaces this with the real IMKCandidates/NSPanel implementations.
protocol CandidateUI {
    func show(candidates: [String], highlight: Int, client: IMKTextInput)
    func hide()
}
final class NullCandidateUI: CandidateUI {
    func show(candidates: [String], highlight: Int, client: IMKTextInput) {}
    func hide() {}
}
```

- [ ] **Step 4: Build + full core tests**

Run: `cd macos-ime && swift build 2>&1 | tail -3 && swift test 2>&1 | tail -3`
Expected: Build complete; 25 tests pass. (Note: `apply` returning false for passThrough-containing action lists is load-bearing — Enter/Tab must reach the host after the commit.)

- [ ] **Step 5: Commit**

```bash
git add macos-ime/Sources/BangluIME/ macos-ime/Resources/Info.plist
git commit -m "feat(macos-ime): S51 — IMK controller: events→Composer, marked text, commits, menu"
```

---

### Task 7: Candidate UI — IMKCandidates + NSPanel fallback

**Files:**
- Create: `macos-ime/Sources/BangluIME/CandidateUI.swift`
- Modify: `macos-ime/Sources/BangluIME/BangluInputController.swift` (remove the protocol + Null impl from that file; instantiate the real UI; route Enter to pick when the panel shows)

**Interfaces:**
- Consumes: `CandidateUI` protocol shape from Task 6, `Composer.pick/highlight/candidates`.
- Produces: `PanelCandidateUI` (custom NSPanel — the PRIMARY implementation) and the constant `useIMKCandidates = false`. Rationale: IMKCandidates cannot render our pick-with-1–6 + raw-English-last-row contract reliably and its API gives no highlight control; the spec's "swappable protocol" is satisfied by keeping the seam, but we ship the NSPanel (editor-look) first and can add an IMKCandidates impl behind the constant later if wanted. This is a controller-approved refinement of spec §5 — the spec's fallback becomes the primary; the protocol seam it demanded is exactly what makes that a one-line change.

- [ ] **Step 1: Implement the panel**

Create `macos-ime/Sources/BangluIME/CandidateUI.swift` (and DELETE the protocol + `NullCandidateUI` from `BangluInputController.swift`):

```swift
import Cocoa
import InputMethodKit
import BangluCore

protocol CandidateUI {
    func show(candidates: [String], highlight: Int, client: IMKTextInput)
    func hide()
    var isVisible: Bool { get }
}

/// Editor-style dark candidate card, caret-anchored via the client's
/// attributes(forCharacterIndex:) rect. Never takes key focus.
final class PanelCandidateUI: CandidateUI {
    private let panel: NSPanel
    private let stack = NSStackView()
    private(set) var isVisible = false

    init() {
        panel = NSPanel(contentRect: .zero,
                        styleMask: [.nonactivatingPanel, .borderless],
                        backing: .buffered, defer: true)
        panel.level = .popUpMenu
        panel.isOpaque = false
        panel.backgroundColor = .clear
        panel.hasShadow = true

        let container = NSVisualEffectView()
        container.material = .hudWindow
        container.state = .active
        container.wantsLayer = true
        container.layer?.cornerRadius = 8

        stack.orientation = .vertical
        stack.alignment = .leading
        stack.spacing = 2
        stack.edgeInsets = NSEdgeInsets(top: 6, left: 8, bottom: 6, right: 8)
        stack.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: container.topAnchor),
            stack.bottomAnchor.constraint(equalTo: container.bottomAnchor),
            stack.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: container.trailingAnchor),
        ])
        panel.contentView = container
    }

    func show(candidates: [String], highlight: Int, client: IMKTextInput) {
        stack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        let bengaliDigits = ["১", "২", "৩", "৪", "৫", "৬"]
        for (i, cand) in candidates.prefix(6).enumerated() {
            let row = NSTextField(labelWithString: "\(bengaliDigits[i])  \(cand)")
            row.font = NSFont.systemFont(ofSize: 15)
            row.textColor = (i == highlight) ? .selectedMenuItemTextColor : .labelColor
            row.drawsBackground = i == highlight
            row.backgroundColor = (i == highlight) ? .selectedContentBackgroundColor : .clear
            stack.addArrangedSubview(row)
        }
        panel.setContentSize(stack.fittingSize)

        // Caret rect from the host; falls back to the mouse location's screen.
        var rect = NSRect.zero
        client.attributes(forCharacterIndex: 0, lineHeightRectangle: &rect)
        let origin = NSPoint(x: rect.origin.x, y: rect.origin.y - panel.frame.height - 4)
        panel.setFrameOrigin(origin)
        panel.orderFrontRegardless()
        isVisible = true
    }

    func hide() {
        panel.orderOut(nil)
        isVisible = false
    }
}

/// Kept as the documented swap seam (spec §5). Not shipped in v1: the system
/// panel offers no highlight control or custom last-row rendering.
let useIMKCandidates = false
```

- [ ] **Step 2: Wire into the controller**

In `BangluInputController.swift`:
1. Delete the local `protocol CandidateUI` and `NullCandidateUI` (moved/replaced by CandidateUI.swift).
2. Change the property to `private var candidateUI: CandidateUI = PanelCandidateUI()`.
3. In `handle(_:client:)`, replace the `let actions = composer.handle(key)` line (and the `return apply(...)` after it) with:

```swift
        let actions: [ComposerAction]
        if key == .returnKey, candidateUI.isVisible, composer.forming {
            actions = composer.pick(composer.highlight)
        } else if key == .arrowUp || key == .arrowDown {
            _ = composer.handle(key)
            candidateUI.show(candidates: composer.candidates,
                             highlight: composer.highlight,
                             client: sender as! IMKTextInput)
            return true
        } else {
            actions = composer.handle(key)
        }
        return apply(actions, to: sender)
```

- [ ] **Step 3: Build + tests**

Run: `cd macos-ime && swift build 2>&1 | tail -3 && swift test 2>&1 | tail -3`
Expected: clean build, 25 tests pass.

- [ ] **Step 4: Commit**

```bash
git add macos-ime/Sources/BangluIME/CandidateUI.swift macos-ime/Sources/BangluIME/BangluInputController.swift
git commit -m "feat(macos-ime): S51 — caret-anchored candidate panel (editor look), Enter/arrow routing"
```

---

### Task 8: Desktop editor — `--tutorial` arg + learningEnabled pref

**Files:**
- Modify: `desktop-app/src/main/kotlin/com/banglu/desktop/Main.kt`, `desktop-app/src/main/kotlin/com/banglu/desktop/editor/DraftStore.kt`, `desktop-app/src/main/kotlin/com/banglu/desktop/editor/EditorScreen.kt`
- Test: `desktop-app/src/test/kotlin/com/banglu/desktop/editor/DraftStoreTest.kt` (append)

**Interfaces:**
- Consumes: existing `EditorPrefs`, `EditorScreen`, `SmartEngineAdapter.configureLearning(enabled: Boolean, personalDictionary: Boolean)`.
- Produces: `EditorPrefs.learningEnabled: Boolean = true` (serialization-compatible with the IME's editor.json writes); `EditorScreen(startInTutorial: Boolean = false)`; `main()` passes `args.contains("--tutorial")`.

- [ ] **Step 1: Failing pref test** (append to `DraftStoreTest.kt`)

```kotlin
    @Test
    fun learningEnabledDefaultsTrueAndRoundTrips() {
        val store = tempStore()
        assertEquals(true, store.loadPrefs().learningEnabled)
        store.savePrefs(store.loadPrefs().copy(learningEnabled = false))
        assertEquals(false, store.loadPrefs().learningEnabled)
    }
```

Run: `./gradlew :desktop-app:test --tests "com.banglu.desktop.editor.DraftStoreTest" 2>&1 | tail -5` — Expected: FAIL (unresolved `learningEnabled`).

- [ ] **Step 2: Implement**

In `DraftStore.kt`, add to `EditorPrefs`:

```kotlin
    val learningEnabled: Boolean = true,
```

In `Main.kt`: change `fun main() = application {` to

```kotlin
fun main(args: Array<String>) {
    val startInTutorial = args.contains("--tutorial")
    application {
```

(close the added brace at file end of `main`), and pass `EditorScreen(startInTutorial = startInTutorial)`.

In `EditorScreen.kt`:
- Signature: `fun FrameWindowScope.EditorScreen(startInTutorial: Boolean = false)`.
- `var tutorialOpen by remember { mutableStateOf(startInTutorial) }`.
- In the engine-boot `LaunchedEffect(Unit)`, after `SmartEngineAdapter.configurePersistenceScope(scope)` add:

```kotlin
        val learning = drafts.loadPrefs().learningEnabled
        SmartEngineAdapter.configureLearning(enabled = learning, personalDictionary = learning)
```

- [ ] **Step 3: Gates**

Run: `./gradlew :desktop-app:test 2>&1 | tail -3 && ./gradlew :desktop-app:compileKotlin 2>&1 | tail -2`
Expected: 30 tests pass (29 + 1), build clean.

- [ ] **Step 4: Commit**

```bash
git add desktop-app/src/main/kotlin/com/banglu/desktop/Main.kt desktop-app/src/main/kotlin/com/banglu/desktop/editor/DraftStore.kt desktop-app/src/main/kotlin/com/banglu/desktop/editor/EditorScreen.kt desktop-app/src/test/kotlin/com/banglu/desktop/editor/DraftStoreTest.kt
git commit -m "feat(desktop): S51 — --tutorial launch arg + learningEnabled pref honored (IME menu integration)"
```

---

### Task 9: Bundle assembly, signing, install (Makefile)

**Files:**
- Create: `macos-ime/Makefile`

**Interfaces:**
- Consumes: everything built so far.
- Produces: `make engine` / `make build` / `make install` / `make uninstall` / `make test`. The installed artifact: `~/Library/Input Methods/Banglu.app` (ad-hoc signed) registered via TIS.

- [ ] **Step 1: Makefile**

Create `macos-ime/Makefile`:

```makefile
# S51: builds and installs the Banglu input method (no Xcode — SPM + scripts).
APP      = build/Banglu.app
BINARY   = .build/release/BangluIME
INSTALL  = $(HOME)/Library/Input\ Methods/Banglu.app

.PHONY: engine build bundle install uninstall test icon

engine:
	./scripts/build-engine.sh

test: engine
	swift test

build:
	swift build -c release

icon:
	mkdir -p Resources/built
	sips -z 32 32 ../desktop-app/icons/banglu.png \
	  --out Resources/built/menuicon.png >/dev/null

bundle: build icon
	rm -rf $(APP)
	mkdir -p $(APP)/Contents/MacOS $(APP)/Contents/Resources
	cp $(BINARY) $(APP)/Contents/MacOS/BangluIME
	cp Resources/Info.plist $(APP)/Contents/
	cp Resources/built/banglu-engine.bundle.js $(APP)/Contents/Resources/
	cp Resources/built/banglu-slim.json $(APP)/Contents/Resources/
	cp Resources/built/menuicon.png $(APP)/Contents/Resources/
	cp Resources/appcompat.json $(APP)/Contents/Resources/
	codesign --force --deep --sign - $(APP)
	@echo "bundle ready: $(APP)"

install: engine bundle
	rm -rf $(INSTALL)
	cp -R $(APP) $(HOME)/Library/Input\ Methods/
	@echo "Installed. Now: System Settings → Keyboard → Input Sources → + → বাংলা → বাংলু"
	@echo "(If it does not appear, log out and back in once — TIS caches the list.)"

uninstall:
	rm -rf $(INSTALL)
	@echo "Removed. Also remove the input source in System Settings."
```

- [ ] **Step 2: Build the bundle end to end**

Run: `cd macos-ime && make install 2>&1 | tail -5 && ls ~/Library/Input\ Methods/Banglu.app/Contents/Resources/`
Expected: install message; Resources lists the engine bundle (≈3–4MB minified), slim json (17MB), menuicon.png, appcompat.json.

- [ ] **Step 3: Verify process boots headlessly**

Run: `~/Library/Input\ Methods/Banglu.app/Contents/MacOS/BangluIME & sleep 3; pgrep -fl BangluIME && kill %1`
Expected: process alive (IMKServer registered; it idles without clients). No crash output.

- [ ] **Step 4: Commit**

```bash
git add macos-ime/Makefile
git commit -m "feat(macos-ime): S51 — bundle assembly, ad-hoc signing, make install/uninstall"
```

---

### Task 10: MANUAL GATE (user) — enable, smoke, full app matrix

No files. The user, on their Mac:

- [ ] **Step 1: Enable** — System Settings → Keyboard → Input Sources → `+` → বাংলা → বাংলু (log out/in once if absent). Switch to it with the Globe/⌃Space switcher in Notes.
- [ ] **Step 2: Smoke script in Notes** — type `kemon acho bondhu`: watch live underlined Bangla form and space-commit; `issa`/`korsi` shorthand; a digit pick from the panel; one Esc-English word (`assignment`); double-space → দাঁড়ি; mid-word backspace edits the English; click elsewhere mid-word → word commits.
- [ ] **Step 3: Full matrix** — same script in TextEdit, Pages, Spotlight, Safari (Gmail, WhatsApp Web, Google Docs), Chrome (same three), Word, WhatsApp Desktop, Messenger, Slack, Discord. Verdict per app: `full` / `plain` / fail. Any `plain` verdicts → add the app's bundle ID to `macos-ime/Resources/appcompat.json` (`osascript -e 'id of app "Slack"'` prints an app's bundle ID), `make install` again, re-verify that app in plain mode.
- [ ] **Step 4: Learning round-trip** — pick a non-primary in the IME; open বাংলু এডিটর and type the same key (should show the taught word first). Teach a word in the editor; switch the IME away and back; type the key (taught word wins).
- [ ] **Step 5: Menu** — বা menu: editor opens; শিখুন opens the editor in the tutorial; শেখা toggle flips (checkmark) and survives restart.
- [ ] **Step 6: Lifecycle** — log out/in: Banglu still listed and working.

Record verdicts in the ledger. Pass = every gated app `full` or `plain`, none failing.

---

### Task 11: Wrap-up — appcompat commits, docs, version, push (controller + user)

- [ ] **Step 1:** Commit any `appcompat.json` entries from the gate with the per-app verdict table in the message.
- [ ] **Step 2:** Full regression wall: `./gradlew :shared:jvmTest :shared:testDebugUnitTest :shared:jsNodeTest :desktop-app:test --rerun-tasks 2>&1 | tail -5` and `cd macos-ime && swift test` — all green.
- [ ] **Step 3:** Update `CLAUDE.md` repo map with the `macos-ime/` entry (one line) and commit.
- [ ] **Step 4:** Controller pushes `main` after the user confirms the gate.

---

## Self-Review Notes

- **Spec coverage:** §2 engine host → Tasks 1–2; §3 architecture/install → Tasks 6, 9; §4 Composer incl pending-space dari → Tasks 3–4; §5 candidates → Task 7 (with a documented refinement: the NSPanel fallback ships as primary behind the protocol seam — IMKCandidates offers no highlight/last-row control; the seam the spec demanded makes this a one-line swap either way); §6 compat/plain mode → Tasks 3 (plainMode), 5 (AppCompat), 10 (matrix fills the table); §7 learning/files/menu → Tasks 1, 5, 6, 8; §8 testing → unit suites in Tasks 2–5 + gate in Task 10; §9 exclusions respected (no Developer ID, no K/N, no click-to-fix, no EN mode).
- **Deviation to surface to the user at execution time (spec §5):** shipping the custom panel as primary instead of IMKCandidates. Reason above; the protocol seam preserves the spec's swappability. If the user prefers IMKCandidates-first, only Task 7 changes.
- **Type consistency check:** `BangluEngine`/`EngineJS` names match across Tasks 2/3/5/6; `ComposerAction`/`ComposerKey` cases used in Tasks 4/6/7 all exist in Task 3's definitions; `CandidateUI.show(candidates:highlight:client:)`/`hide()/isVisible` consistent between Tasks 6/7; `LearnedStore` API in Task 6 matches Task 5; `EditorPrefs.learningEnabled` name identical in Swift (Task 5 JSON key) and Kotlin (Task 8).
- **Known risks flagged for reviewers:** `attributes(forCharacterIndex:)` caret rect returns zero in some hosts (panel then appears at screen origin — Task 10 will surface it; fix = fall back to `NSEvent.mouseLocation` anchor); `handle`'s Enter-picks behavior means Enter never reaches chat apps while the panel is open (intended: pick first, second Enter sends).
