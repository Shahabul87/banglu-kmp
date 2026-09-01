package com.banglu.engine.glide

/**
 * S163: the glide template store — every eligible word's ideal path,
 * resampled to [N_POINTS] and quantized to one byte per coordinate
 * (x: grid*20, y: grid*80), 64 bytes a word. 50K words ≈ 3.6MB with
 * the strings; built once per dictionary version and cached.
 *
 * Serialized form (version-gated; any mismatch → null → rebuild):
 *   "BGL1|<dictionaryVersion>|<count>\n"
 *   then count lines "word\tfreq\n"
 *   then count * 64 raw template bytes.
 */
class GlideLexicon private constructor(
    private val wordsArr: Array<String>,
    private val freqsArr: IntArray,
    private val templates: ByteArray,
    private val starts: FloatArray,   // x,y per word
    private val ends: FloatArray,     // x,y per word
    private val lengths: FloatArray,
    private val centroids: FloatArray, // x,y per word — for the aligned shape channel
) {
    val size: Int get() = wordsArr.size
    fun word(i: Int): String = wordsArr[i]
    fun freq(i: Int): Int = freqsArr[i]
    fun start(i: Int): GlidePoint = GlidePoint(starts[2 * i], starts[2 * i + 1])
    fun end(i: Int): GlidePoint = GlidePoint(ends[2 * i], ends[2 * i + 1])
    fun length(i: Int): Float = lengths[i]
    fun centroidX(i: Int): Float = centroids[2 * i]
    fun centroidY(i: Int): Float = centroids[2 * i + 1]

    /** Dequantizes word i's template into [out] as x0,y0,x1,y1,… */
    fun template(i: Int, out: FloatArray) {
        val base = i * BYTES_PER_TEMPLATE
        for (k in 0 until N_POINTS) {
            out[2 * k] = (templates[base + 2 * k].toInt() and 0xFF) / QX
            out[2 * k + 1] = (templates[base + 2 * k + 1].toInt() and 0xFF) / QY
        }
    }

    companion object {
        const val N_POINTS = 32
        const val BYTES_PER_TEMPLATE = N_POINTS * 2
        private const val QX = 20f
        private const val QY = 80f
        private const val MAGIC = "BGL1"

        fun build(words: List<Pair<String, Int>>, grid: GlideGrid): GlideLexicon {
            val eligible = words.filter { (w, _) ->
                w.length >= 2 && w.all { grid.center(it) != null }
            }
            val n = eligible.size
            val wordsArr = Array(n) { eligible[it].first }
            val freqsArr = IntArray(n) { eligible[it].second }
            val templates = ByteArray(n * BYTES_PER_TEMPLATE)
            for (i in 0 until n) {
                // Same normalization as the decoder applies to gestures
                // (resample → smooth): both sides round corners identically,
                // so a clean glide of a zigzag word scores near zero.
                val pts = GlidePath.smooth(
                    GlidePath.resample(eligible[i].first.mapNotNull { grid.center(it) }, N_POINTS)
                )
                val base = i * BYTES_PER_TEMPLATE
                for (k in 0 until N_POINTS) {
                    templates[base + 2 * k] = quant(pts[k].x, QX)
                    templates[base + 2 * k + 1] = quant(pts[k].y, QY)
                }
            }
            return fromParts(wordsArr, freqsArr, templates)
        }

        fun serialize(l: GlideLexicon, dictionaryVersion: String): ByteArray {
            val sb = StringBuilder()
            sb.append(MAGIC).append('|').append(dictionaryVersion).append('|').append(l.size).append('\n')
            for (i in 0 until l.size) sb.append(l.wordsArr[i]).append('\t').append(l.freqsArr[i]).append('\n')
            val head = sb.toString().encodeToByteArray()
            val out = ByteArray(head.size + l.templates.size)
            head.copyInto(out)
            l.templates.copyInto(out, head.size)
            return out
        }

        fun deserialize(bytes: ByteArray, dictionaryVersion: String): GlideLexicon? {
            try {
                var pos = 0
                fun readLine(): String? {
                    if (pos >= bytes.size) return null
                    val nl = bytes.indexOfFrom('\n'.code.toByte(), pos)
                    if (nl < 0) return null
                    val s = bytes.decodeToString(pos, nl)
                    pos = nl + 1
                    return s
                }
                val header = readLine() ?: return null
                val parts = header.split('|')
                if (parts.size != 3 || parts[0] != MAGIC || parts[1] != dictionaryVersion) return null
                val count = parts[2].toIntOrNull() ?: return null
                if (count < 0) return null
                val wordsArr = arrayOfNulls<String>(count)
                val freqsArr = IntArray(count)
                for (i in 0 until count) {
                    val line = readLine() ?: return null
                    val tab = line.indexOf('\t')
                    if (tab <= 0) return null
                    wordsArr[i] = line.substring(0, tab)
                    freqsArr[i] = line.substring(tab + 1).toIntOrNull() ?: return null
                }
                val need = count * BYTES_PER_TEMPLATE
                if (bytes.size - pos != need) return null
                val templates = bytes.copyOfRange(pos, pos + need)
                @Suppress("UNCHECKED_CAST")
                return fromParts(wordsArr as Array<String>, freqsArr, templates)
            } catch (_: Throwable) {
                return null
            }
        }

        private fun fromParts(
            wordsArr: Array<String>,
            freqsArr: IntArray,
            templates: ByteArray,
        ): GlideLexicon {
            val n = wordsArr.size
            val starts = FloatArray(2 * n)
            val ends = FloatArray(2 * n)
            val lengths = FloatArray(n)
            val centroids = FloatArray(2 * n)
            val scratch = FloatArray(BYTES_PER_TEMPLATE)
            val lex = GlideLexicon(wordsArr, freqsArr, templates, starts, ends, lengths, centroids)
            for (i in 0 until n) {
                lex.template(i, scratch)
                starts[2 * i] = scratch[0]
                starts[2 * i + 1] = scratch[1]
                ends[2 * i] = scratch[2 * N_POINTS - 2]
                ends[2 * i + 1] = scratch[2 * N_POINTS - 1]
                var len = 0f
                var cx = scratch[0]
                var cy = scratch[1]
                for (k in 1 until N_POINTS) {
                    val dx = scratch[2 * k] - scratch[2 * k - 2]
                    val dy = scratch[2 * k + 1] - scratch[2 * k - 1]
                    len += kotlin.math.sqrt(dx * dx + dy * dy)
                    cx += scratch[2 * k]
                    cy += scratch[2 * k + 1]
                }
                lengths[i] = len
                centroids[2 * i] = cx / N_POINTS
                centroids[2 * i + 1] = cy / N_POINTS
            }
            return lex
        }

        private fun quant(v: Float, q: Float): Byte {
            val r = (v * q + 0.5f).toInt()
            return r.coerceIn(0, 255).toByte()
        }

        private fun ByteArray.indexOfFrom(b: Byte, from: Int): Int {
            for (i in from until size) if (this[i] == b) return i
            return -1
        }
    }
}
