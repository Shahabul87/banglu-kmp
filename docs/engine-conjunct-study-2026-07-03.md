# Engine Deep Analysis — 100k-Word Conjunct & Confusable Study

**Date:** 2026-07-03 · **Engine:** engine-v3-solution-round @ 7f35800, db 3.4.1, full mode
**Method:** 11M running tokens of fresh Bengali web text (6M bnwiki encyclopedia + 5M
bnwikisource literature/stories; news crawl in progress) → 657k unique forms → every
in-dictionary word with corpus count ≥ 3 (**92,609 words, 105,966 typings**) run through the
production-equivalent JVM engine (`convertWord` + `getSuggestions`), plus keystroke-by-keystroke
composing traces for the 800 most common রি/ৃ words. Runner committed at
`shared/src/jvmTest/kotlin/com/banglu/engine/ConjunctCorpusStudyJvm.kt` (rerunnable:
`STUDY_INPUT=<counts.tsv> ./gradlew :shared:jvmTest --tests "*.ConjunctCorpusStudyJvm"`).

## 1. Headline numbers

| Metric | Value |
|---|---|
| Canonical typing, raw primary | 93.1% (top-3 96.9%, hard miss 0.4%) |
| Canonical, **majority-spelling** primary (fair metric*) | **95.8%** (freq-weighted 96.4%) |
| **Conjunct words** (contains ্/ৃ/ঋ), canonical primary | **95.9%** (top-3 98.2%) — better than average |
| ৃ (ri-kar) words, canonical primary | 97.9% |
| `kori` → করি | ✅ correct (primary, key_first) |
| Lazy variants: h_lazy / degeminate / rri→ri / w_drop | 94.3 / 87.9 / 93.3 / 84.2% |
| Lazy variant: **y_drop** | **59.1%** — weakest habit class remaining |

\* Many "failures" are twin spellings of the same word under one key (খণ্ড/খন্ড both exist in
the dictionary); the fair question is whether the engine outputs the spelling real Bengali text
actually uses. 2,845 keys carry multiple corpus forms.

**The conjunct-handling core is strong.** The panic moments come from four specific,
general defects below — all measured, all phone-verified where user-visible.

## 2. The four weaknesses (ranked by corpus damage)

### W1 — Engine layers override the correct store answer (1,832 words, weight 182k)
The tier-ranked index has the right word **first** for the typed key, but an earlier engine layer
(seed dictionary) wins arbitration. Root cause observed: `convertWord` only lets the store beat
the seed when `corpusFreq > dictFreq + 5` — ties and near-ties go to the seed, whose entries
carry stale spellings/frequencies.
- `toiri` → **তৈরী** (seed @82, archaic; bnwiki usage 531) while store has **তৈরি** first
  (bnwiki usage 5,021). Phone-verified: তৈরী commits, তৈরি at rank 2. Seed source: `SeedData.kt:6226`.
- `tri` → **ট্রি** over ত্রি (store: ত্রি tier-0 prio-0 first).
- `khond` → খন্ড over খণ্ড; `za` → যায় over যা; `pore` → পড়ে over পরে; `dan` → ডান over দান.

### W2 — Index frequencies don't reflect real usage, so orthographic twins misorder (4,171 words, weight 171k)
The store's `frequency` column comes from the legacy 480k list. When both spellings of a word
exist (ণ্ড/ন্ড, ঙ্ক/ংক, খ্রিস্ট/খ্রিষ্ট, ি/ী, ু/ূ), the wrong twin often outranks. 887 measured
twin pairs; worst clusters: **ঙ্ক 30% fail** (শঙ্কর→শংকর), **ণ্ড 15%** (মণ্ডল→মন্ডল, ঠাণ্ডা↔ঠান্ডা),
**ন্ড 12%**, খ্র 33% (খ্রিস্টাব্দ→খ্রিষ্টাব্দ). We now own an 11M-token corpus that arbitrates
every one of these pairs by evidence.

### W3 — ri-kar flashes mid-word (the user-reported bug) — 57 of the 800 most common রি words (7.1%)
Full-word conversion of রি words is fine (`kori`→করি ✅). But **while typing**, ৃ-words grab the
strip: at `brit` (typing "british") the #1 highlighted chip is **বৃত্ত**, plus বৃত্তি and বৃত —
3 of 6 chips are ৃ forms. Phone-verified this session. Cause: the rri→ri habit alias gives
ৃ-words exact ownership of plain-ri keys (`brit` is an alias key of বৃত্ত), and exact hits beat
prefix continuations mid-word. ৃ carries ~0.4% of corpus usage — it should never dominate a strip.

### W4 — Composing-preview instability (trace evidence)
Mid-word previews jump to unrelated or invented forms: `s`→সঃ, `mrri`→ম্র্রি (garbage),
`porichi`→পড়িচি, `koriy`→কোরীয়. Each keystroke can rewrite the whole preview instead of
extending it. This is the "engine panicking the user" feeling: the text under the cursor
shape-shifts even when the user is typing a perfectly ordinary word.

## 3. Proposed general solutions (no word patches)

- **S5 Corpus-authority refresh (fixes W2, most of W1's data side):** recompile
  `phonetic_index` frequency + tier from the new corpus, weighted toward the modern register
  (bnwiki + news; wikisource discounted for spelling authority since it skews সাধু forms).
  Minority twins drop to tier-B (exact-match-only) so they stop polluting strips.
- **S6 Store-first arbitration (fixes W1):** an exact-key, tier-A, priority-0 store hit wins
  over the seed layer unless the seed word is itself tier-A with strictly higher corpus usage.
  One rule in `convertWord`; kills the তৈরী/ট্রি/খন্ড class wholesale.
- **S7 Composing continuation preference (fixes W3+W4):** while composing, rank words whose
  canonical key **extends the typed prefix** above exact habit-alias hits of different phonetic
  shape; alias-only owners (e.g. বৃত্ত for `brit`) stay available in the strip but never as the
  highlighted default mid-word. Commit-on-space behavior for completed alias keys is unchanged.
- **S8 (next round) y-drop habit aliases:** y_drop at 59.1% is the largest remaining lazy-class
  gap; needs the same alias-table treatment sh→s got.

## 4. Register caveat (honest measurement note)

Of 3,786 majority-form failures, 1,906 are cases where the engine's choice IS the modern
(bnwiki) majority and only the literature corpus disagrees (কোরিয়া vs করিয়া, হয়ে-class সাধু
forms). These are **defensible** and excluded from the fix targets; S5's modern-register
weighting encodes this permanently.
