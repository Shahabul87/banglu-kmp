# S163 Glide Typing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Drag across the letter keys → the intended word commits (Bangla in BN mode via the existing conversion pipeline, English in EN mode) with alternates on the strip.

**Architecture:** A new, fully additive `com.banglu.engine.glide` package in `shared` (pure geometry+frequency decoder over a compact quantized template lexicon) + an Android gesture/overlay/commit layer. `SmartEngine` and every existing conversion path are untouched.

**Tech Stack:** Kotlin Multiplatform (commonMain/commonTest), JVM study harness on the real sqlite, Compose pointer input on Android.

**Spec:** `docs/superpowers/specs/2026-09-01-glide-typing-design.md`

## Global Constraints

- **Main engine untouched:** no file under `shared/src/commonMain/kotlin/com/banglu/engine/` outside the NEW `glide/` package may be modified, except none at all in cut 1; zero parity-pin flips anywhere.
- Full wall suite green before every commit claim: `./gradlew :shared:jvmTest :shared:testDebugUnitTest :shared:jsNodeTest :desktop-app:test :windows-ime:test :android-keyboard:testDebugUnitTest`.
- Zero added work on the per-keystroke tap path (invariant 1); decode runs once per gesture on `engineLane`.
- Glide disabled in raw/URI, private/sensitive fields, during voice, and when the settings switch is off; switch default ON.
- 2GB budget: BN lexicon ≤ 50K words ≈ 3.6MB (20K in lite mode); dropped on memory pressure.
- Ship chain law: `set -o pipefail`, `DEVICE_SMOKE_CLEAN_INSTALL=1 RUN_DEVICE_SMOKE=1`, no `| tail` without pipefail.

---

### Task 1: GlideGrid + GlidePoint — the canonical key grid

**Files:**
- Create: `shared/src/commonMain/kotlin/com/banglu/engine/glide/GlideGrid.kt`
- Test: `shared/src/commonTest/kotlin/com/banglu/engine/glide/GlideGridTest.kt`

**Interfaces:**
- Produces: `data class GlidePoint(val x: Float, val y: Float)`;
  `class GlideGrid(rowOffsets: FloatArray = DEFAULT_ROW_OFFSETS)` with
  `fun center(c: Char): GlidePoint?` and
  `companion val DEFAULT_ROW_OFFSETS = floatArrayOf(0f, 0.5f, 1.5f)`.
  Unit = one key width; row height = 1 unit; letter rows are
  `qwertyuiop` (y-center 0.5), `asdfghjkl` (1.5), `zxcvbnm` (2.5);
  x-center of the i-th letter in a row = rowOffset + i + 0.5.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.banglu.engine.glide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GlideGridTest {
    private val grid = GlideGrid()

    @Test fun qRowCenters() {
        assertEquals(GlidePoint(0.5f, 0.5f), grid.center('q'))
        assertEquals(GlidePoint(9.5f, 0.5f), grid.center('p'))
    }

    @Test fun homeAndBottomRowUseOffsets() {
        assertEquals(GlidePoint(1.0f, 1.5f), grid.center('a'))
        assertEquals(GlidePoint(9.0f, 1.5f), grid.center('l'))
        assertEquals(GlidePoint(2.0f, 2.5f), grid.center('z'))
        assertEquals(GlidePoint(8.0f, 2.5f), grid.center('m'))
    }

    @Test fun nonLettersHaveNoCenter() {
        assertNull(grid.center('1'))
        assertNull(grid.center('ঁ'))
    }

    @Test fun uppercaseFolds() {
        assertEquals(grid.center('k'), grid.center('K'))
    }
}
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew :shared:jvmTest --tests "com.banglu.engine.glide.GlideGridTest"` → FAIL (unresolved references).

- [ ] **Step 3: Implement**

```kotlin
package com.banglu.engine.glide

data class GlidePoint(val x: Float, val y: Float)

/**
 * S163: the canonical letter grid glide geometry lives in. Unit = one key
 * width, one row height. Android maps pixels to THIS space (x/keyWidthPx,
 * y/rowHeightPx from the letter-rows origin); templates are built in it.
 */
