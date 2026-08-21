# বাংলু টাইপার — Windows Laptop Verification Checklist

**What this document is for.** `windows-ime/` was designed, written, and unit-tested
entirely on a Mac. Every line of Win32-specific code in `windows-ime/src/main/kotlin/com/banglu/winime/hook/`
— the low-level keyboard hook, `SendInput` injection, caret lookup, foreground-app
lookup — has never executed on a real Windows machine. `:windows-ime:test` (60
tests) proves the pure-Kotlin typing state machine (`Composer`, `Controller`,
`AppCompat`, `WinStorage`, `WinPrefs`) is correct against the real dictionary; it
proves nothing about whether the hook actually installs, whether `SendInput`
actually lands text in Word, or whether the tray actually looks right on a real
taskbar. This checklist is the only instrument that will ever answer those
questions. Run it start to finish on each laptop; do not skip rows because they
"probably work" — several of them exist because a code reviewer found the exact
opposite.

**How to use it.** One row = one check. Do the exact keystrokes listed, compare
against the exact expected result, and mark Pass or Fail. Where a Bengali UI
string is quoted, it is copied verbatim from `windows-ime/src/main/kotlin/com/banglu/winime/Main.kt`
— if what you see on screen differs from the quoted string (even by a single
character or a missing symbol), that is a Fail, not a rounding error. Write
the exact wording, error message, or number for anything you find below the
table in the Notes column — "didn't work" is not enough for us to act on later.

**Run this on at least one laptop before declaring the feature done.** Both
laptops if you can, since one of the goals is confirming the app survives a
machine you didn't develop it on.

Legend: **P** = Pass, **F** = Fail, **N/A** = not applicable to this machine
(e.g. no password manager installed).

**Recording results from two laptops.** Section 0 has separate Laptop A /
Laptop B columns because it's per-machine facts, not pass/fail. Sections 1–5
use a single **P/F** column per row instead — if you run the checklist on
both laptops, record both results in that one cell as `A:P B:F` (or `A:P
B:P` if both pass). If you only run one laptop, a bare `P` or `F` is enough.

---

## 0. Setup

| # | Item | Laptop A | Laptop B | Notes |
|---|---|---|---|---|
| 0.1 | Windows build/edition, Word/Excel version, Chrome version, WhatsApp Desktop installed? | | | |
| 0.2 | Display scaling in effect (Settings → System → Display → Scale): note the default, you'll switch it to 150% later for one test | | | |
| 0.3 | Any password manager installed (KeePass / KeePassXC / 1Password / Bitwarden)? Which one? | | | |

---

## 1. Install and first run

