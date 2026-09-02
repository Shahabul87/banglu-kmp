# Banglu Android — closed-testing deep audit

Date: 2026-09-01
Tree: `daec5fe` (S167), Android 1.5.105 (2142), db 3.9.7
Device: Samsung Galaxy S22 (SM-S901W), Android 16, 1080x2340 @480dpi, 120 Hz
Build under test: `assemblePerf` of head (R8, debug signature) installed over the 1.5.104 perf build.
Evidence folder: `audit-android-closed-testing-v1.5.105-2026-09-01/`

Method: (1) crash-log harvest from the dev phone (dropbox + logcat), (2) two
read-only code reviews (ComposeKeyboardView.kt, BangluIMEService.kt + policies),
every load-bearing claim re-verified by reading the code or reproducing on the
device, (3) fresh test walls, (4) on-device runtime tests driven through the
accessibility tree and `input motionevent` (typing, glide, hold-repeat, cursor
drag, selection, hide/reshow, field types, landscape, font scale 1.3, gesture
navigation, light mode, cold start, memory, frame timing).

## Summary

| Severity | Count | Headline |
|---|---|---|
| P0 | 1 | Keyboard-process crash on duplicate strip key (hit on the dev phone, v1.5.101). Fixed by dedupe in 1.5.102+, but the strip key is still a single point of failure. |
| P1 | 3 | Backspace with a selection deletes the wrong text; glide decode races later keystrokes; first glide after process start lands seconds late. |
| P2 | 8 | Long-press popup overlaps its own key; font-scale clipping; haptic/sound setting captured stale; RTL mirroring; cursor-arrow hold cost; learning switches revert after lite rebuild; strip/glide/voice disabled in URL and no-learning fields; frame budget on 120 Hz. |
| P3 | 9 | Accessibility gaps (arrows, mic, emoji cells), onboarding polish, double-space guard, prediction chip clipping, panel height jump, sub-360dp bottom row, blocking font load, glide trail width, Enter key at 1.3x. |

Gates: `:shared:jvmTest :shared:testDebugUnitTest :android-keyboard:testDebugUnitTest --rerun`
= 2,474 tests, 0 failures. `verifyImePrivacyBoundary` ran as part of the build.
Logcat over the whole session: no exception, StrictMode, ANR, or "Skipped frames"
lines from the keyboard process.

## P0

### P0-1 Keyboard process crash: duplicate LazyRow key in the suggestion strip
Observed: `dumpsys dropbox` on the dev phone, `data_app_crash` 2026-08-31 22:31:43,
package v2138 (1.5.101):

```
java.lang.IllegalArgumentException: Key "বিয়ে|glide_alt|glide_alt" was already used.
If you are using LazyColumn/Row please make sure you provide a unique key for each item.
  ... onMeasure ... ViewRootImpl.performTraversals
```

Root cause: `ComposeKeyboardView.kt:1655` keys strip items by
`"${bengali}|${source}|${tier}"`. Two glide alternates decoded to the same
Bengali word (two romans of বিয়ে) and the IME process died during measure. The
user loses the keyboard until Android restarts the service.

Status: the glide alternates are deduped by converted word since S163b
(`BangluIMEService.kt:2596`, commit a166935, first in tag v1.5.102). **Any tester
still on 1.5.101 can hit this on every ambiguous glide.** Confirm the Play
closed-testing track is on ≥ 1.5.102.

Residual risk: the strip key is still trusted from every producer (engine lists,
English completions, emoji chips, English glide alternates at line 2598). One
duplicate anywhere is a process crash. Recommend a defensive `distinctBy` on the
key, or an index suffix in the key, at the strip itself.

## P1

### P1-1 Backspace with an active selection deletes the character before the selection
Reproduced on device: text `কেমনা আমি ভালো `, double-tap selected `আমি`
(`f1-selected.png`), tapped Backspace → `কেমনাআমি ভালো ` (the space before the
selection was deleted, the selection survived).
Root cause: the service never tracks `newSelStart != newSelEnd`
(`onUpdateSelection`, `BangluIMEService.kt:1676-1731`); every delete path uses
`deleteSurroundingText(n, 0)`, which by contract excludes the selected range
(`:2277`, `:2285`, `:2364`, `:4628`). In Bangla mode
`tryResumeComposingOnBackspace` (`:2303-2321`) can additionally overwrite the
selection with a composing fragment.
Fix shape: remember the selection from `onUpdateSelection`; when it is non-empty,
`commitText("", 1)` (or `setComposingText("")`) replaces the range, as LatinIME does.

