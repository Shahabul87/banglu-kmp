package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S153 — the general English-register wall. Every pair is the corpus-
 * majority rendering (BanglaTLit/Vashantor gold, >=5 occurrences, >=60%
 * majority; full table in the study appendix). If one of these flips, a
 * documented decision is required — the corpus voted.
 */
class S153EnglishRegisterJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)

    @Test
    fun corpusMajorityRenderings() {
        val expected = mapOf(
            "but" to "বাট",
            "and" to "এন্ড",
            "author" to "অথর",
            "tuner" to "টিউনার",
            "theme" to "থিম",
            "way" to "ওয়ে",
            "custom" to "কাস্টম",
            "recovery" to "রিকভারি",
            "refer" to "রেফার",
            "unlimited" to "আনলিমিটেড",
            "tutorial" to "টিউটোরিয়াল",
            "lol" to "লল",
            "photo" to "ফটো",
            "config" to "কনফিগ",
            "wow" to "ওয়াও",
            "domain" to "ডোমেইন",
            "create" to "ক্রিয়েট",
            "mail" to "মেইল",
            "from" to "ফ্রম",
            "single" to "সিঙ্গেল",
            "msg" to "মেসেজ",
            "category" to "ক্যাটাগরি",
            "published" to "পাবলিশড",
            "paid" to "পেইড",
            "again" to "এগেইন",
            "english" to "ইংলিশ",
            "union" to "ইউনিয়ন",
            "police" to "পুলিশ",
            "plugin" to "প্লাগইন",
            "any" to "এনি",
            "latest" to "লেটেস্ট",
            "proxy" to "প্রক্সি",
            "main" to "মেইন",
            "floor" to "ফ্লোর",
            "sound" to "সাউন্ড",
            "original" to "অরিজিনাল",
            "official" to "অফিসিয়াল",
            "verification" to "ভেরিফিকেশন",
            "unlock" to "আনলক",
            "social" to "সোশ্যাল",
            "translate" to "ট্রান্সলেট",
            "run" to "রান",
            "muslim" to "মুসলিম",
            "host" to "হোস্ট",
            "automatic" to "অটোমেটিক",
            "sir" to "স্যার",
            "short" to "শর্ট",
            "really" to "রিয়েলি",
            "flash" to "ফ্ল্যাশ",
            "connected" to "কানেক্টেড",
            "only" to "অনলি",
            "contact" to "কন্টাক্ট",
            "memory" to "মেমরি",
            "makeup" to "মেকআপ",
            "opera" to "অপেরা",
            "already" to "অলরেডি",
            "editor" to "এডিটর",
            "accept" to "একসেপ্ট",
            "join" to "জয়েন",
            "not" to "নট",
            "up" to "আপ",
            "date" to "ডেট",
            "ban" to "ব্যান",
            "mode" to "মোড",
            "number" to "নাম্বার",
            "phone" to "ফোন",
            "my" to "মাই",
            "ad" to "এড",
            "vi" to "ভাই",
            "ak" to "এক",
            "bt" to "বাট",
            "gd" to "গুড",
            "nc" to "নাইস",
            "data" to "ডাটা",
            "account" to "একাউন্ট",
            "address" to "এড্রেস",
            "id" to "আইডি",
            "gp" to "জিপি",
            "ss" to "এসএস",
            "uc" to "ইউসি",
            "bd" to "বিডি",
            "sms" to "এসএমএস",
            "html" to "এইচটিএমএল",
        )
        val failures = expected.mapNotNull { (k, v) ->
            val got = fold(engine.convertWord(k).bengali)
            if (got != fold(v)) "$k -> $got (wanted $v)" else null
        }
        assertEquals(emptyList(), failures, "register misses")
    }
}
