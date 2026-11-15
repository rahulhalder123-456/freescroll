# FreeScroll

Chrome/Edge extension that counters the 10 social-media attention traps.
Works on Facebook, Instagram, YouTube, TikTok, X, Reddit, Snapchat web, LinkedIn.

## Install (developer mode)
1. Open `chrome://extensions` (or `edge://extensions`), turn on **Developer mode**.
2. **Load unpacked** and pick this folder.
3. Click the toolbar icon to switch individual traps on or off.

## Trap -> counter-measure
| # | Trap | What FreeScroll does |
|---|------|----------------------|
| 1 | Infinite scroll | Stopping point after N screens, then a timed break |
| 2 | Red colour | Red badges recoloured grey, "(3)" stripped from tab title |
| 3 | Streaks | Streak elements hidden |
| 4 | Anticipation | "Typing..." indicators hidden |
| 5 | Filtered reality | Optional greyscale (filters live in the camera app, not the web) |
| 6 | Custom feed | Recommendations, Reels/Shorts hidden, Shorts redirected to normal player |
| 7 | Artificial urgency | Stories hidden |
| 8 | Likes/followers | Counts hidden |
| 9 | Simplicity | Intention gate: type a reason and wait before entering |
| 10 | Buzz | Notification permission prompts always denied |

## Limits (honest)
- Selectors are best-effort; sites rename their markup, so some hiding rules will need updating.
- It cannot change what happens inside the native phone apps. Android needs a separate app.
