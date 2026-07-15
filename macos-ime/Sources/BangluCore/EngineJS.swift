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
