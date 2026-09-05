# Engine real-world coverage study — 2026-09-05 (S188)

**Ask (user):** "Bangladesh newspapers (Prothom Alo, Jugantor, others), Bangla golpo /
uponnas / literature, Bengali science, names of people, objects, districts, villages —
test the engine's power, find every word it cannot handle or produces garbage for, the
failure patterns, check them on real devices, remember one word is typed many ways, and
propose fixes that do not disturb the engine people are testing right now."

**Engine under test:** the shared engine at commit `24c5d8c` (Android 1.5.120, db 3.9.7),
JVM full store on `dictionary.sqlite`. **No engine change was made in this round.**

## 0. Summary

- For words the dictionary knows — the overwhelming majority of what people read in
  newspapers, Wikipedia and books — the engine commits the right word 94–98% of the time
  and shows it on the strip 99.6–99.9% of the time, in every register tested.
- What it cannot handle is vocabulary it has never seen: 1% of frequent news words, but
  8% of place names, 17% of everyday object and food names, 22% of union and village
  names and 25% of science terms. Those fall to the rule transliteration, which gets a
  name's ট/ড/ণ/য/ঁ/ী wrong often enough to read as garbage, or to the compound splitter,
  which cuts one typed word into two.
- Users type one word many ways: the sibilant fold (`pulis`, `des`, `asa`) is the single
  largest in-dictionary miss by use, and the omitted chandrabindu (`tara` for তাঁরা) the
  single most frequent word — in both, the wanted spelling is missing from the strip.
- All 76 sampled failures reproduce on the S22 exactly as on the JVM.
- The fix path is strip-first (twin slots, joined-form chip), then data (a proper-noun and
  science lexicon from the harvest, compiled below every common word), then one narrow
  guard on the rescue layers. No change to the commit of known words is proposed.

## 1. Corpora (all real text, harvested 2026-09-05 unless noted)

| category | source | tokens | unique words |
|---|---|---|---|
| news, fresh | Prothom Alo, 3,576 articles via the public story API (sitemap-daily), `scratchpad/realworld/fetch_news.py` | 1,603,287 | 80,650 |
| news, July | Prothom Alo + BBC Bangla harvest of 2026-07-06 (`dictionary-compiler/data/corpus-2026-07/fresh_news_counts_2026-07-06.tsv`) | 790,444 | 55,633 |
| literature, rendered | bn.wikisource: গল্পগুচ্ছ, দেবদাস, শ্রীকান্ত, পথের পাঁচালী, চোখের বালি, আনন্দমঠ, অগ্নিবীণা, সঞ্চিতা, গোরা, ঘরে-বাইরে, কপালকুণ্ডলা, দুর্গেশনন্দিনী, পল্লীসমাজ, পদ্মানদীর মাঝি, লালসালু, হাজার বছর ধরে, চাঁদের পাহাড়, আরণ্যক, শেষের কবিতা, গৃহদাহ, চরিত্রহীন, বিষবৃক্ষ, রাজসিংহ, যোগাযোগ, নৌকাডুবি, রূপসী বাংলা, বনলতা সেন, ছায়ানট, বিষের বাঁশী, মৃত্যুক্ষুধা (rendered pages, `fetch_wiki2.py`) | 635,161 | 66,658 |
| literature, Wikisource dump | `bnwikisource_counts.tsv` (July harvest of the whole Bengali Wikisource) | 4,946,764 | 430,062 |
| science | bn.wikipedia categories পদার্থবিজ্ঞান, রসায়ন, জীববিজ্ঞান, গণিত, চিকিৎসাবিজ্ঞান, জ্যোতির্বিজ্ঞান, কম্পিউটার বিজ্ঞান, মানবদেহ, উদ্ভিদবিজ্ঞান, প্রাণিবিজ্ঞান, প্রযুক্তি (500 full pages + titles) | 350,979 | 35,896 |
| people | bn.wikipedia categories বাংলাদেশী ব্যক্তি/রাজনীতিবিদ/ক্রিকেটার/লেখক/কবি/অভিনেতা/অভিনেত্রী, একুশে পদক বিজয়ী (titles + 500 pages) | 238,647 | 27,680 |
| places | bn.wikipedia জেলা, উপজেলা (per-district subcategories), গ্রাম, নদী, শহর (titles + 700 pages) | 467,741 | 43,520 |
| unions & villages | bn.wikipedia বাংলাদেশের ইউনিয়ন walked three levels: 2,347 union names + village lists from 300 union pages | 34,935 | 6,920 |
| objects | bn.wikipedia খাদ্য, বাংলাদেশী রন্ধনশৈলী, ফল, সবজি, পোশাক, যানবাহন, আসবাবপত্র, রান্নার সরঞ্জাম, বাদ্যযন্ত্র, পাখি, মাছ, উদ্ভিদ, প্রাণী, সরঞ্জাম | 268,445 | 33,704 |
| Wikipedia general | `bnwiki_counts.tsv` (July harvest of Bengali Wikipedia) | 5,915,467 | 289,475 |

