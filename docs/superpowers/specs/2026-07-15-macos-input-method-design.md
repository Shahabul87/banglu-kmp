# Banglu macOS Input Method (বাংলু ইনপুট মেথড) Design

**Date:** 2026-07-15
**Status:** Approved by user (section-by-section, this session)
**Scope:** A real macOS input method (InputMethodKit) — Banglu appears in the
system input-source menu and converts Banglish→Bangla live inside ANY app
(Notes, browsers, Word, chat apps). The "type directly in Notes" experience
that the S49 popup converter could not deliver. New top-level directory
`macos-ime/`; no changes to the Android IME, desktop editor, or web targets
beyond consuming their existing build artifacts.

---

## 1. Product decisions (user-approved)

1. **Distribution — me first, public later.** v1 installs on the developer's
   own Mac, ad-hoc signed (`codesign --sign -`), $0. Apple Developer ID
   ($99/yr) + notarization deferred until the IME is proven and ready for
   public download on craftsai.org.
2. **v1 behavior set — all of:** candidate window (1–6 / arrows / Enter /
   Esc), double-space দাঁড়ি + Bengali digits ০–৯, learning from picks shared
   with the desktop editor via `~/.banglu/learned.json`, and input-menu
   extras (open editor, open tutorial, learning toggle).
3. **Must-work app gate — all of:** Apple basics (Notes, TextEdit, Pages,
   Spotlight), browsers (Safari + Chrome: Gmail, WhatsApp Web, Google Docs),
   Microsoft Word, Electron chat apps (WhatsApp Desktop, Messenger, Slack,
   Discord). Every gated app must pass at `full` or `plain` mode (see §6);
   none may fail.

## 2. Engine host (chosen: JavaScriptCore + existing JS engine)

The IMK process runs the **same shared Kotlin engine compiled to JS** that
the Chrome extension ships (`banglu-engine.js`, from `shared`'s `js(IR)`
target) with the S45 slim in-memory dictionary (`banglu-slim.json`, 17MB /
1.8MB gz), inside macOS's built-in JavaScriptCore.

Why: it is the real engine — every S-round fix, habit alias, shorthand —
with parity already pinned by the S45/S47 JS test suites; no JVM ships; JSC
is a system framework; word-length conversion is sub-millisecond; process
footprint ~40–60MB (normal for an IME).

Rejected: Kotlin/Native macosArm64 target (a whole new Kotlin platform
bring-up before the first character types — the eventual home only if JSC
becomes a bottleneck); JVM helper daemon over XPC (private JRE inside an
input method, daemon lifecycle, IPC on the keystroke path — heavy for
marginal gain).

Accepted trade-off: the slim dictionary is the web-tier list, not the full
1.35M-row store — identical to the Chrome extension. Rare-word misses still
get the rule-based conversion in the candidates; the desktop editor (full
dictionary) remains the deep-writing tool.

## 3. Process architecture & install

```
Any app (Notes, Chrome, Word…)
    ▲ marked text + commits      ▼ keystrokes
Banglu.app  (~/Library/Input Methods/)
  Swift · IMKServer → BangluInputController
    Composer (state machine)  →  EngineJS (JavaScriptCore:
                                  banglu-engine.js + banglu-slim.json)
    Candidate UI (IMKCandidates, swappable)
    ~/.banglu/learned.json + editor.json  (shared with the editor)
```

- `macos-ime/`: Swift IMK app built via `xcodebuild` from the command line
  (no Xcode GUI in the workflow) + a build script that compiles `shared`'s
  JS target, copies `banglu-engine.js` + `banglu-slim.json` into Resources,
  and ad-hoc signs.
- `Info.plist` declares `InputMethodConnectionName`, Bangla character
  repertoire, an input-source icon (বা glyph), `LSBackgroundOnly`. macOS
  launches the process on demand; no window, no dock icon.
- Install (v1): `make install` — copy to `~/Library/Input Methods/`,
  ad-hoc sign; the user adds Banglu once in System Settings → Keyboard →
  Input Sources → Bangla. English mode = the system input-source switcher
  (Globe / Ctrl-Space); the IME has no internal EN mode.
- Engine boot: JSC context + slim-dictionary parse once at launch
  (~200–400ms, unnoticeable).
- Privacy: no network entitlement, no sockets — typing physically cannot
  leave the machine (same architectural enforcement as the Android IME).

## 4. Typing state machine (Composer)

Pure Swift value-logic class, no IMK imports — unit-testable headlessly
against the real JS engine. Same contract as the editor's `EditorState`.

- At most one forming word, stored as raw Banglish (`formingRaw`). The
  app's **marked text** shows `engine.convert(formingRaw)` — live Bangla
  forming at the cursor, underlined by the host.
- Letter a–z → append to raw, re-convert synchronously (no async refine
  loop needed — JSC is sub-ms), update marked text + candidates.
- Space → commit the marked text exactly as shown (WYSIWYG invariant),
  then space. Double space → দাঁড়ি (।) via commit replacement; a third
  space stays a space.
- Backspace in a forming word → drop the last Banglish letter
  (`kali` ⌫ → `kal` → কাল). No forming word → pass through.
- Digits: while the candidate window shows, 1–6 pick; otherwise commit
  forming, then type ০–৯.
- Punctuation / Enter / Tab → commit forming first, then pass the original
  key through (Enter must still send messages in chat apps).
- Escape → discard the conversion, commit the raw English word (inline
  English in one keypress; no mode switch).
- Focus change / mouse click / app switch (`commitComposition`) → commit
  the visible Bangla; a half-formed word is never lost.
