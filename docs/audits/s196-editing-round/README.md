# S196 editing round — evidence (2026-09-06)

Device before (S22, 1.5.128): backspace through converted words
- tomader: তোমাদের → তোমাদে → তোমা → তো → ত → ∅ (two letters per step)
- bishwabiddaloy: বিশ্ববিদ্যালয় → বিশ্ববিদ্যাল → বিশ্ববিদ্যা → বিশওয়বিদ্য → বিশওয়বিদ → বিশ্ববি → বিশওয়ব → বিশ্ব …
- kotha: কথা → ক → ∅;  amader: আমাদের → আমাদে → আমাদ → আমা → আম → আ → ∅
- hold delete 2.0 s: 33 chars cleared (already fast)
- বাদ, caret after বা, hold c → বাঁদ, marker → বাঁদK (caret at the END — the bug)
- চাঁদ, ←←, marker → Kচাঁদ (correct: চাঁ is one cluster)

Emulator after (Pixel 7, 1.5.129): see delete_test.py / delete_regress.py output in the commit message.
z words: z_device.py (full profile: `adb root; setprop dalvik.vm.heapgrowthlimit 512m`).
