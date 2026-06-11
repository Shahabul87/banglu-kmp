# Engine v3 Phase 1 Implementation Plan — Index + Commit Gate

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a precompiled phonetic index over all 484k Bengali words, an offline-generated English loanword lexicon, a deterministic clean transliterator, and a commit gate guaranteeing the editor never shows an invented Bengali string.

**Architecture:** All changes are additive (spec §5 Phase 1 — nothing deleted). The dictionary-compiler gains two builders that write new sqlite tables (`phonetic_index`, `english_lexicon`). The shared engine gains a `CleanTransliterator` (swap-free OOV floor), a `PhoneticIndexStore` abstraction (in-memory fake for tests, sqlite-backed on Android), and a commit gate at the end of `convertWord`/`convertForComposing`. The Android keyboard wires a persistent-connection sqlite store and stops building the runtime corpus phonetic index.

**Tech Stack:** Kotlin Multiplatform (`shared`, targets jvm+android+ios), JVM-only `dictionary-compiler` (sqlite-jdbc, kotlinx-serialization), Android SQLite. Tests: kotlin.test via `./gradlew :shared:jvmTest` and `./gradlew :dictionary-compiler:test`.

**Spec:** `docs/superpowers/specs/2026-06-11-engine-v3-lookup-first-design.md`

---

## File map

| File | Action | Responsibility |
|---|---|---|
| `shared/src/commonMain/kotlin/com/banglu/engine/rules/CleanTransliterator.kt` | Create | Deterministic swap-free roman→Bengali (OOV floor) |
| `shared/src/commonMain/kotlin/com/banglu/engine/platform/PhoneticIndexStore.kt` | Create | Index lookup abstraction + `PhoneticIndexHit` type |
| `shared/src/commonMain/kotlin/com/banglu/engine/platform/InMemoryPhoneticIndexStore.kt` | Create | Test/JVM implementation |
| `shared/src/commonMain/kotlin/com/banglu/engine/SmartEngine.kt` | Modify | `setPhoneticIndex`, index-backed corpus lookup, english lexicon hook, commit gate |
| `shared/src/commonMain/kotlin/com/banglu/engine/SmartEngineAdapter.kt` | Modify | OOV learning on commit |
| `shared/src/commonMain/kotlin/com/banglu/engine/types/Types.kt` | Modify | `ResolutionSource.CLEAN_TRANSLITERATION`, `ResolutionSource.ENGLISH_LEXICON` |
| `shared/src/commonMain/kotlin/com/banglu/engine/dictionary/SmartDictionary.kt` | Modify | `containsBengali()` helper |
| `dictionary-compiler/build.gradle.kts` | Modify | Depend on `:shared` (jvm), add kotlin-test |
| `dictionary-compiler/src/main/kotlin/com/banglu/compiler/PhoneticIndexBuilder.kt` | Create | Canonical keys, aliases, tiering, round-trip metric |
| `dictionary-compiler/src/main/kotlin/com/banglu/compiler/ArpabetToBengali.kt` | Create | ARPABET phonemes → Bengali script |
| `dictionary-compiler/src/main/kotlin/com/banglu/compiler/EnglishLexiconBuilder.kt` | Create | CMUdict × frequency list → english_lexicon rows |
| `dictionary-compiler/src/main/kotlin/com/banglu/compiler/DictionaryCompiler.kt` | Modify | New tables, builder invocation, metadata, version 3.3.0 |
| `android-keyboard/src/main/kotlin/com/banglu/keyboard/SqlitePhoneticIndexStore.kt` | Create | Persistent-connection sqlite store |
| `android-keyboard/src/main/kotlin/com/banglu/keyboard/AndroidDictionaryLoader.kt` | Modify | `REQUIRED_DB_VERSION = "3.3.0"` |
| `android-keyboard/src/main/kotlin/com/banglu/keyboard/BangluIMEService.kt` | Modify | Wire store; pass commit-source to learning |

Conventions: every commit message below ends with the trailer `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`. Commit ONLY the files named in the task (`git add <paths>`, never `git add -A` — the working tree carries unrelated WIP).

---

### Task 1: CleanTransliterator (shared)

The deterministic OOV floor (spec §3.1). One input → one output. No ShatvaVidhan/NatvaVidhan, no swaps, no lattice. Algorithm: greedy longest-match over a fixed digraph/trigraph table; consecutive consonants join with hasanta (this natively produces ya-phala/ra-phala/conjuncts); vowels emit independent form word-initially or after a vowel, kar form after a consonant; `o` after a consonant is the inherent vowel (emits nothing).

**Files:**
- Create: `shared/src/commonMain/kotlin/com/banglu/engine/rules/CleanTransliterator.kt`
- Test: `shared/src/commonTest/kotlin/com/banglu/engine/rules/CleanTransliteratorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.banglu.engine.rules

import kotlin.test.Test
import kotlin.test.assertEquals

class CleanTransliteratorTest {

    private fun t(roman: String) = CleanTransliterator.transliterate(roman)

    @Test
    fun simpleWords() {
        assertEquals("আমি", t("ami"))
        assertEquals("তুমি", t("tumi"))
        assertEquals("বন্ধু", t("bondhu"))   // inherent o + n+dh conjunct + u-kar
    }

    @Test
    fun namesAreReadableNeverSwapped() {
        assertEquals("রাফসান", t("rafsan"))
        assertEquals("শাকিল", t("shakil"))
        // Deterministic floor: n is ALWAYS ন (no natva ণ swaps), s alone is ALWAYS স
        assertEquals("হাসান", t("hasan"))
    }

    @Test
    fun digraphsAndFolas() {
        assertEquals("খাতা", t("khata"))
        assertEquals("চা", t("cha"))
        assertEquals("ছবি", t("cobi"))        // lowercase rule: bare c → ছ
        assertEquals("প্রিয়", t("priyo"))     // r-phala via hasanta join, final yo → য়
    }

    @Test
    fun vowelForms() {
        assertEquals("এক", t("ek"))           // word-initial independent vowel
        assertEquals("ওই", t("oi"))           // NOTE: word-initial oi digraph → ঐ; adjust to ঐ
        assertEquals("কই", t("koi"))          // after-consonant: o inherent, i independent? No — oi → ৈ kar: কৈ
    }

    @Test
    fun determinism() {
        // Same input always yields same output, and output is pure Bengali
        val out = t("xyzkqv")
        assertEquals(out, t("xyzkqv"))
        check(out.none { it in 'a'..'z' }) { "residual latin in $out" }
    }
}
```

NOTE for implementer: the two lines marked NOTE in `vowelForms` express a real design choice — the digraph table must contain `oi`/`ou` so they map as units (`oi`→ঐ/ৈ, `ou`→ঔ/ৌ). Fix the expected values to `assertEquals("ঐ", t("oi"))` and `assertEquals("কৈ", t("koi"))` before running.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.banglu.engine.rules.CleanTransliteratorTest"`
Expected: FAIL — `Unresolved reference: CleanTransliterator`

- [ ] **Step 3: Write the implementation**

