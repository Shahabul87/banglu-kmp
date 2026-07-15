import Foundation
import BangluCore

// S51: plain executable test runner — the test gate on Command-Line-Tools-only
// machines (no Xcode → no XCTest.framework, and the CLT swift-testing helper
// silently discovers zero tests). Mirrors the assertions in
// Tests/BangluCoreTests/EngineJSTests.swift — keep the two in sync.
// Precedent: ios-keyboard-engine/Tests/RunTests/main.swift.
// Gate: `swift run BangluCoreTestRunner` (exit 1 on any failure).

var failures = 0

func check(_ name: String, _ condition: Bool, _ detail: @autoclosure () -> String = "") {
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

print(failures == 0 ? "ALL TESTS PASSED" : "\(failures) FAILURE(S)")
exit(failures == 0 ? 0 : 1)
