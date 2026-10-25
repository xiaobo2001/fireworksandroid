package com.xiaobo.fireworks;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.Random;

/**
 * 【v1.2.0】音乐律动：监听麦克风振幅（自适应阈值节拍检测），节拍时自动绽放烟花。
 *
 * 典型用法：
 * <pre>
 * // 需先获得 RECORD_AUDIO 运行时权限（库已在 Manifest 声明）
 * RhythmFirework.getInstance().setAutoBloom(true);
 * RhythmFirework.getInstance().start(context);
 * ...
 * RhythmFirework.getInstance().stop();
 * </pre>
 *
 * 说明：手机外放音乐时麦克风能拾取扬声器音量，播放背景音乐即触发；
 * 灵敏度可用 setSensitivity(float) 调节（0.5 更钝 / 2 更灵敏）。
 */
public class RhythmFirework {

    private static volatile RhythmFirework instance;

    public interface OnBeatListener {
        void onBeat(int amplitude);
    }

    private MediaRecorder recorder;
    private File tempFile;
    private Handler handler;
    private boolean running = false;
    private final Random random = new Random();

    private float sensitivity = 1f;          // >1 更灵敏
    private boolean autoBloom = true;
    private FireworkConfig beatConfig;        // 默认随机小烟花
    private OnBeatListener listener;

    // 自适应节拍检测状态
    private float baseline = 0f;              // 环境底噪 EMA
    private long lastBeatMs = 0L;
    private static final long MIN_BEAT_GAP_MS = 320L;
    private static final int MIN_BEAT_AMP = 1800;
    private static final int POLL_MS = 90;

    private RhythmFirework() {
    }

    public static RhythmFirework getInstance() {
        if (instance == null) {
            synchronized (RhythmFirework.class) {
                if (instance == null) instance = new RhythmFirework();
            }
        }
        return instance;
    }

    /** 灵敏度：0.5（钝）~2.0（灵敏），默认 1.0 */
    public void setSensitivity(float s) {
        this.sensitivity = Math.min(2f, Math.max(0.5f, s));
    }

    /** 节拍时是否自动绽放烟花（默认 true） */
    public void setAutoBloom(boolean enable) {
        this.autoBloom = enable;
    }

    /** 自定义律动烟花样式（null = 库内置随机彩色小烟花） */
    public void setBloomConfig(FireworkConfig config) {
        this.beatConfig = config;
    }

    /** 节拍回调（振幅 0~32767），用于自定义联动 */
    public void setOnBeatListener(OnBeatListener l) {
        this.listener = l;
    }

    public boolean isRunning() {
        return running;
    }

    /** 开始监听（需 RECORD_AUDIO 权限已授予，否则静默失败并返回 false） */
    public synchronized boolean start(Context context) {
        if (running) return true;
        try {
            Context app = context.getApplicationContext();
            tempFile = new File(app.getCacheDir(), "rhythm_mic_" + System.currentTimeMillis() + ".tmp");
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
            recorder.setOutputFile(tempFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();

            if (handler == null) handler = new Handler(Looper.getMainLooper());
            running = true;
            baseline = 0f;
            handler.post(pollTask);
            return true;
        } catch (Exception e) {
            releaseRecorder();
            return false;
        }
    }

    public synchronized void stop() {
        running = false;
        if (handler != null) handler.removeCallbacks(pollTask);
        releaseRecorder();
    }

    private void releaseRecorder() {
        try {
            if (recorder != null) {
                recorder.stop();
            }
        } catch (Exception ignored) {
        }
        try {
            if (recorder != null) {
                recorder.release();
            }
        } catch (Exception ignored) {
        }
        recorder = null;
        if (tempFile != null) {
            try {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            } catch (Exception ignored) {
            }
            tempFile = null;
        }
    }

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            if (!running || recorder == null) return;
            try {
                int amp = recorder.getMaxAmplitude();
                // 环境底噪自适应（EMA，快升慢降）
                if (amp > baseline) {
                    baseline = baseline * 0.4f + amp * 0.6f;
                } else {
                    baseline = baseline * 0.92f + amp * 0.08f;
                }
                float threshold = Math.max(MIN_BEAT_AMP, baseline * 1.9f) / sensitivity;
                long now = System.currentTimeMillis();
                if (amp > threshold && now - lastBeatMs > MIN_BEAT_GAP_MS) {
                    lastBeatMs = now;
                    onBeat(amp);
                }
            } catch (Exception ignored) {
            }
            if (running && handler != null) {
                handler.postDelayed(this, POLL_MS);
            }
        }
    };

    private void onBeat(int amplitude) {
        if (listener != null) {
            try {
                listener.onBeat(amplitude);
            } catch (Exception ignored) {
            }
        }
        if (autoBloom) {
            FireworkView view = GlobalFirework.getInstance().getFireworkView();
            if (view == null) return; // 无渲染视图时静默跳过
            FireworkConfig cfg = beatConfig;
            if (cfg == null) {
                // 内置：随机彩色小烟花（数量适中，避免节拍密集时饱和）
                int hue = random.nextInt(360);
                int color = hueToColor(hue);
                cfg = new FireworkConfig.Builder()
                        .colors(color, hueToColor((hue + 40) % 360))
                        .count(40 + random.nextInt(30))
                        .range(0.8f + random.nextFloat() * 0.5f)
                        .trail(1.2f)
                        .build();
            }
            // 节拍密集，用无反馈发射避免高频系统调用（音效由音乐本身承担）
            view.launch(view.getWidth() * (0.15f + random.nextFloat() * 0.7f),
                    view.getHeight() * (0.12f + random.nextFloat() * 0.45f), cfg);
        }
    }

    private static int hueToColor(int hue) {
        float[] hsv = {hue, 0.75f, 1f};
        return android.graphics.Color.HSVToColor(hsv);
    }
}
