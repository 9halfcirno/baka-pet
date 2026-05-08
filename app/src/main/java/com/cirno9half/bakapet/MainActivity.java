package com.cirno9half.bakapet;

import android.view.View;
import com.cirno9half.bakapet.R;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_OVERLAY_PERMISSION = 1001; //
    private static final int REQUEST_PICK_ZIP = 1002;
    private View settingOverlay;
    private int screenHeight;
    private OverlayWindow settingWindow;
    private OverlayWindow aboutWindow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 设置全局异常捕获
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            saveCrashLog(throwable);
            System.exit(1);
        });
        super.onCreate(savedInstanceState);

        // 检查悬浮窗权限
        if (checkOverlayPermission()) {
            startFloatService();
        } else {
            requestOverlayPermission();
        }

        setContentView(R.layout.activity_main);

        settingWindow = new OverlayWindow.Builder(this)
                .setContentView(R.layout.setting_layout) // 简化后的布局
                .setAnimDirection(OverlayWindow.AnimDirection.TOP)
                .setDismissOnBackPressed(true)
                .setDismissOnOutsideClick(true)
                .build();

        MaterialButton btnClose = settingWindow.findViewById(R.id.btn_close_setting);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> settingWindow.dismiss());
        }

        MaterialButton btnSettings = findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(v -> settingWindow.show());

        MaterialButton btnUploadZip = findViewById(R.id.btn_upload_zip);
        btnUploadZip.setOnClickListener(v -> openZipPicker());

        // 创建关于窗口
        aboutWindow = new OverlayWindow.Builder(this)
                .setContentView(R.layout.about_layout)
                .setAnimDirection(OverlayWindow.AnimDirection.TOP)
                .setDismissOnBackPressed(true)
                .setDismissOnOutsideClick(true)
                .build();

        // 关闭按钮
        MaterialButton btnCloseAbout = aboutWindow.findViewById(R.id.btn_close_about);
        btnCloseAbout.setOnClickListener(v -> aboutWindow.dismiss());
        
        // 左上角信息图标点击显示窗口
        MaterialButton btnAbout = findViewById(R.id.btn_about);
        btnAbout.setOnClickListener(v -> aboutWindow.show());
    }

    private void saveCrashLog(Throwable t) {
        try {
            java.io.File dir = getExternalFilesDir(null);
            if (dir != null) {
                java.io.File file = new java.io.File(dir, "crash_log.txt");
                java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(file, true));
                pw.println("\n--- 崩溃时间: " + new java.util.Date().toString() + " ---");
                t.printStackTrace(pw);
                pw.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this); //
        }
        return true;
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + getPackageName())); //
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (checkOverlayPermission()) {
                Toast.makeText(this, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show(); //
                startFloatService();
            } else {
                Toast.makeText(this, "未授予悬浮窗权限，无法启动", Toast.LENGTH_SHORT).show(); //
            }
        } else if (requestCode == REQUEST_PICK_ZIP
                && resultCode == RESULT_OK
                && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                Intent intent = new Intent(this, PetService.class);
                intent.setAction("import_pet_zip");
                intent.putExtra("zip_uri", uri.toString());
                startService(intent);
            }
        }
    }

    private void startFloatService() {
        Intent serviceIntent = new Intent(this, PetService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void openZipPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");

        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{
                        "application/zip",
                        "application/x-zip-compressed",
                        "*/*"
                });

        startActivityForResult(intent, REQUEST_PICK_ZIP);
    }

}
