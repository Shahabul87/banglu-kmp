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
├── WinPrefs.kt           WinPrefsStore — mode/digits/start-on-login/auto-update
│                         prefs on disk.
├── AppVersion.kt         What version this build is (jpackage property, then
│                         the generated resource) and dotted-numeric ordering.
├── EditionPorts.kt       THE EDITION SEAM. UpdateGateway, StartOnLoginControl,
│                         UpdateStatus and the EditionPorts contract that the
│                         two `Edition` objects implement — see "Two editions"
│                         below. Main.kt talks to these, never to the updater.
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

windows-ime/src/msi/kotlin/com/banglu/winime/     ← WEBSITE edition only
├── Edition.kt            hasUpdater = true, Run-key start-on-login.
├── StartupRegistry.kt    The HKCU Run-key toggle + RunKeyStartOnLogin.
└── update/Updater.kt     THE ONLY FILE IN THIS MODULE THAT TOUCHES THE
                          NETWORK. Version comparison, manifest parsing, the
                          host allowlist, the download + SHA-256 check, and
                          the msiexec handoff. It imports no engine, no typing
                          state and no storage — see "Updating" below.

windows-ime/src/store/kotlin/com/banglu/winime/   ← MICROSOFT STORE edition only
└── Edition.kt            hasUpdater = false, no start-on-login control — one
                          disabled tray line pointing at Windows Settings.

windows-ime/msix/         AppxManifest generation inputs and the two PowerShell
                          scripts (pack / sign-for-sideload) that the release
                          and smoke workflows both call.
```

## Two editions, one app

There is one codebase and two builds. `-PbangluStore=true` selects the
Microsoft Store edition; everything else is the default website edition.

| | website (default) | Microsoft Store (`-PbangluStore=true`) |
|---|---|---|
| package | jpackage MSI, downloaded from craftsai.org | MSIX, installed from the Store |
| signing | unsigned (SmartScreen warning) | Microsoft re-signs at ingestion |
| updates | the in-app updater in `update/` | the Store |
| start on login | `HKCU\…\Run`, tray checkbox | manifest `StartupTask`, Windows Settings → Apps → Startup |
| extra source set | `src/msi/kotlin` | `src/store/kotlin` |
| tests | 124 (`src/test` + `src/msiTest`) | 102 (`src/test` + `src/storeTest`) |

**How to tell which build you are running:** the control window's footer ends
with a version line — `সংস্করণ 1.0.1 · ওয়েবসাইট সংস্করণ (website)` or
`সংস্করণ 1.0.1 · Microsoft Store সংস্করণ (store)`. Ask for that line in any
bug report; the two editions genuinely behave differently.

**Why the Store build has no updater.** It is not tidiness. The JDK's web
client opens an internal loopback socket pair in its CONSTRUCTOR, and an MSIX
container refuses it: the app died at start-up with `Unable to establish
loopback connection`, and neither `internetClient` nor
`privateNetworkClientServer` fixed it (`.superpowers/sdd/2026-08-20-windows-ime/msix-spike.md`).
Excising `update/` from the Store source set removes the crash, removes the
only networking the app has, and matches how Store apps are supposed to update.
`verifyStoreEdition` is the gate that keeps it out.

**Why the Store build has no start-on-login toggle.** A Run key names an
absolute path; a packaged app lives under `C:\Program Files\WindowsApps\…`,
where the directory name changes with every version and the user cannot reach
it. MSIX's own mechanism is a `windows.startupTask` manifest extension, which
the package declares with `Enabled="true"` — and the OS owns the switch from
then on (**Settings → Apps → Startup**, or Task Manager's Startup tab).
Toggling it from inside the app needs the WinRT `StartupTask` API, which is not
reachable here without a second native-interop layer outside `hook/` — a repo
isolation law. So the tray shows one disabled line saying where the setting
lives. An absent feature with a signpost beats a switch that lies.

### The isolation laws

There are two, and both are Gradle tasks rather than conventions.

**JNA stays in `hook/`.**

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

**The network stays in `update/`, and typing stays out of it.**

```
./gradlew :windows-ime:verifyUpdaterIsolation
```

fails the build if any compiled file outside `update/` names an HTTP client
(`java.net.http`, `HttpClient`, `URLConnection`, `java.net.Socket`), and
equally fails it if any file inside `update/` names the engine, the composer,
the controller or storage. It walks `src/main/kotlin` **and** the selected
edition source set, so neither edition can smuggle a socket in through its own
tree. A third gate, `verifyStoreEdition`, runs only for `-PbangluStore=true`
and is stricter still: the Store build may not name a network type, a
`CurrentVersion\Run` key or the updater ANYWHERE, because there confinement is
not enough — the code must be absent. The first half means the hook, the controller and
the composer are structurally unable to open a socket; the second means the
one file that *can* reach the network cannot see a single character the user
typed. Those two greps are the mechanism behind the privacy claim below —
both directions are verified to actually fail the build, not merely
registered. It also runs as part of `check`.

## Performance (S187)

The synchronous per-keystroke conversion is sub-millisecond once warm, but a
fresh process pays the sqlite cold cost on its first few hundred keys
(measured p99 20–70 ms). So `Controller.warmUp` runs right after the store
attaches: 220 representative words (the tutorial curriculum plus the cultural
phrases) are converted prefix by prefix on the engine lane, ONE word per queue
turn, so a user's own keystrokes always run between warm-up words. The control
window footer shows the live cost — `টাইপিং: গড় … ms · সর্বোচ্চ … ms (শেষ 200 কি)
· ওয়ার্ম-আপ … শব্দ` — which is the number to ask for in a "feels slow" report.
It measures the worker's work per key (composer + engine + SendInput call);
the host application's own handling of injected keys is outside it, and that
is the larger cost in MS Word: about 40% of keystrokes rewrite the echoed word
(the dictionary's answer changes shape as letters arrive), roughly three
injected events per key. Removing that needs a TSF input method with a
composition string — v2 scope.

`WIN_LATENCY=1 ./gradlew :windows-ime:test --tests '*S187WinLatencyStudy*'`
reproduces the study on the real dictionary (add `-PwinLatencyC1=1` to try a
C1-only JIT; it made no difference).

**When the engine cannot produce a word.** `Composer.liveConversion` never
shows a blank: full pipeline → rule-only transliteration → the raw letters. A
conversion that throws falls back to the rule layer and reports the fault to
the tray once; a dictionary that fails to boot keeps `engineReady` false so
every key passes through untouched and the tray says so; an unknown word gets
the rule transliteration on screen with the raw roman as the last chip (and on
Escape).

## Build and test

```bash
./gradlew :windows-ime:test    # 124 tests: Composer pins, Controller ordering/
                                # swallow rules, AppCompat, WinStorage, WinPrefs,
                                # StartupRegistry OS-guard, the echo-diff and
                                # backspace-safety pins, the updater wall
                                # (version ordering, manifest degradation,
                                # checksum-abort, host allowlist), an engine
                                # smoke test — all driven against the real
                                # repo-root dictionary.sqlite, same wall
                                # discipline as :desktop-app:test. No test in
                                # this module performs a network call.