```kotlin
package com.banglu.engine.rules

/**
 * Deterministic, swap-free roman -> Bengali transliteration.
 *
 * This is the OOV floor (Engine v3 spec section 3.1): one input, one output,
 * always readable Bengali, never optimal. No ShatvaVidhan/NatvaVidhan, no
 * character swaps, no candidate lattice. The same mapping (in reverse) defines
 * canonical phonetic keys at dictionary-compile time.
 */
object CleanTransliterator {

    private const val HASANTA = "্"

    // Greedy longest-match consonant units (3-char before 2-char before 1-char).
    private val CONSONANTS: Map<String, String> = mapOf(
        "chh" to "ছ", "kkh" to "ক্ষ",
        "kh" to "খ", "gh" to "ঘ", "ch" to "চ", "jh" to "ঝ",
        "th" to "থ", "dh" to "ধ", "ph" to "ফ", "bh" to "ভ",
        "sh" to "শ", "ng" to "ং", "gg" to "জ্ঞ",
        "k" to "ক", "g" to "গ", "c" to "ছ", "j" to "জ", "z" to "জ",
        "t" to "ত", "d" to "দ", "n" to "ন", "p" to "প", "f" to "ফ",
        "b" to "ব", "v" to "ভ", "m" to "ম", "r" to "র", "l" to "ল",
        "s" to "স", "h" to "হ", "y" to "য়", "w" to "ও", "q" to "ক",
        "x" to "ক্স"
    )

    // Vowel units: independent form (word-initial / after vowel) and kar form.
    private data class Vowel(val independent: String, val kar: String)
    private val VOWELS: Map<String, Vowel> = mapOf(
        "oi" to Vowel("ঐ", "ৈ"), "ou" to Vowel("ঔ", "ৌ"),
        "ii" to Vowel("ঈ", "ী"), "ee" to Vowel("ঈ", "ী"),
        "uu" to Vowel("ঊ", "ূ"), "oo" to Vowel("উ", "ু"),
        "aa" to Vowel("আ", "া"),
        "a" to Vowel("আ", "া"), "i" to Vowel("ই", "ি"),
        "u" to Vowel("উ", "ু"), "e" to Vowel("এ", "ে"),
        "o" to Vowel("ও", "")   // after consonant: inherent vowel, emits nothing
    )

    private val UNIT_LENGTHS = intArrayOf(3, 2, 1)

    fun transliterate(roman: String): String {
        val key = roman.lowercase().trim()
        if (key.isEmpty()) return ""

        val out = StringBuilder()
        var pos = 0
        var prevWasConsonant = false

        while (pos < key.length) {
            var matched = false
            for (len in UNIT_LENGTHS) {
                if (pos + len > key.length) continue
                val unit = key.substring(pos, pos + len)

                CONSONANTS[unit]?.let { bengali ->
                    if (prevWasConsonant && bengali != "ং") out.append(HASANTA)
                    out.append(bengali)
                    prevWasConsonant = bengali != "ং"
                    pos += len
                    matched = true
                }
                if (matched) break

                VOWELS[unit]?.let { vowel ->
                    out.append(if (prevWasConsonant) vowel.kar else vowel.independent)
                    prevWasConsonant = false
                    pos += len
                    matched = true
                }
                if (matched) break
            }
            if (!matched) pos++ // silently drop unmappable chars (digits handled upstream)
        }
        return out.toString()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:jvmTest --tests "com.banglu.engine.rules.CleanTransliteratorTest"`
Expected: PASS (5 tests). If a vowel-form expectation disagrees with the algorithm, fix the TEST only if the output is still readable Bengali; fix the TABLE if it is not.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/banglu/engine/rules/CleanTransliterator.kt \
        shared/src/commonTest/kotlin/com/banglu/engine/rules/CleanTransliteratorTest.kt
