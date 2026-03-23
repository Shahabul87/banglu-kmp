import Foundation

/// Expands non-standard user input to canonical phonetic forms.
/// e.g. "gg" -> "gy", "ia" -> "iya", "sh" -> "s" (initial only)
public enum TypingHabitNormalizer {

    private struct Rule {
        let pattern: String
        let replacements: [String]
        let context: String  // "initial" or "medial"
        let priority: Int
    }

    private static let rules: [Rule] = [
        Rule(pattern: "gg", replacements: ["gy", "gn"], context: "medial", priority: 90),
        Rule(pattern: "dd", replacements: ["dy", "ddh", "dv"], context: "medial", priority: 90),
        Rule(pattern: "ia", replacements: ["iya"], context: "medial", priority: 85),
        Rule(pattern: "nn", replacements: ["ny", "nno"], context: "medial", priority: 80),
        Rule(pattern: "ua", replacements: ["uya", "owa"], context: "medial", priority: 80),
        Rule(pattern: "ie", replacements: ["iye"], context: "medial", priority: 80),
        Rule(pattern: "ue", replacements: ["uye"], context: "medial", priority: 75),
        Rule(pattern: "io", replacements: ["iyo"], context: "medial", priority: 75),
        Rule(pattern: "cc", replacements: ["cch", "chy"], context: "medial", priority: 70),
        Rule(pattern: "bb", replacements: ["by", "bv"], context: "medial", priority: 70),
        Rule(pattern: "sh", replacements: ["s"], context: "initial", priority: 60),
        Rule(pattern: "pp", replacements: ["py", "pn"], context: "medial", priority: 60),
        Rule(pattern: "mm", replacements: ["my", "mn"], context: "medial", priority: 60),
        Rule(pattern: "ng", replacements: ["nk", "nkh"], context: "medial", priority: 40),
    ].sorted { $0.priority > $1.priority }

    public static func expand(_ input: String, maxVariants: Int = 8) -> [String] {
        let key = input.lowercased().trimmingCharacters(in: .whitespaces)
        guard key.count >= 3 else { return [key] }

        var results: Set<String> = [key]
        for rule in rules {
            guard results.count < maxVariants else { break }
            guard let range = key.range(of: rule.pattern) else { continue }
            let idx = key.distance(from: key.startIndex, to: range.lowerBound)
            if rule.context == "initial" && idx != 0 { continue }
            if rule.context == "medial" && idx == 0 { continue }

            for replacement in rule.replacements {
                guard results.count < maxVariants else { break }
                let variant = key.replacingCharacters(in: range, with: replacement)
                if !variant.isEmpty && variant != key && variant.count >= 2 {
                    results.insert(variant)
                }
            }
        }
        return Array(results)
    }
}
