# Release notes — 1.5.82 (2119)

## Play Console "What's new" (≤500 chars)

**বাংলা**
- "শেখা শব্দ মুছুন" এখন সত্যিই সব শেখা ডেটা মুছে দেয় — নিশ্চিত হলে তবেই সফল বার্তা
- ইমেইল ঠিকানা মনে রাখার জন্য আলাদা সুইচ; বন্ধ করলে সংরক্ষিত ঠিকানা সঙ্গে সঙ্গে মুছে যায়
- ক্লিপবোর্ড ইতিহাস এক ঘণ্টা পর নিজে থেকে মুছে যায়; পাসওয়ার্ড/OTP ঘর থেকে কিছু সংরক্ষিত হয় না
- TalkBack ও Switch Access দিয়ে এখন প্রতিটি কী চাপা যায়
- ভয়েস টাইপিংয়ে বাক্য পুনরাবৃত্তি ও নীরব মাইকের সমস্যা সমাধান (1.5.81)

**English**
- "Clear learned data" now really deletes everything the keyboard learned, and only reports success when it has
- Separate switch for remembering email addresses; turning it off deletes saved addresses at once
- Clipboard history expires after one hour and never stores anything from password/OTP fields
- Every key can now be activated with TalkBack and Switch Access
- Voice typing: repeated sentences and silent-mic cases fixed (1.5.81)

## Internal

- Round: S135 (production-readiness audit, `docs/audits/audit-android-production-readiness-2026-08-26.md`)
- Artifact: `releases/banglu-1.5.82-2119.aab` — built by `scripts/validate_android_release.sh` from tag `v1.5.82`; the SHA-256 and embedded revision are printed by the script and recorded below at release time.
- Privacy policy updated (Aug 26, 2026) — the Play listing's policy URL is unchanged; the Data Safety answers stay "Audio only".

## Recorded at release (2026-08-26)
- Built from commit `4a00d036475d19f2b75469cd1a7905acb47e83f3` (tag `v1.5.82`), embedded revision verified equal
- `banglu-1.5.82-2119.aab` SHA-256 `9ffc836130339d48629775fa27a18955a3bfe45eab195ca920ed7f1dcc7d4abf` (67,810,410 bytes)
- Release APK 64,617,977 bytes; copies in `releases/` and `~/Downloads/`
