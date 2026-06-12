package com.banglu.compiler

/**
 * Converts ARPABET (CMUdict) phoneme sequences to Bengali script, using the conventional
 * Bengali rendering of English loanwords (alveolar T/D -> retroflex, W -> ওয়/ু glide,
 * Y -> ইয়/ি glide, schwa AH0 -> position-dependent).
 *
 * All vowel and conjunct rules below were tuned against the 967-entry curated lexicon
 * (EnglishDirectData + EnglishPronunciationVariantData) as ground truth; each rule's
 * curated evidence is cited inline. Rules are phoneme-context rules, not per-word
 * special cases.
 *
 * Schwa (AH0):
 *   - After consonant, word-final            -> া        (camera ক্যামেরা, canada কানাডা)
 *   - After consonant, in final syllable     -> ে        (cancel ক্যানসেল, agent এজেন্ট, chicken চিকেন)
 *       except after SH/ZH (-tion/-sion)     -> inherent (action অ্যাকশন, precision প্রিসিশন)
 *       except before final M                -> া        (album অ্যালবাম, momentum মোমেন্টাম)
 *       except before final S                -> ি        (tennis টেনিস, service সার্ভিস, practice প্রাকটিস)
 *   - After consonant, elsewhere             -> inherent (computer কম্পিউটার)
 *   - Word-initial                           -> অ্যা     (account অ্যাকাউন্ট, alert অ্যালার্ট)
 *   - After a vowel                          -> য়ে/য়া   (science সায়েন্স / australia অস্ট্রেলিয়া)
 *
 * Conjunct (hasanta) policy: clusters join only in the word onset, in selected codas
 * (ন্ট, ক্স, প্ট, স্ট, ল্ট), as ra-phala (ক্র), after reph (র্ক), as প্ল, or before the
 * Y glide (ম্পিউ). Cross-syllable clusters stay unjoined (laptop ল্যাপটপ, magnet ম্যাগনেট).
 *
 * Velar nasal (NG) lookahead rules:
 *   - NG followed by a vowel phoneme -> "ঙ" (regular consonant; joins, takes kars)
 *   - NG followed by G -> "ঙ" (regular consonant; next G will join via hasanta)
 *   - NG otherwise (word-final or before a non-G consonant) -> "ং" (anusvara; does NOT
 *     set prevWasConsonant=true so it never triggers hasanta on the next consonant)
 */
object ArpabetToBengali {

    // Regex for strict token parsing: one or two uppercase letters, optional stress digit 0-2
    private val TOKEN_RE = Regex("""^([A-Z]{1,2})([0-2])?$""")

    // Consonants: ARPABET phoneme -> Bengali glyph
    private val CONSONANTS = mapOf(
        "B"  to "ব",  "CH" to "চ",  "D"  to "ড",  "DH" to "দ",  "F" to "ফ",
        "G"  to "গ",  "HH" to "হ",  "JH" to "জ",  "K"  to "ক",  "L" to "ল",
        "M"  to "ম",  "N"  to "ন",  "P"  to "প",  "R"  to "র",
        "S"  to "স",  "SH" to "শ",  "T"  to "ট",  "TH" to "থ",  "V" to "ভ",
        "Z"  to "জ",  "ZH" to "শ"
        // NG is handled separately via lookahead (see below)
        // ZH word-final overrides to জ (garage গ্যারেজ); Z in a final cluster to স (jeans জিন্স)
    )

    // Vowels: independent form (word-initial / after vowel) / kar form (after consonant)
    private data class V(val independent: String, val kar: String)

    private val VOWELS = mapOf(
        "AA" to V("অ",    ""   ),   // contextual; handled specially (see convert)
        "AE" to V("অ্যা", "্যা"),
        "AH" to V("আ",    "া"  ),   // stressed AH1/AH2; AH0 handled separately
        "AO" to V("অ",    ""   ),   // contextual; handled specially (see convert)
        "AW" to V("আউ",   "াউ" ),
        "AY" to V("আই",   "াই" ),
        "EH" to V("এ",    "ে"  ),
        "ER" to V("আর",   "ার" ),   // ER always maps to ার/আর regardless of stress
        "EY" to V("এ",    "ে"  ),
        "IH" to V("ই",    "ি"  ),
        "IY" to V("ই",    "ি"  ),
        "OW" to V("ও",    "ো"  ),
        "OY" to V("অয়",  "য়" ),
        "UH" to V("উ",    "ু"  ),
        "UW" to V("উ",    "ু"  )
    )

