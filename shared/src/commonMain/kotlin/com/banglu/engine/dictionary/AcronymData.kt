package com.banglu.engine.dictionary

/**
 * S52 — curated acronym layer. Two tiers, whitelist only (no heuristics; a
 * regex/short-token rule is unsafe in both directions — see
 * `.superpowers/sdd/explore-loanword-machinery.md` §6).
 *
 * Evidence base: `.superpowers/sdd/probe-english-acronyms.md` (B-list, 28
 * acronyms probed against the real store), `.superpowers/sdd/research-banglish-web.md`
 * (top-30 BD-relevant acronyms + verified letter-name table), and a session
 * probe of every candidate key against `./dictionary.sqlite`
 * (`SmartEngine.convertWord` + `getSuggestions`) — see the S52 Task 1 report
 * for the full per-key evidence table.
 *
 * Tier rule (typed-intent test): would a Bengali speaker ever TYPE this key
 * wanting the CURRENT Bengali word? No -> [ACRONYM_OVERRIDES] (Tier P, the
 * key's primary is replaced). Yes, or genuinely in doubt ->
 * [ACRONYM_SUGGESTIONS] (Tier S, primary stays untouched; the acronym is
 * offered as a suggestion chip only).
 */

/**
 * Tier P — primary override. Every entry's current (pre-S52) primary was
 * either unreadable conjunct/RULE soup, an unrelated dictionary collision
 * with near-zero typed-intent for that Bengali word via this exact key, or a
 * broken `english_lexicon` row (garbled by the CMU/Arpabet pipeline). A
 * currently-correct acronym is included verbatim (comment `(correct)`) to
 * harden it against lite mode / the slim web tier, which cannot reach the
 * validator-gated `english_lexicon` arbitration paths that produce it today
 * (`SmartEngine.kt` §4 junk-path, §1 item 2 margin rule — both require
 * `validator.isLoaded()`).
 */
