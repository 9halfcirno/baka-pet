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
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Paint;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.TextView;

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
// 气泡相关
    private TextView bubbleView;
    private WindowManager.LayoutParams bubbleParams;
    private boolean isSaying = false;
    private String currentBubbleText;
    private int typewriterIndex;
    private Runnable typewriterRunnable;
    private Runnable dismissRunnable;
    private static final int TYPEWRITER_INTERVAL = 50; // ms
    private static final long DISPLAY_AFTER_FINISH = 2000L; // 说完后停留 2 秒
    private final Handler bubbleHandler = new Handler(Looper.getMainLooper());

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
                    updateBubblePosition();
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
        mainHandler.post(() -> {
            wm.updateViewLayout(petView, params);
            updateBubblePosition();
        });
    }

    public void move(int dx, int dy) {
        params.x += dx;
        params.y += dy;
        mainHandler.post(() -> {
            wm.updateViewLayout(petView, params);
            updateBubblePosition();
        });
    }

    public void say(String text) {
        if (text == null || text.isEmpty()) return;

        // 1. 如果有正在进行的说话，停止它
        if (isSaying) {
            stopTypewriter();
        }

        // 2. 立即更新当前完整文本（用于后续定位）
        currentBubbleText = text;
        typewriterIndex = 0;
        isSaying = true;

        // 3. 确保气泡视图存在
        if (bubbleView == null) {
            createBubble();
        }

        // 4. 基于新的文本长度重新计算气泡位置（此时测量宽度是短文本宽度）
        updateBubblePosition();

        // 5. 清空视图并显示
        bubbleView.setText("");
        bubbleView.setVisibility(View.VISIBLE);

        // 6. 启动打字机动画（保持不变）
        typewriterRunnable = new Runnable() {
            @Override
            public void run() {
                typewriterIndex++;
                if (bubbleView != null) {
                    String part = currentBubbleText.substring(0, Math.min(typewriterIndex, currentBubbleText.length()));
                    bubbleView.setText(part);
                }
                if (typewriterIndex < currentBubbleText.length()) {
                    bubbleHandler.postDelayed(this, TYPEWRITER_INTERVAL);
                } else {
                    dismissRunnable = () -> dismissBubble();
                    bubbleHandler.postDelayed(dismissRunnable, DISPLAY_AFTER_FINISH);
                }
            }
        };
        bubbleHandler.post(typewriterRunnable);
    }

    private void createBubble() {
        bubbleView = new TextView(service);
        bubbleView.setTextColor(Color.BLACK);
        bubbleView.setTextSize(14);
        bubbleView.setPadding(16, 8, 16, 8);

        // 背景：白色圆角矩形带阴影
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(16);
        bg.setStroke(1, Color.GRAY);
        // 设置阴影 (API 29 以下需要 setLayerType)
        bubbleView.setBackground(bg);
        bubbleView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        bubbleView.setElevation(4); // 投影

        // 窗口参数：不拦截触摸，跟随宠物移动
        bubbleParams = new WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.LEFT;

        wm.addView(bubbleView, bubbleParams);
        updateBubblePosition(); // 初始定位
    }

    private void stopTypewriter() {
        if (typewriterRunnable != null) {
            bubbleHandler.removeCallbacks(typewriterRunnable);
        }
        if (dismissRunnable != null) {
            bubbleHandler.removeCallbacks(dismissRunnable);
        }
    }

    private void dismissBubble() {
        stopTypewriter();
        if (bubbleView != null) {
            bubbleView.setVisibility(View.GONE);
        }
        isSaying = false;
        currentBubbleText = null;
    }

    private void updateBubblePosition() {
        if (bubbleView == null) return;

        // 宠物当前位置
        int petX = params.x;
        int petY = params.y;
        int petW = params.width;
        int petH = params.height;

        int bubbleW, bubbleH;

        // 如果正在说话，用完整文本估算宽度（避免测量空字符串或碎片文本）
        if (isSaying && currentBubbleText != null && !currentBubbleText.isEmpty()) {
            // 计算文本宽度
            float textWidth = bubbleView.getPaint().measureText(currentBubbleText);
            int paddingLeft = bubbleView.getPaddingLeft();
            int paddingRight = bubbleView.getPaddingRight();
            bubbleW = (int) (textWidth + paddingLeft + paddingRight + 0.5f);
            // 文本高度可用单行高度估算
            Paint.FontMetrics fm = bubbleView.getPaint().getFontMetrics();
            bubbleH = (int) (fm.bottom - fm.top + bubbleView.getPaddingTop() + bubbleView.getPaddingBottom() + 0.5f);
        } else {
            // 无说话状态，使用视图当前测量（此时可能有宽度，如之前显示过）
            bubbleW = bubbleView.getMeasuredWidth();
            bubbleH = bubbleView.getMeasuredHeight();
            if (bubbleW <= 0) {
                // 如果从未测量过，给个最小值防止算到屏幕外
                bubbleW = 100;
                bubbleH = 40;
            }
        }

        // 默认位置：宠物正上方，偏移 8px
        int bubbleX = petX + (petW - bubbleW) / 2;
        int bubbleY = petY - bubbleH - 8;

        // 屏幕边界修正
        DisplayMetrics dm = service.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;

        if (bubbleX < 0) bubbleX = 0;
        if (bubbleX + bubbleW > screenW) bubbleX = screenW - bubbleW;
        if (bubbleY < 0) {
            bubbleY = petY + petH + 8;
            if (bubbleY + bubbleH > screenH) {
                bubbleY = screenH - bubbleH;
            }
        }

        bubbleParams.x = bubbleX;
        bubbleParams.y = bubbleY;
        wm.updateViewLayout(bubbleView, bubbleParams);
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

    public boolean isSaying() {
        return isSaying;
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
        bubbleHandler.removeCallbacksAndMessages(null);
        if (bubbleView != null) {
            wm.removeView(bubbleView);
            bubbleView = null;
        }
        wm.removeView(petView);
        actionController.destroy();
        petView.clearCache();
    }
}
