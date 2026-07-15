# Banglu Desktop — বাংলু লেখক (Bangla Editor) Design

**Date:** 2026-07-14
**Status:** Approved by user (section-by-section, this session)
**Scope:** Replace the desktop app's two-box converter with a full-page Bangla
editor. The ⌘⇧B mini converter and tray behavior are unchanged. No new OS
permissions, no signing requirements, no network.

---

## 1. Product decisions (user-approved)

1. **Typing model — live, in place.** Banglish keystrokes form Bangla at the
   cursor as you type (`a m i` → আ → আম → আমি), space commits, the page holds
   Bangla. No second box. Same WYSIWYG contract as the Android IME
   (CLAUDE.md invariant #2).
2. **Documents — real files plus export.** Save/open `.txt`, export to Word
   (.docx) and PDF (via OS print dialog), always-on autosave draft.
3. **Corrections — popup at cursor + click any word.** Avro-style floating
   candidate list under the forming word (pick with ১–৬ / arrows / click);
   clicking any committed word reopens its candidates and swaps it.
4. **App shape — the editor IS the app.** The main window becomes the editor;
   the two-box converter UI is removed. One identity: বাংলু লেখক.
5. **AI later.** Spell/grammar correction is a future round; v1 builds the
   seam (SuggestionSource) but ships no AI and no network.

## 2. Approach (chosen: controlled text field)

One full-page Compose text surface whose keystrokes we intercept. The
document buffer holds committed Bangla; alongside it we keep the raw Banglish
of the word currently forming at the cursor. Every keystroke re-converts just
that word and splices its Bangla form into the display. This is the Android
composing-region architecture rebuilt on desktop, reusing
`SmartEngineAdapter.convertWord` / `getSuggestions` unchanged.

Rejected: commit-on-space-only (violates the approved live-forming model) and
a fully custom canvas text engine (months of work re-owning selection,
scrolling, clipboard, undo).

Supporting decisions:
- **Bundle Noto Sans Bengali** in the app; conjuncts must render perfectly on
  every machine.
- **No direct-PDF library.** Java PDF libraries do not do complex-script
  shaping and break Bengali conjuncts. v1 PDF = OS print dialog (⌘P → Save as
  PDF / Microsoft Print to PDF) via Java2D, which shapes Bengali correctly.

## 3. Layout & visual design

```
┌─────────────────────────────────────────────────────────────┐
│  বাংলু লেখক          চিঠি.txt ●               পূর্ণ অভিধান ✓  │  top bar 44px
│  ⌘N নতুন   ⌘O খুলুন   ⌘S সেভ   এক্সপোর্ট ▾        ⋯          │
├─────────────────────────────────────────────────────────────┤
│        আমার প্রিয় বন্ধু,                                      │
│        কেমন আছো? অনেক দিন পর তোমাকে লিখছি।                   │
│        আমি কali▌                                            │
│             ┌──────────────┐                                │
│             │ ১ কালি  ২ কালী │  candidate popup               │
│             └──────────────┘                                │
├─────────────────────────────────────────────────────────────┤
│  ১২৪ শব্দ · স্বয়ংক্রিয় সংরক্ষিত ✓          গ্লোবাল হটকি ⌘⇧B    │  status 28px
└─────────────────────────────────────────────────────────────┘
```

- Existing brand palette: bg `0xFF080D16`, page card `0xFF0D1524`, accent sky
  `0xFF64D2FF`, borders `0xFF1E293B`, muted `0xFF64748B`.
- Page is a lighter card floating with generous margins; text column max ~68
  characters, centered — manuscript, not edge-to-edge.
- Typography: bundled Noto Sans Bengali, body 19sp, line-height 1.7 (Bangla
  needs tall lines for matras/conjuncts).
- Forming word: live Bangla with subtle sky underline; popup floats below it
  (dark card, soft shadow, Bengali numerals ১–৬ for keyboard pick).
- Top bar: file name + unsaved ● dot, four quiet text actions, dictionary
  status. `⋯` menu: recent files, digit-mode toggle, learned-words reset,
  about. No icon toolbar, no ribbon.
- Status bar: word count (Bengali numerals), autosave state, hotkey reminder
  (moves down from the current main screen).
- Window 860×640 default, size remembered, min 520×400.

## 4. Editing engine

`EditorState` — pure Kotlin, no Compose dependency, unit-testable against the
real `dictionary.sqlite`.

**Document model.** A list of committed segments, each remembering its
origin: `(bangla, raw, chosen)` — plus whitespace/punctuation segments.
Stored raw Banglish powers click-to-fix and the future AI corrector.

**Forming word.** At most one, stored as raw Banglish; the page displays its
conversion, underlined.

Keystroke rules:
- Letter → append to formingRaw, re-convert, refresh popup
  (`getSuggestions(formingRaw, 6)`).
- Backspace in a forming word → remove last **Banglish** letter
  (`kali` ⌫ → `kal` → কাল). Otherwise normal committed-text deletion.
- Space → commit the visible Bangla exactly as shown (WYSIWYG), append
  space. **Double space → দাঁড়ি (।)** + space.
- ১–৬ / arrows+Enter / click in popup → commit that candidate; record as a
  learned word via `FileStorage` — same law as Android: picking the engine's
  own first choice teaches nothing, only corrections teach.
- Escape → dismiss popup for this word. The raw-form candidate (plain
  `kali`) is always listed → inline English is one keypress, no mode switch.
- Punctuation / Enter / digits → commit forming word first, then insert.
  Digits become ০–৯ (toggle in ⋯ menu for Latin digits).

**Threading (same law as Android, invariant #1).** No SQLite/disk on the UI
thread. Each keystroke shows the instant rule-based preview synchronously;
full dictionary conversion + suggestions return on `Dispatchers.Default` and
refresh the word/popup, guarded so a stale result never overwrites a newer
keystroke.

**Click a committed word** → segment highlights, same popup (candidates from
stored `raw`), pick swaps the segment and learns. Generic
select-plus-replace — the AI door.

**Undo/redo** (⌘Z/⇧⌘Z) on document snapshots; undoing a commit restores the
forming word.

## 5. Files, autosave, recovery

Rule: a crash, force-quit, or forgotten ⌘S must never cost text.

- Files are plain UTF-8 `.txt` (Bangla only; raw origins are session-only).
- ⌘S সেভ / ⌘⇧S নতুন নামে — native dialog, defaults `~/Documents`, `.txt`
  appended if omitted. Title shows name + unsaved ● dot.
- ⌘O খুলুন — native dialog; unsaved-changes guard (সেভ করুন / বাতিল / সেভ
  ছাড়াই খুলুন). Opened files: click-to-fix works via on-demand
  `ReverseTransliterator` for the raw form.
- ⌘N নতুন — same guard, blank page.
- Recent files: last 8, in ⋯ menu, persisted in `~/.banglu/editor.json`.
- **Autosave (always on):** 2s after last keystroke and on window close, full
  document state (incl. forming word and session raw-per-word) →
  `~/.banglu/draft.json`. On launch: clean exit + saved → reopen same file,
  cursor restored; unsaved draft → page opens with the text in place + quiet
  banner "আগের লেখা ফিরিয়ে আনা হয়েছে". No dialog.
- Red close button hides to tray (as today) after draft flush; tray বন্ধ করুন
  flushes then quits. No exit path skips the flush.

## 6. Export (এক্সপোর্ট ▾)

- **Word (.docx)** — hand-written minimal docx (~150 lines: content types,
  rels, `document.xml`), no POI. Declares Noto Sans Bengali with Nirmala
  UI/Kalpurush fallbacks; Word shapes the script itself. Paragraphs/line
  breaks preserved; nothing more promised in v1.
- **টেক্সট (.txt)** — same as save.
- **প্রিন্ট / PDF (⌘P)** — OS print dialog (macOS "Save as PDF", Windows
  "Microsoft Print to PDF") via Java2D with the bundled font (correct
  shaping). A4, comfortable margins, 19pt body, file name as light header.
- **সব কপি করুন (⌘⇧C)** — whole document to clipboard as plain text (covers
  WhatsApp/Facebook/email pastes).
- Deliberate omissions: HTML/Markdown/rich-text exports, direct-PDF button
  (until shaping is solved).

## 7. AI seam (future)

`SuggestionSource` interface: given the document's segments (bangla + raw),
propose replacements for specific segments. First consumer: the click-to-fix
popup. First implementation: the engine's candidate list. Future AI
(spell/grammar/rewrite) is a second implementation producing the same "swap
segment N for X" operations (popup or wavy underlines). Editor core never
changes; privacy decision (on-device vs opt-in cloud) stays open. v1 ships
the interface + dictionary implementation only — no AI, no network.

## 8. Testing

- `EditorState` JVM tests against the real `dictionary.sqlite` (like the
  existing `*JvmTest` wall): keystroke-by-keystroke forming (`k-e-m-o-n` →
  কেমন), space commit, backspace semantics, double-space দাঁড়ি, candidate
  pick learns / engine-first-choice does NOT learn, undo restores forming
  word, digits → ০-৯.
- **WYSIWYG pin test:** for a corpus of phrases, the committed document
  equals the concatenation of the previews shown (invariant #2, enforced).
- File round-trips: save → open → identical; crash-draft recovery; docx
  unzips to valid XML containing the exact Bangla.
- Manual gate: packaged DMG on the dev Mac — type a real letter, save,
  reopen, export to Word, open in Pages/Word, verify perfect conjuncts.
  Screenshot-verified like every S-round.

## 9. Out of scope for v1

AI correction (seam only), rich text/formatting, HTML/Markdown export,
direct PDF generation, multiple tabs/windows, spellcheck underlines,
find/replace, the macOS Input Method (next round, separate design).
