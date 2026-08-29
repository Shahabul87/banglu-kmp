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