### P1-2 Glide decode races the next keystroke
Code-verified: `BangluIMEService.kt:2577-2646`. The decode runs on the engine lane;
after the await the only guard is `keyboardMode`. It then does `buffer = ""` and
`commitText(word + " ")` over the composing region (BN) or
`deleteSurroundingText(1, 0)` with a hard-coded 1 (EN). A letter typed after
finger-lift but before the decode lands is silently lost (BN) or the wrong
character is erased (EN). The window is normally small but see P1-3: on the first
glide after a process start it is several seconds, so testers WILL type into it.
Fix shape: a glide generation/buffer snapshot check like the S29 space-commit
reconcile; drop the result if the buffer or editor tail changed.

### P1-3 First glide after a process start commits 3-5 s after finger lift
Observed: after `am force-stop` + `ime set`, the keyboard was visible in 0.57 s and
the first typed word converted normally, but the first glide (`kemon`) committed
only ~5.5 s after lift (uiautomator polling, ±0.7 s); the second glide (`bhalo`)
in the same session took ~2.5 s by the same polling, and earlier warm glides
landed under 1.2 s. Cause: `GlideLexiconStore.banglaLexicon()` loads/builds the
52.8K-template lexicon lazily on the first glide (`GlideLexiconStore.kt:33-38`,
`BangluIMEService.kt:2580-2581`). Nothing is warmed at dictionary-ready time.
User-visible: the trail disappears, nothing happens, the user starts typing, then
a word appears mid-word (P1-2). Fix shape: warm the lexicon on the engine lane
right after the dictionary publishes (skipped in lite mode / memory pressure),
and show a subtle "loading" state on the trail if a glide arrives before it.

## P2

### P2-1 Long-press alternates popup overlaps the pressed key
Reproduced (`lp-1000ms.png`): holding `t`, the ট popup is drawn over the `t` key
itself and overlaps the number row. `ComposeKeyboardView.kt:2693-2695` uses
`IntOffset(0, -96)` — raw pixels, 32 dp on this 3x device, 36 dp on 2.625x, 64 dp on
hdpi. Use a dp offset (`with(density) { (-keyHeight - 8.dp).roundToPx() }`).
Also observed: nothing is shown at 500 ms; the popup arms at
`longPressTimeoutMillis * 3 / 2` = 600 ms (`:2622`), slower than Gboard.

### P2-2 System font scale 1.3 clips labels and overlaps hints
Reproduced (`fontscale13.png`): bottom-row "EN" renders as "E" and "BN" as "B";
number-row hint symbols (@ $ % &) overlap the digits. Cause: containers are fixed
dp, text is sp; `NumberKey`/`BackspaceKey` use raw `.sp` (`:2967`, `:3053-3064`),
the enter glyph is 32 sp in a 40 dp key (`:2468-2488`). Fix: cap label sizes with
`fontScale`-independent units (e.g. `dp.value.sp / fontScale`) or `autoSize`, and
give the number key a two-slot layout.

### P2-3 Vibration/sound setting changes never reach the keys until the view is recreated
Code-verified: `hapticOn`/`soundOn` are read from CompositionLocals
(`:2402`, `:2525`, `:2757`, `:2888`) and captured by `pointerInput(Unit)` blocks
(`:2782`, `:2913`, `:3016`), which never restart. The service only flips the
state value (`reloadSettings`, `:1029-1038`). Toggling vibration in Settings has no
effect in the current keyboard session. Fix: `rememberUpdatedState` for both.

### P2-4 RTL system locale mirrors the keyboard
Code-verified: `supportsRtl="true"` (manifest :60); zero `LocalLayoutDirection`
usage in the view. Under Arabic/Urdu locales the rows render p…q, the glide grid
maps `pos.x / keyW` on an LTR assumption (`:1896`, `:1906`). Force
`CompositionLocalProvider(LocalLayoutDirection provides Ltr)` at the root.

### P2-5 Cursor-arrow hold does a full extracted-text IPC every 60 ms and splits clusters
Code-verified: `moveCursorBySelection` (`BangluIMEService.kt:4649-4660`) calls
`getExtractedText(ExtractedTextRequest(), 0)` (whole document) per tick, on the
main thread, repeated every 60 ms (`ComposeKeyboardView.kt:1248-1254`), and steps by
code point, landing between ক and ি. Use `getTextBeforeCursor`/`getTextAfterCursor`
with a small window and grapheme stepping (BreakIterator, as backspace does).

