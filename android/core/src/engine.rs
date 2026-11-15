//! FreeScroll policy engine. Pure Rust, no Android types, so it is unit-testable on the host.
//! A "site" is a key: an Android package name, or "web:<domain>" for a social site in a browser.

use std::collections::HashMap;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(i32)]
pub enum Action {
    None = 0,
    Gate = 1,  // trap 9: ask why you are here, make you wait
    Stop = 2,  // trap 1: stopping point for infinite scroll
    Break = 3, // locked out until break ends
}

/// How strict FreeScroll is with one app or site.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(i32)]
pub enum Mode {
    Off = 0,  // no restriction at all
    Soft = 1, // intention gate only, then stay as long as you like
    Hard = 2, // gate + scroll stopping point + break lock
}
impl Mode {
    pub fn from_i32(v: i32) -> Mode { match v { 2 => Mode::Hard, 1 => Mode::Soft, _ => Mode::Off } }
}

#[derive(Clone, Debug)]
pub struct Config {
    pub scroll_limit_screens: u32, // 0 = never stop
    pub gate_secs: u32,
    pub gate_pass_min: u32, // minutes away after which the gate asks again (refreshed while you are in the app)
    pub break_min: u32,
}
impl Default for Config {
    fn default() -> Self {
        Config { scroll_limit_screens: 8, gate_secs: 10, gate_pass_min: 2, break_min: 5 }
    }
}

#[derive(Default, Clone, Debug)]
struct SiteState {
    pass_until: u64,
    break_until: u64,
    travelled_px: f64,
}

#[derive(Default, Clone, Debug)]
pub struct Stats {
    pub screens_scrolled: f64,
    pub stops: u32,
    pub breaks: u32,
    pub gates_passed: u32,
    pub buzzes_silenced: u32,
}

#[derive(Default)]
pub struct Engine {
    pub cfg: Config,
    sites: HashMap<String, SiteState>,
    pub stats: Stats,
    stopped: HashMap<String, bool>,
    modes: HashMap<String, Mode>, // user overrides; otherwise sites::default_mode
}

impl Engine {
    pub fn new() -> Self { Self::default() }

    pub fn set_mode(&mut self, site: &str, m: Mode) { self.modes.insert(site.to_string(), m); }

    /// The user's choice for this site, or the built-in default.
    pub fn mode_of(&self, site: &str) -> Mode {
        self.modes.get(site).copied().unwrap_or_else(|| crate::sites::default_mode(site))
    }

    /// A tracked site just came to the foreground (or its URL changed).
    pub fn on_foreground(&mut self, site: &str, now: u64) -> Action {
        let mode = self.mode_of(site);
        if mode == Mode::Off { return Action::None; }
        // A stopping point the user walked away from without answering is still pending when they come back.
        let pending_stop = mode == Mode::Hard && *self.stopped.get(site).unwrap_or(&false);
        let pass_ms = self.cfg.gate_pass_min as u64 * 60_000;
        let gate_on = self.cfg.gate_secs > 0;
        let st = self.sites.entry(site.to_string()).or_default();
        if mode == Mode::Hard && st.break_until > now { return Action::Break; }
        if pending_stop { return Action::Stop; }
        if gate_on && st.pass_until <= now { return Action::Gate; }
        if st.pass_until > now { st.pass_until = now + pass_ms; } // being here keeps the pass alive
        Action::None
    }

    /// Scrolling happened inside a tracked site. `px` is vertical distance in pixels.
    pub fn on_scroll(&mut self, site: &str, px: f64, screen_h: f64, now: u64) -> Action {
        let mode = self.mode_of(site);
        if mode == Mode::Off { return Action::None; }
        let limit = self.cfg.scroll_limit_screens;
        let pass_ms = self.cfg.gate_pass_min as u64 * 60_000;
        let st = self.sites.entry(site.to_string()).or_default();
        if mode == Mode::Hard && st.break_until > now { return Action::Break; }
        if st.pass_until > now { st.pass_until = now + pass_ms; } // scrolling = still here
        let d = px.abs();
        st.travelled_px += d;
        self.stats.screens_scrolled += if screen_h > 0.0 { d / screen_h } else { 0.0 };
        if mode != Mode::Hard || limit == 0 || screen_h <= 0.0 { return Action::None; }
        if st.travelled_px > limit as f64 * screen_h {
            if !*self.stopped.get(site).unwrap_or(&false) {
                self.stopped.insert(site.to_string(), true);
                self.stats.stops += 1;
            }
            return Action::Stop;
        }
        Action::None
    }