Total: about 15.3 million tokens. **Blocked sources (HTTP 403 to automated fetch):**
Jugantor, Samakal, Kaler Kantho, bdnews24, Dhaka Post, Jaijaidin, Anandabazar. Their
registers are covered by Prothom Alo (same news Bangla) but their proper-noun spellings
are not separately verified.

## 2. Method

`shared/src/jvmTest/.../S188RealWorldStudyJvm.kt` (opt-in, `S188_STUDY=1`). For every
unique word (nukta-folded, Bengali letters only, 2–20 chars; the 15,000 most frequent per
corpus) the harness derives the roman a knowledgeable typist would type
(`ReverseTransliterator.reverseWord`, chandrabindu omitted — people type `chad` for চাঁদ)
and a **typist fold** of it (sh→s, chh→ch, ph→f, bh→v, z→j, ee→i, oo→u — the S181
`keyReadingDistance` folds). Each key goes through `convertWord` (the commit) and
`getSuggestions(6)` (the strip). A word **passes on commit** when the commit is the word,
**on strip** when the strip carries it. Misses are tagged by the shape of the difference
(vowel length, ন/ণ, sibilant, য/জ, র/ড়, dental/retroflex, chandrabindu, final ো, conjunct
shape, split into two words, inflected, vowel-initial, long compound, out-of-vocabulary).

A first pass with a `y→j` fold (hoj for হয়) was discarded: nobody types that, and it
manufactured the largest "miss" class. Lesson kept in the harness comment.

## 3. Results

_Status: complete — nine corpora, 135,000 words, two typing variants each, plus the
76-key device pass. Per-corpus summaries, the pattern totals and the 400 most frequent
misses per corpus are archived in `docs/audits/s188-realworld-study/`._

### 3.1 Fresh news (Prothom Alo, 15,000 most frequent words)

| variant / population | words | commit-exact | on the strip |
|---|---|---|---|
| canonical roman, all | 14,617 | 97.0% (97.8% weighted by frequency) | 99.3% (99.8%) |
| typist fold, all | 3,758 | 88.7% (91.1%) | 94.1% (95.0%) |
| in-dictionary words, either variant | 14,431 | 97.8% (98.0%) | 99.9% (99.8%) |
| out-of-vocabulary words | 186 | 35.5% | 52.2% |

Headline: **for words the dictionary knows, the engine is at ceiling** — under one word in
a thousand is missing from the strip. Everything below is about the remaining classes.

### 3.2 Per-corpus tables (canonical roman unless stated; "weighted" = by occurrence)

| corpus | in-dictionary commit / strip | out-of-vocabulary words | OOV commit / strip | typist fold commit / strip |
|---|---|---|---|---|
| fresh news | 97.8% / 99.9% | 186 of 14,617 (1.3%) | 35.5% / 52.2% | 88.7% / 94.1% |
| places (districts, upazilas, villages, rivers, towns) | 96.7% / 99.9% | 1,135 of 13,864 (8.2%) | 22.8% / 42.6% | 74.2% / 88.1% |
| unions and villages (2,347 union names + village lists) | 96.0% / 99.6% | 1,401 of 6,248 (22.4%) | 15.8% / 33.9% | 60.8% / 71.2% |
| people (politicians, cricketers, writers, actors, award winners) | 97.1% / 99.9% | 989 of 14,542 (6.8%) | 24.4% / 38.6% | 77.7% / 90.0% |
| science (physics, chemistry, biology, maths, medicine, astronomy, computing) | 96.6% / 99.9% | 3,650 of 14,372 (25.4%) | 26.1% / 33.9% | 69.6% / 76.5% |
| objects (food, cuisine, fruit, vegetables, clothing, vehicles, furniture, tools, birds, fish, plants, animals) | 95.5% / 99.6% | 2,486 of 14,571 (17.1%) | 29.2% / 40.1% | 74.1% / 83.6% |
| literature, rendered Wikisource (30 novels and poetry collections) | 93.9% / 99.6% | 2,097 of 14,736 (14.2%) | 21.8% / 34.5% | 71.4% / 84.3% |
| literature, whole Wikisource dump (July, 4.8M tokens) | 94.5% / 99.9% | 1,535 of 14,300 (10.7%) | 23.3% / 37.1% | 72.0% / 85.7% |
| Wikipedia general (July dump, 5.8M tokens) | 97.6% / 99.9% | 381 of 14,177 (2.7%) | 33.6% / 47.5% | 85.4% / 94.9% |

