# Audit: banglu-web + craftsai.org live build 1784181626056 — 2026-07-17
Environment: Android 14/API 34 Google sdk_gphone64_arm64 emulator (Pixel 7 AVD), 1.93 GiB RAM, Chrome 113.0.5672.136, web build 1784181626056, en-US, Wi-Fi on/validated, airplane mode off, battery saver off; HTTP measurements from macOS 26.5/Mac Studio M4 Max/36 GB; auditor AI agent/OpenAI Codex (GPT-5 family)
Time spent: 82 minutes  Coverage: 3/5 (partial/skipped: next-word bar not located; real phone unavailable; visual loading state, first-conversion UI latency, and post-load per-origin privacy traffic not measurable with the available browser controller)

## Summary verdict
NOT READY — The homepage violates the WYSIWYG law for both callback and motivation: the live preview is wrong while Space commits a different correct word, making both P0 Blockers. The third worst issue is the golden bujteparcina conversion, which is wrong on every tested surface. Double-space দাঁড়ি, English preservation for assignment, API validation, and stale desktop downloads also need fixes before release.

## Findings
### F-WEB-001 [P0] callback preview differs from the Space commit
- Area: typing
- Frequency: 2/2 attempts
- Repro:
  1. Open https://www.bangluweb.com/ in Android Chrome and scroll to the live editor.
  2. Focus the empty editor and use the visible lowercase Gboard keys to type callback.
  3. Before pressing Space, record the underlined preview and the first engine candidate.
  4. Press Space once and record the committed text.
  5. Clear the editor and repeat steps 2–4.
- Expected: Preview, candidate 0, and committed text are all কলব্যাক.
- Actual: Preview is ছাল্ল্বাছ্ক, candidate 0 is কলব্যাক, and Space commits কলব্যাক.
- Evidence: e-mobile-callback-preview.png, e-mobile-callback-preview-repro.png, e-mobile-home-golden-core.mp4, e-mobile-golden.txt
  callback attempt 1 -> preview ছাল্ল্বাছ্ক; strip[0] কলব্যাক; Space commit কলব্যাক
  callback attempt 2 -> preview ছাল্ল্বাছ্ক; strip[0] কলব্যাক; Space commit কলব্যাক
  API callback -> HTTP 200; result কলব্যাক
- Version note: Reproduced in Chrome 113.0.5672.136 on Android 14; the API returns the correct committed form.

### F-WEB-002 [P0] motivation preview differs from the Space commit
- Area: typing
- Frequency: 2/2 attempts
- Repro:
  1. Open the homepage live editor in Android Chrome and clear it.
  2. Use the visible lowercase Gboard keys to type motivation.
  3. Before pressing Space, record the underlined preview and first engine candidate.
  4. Press Space once and record the committed text.
  5. Clear the editor and repeat steps 2–4.
- Expected: Preview, candidate 0, and committed text are all মোটিভেশন.
- Actual: Preview is মতিভাতিওন, candidate 0 is মোটিভেশন, and Space commits মোটিভেশন.
- Evidence: e-mobile-motivation-preview.png, e-mobile-motivation-preview-repro.png, e-mobile-home-golden-core.mp4, e-mobile-golden.txt
  motivation attempt 1 -> preview মতিভাতিওন; strip[0] মোটিভেশন; Space commit মোটিভেশন
  motivation attempt 2 -> preview মতিভাতিওন; strip[0] মোটিভেশন; Space commit মোটিভেশন
  API motivation -> HTTP 200; result মোটিভেশন
- Version note: Reproduced in Chrome 113.0.5672.136 on Android 14; the API returns the correct committed form.

