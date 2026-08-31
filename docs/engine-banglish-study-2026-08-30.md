# Engine × Banglish corpora — behaviour & failure-pattern study (S149, 2026-08-30)

**Question:** how does the real engine (full ./dictionary.sqlite) behave on the
Banglish datasets collected in the user's deep-research report, and what are
the recurring failure patterns?

**Method.** Downloaded the report's P0/P1 sources: BanglaTLit (paired chat/
comment register), Vashantor (standard + 5 regional dialects, paired), Socian
Romanized (50k), Code-mixed Chaos (10.2k), Bengali SMS Smishing (7k), PolCSBD
(2.2k Latin-only replies). BanglaDual's Mendeley file API refused listing
(and the report itself ranks it low-realism — 85% Wikipedia). Pairs were
word-aligned (roman token count == gold token count, gold fully Bengali),
each roman token converted with `engine.convertWord`, compared after nukta
folding; `top6` = gold reachable in the 6-slot suggestion strip. Harness:
`S149BanglishCorpusStudyJvm` (opt-in: `BANGLU_BANGLISH_STUDY=1`,
`BANGLU_BANGLISH_DIR=<staged data dir>`). Full miss list: `staged/failures.tsv`
in the data dir (11,828 unique miss types, 33,311 weighted).

## Headline numbers

| corpus (register) | word pairs | word-exact | gold in top-6 | sentence-exact |
|---|---:|---:|---:|---:|
| Vashantor-standard (clean standard Banglish) | 77,566 | **91.2%** | **96.9%** | 56.9% |
| BanglaTLit (real messy chat/comments) | 64,072 | **66.7%** | **75.4%** | 15.1% |
| Vashantor-dialect (Ctg/Noakhali/Sylhet/Barishal/Mymensingh) | 17,530 | 70.9% | 82.5% | 11.9% |

Behaviour-only corpora (Socian/Chaos/SMS/PolCSBD, 74,057 tokens, no gold):
**100.0% of tokens produced Bengali output** (single ASCII passthrough:
`covid`), no crashes, mean `convertWord` = **3.16 ms** over 159k pair-words.

Reading: on *clean* standard Banglish the engine is strong (9 of 10 words
exact, 97% within one strip tap). The chat register is the battleground:
1 word in 3 misses primary, 1 in 4 isn't even in the strip. Dialect gold is
out of scope (the engine answers standard Bangla by design) — its 71% mostly
measures how much dialect vocabulary overlaps standard.

## What the misses actually are

Weighted bucket analysis over all 33,311 misses:

**1. Spelling-standard disagreements, not comprehension failures (~14%).**
- ো-final variants only — বড়ো/বড়, ছোটো/ছোট, কোনো/কোন, ছিলো/ছিল, করবো/করব,
  কতো/কত, এতো/এত, মতো/মত, গেলো/গেল, ভালো/ভাল: **11.3%** of weighted misses.
  The engine prefers the modern inclusive-ও Academy spelling; these corpora
  often use the short form — and are internally inconsistent about it.
- ি/ী-only (বেশি/বেশী, নিল/নীল): 2.3%. `ki` কি vs কী (138×) is a genuine
  grammar distinction no single-word converter can decide.
- Policy call, not a bug: keep the modern default; make future corpus scoring
  fold these twins.

**2. English/tech tokens rendered differently than the corpus (~9.4%).**
Two distinct sub-classes:
- *Different loan spelling, both defensible*: account অ্যাকাউন্ট/একাউন্ট,
  number নম্বর/নাম্বার, data ডেটা/ডাটা, help হেলপ/হেল্প, link লিংক/লিঙ্ক.
- *Real errors worth fixing*: `use`→উচ্ছে (the vegetable! should be ইউজ),
  `nice`→নিচে (should be নাইস in this register), `new`→নেড়ো (junk repair,
  should be নিউ), `tnx`→ত্ন্ক্স (should be থ্যাংকস), plus the two-letter
  initialism class the engine has no law for: `id`→আইডি, `fb`→এফবি,
  `mb`→এমবি, `pc`→পিসি, `tv`→টিভি (letter-name renderings).

