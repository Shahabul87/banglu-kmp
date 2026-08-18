# S110 — Book/Paper Register Study (release-readiness)
Date: 2026-08-17 · Engine: db 3.9.3, v1.5.68 (2105) · Harness: `S110BookWordsStudyJvm` (S110_STUDY=1)

## Source corpus
User-collected book/paper prose (`test-bangla.md`, repo root, **kept out of git** —
published book text; the harness reads it locally via `S110_FILE`).
- 81,803 running Bengali tokens · 10,195 unique words · 51% hapax (classic literary tail)
- Register: translation prose (Bengali *Origin of Species*), scientific vocabulary,
  heavy tatsama/conjuncts — the opposite pole from the chat-register studies (S82/S89/S109).

## Method
For every unique word: canonical romanization via `ReverseTransliterator` (what a
knowledgeable typist types) → full-store `convertWord` + top-6 suggestions.
Words present in the 476K dictionary measure RANKING; words absent measure COVERAGE.

## Results

### In-dictionary book words — ranking quality (7,296 unique)
| class | n | primary | top-6 |
|---|---|---|---|
| ALL | 7,296 | **97.3%** | **99.9%** |
| conjunct-bearing | 3,326 | 98.5% | 100.0% |
| no conjunct | 3,970 | 96.4% | 99.9% |
| vowel-initial | 1,307 | 97.8% | 99.9% |
| length ≥ 10 | 1,008 | 98.8% | 100.0% |
| chandrabindu | 164 | 97.0% | 99.4% |
| khanda-ta (ৎ) | 54 | 98.1% | 100.0% |

**Only 6 top-6 misses in the entire book**, all hapax near-homograph pairs
(রোবার→রবার, সুসম→সুষম, সসার→শসার, অ্যাস→ওয়াস, মোরেন→মরেন, বেঁচেও→বেঁচে).
The ~2.7% primary-only gap = words sharing a key with a more frequent owner;
the intended word rides the strip (top-6 99.9%). Conjunct-heavy literary words
score BEST — the conjunct machinery is release-solid.

### Out-of-vocabulary — coverage gap (2,711 unique, 5.6% of running tokens)
The engine cannot rank what the dictionary lacks. Composition analysis:

| OOV class | n | examples |
|---|---|---|
| inflection of a dictionary stem | 600 (27%) | ভ্যারাইটি**দের**(104×), বৈশিষ্ট্য**গুলি**(36×), একক**রা** |
| two dictionary words glued (samasa) | 981 (43%) | দেহ+গঠনের(67×), স্পষ্ট+চিহ্নিত, একই+রূপ |
| genuinely new vocabulary | 675 (30%) | সঙ্করণ family(19×), গৃহপালনাধীন, proper nouns (জিওফ্রয়, ওলাস্টন), taxonomy (কোয়ার্কাস) |

143 further tokens were unromanizable (OCR noise, e.g. Assamese ৰ inside ধৈৰ্য্য).

### Discovered class: khanda-ta encoding twins
The sentence pin caught `utpotti` → উ**ত্**পত্তি (virama spelling) vs standard
উ**ৎ**পত্তি. Census: **936 ৎ/ত্ twin pairs** live in the words table (legacy
web-text encoding, same disease as the pre-fold nukta twins); in 14 pairs the
junk virama form is MORE frequent (উত্পাদন@70 vs উৎপাদন@84 both present,
চমত্কার/চমৎকার, চিকিত্সা/চিকিৎসা…). They split frequency mass and can win keys.

## Release verdict
**The ranking engine is release-ready for the literary register**: an educated
typist entering canonical spellings gets the right word first 97.3% of the time
and on the strip 99.9% of the time, across 7,296 real book words. The remaining
work is COVERAGE, not ranking, and is quantified above.

## Recommended next rounds (in impact order)
1. **S111 candidate — plural/classifier suffix composition** (-গুলি/-গুলো/-দের/-রা/
   -খানা/-টুকু on attested stems): erases the 600-word inflection class by
   mechanism, mirrors the existing negation/particle layers.
2. **Samasa glue layer**: when both halves attest strongly and the whole is
   unattested, offer the glued compound (guarded like tryCompoundSplit but
   joining, not splitting) — 981 words.
3. **Corpus ingestion**: feed user-collected book corpora (this file and future
   ones) into the compiler wordlist/frequency pipeline — the 675 new-vocab words
   plus better literary frequencies. Cheapest coverage win.
4. **Khanda-ta fold pass** in the compiler (mirror of the nukta fold): merge the
   936 ৎ/ত্ twins into the standard ৎ form, summing frequencies.
5. (carried from S109) Extended-dict exact vs store priority-1 arbitration
   (`ato` → আটো@25 over অটো@77 — S27 class).

## Tests added
- `S110BookWordsStudyJvm` — rerunnable study harness (env-gated), reports in
  `build/reports/s110-study/`.
- `S110BookRegisterPinsJvmTest` — permanent wall: 30 book-register words across
  all structural classes round-trip to primary with SELF-DERIVED keys (guards
  ReverseTransliterator + pipeline together), chandrabindu top-6 pins, and a
  sentence-level `parse` pin.
