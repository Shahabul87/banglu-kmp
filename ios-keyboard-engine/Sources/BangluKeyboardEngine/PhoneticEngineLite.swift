import Foundation

public struct ConversionResult: Sendable {
    public let bengali: String
    public let confidence: Double
    public let alternatives: [(bengali: String, confidence: Double)]

    public init(
        bengali: String,
        confidence: Double,
        alternatives: [(bengali: String, confidence: Double)] = []
    ) {
        self.bengali = bengali
        self.confidence = confidence
        self.alternatives = alternatives
    }
}

/// Lite 4-layer phonetic engine for iOS keyboard extension.
///
/// Layer 1: Dictionary lookup via CompactTrie
/// Layer 1.5: TypingHabitNormalizer variant expansion
/// Layers 2-4: Pattern conversion (ConjunctRules -> ConjunctTable -> character rules)
public class PhoneticEngineLite {
    private let trie = CompactTrie()
    private var initialized = false

    // Simple LRU cache (dictionary with size limit)
    private var cache: [String: ConversionResult] = [:]
    private let maxCacheSize = 500

    public init() {}

    public func initialize() {
        guard !initialized else { return }
        for entry in SeedData.dictionary {
            for phonetic in entry.phonetics {
                trie.insert(phonetic, entry.bengali, entry.frequency)
            }
        }
        initialized = true
    }

    public var isInitialized: Bool { initialized }

    public func convert(_ input: String) -> ConversionResult {
        let key = input.lowercased().trimmingCharacters(in: .whitespaces)
        guard !key.isEmpty else {
            return ConversionResult(bengali: "", confidence: 0.0)
        }

        // Check cache
        if let cached = cache[key] { return cached }

        // Layer 1: Dictionary lookup (exact match)
        let exactResults = trie.exactMatch(key)
        if !exactResults.isEmpty {
            let best = exactResults[0]
            let alternatives = exactResults.dropFirst().map {
                (bengali: $0.bengali, confidence: 0.85)
            }
            let result = ConversionResult(
                bengali: best.bengali,
                confidence: 0.95,
                alternatives: Array(alternatives)
            )
            cacheResult(key, result)
            return result
        }

        // Layer 1.5: Try typing habit variants
        let variants = TypingHabitNormalizer.expand(key)
        for variant in variants where variant != key {
            let varResults = trie.exactMatch(variant)
            if !varResults.isEmpty {
                let result = ConversionResult(
                    bengali: varResults[0].bengali,
                    confidence: 0.90
                )
                cacheResult(key, result)
                return result
            }
        }

        // Layers 2-4: Pattern conversion
        let result = convertByPatterns(key)
        cacheResult(key, result)
        return result
    }

    public func getSuggestions(_ input: String, limit: Int = 6) -> [RankedSuggestion] {
        let key = input.lowercased().trimmingCharacters(in: .whitespaces)
        guard key.count >= 2 else { return [] }

        var suggestions: [RankedSuggestion] = []
        var seen: Set<String> = []

        // Primary conversion
        let primary = convert(key)
        if !primary.bengali.isEmpty && seen.insert(primary.bengali).inserted {
            suggestions.append(RankedSuggestion(
                bengali: primary.bengali,
                confidence: 1.0,
                phonetic: key
            ))
        }

        // Prefix search
        for result in trie.prefixSearch(key, limit: limit) {
            if seen.insert(result.bengali).inserted {
                suggestions.append(RankedSuggestion(
                    bengali: result.bengali,
                    confidence: 0.70,
                    phonetic: result.phonetic
                ))
            }
        }

        // Typing habit variants
        for variant in TypingHabitNormalizer.expand(key) where variant != key {
            for result in trie.prefixSearch(variant, limit: 3) {
                if seen.insert(result.bengali).inserted {
                    suggestions.append(RankedSuggestion(
                        bengali: result.bengali,
                        confidence: 0.65,
                        phonetic: result.phonetic
                    ))
                }
            }
        }

        return Array(suggestions.sorted { $0.confidence > $1.confidence }.prefix(limit))
    }

    /// Save a learned word (boosts future lookups)
    public func saveLearnedWord(phonetic: String, bengali: String) {
        trie.insert(phonetic, bengali, 95)
        cache.removeAll()
    }

    // MARK: - Pattern Conversion (Layers 2-4)

