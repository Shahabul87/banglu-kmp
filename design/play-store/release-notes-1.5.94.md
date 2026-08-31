# Banglu 1.5.94 (2131) — S151

## bn-BD (Play Console "What's new")
kothai লিখলেই এখন কোথায় (কথাই সাজেশনে থাকে)। hoi/jai-এর মতো শব্দে হই/হয়, যাই/যায় — দুটো পড়াই এখন সাজেশন বারে, আগের শব্দ দেখে সঠিকটা এগিয়ে আসে।

## en-US
kothai now reads কোথায় (কথাই stays a suggestion). Final ই/য় homographs (hoi, jai) keep BOTH readings on the bar, and the context reranker promotes the one your previous words point to.

## Internal
- kothai → কোথায় chat default (one-point seed race documented in the code).
- homograph_twin strip promotion for final-ই/য় pairs (validator freq ≥ 40);
  ই↔য় added to AIDisambiguator SWAP_RULES.
- S149 harness: variant-exact metric (ো-final, ি/ী folds) + WITH-CONTEXT
  pass over rerankWithContext with per-homograph accuracy; delta appended
  to docs/engine-banglish-study-2026-08-30.md.
- Walls green: jvmTest, testDebugUnitTest, jsNodeTest, desktop-app,
  windows-ime.
