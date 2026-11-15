package com.winlator.widget;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.winlator.R;
import com.winlator.box64.Box64Utils;
import com.winlator.core.CPUStatus;
import com.winlator.core.StringUtils;

import java.util.Locale;

public class FrameRating extends FrameLayout implements Runnable {
    public enum Mode {DISABLED, SIMPLE, FULL}
    private long lastTime = 0;
    private short frameCount = 0;
    private float lastFPS = 0;
    private final LinearLayout fpsPanel;
    private final LinearLayout gpuPanel;
    private final LinearLayout ramPanel;
    private final LinearLayout cpuPanel;
    private Mode mode = Mode.SIMPLE;
    private ActivityManager activityManager;
    private ActivityManager.MemoryInfo memoryInfo;
    private String cpuInfo = null;
    private byte tick = 0;
    private boolean userHidden = false; // NFS_FPS_V1

    // NFS_HUD_V2: extra diagnostics (frame time, temperature, thermal status, per-core clocks)
    private static final String[] THERMAL_NAMES = {"normal", "light", "moderate", "severe", "critical", "emergency", "shutdown"};
    private long lastFrameNanos = 0;
    private float maxFrameMs = 0;
    private volatile float shownMaxFrameMs = 0;
    private TextView extraView = null;

    public FrameRating(Context context) {
        this(context, null);
    }

    public FrameRating(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FrameRating(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        View view = LayoutInflater.from(context).inflate(R.layout.frame_rating, this, false);
        fpsPanel = view.findViewById(R.id.LLFPSPanel);
        gpuPanel = view.findViewById(R.id.LLGPUPanel);
        ramPanel = view.findViewById(R.id.LLRAMPanel);
        cpuPanel = view.findViewById(R.id.LLCPUPanel);
        addView(view);
        createExtraView(context); // NFS_HUD_V2
        setupPanels();
    }

    // NFS_HUD_V2: extra text line is added under the CPU panel. If the panel's parent is not a
    // vertical LinearLayout, nothing is added (HUD stays as before).
    private void createExtraView(Context context) {
        try {
            ViewGroup parent = (ViewGroup)cpuPanel.getParent();
            if (parent instanceof LinearLayout && ((LinearLayout)parent).getOrientation() == LinearLayout.VERTICAL) {
                TextView ref = (TextView)cpuPanel.getChildAt(1);
                extraView = new TextView(context);
                extraView.setTextColor(ref.getTextColors());
                extraView.setTextSize(TypedValue.COMPLEX_UNIT_PX, ref.getTextSize());
                extraView.setTypeface(ref.getTypeface());
                extraView.setHorizontallyScrolling(true); // NFS_HUD_V2: no wrapping, box grows to the longest line
                extraView.setPadding(cpuPanel.getPaddingLeft(), 0, cpuPanel.getPaddingRight(), cpuPanel.getPaddingBottom());
                extraView.setVisibility(GONE);
                parent.addView(extraView);
            }
        }
        catch (Throwable e) {
            extraView = null;
        }
    }

    private void setupPanels() {
        switch (mode) {
            case DISABLED:
                fpsPanel.setVisibility(GONE);
                gpuPanel.setVisibility(GONE);
                ramPanel.setVisibility(GONE);
                cpuPanel.setVisibility(GONE);
                
                activityManager = null;
                memoryInfo = null;
                break;
            case SIMPLE:
                fpsPanel.setVisibility(VISIBLE);
                gpuPanel.setVisibility(GONE);
                ramPanel.setVisibility(GONE);
                cpuPanel.setVisibility(GONE);

                activityManager = null;
                memoryInfo = null;
                break;
            case FULL:
                fpsPanel.setVisibility(VISIBLE);
                gpuPanel.setVisibility(VISIBLE);
                ramPanel.setVisibility(VISIBLE);
                cpuPanel.setVisibility(VISIBLE);

                Context context = getContext();
                activityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
                memoryInfo = new ActivityManager.MemoryInfo();
                break;
        }
        if (extraView != null) extraView.setVisibility(mode == Mode.FULL ? VISIBLE : GONE); // NFS_HUD_V2
    }

    public boolean isUserHidden() {
        return userHidden;
    }

    public void setUserHidden(boolean hidden) {
        userHidden = hidden;
        if (hidden) setVisibility(View.GONE);
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        setupPanels();
    }

    public void setGPUInfo(String gpuInfo) {
        post(() -> ((TextView)gpuPanel.getChildAt(1)).setText(gpuInfo));
    }

    public void reset() {
        frameCount = 0;
        lastTime = SystemClock.elapsedRealtime();
        lastFPS = 0;
        tick = 2;
        lastFrameNanos = 0; // NFS_HUD_V2
        maxFrameMs = 0;
        shownMaxFrameMs = 0;
    }

    public void update() {
        // NFS_HUD_V2: longest frame time in the current 500 ms window
        long nowNs = System.nanoTime();
        if (lastFrameNanos != 0) {
            float ms = (nowNs - lastFrameNanos) / 1000000f;
            if (ms > maxFrameMs) maxFrameMs = ms;
        }
        lastFrameNanos = nowNs;

        long time = SystemClock.elapsedRealtime();
        if (time >= lastTime + 500) {
            lastFPS = ((float)(frameCount * 1000) / (time - lastTime));
            shownMaxFrameMs = maxFrameMs; // NFS_HUD_V2
            maxFrameMs = 0;
            post(this);
            lastTime = time;
            frameCount = 0;
        }

        frameCount++;
    }

    // NFS_HUD_V2: battery temperature in Celsius, -1 if unavailable
    private float getBatteryTemp() {
        try {
            Intent intent = getContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent == null) return -1;
            int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            return temp > 0 ? temp / 10f : -1;
        }
        catch (Throwable e) {
            return -1;
        }
    }

