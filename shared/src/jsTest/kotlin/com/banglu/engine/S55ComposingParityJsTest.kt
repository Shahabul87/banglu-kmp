package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals

private val fs55: dynamic = js("require('fs')")

/**
 * S55 (F-WEB-001/002, docs/audits/audit-android-keyboard-2026-07-16.md):
 * web/extension/macOS-IME never load the 480K validator (BangluWebEngine
 * only calls initSeed()/attachSlimDictionary(), never SmartEngine.initialize
 * with a DictionaryLoader) — so the wrapper's validator-gated junk-rescue
 * NEVER fires here. What actually resolves "callback"/"motivation" on this
 * tier is convertWordRaw's UNCONDITIONAL English-lexicon fallback
 * (tryEnglishLexicon, consulted after the corpus lookup fails — no
 * validator needed), which SmartEngine.convertForComposing now mirrors
 * directly. Traced empirically against the real slim dictionary
 * (S55ProbeJvm's lite-mode probe showed the identical validator-free
 * behavior on the JVM side first).
 */
class S55ComposingParityJsTest {
    private fun slimPath(): String? {
        val candidates = arrayOf(
            "banglu-slim.json", "shared/banglu-slim.json",
            "../banglu-slim.json", "../../banglu-slim.json",
            "/Users/mdshahabulalam/myprojects/banlgu/banglu-kmp/shared/banglu-slim.json"
        )
        for (c in candidates) if (fs55.existsSync(c) as Boolean) return c
        return null
    }

    @Test
    fun compositionPreviewMatchesCommitForLexiconOnlyWords() {
        val path = slimPath() ?: return // slim dict not present (CI) — skip
        BangluWebEngine.attachSlimDictionary(fs55.readFileSync(path, "utf8") as String)

        for (word in arrayOf("callback", "motivation")) {
            val commit = BangluWebEngine.convert(word)
            val preview = BangluWebEngine.compositionPreview(word)
            assertEquals(commit, preview, "slim-tier preview must match commit for '$word'")
        }
        assertEquals("কলব্যাক", BangluWebEngine.convert("callback"))
        assertEquals("মোটিভেশন", BangluWebEngine.convert("motivation"))
    }
}
