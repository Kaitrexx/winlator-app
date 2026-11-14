package com.winlator;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.winlator.core.AppUtils;
import com.winlator.core.LocaleHelper;

public class NfsSettingsActivity extends AppCompatActivity {
    public static final String PREF_SCREEN_SIZE = "nfs_screen_size";
    public static final String PREF_FPS_LIMIT = "nfs_fps_limit";
    public static final String DEFAULT_SCREEN_SIZE = "640x480";

    private static final String[] SIZE_LABELS = {"640x480", "800x600", "1280x720"};
    private static final String[] SIZE_VALUES = {"640x480", "800x600", "1280x720"};
    private static final String[] FPS_LABELS = {"Limitsiz", "30", "60"};
    private static final int[] FPS_VALUES = {0, 30, 60};

    private SharedPreferences preferences;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setSystemLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        addTitle(root, "Qrafika");
        addLabel(root, "Ekran ölçüsü");
        String size = preferences.getString(PREF_SCREEN_SIZE, DEFAULT_SCREEN_SIZE);
        int sizeIndex = 0;
        for (int i = 0; i < SIZE_VALUES.length; i++) if (SIZE_VALUES[i].equals(size)) sizeIndex = i;
        addSpinner(root, SIZE_LABELS, sizeIndex, (position) ->
            preferences.edit().putString(PREF_SCREEN_SIZE, SIZE_VALUES[position]).apply());

        addTitle(root, "Performans");
        addLabel(root, "FPS limiti");
        int fps = preferences.getInt(PREF_FPS_LIMIT, 0);
        int fpsIndex = 0;
        for (int i = 0; i < FPS_VALUES.length; i++) if (FPS_VALUES[i] == fps) fpsIndex = i;
        addSpinner(root, FPS_LABELS, fpsIndex, (position) ->
            preferences.edit().putInt(PREF_FPS_LIMIT, FPS_VALUES[position]).apply());

        TextView note = new TextView(this);
        note.setText("Dəyişikliklər növbəti BAŞLAT-da tətbiq olunur.");
        note.setTextSize(13);
        note.setPadding(0, dp(24), 0, 0);
        root.addView(note);

        setContentView(root);
    }

    private interface OnPick { void onPick(int position); }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density);
    }

    private void addTitle(LinearLayout root, String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(20);
        view.setPadding(0, dp(16), 0, dp(8));
        root.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addLabel(LinearLayout root, String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        root.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addSpinner(LinearLayout root, String[] labels, int selected, OnPick listener) {
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        spinner.setSelection(selected);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                listener.onPick(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        root.addView(spinner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