### P2-6 Learning switches silently revert to ON after a lite-mode rebuild
Code-verified: `reloadUserLearningAsync` calls `SmartEngineAdapter.reset()`
(`:1231`) which sets `learningEnabled = true`, `personalDictionaryEnabled = true`,
`identityAssistEnabled = true` (`SmartEngineAdapter.kt:803-816`), and
`configureLearning` is only re-applied on the next `reloadSettings`
(`:1077-1081`). Trigger: `onTrimMemory` → `degradeToLiteForMemoryPressure`
(`:1470-1480`). Until the next show, commits are learned with learning OFF.
Fix: call `configureLearning` immediately after `reset()`.

### P2-7 Chrome address bar, incognito and no-personalized-learning fields lose the strip, glide and voice
Reproduced (`chrome-textarea.png`, which is the omnibox): conversion works but
the strip shows the action bar with no chips while composing.
`shouldDisablePersonalLearning` (`:760-772`) folds
`IME_FLAG_NO_PERSONALIZED_LEARNING` and `TYPE_TEXT_VARIATION_URI` into
`privateInputMode`, which gates suggestions (`:808-813`), glide (`:329-334`) and
voice (`:2851-2855`). The flag is a learning flag, not a display flag. Design
call: keep the learning gates, show the chips.

### P2-8 Frame budget on 120 Hz: median keystroke frame 13 ms
Measured (`dumpsys gfxinfo framestats`, plain 36-key tap burst, no glide, perf
build): 97 frames, p50 13.2 ms, p90 23.7 ms, p95 26.0 ms, max 29.8 ms, 72/97 over
the 8.3 ms 120 Hz budget, 28/97 over 16.7 ms, 0 over 32 ms; "Slow UI thread" 40/97.
Nothing exceeds the S136 smoke cap (48 ms) and there are no 100 ms-class frames,
but on a flagship this leaves no headroom for 2 GB phones. The UI-thread share
points at measure/layout, not GPU (gpu p95 7 ms). Candidates from the code
review: `KeyButton` takes an unstable `List` so all 26 letters recompose on every
shift flip (`:2590`); the whole `KeyboardActionBar` is swapped for a `LazyRow` at
every word start (`:743-785`); `EmojiData.search` runs unremembered per
recomposition (`:3100`). Profile with the Compose recomposition counter before
changing anything.

## P3

- **P3-1 Accessibility: arrow slots and the mic are not activatable.** The
  accessibility tree lists `Move cursor left/right` and `Bangla voice typing` as
  `clickable=false` (all other keys are `true`); TalkBack/Switch Access users
  cannot move the cursor or start voice. Emoji grid cells expose no
  label/role at all (`emoji.png`; tree shows only ABC/Backspace inside the panel).
- **P3-2 Onboarding slide 2** cuts the fourth feature card mid-title at the bottom
  edge with no fade/scroll affordance (`onb2.png`). Slide 3 stacks two dot
  indicators (card pager + slide pager), mildly confusing.
- **P3-3 Double-space দাঁড়ি deletes blindly.** `deleteSurroundingText(1, 0)` is
  guarded only by `lastSpaceTime` (`:2396-2399`, `:2448-2451`); space → backspace →
  space within 300 ms removes a real character. Check that the previous char is a
  space.
- **P3-4 Prediction chips clip under the compact mic/⋯ slot** with a hard edge
  (`light-bar.png`, fourth chip fragment). Add end padding or a fade.
- **P3-5 Emoji and clipboard panels are taller than the keyboard**, so the host
  app's content jumps when they open (`emoji.png` top edge vs `kb-bn-home.png`).
- **P3-6 Sub-360dp phones:** bottom-row weights sum to 8.5 with 0.8-weight
  EN/comma/dari keys (`:2235-2292`) → ~22 dp visual keys on 320 dp screens; the
  middle-row indent is a fixed 24 dp (`:173`). Not testable on this device.
- **P3-7 First ghost chip triggers a blocking font load**:
  `FontFamily(Font(R.font.jetbrains_mono))` (MainActivity.kt:84) with the default
  `Blocking` strategy reads the TTF on the main thread on the first keystroke.
- **P3-8 Glide trail stroke is 9 raw px** (`:1949`): 3 dp here, 6 dp on hdpi.
- **P3-9 System light mode does not change the keyboard**: theme default is
  `"dark"` (SettingsActivity.kt:215), and "auto" exists but is opt-in. Testers on
  light-themed apps see a dark keyboard (`light-bar.png`). Product call.