    // NFS_HUD_V2: Android thermal status (0 = normal ... 6 = shutdown), -1 if unavailable
    private int getThermalStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1;
        try {
            PowerManager pm = (PowerManager)getContext().getSystemService(Context.POWER_SERVICE);
            return pm != null ? pm.getCurrentThermalStatus() : -1;
        }
        catch (Throwable e) {
            return -1;
        }
    }

    // NFS_HUD_V2
    private void updateExtraInfo(short[] clockSpeeds) {
        if (extraView == null) return;

        // NFS_HUD_V2: every line is kept short so it fits the width of the CPU line (the box width)
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ENGLISH, "Frame max %.0f ms", shownMaxFrameMs));

        float batteryTemp = getBatteryTemp();
        int thermal = getThermalStatus();
        if (batteryTemp > 0 || thermal >= 0) {
            sb.append("\n");
            if (batteryTemp > 0) sb.append(String.format(Locale.ENGLISH, "%.1f\u00b0C", batteryTemp));
            if (batteryTemp > 0 && thermal >= 0) sb.append(" | ");
            if (thermal >= 0) {
                sb.append("Thermal ").append(thermal < THERMAL_NAMES.length ? THERMAL_NAMES[thermal] : String.valueOf(thermal));
            }
        }

        if (clockSpeeds != null && clockSpeeds.length > 0) {
            for (int i = 0; i < clockSpeeds.length; i += 4) {
                int last = Math.min(i + 4, clockSpeeds.length) - 1;
                sb.append("\n[").append(i).append("-").append(last).append("]");
                for (int j = i; j <= last; j++) {
                    sb.append(String.format(Locale.ENGLISH, " %.1f", clockSpeeds[j] / 1000f));
                }
            }
        }
        else {
            sb.append("\nCores: n/a");
        }
        extraView.setText(sb.toString());
    }

    @Override
    public void run() {
        if (userHidden) return;
        if (getVisibility() == GONE) setVisibility(View.VISIBLE);
        ((TextView)fpsPanel.getChildAt(1)).setText(String.format(Locale.ENGLISH, "%.1f", lastFPS));

        if (mode == Mode.FULL && ++tick >= 2) {
            tick = 0;
            activityManager.getMemoryInfo(memoryInfo);
            long usedMem = memoryInfo.totalMem - memoryInfo.availMem;
            String ramText = StringUtils.formatBytes(usedMem, false)+"/"+StringUtils.formatBytes(memoryInfo.totalMem);
            ((TextView)ramPanel.getChildAt(1)).setText(ramText);

            if (cpuInfo == null) {
                cpuInfo = "Box64 v"+ Box64Utils.extractBinVersion(cpuPanel.getContext());
            }

            short[] clockSpeeds = CPUStatus.getCurrentClockSpeeds();
            int maxClockSpeed = 0;
            for (short clockSpeed : clockSpeeds) maxClockSpeed = Math.max(maxClockSpeed, clockSpeed);
            ((TextView)cpuPanel.getChildAt(1)).setText(CPUStatus.formatClockSpeed(maxClockSpeed)+" | "+cpuInfo);

            updateExtraInfo(clockSpeeds); // NFS_HUD_V2
        }
    }
}
