package com.banglu.engine

import java.io.File
import kotlin.test.Test

/**
 * S158: emits TutorialWords as JSON for non-Kotlin consumers (the bangluweb
 * tutorial page). Opt-in: BANGLU_S158_EXPORT=<output path>. Single source of
 * truth stays shared/TutorialWords.kt — regenerate after any curriculum edit.
 */
class S158TutorialJsonExportJvm {
    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    private fun arr(xs: List<String>) = xs.joinToString(",", "[", "]") { q(it) }

    @Test
    fun export() {
        val out = System.getenv("BANGLU_S158_EXPORT") ?: run { println("S158 export skipped"); return }
        val sb = StringBuilder("{\"families\":[")
        TutorialWords.FAMILIES.forEachIndexed { fi, f ->
            if (fi > 0) sb.append(",")
            sb.append("{\"title\":${q(f.title)},\"tagline\":${q(f.tagline)},\"caps\":[")
            f.caps.forEachIndexed { ci, c ->
                if (ci > 0) sb.append(",")
                sb.append("{\"cap\":${q(c.cap)},\"words\":[")
                c.words.forEachIndexed { wi, w ->
                    if (wi > 0) sb.append(",")
                    sb.append("{\"bn\":${q(w.bengali)},\"roman\":${q(w.roman)},\"split\":${arr(w.split)}")
                    if (w.alts.isNotEmpty()) sb.append(",\"alts\":${arr(w.alts)}")
                    if (w.highlight.isNotEmpty()) sb.append(",\"hl\":${q(w.highlight)}")
                    if (w.twinPrimary.isNotEmpty()) sb.append(",\"twinPrimary\":${q(w.twinPrimary)},\"twinNote\":${q(w.twinNote)}")
                    sb.append("}")
                }
                sb.append("]}")
            }
            sb.append("]}")
        }
        sb.append("]}")
        File(out).writeText(sb.toString())
        println("S158 exported ${TutorialWords.ALL_WORDS.size} words -> $out")
    }
}
