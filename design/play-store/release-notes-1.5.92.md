# Banglu 1.5.92 (2129) — S147

## bn-BD (Play Console "What's new")
সম্পূর্ণ নতুন চেহারা: অ্যাপের প্রতিটি স্ক্রিন এখন এক থিমে। তিন ধাপের সেটআপ গাইড নিজেই বুঝে নেয় আপনি কোন ধাপে আছেন — এক বাটনে পরের ধাপ। "যেখানে অন্য কীবোর্ড আটকে যায়" — স্বাধীনতা, স্বাস্থ্য, কৃষ্ণ, বিদ্যুৎ-এর মতো কঠিন শব্দ ছোট হাতের ইংরেজিতেই, একাধিক বানানে। shassoto লিখলেও এখন স্বাস্থ্য। হোম থেকে এক ট্যাপে মতামত পাঠান।

## en-US
A complete visual redesign: one committed dark theme across the whole app. A state-aware 3-step setup guide (one button for the step you're on), keycap cards of genuinely hard words — স্বাধীনতা, স্বাস্থ্য, কৃষ্ণ, বিদ্যুৎ — typed in plain lowercase with their spelling variants, a 3-slide first run, bottom navigation with one-tap feedback. New chat alias: shassoto/shasstho → স্বাস্থ্য.

## Internal
- S147: full app-UI redesign to the approved mock (banglu-android-mocks.html,
  "same to same"): dark-plum palette (#0F0E1A/#22213A) with terracotta CTA,
  mustard highlight, moss success.
  - MainActivity: brand row + state pill; two-line hero; SetupStepperCard
    (state-aware ১/২/৩, dashed connectors, mini illustrations, one CTA per
    step; S55 second-confirmation hint one line after a failed attempt);
    white try-field + mono roman chips; power cards with keycaps (hot cap =
    the letter the engine decided, mustard) and highlighted Bengali; bottom
    nav হোম/শিখুন/সেটিংস/মতামত (bangluweb.com/feedback).
  - Onboarding v2 (4 slides, mock-approved): ১ স্বাগতম welcome (bold Noto
    Sans 900) → ২ বৈশিষ্ট্য (বাংলা+English, lowercase-only, স্মার্ট সাজেশন
    বার, ভয়েস দুই ভাষাতেই — staggered entrance) → ৩ কনফিউজিং শব্দ (tappable
    keycaps + swipeable families, HARD words: বিশ্ববিদ্যালয়, সংক্ষিপ্ত,
    রাষ্ট্র, প্রতিষ্ঠান, উদ্দেশ্য, সম্পূর্ণ, লক্ষ্মী…) → ৪ setup stepper.
    Animated slide transitions + Crossfade on cap taps.
  - SettingsActivity + TutorialActivity: same mock palette (theme-pref no
    longer changes app-screen colors; the keyboard's own themes untouched).
  - ShowcaseWords (shared commonMain): caps/tagline/words+variants+highlight,
    try-chips and onboarding lines — EVERY (variant, word) pair pinned by
    S147ShowcaseWordsJvmTest on the real dictionary.
- Fonts bundled (res/font): Tiro Bangla (display serif), JetBrains Mono
  (romans), Noto Sans Bengali Regular/Bold (app-wide default via
  BangluComposeHost MaterialTheme typography).
- Engine: shassoto/shasstho/sasstho aliases on the স্বাস্থ্য seed (SeedData).
- No IME/keystroke-path changes. No dictionary rebuild needed (seed-level alias).