class GlideGrid(private val rowOffsets: FloatArray = DEFAULT_ROW_OFFSETS) {
    private val centers = HashMap<Char, GlidePoint>().apply {
        ROWS.forEachIndexed { r, row ->
            row.forEachIndexed { i, c ->
                put(c, GlidePoint(rowOffsets[r] + i + 0.5f, r + 0.5f))
            }
        }
    }

    fun center(c: Char): GlidePoint? = centers[c.lowercaseChar()]

    companion object {
        val ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val DEFAULT_ROW_OFFSETS = floatArrayOf(0f, 0.5f, 1.5f)
    }
}
```

- [ ] **Step 4: Run to verify pass** — same command → PASS.
- [ ] **Step 5: Commit** — `git add shared/src/commonMain/kotlin/com/banglu/engine/glide/ shared/src/commonTest/kotlin/com/banglu/engine/glide/ && git commit -m "feat(glide): S163 task 1 — canonical key grid"`

> NOTE for Task 6: the Android letter rows' REAL row offsets must be pinned
> against `DEFAULT_ROW_OFFSETS` (a-row visual indent 0.5 key after the S68
> fold; z-row starts after shift). If the real z-row offset differs (e.g.
> shift is 1.5 keys wide), change `DEFAULT_ROW_OFFSETS` THERE, in cut 2, and
> rebuild lexicons — the constant lives in exactly one place for this reason.

### Task 2: GlidePath — resampling, arc length, corners

**Files:**
- Create: `shared/src/commonMain/kotlin/com/banglu/engine/glide/GlidePath.kt`
- Test: `shared/src/commonTest/kotlin/com/banglu/engine/glide/GlidePathTest.kt`

**Interfaces:**
- Produces (all in `object GlidePath`):
  `fun arcLength(p: List<GlidePoint>): Float`;
  `fun resample(p: List<GlidePoint>, n: Int): List<GlidePoint>` (even by arc
  length; single-point input repeats);
  `fun corners(p: List<GlidePoint>): List<Float>` — arc-length FRACTIONS
  (0..1) of direction changes sharper than 55°, computed on the path
  resampled to 24 points, adjacent corners (< 0.08 apart) merged.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.banglu.engine.glide

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlidePathTest {
    private fun p(x: Float, y: Float) = GlidePoint(x, y)

    @Test fun arcLengthOfUnitSquarePath() {
        val path = listOf(p(0f, 0f), p(1f, 0f), p(1f, 1f))
        assertEquals(2f, GlidePath.arcLength(path), 1e-4f)
    }

    @Test fun resampleIsEvenAndKeepsEndpoints() {
        val path = listOf(p(0f, 0f), p(4f, 0f))
        val r = GlidePath.resample(path, 5)
        assertEquals(5, r.size)
        assertEquals(0f, r.first().x, 1e-4f)
        assertEquals(4f, r.last().x, 1e-4f)
        assertEquals(1f, r[1].x, 1e-2f)
    }

    @Test fun singlePointResamples() {
        val r = GlidePath.resample(listOf(p(2f, 2f)), 4)
        assertEquals(4, r.size)
        assertTrue(r.all { abs(it.x - 2f) < 1e-4f })
    }

    @Test fun straightLineHasNoCorners() {
        val path = listOf(p(0f, 0f), p(5f, 0f))
        assertTrue(GlidePath.corners(path).isEmpty())
    }

    @Test fun rightAngleHasOneCornerNearItsFraction() {
        val path = listOf(p(0f, 0f), p(2f, 0f), p(2f, 2f))
        val c = GlidePath.corners(path)
        assertEquals(1, c.size)
        assertTrue(abs(c[0] - 0.5f) < 0.1f)
    }
}
```

- [ ] **Step 2: Run to verify FAIL.**
- [ ] **Step 3: Implement**

