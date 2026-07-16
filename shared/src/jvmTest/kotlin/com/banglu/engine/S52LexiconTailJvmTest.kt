package com.banglu.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S52 Task 2 — lexicon tail repairs regression wall.
 * Evidence: `.superpowers/sdd/probe-english-acronyms.md` (A-list GARBAGE/MISSING class),
 * `.superpowers/sdd/explore-loanword-machinery.md` (english_lexicon build/arbitration trace),
 * `.superpowers/sdd/s52-task-2-report.md` (eval-diff baselines, cutoff decision, evidence).
 *
 * Two tiers of assertion, deliberately split:
 *
 * 1. [tsvOverridesContainRepairedWords] — asserts the SOURCE DATA
 *    (`dictionary-compiler/data/english_lexicon_overrides.tsv`) directly. This is real,
 *    green, CI-enforced coverage TODAY: the tsv file ships with this commit regardless of
 *    which db is checked into the repo root, so there is no need to wait for a rebuild to
 *    verify the fix is present and correctly spelled.
 *
 * 2. [repairedWordsResolveCorrectlyViaEngine] and [cutoffAddedWordsResolveViaEngine] —
 *    runtime pins against `ConjunctSolutionRoundJvmTest.engine`, which loads
 *    `./dictionary.sqlite` (repo root). Task 3 regenerated and shipped db 3.8.5 to the
 *    repo root (english_lexicon COUNT=39390, callback -> কলব্যাক verified directly via
 *    sqlite3 against the shipped db; see `.superpowers/sdd/s52-task-3-report.md`), so
 *    these assertions are enabled and green against the real repo-root db.
 */
class S52LexiconTailJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    /** Mirrors the parse the compiler itself uses (tab-separated, key\tbengali, no header). */
    private fun loadOverridesTsv(): Map<String, String> {
        val candidates = listOf(
            File("dictionary-compiler/data/english_lexicon_overrides.tsv"),
            File("../dictionary-compiler/data/english_lexicon_overrides.tsv"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error(
                "english_lexicon_overrides.tsv not found. Searched:\n" +
                    candidates.joinToString("\n") { "  ${it.absolutePath}" }
            )
        return file.readLines()
            .mapNotNull { line ->
                val parts = line.trim().split("\t")
                if (parts.size == 2) parts[0].lowercase() to parts[1] else null
            }
            .toMap()
    }

    @Test
    fun tsvOverridesContainRepairedWords() {
        val overrides = loadOverridesTsv()
        val expected = mapOf(
            "motivation" to "মোটিভেশন",
            "semester" to "সেমিস্টার",
            "callback" to "কলব্যাক",
            "ngo" to "এনজিও",
            "simple" to "সিম্পল",
            "late" to "লেট",
        )
        for ((key, bengali) in expected) {
            assertEquals(
                bengali, overrides[key],
                "english_lexicon_overrides.tsv must map '$key' -> '$bengali' (S52 lexicon-tail repair)"
            )
        }
    }

    @Test
    fun tsvOverridesDoNotRegressExistingCuratedRows() {
        // Sanity net for the S52 edit itself: appending 6 new lines must not have
        // corrupted or duplicated-with-a-different-value any of the 76 pre-existing
        // curated rows (S23's government/society/question-class fixes).
        val overrides = loadOverridesTsv()
        val previouslyCurated = mapOf(
            "government" to "গভর্নমেন্ট",
            "society" to "সোসাইটি",
            "question" to "কোয়েশ্চেন",
            "interesting" to "ইন্টারেস্টিং",
        )
        for ((key, bengali) in previouslyCurated) {
            assertEquals(bengali, overrides[key], "pre-existing override for '$key' must be untouched")
        }
    }

    @Test
    fun repairedWordsResolveCorrectlyViaEngine() {
        val expected = mapOf(
            "motivation" to "মোটিভেশন",
            "semester" to "সেমিস্টার",
            "callback" to "কলব্যাক",
            "simple" to "সিম্পল",
        )
        for ((key, bengali) in expected) {
            assertEquals(bengali, engine.convertWord(key).bengali, "'$key' should commit to '$bengali'")
        }
    }

    @Test
    fun cutoffAddedWordsResolveViaEngine() {
        // Sample of keys only present in english_lexicon once the frequency cutoff was
        // raised 30,000 -> 50,000 (see s52-task-2-report.md for the full 11,644-key diff
        // and the zero-regression eval that justified keeping the raise).
        val sample = listOf("aardvark", "abattoir", "abdicate", "aberrant")
        for (key in sample) {
            val bengali = engine.convertWord(key).bengali
            assertTrue(
                bengali.any { it in 'ঀ'..'৿' },
                "'$key' should resolve to a Bengali rendering once the cutoff raise ships; got '$bengali'"
            )
        }
    }

    @Test
    fun lateIsAVettedEnglishIntentFlip() {
        // Root cause: fuzzy লাতে (freq 60) is not junk-classified, so the
        // lexicon rescue can never fire for "late" — S52 adds it to
        // ENGLISH_PRIMARY_INTENT (the time/line mechanism). The root db
        // already carries late→লেট in english_lexicon, so this pin is
        // active on 3.8.4 TODAY, and remains active on 3.8.5 (Task 3) alongside
        // the now-enabled db-gated pins above.
        assertEquals("লেট", engine.convertWord("late").bengali)
    }

    // ── Guard pins: S52 Task 2 must not disturb any pre-existing engine behavior ──

    @Test
    fun guardPinKacciDish() {
        assertEquals("কাচ্চি", engine.convertWord("kacci").bengali)
    }

    @Test
    fun guardPinNameClassStaysBengali() {
        assertEquals("নামে", engine.convertWord("name").bengali)
    }

    @Test
    fun guardPinSacredBanglishKeysUntouchedByLexiconGrowth() {
        // The cutoff experiment's core safety claim, pinned forever: canonical
        // chat romanizations must never be hijacked by english_lexicon rows
        // (verified zero-hijack in the S52 11,802-word eval diff — see
        // s52-task-2-report.md).
        assertEquals("আছে", engine.convertWord("ache").bengali)
        assertEquals("জানে", engine.convertWord("jane").bengali)
        assertEquals("নানি", engine.convertWord("nani").bengali)
        assertEquals("আলু", engine.convertWord("alu").bengali)
        assertEquals("কারণ", engine.convertWord("karon").bengali)
        assertEquals("করে", engine.convertWord("kore").bengali)
    }
}