## Not reproduced / cleared

- Composing word left live after hide: hiding with a composing word and typing
  again on re-show resumed the same word (`কেমন` + `a` → `কেমনা`), the designed
  resume behaviour, not the word-replaced-by-one-letter failure predicted by the
  review.
- Hold-repeat backspace (350/60 ms) deleted 14 characters in 1.2 s and stopped on
  lift. Cursor-arrow hold and space-bar drag moved the caret (verified by inserting
  marker letters).
- WYSIWYG: `ami kemon acho  ` → `আমি কেমন আছো। `; 36-key sentence →
  `আমার সোনার বাংলা আমি তোমায় ভালোবাসি`; warm glides `kemon`/`bhalo` → কেমন/ভালো.
- Field types: password → raw dots, no chips; number → 16-key numeric pad;
  email → full keyboard; Chrome omnibox → Go action key.
- Layouts clean at 1.0x font scale: BN, EN, caps, symbols 1/2 and 2/2, emoji,
  expanded toolbar, clipboard, landscape (number row hidden by design), 3-button
  and gesture navigation (bottom row clears the pill — S166 holds).
- Memory: 137 MB PSS at first show, 124 MB after the full session (Dalvik 55 MB,
  native 20 MB, graphics 31 MB) — under the 320 MB heap cap with wide margin.
- Cold start: keyboard visible 0.57 s after the field tap after a force-stop.
- Privacy: manifest removes INTERNET/ACCESS_NETWORK_STATE/BILLING via
  `tools:node="remove"`; typed-text logs are `BuildConfig.DEBUG`-gated; provider
  not exported; no `!!` in the service.

## Recommended order

1. Confirm the closed-testing track is ≥ 1.5.102 (P0-1); add the strip-key
   dedupe insurance.
2. P1-1 selection delete, P1-2 glide generation guard, P1-3 lexicon warm-up —
   one round, one commit each, with a JVM policy test for the selection case and
   an instrumented test for the glide race.
3. P2-1, P2-2, P2-3 (all view-only, low risk) in one UI polish round; re-shoot
   at font scale 1.0 and 1.3.
4. P2-5, P2-6, P2-7 service round.
5. Frame-budget profiling (P2-8) before any recomposition refactor.

---

## Fix status — S168 round (2026-09-02, Android 1.5.106 / 2143)

Constraint honoured: no change under `shared/` — the conversion engine, its
keystroke path and the engine lane are untouched. Every fix lives in the
Android module; policies are pure objects pinned by 26 new JUnit tests
(`S168*Test`), the rest verified on the S22 perf build.

