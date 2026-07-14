package com.banglu.engine.dictionary

import com.banglu.engine.types.PrefixResult
import com.banglu.engine.types.TrieEntry

/**
 * Phonetic Trie - Prefix tree for O(k) phonetic lookup
 *
 * Stores lowercase phonetic keys mapped to Bengali words.
 * Supports exact match, prefix search, and fuzzy matching.
 */
class PhoneticTrie {

    /**
     * Compact node (S4/C2): children live in two parallel arrays (CharArray +
     * node array, linear scan — fan-out is tiny) and [entries] is allocated
     * lazily only on terminal nodes. The previous LinkedHashMap<Char, TrieNode>
     * + eager ArrayList per node cost ~150-250 bytes of pure overhead across
     * the >1M nodes of the 130K-entry extended dictionary (~200MB resident on
     * JVM measurements) — the single largest reason full-mode load exhausted a
     * 256MB device heap.
     */
    private class TrieNode {
        var childChars: CharArray = EMPTY_CHARS
        var childNodes: Array<TrieNode?> = EMPTY_NODES
        var entries: MutableList<TrieEntry>? = null
        var isTerminal: Boolean = false

        fun child(char: Char): TrieNode? {
            val chars = childChars
            for (i in chars.indices) {
                if (chars[i] == char) return childNodes[i]
            }
            return null
        }

        fun addChild(char: Char, node: TrieNode) {
            val n = childChars.size
            childChars = childChars.copyOf(n + 1).also { it[n] = char }
            childNodes = childNodes.copyOf(n + 1).also { it[n] = node }
        }

        companion object {
            val EMPTY_CHARS = CharArray(0)
            val EMPTY_NODES = arrayOfNulls<TrieNode>(0)
        }
    }

    private var root: TrieNode = TrieNode()
    private var nodeCount: Int = 1
    private var keyCount: Int = 0

    /**
     * Insert a phonetic key -> Bengali word mapping into the trie.
     * Key is normalized to lowercase and trimmed.
     */
    fun insert(phonetic: String, bengali: String, frequency: Int) {
        val key = phonetic.lowercase().trim()
        if (key.isEmpty()) return

        var node = root

        for (char in key) {
            var child = node.child(char)
            if (child == null) {
                child = TrieNode()
                node.addChild(char, child)
                nodeCount++
            }
            node = child
        }

        val entries = node.entries ?: mutableListOf<TrieEntry>().also { node.entries = it }

        // Check for duplicate Bengali word at this node
        val existingIndex = entries.indexOfFirst { it.bengali == bengali }
        if (existingIndex >= 0) {
            val existing = entries[existingIndex]
            // Update frequency if higher
            if (frequency > existing.frequency) {
                entries[existingIndex] = existing.copy(frequency = frequency)
            }
            return
        }

        entries.add(TrieEntry(bengali = bengali, frequency = frequency))
        node.isTerminal = true
        keyCount++
    }

    /**
     * Exact match lookup. Returns all Bengali words for this exact phonetic key,
     * sorted by frequency (highest first).
     */
    fun exactMatch(phonetic: String): List<TrieEntry> {
        val key = phonetic.lowercase().trim()
        if (key.isEmpty()) return emptyList()

        var node = root

        for (char in key) {
            node = node.child(char) ?: return emptyList()
        }

        if (!node.isTerminal) return emptyList()

        // Return sorted by frequency descending
        return node.entries.orEmpty().sortedByDescending { it.frequency }
    }

    /**
     * Prefix search. Returns all Bengali words whose phonetic key starts with
     * the given prefix, sorted by frequency.
     */
    fun prefixSearch(prefix: String, limit: Int = 10): List<PrefixResult> {
        val key = prefix.lowercase().trim()
        if (key.isEmpty()) return emptyList()

        var node = root

        // Navigate to the prefix node
        for (char in key) {
            node = node.child(char) ?: return emptyList()
        }

        // Collect all entries under this prefix
        val results = mutableListOf<PrefixResult>()
        val collectLimit = limit * 3 // Collect extra for sorting
        collectEntries(node, key, results, collectLimit)

        // Sort by frequency and return limited results
        return results
            .sortedByDescending { it.frequency }
            .take(limit)
    }

    /**
     * Recursively collect all entries under a node.
     */
    private fun collectEntries(
        node: TrieNode,
        currentKey: String,
        results: MutableList<PrefixResult>,
        limit: Int
    ) {
        if (results.size >= limit) return

        if (node.isTerminal) {
            for (entry in node.entries.orEmpty()) {
                if (results.size >= limit) return
                results.add(
                    PrefixResult(
                        bengali = entry.bengali,
                        phonetic = currentKey,
                        frequency = entry.frequency
                    )
                )
            }
        }

        val chars = node.childChars
        val nodes = node.childNodes
        for (i in chars.indices) {
            if (results.size >= limit) return
            collectEntries(nodes[i]!!, currentKey + chars[i], results, limit)
        }
    }