- Everything else (⌘ shortcuts, bare arrows, function keys) passes through
  untouched.
- Deliberate difference from the editor: **no click-committed-word-to-fix**
  — committed text belongs to the host app (platform contract; same as
  Avro/Pinyin/Japanese IMEs). Fix by retyping or in the editor.

## 5. Candidate window

- Content: `engine.suggestions(raw, 6)` + the raw English itself as the
  last row (suggestion[0] is the commit contract; raw row = one-keypress
  English). Updates every letter; never shows with an empty forming word;
  never steals focus; hides on commit/Escape/focus loss.
- Primary: **IMKCandidates** (system panel — free caret positioning in any
  app, scrolling, light/dark). Selection keys 1–6; ↑/↓ highlight; Enter
  commits highlighted + space; Esc closes and keeps raw typing.
- Fallback (known IMKCandidates quirks): slim borderless NSPanel positioned
  via `attributes(forCharacterIndex:)` caret rect — editor-style dark card,
  ১–৬ numerals, Noto Sans Bengali. The Composer talks to a tiny protocol
  (`show(candidates:at:)`, `move(±1)`, `pick(n)`, `hide`) so the swap is
  contained to one file.
- Picking teaches ONLY when the pick differs from what would have been
  committed anyway (invariant #3 — the engine's own first choice teaches
  nothing).

## 6. Per-app compatibility & degraded mode

- Apple basics: first-class IMK hosts — expected clean pass.
- Browsers: WebKit/Chromium translate marked text into standard web
  composition events (how CJK users type in Gmail/Docs today). Google Docs
  (custom text layer) is the watch item.
- Word: own composition rendering; historically works with IMK IMEs with
  cosmetic quirks. Gate = text correctness, not pixel polish.
- Electron chat apps: Chromium composition — generally works; classic
  failure is a stray/duplicated composing string in some versions.
- **Degraded `plain` mode** (the safety net): no marked text at all —
  keystrokes buffer internally, the forming Bangla shows only in the
  candidate window, space commits via plain `insertText` (universally
  safe). Loses the in-place underline in that app; keeps correct Bangla,
  candidates, learning, dari. A per-app table (bundle ID → `full`/`plain`)
  ships in the app, default `full`; gate failures get a `plain` entry
  instead of blocking release. Composer identical in both modes — only the
  preview-render strategy switches.
- Password fields bypass input methods at the OS level (Banglu physically
  cannot see them) — state this in the privacy copy.

## 7. Learning, files, and the input menu

- One brain: `~/.banglu/learned.json`, the editor's exact file and row
  format (`{p, b, f, t}`). Loaded into the JS engine at boot; divergent
  picks append/bump rows. NOTE (verified 2026-07-15): `BangluWebEngine`
  (jsMain) currently exports only initSeed / attachSlimDictionary /
  convert / suggestions / instantPreview — the plan must add two small
  `@JsExport` methods to it (`applyLearnedWords(json)` wrapping the
  adapter's learning load, and `recordPick(raw, bangla)` wrapping
  `onWordSelected(..., explicitChoice = true)`). This is the one `shared`
  change the IME needs; it also benefits the Chrome extension later.
- Concurrent-write safety: read-fresh → update → write `.tmp` → atomic
  replace (`FileManager.replaceItemAt`) — the editor's proven pattern.
  Last-writer-wins is acceptable (collisions need two picks in the same
  millisecond from two processes).
- Refresh moment: re-read `learned.json` on every input-source activation
  (no file-watcher machinery); editor learnings appear next switch-in.
- Prefs: `~/.banglu/editor.json` gains `learningEnabled` (default true),
  honored by both the editor and the IME.
- Input menu (বা icon dropdown): **বাংলু এডিটর খুলুন** (launches
  /Applications/Banglu.app), **শিখুন — টিউটোরিয়াল** (launches the editor
  into the tutorial via a launch argument), **শেখা চালু/বন্ধ** (flips
  `learningEnabled`, checkmark shows state).

## 8. Testing

- **Composer unit tests (XCTest, headless, real JS engine in JSC):**
  live-forming + WYSIWYG space-commit pins (reusing the editor's
  WysiwygPinTest phrases), raw-Banglish backspace, double-space dari /
  triple-space plain, digit-pick vs digit-type, Escape commits raw,
  pick-primary teaches nothing vs non-primary writes exactly one
  learned.json row (temp dir), focus-loss commits. The `plain` mode runs
  the same suite — identical commits, different render path.
- **Engine parity:** unchanged — the S45/S47 JS parity wall in `shared`
  already pins the JS engine + slim dictionary; the IME adds no new engine
  surface.
- **Manual gate (user's Mac):** `make install`, add in System Settings,
  then the standard script in every gated app — `kemon acho bondhu`
  forming/commits, issa/korsi shorthand, one digit-pick correction, one
  Esc-English word, double-space dari, mid-word backspace, click-away
  mid-word commits. Verdict per app: `full` / `plain` (recorded in the
  compatibility table) / fail. Pass = all gated apps at full or plain.
- **Lifecycle:** survives logout/login; input-source switching instant;
  learning round-trips with the editor both directions.

## 9. Out of scope for v1

Developer ID signing/notarization + public .pkg (v2, after the IME is
proven), Kotlin/Native engine host, click-committed-word-to-fix (platform
contract), internal EN/BN mode toggle (system switcher covers it), iOS
keyboard (separate future phase; the old `ios-keyboard-engine` Swift
scaffold is NOT a base for this work), per-app custom settings UI,
trigram/context rerank in the IME (slim-dictionary tier).
