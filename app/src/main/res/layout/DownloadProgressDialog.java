package com.winlator.core;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import android.widget.ProgressBar;

import com.winlator.R;
import com.winlator.math.Mathf;

public class DownloadProgressDialog {
    private final Activity activity;
    private Dialog dialog;

    public DownloadProgressDialog(Activity activity) {
        this.activity = activity;
    }

    private void create() {
        if (dialog != null) return;
        dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(R.layout.download_progress_dialog);

        Window window = dialog.getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
    }

    public void show() {
        show(null);
    }

    public void show(int textResId) {
        show(textResId, null);
    }

    public void show(Runnable onCancelCallback) {
        show(0, onCancelCallback);
    }

    public void show(int textResId, final Runnable onCancelCallback) {
        if (isShowing()) return;
        close();
        if (dialog == null) create();

        // NFS_DESIGN_V2: sistem faylları quraşdırılanda Azərbaycanca başlıq və izah göstərilir
        boolean isInstall = textResId == R.string.installing_system_files;
        if (isInstall) textResId = R.string.nfs_install_title;
        if (textResId > 0) ((TextView)dialog.findViewById(R.id.TextView)).setText(textResId);
        dialog.findViewById(R.id.TVHint).setVisibility(isInstall ? View.VISIBLE : View.GONE);

        setProgress(0);
        if (onCancelCallback != null) {
            dialog.findViewById(R.id.BTCancel).setOnClickListener((v) -> onCancelCallback.run());
            dialog.findViewById(R.id.LLBottomBar).setVisibility(View.VISIBLE);
        }
        dialog.show();
    }

    public void setProgress(int progress) {
        if (dialog == null) return;
        progress = Mathf.clamp(progress, 0, 100);
        ((ProgressBar)dialog.findViewById(R.id.PBProgress)).setProgress(progress);
        ((TextView)dialog.findViewById(R.id.TVProgress)).setText(progress+"%");
    }

    public void close() {
        try {
            if (dialog != null) {
                dialog.dismiss();
            }
        }
        catch (Exception e) {}
    }

    public void closeOnUiThread() {
        activity.runOnUiThread(this::close);
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }
}
