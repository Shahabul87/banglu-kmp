# Banglu Keyboard — Competitive Feature Plan

## Vision
Make Banglu the keyboard people CHOOSE to use daily — not just for Bengali, but as their PRIMARY keyboard that handles both Bengali and English seamlessly. Users should never need to switch to Samsung keyboard.

---

## Phase 1: Essential Daily Typing (Must-Have for Launch)

These features are expected by EVERY user. Missing them = instant uninstall.

### 1.1 Double-Space → Period + Space
**What:** Tap space twice quickly → inserts ". " and auto-capitalizes next letter
**Why:** Every keyboard has this. Users muscle-memory depends on it.
**How:**
```
- Track lastSpaceTime in BangluIMEService
- If space pressed within 300ms of last space:
  - Delete the space just inserted
  - Insert ". "
  - Set pendingCapitalize = true
```
**Effort:** 30 min

### 1.2 Auto-Capitalize After Sentence
**What:** First letter after `. ! ? ।` is automatically uppercase
**Why:** Users expect this — typing "hello. how" should auto-capitalize "How"
**How:**
```
- In onKeyPress(), check text before cursor via:
  currentInputConnection.getTextBeforeCursor(2, 0)
- If ends with ". " or "! " or "? " or "। ":
  - Auto-enable shift for one letter
```
**Effort:** 30 min

### 1.3 Context-Aware Enter Key
**What:** Enter key icon changes based on what app expects:
- Search field → 🔍 (search icon)
- Message field → ➤ (send icon)
- Multi-line field → ↵ (newline icon)
- Default → ↵
**Why:** Samsung, GBoard, all keyboards do this. Users expect the visual cue.
**How:**
```
- In onStartInputView(), check EditorInfo.imeOptions:
  - IME_ACTION_SEARCH → show 🔍
  - IME_ACTION_SEND → show ➤
  - IME_ACTION_GO → show →
  - IME_ACTION_NEXT → show ⇥
  - IME_ACTION_DONE → show ✓
  - Default → show ↵
- Pass enterIcon to Compose via mutableStateOf
```
**Effort:** 45 min

### 1.4 Key Preview Popup
**What:** When user presses a key, a magnified version of the character appears ABOVE the key in a floating bubble
**Why:** Critical for accuracy — user sees what they're pressing especially on small screens
**How:**
```
- Track pressedKey position + label in state
- Show a Popup composable above the pressed key:
  - 56x56dp bubble, 10dp above the key
  - Same key background but larger text (32sp)
  - Appears on press, disappears on release
- Use Popup(alignment = Alignment.TopCenter, offset = ...)
```
**Effort:** 1 hour

### 1.5 Backspace Word-by-Word Deletion (Accelerating)
**What:** Holding backspace:
- 0-400ms: single char deleted
- 400ms-1.5s: char-by-char at 50ms
- 1.5s+: word-by-word deletion
**Why:** Samsung does this. Makes deleting long text fast.
**How:**
```
- In BackspaceKey, add elapsed time tracking:
  - if elapsed > 1500ms: delete word instead of char
  - Use findWordBoundary() to find previous space/punctuation
  - currentInputConnection.deleteSurroundingText(wordLength, 0)
```
**Effort:** 30 min (extend existing repeat)

---

## Phase 2: Smart Typing Features (Makes Users Stay)

These differentiate a good keyboard from a basic one.

### 2.1 Swipe Spacebar for Cursor Movement
**What:** Long-press spacebar then slide left/right to move text cursor
**Why:** Samsung's most loved feature — faster than tap-to-position
**How:**
```
- In SpaceBar's pointerInput:
  - On long press (>500ms): enter cursor mode
  - Track horizontal drag distance
  - For every 15dp of drag: move cursor one character
  - currentInputConnection.sendKeyEvent(DPAD_LEFT/DPAD_RIGHT)
  - Visual indicator: spacebar text changes to "← →"
```
**Effort:** 1.5 hours

