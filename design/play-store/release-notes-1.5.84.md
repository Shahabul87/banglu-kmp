# Release notes — 1.5.84 (2121)

## Play Console "What's new" (≤500 chars)

**বাংলা**
- ভয়েস টাইপিং নতুন করে তৈরি: থামলে বা বিরতি দিলে কথা আর হারায় না, একই বাক্য বারবার বসে না
- প্রতিটি বাক্যের পর কীবোর্ড নিজে নতুন শোনার সেশন শুরু করে — দীর্ঘ মেসেজেও প্রথম বাক্যের মতো দ্রুত ও নির্ভুল
- বিরতি অনুযায়ী কমা ও দাঁড়ি নিজে থেকে বসে; ৩০ সেকেন্ড পর্যন্ত চুপ থাকলেও শোনা চলতে থাকে
- ইমেইল ঠিকানা মনে রাখা ডিফল্টে বন্ধ; ক্লিপবোর্ড ব্যক্তিগত ঘর থেকে কিছু সংরক্ষণ করে না (1.5.83)

**English**
- Voice typing rebuilt around how Google's speech service actually behaves: no dropped speech after a pause, no repeated sentences
- A fresh listening session per sentence — long messages stay as fast and accurate as the first sentence
- Commas and দাঁড়ি follow your pauses; listening survives up to ~30 s of silence
- Saved-email memory off by default; clipboard never stores from private fields (1.5.83)

## Internal

- Round: S137 — root-caused from on-device traces (`docs/audits/voice-trace-s137-2026-08-26.log`, SM-S901W, Google speech service 20260720):
  1. Google ends a paused session with an EMPTY final → was ERROR, now the no-speech ladder.
  2. Google starts a fresh hypothesis after its own endpoint (and sometimes mid-session) → the word-count strip ate new segments; now `VoiceCarryPolicy.reconcile` detects resets (fuzzy overlap, previous-hypothesis comparison, new-beginning signal) and the IME seals the previous live region (mark only, never re-committing text).
  3. Later utterances inside one session degrade (late lumps, empty hypotheses) → one session per utterance: idle stop 1.5 s after speech, 250 ms settle (no-delay restarts produced ERROR_SERVER_DISCONNECTED), deferred comma/দাঁড়ি stamping, retry budget reset per healthy session, silence cap 6.
- Pin flipped with decision note: S121 "same-length zero-overlap owes nothing" → reset (VoiceCarryPolicyTest).
