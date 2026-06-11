package com.banglu.compiler

/**
 * Converts ARPABET (CMUdict) phoneme sequences to Bengali script, using the conventional
 * Bengali rendering of English loanwords (alveolar T/D -> retroflex, W -> ওয়/ু glide,
 * Y -> ইয়/ি glide, schwa AH0 -> inherent vowel when after a consonant).
 *
 * Stress handling:
 *   - AH0 (unstressed schwa) after a consonant -> inherent vowel (emit nothing)
 *   - AH0 word-initial or after a vowel -> "আ" independent
 *   - AH1/AH2 (stressed) -> "া" kar / "আ" independent
 *
 * Positional glide rules:
 *   - Y after consonant -> "ি" (matra); otherwise -> "ইয়" (consonant-like)
 *   - W after consonant -> "ু" (matra); otherwise -> "ওয়" (consonant-like)
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
        "Z"  to "জ",  "ZH" to "জ"
        // NG is handled separately via lookahead (see below)
    )

    // Vowels: independent form (word-initial / after vowel) / kar form (after consonant)
    private data class V(val independent: String, val kar: String)

    private val VOWELS = mapOf(
        "AA" to V("আ",    "া"  ),
        "AE" to V("অ্যা", "্যা"),
        "AH" to V("আ",    "া"  ),   // stressed AH1/AH2; AH0 handled separately (inherent or independent)
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

        for (i in tokens.indices) {
            val (p, stress) = tokens[i]
            val nextPhoneme = tokens.getOrNull(i + 1)?.phoneme

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
            // Velar nasal: NG — requires one-phoneme lookahead
            // ------------------------------------------------------------------
            if (p == "NG") {
                val nextIsVowel = nextPhoneme != null && nextPhoneme in VOWELS
                val nextIsG = nextPhoneme == "G"
                if (nextIsVowel || nextIsG) {
                    // NG before a vowel or G: emit ঙ as a regular consonant
                    if (prevWasConsonant) out.append(HASANTA)
                    out.append("ঙ")
                    prevWasConsonant = true
                } else {
                    // Word-final or before a non-G consonant: emit ং (anusvara)
                    // NEVER preceded by hasanta; does NOT set prevWasConsonant = true
                    out.append("ং")
                    prevWasConsonant = false
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

            // Special case: AH0 (unstressed schwa)
            //   - After a consonant -> inherent vowel (emit nothing)
            //   - Otherwise (word-initial or after vowel) -> আ independent
            if (p == "AH" && stress == 0) {
                if (prevWasConsonant) {
                    // Inherent vowel: emit nothing
                    prevWasConsonant = false
                } else {
                    // Word-initial or post-vowel: emit আ as independent vowel
                    out.append("আ")
                    prevWasConsonant = false
                }
                continue
            }

            val vowel = VOWELS[p] ?: return null
            out.append(if (prevWasConsonant) vowel.kar else vowel.independent)
            prevWasConsonant = false
        }

        return out.toString().takeIf { it.isNotEmpty() }
    }
}
