(() => {
  const DEFAULTS = {
    enabled: true, gate: true, gateSeconds: 10, gateValidMin: 20,
    scrollLimit: 8, breakMin: 5,
    gray: false, calm: true, streaks: true, typing: true, stories: true, feed: true, counts: true,
  };
  const host = location.hostname.replace(/^www\./, "");
  const site = host.includes("youtube") ? "youtube" : host.includes("instagram") ? "instagram"
    : host.includes("facebook") ? "facebook" : host.includes("tiktok") ? "tiktok"
    : host === "x.com" || host.includes("twitter") ? "x" : host.includes("reddit") ? "reddit"
    : host.includes("snapchat") ? "snapchat" : "linkedin";
  const key = (k) => `fs:${site}:${k}`;
  const root = document.documentElement;
  let S = { ...DEFAULTS };

  const get = (keys) => new Promise((r) => chrome.storage.local.get(keys, r));
  const set = (o) => chrome.storage.local.set(o);

  function applyClasses() {
    const on = S.enabled;
    root.classList.toggle("fs-" + site, true);
    for (const k of ["gray", "streaks", "typing", "stories", "feed", "counts"]) {
      root.classList.toggle("fs-" + k, on && S[k]);
    }
  }

  // ---- Trap 2: red badges -> grey, and "(3)" removed from tab title
  function calmBadges() {
    if (!(S.enabled && S.calm)) return;
    document.title = document.title.replace(/^\(\d+\+?\)\s*/, "");
    const cand = document.querySelectorAll('[aria-label*="notif" i] *, [class*="badge" i], [data-testid*="badge" i], [class*="count" i], [aria-label*="unread" i] *');
    let n = 0;
    for (const el of cand) {
      if (++n > 400) break;
      if (el.classList.contains("fs-calm") || el.textContent.length > 4) continue;
      const m = getComputedStyle(el).backgroundColor.match(/\d+/g);
      if (m && +m[0] > 170 && +m[1] < 100 && +m[2] < 100) el.classList.add("fs-calm");
    }
  }

  // ---- overlay helpers
  function overlay(html) {
    document.getElementById("fs-overlay")?.remove();
    const o = document.createElement("div");
    o.id = "fs-overlay";
    o.innerHTML = html;
    document.documentElement.appendChild(o);
    return o;
  }
  const leave = () => { location.href = "about:blank"; };

  // ---- Trap 9: intention gate (adds friction before the easy dopamine)
  async function gate() {
    if (!(S.enabled && S.gate)) return;
    const st = await get([key("passUntil"), key("intent")]);
    if ((st[key("passUntil")] || 0) > Date.now()) return showPill(st[key("intent")]);
    const o = overlay(`
      <h1>Pause. Why are you here?</h1>
      <p>This app is built to be effortless to open and hard to close. Say what you came for, then decide.</p>
      <input id="fs-intent" placeholder="e.g. reply to Sara, watch one tutorial" maxlength="120" autofocus>
      <div class="fs-row"><button id="fs-go" disabled>Wait ${S.gateSeconds}s</button>
      <button class="fs-secondary" id="fs-leave">Leave</button></div>`);
    const go = o.querySelector("#fs-go"), inp = o.querySelector("#fs-intent");
    let left = S.gateSeconds, done = false;
    const refresh = () => {
      const ok = left <= 0 && inp.value.trim().length >= 3;
      go.disabled = !ok;
      go.textContent = left > 0 ? `Wait ${left}s` : ok ? "Enter" : "Type your reason";
    };
    const t = setInterval(() => { if (left > 0) left--; refresh(); if (left <= 0) clearInterval(t); }, 1000);
    inp.addEventListener("input", refresh);
    go.onclick = async () => {
      if (go.disabled || done) return; done = true;
      await set({ [key("passUntil")]: Date.now() + S.gateValidMin * 60000, [key("intent")]: inp.value.trim() });
      o.remove(); showPill(inp.value.trim());
    };
    o.querySelector("#fs-leave").onclick = leave;
    refresh();
  }
  function showPill(text) {
    if (!text) return;
    document.getElementById("fs-pill")?.remove();
    const p = document.createElement("div");
    p.id = "fs-pill"; p.textContent = "Here to: " + text;
    document.documentElement.appendChild(p);
  }

  // ---- Break lock after hitting the stopping point
  async function checkBreak() {
    const st = await get([key("breakUntil")]);
    const until = st[key("breakUntil")] || 0;
    if (until <= Date.now()) return false;
    const o = overlay(`<h1>Break time</h1><p id="fs-left"></p><button class="fs-secondary" id="fs-close">Close tab</button>`);
    const upd = () => {
      const s = Math.max(0, Math.round((until - Date.now()) / 1000));
      o.querySelector("#fs-left").textContent = `Come back in ${Math.floor(s / 60)}m ${s % 60}s. Stretch, drink water, look outside.`;
      if (s <= 0) { o.remove(); location.reload(); }
    };
    upd(); setInterval(upd, 1000);
    o.querySelector("#fs-close").onclick = leave;
    return true;
  }

  // ---- Trap 1: stopping point after N screens of scrolling
  let travelled = 0, lastY = 0, stopped = false;
  function trackScroll() {
    const vh = () => window.innerHeight || 800;
    const add = (px) => {
      if (stopped || !(S.enabled && S.scrollLimit > 0)) return;
      travelled += Math.max(0, px);
      if (travelled > S.scrollLimit * vh()) stop();
    };
    window.addEventListener("scroll", () => { const y = scrollY; add(y - lastY); lastY = y; }, { passive: true });
    window.addEventListener("wheel", (e) => add(e.deltaY), { passive: true }); // inner-scroll feeds (TikTok, Reels)
    window.addEventListener("keydown", (e) => { if (["ArrowDown", "PageDown", " "].includes(e.key) && !/INPUT|TEXTAREA/.test(e.target.tagName) && !e.target.isContentEditable) add(vh() * 0.5); });
  }
  function stop() {
    stopped = true;
    const o = overlay(`<h1>You've reached the end.</h1>
      <p>You scrolled ${S.scrollLimit} screens. The feed would never end on its own, so this is your stopping point.</p>
      <div class="fs-row"><button id="fs-break">Take a ${S.breakMin}-min break</button>
      <button class="fs-secondary" id="fs-more">Continue (${Math.ceil(S.scrollLimit / 2)} more screens)</button></div>`);
    o.querySelector("#fs-break").onclick = async () => { await set({ [key("breakUntil")]: Date.now() + S.breakMin * 60000 }); location.reload(); };
    o.querySelector("#fs-more").onclick = () => { o.remove(); travelled = Math.max(0, S.scrollLimit - Math.ceil(S.scrollLimit / 2)) * (window.innerHeight || 800); stopped = false; };
  }

  // ---- Trap 6: YouTube Shorts -> normal player (no swipe-to-next)
  function shortsRedirect() {
    if (!(S.enabled && S.feed) || site !== "youtube") return;
    const m = location.pathname.match(/^\/shorts\/([\w-]+)/);
    if (m) location.replace(`/watch?v=${m[1]}`);
  }
  function blockReelsPages() {
    if (!(S.enabled && S.feed)) return;
    const p = location.pathname;
    if ((site === "instagram" && /^\/(reels|explore)\b/.test(p)) || (site === "facebook" && /^\/(reel|watch)\/?$/.test(p))) {
      overlay(`<h1>Recommendation feeds are off</h1><p>FreeScroll blocks Reels/Explore. Go to a person you chose to follow instead.</p>
        <button id="fs-b">Back</button>`).querySelector("#fs-b").onclick = () => history.back();
    }
  }

  async function init() {
    const st = await get(Object.keys(DEFAULTS));
    S = { ...DEFAULTS, ...st };
    applyClasses();
    chrome.storage.onChanged.addListener((c) => { for (const k in c) if (k in DEFAULTS) S[k] = c[k].newValue; applyClasses(); });
    if (!S.enabled) return;
    shortsRedirect();
    window.addEventListener("yt-navigate-finish", shortsRedirect);
    const ready = () => {
      checkBreak().then((blocked) => { if (!blocked) { gate(); trackScroll(); } });
      blockReelsPages();
      calmBadges(); setInterval(calmBadges, 2000);
    };
    if (document.body) ready(); else document.addEventListener("DOMContentLoaded", ready);
  }
  init();
})();
