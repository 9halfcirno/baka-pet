package com.cirno9half.bakapet;
import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import android.view.View;
import java.io.InputStream;

public class PetView extends View {
    private Bitmap currentBitmap;
    private final Paint paint;
    private final LruCache<String, Bitmap> bitmapCache;
    // 帧率相关变量
    private long lastFrameTimeNs = 0;
    private float fps = 0f;
    private final Paint textPaint;
    private final Paint bgPaint;
    private final Rect textBounds = new Rect();
    public PetView(Context context) {
        super(context);
        paint = new Paint();
        paint.setFilterBitmap(false); // 保持像素锐利
        paint.setAntiAlias(false);
        bitmapCache = new LruCache<String, Bitmap>(70) {
            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                if (oldValue != null && oldValue != currentBitmap && !oldValue.isRecycled()) {
                    oldValue.recycle();
                }
            }
        };
        // 初始化文字画笔
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40);
        textPaint.setShadowLayer(2, 0, 0, Color.BLACK);
        bgPaint = new Paint();
        bgPaint.setColor(Color.argb(128, 0, 0, 0));
        bgPaint.setStyle(Paint.Style.FILL);
    }
    public void updateImg(String path, boolean external) {
        Bitmap bmp = bitmapCache.get(path);
        if (bmp == null || bmp.isRecycled()) {
            try {
                if (external) {
                    bmp = BitmapFactory.decodeFile(path);
                } else {
                    try (InputStream is = getContext().getAssets().open(path)) {
                        bmp = BitmapFactory.decodeStream(is);
                    }
                }
                if (bmp != null) {
                    bitmapCache.put(path, bmp);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        this.currentBitmap = bmp;
        postInvalidate();
    }
    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        // 绘制宠物图片
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            float scale = Math.min((float) getWidth() / currentBitmap.getWidth(),
                    (float) getHeight() / currentBitmap.getHeight());
            float dstW = currentBitmap.getWidth() * scale;
            float dstH = currentBitmap.getHeight() * scale;
            float dx = (getWidth() - dstW) / 2f;
            float dy = (getHeight() - dstH) / 2f;
            Rect srcRect = new Rect(0, 0, currentBitmap.getWidth(), currentBitmap.getHeight());
            RectF dstRect = new RectF(dx, dy, dx + dstW, dy + dstH);
            canvas.drawBitmap(currentBitmap, srcRect, dstRect, paint);
        }
        
        // long nowNs = System.nanoTime();
        // if (lastFrameTimeNs != 0) {
        // float deltaSec = (nowNs - lastFrameTimeNs) / 1_000_000_000.0f;
        // if (deltaSec > 0) {
        // float instantFps = 1.0f / deltaSec;
        // if (fps == 0) {
        // fps = instantFps;
        // } else {
        // fps = fps * 0.9f + instantFps * 0.1f; // 平滑因子 0.1
        // }
        // }
        // }
        // lastFrameTimeNs = nowNs;

        // String fpsText = String.format("FPS: %.1f", fps);
        // textPaint.getTextBounds(fpsText, 0, fpsText.length(), textBounds);
        // int padding = 10;
        // int textWidth = textBounds.width();
        // int textHeight = textBounds.height();
        // int x = padding;
        // int y = padding + textHeight;

        // canvas.drawRect(x - padding / 2,
        // y - textHeight - padding / 2,
        // x + textWidth + padding / 2,
        // y + padding / 2,
        // bgPaint);
        // 绘制文字
        // canvas.drawText(fpsText, x, y, textPaint);
    }
    public void clearCache() {
        bitmapCache.evictAll();
    }
}