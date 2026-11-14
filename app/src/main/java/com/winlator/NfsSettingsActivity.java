package com.winlator;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.winlator.core.AppUtils;
import com.winlator.core.LocaleHelper;

import java.util.ArrayList;
import java.util.List;

public class NfsSettingsActivity extends AppCompatActivity {
    public static final String PREF_SCREEN_SIZE = "nfs_screen_size";
    public static final String PREF_ASPECT = "nfs_aspect";
    public static final String PREF_FPS_LIMIT = "nfs_fps_limit";
    public static final String PREF_HUD_MODE = "nfs_hud_mode";
    public static final String PREF_FULLSCREEN = "nfs_fullscreen";
    public static final String DEFAULT_SCREEN_SIZE = "640x480";
    public static final String PREF_GPU_DRIVER = "nfs_gpu_driver"; // NFS_GPU_V1
    private static final String[] GPU_LABELS = {"Avtomatik", "Turnip (yalnız Snapdragon/Adreno)", "Vortek (Mali və digərləri)"};
    private static final String[] GPU_VALUES = {"auto", "turnip", "vortek"};
    public static final String PREF_CPU_CORES = "nfs_cpu_cores"; // NFS_CPU_V1
    private static final String[] CPU_LABELS = {"6,7 (hazırkı)", "4-7 (4 nüvə)", "0-7 (hamısı)"};
    private static final String[] CPU_VALUES = {"6,7", "4,5,6,7", "0,1,2,3,4,5,6,7"};

    // NFSMW-in öz ölçüləri (speed.exe-dən): yalnız 4:3 və 5:4.
    private static final String[] ASPECT_LABELS = {"4:3", "5:4"};
    private static final String[] ASPECT_VALUES = {"4:3", "5:4"};
    private static final String[] SIZES_43 = {"640x480", "800x600", "1024x768", "1280x960", "1600x1200"};
    private static final String[] SIZES_54 = {"1280x1024"};

    private static final String[] FPS_LABELS = {"Limitsiz", "30", "60"};
    private static final int[] FPS_VALUES = {0, 30, 60};

    private static final String[] HUD_LABELS = {"Söndürülü", "Sadə (yalnız FPS)", "Tam (FPS, GPU, RAM, CPU)"};

    private final List<String> sizeValues = new ArrayList<>();
    private final List<String> sizeLabels = new ArrayList<>();
    private Spinner sizeSpinner;
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

        String savedSize = preferences.getString(PREF_SCREEN_SIZE, DEFAULT_SCREEN_SIZE);
        String aspect = savedSize.equals("1280x1024") ? "5:4" : "4:3";

        addTitle(root, "Qrafika");

        addLabel(root, "Nisbət");
        fillSizes(aspect);
        int sizeIndex = Math.max(0, sizeValues.indexOf(savedSize));
        int aspectIndex = aspect.equals("5:4") ? 1 : 0;

        addSpinner(root, ASPECT_LABELS, aspectIndex, (position) -> applyAspect(ASPECT_VALUES[position]));

        addLabel(root, "Çözünürlük");
        sizeSpinner = addSpinner(root, sizeLabels.toArray(new String[0]), sizeIndex, (position) -> {
            if (position >= 0 && position < sizeValues.size())
                preferences.edit().putString(PREF_SCREEN_SIZE, sizeValues.get(position)).apply();
        });

        Switch swFullscreen = new Switch(this);
        swFullscreen.setText("Ekranı dart (tam ekran)");
        swFullscreen.setChecked(preferences.getBoolean(PREF_FULLSCREEN, true));
        swFullscreen.setPadding(0, dp(12), 0, dp(4));
        swFullscreen.setOnCheckedChangeListener((button, checked) ->
            preferences.edit().putBoolean(PREF_FULLSCREEN, checked).apply());
        root.addView(swFullscreen, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addTitle(root, "GPU sürücüsü");
        addLabel(root, "Qrafika sürücüsü");
        String savedGpu = preferences.getString(PREF_GPU_DRIVER, "auto");
        int gpuIndex = 0;
        for (int i = 0; i < GPU_VALUES.length; i++) if (GPU_VALUES[i].equals(savedGpu)) gpuIndex = i;
        addSpinner(root, GPU_LABELS, gpuIndex, (position) ->
            preferences.edit().putString(PREF_GPU_DRIVER, GPU_VALUES[position]).apply());

        addTitle(root, "Performans");
        addLabel(root, "CPU nüvələri (test üçün)");
        String savedCpu = preferences.getString(PREF_CPU_CORES, CPU_VALUES[0]);
        int cpuIndex = 0;
        for (int i = 0; i < CPU_VALUES.length; i++) if (CPU_VALUES[i].equals(savedCpu)) cpuIndex = i;
        addSpinner(root, CPU_LABELS, cpuIndex, (position) ->
            preferences.edit().putString(PREF_CPU_CORES, CPU_VALUES[position]).apply());

        addLabel(root, "FPS limiti");
        int fps = preferences.getInt(PREF_FPS_LIMIT, 0);
        int fpsIndex = 0;
        for (int i = 0; i < FPS_VALUES.length; i++) if (FPS_VALUES[i] == fps) fpsIndex = i;
        addSpinner(root, FPS_LABELS, fpsIndex, (position) ->
            preferences.edit().putInt(PREF_FPS_LIMIT, FPS_VALUES[position]).apply());

        addTitle(root, "Göstərici");
        addLabel(root, "Oyun içi performans göstəricisi");
        addSpinner(root, HUD_LABELS, preferences.getInt(PREF_HUD_MODE, 0), (position) ->
            preferences.edit().putInt(PREF_HUD_MODE, position).apply());

        TextView note = new TextView(this);
        note.setText("Dəyişikliklər növbəti BAŞLAT-da tətbiq olunur. 1280x960 və yuxarısı RAM-ı çox işlədir.");
        note.setTextSize(13);
        note.setPadding(0, dp(24), 0, 0);
        root.addView(note);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(root);
        setContentView(scrollView);
    }

    private interface OnPick { void onPick(int position); }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density);
    }

    private void fillSizes(String aspect) {
        sizeValues.clear();
        sizeLabels.clear();
        String[] sizes = aspect.equals("5:4") ? SIZES_54 : SIZES_43;
        for (String size : sizes) {
            sizeValues.add(size);
            boolean heavy = size.equals("1280x960") || size.equals("1280x1024") || size.equals("1600x1200");
            sizeLabels.add(heavy ? size + " (ağır)" : size);
        }
    }

    private void applyAspect(String aspect) {
        String saved = preferences.getString(PREF_SCREEN_SIZE, DEFAULT_SCREEN_SIZE);
        fillSizes(aspect);

        int index = sizeValues.indexOf(saved);
        if (index < 0) index = 0;

        preferences.edit()
            .putString(PREF_ASPECT, aspect)
            .putString(PREF_SCREEN_SIZE, sizeValues.get(index))
            .apply();

        if (sizeSpinner != null) {
            sizeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>(sizeLabels)));
            sizeSpinner.setSelection(index);
        }
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

    private Spinner addSpinner(LinearLayout root, String[] labels, int selected, OnPick listener) {
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
        return spinner;
    }
}
