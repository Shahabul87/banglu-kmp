# Release notes — 1.5.85 (2122)

## Play Console "What's new" (≤500 chars)

**বাংলা**
- ক্লিপবোর্ড ইতিহাস এখন আপনার ইচ্ছায় (ডিফল্টে বন্ধ); ব্যক্তিগত ঘরে শুধু বর্তমান কপি এক ট্যাপে বসে
- ভয়েস টাইপিং নতুন করে তৈরি: বিরতির পর কথা হারায় না, বাক্য বারবার বসে না, কমা-দাঁড়ি নিজে বসে (1.5.84)
- শেখা ডেটা মুছে ফেলা আরও নিশ্চিত

**English**
- Clipboard history is now opt-in (off by default); private fields get only a one-tap paste of the current clip
- Voice typing rebuilt: no dropped speech after pauses, no repeated sentences, automatic commas/দাঁড়ি (1.5.84)
- Learned-data deletion made fully serialized

## Internal

- Round: S138 — second re-audit follow-up: clipboard display/opening gated on every private field + opt-in `clipboard_history`; identity migration writes the preference only after a durable purge; `learningLock` serializes every learning mutation with the erase; onDestroy cancels + joins (300 ms) before closing SQLite; validator refuses a version tag at another commit and proves the artifact certificate == configured keystore; device smoke fails on an inconclusive jank sample and drives a 26-key burst; instrumented test clicks Space/number/Backspace/long-press action and deletes emoji families/flags through the real InputConnection; store listing keystore path fixed, battery claim removed.
