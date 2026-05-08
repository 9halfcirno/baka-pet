package com.cirno9half.bakapet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.WindowManager;
import androidx.core.app.NotificationCompat;
import com.quickjs.JSContext;
import com.quickjs.JSValueHelper;
import com.quickjs.QuickJS;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import android.net.Uri;
import java.io.InputStream;
// 在 PetService 顶部添加 import
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

public class PetService extends Service {

    private WindowManager wm;
    private QuickJS quickJS;
    private JSContext jsContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Map<Integer, PetInstance> instanceMap = new ConcurrentHashMap<>();
    private int nextId = 0;
    // 类内部添加成员
    private BroadcastReceiver configReceiver;
    // 音频播放
    private SoundPool soundPool;
    private final Map<String, Integer> soundCache = new HashMap<>();

    public static class Pet {
        private final PetInstance instance;

        Pet(PetInstance instance) {
            this.instance = instance;
        }

        public void setPosition(int x, int y) {
            instance.setPosition(x, y);
        }

        public void move(int dx, int dy) {
            instance.move(dx, dy);
        }

        public int getX() {
            return instance.getX();
        }

        public int getY() {
            return instance.getY();
        }

        public void setScale(float sx, float sy) {
            instance.setScale(sx, sy);
        }

        public void updateImg(String path) {
            instance.updateImg(path);
        }

        public void setAttr(String key, Object val) {
            instance.setAttr(key, val);
        }

        public Object getAttr(String key) {
            return instance.getAttr(key);
        }

        public boolean hasAttr(String key) {
            return instance.hasAttr(key);
        }

        public void deleteAttr(String key) {
            instance.deleteAttr(key);
        }

        public boolean isDragging() {
            return instance.isDragging();
        }

        public void destroy() {
            instance.destroy();
        }