    /**
     * Check if any key exists with this prefix.
     */
    fun hasPrefix(prefix: String): Boolean {
        val key = prefix.lowercase().trim()
        var node = root

        for (char in key) {
            node = node.child(char) ?: return false
        }

        return true
    }

    /**
     * Fuzzy match - find entries within a given edit distance.
     * Uses bounded BFS with Levenshtein distance.
     *
     * @param phonetic The phonetic input to match against.
     * @param maxDistance Maximum edit distance allowed.
     * @param limit Maximum number of results to return.
     * @param anchorFirst If true, the first character must match exactly.
     *   This prevents irrelevant results where only the initial consonant differs
     *   (e.g., "gai" won't match "nai", "jai", "tai").
     */
    fun fuzzyMatch(
        phonetic: String,
        maxDistance: Int = 1,
        limit: Int = 5,
        anchorFirst: Boolean = false
    ): List<PrefixResult> {
        val key = phonetic.lowercase().trim()
        if (key.isEmpty()) return emptyList()

        val results = mutableListOf<PrefixResult>()
        // S46: the collect cap fires BEFORE the frequency sort, so traversal
        // order decided WHICH candidates survived — and that order follows
        // upstream map iteration, which differs between JVM and JS (kmon
        // found কেমন on Android but not on the web). Collect a wide pool at
        // seed scale (trie is ~6.5K words, distance <= 1 — traversal is
        // cheap), then let the sort pick deterministically by frequency.
        val collectLimit = maxOf(limit * 4, 128)

        if (anchorFirst && key.isNotEmpty()) {
            // Only traverse the branch matching the first character
            val firstChar = key[0]
            val firstNode = root.child(firstChar)
            if (firstNode != null) {
                fuzzySearch(firstNode, firstChar.toString(), key, 1, maxDistance, results, collectLimit)
            }
        } else {
            fuzzySearch(root, "", key, 0, maxDistance, results, collectLimit)
        }

        return results
            .sortedByDescending { it.frequency }
            .take(limit)
    }

    private fun fuzzySearch(
        node: TrieNode,
        currentKey: String,
        target: String,
        targetIndex: Int,
        remainingDistance: Int,
        results: MutableList<PrefixResult>,
        limit: Int
    ) {
        if (results.size >= limit) return

        // If we've consumed the entire target
        if (targetIndex == target.length) {
            if (node.isTerminal) {
                for (entry in node.entries.orEmpty()) {
                    if (results.size >= limit) return
                    results.add(
                        PrefixResult(
                            bengali = entry.bengali,
                            phonetic = currentKey,
                            frequency = entry.frequency
                        )
                    )
                }
            }
            // Allow extra characters at the end (deletion errors in target)
            if (remainingDistance > 0) {
                val chars = node.childChars
                val nodes = node.childNodes
                for (i in chars.indices) {
                    fuzzySearch(
                        nodes[i]!!, currentKey + chars[i], target, targetIndex,
                        remainingDistance - 1, results, limit
                    )
                }
            }
            return
        }

        val targetChar = target[targetIndex]

        // Exact match - no distance cost
        val exactChild = node.child(targetChar)
        if (exactChild != null) {
            fuzzySearch(
                exactChild, currentKey + targetChar, target,
                targetIndex + 1, remainingDistance, results, limit
            )
        }

        if (remainingDistance <= 0) return

        // Substitution: try all other children
        val chars = node.childChars
        val nodes = node.childNodes
        for (i in chars.indices) {
            if (chars[i] != targetChar) {
                fuzzySearch(
                    nodes[i]!!, currentKey + chars[i], target,
                    targetIndex + 1, remainingDistance - 1, results, limit
                )
            }
        }

        // Deletion: skip a character in target
        fuzzySearch(node, currentKey, target, targetIndex + 1, remainingDistance - 1, results, limit)

        // Insertion: consume a trie character without advancing target
        for (i in chars.indices) {
            fuzzySearch(
                nodes[i]!!, currentKey + chars[i], target,
                targetIndex, remainingDistance - 1, results, limit
            )
        }
    }

    /** Get total number of unique phonetic keys stored */
    fun getKeyCount(): Int = keyCount

    /** Get total number of trie nodes */
    fun getNodeCount(): Int = nodeCount

    /** Clear all entries */
    fun clear() {
        root = TrieNode()
        nodeCount = 1
        keyCount = 0
    }
}
