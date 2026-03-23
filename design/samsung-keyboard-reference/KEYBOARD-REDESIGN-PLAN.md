# Banglu Keyboard Redesign — Samsung-Quality Layout

## Analysis of Samsung Keyboard (from reference screenshots)

### Layout Structure (5 rows + toolbar)

```
┌─────────────────────────────────────────────────────────┐
│ 🎤 │ 😊 │ 📋 │ 🖼️ │ ⚙️ │ ··· │                        │ Row 0: Toolbar (36dp)
├─────────────────────────────────────────────────────────┤
│  1  │  2  │  3  │  4  │  5  │  6  │  7  │  8  │  9  │  0  │ Row 1: Numbers (42dp)
├─────────────────────────────────────────────────────────┤
│  Q  │  W  │  E  │  R  │  T  │  Y  │  U  │  I  │  O  │  P  │ Row 2: QWERTY (48dp)
├─────────────────────────────────────────────────────────┤
│   A  │  S  │  D  │  F  │  G  │  H  │  J  │  K  │  L  │     │ Row 3: ASDF (48dp, indented)
├─────────────────────────────────────────────────────────┤
│  ⇧  │  Z  │  X  │  C  │  V  │  B  │  N  │  M  │  ⌫       │ Row 4: Shift+letters+back (48dp)
├─────────────────────────────────────────────────────────┤
│ !#1 │  ,  │        English (US)       │  .  │  ↵           │ Row 5: Bottom (48dp)
└─────────────────────────────────────────────────────────┘
                    |||      ○      ∨                          Samsung nav bar
```

### Key Dimensions (measured from Samsung Galaxy)
- Screen: 1080x2340, 480dpi (3x density)
- Total keyboard height: ~810px = ~270dp (including toolbar)
- Toolbar row: ~108px = 36dp
- Number row: ~126px = 42dp
- Letter rows: ~144px = 48dp each
- Key horizontal gap: ~12px = 4dp
- Key vertical gap: ~12px = 4dp
- Key corner radius: ~30px = 10dp
- Standard key width: 10% of screen width
- Shift/Backspace: 15% width
- Spacebar: ~50% width
- Key background: #2C2C2C (dark gray)
- Key pressed: #4A4A4A (lighter gray)
- Special key bg: #3A3A3A
- Text color: #FFFFFF
- Keyboard bg: #1B1B1B

### Symbols View (Page 1)
```
Row 1: 1 2 3 4 5 6 7 8 9 0
Row 2: + × ÷ = / _ < > [ ]
Row 3: ! @ # $ % ^ & * ( )
Row 4: [1/2] – " " : ; ? [⌫]
Row 5: [ABC] , [English (US)] . [↵]
```

### Symbols View (Page 2)
```
Row 1: 1 2 3 4 5 6 7 8 9 0
Row 2: ` ~ \ | { } € £ ¥ ₩
Row 3: ° • ○ ● □ ■ ♤ ♡ ♢ ♧
Row 4: [2/2] ☆ ¤ 《 》 ¡ ¿ [⌫]
Row 5: [ABC] , [English (US)] . [↵]
```

---

## Banglu Keyboard Design — Two Modes

### Mode 1: Banglu (Bengali Phonetic)
```
┌─────────────────────────────────────────────────────────┐
│ [Suggestion chips — horizontally scrollable]            │ Suggestion bar (40dp)
├─────────────────────────────────────────────────────────┤
│  1  │  2  │  3  │  4  │  5  │  6  │  7  │  8  │  9  │  0  │ Number row (42dp)
├─────────────────────────────────────────────────────────┤
│  q  │  w  │  e  │  r  │  t  │  y  │  u  │  i  │  o  │  p  │ QWERTY (48dp)
├─────────────────────────────────────────────────────────┤
│   a  │  s  │  d  │  f  │  g  │  h  │  j  │  k  │  l  │     │ ASDF (48dp, indented)
├─────────────────────────────────────────────────────────┤
│  ⇧  │  z  │  x  │  c  │  v  │  b  │  n  │  m  │  ⌫       │ Letters (48dp)
├─────────────────────────────────────────────────────────┤
│ !#1 │  🌐 │        বাংলু (BN)         │  .  │  ↵           │ Bottom (48dp)
└─────────────────────────────────────────────────────────┘

Behavior: Keys show lowercase English, engine converts to Bengali in real-time
Spacebar shows: "বাংলু (BN)" — indicates Bengali mode
🌐 key: tap to switch to English mode
!#1: switch to symbols
```

### Mode 2: English (Direct passthrough)
```
┌─────────────────────────────────────────────────────────┐
│  1  │  2  │  3  │  4  │  5  │  6  │  7  │  8  │  9  │  0  │ Number row (42dp)
├─────────────────────────────────────────────────────────┤
│  Q  │  W  │  E  │  R  │  T  │  Y  │  U  │  I  │  O  │  P  │ QWERTY (48dp)
├─────────────────────────────────────────────────────────┤
│   A  │  S  │  D  │  F  │  G  │  H  │  J  │  K  │  L  │     │ ASDF (48dp, indented)
├─────────────────────────────────────────────────────────┤
│  ⇧  │  Z  │  X  │  C  │  V  │  B  │  N  │  M  │  ⌫       │ Letters (48dp)
├─────────────────────────────────────────────────────────┤
│ !#1 │  🌐 │       English (EN)        │  .  │  ↵           │ Bottom (48dp)
└─────────────────────────────────────────────────────────┘

