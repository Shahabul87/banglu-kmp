import Foundation

public struct TrieEntry: Sendable {
    public let bengali: String
    public let frequency: Int

    public init(bengali: String, frequency: Int) {
        self.bengali = bengali
        self.frequency = frequency
    }
}

public struct PrefixResult: Sendable {
    public let bengali: String
    public let phonetic: String
    public let frequency: Int

    public init(bengali: String, phonetic: String, frequency: Int) {
        self.bengali = bengali
        self.phonetic = phonetic
        self.frequency = frequency
    }
}

public class CompactTrie {
    private class Node {
        var children: [Character: Node] = [:]
        var entries: [TrieEntry] = []
        var isTerminal = false
    }

    private var root = Node()
    public private(set) var keyCount = 0

    public init() {}

    public func insert(_ phonetic: String, _ bengali: String, _ frequency: Int) {
        let key = phonetic.lowercased().trimmingCharacters(in: .whitespaces)
        guard !key.isEmpty else { return }

        var node = root
        for ch in key {
            if node.children[ch] == nil { node.children[ch] = Node() }
            node = node.children[ch]!
        }

        if let idx = node.entries.firstIndex(where: { $0.bengali == bengali }) {
            if frequency > node.entries[idx].frequency {
                node.entries[idx] = TrieEntry(bengali: bengali, frequency: frequency)
            }
            return
        }

        node.entries.append(TrieEntry(bengali: bengali, frequency: frequency))
        if !node.isTerminal {
            node.isTerminal = true
            keyCount += 1
        }
    }

    public func exactMatch(_ phonetic: String) -> [TrieEntry] {
        let key = phonetic.lowercased().trimmingCharacters(in: .whitespaces)
        guard !key.isEmpty else { return [] }

        var node = root
        for ch in key {
            guard let next = node.children[ch] else { return [] }
            node = next
        }
        guard node.isTerminal else { return [] }
        return node.entries.sorted { $0.frequency > $1.frequency }
    }

    public func prefixSearch(_ prefix: String, limit: Int = 10) -> [PrefixResult] {
        let key = prefix.lowercased().trimmingCharacters(in: .whitespaces)
        guard !key.isEmpty else { return [] }

        var node = root
        for ch in key {
            guard let next = node.children[ch] else { return [] }
            node = next
        }

        var results: [PrefixResult] = []
        collectEntries(node: node, currentKey: key, results: &results, limit: limit * 3)
        return Array(results.sorted { $0.frequency > $1.frequency }.prefix(limit))
    }

    public func hasPrefix(_ prefix: String) -> Bool {
        let key = prefix.lowercased()
        var node = root
        for ch in key {
            guard let next = node.children[ch] else { return false }
            node = next
        }
        return true
    }

    public func clear() {
        root = Node()
        keyCount = 0
    }

    private func collectEntries(node: Node, currentKey: String, results: inout [PrefixResult], limit: Int) {
        guard results.count < limit else { return }
        if node.isTerminal {
            for entry in node.entries {
                guard results.count < limit else { return }
                results.append(PrefixResult(bengali: entry.bengali, phonetic: currentKey, frequency: entry.frequency))
            }
        }
        for (ch, child) in node.children.sorted(by: { $0.key < $1.key }) {
            guard results.count < limit else { return }
            collectEntries(node: child, currentKey: currentKey + String(ch), results: &results, limit: limit)
        }
    }
}
