# Banglu Production-Readiness Audit — Agent Prompt + Report Contract

Give an AI agent ONE product section below per session (plus the "Universal
rules" and "Report contract"). The reports it produces feed directly into
fix rounds — findings without evidence/repro get bounced, so the contract is
strict.

---

## Universal rules (paste into every audit session)

You are auditing a Banglu product for production readiness. Rules:

1. **Evidence or it didn't happen.** Every finding needs: exact repro steps
   you personally executed, expected vs actual, and an artifact (screenshot,
   screen recording, logcat/console snippet, HTTP trace). Never report from
   memory or speculation. If you can't reproduce something twice, mark
   frequency honestly (e.g. "1/5 attempts").
2. **Version-stamp everything.** Record before starting: app version + build,
   dictionary/db version if shown, OS + device model, RAM, locale, network
   state (wifi/data/airplane), battery-saver state.
3. **Test like three personas:** (a) a first-time non-technical user who has
   never installed a keyboard (install → first word typed, count every tap
   and every moment of confusion); (b) a daily chat user (speed, shorthand,
   mixed Bangla/English, emoji, corrections); (c) an adversarial user (mash
   keys, rotate screen mid-word, kill the app, fill storage, deny
   permissions, airplane mode, switch apps mid-action).
4. **The golden conversion set** — type these EVERYWHERE and record any
   deviation: `kemon acho` → কেমন আছো · `issa` → ইচ্ছা · `korsi` → করছি ·
   `golp` → গল্প · `bujteparcina` → বুঝতে পারছিনা · `ssc` → এসএসসি ·
   `phd` → পিএইচডি · `otp` → ওটিপি · `callback` → কলব্যাক ·
   `motivation` → মোটিভেশন · `kacci` → কাচ্চি · double-space → `। ` ·
   digits → ০-৯ · `assignment` stays English (via Esc/candidate where
   applicable). WYSIWYG law: what the preview shows is what commit produces —
   ANY divergence is a Blocker.
5. **Privacy checks are mandatory:** with a network monitor (or airplane
   mode), verify typing works fully offline and produces zero network
   traffic from the typing surface.
6. **Timebox per product: 60–90 minutes.** Coverage of the checklist beats
   depth on one bug — flag rabbit holes as "needs deeper investigation."
7. Do not fix anything. Do not update the app mid-audit. One product per
   report.

## Severity taxonomy

- **P0 Blocker** — data loss, crash, WYSIWYG divergence, privacy leak
  (any network call from a typing surface), cannot install/enable.
- **P1 Major** — a core flow fails or is wrong for a real use case (voice
  dead-mic, candidate popup wrong position, learning not persisting, a
  golden-set word wrong).
- **P2 Minor** — friction/inconsistency that a user notices but can work
  around (label wrong, awkward flow, slow-but-works).
- **P3 Polish** — cosmetics, wording, nice-to-have.

---

## Product sections (one per session)

### A. Android keyboard (Banglu Keyboard, v1.5.34 / db 3.8.5)
- Install & enable: fresh install → enabled → first Bangla word. Count taps;
  note every screen where a novice could get lost. MainActivity guide
  accuracy; tutorial card correctness.
- Typing core: golden set; typing speed burst (as fast as possible for 30s —
  any dropped/stuck keys, frozen preview); suggestion strip picks; strip[0]
  == what space commits; undo chip after autocorrect.
- Learning: pick a non-primary suggestion twice → does it become primary?
  Survives app kill + reboot? Settings → learning off actually stops it?
- Voice typing: 5 sessions incl. after phone idle >30min, on wifi, on data,
  in airplane mode, with battery saver ON. Record: does the mic chip ever
  show "listening" while nothing happens (dead mic)? Latency of results?
  Mixed Bangla/English dictation smoothness. Capture logcat
  (`adb logcat -s BangluIME`) for every session.
- Performance: cold start (kill app → open keyboard: ms until typable, note
  seed-mode window); memory (`adb shell dumpsys meminfo com.banglu.keyboard`)
  idle vs during burst; janky frames during burst
  (`adb shell dumpsys gfxinfo com.banglu.keyboard`).
- Low-end: if available, a 2-3GB device or emulator with
  `setprop dalvik.vm.heapgrowthlimit 128m` (lite mode) — golden set again,
  preview/commit must still agree.
- Rotation/IME lifecycle: rotate mid-word, switch apps mid-word, incoming
  call mid-word — composing text must commit or clear, never duplicate.
- Privacy: airplane mode → everything above still works; network monitor
  shows zero traffic from the keyboard process.
- Store-readiness: screenshots current? listing text matches actual
  features? data-safety answers still true?

### B. Desktop editor (বাংলু এডিটর v1.2.0 — macOS, Windows, Linux; one report per OS)
- Install: from the actual public artifact (GitHub release / craftsai.org
  link) on a CLEAN machine/VM if possible. SmartScreen/Gatekeeper notes
  match what the download page promises? App opens?
- Typing core: golden set; live forming + underline; candidate popup (digits
  ১-৬, arrows, Esc keeps English); click-any-word-to-fix; popup POSITION on
  long words and near window edges.
- Files: save/open round-trip (Bangla filename!); unsaved-changes guard all
  3 buttons; autosave: type a burst → force-kill the app (Task Manager /
  kill -9) → relaunch → is the burst there? Repeat with window-close and
  tray-quit.
- Exports: docx opens in Word/LibreOffice with perfect conjuncts (স্ত্রী,
  বুদ্ধি, ন্ত্র); print-to-PDF output correct; সব কপি করুন pastes into a
  chat app correctly.
- Tray & hotkey: tray menu completeness; global hotkey toggle works live;
  ⌘⇧B/Ctrl+Shift+B mini converter over another app, Enter-paste path (and
  the macOS Accessibility-not-granted fallback = clipboard).
- Tutorial (শিখুন): every card renders, Bangla correct, no clipping at min
  window size (520×400).
- Performance: cold start to typable; typing latency feel during burst;
  memory after 30min idle.
- Privacy: network monitor — zero traffic.

### C. Browser extension (Chrome + Firefox; one report per browser)
- Install from the actual zip (developer mode). First-run card shows once.
- Inline typing in: plain <input>, <textarea>, Gmail compose,
  Facebook/WhatsApp Web message box, Google Docs (known-hard), X/Twitter
  compose. Golden set in each. Record per-site: works / degraded / broken.
- Popup converter: golden set, copy button, suggestion chips.
- Conflicts: page's own keyboard shortcuts still work? Password fields
  ignored? Extension off-toggle actually stops interception?
- Performance: heavy page (long Google Doc) — typing lag?
- Privacy: devtools network tab — zero requests from the extension during
  typing.

### D. macOS input method (বাংলু ইনপুট মেথড)
- Setup assistant: double-click the app → window appears? এক ক্লিকে চালু
  button enables WITHOUT System Settings? বন্ধ করুন disables? Re-enable
  works immediately (the remove-trap must be gone)? Menu-bar বা toggle
  mirrors state? Checkboxes actually change the two system prefs?
- Typing: golden set in EVERY gated app: Notes, TextEdit, Pages, Spotlight,
  Safari (Gmail/WhatsApp Web/Docs), Chrome (same three), Word, WhatsApp
  Desktop, Messenger, Slack, Discord, ChatGPT desktop. Per-app verdict:
  full / degraded / broken + symptom (doubled letters, stray composing
  fragments, lost marked text).
- Candidate panel: position near caret on short and LONG words; digits ১-৬;
  Esc → English stays.
- Pending-space দাঁড়ি: word·space·space → `। `; word·space·comma → tight।
- Learning round-trip with the desktop editor (teach in one, see in other).
- Cold start: first keystrokes after login (seed-echo window ~11s) — does
  raw English echo feel broken or acceptable? Note exactly what a user sees.
- Switching: Globe/Ctrl+Space in/out; per-app memory off means one switch
  applies everywhere?

### E. banglu-web (bangluweb.com) + craftsai.org
- Live site: golden set on the homepage editor, /type, /phonetic; API
  routes if exposed (convert endpoint returns correct Bangla).
- Suggestion panels, next-word bar (now trigram-backed) — sensible output?
- Mobile browser (real phone): usable? keyboard interactions sane?
- Load time: first conversion after page load (slim dictionary fetch ~17MB —
  measure; is there a loading state or a dead page?).
- craftsai.org/products/banglu: all three download links resolve (206/200),
  sizes right, page renders on mobile.

---

## Report contract (the agent MUST output exactly this)

One markdown file per product: `audit-<product>-<date>.md`

```markdown
# Audit: <product> <version> — <date>
Environment: <OS/device, RAM, app+db version, network, auditor (human/agent+model)>
Time spent: <minutes>  Coverage: <checklist items done>/<total> (list skipped + why)

## Summary verdict
READY | READY WITH FIXES | NOT READY — one paragraph, the 3 worst findings.

## Findings
### F-<product>-001 [P0|P1|P2|P3] <one-line title>
- Area: <install|typing|learning|voice|files|export|perf|privacy|ui|store>
- Frequency: <always | N/M attempts>
- Repro:
  1. <numbered, exact, from clean state>
- Expected: <one line>
- Actual: <one line>
- Evidence: <filename of screenshot/recording/log + the 3 relevant log lines inline>
- Version note: <anything env-specific>

(repeat per finding; number sequentially; NO finding without Repro+Evidence)

## Golden-set table
| input | expected | got | pass |
(all rows, every surface tested)

## Perf numbers
<the measured numbers requested in the product section, as a table>

## Not tested / needs deeper look
<honest list>
```

Package evidence files in a folder `audit-<product>-<date>/` next to the
report. Hand the report + folder back — findings enter the S-round pipeline
(reproduce → root-cause → fix at the right layer → regression test).
```

---

## How to run it

1. One agent session per product section (A–E), pasting: Universal rules +
   Severity taxonomy + that product's section + the Report contract.
2. The agent drives the real product (device connected for Android; the
   installed apps for desktop/IME; live URLs for web). Where it cannot
   physically act (tapping a phone), it directs the human tester step-by-step
   and collects the evidence.
3. Reports land in `docs/audits/` in this repo. Each becomes S-round input:
   findings triaged by severity, P0/P1 get immediate rounds, P2/P3 batch.
```