**3. Vowel-less chat shorthand (5.6%).** onk→অনেক, vlo→ভালো, aktu→একটু,
kno→কেন, khubi→খুবই, ekdomi→একদমই, hbe, kmn… The engine already owns this
class (kmon→কেমন works); these specific keys are missing aliases.

**4. The `ta` clitic — the single largest miss (479×).** Standalone `ta` →
তা, but in chat it is overwhelmingly the clitic টা (`price ta`, `app ta`).
`ata`→আটা vs এটা (150×) and `aita`→এইটা are the same family: the initial
`a`→এ reading (ai→এই 141×, ar→এর 112×, apner→আপনার 46×).

**5. Context homographs (unfixable at word level).** ase আসে/আছে (137),
hoi হই/হয়, jai যাই/যায়, pore পরে/পড়ে, jan জান/যান, dosh দশ/দোষ, bon বন/বোন —
both readings are real words; only sentence context (the trigram rerank
lane) can pick. In single-word scoring they count as misses.

**6. Inflected loans**: hospital→হাসপাতাল**ে**, school→স্কুল**ে** — the gold
carries the locative; typed English stem converts to bare loan. Expected.

## Recommendations (in engine-lane order)

1. **`ta`→টা** as shorthand/rank decision, with তা as the strip twin, and the
   `a→এ` twins (ata/aita/ai/ar) guaranteed strip slots. Highest single win.
2. **Chat-shorthand aliases** for the vowel-less class hits: onk, vlo, aktu,
   kno, tnx→থ্যাংকস (+ chat_lexicon.tsv is the right layer). ~5.6% of misses.
3. **Emphatic -i suffix composition**: word+`i` → word+ই (khubi→খুবই,
   ekdomi→একদমই) — same family as the S141 negation compound.
4. **Loan fixes in EnglishDirectData**: use→ইউজ, nice→নাইস, new→নিউ,
   help→হেল্প; plus a small **initialism law** (2-3 consonant ASCII keys →
   letter-name Bengali: id/fb/mb/pc/tv/hd/gb).
5. **Scoring policy**: fold ো-final and ি/ী twins in future corpus pins so
   spelling-standard noise doesn't hide real regressions.
6. Dialect support stays out of scope; Vashantor-dialect numbers above are
   the baseline if that ever changes.

## Reproduction

```
# stage datasets (see scratchpad/banglish-data), then:
BANGLU_BANGLISH_STUDY=1 BANGLU_BANGLISH_DIR=<dir> \
  ./gradlew :shared:jvmTest --tests "com.banglu.engine.S149BanglishCorpusStudyJvm" --rerun-tasks
```

Caps: 9,000 BanglaTLit + 12,500 Vashantor-std + 2,500 dialect sentences,
2,500 lines per behaviour corpus. Gold itself is user-generated and noisy;
treat single-word "misses" in class 1/5 as disagreements, not defects.

---

# Appendix — raw harness output

## banglatlit
- sentences read: 20331, word-aligned: 9000
- word pairs: 64072; word-exact: 42706 (66.7%); gold-in-top6: 48335 (75.4%)
- sentence-exact (all words): 1362 / 9000 (15.1%)
- failure classes:
    - near_miss_le2_edits: 9991 (46.8% of misses)
    - different_word: 7612 (35.6% of misses)
    - final_vowel_ending: 1877 (8.8% of misses)
    - inflection_or_truncation: 699 (3.3% of misses)
    - dental_vs_retroflex: 644 (3.0% of misses)
    - vowel_length_i_u: 207 (1.0% of misses)
    - sibilant_s_sh_ss: 181 (0.8% of misses)
    - english_passthrough_vs_bengali_gold: 39 (0.2% of misses)
    - j_y_class: 38 (0.2% of misses)
    - r_rr_d_class: 37 (0.2% of misses)
    - nasal_ng_m_chandra: 22 (0.1% of misses)
    - multi_confusion_mix: 19 (0.1% of misses)

