package com.banglu.engine.rules

/**
 * ConjunctTable - Centralized Bengali multi-character consonant patterns
 *
 * Ported from SmartEngine's ConjunctTable.ts.
 * All patterns are lowercase - no Shift key required.
 *
 * IMPORTANT: Patterns are matched longest-first. The list is sorted
 * with longer phonetic strings before shorter ones.
 */
object ConjunctTable {

    data class ConjunctEntry(
        val phonetic: String,
        val bengali: String
    )

    /**
     * Multi-character consonant patterns (matched longest-first).
     *
     * Groups:
     * - 4+ char: doubled aspirate conjuncts, 3-consonant clusters
     * - 3 char: aspirated consonant combos, y-phala/r-phala after aspirated
     * - 2 char: aspirated consonants, common conjunct pairs, doubles
     */
    val TABLE: List<ConjunctEntry> = listOf(
        // ====================================================================
        // 5+ chars — long multi-consonant clusters
        // ====================================================================
        ConjunctEntry("kkhm", "ক্ষ্ম"),     // সূক্ষ্ম, লক্ষ্মী
        ConjunctEntry("shtr", "ষ্ট্র"),     // রাষ্ট্র, রাষ্ট্রীয়

        // ====================================================================
        // 4 chars — doubled aspirate conjuncts, 3-consonant clusters
        // ====================================================================
        ConjunctEntry("chch", "চ্ছ"),       // দিচ্ছে
        ConjunctEntry("jhjh", "ঝ্ঝ"),       // rare
        ConjunctEntry("ngkh", "ঙ্খ"),       // শঙ্খ
        ConjunctEntry("nggh", "ঙ্ঘ"),       // সঙ্ঘ

        // ====================================================================
        // 3 chars — aspirated combos, nasal groups, clusters
        // ====================================================================
        ConjunctEntry("chh", "ছ"),           // chh = ছ (aspirated ch)
        ConjunctEntry("cch", "চ্ছ"),         // আচ্ছা (alternate spelling)
        ConjunctEntry("kkh", "ক্ষ"),
        ConjunctEntry("ksh", "ক্ষ"),
        ConjunctEntry("shr", "শ্র"),         // শ্রাবণ
        ConjunctEntry("str", "স্ত্র"),        // স্ত্রী, অস্ত্র
        // Ya-phala after aspirated consonants
        ConjunctEntry("khy", "খ্য"),         // সংখ্যা
        ConjunctEntry("ghy", "ঘ্য"),         // rare
        ConjunctEntry("thy", "থ্য"),         // rare
        ConjunctEntry("dhy", "ধ্য"),         // সন্ধ্যা
        ConjunctEntry("phy", "ফ্য"),         // rare
        ConjunctEntry("bhy", "ভ্য"),         // rare
        // R-phala after aspirated consonants
        ConjunctEntry("dhr", "ধ্র"),         // ধ্রুব
        ConjunctEntry("bhr", "ভ্র"),         // ভ্রমণ
        ConjunctEntry("ghr", "ঘ্র"),         // ঘ্রাণ
        ConjunctEntry("khr", "খ্র"),         // খ্রিস্টান
        ConjunctEntry("ttr", "ট্র"),         // ট্রেন, ট্রাক
        // Special patterns
        ConjunctEntry("rri", "ৃ"),           // context-handled: ঋ when independent
        ConjunctEntry("ngo", "ঙ্গ"),
        ConjunctEntry("nga", "ঙ্গা"),
        ConjunctEntry("ngk", "ঙ্ক"),         // অঙ্ক, লঙ্কা, শঙ্কা
        ConjunctEntry("ngm", "ঙ্ম"),         // বাঙ্ময়
        ConjunctEntry("njh", "ঞ্ঝ"),         // ঝঞ্ঝা
        // 3-char conjuncts — aspirate+consonant combos
        ConjunctEntry("ddh", "দ্ধ"),         // শুদ্ধ, বুদ্ধি
        ConjunctEntry("dbh", "দ্ভ"),         // অদ্ভুত, উদ্ভব
        ConjunctEntry("dgh", "দ্ঘ"),         // উদ্ঘাটন
        ConjunctEntry("mbh", "ম্ভ"),         // দম্ভ, সম্ভব
        ConjunctEntry("lbh", "ল্ভ"),         // প্রগল্ভ
        ConjunctEntry("nth", "ন্থ"),         // গ্রন্থ, পান্থ
        ConjunctEntry("tth", "ত্থ"),         // উত্থান
        ConjunctEntry("jjb", "জ্জ্ব"),       // উজ্জ্বল
        ConjunctEntry("ntr", "ন্ত্র"),        // মন্ত্র, যন্ত্র
        ConjunctEntry("ndr", "ন্দ্র"),        // কেন্দ্র, চন্দ্র
        ConjunctEntry("bdh", "ব্ধ"),         // লব্ধ
        ConjunctEntry("gdh", "গ্ধ"),         // মুগ্ধ
        ConjunctEntry("ndh", "ন্ধ"),         // অন্ধ, বন্ধু

        // ====================================================================
        // 2 chars — aspirated consonants
        // ====================================================================
        ConjunctEntry("kh", "খ"),
        ConjunctEntry("gh", "ঘ"),
        ConjunctEntry("ch", "চ"),            // Default: চ (more common)
        ConjunctEntry("jh", "ঝ"),
        ConjunctEntry("th", "থ"),
        ConjunctEntry("dh", "ধ"),
        ConjunctEntry("ph", "ফ"),
        ConjunctEntry("bh", "ভ"),
        ConjunctEntry("sh", "শ"),
        ConjunctEntry("ng", "ং"),
        ConjunctEntry("rh", "ড়"),
        ConjunctEntry("hr", "হ্র"),          // হ্রদ, হ্রাস

        // ====================================================================
        // 2 chars — conjunct pairs
        // ====================================================================
        ConjunctEntry("gy", "জ্ঞ"),          // জ্ঞান
        ConjunctEntry("jn", "জ্ঞ"),          // জ্ঞান (alternate)
        ConjunctEntry("pt", "প্ত"),          // সুপ্ত, লিপ্ত
        ConjunctEntry("kt", "ক্ত"),          // রক্ত, শক্তি
        ConjunctEntry("gn", "গ্ন"),          // ভগ্ন, অগ্নি
        ConjunctEntry("mn", "ম্ন"),          // নিম্ন
        ConjunctEntry("nt", "ন্ত"),          // অন্ত, শান্ত
        ConjunctEntry("nd", "ন্দ"),          // আনন্দ, সুন্দর
        ConjunctEntry("nj", "ঞ্জ"),          // কুঞ্জ, গঞ্জ
        ConjunctEntry("nc", "ঞ্চ"),          // অঞ্চল, পঞ্চ
        ConjunctEntry("mb", "ম্ব"),          // লম্বা, বিম্ব
        ConjunctEntry("mp", "ম্প"),          // কম্প, সম্পদ
        ConjunctEntry("lp", "ল্প"),          // বিকল্প
        ConjunctEntry("lb", "ল্ব"),          // বিল্ব
        ConjunctEntry("ld", "ল্ড"),          // ফিল্ডিং
        ConjunctEntry("lt", "ল্ট"),          // উল্টা, বোল্ট
        ConjunctEntry("lk", "ল্ক"),          // শুল্ক
        ConjunctEntry("lm", "ল্ম"),          // গুল্ম
        ConjunctEntry("lg", "ল্গ"),          // বল্গা
        ConjunctEntry("lf", "ল্ফ"),          // গল্ফ
        // ত-based conjuncts (dental t)
        ConjunctEntry("tn", "ত্ন"),          // যত্ন, রত্ন
        ConjunctEntry("tm", "ত্ম"),          // আত্মা
        ConjunctEntry("tb", "ত্ব"),          // রাজত্ব
        // দ-based conjuncts (dental d)
        ConjunctEntry("db", "দ্ব"),          // দ্বিতীয়, বিদ্বান
        ConjunctEntry("dg", "দ্গ"),          // উদ্গম
        ConjunctEntry("dm", "দ্ম"),          // পদ্ম
        // জ-based conjuncts
        ConjunctEntry("jb", "জ্ব"),          // জ্বর, জ্বালা
        // হ-based conjuncts
        ConjunctEntry("hm", "হ্ম"),          // ব্রাহ্মণ
        ConjunctEntry("hn", "হ্ন"),          // চিহ্ন
        ConjunctEntry("hl", "হ্ল"),          // আহ্লাদ
        ConjunctEntry("hb", "হ্ব"),          // আহ্বান
        // ন-based additional conjuncts
        ConjunctEntry("nm", "ন্ম"),          // জন্ম, উন্মাদ
        ConjunctEntry("ns", "ন্স"),          // ট্রান্স
        // গ-based conjuncts
        ConjunctEntry("gm", "গ্ম"),          // যুগ্ম
        ConjunctEntry("gl", "গ্ল"),          // গ্লানি, গ্লাস
        // ক-based conjuncts
        ConjunctEntry("kl", "ক্ল"),          // ক্লান্তি, ক্লাস
        ConjunctEntry("km", "ক্ম"),          // রুক্ম (rare)
        ConjunctEntry("kb", "ক্ব"),          // পক্ব (rare)
        // প-based conjuncts
        ConjunctEntry("pl", "প্ল"),          // প্লেন, আপ্লুত
        ConjunctEntry("pn", "প্ন"),          // স্বপ্ন
        ConjunctEntry("ps", "প্স"),          // লিপ্সা
        // ব-based conjuncts
        ConjunctEntry("bl", "ব্ল"),          // ব্লাউজ, ব্লক
        ConjunctEntry("bd", "ব্দ"),          // শব্দ, জব্দ
        // ম-based conjuncts
        ConjunctEntry("ml", "ম্ল"),          // অম্ল
        // ফ-based conjuncts (foreign words)
        ConjunctEntry("fl", "ফ্ল"),          // ফ্লোর, ফ্লেভার

        // ====================================================================
        // 2 chars — double consonants
        // ====================================================================
        ConjunctEntry("kk", "ক্ক"),
        ConjunctEntry("cc", "চ্চ"),
        ConjunctEntry("jj", "জ্জ"),
        ConjunctEntry("dd", "দ্দ"),
        ConjunctEntry("tt", "ত্ত"),
        ConjunctEntry("nn", "ন্ন"),
        ConjunctEntry("mm", "ম্ম"),
        ConjunctEntry("ll", "ল্ল"),
        ConjunctEntry("ss", "স্স"),
        ConjunctEntry("pp", "প্প"),
        ConjunctEntry("bb", "ব্ব"),
        ConjunctEntry("rr", "্র")           // ra-phola
    )

    /** Set of 2-char multi-consonant phonetics for quick digraph detection */
    val DIGRAPH_SET: Set<String> = TABLE
        .filter { it.phonetic.length == 2 }
        .map { it.phonetic }
        .toSet()
}
