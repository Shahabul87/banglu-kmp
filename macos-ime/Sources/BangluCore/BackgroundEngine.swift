import Foundation
import os

/// S108: how the engine boot ended. `loading` is transient; the other three
/// are terminal for the process lifetime.
public enum EngineBootState: Sendable {
    case loading
    /// Full slim dictionary attached.
    case ready
    /// Engine alive on seeds only — the slim was missing/stale/corrupt.
    case seedOnly(String)
    /// EngineJS itself failed (bundle missing/broken): convert echoes raw.
    case failed(String)
}

/// Boots EngineJS on a background queue (slim-dictionary parse ≈ 11s — Task 2
/// measurement). Until ready, convert echoes raw and suggestions are empty
/// (the Android S29 cold-start pattern). After ready, all JSC access is
/// serialized through the owning queue — JSContext is not thread-safe.
///
/// S108: boot failure is no longer silent. The old `try?` swallowed every
/// init error and flipped `ready` anyway, so a broken bundle produced a
/// permanent raw-English echo indistinguishable from "keyboard broken" —
/// no log, no retry, no user signal. Failures now land in [state], are
/// logged via os.Logger, and [bootFailureNotice] gives the controller a
/// one-line Bengali notice to surface.
public final class BackgroundEngine: BangluEngine {
    private let queue = DispatchQueue(label: "com.banglu.engine", qos: .userInitiated)
    private var impl: EngineJS?          // written only on `queue`
    private let stateLock = OSAllocatedUnfairLock<EngineBootState>(initialState: .loading)
    private static let log = Logger(subsystem: "com.banglu.inputmethod", category: "engine")

    public init(bundleJS: URL, slimJSON: URL?, learnedJSON: String?) {
        queue.async { [self] in
            do {
                let e = try EngineJS(bundleJS: bundleJS, slimJSON: slimJSON)
                if let json = learnedJSON { e.applyLearnedWords(json: json) }
                impl = e
                if let reason = e.slimAttachError {
                    Self.log.error("slim dictionary rejected — seed-only: \(reason, privacy: .public)")
                    stateLock.withLock { $0 = .seedOnly(reason) }
                } else {
                    Self.log.info("engine ready (full dictionary)")
                    stateLock.withLock { $0 = .ready }
                }
            } catch {
                let reason = String(describing: error)
                Self.log.fault("engine boot FAILED — raw echo: \(reason, privacy: .public)")
                impl = nil
                stateLock.withLock { $0 = .failed(reason) }
            }
        }
    }

    public var state: EngineBootState { stateLock.withLock { $0 } }

    /// Boot finished with a usable engine (full or seed-only). Kept as the
    /// existing pre-ready/raw-echo contract the controller and tests rely on;
    /// a FAILED boot never becomes ready.
    public var ready: Bool {
        switch state {
        case .ready, .seedOnly: return true
        case .loading, .failed: return false
        }
    }

    /// Non-nil when the engine is PERMANENTLY degraded (not while loading).
    /// The controller shows this once in the candidate panel.
    public var bootFailureNotice: String? {
        switch state {
        case .failed:
            return "বাংলু ইঞ্জিন চালু হয়নি — অক্ষর হুবহু যাবে। বাংলু পুনরায় ইনস্টল করুন।"
        case .seedOnly:
            return "বাংলু সীমিত অভিধানে চলছে — পূর্ণ অভিধান লোড হয়নি।"
        case .loading, .ready:
            return nil
        }
    }

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
