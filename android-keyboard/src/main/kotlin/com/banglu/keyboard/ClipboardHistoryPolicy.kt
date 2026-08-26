package com.banglu.keyboard

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * S135 (F-003, production audit): the pure decision table for the keyboard's
 * clipboard history. The old code persisted whatever the system clipboard
 * held the moment the panel opened — including one-time codes and passwords
 * copied from a password manager — as reversible Base64 that never expired.
 *
 * Law, pinned by [ClipboardHistoryPolicyTest]:
 *  - a clip the source app flagged sensitive (`EXTRA_IS_SENSITIVE`) is never
 *    remembered;
 *  - nothing is remembered while the editor is any private field — password,
 *    one-time code, email, URI, number/phone, no-personalized-learning (the
 *    IME's `privateInputMode`/`sensitiveInputMode`) — the panel still PASTES
 *    there, it just learns nothing from the field; in password/OTP fields it
 *    shows only the current clip as a one-shot, never stored history;
 *  - every entry carries the time it was saved and expires after
 *    [RETENTION_MS] (one hour — the retention Gboard documents for unpinned
 *    clips; Banglu has no pinning, so nothing outlives it);
 *  - entries stored by earlier versions (no timestamp) are treated as saved
 *    now, so an update never wipes a user's panel — they simply expire
 *    within the hour like everything else.
 *
 * The persisted form stays app-private SharedPreferences, excluded from
 * backup/transfer; with the sensitive guards and the one-hour cap, encrypting
 * it further would add Android-Keystore failure modes without a matching
 * threat (the file is readable only by this app's uid).
 */
@OptIn(ExperimentalEncodingApi::class)
object ClipboardHistoryPolicy {

    const val RETENTION_MS: Long = 60L * 60L * 1000L
    const val MAX_ITEMS = 12
    const val MAX_ITEM_CHARS = 1_000

    /** Android's own sensitivity marker (ClipDescription.EXTRA_IS_SENSITIVE,
     *  API 33) — the string is stable and harmless on older releases. */
    const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    private const val ITEM_SEPARATOR = "|"
    private const val FIELD_SEPARATOR = ","

    data class Entry(val text: String, val savedAtMs: Long)

    /** May this clip be added to history? [privateField] is ANY private
     *  editor (password, OTP, email, URI, number/phone, no-learning). */
    fun shouldRemember(privateField: Boolean, clipIsSensitive: Boolean): Boolean =
        !privateField && !clipIsSensitive

    /** Normalizes a candidate clip; null when there is nothing to keep. */
    fun normalize(text: String): String? =
        text.trim().take(MAX_ITEM_CHARS).takeIf { it.isNotBlank() }

    /** [entries] with [text] moved/added to the front, capped at [MAX_ITEMS]. */
    fun remember(entries: List<Entry>, text: String, nowMs: Long): List<Entry> {
        val clean = normalize(text) ?: return entries
        return (listOf(Entry(clean, nowMs)) + entries.filter { it.text != clean }).take(MAX_ITEMS)
    }

    /** Drops expired entries (and anything past the cap). */
    fun prune(entries: List<Entry>, nowMs: Long): List<Entry> =
        entries.filter { nowMs - it.savedAtMs < RETENTION_MS }.take(MAX_ITEMS)

    fun encode(entries: List<Entry>): String = entries.joinToString(ITEM_SEPARATOR) {
        Base64.Default.encode(it.text.toByteArray(Charsets.UTF_8)) +
            FIELD_SEPARATOR + it.savedAtMs
    }

    /** Parses a persisted blob; expired and undecodable items are dropped. */
    fun decode(raw: String?, nowMs: Long): List<Entry> {
        if (raw.isNullOrBlank()) return emptyList()
        val parsed = raw.split(ITEM_SEPARATOR).mapNotNull { item ->
            if (item.isBlank()) return@mapNotNull null
            val fields = item.split(FIELD_SEPARATOR, limit = 2)
            val text = runCatching {
                String(Base64.Default.decode(fields[0]), Charsets.UTF_8)
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            // Legacy (pre-S135) items carried no timestamp: treat as saved now.
            val savedAt = fields.getOrNull(1)?.toLongOrNull() ?: nowMs
            Entry(text, savedAt)
        }
        return prune(parsed, nowMs)
    }
}
