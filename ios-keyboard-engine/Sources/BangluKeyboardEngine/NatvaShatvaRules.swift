import Foundation

/// Combined NatvaVidhan (ন/ণ) and ShatvaVidhan (শ/ষ/স) rules.
public enum NatvaShatvaRules {

    // MARK: - NatvaVidhan

    /// Characters that trigger ন → ণ
    private static let triggers: Set<Character> = ["ঋ", "ৃ", "র", "ষ"]

    /// Characters that block the ণ transformation
    private static let blockers: Set<Character> = [
        "ত", "থ", "দ", "ধ", "ন", "চ", "ছ", "জ", "ঝ", "ঞ",
        "ট", "ঠ", "ড", "ঢ", "শ", "স", "ল",
    ]

    /// Characters that are transparent (don't trigger or block)
    private static let transparent: Set<Character> = [
        "ক", "খ", "গ", "ঘ", "ঙ", "প", "ফ", "ব", "ভ", "ম",
        "য", "হ", "ং", "ঁ",
        "অ", "আ", "ই", "ঈ", "উ", "ঊ", "ঋ", "এ", "ঐ", "ও", "ঔ",
        "া", "ি", "ী", "ু", "ূ", "ৃ", "ে", "ৈ", "ো", "ৌ", "্",
    ]

    // MARK: - ShatvaVidhan

    private static let riVowelChars: [Character] = ["ঋ", "ৃ"]

    private static let triggeringPrefixes: [(phonetic: String, bengali: String)] = [
        ("ni", "নি"), ("bi", "বি"), ("pori", "পরি"), ("pari", "পরি"),
        ("proti", "প্রতি"), ("prati", "প্রতি"), ("obhi", "অভি"), ("abhi", "অভি"),
        ("su", "সু"), ("onu", "অনু"), ("anu", "অনু"), ("upo", "উপ"), ("upa", "উপ"),
        ("apo", "অপ"), ("opo", "অপ"), ("pri", "প্রি"), ("tri", "ত্রি"), ("duri", "দূরী"),
    ]

    // MARK: - Public API

    /// NatvaVidhan: Should ন become ণ based on preceding Bengali context?
    public static func shouldBeRetroflex(_ bengaliContext: String) -> Bool {
        guard !bengaliContext.isEmpty else { return false }
        for ch in bengaliContext.reversed() {
            if triggers.contains(ch) { return true }
            if blockers.contains(ch) { return false }
            if transparent.contains(ch) { continue }
            return false
        }
        return false
    }

    /// ShatvaVidhan: Resolve শ/ষ/স based on context.
    public static func resolveSibilant(
        bengaliContext: String,
        phoneticWord: String = "",
        sibilantIndex: Int = 0
    ) -> (bengali: Character, confidence: Double) {
        // After ঋ/ৃ -> ষ
        if let last = bengaliContext.last, riVowelChars.contains(last) {
            return ("ষ", 0.85)
        }
        // After র্ -> ষ
        if bengaliContext.count >= 2 {
            let chars = Array(bengaliContext)
            if chars[chars.count - 2] == "র" && chars[chars.count - 1] == "্" {
                return ("ষ", 0.85)
            }
        }
        // Prefix match -> ষ
        let lower = phoneticWord.lowercased()
        for prefix in triggeringPrefixes {
            if lower.hasPrefix(prefix.phonetic) && lower.count > prefix.phonetic.count {
                let remaining = String(lower.dropFirst(prefix.phonetic.count))
                if remaining.hasPrefix("s") || remaining.hasPrefix("sh") {
                    return ("ষ", 0.70)
                }
            }
        }
        // Position-based defaults
        if sibilantIndex == 0 { return ("শ", 0.80) }  // Word-initial
        let sibilantLen = (sibilantIndex + 1 < phoneticWord.count &&
            Array(phoneticWord)[sibilantIndex + 1] == "h") ? 2 : 1
        if sibilantIndex + sibilantLen >= phoneticWord.count { return ("শ", 0.65) }  // Word-final
        return ("শ", 0.55)  // Default medial
    }
}
