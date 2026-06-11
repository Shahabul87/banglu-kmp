package com.banglu.compiler

/**
 * ARPABET (CMUdict) phonemes -> Bengali script, using the conventional
 * Bengali rendering of English loanwords (alveolar T/D -> retroflex,
 * W -> ওয়/ু glide, Y -> ইয়/ি glide, schwa AH0 -> inherent/empty).
 *
 * Stress handling:
 *   - AH0 (unstressed schwa) after a consonant -> inherent vowel (emit nothing)
 *   - AH1/AH2 (stressed schwa) -> "া" kar / "আ" independent
 *
 * Positional glide rules:
 *   - Y after consonant -> "ি" (u-matra style); otherwise -> "ইয়" (consonant-like, prevWasConsonant=true)
 *   - W after consonant -> "ু" (vowel-like, prevWasConsonant=false); otherwise -> "ওয়" (consonant-like, prevWasConsonant=true)
 */
object ArpabetToBengali {

    // Consonants: ARPABET phoneme -> Bengali glyph
    private val CONSONANTS = mapOf(
        "B"  to "ব",  "CH" to "চ",  "D"  to "ড",  "DH" to "দ",  "F" to "ফ",
        "G"  to "গ",  "HH" to "হ",  "JH" to "জ",  "K"  to "ক",  "L" to "ল",
        "M"  to "ম",  "N"  to "ন",  "NG" to "ং",  "P"  to "প",  "R" to "র",
        "S"  to "স",  "SH" to "শ",  "T"  to "ট",  "TH" to "থ",  "V" to "ভ",
        "Z"  to "জ",  "ZH" to "জ"
    )

    // Vowels: independent form (word-initial / after vowel) / kar form (after consonant)
    private data class V(val independent: String, val kar: String)

    private val VOWELS = mapOf(
        "AA" to V("আ",    "া"  ),
        "AE" to V("অ্যা", "্যা"),
        "AH" to V("আ",    "া"  ),   // stressed AH1/AH2; AH0 handled separately (inherent)
        "AO" to V("আ",    "া"  ),   // Bengali loanword convention: "water" AO1 -> া
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

    fun convert(phonemes: List<String>): String {
        val out = StringBuilder()
        var prevWasConsonant = false

        for (raw in phonemes) {
            // Parse stress digit (0, 1, 2) from vowel tokens
            val stress = raw.lastOrNull()?.digitToIntOrNull()   // null if no digit
            val p = raw.trimEnd('0', '1', '2')                  // bare phoneme name

            // ------------------------------------------------------------------
            // Glide: Y
            // ------------------------------------------------------------------
            if (p == "Y") {
                if (prevWasConsonant) {
                    // After a consonant: Y acts as ি matra (e.g. P Y UW -> পিউ)
                    out.append("ি")
                    prevWasConsonant = false
                } else {
                    // Word-initial or after vowel: ইয় acts as a consonant cluster
                    out.append("ইয়")
                    prevWasConsonant = true
                }
                continue
            }

            // ------------------------------------------------------------------
            // Glide: W
            // ------------------------------------------------------------------
            if (p == "W") {
                if (prevWasConsonant) {
                    // After a consonant: W -> ু matra (e.g. K W IH -> কুই)
                    out.append("ু")
                    prevWasConsonant = false
                } else {
                    // Word-initial or after vowel: ওয় acts as a consonant cluster
                    out.append("ওয়")
                    prevWasConsonant = true
                }
                continue
            }

            // ------------------------------------------------------------------
            // Regular consonants
            // ------------------------------------------------------------------
            val consonant = CONSONANTS[p]
            if (consonant != null) {
                if (prevWasConsonant) out.append(HASANTA)
                out.append(consonant)
                prevWasConsonant = true
                continue
            }

            // ------------------------------------------------------------------
            // Vowels
            // ------------------------------------------------------------------

            // Special case: AH0 (unstressed schwa) after a consonant -> inherent vowel
            // (emit nothing extra; the consonant already carries the inherent vowel)
            if (p == "AH" && stress == 0) {
                prevWasConsonant = false
                continue
            }

            val vowel = VOWELS[p] ?: continue
            out.append(if (prevWasConsonant) vowel.kar else vowel.independent)
            prevWasConsonant = false
        }

        return out.toString()
    }
}
