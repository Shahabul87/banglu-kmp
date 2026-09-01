package com.banglu.engine

import com.banglu.engine.platform.InMemoryStorage
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * S165 probe (opt-in BANGLU_S165_PROBE=1): tester note 2026-09-01 —
 * (1) hoi/hoy → হয়; hoyce/hoice/hoyche/hoiche → হয়েছে coverage;
 * (2) pyra: is প্যারা offered, and does an explicit pick become the
 *     primary on the next conversion (the preference loop)?
 */
class S165TesterProbeJvm {

    @AfterTest
    fun tearDown() {
        SmartEngineAdapter.reset()
    }

    @Test
    fun probe() = runBlocking {
        if (System.getenv("BANGLU_S165_PROBE") != "1") return@runBlocking
        val engine = ConjunctSolutionRoundJvmTest.engine

        println("S165 ---- report 1: hoi/hoy family ----")
        for (k in listOf("hoi", "hoy", "hoyce", "hoice", "hoyche", "hoiche", "hoise", "pyra", "para", "pera")) {
            val r = engine.convertWord(k)
            val strip = engine.getSuggestions(k, 6).joinToString("|") { it.bengali }
            println("S165 $k -> ${r.bengali} (conf=${"%.2f".format(r.confidence)}, src=${r.source}) strip=[$strip]")
        }

        println("S165 ---- report 2: pyra preference loop (adapter) ----")
        SmartEngineAdapter.setPhoneticIndex(ConjunctSolutionRoundJvmTest.store)
        SmartEngineAdapter.initialize(InMemoryStorage(), TestDictionaryLoader())
        val before = SmartEngineAdapter.convertWord("pyra")
        println("S165 pyra BEFORE pick -> ${before.bengali}")
        SmartEngineAdapter.onWordSelected("pyra", "প্যারা", false, true)
        val after = SmartEngineAdapter.convertWord("pyra")
        println("S165 pyra AFTER pick -> ${after.bengali}")
        val strip2 = SmartEngineAdapter.getSuggestions("pyra", 6).joinToString("|") { it.bengali }
        println("S165 pyra strip AFTER pick -> [$strip2]")
    }
}