### 3.3 Cross-corpus totals (135,000 words studied, 9 corpora)

- **Words the dictionary knows: 93.9–97.8% commit-exact, 99.6–99.9% on the strip in
  every corpus.** The remaining in-dictionary misses are twins (chandrabindu, sibilant,
  vowel-initial, long vowel) that the strip does not carry — not wrong conversions.
- **Out-of-vocabulary share by register:** news 1.3%, Wikipedia general 2.7%, people
  6.8%, places 8.2%, Wikisource 10.7%, rendered literature 14.2%, objects 17.1%, unions
  and villages 22.4%, science 25.4%. Unknown words commit exactly 16–36% of the time and
  reach the strip 34–52% of the time.
- **Miss classes by occurrence, all corpora** (`patterns.tsv`, a word may carry several
  tags): out-of-vocabulary 269,946 · split 87,010 · sibilant 79,749 · vowel length
  67,878 · inflected 58,118 · conjunct shape 55,064 · vowel-initial 46,272 · long
  compound 17,923 · dental/retroflex 10,121 · chandrabindu 5,448 · ন/ণ 5,007 · ৎ 3,660 ·
  য/জ 3,645 · other ranking 2,942.

Reading: two thirds of everything the engine gets wrong is a word it has never seen;
of the rest, the three biggest shapes (split, sibilant, vowel length) are all
strip-fixable without touching a commit.

Places miss classes by occurrence: oov 5,700 · sibilant 3,066 · split 1,797 ·
vowel-initial 1,615 · vowel length 1,066 · inflected 986 · conjunct shape 870.

## 4. Failure patterns (confirmed on the S22, Android 1.5.120, 2026-09-05)

Each class was re-typed on the phone (`scratchpad/s188_device.py`, 76 keys); the phone
reproduced the JVM commit and strip on every key checked. Device columns below are
verbatim.

### P1 — Chandrabindu the typist omits (in-dictionary, high frequency)

People type `tara`, `ba`, `jader`, `kacharii`; the engine commits the ঁ-less twin and
**the strip does not carry the ঁ form**.

| key | expected | device commit | device strip |
|---|---|---|---|
| tara | তাঁরা (1,878 in fresh news) | তারা | তারা, টারা, টায়রা, তারায়, তাড়া |
| ba | বাঁ | বা | বা, বাড়, বায়, বড়া, বয়া |
| jader | যাঁদের | যাদের | যাদের, জাতের, জাহের |
| kacharii | কাঁচারী | কাচারী | কাচারী, কাছারী, কাছারি |

Weight in fresh news: 2,168 occurrences over 8 words — small in words, large in use
(তাঁরা alone is the single most frequent miss in the corpus).

### P2 — Sibilant fold: `s` typed for শ/ষ (in-dictionary, very high frequency)

Chat typists write `pulis`, `des`, `bes`, `asa`, `sekh`, `las`, `sah`, `sona`, `notis`,
`chas`, `pas`, `rosid`. The engine reads `s` as স by rule and commits a স-spelling that is
often a real (rarer) word — and **the শ/ষ word is missing from the strip** in almost every
case (দেশ appears at slot 3 for `des`; পুলিশ, বেশ, আশা, শেখ, লাশ, শাহ, শোনা, নোটিশ,
চাষ, পাশ, রশিদ do not appear at all).

