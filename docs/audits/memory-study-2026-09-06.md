# IME memory study — native heap churn while typing (S195, 2026-09-06)

**Trigger:** the release smoke's Dalvik+native heap reading drifted across four clean
installs of near-identical builds (228 → 284 → 300 → 323 MB, one run failing the 320 MB
cap by 0.9 %, the rerun 292 MB). User: "do this, make sure app performance remains best
always".

**Method.** Galaxy S22 (Android 16), release build, `memstudy.py`: every 5 s
`dumpsys meminfo` of the IME process (native heap PSS / size / alloc / free, Dalvik,
total PSS) through boot, three 26-letter typing bursts, two minutes idle, a system trim.
Then `heapprofd` (the manifest is `profileable`) with 32 KB sampling and 4 s continuous
dumps during a typing burst, analysed with the perfetto trace processor (`heap_an.py`).

## 1. Timeline of the shipped build (1.5.127), clean install

| phase | native alloc | native size | total PSS |
|---|---|---|---|
| boot → 3 min (dictionary copied, idle) | 14 MB | 34 MB | 104 MB |
| typing burst 1 / 2 / 3 | 115 / 200 / 165 MB | 261 / 296 / 324 MB | 359 / 393 / 434 MB |
| idle 20 s after typing | 16 MB | 326 MB (306 MB free, retained) | 195 MB |
| after system trim, final | 23 MB | 330 MB | 133 MB |

The dictionary copy is innocent. **Typing** allocates and frees hundreds of MB of native
memory per burst; the allocator keeps the arena, and the smoke samples it right after
its own typing burst — hence the drift, and the sampling-moment dependence.

## 2. What allocated it (heapprofd, per 4 s window while typing, 1.5.127)

libc.malloc: ~900 MB allocated and ~900 MB freed per window. Top callsites:

| allocator | MB over the 30 s trace | cause in our code |
|---|---|---|
| ICU `RegexCompile` (UnicodeSet freeze) | ~500 | `Regex(...)` constructed INSIDE hot-path functions: `isCleanSuggestion` (per chip), `vowelPath` (4 patterns per candidate), `hasSuspiciousGeneratedConjunct`, `lowercaseV2AlignmentScore` (3 per candidate) |
| `CursorWindow.create` (2 MB per cursor) | ~290 | hundreds of sqlite point queries per keystroke (the S144 finding, never ported to Android) |
| sqlite `pcache1Alloc` / `BtreeOpen` / `allocateTempSpace` | ~230 | the ephemeral sort table every `ORDER BY` point query opens |
| `CloseGuard.openWithCallSite` → `Throwable.fillInStackTrace` | ~110 | `detectLeakedClosableObjects` installed in the RELEASE StrictMode VM policy → a Java stack trace captured for every cursor and cursor window |

## 3. Fixes

1. **Regexes compiled once** (shared engine, every surface): ten patterns hoisted to
   file-level `val`s; identical behaviour.
2. **CloseGuard only in debug builds**: `detectLeakedClosableObjects` is now behind
   `BuildConfig.DEBUG`; the network detector (the offline guard) stays in release.
3. **Point queries get a 64 KB cursor window** (API 28+; default 2 MB) — measured
   alone it changed nothing, because the query COUNT was the problem; kept because
   each remaining query now costs 32× less.
4. **S144 negative index ported to Android**: three Bloom filters (1.66M index keys
   4 MB, 396K extended phonetics 1 MB, 131K extended words 1 MB; 4 hashes) built on a
   daemon thread at minimum priority on the store's own read connection (plain scans,
   no DISTINCT — a DISTINCT over 1.8M rows sorts in sqlite's temp store, the S169b
   spike), a 2,048-entry memo for the Bengali→phonetic reverse lookup, and the
   english-key short circuit. Built within the first 5 s of boot on the S22. No false
   negatives by construction; a false positive only costs the query that used to run.

## 4. Result (warm restart protocol, two runs each)

| metric | 1.5.127 | + regex/CloseGuard/window | + negative index (final) |
|---|---|---|---|
| native size during typing | 216 / 294 MB | 143 / 135 MB | 139 / 136 MB |
| retained free after typing | 195 / 275 MB | 123 / 115 MB | 121 / 117 MB |
| total PSS peak while typing | 318 / 392 MB | 229 / 186 MB | 230 / 229 MB |
| total PSS 2 min idle | 200 / 187 MB | 190 / 148 MB | 130 / 128 MB |
| native PSS 2 min idle | 121 / 110 MB | 93 / 47 MB | 47 / 45 MB |
| malloc churn per 4 s of typing (heapprofd) | ~900 MB | ~150 MB | ~150 MB (CursorWindow 520 → 46 MB over the trace, ≈ 11× fewer queries) |

Files: `s195-memory-study/` (timelines, harness, heapprofd config and analyser).

## 5. Not verified here

- The 2 GB low-RAM profile (S171 protocol on the emulator) was not re-run in this
  round; the change reduces churn and adds 6 MB of Dalvik for the filters.
- The smoke threshold (320 MB heap) is unchanged; the final build samples ~140–230 MB.
