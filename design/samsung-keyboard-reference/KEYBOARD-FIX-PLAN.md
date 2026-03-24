# Keyboard UX Fix Plan — Match Samsung Quality

## Issues Reported
1. Button press not smooth
2. Keys not giving proper words
3. Keys height touching each other
4. Cross button not working

## Root Causes & Fixes

### Fix 1: Increase vertical gap between rows (keys touching)
- BEFORE: KeyGapV = 4.dp
- AFTER: KeyGapV = 8.dp (matches Samsung's 8-12dp gap)
- Also increase KeyRowHeight from 48dp to 52dp

### Fix 2: Smooth key press (haptic + visual on ACTION_DOWN)
- BEFORE: Haptic fires on click (ACTION_UP), uses TextHandleMove
- AFTER: Use pointerInteropFilter to detect ACTION_DOWN, fire VIRTUAL_KEY haptic immediately
- Add key preview popup (character shown above pressed key)
- Add subtle scale animation (0.95 scale on press, 1.0 on release)

### Fix 3: Backspace long-press repeat
- Hold backspace: delete character every 50ms after 400ms delay
- After 1.5s: switch to word-by-word deletion

### Fix 4: Add keyboard dismiss button
- Add a small ↓ (down arrow) button in the toolbar/suggestion bar area
- Tapping it calls `requestHideSelf(0)` to dismiss keyboard

### Fix 5: Font size increase
- Letter keys: 24sp (was 20sp)
- Number keys: 18sp (was 16sp)
- Special keys: 16sp (was 14sp)

### Fix 6: Touch target expansion
- Add 4dp invisible padding around each key for touch expansion
- Keys visually 48dp but touchable area is 56dp
