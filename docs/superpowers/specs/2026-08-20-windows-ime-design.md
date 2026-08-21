# বাংলু টাইপার — Windows System-Wide Input Tool (Design Spec)

**Date:** 2026-08-20
**Status:** Approved design, pre-implementation
**Decision trail:** user chose Path A (Avro-style hook app) over a TSF input
method for v1; chose the full-JVM/full-dictionary host over the slim-JS host;
chose the full typing model plus an English mode toggle. Approach 1
(pure-JVM single app) approved over C#/C++ shells, with the hook isolated
behind an interface as the escape hatch to a native shell.

## 1. Goal

Install one app on Windows and type Bangla in ANY input field — MS Word,
browsers, WhatsApp Desktop, file-rename boxes — the way Bijoy/Avro users
expect, powered by the same shared Banglu engine as Android/desktop/macOS.
Tray on/off, Ctrl+Space toggle, WYSIWYG preview, full 143MB dictionary,
100% offline.

## 2. Non-goals (v1)

- No TSF text service (that is the v2 "proper IME" phase).
- No public signed distribution: v1 is an unsigned MSI for the developer's
  two Windows laptops. Authenticode vs Microsoft Store is a deferred
  decision, same as macOS notarization.
- No auto-elevation for admin apps (documented limitation instead).
- No re-implementation of any conversion rule (invariant 9 — engine
  behavior lives in `shared` only).

## 3. Architecture

One Kotlin process. New Gradle module `windows-ime/` depending on `:shared`
(jvmMain), packaged with jpackage like `desktop-app`.

```
windows-ime/src/main/kotlin/com/banglu/winime/
├── Main.kt                 Compose Desktop tray entry point
├── hook/
│   ├── KeySource.kt        interface: start/stop, key events, swallow decision
│   ├── LowLevelHook.kt     JNA SetWindowsHookEx(WH_KEYBOARD_LL) on a dedicated
│   │                       message-pump thread; re-arm watchdog
│   └── TextInjector.kt     interface + JNA SendInput(KEYEVENTF_UNICODE) impl
├── engine/EngineService.kt SmartEngineAdapter + full sqlite; boot off-thread
├── composer/Composer.kt    pure-Kotlin typing state machine (no JNA imports)
├── ui/PreviewWindow.kt     caret-anchored, non-activating preview + candidates
├── ui/SettingsWindow.kt    hotkey, startup-on-login, passthrough list
└── compat/AppCompat.kt     per-exe passthrough table
```

**Isolation law:** `hook/` is the ONLY package that touches Win32/JNA.
Composer, engine, and UI are Win32-free so the hook layer can be replaced
by a native shell (Approach 3) without touching anything else.

## 4. Typing pipeline

1. Hook callback (synchronous, allocation-free): Bangla mode on AND letter/
   mapped key AND foreground exe not on the passthrough list → swallow +
   enqueue; anything else passes through. The target app never sees roman.
2. Swallowed letters build the roman buffer in `Composer`; the preview
   window shows the live-forming Bangla — instant rule-layer echo, async
   full refine (Android S28 pattern, via shared engine only).
3. Space commits: inject the previewed Bangla via SendInput Unicode.
   WYSIWYG holds by construction (the app receives exactly the preview).
4. Pending-space দাঁড়ি model (port of macOS Composer, same pins): space is
   held; second space → `। `; a letter → `" "` + new word; tight
   punctuation (`,` `।` `?` `!`, checked on the MAPPED char — `.` maps to
   `।` first) swallows the pending space; Enter/Tab/focus-change flushes it.
5. Backspace edits the forming buffer; with an empty buffer it passes
   through. ০-৯ digits per the Bangla-first UX rules.
6. Candidate pick (click or Ctrl+1..5) injects that candidate and teaches
   via `SmartEngine.addWord` — `isPlausibleDynamicMapping` gate intact
   (invariant 11), learning only after full dictionary load (S34 law),
   never from committing the engine's own primary (S26 law).
