// S47 P3: engine host, cold-start hardened.
//
// MV3 evicts this worker after ~30s idle; the FIRST word after a wake used
// to wait for the full 16MB dictionary parse ("first press hangs, then it's
// free" — real-user report). Now:
//   - the seed engine (rules + 6.5K words + all shorthand) answers
//     IMMEDIATELY — worst case is seed-quality for the first word or two
//   - the slim dictionary attaches in the background and upgrades quality
//   - content scripts pre-warm us on focusin, so by the time a human
//     finishes their first word the full dictionary is usually live
import * as mod from "./vendor/banglu-engine.bundle.js";

const ns = (mod.default ?? mod);
const engine = (ns.com ?? ns).banglu.engine.BangluWebEngine;

engine.initSeed(); // fast, synchronous — part of worker startup

let dictReady = false;
const bootDict = (async () => {
  try {
    const res = await fetch(chrome.runtime.getURL("vendor/banglu-slim.json"));
    engine.attachSlimDictionary(await res.text());
    dictReady = true;
  } catch (e) {
    console.error("banglu: slim dictionary failed to load", e);
  }
})();

chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  if (msg?.type === "ping") {
    // fire-and-forget pre-warm: waking the worker started bootDict already
    sendResponse({ ready: dictReady });
    return; // sync
  }
  if (msg?.type === "word" && typeof msg.word === "string") {
    const word = msg.word.trim();
    sendResponse(word ? {
      primary: engine.convert(word),
      chips: Array.from(engine.suggestions(word, 6)),
      full: dictReady,
    } : { primary: "", chips: [], full: dictReady });
    return; // sync — NEVER blocks on the dictionary
  }
  sendResponse({ error: "unknown" });
});