### F-WEB-003 [P1] bujteparcina is wrong on every tested web surface
- Area: typing
- Frequency: always (homepage 1/1, /type 1/1, /phonetic 1/1, API 3/3)
- Repro:
  1. On the homepage live editor, clear the field, type bujteparcina with the visible lowercase keys, and press Space.
  2. On /type, clear the input, type bujteparcina, and read the Bangla output.
  3. On /phonetic, clear the input, type bujteparcina, and read the result.
  4. POST {"text":"bujteparcina"} to /api/convert.
  5. Repeat the API request twice.
- Expected: Every surface returns বুঝতে পারছিনা.
- Actual: Every surface returns বুজ্তেপার্ছিনা.
- Evidence: e-mobile-home-golden-core.mp4, e-mobile-type-golden-final.png, e-mobile-phonetic-golden-final.png, e-mobile-golden.txt, e-api-golden.txt
  homepage bujteparcina -> বুজ্তেপার্ছিনা
  /type and /phonetic -> বুজ্তেপার্ছিনা
  API attempts 1–3 -> HTTP 200; বুজ্তেপার্ছিনা
- Version note: Cross-surface failure on live build 1784181626056.

### F-WEB-004 [P1] Homepage and API double-space do not emit দাঁড়ি
- Area: typing
- Frequency: always (homepage 2/2; API 3/3)
- Repro:
  1. Open the homepage live editor and clear it.
  2. Type kacci using the visible lowercase keys and press Space to commit কাচ্চি.
  3. Press Space a second time and inspect the committed text.
  4. Clear and repeat steps 2–3.
  5. POST {"text":"kacci  "} to /api/convert three times.
- Expected: The word followed by double-space becomes কাচ্চি। .
- Actual: The homepage and API retain কাচ্চি  with two spaces and no দাঁড়ি.
- Evidence: e-mobile-double-space-repro.png, e-mobile-golden.txt, e-api-golden.txt
  homepage attempt 1 -> কাচ্চি<space><space> (no দাঁড়ি)
  homepage attempt 2 -> কাচ্চি<space><space> (no দাঁড়ি)
  API attempts 1–3 -> কাচ্চি<space><space> (no দাঁড়ি)
- Version note: /type and /phonetic appeared to pass end-to-end because Gboard changed the raw double-space to a period before the web engine saw it; those routes do not disprove the direct homepage/API failure.

### F-WEB-005 [P1] assignment cannot be preserved as English on three tested surfaces
- Area: typing
- Frequency: always (homepage 1/1, /phonetic 1/1, API 3/3)
- Repro:
  1. On the homepage live editor, clear the field and type assignment with the visible lowercase keys.
  2. Inspect all visible engine candidates, then press Space.
  3. On /phonetic, type assignment and inspect the result.
  4. POST {"text":"assignment"} to /api/convert three times.
- Expected: assignment remains English through Esc or an English candidate where applicable.
- Actual: The homepage shows only অ্যাসাইনমেন্ট as an engine candidate and commits it; /phonetic returns অ্যাসাইনমেন্ট; the API returns অ্যাসাইনমেন্ট on all three attempts.
- Evidence: e-mobile-assignment-preview.png, e-mobile-assignment-commit.png, e-mobile-phonetic-golden-final.png, e-mobile-golden.txt, e-api-golden.txt
  homepage preview/candidate/commit -> অ্যাসাইনমেন্ট
  /phonetic -> অ্যাসাইনমেন্ট
  API attempts 1–3 -> HTTP 200; অ্যাসাইনমেন্ট
- Version note: /type visibly offered an English Assignment chip, but that chip selection was not validly completed and is not included in this finding.

