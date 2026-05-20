package com.banglu.engine

import com.banglu.engine.types.ResolutionSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CandidateLatticeTest {
    private val engine = SmartEngine().also { it.initializeSync() }

    private fun suggestions(input: String): List<String> =
        engine.getSuggestions(input, 40).map { it.bengali }

    @Test
    fun usesDirectDictionaryEntriesForCommonEnglishTechnologyAndPlaceWords() {
        val cases = listOf(
            "keyboard" to "কীবোর্ড",
            "laptop" to "ল্যাপটপ",
            "software" to "সফটওয়্যার",
            "github" to "গিটহাব",
            "machinelearning" to "মেশিন লার্নিং",
            "barishal" to "বরিশাল",
            "sanfrancisco" to "সান ফ্রান্সিসকো",
            "doctor" to "ডাক্তার",
            "restaurant" to "রেস্টুরেন্ট",
            "download" to "ডাউনলোড"
        )

        for ((roman, bengali) in cases) {
            assertEquals(bengali, engine.convertWord(roman).bengali)
            assertEquals(bengali, suggestions(roman).first())
        }
    }

    @Test
    fun keepsShortDefaultsFirstWhileOfferingRetroflexAlternatives() {
        assertEquals("ত", engine.convertWord("t").bengali)
        assertTrue("ট" in suggestions("t"))

        assertEquals("তা", engine.convertWord("ta").bengali)
        assertTrue("টা" in suggestions("ta"))

        assertEquals("দ", engine.convertWord("d").bengali)
        assertTrue("ড" in suggestions("d"))

        assertEquals("দা", engine.convertWord("da").bengali)
        assertTrue("ডা" in suggestions("da"))
    }

    @Test
    fun promotesCompletedDictionaryWordsWhileKeepingLiteralAlternatives() {
        assertEquals("টাকা", engine.convertWord("taka").bengali)
        assertTrue("টাকা" in suggestions("taka"))
        assertTrue("তাকা" in suggestions("taka"))

        assertEquals("ঠান্ডা", engine.convertWord("thanda").bengali)
        assertTrue("থান্দা" in suggestions("thanda"))
    }

    @Test
    fun composingConversionStaysConservativeUntilCommit() {
        assertEquals("টাকা", engine.convertWord("taka").bengali)
        assertEquals("টাকা", engine.convertForComposing("taka").bengali)

        val composingPrefix = engine.convertForComposing("doro")
        assertTrue(composingPrefix.bengali.isNotEmpty())
        assertTrue(composingPrefix.source == ResolutionSource.RULE || composingPrefix.source == ResolutionSource.DICTIONARY)

        assertEquals("দরজা", engine.convertWord("doroja").bengali)
        val composingCompleted = engine.convertForComposing("doroja")
        assertTrue(composingCompleted.bengali.isNotEmpty())
        assertTrue(composingCompleted.source == ResolutionSource.RULE || composingCompleted.source == ResolutionSource.DICTIONARY)
    }

    @Test
    fun doesNotCollapsePathaPathanToShorterPothCandidate() {
        assertEquals("পথ", engine.convertWord("path").bengali)
        assertEquals("পাথা", engine.convertWord("patha").bengali)
        assertTrue("পাথা" in suggestions("patha"))
        assertTrue("পাঠা" in suggestions("patha"))

        assertEquals("পাঠান", engine.convertWord("pathan").bengali)
        assertTrue("পাঠান" in suggestions("pathan"))
        assertTrue("পাথান" in suggestions("pathan"))
    }

    @Test
    fun keepsShortLongIAndOiOyQuestionWordAlternativesVisible() {
        assertEquals("কি", engine.convertWord("ki").bengali)
        assertTrue("কি" in suggestions("ki"))
        assertTrue("কী" in suggestions("ki"))

        assertEquals("কী", engine.convertWord("kii").bengali)
        assertTrue("কী" in suggestions("kii"))
        assertTrue("কি" in suggestions("kii"))

        assertEquals("কই", engine.convertWord("koi").bengali)
        assertTrue("কই" in suggestions("koi"))
        assertTrue("কয়" in suggestions("koi"))

        assertEquals("কয়", engine.convertWord("koy").bengali)
        assertTrue("কয়" in suggestions("koy"))
        assertTrue("কই" in suggestions("koy"))
    }

    @Test
    fun generalizesOiOyAmbiguityBeyondKPrefixedExamples() {
        val cases = listOf(
            Triple("hoi", "হই", "হয়"),
            Triple("hoy", "হয়", "হই"),
            Triple("noi", "নই", "নয়"),
            Triple("noy", "নয়", "নই"),
            Triple("roi", "রই", "রয়"),
            Triple("roy", "রয়", "রই"),
            Triple("soi", "সই", "সয়"),
            Triple("soy", "সয়", "সই"),
            Triple("boi", "বই", "বয়"),
            Triple("boy", "বয়", "বই")
        )

        for ((input, expectedPrimary, expectedAlternative) in cases) {
            val result = engine.convertWord(input).bengali
            val candidates = listOf(result) + suggestions(input)
            assertTrue(expectedPrimary in candidates, "Expected $input -> $expectedPrimary in $candidates")
            assertTrue(expectedAlternative in suggestions(input), "Expected $expectedAlternative in suggestions for $input")
        }
    }

    @Test
    fun promotesDictionaryBackedOiPlusRetroflexCandidatesForCompletedWords() {
        val result = engine.convertWord("koita").bengali
        val candidates = suggestions("koita")

        assertEquals("কয়টা", result, "candidates=$candidates")
        assertTrue("কয়টা" in candidates)
        assertTrue("কইতা" in candidates)
        assertTrue("কৈটা" in candidates)
    }

    @Test
    fun promotesDictionaryBackedChWordsWhileKeepingChAlternatives() {
        assertEquals("কিছুতেই", engine.convertWord("kichutei").bengali)
        assertTrue("কিছুতেই" in suggestions("kichutei"))
        assertTrue("কিচুতেই" in suggestions("kichutei"))

        assertEquals("ছবি", engine.convertWord("chobi").bengali)
        assertTrue("ছবি" in suggestions("chobi"))
        assertTrue("চবি" in suggestions("chobi"))
    }

    @Test
    fun treatsCAndChAsChhaChaAmbiguityWithDifferentDefaults() {
        assertEquals("চোখ", engine.convertWord("chokh").bengali)
        assertTrue("চোখ" in suggestions("chokh"))
        assertTrue("ছোখ" in suggestions("chokh"))

        assertEquals("চিঠি", engine.convertWord("chithi").bengali)
        assertTrue("চিঠি" in suggestions("chithi"))
        assertTrue("ছিঠি" in suggestions("chithi"))

        assertEquals("ছবি", engine.convertWord("cobi").bengali)
        assertTrue("ছবি" in suggestions("cobi"))
        assertTrue("চবি" in suggestions("cobi"))

        assertEquals("ছুটি", engine.convertWord("cuti").bengali)
        assertTrue("ছুটি" in suggestions("cuti"))
        assertTrue("চুটি" in suggestions("cuti"))
    }

    @Test
    fun keepsBAndVDirectInsteadOfSameSoundAmbiguity() {
        assertEquals("বাল", engine.convertWord("bal").bengali)
        assertEquals("বাল", suggestions("bal").first())
        assertEquals("ভাল", engine.convertWord("bhal").bengali)
        assertEquals("ভাল", engine.convertWord("val").bengali)
    }

    @Test
    fun filtersPhoneticallyUnmatchedSuggestionNoise() {
        engine.addWord("borishal", "বুষাল", 95)
        val candidates = suggestions("borishal")
        assertEquals("বরিশাল", engine.convertWord("borishal").bengali)
        assertEquals("বরিশাল", candidates.first())
        assertTrue("বরিসাল" in candidates)
        assertTrue("বরীশাল" in candidates)
        assertTrue("বুষাল" !in candidates, "Unexpected noisy candidate in $candidates")
    }

    @Test
    fun filtersGeneratedGarbageWhenRealWordIsKnown() {
        val candidates = suggestions("obossoi")

        assertEquals("অবশ্যই", candidates.first(), "candidates=$candidates")
        assertFalse("অবস্যে" in candidates, "Unexpected generated candidate in $candidates")
        assertFalse("অবোসওই" in candidates, "Unexpected generated candidate in $candidates")
        assertFalse("অবোসয়" in candidates, "Unexpected generated candidate in $candidates")
        assertFalse("অবোসওয়" in candidates, "Unexpected generated candidate in $candidates")
        assertTrue(candidates.none { it.any { ch -> ch in 'a'..'z' || ch in 'A'..'Z' } }, "Mixed-script candidate in $candidates")
        assertFalse("অবস্সৈ" in candidates, "Unexpected generated candidate in $candidates")
        assertFalse("অবশ্সৈ" in candidates, "Unexpected generated candidate in $candidates")
    }

    @Test
    fun doesNotCollapsePartialSsIntoS() {
        assertFalse("অবশ" in suggestions("oboss"), "Unexpected partial ss collapse")
        assertFalse("অবশো" in suggestions("obosso"), "Unexpected partial ss collapse")
        assertEquals("অবশ্যই", suggestions("obossoi").first())
    }

    @Test
    fun buildsDorojaCandidatesAndResolvesDorja() {
        val result = engine.convertWord("doroja")
        val candidates = suggestions("doroja")

        assertEquals("দরজা", result.bengali)
        assertEquals("দরজা", candidates.first())
        listOf(
            "দরজা",
            "দরযা",
            "দোরজা",
            "দোরযা",
            "ডরজা",
            "ডরযা",
            "ডোরজা",
            "ডোরযা"
        ).forEach { expected ->
            assertTrue(expected in candidates, "Expected $expected in $candidates")
        }
    }

    @Test
    fun promotesStrongerTypedPathMatchesOverWeakerValidLookingVariants() {
        val cases = listOf(
            "etotai" to "এতটাই",
            "konotai" to "কোনটাই",
            "somoyotai" to "সময়টাই",
            "eitai" to "এইটাই",
            "etai" to "এটাই",
            "setai" to "সেটাই",
            "otai" to "ওটাই"
        )

        for ((input, expected) in cases) {
            val result = engine.convertWord(input).bengali
            val candidates = suggestions(input)
            assertEquals(expected, result, "candidates=$candidates")
            assertEquals(expected, candidates.first(), "candidates=$candidates")
        }
    }

    @Test
    fun handlesCoreAmbiguityCorpusThroughPrimaryOrSuggestions() {
        val cases = listOf(
            "rat" to "রাত",
            "ghat" to "ঘাট",
            "kath" to "কাঠ",
            "path" to "পথ",
            "pathan" to "পাঠান",
            "pathano" to "পাঠানো",
            "pathal" to "পাঠাল",
            "pathyo" to "পাঠ্য",
            "dhan" to "ধান",
            "dhaka" to "ঢাকা",
            "karan" to "কারণ",
            "rang" to "রং",
            "bangla" to "বাংলা",
            "basha" to "বাসা",
            "bhasha" to "ভাষা",
            "bish" to "বিষ",
            "desh" to "দেশ",
            "dristi" to "দৃষ্টি",
            "ghori" to "ঘড়ি",
            "karma" to "কর্ম",
            "gram" to "গ্রাম",
            "jibon" to "জীবন",
            "jog" to "যোগ",
            "hoy" to "হয়",
            "bakyo" to "বাক্য",
            "choto" to "ছোট",
            "chhele" to "ছেলে",
            "iccha" to "ইচ্ছা",
            "din" to "দিন",
            "kul" to "কুল",
            "bon" to "বোন",
            "nouka" to "নৌকা",
            "chand" to "চাঁদ",
            "ki" to "কি",
            "kii" to "কী",
            "koi" to "কই",
            "koy" to "কয়"
        )

        for ((input, expected) in cases) {
            val result = engine.convertWord(input).bengali
            val candidates = listOf(result) + suggestions(input)
            assertTrue(expected in candidates, "Expected $input -> $expected in $candidates")
        }
    }

    @Test
    fun coversExpandedSameSoundAmbiguityCorpusFromMappingTable() {
        val cases = listOf(
            // t / T / ৎ family
            Triple("tumi", "তুমি", "t dental"),
            Triple("tel", "তেল", "t dental"),
            Triple("tara", "তারা", "t dental"),
            Triple("tokhon", "তখন", "t dental"),
            Triple("tap", "তাপ", "t dental"),
            Triple("taka", "টাকা", "t retroflex"),
            Triple("tebil", "টেবিল", "t retroflex"),
            Triple("tan", "টান", "t retroflex"),
            Triple("tukro", "টুকরো", "t retroflex"),
            Triple("toka", "টোকা", "t retroflex"),
            Triple("utpadon", "উৎপাদন", "ৎ learned"),
            Triple("utsob", "উৎসব", "ৎ learned"),
            Triple("rat", "রাত", "t final dental"),
            Triple("ghat", "ঘাট", "t final retroflex"),

            // th / Th family
            Triple("thaka", "থাকা", "th dental"),
            Triple("kotha", "কথা", "th dental"),
            Triple("thanda", "ঠান্ডা", "th retroflex"),
            Triple("kath", "কাঠ", "th final retroflex"),
            Triple("path", "পথ", "th final dental"),
            Triple("pathan", "পাঠান", "th retroflex word"),

            // d / D / r-flap family
            Triple("dorja", "দরজা", "d dental"),
            Triple("dhan", "ধান", "dh dental"),
            Triple("dhaka", "ঢাকা", "dh retroflex"),
            Triple("dal", "ডাল", "d retroflex"),
            Triple("dak", "ডাক", "d retroflex"),
            Triple("ghori", "ঘড়ি", "r flap"),
            Triple("boro", "বড়", "r flap"),

            // n / ng family
            Triple("karon", "কারণ", "n retroflex spelling"),
            Triple("bangla", "বাংলা", "anusvara"),
            Triple("bangali", "বাঙালি", "nga spelling"),
            Triple("rang", "রং", "final nasal"),

            // s / sh / ষ family
            Triple("basha", "বাসা", "s dental"),
            Triple("bhasha", "ভাষা", "ষ spelling"),
            Triple("bish", "বিষ", "ষ final"),
            Triple("desh", "দেশ", "শ final"),
            Triple("dristi", "দৃষ্টি", "ষ্ট cluster"),
            Triple("shohor", "শহর", "শ initial"),

            // r / reph / ra-phala
            Triple("karma", "কর্ম", "reph cluster"),
            Triple("gram", "গ্রাম", "ra-phala"),

            // j / y family
            Triple("jibon", "জীবন", "j"),
            Triple("jog", "যোগ", "y/য word"),
            Triple("hoy", "হয়", "য়"),
            Triple("baky", "বাক্য", "ya-phala"),

            // ch / chh family
            Triple("cha", "চা", "ch"),
            Triple("chhele", "ছেলে", "chh"),
            Triple("iccha", "ইচ্ছা", "চ্ছ cluster"),

            // vowel ambiguity
            Triple("din", "দিন", "short i"),
            Triple("nil", "নীল", "long i"),
            Triple("kul", "কুল", "short u"),
            Triple("dur", "দূর", "long u"),
            Triple("bon", "বোন", "o/ô"),
            Triple("nouka", "নৌকা", "ou"),
            Triple("chand", "চাঁদ", "nasalized vowel")
        )

        assertEquals(53, cases.size)

        for ((input, expected, label) in cases) {
            val result = engine.convertWord(input).bengali
            val candidates = listOf(result) + suggestions(input)
            assertTrue(expected in candidates, "[$label] Expected $input -> $expected in $candidates")
        }
    }
}