## vashantor-std
- sentences read: 12500, word-aligned: 11792
- word pairs: 77566; word-exact: 70720 (91.2%); gold-in-top6: 75126 (96.9%)
- sentence-exact (all words): 6714 / 11792 (56.9%)
- failure classes:
    - near_miss_le2_edits: 2424 (35.4% of misses)
    - final_vowel_ending: 2170 (31.7% of misses)
    - different_word: 667 (9.7% of misses)
    - vowel_length_i_u: 623 (9.1% of misses)
    - inflection_or_truncation: 248 (3.6% of misses)
    - r_rr_d_class: 195 (2.8% of misses)
    - sibilant_s_sh_ss: 189 (2.8% of misses)
    - dental_vs_retroflex: 175 (2.6% of misses)
    - j_y_class: 65 (0.9% of misses)
    - nasal_ng_m_chandra: 50 (0.7% of misses)
    - multi_confusion_mix: 40 (0.6% of misses)

## vashantor-dialect
- sentences read: 2604, word-aligned: 2500
- word pairs: 17530; word-exact: 12431 (70.9%); gold-in-top6: 14467 (82.5%)
- sentence-exact (all words): 297 / 2500 (11.9%)
- failure classes:
    - near_miss_le2_edits: 2715 (53.2% of misses)
    - different_word: 1152 (22.6% of misses)
    - final_vowel_ending: 434 (8.5% of misses)
    - dental_vs_retroflex: 306 (6.0% of misses)
    - inflection_or_truncation: 142 (2.8% of misses)
    - r_rr_d_class: 138 (2.7% of misses)
    - vowel_length_i_u: 107 (2.1% of misses)
    - j_y_class: 51 (1.0% of misses)
    - sibilant_s_sh_ss: 41 (0.8% of misses)
    - multi_confusion_mix: 12 (0.2% of misses)
    - nasal_ng_m_chandra: 1 (0.0% of misses)

## Most frequent misses (all pair sources)

| n | typed | engine | gold |
|---|---|---|---|
| 479 | ta | তা | টা |
| 296 | boro | বড়ো | বড় |
| 264 | choto | ছোটো | ছোট |
| 249 | but | বুট | বাট |
| 222 | kono | কোনো | কোন |
| 213 | id | ইদ | আইডি |
| 209 | chilo | ছিলো | ছিল |
| 200 | fb | ফ্ব | এফবি |
| 177 | help | হেলপ | হেল্প |
| 173 | koto | কতো | কত |
| 160 | trickbd | ট্রিক্ট | ট্রিকবিডি |
| 152 | bon | বন | বোন |
| 150 | tnx | ত্ন্ক্স | থ্যাংকস |
| 150 | ata | আটা | এটা |
| 141 | ai | আই | এই |
| 140 | korbo | করবো | করব |
| 138 | ki | কি | কী |
| 137 | ase | আসে | আছে |
| 137 | mb | মব | এমবি |
| 124 | use | উচ্ছে | ইউজ |
| 123 | phone | ফোনে | ফোন |
| 118 | nice | নিচে | নাইস |
| 117 | keno | কেনো | কেন |
| 116 | hoi | হই | হয় |
| 116 | vi | ভি | ভাই |
| 112 | ar | আর | এর |
| 108 | onk | ওন্ক | অনেক |
| 103 | eto | এতো | এত |
| 99 | moto | মতো | মত |
| 98 | valo | ভালো | ভাল |
| 95 | use | উচ্ছে | ইউস |
| 92 | pore | পরে | পড়ে |
| 82 | me | মে | মি |
| 81 | toyar | অটোয়ার | তোয়ার |
| 80 | account | অ্যাকাউন্ট | একাউন্ট |
| 80 | new | নেড়ো | নিউ |
| 77 | aktu | আকু | একটু |
| 72 | beshi | বেশি | বেশী |
| 71 | author | অথার | অথর |
| 70 | shob | শব | সব |
| 70 | tar | তার | তাঁর |
| 67 | dosh | দশ | দোষ |
| 67 | link | লিংক | লিঙ্ক |
| 67 | nil | নিল | নীল |
| 67 | app | অ্যাপ | এপ |
| 66 | boner | বনের | বোনের |
| 66 | pc | প্ | পিসি |
| 65 | tnx | ত্ন্ক্স | থ্যাংক্স |
| 63 | diya | দিয়া | দিয়ে |
| 63 | number | নম্বর | নাম্বার |
| 62 | jai | যাই | যায় |
| 61 | kew | কেও | কেউ |
| 60 | vlo | ভোলো | ভালো |
| 60 | bro | ব্রো | ব্র |
| 59 | valobase | ভালোবাসে | ভালবাসে |
| 58 | tuner | টুনার | টিউনার |
| 55 | jan | জান | যান |
| 53 | ekdomi | একদম | একদমই |
| 52 | and | আন্ড | এন্ড |
| 52 | gelo | গেলো | গেল |

