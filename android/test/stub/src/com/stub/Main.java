package com.stub;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Test fixture: a long scrolling feed. Contains no content from any real service. */
public class Main extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        for (int i = 1; i <= 120; i++) {
            TextView t = new TextView(this);
            t.setText("Stub feed item " + i + "  (" + getPackageName() + ")");
            t.setTextSize(20); t.setGravity(Gravity.CENTER);
            t.setBackgroundColor(i % 2 == 0 ? Color.parseColor("#dde6f5") : Color.parseColor("#f5e6dd"));
            col.addView(t, new LinearLayout.LayoutParams(-1, 700));
        }
        sv.addView(col);
        setContentView(sv);
    }
}
