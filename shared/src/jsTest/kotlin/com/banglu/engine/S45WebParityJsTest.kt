package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals

private val fs: dynamic = js("require('fs')")

class S45WebParityJsTest {
    private fun slimPath(): String? {
        val envPath = js("typeof process !== 'undefined' ? (process.env.BANGLU_SLIM_PATH || null) : null")
        if (envPath != null) return envPath as String
        val candidates = arrayOf(
            "banglu-slim.json", "shared/banglu-slim.json",
            "../banglu-slim.json", "../../banglu-slim.json",
            // kotlin-js node tests run in <root>/build/js/packages/<module>-test
            "../../../../shared/banglu-slim.json",
            "../../../../../shared/banglu-slim.json",
        )
        for (c in candidates) if (fs.existsSync(c) as Boolean) return c
        // S128 (production audit): parity walls must never SILENTLY pass in
        // CI — a missing slim there means the pipeline forgot to generate it.
        check(js("typeof process !== 'undefined' && process.env.CI === 'true'") != true) {
            "banglu-slim.json missing in CI — generate it (dictionary-compiler: slim <db> <out.json>) before :shared:jsNodeTest"
        }
        return null
    }

    @Test
    fun engineParityOnJs() {
        val path = slimPath() ?: return // slim dict not present (CI) — skip
        BangluWebEngine.attachSlimDictionary(fs.readFileSync(path, "utf8") as String)

        // Core parity set — the same words the Android S-rounds locked.
        assertEquals("আমি", BangluWebEngine.convert("ami"))
        assertEquals("কেমন", BangluWebEngine.convert("kmon"))
        assertEquals("ইচ্ছা", BangluWebEngine.convert("issa"))
        assertEquals("আচ্ছা", BangluWebEngine.convert("assa"))
        assertEquals("সমস্যা", BangluWebEngine.convert("somossa"))
        assertEquals("সমস্যা", BangluWebEngine.convert("somocca"))
        assertEquals("করছি", BangluWebEngine.convert("korsi"))
        assertEquals("হুম", BangluWebEngine.convert("hm"))
        assertEquals("ওকে", BangluWebEngine.convert("ok"))
        assertEquals("তোমরা", BangluWebEngine.convert("tmra"))
        assertEquals("ভিডিও", BangluWebEngine.convert("vdo"))
        assertEquals("গল্প", BangluWebEngine.convert("golp"))
        assertEquals("শব্দ", BangluWebEngine.convert("shobd"))
        assertEquals("টাকা", BangluWebEngine.convert("taka"))
        assertEquals("পারবি", BangluWebEngine.convert("parbi"))
        // Suggestions and instant preview stay functional.
        val sugg = BangluWebEngine.suggestions("taka", 5)
        assertEquals("টাকা", sugg.first())
        assertEquals("আমি", BangluWebEngine.instantPreview("ami"))
    }
}