    /// User typed a reason and waited out the gate.
    pub fn gate_passed(&mut self, site: &str, now: u64) {
        let pass = self.cfg.gate_pass_min as u64 * 60_000;
        self.sites.entry(site.to_string()).or_default().pass_until = now + pass;
        self.stats.gates_passed += 1;
    }

    /// User accepted the break at the stopping point.
    pub fn start_break(&mut self, site: &str, now: u64) {
        let st = self.sites.entry(site.to_string()).or_default();
        st.break_until = now + self.cfg.break_min as u64 * 60_000;
        st.pass_until = 0; // coming back after a break needs a fresh intention
        st.travelled_px = 0.0;
        self.stopped.insert(site.to_string(), false);
        self.stats.breaks += 1;
    }

    /// User chose "continue": give back half the allowance, not a blank cheque.
    pub fn continue_anyway(&mut self, site: &str, screen_h: f64) {
        let keep = (self.cfg.scroll_limit_screens as f64 / 2.0).ceil();
        let st = self.sites.entry(site.to_string()).or_default();
        st.travelled_px = (self.cfg.scroll_limit_screens as f64 - keep) * screen_h;
        self.stopped.insert(site.to_string(), false);
    }

    pub fn break_left_secs(&self, site: &str, now: u64) -> u64 {
        self.sites.get(site).map(|s| s.break_until.saturating_sub(now) / 1000).unwrap_or(0)
    }

    /// "site|pass|break;" so Java can persist across process death.
    pub fn snapshot(&self) -> String {
        self.sites.iter()
            .filter(|(_, s)| s.pass_until > 0 || s.break_until > 0)
            .map(|(k, s)| format!("{}|{}|{}", k, s.pass_until, s.break_until))
            .collect::<Vec<_>>().join(";")
    }
    pub fn restore(&mut self, snap: &str) {
        for part in snap.split(';') {
            let f: Vec<&str> = part.split('|').collect();
            if f.len() == 3 {
                if let (Ok(p), Ok(b)) = (f[1].parse(), f[2].parse()) {
                    let st = self.sites.entry(f[0].to_string()).or_default();
                    st.pass_until = p;
                    st.break_until = b;
                }
            }
        }
    }

