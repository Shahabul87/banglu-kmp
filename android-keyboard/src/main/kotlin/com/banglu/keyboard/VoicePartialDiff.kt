package com.banglu.keyboard

/**
 * S56 (tester: "after some time if i say the words it delete previous all
 * sentence or words and start to new write"): pure decision table for how a
 * new recognizer partial revises the live voice text on screen.
 *
 * The old logic treated any non-prefix revision as "delete the ENTIRE live
 * region and re-commit" — after a long dictation the live region IS the whole
 * sentence, and Google's recognizer emits non-prefix hypotheses both when it
 * rewrites earlier words and when it resets to a fresh segment after internal
 * endpointing. Both cases nuked the user's sentence.
 *
 * New law, pinned here as a pure function (VoiceSessionPolicy pattern):
 *  - a shorter interim hypothesis is ignored (unchanged);
 *  - a pure extension appends only the suffix (unchanged);
 *  - a partial that diverges AFTER a common word prefix deletes ONLY the
 *    diverging tail and writes the new tail — earlier words are never
 *    touched;
 *  - a partial sharing NO word prefix with the live text is a fresh segment:
 *    it APPENDS (with a space boundary) and deletes nothing. Worst case is a
 *    duplicated word — never lost text.
 */
object VoicePartialDiff {

    /** Replace the last [deleteCount] chars of the live region with [insert]
     *  (deleteCount == 0 → plain append). [newLiveText] is the resulting live
     *  region the caller should track. */
    data class Patch(val deleteCount: Int, val insert: String, val newLiveText: String)

    /** @return the patch to apply, or null when the partial must be ignored
     *  (empty / identical / shorter interim hypothesis). */
    fun diff(previous: String, partial: String): Patch? {
        if (partial.isEmpty()) return null
        if (previous.isEmpty()) return Patch(0, partial, partial)
        if (partial == previous || previous.startsWith(partial)) return null
        if (partial.startsWith(previous)) {
            val suffix = partial.removePrefix(previous)
            return Patch(0, suffix, partial)
        }

        val prevWords = previous.split(' ')
        val partWords = partial.split(' ')
        var common = 0
        while (common < prevWords.size && common < partWords.size &&
            prevWords[common] == partWords[common]
        ) {
            common++
        }

        if (common == 0) {
            // Fresh segment (recognizer reset): append, never delete.
            val boundary = if (previous.last().isWhitespace()) "" else " "
            val insert = boundary + partial
            return Patch(0, insert, previous + insert)
        }

        // Rewrite of the tail after a stable word prefix: replace only the
        // diverging words. (Cannot both be exhausted at `common` — that would
        // have matched the equality/prefix branches above.)
        val stablePrefix = prevWords.take(common).joinToString(" ")
        val newTail = partWords.drop(common).joinToString(" ")
        val deleteCount = previous.length - stablePrefix.length
        val insert = if (newTail.isEmpty()) "" else " $newTail"
        val newLive = if (newTail.isEmpty()) stablePrefix else "$stablePrefix $newTail"
        return Patch(deleteCount, insert, newLive)
    }
}
