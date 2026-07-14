const render = () => chrome.storage.sync.get(["disabledSites"], (r) => {
  const sites = r.disabledSites || [];
  const box = document.getElementById("sites");
  if (!sites.length) { box.innerHTML = '<p class="empty">কোনো সাইট বন্ধ করা নেই।</p>'; return; }
  box.replaceChildren(...sites.map((s) => {
    const row = document.createElement("div"); row.className = "row";
    const name = document.createElement("span"); name.textContent = s;
    const btn = document.createElement("button"); btn.textContent = "চালু করুন";
    btn.onclick = () => chrome.storage.sync.set(
      { disabledSites: sites.filter((x) => x !== s) }, render);
    row.append(name, btn); return row;
  }));
});
render();
