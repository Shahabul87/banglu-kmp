package com.banglu.desktop.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S141: next-word predictions after a space commit (the Android/Windows
 * prediction bar on the desktop) — real engine on the repo dictionary.
 */
class S141PredictionsEditorTest {
    private val engine get() = FullEngine.facade
    private fun newState() = EditorState(engine)

    /** A private, FULLY loaded engine (validator + n-gram model, the desktop
     *  boot path of EditorScreen) — the shared adapter singleton stays on the
     *  store-only fixture the other pins were written against. */
    private object FullEngine {
        private val engine: com.banglu.engine.SmartEngine by lazy {
            com.banglu.engine.SmartEngine().also { eng ->
                eng.initializeSync()
                kotlinx.coroutines.runBlocking {
                    eng.initialize(storage = null, loader = com.banglu.engine.JvmSqliteDictionaryLoader(TestEngine.dbFile()))
                }
                eng.setPhoneticIndex(com.banglu.engine.JvmSqlitePhoneticIndexStore(TestEngine.dbFile()))
            }
        }
        val facade: EngineFacade = object : EngineFacade {
            override fun instant(raw: String) = engine.convertForInstantPreview(raw)
            override fun convert(raw: String) = engine.convertWord(raw).bengali
            override fun suggest(raw: String, limit: Int) = engine.getSuggestions(raw, limit).map { it.bengali }
            override fun reverse(bangla: String) = com.banglu.engine.util.ReverseTransliterator.reverseWord(bangla)
            override fun selected(raw: String, bangla: String, explicit: Boolean) {}
            override fun predict(prev2: String?, prev1: String, limit: Int): List<String> {
                val tri = if (!prev2.isNullOrBlank()) engine.getTrigramNextWordPredictions(prev2, prev1, limit) else emptyList()
                val seen = tri.mapTo(mutableSetOf()) { it.bengali }
                return (tri + engine.getNextWordPredictions(prev1, limit).filter { it.bengali !in seen })
                    .take(limit).map { it.bengali }
            }
            override fun usedNext(prev: String, next: String) { engine.recordUserBigram(prev, next) }
        }
    }

    private fun EditorState.type(s: String) {
        for (c in s) applyEdit(display.substring(0, cursor) + c + display.substring(cursor), cursor + 1)
    }

    /** Simulates the async refine landing for the forming word (the UI path). */
    private fun EditorState.settle() {
        if (!forming) return
        refine(formingRaw, engine.convert(formingRaw), engine.suggest(formingRaw))
    }

    /** Simulates the UI's async fetch landing for the current request. */
    private fun EditorState.landPredictions(): List<String> {
        val (prev2, prev1) = contextBeforeCursor()
        val list = if (prev1 == null) emptyList() else engine.predict(prev2, prev1, 5)
        setPredictions(predictionRequest, list)
        return list
    }

    @Test
    fun spaceCommitRequestsPredictionsAndTheEngineKnowsTheCollocation() {
        val s = newState()
        s.type("boishakhi")
        s.settle()                        // refined বৈশাখী (instant preview shows বৈশাখি)
        assertFalse(s.predictionVisible)
        s.type(" ")
        assertTrue(s.predictionAnchor == s.commitPos, "anchored at the cursor")
        val list = s.landPredictions()
        assertTrue("মেলা" in list, "বৈশাখী -> মেলা expected, got $list")
        assertTrue(s.predictionVisible)
        assertEquals(null to "বৈশাখী", s.contextBeforeCursor())
    }

    @Test
    fun pickingAPredictionCommitsWordPlusSpaceAndChains() {
        val s = newState()
        s.type("diner ")
        val list = s.landPredictions()
        assertTrue(list.isNotEmpty(), "দিনের has followers")
        s.pickPrediction(0)
        assertTrue(s.committed.endsWith("${list[0]} "), "got '${s.committed}'")
        assertEquals("দিনের" to list[0], s.contextBeforeCursor())
        assertTrue(s.predictionAnchor == s.commitPos && s.predictions.isEmpty(), "re-requested, awaiting the next fetch")
    }

    @Test
    fun anyEditDismissesPredictionsAndStaleResultsAreDropped() {
        val s = newState()
        s.type("maser ")
        val request = s.predictionRequest
        s.type("p")                       // a letter starts forming — predictions gone
        assertFalse(s.predictionVisible)
        s.setPredictions(request, listOf("পর"))   // late arrival for the old request
        assertFalse(s.predictionVisible)
        assertTrue(s.predictions.isEmpty())
        // Digits keep typing digits — never a pick.
        s.type(" ")
        s.landPredictions()
        s.type("2")
        assertTrue(s.committed.endsWith("২"), "got '${s.committed}'")
        assertFalse(s.predictionVisible)
    }

    @Test
    fun undoClearsPredictions() {
        val s = newState()
        s.type("rater ")
        s.landPredictions()
        s.undo()
        assertFalse(s.predictionVisible)
    }
}
