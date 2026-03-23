import XCTest
@testable import BangluKeyboardEngine

final class PhoneticEngineLiteTests: XCTestCase {
    let engine = PhoneticEngineLite()

    override func setUp() {
        super.setUp()
        engine.initialize()
    }

    func testInitialization() {
        XCTAssertTrue(engine.isInitialized)
    }

    func testEmptyInput() {
        let result = engine.convert("")
        XCTAssertEqual(result.bengali, "")
        XCTAssertEqual(result.confidence, 0.0)
    }

    func testDictionaryLookup() {
        let result = engine.convert("ami")
        XCTAssertEqual(result.bengali, "আমি")
        XCTAssertGreaterThan(result.confidence, 0.90)
    }

    func testCaseInsensitive() {
        let lower = engine.convert("ami").bengali
        let upper = engine.convert("AMI").bengali
        let mixed = engine.convert("Ami").bengali
        XCTAssertEqual(lower, upper)
        XCTAssertEqual(upper, mixed)
    }

    func testSuggestions() {
        let suggestions = engine.getSuggestions("am", limit: 6)
        XCTAssertFalse(suggestions.isEmpty)
        // Should include আমি and আমার as prefix matches
        let bengalis = suggestions.map { $0.bengali }
        XCTAssertTrue(bengalis.contains("আমি"), "Suggestions should contain আমি")
        XCTAssertTrue(bengalis.contains("আমার"), "Suggestions should contain আমার")
    }

    func testSaveLearnedWord() {
        engine.saveLearnedWord(phonetic: "testword", bengali: "টেস্ট")
        let result = engine.convert("testword")
        XCTAssertEqual(result.bengali, "টেস্ট")
    }

    func testPatternConversion() {
        // Words not in dictionary should still convert via patterns
        let result = engine.convert("xyz")
        // Should produce some output (even if not a real Bengali word)
        XCTAssertFalse(result.bengali.isEmpty)
    }

    func testConjunctTableConversion() {
        // "kh" should produce খ
        let result = engine.convert("kho")
        // Pattern: kh -> খ, o -> ো
        XCTAssertEqual(result.bengali, "খো")
    }
}
