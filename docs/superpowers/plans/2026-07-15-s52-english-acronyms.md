# S52 — English Loanword Tail + Acronym Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Acronyms (ssc→এসএসসি class) convert correctly on every surface via a curated two-tier override map, and the measured English-word tail (callback/motivation/semester/late class) is repaired in the lexicon data — with zero regressions, gated by the S24 eval harness.

**Architecture:** Fix 1 is a commonMain whitelist map wired at the exact three `MOBILE_SHORTHAND_OVERRIDES` call sites (validator-free → all surfaces + lite, WYSIWYG-safe). Fix 2 is compiler data (`english_lexicon_overrides.tsv`) plus an eval-gated frequency-cutoff experiment, shipped as db 3.8.5 with regenerated slim JSON. Spec: `docs/superpowers/specs/2026-07-15-s52-english-acronyms-design.md`. Evidence inputs every implementer must read: `.superpowers/sdd/probe-english-acronyms.md`, `.superpowers/sdd/explore-loanword-machinery.md`, `.superpowers/sdd/research-banglish-web.md`.

## Global Constraints

- NO heuristic acronym detection — whitelist only (llb/gb/km collide with real Bengali words; proven by probe).
- Tier P (primary override) is allowed ONLY for keys whose CURRENT primary is garbage or whose current-correct value the map preserves verbatim. Any key whose current primary is a real, meaning-bearing Bengali word (বা, মা, আতপ counts as garbage-collision → P is fine since আতপ is wrong for otp… the test is: would a Bengali speaker ever TYPE this key wanting the Bengali word? ba/ma/id YES → Tier S; otp/nid NO → Tier P). When in doubt → Tier S.
- Never change: kacci→কাচ্চি, jos/hoise/dibi defaults, kassi→কাচছি, name→নামে class, any parity pin. A flipped pin = stop and escalate, never a test edit.
- `isPlausibleDynamicMapping` untouched. Ranking law untouched. HABIT_RULES untouched.
- Cutoff raise ships ONLY with a zero-regression S24EvalJvm diff; otherwise revert cutoff, keep curated rows.
- db changes bump DictionaryCompiler version string "3.8.4"→"3.8.5" AND AndroidDictionaryLoader.REQUIRED_DB_VERSION together; rebuilt db goes to android assets AND repo root; slim regenerated and propagated (extension vendor + macos-ime Resources/built via build-engine.sh).
- Letter-name table (verified): A এ B বি C সি D ডি E ই F এফ G জি H এইচ I আই J জে K কে L এল M এম N এন O ও P পি Q কিউ R আর S এস T টি U ইউ V ভি W ডাব্লিউ X এক্স Y ওয়াই Z জেড. Lexicalized acronyms use word forms: wifi ওয়াইফাই, sim সিম, tv টিভি, pin পিন.
- Gates per surface (all must be green before the round closes): `:shared:jvmTest`, `:shared:testDebugUnitTest`, `:shared:jsNodeTest`, `:desktop-app:test`, `cd macos-ime && swift run BangluCoreTestRunner`.
- Never `git add -A`; never push (controller pushes at round close).

---

### Task 1: ACRONYM_OVERRIDES — two-tier map + regression tests

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/banglu/engine/SmartEngine.kt` (the three MOBILE_SHORTHAND call sites ≈:991/:1303/:1400 — discover exact lines; the map itself may live beside MOBILE_SHORTHAND_OVERRIDES or in a new `dictionary/AcronymData.kt` if the map is large — prefer the new file)
- Test: `shared/src/jvmTest/kotlin/com/banglu/engine/S52EnglishAcronymJvmTest.kt`

**Interfaces:**
- Produces: `ACRONYM_OVERRIDES: Map<String, String>` (Tier P) and `ACRONYM_SUGGESTIONS: Map<String, String>` (Tier S), both consumed inside SmartEngine only. Behavior contract: Tier P keys convert to their mapped value at confidence ≥0.99 in convertWordRaw AND appear identically in `convertForInstantPreview` and the composing path; Tier S keys leave the primary untouched and inject the acronym as a suggestion (study how S24's curated-loanword chip injection works in getSuggestions and follow that mechanism).

- [ ] **Step 1: Build the evidence-tiered list.** Read the three evidence files. Start from the research top-30 + probe B-list. For EVERY candidate key, run a quick probe (temporary test, delete after) printing the CURRENT primary. Tier rules from Global Constraints. Produce the final two maps as Kotlin code with a comment per row: `// current: <old primary> (garbage|correct|collision)`. Target ≈80–120 Tier P, ≈6–12 Tier S. Include in Tier P the currently-correct acronyms with their current values (lite/slim tier hardening).
- [ ] **Step 2: Write the failing tests** — S52EnglishAcronymJvmTest on the real store: every Tier P key `assertEquals(mapped, engine.convertWord(key).bengali)`; every Tier S key asserts primary UNCHANGED (pin the current Bengali word) AND `getSuggestions(key, 6)` contains the acronym form; WYSIWYG: for 8 sampled Tier P keys `convertForInstantPreview(key) == mapped`; guard pins: kacci→কাচ্চি, jos/hoise unchanged, name→নামে, plus 5 random common words (ami/kemon/bhalo/kal/golpo) unchanged. Run → RED (garbage primaries).
- [ ] **Step 3: Implement** the map(s) + the three call-site hooks + the suggestion injection. Run the class → GREEN.
- [ ] **Step 4: Full JVM walls** — `:shared:jvmTest :shared:testDebugUnitTest` all green (a flipped pin = escalate).
- [ ] **Step 5: Commit** `feat(engine): S52 — curated acronym layer (Tier P primaries + Tier S suggestion chips)`.

