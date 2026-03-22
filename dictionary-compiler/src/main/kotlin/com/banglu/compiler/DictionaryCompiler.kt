package com.banglu.compiler

import kotlinx.serialization.json.*
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

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

        // 3. Insert words with frequencies
        println("Inserting words...")
        val insertWord = connection.prepareStatement("INSERT INTO words (bengali, frequency) VALUES (?, ?)")
        var count = 0
        for (wordElement in bengaliWords) {
            val word = wordElement.jsonPrimitive.content
            val freq = frequencies[word]?.jsonPrimitive?.int ?: 0
            insertWord.setString(1, word)
            insertWord.setInt(2, freq)
            insertWord.addBatch()
            count++
            if (count % 10000 == 0) {
                insertWord.executeBatch()
                print("\r  Inserted $count words...")
            }
        }
        insertWord.executeBatch()
        println("\r  Inserted $count words total")

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

        // 5. Insert metadata
        val insertMeta = connection.prepareStatement("INSERT INTO metadata (key, value) VALUES (?, ?)")
        val metadataEntries = mapOf(
            "version" to "3.1.0",
            "word_count" to count.toString(),
            "disambiguation_count" to mappings.size.toString(),
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

        // Print stats
        val fileSize = File(outputPath).length()
        println("\nDone!")
        println("  Output: $outputPath")
        println("  Size: ${fileSize / 1024 / 1024} MB")
        println("  Words: $count")
        println("  Disambiguations: ${mappings.size}")

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
            CREATE TABLE metadata (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
        """)
    }
    connection.commit()
}
