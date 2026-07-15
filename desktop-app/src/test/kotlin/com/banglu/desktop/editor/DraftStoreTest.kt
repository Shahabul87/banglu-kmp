package com.banglu.desktop.editor

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DraftStoreTest {
    private fun tempStore() = DraftStore(createTempDirectory("banglu-test").toFile())

    @Test
    fun draftRoundTripsAndClears() {
        val store = tempStore()
        assertNull(store.loadDraft())
        val d = Draft("কেমন আছো ", 9, "bondh", "/tmp/চিঠি.txt", "কেমন আছো ")
        store.saveDraft(d)
        assertEquals(d, store.loadDraft())
        store.clearDraft()
        assertNull(store.loadDraft())
    }

    @Test
    fun prefsRoundTripWithDefaults() {
        val store = tempStore()
        assertEquals(EditorPrefs(), store.loadPrefs())     // defaults when missing
        val p = EditorPrefs(recent = listOf("/a.txt", "/b.txt"), banglaDigits = false, winW = 900, winH = 700)
        store.savePrefs(p)
        assertEquals(p, store.loadPrefs())
    }

    @Test
    fun corruptFilesFallBackSafely() {
        val dir = createTempDirectory("banglu-test").toFile()
        File(dir, "draft.json").writeText("{not json")
        File(dir, "editor.json").writeText("{not json")
        val store = DraftStore(dir)
        assertNull(store.loadDraft())
        assertEquals(EditorPrefs(), store.loadPrefs())
    }
}
