# Banglu — Agent Knowledge Base

> Read this FIRST. It is the onboarding document for any AI agent or engineer
> working on this repository. It explains what we are building, why, how the
> code is organized, and the hard-won invariants you must not break.

---

## 1. Mission & Vision

**Mission:** let every Bengali speaker type Bangla the way they already think —
lowercase English letters in, correct Bangla out — with zero surprise, zero
lag, and zero data leaving their phone.

**Vision:** become the default Bangla keyboard for the chat generation by
winning on exactly four things (market research, `~/Downloads` deep-research
report 2026-07-12, validated against Ridmik/Gboard/SwiftKey/Borno reviews):

1. **Avro-compatible but smarter** — canonical phonetics PLUS the real chat
   register (issa→ইচ্ছা, kmon→কেমন, korsi→করছি, golp→গল্প, bolbone→বলবোনে).
2. **Predictable, stable, low-surprise typing** — what the preview shows is
   what space commits (WYSIWYG contract); no frozen keys, ever, on any phone
   down to 2GB RAM.
3. **Private by default** — the typing engine is 100% offline, enforced by
   architecture (process isolation + gradle verification task), not by policy.
4. **Bangla-first UX** — dari on double space, tight comma, ০-৯ digits,
   conjunct-perfect output, inline Bangla voice typing.

**Business model:** free core typing, no ads ever in the typing surface.
Future revenue = optional premium (AI rewrite via the :ui process, power-user
packs). NO cloud API on the keystroke path — decided 2026-07-03 (latency,
cost, and privacy-promise reasons; see memory + git history).

**Current status (2026-09-02):** ONE ENGINE, SIX SURFACES (Windows IME
বাংলু টাইপার added S130-S132, on the Microsoft Store + website MSI).
- **S183 cursor pad (2026-09-04, tester: "কার্সর ডানে বামে কাজ করে, কিন্তু
  একের অধিক লাইনের ক্ষেত্রে উপর নিচে?"; user chose mock variant খ with the
  rule "if it opens below then it's fine" — the pad must never cover the
  editor):** new transient `KeyboardMode.CURSOR` (collapses like the
  clipboard; S183CursorPadModeTest). Entry points: HOLD ← or → on the
  empty-strip action bar (a tap still steps; the hold-repeat moved to the
  pad's own arrows), and a "Cursor pad" slot in the ⋯ tools row — needed
  because the action bar exists only while the strip is empty; after any
  space the strip holds prediction chips (the first device run held a
  stale coordinate and hit the দাঁড়ি chip). CursorPadPanel replaces the
  letter rows at the panel height: ABC/"কার্সর সরান" header, ▲ ▼ ◀ ▶
  canvas arrow heads (font arrows render unevenly on Samsung, S160), tap =
  one step, hold = repeat, centre "শেষ" returns. Up/down are DPAD key
  events (the host owns line geometry, onCursorMoveVertical); left/right
  reuse the S168 cluster-step selection path. Verified on the S22 with
  shifted-capital markers: three-line note, ▲ from line 3 landed the
  marker in line 2, three ◀ taps put it before তুমি, a one-second ▶ hold
  repeated to the end; all three entry routes open the pad. Engine
  untouched. Android 1.5.118 (2155).
- **S182 English mode never pushes a word (2026-09-04, tester screenshot:
  typed "Lal", Space auto-replaced it with "all"; user: "engine should not
  push anything, always user selection has to be on the top … make
  English typing feel like Samsung keyboard; if user type any email, user,
  domain name suggest it beforehand"):** EnglishCommitPolicy (pure,
  S182 test) — the typed word ALWAYS commits on Space; a likely correction
  becomes a "→ all" tap chip (applyPendingEnglishOffer replaces the kept
  word and learns the correction); the old auto-replace lives behind a new
  Settings switch "ইংরেজি অটো-রিপ্লেস" (`english_auto_replace`, default
  OFF). While typing, the typed word is the strip's FIRST chip (the same
  contract as the Bangla blue chip), then identity fills, then
  completions. IdentityAssist gains `prefixCompletions` — saved addresses
  AND site names (bangluweb.com, learned from tokens with a dot) appear
  after two letters in any non-sensitive field when identity memory is
  ON; the S136 opt-in default (OFF + purge) is deliberately unchanged
  (privacy audit decision) — flipping it is the user's call. S181 device
  pass (S22, 1.5.117): 996/1000 dictionary-exact (the same four documented
  differences), frames p50 10.2 / p95 21.1 ms.
- **S181 literary-faithful round (2026-09-04, from the Nazrul demo; user:
  "do not create bug while fixing this"):** four engine rules, each pinned
  (S181LiteraryFaithfulJvmTest) and measured before/after on every
  available oracle. (1) The commit wrapper's typo correction never
  replaces a dictionary result (≥0.9, evidenced) that reads the typed key
  EXACTLY after typist folds (chh/ch/c, sh/s, z/j, v/bh, ph/f;
  keyReadingDistance) when the correction is two or more letters from the
  key OR is the same word minus a typed emphatic ও/ই — shantrira keeps
  সান্ত্রীরা (was ছাত্রীরা), phenaiya keeps ফেনাইয়া (was ফেনায়), and 113
  emphatic keys keep their particle (hochcheo → হচ্ছেও); a single-slip
  correction to a real word still wins (motamoto → মতামত, bishwabiddaloy
  → বিশ্ববিদ্যালয়). Two wider versions were REJECTED by the 132K-key
  diff: "reads better" (kept the glued বিশ্বাবিদ্যালয়) and "exact only"
  (kept the junk মোটামতো) — membership evidence cannot tell a corpus-tail
  misspelling from a rare real word (both validator-valid at frequency 0). (2) CleanTransliterator
  writes ঞ before চ ছ জ ঝ (ন্চ never occurs) — shonchito → শঞ্চিত → সঞ্চিত.
  (3) tryNegationCompound's তো split defers when the key minus its final
  "o" is canonically owned by a consonant-final word (punjito → পুঞ্জিত;
  the 132K diff also turned dukkhito/minito/orthato/porinoto/jorito/
  nikoto/sileto/chihnito/byosto/bajeto/siimito into their real words).
  (4) A wrapper retry reads a final "o" as the inherent vowel ONLY when the
  raw is the rule floor or a spaced split, and the shorter key resolves to
  an evidenced consonant-final word within one letter (shonchito); a
  wider version dropped emphatic ও on bortomano/gurutbo/holeno and was
  narrowed. betha → ব্যথা shorthand. Measured: 100K parity 633 → 633;
  41K inflection parity 1,975 → 1,823; completion class unchanged;
  English study 8,237 → 8,238 English-or-correct; book study in-dictionary
  97.2 → 97.6% (chandrabindu 94.7 → 96.5); top-1,000 lists 994/998
  unchanged; the S149 Banglish corpora were NOT re-run (that session's
  staged data is gone). Lesson: a JVM-only StringBuilder call in
  commonMain (setCharAt) is caught only by the JS wall — run all six.
  Android 1.5.117 (2154).
- **S180 fast-commit reconcile tolerates a tail (2026-09-04, from the
  Facebook demo recording: "bujhte parcina is error in the recording"):**
  under screen-recording CPU load the authoritative বুঝতে পারছিনা arrived
  AFTER the double-space দাঁড়ি, and FastCommitReconcilePolicy refused the
  shape `committed + "। " + next word` (a documented S170 pin said a
  double space in between must block) — so the rule-only preview
  বুজতেপার্ছিনা। stayed on screen. Policy now peels a tail of ≤ 3 chars
  (spaces / tight punctuation) between the committed word and the caret
  or the live composing text and re-commits `word + tail`; a letter on
  the word, a longer gap, or a deleted appended space still block. Pin
  flip documented in S170FastCommitReconcilePolicyTest.
  dandaFromDoubleSpaceThenNextWordStillReconciles. Same round: three
  Facebook demo videos recorded in WhatsApp via adb screenrecord
  (scratchpad demo_record.py — human pace, touch dots, chip picks matched
  after nukta fold, real double tap for দাঁড়ি) → ~/Desktop/banglu-demos.
  Android 1.5.116 (2153).