### 2.2 Swipe-to-Type (Glide Typing)
**What:** Slide finger across keys without lifting to type words
**Why:** 50%+ of mobile users prefer swipe typing. Major competitive feature.
**How:** This is complex. Options:
- Option A: Integrate an open-source gesture decoder (like AOSP LatinIME's gesture input)
- Option B: Build a simple path-to-word matcher using our dictionary
- Option C: Skip for v1, add in v2
**Recommendation:** Skip for v1 launch. Add in v2.
**Effort:** 2-4 weeks

### 2.3 English Word Suggestions (English Mode)
**What:** When in English mode, show word predictions/autocorrect
**Why:** If English mode has no suggestions, users switch back to Samsung for English
**How:**
```
- Use Android's built-in spell checker API or
- Load a top-10K English word list
- In English mode: match prefix against word list
- Show top 3 suggestions in suggestion bar
```
**Effort:** 3-4 hours

### 2.4 Clipboard Manager
**What:** Long-press clipboard icon to see recent copied text
**Why:** Samsung has it built-in. Very useful for pasting.
**How:**
```
- Track last 10 clipboard entries via ClipboardManager listener
- Show in a popup panel when clipboard button tapped
- Tap an entry to paste it
```
**Effort:** 2-3 hours

### 2.5 One-Handed Mode
**What:** Shrink keyboard to left or right side for one-handed typing
**Why:** Big phones (6.5"+) need this. Samsung has it.
**How:**
```
- Add toggle in toolbar
- Resize keyboard to 75% width, align left or right
- Add a expand button on the empty side
```
**Effort:** 2 hours

---

## Phase 3: Polish & Delight (Makes Users Love It)

### 3.1 Toolbar Row
**What:** Row above number row with quick actions:
`[Clipboard] [Emoji] [Settings] [One-hand] [Voice] [···]`
**Why:** Samsung has this. Easy access to features without leaving keyboard.
**How:**
```
- New ToolbarRow composable with icon buttons
- Collapsible — tap ··· to expand/collapse
- 36dp height, horizontal scroll
```
**Effort:** 2 hours

### 3.2 Emoji Panel
**What:** Tap emoji button → shows emoji grid with categories
**Why:** Users need emoji. Currently they have to switch to Samsung for emoji.
**How:**
```
- Use Android's EmojiCompat library
- Load system emoji data
- Show in a panel replacing the keyboard area
- Categories: Recent, Smileys, Animals, Food, Travel, Objects, Symbols
- Search emoji by name
```
**Effort:** 4-6 hours

### 3.3 Theme Support
**What:** Light theme, dark theme, custom colors
**Why:** Samsung offers many themes. Users want personalization.
**How:**
```
- Define ThemeColors data class
- Light theme: white keys on light gray background
- Dark theme: current Samsung dark (already done)
- AMOLED theme: pure black background
- Auto: follow system dark mode
```
**Effort:** 2 hours

### 3.4 Voice Input
**What:** Tap mic icon → speech-to-text using Android's SpeechRecognizer
**Why:** Convenient for long text. Samsung has it.
**How:**
```
- Add mic button in toolbar
- Use SpeechRecognizer API
- Show listening animation
- Insert recognized text
```
**Effort:** 3 hours

### 3.5 Long-Press Alternate Characters
**What:** Long-press 'a' → shows popup with à, á, â, ä, etc.
Long-press '1' → shows ①, ¹, ½, etc.
**Why:** Useful for multilingual typing, special chars.
**How:**
```
- Define alternates map: 'a' → ['à','á','â','ä','å','æ']
- On long press (500ms): show popup grid above key
- User slides to select alternate
```
**Effort:** 3 hours

### 3.6 Number Row Long-Press Symbols
**What:** Long-press '1' → '!', '2' → '@', '3' → '#', etc.
**Why:** Samsung does this. Quick access to symbols without switching mode.
**How:**
```
- Map: 1→!, 2→@, 3→#, 4→$, 5→%, 6→^, 7→&, 8→*, 9→(, 0→)
- Long-press (500ms) → insert symbol instead of number
- Show hint text in corner of number key
```
**Effort:** 1.5 hours

### 3.7 Auto-Correction
**What:** Automatically fix common typos: "teh" → "the", "adn" → "and"
**Why:** All major keyboards do this.
**How:**
```
- In Banglu mode: engine already handles this (Layer 1 dictionary)
- In English mode: add a common typos map (top 500 English corrections)
- On space: check if typed word is in typos map, auto-replace
```
**Effort:** 2 hours

### 3.8 Floating/Split Keyboard (Tablet)
**What:** Detach keyboard from bottom, drag to any position. Or split into two halves.
**Why:** Tablet users need this.
**How:** Defer to v2.
**Effort:** 1 week

---

## Phase 4: Bengali-Specific Features (Our Competitive Edge)

These are features NO other keyboard has — our unique selling point.

### 4.1 Smart Bengali Sentence Suggestions
**What:** After typing a word, suggest the NEXT likely Bengali word
**Why:** Our bigram model already has this data. Samsung doesn't do Bengali predictions.
**How:**
```
- Use BigramModel.getTopPredictions(prevWord)
- Show top 3 predicted next words in suggestion bar
- Already partially implemented — just wire it to UI
```
**Effort:** 1 hour

### 4.2 Bengali Spell Check Underline
**What:** Underline misspelled Bengali words with red squiggly line
**Why:** No other keyboard does Bengali spell check.
**How:**
```
- After committing a word, check against 485K dictionary
- If not found: send SpannableString with UnderlineSpan to InputConnection
- On tap: show correction suggestions
```
**Effort:** 3 hours

### 4.3 Phonetic Hint Display
**What:** Show the English phonetic input above Bengali composing text
**Why:** Helps users learn phonetic patterns. "I typed 'otya' and got 'অত্যা'"
**How:**
```
- In suggestion bar, show: "otya → অত্যাচার" style hint
- Already partially there — just format it better
```
**Effort:** 30 min

### 4.4 Learning from User Typing
**What:** Engine learns user's preferred words and boosts them
**Why:** Already implemented in SmartEngineAdapter.onWordSelected()
**Status:** ✅ Already working — just needs persistence across restarts
**How:** AndroidStorage already saves learned words.
**Effort:** Already done

---

## Implementation Timeline

### Week 1: Launch-Critical (Phase 1)
| Day | Feature | Effort |
|-----|---------|--------|
| Day 1 | 1.1 Double-space→period + 1.2 Auto-capitalize | 1 hour |
| Day 1 | 1.3 Context-aware enter + 1.5 Word-by-word backspace | 1.5 hours |
| Day 2 | 1.4 Key preview popup | 1 hour |
| Day 2 | Testing + polish | 1 hour |

### Week 2: Smart Features (Phase 2)
| Day | Feature | Effort |
|-----|---------|--------|
| Day 1 | 2.1 Swipe spacebar cursor | 1.5 hours |
| Day 2 | 2.3 English word suggestions | 3-4 hours |
| Day 3 | 2.4 Clipboard manager | 2-3 hours |
| Day 4 | 2.5 One-handed mode | 2 hours |

### Week 3: Polish & Delight (Phase 3)
| Day | Feature | Effort |
|-----|---------|--------|
| Day 1 | 3.1 Toolbar row + 3.6 Number long-press symbols | 3.5 hours |
| Day 2 | 3.2 Emoji panel | 4-6 hours |
| Day 3 | 3.3 Theme support + 3.5 Long-press alternates | 5 hours |
| Day 4 | 3.4 Voice input + 3.7 Auto-correction | 5 hours |

### Week 4: Bengali Edge (Phase 4)
| Day | Feature | Effort |
|-----|---------|--------|
| Day 1 | 4.1 Bengali next-word predictions | 1 hour |
| Day 2 | 4.2 Bengali spell check underline | 3 hours |
| Day 3 | 4.3 Phonetic hint display | 30 min |
| Day 4 | Final testing + Play Store prep | Full day |

---

## Success Metrics

After all 4 phases, Banglu keyboard should:

1. ✅ Handle 100% of daily typing needs (Bengali + English)
2. ✅ Never force user to switch to Samsung keyboard
3. ✅ Match Samsung keyboard feel (haptics, speed, accuracy)
4. ✅ EXCEED Samsung in Bengali typing (phonetic engine, spell check, predictions)
5. ✅ Be competitive with Ridmik Keyboard on Bengali features
6. ✅ Be < 25MB installed size
7. ✅ Support Android 7.0+ (API 24+)

## Our Competitive Advantages Over Samsung/GBoard/Ridmik

| Feature | Samsung | GBoard | Ridmik | Banglu |
|---------|---------|--------|--------|--------|
| Bengali phonetic typing | ❌ | ❌ Basic | ✅ Good | ✅ Best (7-layer AI) |
| Bengali spell check | ❌ | ❌ | ❌ | ✅ (485K dictionary) |
| Bengali next-word prediction | ❌ | ❌ | Partial | ✅ (bigram model) |
| Bengali disambiguation | ❌ | ❌ | Basic | ✅ (AI swap candidates) |
| English typing quality | ✅ Best | ✅ Best | ❌ Poor | ✅ Good |
| Dual language seamless | ✅ | ✅ | ❌ Separate | ✅ One-tap switch |
| App size | 150MB | 200MB | 35MB | <25MB |
