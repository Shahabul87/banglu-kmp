# Release notes — 1.5.89 (2126)

## Play Console "What's new" (≤500 chars)

**বাংলা**
- ইংরেজি শব্দ টাইপ করলে তার বাংলা উচ্চারণই আসে (tester → টেস্টার, phone → ফোন), আর ইংরেজি বানানটি সাজেশনে থাকে
- engine → ইঞ্জিন, suggestion → সাজেশন — কাঁচা উচ্চারণ আর নয়
- (1.5.88) টাইপ করা শব্দই থাকে; kri → ক্রি/কৃ, sh → শ/ষ/স; পরবর্তী শব্দের পরামর্শ

**English**
- Typing an English word gives its Bangla pronunciation (tester → টেস্টার, phone → ফোন) with the English spelling in the suggestions
- engine → ইঞ্জিন, suggestion → সাজেশন — no more crude renderings
- (1.5.88) what you type stays; kri → ক্রি/কৃ, sh → শ/ষ/স; next-word suggestions

## Internal

- Round: S142 — English-word law (`applyEnglishPronunciationLaw`, shared by commit and preview): exact
  English keys (detector list + regular derivations) commit the curated/lexicon rendering when the
  Bengali reading is below the everyday band (80); everyday words keep the key with the pronunciation
  as a chip (name → নামে). Curated loanword seeds outrank CMU renderings in the S131 flip. S81's
  phone → ফোনে pin re-pinned under this decision. Regression: `S142EnglishWordLawJvmTest`.
- Supersedes 1.5.88 (2125, never uploaded to Play).

## Recorded at release (2026-08-29)
- Built from commit `aade8f941773f4f9738278aa9887789f23c4768a` (tag `v1.5.89`); embedded revision verified; AAB signer `8fa2de6dd2216414954f581cbe1247255e7f74c8da4742c2f7d3d8df43b11a17` == configured keystore
- `banglu-1.5.89-2126.aab` SHA-256 `61e8fdbb1c0095160a5689e49b7fff3cf59feac069b7159646a217f73ee4fd65` (66,373,168 bytes); copies in `releases/` and `~/Downloads/`
- Gates: shared jvmTest + testDebugUnitTest + windows-ime:test green (S142EnglishWordLawJvmTest added, S81 re-pinned), shared JS green, desktop-app:test green, macOS runner 105/105, `scripts/validate_android_release.sh` clean
- NOT run: exact-AAB device smoke / connectedDebugAndroidTest (dev phone locked during the build) — run `RUN_DEVICE_SMOKE=1 scripts/validate_android_release.sh` before promoting to production
- Supersedes 1.5.88 (2125, never uploaded); 1.5.85 (2122) must not be promoted
