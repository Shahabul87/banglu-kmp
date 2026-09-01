# S163 — Glide Typing (গ্লাইড টাইপিং) Design

**Date:** 2026-09-01 · **Approved by:** user ("great if this feature is liked by
the user build it before marketing" + design "ok", defaults accepted)
**Market basis:** competitor-review analysis 2026-08-30 — swipe/glide is
Gboard's most-praised feature in the Bengali subset and our listed weakness.

## 1. Goal

Drag a finger across the letter keys; on release, Banglu commits the intended
word — Bangla in BN mode (decoded roman → the existing conversion pipeline),
English in EN mode — with alternates on the strip. Fully offline, zero cost on
the tap path, works down to 2GB phones.

## 2. Feasibility evidence (spike, 2026-09-01)

`S163GlideProbeJvm` (throwaway yardstick → becomes the tuning harness):
synthetic noisy gestures over the REAL dictionary's canonical romans,
idealized unit-key QWERTY grid, naive single-channel shape decoder:

| σ (noise, key-widths) | top-1 | top-6 | avg decode |
|---|---|---|---|
| 0.15 | 77.6% | 98.0% | 1.3 ms |
| 0.25 | 70.9% | 94.0% | 1.6 ms |
| 0.35 | 61.6% | 86.7% | 1.3 ms |

50K-word lexicon beats 100K (63.9%/88.0% at σ=0.25 — more confusable shapes).
Top-1 gap closes with turning-point weighting + context rerank (both planned;
the context model and S99 char-bigram data already exist).

## 3. Non-goals (v1)

- No glide on desktop / macOS IME / web / Windows টাইপার (no finger).
- No glide for numbers, symbols, emoji search, or identity fields.
- No mid-gesture live preview of the decoded word (Gboard shows one; v1 shows
  the trail only — the preview needs per-move decoding and is a v2 candidate).
- No learned-word templates in v1 (lexicon = canonical store romans; learned
  words still reachable by tapping).

## 4. Architecture

ONE new pure engine unit, one platform loader, one UI layer. Conversion
behavior stays in `shared` (invariant 9).

### 4.1 `shared` — `com.banglu.engine.glide.GlideDecoder` (pure, commonMain)

- **Input:** `List<GlidePoint(x, y)>` in KEY-GRID UNITS (key width = 1.0; the
  Android layer converts pixels → grid using its real row offsets), plus mode
  (BN roman table vs EN wordlist) and a `GlideLexicon`.
- **Pipeline:** resample gesture → prune lexicon by start-key/end-key
  neighborhood + arc-length ratio → score = shape distance (proportional
  resample, N=32) + turning-point channel (corner positions must align) −
  frequency prior → top-6 romans. The decoder is geometry+frequency ONLY.
- **Output:** ranked romans (BN) / words (EN) with scores. The SERVICE then
  converts each BN roman via the normal pipeline and applies the existing
  context rerank (user bigrams > corpus trigrams, two previous words) across
  the converted candidates to pick the commit — context lives where it
  already lives, not inside the decoder.
- All tunables in one `GlideTuning` object with defaults from the harness.

### 4.2 `GlideLexicon` — compact templates, platform-loaded

- Top-50K canonical romans (`priority=0`, `[a-z]{2,}`, frequency-ranked) from
  the EXISTING store — no compiler change, no db version bump, no wall churn.
- Built once off-main on first glide-enable, cached to
  `glide_lexicon.bin` in app files (version-stamped with
  `DictionaryVersion.REQUIRED`; rebuilt when the dictionary changes).
- Template = 32 resampled points quantized to bytes (x: 0..255 ≈ grid×24,
  y likewise) + start/end key + length + freq → 72 B/word ≈ **3.6 MB**.
  Lite mode loads the top 20K (≈1.5 MB). Dropped on S72 memory pressure and
  lazily rebuilt.
- EN lexicon: templates over `EnglishWordData` top words, same format.

### 4.3 Android — gesture layer + commit flow

- `ComposeKeyboardView` letter rows: a drag that starts on a letter key and
  travels ≥ 1.5 key-widths becomes a glide (below that it stays a tap; the
  S32 spacebar cursor-drag and S68 long-press popups are untouched — glide
  only arms on LETTER keys, and the existing gesture owners keep their keys).
- Trail: overlay polyline of recent points fading over ~250 ms, drawn in the
  accent color. Pointer sampling throttled to one point per ~8 px moved.
- On release: points → engine lane → decode → **Gboard-style commit**: top-1
  word + auto space committed immediately; strip shows the alternates as
  `glide_alt` chips; tapping one swaps the just-committed word (delete
  committed length, commit replacement — the EN-chip mechanics). Next
  keystroke or a second glide closes the fix window.
- BN mode: decoded roman runs through `convertWithContext` (context = the two
  previous committed words, as at space-commit); the committed Bangla is
  learned ONLY as a normal engine-primary commit (S26 law: no preference
  recorded unless the user taps an alternate).
- Disabled in: raw/URI fields, private/sensitive input, voice session active,
  clipboard/emoji layers, and when the switch is off.

### 4.4 Settings

`সেটিংস → গ্লাইড টাইপিং` switch, **default ON**, plain-language subtitle.
Stored in the normal prefs; `reloadSettings` picks it up (S95 pattern: user
choice survives keyboard reopen).

## 5. Failure modes & edges

- Gesture too short / one key → treated as a tap (never a dead zone).
- Decoder returns nothing above the floor → commit NOTHING, flash the trail
  red for 150 ms (no surprise text — WYSIWYG spirit).
- Lexicon not built yet (first minutes) → glide falls back to tap behavior;
  build completes off-main and arms silently.
- Two pointers → second pointer cancels the glide (multi-touch typists).
- Memory pressure mid-session → lexicon dropped, glide disarms until rebuilt.
- Backspace after a glide commit deletes the whole committed word (one
  gesture = one undo unit), reusing the S88 resume-composition machinery.

## 6. Performance budgets

- Decode ≤ 40 ms on-device (engine lane, once per gesture; JVM measured
  1.3–3.9 ms; budget allows 10–20× device slowdown).
- Zero added work per keystroke on the tap path (invariant 1).
- Trail rendering allocation-free per frame (reused path buffer).
- Lexicon build ≤ 3 s off-main, once per dictionary version.

## 7. Testing

- `GlideDecoderTest` (commonTest): synthetic-path pins — kmon/kemon/ami class
  words decode top-1; prune correctness; short-gesture rejection; quantization
  round-trip.
- `S163GlideStudyJvm` (the promoted probe): accuracy/latency sweep on the real
  store; regression-run before every glide tuning change with documented
  numbers (S82/S149 study discipline).
- Android unit: gesture classifier (tap vs glide thresholds), commit/swap
  policy object pins.
- Device gates: perf-build burst (frame p95 unchanged while gliding), smoke
  suite untouched, on-device glide of kmon → কেমন + alternate swap verified
  by screenshot before ship (Samsung Notes protocol, coordinate taps).
- Full wall suite per invariant 13 before any "done".

## 8. Rollout

One S-round per layer, shippable at each cut: (1) shared decoder + lexicon +
studies; (2) Android gesture layer + commit flow + settings; (3) tuning round
on-device (σ realities differ from synthetic) + tester build. Version target
1.5.102+; marketing waits for this feature per user decision 2026-09-01.
