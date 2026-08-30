package com.banglu.engine

/**
 * S147: the curated "power words" the Android app shows off — words that are
 * genuinely hard to reach on a plain phonetic keyboard, organised as letter
 * FAMILIES (স/শ/ষ, ত/ট, …). Each keycap in a family carries its own example
 * words (the UI makes caps tappable), with the spelling variants (rule
 * spelling AND the casual chat hit) and the decided letter highlighted.
 * The list lives in `shared` so the UI and the regression wall read the SAME
 * data: every (variant, word) pair anywhere in this object is pinned by
 * S147ShowcaseWordsJvmTest on the real dictionary, so the app can never
 * advertise a conversion the engine gets wrong.
 */
object ShowcaseWords {
    /** [highlight] is the substring the UI paints in accent (may be empty). */
    data class Word(val bengali: String, val variants: List<String>, val highlight: String = "")

    /** One tappable keycap and the example words for THAT letter. */
    data class CapWords(val cap: String, val words: List<Word>)

    /** One swipeable page: confusing letters of one family. */
    data class Family(val tagline: String, val caps: List<CapWords>)

    val FAMILIES: List<Family> = listOf(
        Family(
            tagline = "একটা s — তিনটা অক্ষর, বানান বাংলুর কাজ",
            caps = listOf(
                CapWords("স", listOf(
                    Word("স্বাধীনতা", listOf("sbadhinota", "sadhinota"), "স্ব"),
                    Word("স্বাস্থ্য", listOf("shastho", "sbastho"), "স্বাস্থ্য"),
                    Word("সংক্ষিপ্ত", listOf("songkhipto"), "সংক্ষ")
                )),
                CapWords("শ", listOf(
                    Word("বিশ্ববিদ্যালয়", listOf("bishwabiddaloy"), "শ্ব"),
                    Word("শ্রদ্ধা", listOf("sroddha", "shroddha"), "শ্র"),
                    Word("বিশ্বাস", listOf("bishwas"), "শ্ব")
                )),
                CapWords("ষ", listOf(
                    Word("রাষ্ট্র", listOf("rashtro", "rastro"), "ষ্ট্র"),
                    Word("প্রতিষ্ঠান", listOf("protishthan"), "ষ্ঠ"),
                    Word("ঔষধ", listOf("oushodh"), "ষ")
                ))
            )
        ),
        Family(
            tagline = "একই t — শব্দ দেখে ত না ট",
            caps = listOf(
                CapWords("ত", listOf(
                    Word("তৃপ্তি", listOf("tripti"), "তৃ"),
                    Word("প্রত্যেক", listOf("prottek", "protyek"), "ত্য"),
                    Word("ঐতিহ্য", listOf("oitijjo", "oitihyo"), "তি")
                )),
                CapWords("ট", listOf(
                    Word("চেষ্টা", listOf("chesta"), "ষ্ট"),
                    Word("বৃষ্টি", listOf("brishti", "bristi"), "ষ্ট"),
                    Word("স্পষ্ট", listOf("sposto"), "ষ্ট")
                ))
            )
        ),
        Family(
            tagline = "একই d — বাংলু নিজে র‍্যাঙ্ক করে",
            caps = listOf(
                CapWords("দ", listOf(
                    Word("দৃষ্টি", listOf("drishti"), "দৃ"),
                    Word("উদ্দেশ্য", listOf("uddesho", "uddeshsho"), "দ্দ"),
                    Word("উদ্ধার", listOf("uddhar"), "দ্ধ")
                )),
                CapWords("ড", listOf(
                    Word("ডাক্তার", listOf("daktar"), "ডা"),
                    Word("ডাল", listOf("dal"), "ড")
                ))
            )
        ),
        Family(
            tagline = "সাধারণ r আর h-ই যথেষ্ট",
            caps = listOf(
                CapWords("র", listOf(
                    Word("সম্পূর্ণ", listOf("sompurno"), "র্ণ"),
                    Word("প্রশ্ন", listOf("proshno"), "প্র"),
                    Word("রাষ্ট্র", listOf("rashtro"), "ষ্ট্র")
                )),
                CapWords("ড়", listOf(
                    Word("বাড়ি", listOf("bari"), "ড়"),
                    Word("দাঁড়ি", listOf("dnari"), "ড়")
                )),
                CapWords("ঢ়", listOf(
                    Word("আষাঢ়", listOf("asharh"), "ঢ়")
                ))
            )
        ),
        Family(
            tagline = "কঠিন যুক্তবর্ণ, এক টাইপে",
            caps = listOf(
                CapWords("ক্ষ", listOf(
                    Word("লক্ষ্মী", listOf("lokkhi"), "ক্ষ্ম"),
                    Word("ক্ষমা", listOf("khoma", "kkhoma"), "ক্ষ"),
                    Word("শিক্ষা", listOf("shikkha"), "ক্ষা")
                )),
                CapWords("জ্ঞ", listOf(
                    Word("জিজ্ঞাসা", listOf("jiggasha"), "জ্ঞ"),
                    Word("বিজ্ঞান", listOf("bigyan"), "জ্ঞ"),
                    Word("জ্ঞান", listOf("gyan"), "জ্ঞ")
                )),
                CapWords("ঞ্চ", listOf(
                    Word("অঞ্চল", listOf("onchol"), "ঞ্চ"),
                    Word("পঞ্চাশ", listOf("ponchash"), "ঞ্চ")
                ))
            )
        ),
        Family(
            tagline = "চিহ্ন নিজে থেকেই বসে",
            caps = listOf(
                CapWords("ৎ", listOf(
                    Word("বিদ্যুৎ", listOf("biddut", "bidyut"), "ৎ"),
                    Word("উৎসব", listOf("utsob", "utsab"), "ৎ")
                )),
                CapWords("ঃ", listOf(
                    Word("দুঃখ", listOf("dukkho", "dukho"), "ঃ")
                )),
                CapWords("ঁ", listOf(
                    Word("দাঁড়ি", listOf("dnari"), "ঁ")
                ))
            )
        )
    )

    /** The try-card chips: romans the user is invited to type. */
    val TRY_WORDS: List<Word> = listOf(
        Word("স্বাধীনতা", listOf("sadhinota")),
        Word("স্বাস্থ্য", listOf("shastho")),
        Word("কৃষ্ণ", listOf("krishno")),
        Word("জিজ্ঞাসা", listOf("jiggasha")),
        Word("বিদ্যুৎ", listOf("bidyut")),
        Word("সৃষ্টি", listOf("srishti")),
        Word("লক্ষ্মী", listOf("lokkhi")),
        Word("কেমন", listOf("kmon"))
    )

    /** Onboarding feature-slide examples — pinned like everything else. */
    val FEATURE_WORDS: List<Word> = listOf(
        Word("স্বাধীনতা", listOf("sbadhinota"), "স্ব"),
        Word("টেস্টার", listOf("tester")),
        Word("আমি", listOf("ami")),
        Word("বাংলায়", listOf("banglay")),
        Word("লিখি", listOf("likhi"))
    )

    /** Every (roman, bengali) pair — the JVM pin wall iterates this. */
    val ALL_PAIRS: List<Pair<String, String>>
        get() = (FAMILIES.flatMap { f -> f.caps.flatMap { it.words } } + TRY_WORDS + FEATURE_WORDS)
            .flatMap { w -> w.variants.map { it to w.bengali } }
}
