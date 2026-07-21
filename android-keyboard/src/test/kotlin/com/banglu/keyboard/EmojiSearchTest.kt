package com.banglu.keyboard

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/** S57: pins the three-script emoji search + data sanity. */
class EmojiSearchTest {

    // ── search: Banglish ────────────────────────────────────────────────────

    @Test
    fun banglishHasiFindsLaughEmojis() {
        val r = EmojiData.search("hasi")
        assertTrue("😂" in r, "hasi should find 😂: $r")
        assertTrue(r.isNotEmpty())
    }

    @Test
    fun banglishMonKharapFindsSadEmojis() {
        val r = EmojiData.search("mon kharap")
        assertTrue(r.any { it in listOf("😔", "🙁", "😞") }, "mon kharap should find sad emoji: $r")
    }

    @Test
    fun banglishDuaFindsPrayerAndPhrases() {
        val r = EmojiData.search("dua")
        assertTrue("🤲" in r || "🙏" in r, "dua should find prayer emoji: $r")
        assertTrue(r.any { BanglaPhrases.isPhrase(it) }, "dua should find দোয়া phrases: $r")
    }

    @Test
    fun banglishTakaFindsMoney() {
        val r = EmojiData.search("taka")
        assertTrue(r.any { it in listOf("💰", "🤑", "💵") }, "taka should find money emoji: $r")
    }

    // ── search: Bangla script ───────────────────────────────────────────────

    @Test
    fun banglaHashiFindsLaughEmojis() {
        val r = EmojiData.search("হাসি")
        assertTrue("😂" in r, "হাসি should find 😂: $r")
    }

    @Test
    fun banglaEidFindsPhraseAndMoon() {
        val r = EmojiData.search("ঈদ")
        assertTrue(r.any { it.contains("ঈদ মোবারক") }, "ঈদ should find the Eid phrase: $r")
        assertTrue("🌙" in r, "ঈদ should find the crescent: $r")
    }

    @Test
    fun banglaBiralFindsCat() {
        val r = EmojiData.search("বিড়াল")
        assertTrue("🐱" in r, "বিড়াল should find 🐱: $r")
    }

    // ── search: English ─────────────────────────────────────────────────────

    @Test
    fun englishLoveRanksHeartsFirst() {
        val r = EmojiData.search("love")
        assertTrue(r.isNotEmpty())
        assertTrue(r.take(10).any { it in listOf("❤️", "🥰", "😍") }, "love should rank hearts early: ${r.take(10)}")
    }

    @Test
    fun englishBirthdayFindsCakeAndPhrase() {
        val r = EmojiData.search("birthday")
        assertTrue(r.any { it.contains("জন্মদিন") }, "birthday should find the phrase: $r")
    }

    @Test
    fun englishEidFindsEidPhrase() {
        val r = EmojiData.search("eid")
        assertTrue(r.any { it.contains("ঈদ মোবারক") }, "eid should find ঈদ মোবারক: $r")
    }

    @Test
    fun biryaniFindsCurry() {
        val r = EmojiData.search("biryani")
        assertTrue("🍛" in r, "biryani should find 🍛: $r")
    }

    // ── search: ranking + edges ─────────────────────────────────────────────

    @Test
    fun exactKeywordOutranksSubstring() {
        val r = EmojiData.search("rag")
        val firstEmoji = r.firstOrNull { !BanglaPhrases.isPhrase(it) }
        assertTrue(
            firstEmoji in listOf("😡", "😠", "😤", "🤬"),
            "rag should rank an angry emoji first: ${r.take(6)}"
        )
    }

    @Test
    fun garbageQueryReturnsEmpty() {
        assertTrue(EmojiData.search("zzqx999").isEmpty())
    }

    @Test
    fun blankQueryReturnsEmpty() {
        assertTrue(EmojiData.search("   ").isEmpty())
    }

    // ── data sanity ────────────────────────────────────────────────────────

    @Test
    fun noDuplicateEmojiWithinACategory() {
        for (cat in EmojiData.categories) {
            val dupes = cat.emojis.groupBy { it }.filterValues { it.size > 1 }.keys
            assertTrue(dupes.isEmpty(), "duplicates in ${cat.name}: $dupes")
        }
    }

    @Test
    fun everyPhraseHasAliases() {
        for (phrase in BanglaPhrases.allPhrases) {
            assertTrue(phrase.aliases.size >= 2, "phrase '${phrase.text}' needs >=2 aliases")
        }
    }

    @Test
    fun noDuplicatePhrases() {
        val dupes = BanglaPhrases.allPhrases.groupBy { it.text }.filterValues { it.size > 1 }.keys
        assertTrue(dupes.isEmpty(), "duplicate phrases: $dupes")
    }

    @Test
    fun phraseCountIsSubstantial() {
        assertTrue(BanglaPhrases.allPhrases.size >= 85, "expected 85+ phrases, got ${BanglaPhrases.allPhrases.size}")
    }

    @Test
    fun keywordEmojisAreWellFormed() {
        for ((emoji, words) in EmojiKeywords.keywords) {
            assertTrue(emoji.isNotBlank() && words.isNotEmpty(), "bad keyword row for '$emoji'")
        }
        assertTrue(EmojiKeywords.keywords.size >= 300, "expected 300+ keyword rows")
    }

    @Test
    fun phrasesCategoryExistsAtPinnedIndex() {
        assertEquals("বাক্য", EmojiData.categories[EmojiData.PHRASES_CATEGORY_INDEX].name)
    }
}