| key | expected | device commit | strip has expected? |
|---|---|---|---|
| pulis | পুলিশ | পুলিস | no (পুলিস, পুলিশি, পুলিন, পুলিসের) |
| asa | আশা | আসা | no |
| sekh | শেখ | সেখ | no |
| notis | নোটিশ | নোটিস | no |
| chas | চাষ | চাস | no |
| sahed | সাহেদ | শাহেদ | no (the reverse case: a name spelled with স) |
| des | দেশ | ডেস (phone) / দেস (JVM) | slot 3 |

Weight in fresh news: 8,939 occurrences over 107 words — the largest class by use.

### P3 — Compound splitting of one typed word (names and coined compounds)

The compound splitter turns an unknown single key into two words. Good for
`bujteparcina`; wrong for proper nouns and productive compounds, and **the joined form is
never on the strip**, so the user cannot get the word they typed.

| key | expected | device commit |
|---|---|---|
| joyoshongkor | জয়শঙ্কর | জয় শঙ্কর |
| kathomandubhittik | কাঠমান্ডুভিত্তিক | কাঠমান্ডু ভিত্তিক |
| chhadobhittik | ছাদভিত্তিক | ছাদ ভিত্তিক |
| rosochithi | রসচিঠি | রস চিঠি |
| jonoupatt | জনউপাত্ত | জন উপাত্ত |
| grrihogonona | গৃহগণনা | গৃহ গণনা |

### P4 — Proper nouns at the rule floor: retroflex, ণ, য, chandrabindu, ী

Names of places and people the dictionary does not know fall to the rule
transliteration, whose defaults (t→ত, d→দ, n→ন, z→জ, i→ি, no ঁ) are wrong for a large
share of Bangladeshi names. The output is a plausible but wrong spelling — the
"garbage" the user meant.

| key | expected | device commit | what went wrong |
|---|---|---|---|
| ronohat | রণহাট | রনহাত | ণ→ন, ট→ত |
| plaisotosin | প্লাইসটোসিন | প্লাইসতসিন | ট→ত in a loanword |
| bihaidohor | বিহাইডহর | বিহাইদহর | ড→দ |
| zodupur | যদুপুর | জদুপুর | word-initial য typed z |
| tunggiipara | টুংগীপাড়া | টুঙ্গিপাড়া | engine normalises to the standard spelling (arguably right) |
| madaripur | মাদারিপুর | মাদারীপুর | same: the standard spelling wins |
| raniirobondor | রাণীরবন্দর | রানীরবন্দর | ণ→ন |
| kheora | খেওড়া | খেয়োরা | ও+ড় read as য়ো+র |

Out-of-vocabulary rate: news 186/15,000 words (1.2%), places 1,135/15,000 (7.6%; 22.9%
commit-exact, 42.6% on strip). Note that a visible share of the place "misses" are
Wikipedia's own variant spellings that the engine normalises (টুংগীপাড়া → টুঙ্গিপাড়া,
মাদারিপুর → মাদারীপুর, সংষ্কৃতি → সংস্কৃতি, প্রাথিমক → প্রাথমিক): those are the engine being
right.

### P5 — Over-reach of the typo / rescue layers on unknown keys (true garbage)

A small class, but the ugliest: the key is clean, no dictionary word is within one edit,
and a rescue layer still produces an unrelated word.

| key | expected | device commit | strip |
|---|---|---|---|
| khondol | খন্ডল | খোঁদল | খোঁদল, খণ্ডই, খন্দকার |
| arag | আরাগ | আরাগঁ | আরাগঁ, আরাক, আরাগচি |
| daimenositi | ডাইমেনসিটি | দাড়িম এনসিটি | (JVM) |
| netuyark (typist for নেটওয়ার্ক) | নেটওয়ার্ক | নেতাকর্মীকে | (JVM) |

### P6 — Vowel-initial ambiguity `a` → আ vs এ (in-dictionary)

`ai` → এই (আই wanted 91 times in news, the English "I"/"eye"), `ata` → এটা (আতা, the
fruit). The dictionary's preference is the frequent function word; the আ-reading is not
on the strip.

### P7 — Long-vowel and ন/ণ spelling variants (mostly acceptable)

`lila` → লীলা (corpus had লিলা), `krira` → ক্রীড়া, `prachin` → প্রাচীন, `borni` → বর্ণি.
The engine's answer is the standard spelling; the corpus word is the variant. Reported,
not counted as failures.

