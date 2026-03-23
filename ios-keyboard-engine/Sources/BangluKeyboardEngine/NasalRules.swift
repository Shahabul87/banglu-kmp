import Foundation

/// Nasal consonant resolution: ং vs ঙ
public enum NasalRules {
    /// Resolve "ng" — returns ঙ before g/vowel, otherwise ং
    public static func resolve(_ nextPhoneticChar: Character?) -> Character {
        guard let next = nextPhoneticChar else { return "ং" }
        let lower = next.lowercased().first ?? next
        if lower == "g" { return "ঙ" }
        if "aeiou".contains(lower) { return "ঙ" }
        return "ং"
    }
}
