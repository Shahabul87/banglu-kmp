package com.banglu.engine.dictionary

import com.banglu.engine.types.SmartDictionaryEntry
import com.banglu.engine.types.WordCategory

/**
 * Seed dictionary sync batch 6 — 21 entries synced from web engine.
 * Includes 2 new entries and 19 phonetic variant additions.
 */
object SeedDataSync6 {

    private fun e(
        bengali: String,
        phonetics: List<String>,
        frequency: Int,
        category: WordCategory = WordCategory.UNKNOWN
    ) = SmartDictionaryEntry(bengali, phonetics, frequency, category)

    val ENTRIES: List<SmartDictionaryEntry> by lazy {
        listOf(
            e("স্থানীয়", listOf("sthanio", "sthaniya", "sthaniio", "sthaniyaa", "sthaniiya", "sthaniou", "sthaniiou", "sthaniyo", "sthaniiy"), 87, WordCategory.TADBHAVA), // +phonetics
            e("পরিচিত", listOf("porichito", "parichit", "porichiito", "parichiit", "porichitou", "porichhito", "parichhit", "porichiitou", "porichhiito", "parichhiit", "parichita", "parichitaa", "parichiita", "parichhita", "parichhitaa", "parichhiita", "porichit"), 85, WordCategory.TADBHAVA), // +phonetics
            e("পুলিশ", listOf("pulish", "pulis", "puliish", "puulish", "puliis", "puulis", "police"), 85, WordCategory.FOREIGN), // +phonetics
            e("পরিণত", listOf("porinoto", "parinata", "poriinoto", "parinataa", "pariinata", "porinotou", "poriinotou", "porinot"), 84, WordCategory.TADBHAVA), // +phonetics
            e("লক্ষ্য", listOf("lokkho", "lakshya", "lakshyaa", "lokkhou", "laksya", "laksyaa", "lokkhz"), 84, WordCategory.TADBHAVA), // +phonetics
            e("রাজ্য", listOf("rajjo", "rajya", "razzo", "rajyaa", "razya", "rajjou", "razjo", "razzou", "rajzo", "razyaa", "rajyo", "rajy"), 83, WordCategory.TADBHAVA), // +phonetics
            e("ধর্মীয়", listOf("dhormio", "dharmiya", "dhormiio", "dharmiyaa", "dharmiiya", "dhormiou", "dhormiiou", "dhormiyo", "dhaarmiyo", "dhormiiyo", "dhormiiy"), 81, WordCategory.TADBHAVA), // +phonetics
            e("অতিরিক্ত", listOf("otirikto", "atirikta", "otiriikto", "atiriktaa", "atiriikta", "otiriktou", "otiriiktou", "otirikt"), 80, WordCategory.TADBHAVA), // +phonetics
            e("কষ্টার্জিত", listOf("kostarjito", "koshtarjito"), 80, WordCategory.TATSAMA), // NEW
            e("রনি", listOf("roni", "rony"), 75, WordCategory.TADBHAVA), // +phonetics
            e("ঘন্টা", listOf("ghonta", "ghanta"), 72, WordCategory.TATSAMA), // +phonetics
            e("নিউইয়র্ক", listOf("niuiyork", "newyork", "niuyork"), 70, WordCategory.TADBHAVA), // +phonetics
            e("রনী", listOf("ronii", "ronee"), 65, WordCategory.TADBHAVA), // NEW
            e("জোন", listOf("jon", "joun", "zon"), 60, WordCategory.UNKNOWN), // +phonetics
            e("লুটপাট", listOf("lutpat", "luutpaat", "lutopat", "lotpat", "lootpat"), 55, WordCategory.TADBHAVA), // +phonetics
            e("অ্যাকাউন্ট", listOf("account", "akauntt", "akaaunt", "ozakaunt"), 50, WordCategory.FOREIGN), // +phonetics
            e("ইসরায়েল", listOf("israel", "israyel", "ishrael", "ishrayel", "isorayel"), 50, WordCategory.FOREIGN), // +phonetics
            e("মন্ত্র", listOf("montro", "mantra", "mantraa", "montrou", "montr"), 50, WordCategory.TATSAMA), // +phonetics
            e("সন্তুষ্ট", listOf("shontushto", "santushta", "sontushto", "shontuushto", "santushtaa", "shantushta", "santuushta", "shontusto", "shontushtou", "santusta", "sontusto", "sontushtou", "shontuusto", "shontuushtou", "santustaa", "shantusta", "santuusta", "sontush", "sontuushto", "shontush", "sontuush", "sontuusto", "sontuushtou", "sontusht"), 50, WordCategory.TATSAMA), // +phonetics
            e("সর্বত্র", listOf("sorbotro", "sarbatra", "shorbotro", "sarbatraa", "sharbatra", "sorbotrou", "shorbotrou", "shorbotto", "sorbotto", "sorbotr"), 50, WordCategory.TATSAMA), // +phonetics
            e("হৃদয়ে", listOf("hridoye", "hridaye", "hrridoye"), 50, WordCategory.TATSAMA) // +phonetics
        )
    }
}
