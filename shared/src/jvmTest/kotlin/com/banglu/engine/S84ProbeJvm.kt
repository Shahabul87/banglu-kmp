package com.banglu.engine

import kotlin.test.Test

/**
 * S84 reproduction probe for the 2026-08-13 tester round (handwritten notes):
 *  1. abaro backspace-retype drift (আবারো vs আবারও)
 *  2. ট/ঠ (t/th retroflex) word formation
 *  3. English words typed directly → garbage conversion, no English chip
 *  4. রেফ over-application: kortam→কর্তাম (করতাম missing)
 *  5. typo tolerance: thogieco (intended ঠকিয়েছ)
 *
 * Diagnostic only — skipped unless S84_PROBE=1.
 */
class S84ProbeJvm {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    private fun dump(label: String, keys: List<String>) {
        println("== $label ==")
        for (key in keys) {
            val c = engine.convertWord(key)
            val comp = engine.convertForComposing(key)
            val sugg = engine.getSuggestions(key, 8)
                .joinToString(" | ") { "${it.bengali}(${it.source})" }
            println("$key -> primary=${c.bengali} [${c.source} conf=${c.confidence}] composing=${comp.bengali}")
            println("    strip: $sugg")
        }
    }

    @Test
    fun probe() {
        if (System.getenv("S84_PROBE") != "1") return

        dump("1. abaro class (vowel-ending re-type)", listOf(
            "abaro", "abar", "o", "aro", "abarow", "ekhono", "kokhono", "kono", "tao"
        ))

        dump("2. T/Th retroflex words", listOf(
            "thik", "thakur", "thanda", "thot", "thokano", "thokiyecho",
            "taka", "tebil", "chhoto", "mota", "tak", "matha", "pith",
            "tho", "th", "ta", "thake", "thako"
        ))

        dump("3. English direct words", listOf(
            "pattern", "computer", "school", "hospital", "problem",
            "office", "network", "keyboard", "because", "engine"
        ))

        dump("4. ref class (kortam)", listOf(
            "kortam", "partam", "korta", "korto", "bolta", "boltam",
            "korte", "korchi", "dhorte", "dhortam", "mortam", "porta", "portam"
        ))

        dump("5. typo tolerance", listOf(
            "thogieco", "thokieco", "thokiyeco", "thokiecho", "tumi", "amke", "amake"
        ))
    }
}
