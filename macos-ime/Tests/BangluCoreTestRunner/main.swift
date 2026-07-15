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

// S51 Task 4: Composer — digit picks + learning law tests
// Transposed from the brief's XCTest files (Tests/BangluCoreTests/ComposerTests.swift)
// into this runner, following the same pattern as Task 3 tests above.

// testDigitPicksCandidateAndReportsNonPrimary
do {
    let e = FakeEngine(); e.table["taka"] = ("টাকা", ["তাকা"])
    let c = Composer(engine: e)
    var reported: (String, String, Bool)?
    c.onPick = { reported = ($0, $1, $2) }
    _ = typeInto(c, "taka")
    check("Composer.digitPicksCandidateAndReportsNonPrimary.candidates", c.candidates == ["টাকা", "তাকা", "taka"], "got \(c.candidates)")
    let out = c.handle(.digit("2"))                       // pick তাকা (index 1)
    check("Composer.digitPicksCandidateAndReportsNonPrimary.commit", out.contains(.commit("তাকা")), "got \(out)")
    check("Composer.digitPicksCandidateAndReportsNonPrimary.reportedRaw", reported?.0 == "taka", "got \(reported?.0 ?? "nil")")
    check("Composer.digitPicksCandidateAndReportsNonPrimary.reportedBangla", reported?.1 == "তাকা", "got \(reported?.1 ?? "nil")")
    check("Composer.digitPicksCandidateAndReportsNonPrimary.wasPrimary", reported?.2 == false, "got \(String(describing: reported?.2))")
}

// testPickingPrimaryReportsWasPrimary
do {
    let e = FakeEngine(); e.table["ami"] = ("আমি", ["আমই"])
    let c = Composer(engine: e)
    var wasPrimary: Bool?
    c.onPick = { wasPrimary = $2 }
    _ = typeInto(c, "ami")
    _ = c.handle(.digit("1"))
    check("Composer.pickingPrimaryReportsWasPrimary", wasPrimary == true, "got \(wasPrimary ?? false)")
}

// testOutOfRangeDigitTypesBengaliNumeral
do {
    let e = FakeEngine(); e.table["k"] = ("ক", [])        // 2 candidates: ক + raw k
    let c = Composer(engine: e)
    _ = c.handle(.letter("k"))
    check("Composer.outOfRangeDigitTypesBengaliNumeral.candidateCount", c.candidates.count == 2, "got \(c.candidates.count)")
    let out = c.handle(.digit("5"))                       // beyond list → digit
    check("Composer.outOfRangeDigitTypesBengaliNumeral.commitFormed", out.contains(.commit("ক")), "got \(out)")
    check("Composer.outOfRangeDigitTypesBengaliNumeral.commitDigit", out.contains(.commit("৫")), "got \(out)")
}

// testIdleDigitTypesBengaliNumeral
do {
    let e = FakeEngine()
    let c = Composer(engine: e)
    let out = c.handle(.digit("3"))
    check("Composer.idleDigitTypesBengaliNumeral", out.contains(.commit("৩")), "got \(out)")
}

// testArrowsMoveHighlightAndWrap
do {
    let e = FakeEngine(); e.table["dan"] = ("দান", ["ডান"])
    let c = Composer(engine: e)
    _ = typeInto(c, "dan")                                    // 3 rows incl raw
    _ = c.handle(.arrowDown); check("Composer.arrowsMoveHighlightAndWrap.down1", c.highlight == 1, "got \(c.highlight)")
    _ = c.handle(.arrowDown); check("Composer.arrowsMoveHighlightAndWrap.down2", c.highlight == 2, "got \(c.highlight)")
    _ = c.handle(.arrowDown); check("Composer.arrowsMoveHighlightAndWrap.wrap", c.highlight == 0, "got \(c.highlight)")
    _ = c.handle(.arrowUp);   check("Composer.arrowsMoveHighlightAndWrap.up", c.highlight == 2, "got \(c.highlight)")
}

// testPickAfterArrowsCommitsHighlighted
do {
    let e = FakeEngine(); e.table["pore"] = ("পরে", ["পড়ে"])
    let c = Composer(engine: e)
    _ = typeInto(c, "pore")
    _ = c.handle(.arrowDown)
    let out = c.pick(c.highlight)
    check("Composer.pickAfterArrowsCommitsHighlighted.commit", out.contains(.commit("পড়ে")), "got \(out)")
    check("Composer.pickAfterArrowsCommitsHighlighted.formingRawEmpty", c.formingRaw.isEmpty, "got \(c.formingRaw)")
}

// S51 Task 5: LearnedStore + AppCompat — transposed from the brief's XCTest
// file (Tests/BangluCoreTests/LearnedStoreTests.swift) into this runner,
// same "swift test is broken here" rationale as Tasks 3/4 above.

func learnedStoreTempDir() -> URL {
    let d = FileManager.default.temporaryDirectory
        .appendingPathComponent("banglu-test-\(UUID().uuidString)")
    try! FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
    return d
}

// testRecordPickWritesEditorCompatibleRow
do {
    let store = LearnedStore(directory: learnedStoreTempDir())
    store.recordPick(raw: "Korsi ", bangla: "করছি")
    let data = try! Data(contentsOf: store.learnedFileURL)
    let rows = try! JSONSerialization.jsonObject(with: data) as! [[String: Any]]
    check("LearnedStore.recordPickWritesEditorCompatibleRow.count", rows.count == 1, "got \(rows.count)")
    check("LearnedStore.recordPickWritesEditorCompatibleRow.p", rows[0]["p"] as? String == "korsi", "got \(String(describing: rows[0]["p"]))")
    check("LearnedStore.recordPickWritesEditorCompatibleRow.b", rows[0]["b"] as? String == "করছি", "got \(String(describing: rows[0]["b"]))")
    check("LearnedStore.recordPickWritesEditorCompatibleRow.f", rows[0]["f"] as? Int == 94, "got \(String(describing: rows[0]["f"]))")
    check("LearnedStore.recordPickWritesEditorCompatibleRow.t", rows[0]["t"] != nil, "t was nil")
}