internal val ACRONYM_OVERRIDES: Map<String, String> = mapOf(
    // ── Education / exam initialisms (garbage: CLEAN_TRANSLITERATION/RULE conjunct soup) ──
    "ssc" to "এসএসসি",   // current: স্স্ছ (garbage)
    "hsc" to "এইচএসসি",  // current: হ্স্ছ (garbage)
    "psc" to "পিএসসি",   // current: প্স্ছ (garbage)
    "jsc" to "জেএসসি",   // current: জ্স্ছ (garbage)
    "msc" to "এমএসসি",   // current: ম্স্ছ (garbage)
    "bsc" to "বিএসসি",   // current: বসছ (garbage — RULE-generated, not a natural typed-intent path)
    "bcs" to "বিসিএস",   // current: ব্ছ্স (garbage)
    "mbbs" to "এমবিবিএস", // current: ম্ব্ব্স (garbage)
    "llb" to "এলএলবি",   // current: লড়ব (collision — "will fight"; natural typing is "lorbo", not "llb")
    "bba" to "বিবিএ",    // current: ব্যা (collision — not a natural typed-intent path)
    "gpa" to "জিপিএ",    // current: গ্পা (garbage)
    "cgpa" to "সিজিপিএ", // current: ছাপা (collision — "print/stamp"; natural typing is "chapa", not "cgpa")
    "pdf" to "পিডিএফ",   // current: প্দ্ফ (garbage)

    // ── Govt/admin/tech initialisms (garbage or unrelated-word collision) ──
    "ngo" to "এনজিও",    // current: নয়গো (garbage)
    "otp" to "ওটিপি",    // current: আতপ (collision — "parched rice"; not a natural typed-intent path)
    "nid" to "এনআইডি",   // current: নিদ (collision — poetic "sleep"; not a natural typed-intent path)
    "hd" to "এইচডি",     // current: হদ (collision — RULE-generated "boundary/limit", not a natural typed-intent path)
    "gb" to "জিবি",      // current: গড়ব (collision — "I will build"; natural typing is "gorbo", not "gb")
    "km" to "কিমি",      // current: কড়মড় (garbage/collision — "crunch" onomatopoeia)
    "dc" to "ডিসি",      // current: দ্ (garbage — hanging consonant fragment)
    "sp" to "এসপি",      // current: সপ (garbage — RULE fragment, not a standalone word)
    "dg" to "ডিজি",      // current: দগ (garbage — RULE fragment, not a standalone word)
    "mp" to "এমপি",      // current: ম-প (garbage — stray-hyphen dictionary artifact)
    "coo" to "সিওও",     // current: ছু (collision — rare interjection, not a natural typed-intent path)
    "vc" to "ভিসি",      // current: ভ্ (garbage — hanging consonant fragment)
    "un" to "ইউএন",      // current: উন (collision — bound prefix, rare standalone typed-intent)
    "uno" to "ইউএনও",    // current: উনা (collision — rare/dialectal, low typed-intent)
    "mr" to "মিস্টার",   // current: ময়র (garbage — not a real word)
    "hq" to "এইচকিউ",    // current: হক (collision — RULE-generated "right/due", not a natural typed-intent path)
    "rab" to "র‍্যাব",    // current: রাব (garbage — not a standard word)
    "db" to "ডিবি",      // current: দ্বয় (collision — formal "pair/duo"; natural typing is "dboy", not "db")
    "vvip" to "ভিভিআইপি", // current: ভিআইপি (garbage — collapses indistinguishably to VIP)

    // ── english_lexicon data bugs (broken CMU/Arpabet renderings, fixed here) ──
    "phd" to "পিএইচডি",  // current: পিএচডি (garbage — nonstandard H rendering; standard is এইচ, never এচ: research-banglish-web.md §C.2, OBSERVED bn.wikipedia)
    "eu" to "ইইউ",       // current: ইইয়ু (garbage — lexicon bug)
    "gps" to "জিপিএস",   // current: গিপিএস (garbage — lexicon bug, wrong G rendering)
    "hr" to "এইচআর",     // current: এচার (garbage — lexicon bug)
    "cfo" to "সিএফও",    // current: সিএফো (garbage — lexicon bug, wrong vowel sign)

    // ── Tier-P hardening: currently correct, whitelisted so lite mode / the
    //    slim web tier (no validator, so the junk-path and margin rule in
    //    SmartEngine never fire there) resolve identically to the full store. ──
    "tv" to "টিভি",        // current: টিভি (correct — lexicalized whole word)
    "kg" to "কেজি",        // current: কেজি (correct)
    "etc" to "এটসেটারা",   // current: এটসেটারা (correct)
    "wifi" to "ওয়াইফাই",  // current: ওয়াইফাই (correct — lexicalized whole word)
    "sim" to "সিম",        // current: সিম (correct — lexicalized whole word)
    "mba" to "এমবিএ",      // current: এমবিএ (correct)
    "cng" to "সিএনজি",     // current: সিএনজি (correct)
    "cctv" to "সিসিটিভি",  // current: সিসিটিভি (correct)
    "vip" to "ভিআইপি",     // current: ভিআইপি (correct)
    "cv" to "সিভি",        // current: সিভি (correct)
    "usb" to "ইউএসবি",     // current: ইউএসবি (correct)
    "ceo" to "সিইও",       // current: সিইও (correct)
    "md" to "এমডি",        // current: এমডি (correct)
    "sms" to "এসেমেস",     // current: এসেমেস (correct — colloquial chat register, matches mission's register target)
    "ac" to "এসি",         // current: এসি (correct)
    "pm" to "পিএম",        // current: পিএম (correct)
    "iq" to "আইকিউ",       // current: আইকিউ (correct)
    "vs" to "ভিএস",        // current: ভিএস (correct)
)

/**
 * Tier S — suggestion only. Each key's CURRENT primary is a real, meaning-
 * bearing Bengali word a speaker plausibly types this exact key wanting
 * (or the ambiguity is genuine enough that Global Constraints' "when in
 * doubt -> Tier S" applies). The primary is NEVER touched; the acronym
 * reading is injected as a one-tap suggestion chip, following the S24
 * curated-loanword chip precedent (`SmartEngine.kt`, `getSuggestions`,
 * the `EnglishDetector.isEnglish` + `phoneticIndex.lookupEnglish` block).
 */
internal val ACRONYM_SUGGESTIONS: Map<String, String> = mapOf(
    "ba" to "বিএ",     // primary বা "or" stays; BA degree offered as a chip
    "ma" to "এমএ",     // primary মা "mother" stays; MA degree offered as a chip
    "mb" to "এমবি",    // primary মব "mob" stays (topical BD-news word); megabyte offered as a chip
    "dr" to "ডক্টর",   // primary ড্র "draw" stays (real sports loanword); Doctor offered as a chip
    "oc" to "ওসি",     // primary ওচ "vile" stays (real dictionary word); Officer-in-Charge offered as a chip
    "atm" to "এটিএম",  // primary আত্ম "self-" stays (real bound morpheme); ATM machine offered as a chip
    "vat" to "ভ্যাট",  // primary ভাত "rice" stays (dominant everyday word); VAT offered as a chip
    "bd" to "বিডি",    // primary বদ "bad" stays (real everyday word); Bangladesh offered as a chip
    "id" to "আইডি",    // primary ঈদ "Eid" stays (Global Constraints' own YES example); ID offered as a chip
    "sos" to "এসওএস",  // primary সস "sauce" stays (natural phonetic key for a real loanword); SOS offered as a chip
)