| Finding | Fix | Verification |
|---|---|---|
| P0-1 strip key collision | `StripKeyPolicy.uniqueByKey` dedupes at the LazyRow itself; key built in one place | `S168StripKeyPolicyTest` |
| P1-1 backspace over a selection | Selection tracked from `EditorInfo` + `onUpdateSelection`; `SelectionEditPolicy` routes range selections to `commitText("")` in all three backspace entry points | Device: long-press select আমি in `আমি ভালো কি` → Backspace → ` ভালো কি` |
| P1-2 glide race | `GlideCommitPolicy.resultStillApplies` — session token + typed-prefix snapshot at lift; a stale result is dropped | `S168GlideRaceGuardTest` |
| P1-3 first glide seconds late | Lexicons warmed on IO right after the dictionary publishes; the glide path loads the lexicon on IO, only decode+convert on the engine lane | Device (temporary timing log, then removed): cold process, glide ~2.5 s after show → lexicon wait 1 ms, decode 115 ms; warm glides decode 23-65 ms |
| P2-1 popup on the key | Offset in dp from the key height | Device: ট option bottom (1578 px) above key top (1602 px) |
| P2-2 font scale 1.3 clipping | `KeyLabelScale.systemIndependentSp` — key glyphs follow the keyboard's own font setting, not the system scale; number/backspace glyphs now on the same helper | `S168KeyLabelScaleTest`; device screenshot at 1.3: "EN" intact, hints clear of digits |
| P2-3 stale haptic/sound flags | `rememberUpdatedState` at all six capture sites | Code (the pointer blocks now read live state) |
| P2-4 RTL mirroring | `LocalLayoutDirection provides Ltr` at the keyboard root | Code |
| P2-5 cursor-arrow IPC + cluster split | `CursorStepPolicy` steps by user-visible cluster from a 32-char window; no `getExtractedText` per tick | `S168CursorStepPolicyTest`; device: `কি` → step left → `x` lands at `xকি` |
| P2-6 learning flags after lite rebuild | `configureLearning` re-applied right after `reset()` | Code |
| P2-7 URL / no-learning fields | `InputPrivacyPolicy`: chips, glide, voice stay on; learning gates take the new `learningSuppressedInputMode` | `S168InputPrivacyPolicyTest`; device: Chrome address bar shows ami / আমি / আমী… |
| P2-8 frame budget | `EmojiData.search` remembered; Kotlin 2.1 strong skipping already covers the letter keys | Warm bursts: p50 10.8-13.3 ms, p95 20.2-23.9 ms, 0 frames > 32 ms (baseline p50 13.2 / p95 26.0) — within noise, no regression |
| P3-1 accessibility | Arrow slots expose `onClick`; mic rebuilt on `CompactIconSlot` with a canvas-drawn emoji (a Text child split the label from the click action) | Device tree: arrows + mic `clickable=true`, Button role |
| P3-2 onboarding cut-off | Bottom gradient fade over the scrolling slide | Compiles; not re-shot (first-run state needs a data wipe) |
| P3-3 double-space guard | `DoubleSpacePolicy` checks the editor really ends with a space (IPC only inside the 300 ms window) | `S168DoubleSpacePolicyTest`; device: double space still yields `। ` |
| P3-4 chip clipping under the mic | 18 dp gradient fade at the strip's end | Device screenshot |
| P3-5 panel height jump | Emoji/clipboard panels sized to the measured letters layout | Device: keyboard top 1335 px, emoji panel 1362 px, clipboard 1359 px (was ~290 px taller / ~70 px shorter) |
| P3-6 sub-360dp indent | Middle-row indent proportional to screen width | Code (no narrow device available) |
| P3-7 blocking font load | JetBrains Mono preloaded on IO in `onCreate` | Code |
| P3-8 trail width | `3.dp.toPx()` | Code |
| P3-9 light-mode default | Left as-is — product decision (dark plum is the brand default; "auto" exists in Settings) | — |

