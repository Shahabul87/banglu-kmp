# Personal hot set — spec (S172, 2026-09-02)

## Goal
The keyboard should feel instant on the words a person actually uses every day, from
the first keystroke of every session, without changing ANY conversion answer
(user law 2026-09-02: "if it breaks engine main functionality we can leave it —
engine now working perfect").

## What it is
A small per-user table of roman keys the user has committed, with a usage count and
a last-used day, stored in the keyboard process's private preferences under the same
scoped key family as learned words. At startup, once the dictionary is published, the
top-N keys are replayed through the engine's normal `convertWord` on the engine lane
(one key per lane turn, yielding between keys) so the engine's own store memos are
already warm for those words.

## What it is NOT
- Not a ranking signal. The primary, the chip order, pins and the canonical-owner law
  are untouched; a replayed key gets exactly the answer the engine would give anyway.
- Not a second dictionary. OOV learning stays where it is (explicit chip choice /
  clean transliteration through `SmartEngine.addWord`, anti-poisoning guard intact).
- Not shared engine code. Everything lives in the Android module.

## Rules
1. Recording happens only where learning is already allowed: the same gates as
   `learnCommittedWordAsync` (no private / raw / no-learning fields, dictionary ready).
2. Cap 500 keys; eviction by score = count × recency (half-life 30 days); a key seen
   today with count 1 outranks a key seen 90 days ago with count 3.
3. Persist debounced on the IO lane, atomic through the existing scoped-string path.
4. Erased by the existing "clear learned data" path (same key family) — no new
   erase code, no new surface.
5. Warm-up is interruptible and cheap: N ≤ 500, `yield()` after every key, starts
   after the glide lexicons, and is skipped under memory pressure / lite profile
   caps (N = 200).
6. Never leaves the phone; never logged.

## Verification
- `S172PersonalHotSetTest` pins record / cap / decay / serialization.
- Device: cold process → typing the user's frequent words immediately after the
  dictionary is ready must show no fast-commit previews and the same words as a
  warm process (top-1,000 harness on a subset); full walls unchanged.
