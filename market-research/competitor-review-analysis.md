# Competitor Review Analysis — Bengali Keyboards on Google Play

**Prepared for:** Banglu (বাংলু) launch positioning
**Data collected:** 2026-08-30, via `google-play-scraper` (Python), country=`bd`, languages `bn` + `en`, sorts NEWEST + MOST_RELEVANT, paged until the continuation token was exhausted (exceptions noted), deduplicated by reviewId.
**Raw data:** `market-research/raw/<package>.csv` (columns: score, at, content, thumbsUp, replyContent) + `raw/app_metadata.json` (install counts, histograms, per-combo fetch log).
**Total unique reviews collected: 214,894** across 8 apps.

---

## 1. The competitive field

| App | Package | Real installs | Play score | Total ratings | 1–2★ share (Play histogram) | Reviews fetched |
|---|---|---:|---:|---:|---:|---:|
| **Ridmik Keyboard** (market leader) | ridmik.keyboard | 191,067,784 | 4.35 | 547,379 | **13.4%** | **113,500** |
| Desh Bangla Keyboard | com.bangla.keyboard.for.android | 30,071,761 | 4.48 | 139,387 | 9.8% | 40,629 |
| Ridmik Classic Keyboard | ridmik.keyboard.classic | 26,566,902 | 4.13 | 56,050 | 18.2% | 12,402 |
| Swarachakra Bangla (IIT) | iit.android.swarachakraBengali | 2,092,113 | 4.12 | 21,209 | 17.9% | 7,647 |
| Bijoy Keyboard | com.bijoyapp.androidkeyboard | 1,324,429 | 4.35 | 948 | 13.8% | 268 |
| Bijoy Android Keyboard | com.bijoy.androidkeyboard | 1,031,395 | 3.93 | 4,246 | **23.5%** | 2,429 |
| Borno: Bangla Keyboard | com.codepotro.borno.keyboard | 198,429 | 4.01 | 1,672 | 19.0% | 987 |
| Gboard (Bengali-relevant subset) | com.google.android.inputmethod.latin | 13.6B global | 4.42 | 18,801,264 | 11.4% (global) | 37,032 fetched → **309** Bengali-relevant |

Honest fetch notes:
- **Ridmik:** bn feeds exhausted at 2,597 (NEWEST) / 2,587 (MOST_RELEVANT); en feeds exhausted at 110,903 / 88,530. 113,500 unique — this is essentially every text review the Play API still serves (most of the 547k raters left stars without text).
- **Gboard:** bn feeds exhausted at 9,644 each; en feeds deliberately capped at 15,000 per sort (18.8M global ratings makes exhaustion meaningless). The 37,032 fetched were then filtered to the **309** reviews mentioning bangla/bengali/বাংলা/avro/অভ্র/ridmik — only that subset is analyzed.
- **Mayabi Keyboard: could not be collected.** `com.mayabi.mayabikeyboard` and three alternate IDs return 404 and Play search no longer surfaces it — the app appears delisted from the BD Play Store as of 2026-08-30. No data is presented for it (nothing fabricated).
- All other apps: every lang/sort combo ran to token exhaustion.

---

## 2. Complaint taxonomy (counts are 1–2★ reviews matching the bucket; keyword-matched floors, buckets overlap)

Ridmik negatives fetched: **14,273** 1–2★ reviews. Bucket counts below are from those negatives (count / mentions across all scores).

