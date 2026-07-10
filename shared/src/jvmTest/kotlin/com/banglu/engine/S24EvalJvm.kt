package com.banglu.engine

import java.io.File
import kotlin.test.Test

class S24EvalJvm {
    @Test
    fun eval() {
        val input = System.getenv("EVAL_WORDS") ?: return
        val out = System.getenv("EVAL_OUT") ?: return
        val e = ConjunctSolutionRoundJvmTest.engine
        val v = e.javaClass.getDeclaredField("validator").also { it.isAccessible = true }.get(e)
        val getFreq = v.javaClass.getMethod("getFrequency", String::class.java)
        File(out).bufferedWriter().use { w ->
            w.write("word\toutput\tsource\tfreq\n")
            File(input).readLines().forEach { word ->
                val r = e.convertWord(word)
                val freq = if (r.bengali.any { it in 'ঀ'..'৿' }) getFreq.invoke(v, r.bengali) as Int else -1
                w.write("$word\t${r.bengali}\t${r.source}\t$freq\n")
            }
        }
        println("eval done")
    }
}