    private func convertByPatterns(_ key: String) -> ConversionResult {
        var result = ""
        let chars = Array(key)
        var i = 0
        var confidence = 0.85

        while i < chars.count {
            let pos = i
            let remaining = String(chars[i...])

            // Try ConjunctRules first (sibilant conjuncts -- highest priority)
            if let match = ConjunctRules.matchAt(key, position: pos) {
                result += match.bengali
                i += match.consumed
                // Check for dependent vowel after conjunct
                if i < chars.count && isVowel(chars[i]) {
                    let (vowel, consumed) = resolveVowel(chars, from: i, isIndependent: false)
                    result += vowel
                    i += consumed
                }
                continue
            }

            // Try ConjunctTable (aspirated consonants, compound chars)
            var tableMatched = false
            for entry in ConjunctTable.entries {
                if remaining.hasPrefix(entry.phonetic) {
                    result += entry.bengali
                    i += entry.phonetic.count
                    // Check for dependent vowel after table match
                    if i < chars.count && isVowel(chars[i]) {
                        let (vowel, consumed) = resolveVowel(chars, from: i, isIndependent: false)
                        result += vowel
                        i += consumed
                    }
                    tableMatched = true
                    break
                }
            }
            if tableMatched { continue }

            let ch = chars[i]

            if isVowel(ch) {
                let isInitial = result.isEmpty || isNonBengaliChar(result.last)
                let (vowel, consumed) = resolveVowel(chars, from: i, isIndependent: isInitial)
                result += vowel
                i += consumed
            } else if ch.isLetter {
                let (consonant, consumed, conf) = resolveConsonant(chars, from: i, bengaliContext: result, fullKey: key)
                result += consonant
                confidence = min(confidence, conf)
                i += consumed
                // Check for dependent vowel after consonant
                if i < chars.count && isVowel(chars[i]) {
                    let (vowel, vConsumed) = resolveVowel(chars, from: i, isIndependent: false)
                    result += vowel
                    i += vConsumed
                }
            } else {
                result += String(ch)
                i += 1
            }
        }

        return ConversionResult(bengali: result, confidence: confidence)
    }

    private func isVowel(_ ch: Character) -> Bool {
        "aeiou".contains(ch.lowercased())
    }

    private func isNonBengaliChar(_ ch: Character?) -> Bool {
        guard let ch = ch else { return true }
        // Space, punctuation, digits
        return ch == " " || ch.isPunctuation || ch.isNumber
    }

    private func resolveVowel(_ chars: [Character], from: Int, isIndependent: Bool) -> (String, Int) {
        // Compound vowels (2-char)
        if from + 1 < chars.count {
            let two = String([chars[from], chars[from + 1]]).lowercased()
            switch two {
            case "ou": return (isIndependent ? "ঔ" : "ৌ", 2)
            case "oi": return (isIndependent ? "ঐ" : "ৈ", 2)
            case "oo": return (isIndependent ? "ঊ" : "ূ", 2)
            case "ee": return (isIndependent ? "ঈ" : "ী", 2)
            case "ii": return (isIndependent ? "ঈ" : "ী", 2)
            case "aa": return (isIndependent ? "আ" : "া", 2)
            default: break
            }
        }
        // Single vowels
        switch chars[from].lowercased().first ?? chars[from] {
        case "a": return (isIndependent ? "অ" : "", 1)  // inherent vowel (no mark needed)
        case "i": return (isIndependent ? "ই" : "ি", 1)
        case "u": return (isIndependent ? "উ" : "ু", 1)
        case "e": return (isIndependent ? "এ" : "ে", 1)
        case "o": return (isIndependent ? "ও" : "ো", 1)
        default: return (String(chars[from]), 1)
        }
    }

    private func resolveConsonant(
        _ chars: [Character],
        from: Int,
        bengaliContext: String,
        fullKey: String
    ) -> (String, Int, Double) {
        let remaining = String(chars[from...]).lowercased()

        // "ng" handling -- use NasalRules
        if remaining.hasPrefix("ng") {
            let afterIdx = from + 2
            let nextChar: Character? = afterIdx < chars.count ? chars[afterIdx] : nil
            let nasal = NasalRules.resolve(nextChar)
            return (String(nasal), 2, 0.90)
        }

        // "sh" handling -- use NatvaShatvaRules
        if remaining.hasPrefix("sh") {
            let (bengali, conf) = NatvaShatvaRules.resolveSibilant(
                bengaliContext: bengaliContext,
                phoneticWord: fullKey,
                sibilantIndex: from
            )
            return (String(bengali), 2, conf)
        }

        // "n" handling (NatvaVidhan) -- but not "ng" (already handled above)
        if chars[from].lowercased() == "n" {
            let isRetroflex = NatvaShatvaRules.shouldBeRetroflex(bengaliContext)
            return (isRetroflex ? "ণ" : "ন", 1, isRetroflex ? 0.80 : 0.85)
        }

        // "y" after consonant should be treated as ya-phala if not word-initial
        // But standalone "y" maps to য

        // Standard consonant map
        let consonantMap: [Character: String] = [
            "k": "ক", "g": "গ", "c": "চ", "j": "জ",
            "t": "ত", "d": "দ", "p": "প", "b": "ব",
            "f": "ফ", "m": "ম", "r": "র", "l": "ল",
            "s": "স", "h": "হ", "v": "ভ", "w": "ও",
            "y": "য়", "z": "জ", "q": "ক", "x": "ক্স",
        ]

        let ch = chars[from].lowercased().first ?? chars[from]
        let bengali = consonantMap[ch] ?? String(chars[from])
        return (bengali, 1, 0.80)
    }

    private func cacheResult(_ key: String, _ result: ConversionResult) {
        if cache.count >= maxCacheSize {
            cache.removeAll()
        }
        cache[key] = result
    }
}
