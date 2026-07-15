import Foundation

/// Per-app render mode (spec §6): `full` = marked text; `plain` = no marked
/// text, preview lives only in the candidate panel, commits via insertText.
public struct AppCompat {
    public enum Mode { case full, plain }
    private let plainIDs: Set<String>

    public init(tableURL: URL?) {
        guard let url = tableURL,
              let data = try? Data(contentsOf: url),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let list = obj["plain"] as? [String]
        else { plainIDs = []; return }
        plainIDs = Set(list)
    }

    public func mode(forBundleID id: String?) -> Mode {
        guard let id else { return .full }
        return plainIDs.contains(id) ? .plain : .full
    }
}
