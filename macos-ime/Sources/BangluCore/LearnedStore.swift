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