```kotlin
package com.banglu.engine.glide

import kotlin.math.acos
import kotlin.math.sqrt

object GlidePath {
    fun arcLength(p: List<GlidePoint>): Float {
        var s = 0f
        for (i in 1 until p.size) s += dist(p[i - 1], p[i])
        return s
    }

    fun dist(a: GlidePoint, b: GlidePoint): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    fun resample(p: List<GlidePoint>, n: Int): List<GlidePoint> {
        if (p.isEmpty() || n <= 0) return emptyList()
        if (p.size == 1) return List(n) { p[0] }
        val total = arcLength(p)
        if (total < 1e-6f) return List(n) { p[0] }
        val step = total / (n - 1)
        val out = ArrayList<GlidePoint>(n)
        out.add(p[0])
        var acc = 0f; var i = 1; var prev = p[0]
        while (out.size < n && i < p.size) {
            val d = dist(prev, p[i])
            if (acc + d >= step - 1e-6f && d > 0f) {
                val t = (step - acc) / d
                val np = GlidePoint(prev.x + t * (p[i].x - prev.x), prev.y + t * (p[i].y - prev.y))
                out.add(np); prev = np; acc = 0f
            } else { acc += d; prev = p[i]; i++ }
        }
        while (out.size < n) out.add(p.last())
        return out
    }

    /** Arc-length fractions of >55° direction changes on a 24-pt resample. */
    fun corners(p: List<GlidePoint>): List<Float> {
        val r = resample(p, 24)
        if (r.size < 3) return emptyList()
        val out = ArrayList<Float>()
        for (i in 1 until r.size - 1) {
            val v1x = r[i].x - r[i - 1].x; val v1y = r[i].y - r[i - 1].y
            val v2x = r[i + 1].x - r[i].x; val v2y = r[i + 1].y - r[i].y
            val n1 = sqrt(v1x * v1x + v1y * v1y); val n2 = sqrt(v2x * v2x + v2y * v2y)
            if (n1 < 1e-5f || n2 < 1e-5f) continue
            val cos = ((v1x * v2x + v1y * v2y) / (n1 * n2)).coerceIn(-1f, 1f)
            val deg = acos(cos) * 180f / kotlin.math.PI.toFloat()
            if (deg > 55f) {
                val frac = i / (r.size - 1).toFloat()
                if (out.isEmpty() || frac - out.last() >= 0.08f) out.add(frac)
            }
        }
        return out
    }
}
```

- [ ] **Step 4: Run to verify PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(glide): S163 task 2 — path resampling and corners"`

### Task 3: GlideLexicon — quantized templates + serialization

**Files:**
- Create: `shared/src/commonMain/kotlin/com/banglu/engine/glide/GlideLexicon.kt`
- Test: `shared/src/commonTest/kotlin/com/banglu/engine/glide/GlideLexiconTest.kt`

**Interfaces:**
- Produces:
  `class GlideLexicon` with `val size: Int`, `fun word(i: Int): String`,
  `fun freq(i: Int): Int`, `fun template(i: Int, out: FloatArray)` (fills
  2*N_POINTS floats x0,y0,x1,y1…), `fun start(i: Int): GlidePoint`,
  `fun end(i: Int): GlidePoint`, `fun length(i: Int): Float`;
  companion: `const val N_POINTS = 32`,
  `fun build(words: List<Pair<String, Int>>, grid: GlideGrid): GlideLexicon`
  (skips words with any letter off-grid or length < 2),
  `fun serialize(l: GlideLexicon): ByteArray`,
  `fun deserialize(bytes: ByteArray, dictionaryVersion: String): GlideLexicon?`
  — header `"BGL1|<dictionaryVersion>|<count>\n"`, returns null on version or
  format mismatch. `serialize` takes the SAME dictionaryVersion parameter:
  `fun serialize(l: GlideLexicon, dictionaryVersion: String): ByteArray`.
