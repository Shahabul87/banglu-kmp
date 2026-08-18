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
    // S108: SmartEngine is not internally thread-safe (reentrancy flags,
    // learning-time trie inserts). Android serializes every engine call on a
    // limitedParallelism(1) lane; the desktop refine coroutines are
    // cooperative-cancel only, so a superseded refine can still be executing
    // when the next one starts. One monitor gives the same single-lane
    // guarantee for every full-pipeline entry point. instant() stays
    // lock-free — it reads only immutable rule tables (invariant #1).
    private val engineLock = Any()

    override fun instant(raw: String) = SmartEngineAdapter.convertForInstantPreview(raw)
    override fun convert(raw: String) =
        synchronized(engineLock) { SmartEngineAdapter.convertWord(raw).bengali }
    override fun suggest(raw: String, limit: Int) =
        synchronized(engineLock) { SmartEngineAdapter.getSuggestions(raw, limit).map { it.bengali } }
    override fun reverse(bangla: String) = ReverseTransliterator.reverseWord(bangla)
    override fun selected(raw: String, bangla: String, explicit: Boolean) =
        synchronized(engineLock) {
            SmartEngineAdapter.onWordSelected(raw, bangla, learnAsWord = false, explicitChoice = explicit)
        }
}
