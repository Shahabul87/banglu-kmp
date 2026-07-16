# S53 — "Effortless On": frictionless enable/disable across every surface + web parity

**Date:** 2026-07-16
**Status:** Approved by user (full scope, dev-machine grade, unsigned)
**Product law (user, 2026-07-16):** people abandon software at the
install/enable step, not the features step. Every surface must be trivially
easy to turn ON, turn OFF, show in the menu bar / bottom-right, and switch —
so no one feels bored or blocked before they ever type.

## Motivating failure

The user removed বাংলু from macOS Input Sources; re-adding failed all session
(TIS cache poisons on *remove*; `TISRegisterInputSource` returns 0 yet the
source stays undiscoverable until re-login — confirmed empirically). Raw
`make install` + System Settings hunting + logout roulette is not shippable.

## Part A — macOS: one-click assistant (never "remove")

- Double-clicking `Banglu.app` opens a small **setup window** (today it's a
  silent background server). Contents:
  - Status line: "বাংলু চালু আছে ✓ / বন্ধ আছে" (live TIS enabled-state).
  - **এক ক্লিকে চালু করুন** button → `TISRegisterInputSource` +
    `TISEnableInputSource` programmatically (no System Settings, no logout).
  - **বন্ধ করুন** → `TISDisableInputSource` (graceful; instantly re-onable;
    NEVER the Settings "remove" that poisons the cache — the window's copy
    explicitly steers users away from removing).
  - Two checkboxes wired to the real prefs: "মেনু বারে ইনপুট মেনু দেখান"
    (`com.apple.TextInputMenu visible`), "সব অ্যাপে একই ভাষা"
    (per-context input off).
  - First-launch card: "🌐 Globe বা Ctrl+Space চেপে ভাষা বদলান".
- The IME server still registers on launch when invoked headlessly (input
  method mode) — the window only appears on a normal double-click / when no
  input session is active. Same executable, a launch-context branch.
- Menu-bar `NSStatusItem` (বা) with an in-app enable/disable toggle mirroring
  the window, so on/off is reachable without reopening the app.
- Ships in the same ad-hoc bundle; on the user's Mac the one-click enable
  works today. (Public: needs Developer ID — separate later decision.)

## Part B — Windows/Linux desktop: tray as control center

The editor already lives in the system tray (bottom-right on Windows). Extend
its menu so on/off is always one right-click away:
- বাংলু চালু/বন্ধ toggle for the ⌘⇧B / Ctrl+Shift+B global mini-converter
  (registered/unregistered live via the existing Hotkey object).
- "উইন্ডোজে যেকোনো অ্যাপে টাইপ করুন — Ctrl+Shift+B" hint line.
- Existing items (open editor, mini converter, quit) stay.
- A checkmark reflecting hotkey on/off state.

## Part C — shared "Getting Started" pattern

One dismissible **"৩ ধাপে শুরু করুন"** first-run card, same layout/copy
skeleton on every surface, each filled with that platform's exact enable
steps + a live `kemon → কেমন` mini-demo:
- Android tutorial (already has steps — add the 3-step card at top).
- Desktop editor (a one-time banner above the page).
- Extension popup (a first-open panel).
- macOS assistant window (built into Part A).
Persisted "seen" flag per surface (SharedPreferences / editor.json /
extension storage / defaults). YAGNI: no shared component library — a tiny
per-surface card matching each product's existing style.

## Part D — web engine parity

banglu-web's `/type` page already runs the real shared JS engine (ported
earlier). Finish the job:
- Refresh banglu-web's vendored engine bundle + slim JSON to db 3.8.5
  (acronyms + English tail reach the website).
- Migrate the homepage live-demo and the main converter surface onto the same
  real engine `/type` uses (the shared `BangluWebEngine`), deleting the dead
  old-TS phonetic engine (`phonetic_engine/`, the pre-shared converter).
- Parity check: ssc→এসএসসি, callback→কলব্যাক, kemon→কেমন live on the site.

## Non-goals

Apple Developer ID / notarization (public macOS signing — later); Windows
code-signing; a cross-product shared UI library (per-surface cards only);
Play/store uploads (user-side); iOS.

## Verification

- Part A: on the dev Mac, click enable → বাংলু is TIS-enabled without System
  Settings/logout; click disable → gone; re-enable → back (no cache trap).
  Menu-bar toggle mirrors. XCTest-runner unit coverage for the TIS wrapper's
  state logic where headless-testable; manual gate for the actual switch.
- Part B: tray toggle turns the global hotkey off/on live; desktop test wall
  stays green.
- Part C: each surface shows the card once, dismiss persists.
- Part D: banglu-web build green; live `/type` + homepage demo convert
  ssc/callback/kemon correctly on the 3.8.5 engine; old TS engine deleted,
  no references remain.
