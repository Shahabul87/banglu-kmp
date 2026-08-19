# English-side competitive audit — Banglu vs Samsung Keyboard / Gboard

Date: 2026-08-19 · Basis: code inventory at v1.5.77 (2114), S96–S99 English
suite; comparison set = Samsung Keyboard (One UI 6/7) and Gboard English
feature lists.

## What the English side ALREADY has (verified in code)

| Feature | Status | Where |
|---|---|---|
| Autocorrect on space + undo chip (undo teaches the word) | ✅ S97 | BangluIMEService space-commit path |
| Word completions (3 chips) while typing | ✅ S96 | englishCompletions |
| Next-word predictions after space | ✅ S96 | englishPredictions (learned bigrams) |
| Personal learning w/ context (word + previous word) | ✅ S96 | recordEnglishCommit |
| Auto-capitalization (sentence start, toggle) | ✅ | autoCapitalizeEnabled |
| Double-space → period (toggle) | ✅ | doubleSpacePeriodEnabled |
| Caps lock (dbl-tap shift), auto-unshift | ✅ | ShiftState.CAPS_LOCK |
| Probabilistic touch decoding (fat-finger correction) | ✅ S99 | TouchTargetModel — Gboard-class |
| Email/identity assist (domain completion; never passwords) | ✅ S98 | identityAssist |
| Number row, 2 symbol pages, digit long-press symbols | ✅ | ComposeKeyboardView |
| Clipboard history panel | ✅ | clipboardHistory |
| Cursor control (spacebar swipe) | ✅ | SpaceBar onCursorMove |
| Emoji panel + 3-script search (330+ keyword sets) + বাক্য tab | ✅ S57 | EmojiKeywords/BanglaPhrases |
| Context-aware Enter label (search/go/next) | ✅ | enterLabel |
| URL-field intelligence (no dari/conversion in URI fields) | ✅ S56 | uriInputMode |
| Password/OTP compliance (no learning, no suggestions) | ✅ | sensitiveInputMode |
| Themes, height, font size, haptics/sound, key preview | ✅ | Settings |
| **Differentiator:** English auto-detected inside BANGLA mode (no manual switch for mixed chat) | ✅ | EnglishDetector + lexicon arbitration |

## Gaps vs Samsung/Gboard — Tier 1 (felt daily, cheap→moderate)

1. **English voice dictation** — mic is hardcoded `bn-BD`
   (`VOICE_LANGUAGE`); in EN mode dictation should follow the keyboard
   language (en-US/en-IN). Small effort, high value for "one keyboard".
2. **Numeric keypad for number/phone/PIN fields** — number-class inputs get
   full QWERTY today (no TYPE_CLASS_NUMBER/PHONE layout). Every stock
   keyboard shows a numpad here. Small-medium effort.
3. **Inline emoji suggestions** (type "love" → ❤️ chip in the strip) — the
   3-script keyword data (336 sets) ALREADY exists in EmojiKeywords.kt;
   wiring it into the suggestion strip is small effort.
4. **Text shortcuts / snippets** ("omw" → "on my way"; Samsung Text
   shortcuts, Gboard dictionary shortcuts). Medium effort (settings UI +
   expansion at commit).
5. **Accent long-press on Latin letters** (é è ñ ü) — long-press alternates
   currently map to Bangla letters only. Small effort; table-stakes for a
   "standard" English keyboard, low priority for the BD market.

## Gaps — Tier 2 (bigger bets)

6. **Glide/swipe typing** — THE headline Samsung/Gboard feature. Large
   effort (gesture decoder over a word graph). Could scope English-only
   first (lexicon/trie exist); Bangla glide is genuinely hard (conjuncts).
7. **One-handed mode** (shrink + dock left/right). Medium.
8. **Clipboard upgrades** — pin favorites, expiry, image paste via
   commitContent. Medium.
9. **Floating / split keyboard** (tablets, large phones). Large.
10. **GIF/sticker insertion** — needs network in the typing surface, which
    the IME-offline privacy law forbids by architecture; would require a
    deliberate :ui-process bridge decision. Documented skip, not an
    oversight (fake GIFs were removed in S57 on purpose).

## Non-gaps (deliberate or out of scope)

- Live translate (Samsung) — network on the keystroke path violates the
  privacy invariant; permanent skip.
- Samsung Pass / biometric autofill — platform-private API.
- Red spell-check underline — editor-side (TextView), not an IME feature.

## Recommended order

- **S-next quick wins (one round):** #1 voice language follows mode,
  #2 numeric keypad, #3 emoji chips from existing keyword data.
- **Then:** #4 text shortcuts, #5 accent long-press, #8 clipboard pin.
- **Flagship bet before 1.6:** #6 English glide typing.
