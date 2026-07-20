# Audit: Android keyboard 1.5.34 (2071) / db 3.8.5 — 2026-07-16
Environment: Android 14/API 34 Pixel 7 AVD, 2 GB RAM, Banglu Keyboard 1.5.34 (2071) + db target 3.8.5, en-US, Wi-Fi/emulated cellular plus airplane-mode runs, battery saver off except the specified voice run, auditor agent (Codex/GPT-5)
Time spent: 70 minutes  Coverage: 6/9 (voice was partial: no real microphone speech and no >30-minute idle session; lifecycle was partial: no incoming call; store-readiness/public Play artifact was skipped because only the local perf APK and no authenticated store surface were available)

## Summary verdict
NOT READY — three independently reproduced P0 WYSIWYG violations block release: `callback` previews as `ছাল্ল্বাছ্ক` but commits `কলব্যাক`, `motivation` previews as `মতিভাতিওন` but commits `মোটিভেশন`, and a learned `ki` primary previews `কি` while Space commits `কী`. The golden set also has a wrong `bujteparcina` result and the enabled double-space দাঁড়ি flow never emits `। `.

## Findings
### F-ANDROID-001 [P0] `callback` preview differs from its Space commit
- Area: typing
- Frequency: always (2/2 warm attempts; also reproduced in 128 MB and airplane runs)
- Repro:
  1. From the in-app editor with Banglu in BN mode and no composing text, tap the visible lowercase keys `c`, `a`, `l`, `l`, `b`, `a`, `c`, `k`.
  2. Observe the underlined editor preview and strip position 0.
  3. Tap the visible Space key once.
- Expected: The preview and strip position 0 are `কলব্যাক`, and Space commits exactly `কলব্যাক `.
- Actual: The editor previews `ছাল্ল্বাছ্ক`; Space replaces it with `কলব্যাক `.
- Evidence: `a-golden-09-callback-preview.png`, `a-reproductions.mp4`, `a-privacy-callback-preview.png`, `a-golden-accessibility.txt` — `attempt 1 preview => ছাল্ল্বাছ্ক`; `attempt 1 Space commit => কলব্যাক`; `attempt 2 preview => ছাল্ল্বাছ্ক; Space commit => কলব্যাক`.
- Version note: Reproduced on the 1.5.34 (2071) perf APK in warm, 128 MB heap, and airplane-mode conditions.

### F-ANDROID-002 [P0] `motivation` preview differs from its Space commit
- Area: typing
- Frequency: always (2/2 warm attempts; also reproduced in 128 MB and airplane runs)
- Repro:
  1. From a clean in-app editor with Banglu in BN mode, tap the visible lowercase keys for `motivation`.
  2. Observe the underlined editor preview and strip position 0.
  3. Tap the visible Space key once.
- Expected: The preview and commit are both `মোটিভেশন`.
- Actual: The editor previews `মতিভাতিওন`; Space replaces it with `মোটিভেশন `.
- Evidence: `a-golden-10-motivation-preview.png`, `a-repro-02-motivation-preview.png`, `a-privacy-motivation-preview.png`, `a-golden-accessibility.txt` — `attempt 1 preview => মতিভাতিওন`; `attempt 1 Space commit => মোটিভেশন`; `attempt 2 preview => মতিভাতিওন; Space commit => মোটিভেশন`.
- Version note: Reproduced on the 1.5.34 (2071) perf APK in warm, 128 MB heap, and airplane-mode conditions.

### F-ANDROID-003 [P0] Learned primary candidate changes on Space without updating the preview
- Area: learning
- Frequency: always (2/2 persistence checks: process restart and full reboot)
- Repro:
  1. With learning enabled and a clean learned-choice profile, type `ki` with visible lowercase keys.
  2. Tap the non-primary `কী` candidate; type `ki` again and confirm `কী` is now strip position 0.
  3. Restart the app process or reboot the emulator, reopen the editor, and type `ki` again.
  4. Observe the editor preview and strip position 0, then tap Space.
- Expected: The editor preview equals learned strip position 0 and the text committed by Space.
- Actual: The editor previews `কি`, strip position 0 shows `কী`, and Space changes the text to `কী `.
- Evidence: `a-learning-08-after-process-kill-primary.png`, `a-learning-09-after-process-kill-commit.png`, `a-learning-10-after-reboot-primary-stable.png`, `a-learning-log.txt` — `preview/editor = কি`; `strip[0] = কী`; `Space commit = কী`.
- Version note: Ranking persisted across both restart conditions; learning-off separately passed and did not promote two later selections.

### F-ANDROID-004 [P1] Golden `bujteparcina` conversion is wrong
- Area: typing
- Frequency: always (2/2 warm attempts; also reproduced in 128 MB and airplane runs)
- Repro:
  1. From a clean BN-mode editor, tap the visible lowercase keys `bujteparcina`.
  2. Observe the preview.
  3. Tap Space and inspect the committed word.
