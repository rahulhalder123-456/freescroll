package com.freescroll;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Thin shell: sees which app/site is open and scrolling, asks the Rust engine, draws the answer. */
public class FreeScrollService extends AccessibilityService {
    private static final Map<String, Boolean> catCache = new HashMap<>();

    private final Handler h = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View overlay;
    private int overlayKind = Core.NONE;
    private String overlaySite = "";
    private Runnable ticker;

    private String lastPkg = "", lastSite = "";
    private long lastWebCheck = 0;
    private long lastEval = 0;

    /** Known social package, or one Android itself files under "Social". */
    static boolean isSocial(Context c, String pkg) {
        if (Core.isSocialPackage(pkg)) return true;
        Boolean b = catCache.get(pkg);
        if (b == null) {
            boolean v = false;
            try {
                ApplicationInfo ai = c.getPackageManager().getApplicationInfo(pkg, 0);
                v = Build.VERSION.SDK_INT >= 26 && ai.category == ApplicationInfo.CATEGORY_SOCIAL;
            } catch (Exception ignored) { }
            catCache.put(pkg, v);
            b = v;
        }
        return b;
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Prefs.apply(this);
        Prefs.restoreState(this);
    }

    @Override public void onInterrupt() { dismiss(); }

    @Override public void onAccessibilityEvent(AccessibilityEvent ev) {
        CharSequence cs = ev.getPackageName();
        if (cs == null) return;
        String pkg = cs.toString();
        if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui") || isKeyboard(pkg)) return;

        String site = siteFor(pkg);
        if (site == null) { lastPkg = pkg; lastSite = ""; dismiss(); return; }

        int type = ev.getEventType();
        long now = System.currentTimeMillis();
        if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            float screenH = getResources().getDisplayMetrics().heightPixels;
            double px = Build.VERSION.SDK_INT >= 28 ? Math.abs(ev.getScrollDeltaY()) : 0;
            if (px == 0) px = screenH * 0.15; // no delta from this app: assume a modest flick
            show(Core.onScroll(site, px, screenH, now), site);
        } else if (!site.equals(overlaySite) || overlay == null) {
            if (!site.equals(lastSite) || now - lastEval > 1500) {
                lastEval = now;
                show(Core.onForeground(site, now), site);
            }
        }
        lastPkg = pkg;
        lastSite = site;
    }

    private boolean isKeyboard(String pkg) {
        if (pkg.contains("inputmethod") || pkg.contains("keyboard")) return true;
        String ime = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return ime != null && ime.startsWith(pkg + "/");
    }

    /** Package name for social apps, "web:<domain>" for a social site in a browser, else null. */
    private String siteFor(String pkg) {
        if (isSocial(this, pkg)) return pkg;
        String idRes = Core.urlIdForBrowser(pkg);
        if (idRes == null || idRes.isEmpty()) return null;
        long now = System.currentTimeMillis();
        if (pkg.equals(lastPkg) && now - lastWebCheck < 1200) return lastSite.startsWith("web:") ? lastSite : null;
        lastWebCheck = now;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return pkg.equals(lastPkg) && lastSite.startsWith("web:") ? lastSite : null;
        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(idRes);
            if (nodes == null || nodes.isEmpty()) {
                return pkg.equals(lastPkg) && lastSite.startsWith("web:") ? lastSite : null; // URL bar hidden (fullscreen)
            }
            CharSequence t = nodes.get(0).getText();
            String s = t == null ? "" : Core.siteForUrl(t.toString());
            return s.isEmpty() ? null : s;
        } finally { root.recycle(); }
    }

    // ---------------------------------------------------------------- overlays
    private void show(int action, String site) {
        if (action == Core.NONE) return;
        if (overlay != null && overlayKind == action && site.equals(overlaySite)) return;
        if (overlay != null && overlayKind == Core.BREAK && action != Core.BREAK) return;
        dismiss();
        overlayKind = action;
        overlaySite = site;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.parseColor("#101418"));
        int pad = dp(24);
        box.setPadding(pad, pad, pad, pad);
        if (action == Core.GATE) buildGate(box, site);
        else if (action == Core.STOP) buildStop(box, site);
        else buildBreak(box, site);
        overlay = box;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.OPAQUE);
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        try { wm.addView(overlay, lp); } catch (Exception e) { overlay = null; overlayKind = Core.NONE; }
    }

    private void dismiss() {
        if (ticker != null) { h.removeCallbacks(ticker); ticker = null; }
        if (overlay != null) { try { wm.removeView(overlay); } catch (Exception ignored) { } }
        overlay = null; overlayKind = Core.NONE; overlaySite = "";
    }

    private void buildGate(LinearLayout box, String site) {
        box.addView(title("Pause. Why are you here?"));
        box.addView(body("This app is built to be effortless to open and hard to close. Say what you came for, then decide."));
        EditText in = new EditText(this);
        in.setHint("e.g. reply to Sara, watch one tutorial");
        in.setHintTextColor(Color.GRAY); in.setTextColor(Color.WHITE); in.setSingleLine(true);
        box.addView(in, new LinearLayout.LayoutParams(-1, -2));
        Button go = btn("Wait…", "#3b82f6");
        Button leave = btn("Leave", "#2a343d");
        box.addView(go); box.addView(leave);
        final int[] left = {Prefs.gateSecs(this)};
        final Runnable refresh = () -> {
            boolean ok = left[0] <= 0 && in.getText().toString().trim().length() >= 3;
            go.setEnabled(ok);
            go.setText(left[0] > 0 ? "Wait " + left[0] + "s" : ok ? "Enter" : "Type your reason");
        };
        in.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) { refresh.run(); }
        });
        ticker = new Runnable() { public void run() {
            if (left[0] > 0) { left[0]--; refresh.run(); h.postDelayed(this, 1000); }
        } };
        h.postDelayed(ticker, 1000);
        refresh.run();
        go.setOnClickListener(v -> { Core.gatePassed(site, System.currentTimeMillis()); Prefs.saveState(this); dismiss(); });
        leave.setOnClickListener(v -> { dismiss(); performGlobalAction(GLOBAL_ACTION_HOME); });
    }

    private void buildStop(LinearLayout box, String site) {
        box.addView(title("You've reached the end."));
        box.addView(body("You scrolled " + Prefs.scrollLimit(this) + " screens. This feed would never end on its own, so this is your stopping point."));
        Button br = btn("Take a " + Prefs.breakMin(this) + "-min break", "#3b82f6");
        Button more = btn("Continue (" + (int) Math.ceil(Prefs.scrollLimit(this) / 2.0) + " more screens)", "#2a343d");
        box.addView(br); box.addView(more);
        br.setOnClickListener(v -> {
            Core.startBreak(site, System.currentTimeMillis()); Prefs.saveState(this);
            dismiss(); show(Core.BREAK, site);
        });
        more.setOnClickListener(v -> {
            Core.continueAnyway(site, getResources().getDisplayMetrics().heightPixels); dismiss();
        });
    }

    private void buildBreak(LinearLayout box, String site) {
        box.addView(title("Break time"));
        TextView t = body("");
        box.addView(t);
        Button home = btn("Go home", "#2a343d");
        box.addView(home);
        home.setOnClickListener(v -> { dismiss(); performGlobalAction(GLOBAL_ACTION_HOME); });
        ticker = new Runnable() { public void run() {
            long s = Core.breakLeftSecs(site, System.currentTimeMillis());
            if (s <= 0) { dismiss(); return; }
            t.setText("Come back in " + (s / 60) + "m " + (s % 60) + "s.\nStretch, drink water, look outside.");
            h.postDelayed(this, 1000);
        } };
        ticker.run();
    }

    // ---------------------------------------------------------------- tiny view helpers
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    private TextView title(String s) {
        TextView t = new TextView(this); t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(26);
        t.setGravity(Gravity.CENTER); t.setPadding(0, 0, 0, dp(12)); return t;
    }
    private TextView body(String s) {
        TextView t = new TextView(this); t.setText(s); t.setTextColor(Color.parseColor("#b8c0c6")); t.setTextSize(16);
        t.setGravity(Gravity.CENTER); t.setPadding(0, 0, 0, dp(16)); return t;
    }
    private Button btn(String s, String color) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor(color));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52)); lp.topMargin = dp(10);
        b.setLayoutParams(lp); return b;
    }
}