## Behaviour-only corpora (Socian / Chaos / SMS / PolCSBD, no gold)
- tokens converted: 74057; Bengali output: 74056 (100.0%); ASCII passthrough: 1 (0.0%)
- top passthrough tokens (English law / URLs etc.):
    - covid: 1

- mean convertWord time over 159168 pair-words: 3.16 ms

---

# Post-S150 delta (same harness, same caps, engine at v1.5.93 / aca0209)

| corpus | word-exact | gold in top-6 | sentence-exact |
|---|---:|---:|---:|
| BanglaTLit (chat) | 66.7% → **70.0%** (+3.3) | 75.4% → **77.1%** (+1.7) | 15.1% → **19.0%** (+3.9) |
| Vashantor-standard | 91.2% → 91.2% (+40 words) | 96.9% → 96.9% | 56.9% → 57.1% |
| Vashantor-dialect | 70.9% → 69.8% (−1.1) | 82.5% → 81.4% | 11.9% → 11.0% |

The chat-register fixes land where they were aimed: +2,117 word pairs and
+348 exact sentences on BanglaTLit, with the dental/retroflex bucket
collapsing 644 → 225 (the টা clitic). The small dialect regression is the
same flip seen from the other side — dialectal gold uses তা where standard
chat means টা — and dialect remains explicitly out of scope. Remaining
backlog (unchanged): ো-final/ি-ী scoring folds, kothai→কথাই root-
decomposition ranking, and the context homographs that belong to the
trigram lane.

---

# Post-S151 delta + the first context-mode numbers (engine at v1.5.94+)

New metrics from the upgraded harness (variant-exact folds ো-final and
ি/ী spelling twins; WITH-CONTEXT feeds the two previous GOLD words through
`rerankWithContext`, the exact lane the IME uses at commit):

| corpus | word-exact | variant-exact | top-6 | with-context |
|---|---:|---:|---:|---:|
| BanglaTLit (chat) | 70.0% | **71.5%** | 77.8% (↑ from 77.1) | 70.1% |
| Vashantor-standard | 91.2% | **94.3%** | 97.5% (↑ from 96.9) | **91.7%** |
| Vashantor-dialect | 69.5% | 72.0% | 82.1% | 69.5% |

Homograph keys (ase, hoi, jai, pore, jan, bon, dosh, ar, ki, tar, kore, dan):

| corpus | occurrences | plain | with context |
|---|---:|---:|---:|
| Vashantor-standard | 3,380 | 84.6% | **89.2%** |
| BanglaTLit (chat) | 2,605 | 75.0% | 75.7% |
| Vashantor-dialect | 777 | 96.4% | 96.1% |

Reading: the trigram/bigram context lane genuinely resolves homographs on
clean text (+4.6pt on standard), and the S151 twin promotions lifted top-6
on both main corpora — the right reading is now always one tap away even
when context is silent. On the chat corpus the context gain is small: the
noisy register produces prev-word pairs the corpus trigram table has never
seen (the observed-triple gate correctly refuses to guess). The growth
path there is the user-bigram lane (S78) — personal repetition, which the
harness cannot simulate — plus richer chat n-grams at the next dictionary
rebuild. The ~1.4-3.1pt variant-exact gap over word-exact quantifies the
spelling-standard noise for future score reading.

