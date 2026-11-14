package com.winlator;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
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
    private TextView statusView;
    private AppCompatButton pickButton;
    private AppCompatButton startButton;
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
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        buildUI();

        if (!requestAppPermissions()) startInstallIfNeeded();
        handler.post(poller);
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

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText("Need for Speed Most Wanted");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusView = new TextView(this);
        statusView.setTextSize(15);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, dp(16), 0, dp(24));
        root.addView(statusView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        pickButton = new AppCompatButton(this);
        pickButton.setText("Qovluq seç");
        pickButton.setOnClickListener((v) -> pickFolder());
        root.addView(pickButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        startButton = new AppCompatButton(this);
        startButton.setText("Başlat");
        startButton.setOnClickListener((v) -> startGame());
        root.addView(startButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AppCompatButton advancedButton = new AppCompatButton(this);
        advancedButton.setText("Əlavə ayarlar");
        advancedButton.setAllCaps(false);
        advancedButton.setOnClickListener((v) -> startActivity(new Intent(this, NfsSettingsActivity.class)));
        LinearLayout.LayoutParams advancedParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        advancedParams.topMargin = dp(32);
        root.addView(advancedButton, advancedParams);

        setContentView(root);
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
        if (!ready) text = "Sistem faylları quraşdırılır...";
        else if (gameDir == null) text = "Oyun qovluğunu seçin\n(içində "+EXE_NAME+" olmalıdır)";
        else text = "Qovluq:\n"+gameDir;
        statusView.setText(text);
        startButton.setEnabled(ready && gameDir != null && !busy);
        pickButton.setEnabled(!busy);
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
            Toast.makeText(this, "Qovluğun yolu müəyyən edilə bilmədi", Toast.LENGTH_LONG).show();
            return;
        }
        if (findExe(new File(path)) == null) {
            Toast.makeText(this, EXE_NAME+" bu qovluqda tapılmadı", Toast.LENGTH_LONG).show();
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
                    Toast.makeText(this, "Container yaradıla bilmədi", Toast.LENGTH_LONG).show();
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
