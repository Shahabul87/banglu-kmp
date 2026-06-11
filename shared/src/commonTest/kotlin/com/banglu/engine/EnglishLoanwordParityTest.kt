package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class EnglishLoanwordParityTest {
    private val engine = SmartEngine().also { it.initializeSync() }

    @Test
    fun countryNamesUseDictionaryEntries() {
        val cases = mapOf(
            "america" to "আমেরিকা",
            "china" to "চীন",
            "afghanistan" to "আফগানিস্তান",
            "canada" to "কানাডা",
            "germany" to "জার্মানি"
        )

        for ((input, expected) in cases) {
            assertEquals(expected, engine.convertWord(input).bengali, input)
        }
    }

    @Test
    fun scienceTechnologyAndNamesDoNotFallThroughToPatterns() {
        val cases = mapOf(
            "hardware" to "হার্ডওয়্যার",
            "physics" to "ফিজিক্স",
            "chemistry" to "কেমিস্ট্রি",
            "biology" to "বায়োলজি",
            "mathematics" to "ম্যাথমেটিক্স",
            "virus" to "ভাইরাস",
            "john" to "জন",
            "michael" to "মাইকেল",
            "william" to "উইলিয়াম",
            "aisha" to "আইশা"
        )

        for ((input, expected) in cases) {
            assertEquals(expected, engine.convertWord(input).bengali, input)
        }
    }

    @Test
    fun curatedLoanwordsKeepBengaliPronunciationPrimary() {
        val cases = mapOf(
            "honeymoon" to "হানিমুন",
            "application" to "এপ্লিকেশন",
            "database" to "ডেটাবেস",
            "byatha" to "ব্যথা",
            "porikkha" to "পরীক্ষা",
            "gobeshona" to "গবেষণা",
            "montri" to "মন্ত্রী",
            "ghoshona" to "ঘোষণা",
            "koti" to "কোটি",
            "mangsho" to "মাংস",
            "ojon" to "ওজন",
            "shahid" to "শহীদ",
            "puroshkar" to "পুরস্কার",
            "shilpi" to "শিল্পী",
            "poribohon" to "পরিবহন",
            "tyag" to "ত্যাগ"
        )

        for ((input, expected) in cases) {
            assertEquals(expected, engine.convertWord(input).bengali, input)
        }
    }

    @Test
    fun englishOriginalStaysAvailableForKnownEnglishWords() {
        val suggestions = engine.getSuggestions("honeymoon", 8).map { it.bengali }

        assertEquals("হানিমুন", suggestions.first())
        kotlin.test.assertTrue(suggestions.contains("honeymoon"), suggestions.toString())
    }

    @Test
    fun everydayBanglishLoanwordsUseBengaliPronunciationPrimary() {
        val cases = mapOf(
            "practice" to "প্রাকটিস",
            "scooter" to "স্কুটার",
            "content" to "কনটেন্ট",
            "inbox" to "ইনবক্স",
            "status" to "স্ট্যাটাস",
            "hostel" to "হোস্টেল",
            "dictionary" to "ডিকশনারি",
            "bread" to "ব্রেড",
            "butter" to "বাটার",
            "cheese" to "চিজ",
            "dinner" to "ডিনার",
            "pocket" to "পকেট",
            "skirt" to "স্কার্ট",
            "tshirt" to "টি-শার্ট",
            "pants" to "প্যান্ট",
            "kg" to "কেজি",
            "liter" to "লিটার",
            "percent" to "পারসেন্ট",
            "packet" to "প্যাকেট",
            "minute" to "মিনিট",
            "politics" to "পলিটিক্স",
            "cycle" to "সাইকেল"
        )

        for ((input, expected) in cases) {
            assertEquals(expected, engine.convertWord(input).bengali, input)
        }
    }

    @Test
    fun englishBaseLightVerbFramesConvertNaturallyWordByWord() {
        val cases = mapOf(
            "practice kora" to "প্রাকটিস করা",
            "discussion kora" to "ডিসকাশন করা",
            "complain kora" to "কমপ্লেইন করা",
            "confuse kora" to "কনফিউজ করা",
            "accident howa" to "এক্সিডেন্ট হওয়া",
            "active thaka" to "অ্যাকটিভ থাকা"
        )

        for ((input, expected) in cases) {
            assertEquals(expected, engine.parse(input), input)
        }
    }

    @Test
    fun englishOriginalStaysAvailableForEverydayLoanwords() {
        val suggestions = engine.getSuggestions("practice", 8).map { it.bengali }

        assertEquals("প্রাকটিস", suggestions.first())
        kotlin.test.assertTrue(suggestions.contains("practice"), suggestions.toString())
    }

    @Test
    fun commonEnglishSpellingsUseCuratedPronunciationVariants() {
        val cases = mapOf(
            "remove" to "রিমুভ",
            "possible" to "পসিবল",
            "important" to "ইমপোর্ট্যান্ট",
            "available" to "অ্যাভেইলেবল",
            "correct" to "কারেক্ট",
            "simple" to "সিম্পল",
            "message" to "মেসেজ",
            "meeting" to "মিটিং",
            "project" to "প্রজেক্ট",
            "manager" to "ম্যানেজার",
            "customer" to "কাস্টমার",
            "service" to "সার্ভিস",
            "student" to "স্টুডেন্ট",
            "teacher" to "টিচার",
            "ticket" to "টিকিট",
            "booking" to "বুকিং",
            "treatment" to "ট্রিটমেন্ট",
            "please" to "প্লিজ",
            "sorry" to "সরি",
            "hello" to "হ্যালো"
        )

        for ((input, expected) in cases) {
            assertEquals(expected, engine.convertWord(input).bengali, input)
        }
    }

    @Test
    fun unknownEnglishStillPassesThroughInsteadOfLoosePronunciation() {
        val result = engine.convertWord("brightness")

        assertEquals("brightness", result.bengali)
    }

    @Test
    fun commonMobileShorthandUsesNaturalBengaliWords() {
        val cases = mapOf(
            "amr" to "আমার",
            "tomr" to "তোমার",
            "tmi" to "তুমি",
            "tomi" to "তুমি"
        )

        for ((input, expected) in cases) {
            assertEquals(expected, engine.convertWord(input).bengali, input)
        }
    }
}