### Task 2: Lexicon tail repairs + eval-gated cutoff experiment

**Files:**
- Modify: `dictionary-compiler/data/english_lexicon_overrides.tsv`, `dictionary-compiler/src/.../EnglishLexiconBuilder.kt` (cutoff), possibly reverted
- Test: the existing `S24EvalJvm` harness + a small `S52LexiconTailJvmTest`

**Interfaces:**
- Consumes: compiler run recipe `./gradlew :dictionary-compiler:run --args="<abs>/banglu-web/public <abs>/dictionary.sqlite"` (verify ../banglu-web/public exists first; if absent STOP and report BLOCKED — the wordlists are required).
- Produces: candidate dictionary.sqlite builds for the eval; the FINAL tsv + cutoff decision. db version stays 3.8.4 in this task (3.8.5 happens in Task 3 — trial builds must not brick the version gate; build trial dbs to a scratch path, NOT ./dictionary.sqlite).

- [ ] **Step 1: Baseline** — run S24EvalJvm against the current ./dictionary.sqlite; save the per-word results file (the harness's output) as the diff baseline.
- [ ] **Step 2: tsv repairs** — add/fix rows: motivation মোটিভেশন, semester সেমিস্টার, ngo এনজিও, callback কলব্যাক, late লেট + any remaining A-list probe misses (check probe file). Trial-build a db to the scratchpad, re-run eval pointed at it: expect fixes present, zero regressions.
- [ ] **Step 3: cutoff experiment** — 30,000→50,000 in EnglishLexiconBuilder; trial-build; eval diff vs baseline. ZERO regressions → keep; ANY regression → revert cutoff (keep tsv), document the regressing words in the report.
- [ ] **Step 4: S52LexiconTailJvmTest** — pins for the repaired words against a trial db is impossible in CI (tests read ./dictionary.sqlite) — so write the pins now but mark the class to be enabled in Task 3 after the real db ships (or assert via the lexicon-layer lookup unit-style if feasible). Document the choice.
- [ ] **Step 5: Commit** `feat(compiler): S52 — lexicon tail repairs + cutoff decision (<kept|reverted>, eval-diffed)`.

### Task 3: Ship db 3.8.5 + slim + propagate to all surfaces

**Files:**
- Modify: `dictionary-compiler/src/.../DictionaryCompiler.kt` (version "3.8.5"), `android-keyboard/.../AndroidDictionaryLoader` REQUIRED_DB_VERSION, android-keyboard versionCode/Name bump
- Artifacts: rebuilt `dictionary.sqlite` → android assets + repo root + `desktop-app/resources/common/`; regenerated `shared/banglu-slim.json` (+.gz) via the compiler's --slim output; `./browser-extension/build.sh`; `./macos-ime/scripts/build-engine.sh`

- [ ] **Step 1:** Version bumps (compiler string + REQUIRED_DB_VERSION + android versionCode/Name).
- [ ] **Step 2:** Final compiler run (with --slim); copy db to the three destinations; verify `PRAGMA user_version`/version row says 3.8.5 and the android loader gate matches.
- [ ] **Step 3:** Enable/complete S52LexiconTailJvmTest pins; full walls: `:shared:jvmTest :shared:testDebugUnitTest :shared:jsNodeTest :desktop-app:test` — all green.
- [ ] **Step 4:** Extension vendor refresh + IME engine rebuild; `cd macos-ime && swift run BangluCoreTestRunner` green (83+); spot-probe through the runner's EngineJS: ssc→এসএসসি on the slim tier.
- [ ] **Step 5:** Commit `release(engine): S52 — db 3.8.5: acronym layer + lexicon tail across all surfaces`.

### Task 4: Round close (controller + user)

- [ ] Rebuild + reinstall desktop app (packageDmg + ditto) and IME (`make install`) so the dev machine runs 3.8.5; android perf APK build for the user's phone (`assemblePerf`) → releases/.
- [ ] Ledger + CLAUDE.md status line updates (db 3.8.5, S52 summary); push after walls; user does device/manual spot checks (ssc, hsc, otp, nid, callback, motivation in the editor + IME + phone).

## Self-Review Notes

- Spec coverage: Fix 1 → Task 1; Fix 2 → Tasks 2–3; verification section → distributed gates + Task 4. Out-of-scope items untouched.
- The Tier P/S decision rule lives in Global Constraints (typed-intent test); Task 1 Step 1 makes it evidence-based per key.
- Known open detail deferred to Task 1's implementer with explicit instruction: the exact suggestion-injection mechanism (follow the S24 curated-loanword chip precedent found in the machinery exploration).