---

# S153 — the general English-register round

Method (the "general fix" the user demanded after courier): every English
token in the corpora was mined with its corpus-gold renderings —
`english-register.tsv`, 1,209 tokens intersecting google-10k with ≥4
gold-aligned occurrences; 923 kept at ≥5 occurrences and ≥60% majority.
The corpus majority is treated as the population's preferred rendering.

Engine vs corpus majority:

| | primary matches | in-strip only | absent |
|---|---:|---:|---:|
| before S153 | 633 (68.6%) | 89 | 201 |
| after S153 | **716 (77.6%)** | 64 | **143** |

The 290 misses were triaged into three mechanical layers:
1. **Curated loan seeds (~56)** — the English law fired but rendered the
   wrong spelling (tutorial টুটরিয়েল→টিউটোরিয়াল, config ছন্ফিগ→কনফিগ,
   flash, theme, recovery, unlimited, official, verification…).
2. **Shorthand chat defaults (~28)** — a real Bengali word held the key
   under the S142 band while the corpus voted the loan by landslides:
   but বুট→বাট (518:15), phone ফোনে→ফোন (292:40 — the S81 pin flipped,
   documented), number, date, mode, mail, up, my, account, address, and
   the chat abbreviations vi→ভাই, ak→এক, bt→বাট, gd→গুড.
3. **Acronym Tier P (8)** — id→আইডি reversed at 484:2 (eid→ঈদ keeps the
   festival), gp, ss, uc, bd, sms, html; nc→নাইস via the pre-control-rule
   whitelist. Two traps: a stale sms→এসেমেস duplicate later in the map
   (the S52 phd lesson, again), and the trailing-c hasanta rule.

Corpus-majority spelling flips in the curated seeds: data→ডাটা,
account→একাউন্ট, address→এড্রেস. What remains in the 143 "absent" tail is
dominated by dialect gold (মোরে, ফারে, তনে, চাইর — Banglish romanizations
that merely collide with English words), inflected gold (hospital→
হাসপাতালে), and context homographs — the same classes already documented
above, not English-register defects. Pin wall: S153EnglishRegisterJvmTest
(83 corpus-majority pairs).

## S156 delta — chat n-grams compiled into the dictionary (2026-08-31)

The study's gold corpora now feed the context model itself: `chat_trigrams.tsv`
(54,127 rows) and `chat_bigrams.tsv` (61,584 rows) mined from the staged gold
pairs (BanglaTLit + Vashantor-std, Bengali-only tokens, counts n≥2) compile at
CHAT_WEIGHT into db 3.9.7 — trigram/bigram tables at their 120k/150k caps.
Full study re-run on the rebuilt dictionary (same harness, 25m38s):

| dataset | word-exact | | gold-in-top6 | | homographs w/ context |
|---|---|---|---|---|---|
| | before | S156 | before | S156 | before → S156 |
| banglatlit (chat) | 70.0% (post-S150) | **73.1%** | 75.4% | **79.5%** | 76.5% w/ ctx |
| vashantor-std | 91.2% | **91.3%** (94.4% variant-exact) | 96.9% | **97.5%** | 89.2% → **91.7%** |
| vashantor-dialect | 70.9% (S149) | 69.5% | 82.5% | 82.2% | 96.5% w/ ctx |

- vashantor-std WITH-CONTEXT word-exact reached **93.3%** (rerankWithContext on
  gold prev-2); plain homograph accuracy is unchanged at 84.6%, so the
  91.7% figure is purely the new chat context model doing its job.
- The dialect −1.4pt is cumulative drift across S150–S156 (the dialect set was
  last measured at S149); dialect (Chittagong/Sylhet renderings) is not a
  target register — standard + chat are the product contract.
- Attribution for the derived counts: dictionary-compiler/data/LICENSES.md
  (BanglaTLit MIT, Vashantor CC BY 4.0 — counts only, no text redistributed).
