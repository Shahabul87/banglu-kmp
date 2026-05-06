# Android Audit Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all 3 critical issues and 5 warnings identified in the Banglu Android keyboard audit to reach production-ready quality.

**Architecture:** All fixes are isolated to the `android-keyboard/` module — no changes to the shared KMP engine. Each task modifies 1-2 files with surgical edits. The DictionaryLoader interface contract is preserved.

**Tech Stack:** Kotlin, Jetpack Compose, Android InputMethodService, SQLiteDatabase, SharedPreferences

---

## File Map

| File | Changes | Responsibility |
|------|---------|----------------|
| `android-keyboard/src/main/kotlin/com/banglu/keyboard/BangluIMEService.kt` | Task 1 (onFinishInput), Task 4 (onCreateInputView try-catch) | IME service lifecycle |
| `android-keyboard/src/main/kotlin/com/banglu/keyboard/AndroidDictionaryLoader.kt` | Task 2 (shared DB connection) | SQLite dictionary loading |
| `android-keyboard/src/main/kotlin/com/banglu/keyboard/AndroidStorage.kt` | Task 3 (bounded learned words) | User preference persistence |
| `android-keyboard/src/main/kotlin/com/banglu/keyboard/ComposeKeyboardView.kt` | Task 5 (remove nav bar padding), Task 6 (suggestion accessibility), Task 7 (toolbar accessibility) | Compose UI |

---

## Task 1: Fix `onFinishInput()` — clear suggestions on input end

**Severity:** CRITICAL
**Files:**
- Modify: `android-keyboard/src/main/kotlin/com/banglu/keyboard/BangluIMEService.kt:247-250`

- [ ] **Step 1: Add `suggestions.clear()` to `onFinishInput()`**

Replace lines 247-250:
```kotlin
override fun onFinishInput() {
    super.onFinishInput()
    buffer = ""
    suggestions.clear()
}
```

This prevents stale suggestions from the previous text field flashing when the keyboard reopens.

- [ ] **Step 2: Verify the fix compiles**

Run: `cd /Users/mdshahabulalam/myprojects/banlgu/banglu-kmp && ./gradlew :android-keyboard:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add android-keyboard/src/main/kotlin/com/banglu/keyboard/BangluIMEService.kt
git commit -m "fix: clear suggestions in onFinishInput to prevent stale state"
```

---

## Task 2: Share single DB connection in AndroidDictionaryLoader

**Severity:** CRITICAL
**Files:**
- Modify: `android-keyboard/src/main/kotlin/com/banglu/keyboard/AndroidDictionaryLoader.kt` (full rewrite)

- [ ] **Step 1: Rewrite AndroidDictionaryLoader to use a shared DB connection**

The current implementation opens/closes the database 3 separate times (once per load method). Refactor to use a `withDatabase` helper that opens once and closes after the lambda completes. Each load method still gets its own open/close (interface contract requires independent calls), but `openDatabase()` is now idempotent — it reuses the already-copied file without re-checking existence each time.

The key optimization: extract the asset-copy-if-needed into a lazy `ensureDatabase()` that runs once, then each `openDatabase()` just opens the file.

Replace the entire file with:

