# S55 — Audit-Fix Round (production-readiness P0/P1s)

> Inputs: docs/audits/audit-android-keyboard-2026-07-16.md, docs/audits/audit-banglu-web-2026-07-17.md, .superpowers/sdd/voice-trace-report.md. Executed via superpowers:subagent-driven-development.

**Goal:** clear the audit P0s/P1s: (1) preview==commit for wrapper-layer features (lexicon rescue + learned preference) on every surface; (2) voice typing never shows a dead listening state and handles offline honestly; (3) web surface gaps (dari, English chip, API validation) + Android novice-guide wording.

## Global Constraints
- Invariant #1 (no sync disk on keystroke path) and #2 (WYSIWYG) both bind every change; lite mode must stay preview==commit (validator-gated mirroring: if the wrapper layer can't run, the preview must not pretend it did).
- Never weaken a pin; flipped pin = escalate.
- Audit evidence is the repro contract: each fix cites the finding ID it closes and adds a regression test pinning the exact audit case.
- Gates: full walls per surface as in CLAUDE.md §per-platform gates. Push after green (standing auth).

## Task 1 — Engine: mirror wrapper layers into the composing path (closes F-ANDROID-001/002/003, F-WEB-001/002)
- In `shared`: the composing-preview functions (`convertForComposing`, `getCompositionPreview` — trace exactly where Android's async refine and the web's preview land) must apply, in order and behind the SAME gates as convertWord's wrapper: (a) applyUserPreference (learned/explicit picks — no validator needed), (b) ENGLISH_PRIMARY_INTENT (already mirrored per S26 — verify), (c) the junk-path english-lexicon rescue (validator-gated — mirrored ONLY when validator.isLoaded(), matching the commit path's own gate so lite/slim stay consistent).
- TDD (S55ComposingParityJvmTest): callback/motivation → composing preview == convertWord == কলব্যাক/মোটিভেশন; learned-pick case: after onWordSelected(ki→কী, explicit), composing preview == কী == commit; lite simulation (engine without validator): preview == commit == whatever the unrescued pipeline gives (equality is the law, not the pretty word); existing WYSIWYG + parity pins untouched.
- jsNodeTest: compositionPreview("callback") == convert("callback") on the slim tier (both sides same — slim has english rows AND validator absent: determine what the commit path does on slim for callback — the audit shows web COMMITS কলব্যাক (via suggestions/strip[0] promotion or the rescue running validator-free on slim?) — trace it and make the test pin whatever equality is true).
- Surfaces pick it up by artifact rebuild only (Android refine + web preview call these functions).

## Task 2 — Voice: watchdog + honest offline ladder + async partials (closes F-ANDROID-006 + the tester report; evidence: voice-trace-report.md hypotheses 1-3 all confirmed/likely)
- android-keyboard BangluIMEService voice path:
  (a) Watchdog: arm a handler timeout (~6s) when startListening is called; if no RecognitionListener callback of any kind arrives, tear down the recognizer, reset UI state, show "ভয়েস চালু হলো না — আবার চেষ্টা করুন"; always disarm on first callback.
  (b) Network ladder: remove the isNetworkAvailable() gamble — always try the default (online-capable) recognizer first; on ERROR_NETWORK/ERROR_SERVER retry once with EXTRA_PREFER_OFFLINE; on language-pack/offline failure (SODA error 12 class, ERROR_LANGUAGE_NOT_SUPPORTED) show the actionable message "অফলাইনে বাংলা ভয়েস প্যাক নেই" — never a listening chip that cannot deliver.
  (c) Async partials: normalizeVoiceToken/convertWord calls in onPartialResults move off the main thread (same coalescing pattern as the keystroke path S28).
  (d) Every onError code maps to either retry or a visible state — no swallowed codes.
- Also (same task, tiny): MainActivity setup guide wording for the second Android confirmation (F-ANDROID-007).
- Gates: `:shared:testDebugUnitTest` + `:android-keyboard:assembleDebug` compile + targeted unit tests for any extracted state logic; emulator verification against the audit's logcat signatures (watchdog fires on a stubbed/killed recognizer; airplane mode shows the actionable message not a dead mic).

## Task 3 — Web surfaces (closes F-WEB-004/005/006) [banglu-web repo]
- Homepage editor (UnifiedPhoneticEditor): double-space after a committed word → `। ` (mirror the desktop pending/replace semantics in its input handler); English preservation: always include the raw Latin token as a candidate (append client-side like the IME Composer) and Esc keeps it.
- /phonetic: same English-candidate rule.
- /api/convert: validate body (text must be string, sane length cap) → 400 JSON error, no parser internals; malformed JSON → 400. Keep the API dari-free (document: dari is an editor behavior, the API converts words — note in response of the audit; OR implement the same double-space transform for parity — implementer judgment, document choice).
- Gates: tsc + next build green; engine-smoke extended (double-space via the editor logic where testable); commit+push (auth standing).

## Task 4 — Round close
- Full walls everywhere; rebuild artifacts (JS artifact + slim untouched unless engine changed → extension/IME rebuild if shared changed); reinstall local surfaces; perf APK for the phone; ledger; CLAUDE.md audit-status note; push.
- USER: merge bd-ai-web-firm PR #6 (closes F-WEB-007); bujteparcina tier decision (accept+document vs store-backed split — pending).
