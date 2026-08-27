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
