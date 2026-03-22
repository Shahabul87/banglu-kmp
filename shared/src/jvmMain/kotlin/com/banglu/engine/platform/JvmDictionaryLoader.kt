package com.banglu.engine.platform

import com.banglu.engine.types.BigramModelData
import com.banglu.engine.types.SmartDictionaryEntry

/**
 * JVM implementation of DictionaryLoader.
 *
 * Returns null for all resources - JVM tests use seed-only mode.
 * In a production JVM scenario, this could be extended to load
 * from classpath resources or local files.
 */
class JvmDictionaryLoader : DictionaryLoader {

    override suspend fun loadFullDictionary(): List<String>? = null

    override suspend fun loadFrequencyMap(): Map<String, Int>? = null

    override suspend fun loadDisambiguationMap(): Map<String, String>? = null

    override suspend fun loadExtendedDictionary(): List<SmartDictionaryEntry>? = null

    override suspend fun loadBigramModel(): BigramModelData? = null
}