./gradlew :windows-ime:check   # test + verifyHookIsolation + verifyUpdaterIsolation
                                # + verifyStoreEdition + verifyPackagedDictionary
                                # (the last one only bites once
                                # resources/common/dictionary.sqlite exists —
                                # see Packaging below)

./gradlew :windows-ime:check -PbangluStore=true
                                # 102 tests: the SAME wall against the Store
                                # source set, plus StoreEditionTest (the
                                # updater's classes and the Run-key writer must
                                # not exist) and verifyStoreEdition. Both
                                # editions are gated in CI; neither is optional.

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

1. Bump `bangluTyperVersion` in `windows-ime/build.gradle.kts` — the single
   source of truth for the MSI's `packageVersion`, the version resource the
   running app reads, and the version in the published update manifest.
2. Push a tag matching `windows-v<that same version>` (e.g. `windows-v1.0.1`).
   The workflow's first step fails the run if the tag and
   `bangluTyperVersion` disagree.
3. `.github/workflows/windows-ime-release.yml` runs on `windows-latest`: it
   downloads `dictionary.sqlite` from this repo's `dictionary` release asset,
   verifies its version against `DictionaryVersion.REQUIRED` (the same
   cross-surface version gate every other host enforces), stages it into
   `windows-ime/resources/common/dictionary.sqlite`, runs `:windows-ime:test`,
   then `:windows-ime:packageMsi`.
4. The MSI is uploaded as a workflow artifact and, for a `windows-v*` tag
   push, attached to a GitHub release together with a generated
   `windows-update.json` (see **Updating** below). `workflow_dispatch` still
   produces the artifact and creates no release, so a private test build costs
   nothing public.

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

### Upgrades install in place, and never ask for a reboot

Two lines in the `windows { }` block carry this, and both are load-bearing.

**`upgradeUuid`** is a fixed GUID identifying the *product family*. jpackage
generates a random one per build when none is given, which is what the first
MSIs shipped with — so every build was, to Windows Installer, an unrelated
product. Installing a new one therefore did not replace the old one: both sat
in Add/Remove Programs, the new installer could not overwrite files the
running old app held open, and Windows Installer's standard answer to a locked
file is to schedule the replacement for the next boot and ask the user to
restart. That is where "why do I have to restart the PC after reinstalling?"
came from. With a stable UpgradeCode the installer runs its
FindRelatedProducts/RemoveExistingProducts sequence instead: the old version is
removed and the new one installed as one transaction, in place, with no second
entry and nothing left to schedule. **Never change that GUID** — every already
installed copy would become un-upgradable.