- Quantization: x byte = round(x * 20) clamped 0..255 (covers x < 12.75);
  y byte = round(y * 80) clamped 0..255 (covers y < 3.19).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.banglu.engine.glide

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlideLexiconTest {
    private val grid = GlideGrid()
    private val words = listOf("ami" to 90, "kmon" to 80, "kemon" to 70, "x1bad" to 60, "a" to 50)

    @Test fun buildSkipsIneligibleWords() {
        val lex = GlideLexicon.build(words, grid)
        assertEquals(3, lex.size) // x1bad (digit) and "a" (len<2) dropped
        assertEquals("ami", lex.word(0))
        assertEquals(80, lex.freq(1))
    }

    @Test fun templateEndpointsMatchLetterCenters() {
        val lex = GlideLexicon.build(words, grid)
        val s = lex.start(1) // kmon starts at k
        val k = grid.center('k')!!
        assertTrue(abs(s.x - k.x) < 0.06f && abs(s.y - k.y) < 0.06f)
    }

    @Test fun serializeRoundTripsAndChecksVersion() {
        val lex = GlideLexicon.build(words, grid)
        val bytes = GlideLexicon.serialize(lex, "3.9.7")
        val back = GlideLexicon.deserialize(bytes, "3.9.7")!!
        assertEquals(lex.size, back.size)
        assertEquals("kemon", back.word(2))
        val a = FloatArray(GlideLexicon.N_POINTS * 2); val b = FloatArray(GlideLexicon.N_POINTS * 2)
        lex.template(2, a); back.template(2, b)
        for (i in a.indices) assertTrue(abs(a[i] - b[i]) < 1e-4f)
        assertNull(GlideLexicon.deserialize(bytes, "3.9.8"))
        assertNull(GlideLexicon.deserialize(byteArrayOf(1, 2, 3), "3.9.7"))
    }
}
```

- [ ] **Step 2: Run to verify FAIL.**
- [ ] **Step 3: Implement** — templates built by `GlidePath.resample(letters' centers, N_POINTS)`, stored quantized in one `ByteArray(size * 64)`; words in one `Array<String>`; freqs `IntArray`; start/end/length precomputed `FloatArray`s at build AND at deserialize (recomputed from the dequantized templates — single source of truth). Serialization: header line then per word `word\tfreq\t<base of 64 template bytes appended raw after a fixed-size table>` — implementer's choice of exact binary layout as long as the round-trip test and the version gate pass; keep it allocation-light and forward-refusing (unknown magic → null).
- [ ] **Step 4: Run to verify PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(glide): S163 task 3 — quantized template lexicon"`

### Task 4: GlideDecoder + GlideTuning — the scorer

**Files:**
- Create: `shared/src/commonMain/kotlin/com/banglu/engine/glide/GlideDecoder.kt`
- Create: `shared/src/commonMain/kotlin/com/banglu/engine/glide/GlideTuning.kt`
- Test: `shared/src/commonTest/kotlin/com/banglu/engine/glide/GlideDecoderTest.kt`

**Interfaces:**
- Produces: `data class GlideCandidate(val word: String, val score: Float)`;
  `data class GlideTuning(val anchorRadius: Float = 1.6f, val lengthRatio: Float = 2.6f, val freqWeight: Float = 0.16f, val cornerWeight: Float = 0.35f, val maxScore: Float = 1.15f)`;
  `class GlideDecoder(val lexicon: GlideLexicon, val tuning: GlideTuning = GlideTuning())` with
  `fun decode(path: List<GlidePoint>, limit: Int = 6): List<GlideCandidate>`.
- Score (lower better) = meanPointDistance(resampled gesture, template)
  + cornerWeight * |corners(gesture).size − corners(template).size|
  − freqWeight * ln((freq+1)/maxFreqInLexicon). Candidates above
  `maxScore` are dropped (the "commit NOTHING" floor). Empty input,
  1-point input, or gesture arc length < 1.2 → `emptyList()`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.banglu.engine.glide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlideDecoderTest {
    private val grid = GlideGrid()
    private val lex = GlideLexicon.build(
        listOf("ami" to 95, "kmon" to 90, "kemon" to 85, "bangla" to 80,
               "kothay" to 70, "valo" to 60, "krishno" to 40),
        grid
    )
    private val decoder = GlideDecoder(lex)

    private fun cleanPath(word: String): List<GlidePoint> =
        GlidePath.resample(word.mapNotNull { grid.center(it) }, 24)

    @Test fun cleanPathsDecodeTopOne() {
        for (w in listOf("ami", "kemon", "bangla", "kothay")) {
            val out = decoder.decode(cleanPath(w))
            assertEquals(w, out.first().word, "expected $w, got ${out.map { it.word }}")
        }
    }

    @Test fun noisyPathStillTopSix() {
        // hand-jittered kmon: every point pushed ~0.3 keys
        val path = cleanPath("kmon").mapIndexed { i, p ->
            GlidePoint(p.x + if (i % 2 == 0) 0.3f else -0.25f, p.y + if (i % 3 == 0) 0.3f else -0.2f)
        }
        val out = decoder.decode(path)
        assertTrue(out.take(6).any { it.word == "kmon" })
    }

    @Test fun shortOrDegenerateGesturesReturnNothing() {
        assertTrue(decoder.decode(emptyList()).isEmpty())
        assertTrue(decoder.decode(listOf(GlidePoint(1f, 1f))).isEmpty())
        assertTrue(decoder.decode(List(10) { GlidePoint(1f, 1.5f) }).isEmpty()) // no travel
    }

    @Test fun garbagePathCommitsNothing() {
        // a path that starts/ends far from every word's anchors
        val path = listOf(GlidePoint(0.2f, 0.2f), GlidePoint(0.4f, 2.8f), GlidePoint(0.2f, 0.4f))
        assertTrue(decoder.decode(path).isEmpty())
    }
}
```

- [ ] **Step 2: Run to verify FAIL.**
- [ ] **Step 3: Implement** — exactly the probe pipeline, against `GlideLexicon` accessors, with the corner term added and the `maxScore` floor; reuse a single `FloatArray(N_POINTS*2)` scratch per decode call (no per-candidate allocation).
- [ ] **Step 4: Run to verify PASS** (plus `:shared:jsNodeTest` compiles — pure common code must build for JS even though no JS surface calls it).
- [ ] **Step 5: Commit** — `git commit -m "feat(glide): S163 task 4 — shape decoder with corner channel and floor"`

### Task 5: Promote the probe → S163GlideStudyJvm on the real decoder

**Files:**
- Create: `shared/src/jvmTest/kotlin/com/banglu/engine/S163GlideStudyJvm.kt`
- Delete: `shared/src/jvmTest/kotlin/com/banglu/engine/S163GlideProbeJvm.kt` (spike retired by its replacement)

**Interfaces:**
- Consumes: `GlideGrid`, `GlideLexicon.build`, `GlideDecoder.decode`, `GlidePath`.
- Produces: env-gated study (`BANGLU_S163_STUDY=1`, options `BANGLU_S163_SIGMA`, `BANGLU_S163_LEX`, `BANGLU_S163_N`) printing
  `S163 STUDY sigma=… lex=… top1=…% top6=…% none=…% avg=…ms`. `none` = gestures where the floor returned nothing.

- [ ] **Step 1: Port the probe** — same sqlite query (`SELECT key, MAX(frequency) f FROM phonetic_index WHERE priority = 0 GROUP BY key ORDER BY f DESC LIMIT …`, jvmTest cwd is `shared/` so db path candidates `../dictionary.sqlite` then `dictionary.sqlite`), same Gaussian synthesis (seed 163), but decoding through the REAL `GlideDecoder`. Record the three-sigma sweep numbers in the task-5 commit message.
- [ ] **Step 2: Run** — `BANGLU_S163_STUDY=1 ./gradlew :shared:jvmTest --tests "com.banglu.engine.S163GlideStudyJvm" --rerun`; expect top-6 ≥ 92% at σ=0.25 with the corner channel (v0 was 94.0% without corners but with no floor; if the floor pushes `none` above ~3% at σ=0.25, raise `maxScore` and note the chosen value).
- [ ] **Step 3: Full walls green** (Global Constraints command).
- [ ] **Step 4: Commit** — `git commit -m "feat(glide): S163 task 5 — study harness on the real decoder (numbers in message)"`

### Task 6: Android grid mapping + gesture classifier (pure, unit-tested)

**Files:**
- Create: `android-keyboard/src/main/kotlin/com/banglu/keyboard/GlideInput.kt`
- Test: `android-keyboard/src/test/kotlin/com/banglu/keyboard/S163GlideInputTest.kt`

**Interfaces:**
- Consumes: `GlidePoint`, `GlidePath.arcLength`.
- Produces: `class GlideInput(private val minTravelKeys: Float = 1.5f, private val minSampleDist: Float = 0.12f)` with
  `fun begin(startedOnLetter: Boolean, p: GlidePoint)`,
  `fun move(p: GlidePoint)` (drops points closer than `minSampleDist` to the last kept point; caps stored points at 256 by dropping every other OLD point),
  `val isGlide: Boolean` (true once kept-path arc length ≥ `minTravelKeys` AND at least 4 points, only if begun on a letter),
  `fun finish(): List<GlidePoint>` (returns the kept path and resets),
  `fun cancel()` (resets everything; a second pointer calls this).
- FIRST ACTION in this task: read the letter-row weights in
  `ComposeKeyboardView.kt` (q-row cell width, a-row lead spacer, z-row
  shift width) and verify `GlideGrid.DEFAULT_ROW_OFFSETS = [0, 0.5, 1.5]`
  matches `spacerWidth/keyWidth` per row. If not, fix the constant in
  Task 1's file, rerun Tasks 1-5 tests, and note the correction here.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.banglu.keyboard

import com.banglu.engine.glide.GlidePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class S163GlideInputTest {
    @Test fun tapNeverBecomesGlide() {
        val g = GlideInput()
        g.begin(true, GlidePoint(1f, 1.5f))
        g.move(GlidePoint(1.2f, 1.5f))
        assertFalse(g.isGlide)
    }

    @Test fun longTravelOnLettersArms() {
        val g = GlideInput()
        g.begin(true, GlidePoint(1f, 1.5f))
        var x = 1f
        repeat(8) { x += 0.3f; g.move(GlidePoint(x, 1.5f)) }
        assertTrue(g.isGlide)
        assertTrue(g.finish().size >= 4)
        assertFalse(g.isGlide) // finish resets
    }

    @Test fun nonLetterStartNeverArms() {
        val g = GlideInput()
        g.begin(false, GlidePoint(5f, 3.5f)) // spacebar row
        var x = 5f
        repeat(12) { x += 0.4f; g.move(GlidePoint(x, 3.5f)) }
        assertFalse(g.isGlide)
    }

    @Test fun jitterIsDropped() {
        val g = GlideInput()
        g.begin(true, GlidePoint(1f, 1.5f))
        repeat(50) { g.move(GlidePoint(1f + it % 2 * 0.01f, 1.5f)) }
        assertFalse(g.isGlide)
        assertTrue(g.finish().size <= 2)
    }

    @Test fun cancelResets() {
        val g = GlideInput()
        g.begin(true, GlidePoint(1f, 1.5f))
        var x = 1f
        repeat(8) { x += 0.3f; g.move(GlidePoint(x, 1.5f)) }
        g.cancel()
        assertFalse(g.isGlide)
        assertEquals(0, g.finish().size)
    }

    @Test fun pointCapKeepsRecencyAndEndpoints() {
        val g = GlideInput()
        g.begin(true, GlidePoint(0.5f, 0.5f))
        var x = 0.5f
        repeat(600) { x += 0.13f; g.move(GlidePoint(x % 10f, 0.5f)) }
        assertTrue(g.finish().size <= 256)
    }
}
```

- [ ] **Step 2: Run FAIL → Step 3: Implement → Step 4: PASS** (`:android-keyboard:testDebugUnitTest`).
- [ ] **Step 5: Commit** — `git commit -m "feat(glide): S163 task 6 — android gesture classifier + grid-offset pin"`

### Task 7: Glide commit policy (pure) — what to erase, what to commit, how to swap

**Files:**
- Create: `android-keyboard/src/main/kotlin/com/banglu/keyboard/GlideCommitPolicy.kt`
- Test: `android-keyboard/src/test/kotlin/com/banglu/keyboard/S163GlideCommitPolicyTest.kt`

**Interfaces:**
- Produces: `object GlideCommitPolicy` with
  `const val GLIDE_ALT_SOURCE = "glide_alt"`;
  `data class GlideCommitPlan(val eraseComposingChars: Int, val commitText: String)` and
  `fun planCommit(mode: GlideMode, composingLen: Int, editorCharsFromFirstKey: Int, word: String): GlideCommitPlan`
  where `enum class GlideMode { BANGLA, ENGLISH }` — BANGLA erases the
  composing region by resetting the buffer (eraseComposingChars = 0; the
  IC commit replaces composition), ENGLISH erases the raw chars the
  armed-glide's first press already typed (`editorCharsFromFirstKey`,
  normally 1); commitText is always `word + " "`.
  `fun altChip(roman: String, bengali: String): SmartSuggestion` =
  `SmartSuggestion(bengali, 0.8, GLIDE_ALT_SOURCE, roman, "glide_alt")`.
  `fun swapLengths(justCommitted: String, replacement: String): Pair<Int, String>` =
  `justCommitted.length + 1 to replacement + " "` (delete committed word +
  its auto space, commit replacement + space).

