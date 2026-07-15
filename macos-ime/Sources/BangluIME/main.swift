import Cocoa
import InputMethodKit
import BangluCore

/// Process-wide singletons: ONE JSC engine + store, shared by every
/// controller instance (IMK creates one controller per client app).
///
/// `engine` boots on a background queue (BackgroundEngine) rather than a
/// lazy `EngineJS` on first access: the slim-dictionary parse measured
/// ~11.4s (Task 2), which would freeze the very first keystroke if done
/// synchronously here. Until BackgroundEngine.ready flips, conversion
/// echoes raw input and suggestions are empty — the Android S29 cold-start
/// pattern.
enum AppState {
    static let store = LearnedStore(
        directory: FileManager.default.homeDirectoryForCurrentUser
            .appendingPathComponent(".banglu"))
    static let compat = AppCompat(
        tableURL: Bundle.main.url(forResource: "appcompat", withExtension: "json"))
    static let engine: BackgroundEngine = {
        let bundleJS = Bundle.main.url(forResource: "banglu-engine.bundle", withExtension: "js")
        let slim = Bundle.main.url(forResource: "banglu-slim", withExtension: "json")
        return BackgroundEngine(
            bundleJS: bundleJS ?? URL(fileURLWithPath: "/nonexistent"),
            slimJSON: slim,
            learnedJSON: store.learnedJSON())
    }()

    /// Re-read editor learnings on every input-source activation (spec §7).
    /// Safe to call before the engine is ready — BackgroundEngine queues the
    /// apply and it lands once the JSC context exists.
    static func reloadLearned() {
        if let json = store.learnedJSON() { engine.applyLearnedWords(json: json) }
    }
}

guard let connectionName = Bundle.main.infoDictionary?["InputMethodConnectionName"] as? String
else { fatalError("InputMethodConnectionName missing from Info.plist") }

// Retained for the process lifetime — macOS connects clients through it.
let server = IMKServer(name: connectionName, bundleIdentifier: Bundle.main.bundleIdentifier!)
NSApplication.shared.run()
