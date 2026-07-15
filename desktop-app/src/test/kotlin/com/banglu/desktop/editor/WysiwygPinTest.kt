package com.banglu.desktop.editor

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Invariant #2 pin: what the forming word SHOWS is what space COMMITS —
 * both with the refine landed (normal) and without it (fast typist).
 */
class WysiwygPinTest {
    private val engine = TestEngine.facade

    private val phrases = listOf(
        "kemon acho bondhu",
        "ami bangla likhi",
        "issa korche golpo bolte",
        "bujte parcina keno",
        "kalke dekha hobe",
    )

    private fun run(phrase: String, settle: Boolean): String {
        val s = EditorState(engine)
        val previews = mutableListOf<String>()
        for (word in phrase.split(" ")) {
            for (c in word) s.applyEdit(
                s.display.substring(0, s.cursor) + c + s.display.substring(s.cursor),
                s.cursor + 1
            )
            if (settle) s.refine(s.formingRaw, engine.convert(s.formingRaw), engine.suggest(s.formingRaw))
            previews.add(s.formingBangla)                  // what the user SEES
            s.applyEdit(s.display.substring(0, s.cursor) + " " + s.display.substring(s.cursor), s.cursor + 1)
        }
        assertEquals(previews.joinToString(" ") + " ", s.committed, "phrase: $phrase settle=$settle")
        return s.committed
    }

    @Test fun committedEqualsPreviewsWithRefine() { phrases.forEach { run(it, settle = true) } }
    @Test fun committedEqualsPreviewsWithoutRefine() { phrases.forEach { run(it, settle = false) } }
}
