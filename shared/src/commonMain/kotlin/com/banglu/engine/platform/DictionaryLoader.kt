package com.banglu.engine.platform

import com.banglu.engine.types.BigramModelData
import com.banglu.engine.types.SmartDictionaryEntry

/**
 * Platform-specific dictionary loading abstraction.
 *
 * Each platform provides its own implementation for loading dictionary data:
 * - JVM: JvmDictionaryLoader (returns null; tests use seed-only mode)
 * - Android: loads from assets/
 * - iOS: loads from app bundle
 * - JS/Web: fetches from public/ via HTTP (like SmartEngine.loadLargeDictionary())
 *
 * All loaders return null when the resource is unavailable, so the engine
 * gracefully degrades to seed-dictionary-only mode.
 */
interface DictionaryLoader {
    /** Load the full 480K Bengali word list for validation and recovery. */
    suspend fun loadFullDictionary(): List<String>?

    /** Load word frequency scores for suggestion ranking. */
    suspend fun loadFrequencyMap(): Map<String, Int>?

    /** Load the wrong->right disambiguation map. */
    suspend fun loadDisambiguationMap(): Map<String, String>?

    /** Load the extended phonetic dictionary (~121K entries). */
    suspend fun loadExtendedDictionary(): List<SmartDictionaryEntry>?

    /** Load the bigram model data for context-aware suggestions. */
    suspend fun loadBigramModel(): BigramModelData?
}
