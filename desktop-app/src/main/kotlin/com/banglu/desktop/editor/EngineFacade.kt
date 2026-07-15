package com.banglu.desktop.editor

import com.banglu.engine.SmartEngineAdapter
import com.banglu.engine.util.ReverseTransliterator

/**
 * The editor's only door to the engine — also the seam the future AI
 * corrector implements (spec §7): anything that can propose text for raw
 * Banglish can drive the editor.
 */
interface EngineFacade {
    /** Sync, rule-only, zero I/O — safe on the UI thread (invariant #1). */
    fun instant(raw: String): String
    /** Full pipeline — dictionary/SQLite; call from Dispatchers.Default only. */
    fun convert(raw: String): String
    fun suggest(raw: String, limit: Int = 6): List<String>
    fun reverse(bangla: String): String
    fun selected(raw: String, bangla: String, explicit: Boolean)
}

object RealEngineFacade : EngineFacade {
    override fun instant(raw: String) = SmartEngineAdapter.convertForInstantPreview(raw)
    override fun convert(raw: String) = SmartEngineAdapter.convertWord(raw).bengali
    override fun suggest(raw: String, limit: Int) =
        SmartEngineAdapter.getSuggestions(raw, limit).map { it.bengali }
    override fun reverse(bangla: String) = ReverseTransliterator.reverseWord(bangla)
    override fun selected(raw: String, bangla: String, explicit: Boolean) =
        SmartEngineAdapter.onWordSelected(raw, bangla, learnAsWord = false, explicitChoice = explicit)
}