**`perUserInstall`** puts the app under `%LOCALAPPDATA%` instead of Program
Files. An in-place upgrade of a per-machine install needs elevation, so every
update would raise a UAC prompt, and files under Program Files held open by the
running app are exactly what pushes the installer toward reboot-scheduling.

The updater also passes `REBOOT=ReallySuppress` to `msiexec`, which is the belt
to that pair of braces: even if something were still locked, Windows Installer
reports it rather than scheduling a restart and prompting for one.

**One-time cost, stated plainly.** An already-installed 1.0.0 has *no*
UpgradeCode at all, so it is not part of the new product family and 1.0.1 will
not replace it. Uninstall বাংলু টাইপার once from Add/Remove Programs before
installing 1.0.1. From 1.0.1 onward, upgrades are in place and silent. Nothing
in `%USERPROFILE%\.banglu` is touched by any of this — learned words
(`learned.json`), preferences (`winime-prefs.json`) and the passthrough list
(`winime-appcompat.json`) live outside the install root, so uninstalling,
upgrading and reinstalling all preserve them.

**If a user runs the MSI by hand while the app is running**, Windows Installer
detects the files in use and (with the full UI) shows its "the following
applications are using files that need to be updated" page, offering to close
them. Letting it close বাংলু টাইপার is the right answer and the upgrade
completes in place. Declining leaves files locked, and *that* is the one path
that can still end in a restart prompt. The in-app updater avoids the question
entirely by quitting before the installer gets that far.

**Unsigned installer.** v1 ships an unsigned MSI — there is no Authenticode
certificate. This means Windows SmartScreen shows "Windows protected your PC"
on first run; the way through is "More info" → "Run anyway". This is a known,
accepted, and documented limitation for v1 (same posture as macOS IME's
ad-hoc-signed, developer-machine-only v1 distribution), not a bug to chase.
Public, signed distribution is a deliberate later decision, same as the macOS
notarization decision.

## Packaging the Microsoft Store MSIX

The Store package is built by the `store-msix` job in
`.github/workflows/windows-ime-release.yml`, alongside — never instead of —
the MSI. Both run on every `windows-v*` tag and every manual dispatch.

```
:windows-ime:check -PbangluStore=true              # the Store edition's own wall
:windows-ime:createDistributable -PbangluStore=true # the app image
:windows-ime:generateAppxManifest                   # build/msix/AppxManifest.xml
:windows-ime:generateMsixAssets                     # build/msix/Assets/*.png
windows-ime/msix/pack.ps1                           # MakeAppx -> BangluTyper.msix
```

The artifact to download and upload to Partner Center is
**`banglu-typer-store-msix`**; `banglu-typer-store-manifest` carries the same
run's manifest and tiles for review without unzipping 136 MB.

**The manifest is generated, not hand-maintained.** `generateAppxManifest`
parses `windows-ime/store-identity.md` for `Package/Identity/Name`,
`Package/Identity/Publisher` and `Package/Properties/PublisherDisplayName`, and
stamps `bangluTyperVersion` with a fourth component of `0` (the Store reserves
the revision component and rejects a non-zero one). Partner Center rejects an
upload whose identity differs from what it assigned by even a character, so
there is exactly one place those strings live.

**The manifest declares `runFullTrust`**, the restricted capability a Win32
desktop app in the Store uses. It requires a written justification on Partner
Center's Submission options page, reviewed by a human. AutoHotkey's Store
Edition — an Appx whose entire purpose is a global `WH_KEYBOARD_LL` hook — is
the precedent that this is grantable for an app like ours; it is not a
guarantee.

**Visual assets** are generated from the existing `icons/banglu.ico` rather
than drawn fresh: every entry in that file is a PNG, so `generateMsixAssets`
extracts the 256x256 one and downsamples it to `StoreLogo` (50), `Square44x44`
(44) plus its target-size variants (16, 24, 32, 48, 256), `Square71x71` (71),
`Square150x150` (150), `Square310x310` (310), and `Wide310x150` (the mark
centred at tile height on a transparent field, not stretched). Eleven files.

**The Store package is NOT signed by us.** Microsoft re-signs at ingestion and
rejects a submission that arrives already signed. `sign-for-sideload.ps1` signs
a *separate copy* for testing, with a throwaway self-signed certificate whose
subject is read out of the manifest so it matches `Identity/Publisher` exactly
(`Add-AppxPackage` refuses the package otherwise). The file uploaded to Partner
Center stays byte-identical to MakeAppx's output.

