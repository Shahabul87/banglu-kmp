import Foundation

public struct ConjunctMatch: Sendable {
    public let bengali: String
    public let consumed: Int

    public init(bengali: String, consumed: Int) {
        self.bengali = bengali
        self.consumed = consumed
    }
}

/// Locked sibilant conjunct patterns from ConjunctResolver.
/// These are matched before the general ConjunctTable and handle
/// the sh/s/shch cluster rules that need special ordering.
public enum ConjunctRules {
    // Ordered longest-first for greedy matching
    private static let conjunctMap: [(phonetic: String, bengali: String)] = [
        // ষ conjuncts
        ("shtr", "\u{09B7}\u{09CD}\u{099F}\u{09CD}\u{09B0}"),  // ষ্ট্র
        ("shth", "\u{09B7}\u{09CD}\u{09A0}"),  // ষ্ঠ
        ("shph", "\u{09B7}\u{09CD}\u{09AB}"),  // ষ্ফ
        ("sht", "\u{09B7}\u{09CD}\u{099F}"),   // ষ্ট
        ("shn", "\u{09B7}\u{09CD}\u{09A3}"),   // ষ্ণ
        ("shk", "\u{09B7}\u{09CD}\u{0995}"),   // ষ্ক
        ("shp", "\u{09B7}\u{09CD}\u{09AA}"),   // ষ্প
        ("shm", "\u{09B7}\u{09CD}\u{09AE}"),   // ষ্ম
        // স conjuncts
        ("str", "\u{09B8}\u{09CD}\u{09A4}\u{09CD}\u{09B0}"),  // স্ত্র
        ("sth", "\u{09B8}\u{09CD}\u{09A5}"),  // স্থ
        ("sph", "\u{09B8}\u{09CD}\u{09AB}"),  // স্ফ
        ("st", "\u{09B8}\u{09CD}\u{09A4}"),   // স্ত
        ("sn", "\u{09B8}\u{09CD}\u{09A8}"),   // স্ন
        ("sk", "\u{09B8}\u{09CD}\u{0995}"),   // স্ক
        ("sp", "\u{09B8}\u{09CD}\u{09AA}"),   // স্প
        ("sm", "\u{09B8}\u{09CD}\u{09AE}"),   // স্ম
        ("sl", "\u{09B8}\u{09CD}\u{09B2}"),   // স্ল
        ("sr", "\u{09B8}\u{09CD}\u{09B0}"),   // স্র
        ("sw", "\u{09B8}\u{09CD}\u{09AC}"),   // স্ব
        ("sb", "\u{09B8}\u{09CD}\u{09AC}"),   // স্ব
        // শ conjuncts
        ("shchh", "\u{09B6}\u{09CD}\u{099B}"),  // শ্ছ
        ("shch", "\u{09B6}\u{09CD}\u{099A}"),   // শ্চ
        ("shr", "\u{09B6}\u{09CD}\u{09B0}"),    // শ্র
        ("shl", "\u{09B6}\u{09CD}\u{09B2}"),    // শ্ল
        ("shb", "\u{09B6}\u{09CD}\u{09AC}"),    // শ্ব
        ("shw", "\u{09B6}\u{09CD}\u{09AC}"),    // শ্ব
        // ক্ষ
        ("kkh", "\u{0995}\u{09CD}\u{09B7}"),  // ক্ষ
        ("ksh", "\u{0995}\u{09CD}\u{09B7}"),  // ক্ষ
    ]

    public static func matchAt(_ input: String, position: Int) -> ConjunctMatch? {
        let startIdx = input.index(input.startIndex, offsetBy: position)
        let remaining = String(input[startIdx...]).lowercased()

        for entry in conjunctMap {
            if remaining.hasPrefix(entry.phonetic) {
                return ConjunctMatch(bengali: entry.bengali, consumed: entry.phonetic.count)
            }
        }
        return nil
    }

    public static func resolve(_ pattern: String) -> String? {
        let lower = pattern.lowercased()
        return conjunctMap.first { $0.phonetic == lower }?.bengali
    }
}
