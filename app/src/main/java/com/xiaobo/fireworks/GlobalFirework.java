package com.xiaobo.fireworks;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Canvas;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;

public class GlobalFirework {
    private static GlobalFirework instance;
    private WeakReference<FireworkView> currentViewRef;
    private SoundPool soundPool;
    private int soundId = -1;
    private Vibrator vibrator;
    private boolean isInit = false;

    // ========== 新增：壁纸模式成员 ==========
    private FireworkView wallpaperFireworkView;
    private boolean isWallpaperMode = false;
    // =======================================

    private GlobalFirework() {
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void startCountdown(int seconds, Runnable onFinish) {
        if (seconds < 0) {
            if (onFinish != null) onFinish.run();
            return;
        }

        FireworkView view = getCurrentFireworkView();
        if (view == null) return;

        float centerX = view.getWidth() / 2f;
        float centerY = view.getHeight() / 2f;

        FireworkConfig countConfig = new FireworkConfig.Builder()
                .shape(FireworkConfig.Shape.TEXT)
                .text(seconds == 0 ? "GO!" : String.valueOf(seconds))
                .range(1.2f)
                .count(200)
                .duration(1.5f)
                .colors(0xFFFFD700)
                .build();

        bloom(centerX, centerY, countConfig);

        mainHandler.postDelayed(() -> {
            if (seconds > 0) {
                startCountdown(seconds - 1, onFinish);
            } else {
                if (onFinish != null) onFinish.run();
            }
        }, 1000);
    }

    public static GlobalFirework getInstance() {
        if (instance == null) synchronized (GlobalFirework.class) {
            if (instance == null) instance = new GlobalFirework();
        }
        return instance;
    }

    public void init(Application app) {
        init(app, -1);
    }

    /**
     * 原有 Activity 模式初始化，逻辑完全不变
     */
    public void init(Application app, int soundResId) {
        if (isInit) return;
        isInit = true;
        isWallpaperMode = false;

        vibrator = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
        soundPool = new SoundPool.Builder().setMaxStreams(5)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME).build()).build();
        if (soundResId != -1) soundId = soundPool.load(app, soundResId, 1);

        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
                attach(activity);
                if (currentViewRef != null && currentViewRef.get() != null)
                    currentViewRef.get().resume();
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (currentViewRef != null && currentViewRef.get() != null)
                    currentViewRef.get().stop();
            }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }

    // ========== 新增：壁纸模式 4 个核心方法 ==========

    /**
     * 壁纸模式初始化（不要和 init(Application) 混用）
     * @param soundResId 音效资源，传 -1 关闭音效
     */
    public void initForWallpaper(Context context, int soundResId) {
        if (isWallpaperMode) return;
        isWallpaperMode = true;
        isInit = true;

        // 初始化音效与震动
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        soundPool = new SoundPool.Builder().setMaxStreams(3)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME).build()).build();
        if (soundResId != -1) soundId = soundPool.load(context, soundResId, 1);

        // 创建独立的离屏渲染 View，不依附任何 Activity
        wallpaperFireworkView = new FireworkView(context.getApplicationContext());
        wallpaperFireworkView.resume();
    }

    /**
     * 设置壁纸画布尺寸
     */
    public void setWallpaperSize(int width, int height) {
        if (wallpaperFireworkView == null) return;
        // 手动测量与布局，让 View 获得正确宽高
        int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
        wallpaperFireworkView.measure(widthSpec, heightSpec);
        wallpaperFireworkView.layout(0, 0, width, height);
    }

    /**
     * 更新一帧并绘制到 Canvas（壁纸渲染循环调用）
     */
    public void updateAndDraw(Canvas canvas) {
        if (wallpaperFireworkView == null) return;
        wallpaperFireworkView.update();
        wallpaperFireworkView.drawToCanvas(canvas);
    }

    /**
     * 释放壁纸资源
     */
    public void releaseWallpaper() {
        if (wallpaperFireworkView != null) {
            wallpaperFireworkView.stop();
            wallpaperFireworkView = null;
        }
        isWallpaperMode = false;
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        vibrator = null;
        isInit = false;
    }
    // ==========================================

    private void attach(Activity activity) {
        ViewGroup root = activity.findViewById(android.R.id.content);
        FireworkView view = (FireworkView) root.findViewWithTag("GlobalFirework");
        if (view == null) {
            view = new FireworkView(activity);
            view.setTag("GlobalFirework");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) view.setElevation(100f);
            root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        }
        currentViewRef = new WeakReference<>(view);
    }

    /**
     * 统一获取当前生效的烟花视图，自动适配两种模式
     */
    private FireworkView getCurrentFireworkView() {
        if (isWallpaperMode) {
            return wallpaperFireworkView;
        } else {
            return (currentViewRef != null) ? currentViewRef.get() : null;
        }
    }

    public void bloom(float x, float y) {
        bloom(x, y, null);
    }

    /**
     * 发射烟花，自动兼容 Activity 模式与壁纸模式
     */
    public void bloom(float x, float y, FireworkConfig config) {
        FireworkView view = getCurrentFireworkView();
        if (view != null) {
            view.launch(x, y, config);
            playFeedback();
        }
    }

    private void playFeedback() {
        if (soundId != -1) soundPool.play(soundId, 0.5f, 0.5f, 1, 0, 1.0f);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= 26)
                vibrator.vibrate(VibrationEffect.createOneShot(15, 80));
            else vibrator.vibrate(15);
        }
    }
}