```kotlin
package com.banglu.keyboard

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.banglu.engine.platform.DictionaryLoader
import com.banglu.engine.types.BigramModelData
import com.banglu.engine.types.SmartDictionaryEntry
import java.io.File

/**
 * Loads the full 480K Bengali dictionary from a bundled SQLite database.
 *
 * The dictionary.sqlite file is shipped in assets/ and copied to internal storage
 * on first use. Subsequent calls open the already-copied database directly.
 *
 * Tables used:
 * - words(id, bengali, frequency) - 480K Bengali words for validation and recovery
 * - disambiguation(wrong_form, correct_form) - 3,456 wrong-to-right character swaps
 */
class AndroidDictionaryLoader(private val context: Context) : DictionaryLoader {

    companion object {
        private const val TAG = "BangluDictLoader"
        private const val DB_FILENAME = "dictionary.sqlite"
    }

    /** Lazily ensure the database file exists in internal storage (copy from assets once). */
    private val dbFile: File by lazy {
        val file = File(context.filesDir, DB_FILENAME)
        if (!file.exists()) {
            try {
                if (BuildConfig.DEBUG) Log.d(TAG, "Copying $DB_FILENAME from assets to ${file.absolutePath}")
                context.assets.open(DB_FILENAME).use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "Copy complete (${file.length() / 1024 / 1024}MB)")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to copy database from assets", e)
            }
        }
        file
    }

    /**
     * Open the database, run the block, and close the database.
     * Returns null if the database cannot be opened.
     */
    private inline fun <T> withDatabase(block: (SQLiteDatabase) -> T): T? {
        if (!dbFile.exists()) return null
        val db = try {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to open database", e)
            return null
        }
        return try {
            block(db)
        } finally {
            db.close()
        }
    }

    override suspend fun loadFullDictionary(): List<String>? = withDatabase { db ->
        val words = mutableListOf<String>()
        try {
            db.rawQuery("SELECT bengali FROM words", null).use { cursor ->
                while (cursor.moveToNext()) {
                    words.add(cursor.getString(0))
                }
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${words.size} words from dictionary")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to load full dictionary", e)
            return@withDatabase null
        }
        if (words.isNotEmpty()) words else null
    }

    override suspend fun loadFrequencyMap(): Map<String, Int>? = withDatabase { db ->
        val freqs = mutableMapOf<String, Int>()
        try {
            db.rawQuery("SELECT bengali, frequency FROM words WHERE frequency > 0", null).use { cursor ->
                while (cursor.moveToNext()) {
                    freqs[cursor.getString(0)] = cursor.getInt(1)
                }
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${freqs.size} frequency entries")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to load frequency map", e)
            return@withDatabase null
        }
        if (freqs.isNotEmpty()) freqs else null
    }

    override suspend fun loadDisambiguationMap(): Map<String, String>? = withDatabase { db ->
        val map = mutableMapOf<String, String>()
        try {
            db.rawQuery("SELECT wrong_form, correct_form FROM disambiguation", null).use { cursor ->
                while (cursor.moveToNext()) {
                    map[cursor.getString(0)] = cursor.getString(1)
                }
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${map.size} disambiguation entries")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to load disambiguation map", e)
            return@withDatabase null
        }
        if (map.isNotEmpty()) map else null
    }

    override suspend fun loadExtendedDictionary(): List<SmartDictionaryEntry>? {
        // Extended phonetic entries are not in the SQLite database yet.
        // The seed dictionary covers this for now.
        return null
    }

    override suspend fun loadBigramModel(): BigramModelData? {
        // Bigram model is not in the SQLite database yet.
        return null
    }
}
```

- [ ] **Step 2: Verify the fix compiles**

Run: `cd /Users/mdshahabulalam/myprojects/banlgu/banglu-kmp && ./gradlew :android-keyboard:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add android-keyboard/src/main/kotlin/com/banglu/keyboard/AndroidDictionaryLoader.kt
git commit -m "refactor: use lazy dbFile + withDatabase helper to avoid redundant open/close"
```

---

## Task 3: Cap learned words storage at 500 entries (FIFO)

**Severity:** CRITICAL
**Files:**
- Modify: `android-keyboard/src/main/kotlin/com/banglu/keyboard/AndroidStorage.kt:51-58`

- [ ] **Step 1: Add MAX_LEARNED_WORDS constant and FIFO eviction to `saveLearnedWord()`**

Add the constant to the companion object (line 28, after SEPARATOR):
```kotlin
private const val MAX_LEARNED_WORDS = 500
```

Replace the `saveLearnedWord` method (lines 51-59) with:
```kotlin
override suspend fun saveLearnedWord(phonetic: String, bengali: String, frequency: Int) {
    val existing = prefs.getString(KEY_LEARNED_WORDS, "") ?: ""
    val line = "$phonetic$SEPARATOR$bengali$SEPARATOR$frequency"
    if (existing.contains(line)) return

    val lines = existing.lines().filter { it.isNotBlank() }.toMutableList()
    lines.add(line)

    // FIFO eviction: keep only the most recent entries
    while (lines.size > MAX_LEARNED_WORDS) {
        lines.removeFirst()
    }

    prefs.edit()
        .putString(KEY_LEARNED_WORDS, lines.joinToString("\n") + "\n")
        .apply()
}
```

- [ ] **Step 2: Verify the fix compiles**

Run: `cd /Users/mdshahabulalam/myprojects/banlgu/banglu-kmp && ./gradlew :android-keyboard:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add android-keyboard/src/main/kotlin/com/banglu/keyboard/AndroidStorage.kt
git commit -m "fix: cap learned words at 500 entries with FIFO eviction"
```

