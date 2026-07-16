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
    fun secondSaveOverwritesTheFirst() {
        val store = tempStore()
        store.saveDraft(Draft("প্রথম", 0, "", null, null))
        store.saveDraft(Draft("দ্বিতীয় লেখা", 5, "kal", null, null))
        assertEquals("দ্বিতীয় লেখা", store.loadDraft()?.text)
        store.savePrefs(EditorPrefs(winW = 900))
        store.savePrefs(EditorPrefs(winW = 1000))
        assertEquals(1000, store.loadPrefs().winW)
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

    @Test
    fun learningEnabledDefaultsTrueAndRoundTrips() {
        val store = tempStore()
        assertEquals(true, store.loadPrefs().learningEnabled)
        store.savePrefs(store.loadPrefs().copy(learningEnabled = false))
        assertEquals(false, store.loadPrefs().learningEnabled)
    }

    @Test
    fun gettingStartedSeenDefaultsFalseAndRoundTrips() {
        val store = tempStore()
        assertEquals(false, store.loadPrefs().gettingStartedSeen)
        store.savePrefs(store.loadPrefs().copy(gettingStartedSeen = true))
        assertEquals(true, store.loadPrefs().gettingStartedSeen)
    }
}
