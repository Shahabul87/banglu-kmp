# S52 — English Loanword Tail + Acronym Layer Design

**Date:** 2026-07-15
**Status:** Approved by user (design presented with evidence, approved this session)
**Evidence base:** `.superpowers/sdd/probe-english-acronyms.md` (49+28-word probe
on the real store), `.superpowers/sdd/explore-loanword-machinery.md` (pipeline
trace with root causes), `.superpowers/sdd/research-banglish-web.md` (verified
Bengali spellings, letter-name table, corpora).

## Problem (measured, not assumed)

1. **Plain English words**: 44/49 already correct via S23/S24 machinery. The
   tail: `callback`→ছাল্লাল (excluded by the lexicon builder's top-30,000
   frequency cutoff — ranks #30,742), `motivation`→মোটভেশন and
   `semester`→সমেস্টার (misspelled rows in our own lexicon data),
   `late`/`simple` (ranking losses; correct form in suggestions).
2. **Acronyms**: 19/28 garbage. Not in CMU pronunciation data (not spoken
   words) so no lexicon fix can reach them. Two failure shapes: conjunct soup
   (ssc→স্স্ছ) and real-wrong-word collisions (otp→আতপ, nid→নিদ) that the
   junk-detector rightly never rescues. Generic heuristics are PROVEN unsafe:
   llb/gb/km collide with real Bengali words; tv/kg are vowel-less but already
   correct.

## Fix 1 — ACRONYM_OVERRIDES (curated whitelist, two tiers)

- New map in `shared` commonMain, same shape and same three call sites as
  `MOBILE_SHORTHAND_OVERRIDES` (SmartEngine.kt ≈:991/:1303/:1400): checked
  after `DIRECT_WORD_OVERRIDES`, before negation/store. Zero validator/store
  dependency → identical on Android full+lite, desktop, extension, macOS IME;
  WYSIWYG-safe by construction (instant preview and commit hit the same map).
- **Tier P (primary overrides, ~100 entries)**: keys with no real-Bengali-word
  collision. Sources: the research's OBSERVED list (এসএসসি, এইচএসসি, জেএসসি,
  পিএসসি, বিসিএস, পিএইচডি, এমবিবিএস, এনজিও, ওটিপি, এনআইডি, এলএলবি, বিবিএ,
  এমবিএ, সিএনজি, এটিএম, জিপিএ, সিসিটিভি, ভিআইপি, সিভি, ইউএসবি, জিপিএস,
  পিডিএফ…) plus letter-table-derived expansions (verified table: A এ B বি C সি
  D ডি E ই F এফ G জি H এইচ I আই J জে K কে L এল M এম N এন O ও P পি Q কিউ R আর
  S এস T টি U ইউ V ভি W ডাব্লিউ X এক্স Y ওয়াই Z জেড). Lexicalized-whole-word
  acronyms keep their word forms (ওয়াইফাই, সিম, টিভি, পিন) — never
  letter-spelled. Already-correct keys (tv, kg, ok, phd, wifi, sim, apps, etc)
  ARE included with their current-correct values — this makes them explicit
  and fixes the lite/slim tier where the validator-gated rescue path doesn't
  run.
- **Tier S (suggestion-only, small)**: keys colliding with common Bengali
  words — `ba` (বা), `ma` (মা), `dc`, `sp`, `oc`, `id`-class. Primary NEVER
  changes; the acronym form is injected as a suggestion chip (precedent: S24's
  "loanword always a strip chip" mechanism).
- Never touched: kacci/jos/hoise-class deliberate defaults, existing shorthand.

## Fix 2 — English-lexicon tail repairs (eval-gated)

- `dictionary-compiler/data/english_lexicon_overrides.tsv` additions/fixes
  (first-row-wins INSERT OR IGNORE, the S23 mechanism): motivation→মোটিভেশন,
  semester→সেমিস্টার, ngo→এনজিও (fixes wrong এঙ্গো), callback→কলব্যাক,
  late→লেট, plus any A-list probe misses.
- **Cutoff experiment**: raise `EnglishLexiconBuilder.parseTopWords` top-30,000
  → 50,000, kept ONLY if the S24EvalJvm harness (top-3000 benchmark,
  attestation metric) shows ZERO regressions vs the 3.8.4 baseline. Any
  regression → revert the cutoff, keep only curated rows. (S24 law: eval-loop
  with regression diffing is the only safe way to tune the generator.)
- Ships as **db 3.8.5**: compiler version string + REQUIRED_DB_VERSION bump
  together; rebuilt dictionary.sqlite → android assets + repo root; regenerated
  banglu-slim.json (--slim) → extension vendor + macos-ime resources refresh.

## Non-goals / out of scope

BanglishRev corpus mining for a data-driven English priority list (good later
round — the dataset is identified and licensed CC-BY-NC-SA); Gboard behavior
study; any change to ranking laws, junk-path conditions, HABIT_RULES, or
parity-pinned behaviors; heuristic acronym detection (proven unsafe).

## Verification

- New `S52EnglishAcronymJvmTest` pinning: all Tier-P acronyms → exact Bengali;
  Tier-S keys keep their Bengali primaries AND surface the acronym chip;
  probe's A-list tail words fixed; kacci/jos/hoise/name-class pins untouched.
- Instant-preview mirror test: acronym keys show the override in
  `convertForInstantPreview` (WYSIWYG).
- S24EvalJvm before/after diff (the cutoff gate).
- Full walls: `:shared:jvmTest :shared:testDebugUnitTest :shared:jsNodeTest
  :desktop-app:test` + `swift run BangluCoreTestRunner` after slim regen.
- Cross-surface spot check: extension vendor + IME make install rebuilt from
  the new slim.