- [ ] **Step 1: failing test → Step 3: implement → Step 4: PASS** — pins:
  BANGLA plan never erases editor chars; ENGLISH plan erases exactly
  `editorCharsFromFirstKey`; swapLengths("কেমন", "কেমনে") == (5, "কেমনে ");
  altChip tier/source are `glide_alt`; `TypedChipPolicy.isGhostTier("glide_alt")` is FALSE (alt chips are real chips — blue may land on the first).
- [ ] **Step 5: Commit** — `git commit -m "feat(glide): S163 task 7 — commit/swap policy"`

### Task 8: Lexicon build + cache on Android

**Files:**
- Create: `android-keyboard/src/main/kotlin/com/banglu/keyboard/GlideLexiconStore.kt`
- Modify: `android-keyboard/src/main/kotlin/com/banglu/keyboard/BangluIMEService.kt` (field + lazy arm; exact insertion points chosen at implementation, near the existing store fields)

**Interfaces:**
- Consumes: `GlideLexicon.serialize/deserialize/build`, `DictionaryVersion.REQUIRED`, the dictionary db file path the service already knows (same file `SqlitePhoneticIndexStore` opens), `EnglishWordData` word list.
- Produces: `class GlideLexiconStore(filesDir: File, dbPath: File, liteMode: Boolean)` with
  `suspend fun banglaLexicon(): GlideLexicon?` and `suspend fun englishLexicon(): GlideLexicon?`
  — memory-cached; on miss, tries `glide_bn.bin`/`glide_en.bin` in filesDir
  (version-gated deserialize); on miss, builds (BN: own read-only JDBC-free
  `SQLiteDatabase.openDatabase(dbPath, READONLY)` query, top 50K or 20K in
  lite; EN: top 30K of `EnglishWordData`), writes cache tmp+rename, returns.
  All work on the caller's dispatcher (service calls it on `engineLane`
  launch — NEVER the main thread). `fun dropForMemoryPressure()` clears the
  in-memory refs (files stay). Build failures log + return null (glide
  silently unavailable — never crash the IME).