### F-WEB-006 [P2] Invalid API types return HTTP 500 with internal error details
- Area: typing
- Frequency: 2/2 attempts for each invalid payload
- Repro:
  1. POST malformed JSON {invalid JSON to /api/convert with Content-Type: application/json.
  2. Record status and body, then repeat once.
  3. POST {"text":123} with the same header.
  4. Record status and body, then repeat once.
- Expected: The endpoint returns a controlled 4xx validation response without parser or internal function details.
- Actual: Both cases return HTTP 500; malformed JSON exposes parser detail and numeric text exposes a.a is not a function.
- Evidence: e-api-errors.txt
  malformed JSON attempt 1 -> HTTP 500; Expected property name or '}'...
  malformed JSON attempt 2 -> HTTP 500; same parser detail
  numeric text attempts 1–2 -> HTTP 500; a.a is not a function
- Version note: Live build 1784181626056; missing text is handled correctly with HTTP 400.

### F-WEB-007 [P2] CraftsAI download cards still serve desktop 1.1.0
- Area: store
- Frequency: always (3/3 platform cards)
- Repro:
  1. Open https://www.craftsai.org/products/banglu.
  2. Inspect the public Windows, macOS, and Linux download targets.
  3. Issue a one-byte range request to each public target and record final status and Content-Range total.
  4. Compare the artifact versions with the current desktop product version 1.2.0 supplied for this audit.
- Expected: Download cards serve the current desktop release 1.2.0, and all links resolve with the advertised sizes.
- Actual: All links resolve with HTTP 206 and sizes match, but every artifact filename and release path is version 1.1.0.
- Evidence: e-crafts-mobile-top.png, e-download-links.txt
  Windows -> Banglu-1.1.0.msi; HTTP 206; 134,903,609 bytes
  macOS -> Banglu-1.1.0.dmg; HTTP 206; 150,845,885 bytes
  Linux -> banglu_1.1.0-1_amd64.deb; HTTP 206; 118,002,302 bytes
- Version note: Current desktop version is 1.2.0; this audit did not install the downloaded 1.1.0 files.

## Golden-set table
| input | expected | got | pass |
|---|---|---|---|
| homepage: kemon acho | কেমন আছো | কেমন আছো | yes |
| homepage: issa | ইচ্ছা | ইচ্ছা | yes |
| homepage: korsi | করছি | করছি | yes |
| homepage: golp | গল্প | গল্প | yes |
| homepage: bujteparcina | বুঝতে পারছিনা | বুজ্তেপার্ছিনা | no |
| homepage: ssc | এসএসসি | এসএসসি | yes |
| homepage: phd | পিএইচডি | পিএইচডি | yes |
| homepage: otp | ওটিপি | ওটিপি | yes |
| homepage: callback | কলব্যাক; preview = commit | preview ছাল্ল্বাছ্ক; commit কলব্যাক | no |
| homepage: motivation | মোটিভেশন; preview = commit | preview মতিভাতিওন; commit মোটিভেশন | no |
| homepage: kacci | কাচ্চি | কাচ্চি | yes |
| homepage: double-space | ।  | two spaces | no |
| homepage: 1234567890 | ১২৩৪৫৬৭৮৯০ | not validly tested | — |
| homepage: assignment | assignment | অ্যাসাইনমেন্ট | no |
| /type: kemon acho | কেমন আছো | কেমন আছো | yes |
| /type: issa | ইচ্ছা | ইচ্ছা | yes |
| /type: korsi | করছি | করছি | yes |
| /type: golp | গল্প | গল্প | yes |
| /type: bujteparcina | বুঝতে পারছিনা | বুজ্তেপার্ছিনা | no |
| /type: ssc | এসএসসি | এসএসসি | yes |
| /type: phd | পিএইচডি | পিএইচডি | yes |
| /type: otp | ওটিপি | ওটিপি | yes |
| /type: callback | কলব্যাক | কলব্যাক | yes |
| /type: motivation | মোটিভেশন | মোটিভেশন | yes |
| /type: kacci | কাচ্চি | কাচ্চি | yes |
| /type: double-space | ।  | ।  (Gboard supplied period) | yes |
| /type: 1234567890 | ১২৩৪৫৬৭৮৯০ | ১২৩৪৫৬৭৮৯০ | yes |
| /type: assignment | assignment via English chip | default অ্যাসাইনমেন্ট; chip visible but not selected | — |
| /phonetic: kemon acho | কেমন আছো | কেমন আছো | yes |
| /phonetic: issa | ইচ্ছা | ইচ্ছা | yes |
| /phonetic: korsi | করছি | করছি | yes |
| /phonetic: golp | গল্প | গল্প | yes |
| /phonetic: bujteparcina | বুঝতে পারছিনা | বুজ্তেপার্ছিনা | no |
| /phonetic: ssc | এসএসসি | এসএসসি | yes |
| /phonetic: phd | পিএইচডি | পিএইচডি | yes |
| /phonetic: otp | ওটিপি | ওটিপি | yes |
| /phonetic: callback | কলব্যাক | কলব্যাক | yes |
| /phonetic: motivation | মোটিভেশন | মোটিভেশন | yes |
| /phonetic: kacci | কাচ্চি | কাচ্চি | yes |
| /phonetic: double-space | ।  | ।  (Gboard supplied period) | yes |
| /phonetic: 1234567890 | ১২৩৪৫৬৭৮৯০ | ১২৩৪৫৬৭৮৯০ | yes |
| /phonetic: assignment | assignment | অ্যাসাইনমেন্ট | no |
| API: kemon acho | কেমন আছো | কেমন আছো | yes |
| API: issa | ইচ্ছা | ইচ্ছা | yes |
| API: korsi | করছি | করছি | yes |
| API: golp | গল্প | গল্প | yes |
| API: bujteparcina | বুঝতে পারছিনা | বুজ্তেপার্ছিনা | no |
| API: ssc | এসএসসি | এসএসসি | yes |
| API: phd | পিএইচডি | পিএইচডি | yes |
| API: otp | ওটিপি | ওটিপি | yes |
| API: callback | কলব্যাক | কলব্যাক | yes |
| API: motivation | মোটিভেশন | মোটিভেশন | yes |
| API: kacci | কাচ্চি | কাচ্চি | yes |
| API: double-space | ।  | two spaces | no |
| API: 1234567890 | ১২৩৪৫৬৭৮৯০ | ১২৩৪৫৬৭৮৯০ | yes |
| API: assignment | assignment | অ্যাসাইনমেন্ট | no |

## Perf numbers
| measurement | result |
|---|---:|
| Homepage HTTP TTFB / total | 217.6 ms / 219.5 ms |
| /type HTTP TTFB / total | 202.1 ms / 202.3 ms |
| /phonetic HTTP TTFB / total | 166.0 ms / 166.3 ms |
| CraftsAI product page HTTP TTFB / total | 235.9 ms / 268.2 ms |
| Slim dictionary compressed transfer | 1,924,806 bytes; 116.3 ms TTFB; 423.4 ms total |
| Slim dictionary uncompressed transfer | 17,571,321 bytes; 149.9 ms TTFB; 940.2 ms total |
| Slim dictionary uncompressed size | 17,571,321 bytes |
| API failing-case repeat range | 106.793–197.292 ms |
| Mobile viewport | 1080x2400 |

## Not tested / needs deeper look
- Mandatory privacy claim remains unverified: the available browser controller could not attach, so no DevTools/per-origin trace proved zero post-load typing requests. Initial page and dictionary fetches are expected network activity and were measured separately.
- Next-word/trigram bar behavior and suggestion quality beyond the exercised current-word candidates.
- Homepage digits; the first attempt was tester-invalid and its artifacts were discarded.
- /type English Assignment chip selection; the chip was visible, but the selection rerun became tester-invalid and was discarded.
- A real mobile phone; mobile usability was exercised on a 2 GB Android emulator.
- Visual loading-state duration and first-conversion UI latency after an uncached dictionary fetch.
- Desktop browser layout and cross-browser behavior; this report covers live HTTP plus Android Chrome.
- Full artifact downloads/installs and signing warnings; only link resolution, range status, and byte totals were checked.
