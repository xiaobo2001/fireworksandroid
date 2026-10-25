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
import android.os.SystemClock;
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

    // ===== 反馈（音效/震动）控制与防抖 =====
    // 高频连续绽放（如轨迹动画、多点齐射）时逐次触发 SoundPool.play 与震动 Binder
    // 调用是主线程卡顿与耗电的大头；防抖后仅保留间隔内的首次反馈，单次点击不受影响。
    /** 反馈防抖间隔 ms；0 = 每次绽放都反馈（旧行为） */
    private volatile int feedbackIntervalMs = 120;
    private volatile boolean soundEnabled = true;
    private volatile boolean vibrationEnabled = true;
    private volatile float soundVolume = 0.5f;
    private long lastFeedbackMs = 0L;
    private Boolean hasVibratorCache; // 只探测一次，不再逐次 Binder 调用

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

    /** 【v1.2.0】获取当前 Activity 附着的全屏烟花渲染器（离屏/壁纸模式请自行持有 FireworkView） */
    public FireworkView getFireworkView() {
        return getCurrentFireworkView();
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

    /**
     * 【新增】无反馈发射：粒子效果与 bloom 完全一致，但不触发音效与震动。
     * 适合高频连续发射场景（轨迹动画、粒子雨等），彻底免除逐次系统调用。
     */
    public void bloomSilently(float x, float y) {
        bloomSilently(x, y, null);
    }

    /**
     * 【新增】无反馈发射：粒子效果与 bloom 完全一致，但不触发音效与震动。
     */
    public void bloomSilently(float x, float y, FireworkConfig config) {
        FireworkView view = getCurrentFireworkView();
        if (view != null) {
            view.launch(x, y, config);
        }
    }

    /**
     * 【新增】设置反馈防抖间隔 ms（0 = 每次绽放都反馈，即 v1.0.9 及以前的旧行为）。
     * 连续绽放频繁时，反馈最多以此间隔触发一次，消除高频 Binder 调用。
     */
    public void setFeedbackIntervalMs(int intervalMs) {
        this.feedbackIntervalMs = Math.max(0, intervalMs);
    }

    /** 【新增】单独开关音效 */
    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
    }

    /** 【新增】单独开关震动 */
    public void setVibrationEnabled(boolean enabled) {
        this.vibrationEnabled = enabled;
    }

    /** 【新增】设置音效音量 0~1（默认 0.5） */
    public void setSoundVolume(float volume) {
        this.soundVolume = Math.min(1f, Math.max(0f, volume));
    }

    /**
     * 【新增】设置粒子上限（当前 Activity 视图与壁纸视图同时生效，最小 200）。
     * 低配设备可调小以降低满帧绘制压力。
     */
    public void setMaxParticles(int max) {
        FireworkView view = getCurrentFireworkView();
        if (view != null) view.setMaxParticles(max);
        if (wallpaperFireworkView != null) wallpaperFireworkView.setMaxParticles(max);
    }

    /**
     * 反馈防抖：高频连续绽放只保留间隔内的首次反馈。
     * synchronized：App 主线程与壁纸绘制线程都可能调用。
     */
    private synchronized void playFeedback() {        long now = SystemClock.elapsedRealtime();
        if (now - lastFeedbackMs < feedbackIntervalMs) {
            return;
        }
        lastFeedbackMs = now;

        if (soundPool != null && soundId != -1 && soundEnabled) {
            soundPool.play(soundId, soundVolume, soundVolume, 1, 0, 1.0f);
        }
        if (vibrator != null && vibrationEnabled) {
            if (hasVibratorCache == null) {
                hasVibratorCache = vibrator.hasVibrator();
            }
            if (hasVibratorCache) {
                if (Build.VERSION.SDK_INT >= 26)
                    vibrator.vibrate(VibrationEffect.createOneShot(15, 80));
                else vibrator.vibrate(15);
            }
        }
    }

    /**
     * 【v1.3.0】心跳震动："咚-咚"双击节奏（用于告白文字展示等需要情绪节奏的场景）。
     * 遵循振动开关与防抖间隔。
     */
    public void playHeartbeatVibration() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastFeedbackMs < 400L) return;
        lastFeedbackMs = now;
        if (vibrator == null || !vibrationEnabled) return;
        if (hasVibratorCache == null) hasVibratorCache = vibrator.hasVibrator();
        if (hasVibratorCache) {
            long[] pattern = {0, 110, 170, 230}; // 咚-(停)-咚
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        }
    }

    // ===== v1.3.0 编舞化随机播放 =====

    public static final int PATTERN_CLASSIC = 0;   // 经典：随机位置随机样式
    public static final int PATTERN_WAVE = 1;      // 波浪：左→右扫射推进
    public static final int PATTERN_CONVERGE = 2;  // 聚拢：环形布点向中心汇
    public static final int PATTERN_RHYTHM = 3;    // 节拍：跟随 RhythmFirework 音乐律动

    private Runnable autoShowTask;
    private int autoPattern = PATTERN_CLASSIC;
    private int autoIntervalMs = 700;
    private float showAngle = 0f;
    private int showTick = 0;

    /**
     * 【v1.3.0】编舞化随机播放（替代应用侧无章法的定时乱放）。
     * @param pattern PATTERN_CLASSIC / WAVE / CONVERGE / RHYTHM
     * @param intervalMs 每次绽放间隔 ms（RHYTHM 模式下作为无律动时的兑底间隔）
     */
    public void startAutoShow(int pattern, int intervalMs) {
        stopAutoShow();
        autoPattern = pattern;
        autoIntervalMs = Math.max(300, intervalMs);
        if (pattern == PATTERN_RHYTHM && RhythmFirework.getInstance().isRunning()) {
            RhythmFirework.getInstance().setOnBeatListener(amp -> autoShowBeatBloom());
            autoShowTask = new Runnable() {
                @Override public void run() {
                    // 兑底：律动未触发时低频绽放
                    mainHandler.postDelayed(this, autoIntervalMs * 3);
                }
            };
            mainHandler.postDelayed(autoShowTask, autoIntervalMs * 3);
        } else {
            autoShowTask = new Runnable() {
                @Override public void run() {
                    autoShowStep();
                    mainHandler.postDelayed(this, autoIntervalMs);
                }
            };
            mainHandler.post(autoShowTask);
        }
    }

    public void stopAutoShow() {
        if (autoPattern == PATTERN_RHYTHM) {
            RhythmFirework.getInstance().setOnBeatListener(null);
        }
        if (autoShowTask != null) {
            mainHandler.removeCallbacks(autoShowTask);
            autoShowTask = null;
        }
    }

    public boolean isAutoShowRunning() {
        return autoShowTask != null;
    }

    private void autoShowBeatBloom() {
        FireworkView view = getCurrentFireworkView();
        if (view == null) return;
        int w = Math.max(1, view.getWidth());
        int h = Math.max(1, view.getHeight());
        showTick++;
        float px = w * (0.5f + 0.4f * (float) Math.sin(showTick * 0.6f));
        float py = h * (0.3f + 0.2f * (float) Math.sin(showTick * 1.1f + 1.5f));
        view.launch(px, py, randomShowConfig(0.8f));
        if (showTick % 5 == 0) playFeedback();
    }

    private void autoShowStep() {
        FireworkView view = getCurrentFireworkView();
        if (view == null) return;
        int w = Math.max(1, view.getWidth());
        int h = Math.max(1, view.getHeight());
        showTick++;
        switch (autoPattern) {
            case PATTERN_WAVE: {
                showAngle += 0.35f;
                float px = w * (0.5f + 0.46f * (float) Math.sin(showAngle));
                float py = h * (0.30f + 0.22f * (float) Math.sin(showAngle * 0.7f));
                view.launch(px, py, randomShowConfig(0.9f));
                break;
            }
            case PATTERN_CONVERGE: {
                showAngle += 0.5f;
                float shrink = 0.5f + 0.5f * ((float) Math.sin(showAngle) + 1f) * 0.5f;
                int points = 5;
                for (int i = 0; i < points; i++) {
                    double a = 2 * Math.PI * i / points + showAngle * 0.2;
                    view.launch(w * 0.5f + (float) Math.cos(a) * w * 0.34f * shrink,
                            h * 0.34f + (float) Math.sin(a) * h * 0.2f * shrink, randomShowConfig(0.55f));
                }
                break;
            }
            case PATTERN_RHYTHM: {
                // 律动未运行时兑底为波浪
                showAngle += 0.35f;
                float px = w * (0.5f + 0.46f * (float) Math.sin(showAngle));
                view.launch(px, h * 0.32f, randomShowConfig(0.9f));
                break;
            }
            default: {
                view.launch(w * (0.1f + showRandom.nextFloat() * 0.8f),
                        h * (0.1f + showRandom.nextFloat() * 0.5f), randomShowConfig(0.8f));
            }
        }
        if (showTick % 5 == 0) playFeedback();
    }

    private final java.util.Random showRandom = new java.util.Random();

    private FireworkConfig randomShowConfig(float scale) {
        int hue = showRandom.nextInt(360);
        return new FireworkConfig.Builder()
                .colors(hueColor(hue), hueColor((hue + 45) % 360))
                .count((int) (45 + showRandom.nextInt(35) * scale))
                .range(0.9f + showRandom.nextFloat() * 0.4f)
                .trail(1.1f)
                .build();
    }

    private static int hueColor(int hue) {
        float[] hsv = {hue, 0.75f, 1f};
        return android.graphics.Color.HSVToColor(hsv);
    }
}