- Service: `@Volatile private var glideLexiconStore` initialized in the same
  place the phonetic store is; `onTrimMemory` S72 handler additionally calls
  `dropForMemoryPressure()`.

- [ ] Steps: implement; unit-test the pure parts (cache file version gating
  via a temp dir + a fake tiny db is NOT worth a device sqlite dependency —
  instead test `GlideLexiconStore.selectTop(words, cap)` filtering and the
  EN path with the real in-memory wordlist; the BN sqlite query is covered
  by the Task 5 study and the device gate). Full walls. Commit
  `git commit -m "feat(glide): S163 task 8 — on-device lexicon build + cache"`.

### Task 9: Gesture wiring + trail overlay + commit flow in the IME

**Files:**
- Modify: `android-keyboard/src/main/kotlin/com/banglu/keyboard/ComposeKeyboardView.kt`
  (letter-rows container: Initial-pass pointer observer feeding `GlideInput`;
  trail overlay Canvas; `glideActiveProvider` consulted by key handlers)
- Modify: `android-keyboard/src/main/kotlin/com/banglu/keyboard/BangluIMEService.kt`
  (`kbOnGlideComplete`, decode on engineLane, convert candidates, commit via
  `GlideCommitPolicy`, alt chips into `suggestions`, `glide_alt` tap branch
  in `onSuggestionTap` doing the swap, settings gate)