| # | Item | Steps | Expected result | P/F |
|---|---|---|---|---|
| 1.1 | SmartScreen warning (expected — unsigned MSI) | Double-click the `.msi` installer | Windows shows "Windows protected your PC" / SmartScreen. Click **More info**, then **Run anyway**. This is expected and documented — it is not a defect; do not treat it as a Fail. | |
| 1.2 | Installer completes | Follow the MSI wizard to completion | Install finishes without error; a Start-menu entry and (per `menu = true; shortcut = true` in the packaging config) a desktop shortcut appear | |
| 1.3 | Icon quality — eyeball it | Look at the Start-menu tile/shortcut icon after install | The icon is a real, recognizable Banglu icon (not a blank page, not a broken/garbled image, not the generic Java coffee-cup icon). This `.ico` was generated and has never been looked at by a human before you — actually look at it, don't just confirm it's present. | |
| 1.4 | App launches | Launch বাংলু টাইপার from the Start menu | A tray icon appears in the notification area within a few seconds; no crash dialog | |
| 1.5 | Boot sequence — loading state | Right-click (or left-click, per your Windows version) the tray icon immediately after launch | Top menu line reads exactly **"লোড হচ্ছে…"** (disabled/greyed item, not clickable) | |
| 1.6 | Boot sequence — ready state | Wait a few seconds (dictionary load takes seconds, not instant) and reopen the tray menu | Top line now reads exactly **"পূর্ণ অভিধান ✓"** | |
| 1.7 | Typing before boot completes must NOT half-convert | Immediately after launch (before "পূর্ণ অভিধান ✓" appears), open Notepad and type `ami` fast | The letters `ami` appear completely untouched — plain roman text, no partial/garbled Bangla. (The engine is not ready yet, so every key must pass straight through; it must never intercept and half-convert.) | |
| 1.8 | Dictionary load failure path (only if you can force one — e.g. temporarily rename/corrupt the installed `dictionary.sqlite` under the app's install directory, then relaunch; **restore the file afterwards**) | Relaunch after breaking the dictionary file | Tray top line reads exactly **"অভিধান লোড হয়নি — বাংলা টাইপিং বন্ধ"**, and typing anywhere stays plain roman forever (the keyboard hook is never installed on a failed boot — it must NOT silently fall back to a degraded/seed conversion). Restore the real `dictionary.sqlite` and relaunch before continuing. | |
| 1.9 | License notice opens a real document | Tray menu → **"ওপেন সোর্স লাইসেন্স"** | A real, readable document opens (Notepad, browser, or default text viewer) showing actual license text (CMU dict / dataset notices / the bundled font's OFL). It must NOT be a no-op — nothing happening when you click it is a Fail. | |

---

## 2. Core typing — Notepad first, then every other host app

Do the **full block below** in Notepad first (it is the reference host — no
quirks). Then repeat the **starred (\*) rows only** in each of the other apps
listed in the sub-tables afterward, since those are the ones most likely to
differ per host.

### 2a. Notepad — full pass

| # | Item | Steps | Expected result | P/F |
|---|---|---|---|---|
| 2.1\* | Basic word + single space | Type `ami` then press Space | Text shows exactly **আমি** with the cursor right after it — **no visible space glyph yet**. (The space you pressed is held internally, not injected; press Space again per 2.2 to see it resolve into either a plain space or দাঁড়ি.) | |
| 2.2\* | Double space → দাঁড়ি | Immediately press Space again | Text now reads **আমি। ** (দাঁড়ি + one space) — i.e. typing `ami` + Space + Space produces `আমি। ` | |
| 2.3 | Triple space alternates | Press Space a third time | Text becomes **আমি।  ** (a plain space is appended, not a second দাঁড়ি) | |
| 2.4\* | Tight comma | Type `ami` then Space then `,` | Text reads **আমি,** — no space between আমি and the comma | |
| 2.5 | Period maps to দাঁড়ি | Type `ami` then Space then `.` | Text reads **আমি।** (no space before it, `.` is tight-punctuation-mapped to দাঁড়ি) | |
| 2.6\* | Bracket ordering (the historic bug) | Type `ami` then Space then `(` | Text reads **আমি (** — the space you typed is preserved AND the Bangla appears BEFORE the bracket. (This used to fail: unmapped keys reached the app ahead of the pending word, producing `(আমি`.) | |
| 2.7 | Mid-word unmanaged key — hyphen | Type `am` (do NOT finish the word), then press `-` (hyphen), then type `i`, then press Space | Text reads exactly **আম-ই** with the cursor right after ই — no visible space glyph yet (same held-space note as 2.1). Interrupting `am` mid-word with the hyphen must commit **আম** (the engine's real conversion of the buffer `am` at that point) immediately followed by the literal `-` character, THEN start a fresh word with `i` (which converts to **ই**) — never `-আম`, never `আমি-` with the hyphen swallowed or reordered. | |
| 2.8 | Mid-word unmanaged key — apostrophe | Type `do` (do NOT finish the word), then press `'` (apostrophe), then type `n`, then press Space | Text reads exactly **ডঃ'ন** with the cursor right after ন — no visible space glyph yet. Same ordering rule as 2.7: interrupting `do` mid-word with the apostrophe must commit **ডঃ** (the engine's real conversion of the buffer `do`) immediately followed by the literal `'` character, THEN start a fresh word with `n` (which converts to **ন**). | |
| 2.9\* | Escape reaches the app | Type `ami`, press Space (word commits, space pending), then open a dialog in the app (e.g. Notepad's Find dialog, Ctrl+F), then focus back in the document and press **Escape twice** | Each Escape must actually reach the application — e.g. if you press Escape while the Find dialog is open, it closes the dialog. (This was completely dead in exactly this state — word committed, space pending — until a review caught it; test it in exactly that state, not from a fresh idle keyboard.) | |
| 2.10\* | Held-Shift double bracket | Hold physical Shift down and type `((` (i.e. press the `9` key twice while holding Shift) | Both characters must be **(** **(**. (A conditional shift wrapper exists specifically so the second one does not come out as **৯**.) | |
| 2.11\* | Shift+2 for email addresses | Press Shift+2 | Types **@**, not **২** | |
| 2.12\* | Backspace mid-word | Type `amii` (deliberately one extra `i`), then press Backspace once | The forming preview corrects back to just **আমি**'s composition (i.e. editing continues on the still-forming word, not a raw-text delete) — press Space and confirm the committed word is correct | |
| 2.13 | Backspace with nothing forming | With no word forming and no pending space, press Backspace on some existing text | Deletes a character normally, exactly like Backspace always does — reaches the application directly | |
| 2.14\* | Bengali digit toggle ON | Tray menu → confirm **"বাংলা সংখ্যা (০-৯)"** is checked. In the document, press `5` | Types **৫** | |
| 2.15\* | Bengali digit toggle OFF, live | Tray menu → uncheck **"বাংলা সংখ্যা (০-৯)"** (do NOT restart the app). In the document, press `5` again | Types plain **5** — the change takes effect immediately, no restart needed. Re-check the box afterward to restore the default. | |
| 2.16\* | Candidate chip click | Type `kmn` (or any word with multiple ranked candidates) — the preview strip shows candidate chips below the forming word. Click a non-first candidate chip. | That candidate's Bangla text is injected into the document (not the original top-ranked preview) | |
| 2.17\* | Learning persists across a restart | After 2.16, quit বাংলু টাইপার entirely (tray → **"বন্ধ করুন"**), relaunch it, wait for **"পূর্ণ অভিধান ✓"**, then type the same roman word (`kmn`) again in Notepad | The same non-primary candidate you picked before now appears as the top suggestion / is what a plain Space commits (learning is stored in `%USERPROFILE%\.banglu\learned.json`, shared with the desktop editor) | |

### 2b. Repeat the starred (\*) rows in each of these hosts

| App | 2.1 | 2.2 | 2.4 | 2.6 | 2.9 | 2.10 | 2.11 | 2.12 | 2.14 | 2.15 | 2.16 | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| MS Word | | | | | | | | | | | | |
| Excel (type into a cell) | | | | | | | | | | | | |
| Chrome — Gmail compose box | | | | | | | | | | | | |
| Chrome — a Facebook comment box | | | | | | | | | | | | |
| WhatsApp Desktop — a chat compose box | | | | | | | | | | | | |
| File Explorer — rename a file (F2) | | | | | | | | | | | | |

Any single Fail in this sub-table is worth writing up in detail even if
everything else passes — a host-specific regression is exactly what this
matrix exists to catch.

---

## 3. Preview window position and behavior

| # | Item | Steps | Expected result | P/F |
|---|---|---|---|---|
| 3.1 | Follows the caret — Notepad, 100% scaling | With display scaling at its default (100% typically), type a few letters in Notepad | The preview strip appears directly under the text caret, not floating elsewhere on screen | |
| 3.2 | Follows the caret — Word, 100% scaling | Same, in MS Word | Preview strip under the caret | |
| 3.3 | Mouse-cursor fallback — Chrome, 100% scaling | Type a few letters into a Chrome text field (Chrome hides the real Win32 caret) | Preview strip appears near the mouse pointer (not centered on screen, not in a fixed corner) | |
| 3.4 | **150% scaling** — Notepad | Change Display → Scale to **150%**, sign out/in or restart apps if Windows requires it, then repeat 3.1 in Notepad | Preview strip still lands under the caret. **This is a known suspect area** — the preview window is positioned imperatively while Compose also owns window position, and a reviewer flagged this may misbehave at 150%. If the strip lands centered on screen, offset by a fixed amount, or on the wrong monitor, that confirms the suspected bug — record exactly what you see (a screenshot is ideal) rather than just "wrong". | |
| 3.5 | **150% scaling** — Chrome (mouse fallback) | At 150% scaling, repeat 3.3 in Chrome | Preview strip still lands near the mouse pointer | |
| 3.6 | Preview never steals focus | While the preview strip is visible (a word is forming), keep typing without clicking anything | All keystrokes land in the host application's text field — never in the preview strip itself. If focus ever visibly moves to the preview window (e.g. typing seems to stop appearing in the document), that is a Fail. | |
| 3.7 | Restore scaling | Set Display scaling back to your normal default (recorded in 0.2) before continuing | | |

---

## 4. Modes and controls

| # | Item | Steps | Expected result | P/F |
|---|---|---|---|---|
| 4.1 | Ctrl+Space toggles বাংলা ↔ English | In any text field, press **Ctrl+Space** | Tray icon changes to reflect English mode (tray tooltip becomes "বাংলু টাইপার — English"); typing now produces plain roman text with no conversion | |
| 4.2 | English mode passes everything, incl. clipboard | While in English mode, select some text and press **Ctrl+C**, then **Ctrl+V** elsewhere | Copy/paste work exactly as normal — nothing is intercepted | |
| 4.3 | Ctrl+Space toggles back | Press **Ctrl+Space** again | Tray icon and tooltip revert to বাংলা; typing converts again | |
| 4.4 | Clipboard/select-all/Alt-Tab work in বাংলা mode too | In বাংলা mode, press **Ctrl+C**, **Ctrl+V**, **Ctrl+A**, and **Alt+Tab** | All four work exactly as they do with বাংলু টাইপার not running — none of them get intercepted or altered | |
| 4.5 | বন্ধ (OFF) via tray | Tray menu → select the **"বন্ধ"** radio item (not the "বন্ধ করুন" quit item at the bottom — those are different) | Tray tooltip/icon show OFF; typing anywhere is now completely unconverted, plain roman | |
| 4.6 | Ctrl+Space does NOT undo বন্ধ | While mode is বন্ধ, press **Ctrl+Space** in any normal app | Nothing happens — mode stays বন্ধ. This is deliberate: the tray is the ONLY way back from OFF. | |
| 4.7 | Same, inside a passthrough app | Focus a password-manager window if you have one installed (or any app you've added to the passthrough list), set mode to বন্ধ from the tray, then press Ctrl+Space | Still nothing happens — OFF stays OFF regardless of which app has focus | |
| 4.8 | Recovery from বন্ধ | Tray menu → select **"বাংলা"** (or **"English"**) | Mode changes immediately; typing converts again | |
| 4.9 | Tray glyph legible — dark taskbar | With your taskbar in its normal (likely dark) theme, look closely at the tray icon in each of the three states: বাংলা, English, বন্ধ | Each state is legible and visibly different from the others at real tray size (a filled badge with অ / A / a ring, per state) | |
| 4.10 | Tray glyph legible — light taskbar | Switch Windows to light theme (Settings → Personalization → Colors → choose "Light"), or set the taskbar specifically to light if your Windows version allows it | The same three tray states are still legible and distinguishable against a light taskbar background. Switch back to your normal theme afterward. | |
| 4.11 | Tray glyph — "not yet ready" is visually distinct | Quit and relaunch the app; during the "লোড হচ্ছে…" window, glance at the tray icon | It looks visibly dimmer/different from the fully-ready বাংলা icon — "still loading" must never look identical to "typing Bangla right now" | |
| 4.12 | "লগইনে চালু হবে" — tick and reboot | Tray menu → check **"লগইনে চালু হবে"**, then reboot the laptop | After reboot, বাংলু টাইপার starts automatically (check the tray) without you launching it manually | |
| 4.13 | Registry-write failure doesn't lie to you | (Only if you can arrange a failure — e.g. run without permission to write `HKCU\...\Run`, or note if it happens spontaneously) | If the registry write fails, a tray notification titled **"বাংলু টাইপার — সেটিং সংরক্ষণ হয়নি"** appears and the checkbox must NOT show as ticked — the setting must not claim success it didn't achieve | |
| 4.14 | Mode persistence across quit/relaunch — বাংলা | With mode = বাংলা, quit via tray **"বন্ধ করুন"**, relaunch, wait for ready | Mode is restored to বাংলা automatically, no need to reselect it | |
| 4.15 | Mode persistence — English | Set mode to English, quit, relaunch | Mode restored to English | |
| 4.16 | Mode persistence — বন্ধ | Set mode to বন্ধ, quit, relaunch | Mode restored to বন্ধ (typing stays unconverted immediately on relaunch, before you touch the tray) | |
| 4.17 | Manual hook recovery | Tray menu → **"কীবোর্ড আবার চালু করুন"** (this item is only enabled once the dictionary has finished loading) | After clicking it, typing in Notepad still converts correctly — the keyboard keeps working after a manual re-arm | |

---

## 5. Safety and edge behaviour

| # | Item | Steps | Expected result | P/F |
|---|---|---|---|---|
| 5.1 | No self-conversion loop | In Notepad, type a long sentence reasonably fast (10+ words, e.g. "ami tomake kal dekha korte jabo office e onk kaj ache") | Output is sane, finite Bangla — no runaway repeated/garbled text, no visible freeze or explosion of characters. Our own injected Bangla must never be re-converted. | |
| 5.2 | Password manager untouched (if installed) | Focus your password manager's window (from 0.3) and type into a field there | Bangla conversion does NOT happen — the password manager sees your exact keystrokes, since it's on the passthrough list by default | N/A if none installed |
| 5.3 | Password-manager auto-type INTO a browser | Use your password manager's auto-type/autofill feature to fill a login form in Chrome | Record exactly what happens. Expected: auto-typed characters are injected by the password manager tool itself — not tagged as coming from you at the keyboard hook — so they WILL be seen and possibly converted by বাংলু টাইপার in বাংলা mode, since the passthrough list only covers the app that currently has keyboard focus, not the origin of synthetic input. Confirm whether your password/credentials come through garbled in বাংলা mode, and switch to English mode before using autofill if so. | N/A if none installed |
| 5.4 | Elevated (Run as administrator) app | Right-click an app (e.g. Notepad or a script host) → **Run as administrator**, then try typing Bangla into it | Text does not get typed in (Windows blocks synthetic input into elevated windows — UIPI). You should see exactly **one** tray notification, titled **"বাংলু টাইপার — লেখা পাঠানো যায়নি"**, reading: *"প্রশাসক (administrator) হিসেবে চলা অ্যাপে উইন্ডোজ বাইরের কীবোর্ড ঢুকতে দেয় না। ওখানে বাংলা লিখতে বাংলু টাইপারকেও \"Run as administrator\" দিয়ে চালান।"* — and only ONE, not one per keystroke. Type several more words into the same elevated window and confirm no additional notifications appear. | |
| 5.5 | Unmanaged key mid-word commits the word | Start typing a word (e.g. `ka`), then without finishing it press **F5** (or a volume key, or an arrow key) | The forming word commits as-is (whatever was previewed for `ka`) and then F5/volume/arrow does its normal thing. Confirm this is tolerable in practice — i.e. it doesn't feel like data loss, just an early commit — and note if it's surprising. | |
| 5.6 | On-screen/touch keyboard still converts | Open the Windows on-screen keyboard (Win+Ctrl+O, or search "On-Screen Keyboard") and type `ami` using it, in Notepad | Converts to **আমি** exactly like physical typing — the app deliberately does not ignore synthetic keyboard input, only its own tagged injections | |
| 5.7 | 30-minute mixed typing session | Type naturally across several of the apps above for 30 continuous minutes — mix of short and long words, punctuation, mode switches, candidate picks | The keyboard hook must never die. If at any point the tray shows the **"⚠ কীবোর্ড হুক বসেনি"** warning, record: what you were doing right before it appeared, which app had focus, and whether "কীবোর্ড আবার চালু করুন" from the tray restored it. | |
| 5.8 | Idle CPU and RAM | Leave the app idle (not typing) for a minute, then open Task Manager → Details, find the বাংলু টাইপার process | Idle CPU should read approximately **0%**. Record the RAM (Working Set / Memory) figure — no specific pass/fail threshold, just record it for future comparison. | CPU: ____%  RAM: ____MB |

---

## Summary

- Total checklist items: **61** individual pass/fail rows across sections 0–5
  (0: 3, 1: 9, 2a: 17, 3: 7, 4: 17, 5: 8), plus the six-app repeat sub-table
  in 2b, which adds 66 more pass/fail cells (6 apps × 11 starred rows).
- Any Fail should be written up with: exact steps, exact observed output
  (verbatim text/screenshot), which app, and which laptop. Do not summarize a
  Fail as "didn't work."
- **The feature is not done when this checklist is clean on paper — it is
  done when a human ran it, on a real Windows laptop, and it passed.** No
  amount of green CI substitutes for this.
