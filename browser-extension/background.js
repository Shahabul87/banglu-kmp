// S47 P1: the engine host. One engine + slim dictionary per browser profile
// (not per tab). Content scripts and the popup talk to it via messages:
//   {type:"word", word}  -> {primary, chips: string[]}
//   {type:"ping"}        -> {ready: boolean}
import * as mod from "./vendor/banglu-engine.bundle.js";

const ns = (mod.default ?? mod);
const engine = (ns.com ?? ns).banglu.engine.BangluWebEngine;

let ready = false;
const boot = (async () => {
  engine.initSeed();
  const res = await fetch(chrome.runtime.getURL("vendor/banglu-slim.json"));
  engine.attachSlimDictionary(await res.text());
  ready = true;
})();

chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  (async () => {
    if (msg?.type === "ping") {
      sendResponse({ ready });
      return;
    }
    if (msg?.type === "word" && typeof msg.word === "string") {
      await boot; // seed answers are fine, but wait for full dict once booting
      const word = msg.word.trim();
      if (!word) { sendResponse({ primary: "", chips: [] }); return; }
      sendResponse({
        primary: engine.convert(word),
        chips: Array.from(engine.suggestions(word, 6)),
      });
      return;
    }
    sendResponse({ error: "unknown" });
  })();
  return true; // async sendResponse
});
