# S157 — tutorial letter-card research (2026-08-31)

Method: every Bengali word in the dictionary starting with (or, for
ণ/ঞ/ঙ/ঠ/ঢ/ড়/ঢ়/য়/ৎ/ং/ঁ, containing) each letter, conjunct-bearing,
frequency-ranked, was reverse-transliterated to its canonical roman
(`ReverseTransliterator.reverseWord`) and run through the REAL engine
(`S157BulkProbeJvm`): convert + 6-slot suggestions on ./dictionary.sqlite.

- `tutorial-letter-words-s157.tsv`: 3,400+ verified rows —
  letter, word, freq, roman, verdict (TOP1/TOP6/MISS), engine primary.
  Overall: 3,359 top-1, 67 top-6, 18 misses.
- The in-app curriculum (`shared/.../TutorialWords.kt`) is the hand-curated
  cut: 308 words / 341 variant pairs across 9 families (স্বরবর্ণ, the five
  বর্গs, অন্তঃস্থ+হ, উষ্ম, বিশেষ), 2-10 words per letter, diversified by
  conjunct cluster, chat variants added where attested (issa, somossa,
  bissobiddaloy…), syllable splits whose concatenation always equals the
  roman, and twin words (pore→পরে/পড়ে, taka→টাকা/তাকা, ঈগল, ঊষা,
  যন্ত্রণা ণ/ন, ভঙ্গি/ভগ্নি) declared with the engine's real primary.
- Pin wall: `S157TutorialWordsJvmTest` — every advertised variant must
  behave exactly as displayed; twins must keep the declared primary AND
  the twin on the strip. A future flip is a documented decision, not a
  silent edit (CLAUDE.md invariant 13/7).
- Probes kept for reuse: `S157BulkProbeJvm` (env-gated bulk),
  `S157VowelProbeJvm` (env-gated TSV probe).
