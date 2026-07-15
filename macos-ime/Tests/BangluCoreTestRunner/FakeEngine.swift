import Foundation
import BangluCore

// S51 Task 3: transposed from the brief's XCTest FakeEngine
// (Tests/BangluCoreTests/FakeEngine.swift in the brief) into the runner
// target — this machine's `swift test` gate is broken (see main.swift).

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
