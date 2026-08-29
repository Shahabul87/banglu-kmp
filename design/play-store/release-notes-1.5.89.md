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
