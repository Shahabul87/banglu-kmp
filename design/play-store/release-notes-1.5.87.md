# Release notes — 1.5.87 (2124)

## Play Console "What's new" (≤500 chars)

**বাংলা**
- "শেখা শব্দ মুছুন" চাপার পর মুছে ফেলা শব্দ আর কখনো — এমনকি কয়েক সেকেন্ডের জন্যও — ফিরে আসে না
- ভয়েস টাইপিং, ক্লিপবোর্ড ও গোপনীয়তার উন্নতি (1.5.84–1.5.86)

**English**
- Deleted learned words can no longer reappear, even briefly, when "Clear learned data" is tapped while the dictionary is still loading
- Voice typing, clipboard and privacy improvements (1.5.84–1.5.86)

## Internal

- Round: S140 — closes the last production blocker from the v1.5.86 re-audit (`docs/audits/reaudit-android-production-readiness-v1.5.86-2026-08-26.md`): engine, preference maps and `engineFullyLoaded` are now published atomically under `learningLock` only if no erase happened since the build's storage snapshot; a stale build is discarded (light path drops the in-place engine). Regression: `S140InitializeVersusEraseJvmTest` (barrier-controlled erase during initialize). Lower-severity: the first-run identity migration writes the default only if the user has not decided meanwhile.

## Recorded at release (2026-08-26)
- Built from commit `00ec4dc819fbe07a2e603731122c1ef4f14c3923` (tag `v1.5.87`); embedded revision verified; APK and AAB signer `8fa2de6dd2216414954f581cbe1247255e7f74c8da4742c2f7d3d8df43b11a17` == configured keystore
- `banglu-1.5.87-2124.aab` SHA-256 `6e69dac0f21fdaf99a91e45cbcf851e854040cd0b7985a8d0837f32763aa5cf7` (66,364,431 bytes); copies in `releases/` and `~/Downloads/`
- Gates: shared jvmTest 676 (incl. S140 initialize-vs-erase race), shared Android 417, shared JS 433, android debug/release 144 each, lint 0 errors, connectedDebugAndroidTest 7/7, exact-AAB device smoke certified (`device-smoke-1.5.87.json`: activation 255 ms, heap 261.3 MB, frame p95 37.7 ms over 28 frames, no ANR)
- Supersedes 1.5.86 (2123); 1.5.85 (2122) must not be promoted
