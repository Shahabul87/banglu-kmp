# AI সংশোধন — DeepSeek document review in the banglu-web dashboard editor

**Date:** 2026-07-22 · **Surface:** banglu-web `/dashboard/editor` · **Round:** S61

## Decisions (user-approved)

- **Apply mode:** propose + accept by default, with a settings toggle
  ("স্বয়ংক্রিয় সংশোধন", off by default) that auto-applies all corrections.
- **Trigger:** on demand only — a button click or AI-panel chip. No background
  calls; document text goes to DeepSeek only when the user explicitly runs a
  review. The keystroke path stays 100% offline (brand law).
- **Scope:** spelling + grammar. No style rewrites.

## Architecture

### 1. API — `app/api/ai/review/route.ts`

POST `{ text: string }` (Zod-validated, non-empty, capped at 8,000 chars —
callers chunk longer documents). Prompts DeepSeek (same `DEEPSEEK_API_KEY`,
same model as `/api/ai/chat`) to return strict JSON:

```json
{ "corrections": [ { "wrong": "পরিসংক্ষান", "corrected": "পরিসংখ্যান", "reason": "বানান ভুল" } ] }
```

`wrong` must be the exact text as it appears in the document (word or short
phrase — phrase-level entries make grammar fixes ride the same mechanism).
Parsing is defensive: strip code fences, retry once on malformed JSON, then
return a typed error. GET returns the same health shape as the chat route.

### 2. Editor — in-place proposal marks (no document mutation until accept)

- `runReview()` in `EditorClient` — callable from a **রিভিউ করুন** ribbon
  button (AI tab) and a quick-action chip in the existing AIPanel.
- Pure logic in `app/dashboard/editor/lib/aiReview.ts`:
  `extractCorrections(raw)` (model-output JSON extraction) and
  `locateCorrections(docText, corrections)` (exact-match positions;
  unlocatable corrections — AI hallucinations — are silently dropped).
- A small TipTap extension renders ProseMirror **decorations** (amber
  underline, both themes) over located ranges — decorations, not marks, so
  the document and undo history stay untouched until the user accepts.
- Clicking a highlighted word opens a popover: corrected word, reason in
  Bengali, ✓ গ্রহণ / ✕ বাতিল.
- A floating bar shows "N টি সংশোধন পাওয়া গেছে — সব ঠিক করুন / বাতিল".
- Accept = TipTap transaction replacing the located range → undo always works.
- After any accept/doc change, remaining pending corrections re-locate; ones
  no longer present are dropped.

### 3. Toggle

Editor settings gain "স্বয়ংক্রিয় সংশোধন" (localStorage, default off). When
on, `runReview()` applies every located correction immediately (single
undo-able transaction batch) and toasts "N টি শব্দ ঠিক করা হয়েছে".

### 4. Chat insert

Already shipped (AIPanel insert/replace buttons) — untouched.

## Failure modes

- Key missing → friendly Bengali message (mirror chat route's not_configured).
- DeepSeek timeout/error → "আবার চেষ্টা করুন" with retry button.
- Zero corrections → "কোনো ভুল পাওয়া যায়নি ✓".
- Malformed model JSON → one retry, then error state; never crash the editor.

## Testing

- Jest units on `aiReview.ts` (extraction from fenced/prefixed model output,
  location including duplicates and overlaps, drop-on-missing).
- `tsc`, lint, production build.
- Final visual verification is the user's (page is auth-gated).
