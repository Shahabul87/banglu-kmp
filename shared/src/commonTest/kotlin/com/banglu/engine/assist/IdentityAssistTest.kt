package com.banglu.engine.assist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/** S98: identity assist, pinned. */
class IdentityAssistTest {

    private fun fresh() = IdentityAssist()

    // ── domain completion ────────────────────────────────────────────────

    @Test
    fun atSignOffersCommonDomains() {
        val a = fresh()
        val s = a.domainSuggestions("sham251087@", 3)
        assertEquals(
            listOf("sham251087@gmail.com", "sham251087@yahoo.com", "sham251087@outlook.com"),
            s
        )
    }

    @Test
    fun partialDomainFilters() {
        val a = fresh()
        assertEquals(listOf("x99@gmail.com"), a.domainSuggestions("x99@gm", 3))
        assertEquals(
            listOf("me@yahoo.com"),
            a.domainSuggestions("me@ya", 3)
        )
    }

    @Test
    fun usersOwnDomainRanksFirst() {
        val a = fresh()
        a.recordIdentity("sham@du.ac.bd")
        val s = a.domainSuggestions("newname@", 3)
        assertEquals("newname@du.ac.bd", s.first(), "learned domain must lead: $s")
    }

    @Test
    fun nonEmailTokensGetNothing() {
        val a = fresh()
        assertTrue(a.domainSuggestions("hello", 3).isEmpty())
        assertTrue(a.domainSuggestions("@gmail", 3).isEmpty(), "no local part")
        assertTrue(a.domainSuggestions("", 3).isEmpty())
        assertFalse(a.isEmailLikeToken("বাংলা@x"))
    }

    // ── saved identities ─────────────────────────────────────────────────

    @Test
    fun completeEmailsAreSavedMostRecentFirst() {
        val a = fresh()
        a.recordIdentity("first@gmail.com")
        a.recordIdentity("second@yahoo.com")
        assertEquals(listOf("second@yahoo.com", "first@gmail.com"), a.savedIdentities(3))
        // Re-using an old one moves it to the front.
        a.recordIdentity("first@gmail.com")
        assertEquals("first@gmail.com", a.savedIdentities(3).first())
    }

    @Test
    fun incompleteOrJunkIsNeverSaved() {
        val a = fresh()
        a.recordIdentity("sham@")            // no domain
        a.recordIdentity("sham@gmail")       // no tld
        a.recordIdentity("not an email")
        a.recordIdentity("a@b@c.com")        // double @
        assertTrue(a.savedIdentities(3).isEmpty())
    }

    @Test
    fun storesStayBounded() {
        val a = fresh()
        repeat(20) { a.recordIdentity("user$it@mail$it.com") }
        assertTrue(a.savedIdentities(100).size <= 8)
    }

    // ── persistence ──────────────────────────────────────────────────────

    @Test
    fun serializeLoadRoundTrips() {
        val a = fresh()
        a.recordIdentity("sham251087@gmail.com")
        a.recordIdentity("work@du.ac.bd")
        val b = fresh()
        b.load(a.serialize())
        assertEquals(a.savedIdentities(5), b.savedIdentities(5))
        assertEquals("x@du.ac.bd", b.domainSuggestions("x@", 1).first())
    }

    @Test
    fun loadToleratesGarbage() {
        val a = fresh()
        a.load("e\tok@gmail.com\nnot-a-line\ne\tbroken@\nd\tgmail.com\nd\t.bad\n")
        assertEquals(listOf("ok@gmail.com"), a.savedIdentities(3))
    }
}
