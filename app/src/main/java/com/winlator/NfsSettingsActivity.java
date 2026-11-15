package com.winlator;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

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
    private static final String[] GPU_VALUES = {"auto", "turnip", "vortek"};
    public static final String PREF_CPU_CORES = "nfs_cpu_cores"; // NFS_CPU_V1
    private static final String[] CPU_VALUES = {"6,7", "4,5,6,7", "0,1,2,3,4,5,6,7"};

    // NFSMW-in öz ölçüləri (speed.exe-dən): yalnız 4:3 və 5:4.
    private static final String[] ASPECT_LABELS = {"4:3", "5:4"};
    private static final String[] ASPECT_VALUES = {"4:3", "5:4"};
    private static final String[] SIZES_43 = {"640x480", "800x600", "1024x768", "1280x960", "1600x1200"};
    private static final String[] SIZES_54 = {"1280x1024"};
    private static final int[] FPS_VALUES = {0, 30, 60};

    // NFS_SET_V1: tünd/sarı dizayn
    private static final int YELLOW = 0xFFFFC107;
    private static final int BG = 0xFF0B0B0D;
    private static final int CARD = 0xFF131315;
    private static final int LINE = 0xFF2A2A2E;
    private static final int MUTED = 0xFFB4B4B4;

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
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE); // yalnız landscape
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        applyImmersive();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        final int match = ViewGroup.LayoutParams.MATCH_PARENT;
        final int wrap = ViewGroup.LayoutParams.WRAP_CONTENT;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // Başlıq
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(0xFF141416);
        header.setPadding(dp(40), dp(8), dp(40), dp(8));

        TextView back = new TextView(this);
        back.setText("\u2039");
        back.setTextSize(28);
        back.setTextColor(YELLOW);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription(getString(R.string.nfs_set_back));
        GradientDrawable backBg = new GradientDrawable();
        backBg.setColor(0xFF141416);
        backBg.setStroke(dp(2), YELLOW);
        back.setBackground(backBg);
        back.setOnClickListener((v) -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = new TextView(this);
        title.setText(R.string.nfs_set_title);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, wrap, 1f);
        titleParams.leftMargin = dp(16);
        header.addView(title, titleParams);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.nfs_set_subtitle);
        subtitle.setTextSize(13);
        subtitle.setTextColor(0xFFE6E6E6);
        header.addView(subtitle, new LinearLayout.LayoutParams(wrap, wrap));
        root.addView(header, new LinearLayout.LayoutParams(match, wrap));

        View yellowLine = new View(this);
        yellowLine.setBackgroundColor(YELLOW);
        root.addView(yellowLine, new LinearLayout.LayoutParams(match, dp(3)));

        // Gövdə: iki sütun
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setPadding(dp(40), dp(16), dp(40), dp(8));
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        body.addView(left, new LinearLayout.LayoutParams(0, wrap, 1f));
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, wrap, 1f);
        rightParams.leftMargin = dp(16);
        body.addView(right, rightParams);

        // QRAFİKA
        String savedSize = preferences.getString(PREF_SCREEN_SIZE, DEFAULT_SCREEN_SIZE);
        String aspect = savedSize.equals("1280x1024") ? "5:4" : "4:3";
        fillSizes(aspect);
        int sizeIndex = Math.max(0, sizeValues.indexOf(savedSize));
        int aspectIndex = aspect.equals("5:4") ? 1 : 0;

        LinearLayout graphics = addCard(left, getString(R.string.nfs_set_graphics));
        addSpinnerRow(graphics, getString(R.string.nfs_set_aspect), ASPECT_LABELS, aspectIndex, (position) -> applyAspect(ASPECT_VALUES[position]));
        sizeSpinner = addSpinnerRow(graphics, getString(R.string.nfs_set_resolution), sizeLabels.toArray(new String[0]), sizeIndex, (position) -> {
            if (position >= 0 && position < sizeValues.size())
                preferences.edit().putString(PREF_SCREEN_SIZE, sizeValues.get(position)).apply();
        });

        Switch swFullscreen = new Switch(this);
        swFullscreen.setChecked(preferences.getBoolean(PREF_FULLSCREEN, true));
        swFullscreen.setThumbTintList(new ColorStateList(
            new int[][]{{android.R.attr.state_checked}, {}}, new int[]{YELLOW, 0xFF8A8A8E}));
        swFullscreen.setTrackTintList(new ColorStateList(
            new int[][]{{android.R.attr.state_checked}, {}}, new int[]{0x99FFC107, 0xFF3A3A3E}));
        swFullscreen.setOnCheckedChangeListener((button, checked) ->
            preferences.edit().putBoolean(PREF_FULLSCREEN, checked).apply());
        addRow(graphics, getString(R.string.nfs_set_fullscreen), swFullscreen);

        // GPU SÜRÜCÜSÜ
        LinearLayout gpuCard = addCard(left, getString(R.string.nfs_set_gpu_title));
        String savedGpu = preferences.getString(PREF_GPU_DRIVER, "auto");
        int gpuIndex = 0;
        for (int i = 0; i < GPU_VALUES.length; i++) if (GPU_VALUES[i].equals(savedGpu)) gpuIndex = i;
        addSpinnerRow(gpuCard, getString(R.string.nfs_set_gpu_driver), getResources().getStringArray(R.array.nfs_gpu_labels), gpuIndex, (position) ->
            preferences.edit().putString(PREF_GPU_DRIVER, GPU_VALUES[position]).apply());

        // PERFORMANS
        LinearLayout perf = addCard(right, getString(R.string.nfs_set_perf));
        String savedCpu = preferences.getString(PREF_CPU_CORES, CPU_VALUES[0]);
        int cpuIndex = 0;
        for (int i = 0; i < CPU_VALUES.length; i++) if (CPU_VALUES[i].equals(savedCpu)) cpuIndex = i;
        addSpinnerRow(perf, getString(R.string.nfs_set_cpu), getResources().getStringArray(R.array.nfs_cpu_labels), cpuIndex, (position) ->
            preferences.edit().putString(PREF_CPU_CORES, CPU_VALUES[position]).apply());

        int fps = preferences.getInt(PREF_FPS_LIMIT, 0);
        int fpsIndex = 0;
        for (int i = 0; i < FPS_VALUES.length; i++) if (FPS_VALUES[i] == fps) fpsIndex = i;
        addSpinnerRow(perf, getString(R.string.nfs_set_fps), getResources().getStringArray(R.array.nfs_fps_labels), fpsIndex, (position) ->
            preferences.edit().putInt(PREF_FPS_LIMIT, FPS_VALUES[position]).apply());

        // GÖSTƏRİCİ
        LinearLayout hud = addCard(right, getString(R.string.nfs_set_hud_title));
        addSpinnerRow(hud, getString(R.string.nfs_set_hud), getResources().getStringArray(R.array.nfs_hud_labels), preferences.getInt(PREF_HUD_MODE, 0), (position) ->
            preferences.edit().putInt(PREF_HUD_MODE, position).apply());

        TextView note = new TextView(this);
        note.setText(R.string.nfs_set_note);
        note.setTextSize(12);
        note.setTextColor(MUTED);
        note.setPadding(0, 0, 0, dp(10));
        right.addView(note, new LinearLayout.LayoutParams(match, wrap));

        // Dəyişikliklər seçən kimi saxlanılır; düymə təsdiqləyib bağlayır.
        TextView save = new TextView(this);
        save.setText(R.string.nfs_set_save);
        save.setTextSize(16);
        save.setTypeface(Typeface.DEFAULT_BOLD);
        save.setLetterSpacing(0.1f);
        save.setTextColor(BG);
        save.setGravity(Gravity.CENTER);
        save.setBackgroundColor(YELLOW);
        save.setOnClickListener((v) -> {
            Toast.makeText(this, R.string.nfs_set_saved, Toast.LENGTH_SHORT).show();
            finish();
        });
        right.addView(save, new LinearLayout.LayoutParams(match, dp(52)));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(body, new ViewGroup.LayoutParams(match, wrap));
        root.addView(scrollView, new LinearLayout.LayoutParams(match, 0, 1f));
        setContentView(root);
    }

    private void applyImmersive() {
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    private interface OnPick { void onPick(int position); }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout addCard(LinearLayout column, String titleText) {
        final int match = ViewGroup.LayoutParams.MATCH_PARENT;
        final int wrap = ViewGroup.LayoutParams.WRAP_CONTENT;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackgroundColor(CARD);
        View bar = new View(this);
        bar.setBackgroundColor(YELLOW);
        card.addView(bar, new LinearLayout.LayoutParams(dp(4), match));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(10), dp(16), dp(6));
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(15);
        title.setTextColor(0xFFFFFFFF);
        title.setLetterSpacing(0.08f);
        title.setPadding(0, 0, 0, dp(6));
        content.addView(title, new LinearLayout.LayoutParams(match, wrap));
        card.addView(content, new LinearLayout.LayoutParams(0, wrap, 1f));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(match, wrap);
        params.bottomMargin = dp(12);
        column.addView(card, params);
        return content;
    }

    private void addRow(LinearLayout content, String labelText, View control) {
        final int wrap = ViewGroup.LayoutParams.WRAP_CONTENT;
        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        content.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextSize(14);
        label.setTextColor(MUTED);
        row.addView(label, new LinearLayout.LayoutParams(0, wrap, 1f));
        row.addView(control, new LinearLayout.LayoutParams(wrap, wrap));
        content.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, wrap));
    }

    private ArrayAdapter<String> makeAdapter(String[] labels) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, labels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView)super.getView(position, convertView, parent);
                view.setText(getItem(position) + " \u25BE");
                view.setTextColor(0xFFFFFFFF);
                view.setTextSize(14);
                view.setSingleLine(true);
                view.setBackgroundColor(0x00000000);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView)super.getDropDownView(position, convertView, parent);
                view.setTextColor(0xFFFFFFFF);
                view.setTextSize(14);
                view.setBackgroundColor(0xFF17171A);
                return view;
            }
        };
    }

    private Spinner addSpinnerRow(LinearLayout content, String labelText, String[] labels, int selected, OnPick listener) {
        Spinner spinner = new Spinner(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(BG);
        bg.setStroke(dp(1), LINE);
        spinner.setBackground(bg);
        spinner.setPopupBackgroundDrawable(new ColorDrawable(0xFF17171A));
        spinner.setPadding(dp(8), 0, dp(8), 0);
        spinner.setMinimumWidth(dp(120));
        spinner.setAdapter(makeAdapter(labels));
        spinner.setSelection(selected);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                listener.onPick(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        addRow(content, labelText, spinner);
        return spinner;
    }

    private void fillSizes(String aspect) {
        sizeValues.clear();
        sizeLabels.clear();
        String[] sizes = aspect.equals("5:4") ? SIZES_54 : SIZES_43;
        for (String size : sizes) {
            sizeValues.add(size);
            boolean heavy = size.equals("1280x960") || size.equals("1280x1024") || size.equals("1600x1200");
            String shown = size.replace('x', '\u00d7');
            sizeLabels.add(heavy ? shown + getString(R.string.nfs_set_heavy) : shown);
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
            sizeSpinner.setAdapter(makeAdapter(sizeLabels.toArray(new String[0])));
            sizeSpinner.setSelection(index);
        }
    }
}