**Proof that it starts.** The `msix` job in `windows-ime-smoke.yml` packs the
Store edition, signs a sideload copy, installs it, asserts the installed
package family name is the one Partner Center assigned and that the
`windows.startupTask` survived packaging, then launches the app twice — once
with output captured inside the container, once through
`shell:AppsFolder\…!BangluTyper` the way a user does. A healthy tray app never
exits, so the pass condition is *still running after 90 seconds with nothing on
stderr*. That job exists because the packaging spike watched this exact app die
inside a container; "the MSI starts" proves nothing about the MSIX.

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

Same privacy law as every other Banglu surface (invariant 12): **conversion is
entirely on-device and nothing derived from a keystroke ever leaves the
machine.** There is no telemetry, no analytics, no crash reporting and no
account of any kind.

There is exactly one network feature, and it is described in full below.

## Updating

The app can update itself, so a fix reaches users without them downloading and
reinstalling an MSI by hand.

**What it does, in order.** On startup — if **স্বয়ংক্রিয় আপডেট** is on — and
whenever the user picks tray → **আপডেট দেখুন**, the app fetches a small JSON
manifest. If it names a version newer than this build, the control window shows
one line (`নতুন সংস্করণ 1.0.2 এসেছে — …`) and an **আপডেট করুন** button. Pressing
it downloads the MSI into `%USERPROFILE%\.banglu\updates`, showing percentage
progress, verifies its SHA-256 against the manifest, launches
`msiexec /i <file> /qb REBOOT=ReallySuppress`, and quits immediately — the app
must not be holding its own files open while the installer replaces them.

**jpackage's MSI does not relaunch the app afterwards**, so the app stays closed
when the install finishes. The message says so rather than pretending
otherwise: *"ইনস্টল শেষ হলে Start মেনু থেকে আবার চালু করুন"*. Users who have
**লগইনে চালু হবে** on get it back automatically at their next sign-in.

**Every failure is silent-but-honest.** No network, a 404, a redirect off the
allowlist, a malformed or truncated manifest, a garbage version string, a
checksum mismatch, an installer that will not start — none of them throws, none
of them touches typing, and none of them opens a dialog or a tray balloon. They
produce at most one line in the control window, and *only when the user asked*:
an automatic check that failed says nothing at all, because a fresh install on
a train should not open with an error. A download whose checksum does not match
is deleted, not kept.

**স্বয়ংক্রিয় আপডেট governs the automatic check only.** It defaults ON,
persists through `WinPrefsStore` like every other toggle, and a prefs file
written before the setting existed reads as ON. Turning it off never disables
**আপডেট দেখুন** — it means "stop looking on your own", not "refuse to look when
I ask".

### The exact network surface

| | |
|---|---|
| **Hosts** | `github.com`, and the `*.githubusercontent.com` host GitHub redirects release downloads to. Nothing else. |
| **Method** | `GET`, unauthenticated, of a static file. |
| **Sent** | Nothing. No request body, no cookies, no headers of our own, no query string, no version number, no identifier, no machine or user attribute. |
| **When** | Once at startup (if the toggle is on), and when the user asks. Never on the typing path. |
| **Where** | `update/Updater.kt`, and nowhere else in the module. |

Those claims are enforced rather than asserted. Redirects are **not** followed
by the HTTP client (`Redirect.NEVER`); each hop is resolved by hand and its
host re-checked against the allowlist before another byte is sent, so a
redirect cannot walk the download off GitHub. Every URL the app itself
authors — the manifest URL and the download URL inside the manifest — must pass
a check that rejects a query string, a fragment and user-info outright, which
is what makes "we send nothing about the user" something you can verify by
reading `Net.safeUri` rather than something you have to believe. (A hop GitHub
redirects us to *may* carry a query: its CDN URLs are signed. We never add one.)
`verifyUpdaterIsolation` keeps HTTP out of every other file and keeps the engine,
the composer, the controller and storage out of this one.

### Publishing an update

`windows-update.json` is generated by the release workflow from the MSI it just
built — `{"version", "url", "sha256", "notes"}` — so the checksum and the file
can never describe different builds. It is attached to the same GitHub release
as the MSI *and* uploaded to a permanent pointer release tagged
`windows-update`, from the same bytes in the same run. The app fetches the
pointer, because this repository's `latest` release belongs to বাংলু এডিটর
(`desktop-v*`) and would hand the Windows app the wrong manifest.

The `notes` line is the first line of `windows-ime/update-notes.txt` — edit it
in the same commit that bumps `bangluTyperVersion`, and it becomes the sentence
users read beside the update offer.

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
one — the three settings that exist (Bengali digits, start-on-login,
automatic updates) are tray `CheckboxItem`s, and the passthrough app list has
no UI at all: it is edited
by hand, and it takes a restart, as documented under **Passthrough apps**
above. `AppCompat.add`/`remove` exist and are tested, but nothing in the
running app calls them yet — a settings window is what would. That window is
tracked as future work: the first user who actually needs the escape hatch
is the signal to build it, and until then the documented file edit is the
supported route.