// testRepeatPickBumpsFrequencyNotDuplicates
do {
    let store = LearnedStore(directory: learnedStoreTempDir())
    store.recordPick(raw: "korsi", bangla: "করছি")
    store.recordPick(raw: "korsi", bangla: "করছি")
    let rows = try! JSONSerialization.jsonObject(with: try! Data(contentsOf: store.learnedFileURL)) as! [[String: Any]]
    check("LearnedStore.repeatPickBumpsFrequencyNotDuplicates.count", rows.count == 1, "got \(rows.count)")
    check("LearnedStore.repeatPickBumpsFrequencyNotDuplicates.f", rows[0]["f"] as? Int == 95, "got \(String(describing: rows[0]["f"]))")
}

// testMergesRowsWrittenByAnotherProcess
do {
    let dir = learnedStoreTempDir()
    let store = LearnedStore(directory: dir)
    store.recordPick(raw: "a", bangla: "আ")
    // The editor writes a row between our reads:
    let external = """
    [{"p":"a","b":"আ","f":94,"t":1},{"p":"editor","b":"এডিটর","f":94,"t":2}]
    """
    try! external.data(using: .utf8)!.write(to: store.learnedFileURL)
    store.recordPick(raw: "b", bangla: "ব")
    let rows = try! JSONSerialization.jsonObject(with: try! Data(contentsOf: store.learnedFileURL)) as! [[String: Any]]
    check("LearnedStore.mergesRowsWrittenByAnotherProcess.count", rows.count == 3, "got \(rows.count)") // read-fresh preserved "editor"
}

// testLearningEnabledDefaultsTrueAndPersistsWithoutClobbering
do {
    let dir = learnedStoreTempDir()
    // editor.json already has editor prefs — they must survive our write.
    try! """
    {"recent":["/x.txt"],"banglaDigits":false,"winW":900,"winH":700}
    """.data(using: .utf8)!.write(to: dir.appendingPathComponent("editor.json"))
    let store = LearnedStore(directory: dir)
    check("LearnedStore.learningEnabledDefaultsTrueAndPersistsWithoutClobbering.defaultsTrue", store.learningEnabled == true, "got \(store.learningEnabled)")
    store.setLearningEnabled(false)
    check("LearnedStore.learningEnabledDefaultsTrueAndPersistsWithoutClobbering.persists", LearnedStore(directory: dir).learningEnabled == false, "got \(LearnedStore(directory: dir).learningEnabled)")
    let prefs = try! JSONSerialization.jsonObject(with: try! Data(contentsOf: dir.appendingPathComponent("editor.json"))) as! [String: Any]
    check("LearnedStore.learningEnabledDefaultsTrueAndPersistsWithoutClobbering.winWUntouched", prefs["winW"] as? Int == 900, "got \(String(describing: prefs["winW"]))") // untouched
    check("LearnedStore.learningEnabledDefaultsTrueAndPersistsWithoutClobbering.banglaDigitsUntouched", prefs["banglaDigits"] as? Bool == false, "got \(String(describing: prefs["banglaDigits"]))")
}

// testCorruptFilesFallBackSafely
do {
    let dir = learnedStoreTempDir()
    try! "{not json".data(using: .utf8)!.write(to: dir.appendingPathComponent("learned.json"))
    let store = LearnedStore(directory: dir)
    let learnedRaw = store.learnedJSON()
    let effective: String? = (learnedRaw == "{not json") ? nil : learnedRaw
    check("LearnedStore.corruptFilesFallBackSafely.learnedJSONSafe", effective == nil, "got \(String(describing: effective))")
    store.recordPick(raw: "x", bangla: "ক্স") // must not crash; rewrites clean
    let rows = try! JSONSerialization.jsonObject(with: try! Data(contentsOf: store.learnedFileURL)) as! [[String: Any]]
    check("LearnedStore.corruptFilesFallBackSafely.rewritesClean", rows.count == 1, "got \(rows.count)")
}

// testAppCompatModes
do {
    let dir = learnedStoreTempDir()
    let table = dir.appendingPathComponent("appcompat.json")
    try! """
    {"plain": ["com.tinyspeck.slackmacgap"]}
    """.data(using: .utf8)!.write(to: table)
    let compat = AppCompat(tableURL: table)
    check("AppCompat.appCompatModes.notesIsFull", compat.mode(forBundleID: "com.apple.Notes") == .full, "got \(compat.mode(forBundleID: "com.apple.Notes"))")
    check("AppCompat.appCompatModes.slackIsPlain", compat.mode(forBundleID: "com.tinyspeck.slackmacgap") == .plain, "got \(compat.mode(forBundleID: "com.tinyspeck.slackmacgap"))")
    check("AppCompat.appCompatModes.nilIsFull", compat.mode(forBundleID: nil) == .full, "got \(compat.mode(forBundleID: nil))")
    check("AppCompat.appCompatModes.noTableIsFull", AppCompat(tableURL: nil).mode(forBundleID: "anything") == .full, "got \(AppCompat(tableURL: nil).mode(forBundleID: "anything"))")
}

print("\(checkCount) checks: \(failures == 0 ? "ALL TESTS PASSED" : "\(failures) FAILURE(S)")")
exit(failures == 0 ? 0 : 1)
