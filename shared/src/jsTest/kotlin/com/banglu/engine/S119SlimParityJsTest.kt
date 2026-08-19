package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val fs119: dynamic = js("require('fs')")

/**
 * S119 — slim-tier parity round (tester: "hobeto shows হবেত on web,
 * oshojjo stays literal — fix this").
 *
 * Two root causes, both general:
 * 1. attachSlimDictionary never loaded the VALIDATOR, so every
 *    validator-gated layer (tryNegationCompound, the S118 stem-evidence
 *    law) was silently dead on JS surfaces. The slim now ships a freqs
 *    array and the attach wires validator words + frequencies.
 * 2. The unowned-key query normalizer lacked the sh→s fold (the runtime
 *    mirror of the compiler's h_lazy_sh habit) — oshojjo owned nothing
 *    while its s-twin osojjo owns অসহ্য.
 */
class S119SlimParityJsTest {
    private fun slimPath(): String? {
        val candidates = arrayOf(
            "banglu-slim.json", "shared/banglu-slim.json",
            "../banglu-slim.json", "../../banglu-slim.json",
            "/Users/mdshahabulalam/myprojects/banlgu/banglu-kmp/shared/banglu-slim.json"
        )
        for (c in candidates) if (fs119.existsSync(c) as Boolean) return c
        return null
    }

    @Test
    fun slimParityPins() {
        val path = slimPath() ?: return // slim dict not present (CI) — skip
        BangluWebEngine.attachSlimDictionary(fs119.readFileSync(path, "utf8") as String)

        // 1. validator-gated glue layer now lives on slim
        assertEquals("হবে তো", BangluWebEngine.convert("hobeto"), "hobeto")
        assertEquals("বলবোনে", BangluWebEngine.convert("bolbone"), "bolbone")

        // 2. sh→s unowned-key fold
        assertEquals("অসহ্য", BangluWebEngine.convert("oshojjo"), "oshojjo")

        // S118 classes stay pinned on the slim tier
        assertEquals("অসহ্য", BangluWebEngine.convert("osojjo"), "osojjo")
        assertEquals("অসহ্য", BangluWebEngine.convert("osojya"), "osojya")
        assertEquals("অতীব", BangluWebEngine.convert("otibo"), "otibo")
        assertEquals("অভাব", BangluWebEngine.convert("ovhab"), "ovhab")
        assertEquals("অস্তিত্ব", BangluWebEngine.convert("astitto"), "astitto")

        // owned sh-words must never be folded (the normalizer only runs on
        // unowned keys — this is the guard's contract)
        assertEquals("শব", BangluWebEngine.convert("shob"), "shob")
        assertTrue(BangluWebEngine.convert("shokal").isNotEmpty(), "shokal")
    }
}
