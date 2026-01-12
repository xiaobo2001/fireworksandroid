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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.util.Random;

/**
 * 全局烟花管理器
 * 优化：加入细腻的“小震动”反馈 (Haptic Feedback)
 */
public class GlobalFirework {

    private static GlobalFirework instance;
    private WeakReference<FireworkView> currentFireworkViewRef;
    private static final String VIEW_TAG = "GlobalFireworkView";

    private SoundPool soundPool;
    private int soundId = -1;
    private Vibrator vibrator;
    private Random random = new Random();
    private boolean isInit = false;
    private long lastBloomTime = 0; // 防抖时间戳

    private GlobalFirework() {
    }

    public static GlobalFirework getInstance() {
        if (instance == null) {
            synchronized (GlobalFirework.class) {
                if (instance == null) instance = new GlobalFirework();
            }
        }
        return instance;
    }

    public void init(Application application, int soundResId) {
        if (isInit) return;
        isInit = true;

        initHardware(application, soundResId);

        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
                attachViewToActivity(activity);
                FireworkView view = getFireworkView(activity);
                if (view != null) view.resume();
            }

            @Override
            public void onActivityPaused(Activity activity) {
                // 页面跳转/切后台时立即停止，释放资源
                FireworkView view = getFireworkView(activity);
                if (view != null) view.stop();
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }

    private FireworkView getFireworkView(Activity activity) {
        ViewGroup rootView = activity.findViewById(android.R.id.content);
        View view = rootView.findViewWithTag(VIEW_TAG);
        if (view instanceof FireworkView) {
            return (FireworkView) view;
        }
        return null;
    }

    private void attachViewToActivity(Activity activity) {
        ViewGroup rootView = activity.findViewById(android.R.id.content);
        if (rootView.findViewWithTag(VIEW_TAG) != null) {
            currentFireworkViewRef = new WeakReference<>((FireworkView) rootView.findViewWithTag(VIEW_TAG));
            return;
        }

        FireworkView fireworkView = new FireworkView(activity);
        fireworkView.setTag(VIEW_TAG);

        // 防止被状态栏遮挡
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            fireworkView.setElevation(100f);
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        fireworkView.setLayoutParams(params);

        rootView.addView(fireworkView);
        currentFireworkViewRef = new WeakReference<>(fireworkView);

        // 全局触摸监听：点哪里炸哪里
        fireworkView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                bloom(event.getX(), event.getY());
            }
            return false; // 返回 false 以允许点击事件穿透到下层按钮
        });
    }

    private void initHardware(Context context, int soundResId) {
        try {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder()
                    .setMaxStreams(5)
                    .setAudioAttributes(audioAttributes)
                    .build();

            if (soundResId != -1) {
                soundId = soundPool.load(context, soundResId, 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 触发烟花效果
     */
    public void bloom(float x, float y) {
        FireworkView view = (currentFireworkViewRef != null) ? currentFireworkViewRef.get() : null;
        if (view != null) {
            view.launch(x, y);
        }

        long now = System.currentTimeMillis();
        if (now - lastBloomTime > 60) {
            lastBloomTime = now;

            // 1. 音效
            if (soundId != -1 && soundPool != null) {
                float rate = 0.9f + random.nextFloat() * 0.3f;
                soundPool.play(soundId, 0.4f, 0.4f, 1, 0, rate);
            }

            // 2. 震动 (极致适配 安卓 16)
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // 【安卓 13 ~ 安卓 16 专用】：必须带上 VibrationAttributes
                    // USAGE_MEDIA 属性可以绕过“触摸震动”被关闭的限制
                    android.os.VibrationAttributes attributes = new android.os.VibrationAttributes.Builder()
                            .setUsage(android.os.VibrationAttributes.USAGE_MEDIA)
                            .build();
                    // 20毫秒，强度100 (轻脆手感)
                    vibrator.vibrate(VibrationEffect.createOneShot(20, 100), attributes);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // 安卓 8.0 ~ 12.0
                    vibrator.vibrate(VibrationEffect.createOneShot(20, 100));
                } else {
                    // 旧版本
                    vibrator.vibrate(20);
                }
            }
        }
    }
}