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
