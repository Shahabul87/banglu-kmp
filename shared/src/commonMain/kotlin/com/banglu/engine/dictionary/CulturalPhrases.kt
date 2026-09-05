package com.banglu.engine.dictionary

/**
 * S184 — everyday Bangladeshi greetings and religious phrases (user, 2026-09-04:
 * "bengali culture use assalamulaikum … and walaikum … make this type of
 * sentence available to suggestions based on user typing intent").
 *
 * Two behaviours, both static data (no store, no validator — safe in lite mode
 * and mirrored into the composing preview by construction):
 *  - an EXACT roman key commits the phrase with its correct spacing
 *    (assalamualaikum → আসসালামু আলাইকুম);
 *  - a typed prefix of four or more letters surfaces the phrase as a strip
 *    chip ([completion]) — the intent chip; the typed key's own reading keeps
 *    strip[0] (S141: the engine must not ignore what was typed).
 *
 * Keys are lowercase letters only (spaces removed). Order inside a phrase's
 * variant list is most-common-first; the first phrase whose variant starts
 * with the prefix wins, so common phrases are listed before rare ones.
 */
object CulturalPhrases {
    private val PHRASES: List<Pair<String, List<String>>> = listOf(
        "আসসালামু আলাইকুম" to listOf("assalamualaikum", "asalamualaikum", "assalamualikum", "assalamu", "asalamu", "assalamualaykum", "salamualaikum", "assalamoalaikum"),
        "ওয়ালাইকুম আসসালাম" to listOf("walaikumassalam", "walaikumasalam", "oalaikumassalam", "walaikumsalam", "walaikum", "oalaikum", "waalaikumassalam", "walaikumusalam"),
        "ইনশাআল্লাহ" to listOf("inshallah", "inshaallah", "insallah", "inshaallah", "insha"),
        "আলহামদুলিল্লাহ" to listOf("alhamdulillah", "alhamdulilah", "alhamdu", "alhamdullilah"),
        "মাশাআল্লাহ" to listOf("mashallah", "mashaallah", "masallah", "mashaallah"),
        "সুবহানাল্লাহ" to listOf("subhanallah", "subhanalla", "sobhanallah"),
        "আসতাগফিরুল্লাহ" to listOf("astagfirullah", "astaghfirullah", "astagfirulla"),
        "জাযাকাল্লাহ খাইরান" to listOf("jazakallahkhairan", "jazakallahkhair", "jazakallah", "jazakalla"),
        "বিসমিল্লাহ" to listOf("bismillah", "bismilla"),
        "ইন্না লিল্লাহ" to listOf("innalillah", "innalilah"),
        "ফি আমানিল্লাহ" to listOf("fiamanillah", "fiamanilla"),
        "আল্লাহ হাফেজ" to listOf("allahhafez", "allahafez", "allahhafiz"),
        "খোদা হাফেজ" to listOf("khodahafez", "khudahafez", "khodahafiz"),
        "ঈদ মুবারক" to listOf("eidmubarak", "idmubarak", "eidmubarok"),
        "রমজান মুবারক" to listOf("ramadanmubarak", "romjanmubarak", "ramjanmubarak"),
        "জুম্মা মুবারক" to listOf("jummamubarak", "jumamubarak"),
        "শুভ সকাল" to listOf("shuvosokal", "shubhosokal", "suvosokal"),
        "শুভ রাত্রি" to listOf("shuvoratri", "shubhoratri", "suvoratri"),
        "শুভ সন্ধ্যা" to listOf("shuvosondha", "shubhosondha", "shuvoshondha"),
        "শুভ নববর্ষ" to listOf("shuvonoboborsho", "shubhonoboborsho", "suvonoboborsho"),
        "শুভ জন্মদিন" to listOf("shuvojonmodin", "shubhojonmodin", "suvojonmodin"),
        "নমস্কার" to listOf("namaskar", "nomoskar", "nomoshkar"),
        "আদাব" to listOf("adab"),
        "ধন্যবাদ" to listOf("dhonnobad", "dhonnobaad", "dhonyobad", "dhonnyobad"),
    )

    /** roman key (letters only) → phrase. */
    val EXACT: Map<String, String> = LinkedHashMap<String, String>().also { m ->
        for ((phrase, keys) in PHRASES) for (k in keys) if (k !in m) m[k] = phrase   // first phrase owns a shared variant (no putIfAbsent in Kotlin/JS)
    }

    /** All (variant, phrase) pairs in declaration order — for the pin test. */
    val PAIRS: List<Pair<String, String>> = PHRASES.flatMap { (p, ks) -> ks.map { it to p } }

    const val MIN_PREFIX = 4

    /**
     * The phrase a typed prefix is heading for, or null. Requires
     * [MIN_PREFIX] letters; an exact variant is handled by [EXACT] and is
     * not repeated here.
     */
    fun completion(prefix: String): String? {
        val p = prefix.lowercase()
        if (p.length < MIN_PREFIX || !p.all { it in 'a'..'z' }) return null
        for ((phrase, keys) in PHRASES) {
            if (keys.any { it.length > p.length && it.startsWith(p) }) return phrase
        }
        return null
    }
}
