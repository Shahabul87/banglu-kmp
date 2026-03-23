import Foundation
import BangluKeyboardEngine

// MARK: - Lightweight Test Infrastructure

var totalTests = 0
var passedTests = 0
var failedTests = 0
var allFailures: [(test: String, message: String)] = []
var currentTestName = ""

func assertEqual<T: Equatable>(_ a: T, _ b: T, _ message: String = "", file: String = #file, line: Int = #line) {
    if a != b {
        let msg = message.isEmpty ? "assertEqual failed: '\(a)' != '\(b)'" : message + " ('\(a)' != '\(b)')"
        allFailures.append((test: currentTestName, message: "\(file):\(line) - \(msg)"))
    }
}

func assertTrue(_ condition: Bool, _ message: String = "", file: String = #file, line: Int = #line) {
    if !condition {
        let msg = message.isEmpty ? "assertTrue failed" : message
        allFailures.append((test: currentTestName, message: "\(file):\(line) - \(msg)"))
    }
}

func assertFalse(_ condition: Bool, _ message: String = "", file: String = #file, line: Int = #line) {
    assertTrue(!condition, message.isEmpty ? "assertFalse failed" : message, file: file, line: line)
}

func assertNotNil<T>(_ value: T?, _ message: String = "", file: String = #file, line: Int = #line) {
    if value == nil {
        let msg = message.isEmpty ? "assertNotNil failed: value was nil" : message
        allFailures.append((test: currentTestName, message: "\(file):\(line) - \(msg)"))
    }
}

func assertNil<T>(_ value: T?, _ message: String = "", file: String = #file, line: Int = #line) {
    if value != nil {
        let msg = message.isEmpty ? "assertNil failed: value was not nil" : message
        allFailures.append((test: currentTestName, message: "\(file):\(line) - \(msg)"))
    }
}

func assertGreaterThan<T: Comparable>(_ a: T, _ b: T, _ message: String = "", file: String = #file, line: Int = #line) {
    if !(a > b) {
        let msg = message.isEmpty ? "assertGreaterThan failed: \(a) not > \(b)" : message
        allFailures.append((test: currentTestName, message: "\(file):\(line) - \(msg)"))
    }
}

func runTest(_ name: String, _ body: () -> Void) {
    totalTests += 1
    currentTestName = name
    let beforeFailures = allFailures.count
    body()
    if allFailures.count == beforeFailures {
        passedTests += 1
        print("  PASS  \(name)")
    } else {
        failedTests += 1
        print("  FAIL  \(name)")
        for f in allFailures[beforeFailures...] {
            print("        \(f.message)")
        }
    }
}

func printResults() {
    print("\n" + String(repeating: "=", count: 60))
    print("Results: \(passedTests)/\(totalTests) passed, \(failedTests) failed")
    if !allFailures.isEmpty {
        print("\nAll failures:")
        for f in allFailures {
            print("  [\(f.test)] \(f.message)")
        }
    }
    print(String(repeating: "=", count: 60))
}

// MARK: - CompactTrie Tests