Gates: `:shared:jvmTest` 741, `:shared:testDebugUnitTest` 435, `:android-keyboard:testDebugUnitTest` 191 (fresh), `:shared:jsNodeTest` 451, `:desktop-app:test` 41, `:windows-ime:test` 161 — all green (the non-Android walls are cached from today's fresh run; `shared/` is unchanged). Perf build typed the full test sentence, glided six words, and left the crash buffer empty.

---

## Post-release note — navigation-bar overlap on the dev phone (2026-09-02)

**Symptom (user):** the app's bottom tabs (হোম / শিখুন / সেটিংস / মতামত) drawn on
top of the phone's ||| ○ < buttons.

**Cause: test-harness artefact, not app code.** The audit switched the S22
between gesture and 3-button navigation with `cmd overlay enable …navbar.gestural`
/ `…navbar.threebutton`. Enabling the second overlay did not disable the first,
so BOTH stayed enabled. SystemUI then drew the 144 px three-button bar but
reported the 45 px gesture-pill inset to apps (`dumpsys window`: navigationBars
`frame=[0,2295][1080,2340]`, `insetsSize bottom=45`, while the bar surface sat at
y=2196). `BottomNav` applies `navigationBarsPadding()` correctly and padded for
the 45 px it was told about. Every app launch on the phone between the gesture-nav
test (2026-09-01 ~23:41) and the repair looked overlapped; the audit screenshots
taken before any toggling (`kb-bn-home.png`) show the tabs clear of the bar.

**Repair:** `cmd overlay disable …navbar.gestural` +
`cmd overlay enable-exclusive --category …navbar.threebutton`. Inset back to
`frame=[0,2196][1080,2340]`; a cold start places the tabs at y 2119-2178 above the
bar at 2196 (`home-3button-fixed.png`). Gesture navigation was correct throughout.

**Consequence for the release:** none — no source change, 1.5.106 (2143) stands.

**Process rule going forward:** when a test toggles navigation mode, restore it
with `enable-exclusive --category` and verify `dumpsys window` reports a single
navbar overlay and a 144 px (3-button) or ~45 px (gesture) inset before handing
the phone back; never leave two navbar overlays enabled.

---

## S169 — frame-budget profiling round (2026-09-02, Android 1.5.107 / 2144)

Engine untouched (no change under `shared/`). Method: `dumpsys gfxinfo framestats`
per-phase split → Perfetto system trace (sched/binder/gfx/view) → Perfetto
`linux.perf` callstack sampling of the keyboard process on a release build
(manifest now `<profileable android:shell="true"/>`; symbols from a temporary
`-dontobfuscate` build). Restore point before this work: tag
`restore-closed-testing-1.5.106`.

**Hypotheses rejected with evidence:** blocking InputConnection IPC (653 binder
calls, 10 ms total over a 46-key burst); the key-preview popup (drawn in place, no
window); Compose recomposition scope (all 47 keyboard composables skippable, strong
skipping on); a Material ripple on key presses (16% of samples on the interpreted
build, 0% once compiled — a symbolization artefact of interpreted frames).

**Root causes found:**
1. **Interpreted code on fresh installs.** ~30% of main-thread samples were in ART's
   interpreter. Paired cold-process measurements, 3 cycles each, same 36-key easy
   sentence and 30 conjunct words at 50 ms/key:

   | State | easy p50 | easy p95 | frames > 32 ms | conjunct p50 | conjunct p95 |
   |---|---|---|---|---|---|
   | interpreted (`compile --reset`, = day-one install without profile) | 14.7-16.2 ms | 30-33 ms | 2-8 / ~85 | 11.3-13.5 ms | 22.5-27 ms |
   | compiled with the baseline profile | 8.0-8.8 ms | 17-21 ms | 0 / ~115 | 8.8-9.5 ms | 18-19 ms |

   Conjunct correctness 30/30 in every cycle. During the very first interpreted
   minutes at 40 ms/key one compound (`bujteparcina`) committed the rule-only preview
   (S29 fast-commit path, reconcile window missed) — the same mechanism the profile
   protects.
2. **Strip node churn.** Content-keyed LazyRow items rebuilt 5-8 chip subtrees per
   keystroke (`Pending.keyMap` 17%, `UiApplier.dispatchChanges` 12.5% of samples;
   measure spikes 7-10 ms). Chips are now slot-keyed; duplicate keys are impossible
   by construction (StripKeyPolicy dedupe kept as cosmetic insurance).

**Shipped:** `src/main/baseline-prof.txt` = wildcard rules compiling every method of
`com.banglu.engine` and `com.banglu.keyboard` (4,909 methods after R8 expansion — no
dependence on which words a session typed) + the observed androidx/kotlin hot set
(4,686 methods, 1,680 classes) from a realistic unobfuscated session;
`androidx.profileinstaller` 1.4.1 with an explicit `ProfileInstaller.writeProfile`
on the service's IO startup lane (the androidx.startup initializer is stripped from
the manifest for cold-start time, so sideloaded APKs would otherwise never install
the profile; Play installs receive it as install-time dex metadata regardless).
Verified on device: dexopt state `speed-profile` after install+launch+compile.

**Still open:** the median keystroke frame on this 120 Hz flagship is ~8.5 ms, of
which ~3.8 ms is GPU issue/swap and the rest touch dispatch + a 5-9 ms measure on
strip-change frames; tail frames come from the strip's Bengali text layout. Next
lever, if ever needed: a fixed-height strip with pre-measured chip widths.

**S169b — first-install native-heap spike (found by the 1.5.107 release smoke).** The
gate's device smoke failed `memory_heap` at 322 MB (cap 320) while a standalone re-run
passed at 78 MB: the difference was timing. A native-heap sampler alongside the smoke
showed the keyboard process at 17 MB native during the probe and 100 → 182 MB right
after, and heapprofd attributed 453 MB of cumulative allocations to
`SQLiteCursor.fillWindow` under `GlideLexiconStore` ← `warmGlideLexicons`: the BN
lexicon build's `GROUP BY key ORDER BY f DESC` made SQLite sort all 1.65M keys in
memory (Android's SQLite keeps temp stores in RAM). Present since S163 (on the first
glide) and since S168 right after the dictionary load — an LMK risk on 2 GB phones at
the worst moment. Fix (Android module only): `GROUP BY key` walks
`idx_phonetic_index_key` in order (query plan: no temp b-tree) and
`GlideLexiconStore.topKByFrequency` keeps a bounded top-K in Kotlin
(`S169GlideTopKTest`, LEXICON_REV 4). Verified with wiped app data: native heap peak
22 MB across the whole cold start and lexicon build (was 182 MB); glide commits from
the fresh lexicon.