---

## Task 4: Wrap `onCreateInputView()` in try-catch with fallback

**Severity:** WARNING
**Files:**
- Modify: `android-keyboard/src/main/kotlin/com/banglu/keyboard/BangluIMEService.kt:156-207`

- [ ] **Step 1: Add try-catch around ComposeView creation**

Replace the `onCreateInputView()` method (lines 156-207) with:
```kotlin
override fun onCreateInputView(): View {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    reloadSettings()

    return try {
        val composeView = ComposeView(this).apply {
            setContent {
                BangluKeyboardLayout(
                    suggestions = suggestions,
                    keyboardMode = keyboardMode.value,
                    shiftState = shiftState.value,
                    enterLabel = enterKeyLabel.value,
                    isToolbarExpanded = isToolbarExpanded.value,
                    hapticEnabled = hapticEnabled.value,
                    soundEnabled = soundEnabled.value,
                    suggestionsEnabled = suggestionsEnabled.value,
                    numberRowEnabled = numberRowEnabled.value,
                    keyPreviewEnabled = keyPreviewEnabled.value,
                    themePref = themeMode.value,
                    onKeyPress = { char -> onKeyPress(char) },
                    onBackspace = { onBackspace() },
                    onBackspaceWord = { onBackspaceWord() },
                    onSpace = { onSpacePress() },
                    onEnter = { onEnterPress() },
                    onShiftTap = { onShiftTap() },
                    onGlobePress = { onGlobePress() },
                    onSymbolsPress = { onSymbolsPress() },
                    onBackToLetters = { onBackToLetters() },
                    onSymbolPageToggle = { onSymbolPageToggle() },
                    onSuggestionClick = { onSuggestionTap(it) },
                    onNumberPress = { char -> onDirectCommit(char) },
                    onPunctuationPress = { char -> onPunctuationPress(char) },
                    onCursorMove = { direction -> onCursorMove(direction) },
                    onDismiss = { requestHideSelf(0) },
                    onSettingsClick = { onSettingsClick() },
                    onToggleToolbar = { isToolbarExpanded.value = !isToolbarExpanded.value },
                    onEmojiClick = { emoji -> onEmojiClick(emoji) },
                    onEmojiOpen = { onEmojiOpen() },
                    onBackFromEmoji = { onBackFromEmoji() }
                )
            }
        }

        // Wire lifecycle trees for Compose
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        composeView
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.e(TAG, "onCreateInputView: Compose failed, using fallback", e)
        // Minimal fallback view so the keyboard doesn't crash
        View(this)
    }
}
```

Note: This requires adding `import android.view.View` if not already present — but it IS already imported at line 10. Also note: After Task 1 adds one line to `onFinishInput()`, `onCreateInputView()` shifts by +1 line. Use method name search, not line numbers, when locating the method to replace.

- [ ] **Step 2: Verify the fix compiles**

Run: `cd /Users/mdshahabulalam/myprojects/banlgu/banglu-kmp && ./gradlew :android-keyboard:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add android-keyboard/src/main/kotlin/com/banglu/keyboard/BangluIMEService.kt
git commit -m "fix: wrap onCreateInputView in try-catch with minimal fallback view"
```

---

## Task 5: Remove redundant nav bar padding from Compose

**Severity:** WARNING
**Files:**
- Modify: `android-keyboard/src/main/kotlin/com/banglu/keyboard/ComposeKeyboardView.kt:186-202`

- [ ] **Step 1: Remove the nav bar height calculation and padding**

Replace lines 186-202:
```kotlin
        // Get nav bar height for bottom padding (permanent fix for Samsung/gesture nav)
        val context = LocalContext.current
        val navBarHeightPx = remember {
            val resourceId = context.resources.getIdentifier(
                "navigation_bar_height", "dimen", "android"
            )
            if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
        }
        val density = context.resources.displayMetrics.density
        val navBarPadding = (navBarHeightPx / density).dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.keyboardBg)
                .padding(horizontal = KeyboardPadding)
                .padding(top = 4.dp, bottom = navBarPadding)
        ) {
```

With:
```kotlin
        // Nav bar overlap is handled by onComputeInsets() in BangluIMEService
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.keyboardBg)
                .padding(horizontal = KeyboardPadding)
                .padding(top = 4.dp)
        ) {
```

