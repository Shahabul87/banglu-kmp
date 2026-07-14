// S47 P0: popup converter. Engine loads directly in the popup (short-lived
// document; SW host arrives with P1 when content scripts need shared state).
// Same override semantics as banglu-web /type.

import * as mod from "./vendor/banglu-engine.bundle.js";

// esbuild wraps the UMD module: exports may sit on default or the namespace.
const ns = (mod.default ?? mod);
const engine = (ns.com ?? ns).banglu.engine.BangluWebEngine;
const $ = (id) => document.getElementById(id);
const input = $("in"), out = $("out"), chipsEl = $("chips"),
      status = $("status"), copyBtn = $("copy");

let overrides = Object.create(null);
let ready = false;

function render() {
  if (!ready) return;
  const parts = input.value.split(/(\s+)/);
  let ti = 0;
  out.textContent = parts.map((p) => {
    if (p === "" || /^\s+$/.test(p)) return p;
    const key = ti + ":" + p.trim().toLowerCase();
    ti++;
    return overrides[key] ?? engine.convert(p);
  }).join("") || "…";
  const last = parts.length ? parts[parts.length - 1].trim() : "";
  renderChips(last ? engine.suggestions(last, 6) : []);
}

function renderChips(list) {
  chipsEl.replaceChildren(...list.map((c) => {
    const b = document.createElement("button");
    b.textContent = c;
    b.onclick = () => {
      const tokens = input.value.split(/\s+/).filter(Boolean);
      if (!tokens.length) return;
      overrides[(tokens.length - 1) + ":" + tokens[tokens.length - 1].toLowerCase()] = c;
      if (!input.value.endsWith(" ")) input.value += " ";
      input.focus();
      input.setSelectionRange(input.value.length, input.value.length);
      render();
    };
    return b;
  }));
}

input.addEventListener("input", render);
copyBtn.addEventListener("click", async () => {
  await navigator.clipboard.writeText(out.textContent === "…" ? "" : out.textContent);
  copyBtn.textContent = "✓ কপি হয়েছে";
  setTimeout(() => (copyBtn.textContent = "কপি"), 1400);
});

(async () => {
  engine.initSeed();
  ready = true;
  status.textContent = "সিড ইঞ্জিন";
  render();
  const url = (globalThis.chrome?.runtime?.getURL)
    ? chrome.runtime.getURL("vendor/banglu-slim.json")
    : "./vendor/banglu-slim.json"; // dev mode: served over http
  const res = await fetch(url);
  engine.attachSlimDictionary(await res.text());
  status.textContent = "পূর্ণ অভিধান ✓";
  render();
})();
