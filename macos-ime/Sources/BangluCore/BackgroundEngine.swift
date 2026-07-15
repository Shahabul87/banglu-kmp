import Foundation
import os

/// Boots EngineJS on a background queue (slim-dictionary parse ≈ 11s — Task 2
/// measurement). Until ready, convert echoes raw and suggestions are empty
/// (the Android S29 cold-start pattern). After ready, all JSC access is
/// serialized through the owning queue — JSContext is not thread-safe.
public final class BackgroundEngine: BangluEngine {
    private let queue = DispatchQueue(label: "com.banglu.engine", qos: .userInitiated)
    private var impl: EngineJS?          // written only on `queue`
    private let readyLock = OSAllocatedUnfairLock(initialState: false)

    public init(bundleJS: URL, slimJSON: URL?, learnedJSON: String?) {
        queue.async { [self] in
            let e = try? EngineJS(bundleJS: bundleJS, slimJSON: slimJSON)
            if let json = learnedJSON { e?.applyLearnedWords(json: json) }
            impl = e
            readyLock.withLock { $0 = true }
        }
    }

    public var ready: Bool { readyLock.withLock { $0 } }

    public func convert(_ raw: String) -> String {
        guard ready else { return raw }
        return queue.sync { impl?.convert(raw) ?? raw }
    }

    public func suggestions(_ raw: String, limit: Int) -> [String] {
        guard ready else { return [] }
        return queue.sync { impl?.suggestions(raw, limit: limit) ?? [] }
    }

    public func recordPick(raw: String, bangla: String) {
        queue.async { [self] in impl?.recordPick(raw: raw, bangla: bangla) }
    }

    public func applyLearnedWords(json: String) {
        queue.async { [self] in impl?.applyLearnedWords(json: json) }
    }
}
