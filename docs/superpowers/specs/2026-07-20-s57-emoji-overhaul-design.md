# S57 — Emoji Overhaul (design)

Tester complaints: emoji "garbage, not smooth like WhatsApp"; too few Bengali
quick phrases; congested UI; dumb search. Root causes found in code review:
the "GIF" tab sends procedurally-drawn 72px single-frame GIFs
(ReactionGifFactory) that look broken in real chats; the panel stacks THREE
chrome rows (top category rail + search row + bottom rail); the grid packs 8
columns at 2dp gaps; search knows ~23 alias keys; only 20 phrases exist.

User decisions: remove the GIF tab entirely (privacy law forbids network GIF
search in the IME process; bundled sticker art is a future round). Design
approved 2026-07-20.

## 1. Layout (WhatsApp/Gboard pattern — two chrome rows)

- Top: single search pill (tap → existing inline search keys, restyled hint).
- Bottom: ABC | category rail (Recents ⏱, বাক্য phrases, Smileys, Gestures,
  People, Animals, Food, Activities, Travel, Objects, Symbols, Flags) |
  backspace. The TOP category rail is deleted.
- Grid: 8 columns, 6dp gaps, ~30sp glyphs, ripple feedback, full panel height
  (the space freed by the deleted top rail goes to the grid).
- Perf: no sticker-type checks inside the emoji grid hot path; plain string
  cells only. Phrases render on their own tab as sectioned cards
  (LazyVerticalGrid with full-span section headers).

## 2. Everyday Bengali phrases (~90, researched register)

Sections (each phrase carries Bangla + Banglish + English aliases):
সালাম ও শুভেচ্ছা · খোঁজখবর · এখন অবস্থা · দোয়া ও ধর্ম · ভালোবাসা ও পরিবার ·
উৎসব · অভিনন্দন ও সমবেদনা · কাজ ও পড়াশোনা. One tap commits the text.

## 3. Intelligent search

Per-emoji keyword index (English CLDR-style names + Bangla + Banglish) across
the full set; phrases searchable by all three scripts; ranking exact > prefix
> substring; instant per keystroke; ~24-char query cap as today. All offline,
compiled into the APK.

## 4. Emoji coverage

Expand ~600 → ~1,100 (hearts, nature/weather, fuller people/objects/flags).
Skin-tone long-press picker: explicitly OUT of scope this round.

## Removals

EmojiData.GifSticker + gifStickers + isGifSticker + gifStickerFor,
ReactionGifFactory.kt, BangluIMEService commitGifSticker/cachedBundledGif
path, GifStickerCard composable.

## Tests

- EmojiSearchTest (JVM unit, android module): Bangla (হাসি, ঈদ, মন খারাপ),
  Banglish (hasi, dua, mon kharap), English (love, birthday, fire) queries
  return expected emoji; phrase queries return phrases; empty/garbage queries
  return empty.
- EmojiDataTest: no duplicate emoji per category; every phrase has ≥2 aliases;
  no GIF remnants.
- Device: screenshots of panel/search/phrases; commit-to-editor check.

## Ship

v1.5.37 (2074). No db change. Android-only surface (emoji panel is not part
of the shared engine).
