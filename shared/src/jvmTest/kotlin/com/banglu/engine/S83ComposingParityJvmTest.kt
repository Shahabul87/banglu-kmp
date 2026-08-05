package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S83: the composing preview's dictionary-vs-store arbitration is the commit
 * path's own function (storeBeatsDictionary) — parity by construction. The
 * old hand-copied mirror had drifted (missing two of four branches): on the
 * 100K study, 503 of the 1,034 preview≠commit keys were 4+ letters and this
 * class; the shared function fixes 96% of them. The 2-3 letter divergence
 * (531 keys) is the pin-protected V2 kar-composition contract, NOT drift —
 * see V2KarCompositionRegressionTest.
 */
class S83ComposingParityJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)

    @Test
    fun driftClassKeysPreviewWhatSpaceCommits() {
        // Representatives of the drifted arbitration branches from the study:
        // ghosh (corpusFreq>dictFreq+5 branch), isolami (ইসলামী seed vs
        // ইসলামি store), dolfin (S81 loanword class).
        for (key in listOf("ghosh", "isolami", "dolfin", "toiri", "likh")) {
            assertEquals(
                fold(engine.convertWord(key).bengali),
                fold(engine.convertForComposing(key).bengali),
                "preview must equal commit for '$key'"
            )
        }
    }

    @Test
    fun compoundAndNegationParityHolds() {
        for (key in listOf("korbone", "parbona", "bolbone", "parle", "beche")) {
            assertEquals(
                fold(engine.convertWord(key).bengali),
                fold(engine.convertForComposing(key).bengali),
                "preview must equal commit for '$key'"
            )
        }
    }
}