func runCompactTrieTests() {
    print("\n--- CompactTrieTests ---")

    runTest("testInsertAndExactMatch") {
        let trie = CompactTrie()
        trie.insert("ami", "আমি", 100)
        let results = trie.exactMatch("ami")
        assertEqual(results.count, 1)
        assertEqual(results[0].bengali, "আমি")
        assertEqual(results[0].frequency, 100)
    }

    runTest("testPrefixSearch") {
        let trie = CompactTrie()
        trie.insert("ami", "আমি", 100)
        trie.insert("amar", "আমার", 95)
        trie.insert("amake", "আমাকে", 90)
        let results = trie.prefixSearch("am", limit: 10)
        assertEqual(results.count, 3)
        assertEqual(results[0].bengali, "আমি")
    }

    runTest("testCaseInsensitive") {
        let trie = CompactTrie()
        trie.insert("Ami", "আমি", 100)
        assertEqual(trie.exactMatch("ami").count, 1)
        assertEqual(trie.exactMatch("AMI").count, 1)
    }

    runTest("testMiss") {
        let trie = CompactTrie()
        trie.insert("ami", "আমি", 100)
        assertEqual(trie.exactMatch("xyz").count, 0)
    }

    runTest("testHasPrefix") {
        let trie = CompactTrie()
        trie.insert("ami", "আমি", 100)
        assertTrue(trie.hasPrefix("am"))
        assertFalse(trie.hasPrefix("xyz"))
    }

    runTest("testDuplicateUpdate") {
        let trie = CompactTrie()
        trie.insert("ami", "আমি", 50)
        trie.insert("ami", "আমি", 100)
        let results = trie.exactMatch("ami")
        assertEqual(results.count, 1)
        assertEqual(results[0].frequency, 100)
    }

    runTest("testMultipleEntries") {
        let trie = CompactTrie()
        trie.insert("oi", "ওই", 80)
        trie.insert("oi", "ঐ", 70)
        let results = trie.exactMatch("oi")
        assertEqual(results.count, 2)
        assertEqual(results[0].bengali, "ওই")
    }

    runTest("testClear") {
        let trie = CompactTrie()
        trie.insert("ami", "আমি", 100)
        trie.clear()
        assertEqual(trie.exactMatch("ami").count, 0)
        assertEqual(trie.keyCount, 0)
    }

    runTest("testEmptyInput") {
        let trie = CompactTrie()
        trie.insert("ami", "আমি", 100)
        assertEqual(trie.exactMatch("").count, 0)
        assertEqual(trie.prefixSearch("").count, 0)
    }
}

// MARK: - ConjunctRules Tests

func runConjunctRulesTests() {
    print("\n--- ConjunctRulesTests ---")

    runTest("testKkh") { assertEqual(ConjunctRules.resolve("kkh"), "ক্ষ") }
    runTest("testKsh") { assertEqual(ConjunctRules.resolve("ksh"), "ক্ষ") }
    runTest("testSht") { assertEqual(ConjunctRules.resolve("sht"), "ষ্ট") }
    runTest("testShth") { assertEqual(ConjunctRules.resolve("shth"), "ষ্ঠ") }
    runTest("testStr") { assertEqual(ConjunctRules.resolve("str"), "স্ত্র") }
    runTest("testShr") { assertEqual(ConjunctRules.resolve("shr"), "শ্র") }
    runTest("testSt") { assertEqual(ConjunctRules.resolve("st"), "স্ত") }
    runTest("testSk") { assertEqual(ConjunctRules.resolve("sk"), "স্ক") }
    runTest("testNoMatch") { assertNil(ConjunctRules.resolve("xyz")) }

    runTest("testMatchAt") {
        let match = ConjunctRules.matchAt("shthane", position: 0)
        assertNotNil(match)
        assertEqual(match?.bengali, "ষ্ঠ")
        assertEqual(match?.consumed, 4)
    }

    runTest("testLongestMatchFirst") {
        let match = ConjunctRules.matchAt("shtr", position: 0)
        assertNotNil(match)
        assertEqual(match?.bengali, "ষ্ট্র")
        assertEqual(match?.consumed, 4)
    }
}

// MARK: - PhoneticEngineLite Tests

func runPhoneticEngineLiteTests() {
    print("\n--- PhoneticEngineLiteTests ---")
    let engine = PhoneticEngineLite()
    engine.initialize()

    runTest("testInitialization") {
        assertTrue(engine.isInitialized)
    }

    runTest("testEmptyInput") {
        let result = engine.convert("")
        assertEqual(result.bengali, "")
    }

    runTest("testDictionaryLookup") {
        let result = engine.convert("ami")
        assertEqual(result.bengali, "আমি")
        assertGreaterThan(result.confidence, 0.90)
    }

    runTest("testCaseInsensitive") {
        assertEqual(engine.convert("ami").bengali, engine.convert("AMI").bengali)
        assertEqual(engine.convert("AMI").bengali, engine.convert("Ami").bengali)
    }

    runTest("testSuggestions") {
        let suggestions = engine.getSuggestions("am", limit: 6)
        assertFalse(suggestions.isEmpty, "Suggestions for 'am' should not be empty")
    }

    runTest("testSaveLearnedWord") {
        engine.saveLearnedWord(phonetic: "testword", bengali: "টেস্ট")
        assertEqual(engine.convert("testword").bengali, "টেস্ট")
    }

    runTest("testPatternConversionKho") {
        // "kho" not in dict -> pattern: kh->খ, o->ো
        // But "kho" might match something... let's check pure patterns
        let e2 = PhoneticEngineLite()
        e2.initialize()
        // Use a word definitely not in dictionary
        let result = e2.convert("kho")
        // kh->খ from ConjunctTable, o->ো dependent vowel
        assertEqual(result.bengali, "খো")
    }
}

