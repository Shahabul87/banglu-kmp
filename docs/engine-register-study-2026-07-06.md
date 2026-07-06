# Engine Register Study — 2026-07-06 (S17)

Multi-register evaluation of the v3 engine (db 3.7.2, post-S16) against fresh
and register-specific Bengali text. Method: for each register, build a
`word TAB count` corpus, reverse-transliterate each in-dictionary word to its
canonical typing, run `convertWord` + `getSuggestions(10)` through the
production-equivalent JVM engine (`ConjunctCorpusStudyJvm`), and measure
primary accuracy, top-3 presence, and lazy-variant behavior.

## Corpora

| register | source | types | note |
|---|---|---|---|
| news | FRESH harvest 2026-07-06: Prothom Alo (sitemap/API) + BBC Bangla, 800K tokens, 1.7K articles | 15,359 | bdnews24/anandabazar blocked (403) |
| verbs | verb tense/aspect suffix slice of the 12.6M merged corpus (লাম/ছি/ছে/তেছ/বেন/য়েছ... endings) | 7,651 | action words across tenses |
| mixed | English loanwords in Bengali script: english_lexicon ∩ corpus | 4,281 | ডাক্তার/অফিস/লিগ class |
| slang | curated chat-register list (154 forms: -সি/-ছি continuous, মু-future, -োস 2nd person, intensifiers, discourse words) | 154 | synthesized — no politely scrapeable chat source |
| medical | medical-stem slice of merged corpus (110 domain stems: রোগ/চিকিৎ/ওষুধ/হৃদ/কোষ/জ্বর...) | 1,547 | live bn-wiki category harvest was API-throttled; slice used instead |
| literature | bnwikisource (existing corpus, min count 15) | 25,576 | archaic orthography register |

## Results

| register | dict coverage (types / tokens) | tested | primary | top-3 | token-wtd primary | lazy variants (prim / top-3) |
|---|---|---|---|---|---|---|
| news | 96.8% / 97.3% | 14,851 | **98.5%** | 99.8% | 99.5% | — |
| verbs | 83.3% / 96.2% | 6,371 | **97.6%** | 99.7% | 99.3% | 89.3% / 94.7% |
| medical | 69.3% / 94.7% | 1,072 | **98.0%** | 99.4% | 98.9% | 90.3% / — |
| literature | 77.4% / 90.8% | 14,999 | **95.5%** | 99.1% | 98.0% | 77.8% / 89.8% |
| slang | 84.4% / 86.7% | 130 | **93.1%** | 97.7% | 93.4% | 54.5% / 68.2% |
| mixed | 91.1% / 99.5% | 3,884 | **92.4%** | 99.2% | 98.2% | 71.5% / 84.2% |

Headline: the formal registers (news/verbs/medical) are strong — token-weighted
primary ≈ 99%. The weaknesses concentrate in **chat/slang** and **loanword
(mixed)** registers, and in **lazy typing variants** everywhere.

## Failure classes (share of canonical-typing misses)

1. **Inherent homophone pairs — NOT bugs, context problems** (~50-60% of all
   misses). The typed key legitimately spells two real words and the engine
   picks the more frequent; the target sits rank 2-3:
   - o-kar pairs (21-33%): mot মত/মোট, pore পরে/পড়ে, bon বন/বোন, holo হলো/হোলো
   - retroflex/dental + অ/ও initials (bulk of "other"): hat হাত/হাট, di দি/ডি,
     dan দান/ডান, tin তিন/টিন, opor অপর/ওপর, je যে/জে (loanword names lose to
     native words: pitar পিতার/পিটার)
   - r/ড় (13-19% in verbs/literature/news): har হার/হাড়, bat বাট/বাত
   Top-3 presence is ~99% for these; the real fix is **context** — the bigram
   rerank exists, next lever is trigrams (roadmap).

2. **WYSIWYG divergence — real defect class.** Rows where the strip's FIRST
   suggestion is the target but the committed primary differs: news 18,
   verbs 26, literature 61, mixed 18. Example: chil → primary ছিল, strip
   shows চিল first; setar → primary সেতার, strip সেটার first. The S1/D1
   invariant (primary must lead the strip) has residual leaks.

3. **Junk squatters on short keys — S16 class, primary-selection side.**
   jos → জস (junk) over জোস; koros → কোরস (rank-10 target!); ston → স্টোন
   over স্তন; nisho → নিশা. S16 fixed this for compositions; the primary
   arbitration for short keys still lets floor-frequency corpus junk win.

4. **Chat-register vocabulary gaps (24 true OOV in the curated 154):**
   - -নাই attached negation: খাইনাই, করিনাই, জানিনাই, যাইনাই, হইনাই
     (extend tryNegationCompound to "nai" — direct S16 follow-on)
   - -োস/-স 2nd-person informal: আছোস, পারোস, আসোস, করোস(junk-beaten), দেখছোস
   - -সি continuous chat spellings: গেসি, করতেসি, বুঝতেসি, কইসে
   - loan/discourse: ব্রো, ব্রেকাপ, বেস্টু, টেনশান, পুরাই, ঠিকাছে, ওমনে
   Also slang lazy variants are weakest of all registers (54.5% primary):
   para→প্যারা lost to পাড়া, ada→আড্ডা missing, hebi→হেব্বি missing.

5. **Literature archaic forms** — hasanta-finals (কোন্, বল্, কর্, থাক্) and
   sadhu forms rank -1/low. Deliberate non-goals; no action.

6. **Normalization artifacts** — a handful of "misses" where output and target
   are byte-different but render identically (ঁ ordering, ZWJ); measurement
   noise, not user-visible.

7. **Medical** — conversion is fine (98.0%); the type-level coverage gap
   (69.3%) is mostly rare inflected compounds (কোষগুলি, রোগিণীকে,
   ঔষধবিজ্ঞান) which composition largely reaches; token coverage 94.7%.

## Recommended next rounds (ranked)

1. **S18 chat-register round**: "nai"-negation in tryNegationCompound;
   -os 2nd-person habit alias/suffix; add curated chat lexicon words
   (গেসি/করতেসি class as tier-A words with aliases); fix jos/koros/ston-class
   short-key junk arbitration (extend S16 validator-frequency trust to the
   short-key primary path).
2. **WYSIWYG divergence audit**: instrument the ~120 divergent rows from this
   study; primary must equal strip[0] (S1/D1 completion).
3. **Trigram context** (bigger): the only lever for the dominant homophone
   class; corpus pipeline is ready.
4. Medical/loanword vocab top-up from OOV lists (low urgency; token coverage
   already 95-99%).

## Artifacts

Scratch (session-local): register-study/{fresh_news,verbs,mixed,slang,
medical_counts2}_counts.tsv + out_*/results.tsv. Fresh-news TSV is worth
archiving with the corpus data if a dictionary rebuild uses it.
