package com.banglu.engine.platform

class InMemoryPhoneticIndexStore(
    entries: List<Pair<PhoneticIndexHit, String>>, // hit to key
    private val english: Map<String, String> = emptyMap()
) : PhoneticIndexStore {

    private val byKey: Map<String, List<PhoneticIndexHit>> =
        entries.groupBy({ it.second }, { it.first })
            .mapValues { (_, hits) -> hits.sortedByDescending { it.frequency } }

    override fun lookupExact(key: String): List<PhoneticIndexHit> =
        byKey[key].orEmpty()

    override fun lookupPrefix(prefix: String, limit: Int): List<PhoneticIndexHit> =
        byKey.asSequence()
            .filter { it.key.startsWith(prefix) }
            .flatMap { it.value }
            .filter { it.tier == 0 }
            .sortedByDescending { it.frequency }
            .take(limit)
            .toList()

    override fun lookupEnglish(key: String): String? = english[key]
}