- Expected: `বুঝতে পারছিনা`.
- Actual: `বুজ্তেপার্ছিনা`.
- Evidence: `a-golden-05-bujteparcina-preview.png`, `a-repro-03-bujteparcina-preview.png`, `a-low-end-golden.mp4`, `a-golden-accessibility.txt` — `warm attempt 1 => বুজ্তেপার্ছিনা`; `warm attempt 2 => বুজ্তেপার্ছিনা`; `airplane => বুজ্তেপার্ছিনা`.
- Version note: Identical under warm, 128 MB heap, and airplane-mode conditions.

### F-ANDROID-005 [P1] Enabled double-space period emits two spaces instead of দাঁড়ি
- Area: typing
- Frequency: always (2/2 warm attempts; also reproduced in 128 MB and airplane runs)
- Repro:
  1. Confirm Settings shows “ডাবল-স্পেস পিরিয়ড” enabled.
  2. In BN mode, type and Space-commit `kacci`.
  3. Tap Space a second time.
- Expected: The trailing word separator becomes `। `.
- Actual: The editor contains two U+0020 spaces and no `।`.
- Evidence: `a-golden-12-double-space-stable.png`, `a-repro-04-double-space.png`, `a-low-end-golden.mp4`, `a-golden-accessibility.txt` — `warm attempt 1 => U+0020 U+0020`; `warm attempt 2 => U+0020 U+0020`; `airplane => U+0020 U+0020`.
- Version note: The visible Settings description promises দাঁড়ি/period on two Space taps.

### F-ANDROID-006 [P1] Airplane-mode voice typing cannot start without an installed offline provider pack
- Area: voice
- Frequency: always (2/2 airplane attempts)
- Repro:
  1. On the API 34 Pixel 7 AVD, grant microphone permission and accept the in-app voice disclosure.
  2. Enable airplane mode and verify “Active default network: none.”
  3. Open Banglu, focus the editor, and tap the visible microphone chip.
  4. Capture provider logcat while waiting for recognition to start.
- Expected: Bangla voice input starts offline, or the UI gives an actionable offline-language-pack error without presenting a nonfunctional listening flow.
- Actual: The network recognizer fails with `ERR_INTERNET_DISCONNECTED`; the offline recognizer fails because the bn-BD pack is absent, so no result can be produced.
- Evidence: `a-voice-03-airplane-stable.png`, `a-voice-04-airplane-repro.png`, `a-voice-logcat.txt` — `07-16 22:00:43.761 RecognitionService#onStartListening`; `07-16 22:00:43.775 NetworkSpeechRecognizer: ... ERR_INTERNET_DISCONNECTED`; `07-16 22:00:43.789 SodaSpeechRecognizer: Failed to get language pack ... error 12`.
- Version note: Environment-specific to an API 34 AVD without the offline bn-BD speech pack; real speech and mixed Bangla/English latency remain untested.

### F-ANDROID-007 [P2] Setup guide does not prepare a novice for Android's second enable confirmation
- Area: install
- Frequency: 1/1 clean-install attempt
- Repro:
  1. Fresh-install the 1.5.34 APK and tap Start, then the Banglu setup/enable control.
  2. In Android keyboard settings, turn on Banglu and accept the first warning.
  3. At the second reboot confirmation, press Back as a novice who assumes the first OK completed enablement.
  4. Inspect the Banglu toggle and return to the guide.
- Expected: The guide tells the user that both Android confirmations must be accepted, or detects the rejected second confirmation and explains the next action.
- Actual: Back dismisses the second confirmation and leaves Banglu disabled; the tester had to retry the toggle without guidance.
- Evidence: `a-install-04-enable-warning.png`, `a-install-07-second-confirmation.png`, `a-install-08-reboot-confirmation.png`, `a-install-tap-log.txt` — `tap 4: first warning accepted`; `tap 5: second confirmation dismissed`; `result: Banglu switch remained off`.
- Version note: Android 14/API 34 system wording and confirmation behavior may differ on other OS versions.

