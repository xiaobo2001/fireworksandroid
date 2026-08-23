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

    // 壁纸模式专属渲染实例，与App内完全隔离
    private FireworkView wallpaperFireworkView;

    private GlobalFirework() {
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void startCountdown(int seconds, Runnable onFinish) {
        if (seconds < 0) {
            if (onFinish != null) onFinish.run();
            return;
        }

        // 【原有逻辑不变】仅作用于当前Activity的烟花视图
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
     * 【原有逻辑完全不变】App内Activity模式初始化
     */
    public void init(Application app, int soundResId) {
        if (isInit) return;
        isInit = true;

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

            @Override
            public void onActivityCreated(Activity a, Bundle b) {
            }

            @Override
            public void onActivityStarted(Activity a) {
            }

            @Override
            public void onActivityStopped(Activity a) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity a, Bundle b) {
            }

            @Override
            public void onActivityDestroyed(Activity a) {
            }
        });
    }

    /**
     * 壁纸模式初始化，仅创建壁纸专属渲染器，不影响App内原有逻辑
     *
     * @param soundResId 音效资源，传-1关闭音效
     */
    public void initForWallpaper(Context context, int soundResId) {
        // 已有壁纸渲染器则直接返回，不重复创建
        if (wallpaperFireworkView != null) return;

        // 公共资源未初始化（壁纸先于App启动的场景）才初始化
        if (!isInit) {
            isInit = true;
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            soundPool = new SoundPool.Builder().setMaxStreams(5)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME).build()).build();
        }

        // 传入有效音效且未加载时，才加载音效
        if (soundResId != -1 && soundId == -1 && soundPool != null) {
            soundId = soundPool.load(context, soundResId, 1);
        }

        // 创建壁纸独立的离屏渲染视图
        wallpaperFireworkView = new FireworkView(context.getApplicationContext());
        wallpaperFireworkView.resume();
    }

    /**
     * 设置壁纸画布尺寸
     */
    public void setWallpaperSize(int width, int height) {
        if (wallpaperFireworkView == null) return;
        int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
        wallpaperFireworkView.measure(widthSpec, heightSpec);
        wallpaperFireworkView.layout(0, 0, width, height);
    }

    /**
     * 【壁纸专属】发射烟花，不影响App内的bloom方法
     */
    public void bloomWallpaper(float x, float y, FireworkConfig config) {
        if (wallpaperFireworkView != null) {
            wallpaperFireworkView.launch(x, y, config);
            playFeedback();
        }
    }

    /**
     * 【壁纸专属】更新一帧并绘制到画布
     */
    public void updateAndDrawWallpaper(Canvas canvas) {
        if (wallpaperFireworkView == null) return;
        wallpaperFireworkView.update();
        wallpaperFireworkView.drawToCanvas(canvas);
    }

    /**
     * 释放壁纸专属资源，绝不触碰App公共资源
     */
    public void releaseWallpaper() {
        if (wallpaperFireworkView != null) {
            wallpaperFireworkView.stop();
            wallpaperFireworkView = null;
        }

    }

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


    private FireworkView getCurrentFireworkView() {
        return (currentViewRef != null) ? currentViewRef.get() : null;
    }

    public void bloom(float x, float y) {
        bloom(x, y, null);
    }


    public void bloom(float x, float y, FireworkConfig config) {
        FireworkView view = getCurrentFireworkView();
        if (view != null) {
            view.launch(x, y, config);
            playFeedback();
        }
    }

    private void playFeedback() {
        // 空值兜底，所有场景安全
        if (soundPool != null && soundId != -1) {
            soundPool.play(soundId, 0.5f, 0.5f, 1, 0, 1.0f);
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= 26)
                vibrator.vibrate(VibrationEffect.createOneShot(15, 80));
            else vibrator.vibrate(15);
        }
    }
}
