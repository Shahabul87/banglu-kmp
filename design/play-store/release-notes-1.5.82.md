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
- Built from commit `54921fee7160a1a24e4f514f07936a77d73669b7` (tag `v1.5.82`), embedded revision verified equal by `scripts/validate_android_release.sh`
- `banglu-1.5.82-2119.aab` SHA-256 `6d7f7481c6c1dd894207253192287a9fd190f447fd0f3d9a1fd2e84727fdaac4` (67,811,324 bytes)
- Release APK 64,617,977 bytes; copies in `releases/` and `~/Downloads/`
- On-device verification (SM-S901W, Android 16, debug build of the same source): 50/51 keyboard nodes clickable in the accessibility tree (the 51st, the দাঁড়ি toolbar chip, has its clickable node as the parent of its label); "শেখা শব্দ মুছুন" reduced `banglu_learning.xml` from 60,388 bytes / 4 data keys to 65 bytes / 0 keys, the IME rebuilt ("reloadUserLearning: active profile preferences loaded") and "ami" ranked আমি first again; clipboard entries persist as `base64,timestamp`; the email switch-off removed the identity key entirely.
