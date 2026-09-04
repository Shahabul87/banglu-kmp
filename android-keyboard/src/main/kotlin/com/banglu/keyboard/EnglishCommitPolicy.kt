package com.banglu.keyboard

/**
 * S182 (tester: typed "Lal", the keyboard pushed it to "all" — "engine
 * should not push anything; the user's selection has to be on top"):
 * what Space does with an English word in EN mode. The typed word ALWAYS
 * commits; a likely correction becomes a chip the user may tap. Only the
 * explicit auto-replace switch (default OFF) restores the old behaviour.
 * Pure decision — the service owns the InputConnection.
 */
object EnglishCommitPolicy {
    sealed class Decision {
        /** Commit the typed word; nothing to offer. */
        data class Keep(val word: String) : Decision()
        /** Commit the typed word and offer [correction] as a tap-to-replace chip. */
        data class KeepWithOffer(val word: String, val correction: String) : Decision()
        /** Auto-replace switch ON: commit [correction], keep the ↶ undo chip. */
        data class Replace(val word: String, val correction: String) : Decision()
    }

    fun decide(word: String, correction: String?, autoReplaceEnabled: Boolean): Decision = when {
        correction == null || correction == word -> Decision.Keep(word)
        autoReplaceEnabled -> Decision.Replace(word, correction)
        else -> Decision.KeepWithOffer(word, correction)
    }
}