**Hard rules from past rounds (verbatim constraints for the implementer):**
- S15: never leave a second release-wait after a pointer sequence ends.
- S13/S32: keys commit on DOWN — the glide's first letter is ALREADY in the
  composing buffer when the glide arms; BN commit resets `buffer` before
  committing (composition replace), EN erases per `GlideCommitPolicy`.
- The observer runs in `PointerEventPass.Initial` and only OBSERVES until
  `GlideInput.isGlide` flips; from that moment it CONSUMES changes so key
  handlers see the stream end, and sets `glideActive` so KeyButton
  long-press/alternate paths abort (check the flag before firing).
- The S133 frozen-strip observer and S32 spacebar drag are untouched: the
  glide observer ignores sequences that begin on non-letter keys
  (`GlideInput.begin(startedOnLetter=false…)` never arms).
- Trail: `mutableStateListOf<GlidePoint>` capped 64, drawn in an overlay
  `Canvas` with `graphicsLayer` alpha fade; points appended from the
  observer; cleared on finish/cancel. No allocation in the draw lambda.
- Decode + convert on `engineLane`; commit back on the main handler like
  every other async commit; the no-candidate case draws the 150ms red trail
  flash and commits nothing.
- Gates: `glideTypingEnabled.value`, not raw/URI, not private/sensitive, not
  during voice, BN or EN letter layers only.
