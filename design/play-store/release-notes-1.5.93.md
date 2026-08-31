# Banglu 1.5.93 (2130) — S150

## bn-BD (Play Console "What's new")
চ্যাটের বানান আরও ভালো বোঝে: ta মানেই টা, ata/ai মানেই এটা/এই; onk, vlo, aktu, kno, tnx-এর মতো শর্টহ্যান্ড এখন সরাসরি অনেক, ভালো, একটু, কেন, থ্যাংকস; fb/mb/pc লিখলেই এফবি/এমবি/পিসি; khub-i ধাঁচের জোর-দেওয়া ই (ekdomi → একদমই) নিজে বসে। use/nice/new এখন ইউজ/নাইস/নিউ।

## en-US
The chat register got smarter: ta commits the টা clitic (তা one tap away), ata/ai read এটা/এই, vowel-less shorthands (onk, vlo, aktu, kno, tnx) resolve directly, fb/mb/pc render their letter names, and the emphatic -ই composes on consonant-final stems (ekdomi → একদমই). Corrected loans: use → ইউজ, nice → নাইস, new → নিউ, help → হেল্প.

## Internal
- Driven by the S149 Banglish-corpus study (docs/engine-banglish-study-
  2026-08-30.md) — fixes 1-4 of its ranked backlog. Post-fix delta measured
  by re-running the same study (appended to the report).
- Documented pin flips: ta তা→টা (CandidateLatticeTest, ConfusingWordsTest,
  SmartEngineV2LowercaseParityTest — 479× corpus margin).
- New engine layer: tryEmphaticICompound (S56 sibling) — consonant-final
  stems only, junk-aware whole-word precedence (TIER_A or freq≥25 owns),
  composition must be attested. khubi correctly stays খুবি (owner@69).
- fb/mb/pc → ACRONYM_OVERRIDES Tier P; id stays Tier S by decision (ঈদ).
- Walls green: :shared:jvmTest, :shared:testDebugUnitTest, :shared:jsNodeTest,
  :desktop-app:test, :windows-ime:test. Engine change reaches other surfaces
  at their next rebuild (slim JSON unaffected — no dictionary change).
