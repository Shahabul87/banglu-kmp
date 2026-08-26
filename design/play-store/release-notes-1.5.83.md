# Release notes — 1.5.83 (2120)

## Play Console "What's new" (≤500 chars)

**বাংলা**
- "শেখা শব্দ মুছুন" এখন সব প্রোফাইলের শেখা ডেটা মুছে দেয় এবং স্টোরেজ নিশ্চিত করলে তবেই "মুছে ফেলা হয়েছে" দেখায়
- ইমেইল ঠিকানা মনে রাখা এখন ডিফল্টে বন্ধ; চালু করলে শুধু ইমেইল ঘর থেকে শেখে
- ক্লিপবোর্ড: কোনো ব্যক্তিগত ঘর (পাসওয়ার্ড, OTP, ইমেইল, লিংক, নম্বর) থেকে কিছু সংরক্ষিত হয় না; এক ঘণ্টা পর মুছে যায়
- অভিধান লোড না হলে কীবোর্ডে জানানো হয় এবং জায়গা খালি করলে নিজে থেকে ঠিক হয়
- ইমোজি পরিবার/পতাকা এক ব্যাকস্পেসে মোছে

**English**
- "Clear learned data" now erases every profile's learning and reports success only after storage confirms
- Remembering email addresses is now off by default; when on, it learns only from email fields
- Clipboard: nothing is stored from any private field (password, OTP, email, URL, number); entries expire after an hour
- If the dictionary cannot load, the keyboard says so and heals itself once storage is freed
- Emoji families and flags delete with one backspace

## Internal

- Round: S136 — second-pass fixes for docs/audits/audit-android-production-readiness-2026-08-26.md
- Toolchain: AGP 8.9.3 (API 36 supported; suppression removed), Gradle 8.11.1 with wrapper checksum; the account/billing split is no longer in the launch AAB (`-PbangluAccount=true` to include)
- Gates: unit/lint walls, `connectedDebugAndroidTest` (multiprocess erase provider), `validate_android_release.sh` with `RUN_DEVICE_SMOKE=1` (bundletool splits from the exact AAB, typing + accessibility + memory/jank/ANR thresholds)

## Recorded at release (2026-08-26)
- Built from commit `b922b2d6c5d43ed405282015c7513cb6e05f26f2` (tag `v1.5.83`); the validator verified the AAB's embedded revision equals HEAD
- `banglu-1.5.83-2120.aab` SHA-256 `3b832246cad1bca494001f4cfb7b3996725d0e87cb80299fb20063eb53a0f3a2` (66,352,780 bytes; no account split); copies in `releases/` and `~/Downloads/`
- Device certification (SM-S901W, Android 16, exact release splits via bundletool): `device-smoke-1.5.83.json` — activation 232 ms, 50/51 clickable nodes, typing + delete/retype probes pass, 155 MB PSS, no ANR (jank not enforced: 11-frame sample)
- Instrumented: `connectedDebugAndroidTest` 4/4 — multiprocess erase provider (all/identity/unknown scope) and assistive key activation (ACTION_CLICK on key nodes types আমি)
- Unit walls: shared jvmTest 674, shared Android 417, android debug/release 132 each; lint 0 errors on AGP 8.9.3
