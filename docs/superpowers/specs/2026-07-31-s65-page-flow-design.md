# S65 — Real page flow in the banglu-web editor (Word-like pagination)

**Date:** 2026-07-31 · **Status:** approved (user: pages-by-default + build it)

## Problem

The editor's print layout renders ONE white page box that stretches forever.
Long documents spill past the visual page edge with no page separation
(tester screenshot 2026-07-31). Page size (A4/Letter/Legal/A5) and margins
(presets + custom inches) already exist in ফরম্যাট → পৃষ্ঠা সেটআপ, but feel
dead because nothing ever paginates, and print hardcodes 20mm margins.

## Design

1. **PaginationExtension** (TipTap/ProseMirror, decoration-based, banglu-web
   `app/dashboard/editor/extensions/PaginationExtension.ts`):
   - Pure break computation in `lib/pagination.ts`:
     `computePageBreaks(blocks: {top,height}[], pageContentHeight)` →
     `{beforeIndex, filler}[]`. Blocks never straddle a boundary; a block
     taller than one page overflows (accepted Word-web limit).
   - Plugin measures top-level block DOM via `view.nodeDOM`, converts to
     "natural" coordinates by subtracting the heights of its own spacers and
     dividing by the real scale factor (page rect ÷ nominal width — robust
     under CSS `zoom`), computes desired breaks, and dispatches them as
     widget decorations only when they differ (stability loop, converges).
   - Spacer widget = filler + bottom-margin + 24px gray gap + top-margin,
     bleeding full page width via negative horizontal margins (margins are
     ProseMirror padding). Recomputes on doc change (rAF-coalesced), resize,
     and settings meta (page size / margins / zoom / font).
2. **Settings honored end-to-end:** page dims + margins from the store drive
   the pagination; `usePagination` counts real spacers instead of the
   hardcoded 120px estimate.
3. **Print parity:** dynamic `@page { size; margin }` injected from settings;
   print CSS stops hardcoding 20mm padding; spacers `display:none` in print.
4. **Pages by default on desktop:** store default `viewLayout: 'printLayout'`
   with a persist `version: 1` migration flipping existing stored prefs once;
   effective layout falls back to continuous on <768px viewports (a 794px
   page is unusable on phones).

## Out of scope

Splitting a single oversized paragraph across pages; per-page header/footer
cloning; section breaks / mixed page sizes in one document.

## Tests

Jest unit tests on `computePageBreaks` (fit, overflow, exact-fit boundary,
oversized block, stability under re-measure with spacers present).
Gates: tsc, eslint, jest, production build.