        public int getInstanceId() {
            return instance.getInstanceId();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // 全局崩溃日志
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.File f = new java.io.File(getExternalFilesDir(null), "crash_log.txt");
                java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(f, true));
                e.printStackTrace(pw);
                pw.close();
            } catch (Exception ignored) {
            }
            System.exit(1);
        });

        // 前台通知，我也不知道这个代码有没有用
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String CHANNEL_ID = "pet_service_channel";
            NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "桌宠服务", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
            startForeground(1, new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Baka Running")
                    .setContentText("有一只Baka在你的屏幕上")
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .build());
        }

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        AudioAttributes audioAttrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            soundPool = new SoundPool.Builder()
                    .setMaxStreams(4)
                    .setAudioAttributes(audioAttrs)
                    .build();
        } else {
            soundPool = new SoundPool(4, AudioManager.STREAM_MUSIC, 0);
        }

        quickJS = QuickJS.createRuntime();
        jsContext = quickJS.createContext();

        // 注入屏幕尺寸
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        jsContext.executeVoidScript(String.format(
                "var device = { width: %d, height: %d };", metrics.widthPixels, metrics.heightPixels),
                "device.js");

        // 默认创建一只 9
        addPet("pets/cirno");
        /*addPet(
        "/storage/emulated/0/Android/data/com.cirno9half.bakapet/files/pets/suika"
        );*/
        registerConfigReceiver();
    }

    private void registerConfigReceiver() {
        configReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateDeviceInfo();
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED);
        registerReceiver(configReceiver, filter);
    }

    private void updateDeviceInfo() {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        String script = String.format(
                "device.width = %d; device.height = %d;",
                metrics.widthPixels, metrics.heightPixels);
        jsContext.executeVoidScript(script, "device_update");
    }

    private void registerInstanceMethods(int instanceId) {
        String suffix = "_" + instanceId;

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                if (inst != null)
                    inst.setPosition((int) args.getDouble(0), (int) args.getDouble(1));
                return null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_setPosition" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                if (inst != null) inst.move((int) args.getDouble(0), (int) args.getDouble(1));
                return null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_move" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                return inst != null ? inst.getX() : 0;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_getX" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                return inst != null ? inst.getY() : 0;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_getY" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                if (inst != null) inst.setScaleX((float) args.getDouble(0));
                return null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_setScaleX" + suffix);

        // 获取 scaleX
        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                return inst != null ? inst.getScaleX() : 1.0;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_getScaleX" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                if (inst != null)
                    inst.setScale((float) args.getDouble(0), (float) args.getDouble(1));
                return null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_setScale" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                if (inst != null) inst.updateImg(args.getString(0));
                return null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_updateImg" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                return inst != null && inst.isDragging();
            } finally {
                JSValueHelper.close(args);
            }
        }, "_dragging" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                return inst != null && inst.getAnimationController().isLastFrame();
            } finally {
                JSValueHelper.close(args);
            }
        }, "_isLastFrame" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                return inst != null ? inst.getWidth() : 0;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_getImgWidth" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                return inst != null ? inst.getHeight() : 0;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_getImgHeight" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                if (inst != null) inst.setAttr(args.getString(0), args.getString(1));
                return null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_setAttr" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                return inst != null ? inst.getAttr(args.getString(0)) : null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_getAttr" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                return inst != null && inst.hasAttr(args.getString(0));
            } finally {
                JSValueHelper.close(args);
            }
        }, "_hasAttr" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                if (inst != null) inst.deleteAttr(args.getString(0));
                return null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_deleteAttr" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                if (inst != null) inst.getAnimationController().switchTo(args.getString(0));
                return null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_animSwitchTo" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                if (inst != null) inst.getActionController().switchTo(args.getString(0));
                return null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_actionSwitchTo" + suffix);

        // 播放音频桥接
        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                String audioName = args.getString(0);
                PetInstance inst = instanceMap.get(instanceId);
                if (inst != null) playAudio(inst, audioName);
                return null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_playAudio" + suffix);
    }

    /** 根据桌宠屏幕位置播放双声道音频 */
    private void playAudio(PetInstance inst, String audioName) {
        String fullPath = inst.getAssetPath() + "/audio/" + audioName;
        Integer soundId = soundCache.get(fullPath);
        if (soundId == null) {
            try {

                if (fullPath.startsWith("/")) {

                    soundId = soundPool.load(fullPath, 1);

                } else {

                    try (AssetFileDescriptor afd = getAssets().openFd(fullPath)) {

                        soundId = soundPool.load(afd, 1);
                    }
                }

                soundCache.put(fullPath, soundId);

            } catch (Exception e) {

                e.printStackTrace();

                return;
            }
        }

        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float screenW = dm.widthPixels;
        float petCenterX = inst.getX() + inst.getWidth() / 2f;
        float pan = (petCenterX - screenW / 2f) / (screenW / 2f); // -1（最左）到 1（最右）

        float leftVol = Math.min(1f, 1f - pan);
        float rightVol = Math.min(1f, 1f + pan);

        soundPool.play(soundId, leftVol, rightVol, 1, 0, 1.0f);
    }

    private String createPetObject(int instanceId) {
        String varName = "__pet_" + instanceId;
        String suffix = "_" + instanceId;

        String script = String.format(
                "var %1$s = {\n" +
                        "  setPosition: _setPosition%2$s,\n" +
                        "  move: _move%2$s,\n" +
                        "  getX: _getX%2$s,\n" +
                        "  getY: _getY%2$s,\n" +
                        "  setScale: function(x, y) {\n" +
                        "    if (x === undefined) x = 1.0;\n" +
                        "    if (y === undefined) y = x;\n" +
                        "    _setScale%2$s(x, y);\n" +
                        "  },\n" +
                        "  updateImg: _updateImg%2$s,\n" +
                        "  setAttr: _setAttr%2$s,\n" +
                        "  getAttr: _getAttr%2$s,\n" +
                        "  hasAttr: _hasAttr%2$s,\n" +
                        "  deleteAttr: _deleteAttr%2$s,\n" +
                        "  animation: { switchTo: _animSwitchTo%2$s },\n" +
                        "  action: { switchTo: _actionSwitchTo%2$s },\n" +
                        "  playAudio: _playAudio%2$s,\n" +
                        "  _view: {},\n" +
                        "  getImageView: function() { return this._view; }\n" +
                        "};\n" +
                        "Object.defineProperty(%1$s, 'dragging', { get: _dragging%2$s });\n" +
                        "Object.defineProperty(%1$s, 'x', { get: _getX%2$s });\n" +
                        "Object.defineProperty(%1$s, 'y', { get: _getY%2$s });\n" +
                        "Object.defineProperty(%1$s.animation, 'isLastFrame', { get: _isLastFrame%2$s });\n" +
                        "Object.defineProperty(%1$s._view, 'width', { get: _getImgWidth%2$s });\n" +
                        "Object.defineProperty(%1$s._view, 'height', { get: _getImgHeight%2$s });\n" +
                        "Object.defineProperty(%1$s._view, 'scaleX', { \n" +
                        "  get: function() { return _getScaleX%2$s(); }, \n" +
                        "  set: function(v) { _setScaleX%2$s(v); } \n" +
                        "});",
                varName, suffix);

        jsContext.executeVoidScript(script, "pet_init_" + instanceId);
        return varName;
    }



    public Pet addPet(String assetPath) {
        int id = nextId++;
        registerInstanceMethods(id);
        String petVarName = createPetObject(id);
        PetInstance inst = new PetInstance(this, id, assetPath, petVarName, mainHandler, jsContext);

        // 先将实例放入 Map，用于 JS 回调定位，再执行后续初始化
        instanceMap.put(id, inst);
        inst.init(); // 调用 PetInstance 的 init()

        return new Pet(inst);
    }

    public void removePet(Pet pet) {
        PetInstance inst = instanceMap.remove(pet.getInstanceId());
        if (inst != null) {
            inst.destroy();
        }
    }

    public void removeAllPets() {
        for (int id : instanceMap.keySet()) {
            PetInstance inst = instanceMap.remove(id);
            if (inst != null) inst.destroy();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeAllPets();
        if (soundPool != null) {
            soundPool.release();
        }
        if (configReceiver != null) {
            unregisterReceiver(configReceiver);
        }

        if (jsContext != null) jsContext.close();
        if (quickJS != null) quickJS.close();
    }

    @Override
    public int onStartCommand(Intent intent,
            int flags,
            int startId) {
        if (intent != null
                && "import_pet_zip".equals(intent.getAction())) {
            String uriStr = intent.getStringExtra("zip_uri");
            if (uriStr != null) {
                new Thread(() -> {
                    try {
                        Uri uri = Uri.parse(uriStr);
                        File zipFile = copyUriToCache(uri);
                        File petDir = importPetZip(
                        zipFile.getAbsolutePath()
                        );
                        addPet(
                        petDir.getAbsolutePath()
                        );
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // 内部使用
    WindowManager getWm() {
        return wm;
    }

    JSContext getJsContext() {
        return jsContext;
    }

    Handler getMainHandler() {
        return mainHandler;
    }

    public File importPetZip(String zipPath) throws Exception {

        File baseDir = new File(getExternalFilesDir(null), "pets");
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }

        // zip文件名作为宠物目录名
        String zipName = new File(zipPath).getName();

        if (zipName.endsWith(".zip")) {
            zipName = zipName.substring(
                    0,
                    zipName.length() - 4
            );
        }

        File petRoot = new File(baseDir, zipName);
        if (!petRoot.exists()) {
            petRoot.mkdirs();
        }

        java.util.Set<String> topLevels = new java.util.HashSet<>();

        ZipInputStream scanZis = new ZipInputStream(
        new FileInputStream(zipPath)
        );
        ZipEntry scanEntry;

        while ((scanEntry = scanZis.getNextEntry()) != null) {
            String name = scanEntry.getName();
            if (name.isEmpty()) continue;
            String[] parts = name.split("/");
            if (parts.length > 0) {
                topLevels.add(parts[0]);
            }
            scanZis.closeEntry();
        }
        scanZis.close();

        // 是否存在唯一顶层目录
        boolean hasSingleRoot = topLevels.size() == 1;
        String rootFolder = null;

        if (hasSingleRoot) {
            rootFolder = topLevels.iterator().next();
        }


        ZipInputStream zis = new ZipInputStream(
        new FileInputStream(zipPath)
        );

        ZipEntry entry;
        byte[] buffer = new byte[4096];

        while ((entry = zis.getNextEntry()) != null) {
            String entryName = entry.getName();
            if (hasSingleRoot
                    && entryName.startsWith(rootFolder + "/")) {
                entryName = entryName.substring(
                        rootFolder.length() + 1
                );
            }

            // 跳过空路径
            if (entryName.isEmpty()) {
                zis.closeEntry();
                continue;
            }
            File outFile = new File(petRoot, entryName);

            // 防 Zip Slip
            String canonicalRoot = petRoot.getCanonicalPath();
            String canonicalOut = outFile.getCanonicalPath();

            if (!canonicalOut.startsWith(canonicalRoot)) {
                throw new SecurityException("非法Zip路径");
            }

            if (entry.isDirectory()) {
                outFile.mkdirs();
            } else {
                File parent = outFile.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                FileOutputStream fos = new FileOutputStream(outFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }
            zis.closeEntry();
        }
        zis.close();
        // 验证 pet.json
        File petJson = new File(petRoot, "pet.json");

        if (!petJson.exists()) {

            throw new RuntimeException(
            "非法Pet包，缺少pet.json"
            );
        }

        return petRoot;
    }

    private File copyUriToCache(Uri uri) throws Exception {

        String fileName = "pet.zip";

        android.database.Cursor cursor = getContentResolver().query(
                uri,
                null,
                null,
                null,
                null
        );

        if (cursor != null) {

            int nameIndex = cursor.getColumnIndex(
                    android.provider.OpenableColumns.DISPLAY_NAME
            );

            if (nameIndex >= 0
                    && cursor.moveToFirst()) {

                fileName = cursor.getString(nameIndex);
            }

            cursor.close();
        }

        File outFile = new File(getCacheDir(), fileName);

        try (InputStream is = getContentResolver()
                        .openInputStream(uri);
                FileOutputStream fos = new FileOutputStream(outFile)) {

            byte[] buffer = new byte[4096];

            int len;

            while ((len = is.read(buffer)) != -1) {

                fos.write(buffer, 0, len);
            }
        }

        return outFile;
    }
}
