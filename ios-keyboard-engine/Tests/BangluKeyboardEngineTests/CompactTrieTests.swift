import XCTest
@testable import BangluKeyboardEngine

final class CompactTrieTests: XCTestCase {

    func testInsertAndExactMatch() {
        let trie = CompactTrie()
        trie.insert("ami", "আমি", 100)
        let results = trie.exactMatch("ami")
        XCTAssertEqual(results.count, 1)
        XCTAssertEqual(results[0].bengali, "আমি")
        XCTAssertEqual(results[0].frequency, 100)
    }

    func testPrefixSearch() {
        let trie = CompactTrie()
        trie.insert("ami", "আমি", 100)
        trie.insert("amar", "আমার", 95)
        trie.insert("amake", "আমাকে", 90)
        let results = trie.prefixSearch("am", limit: 10)
        XCTAssertEqual(results.count, 3)
        // Should be sorted by frequency descending
        XCTAssertEqual(results[0].bengali, "আমি")
        XCTAssertEqual(results[1].bengali, "আমার")
        XCTAssertEqual(results[2].bengali, "আমাকে")
    }

    func testCaseInsensitive() {
        let trie = CompactTrie()
        trie.insert("Ami", "আমি", 100)
        XCTAssertEqual(trie.exactMatch("ami").count, 1)
        XCTAssertEqual(trie.exactMatch("AMI").count, 1)
        XCTAssertEqual(trie.exactMatch("aMi").count, 1)
    }

    func testMiss() {
        let trie = CompactTrie()
        trie.insert("ami", "আমি", 100)
        XCTAssertEqual(trie.exactMatch("xyz").count, 0)
        XCTAssertEqual(trie.prefixSearch("xyz").count, 0)
    }

    func testHasPrefix() {
        let trie = CompactTrie()
        trie.insert("ami", "আমি", 100)
        XCTAssertTrue(trie.hasPrefix("am"))
        XCTAssertTrue(trie.hasPrefix("ami"))
        XCTAssertFalse(trie.hasPrefix("xyz"))
    }

    func testDuplicateInsert() {
        let trie = CompactTrie()
        trie.insert("ami", "আমি", 50)
        trie.insert("ami", "আমি", 100)  // Higher frequency should update
        let results = trie.exactMatch("ami")
        XCTAssertEqual(results.count, 1)
        XCTAssertEqual(results[0].frequency, 100)
    }

    func testMultipleEntriesSameKey() {
        let trie = CompactTrie()
        trie.insert("oi", "ওই", 80)
        trie.insert("oi", "ঐ", 70)
        let results = trie.exactMatch("oi")
        XCTAssertEqual(results.count, 2)
        // Sorted by frequency
        XCTAssertEqual(results[0].bengali, "ওই")
        XCTAssertEqual(results[1].bengali, "ঐ")
    }

    func testEmptyInput() {
        let trie = CompactTrie()
        trie.insert("ami", "আমি", 100)
        XCTAssertEqual(trie.exactMatch("").count, 0)
        XCTAssertEqual(trie.prefixSearch("").count, 0)
    }

    func testClear() {
        let trie = CompactTrie()
        trie.insert("ami", "আমি", 100)
        XCTAssertEqual(trie.exactMatch("ami").count, 1)
        trie.clear()
        XCTAssertEqual(trie.exactMatch("ami").count, 0)
        XCTAssertEqual(trie.keyCount, 0)
    }
}