// MARK: - Parity Tests (CRITICAL)

func runParityTests() {
    print("\n--- ParityTests (CRITICAL) ---")
    let engine = PhoneticEngineLite()
    engine.initialize()

    let cases: [(input: String, expected: String)] = [
        // Pronouns
        ("ami", "আমি"),
        ("tumi", "তুমি"),
        ("apni", "আপনি"),
        ("se", "সে"),
        ("amra", "আমরা"),
        ("tara", "তারা"),
        ("amar", "আমার"),
        ("tomar", "তোমার"),
        // Question words
        ("ki", "কি"),
        ("ke", "কে"),
        ("keno", "কেন"),
        ("kokhon", "কখন"),
        ("kothay", "কোথায়"),
        ("koto", "কত"),
        // Common verbs
        ("ache", "আছে"),
        ("kori", "করি"),
        ("kore", "করে"),
        ("hoy", "হয়"),
        ("hobe", "হবে"),
        // Common nouns
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
        // Adjectives
        ("bhalo", "ভালো"),
        ("kharap", "খারাপ"),
        ("sundor", "সুন্দর"),
        ("notun", "নতুন"),
        ("lal", "লাল"),
        // Conjunctions / Particles
        ("ebong", "এবং"),
        ("kintu", "কিন্তু"),
        ("ar", "আর"),
        ("tai", "তাই"),
        ("ekhon", "এখন"),
        // Food / Common nouns
        ("khabar", "খাবার"),
        ("jol", "জল"),
        ("boi", "বই"),
        ("desh", "দেশ"),
        ("gram", "গ্রাম"),
        ("kaj", "কাজ"),
        ("mon", "মন"),
        // Aspirated consonant
        ("ghar", "ঘর"),
        // Verb roots
        ("bol", "বল"),
        ("kor", "কর"),
        ("dekh", "দেখ"),
        ("shon", "শোন"),
        // Greetings
        ("dhonnobad", "ধন্যবাদ"),
        // Numbers
        ("ek", "এক"),
        ("dui", "দুই"),
        ("tin", "তিন"),
    ]

    runTest("testAllParityCases") {
        var parityFailures: [(input: String, expected: String, got: String)] = []
        for c in cases {
            let result = engine.convert(c.input).bengali
            if result != c.expected {
                parityFailures.append((c.input, c.expected, result))
            }
        }

        if !parityFailures.isEmpty {
            let msg = parityFailures.map {
                "'\($0.input)': expected '\($0.expected)', got '\($0.got)'"
            }.joined(separator: "\n        ")
            assertTrue(false, "\(parityFailures.count)/\(cases.count) parity failures:\n        \(msg)")
        }
    }

    // Individual parity checks for debugging
    for c in cases {
        runTest("parity_\(c.input)") {
            let result = engine.convert(c.input).bengali
            assertEqual(result, c.expected, "'\(c.input)' should produce '\(c.expected)'")
        }
    }
}

// MARK: - Main

print("BangluKeyboardEngine Test Suite")
print(String(repeating: "=", count: 60))

runCompactTrieTests()
runConjunctRulesTests()
runPhoneticEngineLiteTests()
runParityTests()

printResults()

if failedTests > 0 {
    exit(1)
}
