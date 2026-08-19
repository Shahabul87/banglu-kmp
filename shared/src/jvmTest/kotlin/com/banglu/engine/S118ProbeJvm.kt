package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test

/**
 * S118 probe: user regression report — osojjo/osojya (অসহ্য) and the wider
 * অ-initial + s/sh classes allegedly producing garbage after S109-S113.
 * S118_PROBE=1 ./gradlew :shared:jvmTest --tests "com.banglu.engine.S118ProbeJvm"
 */
class S118ProbeJvm {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test
    fun probe() {
        if (System.getenv("S118_PROBE") != "1") return
        val keys = listOf(
            // the reported word, every plausible typing
            "osojjo", "osojya", "oshojjo", "oshojjho", "osohjo", "osohjjo",
            "asojjo", "ashojjo", "osojho",
            // অ-initial common words (user says the class is failing)
            "onek", "obosta", "obostha", "oporadh", "ovab", "ovhab",
            "osadharon", "oshadharon", "obak", "ongsho", "onusthan",
            "onubhuti", "onuvuti", "osukh", "oshanti", "osomvob", "oshomvob",
            // s vs sh confusion probes
            "somoy", "shomoy", "sokal", "shokal", "sob", "shob",
            "sosta", "shosta", "sostho", "shostho", "sustho", "shushto",
            // hy-class siblings (general rule, not just অসহ্য)
            "sojjo", "shojjo", "oitijjo", "oitijjho", "grajjho", "grajjo",
            // echo-o retry class
            "otibo", "atibo", "otib", "atib",
            // ভ as v / vh (query normalization)
            "valo", "vai", "ovhab", "vhalo", "vhai", "ovab", "obhab",
            "vitore", "vhitore", "vromon", "vhromon",
            // glue-guard sanity (must still work)
            "bolbone", "hobeto", "dhapoguli", "korbona",
        )
        for (k in keys) {
            val r = engine.convertWord(k)
            val strip = engine.getSuggestions(k, 6).joinToString("|") { it.bengali }
            println("S118PROBE $k -> ${r.bengali} (conf=${r.confidence}, src=${r.source}) preview=${engine.convertForInstantPreview(k)} strip=[$strip]")
        }
        println("S118PROBE reverse(অসহ্য)=" + ReverseTransliterator.reverseWord("অসহ্য"))
    }
}
