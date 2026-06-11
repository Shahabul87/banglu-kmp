package com.banglu.engine

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImeLargeRegressionFixtureTest {

    @AfterTest
    fun tearDown() {
        SmartEngineAdapter.reset()
    }

    @Test
    fun exactGoldenWordsStayStable() {
        SmartEngineAdapter.initializeSync()

        val cases = listOf(
            "ami" to "আমি",
            "tumi" to "তুমি",
            "apni" to "আপনি",
            "bangladesh" to "বাংলাদেশ",
            "bhalo" to "ভালো",
            "khabar" to "খাবার",
            "ekhon" to "এখন",
            "kintu" to "কিন্তু",
            "sundor" to "সুন্দর",
            "proshno" to "প্রশ্ন",
            "uttor" to "উত্তর",
            "shikkha" to "শিক্ষা",
            "srishti" to "সৃষ্টি",
            "drishti" to "দৃষ্টি",
            "krishna" to "কৃষ্ণ",
            "brihospoti" to "বৃহস্পতি",
            "dorja" to "দরজা",
            "taka" to "টাকা",
            "biswas" to "বিশ্বাস",
            "bissas" to "বিশ্বাস",
            "obossoi" to "অবশ্যই",
            "bhiti" to "ভিতি",
            "onekkhon" to "অনেকক্ষণ",
            "priyojon" to "প্রিয়জন",
            "ghoro" to "ঘর",
            "puruskar" to "পুরস্কার",
            "education" to "এডুকেশন",
            "application" to "এপ্লিকেশন",
            "database" to "ডেটাবেস",
            "honeymoon" to "হানিমুন",
            "porikkha" to "পরীক্ষা",
            "gobeshona" to "গবেষণা",
            "byatha" to "ব্যথা",
            "montri" to "মন্ত্রী",
            "ghoshona" to "ঘোষণা",
            "mangsho" to "মাংস",
            "ojon" to "ওজন",
            "shahid" to "শহীদ",
            "shilpi" to "শিল্পী",
            "poribohon" to "পরিবহন",
            "tyag" to "ত্যাগ",
            "amr" to "আমার",
            "tomr" to "তোমার",
            "tmi" to "তুমি",
            "tomi" to "তুমি",
        )

        for ((input, expected) in cases) {
            assertEquals(expected, commitLikeIme(input), "input=$input")
        }
    }

    @Test
    fun exactGoldenPhrasesStayStable() {
        SmartEngineAdapter.initializeSync()

        val cases = listOf(
            "ami bhalo achi" to "আমি ভালো আছি",
            "tumi kemon acho" to "তুমি কেমন আছো",
            "ami bangla likhte chai" to "আমি বাংলা লিখতে চাই",
            "taka dorja" to "টাকা দরজা",
            "ami taka dibo" to "আমি টাকা দিবো",
            "tumi dorja kholo" to "তুমি দরজা খোলো",
            "shikkha khub dorkar" to "শিক্ষা খুব দরকার",
            "proshno uttor" to "প্রশ্ন উত্তর",
            "obiswas noy biswas" to "অবিশ্বাস নয় বিশ্বাস",
            "obossoi ami jabo" to "অবশ্যই আমি যাবো",
            "porikkha bhalo hoyeche" to "পরীক্ষা ভালো হয়েছে",
            "gobeshona khub guruttopurno" to "গবেষণা খুব গুরুত্বপূর্ণ",
            "application database" to "এপ্লিকেশন ডেটাবেস",
            "education system bhalo" to "এডুকেশন সিস্টেম ভালো",
            "honeymoon travel plan" to "হানিমুন ট্রাভেল প্ল্যান",
            "amr bari bangladesh" to "আমার বাড়ি বাংলাদেশ",
            "tomr khabar ache" to "তোমার খাবার আছে",
            "tmi bhalo kore poro" to "তুমি ভালো করে পড়ো",
            "bhiti dur koro" to "ভিতি দূর করো",
            "onekkhon dhore boshe achi" to "অনেকক্ষণ ধরে বসে আছি",
            "priyojon khub dorkar" to "প্রিয়জন খুব দরকার",
            "ghoro porishkar koro" to "ঘর পরিষ্কার করো",
            "puruskar peye khushi" to "পুরস্কার পেয়ে খুশি",
            "montri ghoshona korechen" to "মন্ত্রী ঘোষণা করেছেন",
            "mangsho ojon kore dao" to "মাংস ওজন করে দাও",
            "shahid shilpi poribohon" to "শহীদ শিল্পী পরিবহন",
            "tyag korte hobe" to "ত্যাগ করতে হবে",
            "srishti drishti krishna" to "সৃষ্টি দৃষ্টি কৃষ্ণ",
            "brihospoti din" to "বৃহস্পতি দিন",
            "ami application banabo" to "আমি এপ্লিকেশন বানাবো",
        )

        for ((input, expected) in cases) {
            assertEquals(expected, commitLikeIme(input), "input=$input")
        }
    }

    @Test
    fun broadRealPhraseFixtureDoesNotLeakLatinOrBrokenClusters() {
        SmartEngineAdapter.initializeSync()

        val subjects = listOf("ami", "tumi", "apni", "amra", "ora", "se", "tmi", "tomi")
        val actions = listOf(
            "bhalo achi",
            "bangla likhi",
            "khabar khai",
            "porikkha dibo",
            "gobeshona kori",
            "dorja kholo",
            "taka dibo",
            "kotha boli",
            "shikkha nibo",
            "proshno korbo",
            "uttor dibo",
            "application banabo",
            "database dekhbo",
            "education nibo",
            "ghoro porishkar korbo",
            "onekkhon boshe achi",
            "priyojon dakbo",
            "obossoi jabo",
            "bhalo kore porbo",
            "sundor kore likhbo",
        )
        val endings = listOf(
            "aj",
            "ekhon",
            "kal",
            "obossoi",
            "kintu",
            "bhalo kore",
            "khub taratari",
            "bangladesh e",
            "dorjar pase",
            "onek somoy",
        )

        val generated = buildList {
            for (subject in subjects) {
                for (action in actions) {
                    for (ending in endings) {
                        add("$subject $action $ending")
                    }
                }
            }
        }.take(300)

        assertTrue(generated.size >= 250, "Expected at least 250 generated phrase cases")

        for (input in generated) {
            val output = commitLikeIme(input)
            assertTrue(output.isNotBlank(), "blank output for input=$input")
            assertFalse(Regex("[A-Za-z]").containsMatchIn(output), "Latin leaked for input=$input output=$output")
            assertFalse(output.contains("্্"), "double hasanta for input=$input output=$output")
            assertFalse(output.endsWith("্"), "dangling hasanta for input=$input output=$output")
        }
    }

    @Test
    fun customDictionaryFormulaWinsAcrossLargeFixture() {
        SmartEngineAdapter.initializeSync()
        SmartEngineAdapter.addCustomConversion("ottadhunik", "অত্যাধুনিক")
        SmartEngineAdapter.addCustomConversion("taxomind", "ট্যাক্সোমাইন্ড")

        val cases = listOf(
            "ottadhunik jug" to "অত্যাধুনিক যুগ",
            "ami ottadhunik application banabo" to "আমি অত্যাধুনিক এপ্লিকেশন বানাবো",
            "taxomind application" to "ট্যাক্সোমাইন্ড এপ্লিকেশন",
            "taxomind database" to "ট্যাক্সোমাইন্ড ডেটাবেস",
        )

        for ((input, expected) in cases) {
            assertEquals(expected, commitLikeIme(input), "input=$input")
        }
    }

    private fun commitLikeIme(input: String): String {
        val output = StringBuilder()
        val context = mutableListOf<String>()
        var buffer = StringBuilder()

        fun commitBuffer() {
            if (buffer.isEmpty()) return
            val result = SmartEngineAdapter.convertWordWithContext(buffer.toString(), context)
            output.append(result.bengali)
            context.add(result.bengali)
            buffer = StringBuilder()
        }

        for (ch in input) {
            if (ch.isWhitespace()) {
                commitBuffer()
                output.append(ch)
            } else {
                buffer.append(ch)
            }
        }
        commitBuffer()

        return output.toString()
    }
}
