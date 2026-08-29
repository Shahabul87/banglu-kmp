# Release notes — 1.5.88 (2125)

## Play Console "What's new" (≤500 chars)

**বাংলা**
- আপনি যা টাইপ করেন, ইঞ্জিন তা আর উপেক্ষা করে না: নতুন/অচেনা শব্দ (যেমন "banglu" → বাংলু) হুবহু থাকে, অভিধানের কাছাকাছি শব্দটি সাজেশনে আসে
- kri → ক্রি/কৃ, ku → কু/কূ, sh → শ/ষ/স — একটি এডিটরে, বাকিগুলো সাজেশনে
- অসম্পূর্ণ শব্দে (banglish) বাংলাদেশ-এর মতো পূর্ণ শব্দ সাজেশনে

**English**
- The engine no longer overrides what you typed: new or unknown words (e.g. "banglu" → বাংলু) stay as typed, with the nearby dictionary word offered as a suggestion
- kri → ক্রি/কৃ, ku → কু/কূ, sh → শ/ষ/স — one in the editor, the rest in suggestions
- Prefix completions for unfinished words (banglish → বাংলাদেশ)

## Internal

- Round: S141 — typed-faithful engine law (user-directed, 2026-08-29). Engine-only change in `shared`
  (every surface inherits it): substitution typo repairs only when the typed reading is not clean
  Bengali (`readsAsCleanBengali`); Layer-6 recovery and fuzzy suffix stems limited to spelling
  normalisation (`spellingSkeleton`); habit aliases cannot smuggle a vowel swap (pass 1 + pass 2);
  `typed_literal` last-slot law in the fuzzy band; open-syllable vowel twins at strip[1]; letter-class
  twins for letter keys; `roman_prefix` completions for leaf keys. Three P1 root-decomposition parity
  fixtures re-pinned with a decision note. Regression: `S141TypedFaithfulJvmTest`.
- Prediction bar parity: desktop editor, macOS IME, browser extension and the bangluweb dashboard
  editor now show next-word predictions after a space commit (Android and the Windows IME already did);
  slim JSON carries a pruned n-gram model (42.5K bigrams / 88.2K trigrams / 17K unigrams, 31 MB).

## Recorded at release (2026-08-29)
- Built from commit `493e092685696b945c85a7e624f2122d05815bab` (tag `v1.5.88`); embedded revision verified; AAB signer `8fa2de6dd2216414954f581cbe1247255e7f74c8da4742c2f7d3d8df43b11a17` == configured keystore
- `banglu-1.5.88-2125.aab` SHA-256 `49e1e84a0be7fc03ef88a8b9069c54197afe633999d48a9457b3b250ee19530e` (66,372,795 bytes); copies in `releases/` and `~/Downloads/`
- Gates: shared jvmTest + testDebugUnitTest green (S141TypedFaithfulJvmTest added), shared JS green, desktop-app:test 41, macOS runner 105/105, windows-ime:test green, `scripts/validate_android_release.sh` clean
- NOT run for this build: the exact-AAB device smoke and connectedDebugAndroidTest (the dev phone was locked during the build) — run `RUN_DEVICE_SMOKE=1 scripts/validate_android_release.sh` before promoting to production
- Supersedes 1.5.87 (2124); 1.5.85 (2122) must not be promoted