7. English mode: hotkey cycles বাংলা → English. English = full passthrough,
   only the mode hotkey stays claimed. Off = hook unregistered entirely.
8. Boot: while the store attaches off-thread, letters pass through
   untouched and the tray shows "লোড হচ্ছে…" — never half-convert.

## 5. UI

- **Tray**: icon reflects mode (বাংলা / EN / off / loading). Menu: mode
  toggles, hotkey display (default Ctrl+Space), সেটিংস, টিউটোরিয়াল
  (v1 links to the web guide), quit.
- **Preview strip**: one undecorated always-on-top window, visible only
  while a word forms; forming word + up to 6 candidates (revised from 5
  during the final review: the last entry is always the raw-roman escape
  hatch, so a 5-chip strip hid it whenever the engine filled the list —
  `Composer.MAX_CANDIDATES` is now the single source of that number, shared
  by the digit-pick range and the chip row). Anchored at the
  caret via `GetGUIThreadInfo`; falls back to near-cursor / remembered
  corner when the app hides its caret (Electron). `WS_EX_NOACTIVATE` —
  never steals focus.
- **Startup on login**: registry Run key, on by default, toggle in settings.

## 6. Learning, storage, privacy

- `Storage.kt` `FileStorage` unchanged → `%USERPROFILE%\.banglu\learned.json`,
  same `{p,b,f,t}` rows, atomic tmp + `Files.move(REPLACE_EXISTING)`
  (invariant 10). One brain shared with desktop editor conventions.
- Privacy = invariant 12: no network dependency, no telemetry, keystrokes
  never leave the process. Secure desktop (UAC/login) is untouchable by OS
  design. Passthrough list ships pre-seeded with common password managers.
- Elevated apps cannot receive injection (UIPI): documented limitation,
  "run as administrator" guidance in the tutorial.

## 7. Packaging & CI

- jpackage MSI (Temurin 17 runtime, `java.sql` module, dictionary in app
  resources) — the desktop-app packaging pattern, including the
  `verifyPackagedDictionary`-style version gate against
  `DictionaryVersion.REQUIRED`.
- New workflow `windows-ime-release.yml`: `windows-v*` tags, windows-latest
  runner, dictionary downloaded from the `dictionary` release asset
  (S128 pattern). MSI attached to a GitHub release.
- CI wall gains `:windows-ime:test` (pure-JVM, runs on the ubuntu runner).

## 8. Testing

- **Automated (the wall):** `:windows-ime:test` drives `Composer`
  keystroke-by-keystroke on the real repo-root `dictionary.sqlite`:
  WYSIWYG pins, pending-space দাঁড়ি pins, digits, punctuation swallow,
  backspace edges, English passthrough, learning gates. Same class as
  desktop `EditorState` tests and the macOS `BangluCoreTestRunner`.
- **Manual (laptop gate):** written checklist per build on the user's two
  Windows laptops — Word, Excel, Chrome (Facebook/Gmail), Notepad,
  WhatsApp Desktop, file-rename box; hotkey toggle; tray states; preview
  positioning; 30-minute hook-survival session; idle CPU. No "done" claim
  without this gate (the dev machine is a Mac and can never run the app).

## 9. Risk register

| Risk | Answer |
|---|---|
| Windows silently drops a slow hook | allocation-free callback + watchdog re-arm + tray warning |
| JVM GC pause lags keystrokes | engine work off the hook thread; measure on real laptops before further tuning |
| Caret position unavailable | cursor-fallback + per-app remembered position |
| Electron/host quirks | AppCompat passthrough table (macOS S51 lesson) |
| Hook approach fundamentally flaky | swap `hook/` for a native shell — Approach 3 escape hatch behind `KeySource` |
| SmartScreen scares testers | documented "More info → Run anyway"; signing decision deferred |
