package com.banglu.engine.types

import kotlinx.serialization.Serializable

enum class WordCategory {
    TATSAMA, TADBHAVA, FOREIGN, MIXED, UNKNOWN, PROPER
}

enum class ResolutionSource {
    DICTIONARY, RULE, STATISTICAL, USER_HISTORY, SECTION, NARROWING, CONJUNCT_RULE,
    ENGLISH_PASSTHROUGH, ENGLISH_LEXICON, CLEAN_TRANSLITERATION
}

@Serializable
data class SmartDictionaryEntry(
    val bengali: String,
    val phonetics: List<String>,
    val frequency: Int = 50,
    val category: WordCategory = WordCategory.UNKNOWN
)

data class LookupResult(
    val bengali: String,
    val matchedPhonetic: String,
    val frequency: Int,
    val confidence: Double,
    val source: ResolutionSource
)

data class PrefixResult(
    val bengali: String,
    val phonetic: String,
    val frequency: Int
)

data class TrieEntry(
    val bengali: String,
    val frequency: Int
)

data class Alternative(
    val bengali: String,
    val confidence: Double
)

data class ConversionResult(
    val bengali: String,
    val confidence: Double,
    val source: ResolutionSource,
    val alternatives: List<Alternative> = emptyList()
)

data class SmartSuggestion(
    val bengali: String,
    val confidence: Double,
    val source: String = "",
    val phonetic: String = "",
    val tier: String = ""
)

data class OverlapResult(
    val score: Double,
    val inputCoverage: Double,
    val isPrefix: Boolean
)

data class ConjunctMatch(
    val bengali: String,
    val consumed: Int,
    val confidence: Double = 1.0
)

data class NarrowingCandidate(
    val bengali: String,
    val phonetic: String,
    val frequency: Int,
    val overlapScore: Double,
    val combinedScore: Double,
    val source: String = ""
)

data class SectionSuggestion(
    val bengali: String,
    val confidence: Double,
    val source: String = "",
    val section: String = ""
)

data class LearnedWord(
    val id: String,
    val phonetic: String,
    val bengali: String,
    val frequency: Int,
    val lastUsed: Long
)

@Serializable
data class BigramModelData(
    val unigrams: Map<String, Int>,
    val bigrams: Map<String, Int>,
    val totalUnigrams: Int,
    val totalBigrams: Int
)

data class PredictedWord(
    val bengali: String,
    val confidence: Double
)

data class DictionaryStats(
    val totalEntries: Int,
    val totalPhoneticKeys: Int,
    val trieNodeCount: Int,
    val averageKeyLength: Double,
    val longestKey: String,
    val topFrequencyWords: List<TopWord>
)

data class TopWord(
    val bengali: String,
    val phonetic: String,
    val frequency: Int
)