    private const val HASANTA = "্"
    private const val ZWJ = "‍"

    /**
     * Converts a list of uppercase ARPABET phoneme tokens (with optional stress digit 0–2
     * on vowels) to a Bengali script string.
     *
     * Returns null if:
     * - Any token does not match the strict format `[A-Z]{1,2}[0-2]?`
     * - Any phoneme name is not found in the consonant or vowel tables (after special-case
     *   handling of Y, W, NG, AH0)
     * - The resulting Bengali output would be empty
     *
     * Callers should skip words and count failures when null is returned.
     */
    fun convert(phonemes: List<String>): String? {
        if (phonemes.isEmpty()) return null

        // Parse and validate all tokens upfront into (phoneme, stress?) pairs
        data class Token(val phoneme: String, val stress: Int?)
        val tokens = mutableListOf<Token>()
        for (raw in phonemes) {
            val m = TOKEN_RE.matchEntire(raw) ?: return null
            val phoneme = m.groupValues[1]
            val stressStr = m.groupValues[2]
            val stress = if (stressStr.isEmpty()) null else stressStr.toInt()
            // Validate: phoneme must be in CONSONANTS, VOWELS, or be one of the special glides/NG
            val knownPhoneme = phoneme == "Y" || phoneme == "W" || phoneme == "NG" ||
                    phoneme in CONSONANTS || phoneme in VOWELS
            if (!knownPhoneme) return null
            tokens.add(Token(phoneme, stress))
        }

        val out = StringBuilder()
        var prevWasConsonant = false
        var prevPhoneme: String? = null
        var prevStress: Int? = null
        var prevPrevPhoneme: String? = null
        var vowelEmitted = false   // true once any vowel nucleus has been produced
        var seenPrimaryStress = false // true once the primary-stressed vowel has been emitted
        var lastJoined = false     // true if the last emitted consonant was conjunct-joined
        var skipNext = false

        fun isVowelish(q: String?) = q != null && (q in VOWELS || q == "Y" || q == "W")

        // True when every phoneme after index i is consonant-like (no vowels, no glides),
        // i.e. index i sits in the word's final syllable nucleus.
        fun restAreConsonants(i: Int): Boolean {
            for (j in i + 1 until tokens.size) {
                if (isVowelish(tokens[j].phoneme)) return false
            }
            return true
        }

        for (i in tokens.indices) {
            if (skipNext) {
                skipNext = false
                continue
            }
            val (p, stress) = tokens[i]
            val next = tokens.getOrNull(i + 1)
            val nextPhoneme = next?.phoneme
            val prev = prevPhoneme
            val prevWasStressed = prevStress != 0
            val prev2 = prevPrevPhoneme
            prevPrevPhoneme = prev
            prevPhoneme = p
            prevStress = stress
            val postTonic = seenPrimaryStress
            if (stress == 1) seenPrimaryStress = true

            // ------------------------------------------------------------------
            // Glide: Y
            // ------------------------------------------------------------------
            if (p == "Y") {
                if (!prevWasConsonant && !vowelEmitted && nextPhoneme == "UW") {
                    // Word-initial Y UW -> ইউ (youtube ইউটিউব, university ইউনিভার্সিটি, usb ইউএসবি)
                    out.append("ইউ")
                    skipNext = true
                    prevPhoneme = "UW"
                    prevWasConsonant = false
                    vowelEmitted = true
                } else if (prevWasConsonant) {
                    if (nextPhoneme == "AH" && next?.stress == 0 && i + 1 < tokens.size - 1) {
                        // C + Y + schwa -> ু (document ডকুমেন্ট, formula ফর্মুলা, ambulance অ্যাম্বুলেন্স)
                        // (not when the schwa is word-final: australia অস্ট্রেলিয়া)
                        out.append("ু")
                        skipNext = true
                        prevPhoneme = "AH"
                    } else {
                        // After a consonant: Y acts as ি matra (e.g. P Y UW -> পিউ)
                        out.append("ি")
                    }
                    prevWasConsonant = false
                    vowelEmitted = true
                } else {
                    // Word-initial or after vowel: ইয় acts as a consonant cluster
                    out.append("ইয়")
                    prevWasConsonant = true
                    lastJoined = false
                }
                continue
            }

            // ------------------------------------------------------------------
            // Glide: W
            // ------------------------------------------------------------------
            if (p == "W") {
                if (prevWasConsonant) {
                    if (!vowelEmitted && nextPhoneme == "IH") {
                        // Onset cluster before IH: W -> ু matra (quick কুইক, switch সুইচ)
                        out.append("ু")
                        prevWasConsonant = false
                        vowelEmitted = true
                    } else if (prev == "K" || prev == "G") {
                        // qu- -> কোয়া/কোয়ে (quantum কোয়ান্টাম, request রিকোয়েস্ট)
                        out.append("োয়")
                        prevWasConsonant = true
                        lastJoined = false
                    } else if (!vowelEmitted) {
                        out.append("ু")
                        prevWasConsonant = false
                        vowelEmitted = true
                    } else {
                        // Compound-internal W: network নেটওয়ার্ক, password পাসওয়ার্ড
                        out.append("ওয়")
                        prevWasConsonant = true
                        lastJoined = false
                    }
                } else {
                    // Word-initial or after vowel: ওয় acts as a consonant cluster
                    out.append("ওয়")
                    prevWasConsonant = true
                    lastJoined = false
                }
                continue
            }

            // ------------------------------------------------------------------
            // Velar nasal: NG — requires one-phoneme lookahead
            // ------------------------------------------------------------------
            if (p == "NG") {
                val nextIsVowel = nextPhoneme != null && nextPhoneme in VOWELS
                val nextIsG = nextPhoneme == "G"
                if (nextIsVowel || nextIsG) {
                    // NG before a vowel or G: emit ঙ as a regular consonant
                    if (prevWasConsonant) out.append(HASANTA)
                    out.append("ঙ")
                    lastJoined = prevWasConsonant
                    prevWasConsonant = true
                } else {
                    // Word-final or before a non-G consonant: emit ং (anusvara)
                    // NEVER preceded by hasanta; does NOT set prevWasConsonant = true
                    out.append("ং")
                    prevWasConsonant = false
                    lastJoined = false
                }
                continue
            }

            // ------------------------------------------------------------------
            // Regular consonants — conjunct policy (curated-benchmark driven)
            // ------------------------------------------------------------------
            val consonant = CONSONANTS[p]
            if (consonant != null) {
                val glyph = when {
                    // ZH word-final -> জ (garage গ্যারেজ); elsewhere শ (television টেলিভিশন)
                    p == "ZH" && i == tokens.size - 1 -> "জ"
                    // Z in a word-final cluster after a consonant -> স (jeans জিন্স, details ডিটেইলস)
                    p == "Z" && prev in CONSONANTS && restAreConsonants(i - 1) -> "স"
                    // N before CH assimilates to ঞ (branch ব্রাঞ্চ, launch লঞ্চ)
                    p == "N" && nextPhoneme == "CH" -> "ঞ"
                    else -> consonant
                }
                val join = prevWasConsonant && run {
                    val coda = restAreConsonants(i - 1)
                    when {
                        !vowelEmitted -> true                      // onset cluster: স্ক্রিন, প্রিন্টার
                        p == "R" -> true                           // ra-phala: মাইক্রোফোন, এনক্রিপশন
                        prev == "R" -> true                        // reph: মার্কেট, পোর্ট
                        // Stressed ER conjuncts (বার্গার, সার্ভিস, সার্কিট); unstressed ER0
                        // stays open (পারসেন্ট, অবজারভেশন, ইন্টারনেট)
                        prev == "ER" && prevWasStressed -> true
                        prev == "P" && p == "L" -> true            // প্ল: কমপ্লিট, ডিসপ্লে, এমপ্লয়ি
                        prev == "NG" && p == "G" -> true           // ঙ্গ: ইঙ্গলিশ
                        nextPhoneme == "Y" -> true                 // cluster before ি glide: কম্পিউটার
                        // Homorganic nasal clusters join even mid-word:
                        prev == "N" && (p == "T" || p == "D") -> true   // অ্যান্টেনা, ইন্টারনেট, ব্লেন্ডার
                        prev == "N" && p == "CH" -> true                // ব্রাঞ্চ (ঞ্চ); N+S stays open: ক্যানসেল
                        prev == "M" && p == "P" && prev2 == "AH" &&
                            isVowelish(nextPhoneme) -> true             // কম্পাইলার, কম্পোনেন্ট (but ইমপসিবল)
                        prev == "S" && (p == "T" || p == "K") && !lastJoined -> true // স্ট/স্ক: ফাস্ট, ডেস্ক (but নেক্সট)
                        // Cluster directly before a word-final -er/-or syllable: চ্যাপ্টার, ফিল্টার, অ্যাক্টর
                        nextPhoneme == "ER" && i + 1 == tokens.size - 1 &&
                            (p == "T" || p == "D" || p == "S") -> true
                        coda && !lastJoined &&
                            (prev == "N" || prev == "K" || prev == "P") -> true // ন্ট, ক্স, প্ট codas
                        coda && prev == "L" && p == "T" -> true    // ল্ট: রেজাল্ট, ডিফিকাল্ট
                        else -> false                              // cross-syllable: ল্যাপটপ, ম্যাগনেট
                    }
                }
                if (join) out.append(HASANTA)
                out.append(glyph)
                lastJoined = join
                prevWasConsonant = true
                continue
            }

            // ------------------------------------------------------------------
            // Vowels
            // ------------------------------------------------------------------

            // Special case: AH0 (unstressed schwa) — see class KDoc for the rule table.
            if (p == "AH" && stress == 0) {
                if (prevWasConsonant) {
                    when {
                        i == tokens.size - 1 -> out.append("া")
                        restAreConsonants(i) -> when {
                            prev == "SH" || prev == "ZH" -> { /* -tion/-sion: inherent */ }
                            nextPhoneme == "M" -> out.append("া")   // album অ্যালবাম, momentum মোমেন্টাম
                            nextPhoneme == "S" -> out.append("ি")   // tennis টেনিস, service সার্ভিস
                            else -> out.append("ে")                  // cancel ক্যানসেল, agent এজেন্ট
                        }
                        prev == "W" -> out.append("ে")               // frequency ফ্রিকোয়েন্সি
                        prev == "AY" -> out.append("া")              // diabetes ডায়াবেটিস, diagnosis ডায়াগনোসিস
                        postTonic -> out.append("ি")                 // monitor মনিটর, oxygen অক্সিজেন
                        else -> { /* pre-tonic: inherent (computer কম্পিউটার, command কমান্ড) */ }
                    }
                    prevWasConsonant = false
                } else if (out.isEmpty()) {
                    // Word-initial: Bengali convention writes initial schwa as অ্যা
                    out.append("অ্যা")
                    prevWasConsonant = false
                } else {
                    // Post-vowel schwa: glide in with য় — য়ে before a final coda
                    // (science সায়েন্স, gradient গ্রেডিয়েন্ট), য়া otherwise / word-final
                    // (australia অস্ট্রেলিয়া, algeria আলজেরিয়া)
                    out.append(if (i < tokens.size - 1 && restAreConsonants(i)) "য়ে" else "য়া")
                    prevWasConsonant = false
                }
                vowelEmitted = true
                continue
            }

            // AA — Bengali borrowing convention:
            //   - After W                     -> া         (watch ওয়াচ, quantum কোয়ান্টাম)
            //   - Before R                    -> া/আ      (car কার, market মার্কেট)
            //   - After consonant, word-final -> া         (spa স্পা)
            //   - After consonant, otherwise  -> inherent  (block ব্লক, college কলেজ, coffee কফি)
            //   - Word-initial                -> অ         (audio অডিও, oxygen অক্সিজেন)
            //   - After a vowel               -> ও         (geometry জিওমেট্রি)
            if (p == "AA") {
                when {
                    prevWasConsonant && prev == "W" -> out.append("া")
                    nextPhoneme == "R" -> out.append(if (prevWasConsonant) "া" else "আ")
                    prevWasConsonant -> if (i == tokens.size - 1) out.append("া") // else inherent
                    vowelEmitted -> out.append("ও")
                    else -> out.append("অ")
                }
                prevWasConsonant = false
                vowelEmitted = true
                continue
            }

            // AO — Bengali borrowing convention:
            //   - After W                       -> া        (water ওয়াটার, warning ওয়ার্নিং)
            //   - Word-initial / post-vowel     -> অ        (order অর্ডার, Australia অস্ট্রেলিয়া)
            //   - Before R after B/P            -> ো        (board বোর্ড, port পোর্ট)
            //   - After consonant, otherwise    -> inherent (call কল, blog ব্লগ, fork ফর্ক, format ফরম্যাট)
            if (p == "AO") {
                when {
                    prevWasConsonant && prev == "W" -> out.append("া")
                    !prevWasConsonant -> out.append("অ")
                    nextPhoneme == "R" && (prev == "B" || prev == "P") -> out.append("ো")
                    else -> { /* inherent: emit nothing */ }
                }
                prevWasConsonant = false
                vowelEmitted = true
                continue
            }

            // EH/IH + R + (consonant or word end) — Bengali writes the r-coloured vowel with
            // a glide: chair চেয়ার, airport এয়ারপোর্ট, gear গিয়ার, earphone ইয়ারফোন.
            // (Before a vowel the plain forms stay: america আমেরিকা, algeria আলজেরিয়া.)
            if ((p == "EH" || p == "IH") && nextPhoneme == "R") {
                val nextNext = tokens.getOrNull(i + 2)?.phoneme
                if (nextNext == null || !isVowelish(nextNext)) {
                    // Emit the unit and consume the R; the র stays open
                    // (no conjunct with a following consonant: এয়ারপোর্ট, not এয়ার্পোর্ট).
                    out.append(
                        when {
                            p == "EH" -> if (prevWasConsonant) "েয়ার" else "এয়ার"
                            else -> if (prevWasConsonant) "িয়ার" else "ইয়ার"
                        }
                    )
                    prevWasConsonant = false
                    prevPhoneme = "R"
                    skipNext = true
                    vowelEmitted = true
                    continue
                }
            }

            // EY + L -> েই (detail ডিটেইল, email ইমেইল, failed ফেইলড); plain ে elsewhere (train ট্রেন)
            if (p == "EY" && nextPhoneme == "L") {
                out.append(if (prevWasConsonant) "েই" else "এই")
                prevWasConsonant = false
                vowelEmitted = true
                continue
            }

            // AY — before ER the ই is absorbed by the glide (tire টায়ার, amplifier ফায়ার);
            // before another vowel it closes with য় (science সায়েন্স, diode ডায়োড)
            if (p == "AY") {
                when {
                    nextPhoneme == "ER" -> {
                        out.append(if (prevWasConsonant) "া" else "আ")
                        prevWasConsonant = false
                    }
                    nextPhoneme != null && nextPhoneme in VOWELS -> {
                        out.append(if (prevWasConsonant) "ায়" else "আয়")
                        prevWasConsonant = true
                        lastJoined = false
                    }
                    else -> {
                        out.append(if (prevWasConsonant) "াই" else "আই")
                        prevWasConsonant = false
                    }
                }
                vowelEmitted = true
                continue
            }

            // AW before a vowel closes with ও (towel টাওয়েল, power পাওয়ার)
            if (p == "AW" && nextPhoneme != null && nextPhoneme in VOWELS) {
                out.append(if (prevWasConsonant) "াও" else "আও")
                prevWasConsonant = false
                vowelEmitted = true
                continue
            }

            // OY — ends in য় (a consonant base): a final coda inserts ে (voice ভয়েস,
            // join জয়েন, endpoint এন্ডপয়েন্ট); a following vowel attaches as kar
            // (employee এমপ্লয়ি)
            if (p == "OY") {
                out.append(if (prevWasConsonant) "য়" else "অয়")
                if (nextPhoneme != null && restAreConsonants(i)) {
                    out.append("ে")
                    prevWasConsonant = false
                } else {
                    prevWasConsonant = true
                    lastJoined = false
                }
                vowelEmitted = true
                continue
            }

            // AE — joined R/L onset clusters take া (class ক্লাস, tram ট্রাম, program প্রোগ্রাম);
            // before S also া (fast ফাস্ট, password পাসওয়ার্ড); word-initial bare র needs
            // ZWJ to render র‍্যা (ram র‍্যাম); plain ্যা elsewhere (cat ক্যাট, bank ব্যাংক)
            if (p == "AE") {
                if (prevWasConsonant) {
                    out.append(
                        when {
                            lastJoined && (prev == "R" || prev == "L") -> "া"
                            nextPhoneme == "S" -> "া"
                            prev == "R" -> "$ZWJ্যা"
                            else -> "্যা"
                        }
                    )
                } else {
                    out.append("অ্যা")
                }
                prevWasConsonant = false
                vowelEmitted = true
                continue
            }

            // ER ends in the consonant র, so it leaves the syllable in consonant state:
            // a following vowel attaches as kar (battery ব্যাটারি) and a following
            // consonant conjuncts as reph (burger বার্গার, circuit সার্কিট).
            // After a vowel it glides in with য় (player প্লেয়ার, tire টায়ার).
            if (p == "ER") {
                out.append(
                    when {
                        prevWasConsonant -> "ার"
                        vowelEmitted -> "য়ার"
                        else -> "আর"
                    }
                )
                prevWasConsonant = true
                lastJoined = false
                vowelEmitted = true
                continue
            }

            val vowel = VOWELS[p] ?: return null
            out.append(if (prevWasConsonant) vowel.kar else vowel.independent)
            prevWasConsonant = false
            vowelEmitted = true
        }

        return out.toString().takeIf { it.isNotEmpty() }
    }
}
