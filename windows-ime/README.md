# বাংলু টাইপার — Windows IME

A system-wide Bangla typing tool for Windows. Install it once, and it converts
roman keystrokes to Bangla in whatever application currently has focus — MS
Word, browsers, WhatsApp Desktop, the File Explorer rename box, anything —
the way Bijoy/Avro users expect. Powered by the same shared Banglu engine as
the Android keyboard, the desktop editor, and the macOS input method: no
conversion rule is re-implemented here (repo invariant 9).

v1 is a single Kotlin/Compose Desktop tray application. There is no Windows
Text Services Framework (TSF) input method yet — that is a deliberate v2
scope decision (see the design spec). This app works by installing a
low-level keyboard hook, intercepting keystrokes itself, and injecting the
converted Bangla back into the focused window.

## Module map

```
windows-ime/src/main/kotlin/com/banglu/winime/
├── Main.kt              Compose Desktop entry point: boots the engine,
│                         constructs Controller with its Win32 ports, starts
│                         the hook, renders the tray menu and the preview
│                         window. No typing behaviour lives here.
├── KeyPorts.kt           The OS-free contracts between the hook layer and
│                         everything else: RawKey, Mode, KeySource, HookSink,
│                         TextInjector, ControllerListener. Nothing here may
│                         know a VK code, an HWND, or a JNA type.
├── Controller.kt         The mode machine: hook thread → single worker
│                         thread → Composer/engine/injector. Owns the
│                         swallow rules (what gets claimed vs. passed
│                         through), the FIFO ordering guarantee (a
│                         committed word always reaches the app before a
│                         forwarded key that followed it), and the ECHO
│                         LEDGER — the text it has physically typed into
│                         the focused window for the word being formed,
│                         and the only text it may ever backspace.
├── DebounceScheduler.kt  The 45 ms idle timer behind the suggestion
│                         query. Its task only ENQUEUES onto the worker;
│                         the engine has exactly one lane.
├── composer/Composer.kt  The pure-Kotlin typing state machine — a
│                         line-faithful port of macos-ime's Composer.swift,
│                         re-expressed for a hook app (no marked text; a
│                         "let this key through" decision becomes a
│                         ForwardKey action the controller re-injects).
│                         Immediate-space দাঁড়ি model (see below), tight
│                         punctuation, ০-৯ digits, WYSIWYG commit. Zero
│                         Win32/JNA imports. Engine work is split by measured
│                         cost: convert is synchronous per keystroke,
│                         refineCandidates (~2.3 ms) is not.
├── AppCompat.kt          Per-exe passthrough table. Password managers
│                         (KeePass, KeePassXC, 1Password, Bitwarden) are
│                         passthrough by default; overrides persist to
│                         %USERPROFILE%\.banglu\winime-appcompat.json.
├── WinStorage.kt         PlatformStorage backing learned.json — a direct
│                         port of desktop-app's FileStorage.
├── WinPrefs.kt           WinPrefsStore (mode/digits/start-on-login prefs)
│                         and StartupRegistry (the HKCU Run-key toggle).
├── ui/PreviewWindow.kt   The caret-anchored, non-activating suggestion
│                         strip: up to 6 candidate chips, the last of which
│                         is always the raw roman escape hatch. It does NOT
│                         show the forming word — that is typed straight
│                         into the user's document (see "Live echo" below).
│                         The chip count is `Composer.MAX_CANDIDATES`, the
│                         same number the digit keys 1-6 pick from: a strip
│                         that showed fewer would hide a pickable candidate.
└── hook/                 THE ONLY PACKAGE THAT MAY IMPORT com.sun.jna.
    ├── LowLevelHook.kt       WH_KEYBOARD_LL on a dedicated message-pump
    │                         thread, plus a foreground-window watcher and
    │                         a watchdog that re-arms a hook Windows
    │                         silently dropped.
    ├── SendInputInjector.kt  SendInput(KEYEVENTF_UNICODE) — tags every
    │                         event it emits so the hook can tell its own
    │                         injected Bangla apart from what the user typed.
    ├── CaretLocator.kt       GetGUIThreadInfo + ClientToScreen for the
    │                         preview window's anchor point.
    └── ForegroundApp.kt      Resolves and caches the focused window's exe
                              name, for the passthrough table.
```

### The isolation law

