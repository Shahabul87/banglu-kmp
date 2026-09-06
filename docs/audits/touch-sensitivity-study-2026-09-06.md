# Touch sensitivity study — Banglu vs the Samsung keyboard (S194, 2026-09-06)

**Trigger:** a tester wrote (Messenger, 2026-09-06) that a new keyboard "changes the
keyboard's sensitivity", that they want "appearance and sensitivity same as my phone
keyboard (Samsung keyboard)", and that their English-mode accuracy degraded. Their own
message carried the evidence: apoearnce, a d, sane, keybiard, installl, tyoing, kirese.

**Method.** Same phone (Galaxy S22, 1080×2340, 480 dpi), same text field (the Banglu
home try-field, an ordinary EditText), both keyboards driven by the same injected
MotionEvent sequences (scratchpad `inj/Inject.java` via `app_process`, multi-touch
aware; key centres read from each keyboard's own accessibility nodes). Phrase: "the
quick brown fox jumps over the lazy dog". Edits = Levenshtein distance to the phrase.

## Geometry (screenshots, same field)

| | key pitch | visible key | gap | letter row pitch |
|---|---|---|---|---|
| Banglu 1.5.125 | 105 px | 83 px | 22 px | 147 px (49 dp) |
| Samsung | 105 px | 89 px | 16 px | 150 px (50 dp) |

Touch cells tile edge to edge on both (Banglu folds the gap into the cell, S11/S68).
Geometry is not the difference.

## Behaviour (edits per 43-character phrase)

| sequence | Banglu 1.5.125 | Samsung |
|---|---|---|
| T1 centre taps, 30 ms down / 30 ms gap | 0 | 0 |
| T2 12 ms taps, 50 ms gap | 0 | 0 |
| T3 rollover: next key down 25 ms before the previous up | **17** | 0 |
| T4 24 px slide during the press | 0 | 0 |
| T5 taps 38 % of a key toward the right neighbour | 0 | 0 |
| T6 taps 40 % of a row toward the row below | 0 | 0 |
| T7 gaussian jitter σ 14 px | 0 | 0 |
| T8 gaussian jitter σ 22 px | 2 (doc for fox) | 1 (fog for dog) |

Isolation of T3 on Banglu: letters overlapping letters (25 ms and 60 ms) → 0 edits;
only the **spacebar** overlapping the next letter → 16 edits ("theq uickb rownf…").

## Root cause

Letters commit on finger-down (S11). The spacebar commits on release, because a tap and
a cursor drag are indistinguishable on the way down (S13/S32). A two-thumb typist lands
the next letter before lifting from space, so the space arrives one character late. The
Samsung keyboard commits the held space the moment a second finger lands.

## Fix (S194)

`SpaceRolloverPolicy` (pure, JUnit-pinned) + a root-level pointer observer on the
keyboard column (Initial pass, root-first): a pointer landing while space is held commits
the space right then, in press order; the rest of that hold is inert (no second commit
on release, no cursor drag). A plain tap and a cursor drag are unchanged.

## After the fix (Banglu 1.5.126, same sequences)

| sequence | before | after |
|---|---|---|
| T3 rollover 25 ms | 17 | **0** |
| T3c only the spacebar overlaps the next letter | 16 | **0** |
| T1, T2, T4, T5, T6, T7 | 0 | 0 |
| T8 σ 22 px jitter | 2 | 2 (unchanged, model territory) |

Spacebar regressions (injected): double space → "The. End"; plain space → "the end";
hold 200 ms then pull 260 px left, then x → "xabc" (cursor drag intact); rollover
space, then the same finger pulls, then x → "ab cx" (the early-committed space cannot
drag). Files: s194-touch-banglu-before.tsv, s194-touch-samsung.tsv,
s194-touch-banglu-after.tsv, s194-touch_exp.py, s194-Inject.java.

## What this study does NOT fix

- The tester's mid-word neighbour slips (p→o, m→n, o→i) are touch-model territory.
  Banglu's S99 model corrects only horizontal neighbours from a character bigram and skips
  word-initial letters; Samsung also auto-corrects the finished word. S182 deliberately
  turned English auto-replace OFF by default ("engine should not push anything"); the
  "→ word" tap chip remains. Re-enabling it is a product decision, not a bug.
- T8 (σ 22 px, about a fifth of a key) shows the model gap: 2 edits vs 1.
