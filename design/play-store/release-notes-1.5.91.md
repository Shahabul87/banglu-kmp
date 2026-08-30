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
