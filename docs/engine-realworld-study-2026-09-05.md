# Engine real-world coverage study — 2026-09-05 (S188)

**Ask (user):** "Bangladesh newspapers (Prothom Alo, Jugantor, others), Bangla golpo /
uponnas / literature, Bengali science, names of people, objects, districts, villages —
test the engine's power, find every word it cannot handle or produces garbage for, the
failure patterns, check them on real devices, remember one word is typed many ways, and
propose fixes that do not disturb the engine people are testing right now."

**Engine under test:** the shared engine at commit `24c5d8c` (Android 1.5.120, db 3.9.7),
JVM full store on `dictionary.sqlite`. **No engine change was made in this round.**

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

_Status: the fresh-news corpus and the device pass are complete; the seven remaining
corpora (places, unions, people, science, objects, literature ×2, Wikipedia general) are
still scoring on the JVM (unknown proper nouns are slow: every one runs the rescue
layers) and their tables are appended in section 3.2 as they finish._

### 3.1 Fresh news (Prothom Alo, 15,000 most frequent words)

| variant / population | words | commit-exact | on the strip |
|---|---|---|---|
| canonical roman, all | 14,617 | 97.0% (97.8% weighted by frequency) | 99.3% (99.8%) |
| typist fold, all | 3,758 | 88.7% (91.1%) | 94.1% (95.0%) |
| in-dictionary words, either variant | 14,431 | 97.8% (98.0%) | 99.9% (99.8%) |
| out-of-vocabulary words | 186 | 35.5% | 52.2% |

Headline: **for words the dictionary knows, the engine is at ceiling** — under one word in
a thousand is missing from the strip. Everything below is about the remaining classes.

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