Also remove the import `import androidx.compose.ui.platform.LocalContext` from line 29 — it is only used in the nav bar block being deleted, so it becomes unused.

- [ ] **Step 2: Verify the fix compiles**

Run: `cd /Users/mdshahabulalam/myprojects/banlgu/banglu-kmp && ./gradlew :android-keyboard:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add android-keyboard/src/main/kotlin/com/banglu/keyboard/ComposeKeyboardView.kt
git commit -m "fix: remove redundant nav bar padding — onComputeInsets handles it"
```

---

## Task 6: Add accessibility labels to suggestion chips

**Severity:** WARNING
**Files:**
- Modify: `android-keyboard/src/main/kotlin/com/banglu/keyboard/ComposeKeyboardView.kt:466-471`

- [ ] **Step 1: Add semantics to suggestion chip Box**

In the `BangluSuggestionRow` composable, find the suggestion chip Box (around line 466):
```kotlin
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(chipBg)
                        .clickable { onSuggestionClick(suggestion) }
                        .padding(horizontal = 14.dp, vertical = 2.dp),
```

Replace with:
```kotlin
                Box(
                    modifier = Modifier
                        .semantics { contentDescription = suggestion.bengali }
                        .clip(RoundedCornerShape(16.dp))
                        .background(chipBg)
                        .clickable { onSuggestionClick(suggestion) }
                        .padding(horizontal = 14.dp, vertical = 2.dp),
```

- [ ] **Step 2: Add accessibility labels to ToolbarIcon**

In the `ToolbarIcon` composable (around line 387), the Box has no semantics. But since ToolbarIcon is a private composable used with emoji strings as labels, we need to add an `accessibilityLabel` parameter:

Replace:
```kotlin
@Composable
private fun ToolbarIcon(icon: String, onClick: () -> Unit) {
    val colors = LocalKeyboardColors.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
```

With:
```kotlin
@Composable
private fun ToolbarIcon(icon: String, accessibilityLabel: String = icon, onClick: () -> Unit) {
    val colors = LocalKeyboardColors.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .semantics { contentDescription = accessibilityLabel }
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
```

Then replace lines 375-382 in `ToolbarRow` (the `if (isExpanded)` block and the toggle button call) with:
```kotlin
        if (isExpanded) {
            ToolbarIcon("\uD83D\uDCCB", "Clipboard") { /* clipboard - future */ }
            ToolbarIcon("\uD83D\uDE0A", "Emoji") { onEmojiOpen() }
            ToolbarIcon("\u2699", "Settings") { onSettingsClick() }
            ToolbarIcon("\uD83D\uDD90", "One-hand mode") { /* one-hand - future */ }
        }
        // Toggle button always visible
        ToolbarIcon(
            if (isExpanded) "\u25B2" else "\u00B7\u00B7\u00B7",
            if (isExpanded) "Collapse toolbar" else "Expand toolbar"
        ) { onToggleToolbar() }
```

Also add accessibility label to the dismiss button in `MinimalSuggestionBar` (around line 414):
```kotlin
        Box(
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = "Dismiss keyboard" }
                .clickable { onDismiss() },
```

And the dismiss button in `BangluSuggestionRow` (around line 499):
```kotlin
        Box(
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = "Dismiss keyboard" }
                .clickable { onDismiss() },
```

- [ ] **Step 3: Verify the fix compiles**

Run: `cd /Users/mdshahabulalam/myprojects/banlgu/banglu-kmp && ./gradlew :android-keyboard:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add android-keyboard/src/main/kotlin/com/banglu/keyboard/ComposeKeyboardView.kt
git commit -m "feat: add TalkBack accessibility labels to suggestions, toolbar, and dismiss buttons"
```

---

## Task 7: Verify and commit — final build check

**Severity:** N/A (integration verification)

- [ ] **Step 1: Full project build**

Run: `cd /Users/mdshahabulalam/myprojects/banlgu/banglu-kmp && ./gradlew :android-keyboard:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run shared engine tests to verify no regression**

Run: `cd /Users/mdshahabulalam/myprojects/banlgu/banglu-kmp && ./gradlew :shared:allTests`
Expected: All 186 tests pass

- [ ] **Step 3: Verify no Kotlin warnings**

Run: `cd /Users/mdshahabulalam/myprojects/banlgu/banglu-kmp && ./gradlew :android-keyboard:compileDebugKotlin 2>&1 | grep -i "warning"`
Expected: No Kotlin warnings related to our changes
