package com.banglu.engine

import kotlin.test.Test

/**
 * S111 probe: current behavior of the S110 OOV target classes (plural
 * suffixes, glued samasa compounds) BEFORE the composition layers land.
 * S111_PROBE=1 ./gradlew :shared:jvmTest --tests "com.banglu.engine.S111ProbeJvm"
 */
class S111ProbeJvm {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test
    fun probe() {
        if (System.getenv("S111_PROBE") != "1") return
        val keys = listOf(
            // plural/classifier suffixes (expected: stem word + suffix glued)
            "bhyaraitider", "udbhidoder", "potongooder",
            "boishishtyoguli", "ongooguli", "dhapoguli", "gonogulir",
            "cheleguli", "boiguli", "manushder", "shobdogulo",
            // glued samasa compounds (expected word glued)
            "dehogothoner", "dehogothon", "sposhtochihnit", "ekoiruup",
            "kromobinyasogot",
            "chihnit", "deh", "deho", "dhap", "dhapo", "sposhto", "ato", "oswabhabikobhabei", "zounosongzog", "lyaminati",
        )
        for (k in keys) {
            val r = engine.convertWord(k)
            val strip = engine.getSuggestions(k, 6).joinToString("|") { it.bengali }
            println("S111PROBE $k -> ${r.bengali} (conf=${r.confidence}, src=${r.source}) strip=[$strip]")
        }
    }
}
