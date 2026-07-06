package com.banglu.compiler

import kotlinx.serialization.json.*
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

private val engineComposedVerbSuffixes = listOf(
    "teci",
    "techi",
    "tecina",
    "techina",
    "tece",
    "teche",
    "teco",
    "techo",
    "tecen",
    "techen",
    "tecilam",
    "techilam",
    "tecile",
    "techile",
    "tecilo",
    "techilo",
    "tecilen",
    "techilen",
)

private fun isEngineComposedVerbVariant(phonetic: String): Boolean {
    val key = phonetic.lowercase().trim()
    return engineComposedVerbSuffixes.any { suffix ->
        key.length > suffix.length + 1 && key.endsWith(suffix)
    }
}

fun main(args: Array<String>) {
    val inputDir = args.getOrNull(0) ?: error("Usage: DictionaryCompiler <input-dir> <output-path>")
    val outputPath = args.getOrNull(1) ?: error("Usage: DictionaryCompiler <input-dir> <output-path>")

    println("Banglu Dictionary Compiler")
    println("Input:  $inputDir")
    println("Output: $outputPath")

    // Delete existing output
    File(outputPath).delete()

    val connection = DriverManager.getConnection("jdbc:sqlite:$outputPath")
    connection.autoCommit = false

    try {
        createTables(connection)

        // 1. Load and insert Bengali words
        val dictFile = File(inputDir, "bangla_dictionary.json")
        println("Reading ${dictFile.name}...")
        val dictJson = Json.parseToJsonElement(dictFile.readText()).jsonObject
        val bengaliWords = dictJson["bengali_words"]!!.jsonArray
        println("  Found ${bengaliWords.size} words")

        // 2. Load frequency map
        val freqFile = File(inputDir, "word-frequency.json")
        println("Reading ${freqFile.name}...")
        val freqJson = Json.parseToJsonElement(freqFile.readText()).jsonObject
        val frequencies = freqJson["frequencies"]!!.jsonObject
        println("  Found ${frequencies.size} frequency entries")

        // 3. Nukta-fold + dedupe the word list, then insert words with frequencies.
        // The words table stores the FOLDED form only (engine-side comparisons
        // are nukta-folded post-F1); duplicate folded forms merge (max frequency).
        val rawWordList = bengaliWords.map { it.jsonPrimitive.content }.toMutableList()
        val freqMap = frequencies.entries.associate { (w, f) -> w to f.jsonPrimitive.int }.toMutableMap()

        // S18: chat-register supplemental lexicon (register study 2026-07-06).
        // Words real typists use daily that never appear in wiki/news corpora
        // (গেসি, করতেসি, আছোস...). Frequencies are curated (40-60 band): high
        // enough to surface when typed, below evidenced formal words. Absent
        // from the usage corpus they assign as tier-B exact-only keys — they
        // never pollute suggestions for other typings.
        val chatLexicon = sequenceOf(
            File("data/chat_lexicon.tsv"),
            File("dictionary-compiler/data/chat_lexicon.tsv")
        ).firstOrNull { it.exists() }
        val chatWords = mutableListOf<String>()
        if (chatLexicon != null) {
            chatLexicon.readLines().forEach { line ->
                val parts = line.trim().split("\t")
                if (parts.size == 2 && parts[1].toIntOrNull() != null) {
                    rawWordList.add(parts[0])
                    freqMap.putIfAbsent(parts[0], parts[1].toInt())
                    chatWords.add(parts[0])
                }
            }
            println("  Chat lexicon: merged ${chatWords.size} supplemental words")
        }
        val foldResult = PhoneticIndexBuilder.foldAndDedupe(rawWordList, freqMap)
        println("  Nukta fold/dedupe removed ${foldResult.mergedCount} duplicate rows " +
            "(${rawWordList.size} -> ${foldResult.words.size} words)")

        // S5 corpus-authority frequency refresh (study W2): rebuild frequencies
        // from real web usage so twin spellings order by evidence, not legacy.
        val corpusDir = sequenceOf(
            File("data/corpus-2026-07"),
            File("dictionary-compiler/data/corpus-2026-07")
        ).firstOrNull { it.isDirectory }
        val usageCounts = HashMap<String, Long>(400_000)
        if (corpusDir != null) {
            println("Applying corpus authority from ${corpusDir.path}...")
            val modern = CorpusAuthority.addCounts(File(corpusDir, "modern_counts.tsv"), 1, 1, usageCounts)
            val literature = CorpusAuthority.addCounts(File(corpusDir, "bnwikisource_counts.tsv"), 1, 4, usageCounts)
            println("  Corpus counts: $modern modern lines, $literature literature lines, " +
                "${usageCounts.size} weighted words")
        } else {
            println("WARNING: corpus-2026-07 not found — frequencies stay legacy")
        }
        // S18: chat-lexicon words carry curated usage evidence — they exist
        // precisely because wiki/news corpora never contain them. Without
        // this they assign tier-B (exact-only, STRIP-INVISIBLE) and the whole
        // point of adding them (chat typings in the suggestion strip) is lost.
        for (w in chatWords) {
            val folded = com.banglu.engine.util.ReverseTransliterator.foldNukta(w.trim())
            usageCounts[folded] = maxOf(usageCounts[folded] ?: 0L, 3L)
        }
        val frequenciesByWord = CorpusAuthority.refreshFrequencies(foldResult.frequencies, usageCounts)

        // Vocabulary expansion: news-anchored corpus words the base list lacks
        // (names, modern spellings, compound inflections). They inherit their
        // corpus-scaled frequency from refreshFrequencies via usageCounts.
        val newWords = corpusDir?.let {
            CorpusAuthority.newsAnchoredNewWords(File(it, "news_counts.tsv"), foldResult.words.toHashSet())
        }.orEmpty()
        println("  Vocabulary expansion: ${newWords.size} news-anchored new words")
        val allWords = foldResult.words + newWords

        println("Inserting words...")
        val insertWord = connection.prepareStatement("INSERT INTO words (bengali, frequency) VALUES (?, ?)")
        val wordIdByBengali = HashMap<String, Int>(allWords.size * 2)
        var count = 0
        for (word in allWords) {
            insertWord.setString(1, word)
            insertWord.setInt(2, frequenciesByWord[word] ?: 0)
            insertWord.addBatch()
            count++
            // id == insertion ordinal (AUTOINCREMENT starts at 1)
            wordIdByBengali.putIfAbsent(word, count)
            if (count % 10000 == 0) {
                insertWord.executeBatch()
                print("\r  Inserted $count words...")
            }
        }
        insertWord.executeBatch()
        println("\r  Inserted $count words total")

        // 3a. Real-usage word list (suggestion tier evidence): word<TAB>count
        // from real Bengali web text. Membership makes a word Tier A.
        val dataDir = sequenceOf(File("data"), File("dictionary-compiler/data")).firstOrNull { it.isDirectory }
        val usageFile = dataDir?.let { File(it, "bengali-web-usage.tsv") }
        val usageWords: Set<String> = if (usageFile != null && usageFile.exists()) {
            usageFile.readLines()
                .mapNotNull { line -> line.substringBefore('\t').trim().takeIf { it.isNotEmpty() } }
                .toSet()
        } else {
            println("WARNING: bengali-web-usage.tsv not found — tiering falls back to frequency only")
            emptySet()
        }
        println("  Loaded ${usageWords.size} real-usage words")

        // 3b. Build precompiled phonetic index (Engine v3)
        println("Building phonetic index...")
        val indexRows = PhoneticIndexBuilder.build(allWords, frequenciesByWord, usageWords)
        val report = PhoneticIndexBuilder.lastReport
        println("  Rows: ${report.totalRows} (canonical ${report.canonicalRows}, habit-alias ${report.habitAliasRows})")
        println("  Words: tier-A ${report.tierAWords}, tier-B ${report.tierBWords}")
        println("  Round-trip coverage: " +
            "${"%.1f".format(java.util.Locale.ROOT, report.coveragePercent)}% (${report.roundTripOk}/${report.totalWords})")
        println("  Dropped keys: ${report.droppedKeys}, words with no rows: ${report.wordsWithNoRows}")

        val insertIndex = connection.prepareStatement(
            "INSERT INTO phonetic_index (key, word_id, frequency, tier, priority) VALUES (?, ?, ?, ?, ?)"
        )
        var indexCount = 0
        var unmappedCount = 0
        for (row in indexRows) {
            val wordId = wordIdByBengali[row.bengali]
                ?: wordIdByBengali[row.bengali.trim()]
            if (wordId == null) {
                unmappedCount++
                continue
            }
            insertIndex.setString(1, row.key)
            insertIndex.setInt(2, wordId)
            insertIndex.setInt(3, row.frequency)
            insertIndex.setInt(4, row.tier)
            insertIndex.setInt(5, row.priority)
            insertIndex.addBatch()
            if (++indexCount % 50000 == 0) insertIndex.executeBatch()
        }
        insertIndex.executeBatch()
        println("  Inserted $indexCount phonetic index rows")
        if (unmappedCount > 0) {
            println("  WARNING: $unmappedCount phonetic index rows had no matching word_id (unmapped)")
        }

        // 3c. Build English lexicon (Engine v3)
        val cmudictFile = dataDir?.let { File(it, "cmudict.dict") }
        val freqListFile = dataDir?.let { File(it, "en_50k.txt") }
        var englishCount = 0
        if (cmudictFile != null && cmudictFile.exists() && freqListFile != null && freqListFile.exists()) {
            println("Building English lexicon...")
            val topWords = EnglishLexiconBuilder.parseTopWords(freqListFile.readLines())
            val englishEntries = EnglishLexiconBuilder.build(cmudictFile.readLines(), topWords)
            val insertEnglish = connection.prepareStatement(
                "INSERT OR IGNORE INTO english_lexicon (key, bengali) VALUES (?, ?)"
            )
            for (entry in englishEntries) {
                insertEnglish.setString(1, entry.key)
                insertEnglish.setString(2, entry.bengali)
                insertEnglish.addBatch()
            }
            insertEnglish.executeBatch()
            englishCount = englishEntries.size
            println("  Inserted $englishCount english lexicon entries " +
                "(${EnglishLexiconBuilder.lastSkippedUnconvertible} unconvertible skipped)")
        } else {
            println("Skipping english lexicon (data files not found: ${dataDir?.absolutePath ?: "no data dir found"})")
        }

        // 4. Load and insert disambiguation map
        val disambigFile = File(inputDir, "disambiguation-map.json")
        println("Reading ${disambigFile.name}...")
        val disambigJson = Json.parseToJsonElement(disambigFile.readText()).jsonObject
        val mappings = disambigJson["mappings"]!!.jsonObject
        println("  Found ${mappings.size} disambiguation entries")

        val insertDisambig = connection.prepareStatement("INSERT INTO disambiguation (wrong_form, correct_form) VALUES (?, ?)")
        for ((wrong, correct) in mappings) {
            insertDisambig.setString(1, wrong)
            insertDisambig.setString(2, correct.jsonPrimitive.content)
            insertDisambig.addBatch()
        }
        insertDisambig.executeBatch()
        println("  Inserted ${mappings.size} disambiguation entries")

        // 5. Load and insert extended phonetic dictionary
        val extendedFile = File(inputDir, "dictionary-extended.json")
        var extendedEntryCount = 0
        var extendedPhoneticCount = 0
        var prunedEngineVerbPhoneticCount = 0
        if (extendedFile.exists()) {
            println("Reading ${extendedFile.name}...")
            val extendedJson = Json.parseToJsonElement(extendedFile.readText()).jsonArray
            println("  Found ${extendedJson.size} extended entries")

            val insertExtended = connection.prepareStatement(
                "INSERT INTO extended_dictionary (id, bengali, frequency, category) VALUES (?, ?, ?, ?)"
            )
            val insertExtendedPhonetic = connection.prepareStatement(
                "INSERT INTO extended_phonetics (phonetic, entry_id) VALUES (?, ?)"
            )

            var entryId = 0
            for (entryElement in extendedJson) {
                val entry = entryElement.jsonObject
                val bengali = entry["bengali"]?.jsonPrimitive?.contentOrNull ?: continue
                val phonetics = entry["phonetics"]?.jsonArray ?: continue
                val frequency = entry["frequency"]?.jsonPrimitive?.intOrNull ?: 0
                val category = entry["category"]?.jsonPrimitive?.contentOrNull ?: "unknown"

                entryId++
                insertExtended.setInt(1, entryId)
                insertExtended.setString(2, bengali)
                insertExtended.setInt(3, frequency)
                insertExtended.setString(4, category)
                insertExtended.addBatch()
                extendedEntryCount++

                for (phoneticElement in phonetics) {
                    val phonetic = phoneticElement.jsonPrimitive.content.lowercase().trim()
                    if (phonetic.isEmpty()) continue
                    if (isEngineComposedVerbVariant(phonetic)) {
                        prunedEngineVerbPhoneticCount++
                        continue
                    }
                    insertExtendedPhonetic.setString(1, phonetic)
                    insertExtendedPhonetic.setInt(2, entryId)
                    insertExtendedPhonetic.addBatch()
                    extendedPhoneticCount++
                }

                if (entryId % 10000 == 0) {
                    insertExtended.executeBatch()
                    insertExtendedPhonetic.executeBatch()
                    print("\r  Inserted $entryId extended entries...")
                }
            }
            insertExtended.executeBatch()
            insertExtendedPhonetic.executeBatch()
            println("\r  Inserted $extendedEntryCount extended entries, $extendedPhoneticCount phonetics")
            println("  Pruned $prunedEngineVerbPhoneticCount engine-composed verb phonetics")
        } else {
            println("Skipping dictionary-extended.json (not found)")
        }

        // 6. Load and insert bigram model.
        // S9: prefer the 2026-07 corpus bigrams (real observed pairs, modern
        // register weighted 4:1 over literature) over the legacy JSON model —
        // homophone context ranking (mach → মাছ vs ম্যাচ) needs pair evidence
        // the 25k-pair legacy model doesn't have.
        val bigramFile = File(inputDir, "bigram-model.json")
        var unigramCount = 0
        var bigramCount = 0
        var totalUnigrams = 0
        var totalBigrams = 0
        val corpusBigrams = corpusDir?.let {
            CorpusBigrams.build(
                modern = File(it, "bnwiki_bigrams.tsv"),
                literature = File(it, "bnwikisource_bigrams.tsv"),
                unigramCounts = usageCounts,
                dictionaryWords = wordIdByBengali.keys
            )
        }
        if (corpusBigrams != null && corpusBigrams.pairs.isNotEmpty()) {
            println("Building corpus bigram model...")
            val insertUnigram = connection.prepareStatement(
                "INSERT INTO bigram_unigrams (word, count) VALUES (?, ?)"
            )
            for ((word, count) in corpusBigrams.unigrams) {
                insertUnigram.setString(1, word)
                insertUnigram.setInt(2, count)
                insertUnigram.addBatch()
                if (++unigramCount % 50000 == 0) insertUnigram.executeBatch()
            }
            insertUnigram.executeBatch()
            val insertBigram = connection.prepareStatement(
                "INSERT INTO bigram_pairs (previous_word, next_word, count) VALUES (?, ?, ?)"
            )
            for ((pair, count) in corpusBigrams.pairs) {
                insertBigram.setString(1, pair.first)
                insertBigram.setString(2, pair.second)
                insertBigram.setInt(3, count)
                insertBigram.addBatch()
                if (++bigramCount % 50000 == 0) insertBigram.executeBatch()
            }
            insertBigram.executeBatch()
            totalUnigrams = corpusBigrams.totalUnigrams
            totalBigrams = corpusBigrams.totalBigrams
            println("  Inserted $unigramCount unigrams and $bigramCount corpus bigram pairs")
        } else if (bigramFile.exists()) {
            println("Reading ${bigramFile.name}...")
            val bigramJson = Json.parseToJsonElement(bigramFile.readText()).jsonObject
            val unigrams = bigramJson["unigrams"]!!.jsonObject
            val bigrams = bigramJson["bigrams"]!!.jsonObject
            totalUnigrams = bigramJson["totalUnigrams"]?.jsonPrimitive?.intOrNull ?: 0
            totalBigrams = bigramJson["totalBigrams"]?.jsonPrimitive?.intOrNull ?: 0
            println("  Found ${unigrams.size} unigrams and ${bigrams.size} bigrams")

            val insertUnigram = connection.prepareStatement(
                "INSERT INTO bigram_unigrams (word, count) VALUES (?, ?)"
            )
            for ((word, countElement) in unigrams) {
                insertUnigram.setString(1, word)
                insertUnigram.setInt(2, countElement.jsonPrimitive.int)
                insertUnigram.addBatch()
                unigramCount++
            }
            insertUnigram.executeBatch()

            val insertBigram = connection.prepareStatement(
                "INSERT INTO bigram_pairs (previous_word, next_word, count) VALUES (?, ?, ?)"
            )
            for ((key, countElement) in bigrams) {
                val parts = key.split('\t', limit = 2)
                if (parts.size != 2) continue
                insertBigram.setString(1, parts[0])
                insertBigram.setString(2, parts[1])
                insertBigram.setInt(3, countElement.jsonPrimitive.int)
                insertBigram.addBatch()
                bigramCount++
            }
            insertBigram.executeBatch()
            println("  Inserted $unigramCount unigrams and $bigramCount bigrams")
        } else {
            println("Skipping bigram-model.json (not found)")
        }

        // 7. Insert metadata
        val insertMeta = connection.prepareStatement("INSERT INTO metadata (key, value) VALUES (?, ?)")
        val metadataEntries = mapOf(
            "version" to "3.7.3",
            "word_count" to count.toString(),
            "disambiguation_count" to mappings.size.toString(),
            "extended_entry_count" to extendedEntryCount.toString(),
            "extended_phonetic_count" to extendedPhoneticCount.toString(),
            "pruned_engine_verb_phonetic_count" to prunedEngineVerbPhoneticCount.toString(),
            "bigram_unigram_count" to unigramCount.toString(),
            "bigram_pair_count" to bigramCount.toString(),
            "total_unigrams" to totalUnigrams.toString(),
            "total_bigrams" to totalBigrams.toString(),
            "phonetic_index_count" to indexCount.toString(),
            "phonetic_unmapped_rows" to unmappedCount.toString(),
            "phonetic_canonical_rows" to report.canonicalRows.toString(),
            "phonetic_habit_alias_rows" to report.habitAliasRows.toString(),
            "phonetic_tier_a_words" to report.tierAWords.toString(),
            "phonetic_tier_b_words" to report.tierBWords.toString(),
            "nukta_merged_words" to foldResult.mergedCount.toString(),
            "usage_word_count" to usageWords.size.toString(),
            "phonetic_roundtrip_coverage" to "%.2f".format(java.util.Locale.ROOT, report.coveragePercent),
            "phonetic_dropped_keys" to report.droppedKeys.toString(),
            "phonetic_words_no_rows" to report.wordsWithNoRows.toString(),
            "english_lexicon_count" to englishCount.toString(),
            "compiled_at" to java.time.Instant.now().toString(),
            "source" to "banglu-web/public/"
        )
        for ((key, value) in metadataEntries) {
            insertMeta.setString(1, key)
            insertMeta.setString(2, value)
            insertMeta.addBatch()
        }
        insertMeta.executeBatch()

        connection.commit()

        // Compact the artifact: indexes were populated by out-of-order bulk
        // inserts, leaving B-tree pages ~2/3 full. VACUUM rebuilds them packed
        // (must run outside a transaction).
        println("Vacuuming...")
        connection.autoCommit = true
        connection.createStatement().execute("VACUUM")
        connection.autoCommit = false

        // Print stats
        val fileSize = File(outputPath).length()
        println("\nDone!")
        println("  Output: $outputPath")
        println("  Size: ${fileSize / 1024 / 1024} MB")
        println("  Words: $count")
        println("  Disambiguations: ${mappings.size}")
        println("  Extended entries: $extendedEntryCount")
        println("  Bigram pairs: $bigramCount")

    } catch (e: Exception) {
        connection.rollback()
        throw e
    } finally {
        connection.close()
    }
}