### P9 — Science and technical vocabulary is a quarter unknown

The science corpus has the highest out-of-vocabulary rate of all (25.4% of its 15,000
most frequent words), split between Greco-Latin loanwords the rule layer spells with the
wrong stop or conjunct (`kripton` → ক্রিপটন for ক্রিপ্টন, `lorentoj` → লরেন্তজ for লরেন্টজ,
`iutekotik` → ইউতেকতিক for ইউটেকটিক, `stronoshiyam` → স্ট্রনসিয়াম) and Sanskrit-built
compounds the splitter breaks (`somosthanik` → সম স্থানিক, `toritochumbokiiy` → তড়িৎ
চুম্বকীয়, `porigononamuulok` → পরিগণনা মূলক, `ghonomatra` → ঘন মাত্রা). A note on method:
the reverse map writes অ্যা as `oya` (`oyasider` for অ্যাসিডের), which no typist writes —
they type `asid`/`acid` — so the অ্যা-initial rows in this corpus overstate the failure;
the loanword and compound rows above do not depend on it. Fix path: F3 with a science
glossary (the harvested titles and page text are the source) and F2 for the compounds.

### P10 — Literature: archaic orthography, character names, and OCR noise

Of the 1,383 out-of-vocabulary literature misses, 128 (2,296 occurrences) are Wikisource
OCR artefacts — a ো split into া + ে in the wrong order (হােসেন, তাে, তােমার, মতাে,
ভালাে) — which no typist produces; they are excluded from the pattern counts. The real
classes: **archaic doubled consonants after reph** (99 words: ধর্ম্ম, সর্ব্বজয়া,
পুনর্ব্বার, সর্ব্বং — the engine gives the modern ধর্ম / সর্বজয়া, which is right for a
modern typist and wrong for someone copying a 19th-century text), **character and
place names split** (197 words: কৃষ্ণ দয়াল, জগৎ সিংহ, হরি মোহিনী, আনন্দ মঠ — P3), and
**period spellings** (বল্লে → বললে, ওস্মান → অসমান, আয়েষা → আয়েশা, মাণিকলাল → মানিকলাল).
Fix path: F2 for the split names; an optional "সাধু বানান" alias tier in F3 for the
doubled-consonant forms (never the default); nothing for OCR noise.

### P8 — Loanwords typed with a long vowel the dictionary spells short

`bhuutta` (ভুট্টা spelled ভূট্টা in the corpus) → ভূতটা: the ূ blocks the dictionary hit and
the compound layer glues ভূত + টা. Same family: `muukhii` → মুখী (right), `puurnomoti` →
পূর্ণমতী (right). Only the ভূট্টা case is a real failure; the fix is F1's vowel-length twin
(a corpus word one long/short vowel away) on the strip.

### Device confirmation

76 keys (`scripts/s188-realworld-study/s188_sample.tsv`) typed on the S22 with Android
1.5.120 through the Bangla layout at 80 ms per key, strip read before Space, commit read
after: **76/76 reproduce the JVM commit** (`s188_device_results.tsv`). The one visible
difference is `des` → ডেস on the phone versus দেস on the JVM, which is the phone's own
learning (the dev phone has typed English "desk"-class words); দেশ sits at strip slot 3 on
both. Additional P5 garbage confirmed on the device: `pakarasta` → পাকারমাথা, `binna` →
বিনয়না, `lyantarnos` → ল্যান্ডমার্কস, `chyapo` → চাপ, `phincarer` → ফিচারের.

## 5. How to fix without disturbing the engine people are testing

Ordering principle: **strip-only additions first** (they never change what Space
commits, so the WYSIWYG contract and every pin stay intact), **data additions second**
(dictionary rows, no logic), **engine-logic changes last and narrowest**. Every item
below is gated by the full six-wall run and the S169c/S181 device lists before it ships.

### F1 — Twin slots on the strip (no commit change) — covers P1, P2, P6

The S151 `homograph_twin` promotion already guarantees a strip slot for ই/য় twins
(hoi → হয়, jai → যায়). Extend the same mechanism with three more twin rules, each
promoting an attested corpus word that differs from the primary by exactly one
substitution class:

