package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private val fsGate: dynamic = js("require('fs')")

/**
 * S108: attachSlimDictionary must reject a slim whose version doesn't match
 * [DictionaryVersion.REQUIRED] — the observed drift (3.9.2 sqlite on Android
 * while store zips shipped a 3.8.10 slim) is exactly what this gate stops.
 * Hosts (extension background, macOS EngineJS) catch and stay on seeds.
 */
class S108SlimVersionGateJsTest {

    private fun minimalSlim(version: String): String =
        """{"version":"$version","index":[],"english":[],"words":[]}"""

    @Test
    fun staleSlimIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            BangluWebEngine.attachSlimDictionary(minimalSlim("0.0.0"))
        }
        // The seed engine must survive the rejected attach untouched.
        assertEquals("আমি", BangluWebEngine.convert("ami"))
    }

    @Test
    fun currentVersionSlimAttaches() {
        // Prefer the real slim so the shared singleton keeps its full store
        // for whichever test runs after this one; the minimal fallback keeps
        // the gate exercised on CI where the slim is absent.
        val candidates = arrayOf(
            "banglu-slim.json", "shared/banglu-slim.json",
            "../banglu-slim.json", "../../banglu-slim.json",
            // kotlin-js node tests run in <root>/build/js/packages/<module>-test
            "../../../../shared/banglu-slim.json",
            "../../../../../shared/banglu-slim.json",
        )
        val real = candidates.firstOrNull { fsGate.existsSync(it) as Boolean }
        val json = if (real != null) fsGate.readFileSync(real, "utf8") as String
        else minimalSlim(DictionaryVersion.REQUIRED)
        BangluWebEngine.attachSlimDictionary(json)
        assertEquals("আমি", BangluWebEngine.convert("ami"))
    }
}
