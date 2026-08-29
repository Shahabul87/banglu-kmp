package com.banglu.engine.platform

class InMemoryPhoneticIndexStore(
    entries: List<Pair<PhoneticIndexHit, String>>, // hit to key
    private val english: Map<String, String> = emptyMap(),
    words: Set<String> = emptySet(),
    extendedEntries: List<ExtendedDictionaryHit> = emptyList()
) : PhoneticIndexStore {

    private val byKey: Map<String, List<PhoneticIndexHit>> =
        entries.groupBy({ it.second }, { it.first })
            .mapValues { (_, hits) ->
                // S4/C1 tier-first key ranking: a Tier-A (real-usage) word beats
                // a Tier-B junk word even when the junk word canonically owns the
                // key; within a tier, canonical owners (priority 0) beat
                // habit-alias claimants (priority 1); frequency breaks ties.
                hits.sortedWith(
                    compareBy<PhoneticIndexHit> { it.tier }
                        .thenBy { it.priority }
                        .thenByDescending { it.frequency }
                )
            }

    /**
     * Words table emulation: explicit [words] UNION every indexed Bengali form —
     * every indexed word is by definition a dictionary word in the compiled db.
     */
    private val dictionaryWords: Set<String> =
        words + entries.map { it.first.bengali }

    override fun lookupExact(key: String): List<PhoneticIndexHit> =
        byKey[key].orEmpty()

    override fun lookupPrefix(prefix: String, limit: Int): List<PhoneticIndexHit> {
        if (limit <= 0) return emptyList()
        return byKey.asSequence()
            .filter { it.key.startsWith(prefix) }
            .flatMap { it.value }
            .filter { it.tier == PhoneticIndexHit.TIER_A }
            .sortedWith(
                compareBy<PhoneticIndexHit> { it.priority }
                    .thenByDescending { it.frequency }
            )
            .take(limit)
            .toList()
    }

    override fun lookupEnglish(key: String): String? = english[key]
    override fun englishKeys(): Set<String> = english.keys

    override fun containsWord(bengali: String): Boolean = bengali in dictionaryWords

    // ── S102 extended-dictionary emulation (jvmTest runs the Android
    //    store-served-extended configuration through this fixture) ────────
    private val extendedByKey: Map<String, List<ExtendedDictionaryHit>> =
        extendedEntries.groupBy { it.phonetic }
            .mapValues { (_, hits) -> hits.sortedByDescending { it.frequency } }

    private val extendedByBengali: Map<String, String> = buildMap {
        for (hit in extendedEntries) {
            if (hit.bengali !in this) put(hit.bengali, hit.phonetic)
        }
    }

    override fun hasExtendedData(): Boolean = extendedByKey.isNotEmpty()

    override fun lookupExtendedExact(key: String): List<ExtendedDictionaryHit> =
        extendedByKey[key].orEmpty()

    override fun lookupExtendedPrefix(prefix: String, limit: Int): List<ExtendedDictionaryHit> {
        if (limit <= 0) return emptyList()
        return extendedByKey.asSequence()
            .filter { it.key.startsWith(prefix) }
            .flatMap { it.value }
            .sortedByDescending { it.frequency }
            .take(limit)
            .toList()
    }

    override fun extendedPhoneticForBengali(bengali: String): String? = extendedByBengali[bengali]
}
