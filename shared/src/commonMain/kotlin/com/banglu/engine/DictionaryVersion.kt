package com.banglu.engine

/**
 * S108: single source of truth for the dictionary build every surface must
 * ship. The compiler stamps this into dictionary.sqlite metadata (and the
 * slim JSON copies it from there), and every consumer gates on it:
 *
 * - Android: AndroidDictionaryLoader re-copies the asset on mismatch and
 *   SqlitePhoneticIndexStore refuses to serve a mismatched db.
 * - Desktop/JVM: JvmSqlitePhoneticIndexStore reports isAvailable=false on
 *   mismatch (the editor then boots seed-only with a visible status).
 * - JS surfaces (extension, macOS IME, web): BangluWebEngine.attachSlimDictionary
 *   throws on mismatch so the host degrades loudly to the seed engine.
 * - Build scripts (browser-extension/build.sh, macos-ime/scripts/build-engine.sh)
 *   grep this constant and refuse to bundle a stale banglu-slim.json.
 *
 * Bump this together with the compiler round; nothing else needs editing.
 */
object DictionaryVersion {
    // 3.9.4 (S112): khanda-ta ৎ/ত্ twin fold (936 legacy-encoding pairs
    // merge into the standard ৎ forms) + book_lexicon.tsv reading-register
    // vocabulary (416 words from the S110 book corpus at count >= 3).
    // 3.9.3 (S109): word-initial অ chat onsets — "a"/"aw" alias seeds for
    // অ-initial words + the initial-o twin promote pass.
    const val REQUIRED = "3.9.8"
}
