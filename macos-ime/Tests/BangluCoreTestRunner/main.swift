import Foundation
import BangluCore

// S51: plain executable test runner — the test gate on Command-Line-Tools-only
// machines (no Xcode → no XCTest.framework, and the CLT swift-testing helper
// silently discovers zero tests). Mirrors the assertions in
// Tests/BangluCoreTests/EngineJSTests.swift — keep the two in sync.
// Precedent: ios-keyboard-engine/Tests/RunTests/main.swift.
// Gate: `swift run BangluCoreTestRunner` (exit 1 on any failure).

var failures = 0
var checkCount = 0

func check(_ name: String, _ condition: Bool, _ detail: @autoclosure () -> String = "") {
    checkCount += 1
    if condition {
        print("PASS \(name)")
    } else {
        failures += 1
        let d = detail()
        print("FAIL \(name)\(d.isEmpty ? "" : ": \(d)")")
    }
}

func engineArtifacts() -> (bundle: URL, slim: URL)? {
    let env = ProcessInfo.processInfo.environment
    let root = URL(fileURLWithPath: #filePath)          // …/Tests/BangluCoreTestRunner/…
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
    let bundle = env["BANGLU_JS_BUNDLE"].map(URL.init(fileURLWithPath:))
        ?? root.appendingPathComponent("Resources/built/banglu-engine.bundle.js")
    let slim = env["BANGLU_SLIM_JSON"].map(URL.init(fileURLWithPath:))
        ?? root.appendingPathComponent("Resources/built/banglu-slim.json")
    guard FileManager.default.fileExists(atPath: bundle.path) else { return nil }
    return (bundle, slim)
}

guard let artifacts = engineArtifacts() else {
    print("SKIP engine bundle missing — run macos-ime/scripts/build-engine.sh first")
    exit(1)
}

let engine: EngineJS
let loadStart = Date()
do {
    engine = try EngineJS(bundleJS: artifacts.bundle, slimJSON: artifacts.slim)
} catch {
    print("FAIL EngineJS init: \(error)")
    exit(1)
}
print("engine loaded in \(String(format: "%.1f", Date().timeIntervalSince(loadStart)))s")

// testConvertsThroughTheRealSlimDictionary
let kemon = engine.convert("kemon")
check("convertsThroughTheRealSlimDictionary.convert", kemon == "কেমন", "got \(kemon)")
let suggs = engine.suggestions("kemon", limit: 6)
check("convertsThroughTheRealSlimDictionary.suggestions", suggs.contains("কেমন"), "got \(suggs)")

// testRecordPickTeaches
// plausible pair (passes isPlausibleDynamicMapping): reverse(দেখবো) ≈ "dekhbo"
engine.recordPick(raw: "dkhbo", bangla: "দেখবো")
let taught = engine.convert("dkhbo")
check("recordPickTeaches", taught == "দেখবো", "got \(taught)")

// S51 Task 3: Composer — transposed from the brief's XCTest files
// (Tests/BangluCoreTests/ComposerTests.swift + ComposerParityTests.swift)
// into this runner, per the same "swift test is broken here" rationale as
// EngineJSTests above. FakeEngine lives in FakeEngine.swift (same target).

func typeInto(_ c: Composer, _ s: String) -> [ComposerAction] {
    var out: [ComposerAction] = []
    for ch in s {
        if ch == " " { out += c.handle(.space) }
        else if ch.isLetter { out += c.handle(.letter(ch)) }
    }
    return out
}

// testLettersFormAndSpaceCommitsWYSIWYG
do {
    let e = FakeEngine(); e.table["ami"] = ("আমি", ["আমই"])
    let c = Composer(engine: e)
    _ = c.handle(.letter("a")); _ = c.handle(.letter("m"))
    let last = c.handle(.letter("i"))
    check("Composer.lettersFormAndSpaceCommitsWYSIWYG.lastSetMarked", last.contains(.setMarked("আমি")), "got \(last)")
    let commit = c.handle(.space)
    check("Composer.lettersFormAndSpaceCommitsWYSIWYG.commitsWord", commit.contains(.commit("আমি")), "got \(commit)")
    check("Composer.lettersFormAndSpaceCommitsWYSIWYG.clearsMarked", commit.contains(.setMarked("")), "got \(commit)")
    check("Composer.lettersFormAndSpaceCommitsWYSIWYG.formingRawEmpty", c.formingRaw == "", "got \(c.formingRaw)")
}

// testPendingSpaceDari
do {
    let e = FakeEngine(); e.table["kemon"] = ("কেমন", [])
    let c = Composer(engine: e)
    _ = typeInto(c, "kemon")
    _ = c.handle(.space)                                  // commit কেমন, hold space
    let dari = c.handle(.space)                           // second space
    check("Composer.pendingSpaceDari.secondSpaceIsDari", dari.contains(.commit("। ")), "got \(dari)")
    let third = c.handle(.space)                          // third: plain space held→
    check("Composer.pendingSpaceDari.thirdSpaceIsNotDari", !third.contains(.commit("। ")), "got \(third)")
}

// testLetterAfterPendingSpaceReleasesIt
do {
    let e = FakeEngine(); e.table["ami"] = ("আমি", []); e.table["k"] = ("ক", [])
    let c = Composer(engine: e)
    _ = typeInto(c, "ami")
    _ = c.handle(.space)
    let next = c.handle(.letter("k"))
    check("Composer.letterAfterPendingSpaceReleasesIt.releasesHeldSpace", next.contains(.commit(" ")), "got \(next)")
    check("Composer.letterAfterPendingSpaceReleasesIt.newWordForms", next.contains(.setMarked("ক")), "got \(next)")
}

// testTightPunctuationSwallowsPendingSpace
do {
    let e = FakeEngine(); e.table["kotha"] = ("কথা", [])
    let c = Composer(engine: e)
    _ = typeInto(c, "kotha")
    _ = c.handle(.space)
    let comma = c.handle(.punctuation(","))
    check("Composer.tightPunctuationSwallowsPendingSpace.commitsComma", comma.contains(.commit(",")), "got \(comma)")
    check("Composer.tightPunctuationSwallowsPendingSpace.swallowsSpace", !comma.contains(.commit(" ")), "got \(comma)")
}

// testBackspaceEditsRawThenPassesThrough
do {
    let e = FakeEngine(); e.table["kal"] = ("কাল", []); e.table["kali"] = ("কালি", [])
    let c = Composer(engine: e)
    _ = typeInto(c, "kali")
    let bs = c.handle(.backspace)
    check("Composer.backspaceEditsRawThenPassesThrough.setMarkedAfterOneBackspace", bs.contains(.setMarked("কাল")), "got \(bs)")
    check("Composer.backspaceEditsRawThenPassesThrough.formingRawAfterOneBackspace", c.formingRaw == "kal", "got \(c.formingRaw)")
    _ = c.handle(.backspace); _ = c.handle(.backspace); _ = c.handle(.backspace)
    check("Composer.backspaceEditsRawThenPassesThrough.formingRawEmptied", c.formingRaw == "", "got \(c.formingRaw)")
    let idle = c.handle(.backspace)
    check("Composer.backspaceEditsRawThenPassesThrough.idleBackspacePassesThrough", idle.contains(.passThrough), "got \(idle)")
}

// testEscapeCommitsRawEnglish
do {
    let e = FakeEngine(); e.table["kali"] = ("কালি", [])
    let c = Composer(engine: e)
    _ = typeInto(c, "kali")
    let esc = c.handle(.escape)
    check("Composer.escapeCommitsRawEnglish.commitsRaw", esc.contains(.commit("kali")), "got \(esc)")
    check("Composer.escapeCommitsRawEnglish.clearsMarked", esc.contains(.setMarked("")), "got \(esc)")
}

// testReturnCommitsFormingThenPassesThrough
do {
    let e = FakeEngine(); e.table["hobe"] = ("হবে", [])
    let c = Composer(engine: e)
    _ = typeInto(c, "hobe")
    let ret = c.handle(.returnKey)
    check("Composer.returnCommitsFormingThenPassesThrough.commitsWord", ret.contains(.commit("হবে")), "got \(ret)")
    check("Composer.returnCommitsFormingThenPassesThrough.passesThrough", ret.contains(.passThrough), "got \(ret)")
}

// testFocusLostCommitsVisibleWord
do {
    let e = FakeEngine(); e.table["adh"] = ("আধ", [])
    let c = Composer(engine: e)
    _ = typeInto(c, "adh")
    let out = c.focusLost()
    check("Composer.focusLostCommitsVisibleWord.commitsWord", out.contains(.commit("আধ")), "got \(out)")
    check("Composer.focusLostCommitsVisibleWord.formingRawEmpty", c.formingRaw == "", "got \(c.formingRaw)")
}

// testPlainModeSuppressesMarkedTextOnly
do {
    let e = FakeEngine(); e.table["ami"] = ("আমি", ["আমই"])
    let c = Composer(engine: e, plainMode: true)
    let a = c.handle(.letter("a"))
    let hasNonEmptyMarked = a.contains { if case .setMarked(let s) = $0 { return !s.isEmpty } else { return false } }
    check("Composer.plainModeSuppressesMarkedTextOnly.noMarkedTextEmitted", !hasNonEmptyMarked, "got \(a)")
    _ = c.handle(.letter("m")); _ = c.handle(.letter("i"))
    check("Composer.plainModeSuppressesMarkedTextOnly.candidatesStillLive", !c.candidates.isEmpty, "got \(c.candidates)")
    let commit = c.handle(.space)
    check("Composer.plainModeSuppressesMarkedTextOnly.commitIdenticalToFullMode", commit.contains(.commit("আমি")), "got \(commit)")
}

// periodSwallowsPendingSpace (reviewer round): period after a committed word
// must be tight: word␣. → শব্দ। (no space before dari). Guards the mapped-first
// swallow — membership must test "।", not the raw ".".
do {
    let e = FakeEngine(); e.table["shobdo"] = ("শব্দ", [])
    let c = Composer(engine: e)
    for ch in "shobdo" { _ = c.handle(.letter(ch)) }
    _ = c.handle(.space)                                  // commit শব্দ, hold space
    let out = c.handle(.punctuation("."))
    check("Composer.periodSwallowsPendingSpace.noSpace", !out.contains(.commit(" ")), "got \(out)")
    check("Composer.periodSwallowsPendingSpace.dari", out.contains(.commit("।")), "got \(out)")
    let next = c.handle(.space)                           // dariJustCommitted set → this space is plain
    check("Composer.periodSwallowsPendingSpace.plainSpaceAfter", next.contains(.commit(" ")), "got \(next)")
    check("Composer.periodSwallowsPendingSpace.noDoubleDari", !next.contains(.commit("। ")), "got \(next)")
}

// ComposerParityTests.testCommittedEqualsShownMarkedText — real engine, WYSIWYG
// pin. Reuses `engine` (already loaded above) instead of a fresh TestSupport,
// so the ~11s slim-dictionary load happens once for the whole runner.
let parityPhrases = [
    "kemon acho bondhu",
    "ami bangla likhi",
    "issa korche golpo bolte",
    "bujte parcina keno",
    "kalke dekha hobe",
]
for phrase in parityPhrases {
    let c = Composer(engine: engine)
    var committed = ""
    var lastMarked = ""
    for ch in phrase {
        let actions: [ComposerAction] = ch == " " ? c.handle(.space) : c.handle(.letter(ch))
        for a in actions {
            if case .setMarked(let m) = a, !m.isEmpty { lastMarked = m }
            if case .commit(let t) = a {
                if t != " " && t != "। " {
                    check("ComposerParity.\"\(phrase)\".commitMatchesShownMarked(\(t))", t == lastMarked, "committed \(t) != lastMarked \(lastMarked)")
                }
                committed += t
            }
        }
    }
    _ = c.handle(.space)   // flush last word
    check("ComposerParity.\"\(phrase)\".committedNonEmpty", !committed.isEmpty, "committed was empty")
}

print("\(checkCount) checks: \(failures == 0 ? "ALL TESTS PASSED" : "\(failures) FAILURE(S)")")
exit(failures == 0 ? 0 : 1)