- **S179 "কঠিন শব্দ, এখানে সহজ" conjunct curriculum (2026-09-04, user:
  "change this to kothin sobdo ekhane sohoj, add more complex … multiple
  cards with all kinds of complex conjunct which might be difficult in
  other keyboards"):** the S178 second family renamed and grown from 19
  words / 7 caps to 105 words / 16 caps, one cap per conjunct class —
  ৎ · ঁ · ঃ · ঐ ঔ ঋ · ক্ষ জ্ঞ · ঙ্ক ঙ্গ · ঞ্চ ঞ্জ · ণ্ড ণ্ঠ ষ্ণ · হ্ন হ্ম হ্ব ·
  ত্ম দ্ভ ম্ভ · দ্ধ ক্ত ল্প · ন্ত্র ষ্ট্র স্ত্র · য-ফলা · ব-ফলা · র-ফলা রেফ · ৃ.
  Candidates mined from the dictionary by conjunct regex, but the cards
  carry NATURAL romans (songe, onchol, juddho — the compiler's canonical
  keys songoe/oncol/zuddh are not what people type), each verified on the
  real engine first (111 candidates → 105 with ≥1 spelling; the 6 misses
  are engine gaps kept OFF the cards: dukkhito → "দুঃখী তো" compound
  split, ingit → ইংগিত, bhenge → ভেঙে, inchi → ইঞ্চ, trishna → ত্রিশনা,
  oporahno → অপরাহ্ণ). Syllable splits generated by a digraph-aware
  splitter (concat == roman, pinned). Every card note: "অন্য কিবোর্ডে
  হসন্ত লাগে — এখানে শুধু <roman>" (signs: "আলাদা কি
  লাগে"). Curriculum 454 words / 11 families, all pinned by
  S157TutorialWordsJvmTest. Android 1.5.115 (2152); web JSON
  regenerated. Engine untouched.
- **S178 tutorial "many spellings" + "hard elsewhere" families (2026-09-03,
  user: "user can get same words in many ways like pacci or pacchi or
  passi … check did we provide multiple ways … what kinds of words is very
  difficult on other keyboards"):** audit found the 313-word curriculum
  taught conjuncts but not multi-spelling (33 words with one alt, 1 with
  two, no everyday chat words). Probed ~100 candidate groups on the real
  engine, kept only groups with ≥3 verified spellings (36) → two new
  families FIRST in shared TutorialWords: "একই শব্দ, অনেক বানান" (caps চ্ছ /
  ছি / শ স / শর্ট; pacchi·pacci·passi·pachchi → পাচ্ছি, accha·acca·assa·
  acha·achha → আচ্ছা, korchi·korsi·korci·korchhi → করছি …) and "অন্য
  কিবোর্ডে কঠিন, এখানে সহজ" (ৎ ঁ ঃ ঐ ঔ ঋ ক্ষ জ্ঞ ষ্ট্র ন্ত্র, each card
  carrying a `note` — new optional Word field, rendered in the existing
  pill on Android/desktop/web, exported to JSON). 368 words / 11 families,
  every (spelling, word) pair pinned by S157TutorialWordsJvmTest. Web:
  tutorial-words.json regenerated, Conversational Spelling rows now derived
  from the shared family (bangluweb 05e09ff). Probe gaps NOT advertised
  (engine candidates for a later round): keno→কেনো, achchha→আঁচছ, jbo→জ্ব,
  shotto→ষত্ব, ha→হা, hna→হয়না, utsov→উৎস, oushod→ঔষদ, hotat→হটাত, thk/khb/
  taile/ame/apne shorthand misses. Engine untouched. Android 1.5.114 (2151).
- **S176/S177 preview-parity + typed-word round (2026-09-03, tester
  screenshots: "engine is producing garbage in the editor … words showing
  in the suggestion bar why??" / "hrid produces hridoy, hridoy should be
  in the suggestions … for many words"):** S176 — the composing preview
  was a hand-mirror of the commit layers missing suffix/root/recovery/
  compound/typo, so inflected loanwords previewed rule-floor garbage
  (হৃয্দ্রগেনের) while the strip read হাইড্রোজেনের; now a ≥4-letter key
  previews convertWordRaw's own (cached) answer after the conservative
  layers — parity by construction, kar contract untouched, raw-Latin
  passthrough still un-mirrored. Study on 41,190 inflected keys: 11,273
  preview≠commit → 1,979 (rule-floor garbage 9,302 → 912; the rest is the
  commit wrapper's typo shortening, deliberately not mirrored); 100K
  dictionary keys 659 → 638. S177 — the extended dictionary maps "hrid"
  to the completion হৃদয় while হৃদ@67 is only an alias row and the S7
  continuation bail handed the key over; the bail now spares an alias
  that IS the literal reading (romanReadsKey, typist folds ৃ/ী/ূ), and
  storeBeatsDictionary gained a completion-over-typed-word branch
  (completion's own roman extends the key, index never maps the key to
  it, first tier-A index word attested and not a spelling twin). Whole-
  extended-dictionary study: 1,280 keys of the shape, completion wins
  33 → 29 (the 29 are ি/ী twins or index words that do not read the
  key). Opt-in harnesses S176InflectionParityStudyJvm /
  S177CompletionStudyJvm; pins S176ComposingPreviewParityJvmTest,
  S177TypedWordBeatsCompletionJvmTest. All six walls green, no pin flip.
  Android 1.5.113 (2150); engine re-propagated to every surface.
- **S175 mid-word editing, second pass (2026-09-03, user: "is this mid
  word issue fixed??"):** measured the caret directly (shifted-capital
  marker + emulator selection log) and found three Android-module causes:
  (1) no insertion point after a re-composition — MidWordCaret now carries
  a roman edit point with the buffer (insert/backspace/hold apply there);
  (2) the S109/S174 typing gate demanded a rule-only echo, which every
  internal-ো word fails (তোমা → toma → তমা) — তোমার + e gave তোমারএ and a
  mid-word letter was plainly inserted, the space then split the word;
  typing paths now need only a sane reverse roman and show the word's own
  text until the letter lands, delete paths keep a whole-word echo gate
  (documented pin flips in S109TypingResumeTest / S174MidWordEditTest);
  (3) a lone consonant reverses without its inherent vowel (তমাদের minus
  মা → "t"), so the delete path derives the prefix roman from the whole
  original word minus the deleted tail. Emulator flows: tomoder → তোমাদের,
  tomar|r + de → তোমাদের, tomar + e → তোমারে, bola|la + h → ভোলা. Harness
  law: move the caret with DPAD_LEFT from the END (cluster steps) and
  read the caret with a shifted capital — never DPAD_RIGHT from HOME.
  Engine untouched. Android 1.5.112 (2149). Same round: S173 engine
  propagated — macOS bundle (runner 105/105) installed, extension zips,
  bangluweb vendor pushed, desktop 1.3.10 / টাইপার 1.0.14 tags.
- **S174 mid-word edit (2026-09-02, user: "try to type a letter wrong
  in the middle and try to fix that. you will feel it"):** the S88 resume
  only re-composed the text BEFORE the caret, so fixing a letter inside a
  committed word split it (kotha → কথা, caret after ক, type o, space →
  'কো থা'). BackspaceResume.planForMidWordEdit / planForMidWordBackspace:
  when the caret sits inside a Bengali word whose two halves both
  round-trip, both halves are deleted and prefix-roman + typed letter +
  suffix-roman compose as ONE word (the service carries the suffix in
  midWordSuffixRoman); mid-word backspace drops the last cluster before
  the caret. Tried before the end-of-word resume, so S88 is unchanged.
  Device: 'কথা' (no split). Pins S174MidWordEditTest. Release gate +
  clean-install smoke certified. Engine untouched. Android 1.5.111 (2148).
- **S173 rare-stem inflections (2026-09-02, user: "shororipu produces the
  word, shororipur cannot… hydrogener… do full engine test all time"):**
  trySuffixStrippedDictionary only saw trie stems, so store-only canonical
  words (ষড়রিপু) had no stem and their inflections fell to the compound
  splitter ("সরো রিপুর"); the genitive "r" rendered as bare র after
  consonant-final loans (টেলিফোনর). Strictly additive two-pass scan: pass 2
  runs only when the pre-S173 pass finds nothing and admits store
  canonical-owner stems (priority 0, freq ≥ 10) that the engine itself
  commits for the bare key (isCanonicalOwnerStem); renderInflection gives
  ের after a consonant; isInvalidVowelJunction refuses a vowel-sign suffix
  on a vowel-final stem unless attested (S143 amaer pin kept). Prevalence
  probe on the top-2,000 stems' OOV inflections: top-1 7318 → 7648 of
  8384 (+329, lost 0), splits 100 → 9; real-usage 1,000 list JVM 993 → 997,
  device 996/1000 dictionary-exact, 999/1000 == JVM. Rejected shapes:
  trusting low-freq store rows broke S143; competing passes flipped
  hochchete/baccate. Pins S173InflectionCompositionJvmTest. All six walls
  --rerun green. JS surfaces pick it up on the next propagation build.
- **S172 personal hot set (2026-09-02, user idea, constraint "if it
  breaks engine main functionality we can leave it"):** per-user roman keys
  with usage count + last-used day (PersonalHotSet, cap 500 / lite 200,
  count × 30-day-half-life recency), recorded behind the existing learning
  gates, persisted in the `personal_hot_set` scoped key family (erased with
  the rest), replayed through ordinary convertWord at startup one key per
  engine-lane turn with yield — warm memos for THIS person's words, never a
  ranking input; the frequency-ordered strip half was deliberately NOT built.
  Spec docs/superpowers/specs/2026-09-02-personal-hot-set.md. Verified on
  the rooted emulator (persist / restart / erase) and phone (18/18 recipe).
  Engine untouched. Android 1.5.110 (2147).
- **S171 lite-profile glide lexicon (2026-09-02, user: "you can do it if
  its not damage engine power"):** the lexicon store took the MANUAL lite
  switch, so auto-lite (memoryClass < 256) 2 GB phones built the full 50K
  lexicon (4.0 MB cache) and a single cache name let a lite store load a
  full-cap file. Now shouldUseLiteDictionary() + cap-keyed
  `glide_bn_<cap>.bin` (legacy file deleted once), store/decoders dropped on
  the profile-flip rebuild. Low-RAM validation REDONE on the real build
  (after finding the S170b run had measured a stale 1.5.104 — see
  [[verify-installed-build]] memory): 998/1000 dictionary-exact, 1000/1000
  engine-exact, one PID, 0 LMK/OOM/ANR, median PSS 131 MB, lite cache
  1.58 MB, glide works. Engine untouched. Android 1.5.109 (2146).
- **S170 fast-commit reconcile (2026-09-02, from the S169c study; user
  "go"):** the S32 fast commit's reconcile refused whenever the NEXT word was
  already composing, so at machine-speed typing right after a backspace
  hold (engine lane busy with S88 resume conversions) long words kept their
  rule-only preview (ইনস্তিতিউত for ইনস্টিটিউট). Reproduced 3/6 → fixed
  24/24: FastCommitReconcilePolicy adds ReplaceBeforeComposing — finish the
  live composing text, delete back through the committed segment, commit
  the authoritative word, re-set the composing span. Top-1,000 conjunct
  pass rerun: 998/1000 dictionary-exact, 1000/1000 engine-exact. S170b
  low-RAM run on the 2 GB emulator turned out to have measured a STALE
  debug-signed 1.5.104 (release install failed on signature, hidden by a
  piped tail) — corrected in the audit doc; S171 redoes it on the real
  build. Engine untouched. Android 1.5.108 (2145).
- **S169 frame-budget profiling (2026-09-02, user: "continue but engine
  behaviour should not be change"):** Perfetto sched/binder + callstack
  sampling of the release build (manifest now `profileable shell=true`)
  ruled OUT binder IPC, the key preview, recomposition scope and ripple; the
  real costs were (1) INTERPRETED code on fresh installs (~30% of main-thread
  samples in nterp; day-one keystroke frames p50 15-16 / p95 30-33 ms vs
  compiled p50 8-9 / p95 17-21 ms, paired cold-process cycles) and (2) the
  suggestion strip rebuilding every chip subtree per keystroke under
  content keys (Pending.keyMap 17% + dispatchChanges 12.5%). Shipped:
  `android-keyboard/src/main/baseline-prof.txt` (wildcards compile EVERY
  method of com.banglu.engine + com.banglu.keyboard — 4,909 after R8 — plus
  the observed androidx hot set; regenerate only from a `-dontobfuscate`
  build, an obfuscated build exports R8 names), profileinstaller 1.4.1 with
  an explicit `ProfileInstaller.writeProfile` in the service (the startup
  initializer is stripped for cold-start; Play delivers the profile as dex
  metadata anyway), slot-keyed strip items. Conjunct set 30/30 correct in
  every cycle. S169b: the release smoke caught a first-install NATIVE
  spike (17 → 182 MB) — the BN glide lexicon build's `GROUP BY … ORDER BY`
  sorted all 1.65M index keys in SQLite's in-memory temp store (LMK risk on
  2 GB phones); now an index-order GROUP BY + bounded Kotlin top-K
  (GlideLexiconStore.topKByFrequency, LEXICON_REV 4): peak 22 MB. S169c
  device study (user: "at least most frequent 1000 words"): top-1,000
  conjunct words AND top-1,000 overall typed end-to-end on the S22 at
  50 ms/key — 995/1000 and 994/1000 dictionary-exact, 997 and 998 engine-
  exact; every difference is a documented engine choice except 3 long
  words (10-13 letters) that fast-committed the rule-only preview under
  sustained machine-speed load (correct in isolation; S29 reconcile guard
  candidate). Data in docs/audits/…/top1000/. Shared engine untouched.
  Android 1.5.107 (2144).
- **S168 closed-testing fix round (2026-09-02, user: "fix all the issue one
  by one and do test it"):** the 2026-09-01 deep audit
  (docs/audits/audit-android-closed-testing-v1.5.105-2026-09-01.md) found
  one device crash (duplicate strip key, fixed in 1.5.102), three P1s and a
  layout/gesture backlog; ALL fixed in the Android module only (shared
  engine untouched). New pure policies, each JUnit-pinned: SelectionEditPolicy
  (range selection → backspace deletes the RANGE), GlideCommitPolicy.
  resultStillApplies (stale glide dropped), CursorStepPolicy (cluster steps
  from a 32-char window, no whole-document extract per hold tick),
  InputPrivacyPolicy (URL/incognito keep chips+glide+voice, never learn),
  DoubleSpacePolicy, StripKeyPolicy (LazyRow key dedupe = crash insurance),
  KeyLabelScale (key glyphs ignore the SYSTEM font scale — "EN"→"E" at 1.3x).
  Glide lexicons warm on IO after the dictionary publishes and the glide
  path never loads them on the engine lane (cold first glide: lexicon wait
  1 ms, decode ≤115 ms; was 3-5 s). Popup lifted in dp; haptic/sound via
  rememberUpdatedState; LTR locked at the root; learning flags re-applied
  after reset(); mic slot = CompactIconSlot with a canvas-drawn emoji (a Text
  child split label from click for TalkBack); panels sized to the measured
  letters layout; onboarding fade. Android 1.5.106 (2143).
- **S163 glide typing (2026-09-01, user: "if this feature is liked by the
  user build it before marketing"):** Gboard-style glide on the letter
  rows — drag through a word's roman letters, release commits the word
  (BN converts through the normal pipeline, EN direct) + auto space, alt
  chips swap the just-glided word, no-confidence gestures commit NOTHING
  (red trail flash). New ADDITIVE com.banglu.engine.glide package
  (GlideGrid/GlidePath/GlideLexicon/GlideDecoder — geometry+frequency
  only; SmartEngine untouched except ONE inert internal getter exposing
  shorthand keys, full walls --rerun green). Decoder: resample+smooth
  normalization BOTH sides, centroid-aligned shape channel 0.7 (real
  thumbs drift — study bias model reproduced the field complaint),
  first-key prior 0.25, corner channel OFF by ablation. Lexicon rev 3:
  seeds (kmon/tmi shorthands) + ALL-priority index keys (korsi/issa
  live at priority 1!) + bh→v variants (valo has NO key anywhere —
  rules produce it) — 52.8K templates ≈ 3.7MB, on-device build cached
  version#rev-stamped, dropped on memory pressure, 20K in lite.
  Study S163GlideStudyJvm: top1WORD 83.0% / top6WORD 95.5% (σ.25 bias
  .18); device 12/14 word-exact via the app_process MotionEvent injector
  (monkey drops MOVEs — scratchpad GlideInjector/glide_test.py is the
  only trustworthy synthetic glide tool). Settings: গ্লাইড টাইপিং
  default ON. Spec+plan in docs/superpowers/. Android 1.5.102 (2139).
- **S162 ghost-chip strip round (2026-09-01, tester proposal via user,
  approved mock variant ঙ):** the typed roman leads the strip as an
  outlined mono GHOST chip (tap = keep the English literal; never
  learned); the blue commit highlight belongs to the first NON-ghost
  chip (TypedChipPolicy — invariant 5 kept: the big blue word IS what
  space commits); chips all single-line (primary 16.5sp, the S117/S152
  two-line hint retired); S141/S142 literal dupes fold into the ghost.
  EN mode mirror: a non-English token (kemon) earns a Bangla ghost chip
  (real conversion, gated conf≥0.6 + Bengali script + !common-English) —
  tap swaps. Bottom nav labels 14sp (were 11sp, tester: "not properly
  visible"). Pins S162TypedChipPolicyTest. TESTING GOTCHA: `adb input
  text` bypasses the IME — strip tests need coordinate taps on the
  on-screen keys; Samsung Notes is the safe field. Android 1.5.101
  (2138).
- **S160 Android strip + toolbar round (2026-08-31, user: "swipe left…
  acting like spring" + approved mock ক "exactly make this tools bar
  design"):** suggestion-strip spring-back root-caused — the LaunchedEffect
  re-ran scrollToItem(0) on every recomposition while unfrozen, yanking any
  user scroll back; now a `lastSnapped` guard snaps only when NEW
  suggestions arrive. Action bar rebuilt to the approved mock
  (😊 ← → 📋 🎤 ⚙ ⋯): stickers + duplicate দাঁড়ি slots replaced by
  CursorArrowSlot pair (hold-repeat 350ms then 60ms via onCursorMove;
  mirrored Canvas vectors — ← → font glyphs render unevenly on Samsung);
  mic is the 🎤 emoji (MicEmojiSlot) in ALL THREE spots — action bar,
  expanded ToolbarRow, idle-strip pinned — accent ring only while
  listening. User feel-tested on device. Android 1.5.100 (2137).
- **S159 bangluweb tutorial redesign (2026-08-31, user: "right now its too
  confusing. Do organize all in proper manner"):** /tutorial rebuilt as one
  organized scroll — hero, LetterCardExplorer (the approved S157 card UI as
  a theme-aware web component, light + dark via .banglu-lce CSS variables),
  TryItBox live practice on the real engine, sticky chip nav, per-rule
  sections with expanders. Deployed (bangluweb 09434b9). S161 same day:
  feedback form problem select (grouped কী সমস্যা হচ্ছে? menu, 16 problems,
  shared src/lib/feedbackProblems.ts across form/API/admin; nullable
  tester_feedback.problem column applied to prod via user-run CLI DDL;
  bangluweb 056d77d).
- **S158 cross-surface tutorial round (2026-08-31, user: "did you use all
  varieties… j fola b fola r fola hosanto chandro bindu… add all this kind
  of tutorial in the web app and also all others apps"):** coverage audit
  on TutorialWords proved the fola wall (য-ফলা 44, র-ফলা 61, ব-ফলা 18,
  রেফ 32, ঋ-কার 7, ঁ 6, ৎ 7, ং 8, ক্ষ 13, জ্ঞ 7) and exposed ONE gap —
  বিসর্গ had only দুঃখ → new ঃ cap (নিঃশ্বাস, নিঃসন্দেহে, দুঃসংবাদ,
  দুঃসাহস, পুনঃপ্রকাশ; dukkhito/স্বতঃস্ফূর্ত dropped on probe evidence:
  the dictionary spells স্বতস্ফূর্ত). Curriculum now 305 words/346 pairs.
  Ports: bangluweb tutorial page gets 9 generated letter-card sections
  (S158TutorialJsonExportJvm → data/letter-card-sections.ts, TryItBox
  gives live practice; twins target their real primary); desktop
  TutorialView gets a native interactive LetterCards section (desktop
  palette, FlowRow equation); winime ControlWindow footer + extension
  popup link to bangluweb.com/tutorial (their compact layouts cannot host
  a curriculum). Root-cause fix caught by the visual check: EditorScreen's
  unconditional focus.requestFocus() crashed --tutorial launches (field
  not composed) → keyed on tutorialOpen. Desktop resources/common
  dictionary was stale 3.9.6 locally — refreshed (CI pulls the release
  asset, unaffected). Android 1.5.99 (2136), desktop 1.3.9,
  টাইপার 1.0.13.
- **S157 tutorial letter-card round (2026-08-31, user: "enrich our tutorial…
  redesign as card… all confusing, complex and conjunction words at least
  thousands… break as syllable… same to same"):** the tutorial's centerpiece
  is now a letter-card curriculum on the approved mock (scratchpad
  banglu-tutorial-vowel-mock.html): 9 family pages (স্বরবর্ণ, ৫টি বর্গ,
  অন্তঃস্থ+হ, উষ্ম, বিশেষ ড়ঢ়য়ৎংঁ), tappable keycaps, 44sp Tiro-Bangla word
  with the conjunct in terracotta, syllable equation (ot+ta+dhu+nik =
  অত্যাধুনিক), alt-spelling chips, moss twin pills (pore→পরে/পড়ে,
  taka→টাকা/তাকা, ঈগল, ঊষা, যন্ত্রণা ণ/ন, ভঙ্গি/ভগ্নি), dots+arrows+swipe.
  Research: 3,444 dictionary words bulk-verified on the real engine
  (S157BulkProbeJvm, reverseWord romans — docs/tutorial-letter-cards-s157.md
  + .tsv); curated cut = shared TutorialWords (308 words / 341 variant
  pairs), EVERY pair pinned by S157TutorialWordsJvmTest (twins pin the
  declared primary AND strip membership; splits must concat to the roman).
  Gotcha: pass-2 curation's 4-consonant-run filter silently dropped
  sh-cluster words (রাষ্ট্রপতি, সংখ্যা) — lookups must fail loudly.
  Android 1.5.98 (2135).
- **S156 chat-n-gram dictionary round (2026-08-31):** the S149 study's own
  gold corpora now feed the context model — chat_trigrams.tsv (54k rows) +
  chat_bigrams.tsv (62k rows) mined from the staged pairs (BanglaTLit +
  Vashantor-std, n≥2, Bengali-only) compile at CHAT_WEIGHT into db 3.9.7
  (trigram/bigram tables at their 120k/150k caps; CorpusBigrams takes a
  chat-file list). Measured on the re-run study: chat register 70.0→73.1%
  word-exact (top-6 75.4→79.5%), vashantor-std homographs-WITH-CONTEXT
  89.2→91.7% (plain unchanged 84.6% — the gain is purely the context
  model), WITH-CONTEXT word-exact 93.3%; dialect 70.9→69.5% cumulative
  S150-S156 drift (documented, non-target register). GOTCHA burned into
  the process: gradle does NOT see dictionary.sqlite/slim as test inputs —
  after any dictionary rebuild the walls must run with --rerun or they
  silently pass as UP-TO-DATE. All five walls + macOS runner (105/105 on
  the new 32MB slim) re-ran green. Device-smoke frame flake fixed: up to
  two top-up key bursts before frame_timing, 20-frame floor kept (S138
  rule intact). Android 1.5.97 (2134), desktop 1.3.8, টাইপার 1.0.12;
  slim + extension zips + macOS install + bangluweb vendor propagated.
- **S154 propagation round (2026-08-30):** the S150-S153 engine reached
  all six surfaces: macOS IME bundle rebuilt (runner 105/105) + installed;
  extension vendor + store zips rebuilt (uploads user-side); bangluweb
  vendor pushed (Railway auto-deploy); desktop 1.3.7 + Windows টাইপার
  1.0.11 tagged (CI cuts installers; winime CI refreshes the website MSI
  alias itself; desktop assets need the downloads-release alias refresh
  after CI).
- **S153 general English-register round (2026-08-30, user: "did you just
  fix only courier?? do general fix"):** mined every English token
  Bangladeshis actually type from the study corpora (923 tokens with ≥5
  gold-aligned occurrences and ≥60% majority rendering; english-register
  method in the study appendix), diffed the engine against the corpus
  majority (633/923 already matched), and fixed the 290 misses in three
  layers: ~56 curated loan seeds (tutorial→টিউটোরিয়াল, config→কনফিগ,
  flash→ফ্ল্যাশ…), ~28 shorthand chat defaults for band-law keys
  (but→বাট 518:15, phone→ফোন 292:40 — S81/S142 pin flips documented,
  number→নাম্বার, date→ডেট, up→আপ…), and 8 acronym Tier-P entries
  (id→আইডি reversed at 484:2 with eid→ঈদ shorthand; gp/ss/uc/bd/sms/
  html; nc→নাইস via the pre-control-rule whitelist — the trailing-c
  hasanta rule swallowed it; stale sms duplicate killed). Corpus-majority
  spelling flips: data→ডাটা, account→একাউন্ট, address→এড্রেস. Pin wall:
  S153EnglishRegisterJvmTest (83 pairs). Android 1.5.96 (2133).
- **S152 tester round (2026-08-30, Siddique Ahit's messages):** the roman
  hint on the primary strip chip 9sp-grey → 11sp near-white ("blue cell er
  vetor nicher English" was unreadable); courier → কুরিয়ার curated (lexicon
  guessed কারিয়ার — the tester's own example), cash → ক্যাশ and bank →
  ব্যাংক chat defaults (চাষ/বাঁক keep strip slots). Probe showed the rest of
  the courier register already worked. Pins: S152TesterEnglishJvmTest.
  Android 1.5.95 (2132).
- **S151 homograph/context round (2026-08-30, "do fix it"):** kothai →
  কোথায় chat default (কথাই was winning a one-point seed race; strip twin
  kept); final-ই/য় homograph twins get a guaranteed strip slot
  (`homograph_twin` promotion: hoi carries হয়, jai carries যায় — the
  context rerank always has both readings to promote); ই↔য় joined
  AIDisambiguator SWAP_RULES; the S149 harness gained a variant-exact
  metric (ো-final + ি/ী spelling twins scored as policy, not misses) and a
  WITH-CONTEXT pass measuring rerankWithContext(prev2, prev1) on gold
  context, incl. per-homograph accuracy. Pins:
  S151ContextAndSpellingJvmTest. Android 1.5.94 (2131).
- **S149/S150 Banglish-corpus round (2026-08-30):** the deep-research
  datasets (BanglaTLit, Vashantor, Socian, Chaos, SMS, PolCSBD) ran through
  the real engine (S149BanglishCorpusStudyJvm, opt-in env; report
  docs/engine-banglish-study-2026-08-30.md): clean standard Banglish 91.2%
  word-exact / 96.9% top-6, chat register 66.7% / 75.4%, 3.16 ms/word, zero
  crashes. S150 fixes from its ranked backlog: the টা clitic owns "ta"
  (documented pin flip, তা stays a strip twin); a→এ deictics ata/aita/ai;
  vowel-less shorthand onk/vlo/aktu/kno/tnx + use/nice/new join the kmon
  class; fb/mb/pc promoted to ACRONYM_OVERRIDES Tier P (id deliberately
  stays Tier S — ঈদ keeps the key, আইডি chips); হেল্প/ইউজ/নাইস/নিউ curated
  in EnglishDirectData; NEW tryEmphaticICompound layer (khub-i class:
  ekdomi→একদমই) with diphthong guard (kothai-class i is the word's own
  vowel) and junk-aware whole-word precedence — khubi stays খুবি (canonical
  tier-A owner@69) with খুবই on the strip. Pins: S150ChatRegisterJvmTest.
  Android 1.5.93 (2130).
- **S147 Android app-UI redesign (2026-08-30, user: "completely redesign like
  this mock … full app UI based on the mock theme, same to same"):** the whole
  :ui process (MainActivity home + onboarding, Settings, Tutorial) committed
  to the approved mock look (scratchpad banglu-android-mocks.html): dark plum
  #0F0E1A/#22213A, terracotta CTA #D9633F, mustard highlight #E9B84A, moss
  #3FA372. Home = brand row + state pill, two-line hero, state-aware
  three-step stepper (one CTA for the step you're on; S55 hint only after a
  failed attempt), white try-field + roman chips, keycap power cards (hot cap
  = the letter the engine decided), bottom nav হোম/শিখুন/সেটিংস/মতামত
  (bangluweb.com/feedback). First run = 4 slides (স্বাগতম welcome → বৈশিষ্ট্য features →
  কনফিউজিং-শব্দ explorer → setup stepper), animated transitions. The showcase list
  lives in shared `ShowcaseWords` and EVERY advertised (variant, word) pair —
  cards, chips, onboarding lines — is pinned by S147ShowcaseWordsJvmTest on
  the real dictionary. Engine: shassoto/shasstho/sasstho → স্বাস্থ্য seed
  aliases. Fonts bundled in res/font: Tiro Bangla (display serif, the mock's
  --bn), JetBrains Mono (romans), Noto Sans Bengali Reg/Bold (app-wide default
  via BangluComposeHost typography). Android 1.5.92 (2129).
- **S141 typed-faithful round (2026-08-29, user law: "the engine must not
  ignore what I typed — at least show it in the suggestions"):** a clean-
  reading OOV literal keeps the commit (banglu → বাংলু, not বাংলা/বাংরু);
  substitution repairs only for unclean readings (`readsAsCleanBengali`);
  Layer-6 recovery and fuzzy suffix stems are spelling normalisers only
  (`spellingSkeleton`); habit aliases cannot smuggle a vowel swap; the typed
  reading holds the last strip slot in the fuzzy band (`typed_literal`);
  open-syllable vowel twins sit at strip[1] (kri → ক্রি/কৃ, ku → কু/কূ);
  roman-prefix completions for leaf keys (banglish → বাংলাদেশ,
  `roman_prefix`). Next-word prediction bar on EVERY surface: desktop
  editor (`EditorState.predictions`, click-only), macOS IME
  (`.updatePredictions`, clickable panel rows), extension (`type:"next"`),
  bangluweb dashboard editor; the slim JSON now carries a pruned n-gram
  model (`bi`/`tri`/`uni`, SlimExporter) and the JS facade exports
  `nextWordPredictions2(prev2, prev1, limit)` + `recordNextWord`.
  Pin decisions recorded in parity-fixtures.json notes (3 P1 root-
  decomposition rows re-pinned). Test: S141TypedFaithfulJvmTest.
- **S142 English-word law (2026-08-29, user: "an exact English word returns
  its Bangla pronunciation, the English word in the suggestions — one
  behaviour, not some words yes and others no"):** `applyEnglishPronunciationLaw`
  (shared by commit wrapper and composing preview), kept deliberately
  simple: "is this a correct English word?" = the english_lexicon knows the
  4+-letter key (or the detector list / a test+er derivation); then the
  curated-seed-or-lexicon rendering is the commit whenever the pipeline read
  the key as a different Bengali word BELOW the everyday band
  (`EVERYDAY_WORD_BAND` = 75: tester → টেস্টার over টেস্টের@70, call → কল,
  gate → গেট, window/color/date …); everyday words keep the key with the
  pronunciation as a chip (name → নামে@89 invariant 6, phone → ফোনে@78 the
  S81 pin, abba → আব্বা@76, bade → বাদে@78). The rendering must be attested
  unless curated. `lookupEnglishMemo` is curated-first (EnglishDirectData:
  door → ডোর, table → টেবিল, milk → মিল্ক, engine → ইঞ্জিন, সাজেশন, টেস্টার)
  so every consumer (intent list, S131 flip, 4x rule, chips) agrees; the 4x
  rule no longer overrides DIRECT_WORD_OVERRIDES. English chip for every
  English-word key; the displaced Bengali reading keeps a strip slot. Test:
  S142EnglishWordLawJvmTest.
- **S143 English spelling rescue (2026-08-29, user: "type suggention and see
  what the engine returns" → সুজ্ঞেন্তিওন; "test with thousands of common
  English words"):** the general half of the English law.
  `applyEnglishSpellingRescue` (commit + preview, plus a late door after the
  Bengali typo layer): when nothing confidently owns the key, the nearest
  English word one slip away (`nearestEnglishWords`: edit-1 + doubled-letter
  collapse over the store's in-memory `englishKeys()` index, first letter
  preferred, attested rendering preferred) is rendered through the engine's
  OWN answer for the correct spelling (`renderEnglishWord`: curated seed →
  dictionary → lexicon) and its corrected spelling rides the strip
  (`english_correction`). Fires for English-shaped keys (`looksEnglish`),
  unclean readings, floor keys one slip from a COMMON English word
  (`CommonEnglishWords`, google-10000, 3+ letters — `EnglishDetector.
  isCommonEnglishWord`, deliberately NOT part of `isEnglish`/passthrough),
  and keys the Bengali typo layer already abandoned (distance rule: the
  Bengali repair keeps only if it reads the key within one edit). English
  renderings are final (`isEnglishRendering`: exempt from the S113 onset
  floor and the junk/typo passes — want → ওয়ান্ট, not অন্ত). Study
  `S143EnglishCorpusStudyJvm` → docs/engine-english-study-2026-08-29.md:
  8400 common words, 98.4% English-or-correct (137 residue, nearly all the
  dictionary's better loanword spellings), 94% of one-slip misspellings
  rescued. Test: S143EnglishSpellingJvmTest.
- **S144 keystroke sqlite budget (2026-08-30, Windows field report: "deleting
  words is not smooth, space lags"):** measured on the sqlite-backed JVM
  store — a keystroke issued 130–550 phonetic_index point queries, up to
  ~2,000 extended-dictionary queries and ~100 Bengali→phonetic reverse
  queries (every edit variant the typo/lattice layers probe; the 128-entry
  memo thrashed so backspaces re-queried everything). Invisible on a warm
  Mac page cache, 50–500 ms per key on an antivirus-hooked Windows disk.
  Fix at the store boundary: `JvmSqlitePhoneticIndexStore` builds three
  Bloom negative indexes (phonetic_index keys, extended phonetics, extended
  Bengali — `util/BloomFilter`) on a daemon thread with its own read
  connection (~0.6 s, boot not delayed; until ready, sqlite as before, no
  false negatives), memoizes the reverse lookup, short-circuits lexicon
  misses through the in-memory key set; `MAX_STORE_MEMO` 128 → 2048;
  `isMidWordPrefix` memoized. Result: 18 unknown-word keystrokes = 101
  sqlite queries (was 2,129 with the bloom off, thousands more before).
  Pins: `S144KeystrokeSqliteBudgetJvmTest` (≤ 40 queries/keystroke on the
  real db), `S144BloomFilterTest`. Android's own store is untouched (its
  conversions are async); Windows 1.0.10 / desktop 1.3.6 / Android 1.5.91.
- **Android** v1.5.91 (2128), db 3.9.6 — S140: engine publication is
  generation-checked + atomic under learningLock (an erase during the
  dictionary load can no longer resurface deleted words); identity
  migration never overwrites a decision. Before that S139: 1.5.85 had a clipboard
  preference key collision (Boolean switch + String payload on one key →
  ClassCastException on upgrade); keys split + PrefsMigrations; identity
  erase-before-pref invariant; lock-checked publication; store preload;
  AAB-signer check. Before that S138 re-audit follow-up (clipboard
  opt-in + private-field gating, learningLock, teardown join, cert-match
  validator, inconclusive-jank failure, extended instrumented tests). Before
  that S137 voice round (traced on
  device: Google's 2026 speech service ends paused sessions with EMPTY
  finals, restarts its hypothesis after its own endpoint, and degrades
  later utterances inside one session → one session per utterance (idle
  stop 1.5s, 250ms settle), reset-aware VoiceCarryPolicy.reconcile, mark-
  only live-region seal, deferred comma/দাঁড়ি; the trace lives in
  docs/audits/voice-trace-s137-2026-08-26.log). Before that S136 re-audit round (erase is
  generation-checked + mutex-serialized on the persistence lane and removes
  every learning key of every scope with commit results propagated;
  clipboard never learns from ANY private field, one-shot paste in
  password/OTP fields, active expiry; identity assist DEFAULT OFF and
  email-fields-only; real BANGLU_STORE_FILE keystore locked + checked;
  exact-AAB bundletool device smoke with thresholds
  (scripts/android_device_smoke.py) + androidTest erase-provider test;
  AGP 8.9.3; account split OUT of the launch AAB (-PbangluAccount=true);
  dictionary pin android-keyboard/dictionary.sha256; pinned CI actions +
  wrapper checksum; lifecycle PAUSE/STOP on hide + full teardown; ICU
  grapheme fallback for emoji backspace; low-storage dictionary notice with
  auto-retry; durable crash record + ApplicationExitInfo). Before that S135 production-readiness round
  (docs/audits/audit-android-production-readiness-2026-08-26.md verified
  true and fixed: cross-process "clear learned data" via BangluPrefsProvider
  METHOD_ERASE_LEARNING — the :ui process has NO engine storage, erase only
  in the keyboard process; ClipboardHistoryPolicy — sensitive-clip/-field
  guard + one-hour expiry; dedicated `identity_assist` switch for saved
  emails; semantic onClick/customActions on every key for TalkBack/Switch
  Access; release script asserts clean tree + AAB embedded revision == HEAD
  + owner-only signing files; privacy policy rewritten from the artifact).
  S133 voice/tap fixes. Before that: S110-S113 book-register block
  (book-corpus study harness + literary pin wall; plural-suffix and samasa
  composition; khanda-ta ৎ/ত্ twin fold; book_lexicon.tsv ingestion; OOV
  onset-integrity honesty floor; ato-class arbitration). Before that: S109
  vowel-onset round, S108 production-hardening round
  (cross-surface dictionary version gate via `DictionaryVersion.REQUIRED`,
  desktop engine-lane + store error handling, atomic learned.json, macOS
  boot-failure states). Earlier: (S59: মূর্ধন্য-ষ manual aliases) (S56: tester round — URI-field conversion,
  voice liveness watchdog + word-level partial diff, likh preview parity,
  emphatic-o layer, shw→ssh chat aliases, screenshot loanword; S57: emoji
  overhaul — fake procedural GIFs removed, WhatsApp-style two-row panel,
  ~90 everyday Bengali phrases on the বাক্য tab (BanglaPhrases.kt), 330+
  three-script search keywords (EmojiKeywords.kt)) — pre-launch; Play upload
  is the pending user action (`releases/banglu-1.5.39-2076.aab`).
- **Desktop editor (বাংলু এডিটর)** v1.3.3 — SHIPPED PUBLICLY (S48–S50):
  installers for macOS/Windows/Linux on the GitHub release `desktop-v1.3.3`,
  download page live at https://www.craftsai.org/products/banglu.
- **Browser extension** (S47) — Chrome/Firefox zips built
  (`browser-extension/banglu-*.zip`), store uploads pending (user).
- **macOS input method (বাংলু ইনপুট মেথড)** (S51) — built + installed on the
  dev Mac, awaiting the user's manual acceptance gate (app matrix).
- **iOS** — future phase; the old `ios-keyboard-engine` Swift scaffold is
  NOT a base for anything (seed-only, never paritied).

---

## 2. Repository Map

```
banglu-kmp/
├── CLAUDE.md                  ← this file
├── dictionary.sqlite          ← dev copy of compiled db — JVM TESTS LOAD THIS
│                                 (cp from compiler output after every rebuild!)
├── shared/                    ← Kotlin Multiplatform ENGINE (the brain)
│   └── src/
│       ├── commonMain/kotlin/com/banglu/engine/
│       │   ├── SmartEngine.kt          ← conversion pipeline (~5000 lines)
│       │   ├── SmartEngineAdapter.kt   ← singleton facade: init, learning,
│       │   │                              preferences, engine swap
│       │   ├── dictionary/  SeedData*.kt (≈6.5K curated words + phonetics),
│       │   │                EnglishDirectData, WordCategory
│       │   ├── rules/       CleanTransliterator, pattern tables
│       │   ├── platform/    PhoneticIndexStore (interface), InMemory impl,
│       │   │                PlatformStorage, DictionaryLoader
│       │   ├── disambiguation/  ত/ট দ/ড ন/ণ শ/ষ resolution
│       │   ├── ai/          bigram/trigram context rerank (on-device)
│       │   └── util/        ReverseTransliterator (Bengali→roman), nukta fold
│       ├── commonTest/      seed-only engine tests (no store)
│       ├── jvmTest/         FULL-STORE tests — the real regression wall:
│       │                    parity pins, S26/S27/S33/S34/S35/S43 round tests,
│       │                    ConjunctSolutionRoundJvmTest.engine = shared
│       │                    engine loaded from ./dictionary.sqlite
│       ├── jvmMain/         JvmSqlitePhoneticIndexStore + loader (desktop
│       │                    editor AND jvmTest share these)
│       ├── jsMain/          BangluWebEngine.kt — @JsExport facade (initSeed,
│       │                    attachSlimDictionary, convert, suggestions,
│       │                    instantPreview, applyLearnedWords, recordPick).
│       │                    Consumed by browser-extension AND macos-ime.
│       └── jsTest/          S45 web-parity wall + S51 learning tests
│                            (gate: ./gradlew :shared:jsNodeTest)
├── shared/banglu-slim.json      ← S45 slim in-memory dictionary (22MB;
│                                  2.1MB gz) for JS surfaces — regenerate via
│                                  dictionary-compiler --slim; untracked
├── android-keyboard/          ← the ANDROID APP (IME + UI activities)
│   └── src/main/kotlin/com/banglu/keyboard/
│       ├── BangluIMEService.kt     ← THE keyboard service (default process,
│       │                              offline; ~3000 lines; hot path)
│       ├── ComposeKeyboardView.kt  ← all key layouts/gestures (Compose)
│       ├── SmartEngine hookups: AndroidDictionaryLoader (asset copy + lite
│       │   gating), SqlitePhoneticIndexStore, AndroidStorage (learned words)
│       ├── MainActivity.kt         ← home: hero + live try-it editor (:ui)
│       ├── SettingsActivity.kt     ← settings incl. theme/height/font/learning
│       ├── TutorialActivity.kt     ← the guide (steps + full phonetic mapping)
│       ├── VoicePermissionActivity, BangluPrefsProvider, BangluProcessGuards
│       └── build.gradle.kts        ← versionCode/Name, perf buildType,
│                                      verifyImePrivacyBoundary task
├── android_account/            ← dynamic feature: auth/billing — :ui process
│                                  ONLY, never loaded in the IME process
├── dictionary-compiler/         ← JVM tool that builds dictionary.sqlite
│   ├── src/.../DictionaryCompiler.kt   (db schema, version string)
│   ├── src/.../PhoneticIndexBuilder.kt (HABIT_RULES alias chains, tiering,
│   │                                    chh-promote pass)
│   └── data/                    corpus TSVs, chat_lexicon.tsv,
│                                english_lexicon_overrides.tsv (CMU fixes)
├── desktop-app/                 ← বাংলু এডিটর (S48–S50): Compose Desktop app,
│   │                              FULL engine (JVM + 143MB sqlite via
│   │                              resources/common/, gitignored)
│   ├── src/main/kotlin/com/banglu/desktop/
│   │   ├── Main.kt              window/tray/hotkey wiring; --tutorial arg
│   │   ├── Hotkey.kt            ⌘⇧B/Ctrl+Shift+B via jkeymaster (OS hotkey
│   │   │                        API — NO permissions; never JNativeHook)
│   │   ├── Paste.kt, Storage.kt (FileStorage → ~/.banglu/learned.json)
│   │   └── editor/              EditorState (pure-Kotlin state machine),
│   │                            EditorScreen (Compose UI), EngineFacade,
│   │                            DraftStore (~/.banglu draft+prefs, atomic),
│   │                            DocxWriter (hand-written OOXML), Printer
│   │                            (Java2D — the ONLY Java path that shapes
│   │                            Bangla; never add a PDF library),
│   │                            TutorialView (Android curriculum ported),
│   │                            EditorTheme (brand palette + bundled Noto
│   │                            Sans Bengali v2.003, OFL)
│   └── src/test/                29+ JVM tests on the REAL dictionary incl.
│                                WYSIWYG pin tests
├── macos-ime/                   ← বাংলু ইনপুট মেথড (S51): real InputMethodKit
│   │                              input method — type Bangla in ANY app.
│   │                              Swift SPM, NO XCODE on this machine
│   │                              (CommandLineTools only — never add
│   │                              .xcodeproj; `swift test` FAILS here, the
│   │                              gate is `swift run BangluCoreTestRunner`)
│   ├── Sources/BangluCore/      EngineJS (shared JS engine hosted in
│   │                            JavaScriptCore), BackgroundEngine (seed-echo
│   │                            until the ~11s slim load finishes on a
│   │                            serial queue — JSC is NOT thread-safe),
│   │                            Composer (pending-space দাঁড়ি model — IMK
│   │                            can't edit committed text), LearnedStore
│   │                            (editor-shared ~/.banglu brain), AppCompat
│   │                            (per-app full/plain mode table)
│   ├── Sources/BangluIME/       IMKInputController glue + caret-anchored
│   │                            NSPanel candidate UI (custom panel is
│   │                            PRIMARY; IMKCandidates seam kept)
│   ├── Tests/BangluCoreTestRunner/  THE test gate (83 checks, real engine)
│   ├── scripts/build-engine.sh  gradle JS build + esbuild IIFE bundle
│   └── Makefile                 make install → ~/Library/Input Methods/
│                                (ad-hoc signed; Developer ID = v2/public)
├── browser-extension/           ← Chrome/Firefox extension (S47, MV3):
│   │                              inline typing in input/textarea + popup
│   │                              converter, SAME shared engine as JS
│   ├── build.sh                 pulls shared JS artifact + slim json into
│   │                            vendor/, esbuild bundle
│   └── banglu-chrome.zip, banglu-firefox.zip  (store uploads pending)
├── .github/workflows/desktop-release.yml  ← CI: DMG/MSI/DEB on 3 runners,
│                                  triggered by desktop-v* tags; downloads
│                                  dictionary.sqlite from the GitHub release
│                                  tagged `dictionary` (150MB asset)
├── ios-app/, ios-keyboard-engine/  ← iOS scaffold (future phase; market gap:
│                                     SwiftKey has NO iOS transliteration.
│                                     NOT a base — seed-only, unparitied)
├── banglu-web (SIBLING REPO ../banglu-web)  ← wordlist source for compiler +
│                                     web app — FULLY on the shared JS engine
│                                     since S54 (lib/banglu-engine vendor +
│                                     loader.ts; old TS engines decommissioned
│                                     — see its CLAUDE.md engine-law section)
├── design/play-store/           ← STORE-LISTING.md (paste-ready), PRIVACY-
│                                  POLICY.md (canonical), DATA-SAFETY-FORM.md,
│                                  screenshots-1.5.24/, icons
├── releases/                    ← versioned .apk (testers) + .aab (Play),
│                                  gitignored artifacts
├── docs/                        ← engine research studies (register, conjunct)
└── scripts/
```

---

## 3. Architecture

### 3.1 Process & privacy architecture (NON-NEGOTIABLE)

```
┌────────────── default process (OFFLINE, no network) ──────────────┐
│ BangluIMEService → ComposeKeyboardView                            │
│        │ keystrokes                                               │
│        ▼                                                          │
│ SmartEngineAdapter (singleton) → SmartEngine (seed + swapped full)│
│        │                              │                           │
│ AndroidStorage (learned words)   SqlitePhoneticIndexStore         │
│ SharedPreferences                dictionary.sqlite (143MB asset)  │
└───────────────────────────────────────────────────────────────────┘
┌────────────── :ui process (may use network) ──────────────────────┐
│ MainActivity / Settings / Tutorial / VoicePermission / account    │
└───────────────────────────────────────────────────────────────────┘
```

- `verifyImePrivacyBoundary` (android-keyboard/build.gradle.kts) greps the IME
  hot-path files for forbidden tokens (URL, BillingClient, auth classes) and
  fails the build. It runs on preBuild. Never weaken it.
- Voice typing uses the OS SpeechRecognizer with explicit first-use disclosure.

### 3.2 Conversion pipeline (SmartEngine.convertWord)

Wrapper (typo correction + English arbitration + intent flips)
→ `convertWordRaw` layers, in order:
1. DIRECT_WORD_OVERRIDES, then MOBILE_SHORTHAND_OVERRIDES (kmon, hm, ok, vdo,
   rain, tmra… — conf 0.999, also mirrored in the instant preview)
2. tryNegationCompound — attached না/নাই/তো/নে (bolbone→বলবোনে); guards:
   whole-word store precedence, stem attestation (validator OR corpus
   containsWord), prefix conf ≥0.9
3. store (sqlite phonetic_index) exact → dictionary/seed → compound split
   (bujteparcina→বুঝতে পারছিনা) → skeleton/fuzzy → recovery → rule fallback
4. context rerank (user bigrams > corpus trigrams, observed-triple gated)

**Ranking law:** index rows order by (tier ASC, priority ASC, freq DESC).
tier 0 = suggestible corpus words; priority 0 = canonical romanization owner,
1 = habit alias. "Canonical owner wins" — with the S33 exception: an archaic
চ্চ owner is demoted when a strictly-more-frequent চ্ছ twin shares the key
(compiler `promoteModernChhOverArchaicCc`).

### 3.3 IME hot path (S28/S32/S29 architecture — keep it this way)

- EVERY keystroke: sync rule-only `convertForInstantPreview` (sub-ms, zero
  I/O, test-enforced) → async refine on Dispatchers.Default (buffer==snapshot
  guarded, job-cancel coalescing).
- Space commit: cached async conversion if ready; else commit the VISIBLE
  preview instantly and reconcile off-thread (replace only while editor still
  ends with what we committed; session token + buffer guards). NEVER convert
  synchronously on the UI thread.
- Cold start: seed build + store attach + AndroidStorage all off main
  (view shows in ~180ms). Instant preview returns raw input until seeds land.
- StrictMode (debug builds) flags any main-thread disk I/O — treat new
  violations as bugs.

### 3.4 Learning system (poisoning-hardened)

- Passive space-commits of the engine's own primary are NEVER recorded (S26).
- No learning at all until the full dictionary load completes (S34) —
  `dictionaryReadyForLearning` gate in the service.
- Load heal: a learned entry equal to the raw transliteration of its own key
  is skipped when it's not a corpus word AND the pipeline resolves elsewhere
  (S34, `isLearnedEntryTrusted`); skipped ≠ deleted (F5b reversibility).
- ENGLISH_PRIMARY_INTENT flips and curated loanwords are preference-immune.

### 3.5 Dictionary build

```
../banglu-web/public wordlists + corpus TSVs + SeedData
        │  ./gradlew :dictionary-compiler:run \
        │     --args="<abs>/banglu-web/public <abs>/dictionary.sqlite"
        ▼
dictionary.sqlite (words, phonetic_index ~1.35M rows, english_lexicon,
                   trigram_triples, disambiguation)  — version gate (S108):
                   shared/.../DictionaryVersion.REQUIRED is the ONE bump
                   point — the compiler stamps it, Android/JVM stores and
                   the JS attachSlimDictionary all refuse a mismatch, and
                   build.sh/build-engine.sh assert it before bundling
        ▼  cp to android-keyboard/src/main/assets/dictionary.sqlite
        ▼  cp to ./dictionary.sqlite   ← REPO ROOT — JVM tests read THIS
```
HABIT_RULES compose in table order over aliases-produced-so-far; a later rule
never re-triggers an earlier one (order bugs are silent — S27 lesson).

### 3.6 Lite mode (low-RAM phones)

`liteModeEnabled || isLowRamDevice || memoryClass < 256` → loader skips the
476K validator list, extended dict, freq scores, disambiguation, bigrams.
Sqlite store + seeds remain → conversions stay store-backed. Any convertWord-
wrapper feature must either work without the validator or NOT be mirrored
into the composing preview, else lite preview/commit diverge (S26b law).

### 3.7 Multi-platform engine delivery (ONE engine, many hosts)

The rule that makes five surfaces manageable: **conversion behavior lives in
`shared` (Kotlin) and NOWHERE else.** Platforms differ only in how they host
the engine and which dictionary tier they carry:

| Surface            | Engine host                     | Dictionary            |
|--------------------|---------------------------------|-----------------------|
| Android IME        | JVM/ART, adapter singleton      | full sqlite (143MB), lite fallback |
| Desktop editor     | JVM (Compose Desktop, jpackage) | full sqlite via JDBC  |
| Browser extension  | Kotlin/JS in the page/worker    | slim JSON (22MB mem)  |
| macOS input method | Kotlin/JS in JavaScriptCore     | slim JSON (22MB mem)  |
| Web (banglu-web)   | Kotlin/JS in page + Node routes | slim JSON (22MB mem)  |

- JS artifact: `./gradlew :shared:jsBrowserProductionLibraryDistribution` →
  `shared/build/dist/js/productionLibrary/banglu-engine.js` → esbuild bundle
  (extension: ESM; macOS IME: IIFE `--global-name=BangluNS`; banglu-web
  vendors the raw library + loader.ts). JS access path:
  `(ns.com ?? ns).banglu.engine.BangluWebEngine`. S54 exports the full web
  surface: parse, convertWithContext, suggestionsWithContext,
  compositionPreview, nextWordPredictions, addCustomWord.
- Parity walls: JVM = `:shared:jvmTest` (475) on ./dictionary.sqlite;
  JS = `:shared:jsNodeTest` (379, incl. S45 web-parity pins); macOS IME =
  `swift run BangluCoreTestRunner` (83 checks incl. WYSIWYG pins on the real
  JSC-hosted engine); desktop = `:desktop-app:test` (30) on the real sqlite.
- Learning writes go through `SmartEngine.addWord`, which is gated by
  `isPlausibleDynamicMapping` (anti-poisoning: key must phonetically overlap
  the reverse-transliteration of the Bengali). NEVER bypass it — learned.json
  is user-editable; real picks pass it by construction. Test fixtures must
  use plausible pairs (jbo→যাবো class), never junk keys.

### 3.8 Desktop editor (বাংলু এডিটর — S48–S50 architecture)

- `EditorState` = pure-Kotlin typing state machine (no Compose imports),
  JVM-tested keystroke-by-keystroke on the real dictionary. UI renders
  `display`, routes every field change through `applyEdit`, async engine
  refinement lands via generation-guarded `refine` (LaunchedEffect keyed on
  `generation` — the cancellation IS the stale-result guard).
- WYSIWYG: space commits EXACTLY the visible forming word; double-space dari;
  digits ০-৯; popup picks teach only non-primary choices; click any committed
  word to fix (the AI seam: wordRangeAt/candidatesForCommitted/
  replaceCommitted — future AI proposes the same "swap segment" ops).
- Never lose text: 2s-debounce autosave + DisposableEffect flush on window
  dispose + DraftFlush hook before tray-quit. All ~/.banglu writes are
  tmp + atomic replace (renameTo silently fails on Windows — always
  Files.move REPLACE_EXISTING).
- Packaging landmines (each cost a broken install): shared jvmTarget PINNED
  to 17 (Gradle daemon is JBR 21 → class-file 65 crashes the Temurin-17
  jpackage runtime); jpackage needs modules java.sql/instrument/management/
  jdk.unsupported; never reinstall from a stale mounted DMG volume.
- Exports: docx = hand-written OOXML with w:cs complex-script fonts (Word
  shapes Bangla itself); PDF = ⌘P → OS dialog via Java2D (direct-PDF Java
  libraries CANNOT shape Bengali conjuncts — never add one).

### 3.9 macOS input method (বাংলু ইনপুট মেথড — S51 architecture)

- IMK app in `~/Library/Input Methods/`; marked text shows the live-forming
  Bangla; commits via insertText. No click-to-fix (committed text belongs to
  the host app — platform contract, same as Avro/Pinyin).
- **Pending-space দাঁড়ি model** (IMK can't edit committed text): space after
  a word commits the word and HOLDS the space; next space → `। `; a letter →
  `" "`; tight punctuation (`,` `।` `?` `!` — tested on the MAPPED char, `.`
  maps to `।` first) swallows it; Enter/Tab/focus-loss drop it.
- **BackgroundEngine**: JSC context lives on a dedicated serial queue (JSC is
  not thread-safe); slim load takes ~11s, so until `ready` the IME echoes raw
  input (Android S29 cold-start pattern). Never call EngineJS off its queue.
- Per-app compat: `appcompat.json` maps bundle IDs to `plain` mode (no marked
  text; preview lives in the candidate panel only; commits via insertText) —
  the escape hatch for misbehaving Electron hosts.
- Distribution v1 = ad-hoc signing, dev Mac only. Public = Apple Developer
  ID ($99/yr) + notarization — a deliberate later decision.

---

## 4. How we work (S-rounds)

Tester report → reproduce → JVM probe on the real store → root-cause →
targeted fix at the RIGHT layer (shorthand < seed < habit rule < engine
logic < compiler pass) → regression test named `S<NN>...JvmTest` → full
suites (`:shared:jvmTest :shared:testDebugUnitTest`, 470+ green) → perf build
on device → screenshot-verified → version bump → release artifacts → commit
with S-number → tag → push. One S-round = one commit = one story.

### Build variants
- `assembleDebug` — logging + StrictMode; slow; never judge feel on it.
- `assemblePerf` — R8 + DEBUG SIGNATURE: installs over debug/perf on the dev
  phone, learned data survives. THE variant for typing-feel testing.
- `assembleRelease`/`bundleRelease` — release keystore (`banglu-release.jks`,
  gitignored, exists ONLY on this laptop — must stay backed up off-machine).
  Artifacts → `releases/banglu-<ver>-<code>.apk|.aab`.

### Device/emulator gotchas (cost hours if forgotten)
- `adb install -r` over the LIVE IME leaves the system binding stale (no
  process, keyboard won't appear): fix `ime disable` + `enable` + `set`.
- `am force-stop` on the IME makes Android fall back to the OEM keyboard —
  re-run `ime set` before testing.
- Emulator low-end profile: `adb root; setprop dalvik.vm.heapgrowthlimit 128m`
  (→ lite mode; 512m → full). Resets on reboot.
- adb shell inside loops eats stdin → append `</dev/null`; zsh needs `${=var}`
  for word splitting.
- SettingsActivity/TutorialActivity are NOT exported — enter via MainActivity.

### Versioning & releases
versionCode/versionName in android-keyboard/build.gradle.kts; bump BOTH every
shippable change; tag `v<versionName>`; push main + tag. db changes bump the
compiler version string AND REQUIRED_DB_VERSION together.

### Per-platform build & test gates (run before ANY "done" claim)
- Android + engine: `./gradlew :shared:jvmTest :shared:testDebugUnitTest`
- JS surfaces: `./gradlew :shared:jsNodeTest` (then rebuild the library
  distribution if a JS consumer ships)
- Desktop: `./gradlew :desktop-app:test` then `:desktop-app:packageDmg`;
  install via ditto from the app image (never a stale mounted DMG);
  Windows/Linux installers come from CI (`git tag desktop-vX.Y.Z && git push
  origin desktop-vX.Y.Z` → artifacts on the Actions run; attach to a GitHub
  release for public URLs)
- macOS IME: `cd macos-ime && swift run BangluCoreTestRunner` (83 checks;
  **`swift test` does NOT work on this machine** — no XCTest in
  CommandLineTools, and the CLT Swift Testing helper silently runs zero
  tests; the runner is the only trusted gate) then `make install`
- Extension: `./browser-extension/build.sh` after any engine change

### Distribution surfaces (where users get Banglu)
- Desktop installers: GitHub release `desktop-v1.3.3` on Shahabul87/banglu-kmp
  (.msi/.dmg/.deb, permanent CDN URLs). The 150MB dictionary asset for CI
  lives on the release tagged `dictionary`.
- Public download page: https://www.craftsai.org/products/banglu — source is
  the SIBLING firm repo `~/myprojects/bdaiwebfirm/bd-ai-web-firm` (Next.js +
  velite; product schema has an optional `downloads:` list — new versions
  only edit `content/products/banglu.mdx`; that repo uses PR workflow, merge
  = production deploy).
- Android: Play Console (pending user upload). Extension: Chrome Web Store /
  Firefox AMO (pending user upload).

---

## 5. Invariants (breaking any of these is a production incident)

1. Keystroke path: no sync dictionary/SQLite/disk work on the main thread.
2. WYSIWYG: composing preview and space-commit must agree (full AND lite).
3. No learning from seed-window commits; never learn a preview the engine
   didn't rank first.
4. IME process stays offline; account/billing stays in :ui.
5. Suggestion strip[0] IS the commit contract (S19).
6. Never break: kacci→কাচ্চি (dish), jos→জোস (slang default since S100,
   was জস)/hoise/dibi defaults (deliberate),
   kassi→কাচছি (standard orthography), name→নামে class stays Bengali.
7. Parity pin tests exist for a reason — a "fix" that flips a pin needs a
   documented decision, not a test edit.
8. New Prisma-style destructive ops don't exist here, but the same spirit:
   never delete learned-word storage; skip-on-load is the only sanitation.
9. Conversion behavior lives ONLY in `shared` — no platform re-implements
   rules (the old ios-keyboard-engine and the two decommissioned banglu-web
   TS engines are cautionary tales, not patterns; banglu-web has been fully
   on the shared engine since S54).
10. `~/.banglu/learned.json` is the ONE learning brain for desktop + macOS
    IME — rows are `{p,b,f,t}` exactly (Storage.kt is the source of truth);
    every writer uses read-fresh → tmp → atomic replace.
11. `isPlausibleDynamicMapping` (the addWord anti-poisoning guard) is never
    bypassed — not in code, not in tests.
12. Desktop/IME privacy = same law as Android: no network entitlement, no
    sockets, typing never leaves the machine.
13. **No fix ships on partial evidence (user law, 2026-08-30: "do not
    create mis engine behaviour while fixing others — always test full
    engine behaviour with all the tests"):** every engine or surface
    change runs the FULL wall suite before any "done" claim — ALL of
    `:shared:jvmTest :shared:testDebugUnitTest :shared:jsNodeTest
    :desktop-app:test :windows-ime:test` (plus the macOS runner whenever
    its JS bundle is rebuilt), never just the round's own new test. A
    change that flips an EXISTING pin is a documented decision recorded
    in the pin itself (corpus/tester evidence cited), never a silent
    test edit — and the S150-S153 rounds are the case law: the full
    walls caught শান্তি→শান্তই collateral, the S81 phone pin, and the
    stale sms duplicate that a new-test-only run would have shipped.

---

## 6. Where to look things up

- Round-by-round history + lessons: `git log --oneline` (S13…S51 messages are
  mini design docs) and the auto-memory file `banglu-ship-backlog.md`.
  Recent rounds: S45 slim dictionary + JS parity, S47 browser extension,
  S48–S50 desktop editor (spec + plan in docs/superpowers/), S51 macOS input
  method (spec + plan in docs/superpowers/).
- Design specs & implementation plans: docs/superpowers/specs/ and
  docs/superpowers/plans/ (the editor and macOS IME were built plan-driven
  with per-task adversarial review — the plans double as architecture docs).
- Engine architecture reference (block diagrams of the 7-layer pipeline,
  hot path, learning/erase, voice model, dictionary build):
  docs/architecture/engine-architecture.md.
- Engine research method & corpus harness: docs/engine-*-study-*.md.
- Store submission pack: design/play-store/ (listing, privacy, data safety,
  screenshots). Privacy policy live at
  https://shahabul87.github.io/banglu-privacy-policy/ (source: PRIVACY-POLICY.md).
- Pending strategic items: macOS IME manual gate + one-click-assistant
  verification + public signing decision, Play upload (user-side), extension
  store uploads (user-side), banglu-web src/engine/smart deletion after its
  uncommitted dictionary-override WIP is harvested into the compiler data,
  corpus archiving, iOS phase, trigram quality round, per-word "never learn
  this word" control (Release-A candidate).
```
