# Release notes — 1.5.90 (2127)  (1.5.89/2126 was cut minutes earlier with a narrower English oracle and never uploaded)

## Play Console "What's new" (≤500 chars)

**বাংলা**
- ইংরেজি শব্দ টাইপ করলে তার বাংলা উচ্চারণই আসে (tester → টেস্টার, phone → ফোন), আর ইংরেজি বানানটি সাজেশনে থাকে
- ভুল বানানের ইংরেজি শব্দও চেনে (suggention → সাজেশন, compter → কম্পিউটার), সঠিক বানানটি সাজেশনে
- engine → ইঞ্জিন, suggestion → সাজেশন — কাঁচা উচ্চারণ আর নয়
- (1.5.88) টাইপ করা শব্দই থাকে; kri → ক্রি/কৃ, sh → শ/ষ/স; পরবর্তী শব্দের পরামর্শ

**English**
- Typing an English word gives its Bangla pronunciation (tester → টেস্টার, phone → ফোন) with the English spelling in the suggestions
- Misspelled English words are recognised too (suggention → সাজেশন, compter → কম্পিউটার) with the corrected spelling in the suggestions
- engine → ইঞ্জিন, suggestion → সাজেশন — no more crude renderings
- (1.5.88) what you type stays; kri → ক্রি/কৃ, sh → শ/ষ/স; next-word suggestions

## Internal

- Round: S142 — English-word law (`applyEnglishPronunciationLaw`, shared by commit and preview):
  "correct English word" = the english_lexicon knows the 4+-letter key; its curated/lexicon rendering
  commits when the Bengali reading is below the everyday band (75); everyday words keep the key with
  the pronunciation as a chip (name → নামে, phone → ফোনে, abba → আব্বা). Curated loanword seeds are
  the first door for every lexicon consumer (door → ডোর, table → টেবিল, engine → ইঞ্জিন).
  Regression: `S142EnglishWordLawJvmTest`.
- Round: S143 — English spelling rescue (`applyEnglishSpellingRescue`, `nearestEnglishWords` over the store's
  in-memory `englishKeys()`, `renderEnglishWord`, `isEnglishRendering`, `CommonEnglishWords` 10K oracle).
  Study on google-10000-english: 8400 words, 98.4% English-or-correct; 94% of one-slip misspellings
  rescued (docs/engine-english-study-2026-08-29.md). Regression: `S143EnglishSpellingJvmTest`.
- Supersedes 1.5.89 (2126) and 1.5.88 (2125), neither uploaded to Play.

