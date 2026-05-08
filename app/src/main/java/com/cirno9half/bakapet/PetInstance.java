package com.cirno9half.bakapet;

import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import com.quickjs.JSContext;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class PetInstance {

    private final PetService service;
    private final int instanceId;
    private final String assetPath;
    private final boolean external;
    private final String petVarName;

    private final PetView petView;
    private final WindowManager wm;
    private final WindowManager.LayoutParams params;

    private final AnimationController animController;
    private final ActionController actionController;

    private boolean dragging = false;
    private final Map<String, Object> attrs = new HashMap<>();
    private final Handler mainHandler;

    private static final int LONG_PRESS_TIMEOUT = 500; // 长按判定时长ms
    private static final float TOUCH_SLOP = 10; // 移动阈值，小于此距离视为点击
    private long downTime;
    private float downX, downY;
    private boolean hasMoved = false;
    private boolean isLongPressed = false;
    private Runnable longPressRunnable;
    private float savedDownParamsX, savedDownParamsY; // 记录按下时的窗口坐标，便于拖动

    public PetInstance(PetService service, int instanceId, String assetPath,
            String petVarName, Handler mainHandler, JSContext jsContext) {
        this.service = service;
        this.instanceId = instanceId;
        this.assetPath = assetPath;
        this.external = assetPath.startsWith("/");
        this.petVarName = petVarName;
        this.mainHandler = mainHandler;
        this.wm = service.getWm();


        petView = new PetView(service);
        params = new WindowManager.LayoutParams(
        150, 150,
        Build.VERSION.SDK_INT >= 26 ? 2038 : 2002,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        petView.setOnTouchListener(new TouchHandler());
        wm.addView(petView, params);

        // 初始在屏幕中间
        android.util.DisplayMetrics dm = service.getResources().getDisplayMetrics();
        params.x = (dm.widthPixels - params.width) / 2;
        params.y = (dm.heightPixels - params.height) / 2;
        wm.updateViewLayout(petView, params);

        // 控制器
        animController = new AnimationController(
        this,
        petView,
        external
        );
        actionController = new ActionController(this, jsContext, petVarName);

    }

    public void init() {
        loadResources();
        startLoop();
    }

    private class TouchHandler implements View.OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = true;
                    hasMoved = false;
                    isLongPressed = false;
                    downTime = System.currentTimeMillis();
                    downX = e.getRawX();
                    downY = e.getRawY();
                    // 记录按下时的窗口坐标，用于后续拖动的增量计算
                    savedDownParamsX = params.x;
                    savedDownParamsY = params.y;

                    // 取消之前的长按任务（如果有）
                    if (longPressRunnable != null) {
                        mainHandler.removeCallbacks(longPressRunnable);
                    }

                    // 准备长按检测
                    longPressRunnable = () -> {
                        if (!hasMoved && !isLongPressed) {
                            isLongPressed = true;
                            actionController.onHold((int) downX, (int) downY);
                        }
                    };
                    mainHandler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT);
                    break;

                case MotionEvent.ACTION_MOVE:
                    float movedX = e.getRawX() - downX;
                    float movedY = e.getRawY() - downY;
                    float distance = (float) Math.hypot(movedX, movedY);

                    if (!isLongPressed && distance > TOUCH_SLOP) {
                        // 移动超过阈值，取消长按检测
                        if (longPressRunnable != null) {
                            mainHandler.removeCallbacks(longPressRunnable);
                        }
                        hasMoved = true;
                        dragging = true;
                    }

                    if (dragging || isLongPressed) {
                        // 拖动
                        params.x = (int) (e.getRawX() - downX + savedDownParamsX);
                        params.y = (int) (e.getRawY() - downY + savedDownParamsY);
                        wm.updateViewLayout(petView, params);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    // 取消长按任务
                    if (longPressRunnable != null) {
                        mainHandler.removeCallbacks(longPressRunnable);
                    }

                    if (!hasMoved && !isLongPressed) {
                        // 短按
                        actionController.onTouch((int) downX, (int) downY);
                    }
                    dragging = false;
                    break;

                case MotionEvent.ACTION_CANCEL:
                    if (longPressRunnable != null) {
                        mainHandler.removeCallbacks(longPressRunnable);
                    }
                    dragging = false;
                    break;
            }
            return true;
        }
    }

    private void loadResources() {
        try {
            JSONObject config = new JSONObject(readAsset(assetPath + "/pet.json"));
            if (config.has("size")) {
                int px = (int) (Integer.parseInt(config.getString("size").replace("dp", ""))
                                * service.getResources().getDisplayMetrics().density);
                params.width = params.height = px;
                mainHandler.post(() -> wm.updateViewLayout(petView, params));
            }
            JSONObject motions = config.getJSONObject("motions");
            for (Iterator<String> it = motions.keys(); it.hasNext(); ) {
                String key = it.next();
                animController.load(key, assetPath + "/motions/" + motions.getString(key));
            }
            JSONObject actions = config.getJSONObject("actions");
            for (Iterator<String> it = actions.keys(); it.hasNext(); ) {
                String key = it.next();
                if (!key.equals("default")) {
                    String script = readAsset(assetPath + "/actions/" + actions.getString(key));
                    actionController.load(key, script);
                }
            }
            if (actions.has("default")) {
                actionController.switchTo(actions.getString("default"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startLoop() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                animController.update();
                actionController.update();
                mainHandler.postDelayed(this, 16);
            }
        });
    }

    private String readAsset(String path) throws Exception {

        InputStream is;

        if (external) {
            is = new java.io.FileInputStream(path);
        } else {
            is = service.getAssets().open(path);
        }

        try (InputStream input = is;
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {

            byte[] buffer = new byte[4096];

            int len;

            while ((len = input.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }

            return bos.toString("UTF-8");
        }
    }

    public void setPosition(int x, int y) {
        params.x = x;
        params.y = y;
        mainHandler.post(() -> wm.updateViewLayout(petView, params));
    }

    public void move(int dx, int dy) {
        params.x += dx;
        params.y += dy;
        mainHandler.post(() -> wm.updateViewLayout(petView, params));
    }

    public int getX() {
        return params.x;
    }

    public int getY() {
        return params.y;
    }

    public int getWidth() {
        return params.width;
    }

    public int getHeight() {
        return params.height;
    }

    public boolean isDragging() {
        return dragging;
    }

    public void setScaleX(float sx) {
        mainHandler.post(() -> petView.setScaleX(sx));
    }

    public void setScale(float sx, float sy) {
        mainHandler.post(() -> {
            petView.setScaleX(sx);
            petView.setScaleY(sy);
        });
    }

    public float getScaleX() {
        return petView.getScaleX();
    }

    public String getAssetPath() {
        return assetPath;
    }

    public void updateImg(String path) {
        mainHandler.post(() ->
                petView.updateImg(path, external));
    }

    public void setAttr(String key, Object val) {
        attrs.put(key, val);
    }

    public Object getAttr(String key) {
        return attrs.get(key);
    }

    public boolean hasAttr(String key) {
        return attrs.containsKey(key);
    }

    public void deleteAttr(String key) {
        attrs.remove(key);
    }

    public AnimationController getAnimationController() {
        return animController;
    }

    public ActionController getActionController() {
        return actionController;
    }

    public String getPetVarName() {
        return petVarName;
    }

    public int getInstanceId() {
        return instanceId;
    }

    public void destroy() {
        mainHandler.removeCallbacksAndMessages(null);
        wm.removeView(petView);
        actionController.destroy();
        petView.clearCache();
    }
}