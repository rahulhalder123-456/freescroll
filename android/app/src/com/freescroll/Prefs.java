package com.freescroll;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private Prefs() {}
    static SharedPreferences sp(Context c) { return c.getSharedPreferences("fs", Context.MODE_PRIVATE); }

    static int scrollLimit(Context c) { return sp(c).getInt("scrollLimit", 8); }
    static int gateSecs(Context c)    { return sp(c).getInt("gateSecs", 10); }
    static int passMin(Context c)     { return sp(c).getInt("passMin", 2); } // minutes away before the gate asks again
    static int breakMin(Context c)    { return sp(c).getInt("breakMin", 5); }
    static boolean silence(Context c) { return sp(c).getBoolean("silence", true); }

    /** Push saved settings (and persisted break/pass state) into the Rust engine. */
    static void apply(Context c) {
        Core.setConfig(scrollLimit(c), gateSecs(c), passMin(c), breakMin(c));
        for (java.util.Map.Entry<String, ?> e : sp(c).getAll().entrySet()) { // the user's Hard/Soft/Off choices
            if (e.getKey().startsWith("mode:") && e.getValue() instanceof Integer) {
                Core.setMode(e.getKey().substring(5), (Integer) e.getValue());
            }
        }
    }
    /** Remember and apply the restriction level for one app/site key. */
    static void setMode(Context c, String site, int mode) {
        sp(c).edit().putInt("mode:" + site, mode).apply();
        Core.setMode(site, mode);
    }
    static void restoreState(Context c) { Core.restore(sp(c).getString("state", "")); }
    static void saveState(Context c)    { sp(c).edit().putString("state", Core.snapshot()).apply(); }
}