| Complaint cluster | Ridmik | Ridmik Classic | Desh Bangla | Bijoy Android | Borno | Swarachakra | Gboard-bn |
|---|---:|---:|---:|---:|---:|---:|---:|
| Lag / typing latency | **715** / 1,446 | 79 | 28 | 42 | 9 | **55** | 7 |
| Crashes / battery | **686** / 1,173 | 39 | 21 | 27 | 9 | 12 | 4 |
| Update regressions | **625** / 1,096 | 18 | 6 | 14 | 7 | 1 | 0 |
| Emoji / stickers | 513 / 1,869 | 58 | 16 | 11 | 5 | 6 | 0 |
| Voice typing | 340 / 729 | 66 | 33 | 3 | 9 | 1 | **18** |
| Layout issues | 262 / 756 | 17 | 16 | 48 | 4 | 0 | 6 |
| Phonetic accuracy | 248 / 782 | 11 | 12 | **89** | 5 | 4 | 8 |
| Ads | 197 / 310 | 2 | 31 | 3 | 1 | 0 | 0 |
| Suggestion quality | 103 / 390 | 11 | 14 | 21 | 5 | 2 | 6 |
| Privacy / permissions | 48 / 72 | 5 | 5 | **97** | 2 | 1 | 0 |
| App size / RAM | 40 / 100 | 0 | 0 | 10 | 0 | 0 | 2 |
| Login / account | 13 | 2 | 3 | 2 | 0 | 0 | 0 |