| rule | trigger | promoted word must be | example |
|---|---|---|---|
| chandrabindu twin | primary has no ঁ; a corpus word = primary + ঁ at one cluster | frequency ≥ primary's or ≥ 30 | tara → তারা **+ তাঁরা** |
| sibilant twin | key has an `s` read as স; a corpus word = primary with শ/ষ at that slot | frequency > primary's | pulis → পুলিস **+ পুলিশ**; sahed → শাহেদ **+ সাহেদ** |
| vowel-initial twin | key starts with `a` read as এ; the আ reading is a corpus word | frequency ≥ 30 | ai → এই **+ আই**; ata → এটা **+ আতা** |

Cost: one attested-word lookup per candidate on the async suggestion path (never the
keystroke preview). Risk: none to commits; the only visible change is one more chip.
Measured need: P2 is the largest class by use (8,939 occurrences in 15,000 fresh-news
words), P1 the single most frequent word (তাঁরা).

### F2 — Joined twin for split compounds (no commit change) — covers P3

When the compound splitter wins, add the joined form (the parts without the space,
rendered as one word) as a strip candidate with source `joined_twin`. The user who typed
one key can tap it; `bujteparcina` still commits the split. Extra: when the joined form is
itself an attested word, promote it above the split (that is a ranking change — hold it
for a later round with its own pin wall).

### F3 — Names and places lexicon (data only) — covers P4

The harvest already produced the material: 2,347 union names, 64 districts, the upazila
lists, 278 towns, 120 rivers, and 3,000+ people titles, all from Bengali Wikipedia. Add a
`dictionary-compiler/data/proper_nouns.tsv` (word, count, category) compiled at habit
priority (priority 1, tier B) so **a name never outranks an existing common word** and
only fills the gap when the store has nothing better. Rebuilding the dictionary bumps
`DictionaryVersion.REQUIRED`, so this lands with the next planned dictionary round, not
as a hotfix. Before compiling: drop titles that collide with dictionary words (a village
named ফুল must not become a priority-1 alias of `phul`), and keep both spellings where
Wikipedia and the dictionary disagree (টুংগীপাড়া / টুঙ্গিপাড়া) as aliases of one owner.

### F4 — Rescue-layer guard for clean unknown keys (narrow logic) — covers P5

`khondol → খোঁদল`, `arag → আরাগঁ`, `daimenositi → দাড়িম এনসিটি`: the junk-rescue /
typo layers replace a clean rule reading with a word two or more letters away. S181
already bounds the typo correction with `keyReadingDistance`; the same bound should
gate `tryJunkLexiconRescue` and the lattice: **if the rule reading is clean
(`readsAsCleanBengali`) and no candidate reads the key within one edit, keep the rule
reading and put the rescue on the strip instead.** Ship only with the 132K-key S181 dump
diff (`S181CommitDumpJvm`) showing no regression on the evidenced set.

### F5 — Retroflex and ণ in proper nouns (do NOT change the rule defaults)

`t→ত`, `d→দ`, `n→ন` are right for the language as a whole; changing them would break the
evidenced dictionary path. The honest fix for `ronohat → রনহাত` is F3 (the name in the
lexicon) plus the existing long-press ট/ড alternatives and the S184 chandrabindu key
for the rare cases. No rule change proposed.

### Not proposed

- Any change to the commit for in-dictionary words: 97.8% commit-exact and 99.9% strip
  coverage on fresh news is ceiling behaviour; the remaining misses are twins, which F1
  addresses on the strip.
- Any change for spelling-variant corpus words (P7): the engine's answer is the standard
  spelling.

## 6. Implemented: F1 + F2 as S189 (strip-only)

Shipped the day of the study, engine commit untouched: chandrabindu twin, key-fold twins
(s→sh, ii→i, uu→u) via exact store lookups, vowel-initial twin from the primary, joined
twin when the splitter wins; at most two twins per strip, a twin more frequent than the
primary beside it, a rarer one above the typed-literal slot. Pins:
`S189StripTwinsJvmTest`. Before/after on the same three corpora (6,000 words each,
S188 harness, same code path for "before" — the compiled engine predates the edit):

