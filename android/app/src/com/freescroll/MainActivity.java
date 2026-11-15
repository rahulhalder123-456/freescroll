package com.freescroll;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

public class MainActivity extends Activity {
    private TextView status, stats;
    private EditText limit, gate, brk;
    private CheckBox silence;

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

        Button acc = button("1. Turn on FreeScroll in Accessibility");
        acc.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(acc);
        Button nl = button("2. Allow notification access (silences buzz)");
        nl.setOnClickListener(v -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        root.addView(nl);

        limit = num(root, "Stop after N screens (0 = never)", Prefs.scrollLimit(this));
        gate  = num(root, "Intention gate wait, seconds (0 = off)", Prefs.gateSecs(this));
        brk   = num(root, "Break length, minutes", Prefs.breakMin(this));
        silence = new CheckBox(this); silence.setText("Silence notifications from social apps");
        silence.setChecked(Prefs.silence(this)); root.addView(silence);
        Button save = button("Save settings");
        save.setOnClickListener(v -> save());
        root.addView(save);

        stats = text("", 14, false); root.addView(stats);
        root.addView(text("\nWhat it does\n"
            + "1 Infinite scroll: a stopping point, then a timed break\n"
            + "9 Simplicity: type why you are here and wait before entering\n"
            + "10 Buzz: social notifications are removed\n"
            + "Works in every listed social app, any app Android files under Social, and social sites opened in Chrome, Firefox, Samsung Internet, Edge, Brave, Opera and others.\n\n"
            + "Not possible on a phone: hiding counts/Reels inside another app's page, or recolouring its badges. For that, use Firefox for Android with the FreeScroll extension.", 13, false));
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void save() {
        Prefs.sp(this).edit()
            .putInt("scrollLimit", val(limit, 8)).putInt("gateSecs", val(gate, 10))
            .putInt("breakMin", Math.max(1, val(brk, 5))).putBoolean("silence", silence.isChecked()).apply();
        Prefs.apply(this);
        refresh();
    }

    private void refresh() {
        boolean a = enabled(FreeScrollService.class.getName()), n = enabled(BuzzService.class.getName());
        status.setText("Accessibility: " + (a ? "ON ✓" : "OFF ✗") + "\nNotification access: " + (n ? "ON ✓" : "OFF (optional)"));
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
        return flat != null && flat.contains(new ComponentName(this, cls).flattenToShortString());
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