`hook/` is the only package in this module allowed to import `com.sun.jna.*`.
Everything else — the composer, the controller, the tray/preview UI, storage,
prefs — is Win32-free and runs (and is tested) on a plain JVM, including on
this repo's Mac development machine. That boundary is what let the entire
typing pipeline be built and pin-tested without ever touching a Windows
machine, and it is what would let `hook/` be swapped for a native shell
later (the spec's Approach 3 escape hatch) without touching anything else.

It is enforced by a Gradle task, not just convention:

```
./gradlew :windows-ime:verifyHookIsolation
```

which walks every `.kt` file under `src/main/kotlin` outside `hook/` and
fails the build if any of them contains `import com.sun.jna`. It runs as
part of `check`, so it also runs in CI.

## Build and test

```bash
./gradlew :windows-ime:test    # 83 tests: Composer pins, Controller ordering/
                                # swallow rules, AppCompat, WinStorage, WinPrefs,
                                # StartupRegistry OS-guard, the echo-diff and
                                # backspace-safety pins, an engine smoke test —
                                # all driven against the real repo-root
                                # dictionary.sqlite, same wall discipline as
                                # :desktop-app:test.

./gradlew :windows-ime:check   # test + verifyHookIsolation + verifyPackagedDictionary
                                # (the last one only bites once resources/common/
                                # dictionary.sqlite exists — see Packaging below)

./gradlew :windows-ime:build   # compiles clean on the Mac dev machine. This is
                                # NOT a Windows runtime check — see below.
```

**What these gates prove, and what they do not.** Every automated test above
runs on pure-Kotlin logic (`Composer`, `Controller`, `AppCompat`, `WinStorage`,
`WinPrefs`) against the real dictionary, so conversion correctness, ordering,
and the swallow rules are genuinely proven. Nothing under `hook/` has an
automated test — there is no Windows runtime available to this repo's dev
machine, and a Win32 keyboard hook cannot be meaningfully faked. Compiling
clean and passing `:windows-ime:test` means the app is *ready* to try on
Windows; it does not mean the app *works* on Windows. That gate is
[`docs/windows-ime-laptop-checklist.md`](../docs/windows-ime-laptop-checklist.md),
run by hand on a real laptop — see below.

## Packaging a real installer

Building the actual MSI needs a Windows runner (jpackage cannot cross-compile
from macOS) and the full 143MB `dictionary.sqlite`, so it happens in CI, not
locally:

1. Push a tag matching `windows-v*` (e.g. `windows-v1.0.0`).
2. `.github/workflows/windows-ime-release.yml` runs on `windows-latest`: it
   downloads `dictionary.sqlite` from this repo's `dictionary` release asset,
   verifies its version against `DictionaryVersion.REQUIRED` (the same
   cross-surface version gate every other host enforces), stages it into
   `windows-ime/resources/common/dictionary.sqlite`, runs `:windows-ime:test`,
   then `:windows-ime:packageMsi`.
3. The MSI is uploaded as a workflow artifact and, for a `windows-v*` tag
   push, attached to a GitHub release.

The packaging config lives in `windows-ime/build.gradle.kts`
(`compose.desktop.application.nativeDistributions`): `TargetFormat.Msi` only,
package name `BangluTyper`, the icon at `windows-ime/icons/banglu.ico`, and
the extra jpackage modules (`java.sql`, `java.instrument`, `java.management`,
`jdk.unsupported`) that a minimal jpackage runtime omits by default but the
JDBC dictionary store needs. `verifyPackagedDictionary` refuses to package a
stale dictionary — it reads the packaged sqlite file's own version metadata
and compares it against `DictionaryVersion.REQUIRED`, failing the build
rather than shipping an installer that silently degrades to seed-only
conversion at runtime.

**Unsigned installer.** v1 ships an unsigned MSI — there is no Authenticode
certificate. This means Windows SmartScreen shows "Windows protected your PC"
on first run; the way through is "More info" → "Run anyway". This is a known,
accepted, and documented limitation for v1 (same posture as macOS IME's
ad-hoc-signed, developer-machine-only v1 distribution), not a bug to chase.
Public, signed distribution is a deliberate later decision, same as the macOS
notarization decision.

## Known limitation: elevated applications

Windows blocks synthetic keyboard input from a normal-integrity process into
a window running elevated (User Interface Privilege Isolation — UIPI). This
means বাংলু টাইপার cannot type into an application you launched with "Run as
administrator" unless বাংলু টাইপার itself is also run elevated. There is no
v1 auto-elevation; the app instead detects the `SendInput` failure and shows
one tray notification per offending application (deduplicated, so a blocked
elevated window does not spam one warning per keystroke), pointing the user
at running বাংলু টাইপার as administrator too.

## When Bangla conversion silently stops

Windows can unregister a low-level keyboard hook it judged slow, without an
error, an event, or any way to ask about it afterwards. When that happens we
still hold the hook handle, so `LowLevelHook.isInstalled` still answers true,
the tray does **not** show **"⚠ কীবোর্ড হুক বসেনি"**, and the watchdog does
not re-arm (re-arming a hook that might still be healthy would eat
keystrokes). The tray warning only covers the case where Windows told us the
install failed.

So: **if typing stops converting and there is no tray warning, the first
thing to do is tray menu → "কীবোর্ড কাজ করছে না? আবার চালু করুন"** — that
re-installs the hook from scratch. If that fixes it, the hook had been
dropped; please record what you were doing just before it happened (which
app, whether the machine had just resumed/locked, whether anything was
loading heavily), because that context is the only lead we will ever get
about which callbacks Windows considered slow.

## Live echo: the word appears in the document as it is typed

Bangla is typed straight into whatever window has focus, letter by letter, the
way Avro does it — not held in a popup until space. The controller keeps a
ledger of what it has injected for the word currently forming and reconciles it
against each new conversion with a common-prefix diff: send
`(echoed.length - commonPrefix)` backspaces, then type the rest. `ami` costs
three inserts and no deletions (আ, ম, ি — each conversion extends the last);
`kmn` costs ক, িমি, then three backspaces and েমন, because কেমন shares only its
first character with কিমি.

**The safety rule.** Backspaces are only ever sent while our own caret is
demonstrably still the focused one. Five paths end a word WITHOUT that
guarantee — a foreground change, a mode switch (the user reached our tray or
window to make it), an unmanaged key such as an arrow that moves the caret,
fault recovery, and shutdown — and every one of them clears the ledger without
injecting anything. A backspace on any of those paths would delete text the
user typed themselves. `ControllerTest.everyEchoEndingPathIsBackspaceFree`
names the complete list in one place; treat it as the pin that guards the
user's documents.

Because the conversion is computed synchronously, what is on screen is already
the final answer, so committing a word (Enter, Tab) normally injects nothing at
all — the WYSIWYG contract holds by construction rather than by agreement
between two code paths.

## Space and দাঁড়ি: immediate, then retroactively corrected

Pressing space **always writes a space, immediately and visibly**. Pressing it
a second time takes that space back out and writes `। ` in its place. That is
what Avro does and what a Bangla typist expects.

| state when Space is pressed  | what the user sees                       |
|------------------------------|------------------------------------------|
| a word is forming            | the word is sealed and a space appears   |
| the space we just wrote      | it is deleted and `। ` appears in its place |
| anything else                | a plain space (a third space is just a space) |

Tight punctuation (`,` `।` `?` `!` — and `.`, which maps to `।` first) does the
same thing: `আমি ` plus `,` deletes the space and gives `আমি,`. Enter, Tab,
Escape, an arrow key, a backspace and a focus change all leave an already-typed
space exactly as it is.

This module inherited the **pending-space** model from the macOS input method,
where IMK gives no way back into committed text so the space had to be held
until the next keystroke disambiguated it. That was wrong here — this app
injects backspaces already — and it shipped as a defect: the user pressed
space, saw nothing happen, pressed it again, and got a দাঁড়ি they never asked
for. The deferred model and its pins are gone.

**The one deletion that reaches outside the echo ledger** is that retro-দাঁড়ি
(and the space a tight mark swallows). It is armed only while the space we
wrote is still the last character in the document, and every key that is not
the one consuming it disarms it — which is also why an armed space keeps the
composer "active" in the hook's mirror: it is what routes an arrow key through
the worker instead of letting it move the caret behind our back. **Residual
risk, stated rather than hidden:** a mouse click that moves the caret *within
the same window* is invisible to a keyboard hook, so a user who types `word `,
clicks elsewhere, and then presses space loses one character at the new caret.
Avro has the identical exposure; closing it needs a low-level mouse hook, which
is a deliberate v2 decision.

## Typing latency: what runs per keystroke

Measured against the real dictionary, warmed, on the prefix workload a real
keystroke produces:

| call                     | per call | where it runs                  |
|--------------------------|----------|--------------------------------|
| `convertWord`            | ~0.74 ms | synchronously, every keystroke |
| `getSuggestions(raw, 3)` | ~2.25 ms | debounced, 45 ms idle          |
| `getSuggestions(raw, 5)` | ~2.37 ms | debounced, 45 ms idle          |
| `getSuggestions(raw, 6)` | ~2.65 ms | debounced, 45 ms idle          |

(Warmed, over a 22-prefix keystroke workload on the real dictionary. The
candidate *count* is not the lever — the cost is the lookup, not the list — so
the strip still shows six chips.)

The suggestion query is the expensive one and was the whole of the reported
lag; it is the only thing on a timer. Conversion is cheap enough to stay
synchronous, and keeping it there is what makes the live echo final rather than
an approximation that gets rewritten under the user.

**The strip is not blanked between keystrokes.** It used to be — the candidate
list was dropped on every letter and refilled only after a 120 ms pause, which
is longer than the 40-90 ms gap between two letters, so a user typing normally
saw no suggestions at all ("suggestion is gone"). Now a keystroke leaves the
list alone and the debounce replaces it, so the popup is populated for the whole
time a word is being typed and empty the moment it is committed. The list
records which buffer it was ranked for: a pick from a momentarily stale strip
still commits the chip the user clicked (WYSIWYG), but never teaches the engine
a mapping they did not make.

`ComposerEngine.instant` (the rule-only layer) is the degraded path: it needs no
dictionary, no SQLite and no learned data, so when the full pipeline throws the
composer still produces the user's word instead of losing it — and reports the
fault through the tray rather than degrading in silence.

## Passthrough apps (`winime-appcompat.json`)

`AppCompat` keeps a per-exe list of applications that must never have their
keystrokes intercepted. Password managers (`keepass.exe`, `keepassxc.exe`,
`1password.exe`, `bitwarden.exe`) are on it by default. v1 has no UI for this
list, so adding an application to it is a manual edit of
`%USERPROFILE%\.banglu\winime-appcompat.json`, which is a JSON array of
`{exe, passthrough}` rows:

```json
[
  { "exe": "electron-app.exe", "passthrough": true },
  { "exe": "keepass.exe", "passthrough": false }
]
```

- `"passthrough": true` adds an application to the list (this is what you
  want for a misbehaving Electron/game/remote-desktop host).
- `"passthrough": false` removes one of the built-in password managers — a
  row is only needed to *deviate* from the built-in defaults, so the file is
  normally short or absent.
- `exe` is matched case-insensitively against the focused window's executable
  name only — not a path, not a window title.

**A change requires restarting বাংলু টাইপার.** The effective set is read once
into an immutable snapshot when the app starts (the keyboard-hook thread
consults it on every keystroke and must never touch the disk or a lock), and
there is no reload path in v1. Edit the file, quit via tray → **"বন্ধ করুন"**,
and relaunch.

## Learning data and privacy

Learned word picks — an explicit non-primary candidate choice, never the
engine's own top-ranked commit (the S26 law) — are written to
`%USERPROFILE%\.banglu\learned.json`, in the same `{p,b,f,t}` row shape every
other Banglu surface uses. **This file is the same learning brain the desktop
editor (বাংলু এডিটর) reads and writes** — picking a word in one app teaches
the other. Writes go through the same tmp-file-plus-atomic-`Files.move`
pattern as `WinStorage.kt`'s desktop counterpart, guarded by the
`isPlausibleDynamicMapping` anti-poisoning check in the shared engine (never
bypassed, per repo invariant 11).

Same privacy law as every other Banglu surface (invariant 12): this module
has no HTTP client, no telemetry dependency, and no network capability of
any kind. Keystrokes are converted entirely on-device and never leave the
process.

## Further reading

- **Design spec:** [`docs/superpowers/specs/2026-08-20-windows-ime-design.md`](../docs/superpowers/specs/2026-08-20-windows-ime-design.md)
  — architecture, the typing pipeline, and the risk register this module was
  built against.
- **Implementation plan:** [`docs/superpowers/plans/2026-08-20-windows-ime.md`](../docs/superpowers/plans/2026-08-20-windows-ime.md)
  — the task-by-task build log, including the exact reasoning behind each
  design choice (e.g. why the swallow mirror is optimistic, why the preview
  window is created once and hidden rather than composed conditionally).
- **Laptop verification checklist:** [`docs/windows-ime-laptop-checklist.md`](../docs/windows-ime-laptop-checklist.md)
  — the manual gate that actually proves this app works on Windows. Nothing
  under `hook/` is considered verified until this has been run, by hand, on
  a real laptop.

## Getting the control window back

Hiding the control window does not stop typing — the keyboard is the product
and the window is only its control panel. Three routes bring it back, and all
three work whether it is hidden, minimised, or merely buried behind Word:

1. **Click the tray icon.** This is the primary route and the one Windows users
   try first (`Tray(onAction = …)` in `Main.kt`).
2. Tray menu → **"বাংলু উইন্ডো দেখান"**.
3. It also comes to the FRONT when it is already open: the window keys its
   `toFront()`/`requestFocus()` on a show-request counter, not on visibility, so
   a show that finds it already visible is never a silent no-op.

The first time the window is hidden in a session, a tray notification says the
app is still running and how to get back. Once per session, not per hide.

Only **"বন্ধ করুন"** quits: it shuts the controller's worker down, unhooks, and
exits. The window's close box hides.

## A note on v1 scope: no settings window

The design spec sketches a dedicated `ui/SettingsWindow.kt`. v1 does not have
one — the two settings that exist (Bengali digits, start-on-login) are tray
`CheckboxItem`s, and the passthrough app list has no UI at all: it is edited
by hand, and it takes a restart, as documented under **Passthrough apps**
above. `AppCompat.add`/`remove` exist and are tested, but nothing in the
running app calls them yet — a settings window is what would. That window is
tracked as future work: the first user who actually needs the escape hatch
is the signal to build it, and until then the documented file edit is the
supported route.