| corpus, variant | commit-exact before → after | on the strip before → after (weighted) |
|---|---|---|
| fresh news, canonical | 98.5% → 98.5% | 99.9% → 99.9% (99.8 → 100.0) |
| fresh news, typist fold | 91.5% → 91.5% | 97.8% → 99.8% (97.4 → 99.9) |
| places, canonical | 96.3% → 96.3% | 99.0% → 99.1% |
| places, typist fold | 80.9% → 80.9% | 94.8% → 97.7% (95.9 → 99.2) |
| science, canonical | 88.5% → 88.5% | 91.7% → 93.1% |
| science, typist fold | 80.8% → 80.8% | 87.5% → 91.4% (94.3 → 96.9) |

Commit drift on every key that missed before and after: **0** (news 7 of 7 common misses
identical, places 90 of 90, science 556 of 556). Miss count on fresh news: 46 → 7. The
one pin the walls caught on the way: the S52 acronym chip (ba → বিএ) must stay inside the
visible strip, so a twin now never displaces an acronym or phrase chip.

Device confirmation (S22, 1.5.121): তাঁরা and আই at chip 4, পুলিশ / দেশ / আশা / নোটিশ /
ভুট্টা beside the primary, জয়শঙ্কর / কাঠমান্ডুভিত্তিক / রসচিঠি as the second chip. Lesson:
the host asks for eight chips and shows five or six (three when the chips are wide
compounds), so a twin placed at "limit minus two" was the invisible seventh chip on the
phone; rarer twins are capped at the fourth chip and the joined form sits beside the
primary.

## 7. Implemented: F3 as S190 — dictionary 3.9.8 (data only)

Curation (`S190LexiconCurationJvm`, opt-in) over the harvest: 6,147 candidates passed
the static gates (Bengali-only, 2–18 chars, no OCR-split vowel signs, not in the words
table; titles accepted as facts, body words at ≥ 5 occurrences). Against the real engine
1,366 already resolved (dropped), 2,276 were spelling variants the engine already
normalises (উঠেচে → উঠেছে; dropped — adding them would let the variant win its own exact
key), leaving **1,576 proper nouns + 902 science terms**, written to
`dictionary-compiler/data/proper_nouns.tsv` and `science_glossary.tsv` with frequencies
capped at 40 and no usage injection, so they assign tier B (typeable by exact key, never a
completion, never above a common word); the corpus-authority refresh lifts a few with real
usage evidence (ক্রিপ্টন → 73) into tier A, which is correct. Known gate limitation: a real
loanword whose canonical roman the engine happens to normalise to an unrelated dictionary
word is excluded too (শিকিমেট → সিকিমে, নর্স → নড়ছ) — listed in `s190-review.tsv` for a
manual pass.

Compiled: 478,835 words (+2,478), 1,810,468 index rows, version 3.9.8
(`DictionaryVersion.REQUIRED`), slim 31 MB (floor 35, so only the more frequent new names
reach the JS surfaces).

Commit-dump diff (S181CommitDumpJvm, 132,769 keys before, 133,562 after, 132,759 in
common): **26 commits changed (0.02%)**, all rare or synthetic keys — most are
improvements (zekonoo → যেকোনও, sthanangker → স্থানাঙ্কের, khristopuurbe → খ্রিস্টপূর্বে,
tahaderi → তাহাদেরই, paromanobikta → পারমাণবিকতা, alternativeser → অল্টারনেটিভসের), a few
junk-to-junk on synthetic keys (drawta, charitablete), three debatable (thekera → ঠেকেরা,
zayte → জায়তে, shahta → সাহতা) where a new tier-B name owns a key that used to fall to a
compound; none in the evidenced everyday set. List: `s190-dump-diff.tsv` (archived).

Coverage on the same 6,000-word slices (S188 harness, canonical roman):

| corpus | commit-exact S189 → S190 | on the strip | unknown words | unknown commit-exact |
|---|---|---|---|---|
| fresh news | 98.5% → 98.5% | 99.9% → 99.9% | 7 → 6 | 42.9% → 50.0% |
| places | 96.3% → 96.8% | 99.1% → 99.4% | 97 → 70 | 19.6% → 27.1% |
| science | 88.5% → 93.8% | 93.1% → 96.7% | 736 → 430 | 27.6% → 47.0% |
| unions and villages (vs the full-run baseline) | 77.9% → 82.1% | 84.8% → 89.9% | 1,401 → 979 | 15.8% → 18.4% |

One pin flip, documented in `S189StripTwinsJvmTest`: `joyoshongkor` now commits জয়শঙ্কর
(a tier-B name) instead of the split, with the split on the strip.


