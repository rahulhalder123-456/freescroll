package com.freescroll;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

public class MainActivity extends Activity {
    private TextView status, stats, stepsHelp;
    private EditText limit, gate, brk;
    private CheckBox silence;
    private Button b1, b2, b3;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (18 * getResources().getDisplayMetrics().density);
        root.setPadding(p, p, p, p);
        ScrollView sv = new ScrollView(this); sv.addView(root);
        setContentView(sv);

        root.addView(text("FreeScroll", 26, true));
        root.addView(text("Defuses the 10 attention traps of social media apps and websites.", 14, false));
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
        root.addView(text("\nWhat it does\n"
            + "1 Infinite scroll: a stopping point, then a timed break\n"
            + "9 Simplicity: type why you are here and wait before entering\n"
            + "10 Buzz: social notifications are removed\n"
            + "While a FreeScroll screen is up, the app behind it is muted.\n"
            + "Works in every listed social app, any app Android files under Social, and social sites opened in Chrome, Firefox, Samsung Internet, Edge, Brave, Opera and others.\n\n"
            + "Not possible on a phone: hiding counts/Reels inside another app's page, or recolouring its badges. For that, use Firefox for Android with the FreeScroll extension.", 13, false));

        if (!consented()) askConsent();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

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