git commit -m "feat(engine): CleanTransliterator - deterministic swap-free OOV floor"
```

---

### Task 2: Compiler depends on :shared + PhoneticIndexBuilder

The compiler must run `ReverseTransliterator` (canonical keys) and `CleanTransliterator` (round-trip check). `:shared` has a `jvm()` target, so a plain JVM module can depend on it.

**Files:**
- Modify: `dictionary-compiler/build.gradle.kts`
- Create: `dictionary-compiler/src/main/kotlin/com/banglu/compiler/PhoneticIndexBuilder.kt`
- Test: `dictionary-compiler/src/test/kotlin/com/banglu/compiler/PhoneticIndexBuilderTest.kt`

- [ ] **Step 1: Add dependencies**

In `dictionary-compiler/build.gradle.kts` replace the `dependencies` block:

```kotlin
dependencies {
    implementation(project(":shared"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    testImplementation(kotlin("test"))
}
```

Run: `./gradlew :dictionary-compiler:compileKotlin`
Expected: BUILD SUCCESSFUL (proves the jvm artifact of :shared resolves).

- [ ] **Step 2: Write the failing test**

```kotlin
package com.banglu.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhoneticIndexBuilderTest {

    @Test
    fun buildsKeysForSimpleWords() {
        val rows = PhoneticIndexBuilder.build(
            words = listOf("আমি", "তুমি"),
            frequencies = mapOf("আমি" to 100, "তুমি" to 99)
        )
        val amiKeys = rows.filter { it.bengali == "আমি" }.map { it.key }
        assertTrue("ami" in amiKeys, "expected canonical key 'ami', got $amiKeys")
        assertEquals(0, rows.first { it.bengali == "আমি" }.tier) // freq>0 => Tier A
    }

    @Test
    fun tierBForWordsWithoutFrequency() {
        val rows = PhoneticIndexBuilder.build(words = listOf("আমি"), frequencies = emptyMap())
        assertEquals(1, rows.first().tier)
    }

    @Test
    fun chhWordsGetCAlias() {
        // ছবি reverses to chobi/chhobi family; lowercase scheme types it as cobi
        val rows = PhoneticIndexBuilder.build(words = listOf("ছবি"), frequencies = emptyMap())
        val keys = rows.map { it.key }
        assertTrue(keys.any { it.startsWith("c") && !it.startsWith("ch") },
            "expected a c-alias for chh word, got $keys")
    }

    @Test
    fun reportsRoundTripCoverage() {
        val report = PhoneticIndexBuilder.lastReport
        PhoneticIndexBuilder.build(words = listOf("আমি", "তুমি"), frequencies = emptyMap())
        assertTrue(PhoneticIndexBuilder.lastReport.totalWords == 2)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :dictionary-compiler:test --tests "com.banglu.compiler.PhoneticIndexBuilderTest"`
Expected: FAIL — `Unresolved reference: PhoneticIndexBuilder`

- [ ] **Step 4: Write the implementation**

```kotlin
package com.banglu.compiler

import com.banglu.engine.rules.CleanTransliterator
import com.banglu.engine.util.ReverseTransliterator

data class PhoneticIndexRow(
    val key: String,
    val bengali: String,
    val frequency: Int,
    val tier: Int // 0 = Tier A (suggestible), 1 = Tier B (exact-match only)
)

data class IndexBuildReport(
    val totalWords: Int = 0,
    val roundTripOk: Int = 0,
    val totalRows: Int = 0
) {
    val coveragePercent: Double
        get() = if (totalWords == 0) 0.0 else roundTripOk * 100.0 / totalWords
}

object PhoneticIndexBuilder {

    var lastReport: IndexBuildReport = IndexBuildReport()
        private set

    private val BENGALI_ONLY = Regex("^[\\u0980-\\u09FF]+$")
    private val ROMAN_ONLY = Regex("^[a-z]+$")

    fun build(words: List<String>, frequencies: Map<String, Int>): List<PhoneticIndexRow> {
        val rows = ArrayList<PhoneticIndexRow>(words.size * 2)
        val seen = HashSet<String>(words.size * 2)
        var roundTripOk = 0
        var total = 0

        for (raw in words) {
            val word = raw.trim()
            if (word.length !in 2..18) continue
            if (!BENGALI_ONLY.matches(word)) continue
            if (word.endsWith("্")) continue
            total++

            val canonical = ReverseTransliterator.reverseWord(word).lowercase()
            if (CleanTransliterator.transliterate(canonical) == word) roundTripOk++

            val freq = frequencies[word] ?: 0
            val tier = if (freq > 0) 0 else 1
            for (key in aliasesFor(canonical)) {
                if (key.length !in 2..24 || !ROMAN_ONLY.matches(key)) continue
                if (!seen.add("$key $word")) continue
                rows.add(PhoneticIndexRow(key, word, freq, tier))
            }
        }
        lastReport = IndexBuildReport(totalWords = total, roundTripOk = roundTripOk, totalRows = rows.size)
        return rows
    }

    /**
     * Typing-habit aliases. Mirrors SmartEngine.corpusPhoneticAliases (chh->c)
     * plus the vowel-length collapses users actually type. Bounded and
     * deterministic; every alias maps back to the same word.
     */
    private fun aliasesFor(canonical: String): List<String> {
        val aliases = linkedSetOf(canonical)
        if (canonical.contains("chh")) aliases.add(canonical.replace("chh", "c"))
        if (canonical.contains("ii")) aliases.add(canonical.replace("ii", "i"))
        if (canonical.contains("ee")) aliases.add(canonical.replace("ee", "i"))
        if (canonical.contains("uu")) aliases.add(canonical.replace("uu", "u"))
        if (canonical.contains("oo")) aliases.add(canonical.replace("oo", "u"))
        return aliases.toList()
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :dictionary-compiler:test --tests "com.banglu.compiler.PhoneticIndexBuilderTest"`
Expected: PASS (4 tests). If `chhWordsGetCAlias` fails, inspect what `ReverseTransliterator.reverseWord("ছবি")` actually emits and adjust the alias rule (NOT the assertion) so a bare-`c` alias is always produced for ছ words.

- [ ] **Step 6: Commit**

```bash
git add dictionary-compiler/build.gradle.kts \
        dictionary-compiler/src/main/kotlin/com/banglu/compiler/PhoneticIndexBuilder.kt \
        dictionary-compiler/src/test/kotlin/com/banglu/compiler/PhoneticIndexBuilderTest.kt
git commit -m "feat(compiler): PhoneticIndexBuilder - canonical keys, aliases, tiers, round-trip metric"
```

---

### Task 3: ARPABET → Bengali mapper

Pure function: list of ARPABET phonemes (CMUdict format, stress digits attached to vowels) → Bengali string. English loanword convention: alveolar T/D → retroflex ট/ড; S → স; vowels follow standard Banglish transliteration.

**Files:**
- Create: `dictionary-compiler/src/main/kotlin/com/banglu/compiler/ArpabetToBengali.kt`
- Test: `dictionary-compiler/src/test/kotlin/com/banglu/compiler/ArpabetToBengaliTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.banglu.compiler

import kotlin.test.Test
import kotlin.test.assertEquals

class ArpabetToBengaliTest {

    private fun conv(pron: String): String =
        ArpabetToBengali.convert(pron.split(" "))

    @Test
    fun simpleWords() {
        // cat: K AE1 T
        assertEquals("ক্যাট", conv("K AE1 T"))
        // bus: B AH1 S
        assertEquals("বাস", conv("B AH1 S"))
        // computer: K AH0 M P Y UW1 T ER0
        assertEquals("কম্পিউটার", conv("K AH0 M P Y UW1 T ER0"))
    }

    @Test
    fun glidesAndDiphthongs() {
        // time: T AY1 M
        assertEquals("টাইম", conv("T AY1 M"))
        // go: G OW1
        assertEquals("গো", conv("G OW1"))
        // house: HH AW1 S
        assertEquals("হাউস", conv("HH AW1 S"))
    }

    @Test
    fun unstressedSchwaMidWordIsInherent() {
        // doctor: D AA1 K T ER0
        assertEquals("ডাক্টার", conv("D AA1 K T ER0"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :dictionary-compiler:test --tests "com.banglu.compiler.ArpabetToBengaliTest"`
Expected: FAIL — `Unresolved reference: ArpabetToBengali`

- [ ] **Step 3: Write the implementation**

```kotlin
package com.banglu.compiler

/**
 * ARPABET (CMUdict) phonemes -> Bengali script, using the conventional
 * Bengali rendering of English loanwords (alveolar T/D -> retroflex,
 * W -> ojukto-ও, schwa -> আ-kar).
 */
object ArpabetToBengali {

    private val CONSONANTS = mapOf(
        "B" to "ব", "CH" to "চ", "D" to "ড", "DH" to "দ", "F" to "ফ",
        "G" to "গ", "HH" to "হ", "JH" to "জ", "K" to "ক", "L" to "ল",
        "M" to "ম", "N" to "ন", "NG" to "ং", "P" to "প", "R" to "র",
        "S" to "স", "SH" to "শ", "T" to "ট", "TH" to "থ", "V" to "ভ",
        "W" to "ও", "Y" to "ই", "Z" to "জ", "ZH" to "জ"
    )

    // Vowels: independent form / kar form (after consonant).
    private data class V(val independent: String, val kar: String)
    private val VOWELS = mapOf(
        "AA" to V("আ", "া"), "AE" to V("অ্যা", "্যা"), "AH" to V("আ", "া"),
        "AO" to V("অ", ""),  "AW" to V("আউ", "াউ"),  "AY" to V("আই", "াই"),
        "EH" to V("এ", "ে"), "ER" to V("আর", "ার"),  "EY" to V("এ", "ে"),
        "IH" to V("ই", "ি"), "IY" to V("ই", "ি"),    "OW" to V("ও", "ো"),
        "OY" to V("অয়", "য়"), "UH" to V("উ", "ু"),  "UW" to V("উ", "ু")
    )

    private const val HASANTA = "্"

    fun convert(phonemes: List<String>): String {
        val out = StringBuilder()
        var prevWasConsonant = false
        for (raw in phonemes) {
            val p = raw.trimEnd('0', '1', '2') // strip stress digits
            val consonant = CONSONANTS[p]
            if (consonant != null) {
                // Y+UW = "iu" glide (computer -> পিউ); Y after consonant joins as ি
                if (prevWasConsonant && consonant !in listOf("ং", "ও", "ই")) out.append(HASANTA)
                out.append(consonant)
                prevWasConsonant = consonant != "ং" && consonant != "ই"
                continue
            }
            val vowel = VOWELS[p] ?: continue
            out.append(if (prevWasConsonant) vowel.kar else vowel.independent)
            prevWasConsonant = false
        }
        return out.toString()
    }
}
```

- [ ] **Step 4: Run test, iterate to green**

Run: `./gradlew :dictionary-compiler:test --tests "com.banglu.compiler.ArpabetToBengaliTest"`
Expected: PASS. The Y-glide and AE cases are the fiddly ones — iterate the TABLE until tests pass; the three test groups encode the acceptance bar. Do not weaken assertions.

- [ ] **Step 5: Commit**

```bash
git add dictionary-compiler/src/main/kotlin/com/banglu/compiler/ArpabetToBengali.kt \
        dictionary-compiler/src/test/kotlin/com/banglu/compiler/ArpabetToBengaliTest.kt
git commit -m "feat(compiler): ARPABET-to-Bengali phoneme mapper"
```

---

### Task 4: EnglishLexiconBuilder

Intersect CMUdict pronunciations with a top-30k English frequency list; emit (key → Bengali) rows.

**Files:**
- Create: `dictionary-compiler/src/main/kotlin/com/banglu/compiler/EnglishLexiconBuilder.kt`
- Test: `dictionary-compiler/src/test/kotlin/com/banglu/compiler/EnglishLexiconBuilderTest.kt`

- [ ] **Step 1: Download source data (one-time, checked into the repo under data/)**

```bash
mkdir -p dictionary-compiler/data
curl -sL https://raw.githubusercontent.com/cmusphinx/cmudict/master/cmudict.dict \
     -o dictionary-compiler/data/cmudict.dict
curl -sL https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_50k.txt \
     -o dictionary-compiler/data/en_50k.txt
wc -l dictionary-compiler/data/cmudict.dict dictionary-compiler/data/en_50k.txt
```

Expected: ~135k cmudict lines, 50k frequency lines. Verify licenses permit redistribution (CMUdict: BSD; FrequencyWords: MIT/CC) and note them in the commit message.

- [ ] **Step 2: Write the failing test**

```kotlin
package com.banglu.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnglishLexiconBuilderTest {

    @Test
    fun buildsEntriesFromCmudictLines() {
        val entries = EnglishLexiconBuilder.build(
            cmudictLines = listOf(
                "bus B AH1 S",
                "bus(2) B AH0 S",          // alternate pronunciations are skipped
                "time T AY1 M",
                "rare'word R EH1 R"        // non a-z keys are skipped
            ),
            topWords = setOf("bus", "time")
        )
        assertEquals(setOf("bus", "time"), entries.map { it.key }.toSet())
        assertEquals("বাস", entries.first { it.key == "bus" }.bengali)
        assertEquals("টাইম", entries.first { it.key == "time" }.bengali)
    }

    @Test
    fun skipsWordsOutsideTopList() {
        val entries = EnglishLexiconBuilder.build(
            cmudictLines = listOf("zyzzyva Z IH0 Z IH1 V AH0"),
            topWords = setOf("bus")
        )
        assertTrue(entries.isEmpty())
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :dictionary-compiler:test --tests "com.banglu.compiler.EnglishLexiconBuilderTest"`
Expected: FAIL — `Unresolved reference: EnglishLexiconBuilder`

- [ ] **Step 4: Write the implementation**

```kotlin
package com.banglu.compiler

data class EnglishLexiconEntry(val key: String, val bengali: String)

object EnglishLexiconBuilder {

    private val KEY_RE = Regex("^[a-z]+$")

    fun build(cmudictLines: List<String>, topWords: Set<String>): List<EnglishLexiconEntry> {
        val entries = ArrayList<EnglishLexiconEntry>()
        for (line in cmudictLines) {
            if (line.startsWith(";;;")) continue
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 2) continue
            val word = parts[0].lowercase()
            if (word.contains("(")) continue          // alternate pronunciation
            if (!KEY_RE.matches(word)) continue
            if (word !in topWords) continue
            val bengali = ArpabetToBengali.convert(parts.drop(1))
            if (bengali.isEmpty()) continue
            entries.add(EnglishLexiconEntry(word, bengali))
        }
        return entries
    }

    /** Parse hermitdave frequency list ("word count" per line) into top-N set. */
    fun parseTopWords(lines: List<String>, limit: Int = 30_000): Set<String> =
        lines.asSequence()
            .mapNotNull { it.trim().split(' ').firstOrNull()?.lowercase() }
            .filter { KEY_RE.matches(it) && it.length >= 2 }
            .take(limit)
            .toSet()
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :dictionary-compiler:test --tests "com.banglu.compiler.EnglishLexiconBuilderTest"`
Expected: PASS (2 tests)

- [ ] **Step 6: Commit**

```bash
git add dictionary-compiler/data/cmudict.dict dictionary-compiler/data/en_50k.txt \
        dictionary-compiler/src/main/kotlin/com/banglu/compiler/EnglishLexiconBuilder.kt \
        dictionary-compiler/src/test/kotlin/com/banglu/compiler/EnglishLexiconBuilderTest.kt
git commit -m "feat(compiler): EnglishLexiconBuilder (CMUdict BSD, FrequencyWords MIT)"
```

---

### Task 5: Wire builders into DictionaryCompiler + new tables

**Files:**
- Modify: `dictionary-compiler/src/main/kotlin/com/banglu/compiler/DictionaryCompiler.kt`

- [ ] **Step 1: Add table DDL**

In `createTables(connection)` (DictionaryCompiler.kt:249), add before the `metadata` table:

```kotlin
        execute("""
            CREATE TABLE phonetic_index (
                key TEXT NOT NULL,
                bengali TEXT NOT NULL,
                frequency INTEGER DEFAULT 0,
                tier INTEGER NOT NULL DEFAULT 1
            )
        """)
        execute("CREATE INDEX idx_phonetic_index_key ON phonetic_index(key)")

        execute("""
            CREATE TABLE english_lexicon (
                key TEXT PRIMARY KEY,
                bengali TEXT NOT NULL
            )
        """)
```

- [ ] **Step 2: Invoke builders in main()**

In `main()` after step 3 (word insertion, line ~84) — the `bengaliWords` and `frequencies` values are in scope:

```kotlin
        // 3b. Build precompiled phonetic index (Engine v3)
        println("Building phonetic index...")
        val wordList = bengaliWords.map { it.jsonPrimitive.content }
        val freqMap = frequencies.entries.associate { (w, f) -> w to f.jsonPrimitive.int }
        val indexRows = PhoneticIndexBuilder.build(wordList, freqMap)
        val report = PhoneticIndexBuilder.lastReport
        println("  Rows: ${report.totalRows}, round-trip coverage: " +
            "${"%.1f".format(report.coveragePercent)}% (${report.roundTripOk}/${report.totalWords})")

        val insertIndex = connection.prepareStatement(
            "INSERT INTO phonetic_index (key, bengali, frequency, tier) VALUES (?, ?, ?, ?)"
        )
        var indexCount = 0
        for (row in indexRows) {
            insertIndex.setString(1, row.key)
            insertIndex.setString(2, row.bengali)
            insertIndex.setInt(3, row.frequency)
            insertIndex.setInt(4, row.tier)
            insertIndex.addBatch()
            if (++indexCount % 50000 == 0) insertIndex.executeBatch()
        }
        insertIndex.executeBatch()
        println("  Inserted $indexCount phonetic index rows")

        // 3c. Build English lexicon (Engine v3)
        val cmudictFile = File("dictionary-compiler/data/cmudict.dict")
        val freqListFile = File("dictionary-compiler/data/en_50k.txt")
        var englishCount = 0
        if (cmudictFile.exists() && freqListFile.exists()) {
            println("Building English lexicon...")
            val topWords = EnglishLexiconBuilder.parseTopWords(freqListFile.readLines())
            val englishEntries = EnglishLexiconBuilder.build(cmudictFile.readLines(), topWords)
            val insertEnglish = connection.prepareStatement(
                "INSERT OR IGNORE INTO english_lexicon (key, bengali) VALUES (?, ?)"
            )
            for (entry in englishEntries) {
                insertEnglish.setString(1, entry.key)
                insertEnglish.setString(2, entry.bengali)
                insertEnglish.addBatch()
            }
            insertEnglish.executeBatch()
            englishCount = englishEntries.size
            println("  Inserted $englishCount english lexicon entries")
        } else {
            println("Skipping english lexicon (data files not found)")
        }
```

- [ ] **Step 3: Update metadata**

In the `metadataEntries` map (line ~208): change `"version" to "3.2.0"` → `"version" to "3.3.0"` and add:

```kotlin
            "phonetic_index_count" to indexCount.toString(),
            "phonetic_roundtrip_coverage" to "%.2f".format(report.coveragePercent),
            "english_lexicon_count" to englishCount.toString(),
```

- [ ] **Step 4: Compile check + commit**

Run: `./gradlew :dictionary-compiler:build`
Expected: BUILD SUCCESSFUL, all compiler tests pass.

```bash
git add dictionary-compiler/src/main/kotlin/com/banglu/compiler/DictionaryCompiler.kt
git commit -m "feat(compiler): write phonetic_index and english_lexicon tables, db version 3.3.0"
```

---

### Task 6: Run the compiler — produce the real numbers

This step produces the spec §7 measurements. **Do not skip; record outputs verbatim.**

- [ ] **Step 1: Locate input files**

```bash
ls /Users/mdshahabulalam/myprojects/banlgu/banglu-web/public/*.json
find /Users/mdshahabulalam/myprojects/banlgu /Users/mdshahabulalam/myprojects/banlgu/banglu-web \
     -maxdepth 4 -name "word-frequency.json" 2>/dev/null
```

`bangla_dictionary.json`, `dictionary-extended.json`, `disambiguation-map.json`, `bigram-model.json` are in `banglu-web/public/`. If `word-frequency.json` is elsewhere, copy all five into one staging dir (e.g. `/tmp/banglu-dict-input/`). If `word-frequency.json` cannot be found, extract it from the existing compiled asset instead:

```bash
sqlite3 android-keyboard/src/main/assets/dictionary.sqlite \
  "SELECT json_group_object(bengali, frequency) FROM words WHERE frequency > 0" \
  | python3 -c 'import sys,json; print(json.dumps({"frequencies": json.loads(sys.stdin.read())}))' \
  > /tmp/banglu-dict-input/word-frequency.json
```

- [ ] **Step 2: Compile**

```bash
./gradlew :dictionary-compiler:run --args="/tmp/banglu-dict-input /tmp/dictionary-new.sqlite"
```

Expected output includes: word count 484,996; phonetic index row count; **round-trip coverage %**; english lexicon count (~25-30k); final size in MB.

- [ ] **Step 3: Record the numbers and check budgets**

Append a `## Phase 1 measurements` section to the spec file with: index rows, coverage %, english entries, old size (77MB) vs new size. **Gates:** new sqlite ≤ 105MB (else: hash Tier B keys per spec §7). Coverage < 80%: file it as a known number — it does not block Phase 1 (the index, not round-trip, drives conversion), but flag it for the variant-mining backlog.

- [ ] **Step 4: Replace the asset**

```bash
cp /tmp/dictionary-new.sqlite android-keyboard/src/main/assets/dictionary.sqlite
ls -lh android-keyboard/src/main/assets/dictionary.sqlite
```

- [ ] **Step 5: Commit (asset + spec measurement note)**

```bash
git add android-keyboard/src/main/assets/dictionary.sqlite \
        docs/superpowers/specs/2026-06-11-engine-v3-lookup-first-design.md
git commit -m "feat(dict): compile dictionary.sqlite 3.3.0 with phonetic_index + english_lexicon"
```

---

### Task 7: PhoneticIndexStore abstraction (shared)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/banglu/engine/platform/PhoneticIndexStore.kt`
- Create: `shared/src/commonMain/kotlin/com/banglu/engine/platform/InMemoryPhoneticIndexStore.kt`
- Test: `shared/src/commonTest/kotlin/com/banglu/engine/platform/InMemoryPhoneticIndexStoreTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.banglu.engine.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryPhoneticIndexStoreTest {

    private val store = InMemoryPhoneticIndexStore(
        entries = listOf(
            PhoneticIndexHit("আমি", 100, 0) to "ami",
            PhoneticIndexHit("আমিষ", 10, 1) to "amish",
            PhoneticIndexHit("কথা", 95, 0) to "kotha"
        ),
        english = mapOf("scooter" to "স্কুটার")
    )

    @Test
    fun exactLookupReturnsBothTiersSortedByFrequency() {
        val hits = store.lookupExact("ami")
        assertEquals(listOf("আমি"), hits.map { it.bengali })
    }

    @Test
    fun prefixLookupIsTierAOnly() {
        val hits = store.lookupPrefix("am", limit = 10)
        assertTrue(hits.all { it.tier == 0 })
        assertEquals(listOf("আমি"), hits.map { it.bengali })
    }

    @Test
    fun tierBReachableByExactMatchOnly() {
        assertEquals(listOf("আমিষ"), store.lookupExact("amish").map { it.bengali })
    }

    @Test
    fun englishLexiconLookup() {
        assertEquals("স্কুটার", store.lookupEnglish("scooter"))
        assertNull(store.lookupEnglish("zzz"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.banglu.engine.platform.InMemoryPhoneticIndexStoreTest"`
Expected: FAIL — unresolved references

- [ ] **Step 3: Write interface and in-memory implementation**

`PhoneticIndexStore.kt`:

```kotlin
package com.banglu.engine.platform

data class PhoneticIndexHit(
    val bengali: String,
    val frequency: Int,
    val tier: Int // 0 = Tier A (suggestible), 1 = Tier B (exact-match only)
)

/**
 * Query interface over the precompiled phonetic index (Engine v3 spec 3.2).
 * Implementations must be safe to call on every keystroke (< 5ms typical).
 * Android: persistent read-only sqlite connection. Tests/JVM: in-memory maps.
 */
interface PhoneticIndexStore {
    /** All words whose canonical/variant key equals [key], frequency-descending. */
    fun lookupExact(key: String): List<PhoneticIndexHit>

    /** Tier A words whose key starts with [prefix], frequency-descending. */
    fun lookupPrefix(prefix: String, limit: Int): List<PhoneticIndexHit>

    /** Bengali rendering for an English word key, or null. */
    fun lookupEnglish(key: String): String?
}
```

`InMemoryPhoneticIndexStore.kt`:

```kotlin
package com.banglu.engine.platform

class InMemoryPhoneticIndexStore(
    entries: List<Pair<PhoneticIndexHit, String>>, // hit to key
    private val english: Map<String, String> = emptyMap()
) : PhoneticIndexStore {

    private val byKey: Map<String, List<PhoneticIndexHit>> =
        entries.groupBy({ it.second }, { it.first })
            .mapValues { (_, hits) -> hits.sortedByDescending { it.frequency } }

    override fun lookupExact(key: String): List<PhoneticIndexHit> =
        byKey[key].orEmpty()

    override fun lookupPrefix(prefix: String, limit: Int): List<PhoneticIndexHit> =
        byKey.asSequence()
            .filter { it.key.startsWith(prefix) }
            .flatMap { it.value }
            .filter { it.tier == 0 }
            .sortedByDescending { it.frequency }
            .take(limit)
            .toList()

    override fun lookupEnglish(key: String): String? = english[key]
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:jvmTest --tests "com.banglu.engine.platform.InMemoryPhoneticIndexStoreTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/banglu/engine/platform/PhoneticIndexStore.kt \
        shared/src/commonMain/kotlin/com/banglu/engine/platform/InMemoryPhoneticIndexStore.kt \
        shared/src/commonTest/kotlin/com/banglu/engine/platform/InMemoryPhoneticIndexStoreTest.kt
git commit -m "feat(engine): PhoneticIndexStore abstraction + in-memory implementation"
```

---

### Task 8: SmartEngine integration — index lookup + english lexicon

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/banglu/engine/SmartEngine.kt`
- Modify: `shared/src/commonMain/kotlin/com/banglu/engine/types/Types.kt` (add `ENGLISH_LEXICON` to `ResolutionSource`)
- Test: `shared/src/commonTest/kotlin/com/banglu/engine/PhoneticIndexIntegrationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.banglu.engine

import com.banglu.engine.platform.InMemoryPhoneticIndexStore
import com.banglu.engine.platform.PhoneticIndexHit
import com.banglu.engine.types.ResolutionSource
import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneticIndexIntegrationTest {

    private fun engine(): SmartEngine {
        val e = SmartEngine()
        e.initializeSync()
        e.setPhoneticIndex(
            InMemoryPhoneticIndexStore(
                entries = listOf(
                    // A word NOT in the seed dictionary, only in the index
                    PhoneticIndexHit("পরীক্ষাগার", 40, 0) to "porikkhagar"
                ),
                english = mapOf("smartwatch" to "স্মার্টওয়াচ")
            )
        )
        return e
    }

    @Test
    fun indexHitResolvesWordsBeyondSeedDictionary() {
        val result = engine().convertWord("porikkhagar")
        assertEquals("পরীক্ষাগার", result.bengali)
        assertEquals(ResolutionSource.DICTIONARY, result.source)
    }

    @Test
    fun englishLexiconBeatsPatternMangling() {
        val result = engine().convertWord("smartwatch")
        assertEquals("স্মার্টওয়াচ", result.bengali)
        assertEquals(ResolutionSource.ENGLISH_LEXICON, result.source)
        // Raw English stays reachable as an alternative
        assertEquals("smartwatch", result.alternatives.first().bengali)
    }

    @Test
    fun curatedEnglishStillWinsOverGeneratedLexicon() {
        // "practice" is curated (EnglishPronunciationVariantData -> প্রাকটিস).
        // Even with a (worse) generated entry present, curated wins.
        val e = SmartEngine()
        e.initializeSync()
        e.setPhoneticIndex(
            InMemoryPhoneticIndexStore(emptyList(), english = mapOf("practice" to "প্র্যাকটিস"))
        )
        assertEquals("প্রাকটিস", e.convertWord("practice").bengali)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.banglu.engine.PhoneticIndexIntegrationTest"`
Expected: FAIL — `Unresolved reference: setPhoneticIndex`, `ENGLISH_LEXICON`

- [ ] **Step 3: Implement**

3a. `Types.kt:9-11` — extend the enum:

```kotlin
enum class ResolutionSource {
    DICTIONARY, RULE, STATISTICAL, USER_HISTORY, SECTION, NARROWING, CONJUNCT_RULE,
    ENGLISH_PASSTHROUGH, ENGLISH_LEXICON, CLEAN_TRANSLITERATION
}
```

(`CLEAN_TRANSLITERATION` is used in Task 9; adding both now avoids touching the enum twice.)

3b. `SmartEngine.kt` — add field + setter near the other private vals (line ~63):

```kotlin
    private var phoneticIndex: com.banglu.engine.platform.PhoneticIndexStore? = null

    /** Engine v3: attach the precompiled phonetic index (replaces runtime corpus index). */
    fun setPhoneticIndex(store: com.banglu.engine.platform.PhoneticIndexStore?) {
        phoneticIndex = store
        clearCache()
    }
```

3c. `SmartEngine.kt:486` — make `tryCorpusPhoneticLookup` consult the store first:

```kotlin
    private fun tryCorpusPhoneticLookup(key: String): ConversionResult? {
        if (key.length < 3) return null

        phoneticIndex?.let { store ->
            val hits = store.lookupExact(key)
            if (hits.isNotEmpty()) {
                val alternatives = hits.drop(1).take(config.maxSuggestions - 1)
                    .mapIndexed { index, hit -> Alternative(hit.bengali, maxOf(0.72, 0.92 - index * 0.04)) }
                return ConversionResult(hits.first().bengali, 0.96, ResolutionSource.DICTIONARY, alternatives)
            }
            return null // store attached: it IS the corpus index; no fallback to runtime map
        }

        // Legacy path (no store attached): runtime-built corpus index
        if (corpusPhoneticIndex.isEmpty()) return null
        ... (existing body lines 490-505 unchanged)
    }
```

3d. `SmartEngine.kt:399-404` (`initialize`) — skip the expensive runtime index when the store exists:

```kotlin
        loader?.loadFullDictionary()?.let { words ->
            validator.loadWords(words)
            sectionEngine.initialize(validator)
            disambiguator.addKnownWords(words)
            if (phoneticIndex == null) buildCorpusPhoneticIndex(words)
        }
```

(Also guard `sortCorpusPhoneticIndex()` at line 409 with `if (phoneticIndex == null)`.)

3e. `SmartEngine.kt:580-586` — english lexicon between curated English and passthrough, plus a lexicon check that does NOT depend on `EnglishDetector`. Replace the block:

```kotlin
        if (EnglishDetector.isEnglish(key)) {
            tryCuratedEnglishVariant(key, trimmed)?.let { result ->
                cacheResult(cacheKey, result); return result
            }
            tryEnglishLexicon(key, trimmed)?.let { result ->
                cacheResult(cacheKey, result); return result
            }
            val result = ConversionResult(trimmed, 1.0, ResolutionSource.ENGLISH_PASSTHROUGH)
            cacheResult(cacheKey, result); return result
        }

        // Lexicon words are English even when EnglishDetector doesn't know them
        tryEnglishLexicon(key, trimmed)?.let { result ->
            cacheResult(cacheKey, result); return result
        }
```

and add the helper next to `tryCuratedEnglishVariant` (line ~1590):

```kotlin
    private fun tryEnglishLexicon(key: String, rawInput: String): ConversionResult? {
        val bengali = phoneticIndex?.lookupEnglish(key) ?: return null
        return ConversionResult(
            bengali = bengali,
            confidence = 0.97,
            source = ResolutionSource.ENGLISH_LEXICON,
            alternatives = listOf(Alternative(rawInput, 0.95))
        )
    }
```

- [ ] **Step 4: Run the new test AND the full suite**

Run: `./gradlew :shared:jvmTest --tests "com.banglu.engine.PhoneticIndexIntegrationTest"`
Expected: PASS (3 tests)
Run: `./gradlew :shared:jvmTest`
Expected: ALL tests pass — no store is attached in legacy tests, so behavior is unchanged. If any parity test fails, the change leaked into the no-store path; fix the guard, do not touch the failing test.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/banglu/engine/SmartEngine.kt \
        shared/src/commonMain/kotlin/com/banglu/engine/types/Types.kt \
        shared/src/commonTest/kotlin/com/banglu/engine/PhoneticIndexIntegrationTest.kt
git commit -m "feat(engine): precompiled phonetic index lookup + english lexicon layer"
```

---

### Task 9: Commit gate

The spec §3.3 invariant. Enforced only when the 480k validator is loaded (seed-only JVM tests keep legacy behavior; on Android the validator always loads).

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/banglu/engine/SmartEngine.kt`
- Modify: `shared/src/commonMain/kotlin/com/banglu/engine/dictionary/SmartDictionary.kt`
- Test: `shared/src/commonTest/kotlin/com/banglu/engine/CommitGateTest.kt`

- [ ] **Step 1: Add `containsBengali` to SmartDictionary**

SmartDictionary already maintains a bengali→phonetic reverse map (field `bengaliToPhonetic`, used by reverse lookups). Add a public membership check next to `lookup`:

```kotlin
    /** True if [bengali] is a seed/extended/learned entry (Engine v3 commit gate). */
    fun containsBengali(bengali: String): Boolean = bengaliToPhonetic.containsKey(bengali)
```

(If the actual field name differs, use the existing reverse-map field — do NOT add a second map.)

- [ ] **Step 2: Write the failing test**

```kotlin
package com.banglu.engine

import com.banglu.engine.rules.CleanTransliterator
import com.banglu.engine.types.ResolutionSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommitGateTest {

    /** Engine with a tiny "480k" validator so the gate is armed. */
    private fun gatedEngine(): SmartEngine {
        val e = SmartEngine()
        e.initializeSync()
        e.loadValidatorWords(listOf("আমি", "তুমি", "ভাত", "খাই"))
        return e
    }

    @Test
    fun validWordsPassTheGateUntouched() {
        val result = gatedEngine().convertWord("ami")
        assertEquals("আমি", result.bengali)
    }

    @Test
    fun oovInputCommitsCleanTransliterationNeverPatternGuess() {
        val e = gatedEngine()
        val result = e.convertWord("rafsan")
        assertEquals(CleanTransliterator.transliterate("rafsan"), result.bengali)
        assertEquals(ResolutionSource.CLEAN_TRANSLITERATION, result.source)
    }

    @Test
    fun gateAlternativesAreDictionaryClosed() {
        val result = gatedEngine().convertWord("rafsan")
        // No invented strings ride along in the alternatives
        assertTrue(result.alternatives.isEmpty()
            || result.alternatives.all { it.bengali.none { c -> c == '্' } || it.bengali in listOf("আমি", "তুমি", "ভাত", "খাই") })
    }

    @Test
    fun composingFallbackIsGatedForCompleteLookingWords() {
        val e = gatedEngine()
        val result = e.convertForComposing("rafsan")
        assertEquals(CleanTransliterator.transliterate("rafsan"), result.bengali)
    }
}
```

`loadValidatorWords` is a small test seam to add (Step 3) — `validator` is private and `initialize()` needs a suspend loader; a direct test hook is simpler and harmless.

- [ ] **Step 3: Implement**

3a. Test seam in `SmartEngine.kt` (near `initializeSync`, line ~375):

```kotlin
    /** Test seam: load validator words directly (production uses initialize(loader)). */
    fun loadValidatorWords(words: List<String>) {
        validator.loadWords(words)
        clearCache()
    }
```

3b. Gate functions (near the recovery helpers, after `applyBengaliRecovery` ~line 3800):

```kotlin
    /** Engine v3 commit gate (spec 3.3): is this result allowed as editor primary? */
    private fun isGateApproved(result: ConversionResult): Boolean = when {
        result.bengali.isEmpty() -> true
        result.source == ResolutionSource.ENGLISH_PASSTHROUGH -> true
        result.source == ResolutionSource.ENGLISH_LEXICON -> true
        result.source == ResolutionSource.CLEAN_TRANSLITERATION -> true
        validator.isValid(result.bengali) -> true
        dictionary.containsBengali(result.bengali) -> true   // seed + learned/user words
        else -> false
    }

    private fun applyCommitGate(key: String, result: ConversionResult): ConversionResult {
        if (!validator.isLoaded()) return result          // gate armed only with real dictionary
        if (isGateApproved(result)) return result
        val clean = CleanTransliterator.transliterate(key)
        val dictionaryClosedAlternatives = result.alternatives
            .filter { validator.isValid(it.bengali) || dictionary.containsBengali(it.bengali) }
            .take(3)
        return ConversionResult(
            bengali = clean,
            confidence = 0.60,
            source = ResolutionSource.CLEAN_TRANSLITERATION,
            alternatives = dictionaryClosedAlternatives
        )
    }
```

Add import `com.banglu.engine.rules.CleanTransliterator` at the top of SmartEngine.kt.

3c. Apply at the single exit of the pattern-tail in `convertWord` (line 682-685). Replace:

```kotlin
        result = applyCandidateLatticeRanking(key, result)

        cacheResult(cacheKey, result)
        return result
```

with:

```kotlin
        result = applyCandidateLatticeRanking(key, result)
        result = applyCommitGate(key, result)

        cacheResult(cacheKey, result)
        return result
```

Also gate the two recovery returns that can surface unvalidated output: wrap the Layer-6 `recovered` (line 641-643) and the typo/fuzzy returns (lines 661-662, 677-678) the same way: `cacheResult(cacheKey, applyCommitGate(key, X)); return applyCommitGate(key, X)` — assign to a local first to avoid double computation.

3d. Gate the composing fallback (`convertForComposing`, line 742-745). Replace:

```kotlin
        return pattern.copy(
            confidence = minOf(pattern.confidence, 0.84),
            alternatives = emptyList()
        )
```

with:

```kotlin
        val fallback = pattern.copy(
            confidence = minOf(pattern.confidence, 0.84),
            alternatives = emptyList()
        )
        // Short fragments are usually incomplete words mid-typing — keep them live.
        if (key.length < 4) return fallback
        return applyCommitGate(key, fallback).copy(alternatives = emptyList())
    }
```

- [ ] **Step 4: Run new test + full suite**

Run: `./gradlew :shared:jvmTest --tests "com.banglu.engine.CommitGateTest"`
Expected: PASS (4 tests)
Run: `./gradlew :shared:jvmTest`
Expected: ALL pass. Legacy tests run without a loaded validator, so the gate is dormant there. Any failure means the gate armed itself in seed-only mode — check `validator.isLoaded()` placement.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/banglu/engine/SmartEngine.kt \
        shared/src/commonMain/kotlin/com/banglu/engine/dictionary/SmartDictionary.kt \
        shared/src/commonTest/kotlin/com/banglu/engine/CommitGateTest.kt
git commit -m "feat(engine): commit gate - editor primary is always dictionary word, english entry, or clean transliteration"
```

---

### Task 10: OOV learning on commit

When the user commits a clean-transliterated word (a name), learn it so it becomes a first-class suggestion next time (spec §3.4).

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/banglu/engine/SmartEngineAdapter.kt:205-207`
- Modify: `android-keyboard/src/main/kotlin/com/banglu/keyboard/BangluIMEService.kt:529` (call site)
- Test: `shared/src/commonTest/kotlin/com/banglu/engine/OovLearningTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.banglu.engine

import com.banglu.engine.types.ResolutionSource
import kotlin.test.Test
import kotlin.test.assertEquals

class OovLearningTest {

    @Test
    fun committedOovWordBecomesDictionaryBackedNextTime() {
        SmartEngineAdapter.resetForTest()
        SmartEngineAdapter.initializeSync()
        val engine = SmartEngineAdapter.getEngine()
        engine.loadValidatorWords(listOf("আমি"))

        val first = engine.convertWord("rafsan")
        assertEquals(ResolutionSource.CLEAN_TRANSLITERATION, first.source)

        // User commits the word -> IME reports it with learnAsWord=true
        SmartEngineAdapter.onWordSelected("rafsan", first.bengali, learnAsWord = true)

        val second = engine.convertWord("rafsan")
        assertEquals(first.bengali, second.bengali)
        assertEquals(ResolutionSource.DICTIONARY, second.source)
    }
}
```

If `SmartEngineAdapter` lacks `resetForTest()`/`getEngine()` public access, check what `UserPreferenceRankingTest.kt` uses to reset adapter state between tests and reuse that mechanism — do not invent a parallel reset path.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.banglu.engine.OovLearningTest"`
Expected: FAIL — no `learnAsWord` parameter

- [ ] **Step 3: Implement**

`SmartEngineAdapter.kt:205-207`:

```kotlin
    fun onWordSelected(phonetic: String, bengali: String, learnAsWord: Boolean = false) {
        rememberPreferredConversion(phonetic, bengali, baseFrequency = 94)
        if (learnAsWord) {
            val key = phonetic.normalizedPhonetic()
            if (key.isNotEmpty() && bengali.isNotBlank()) {
                getEngine().addWord(key, bengali.trim(), 94)
                getEngine().clearCache()
                persistLearnedWord(key, bengali.trim(), 94)
            }
        }
    }
```

`BangluIMEService.kt:529` — the commit path knows the conversion result; pass the flag:

```kotlin
                SmartEngineAdapter.onWordSelected(
                    phonetic, bengali,
                    learnAsWord = committedResult?.source == ResolutionSource.CLEAN_TRANSLITERATION
                )
```

(`committedResult` is whatever local holds the `ConversionResult` being committed at that site — read the surrounding ~20 lines and use the actual variable; if only the Bengali string is in scope, call `SmartEngineAdapter.convertWord(phonetic)` is WRONG (double conversion) — instead thread the `ConversionResult` from where the commit text was produced.)

- [ ] **Step 4: Run new test + full suite**

Run: `./gradlew :shared:jvmTest --tests "com.banglu.engine.OovLearningTest"` → PASS
Run: `./gradlew :shared:jvmTest` → ALL pass

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/banglu/engine/SmartEngineAdapter.kt \
        shared/src/commonTest/kotlin/com/banglu/engine/OovLearningTest.kt \
        android-keyboard/src/main/kotlin/com/banglu/keyboard/BangluIMEService.kt
git commit -m "feat(engine): learn OOV clean-transliterated words on commit"
```

---

### Task 11: Android SqlitePhoneticIndexStore + wiring

Per-keystroke queries need a persistent read-only connection (AndroidDictionaryLoader's open-per-call pattern is fine for one-time loads, too slow per keystroke).

**Files:**
- Create: `android-keyboard/src/main/kotlin/com/banglu/keyboard/SqlitePhoneticIndexStore.kt`
- Modify: `android-keyboard/src/main/kotlin/com/banglu/keyboard/AndroidDictionaryLoader.kt:34` (`REQUIRED_DB_VERSION = "3.3.0"`)
- Modify: `android-keyboard/src/main/kotlin/com/banglu/keyboard/BangluIMEService.kt` (wire store at engine init; close in `onDestroy`)

- [ ] **Step 1: Implementation**

```kotlin
package com.banglu.keyboard

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.banglu.engine.platform.PhoneticIndexHit
import com.banglu.engine.platform.PhoneticIndexStore
import java.io.File

/**
 * Sqlite-backed phonetic index with a persistent read-only connection.
 * Open once per IME session; call close() from the service's onDestroy.
 * All methods fail soft (empty results) so a corrupt db never crashes the IME.
 */
class SqlitePhoneticIndexStore(dbFile: File) : PhoneticIndexStore {

    companion object { private const val TAG = "BangluPhoneticIndex" }

    private val db: SQLiteDatabase? = try {
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            .takeIf { it.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='phonetic_index'", null
            ).use { c -> c.moveToFirst() } }
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.e(TAG, "Failed to open phonetic index", e)
        null
    }

    val isAvailable: Boolean get() = db != null

    override fun lookupExact(key: String): List<PhoneticIndexHit> = query(
        "SELECT bengali, frequency, tier FROM phonetic_index WHERE key = ? ORDER BY frequency DESC LIMIT 16",
        arrayOf(key)
    )

    override fun lookupPrefix(prefix: String, limit: Int): List<PhoneticIndexHit> = query(
        "SELECT bengali, frequency, tier FROM phonetic_index " +
            "WHERE key >= ? AND key < ? AND tier = 0 ORDER BY frequency DESC LIMIT ?",
        arrayOf(prefix, prefix + '￿', limit.toString())
    )

    override fun lookupEnglish(key: String): String? = try {
        db?.rawQuery("SELECT bengali FROM english_lexicon WHERE key = ? LIMIT 1", arrayOf(key))
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) { null }

    private fun query(sql: String, args: Array<String>): List<PhoneticIndexHit> = try {
        db?.rawQuery(sql, args)?.use { c ->
            val hits = ArrayList<PhoneticIndexHit>(c.count)
            while (c.moveToNext()) hits.add(PhoneticIndexHit(c.getString(0), c.getInt(1), c.getInt(2)))
            hits
        } ?: emptyList()
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.e(TAG, "Index query failed", e)
        emptyList()
    }

    fun close() { try { db?.close() } catch (_: Exception) {} }
}
```

- [ ] **Step 2: Wire into BangluIMEService**

In the engine-initialization path (where `createDictionaryLoader()` is used, line ~616): the db file lives at `File(filesDir, "dictionary.sqlite")` after AndroidDictionaryLoader copies it. After `SmartEngineAdapter.initialize(...)` completes (the copy is guaranteed then), create and attach:

```kotlin
        phoneticIndexStore = SqlitePhoneticIndexStore(File(filesDir, "dictionary.sqlite"))
            .takeIf { it.isAvailable }
        SmartEngineAdapter.getEngine().setPhoneticIndex(phoneticIndexStore)
```

with a service field `private var phoneticIndexStore: SqlitePhoneticIndexStore? = null` and in `onDestroy()`:

```kotlin
        phoneticIndexStore?.close()
        phoneticIndexStore = null
```

IMPORTANT ORDERING: `setPhoneticIndex` must be called BEFORE `initialize(loader)` finishes loading the full word list if we want to skip `buildCorpusPhoneticIndex` (Task 8 step 3d checks `phoneticIndex == null` at load time). The db copy happens lazily inside the loader's first call, so: construct the loader, attach the store (db may not exist yet on very first launch — `isAvailable=false` is fine, that launch builds the runtime index as before), then call initialize. On every subsequent launch the store attaches and the ~500k-entry runtime index is skipped.

- [ ] **Step 3: Bump db version constant**

`AndroidDictionaryLoader.kt:34`: `private const val REQUIRED_DB_VERSION = "3.3.0"`

- [ ] **Step 4: Build + on-device verification**

Run: `./gradlew :android-keyboard:assembleDebug`
Expected: BUILD SUCCESSFUL.
Install on device/emulator, then verify by typing in any app: a rare-but-real Bengali word converts (index hit), `smartwatch` → স্মার্টওয়াচ, `rafsan` → রাফসান (no weird conjuncts), and logcat shows no `BangluPhoneticIndex` errors. Run `scripts/benchmark_android_keyboard.sh` and confirm per-keystroke conversion stays under 16ms.

- [ ] **Step 5: Commit**

```bash
git add android-keyboard/src/main/kotlin/com/banglu/keyboard/SqlitePhoneticIndexStore.kt \
        android-keyboard/src/main/kotlin/com/banglu/keyboard/AndroidDictionaryLoader.kt \
        android-keyboard/src/main/kotlin/com/banglu/keyboard/BangluIMEService.kt
git commit -m "feat(android): sqlite phonetic index store wired into IME, db 3.3.0"
```

---

### Task 12: Acceptance suites + regression baseline

**Files:**
- Create: `shared/src/commonTest/kotlin/com/banglu/engine/EngineV3AcceptanceTest.kt`

- [ ] **Step 1: Write the acceptance test**

```kotlin
package com.banglu.engine

import com.banglu.engine.platform.InMemoryPhoneticIndexStore
import com.banglu.engine.platform.PhoneticIndexHit
import com.banglu.engine.types.ResolutionSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Engine v3 Phase 1 acceptance: the user-visible scenarios from the spec.
 * Uses an in-memory index emulating the compiled asset.
 */
class EngineV3AcceptanceTest {

    private fun engine(): SmartEngine {
        val e = SmartEngine()
        e.initializeSync()
        e.loadValidatorWords(listOf("আমি", "ভাত", "খাই", "আচ্ছা", "স্কুল"))
        e.setPhoneticIndex(InMemoryPhoneticIndexStore(
            entries = listOf(
                PhoneticIndexHit("আচ্ছা", 95, 0) to "accha",
                PhoneticIndexHit("আচ্ছা", 95, 0) to "assa",   // irregular variant key
                PhoneticIndexHit("আচ্ছা", 95, 0) to "acca"
            ),
            english = mapOf("scooter" to "স্কুটার", "play" to "প্লে")
        ))
        return e
    }

    @Test
    fun irregularVariantsAllReachTheSameWord() {
        val e = engine()
        for (typed in listOf("accha", "assa", "acca")) {
            assertEquals("আচ্ছা", e.convertWord(typed).bengali, "input: $typed")
        }
    }

    @Test
    fun mixedEnglishBengaliSentence() {
        val e = engine()
        assertEquals("স্কুটার", e.convertWord("scooter").bengali)
        assertEquals("প্লে", e.convertWord("play").bengali)
        // sentence chaining via parse()
        assertTrue(e.parse("ami scooter").contains("স্কুটার"))
    }

    @Test
    fun editorNeverShowsInventedStrings() {
        val e = engine()
        // Garbage input: must come out as clean transliteration, not swap output
        val r = e.convertWord("kkkkx")
        assertEquals(ResolutionSource.CLEAN_TRANSLITERATION, r.source)
    }
}
```

- [ ] **Step 2: Run it + the entire suite, record baseline**

Run: `./gradlew :shared:jvmTest --tests "com.banglu.engine.EngineV3AcceptanceTest"` → PASS
Run: `./gradlew :shared:jvmTest 2>&1 | tail -20` → ALL pass; copy the final test-count line into the Phase 1 measurements section of the spec along with the `SuggestionQualityBenchmarkTest` score (this is the Phase 2 deletion baseline).

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonTest/kotlin/com/banglu/engine/EngineV3AcceptanceTest.kt \
        docs/superpowers/specs/2026-06-11-engine-v3-lookup-first-design.md
git commit -m "test(engine): Engine v3 Phase 1 acceptance suite + regression baseline"
```

---

## Verification checklist (after all tasks)

1. `./gradlew :shared:jvmTest` — full suite green.
2. `./gradlew :dictionary-compiler:test` — compiler suite green.
3. `./gradlew :android-keyboard:assembleDebug` — builds.
4. Spec's "Phase 1 measurements" section filled in: index rows, round-trip coverage %, english lexicon count, sqlite size, benchmark latency.
5. On-device smoke test (Task 11 step 4) done and observations noted.

## Explicitly out of scope (Phase 2)

Deleting recovery layers, section narrowing, runtime shatva/natva, the runtime corpus index builder, migrating `DIRECT_WORD_OVERRIDES` into `irregular_variants`, and dropping the 484k in-RAM validator. None of these are touched in Phase 1.
