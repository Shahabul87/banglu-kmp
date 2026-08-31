# Banglu (বাংলু) Launch Marketing Plan — grounded in 214,894 competitor reviews

**Basis:** `market-research/competitor-review-analysis.md` (2026-08-30 scrape; quotes and counts referenced below come from it). Banglu product claims below are repo-verified facts (process-isolated offline IME, S141–S143 engine rounds, corpus studies in `docs/`).

---

## 1. Where Banglu WINS — complaint cluster → Banglu answer → the evidence

| # | Competitor pain (evidence) | Banglu answer (verified in repo) |
|---|---|---|
| 1 | **Ads in the keyboard.** Ridmik's most-thumbed negative ever (+11,064): "now it shows ads… time to move on to a new keyboard"; 2023 auto-clicking ads; 2026 ads inside the emoji panel. | **No ads, ever, in the typing surface** — and no ad SDK *can* ship in the IME: the build fails if networking/billing classes enter the keyboard process (`verifyImePrivacyBoundary`). This is architecture, not a promise. |
| 2 | **Privacy panic.** "DuckDuckGo blocked at least 400 tracking attempts within a hour… through Ridmik Keyboard" (2025, +50); "it can record everything what I type… insane" (+1,339); Bijoy's credit-card-permission wave. | **100% offline typing engine.** The keyboard process has no network path at all; typing physically cannot leave the phone. No account, no login, no permission shopping list. Ridmik Classic's top praise ("Respects user's PRIVACY-RIGHTS", +5,142) proves privacy converts in this market. |
| 3 | **Battery drain / always-running (live 2026 Ridmik crisis).** "battery eating monster" (+45), "30%+ battery in less than an hour in background" (+41), "Gboard only runs when I use it but Ridmik keeps running all the time" (+22). 25 of 34 Ridmik battery mentions in 2026 are 1–2★. | No background services, no sync process, no cloud calls to retry. The IME does work only while a key is pressed. (Claim it exactly that carefully — see §2 on not overclaiming benchmarks we haven't published.) |
| 4 | **Lag** — Ridmik's #1 complaint by volume (715 negatives; "keyboard gets stuck to the screen", +1,039 in Jan 2026; Swarachakra hangs until reboot). | Sub-millisecond synchronous preview on every keystroke, zero disk I/O on the hot path (test-enforced), lite mode keeps 2GB phones store-backed instead of frozen. |
| 5 | **Phonetic betrayals.** Ridmik: "It becomes মৃতয়ু when I wanted to type মৃত্যু" (+5,359); Gboard: "typing 'sub'… shouldn't force-default to 'শুভ'… vowel mapping non-standard compared to Avro" (+10, Feb 2026); Desh Bangla has no phonetic mode at all (+306 asks for one). | Avro-compatible canonical phonetics PLUS the chat register: kmon→কেমন, onk→অনেক, vlo→ভালো, hoise, korsi; hard words from plain lowercase (sadhinota→স্বাধীনতা, shastho→স্বাস্থ্য, krishno→কৃষ্ণ, bidyut→বিদ্যুৎ, bishwabiddaloy→বিশ্ববিদ্যালয়). **WYSIWYG contract: what the preview shows is exactly what space commits** — the "surprise" class of complaint is designed out. |
| 6 | **English words mangled into fake Bangla.** Gboard force-corrects; Ridmik shows "not even remotely connected Eng[lish]" suggestions (+379). | A real English word yields its Bangla pronunciation with the English spelling one tap away (courier→কুরিয়ার, tutorial→টিউটোরিয়াল, phone→ফোন) — corpus-validated at 77.6% agreement with how Bangladeshis actually write these (91% on clean standard Banglish; BanglaTLit/Vashantor). |
| 7 | **Suggestion quality.** Even Ridmik's fans concede it (+4,959: "unable to suggest the right words… which G-board does finely"); Borno: "next-word prediction that never ever guesses the right word". | On-device next-word prediction (user bigrams > corpus trigrams) that learns *your* words — without shipping them anywhere. |
| 8 | **Update regressions.** 625 Ridmik negatives; Borno's 2026 redesign burned 3-year loyalists. | 475+ JVM parity pins + per-surface test walls mean a "fix" that changes typing behavior fails CI. Make "no surprise updates" a public promise and keep it. |
| 9 | **Emoji friction.** "emoji isn't open most of the time" (Ridmik +306); "i text joker… no [joker] emoji [suggested]" (Desh). | Emoji searchable **in Bengali**, two-row WhatsApp-style panel, ~90 everyday Bengali phrases tab. |
| 10 | **Voice typing failures** — Ridmik's most-thumbed review ever (+12,287) is a voice failure; Gboard's Bengali voice deletes its own output. | On-device OS recognizer with the S137 session model (one session per utterance, reset-aware carry) — built specifically against the failure Gboard users describe. Bengali দাঁড়ি/comma handling included. |

**Who we take users from, in order:** (1) Ridmik users mid-rage over ads/battery/updates — largest pool, actively shopping (52 mention Gboard, 68 mention Avro inside Ridmik negatives); (2) Gboard Bengali users angry at force-correction; (3) Borno users (already self-selected for "Avro-but-better", currently being burned by the 2026 redesign); (4) Desh Bangla users asking for phonetic typing.

## 2. Where Banglu LOSES today — and honest messaging around it

| Weakness | Reality | How to message it (never deny it) |
|---|---|---|
| Zero reviews, zero brand trust | Ridmik = 191M installs, 11 years, "first software I install". | Don't fight trust with claims; fight it with **verifiability**: "Don't trust us — turn off Wi-Fi and mobile data. Banglu types exactly the same. Try that with your current keyboard." Privacy policy in plain Bangla. |
| 143MB dictionary download on first run | Big vs Play norms. | Say it before they feel it: "বড় অভিধান, একবারই নামে" (*"A big dictionary — downloads once"*) — the size IS the offline promise: the whole brain lives on the phone. Never hide it; hidden size is a 1★ generator ("almost 1 gb of rom" — Ridmik +32). |
| Phonetic-only; no fixed jatiyo/probhat layout | Real demand exists ("please add Jatio (জাতীয়) layout", +456 on Desh). | Position as focus, not absence: "বাংলু এক কাজেই সেরা — ফোনেটিক" and keep a public roadmap item. Do not promise a date. |
| Android-only | No iOS. | Say "Android now" and nothing else; iOS users aren't the launch audience. |
| Solo developer | Perceived bus-factor. | Flip it: every review gets answered by the person who wrote the engine. In this corpus, unanswered reviews are the norm (Ridmik's +12,287 voice complaint sat for years; Swarachakra: "Developer died i guess"). One dev who replies beats a company that doesn't. |
| No swipe/glide typing | Gboard's most-praised feature in the Bengali subset. | Don't mention in ads; answer honestly when asked; backlog item. |

Rules: no fake or incentivized reviews (Play policy + it's the exact behavior this market already resents), no competitor names in Play listing metadata (policy), never claim a benchmark we haven't published — the audience that punished Ridmik's untested updates will fact-check us.

## 3. The three sharpest positioning messages (Bengali + English)

1. **Privacy/offline (lead message — answers ads + trackers + battery at once):**
   - BN: **"আপনার লেখা আপনার ফোনেই থাকে। বাংলু ১০০% অফলাইন — কোনো বিজ্ঞাপন নেই, কোনো ট্র্যাকার নেই, কোনো অ্যাকাউন্ট লাগে না। বিশ্বাস না হলে ইন্টারনেট বন্ধ করে টাইপ করে দেখুন।"**
   - EN: "What you type stays on your phone. 100% offline — no ads, no trackers, no account. Don't believe it? Turn off the internet and keep typing."
2. **Chat register (the differentiator no competitor has):**
   - BN: **"যেভাবে ভাবেন, সেভাবেই লিখুন — kmon লিখলেই কেমন, hoise লিখলেই হইসে, bishwabiddaloy লিখলেই বিশ্ববিদ্যালয়।"**
   - EN: "Type the way you already think — kmon becomes কেমন, hoise stays হইসে, and bishwabiddaloy comes out বিশ্ববিদ্যালয়, all from plain lowercase."
3. **Zero surprise (answers মৃত্যু→মৃতয়ু, sub→শুভ, and the update-regression rage):**
   - BN: **"প্রিভিউতে যা দেখবেন, স্পেস চাপলে ঠিক সেটাই লেখা হবে। কোনো চমক নেই, কোনো জোর-করা 'শুদ্ধি' নেই — ২ জিবি র‍্যামের ফোনেও।"**
   - EN: "What the preview shows is exactly what space commits. No surprises, no forced 'corrections' — even on a 2GB phone."

## 4. Channel plan

**Play Store listing (ASO).** Title: "বাংলু — Bangla Keyboard (Offline)". Keyword surface for title/short/long description: *bangla keyboard, বাংলা কিবোর্ড, phonetic bangla, avro style typing, offline bangla keyboard, banglish to bangla, english to bangla typing, bangla voice typing, no ads keyboard, বাংলা টাইপিং*. (Generic "avro style/phonetic" is fair; the brand names Ridmik/Gboard must NOT appear in metadata.) Short description = message #1. Screenshots must show: airplane-mode typing, kmon→কেমন live preview, English-word chip (phone→ফোন | phone), Bengali emoji search, dark theme. First screenshot carries "কোনো বিজ্ঞাপন নেই • ১০০% অফলাইন".

**Facebook (where the audience actually is).** BD tech/user groups, Android user groups, university groups, freelancer groups. Format that works: 20-second screen-recordings — (a) typing with data OFF, (b) hard-word race: sbadhinota→স্বাধীনতা first try, (c) "your keyboard shows ads? mine can't — it has no internet." Post as founder, in Bangla, reply to every comment.

**trickbd.com** (our corpus work showed exactly this audience lives there): one honest long-form founder post — "কেন আমি আরেকটা বাংলা কিবোর্ড বানালাম" (*Why I built yet another Bangla keyboard*) — architecture story, no competitor bashing, APK + Play link. The tone of the top Ridmik complaint ("be stable, don't experiment on users") is the tone to write in. Same story cross-posted to r/bangladesh and Bangladeshi dev communities (INFERENCE on specific subreddit receptivity — validate with one post before investing).

**YouTube tech reviewers.** Pitch BD Android channels a review unit angle they can demo on camera: airplane-mode typing + the chat-register race vs their current keyboard. Offer nothing but early access; disclose it's a solo dev. (Specific channel shortlist = INFERENCE, build it by searching "বাংলা কিবোর্ড রিভিউ" and ranking by BD audience.)

**Tester word-of-mouth.** Existing testers get a shareable one-pager + a "founding user" tag in release notes. Ask each for one honest Play review and one group share — honest, not scripted.

## 5. Review acquisition — beating the cold start honestly

1. **In-app review at the right moment:** trigger Play's In-App Review API only after real success signals (e.g., ≥3 days active AND ≥500 words committed AND no crash in session) — never from the typing surface, never twice. The corpus shows rating prompts inside typing flows get punished.
2. **Reply to 100% of reviews in the first 90 days, in the reviewer's language.** In 214k competitor reviews, meaningful developer replies are rare; the few apps that reply visibly convert 1★→updates ("I will install it again when new update comes out"). This is the cheapest trust machine available.
3. **Fix-then-ask loop:** when a reviewer reports a bug that an S-round fixes, reply on the release day naming the version. Converted complainants write the most credible 5★ texts (multiple Ridmik users literally edited ratings upward when fixed).
4. **Never** buy, swap, or incentivize ratings; never prompt on first run (the 1★ "I just installed and it nagged me" class).
5. Seed honesty: launch listing says "নতুন অ্যাপ — সমস্যা পেলে রিভিউতে লিখুন, আমি নিজে ঠিক করি" (*new app — report problems in reviews, I fix them myself*). It sets the contract that reviews get acted on.

## 6. First 30 days — concrete checklist

**Week 0 (pre-launch)**
- [ ] Play listing finalized: title/short/long with §4 keywords; 8 screenshots incl. airplane-mode shot; privacy policy link (already live) in plain language.
- [ ] Data-safety form: "No data collected" for the typing surface — matches the architecture; double-check the :ui process disclosures so the form is exactly true.
- [ ] Crash/ANR monitoring dashboard ready (Play Console vitals); alert thresholds set.
- [ ] One-pager (BN) for testers + reviewers; 3 demo screen-recordings recorded (offline proof, chat-register race, English-word law).
- [ ] In-app review trigger implemented with the §5 gates, OFF by remote-config-style pref until week 2.

**Week 1 (launch)**
- [ ] Staged rollout 20% → watch vitals 48h → 100% (the update-regression graveyard in the analysis is the reason).
- [ ] Founder post on trickbd + 3 Facebook groups (BN), same day as Play availability.
- [ ] Personal message to every existing tester: review + one share ask.
- [ ] Reply SLA starts: every review answered <24h.

**Week 2**
- [ ] First S-round from field reports shipped; release notes name the fixed reviews' bugs.
- [ ] Enable in-app review prompt.
- [ ] YouTube outreach wave (5 channels, demo-angle pitch).

**Week 3**
- [ ] Publish "the offline proof" short video as the pinned asset on all channels.
- [ ] Second Facebook wave targeting the live 2026 pains: battery drain and emoji-panel ads (screenshots of our permission screen vs "a typical keyboard's" — generic, unnamed).
- [ ] Collect top 10 requested features publicly; publish the roadmap (jatiyo/probhat listed honestly as "researching").

**Week 4**
- [ ] Retrospective against targets: ≥1,000 installs, ≥25 reviews, rating ≥4.5, crash-free ≥99.5%, D7 retention baseline recorded. (Targets are working assumptions, not benchmarks — recalibrate with real data.)
- [ ] Publish "first month, honestly" post (installs, bugs fixed, what broke) — the anti-Ridmik-update-culture statement, in public.
- [ ] Decide wave-2 channels from what converted (UTM-tagged links where possible; Play referrer otherwise).

---
*Every competitor quote above is traceable to `market-research/raw/*.csv`; counts and sampling caveats are in `competitor-review-analysis.md` §5. Bengali translations are ours.*
