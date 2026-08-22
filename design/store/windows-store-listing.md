# Microsoft Store listing — বাংলু টাইপার / Banglu Typer

Paste-ready copy for Partner Center. Product: Store ID `9NVJGRJDRJGK`.

---

## Product name

**Banglu Typer** (English) — plus **বাংলু টাইপার** reserved as a second name so
Bengali-locale Windows shows it in Bengali.

> Deliberately NOT "Banglu Typer — Bangla Keyboard". Store policy 10.1.1: a
> product name "must not contain marketing or descriptive text, including
> extraneous use of keywords". The discoverability that suffix was meant to buy
> is bought legitimately by the search terms below, which is what that field
> exists for (policy 10.1.3, max seven).

## Search terms (7 max, policy 10.1.3)

```
bangla keyboard
bengali keyboard
bangla typing
phonetic bangla
unicode bangla
বাংলা কীবোর্ড
বাংলা লেখা
```

> No competitor names (Avro, Bijoy, Ridmik). Policy 10.1.3 forbids using other
> products' titles as search terms, and it would be a rejection worth nothing.

## Short description (≤200 characters)

> Type Bangla in any Windows app using English letters. Works in Word, Chrome,
> WhatsApp and everywhere else — completely offline.

Bengali:

> ইংরেজি অক্ষরে টাইপ করুন, বাংলা হয়ে যাবে — Word, Chrome, WhatsApp সহ যেকোনো
> অ্যাপে। সম্পূর্ণ অফলাইন।

## Description

```
Type Bangla the way you already think — in English letters — and watch it
become correct Bangla as you type. Not in one special editor, but everywhere:
Microsoft Word, Chrome, Facebook, WhatsApp Desktop, Excel, even the file
rename box.

Write "ami banglay likhi" and get "আমি বাংলায় লিখি".

WHAT MAKES IT DIFFERENT

• Works everywhere. Banglu Typer sits quietly in your tray and converts as you
  type, in whatever application has your cursor. There is nothing to copy and
  paste.

• Understands how people actually write. Beyond the textbook spellings, it
  knows the chat register Bengalis really type — kmon becomes কেমন, korsi
  becomes করছি, issa becomes ইচ্ছা.

• Instant. Conversion happens in microseconds; suggestions appear a moment
  later without ever slowing your typing.

• One switch. বাংলা, English, or off — from the window or with Ctrl+Space.
  Turn it off and your keyboard is exactly as it was.

• Completely offline. The dictionary lives on your computer. Nothing you type
  is sent anywhere, stored anywhere, or seen by anyone — there is no account,
  no telemetry, and the app has no way to reach the internet.

• It learns your words. When you pick a different spelling, it remembers — and
  shares that memory with the Banglu desktop editor if you use it too.

HOW IT WORKS

Open the app, leave it running, and type. The window shows whether Bangla is
on, and closing it keeps the keyboard working quietly in the background. Press
space twice for a দাঁড়ি, type numbers to get ০-৯, and click any suggestion to
choose a different spelling.

Banglu also makes a Bangla keyboard for Android, a desktop editor, and a
browser extension — all sharing the same engine, so your Bangla is the same
everywhere.
```

Bengali description: same structure, translated — write it after the English
is approved so the two cannot drift.

## Notes for certification — IMPORTANT

Paste this verbatim into the "Notes for certification" field. A reviewer who
sees a keyboard hook and is told nothing will assume the worst; telling them
first turns a rejection into a routine approval.

```
Banglu Typer is a Bangla input method (a keyboard utility, like Microsoft's
own IMEs or AutoHotkey Store Edition).

To convert roman letters into Bangla in whatever application the user is
typing in, it installs a low-level keyboard hook (SetWindowsHookEx,
WH_KEYBOARD_LL) and writes the converted text back with SendInput. That is
the standard mechanism for this category of app on Windows and is the same
approach used by existing Bangla input tools.

Please note:

1. It is entirely offline. The package contains no network client of any kind
   and makes no network requests — no telemetry, no accounts, no update
   checks. Keystrokes never leave the machine and are never written to disk
   except for words the user explicitly chooses from the suggestion list,
   which are saved locally so the app learns their preferences.

2. It is user-controlled and clearly visible. The app opens a window showing
   whether Bangla typing is on, with an obvious on/off switch, plus a tray
   icon. Ctrl+Space toggles it. Turning it off restores the keyboard exactly.

3. Password managers are excluded by default, and Windows' secure desktop
   (UAC prompts, the login screen) is never hooked.

4. The full source is public at https://github.com/Shahabul87/banglu-kmp
   (module: windows-ime), including the keyboard hook implementation.

Testing: install and run the app, open any text field (Notepad is fine), and
type "ami" followed by a space. It should produce "আমি". Press Ctrl+Space to
switch to English mode and confirm typing returns to normal.
```

## Privacy policy

Required (policy 10.5.1 — Win32 products must always have one). Use the
existing Banglu privacy policy URL, with a Windows section added covering:
what the app hooks, that keystrokes are never transmitted or stored, what
`%USERPROFILE%\.banglu\learned.json` contains, and how to delete it.

## Category

Productivity.

## Age rating

The IARC questionnaire's honest answers: no violence, no user-generated
content, no data collection, no advertising, no purchases, no unrestricted
web access → rated for everyone.
