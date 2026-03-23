import Foundation

/// Centralized Bengali multi-character consonant patterns.
/// Ported from KMP ConjunctTable.kt — all patterns are lowercase, matched longest-first.
public enum ConjunctTable {

    public struct Entry: Sendable {
        public let phonetic: String
        public let bengali: String

        public init(phonetic: String, bengali: String) {
            self.phonetic = phonetic
            self.bengali = bengali
        }
    }

    /// Multi-character consonant patterns sorted longest-first for greedy matching.
    public static let entries: [Entry] = [
        // ====================================================================
        // 5+ chars -- long multi-consonant clusters
        // ====================================================================
        Entry(phonetic: "kkhm", bengali: "ক্ষ্ম"),     // সূক্ষ্ম, লক্ষ্মী
        Entry(phonetic: "shtr", bengali: "ষ্ট্র"),     // রাষ্ট্র, রাষ্ট্রীয়

        // ====================================================================
        // 4 chars -- doubled aspirate conjuncts, 3-consonant clusters
        // ====================================================================
        Entry(phonetic: "chch", bengali: "চ্ছ"),       // দিচ্ছে
        Entry(phonetic: "jhjh", bengali: "ঝ্ঝ"),       // rare
        Entry(phonetic: "ngkh", bengali: "ঙ্খ"),       // শঙ্খ
        Entry(phonetic: "nggh", bengali: "ঙ্ঘ"),       // সঙ্ঘ

        // ====================================================================
        // 3 chars -- aspirated combos, nasal groups, clusters
        // ====================================================================
        Entry(phonetic: "chh", bengali: "ছ"),           // chh = ছ (aspirated ch)
        Entry(phonetic: "cch", bengali: "চ্ছ"),         // আচ্ছা (alternate spelling)
        Entry(phonetic: "kkh", bengali: "ক্ষ"),
        Entry(phonetic: "ksh", bengali: "ক্ষ"),
        Entry(phonetic: "shr", bengali: "শ্র"),         // শ্রাবণ
        Entry(phonetic: "str", bengali: "স্ত্র"),       // স্ত্রী, অস্ত্র
        // Ya-phala after aspirated consonants
        Entry(phonetic: "khy", bengali: "খ্য"),         // সংখ্যা
        Entry(phonetic: "ghy", bengali: "ঘ্য"),         // rare
        Entry(phonetic: "thy", bengali: "থ্য"),         // rare
        Entry(phonetic: "dhy", bengali: "ধ্য"),         // সন্ধ্যা
        Entry(phonetic: "phy", bengali: "ফ্য"),         // rare
        Entry(phonetic: "bhy", bengali: "ভ্য"),         // rare
        // R-phala after aspirated consonants
        Entry(phonetic: "dhr", bengali: "ধ্র"),         // ধ্রুব
        Entry(phonetic: "bhr", bengali: "ভ্র"),         // ভ্র মণ
        Entry(phonetic: "ghr", bengali: "ঘ্র"),         // ঘ্রাণ
        Entry(phonetic: "khr", bengali: "খ্র"),         // খ্রিস্টান
        Entry(phonetic: "ttr", bengali: "ট্র"),         // ট্রেন, ট্রাক
        // Special patterns
        Entry(phonetic: "rri", bengali: "ৃ"),           // context-handled: ঋ when independent
        Entry(phonetic: "ngo", bengali: "ঙ্গ"),
        Entry(phonetic: "nga", bengali: "ঙ্গা"),
        Entry(phonetic: "ngk", bengali: "ঙ্ক"),         // অঙ্ক, লঙ্কা, শঙ্কা
        Entry(phonetic: "ngm", bengali: "ঙ্ম"),         // বাঙ্ময়
        Entry(phonetic: "njh", bengali: "ঞ্ঝ"),         // ঝঞ্ঝা
        // 3-char conjuncts -- aspirate+consonant combos
        Entry(phonetic: "ddh", bengali: "দ্ধ"),         // শুদ্ধ, বুদ্ধি
        Entry(phonetic: "dbh", bengali: "দ্ভ"),         // অদ্ভুত, উদ্ভব
        Entry(phonetic: "dgh", bengali: "দ্ঘ"),         // উদ্ঘাটন
        Entry(phonetic: "mbh", bengali: "ম্ভ"),         // দম্ভ, সম্ভব
        Entry(phonetic: "lbh", bengali: "ল্ভ"),         // প্রগল্ভ
        Entry(phonetic: "nth", bengali: "ন্থ"),         // গ্রন্থ, পান্থ
        Entry(phonetic: "tth", bengali: "ত্থ"),         // উত্থান
        Entry(phonetic: "jjb", bengali: "জ্জ্ব"),       // উজ্জ্বল
        Entry(phonetic: "ntr", bengali: "ন্ত্র"),       // মন্ত্র, যন্ত্র
        Entry(phonetic: "ndr", bengali: "ন্দ্র"),       // কেন্দ্র, চন্দ্র
        Entry(phonetic: "bdh", bengali: "ব্ধ"),         // লব্ধ
        Entry(phonetic: "gdh", bengali: "গ্ধ"),         // মুগ্ধ
        Entry(phonetic: "ndh", bengali: "ন্ধ"),         // অন্ধ, বন্ধু

        // ====================================================================
        // 2 chars -- aspirated consonants
        // ====================================================================
        Entry(phonetic: "kh", bengali: "খ"),
        Entry(phonetic: "gh", bengali: "ঘ"),
        Entry(phonetic: "ch", bengali: "চ"),
        Entry(phonetic: "jh", bengali: "ঝ"),
        Entry(phonetic: "th", bengali: "থ"),
        Entry(phonetic: "dh", bengali: "ধ"),
        Entry(phonetic: "ph", bengali: "ফ"),
        Entry(phonetic: "bh", bengali: "ভ"),
        Entry(phonetic: "sh", bengali: "শ"),
        Entry(phonetic: "ng", bengali: "ং"),
        Entry(phonetic: "rh", bengali: "ড়"),
        Entry(phonetic: "hr", bengali: "হ্র"),

        // ====================================================================
        // 2 chars -- conjunct pairs
        // ====================================================================
        Entry(phonetic: "gy", bengali: "জ্ঞ"),          // জ্ঞান
        Entry(phonetic: "jn", bengali: "জ্ঞ"),          // জ্ঞান (alternate)
        Entry(phonetic: "pt", bengali: "প্ত"),           // সুপ্ত, লিপ্ত
        Entry(phonetic: "kt", bengali: "ক্ত"),           // রক্ত, শক্তি
        Entry(phonetic: "gn", bengali: "গ্ন"),           // ভগ্ন, অগ্নি
        Entry(phonetic: "mn", bengali: "ম্ন"),           // নিম্ন
        Entry(phonetic: "nt", bengali: "ন্ত"),           // অন্ত, শান্ত
        Entry(phonetic: "nd", bengali: "ন্দ"),           // আনন্দ, সুন্দর
        Entry(phonetic: "nj", bengali: "ঞ্জ"),           // কুঞ্জ, গঞ্জ
        Entry(phonetic: "nc", bengali: "ঞ্চ"),           // অঞ্চল, পঞ্চ
        Entry(phonetic: "mb", bengali: "ম্ব"),           // লম্বা, বিম্ব
        Entry(phonetic: "mp", bengali: "ম্প"),           // কম্প, সম্পদ
        Entry(phonetic: "lp", bengali: "ল্প"),           // বিকল্প
        Entry(phonetic: "lb", bengali: "ল্ব"),           // বিল্ব
        Entry(phonetic: "ld", bengali: "ল্ড"),           // ফিল্ডিং
        Entry(phonetic: "lt", bengali: "ল্ট"),           // উল্টা, বোল্ট
        Entry(phonetic: "lk", bengali: "ল্ক"),           // শুল্ক
        Entry(phonetic: "lm", bengali: "ল্ম"),           // গুল্ম
        Entry(phonetic: "lg", bengali: "ল্গ"),           // বল্গা
        Entry(phonetic: "lf", bengali: "ল্ফ"),           // গল্ফ
        // ত-based conjuncts (dental t)
        Entry(phonetic: "tn", bengali: "ত্ন"),           // যত্ন, রত্ন
        Entry(phonetic: "tm", bengali: "ত্ম"),           // আত্মা
        Entry(phonetic: "tb", bengali: "ত্ব"),           // রাজত্ব
        // দ-based conjuncts (dental d)
        Entry(phonetic: "db", bengali: "দ্ব"),           // দ্বিতীয়, বিদ্বান
        Entry(phonetic: "dg", bengali: "দ্গ"),           // উদ্গম
        Entry(phonetic: "dm", bengali: "দ্ম"),           // পদ্ম
        // জ-based conjuncts
        Entry(phonetic: "jb", bengali: "জ্ব"),           // জ্বর, জ্বালা
        // হ-based conjuncts
        Entry(phonetic: "hm", bengali: "হ্ম"),           // ব্রাহ্মণ
        Entry(phonetic: "hn", bengali: "হ্ন"),           // চিহ্ন
        Entry(phonetic: "hl", bengali: "হ্ল"),           // আহ্লাদ
        Entry(phonetic: "hb", bengali: "হ্ব"),           // আহ্বান
        // ন-based additional conjuncts
        Entry(phonetic: "nm", bengali: "ন্ম"),           // জন্ম, উন্মাদ
        Entry(phonetic: "ns", bengali: "ন্স"),           // ট্রান্স
        // গ-based conjuncts
        Entry(phonetic: "gm", bengali: "গ্ম"),           // যুগ্ম
        Entry(phonetic: "gl", bengali: "গ্ল"),           // গ্লানি, গ্লাস
        // ক-based conjuncts
        Entry(phonetic: "kl", bengali: "ক্ল"),           // ক্লান্তি, ক্লাস
        Entry(phonetic: "km", bengali: "ক্ম"),           // রুক্ম (rare)
        Entry(phonetic: "kb", bengali: "ক্ব"),           // পক্ব (rare)
        // প-based conjuncts
        Entry(phonetic: "pl", bengali: "প্ল"),           // প্লেন, আপ্লুত
        Entry(phonetic: "pn", bengali: "প্ন"),           // স্বপ্ন
        Entry(phonetic: "ps", bengali: "প্স"),           // লিপ্সা
        // ব-based conjuncts
        Entry(phonetic: "bl", bengali: "ব্ল"),           // ব্লাউজ, ব্লক
        Entry(phonetic: "bd", bengali: "ব্দ"),           // শব্দ, জব্দ
        // ম-based conjuncts
        Entry(phonetic: "ml", bengali: "ম্ল"),           // অম্ল
        // ফ-based conjuncts (foreign words)
        Entry(phonetic: "fl", bengali: "ফ্ল"),           // ফ্লোর, ফ্লেভার

        // ====================================================================
        // 2 chars -- double consonants
        // ====================================================================
        Entry(phonetic: "kk", bengali: "ক্ক"),
        Entry(phonetic: "cc", bengali: "চ্চ"),
        Entry(phonetic: "jj", bengali: "জ্জ"),
        Entry(phonetic: "dd", bengali: "দ্দ"),
        Entry(phonetic: "tt", bengali: "ত্ত"),
        Entry(phonetic: "nn", bengali: "ন্ন"),
        Entry(phonetic: "mm", bengali: "ম্ম"),
        Entry(phonetic: "ll", bengali: "ল্ল"),
        Entry(phonetic: "ss", bengali: "স্স"),
        Entry(phonetic: "pp", bengali: "প্প"),
        Entry(phonetic: "bb", bengali: "ব্ব"),
        Entry(phonetic: "rr", bengali: "্র"),            // ra-phola
    ]

    /// Set of 2-char digraphs for quick detection
    public static let digraphSet: Set<String> = Set(
        entries.filter { $0.phonetic.count == 2 }.map { $0.phonetic }
    )
}