## Golden-set table
| input | expected | got | pass |
|---|---|---|---|
| warm: `kemon acho` | কেমন আছো | কেমন আছো | yes |
| warm: `issa` | ইচ্ছা | ইচ্ছা | yes |
| warm: `korsi` | করছি | করছি | yes |
| warm: `golp` | গল্প | গল্প | yes |
| warm: `bujteparcina` | বুঝতে পারছিনা | বুজ্তেপার্ছিনা | no |
| warm: `ssc` | এসএসসি | এসএসসি | yes |
| warm: `phd` | পিএইচডি | পিএইচডি | yes |
| warm: `otp` | ওটিপি | ওটিপি | yes |
| warm: `callback` | কলব্যাক | preview ছাল্ল্বাছ্ক; Space কলব্যাক | no |
| warm: `motivation` | মোটিভেশন | preview মতিভাতিওন; Space মোটিভেশন | no |
| warm: `kacci` | কাচ্চি | কাচ্চি | yes |
| warm: double-space | `। ` | two U+0020 spaces | no |
| warm: digits `1`–`0` | ১২৩৪৫৬৭৮৯০ | ১২৩৪৫৬৭৮৯০ | yes |
| warm: `assignment` via EN | assignment | assignment | yes |
| 128 MB: `kemon acho` | কেমন আছো | কেমন আছো | yes |
| 128 MB: `issa` | ইচ্ছা | ইচ্ছা | yes |
| 128 MB: `korsi` | করছি | করছি | yes |
| 128 MB: `golp` | গল্প | গল্প | yes |
| 128 MB: `bujteparcina` | বুঝতে পারছিনা | বুজ্তেপার্ছিনা | no |
| 128 MB: `ssc` | এসএসসি | এসএসসি | yes |
| 128 MB: `phd` | পিএইচডি | পিএইচডি | yes |
| 128 MB: `otp` | ওটিপি | ওটিপি | yes |
| 128 MB: `callback` | কলব্যাক | preview ছাল্ল্বাছ্ক; Space কলব্যাক | no |
| 128 MB: `motivation` | মোটিভেশন | preview মতিভাতিওন; Space মোটিভেশন | no |
| 128 MB: `kacci` | কাচ্চি | কাচ্চি | yes |
| 128 MB: double-space | `। ` | two U+0020 spaces | no |
| 128 MB: digits `1`–`0` | ১২৩৪৫৬৭৮৯০ | ১২৩৪৫৬৭৮৯০ | yes |
| 128 MB: `assignment` via EN | assignment | assignment | yes |
| airplane: `kemon acho` | কেমন আছো | কেমন আছো | yes |
| airplane: `issa` | ইচ্ছা | ইচ্ছা | yes |
| airplane: `korsi` | করছি | করছি | yes |
| airplane: `golp` | গল্প | গল্প | yes |
| airplane: `bujteparcina` | বুঝতে পারছিনা | বুজ্তেপার্ছিনা | no |
| airplane: `ssc` | এসএসসি | এসএসসি | yes |
| airplane: `phd` | পিএইচডি | পিএইচডি | yes |
| airplane: `otp` | ওটিপি | ওটিপি | yes |
| airplane: `callback` | কলব্যাক | preview ছাল্ল্বাছ্ক; Space কলব্যাক | no |
| airplane: `motivation` | মোটিভেশন | preview মতিভাতিওন; Space মোটিভেশন | no |
| airplane: `kacci` | কাচ্চি | কাচ্চি | yes |
| airplane: double-space | `। ` | two U+0020 spaces | no |
| airplane: digits `1`–`0` | ১২৩৪৫৬৭৮৯০ | ১২৩৪৫৬৭৮৯০ | yes |
| airplane: `assignment` via EN | assignment | assignment | yes |

## Perf numbers
| measurement | result |
|---|---:|
| Cold MainActivity launch, fresh process | 353 ms |
| Cold MainActivity launch, first after reboot | 621 ms TotalTime / 630 ms WaitTime |
| Cold keyboard until typable | not independently timed |
| 30-second burst | 272 attempted / 272 committed `কেমন`; 0 dropped or stuck |
| Idle Java / native heap | 10,332 / 27,360 KB |
| Burst Java / native heap | 25,360 / 30,244 KB |
| Idle total PSS / RSS | 72,784 / 134,968 KB |
| Burst total PSS / RSS | 90,321 / 161,064 KB |
| Burst janky frames | 286/879 (32.54%), one synthetic run |
| Frame percentiles p50/p90/p95/p99 | 16/16/17/31 ms |
| Temporary low-end profile | 2 GB AVD, heapgrowthlimit 128m; restored to 192m |
| Voice result latency | not measurable without a real speech source |

## Not tested / needs deeper look
- Voice after more than 30 minutes of phone idle; real microphone speech, mixed Bangla/English accuracy, and result latency.
- Incoming-call interruption mid-word.
- Exact cold IME start to first typable keystroke; the measured cold numbers are MainActivity launch times.
- Repeating the synthetic gfx burst to decide whether the 32.54% jank rate is representative of human typing.
- Authenticated Play Store listing, current store screenshots, data-safety answers, and installation from the actual public artifact; this session used the local `releases/banglu-1.5.34-2071-perf.apk`.
- A packet-capture monitor with per-packet process attribution. Airplane mode, no active default network, unchanged/no UID byte history, and a process-scoped network log filter showed no typing-surface traffic.
- Tutorial-card-by-card accuracy beyond the fresh setup path.