Behavior: Direct English text — no conversion, just like Samsung keyboard
Spacebar shows: "English (EN)"
🌐 key: tap to switch to Banglu mode
No suggestion bar (or show autocomplete)
```

### Mode 3: Symbols
```
┌─────────────────────────────────────────────────────────┐
│  1  │  2  │  3  │  4  │  5  │  6  │  7  │  8  │  9  │  0  │
├─────────────────────────────────────────────────────────┤
│  +  │  ×  │  ÷  │  =  │  /  │  _  │  <  │  >  │  [  │  ]  │
├─────────────────────────────────────────────────────────┤
│  !  │  @  │  #  │  $  │  %  │  ^  │  &  │  *  │  (  │  )  │
├─────────────────────────────────────────────────────────┤
│ 1/2 │  –  │  "  │  "  │  :  │  ;  │  ?  │  ⌫              │
├─────────────────────────────────────────────────────────┤
│ ABC │  ,  │       [current lang]      │  .  │  ↵           │
└─────────────────────────────────────────────────────────┘

ABC returns to the previously active mode (Banglu or English)
```

---

## Implementation Plan

### Task 1: Keyboard State Management
- Create `KeyboardMode` enum: `BANGLU`, `ENGLISH`, `SYMBOLS_1`, `SYMBOLS_2`
- Track current mode, shift state, caps lock
- Mode persists across keyboard show/hide
- 🌐 key toggles BANGLU ↔ ENGLISH
- !#1 / ABC toggles letters ↔ symbols

### Task 2: Number Row
- Add persistent number row above QWERTY
- Numbers always commit directly (no conversion in Banglu mode)
- Height: 42dp, same key styling

### Task 3: Language Switching (🌐 key)
- In BANGLU mode: keys feed buffer → SmartEngine → Bengali composing text
- In ENGLISH mode: keys commit directly as English letters (like Samsung)
- Spacebar label changes: "বাংলু (BN)" ↔ "English (EN)"
- 🌐 key position: left of spacebar (replacing comma or as separate key)
- Visual indicator: spacebar text shows current mode

### Task 4: Symbols Layout
- Two pages of symbols matching Samsung
- Page 1: math, brackets, common symbols
- Page 2: currency, shapes, rare symbols
- 1/2 and 2/2 toggle between pages
- ABC returns to letter mode
- Numbers stay on top in symbol mode too

### Task 5: Shift / Caps Lock Behavior
- Single tap: shift ON for one letter, then auto-off
- Double tap: CAPS LOCK (all uppercase until tapped again)
- Shift icon changes: ⇧ (off) → ⇧ filled (on) → ⇧ underlined (caps lock)
- In BANGLU mode: shift maps to aspirated/retroflex forms (t→ত, T→ট)

### Task 6: Key Styling (Samsung-match)
- Corner radius: 10dp (Samsung uses slightly more rounded than our 8dp)
- Key height: 48dp for letter rows, 42dp for number row
- Proper elevation/shadow on keys (subtle 1dp)
- Press animation: darken key background
- Font: system default, 20sp for letters, 16sp for numbers, 14sp for labels

### Task 7: Bottom Row Layout
- !#1 button (15% width) — symbols toggle
- 🌐 globe button (10% width) — language switch
- Spacebar (45% width) — shows mode label
- Period . (10% width)
- Enter ↵ (20% width) — adapts to context (search icon, send icon, etc.)

### Task 8: iOS Keyboard Extension (Same Layout)
- Port the exact same layout to Swift UIKit for iOS keyboard extension
- Same 5-row structure, same dimensions in dp→points
- 🌐 becomes the required Apple globe key
- Same BANGLU/ENGLISH mode switching

---

## File Changes

### Android (Compose)
```
android-keyboard/src/main/kotlin/com/banglu/keyboard/
├── BangluIMEService.kt          # Update: mode state, English passthrough
├── ComposeKeyboardView.kt       # Rewrite: full Samsung-style layout
├── KeyboardState.kt             # NEW: mode, shift, symbols state
├── KeyboardKeys.kt              # NEW: key definitions per mode
├── KeyboardTheme.kt             # NEW: colors, dimensions, typography
└── SuggestionBarView.kt         # DELETE: merged into Compose
```

### iOS (Swift)
```
ios-keyboard/KeyboardExtension/
├── KeyboardViewController.swift  # Update: mode switching
├── KeyboardView.swift           # Rewrite: Samsung-style layout
├── KeyboardState.swift          # NEW: mode management
└── SuggestionBar.swift          # Update: Compose-like chips
```

---

## Priority Order

1. **Task 1+2+3**: State + Number row + Language switching (core functionality)
2. **Task 6+7**: Key styling + Bottom row (visual quality)
3. **Task 4**: Symbols layout (feature completeness)
4. **Task 5**: Shift/caps lock refinement
5. **Task 8**: iOS port
