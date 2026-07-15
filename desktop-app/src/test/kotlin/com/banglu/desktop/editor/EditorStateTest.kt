package com.banglu.desktop.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorStateTest {
    private val engine = TestEngine.facade

    @Test
    fun engineFacadeConvertsThroughTheRealStore() {
        assertEquals("কেমন", engine.convert("kemon"))
        assertTrue(engine.suggest("kemon").contains("কেমন"))
        assertTrue(engine.instant("kemon").isNotEmpty())
    }
}
