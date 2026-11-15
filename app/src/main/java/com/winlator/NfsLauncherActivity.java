package com.winlator;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.PathShape;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.DXWrappers;
import com.winlator.container.GraphicsDrivers;
import com.winlator.core.AppUtils;
import com.winlator.core.DefaultVersion;
import com.winlator.core.LocaleHelper;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.RootFSInstaller;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public class NfsLauncherActivity extends AppCompatActivity {
    private static final int REQ_PERMISSIONS = 1;
    private static final int REQ_PICK_DIR = 10;
    private static final String PREF_GAME_DIR = "nfs_game_dir";
    private static final String CONTAINER_NAME = "NFSMW";
    private static final String EXE_NAME = "speed.exe";
    private static final String SCREEN_SIZE = "640x480";
    private static final String CPU_LIST = "6,7";
    private static final String DXWRAPPER_CONFIG = "version="+DefaultVersion.MINOR_DXVK;
    private static final String WINCOMPONENTS = "direct3d=1,directsound=1,directmusic=0,directshow=0,directplay=0,xaudio=0,vcrun2005=0,vcrun2010=0,wmdecoder=1";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private static final int YELLOW = 0xFFFFC107;
    private static final int BG = 0xFF0B0B0D;
    private static final int MUTED = 0xFF9A9A9A;

    private TextView statusView;
    private TextView pickButton;
    private TextView startButton;
    private TextView chipRes;
    private TextView chipGpu;
    private TextView chipCpu;
    private TextView chipFps;
    private boolean installStarted = false;
    private boolean busy = false;
    private boolean destroyed = false;

    private final Runnable poller = new Runnable() {
        @Override
        public void run() {
            updateUI();
            if (!destroyed && !isRootFsReady()) handler.postDelayed(this, 700);
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setSystemLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE); // NFS_UI_V1: yalnız landscape
        applyImmersive();
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        buildUI();

        if (!requestAppPermissions()) startInstallIfNeeded();
        handler.post(poller);
    }

    // NFS_UI_V2: status/naviqasiya zolağı gizlədilir, kamera çıxıntısı sahəsi də istifadə olunur
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

    @Override
    protected void onResume() {
        super.onResume();
        if (preferences != null && statusView != null) updateUI();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density);
    }

    private TextView makeChip() {
        TextView chip = new TextView(this);
        chip.setTextSize(12);
        chip.setTextColor(0xFFE6E6E6);
        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF17171A);
        bg.setStroke(dp(1), 0xFF2A2A2E);
        chip.setBackground(bg);
        return chip;
    }

    private View makeStripe() {
        int s = dp(16);
        Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(BG);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(YELLOW);
        Path band = new Path();
        band.moveTo(0, s / 2f); band.lineTo(s / 2f, 0); band.lineTo(s, 0); band.lineTo(0, s); band.close();
        canvas.drawPath(band, paint);
        Path corner = new Path();
        corner.moveTo(s, s / 2f); corner.lineTo(s, s); corner.lineTo(s / 2f, s); corner.close();
        canvas.drawPath(corner, paint);
        BitmapDrawable drawable = new BitmapDrawable(getResources(), bmp);
        drawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        View stripe = new View(this);
        stripe.setBackground(drawable);
        return stripe;
    }

    private void fitBanner(ImageView view) {
        Drawable d = view.getDrawable();
        if (d == null || view.getWidth() == 0 || view.getHeight() == 0) return;
        float scale = Math.max(view.getWidth() / (float)d.getIntrinsicWidth(), view.getHeight() / (float)d.getIntrinsicHeight());
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate((view.getWidth() - d.getIntrinsicWidth() * scale) / 2f, 0); // yuxarıdan hizala: yazı kəsilməsin
        view.setImageMatrix(matrix);
    }

    private String versionName() {
        try {
            return "v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        }
        catch (Exception e) {
            return "";
        }
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.nfs_about)
            .setMessage(R.string.nfs_about_text)
            .setPositiveButton(R.string.nfs_close, null)
            .show();
    }

    private void buildUI() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        final int match = ViewGroup.LayoutParams.MATCH_PARENT;
        final int wrap = ViewGroup.LayoutParams.WRAP_CONTENT;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        root.addView(makeStripe(), new LinearLayout.LayoutParams(match, dp(8)));

        final ImageView banner = new ImageView(this);
        banner.setImageResource(R.drawable.nfs_banner);
        banner.setScaleType(ImageView.ScaleType.MATRIX);
        banner.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) fitBanner(banner);
        });
        root.addView(banner, new LinearLayout.LayoutParams(match, 0, 58f));

        LinearLayout lower = new LinearLayout(this);
        lower.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(36), dp(14), dp(36), 0);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        startButton = new TextView(this);
        startButton.setText(R.string.nfs_start);
        startButton.setTextSize(20);
        startButton.setTypeface(Typeface.DEFAULT_BOLD);
        startButton.setLetterSpacing(0.15f);
        startButton.setTextColor(BG);
        startButton.setGravity(Gravity.CENTER);
        Path cut = new Path();
        cut.moveTo(0, 0); cut.lineTo(100, 0); cut.lineTo(100, 70); cut.lineTo(95, 100); cut.lineTo(0, 100); cut.close();
        ShapeDrawable startBg = new ShapeDrawable(new PathShape(cut, 100, 100));
        startBg.getPaint().setColor(YELLOW);
        startButton.setBackground(startBg);
        startButton.setOnClickListener((v) -> startGame());
        buttons.addView(startButton, new LinearLayout.LayoutParams(dp(190), dp(52)));

        TextView settingsButton = new TextView(this);
        settingsButton.setText(R.string.nfs_settings);
        settingsButton.setTextSize(16);
        settingsButton.setTypeface(Typeface.DEFAULT_BOLD);
        settingsButton.setLetterSpacing(0.15f);
        settingsButton.setTextColor(YELLOW);
        settingsButton.setGravity(Gravity.CENTER);
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(BG);
        outline.setStroke(dp(2), YELLOW);
        settingsButton.setBackground(outline);
        settingsButton.setOnClickListener((v) -> startActivity(new Intent(this, NfsSettingsActivity.class)));
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(150), dp(52));
        settingsParams.leftMargin = dp(14);
        buttons.addView(settingsButton, settingsParams);
        left.addView(buttons, new LinearLayout.LayoutParams(wrap, wrap));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, dp(6), 0, 0);

        statusView = new TextView(this);
        statusView.setTextSize(12);
        statusView.setTextColor(MUTED);
        statusView.setSingleLine(true);
        statusView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        statusView.setMaxWidth(dp(250));
        statusRow.addView(statusView, new LinearLayout.LayoutParams(wrap, wrap));

        pickButton = new TextView(this);
        pickButton.setText(R.string.nfs_pick_folder);
        pickButton.setTextSize(13);
        pickButton.setTextColor(YELLOW);
        pickButton.setPaintFlags(pickButton.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        pickButton.setMinHeight(dp(36));
        pickButton.setGravity(Gravity.CENTER_VERTICAL);
        pickButton.setOnClickListener((v) -> pickFolder());
        LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(wrap, wrap);
        pickParams.leftMargin = dp(12);
        statusRow.addView(pickButton, pickParams);
        left.addView(statusRow, new LinearLayout.LayoutParams(wrap, wrap));

        row.addView(left, new LinearLayout.LayoutParams(wrap, wrap));
        row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);

        LinearLayout chips1 = new LinearLayout(this);
        chips1.setOrientation(LinearLayout.HORIZONTAL);
        chipRes = makeChip();
        chipGpu = makeChip();
        chipCpu = makeChip();
        chipFps = makeChip();
        TextView[] first = {chipRes, chipGpu, chipCpu};
        for (TextView chip : first) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(wrap, wrap);
            p.leftMargin = dp(8);
            chips1.addView(chip, p);
        }
        right.addView(chips1, new LinearLayout.LayoutParams(wrap, wrap));
        LinearLayout.LayoutParams fpsParams = new LinearLayout.LayoutParams(wrap, wrap);
        fpsParams.topMargin = dp(8);
        right.addView(chipFps, fpsParams);
        row.addView(right, new LinearLayout.LayoutParams(wrap, wrap));
        lower.addView(row, new LinearLayout.LayoutParams(match, wrap));
        lower.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(dp(36), 0, dp(36), dp(8));
        TextView about = new TextView(this);
        about.setText(R.string.nfs_about);
        about.setTextSize(13);
        about.setTextColor(MUTED);
        about.setPaintFlags(about.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        about.setMinHeight(dp(36));
        about.setGravity(Gravity.CENTER_VERTICAL);
        about.setOnClickListener((v) -> showAbout());
        bottom.addView(about, new LinearLayout.LayoutParams(wrap, wrap));
        bottom.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView version = new TextView(this);
        version.setText(versionName());
        version.setTextSize(13);
        version.setTextColor(MUTED);
        version.setGravity(Gravity.CENTER_VERTICAL);
        bottom.addView(version, new LinearLayout.LayoutParams(wrap, dp(36)));
        lower.addView(bottom, new LinearLayout.LayoutParams(match, wrap));
        root.addView(lower, new LinearLayout.LayoutParams(match, 0, 42f));

        setContentView(root);
    }

    private String formatCores(String list) {
        java.util.ArrayList<Integer> cores = new java.util.ArrayList<>();
        for (String part : list.split(",")) {
            try { cores.add(Integer.parseInt(part.trim())); } catch (NumberFormatException e) {}
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < cores.size()) {
            int j = i;
            while (j + 1 < cores.size() && cores.get(j + 1) == cores.get(j) + 1) j++;
            if (sb.length() > 0) sb.append(',');
            if (j - i >= 2) sb.append(cores.get(i)).append('-').append(cores.get(j));
            else for (int k = i; k <= j; k++) { if (k > i) sb.append(','); sb.append(cores.get(k)); }
            i = j + 1;
        }
        return sb.toString();
    }

    private boolean requestAppPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) return false;

        String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
        ActivityCompat.requestPermissions(this, permissions, REQ_PERMISSIONS);
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startInstallIfNeeded();
                handler.post(poller);
            }
            else finish();
        }
    }

    private void startInstallIfNeeded() {
        if (installStarted) return;
        installStarted = true;
        RootFSInstaller.installIfNeeded(this);
    }

    private boolean isRootFsReady() {
        try {
            RootFS rootFS = RootFS.find(this);
            return rootFS.isValid() && rootFS.getVersion() >= RootFSInstaller.LATEST_VERSION;
        }
        catch (Exception e) {
            return false;
        }
    }

    private File findExe(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (file.isFile() && file.getName().equalsIgnoreCase(EXE_NAME)) return file;
        }
        return null;
    }

    private String getGameDir() {
        String dir = preferences.getString(PREF_GAME_DIR, null);
        return dir != null && findExe(new File(dir)) != null ? dir : null;
    }

    private void updateUI() {
        boolean ready = isRootFsReady();
        String gameDir = getGameDir();
        String text;
        if (!ready) text = getString(R.string.nfs_status_installing);
        else if (gameDir == null) text = getString(R.string.nfs_status_pick, EXE_NAME);
        else text = getString(R.string.nfs_status_folder, gameDir);
        statusView.setText(text);
        boolean canStart = ready && gameDir != null && !busy;
        startButton.setEnabled(canStart);
        startButton.setAlpha(canStart ? 1f : 0.4f);
        pickButton.setEnabled(!busy);

        String size = preferences.getString(NfsSettingsActivity.PREF_SCREEN_SIZE, NfsSettingsActivity.DEFAULT_SCREEN_SIZE);
        chipRes.setText(size.replace('x', '\u00d7'));
        String gpu = preferences.getString(NfsSettingsActivity.PREF_GPU_DRIVER, "auto");
        chipGpu.setText(getString(R.string.nfs_chip_gpu, gpu.equals("turnip") ? "Turnip" : gpu.equals("vortek") ? "Vortek" : getString(R.string.nfs_gpu_auto)));
        chipCpu.setText(getString(R.string.nfs_chip_cpu, formatCores(preferences.getString(NfsSettingsActivity.PREF_CPU_CORES, CPU_LIST))));
        int fps = preferences.getInt(NfsSettingsActivity.PREF_FPS_LIMIT, 0);
        chipFps.setText(getString(R.string.nfs_chip_fps, fps > 0 ? String.valueOf(fps) : getString(R.string.nfs_unlimited)));
    }

    private void pickFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQ_PICK_DIR);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_DIR || resultCode != RESULT_OK || data == null || data.getData() == null) return;

        String path = treeUriToPath(data.getData());
        if (path == null || !new File(path).isDirectory()) {
            Toast.makeText(this, R.string.nfs_err_path, Toast.LENGTH_LONG).show();
            return;
        }
        if (findExe(new File(path)) == null) {
            Toast.makeText(this, getString(R.string.nfs_err_exe, EXE_NAME), Toast.LENGTH_LONG).show();
            return;
        }
        preferences.edit().putString(PREF_GAME_DIR, path).apply();
        updateUI();
    }

    // Təsdiqlənməyib: yalnız standart "volume:yol" sənəd ID formatı üçün işləyir.
    private String treeUriToPath(Uri uri) {
        try {
            String docId = DocumentsContract.getTreeDocumentId(uri);
            int index = docId.indexOf(':');
            if (index < 0) return null;
            String volume = docId.substring(0, index);
            String relative = docId.substring(index + 1);
            File base = volume.equalsIgnoreCase("primary") ? Environment.getExternalStorageDirectory() : new File("/storage/"+volume);
            return (relative.isEmpty() ? base : new File(base, relative)).getPath();
        }
        catch (Exception e) {
            return null;
        }
    }

    private void startGame() {
        String gameDir = getGameDir();
        if (gameDir == null || !isRootFsReady()) return;
        final File exe = findExe(new File(gameDir));
        final String drives = "D:"+new File(gameDir).getPath();

        busy = true;
        updateUI();

        ContainerManager manager = new ContainerManager(this);
        Container existing = null;
        for (Container container : manager.getContainers()) {
            if (CONTAINER_NAME.equals(container.getName())) {
                existing = container;
                break;
            }
        }

        if (existing != null) {
            existing.setDrives(drives);
            applySettings(existing);
            launch(existing.id, exe);
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("name", CONTAINER_NAME);
            data.put("screenSize", SCREEN_SIZE);
            data.put("graphicsDriver", GraphicsDrivers.getDefaultDriver(this));
            data.put("dxwrapper", DXWrappers.DXVK);
            data.put("dxwrapperConfig", DXWRAPPER_CONFIG);
            data.put("cpuList", CPU_LIST);
            data.put("cpuListWoW64", CPU_LIST);
            data.put("wincomponents", WINCOMPONENTS);
            data.put("drives", drives);

            manager.createContainerAsync(data, (container) -> {
                if (container == null) {
                    busy = false;
                    updateUI();
                    Toast.makeText(this, R.string.nfs_err_container, Toast.LENGTH_LONG).show();
                }
                else {
                    applySettings(container);
                    launch(container.id, exe);
                }
            });
        }
        catch (JSONException e) {
            busy = false;
            updateUI();
        }
    }

    private void applySettings(Container container) {
        String size = preferences.getString(NfsSettingsActivity.PREF_SCREEN_SIZE, NfsSettingsActivity.DEFAULT_SCREEN_SIZE);
        int fps = preferences.getInt(NfsSettingsActivity.PREF_FPS_LIMIT, 0);
        container.setScreenSize(size);
        container.setHUDMode((byte)preferences.getInt(NfsSettingsActivity.PREF_HUD_MODE, 0));

        // NFS_CPU_V1: CPU nüvə seçimi (mövcud olmayan nüvələr çıxarılır)
        String cpuPref = preferences.getString(NfsSettingsActivity.PREF_CPU_CORES, CPU_LIST);
        int cpuCount = Runtime.getRuntime().availableProcessors();
        StringBuilder cpuOut = new StringBuilder();
        for (String core : cpuPref.split(",")) {
            int index;
            try { index = Integer.parseInt(core.trim()); } catch (NumberFormatException e) { continue; }
            if (index < cpuCount) {
                if (cpuOut.length() > 0) cpuOut.append(',');
                cpuOut.append(index);
            }
        }
        if (cpuOut.length() == 0) {
            for (int i = 0; i < cpuCount; i++) {
                if (i > 0) cpuOut.append(',');
                cpuOut.append(i);
            }
        }
        container.setCPUList(cpuOut.toString());
        container.setCPUListWoW64(cpuOut.toString());

        // NFS_GPU_V1: sürücü seçimi (avtomatik = Adreno-da Turnip, qalanında Vortek)
        String gpuPref = preferences.getString(NfsSettingsActivity.PREF_GPU_DRIVER, "auto");
        String wantedDriver;
        if (gpuPref.equals("turnip")) wantedDriver = GraphicsDrivers.TURNIP + "," + GraphicsDrivers.DEFAULT_OPENGL_DRIVER;
        else if (gpuPref.equals("vortek")) wantedDriver = GraphicsDrivers.VORTEK + "," + GraphicsDrivers.DEFAULT_OPENGL_DRIVER;
        else wantedDriver = GraphicsDrivers.getDefaultDriver(this);
        if (!GraphicsDrivers.parseIdentifiers(container.getGraphicsDriver())[0].equals(GraphicsDrivers.parseIdentifiers(wantedDriver)[0])) {
            container.setGraphicsDriver(wantedDriver);
            container.setGraphicsDriverConfig("");
        }

        StringBuilder env = new StringBuilder();
        for (String token : container.getEnvVars().split(" ")) {
            if (!token.isEmpty() && !token.startsWith("DXVK_FRAME_RATE=")) env.append(token).append(' ');
        }
        if (fps > 0) env.append("DXVK_FRAME_RATE=").append(fps);
        container.setEnvVars(env.toString().trim());
        container.saveData();
    }

    private void launch(int containerId, File exe) {
        Intent intent = new Intent(this, XServerDisplayActivity.class);
        intent.putExtra("container_id", containerId);
        intent.putExtra("exec_path", exe.getPath());
        startActivity(intent);
        busy = false;
        updateUI();
    }
}
