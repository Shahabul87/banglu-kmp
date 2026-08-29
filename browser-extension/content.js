// S47 P1: inline Banglish -> Bangla in <input type=text|search> and
// <textarea>. Semantics (Google-Input-Tools style, predictable):
//   - type a roman word, press SPACE -> word converts + space
//   - suggestion strip floats under the field while typing; click = commit
//   - Alt+B toggles per-site (persisted); everything runs locally.
(() => {
  const HOST_KEY = location.hostname || "local";
  let enabled = true;

  chrome.storage?.local.get(["disabledSites"], (r) => {
    enabled = !(r.disabledSites || []).includes(HOST_KEY);
    if (!enabled) strip.hide();
  });

  const isValueEditable = (el) =>
    el instanceof HTMLTextAreaElement ||
    (el instanceof HTMLInputElement && /^(text|search)$/.test(el.type));

  // P2: rich editors (Gmail compose, Facebook, WhatsApp Web) are
  // contenteditable trees — resolve the editable ROOT from any inner node.
  const ceRoot = (node) => {
    let el = node instanceof Element ? node : node?.parentElement;
    while (el) {
      if (el.isContentEditable && (!el.parentElement || !el.parentElement.isContentEditable)) return el;
      el = el.parentElement;
    }
    return null;
  };
  const isEditable = (el) => isValueEditable(el) || !!(el instanceof Node && ceRoot(el));

  const nativeSet = (el, value) => {
    const proto = el instanceof HTMLTextAreaElement
      ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(proto, "value").set.call(el, value);
    el.dispatchEvent(new InputEvent("input", { bubbles: true }));
  };

  const ask = (word) => new Promise((resolve) => {
    // P3: a keystroke must never hang on the service worker. If the reply
    // takes longer than a human notices, commit the roman word untouched —
    // the user keeps typing and can retype that one word.
    const fallback = setTimeout(() => resolve({ primary: word, chips: [] }), 900);
    try {
      chrome.runtime.sendMessage({ type: "word", word }, (r) => {
        clearTimeout(fallback);
        resolve(r && !chrome.runtime.lastError ? r : { primary: word, chips: [] });
      });
    } catch { clearTimeout(fallback); resolve({ primary: word, chips: [] }); }
  });

  // S141: next-word predictions for the two Bengali words before the caret.
  const askNext = (prev2, prev1) => new Promise((resolve) => {
    const fallback = setTimeout(() => resolve([]), 600);
    try {
      chrome.runtime.sendMessage({ type: "next", prev2, prev1 }, (r) => {
        clearTimeout(fallback);
        resolve(r && !chrome.runtime.lastError && Array.isArray(r.chips) ? r.chips : []);
      });
    } catch { clearTimeout(fallback); resolve([]); }
  });
  const tellUsed = (prev, next) => {
    try { chrome.runtime.sendMessage({ type: "used", prev, next }, () => void chrome.runtime.lastError); } catch {}
  };

  // Pre-warm the worker the moment an editable gets focus, so the full
  // dictionary is live before the first word finishes.
  document.addEventListener("focusin", (e) => {
    if (!isEditable(e.target)) return;
    try { chrome.runtime.sendMessage({ type: "ping" }, () => void chrome.runtime.lastError); } catch {}
  }, true);

  // ---- suggestion strip (shadow DOM so site CSS can't break it) ----
  const strip = (() => {
    const host = document.createElement("banglu-strip");
    host.style.cssText = "position:fixed;z-index:2147483647;display:none;";
    const root = host.attachShadow({ mode: "open" });
    const style = document.createElement("style");
    style.textContent = `
      .bar{display:flex;gap:6px;padding:6px 8px;background:#0d1524;
        border:1px solid #1e3a5f;border-radius:12px;font-family:system-ui;
        box-shadow:0 6px 24px rgba(0,0,0,.45)}
      button{border-radius:999px;border:1px solid rgba(14,165,233,.35);
        background:rgba(14,165,233,.16);color:#bae6fd;padding:4px 12px;
        font-size:14px;cursor:pointer;white-space:nowrap}
      button:hover{background:rgba(14,165,233,.35)}
      .off{color:#64748b;font-size:11px;align-self:center;padding:0 4px}
      .next{color:#64748b;font-size:11px;align-self:center;padding:0 4px;white-space:nowrap}
      button.pred{border-color:rgba(59,130,246,.35);background:rgba(59,130,246,.14);color:#bfdbfe}
      button.pred:hover{background:rgba(59,130,246,.32)}`;
    const bar = document.createElement("div");
    bar.className = "bar";
    root.append(style, bar);
    document.documentElement.appendChild(host);
    return {
      show(rect, chips, onPick, predictions = false) {
        if (!chips.length) return this.hide();
        const items = chips.map((c) => {
          const b = document.createElement("button");
          b.textContent = c;
          if (predictions) b.className = "pred";
          // mousedown so the field never loses focus
          b.addEventListener("mousedown", (e) => { e.preventDefault(); onPick(c); });
          return b;
        });
        if (predictions) {
          const label = document.createElement("span");
          label.className = "next";
          label.textContent = "পরবর্তী →";
          items.unshift(label);
        }
        bar.replaceChildren(...items);
        const r = rect;
        host.style.left = Math.max(8, r.left) + "px";
        host.style.top = Math.min(window.innerHeight - 54, r.bottom + 6) + "px";
        host.style.display = "block";
      },
      hide() { host.style.display = "none"; bar.replaceChildren(); },
    };
  })();

  // ---- word tracking + commit: two backends -------------------------
  const wordBefore = (el) => {
    if (isValueEditable(el)) {
      const caret = el.selectionStart ?? 0;
      const m = el.value.slice(0, caret).match(/[a-zA-Z]+$/);
      return m ? { kind: "value", el, word: m[0], start: caret - m[0].length, end: caret } : null;
    }
    // contenteditable: word is the roman tail of the caret's text node
    const sel = window.getSelection();
    if (!sel || !sel.isCollapsed || sel.rangeCount === 0) return null;
    const r = sel.getRangeAt(0);
    const node = r.startContainer;
    if (node.nodeType !== Node.TEXT_NODE) return null;
    const upto = node.textContent.slice(0, r.startOffset);
    const m = upto.match(/[a-zA-Z]+$/);
    if (!m) return null;
    return {
      kind: "ce", el, word: m[0], node,
      start: r.startOffset - m[0].length, end: r.startOffset,
    };
  };

  const commit = (w, text) => {
    if (w.kind === "value") {
      const el = w.el, v = el.value;
      nativeSet(el, v.slice(0, w.start) + text + v.slice(w.end));
      const pos = w.start + text.length;
      el.setSelectionRange(pos, pos);
    } else {
      // Select the roman word, then insertText — execCommand keeps the
      // editor's own undo stack and fires the events rich editors expect.
      const sel = window.getSelection();
      const range = document.createRange();
      try {
        range.setStart(w.node, w.start);
        range.setEnd(w.node, w.end);
      } catch { return; }
      sel.removeAllRanges();
      sel.addRange(range);
      document.execCommand("insertText", false, text);
    }
    strip.hide();
  };

  // S141: the two Bengali words before the caret (prev2, prev1) — the
  // prediction context — or null when the caret is not right after a word.
  const bengaliBefore = (el) => {
    let text = null;
    if (isValueEditable(el)) {
      text = el.value.slice(0, el.selectionStart ?? 0);
    } else {
      const sel = window.getSelection();
      if (!sel || !sel.isCollapsed || sel.rangeCount === 0) return null;
      const r = sel.getRangeAt(0);
      if (r.startContainer.nodeType !== Node.TEXT_NODE) return null;
      text = r.startContainer.textContent.slice(0, r.startOffset);
    }
    if (text === null || !/[\u0980-\u09FF][\u0980-\u09FF\u200C\u200D]* $/.test(text)) return null;
    const words = text.trim().split(/[\s।,!?;:"'()\-]+/).filter((w) => /[\u0980-\u09FF]/.test(w));
    if (!words.length) return null;
    return { prev1: words[words.length - 1], prev2: words[words.length - 2] ?? "" };
  };

  // Insert a prediction (word + space) at the caret, then chain the next bar.
  const insertPrediction = (el, word) => {
    if (isValueEditable(el)) {
      const pos = el.selectionStart ?? el.value.length;
      nativeSet(el, el.value.slice(0, pos) + word + " " + el.value.slice(pos));
      el.setSelectionRange(pos + word.length + 1, pos + word.length + 1);
    } else {
      document.execCommand("insertText", false, word + " ");
    }
  };

  const showPredictions = (el) => {
    const ctx = bengaliBefore(el);
    if (!ctx) return strip.hide();
    askNext(ctx.prev2, ctx.prev1).then((chips) => {
      // stale guard: the caret must still sit right after the same word
      const now = bengaliBefore(el);
      if (!now || now.prev1 !== ctx.prev1 || document.activeElement !== el && !el.contains(document.activeElement)) return;
      const w = { kind: isValueEditable(el) ? "value" : "ce", el };
      strip.show(anchorRect(w), chips, (chip) => {
        insertPrediction(el, chip);
        tellUsed(ctx.prev1, chip);
        showPredictions(el);
      }, true);
    });
  };

  // strip anchor: caret rect for CE (fields are huge), element rect otherwise
  const anchorRect = (w) => {
    if (w.kind === "ce") {
      const sel = window.getSelection();
      if (sel && sel.rangeCount) {
        const rr = sel.getRangeAt(0).cloneRange().getBoundingClientRect();
        if (rr && (rr.width || rr.height || rr.top)) return rr;
      }
    }
    return w.el.getBoundingClientRect();
  };

  document.addEventListener("keydown", (e) => {
    // Alt+B: per-site toggle
    if (e.altKey && (e.code === "KeyB")) {
      e.preventDefault();
      enabled = !enabled;
      strip.hide();
      chrome.storage?.local.get(["disabledSites"], (r) => {
        const set = new Set(r.disabledSites || []);
        enabled ? set.delete(HOST_KEY) : set.add(HOST_KEY);
        chrome.storage.local.set({ disabledSites: [...set] });
      });
      return;
    }
    if (!enabled) return;
    const el = e.target;
    if (!isEditable(el)) return;
    if (e.key !== " ") return;
    // S77: Shift+Space keeps the word as typed English — skip conversion
    // and let the browser insert the space itself (parity with the web
    // editor and desktop app).
    if (e.shiftKey) { strip.hide(); return; }
    const w = wordBefore(el);
    if (!w) return;
    e.preventDefault();
    ask(w.word).then(({ primary }) => { commit(w, primary + " "); showPredictions(el); });
  }, true);

  document.addEventListener("input", (e) => {
    if (!enabled) return;
    const el = e.target;
    if (!isEditable(el) || !(e instanceof InputEvent) || !e.isTrusted) return;
    const w = wordBefore(el);
    if (!w) return strip.hide();
    ask(w.word).then(({ chips }) => {
      // stale guard: only show if the word is still the tail
      const now = wordBefore(el);
      if (!now || now.word !== w.word) return;
      strip.show(anchorRect(now), chips, (chip) => {
        // recompute at click time — CE ranges go stale across edits
        const fresh = wordBefore(now.el) ?? now;
        commit(fresh, chip + " ");
        showPredictions(now.el);
      });
    });
  }, true);

  document.addEventListener("focusout", () => strip.hide(), true);
  window.addEventListener("scroll", () => strip.hide(), true);
})();
