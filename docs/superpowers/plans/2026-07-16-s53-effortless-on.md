# S53 — Effortless On Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Spec: docs/superpowers/specs/2026-07-16-s53-effortless-on-design.md

**Goal:** One-click enable/disable on every surface (macOS assistant window + menu-bar toggle, Win/Linux tray control, shared getting-started cards) and banglu-web fully on the 3.8.5 shared engine.

**Tech:** Swift/AppKit (TIS APIs), Compose Desktop tray, Kotlin (cards), Next.js (banglu-web).

## Global Constraints
- macOS: NEVER call remove — enable via TISRegisterInputSource+TISEnableInputSource, disable via TISDisableInputSource only. Window appears on plain launch; headless/IMK launches stay silent servers. Gate: `swift run BangluCoreTestRunner` stays green; manual click-through on the dev Mac.
- Desktop: tray toggle drives the existing Hotkey registration live; `:desktop-app:test` stays green.
- banglu-web is a SIBLING repo with its own conventions (Netlify/Vercel deploy — check before pushing); vendored engine = lib/banglu-engine/* + public/banglu-slim.json; /type already uses the shared engine (pattern to copy). Old TS engine to delete = phonetic_engine/ + its importers (homepage demo/converter) after migration.
- All Bangla copy: শুরু করুন cards use "৩ ধাপে শুরু করুন" heading.

## Tasks
1. **Part D (agent, banglu-web):** refresh vendored engine (banglu-engine.js et al from :shared:jsBrowserProductionLibraryDistribution + shared/banglu-slim.json → public/), migrate homepage demo + converter to BangluWebEngine (copy /type's loader pattern), delete phonetic_engine + dead imports, build green, parity spot-check (ssc/callback/kemon), commit (push per that repo's convention — check its CLAUDE.md).
2. **Part A (controller, macos-ime):** `SetupWindow.swift` (AppKit panel: status, enable/disable buttons via new `BangluCore/InputSourceControl.swift` TIS wrapper, two pref checkboxes, globe hint); launch-context branch in main.swift (plain launch → window+accessory activation; IMK launch → server as today); NSStatusItem with enable/disable + open-editor items; runner stays green; manual gate.
3. **Part B (controller or agent, desktop-app):** tray menu gains hotkey on/off toggle (checkmark state) + hint line; Hotkey gains unregister()/re-register; tests green.
4. **Part C (agent):** per-surface "৩ ধাপে শুরু করুন" dismissible cards: editor banner (editor.json seen-flag), extension popup first-open panel (storage flag), Android tutorial top card (SharedPreferences flag), macOS assistant window card (defaults flag; folded into Task 2). Walls per surface.
5. Wrap: reinstall surfaces, CLAUDE.md status, ledger, push, user click-through.
