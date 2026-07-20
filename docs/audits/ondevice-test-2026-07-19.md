# On-device test: Banglu Keyboard v1.5.35 (2072) perf — 2026-07-19

Environment: Samsung Galaxy S22 (SM-S901W, r0q), Android (One UI), full-dictionary mode,
wifi connected, battery 100%. Driver: adb soft-key taps on the real IME (agent-driven),
evidence in `ondevice-test-2026-07-19/`.

## Summary verdict

Typing core: PASS — golden set 12/12 including S52 acronym/English layer and S55 WYSIWYG
parity words, full-mode compound split, dari, digits, trigram next-word.

Voice: **FAIL — dead-mic state reproduced 2/2** on this device. The S55 ladder survives the
first NO_SPEECH restart, but the third recognizer session receives
`LANGUAGE_PACK_ERROR (code 12)`; the mic opens for ~75 ms and closes, while the keyboard
keeps showing the "বাংলায় বলুন" listening chip indefinitely (observed 90+ s). The ✕ button
does recover the keyboard.

## Golden-set table (typed via real soft-key taps, committed by space)

| input | expected | got | pass |
|---|---|---|---|
| kemon | কেমন | কেমন | ✅ |
| acho | আছো | আছো | ✅ |
| issa | ইচ্ছা | ইচ্ছা | ✅ |
| korsi | করছি | করছি | ✅ |
| golp | গল্প | গল্প | ✅ |
| bujteparcina | বুঝতে পারছিনা | বুঝতে পারছিনা | ✅ (full mode) |
| ssc | এসএসসি | এসএসসি | ✅ |
| phd | পিএইচডি | পিএইচডি | ✅ |
| callback | কলব্যাক | কলব্যাক | ✅ |
| motivation | মোটিভেশন | মোটিভেশন | ✅ |
| kacci | কাচ্চি | কাচ্চি | ✅ |
| double-space | । | কাচ্চি। | ✅ |
| digits ১২৩ | ১২৩ | ১২৩ | ✅ |

Also observed: undo chip after bujteparcina commit (বুজতেপার্ছিনা/bujteparcina) correct;
next-word prediction after কাচ্চি = বিরিয়ানি (trigram context working); auto-capitalize /
double-space / suggestion / learning toggles all present and ON in settings.

## Finding F-ONDEVICE-001 [P1] Voice dead-mic via LANGUAGE_PACK_ERROR 12 on third session

- Area: voice
- Frequency: 2/2 (identical logcat signature both runs)
- Repro:
  1. Banglu keyboard up (toolbar visible), wifi on, bn-BD offline pack NOT installed
     (Samsung S22 — MDD does not support bn-BD: `SodaLPDirGenerator: Returning no LP`).
  2. Tap mic. Say nothing.
  3. t+0s session 1: mic opens, SODA offline fails error 12 but online leg listens (OK).
  4. t+5s `NO_SPEECH_DETECTED` → IME restarts (session 2) — mic opens (OK).
  5. t+5.6s session 2 is CANCELLED client-side after ~0.6 s (why? — root-cause target).
  6. t+5.7s session 3 starts; service reports **error type LANGUAGE_PACK_ERROR code 12**;
     mic opens ~75 ms then closes.
  7. From then on: no further sessions, no recording (`appops RECORD_AUDIO` shows last
     access duration +75ms), keyboard chip still shows "বাংলায় বলুন" with dots — user
     believes it is listening. Observed stuck ≥90 s.
- Expected: error 12 maps to a visible terminal state ("অফলাইনে বাংলা ভয়েস প্যাক নেই" or
  retry-online), never a silent listening chip with a closed mic.
- Actual: chip stays in listening state; mic closed; no message.
- Evidence: voice-session1-logcat.log, voice-session-tail-logcat.log,
  voice-stuck-35s-mic-closed.png (chip listening, no status-bar mic indicator),
  appops output in session transcript.
- Note: perf build strips IME logging (R8), so BangluIME's internal decisions are not
  visible in logcat — reproduce on a debug build to trace VoiceSessionPolicy's actual
  branch. The 0.6 s client-side CANCELLED on session 2 is the anomaly that precedes the
  bad state and likely the root cause (double restart racing?).
- Recovery: ✕ closes the panel and typing resumes normally.

## Not tested / needs deeper look

- Actual speech recognition (agent cannot speak); user should dictate once on wifi.
- Airplane-mode voice ladder; battery-saver voice; low-RAM lite mode on this device.
- Learning round-trip (pick non-primary twice → primary) — not exercised this session.

---

## S56 addendum (2026-07-20) — tester round fixes, emulator-verified

Tester report: Google/Chrome search bar no conversion; likh → "likhy" preview;
screenshot → garbage; ashiko dropped the typed o (wanted আশিকো + আশিকও chips);
voice deletes previous sentence mid-dictation.

All five root-caused and fixed in v1.5.36 (2073) / db 3.8.6:

| issue | root cause | fix layer |
|---|---|---|
| omnibox raw Latin | Chrome omnibox = TYPE_TEXT_VARIATION_URI (0x80011); shouldUseRawCommitMode raw-gated URI | URI removed from raw list; uriInputMode suppresses দাঁড়ি behaviors only |
| likh → লিখয় preview | extended_dictionary stale twin লিখয়@68 > লিখ@60 promoted by the composing lattice; commit path used store-canonical লিখ | S56 store-precedence clamp in convertForComposingCore |
| screenshot → স্ক্রিন শত | compound split before lexicon; শত="hundred" | english_lexicon screenshot→স্ক্রিনশট + ENGLISH_PRIMARY_INTENT |
| ashiko → আশিক | no store key; fuzzy dropped the o | tryEmphaticOCompound (negation-compound guards, attestation-gated) + paired-spelling strip promotion |
| bissokap → বিস্তারকারী | no ss-spelling aliases for শ্ব words | compiler shw→ssh habit rule (bisso/bissokap/bissas classes) |
| voice sentence wipe | whole-live-region replace on non-prefix hypothesis; stale cross-session auto-committed prefix | VoicePartialDiff word-level law (unit-pinned) + per-session prefix clear |
| F-ONDEVICE-001 dead mic | one early callback disarmed the start watchdog; binding wedged silently | rolling liveness deadline (12 s) + capped destroy/recreate recovery |

Emulator (Pixel 7 API 34) evidence: s56-omnibox-futbol-bissokap-fixed.png
(Chrome omnibox: ফুটবল বিশ্বকাপ composing), s56-likh-preview-fixed-fullmode.png,
s56-ashiko-emphatic-fullmode.png (আশিকও primary). Lite mode (192m heap)
verified separately: emphatic layer correctly inert, preview==commit==আশিক.

Walls at close: :shared:jvmTest 519 green (S56TesterRoundJvmTest 9 + VoicePartialDiffTest 9
in android module), :shared:jsNodeTest green, :desktop-app:test green (fresh run),
BangluCoreTestRunner 83/83. On-PHONE voice signature check still pending
(phone was disconnected) — run `adb logcat -s BangluIME` on a debug build
while dictating to confirm the liveness watchdog + partial-diff behavior live.
