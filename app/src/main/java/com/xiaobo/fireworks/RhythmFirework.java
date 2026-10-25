package com.xiaobo.fireworks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import java.util.Random;

/**
 * 【v1.2.3 重制】音乐律动：AudioRecord 实时采集麦克风 RMS（后台线程，零主线程开销），
 * 自适应阈值检测音乐节拍，并按“编舞”绽放烟花——不再是无规律的小烟花乱炸：
 * - 节拍色相逐拍流转（一段副歌走完一道彩虹）；
 * - 位置沿平滑轨迹游走，烟花随音乐在夜空中有序律动而非随机瞬移；
 * - 强弱拍交替（奇数拍轻盈、偶数拍饱满），每 8 拍一次高潮双爆；
 * - 音量越大绽放越大。
 *
 * 需 RECORD_AUDIO 运行时权限（库已在 Manifest 声明）。
 */
public class RhythmFirework {

    private static volatile RhythmFirework instance;

    public interface OnBeatListener {
        void onBeat(int amplitude);
    }

    private AudioRecord record;
    private Thread audioThread;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;
    private final Random random = new Random();

    private float sensitivity = 1f;          // >1 更灵敏
    private volatile boolean autoBloom = true;
    private FireworkConfig beatConfig;        // null = 内置编舞
    private OnBeatListener listener;

    // ===== 自适应节拍检测（主线程状态） =====
    private float baseline = 0f;              // 环境底噪/音乐均值的 EMA
    private long lastBeatMs = 0L;
    private int beatCount = 0;
    private int beatHue = 20;
    private static final long MIN_BEAT_GAP_MS = 280L;
    private static final int MIN_BEAT_AMP = 2200;

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

    /** 自定义律动烟花样式（null = 库内置编舞） */
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

    /** 开始监听（需 RECORD_AUDIO 权限已授予，否则返回 false） */
    @SuppressLint("MissingPermission")
    public synchronized boolean start(Context context) {
        if (running) return true;
        try {
            int sampleRate = 8000;
            int minBuf = AudioRecord.getMinBufferSize(sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int bufSize = Math.max(2048, minBuf);
            record = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize);
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                releaseRecorder();
                return false;
            }
            record.startRecording();

            baseline = 0f;
            beatCount = 0;
            running = true;
            audioThread = new Thread(this::readLoop, "RhythmFirework");
            audioThread.start();
            return true;
        } catch (Exception e) {
            releaseRecorder();
            return false;
        }
    }

    public synchronized void stop() {
        running = false;
        if (audioThread != null) {
            audioThread.interrupt();
            audioThread = null;
        }
        releaseRecorder();
    }

    private void releaseRecorder() {
        try {
            if (record != null) record.stop();
        } catch (Exception ignored) {
        }
        try {
            if (record != null) record.release();
        } catch (Exception ignored) {
        }
        record = null;
    }

    /** 后台线程：阻塞式读取 PCM，计算 RMS 振幅，投递主线程检测节拍 */
    private void readLoop() {
        android.media.AudioRecord rec = record;
        if (rec == null) return;
        short[] buf = new short[1024]; // 8000Hz 下约 128ms 一块，节拍响应及时
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                int n = rec.read(buf, 0, buf.length);
                if (n <= 0) {
                    if (!running) break;
                    continue;
                }
                double sum = 0;
                for (int i = 0; i < n; i++) {
                    double v = buf[i];
                    sum += v * v;
                }
                final int amp = (int) Math.min(32767, Math.sqrt(sum / n));
                handler.post(() -> onAmplitude(amp));
            } catch (Exception e) {
                if (!running) break;
            }
        }
    }

    /** 主线程：自适应阈值节拍检测 + 编舞绽放 */
    private void onAmplitude(int amp) {
        if (!running) return;
        // 底噪/均值自适应：快升慢降，跟随音乐整体响度的变化
        baseline += (amp - baseline) * (amp > baseline ? 0.22f : 0.05f);
        float threshold = Math.max(MIN_BEAT_AMP, baseline * 1.7f) / sensitivity;

        long now = System.currentTimeMillis();
        if (amp > threshold && now - lastBeatMs > MIN_BEAT_GAP_MS) {
            lastBeatMs = now;
            onBeat(amp, threshold);
        }
    }

    private void onBeat(int amplitude, float threshold) {
        if (listener != null) {
            try {
                listener.onBeat(amplitude);
            } catch (Exception ignored) {
            }
        }
        if (!autoBloom) return;

        FireworkView view = GlobalFirework.getInstance().getFireworkView();
        if (view == null) return; // 无渲染视图时静默跳过
        int w = Math.max(1, view.getWidth());
        int h = Math.max(1, view.getHeight());

        if (beatConfig != null) {
            view.launch(w * (0.2f + random.nextFloat() * 0.6f),
                    h * (0.15f + random.nextFloat() * 0.4f), beatConfig);
            return;
        }

        // ===== 内置编舞 =====
        beatCount++;
        beatHue = (beatHue + 37) % 360; // 色相逐拍流转
        int c1 = hueToColor(beatHue);
        int c2 = hueToColor((beatHue + 45) % 360);
        // 音量越大绽放越大
        float t = Math.min(1f, amplitude / (threshold * 2.4f));
        // 位置沿平滑轨迹游走（烟花随音乐在夜空中有序律动）
        float px = w * (0.5f + 0.33f * (float) Math.sin(beatCount * 0.55));
        float py = h * (0.30f + 0.15f * (float) Math.sin(beatCount * 0.9 + 1.7));

        if (beatCount % 8 == 0) {
            // 每 8 拍：高潮双爆（左右对称）
            FireworkConfig big = new FireworkConfig.Builder()
                    .colors(c1, c2)
                    .count(90 + (int) (60 * t))
                    .range(1.15f + 0.4f * t)
                    .trail(1.6f)
                    .build();
            view.launch(px - w * 0.12f, py, big);
            view.launch(px + w * 0.12f, py, big);
        } else if (beatCount % 2 == 0) {
            // 偶数拍：饱满绽放
            view.launch(px, py, new FireworkConfig.Builder()
                    .colors(c1, c2)
                    .count(55 + (int) (45 * t))
                    .range(0.95f + 0.45f * t)
                    .trail(1.3f)
                    .build());
        } else {
            // 奇数拍：轻盈点缀
            view.launch(px, py, new FireworkConfig.Builder()
                    .colors(c1)
                    .count(28 + (int) (18 * t))
                    .range(0.7f + 0.3f * t)
                    .trail(0.9f)
                    .build());
        }
    }

    private static int hueToColor(int hue) {
        float[] hsv = {hue, 0.75f, 1f};
        return Color.HSVToColor(hsv);
    }
}
