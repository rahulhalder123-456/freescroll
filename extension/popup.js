const DEFAULTS = {
  enabled: true, gate: true, gateSeconds: 10, gateValidMin: 20, scrollLimit: 8, breakMin: 5,
  gray: false, calm: true, streaks: true, typing: true, stories: true, feed: true, counts: true,
};
const ROWS = [
  ["enabled", "FreeScroll on", "bool", ""],
  ["scrollLimit", "Stop after N screens (trap 1)", "num", "0 = never"],
  ["breakMin", "Break length, minutes", "num", ""],
  ["calm", "Grey red badges (trap 2)", "bool", ""],
  ["streaks", "Hide streaks (trap 3)", "bool", ""],
  ["typing", "Hide typing dots (trap 4)", "bool", ""],
  ["gray", "Greyscale pages (trap 5)", "bool", "less pull from colour"],
  ["feed", "Hide recommendations/Reels (trap 6)", "bool", ""],
  ["stories", "Hide stories (trap 7)", "bool", ""],
  ["counts", "Hide like/follower counts (trap 8)", "bool", ""],
  ["gate", "Intention gate (trap 9)", "bool", ""],
  ["gateSeconds", "Gate wait, seconds", "num", ""],
];
chrome.storage.local.get(Object.keys(DEFAULTS), (st) => {
  const cur = { ...DEFAULTS, ...st };
  const box = document.getElementById("opts");
  for (const [k, label, type, hint] of ROWS) {
    const l = document.createElement("label");
    l.innerHTML = `<span>${label}${hint ? `<span class="hint">${hint}</span>` : ""}</span>`;
    const i = document.createElement("input");
    if (type === "bool") { i.type = "checkbox"; i.checked = !!cur[k]; i.onchange = () => chrome.storage.local.set({ [k]: i.checked }); }
    else { i.type = "number"; i.min = 0; i.value = cur[k]; i.onchange = () => chrome.storage.local.set({ [k]: Math.max(0, +i.value || 0) }); }
    l.appendChild(i); box.appendChild(l);
  }
  const n = document.createElement("small");
  n.textContent = "Trap 10 (buzz): notification prompts are always blocked.";
  box.appendChild(n);
});
