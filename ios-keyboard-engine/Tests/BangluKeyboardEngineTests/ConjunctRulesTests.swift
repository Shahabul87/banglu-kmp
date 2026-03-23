import XCTest
@testable import BangluKeyboardEngine

final class ConjunctRulesTests: XCTestCase {

    func testKkh() { XCTAssertEqual(ConjunctRules.resolve("kkh"), "ক্ষ") }
    func testKsh() { XCTAssertEqual(ConjunctRules.resolve("ksh"), "ক্ষ") }
    func testSht() { XCTAssertEqual(ConjunctRules.resolve("sht"), "ষ্ট") }
    func testShth() { XCTAssertEqual(ConjunctRules.resolve("shth"), "ষ্ঠ") }
    func testStr() { XCTAssertEqual(ConjunctRules.resolve("str"), "স্ত্র") }
    func testShr() { XCTAssertEqual(ConjunctRules.resolve("shr"), "শ্র") }
    func testSt() { XCTAssertEqual(ConjunctRules.resolve("st"), "স্ত") }
    func testSk() { XCTAssertEqual(ConjunctRules.resolve("sk"), "স্ক") }
    func testSw() { XCTAssertEqual(ConjunctRules.resolve("sw"), "স্ব") }
    func testSb() { XCTAssertEqual(ConjunctRules.resolve("sb"), "স্ব") }

    func testNoMatch() { XCTAssertNil(ConjunctRules.resolve("xyz")) }

    func testMatchAt() {
        let match = ConjunctRules.matchAt("shthane", position: 0)
        XCTAssertNotNil(match)
        XCTAssertEqual(match?.bengali, "ষ্ঠ")
        XCTAssertEqual(match?.consumed, 4)
    }

    func testMatchAtMiddle() {
        // "abcstr..." at position 3 should match "str"
        let match = ConjunctRules.matchAt("abcstr", position: 3)
        XCTAssertNotNil(match)
        XCTAssertEqual(match?.bengali, "স্ত্র")
        XCTAssertEqual(match?.consumed, 3)
    }

    func testMatchAtNoMatch() {
        let match = ConjunctRules.matchAt("hello", position: 0)
        XCTAssertNil(match)
    }

    func testLongestMatchFirst() {
        // "shtr" should match ষ্ট্র (4 chars), not "sh" (2 chars)
        let match = ConjunctRules.matchAt("shtr", position: 0)
        XCTAssertNotNil(match)
        XCTAssertEqual(match?.bengali, "ষ্ট্র")
        XCTAssertEqual(match?.consumed, 4)
    }
}
