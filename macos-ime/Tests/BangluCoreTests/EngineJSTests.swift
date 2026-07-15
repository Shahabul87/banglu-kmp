import XCTest
@testable import BangluCore

// NOTE: this XCTest target only runs on machines with Xcode installed
// (XCTest.framework does not ship with Command Line Tools). On CLT-only
// machines the gate is `swift run BangluCoreTestRunner`, which executes the
// same assertions — keep the two in sync.

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
        // plausible pair (passes isPlausibleDynamicMapping): reverse(দেখবো) ≈ "dekhbo"
        TestSupport.shared.recordPick(raw: "dkhbo", bangla: "দেখবো")
        XCTAssertEqual(TestSupport.shared.convert("dkhbo"), "দেখবো")
    }
}
