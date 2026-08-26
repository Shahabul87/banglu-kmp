package com.banglu.keyboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** S135 (F-003): pins the clipboard-history retention and sensitivity law. */
class ClipboardHistoryPolicyTest {

    private val now = 1_700_000_000_000L

    @Test
    fun sensitiveClipOrPrivateFieldIsNeverRemembered() {
        assertTrue(ClipboardHistoryPolicy.shouldRemember(privateField = false, clipIsSensitive = false))
        assertFalse(ClipboardHistoryPolicy.shouldRemember(privateField = true, clipIsSensitive = false))
        assertFalse(ClipboardHistoryPolicy.shouldRemember(privateField = false, clipIsSensitive = true))
        assertFalse(ClipboardHistoryPolicy.shouldRemember(privateField = true, clipIsSensitive = true))
    }

    @Test
    fun legacyBlobReencodesDated_soTheLoaderPersistsItOnce() {
        // S136: the IME writes the dated form back when encode(decode(raw)) != raw.
        val legacy = java.util.Base64.getEncoder().encodeToString("পুরনো".toByteArray())
        val decoded = ClipboardHistoryPolicy.decode(legacy, now)
        assertTrue(ClipboardHistoryPolicy.encode(decoded) != legacy)
        assertEquals(decoded, ClipboardHistoryPolicy.decode(ClipboardHistoryPolicy.encode(decoded), now))
    }

    @Test
    fun roundTripKeepsTextOrderAndTimestamps() {
        val entries = listOf(
            ClipboardHistoryPolicy.Entry("আমি ভালো আছি", now),
            ClipboardHistoryPolicy.Entry("hello, world", now - 1_000)
        )
        val decoded = ClipboardHistoryPolicy.decode(ClipboardHistoryPolicy.encode(entries), now)
        assertEquals(entries, decoded)
    }

    @Test
    fun entriesExpireAfterOneHour() {
        val entries = listOf(
            ClipboardHistoryPolicy.Entry("fresh", now - ClipboardHistoryPolicy.RETENTION_MS + 1),
            ClipboardHistoryPolicy.Entry("stale", now - ClipboardHistoryPolicy.RETENTION_MS)
        )
        assertEquals(listOf("fresh"), ClipboardHistoryPolicy.prune(entries, now).map { it.text })
        val decoded = ClipboardHistoryPolicy.decode(ClipboardHistoryPolicy.encode(entries), now)
        assertEquals(listOf("fresh"), decoded.map { it.text })
    }

    @Test
    fun legacyItemsWithoutTimestampAreTreatedAsSavedNow() {
        // Pre-S135 format: bare Base64 items joined by '|'.
        val legacy = java.util.Base64.getEncoder().encodeToString("পুরনো".toByteArray())
        val decoded = ClipboardHistoryPolicy.decode(legacy, now)
        assertEquals(listOf(ClipboardHistoryPolicy.Entry("পুরনো", now)), decoded)
    }

    @Test
    fun rememberDedupesMovesToFrontAndCaps() {
        var entries = emptyList<ClipboardHistoryPolicy.Entry>()
        for (i in 1..(ClipboardHistoryPolicy.MAX_ITEMS + 3)) {
            entries = ClipboardHistoryPolicy.remember(entries, "item $i", now + i)
        }
        assertEquals(ClipboardHistoryPolicy.MAX_ITEMS, entries.size)
        assertEquals("item ${ClipboardHistoryPolicy.MAX_ITEMS + 3}", entries.first().text)
        entries = ClipboardHistoryPolicy.remember(entries, "  item 5  ", now + 100)
        assertEquals("item 5", entries.first().text)
        assertEquals(1, entries.count { it.text == "item 5" })
        assertEquals(ClipboardHistoryPolicy.MAX_ITEMS, entries.size)
    }

    @Test
    fun undecodableAndBlankItemsAreDropped() {
        val good = java.util.Base64.getEncoder().encodeToString("ok".toByteArray())
        val decoded = ClipboardHistoryPolicy.decode("$good,$now|not base64!!|,$now", now)
        assertEquals(listOf("ok"), decoded.map { it.text })
    }
}
