package com.banglu.winime

import com.banglu.winime.composer.Composer
import com.banglu.winime.composer.ComposerAction
import com.banglu.winime.composer.ComposerKey
import kotlin.test.Test

/**
 * S187 (opt-in, WIN_LATENCY=1): what one keystroke costs the Windows typer on
 * a FRESH JVM — the situation every launch puts the user in. Drives the real
 * Composer over AdapterComposerEngine (the production seam) and reports the
 * synchronous per-key conversion, the debounced suggestion query, and the
 * host-app work the echo diff would cause (backspaces + inserted chars).
 */
class S187WinLatencyStudy {
    private val text = ("ami tumi kemon acho bhalo achi ki khobor aj ke dekha hobe na kal ashbo " +
        "amader bangladesh ekta sundor desh bujhte parcina tomar kotha shune khub bhalo laglo " +
        "assalamualaikum walaikumassalam inshallah alhamdulillah dhonnobad shuvo sokal " +
        "bishwabiddaloy shikkha byabostha unnoyon proyojon shorkar ghoshona korechen " +
        "hridoy oxygen hydrogen computer software engineer university student teacher " +
        "korsi jabo khabo ghumabo uthbo kajer chap onek beshi somoy nai taratari koro " +
        "chad rat akash tara megh bristi jhor rod garam thanda shit grishmo borsha " +
        "prothom ditio tritio chaturtho pancham shesh shuru majhe age pore ekhon tokhon " +
        "manush jibon mrittu bhalobasha ghrina asha nirasha shanti juddho shohor gram").trim()

    private fun diff2(from: String, to: String): Pair<Int, Int> { var k = 0; val lim = minOf(from.length, to.length); while (k < lim && from[k] == to[k]) k++; return (from.length - k) to (to.length - k) }

    @Test
    fun keystrokeCost() {
        if (System.getenv("WIN_LATENCY") != "1") return
        TestEngine.boot()
        val engine = AdapterComposerEngine
        val composer = Composer(engine)
        val convertNs = ArrayList<Long>()
        val suggestNs = ArrayList<Long>()
        val backspaces = ArrayList<Int>()
        val inserts = ArrayList<Int>()
        var echoed = ""
        fun echoDiff(target: String) {
            var i = 0; val lim = minOf(echoed.length, target.length)
            while (i < lim && echoed[i] == target[i]) i++
            backspaces += echoed.length - i; inserts += target.length - i; echoed = target
        }
        val words = text.split(' ')
        repeat(3) { round ->
            for (w in words) {
                for (c in w) {
                    val t0 = System.nanoTime()
                    val actions = composer.handle(ComposerKey.Letter(c))
                    convertNs += System.nanoTime() - t0
                    actions.filterIsInstance<ComposerAction.Preview>().lastOrNull()?.let { echoDiff(it.bangla) }
                    val t1 = System.nanoTime()
                    composer.refineCandidates(composer.generation)
                    suggestNs += System.nanoTime() - t1
                }
                composer.handle(ComposerKey.Space); echoed = ""
            }
            fun pct(xs: List<Long>, p: Double) = xs.sorted()[((xs.size - 1) * p).toInt()] / 1e6
            fun report(label: String, xs: List<Long>) =
                println("STUDY\t$label\tn=${xs.size}\tp50=%.2f\tp90=%.2f\tp99=%.2f\tmax=%.2f ms".format(pct(xs, .5), pct(xs, .9), pct(xs, .99), pct(xs, 1.0)))
            val n = convertNs.size
            if (round == 0) {
                report("round0 convert first100", convertNs.take(100)); report("round0 convert 100-400", convertNs.subList(100, minOf(400, n)))
                report("round0 convert rest", convertNs.subList(minOf(400, n), n)); report("round0 suggest first100", suggestNs.take(100)); report("round0 suggest rest", suggestNs.subList(100, n))
            } else {
                val from = n - words.sumOf { it.length }
                report("round$round convert", convertNs.subList(from, n)); report("round$round suggest", suggestNs.subList(from, n))
            }
        }
        // Echo strategies over the same words: (1) full conversion every key
        // (today); (2) rule-only instant every key + one reconcile at commit;
        // per word: injected events = backspaces + inserts.
        var evFull = 0; var evInstant = 0; var bsFull = 0; var bsInstant = 0
        val worst = ArrayList<Pair<Int, String>>()
        for (w in words) {
            var e1 = ""; var e2 = ""; var wb = 0
            for (i in 1..w.length) {
                val raw = w.substring(0, i)
                val full = engine.convert(raw, null, null); val inst = engine.instant(raw)
                fun diff(from: String, to: String): Pair<Int, Int> { var k = 0; val lim = minOf(from.length, to.length); while (k < lim && from[k] == to[k]) k++; return (from.length - k) to (to.length - k) }
                val (b1, i1) = diff(e1, full); evFull += b1 + i1; bsFull += b1; wb += b1; e1 = full
                val (b2, i2) = diff(e2, inst); evInstant += b2 + i2; bsInstant += b2; e2 = inst
            }
            val (b3, i3) = diff2(e2, e1); evInstant += b3 + i3; bsInstant += b3
            worst += wb to w
        }
        println("STUDY\tstrategy full-per-key\tevents=$evFull\tbackspaces=$bsFull")
        println("STUDY\tstrategy instant-per-key+commit\tevents=$evInstant\tbackspaces=$bsInstant")
        println("STUDY\tworst rewrite words\t" + worst.sortedByDescending { it.first }.take(12).joinToString(" ") { "${it.second}:${it.first}" })
        val rewrites = backspaces.count { it > 0 }
        println("STUDY\techo\tkeys=${backspaces.size}\tkeys-with-backspace=$rewrites\tbackspaces-total=${backspaces.sum()}\tinserts-total=${inserts.sum()}\tmax-backspaces=${backspaces.max()}")
    }
}
