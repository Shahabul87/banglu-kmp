# Release notes — 1.5.85 (2122)

## Play Console "What's new" (≤500 chars)

**বাংলা**
- ক্লিপবোর্ড ইতিহাস এখন আপনার ইচ্ছায় (ডিফল্টে বন্ধ); ব্যক্তিগত ঘরে শুধু বর্তমান কপি এক ট্যাপে বসে
- ভয়েস টাইপিং নতুন করে তৈরি: বিরতির পর কথা হারায় না, বাক্য বারবার বসে না, কমা-দাঁড়ি নিজে বসে (1.5.84)
- শেখা ডেটা মুছে ফেলা আরও নিশ্চিত

**English**
- Clipboard history is now opt-in (off by default); private fields get only a one-tap paste of the current clip
- Voice typing rebuilt: no dropped speech after pauses, no repeated sentences, automatic commas/দাঁড়ি (1.5.84)
- Learned-data deletion made fully serialized

## Internal

- Round: S138 — second re-audit follow-up: clipboard display/opening gated on every private field + opt-in `clipboard_history`; identity migration writes the preference only after a durable purge; `learningLock` serializes every learning mutation with the erase; onDestroy cancels + joins (300 ms) before closing SQLite; validator refuses a version tag at another commit and proves the artifact certificate == configured keystore; device smoke fails on an inconclusive jank sample and drives a 26-key burst; instrumented test clicks Space/number/Backspace/long-press action and deletes emoji families/flags through the real InputConnection; store listing keystore path fixed, battery claim removed.

## Recorded at release (2026-08-26)
- Built from commit `3709aed78ed313d284eecfc7ebc4817d2b35eb67` (tag `v1.5.85`); embedded revision verified; signing certificate SHA-256 `8fa2de6dd2216414954f581cbe1247255e7f74c8da4742c2f7d3d8df43b11a17` proven equal to the configured keystore (`BANGLU_STORE_FILE`)
- `banglu-1.5.85-2122.aab` SHA-256 `4e20f11b1ba88f999b59f10eb2f768f7cd7d86d6512486faf1874c6db071adc1` (66,361,748 bytes); copies in `releases/` and `~/Downloads/`
- Gates: shared jvmTest 674, shared Android 417, android debug/release 144 each, lint 0 errors, connectedDebugAndroidTest 4/4 (erase provider ×3; assistive keys incl. Space/number/Backspace/long-press action + emoji family/flag deletion via real InputConnection), exact-AAB device smoke certified (`device-smoke-1.5.85.json`: activation 191 ms, heap 284.9 MB, frame p50 5.1 / p95 33.9 ms over 52 frames, no ANR)
- Live privacy policy published (Pages build 7f1c4de) describing opt-in clipboard history and default-off saved emails
