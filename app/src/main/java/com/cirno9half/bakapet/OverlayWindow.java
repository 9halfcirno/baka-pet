package com.cirno9half.bakapet;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

public class OverlayWindow {

    public enum AnimDirection {
        TOP, // 从顶部滑入/滑出
        BOTTOM, // 从底部滑入/滑出
        CENTER // 淡入淡出 + 缩放
    }

    private final Activity activity;
    private final View contentView;
    private final AnimDirection animDirection;
    private final boolean dismissOnBackPressed;
    private final boolean dismissOnOutsideClick;

    private ViewGroup container; // 全屏容器
    private View overlayBg; // 半透明背景
    private ViewGroup contentContainer; // 内容容器
    private boolean isShowing = false;

    private static final int DURATION = 300;

    private OverlayWindow(Builder builder) {
        this.activity = builder.activity;
        this.contentView = builder.contentView;
        this.animDirection = builder.animDirection;
        this.dismissOnBackPressed = builder.dismissOnBackPressed;
        this.dismissOnOutsideClick = builder.dismissOnOutsideClick;

        initView();
    }

    private void initView() {
        container = new FrameLayout(activity);
        container.setLayoutParams(new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT));
        container.setVisibility(View.GONE);
        //container.setClickable(true);
        // 防止点击卡片内容时穿透到背景蒙层（不产生水波纹动画）
        container.setOnClickListener(v -> {}); // 空实现，拦截事件但不显示涟漪

        // 半透明背景
        overlayBg = new View(activity);
        overlayBg.setLayoutParams(new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT));
        overlayBg.setBackgroundColor(0x80000000);
        overlayBg.setAlpha(0f);
        container.addView(overlayBg);

        // 内容容器
        contentContainer = new FrameLayout(activity);
        contentContainer.setLayoutParams(new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT));

        int marginPx = (int) (16 * activity.getResources().getDisplayMetrics().density); // 16dp
        contentContainer.setPadding(marginPx, marginPx, marginPx, marginPx);

        switch (animDirection) {
            case TOP:
                contentContainer.setTranslationY(-getScreenHeight());
                break;
            case BOTTOM:
                contentContainer.setTranslationY(getScreenHeight());
                break;
            case CENTER:
                contentContainer.setScaleX(0.8f);
                contentContainer.setScaleY(0.8f);
                contentContainer.setAlpha(0f);
                break;
        }
        container.addView(contentContainer);

        contentContainer.addView(contentView);

        //contentView.setClickable(true);
        //contentView.setFocusable(true);
        // 防止点击卡片内容时穿透到背景蒙层（不产生水波纹动画）
contentView.setOnClickListener(v -> {}); // 空实现，拦截事件但不显示涟漪
        // 设置点击背景关闭
        if (dismissOnOutsideClick) {
            overlayBg.setOnClickListener(v -> dismiss());
        }
        // 设置返回键关闭
        if (dismissOnBackPressed) {
            container.setFocusableInTouchMode(true);
            container.requestFocus();
            container.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP && isShowing) {
                    dismiss();
                    return true;
                }
                return false;
            });
        }

        ViewGroup root = activity.findViewById(android.R.id.content);
        root.addView(container);
    }

    private int getScreenHeight() {
        return activity.getResources().getDisplayMetrics().heightPixels;
    }

    public void show() {
        if (isShowing) return;
        isShowing = true;
        container.setVisibility(View.VISIBLE);

        overlayBg.animate()
                .alpha(1f)
                .setDuration(DURATION)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        switch (animDirection) {
            case TOP:
                contentContainer.animate()
                        .translationY(0)
                        .setDuration(DURATION)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                break;
            case BOTTOM:
                contentContainer.animate()
                        .translationY(0)
                        .setDuration(DURATION)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                break;
            case CENTER:
                contentContainer.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(DURATION)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                break;
        }
    }

    public void dismiss() {
        if (!isShowing) return;
        isShowing = false;

        overlayBg.animate()
                .alpha(0f)
                .setDuration(DURATION)
                .setInterpolator(new AccelerateInterpolator())
                .start();

        switch (animDirection) {
            case TOP:
                contentContainer.animate()
                        .translationY(-getScreenHeight())
                        .setDuration(DURATION)
                        .setInterpolator(new AccelerateInterpolator())
                        .withEndAction(() -> container.setVisibility(View.GONE))
                        .start();
                break;
            case BOTTOM:
                contentContainer.animate()
                        .translationY(getScreenHeight())
                        .setDuration(DURATION)
                        .setInterpolator(new AccelerateInterpolator())
                        .withEndAction(() -> container.setVisibility(View.GONE))
                        .start();
                break;
            case CENTER:
                contentContainer.animate()
                        .scaleX(0.8f)
                        .scaleY(0.8f)
                        .alpha(0f)
                        .setDuration(DURATION)
                        .setInterpolator(new AccelerateInterpolator())
                        .withEndAction(() -> container.setVisibility(View.GONE))
                        .start();
                break;
        }
    }

    public boolean isShowing() {
        return isShowing;
    }

    @SuppressWarnings("unchecked")
    public <T extends View> T findViewById(int id) {
        return contentView.findViewById(id);
    }

    public static class Builder {
        private final Activity activity;
        private View contentView;
        private int layoutResId;
        private AnimDirection animDirection = AnimDirection.TOP;
        private boolean dismissOnBackPressed = true;
        private boolean dismissOnOutsideClick = true;

        public Builder(Activity activity) {
            this.activity = activity;
        }

        public Builder setContentView(View view) {
            this.contentView = view;
            return this;
        }

        public Builder setContentView(int layoutResId) {
            this.layoutResId = layoutResId;
            return this;
        }

        public Builder setAnimDirection(AnimDirection direction) {
            this.animDirection = direction;
            return this;
        }

        public Builder setDismissOnBackPressed(boolean dismiss) {
            this.dismissOnBackPressed = dismiss;
            return this;
        }

        public Builder setDismissOnOutsideClick(boolean dismiss) {
            this.dismissOnOutsideClick = dismiss;
            return this;
        }

        public OverlayWindow build() {
            if (contentView == null && layoutResId != 0) {
                contentView = LayoutInflater.from(activity).inflate(layoutResId, null);
            }
            if (contentView == null) {
                throw new IllegalStateException("必须设置 contentView 或 layoutResId");
            }
            return new OverlayWindow(this);
        }
    }
}