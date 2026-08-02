package com.xiaobo.fireworks;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
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

    private GlobalFirework() {
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * 启动倒计时烟花
     *
     * @param seconds  从几秒开始倒计时
     * @param onFinish 倒计时结束后的回调 (可以用来触发最后的大爆发)
     */
    public void startCountdown(int seconds, Runnable onFinish) {
        if (seconds < 0) {
            if (onFinish != null) onFinish.run();
            return;
        }

        // 获取屏幕中心点
        FireworkView view = (currentViewRef != null) ? currentViewRef.get() : null;
        if (view == null) return;

        float centerX = view.getWidth() / 2f;
        float centerY = view.getHeight() / 2f;

        // 创建倒计时专用的配置
        FireworkConfig countConfig = new FireworkConfig.Builder()
                .shape(FireworkConfig.Shape.TEXT)
                .text(seconds == 0 ? "GO!" : String.valueOf(seconds))
                .range(1.2f)        // 稍微大一点的爆炸范围
                .count(200)         // 粒子数量多一点，数字才清晰
                .duration(1.5f)     // 停留时间稍微长一点
                .colors(0xFFFFD700) // 金色
                .build();

        // 发射烟花
        bloom(centerX, centerY, countConfig);

        // 递归调用，实现每秒一次
        mainHandler.postDelayed(() -> {
            if (seconds > 0) {
                startCountdown(seconds - 1, onFinish);
            } else {
                if (onFinish != null) onFinish.run();
            }
        }, 1000); // 间隔 1 秒
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
     * 初始化
     *
     * @param soundResId 烟花音效资源ID (例如 R.raw.firework)，传 -1 则无声
     */
    public void init(Application app, int soundResId) {
        if (isInit) return;
        isInit = true;

        // 初始化硬件
        vibrator = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
        soundPool = new SoundPool.Builder().setMaxStreams(5)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME).build()).build();
        if (soundResId != -1) soundId = soundPool.load(app, soundResId, 1);

        // 自动挂载 View 到每个 Activity
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


    public void bloom(float x, float y) {
        bloom(x, y, null);
    }

    /**
     * 高级调用：使用自定义配置
     */
    public void bloom(float x, float y, FireworkConfig config) {
        FireworkView view = (currentViewRef != null) ? currentViewRef.get() : null;
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