package com.banglu.engine.platform

class InMemoryPhoneticIndexStore(
    entries: List<Pair<PhoneticIndexHit, String>>, // hit to key
    private val english: Map<String, String> = emptyMap(),
    words: Set<String> = emptySet()
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

    override fun containsWord(bengali: String): Boolean = bengali in dictionaryWords
}