private fun createTables(connection: Connection) {
    connection.createStatement().apply {
        execute("""
            CREATE TABLE words (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                bengali TEXT NOT NULL,
                frequency INTEGER DEFAULT 0
            )
        """)
        execute("CREATE INDEX idx_words_bengali ON words(bengali)")
        execute("CREATE INDEX idx_words_frequency ON words(frequency DESC)")

        execute("""
            CREATE TABLE disambiguation (
                wrong_form TEXT PRIMARY KEY,
                correct_form TEXT NOT NULL
            )
        """)

        execute("""
            CREATE TABLE extended_dictionary (
                id INTEGER PRIMARY KEY,
                bengali TEXT NOT NULL,
                frequency INTEGER DEFAULT 0,
                category TEXT DEFAULT 'unknown'
            )
        """)
        execute("CREATE INDEX idx_extended_dictionary_bengali ON extended_dictionary(bengali)")

        execute("""
            CREATE TABLE extended_phonetics (
                phonetic TEXT NOT NULL,
                entry_id INTEGER NOT NULL,
                FOREIGN KEY(entry_id) REFERENCES extended_dictionary(id)
            )
        """)
        execute("CREATE INDEX idx_extended_phonetics_phonetic ON extended_phonetics(phonetic)")
        execute("CREATE INDEX idx_extended_phonetics_entry ON extended_phonetics(entry_id)")

        execute("""
            CREATE TABLE bigram_unigrams (
                word TEXT PRIMARY KEY,
                count INTEGER NOT NULL
            )
        """)

        execute("""
            CREATE TABLE bigram_pairs (
                previous_word TEXT NOT NULL,
                next_word TEXT NOT NULL,
                count INTEGER NOT NULL,
                PRIMARY KEY(previous_word, next_word)
            )
        """)
        execute("CREATE INDEX idx_bigram_pairs_previous ON bigram_pairs(previous_word)")

        execute("""
            CREATE TABLE phonetic_index (
                key TEXT NOT NULL,
                word_id INTEGER NOT NULL,
                frequency INTEGER DEFAULT 0,
                tier INTEGER NOT NULL DEFAULT 1,
                priority INTEGER NOT NULL DEFAULT 0
            )
        """)
        // S4/C1: tier-first key ranking — the covering index matches the
        // engine's ORDER BY (tier ASC, priority ASC, frequency DESC) prefix.
        execute("CREATE INDEX idx_phonetic_index_key ON phonetic_index(key, tier, priority)")

        execute("""
            CREATE TABLE english_lexicon (
                key TEXT PRIMARY KEY,
                bengali TEXT NOT NULL
            )
        """)

        execute("""
            CREATE TABLE metadata (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
        """)
    }
    connection.commit()
}
