# Release notes — 1.5.91 (2128)

## Play Console "What's new" (≤500 chars)

**বাংলা**
- (1.5.90) ইংরেজি শব্দ টাইপ করলে তার বাংলা উচ্চারণ, ভুল বানানও চেনে; টাইপ করা শব্দই থাকে; kri → ক্রি/কৃ; পরবর্তী শব্দের পরামর্শ
- ইঞ্জিনের ভেতরের ক্যাশ বড় করা হয়েছে — দীর্ঘ শব্দ টাইপ ও ব্যাকস্পেসে আরও মসৃণ

**English**
- (1.5.90) English words give their Bangla pronunciation, misspellings recognised; what you type stays; kri → ক্রি/কৃ; next-word suggestions
- Larger internal engine caches — smoother typing and backspacing through long words

## Internal

- Round: S144 — keystroke sqlite budget (Windows field report). Shared change: `MAX_STORE_MEMO` 128 → 2048,
  `isMidWordPrefix` memoized. JVM store (desktop/Windows): Bloom negative indexes + reverse-lookup memo
  (`S144KeystrokeSqliteBudgetJvmTest`, `S144BloomFilterTest`). Android's SqlitePhoneticIndexStore untouched.
- Carries S141–S143 (see release-notes-1.5.90.md). Supersedes 1.5.90 (2127), never uploaded.

## Recorded at release (2026-08-30)
- Built from commit `1bd6a86` (tag `v1.5.91`); embedded revision verified; AAB signer `8fa2de6dd2216414954f581cbe1247255e7f74c8da4742c2f7d3d8df43b11a17` == configured keystore
- `banglu-1.5.91-2128.aab` SHA-256 `07698e0ff8fd6fff9db90cea80eaef57c1cd9951ca8890d069b1150dc8d8e911` (66,415,800 bytes); copies in `releases/` and `~/Downloads/`
- Gates: shared jvmTest + testDebugUnitTest + windows-ime:test + desktop-app:test green (S144 pins), shared JS green, macOS runner 105/105, `scripts/validate_android_release.sh` clean
- NOT run: exact-AAB device smoke / connectedDebugAndroidTest (dev phone locked) — run `RUN_DEVICE_SMOKE=1 scripts/validate_android_release.sh` before promoting to production
- Supersedes 1.5.90 (2127), 1.5.89, 1.5.88 (none uploaded); 1.5.85 (2122) must not be promoted
