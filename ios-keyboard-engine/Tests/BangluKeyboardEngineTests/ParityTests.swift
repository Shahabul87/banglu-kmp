import XCTest
@testable import BangluKeyboardEngine

/// Cross-platform parity tests.
/// These MUST produce identical results to the KMP ParityTest.kt.
final class ParityTests: XCTestCase {
    let engine = PhoneticEngineLite()

    override func setUp() {
        super.setUp()
        engine.initialize()
    }

    func testParityCases() {
        let cases: [(input: String, expected: String)] = [
            // ═══════════════════════ Pronouns ═══════════════════════
            ("ami", "আমি"),
            ("tumi", "তুমি"),
            ("apni", "আপনি"),
            ("se", "সে"),
            ("amra", "আমরা"),
            ("tara", "তারা"),
            ("amar", "আমার"),
            ("tomar", "তোমার"),

            // ═══════════════════════ Question words ═══════════════════════
            ("ki", "কি"),
            ("ke", "কে"),
            ("keno", "কেন"),
            ("kokhon", "কখন"),
            ("kothay", "কোথায়"),
            ("koto", "কত"),

            // ═══════════════════════ Common verbs ═══════════════════════
            ("ache", "আছে"),
            ("kori", "করি"),
            ("kore", "করে"),
            ("hoy", "হয়"),
            ("hobe", "হবে"),

            // ═══════════════════════ Common nouns ═══════════════════════
            ("bangla", "বাংলা"),
            ("bangladesh", "বাংলাদেশ"),
            ("dhaka", "ঢাকা"),
            ("bari", "বাড়ি"),
            ("ghor", "ঘর"),
            ("pani", "পানি"),
            ("ma", "মা"),
            ("baba", "বাবা"),
            ("din", "দিন"),
            ("rat", "রাত"),
            ("sokal", "সকাল"),

            // ═══════════════════════ Adjectives ═══════════════════════
            ("bhalo", "ভালো"),
            ("kharap", "খারাপ"),
            ("sundor", "সুন্দর"),
            ("notun", "নতুন"),
            ("lal", "লাল"),

            // ═══════════════════════ Conjunctions / Particles ═══════════════════════
            ("ebong", "এবং"),
            ("kintu", "কিন্তু"),
            ("ar", "আর"),
            ("tai", "তাই"),
            ("ekhon", "এখন"),

            // ═══════════════════════ Food / Common nouns ═══════════════════════
            ("khabar", "খাবার"),
            ("jol", "জল"),
            ("boi", "বই"),
            ("desh", "দেশ"),
            ("gram", "গ্রাম"),
            ("kaj", "কাজ"),
            ("mon", "মন"),

            // ═══════════════════════ Aspirated consonant test ═══════════════════════
            ("ghar", "ঘর"),

            // ═══════════════════════ Verb roots ═══════════════════════
            ("bol", "বল"),
            ("kor", "কর"),
            ("dekh", "দেখ"),
            ("shon", "শোন"),

            // ═══════════════════════ Greetings ═══════════════════════
            ("dhonnobad", "ধন্যবাদ"),

            // ═══════════════════════ Numbers ═══════════════════════
            ("ek", "এক"),
            ("dui", "দুই"),
            ("tin", "তিন"),
        ]

        var failures: [(input: String, expected: String, got: String)] = []
        for c in cases {
            let result = engine.convert(c.input).bengali
            if result != c.expected {
                failures.append((c.input, c.expected, result))
            }
        }

        if !failures.isEmpty {
            let msg = failures.map {
                "'\($0.input)': expected '\($0.expected)', got '\($0.got)'"
            }.joined(separator: "\n")
            XCTFail("\(failures.count)/\(cases.count) parity failures:\n\(msg)")
        }
    }

    func testSuggestions() {
        let suggestions = engine.getSuggestions("am", limit: 6)
        XCTAssertFalse(suggestions.isEmpty, "Suggestions for 'am' should not be empty")
    }

    func testEmptyInput() {
        let result = engine.convert("")
        XCTAssertEqual(result.bengali, "")
    }

    func testCaseInsensitivity() {
        let lower = engine.convert("ami").bengali
        let upper = engine.convert("AMI").bengali
        let mixed = engine.convert("Ami").bengali
        XCTAssertEqual(lower, upper, "Case should not matter")
        XCTAssertEqual(upper, mixed, "Case should not matter")
    }
}
