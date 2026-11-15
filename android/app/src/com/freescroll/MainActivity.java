package com.freescroll;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private TextView status, stats, stepsHelp;
    private EditText limit, gate, brk;
    private CheckBox silence;
    private Button b1, b2, b3;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18);
        root.setPadding(p, p, p, p);
        ScrollView sv = new ScrollView(this); sv.addView(root);
        setContentView(sv);

        root.addView(text("FreeScroll", 26, true));
        root.addView(text("Helps you leave the endless-scroll traps of social media apps and websites.", 14, false));
        status = text("", 15, false); root.addView(status);

        // Setup steps. Android requires the user to unlock sideloaded apps by hand; these buttons jump to each screen.
        root.addView(text("Setup (3 steps)", 17, true));
        stepsHelp = text("", 13, false); root.addView(stepsHelp);
        b1 = button("1. Allow restricted settings (opens App info)");
        b1.setOnClickListener(v -> {
            if (!consented()) { askConsent(); return; }
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        });
        root.addView(b1);
        b2 = button("2. Turn on FreeScroll in Accessibility");
        b2.setOnClickListener(v -> { if (!consented()) { askConsent(); return; } startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); });
        root.addView(b2);
        b3 = button("3. Allow notification access (optional, silences buzz)");
        b3.setOnClickListener(v -> { if (!consented()) { askConsent(); return; } startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")); });
        root.addView(b3);

        // ---- Dashboard: one Hard / Soft / Off switch per app and website
        root.addView(text("\nYour social apps and sites", 19, true));
        root.addView(text("Tap a button to change how strict FreeScroll is with that app.\n"
            + "HARD: ask why you are there, stop you after a few screens, then a timed break.\n"
            + "SOFT: ask why you are there once, then stay as long as you like.\n"
            + "OFF: leave this app alone.", 13, false));
        LinearLayout appsBox = new LinearLayout(this); appsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(appsBox);
        buildAppRows(appsBox);
        root.addView(text("\nWebsites (when opened in a browser)", 16, true));
        LinearLayout sitesBox = new LinearLayout(this); sitesBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(sitesBox);
        buildSiteRows(sitesBox);

        root.addView(text("\nLimits for HARD mode", 16, true));
        limit = num(root, "Stop after N screens (0 = never)", Prefs.scrollLimit(this));
        gate  = num(root, "Intention gate wait, seconds (0 = off)", Prefs.gateSecs(this));
        brk   = num(root, "Break length, minutes", Prefs.breakMin(this));
        silence = new CheckBox(this); silence.setText("Silence notifications from social apps");
        silence.setChecked(Prefs.silence(this)); root.addView(silence);
        Button save = button("Save settings");
        save.setOnClickListener(v -> save());
        root.addView(save);
        Button privacy = button("What FreeScroll can see (privacy)");
        privacy.setOnClickListener(v -> showDisclosure(false));
        root.addView(privacy);

        stats = text("", 14, false); root.addView(stats);
        root.addView(text("\nWhat this app can and cannot do\n"
            + "It CAN: ask why you are opening an app, stop endless scrolling, give you a break, mute the app behind its own screens, and remove social notifications.\n"
            + "It CANNOT change what is drawn inside another app: hiding like counts, stories, streaks, typing dots or red badges, or filtering a feed. Android does not allow one app to edit another. "
            + "Those protections exist only in the FreeScroll browser extension (desktop browsers and Firefox for Android).\n"
            + "Works in every app listed above, any app Android files under Social, and the listed websites in Chrome, Firefox, Samsung Internet, Edge, Brave, Opera and others.", 13, false));

        if (!consented()) askConsent();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    // ------------------------------------------------------------------ dashboard
    private void buildAppRows(LinearLayout box) {
        PackageManager pm = getPackageManager();
        Set<String> pkgs = new HashSet<>();
        for (String k : Core.knownPackages().split(",")) {
            try { pm.getApplicationInfo(k, 0); pkgs.add(k); } catch (Exception ignored) { }
        }
        if (Build.VERSION.SDK_INT >= 26) { // apps Android itself files under "Social"
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                if (ai.category == ApplicationInfo.CATEGORY_SOCIAL && !ai.packageName.equals(getPackageName())) pkgs.add(ai.packageName);
            }
        }
        List<String[]> rows = new ArrayList<>(); // {label, pkg}
        for (String k : pkgs) {
            String label = k;
            try { label = pm.getApplicationLabel(pm.getApplicationInfo(k, 0)).toString(); } catch (Exception ignored) { }
            rows.add(new String[]{label, k});
        }
        Collections.sort(rows, (a, c) -> a[0].compareToIgnoreCase(c[0]));
        if (rows.isEmpty()) box.addView(text("No social apps found on this phone yet.", 13, false));
        for (String[] r : rows) {
            box.addView(row(r[0], r[1], r[1], pm));
            if (r[1].equals("com.google.android.youtube") || r[1].equals("com.google.android.apps.youtube.mango") || r[1].equals("app.revanced.android.youtube")) {
                box.addView(row("   " + r[0] + " Shorts", r[1] + "/shorts", null, pm)); // Shorts vs long videos
            }
        }
    }

    private void buildSiteRows(LinearLayout box) {
        List<String> keys = new ArrayList<>();
        Collections.addAll(keys, Core.knownSites().split(","));
        Collections.sort(keys);
        for (String k : keys) box.addView(row(k.substring(4), k, null, null));
    }

    /** One line: [icon] name ........ [HARD|SOFT|OFF]. `iconPkg` may be null. */
    private LinearLayout row(String label, String key, String iconPkg, PackageManager pm) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(4), 0, dp(4));
        ImageView icon = new ImageView(this);
        if (iconPkg != null && pm != null) { try { icon.setImageDrawable(pm.getApplicationIcon(iconPkg)); } catch (Exception ignored) { } }
        r.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView t = new TextView(this);
        t.setText(label); t.setTextSize(15); t.setPadding(dp(10), 0, dp(6), 0);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        Button m = new Button(this);
        m.setAllCaps(false); m.setTextColor(Color.WHITE);
        styleMode(m, Core.modeOf(key));
        m.setOnClickListener(v -> {
            int cur = Core.modeOf(key);
            int next = cur == Core.HARD ? Core.SOFT : cur == Core.SOFT ? Core.OFF : Core.HARD; // Hard -> Soft -> Off -> Hard
            Prefs.setMode(this, key, next);
            styleMode(m, next);
        });
        r.addView(m, new LinearLayout.LayoutParams(dp(92), dp(44)));
        return r;
    }

    private void styleMode(Button b, int mode) {
        b.setText(mode == Core.HARD ? "Hard" : mode == Core.SOFT ? "Soft" : "Off");
        b.setBackgroundColor(Color.parseColor(mode == Core.HARD ? "#c0392b" : mode == Core.SOFT ? "#d68910" : "#7f8c8d"));
    }

    // ------------------------------------------------------------------ consent / settings
    private boolean consented() { return Prefs.sp(this).getBoolean("consent", false); }

    private void askConsent() { showDisclosure(true); }

    /** Prominent disclosure shown before any permission is requested. */
    private void showDisclosure(boolean needAnswer) {
        AlertDialog.Builder d = new AlertDialog.Builder(this)
            .setTitle("Before you start")
            .setMessage("FreeScroll needs your permission to work. Please read:\n\n"
                + "• It uses Android's Accessibility service to see WHICH app or website is open and WHEN you scroll, so it can ask why you are there and give you a stopping point.\n\n"
                + "• It does NOT read your messages, posts, passwords or what you type in other apps, and it does NOT collect or store personal data.\n\n"
                + "• It has no internet permission. Nothing leaves your phone.\n\n"
                + "• Optional notification access is used only to remove notifications from social apps.\n\n"
                + "• You can turn it off any time in Settings > Accessibility.");
        if (needAnswer) {
            d.setCancelable(false)
             .setPositiveButton("I understand and agree", (x, y) -> { Prefs.sp(this).edit().putBoolean("consent", true).apply(); refresh(); })
             .setNegativeButton("No thanks", (x, y) -> finish());
        } else {
            d.setPositiveButton("OK", null);
        }
        d.show();
    }

    private void save() {
        Prefs.sp(this).edit()
            .putInt("scrollLimit", val(limit, 8)).putInt("gateSecs", val(gate, 10))
            .putInt("breakMin", Math.max(1, val(brk, 5))).putBoolean("silence", silence.isChecked()).apply();
        Prefs.apply(this);
        refresh();
    }

    private void refresh() {
        boolean ok = consented();
        boolean a = enabled(FreeScrollService.class.getName()), n = enabled(BuzzService.class.getName());
        status.setText("Permission agreed: " + (ok ? "yes ✓" : "not yet") + "\nAccessibility: " + (a ? "ON ✓" : "OFF ✗")
            + "\nNotification access: " + (n ? "ON ✓" : "OFF (optional)"));
        b1.setEnabled(ok); b2.setEnabled(ok); b3.setEnabled(ok);
        stepsHelp.setText(a ? "All set. FreeScroll is running."
            : "If step 2 says \"Restricted setting\" or is greyed out: do step 1, tap the ⋮ menu at the top right, "
              + "choose \"Allow restricted settings\", then do step 2. (Android asks for this for any app installed from an APK file.)");
        try {
            JSONObject j = new JSONObject(Core.statsJson());
            stats.setText("\nSince the app started: " + j.getInt("screens") + " screens scrolled, "
                + j.getInt("stops") + " stops, " + j.getInt("breaks") + " breaks, "
                + j.getInt("gates") + " gates passed, " + j.getInt("buzzes") + " buzzes silenced");
        } catch (Exception e) { stats.setText(""); }
    }

    private boolean enabled(String cls) {
        String flat = Settings.Secure.getString(getContentResolver(),
            cls.contains("Buzz") ? "enabled_notification_listeners" : Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (flat == null) return false;
        ComponentName cn = new ComponentName(this, cls); // Android may store either spelling of the name
        return flat.contains(cn.flattenToShortString()) || flat.contains(cn.flattenToString());
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    private int val(EditText e, int def) { try { return Math.max(0, Integer.parseInt(e.getText().toString().trim())); } catch (Exception x) { return def; } }
    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setPadding(0, 6, 0, 6);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD); return t;
    }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); return b; }
    private EditText num(LinearLayout root, String label, int v) {
        root.addView(text(label, 14, false));
        EditText e = new EditText(this); e.setInputType(InputType.TYPE_CLASS_NUMBER); e.setText(String.valueOf(v));
        root.addView(e); return e;
    }
}