    pub fn stats_json(&self) -> String {
        format!(
            "{{\"screens\":{:.0},\"stops\":{},\"breaks\":{},\"gates\":{},\"buzzes\":{}}}",
            self.stats.screens_scrolled, self.stats.stops, self.stats.breaks,
            self.stats.gates_passed, self.stats.buzzes_silenced
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    const H: f64 = 2000.0;
    fn hard() -> Engine {
        let mut e = Engine::new();
        for k in ["a", "b", "com.x", "web:instagram.com"] { e.set_mode(k, Mode::Hard); }
        e
    }

    #[test]
    fn gate_then_pass() {
        let mut e = hard();
        assert_eq!(e.on_foreground("a", 0), Action::Gate);
        e.gate_passed("a", 0);
        assert_eq!(e.on_foreground("a", 1000), Action::None);
        assert_eq!(e.on_foreground("a", 4 * 60_000), Action::Gate); // away longer than 2 min -> asked again
    }
    #[test]
    fn stops_after_limit_and_break_locks() {
        let mut e = hard();
        e.gate_passed("a", 0);
        for _ in 0..8 { assert_eq!(e.on_scroll("a", H, H, 10), Action::None); }
        assert_eq!(e.on_scroll("a", 1.0, H, 10), Action::Stop);
        e.start_break("a", 100);
        assert_eq!(e.on_foreground("a", 200), Action::Break);
        assert_eq!(e.on_scroll("a", H, H, 200), Action::Break);
        assert_eq!(e.on_foreground("a", 100 + 5 * 60_000 + 1), Action::Gate); // back, gate again
        assert_eq!(e.stats.breaks, 1);
        assert_eq!(e.stats.stops, 1);
    }
    #[test]
    fn continue_gives_half_allowance() {
        let mut e = hard();
        e.gate_passed("a", 0);
        for _ in 0..9 { e.on_scroll("a", H, H, 10); }
        e.continue_anyway("a", H);
        for _ in 0..4 { assert_eq!(e.on_scroll("a", H, H, 10), Action::None); }
        assert_eq!(e.on_scroll("a", H, H, 10), Action::Stop);
    }
    #[test]
    fn limit_zero_never_stops() {
        let mut e = hard();
        e.cfg.scroll_limit_screens = 0;
        for _ in 0..100 { assert_eq!(e.on_scroll("a", H, H, 1), Action::None); }
    }
    #[test]
    fn snapshot_roundtrip() {
        let mut e = hard();
        e.gate_passed("web:instagram.com", 5);
        e.start_break("com.x", 9);
        let s = e.snapshot();
        let mut f = hard();
        f.restore(&s);
        assert_eq!(f.on_foreground("com.x", 10), Action::Break);
        assert_eq!(f.on_foreground("web:instagram.com", 10), Action::None);
    }
    #[test]
    fn sites_are_independent() {
        let mut e = hard();
        e.start_break("a", 0);
        assert_eq!(e.on_foreground("b", 1), Action::Gate);
    }

    #[test]
    fn soft_gates_but_never_stops_or_locks() {
        let mut e = hard();
        e.set_mode("a", Mode::Soft);
        assert_eq!(e.on_foreground("a", 0), Action::Gate);
        e.gate_passed("a", 0);
        for _ in 0..50 { assert_eq!(e.on_scroll("a", H, H, 10), Action::None); }
        assert_eq!(e.stats.stops, 0);
    }
    #[test]
    fn off_does_nothing() {
        let mut e = hard();
        e.set_mode("a", Mode::Off);
        assert_eq!(e.on_foreground("a", 0), Action::None);
        assert_eq!(e.on_scroll("a", H * 100.0, H, 0), Action::None);
    }
    #[test]
    fn soft_ignores_an_old_break() {
        let mut e = hard();
        e.start_break("a", 0);
        e.set_mode("a", Mode::Soft);
        assert_ne!(e.on_foreground("a", 1), Action::Break);
    }
    #[test]
    fn defaults_follow_the_site_list() {
        let e = Engine::new();
        assert_eq!(e.mode_of("com.instagram.android"), Mode::Hard);
        assert_eq!(e.mode_of("com.whatsapp"), Mode::Soft);
        assert_eq!(e.mode_of("com.some.unknown.social"), Mode::Soft);
        assert_eq!(e.mode_of("com.google.android.youtube"), Mode::Soft);
        assert_eq!(e.mode_of("com.google.android.youtube/shorts"), Mode::Hard);
    }

    #[test]
    fn unanswered_stop_is_still_there_after_leaving_and_returning() {
        let mut e = hard();
        e.gate_passed("a", 0);
        for _ in 0..8 { e.on_scroll("a", H, H, 10); }
        assert_eq!(e.on_scroll("a", 1.0, H, 10), Action::Stop);
        // user minimises without choosing, then returns
        assert_eq!(e.on_foreground("a", 5_000), Action::Stop);
        assert_eq!(e.on_foreground("a", 6_000), Action::Stop);
        e.continue_anyway("a", H);
        assert_ne!(e.on_foreground("a", 7_000), Action::Stop);
    }
    #[test]
    fn pass_stays_alive_while_in_the_app_and_expires_when_away() {
        let mut e = hard();
        e.gate_passed("a", 0);
        // keeps scrolling for 10 minutes: never asked again
        for k in 1..=10 { assert_eq!(e.on_scroll("a", 10.0, H, k * 60_000), Action::None); }
        assert_eq!(e.on_foreground("a", 10 * 60_000 + 30_000), Action::None);
        // leaves for 3 minutes: asked again
        assert_eq!(e.on_foreground("a", 10 * 60_000 + 30_000 + 3 * 60_000), Action::Gate);
    }
    #[test]
    fn stop_pending_is_ignored_once_mode_is_soft() {
        let mut e = hard();
        e.gate_passed("a", 0);
        for _ in 0..9 { e.on_scroll("a", H, H, 10); }
        e.set_mode("a", Mode::Soft);
        assert_ne!(e.on_foreground("a", 20), Action::Stop);
    }
}