- Alt chips: top-1 commits; candidates 2..6 convert (BN) and ride the strip
  as `glide_alt` chips; tap = swap via `swapLengths` + `learnCommittedWordAsync(roman, bengali, explicitChoice = true)`.

- [ ] Implement; `:android-keyboard:testDebugUnitTest` + compilePerfKotlin green; full walls green.
- [ ] Commit — `git commit -m "feat(glide): S163 task 9 — gesture wiring, trail, commit flow"`

### Task 10: Settings switch

**Files:**
- Modify: `android-keyboard/src/main/kotlin/com/banglu/keyboard/SettingsActivity.kt` (new row "গ্লাইড টাইপিং", subtitle "আঙুল টেনে লিখুন — ছেড়ে দিলেই শব্দ বসে", key `glide_typing_enabled`, default true, placed beside the সাজেশন switch)
- Modify: `BangluIMEService.kt` `reloadSettings()` reads it into `glideTypingEnabled` (a `mutableStateOf(true)` service field — S95 pattern)

- [ ] Implement; unit walls; commit `git commit -m "feat(glide): S163 task 10 — settings switch (default ON)"`.

### Task 11: Device verification + tuning + ship

- [ ] Perf build → install (uninstall first if signature mismatch) → full IME rebind → Samsung Notes protocol (create note, COORDINATE key taps — `adb input text` bypasses the IME; a glide is `adb shell input swipe` chains or manual user test — prefer asking the user to glide kmon/ami/bangla and report, plus screenshot of trail + alt chips).
- [ ] Screenshot design review (trail color/fade, chip layout) — fix-first rule.
- [ ] Re-run `S163GlideStudyJvm` sweep; record final tuning values in the commit.
- [ ] CLAUDE.md status entry + memory update.
- [ ] Version bump (next free 1.5.x, code +1) in `android-keyboard/build.gradle.kts`.
- [ ] Certified ship chain: commit → `set -o pipefail && DEVICE_SMOKE_CLEAN_INSTALL=1 RUN_DEVICE_SMOKE=1 bash scripts/validate_android_release.sh` → releases copy → tag → push main+tag → `banglu-android.apk` alias (upload a COPY named exactly `banglu-android.apk`, verify by sha) → perf restore + rebind.

## Self-review notes

- Spec coverage: grid/decoder/lexicon (§4.1-4.2 → Tasks 1-5), Android layer
  (§4.3 → Tasks 6-9), settings (§4.4 → Task 10), edges (§5 → Tasks 4 floor,
  6 cancel/cap, 8 pressure-drop, 9 gates), budgets (§6 → Task 5 latency +
  Task 9 allocation rules), testing (§7 → every task + Task 11), rollout
  (§8 → commit-per-task, ship in Task 11). Backspace-deletes-whole-word
  (§5 last bullet) is NOT in v1 tasks — S88 resume machinery already treats
  a committed word as one unit for backspace-resume; verify behavior on
  device in Task 11 and only add work if the observed behavior contradicts
  the spec (noted deliberately, not a placeholder).
- Type consistency: `GlidePoint(Float,Float)` everywhere; lexicon accessor
  names match between Tasks 3/4/5/8; `glide_alt` source/tier match 7/9.
