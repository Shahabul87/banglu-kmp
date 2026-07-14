# S47 Scope — Banglu Browser Extension (Chrome first, Firefox after)

## Product statement
Type Banglish in any text box on the web and get Bangla inline — the same
engine as the Android keyboard and /type, with the suggestion strip. The
privacy story is the moat: the manifest requests NO network permission at
all; everything ships inside the package and runs locally.

## Why now
- Google Input Tools (the incumbent) is effectively abandoned
- Avro-style extensions are aging; none carry a chat-register engine
- Engine + slim dictionary already exist as build artifacts (S45/S46):
  `shared/build/dist/js/productionLibrary/` + `banglu-slim.json` (1.8MB gz)

## Architecture (MV3)

```
┌─ service worker (background) ─────────────────────────────┐
│ hosts BangluWebEngine + slim dictionary (packaged asset)  │
│ cold start: fetch(runtime.getURL(slim)) + parse ~250ms    │
│ API via runtime messaging:                                │
│   {type:"convert", word} -> {primary}                     │
│   {type:"suggest", word, n} -> {chips[]}                  │
└───────────────────────────────────────────────────────────┘
        ▲ sendMessage (1-5ms round trip)
┌─ content script (all_urls, opt-out per site) ─────────────┐
│ caret/word tracker on focused editable                    │
│ word boundary (space/punct) -> replace roman with Bangla  │
│ floating suggestion strip anchored to caret               │
│ toggle: Alt+B global, per-site persistent (storage.sync)  │
│ editables v1: <input type=text|search>, <textarea>        │
│ editables v2: contenteditable (Gmail/FB/WhatsApp Web are  │
│   contenteditable — v2 is NOT optional for real usage)    │
│ React-controlled inputs: native value setter + dispatched │
│   InputEvent (the standard workaround)                    │
└───────────────────────────────────────────────────────────┘
┌─ popup ───────────────┐  ┌─ options page ────────────────┐
│ mini /type converter  │  │ default on/off, site list,    │
│ + global & site toggle│  │ shortcut, danda/digit prefs   │
└───────────────────────┘  └───────────────────────────────┘
```

Decisions:
- Engine lives in the SERVICE WORKER, not per-tab content scripts (one
  dictionary in memory, not N; content scripts stay tiny). MV3 SW sleep is
  fine — wake+parse ~250ms once, then message latency is negligible.
- Dictionary PACKAGED in the extension (no runtime downloads — required for
  the "no network" manifest and instant CWS review story).
- No keystroke logging of any kind; no analytics. Same posture as the app.

## Out of scope v1
- Google Docs canvas rendering (nobody's extension can; popup converter is
  the documented fallback), password/credit-card fields (hard-blocked),
  cross-origin iframes beyond best-effort, voice.

## Phases
- **P0 (2-3d): skeleton + popup converter.** manifest.json, build script
  copying engine dist + slim json from banglu-kmp, SW engine host with
  message API, popup = /type in miniature. Testable immediately.
- **P1 (2-3d): inline typing in input/textarea.** word tracker, boundary
  commit, strip UI, Alt+B + per-site toggle. Verify: Twitter/X compose,
  simple search boxes, GitHub comment box.
- **P2 (3-4d): contenteditable + framework events.** Gmail compose,
  Facebook posts/comments, WhatsApp Web, Messenger. This is the hard 40%
  — Range/Selection manipulation + synthetic event dispatch.
- **P3 (1-2d): ship.** Options page, icons (reuse app icon set), CWS
  listing (privacy-first copy from STORE-LISTING.md), Firefox port
  (browser.* shims + manifest v3 gecko keys), store submission.

## Risks / honesty
- CWS review: days-to-weeks; "broad host permissions" prompts extra review
  — mitigated by no-network manifest + clear single purpose.
- contenteditable breakage is per-site whack-a-mole; ship P1 sites as the
  claim, treat P2 sites as a compatibility list that grows.
- SW eviction mid-typing: worst case one word converts ~250ms late. Accept.
- 16MB packed asset -> CWS package limit is 2GB; irrelevant. Memory: one
  parsed store in SW (~60-80MB) — fine on desktop.

## Success criteria
Type "ami tomake valobashi" into a GitHub comment with the extension on →
আমি তোমাকে ভালোবাসি inline, chips appeared for each word, zero network
requests in DevTools, Alt+B turns it off instantly.
