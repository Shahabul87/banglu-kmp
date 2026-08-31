# Banglu 1.5.96 (2133) — S153

## bn-BD (Play Console "What's new")
ইংরেজি শব্দ এখন আরও নিখুঁত: but → বাট, phone → ফোন, number → নাম্বার, tutorial → টিউটোরিয়াল, config → কনফিগ — চ্যাটে মানুষ যেসব ইংরেজি শব্দ লেখে, ৯০০+ শব্দের কর্পাস-গবেষণা থেকে সবগুলোর সঠিক বাংলা রূপ।

## en-US
The English register got a general overhaul: 923 corpus-mined English words Bangladeshis actually type were diffed against their majority Bengali renderings — but → বাট, phone → ফোন, number → নাম্বার, tutorial → টিউটোরিয়াল, id → আইডি and ~80 more fixes across curated loans, chat defaults, and initialisms.

## Internal
- Method: english-register.tsv mined from BanglaTLit/Vashantor gold pairs
  (≥5 occurrences, ≥60% majority); engine diffed; 290 misses triaged into
  curated seeds (~56), shorthand chat defaults (~28, incl. documented
  S81/S142 phone pin flips), acronym Tier P (8; id reversal 484:2, eid→ঈদ).
- Corpus-majority spelling flips: data→ডাটা, account→একাউন্ট, address→এড্রেস.
- Trap fixes: stale "sms"→এসেমেস duplicate later in ACRONYM_OVERRIDES (the
  S52 phd lesson again); nc swallowed by the trailing-c hasanta rule →
  pre-rule whitelist.
- Pins: S153EnglishRegisterJvmTest (83 corpus-majority pairs).
