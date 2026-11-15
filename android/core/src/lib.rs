//! FreeScroll core: the brain. Java is only a thin Android shell around these JNI functions.
pub mod engine;
pub mod sites;

use engine::{Action, Engine};
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jdouble, jint, jlong, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use std::sync::{Mutex, OnceLock};

fn eng() -> &'static Mutex<Engine> {
    static E: OnceLock<Mutex<Engine>> = OnceLock::new();
    E.get_or_init(|| Mutex::new(Engine::new()))
}
fn lock() -> std::sync::MutexGuard<'static, Engine> {
    eng().lock().unwrap_or_else(|p| p.into_inner())
}
fn s(env: &mut JNIEnv, j: &JString) -> String {
    env.get_string(j).map(|v| v.into()).unwrap_or_default()
}
fn out(env: &mut JNIEnv, v: &str) -> jstring {
    env.new_string(v).map(|j| j.into_raw()).unwrap_or(std::ptr::null_mut())
}
fn act(a: Action) -> jint { a as jint }

#[no_mangle]
pub extern "system" fn Java_com_freescroll_Core_setConfig(
    _e: JNIEnv, _c: JClass, scroll_limit: jint, gate_secs: jint, gate_pass_min: jint, break_min: jint,
) {
    let mut g = lock();
    g.cfg.scroll_limit_screens = scroll_limit.max(0) as u32;
    g.cfg.gate_secs = gate_secs.max(0) as u32;
    g.cfg.gate_pass_min = gate_pass_min.max(1) as u32;
    g.cfg.break_min = break_min.max(1) as u32;
}

#[no_mangle]
pub extern "system" fn Java_com_freescroll_Core_isSocialPackage(
    mut env: JNIEnv, _c: JClass, pkg: JString,
) -> jboolean {
    if sites::is_social_package(&s(&mut env, &pkg)) { JNI_TRUE } else { JNI_FALSE }
}

#[no_mangle]
pub extern "system" fn Java_com_freescroll_Core_urlIdForBrowser(
    mut env: JNIEnv, _c: JClass, pkg: JString,
) -> jstring {
    let p = s(&mut env, &pkg);
    out(&mut env, sites::url_id_for_browser(&p).unwrap_or(""))
}

/// Returns "web:<domain>" or "".
#[no_mangle]
pub extern "system" fn Java_com_freescroll_Core_siteForUrl(
    mut env: JNIEnv, _c: JClass, text: JString,
) -> jstring {
    let t = s(&mut env, &text);
    out(&mut env, &sites::site_for_url(&t).unwrap_or_default())
}

#[no_mangle]
pub extern "system" fn Java_com_freescroll_Core_onForeground(
    mut env: JNIEnv, _c: JClass, site: JString, now: jlong,
) -> jint {
    let k = s(&mut env, &site);
    act(lock().on_foreground(&k, now as u64))
}

#[no_mangle]
pub extern "system" fn Java_com_freescroll_Core_onScroll(
    mut env: JNIEnv, _c: JClass, site: JString, px: jdouble, screen_h: jdouble, now: jlong,
) -> jint {
    let k = s(&mut env, &site);
    act(lock().on_scroll(&k, px, screen_h, now as u64))
}

#[no_mangle]
pub extern "system" fn Java_com_freescroll_Core_gatePassed(
    mut env: JNIEnv, _c: JClass, site: JString, now: jlong,
) {
    let k = s(&mut env, &site);
    lock().gate_passed(&k, now as u64);
}

#[no_mangle]
pub extern "system" fn Java_com_freescroll_Core_startBreak(
    mut env: JNIEnv, _c: JClass, site: JString, now: jlong,
) {
    let k = s(&mut env, &site);
    lock().start_break(&k, now as u64);
}

#[no_mangle]
pub extern "system" fn Java_com_freescroll_Core_continueAnyway(
    mut env: JNIEnv, _c: JClass, site: JString, screen_h: jdouble,
) {
    let k = s(&mut env, &site);
    lock().continue_anyway(&k, screen_h);
}

#[no_mangle]
pub extern "system" fn Java_com_freescroll_Core_breakLeftSecs(
    mut env: JNIEnv, _c: JClass, site: JString, now: jlong,
) -> jlong {
    let k = s(&mut env, &site);
    lock().break_left_secs(&k, now as u64) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_freescroll_Core_buzzSilenced(_e: JNIEnv, _c: JClass) {
    lock().stats.buzzes_silenced += 1;
}

#[no_mangle]
pub extern "system" fn Java_com_freescroll_Core_snapshot(env: JNIEnv, _c: JClass) -> jstring {
    let v = lock().snapshot();
    let mut env = env;
    out(&mut env, &v)
}

#[no_mangle]
pub extern "system" fn Java_com_freescroll_Core_restore(mut env: JNIEnv, _c: JClass, snap: JString) {
    let v = s(&mut env, &snap);
    lock().restore(&v);
}

#[no_mangle]
pub extern "system" fn Java_com_freescroll_Core_statsJson(env: JNIEnv, _c: JClass) -> jstring {
    let v = lock().stats_json();
    let mut env = env;
    out(&mut env, &v)
}

/// mode: 0 = Off, 1 = Soft, 2 = Hard
#[no_mangle]
pub extern "system" fn Java_com_freescroll_Core_setMode(
    mut env: JNIEnv, _c: JClass, site: JString, mode: jint,
) {
    let k = s(&mut env, &site);
    lock().set_mode(&k, engine::Mode::from_i32(mode));
}

/// The mode in force for a site (the user's choice or the built-in default).
#[no_mangle]
pub extern "system" fn Java_com_freescroll_Core_modeOf(
    mut env: JNIEnv, _c: JClass, site: JString,
) -> jint {
    let k = s(&mut env, &site);
    lock().mode_of(&k) as jint
}

/// Comma-separated list of every known social package name.
#[no_mangle]
pub extern "system" fn Java_com_freescroll_Core_knownPackages(env: JNIEnv, _c: JClass) -> jstring {
    let mut env = env;
    out(&mut env, &sites::SOCIAL_PACKAGES.join(","))
}

/// Comma-separated list of every known social website, as engine keys ("web:domain").
#[no_mangle]
pub extern "system" fn Java_com_freescroll_Core_knownSites(env: JNIEnv, _c: JClass) -> jstring {
    let mut env = env;
    let mut v: Vec<String> = sites::SOCIAL_DOMAINS.iter().map(|d| format!("web:{}", d)).collect();
    v.push("web:youtube.com/shorts".to_string());
    out(&mut env, &v.join(","))
}