### 2.1 Lag / typing latency — Ridmik's #1 complaint by volume
> [1★, +813 thumbs, 2021] "The keyboard don't work smoothly. It makes slow when typing is running. And sometimes space button don't work." — Ridmik
>
> [1★, +544, **2025-12-31**] "Recently it has started lagging a lot. The keyboard is too large at the top, so I can't see what I'm typing… the suggestion box is missing." — Ridmik
>
> [1★, +1,039, **2026-01-19**] "the keyboard gets stuck to the screen when using facebook youtube… now recently … the keyboard is half visible in some devices" — Ridmik (top-voted 2026 negative)
>
> [2★, +281, 2019] "Touch response is very very slow in this keyboard." — Ridmik Classic
>
> [1★, +493, 2019] "every 10/5 minut after that hanging.. thats why its very annoying" — Swarachakra (its hang-until-reboot bug is that app's defining complaint)
>
> [1★, +76, **2026-02-22**] "Touch response not satisfied, because typing timing slow" — Desh Bangla

### 2.2 Ads — the single most-thumbed negative in the entire dataset
> [2★, **+11,064**, 2022] "This was genuinely a great keyboard. But now it shows ads. And the ads can't be turned off… I think its time to move on to a new keyboard" — Ridmik
>
> [2★, +213, 2023] "they added ads which were fine. But now they went too far. Now they auto click the ads. Whenever the keyboard is in focus… it just drags you to your browser." — Ridmik
>
> [1★, +153, 2022] "nowadays its shows add when opening recent emoji section… the ads take half of the keyboard recent section… For this reason i am unistallin[g]" — Ridmik
>
> [1★, +26, **2026-05-21**] "The emoji section of the keyboard… are suddenly running adds today. So now I uninstalled it cz of privacy Safety… Even i lost my important clipboard" — Ridmik
>
> [1★, +200, 2023] "Ads are making this app unusable. When im gonna type something, then promoted gambling thirdclass apps are coming." — Desh Bangla

### 2.3 Privacy / permission fears
> [1★, +50, **2025-10-23**] "This app is a privacy nightmare. DuckDuckGo blocked at least 400 tracking attempts within a hour from Google, Facebook, Smaato, InMobi through Ridmik Keyboard which is highest among the apps on my phone." — Ridmik
>
> [1★, +1,339, 2019] "why it is saying that it can record everything what I type... this is insane...!!" — Ridmik Classic
>
> [1★, +33, 2019] "Why this software (only for keyboard use) need everythings permission of my phone??!!" — Ridmik
>
> [1★, +93, 2023] "While installing it, it ask for the permission to keep for personal data, password and credit card information!" — Bijoy Android (97 of its 99 privacy mentions are negative; a Jan-2023 wave — see sampling limits)
>
> [1★, +105, 2021] "An app should never be able to see passwords, credit card number… So UNINSTALLED" — Swarachakra

### 2.4 Word-suggestion quality
> [4★, +4,959, 2021] "…still I'm rating 4 as this keyboard is unable to suggest the right words depending on my typing history and all, which G-board does finely." — Ridmik (even fans concede this)
>
> [1★, +379, 2023] "whenever I type bangla in Notepad, every bangla word comes with a red underline… when i touch the word, it shows few useless / not even remotely connected Eng[lish suggestions]" — Ridmik
>
> [2★, +4, **2026-04-10**] "awful 'next-word prediction' that never ever guesses the right word" — Borno
>
> [2★, +262, 2024] "whenever I try to type a specific word in Bangla, [it messes up]" — Gboard

### 2.5 Phonetic accuracy — which words actually fail
> [1★, +5,359, 2021] "whenever I try to type anything with ে, ো, ি, ু and attached alphabet. **It becomes মৃতয়ু when I wanted to type মৃত্যু**" — Ridmik (conjunct + vowel-sign breakage on the word "death")
>
> [1★, +89, **2026-02-27**] "only on Facebook. Some Bangla letters do not work properly. The akar (া) and okar (ো) are not working, and while 'ব' types correctly, 'ভ' does not appear." — Ridmik
>
> [2★, +10, **2026-02-21**] "Gboard's Bengali Phonetic layout is frustrating because it prioritizes word frequency over exact input. Even with auto-correct off, typing 'sub' for 'সুব' shouldn't force-default to 'শুভ.' **The vowel mapping is also non-standard compared to Avro**" — Gboard
>
> [1★, +306, 2025] Desh Bangla lacks English→Bangla phonetic at all: "the main reason being the lack of a Bangla system, like the R[i]dmi[k] Keyboard, which allows you to write in English but type in Bangla"
>
> [1★, +0, **2026-08-10**] "I'm having issues with chondrobindu in phonetic version… bring back old version" — Borno

### 2.6 Layout issues
> [1★, +1,114, 2025] Galaxy Z Fold5: "fold my device… the keyboard layout size optimization doesn't work" — Ridmik
>
> [1★, +261, 2025] S25 Ultra landscape gaming: "the 'P' key and right-side keys become unresponsive or hidden" — Ridmik
>
> [1★, +456, 2025] "please add 'Jatio (জাতীয়)' layout as from ridmik bangla keyboard" — Desh Bangla (fixed-layout demand exists)
>
> [1★, +124, 2023] "must need 2 shift button… WE do not like to type in a single hand" — Bijoy Android

### 2.7 Voice typing
> [1★, **+12,287**, 2023 — the most-thumbed Ridmik review of any kind] "when I go to voice input… [it fails]" (same text re-posted 2024 at +11,229)
>
> [2★, +2,125, 2020] "সর্বশেষ আপডেটেড ভার্সনে ভয়েস টাইপিং-এ সমস্যা হচ্ছে। একেবারেই কাজ করছেনা।" (*"Voice typing is broken in the latest updated version. Not working at all."*) — Ridmik Classic
>
> [2★, +4,702, 2022 — Desh Bangla's top negative] "the voice typing button is so loud. It hurts the brain… it gives a really small amount of time [to speak]"
>
> [2★, +10, 2024] "Whenever I use the voice typing feature in the Bengali language, the typed text automatically deletes" — Gboard
>
> [1★, +3, 2024] "Microphone speech to Bangla text not working anymore!" — Borno

### 2.8 Emoji
> [1★, +306, 2024] "emoji isn't open most of the time… I could use alternative like (classic one and also gboard)" — Ridmik
>
> [1★, +821, 2020] "Emoji doesn't work most of the time. It needs to reset the keyboard" — Ridmik Classic
>
> [1★, +133, 2024] "if i text joker than others keyboard recommended joker emoji but here no emoji" — Desh Bangla (emoji-from-word suggestions expected)

### 2.9 Crashes / battery — the 2026 Ridmik story
Of 34 Ridmik reviews mentioning battery in 2026, 25 are 1–2★ (mean score 1.74):
> [1★, +45, **2026-01-26**] "battery eating monster! i have been using it for a decade and recently i had to switch."
>
> [1★, +41, **2026-05-05**] "it is consuming 30%+ battery in less than an hour in background"
>
> [1★, +733, 2025-08] "this app is being used so much in the background that it is wasting about 39% of my phone's battery"
>
> [2★, +22, **2026-02-13**] "It is always running, even when I turn off background usage… Gboard only runs when I use it but Ridmik keeps running all the time."
>
> [1★, +0, **2026-05-06**] "ভয়ানক ব্যাটারি খায়। নট রিকমেন্ডেড।" (*"Eats battery horribly. Not recommended."*) — Borno

### 2.10 Update regressions ("new update ruined it") — 625 Ridmik negatives
> [1★, +2,876, 2019] "the older one is better than updated one… please update your keyboard once again without any adds business."
>
> [1★, +2,707, 2019] "রিদমিক অনেক পছন্দের অ্যাপ। কিন্তু খুব খারাপ lage jokhon dekhi valomoto testing na korei er kono update die deoa hoy." (*"Ridmik is a favourite app, but it feels terrible to see updates shipped without proper testing — because of this update's fault I couldn't even write this complaint in Bangla. Be stable. Don't experiment…"*)
>
> [2★, +2, **2026-04-01**] "Used Borno for nearly 3 years, never faced any issues… after it's latest update, the keyboard got stuck… overall functionality got worse" — Borno
>
> [1★, +7, **2026-02-24**] "after updating to the latest built… it won't work without Play Store enabled" — Borno
>
> [1★, +23, **2026-06-28**] "Latest update is bad. The button to change between languages keeps changing it back to default (samsung) keyboard." — Ridmik

### 2.11 App size / RAM
> [1★, +32, 2019] "it is taking a lot of storage almost 1 gb of rom" — Ridmik
>
> [1★, +27, 2022] "Very heavy app. Too much ram hungry… slow down my device too much." — Ridmik
>
> [2★, +85, 2021] "It uses too much memory. Otherwise would have given it a 5 star review." — Ridmik

---

## 3. Praise taxonomy — what users love (the bar Banglu must clear)

Positive-bucket counts (4–5★ matching): Ridmik best/love 12,772 · easy 1,428 · "helpful in life" 1,051 · Avro/phonetic praise 375 · fast 353 · suggestions 162.

**Ridmik is loved for:**
- **Avro-style phonetic typing itself** — [5★, +3,240, 2020] "It's very easy to write as it supports phonetic bangla writing. For writing bangla it's the best app available"; [5★, +3,168, 2019] "It's phonetic Bangla typing is its' best feature… Huge Bangla and English word Bank."
- **Longevity & trust** — [5★, +18,987, 2022] "Have been using since 2015!… Cannot think of alternative"; [5★, +4,300, 2023] "I've been using Ridmik/Avro for a few years… This is the first software I install on my device."
- **Utility touches** — [5★, +8,267, 2025] "The clipboard functionality is highly practical, and using volume keys for cursor movement is a brilliant addition."
- Even the praise carries the gap: [4★, +8,826, 2019] users *liked* shorthand auto-conversions ("বরব became আসছি… actually kinda helpful") and were angry when an update removed them — chat-register conversion is a felt need.

**Ridmik Classic:** privacy is praised explicitly — [4★, +5,142, 2020] "This app Respect user's PRIVACY-RIGHTS. THANKS to devs." (Privacy sells in this market.)

**Desh Bangla:** accuracy praise — [5★, +2,142, 2025] "good for people who… aren't very fluent at using the vernacular script; … the bangla word choices for the typed english inputs are accurate."

**Borno:** the anti-Ridmik positioning already works at small scale — [5★, +27, 2021] "I have switched to this keyboard from Ridmik… better customization… also available in cross platforms"; [5★, +5, 2025] "Best option if you prefer Avro in Android. Simple, Lite, Classy"; [5★, +10, 2025] "**No ads**, Fantastic themes, Word suggestions are great."

**Gboard:** swipe typing + learning — [5★, +307, 2023] "even manages to learn and predict the Benglish/Henglish (Bengali/Hindi words written in English)."

**Swarachakra:** simplicity for script-first users, but users beg for basics — [5★, +1,127, 2020] "update the dictionary memory system so that typed words will be saved" (no learning!), and it is effectively abandoned: [1★, +339, 2024] "Developer died i guess... cause no update since last 7 years."

---

## 4. Switching triggers — why users actually abandon a Bengali keyboard (the marketing gold)

Ranked by evidence weight (thumbs-up mass × recency × explicit "I'm leaving" language):

1. **Ads invaded the typing surface.** The most-thumbed negative in 214k reviews (+11,064) is a Ridmik user leaving over ads. 2023: ads that *auto-click*. 2026: ads inside the emoji panel that cost a user their clipboard. Ridmik's own reply history shows no rollback. 197 Ridmik 1–2★ ad complaints, 63.5% of ALL its ad mentions are negative — the highest negativity ratio of any bucket.
2. **An update broke what worked.** 625 Ridmik negatives; the pattern is always a loyal user ("using for 4 years / a decade / since 2015") flipping to 1★ overnight. This cuts both ways: Borno's 2026 redesign generated the same class ("Used Borno for nearly 3 years… functionality got worse"). Stability is a retention feature, and every competitor keeps re-triggering this.
3. **Battery drain / always-running background behavior (2026-current).** 25 of 34 battery mentions on Ridmik in 2026 are 1–2★; "battery eating monster", "30%+ in an hour", explicitly benchmarked against Gboard's process behavior. This is live RIGHT NOW and unanswered.
4. **Privacy panic.** "It can record everything you type" warnings, the DuckDuckGo 400-trackers-per-hour report (2025), Bijoy's credit-card-permission wave, Swarachakra's password fear. Users in this market actively read permission prompts and punish keyboards for them.
5. **The conversion engine betrayed the typist.** মৃত্যু→মৃতয়ু (Ridmik), sub→শুভ force-correction and "vowel mapping non-standard compared to Avro" (Gboard), no phonetic mode at all (Desh Bangla, Bijoy). When the phonetic engine surprises, users churn to whichever keyboard "types what I mean."
6. **Voice typing that fails silently.** The single most-thumbed Ridmik review ever (+12,287) is a voice-typing failure; Gboard's Bengali voice deletes text after typing it; Borno's mic died after an update; Desh Bangla's mic beep "hurts the brain."

Competitor-name mentions inside Ridmik's 14,273 negatives: Gboard 52, Avro 68, Borno 13 — dissatisfied Ridmik users publicly shop for alternatives in their reviews.

---

## 5. Sampling limits (read before quoting numbers)

- **Text reviews ≠ all raters.** Ridmik has 547k ratings but the API serves ~113.5k text reviews; silent raters are invisible. Histogram percentages in §1 come from Play's own all-rater histogram, not our sample.
- **Keyword buckets are floors.** Substring matching over noisy Banglish/Bengali misses spelling variants (e.g., "lyag", "ল্যাগে") and mis-buckets some sarcasm; treat counts as relative magnitudes, not censuses. Buckets overlap (one review can hit three buckets).
- **Age skew.** The fetched Ridmik corpus peaks in 2018–2022 (2025: 6,640; 2026: 2,806 so far). Old complaints may be fixed; wherever a claim matters for 2026 positioning, we re-verified against 2025–2026 reviews (battery, ads-in-emoji, stuck keyboard, Facebook akar bug are all current).
- **Bijoy Android's January-2023 spike** (dates cluster 2023-01-16→18, near-identical texts) is a review-bombing wave tied to a public controversy; its privacy/quality counts are inflated by campaign behavior. Direction is still informative (the market's privacy sensitivity), magnitude is not.
- **Gboard subset is tiny (309)** — directional only for the Bengali experience; says nothing about Gboard overall.
- **Mayabi Keyboard is absent** (delisted; 404 on every known package ID). Historic complaints about it could not be collected and none are cited.
- Reviews are self-selected complainers/superfans; mean scores of fetched samples (Ridmik 4.35) happen to match Play's official score, which is a good representativeness sign for Ridmik at least.
