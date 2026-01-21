package com.xiaobo.fireworks;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
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

    private GlobalFirework() {}

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
                if (currentViewRef != null && currentViewRef.get() != null) currentViewRef.get().resume();
            }
            @Override
            public void onActivityPaused(Activity activity) {
                if (currentViewRef != null && currentViewRef.get() != null) currentViewRef.get().stop();
            }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
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

    /**
     * 基础调用：使用默认配置
     */
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
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(15, 80));
            else vibrator.vibrate(15);
        }
    }
}