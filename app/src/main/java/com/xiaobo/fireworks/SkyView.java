package com.xiaobo.fireworks;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

/**
 * 【v1.2.0】夜空大气层引擎：星空 + 流星 + 孔明灯 + 天气粒子(雪/樱花/银杏) + 真实月相月亮 + 当日星座连线。
 *
 * 两种驱动方式：
 * 1. View 模式：加入布局后自动驱动（内部 ValueAnimator，帧率时间补偿，60fps 基准）；
 * 2. 离屏/壁纸模式：setSize(w,h) + updateSky(scale) + drawSky(canvas) 手动逐帧推进。
 *
 * 所有运动均按真实帧间隔做时间补偿：高刷屏不加速、低帧率不减速。
 */
public class SkyView extends View {

    // ===== 天气模式 =====
    public static final int WEATHER_NONE = 0;
    public static final int WEATHER_SNOW = 1;
    public static final int WEATHER_SAKURA = 2;
    public static final int WEATHER_GINKGO = 3;

    // ===== 画布尺寸（View 模式=控件尺寸；离屏模式=setSize 传入） =====
    private int fieldWidth = 0;
    private int fieldHeight = 0;

    // ===== 配置（可由 App 按设备档位下发） =====
    private int starCount = 120;
    private boolean starAntiAlias = true;
    private int frameIntervalMs = 0;              // 0=满帧；33≈30fps 节流
    private int meteorBaseMs = 5000, meteorRandomMs = 5000;
    private int lanternBaseMs = 45000, lanternRandomMs = 30000;
    private int weather = WEATHER_NONE;
    private boolean moonEnabled = true;
    private boolean constellationEnabled = true;
    private int[] moodStarColors;                 // 心情染色（空=纯白）

    // ===== 状态 =====
    private final List<Star> starList = new ArrayList<>(128);
    private final List<Meteor> meteorList = new ArrayList<>(2);
    private final List<Spark> sparkList = new ArrayList<>(16);
    private final List<Lantern> lanternList = new ArrayList<>(8);
    private final List<WeatherP> weatherList = new ArrayList<>(80);
    private final Paint paint = new Paint();
    private final Random random = new Random();
    private ValueAnimator animator;
    private long lastFrameNs = 0L;
    private float meteorTimerMs = 0f, meteorNextMs = 0f;
    private float lanternTimerMs = 0f, lanternNextMs = 0f;
    private float moonAgeDays = -1f;              // -1 = 未计算
    private String zodiacName;
    private float[][] zodiacPoints;
    private int[][] zodiacSegments;

    private static final float METEOR_MIN_ANGLE = 8f, METEOR_MAX_ANGLE = 22f;
    private static final int METEOR_SEGMENTS = 10;

    public SkyView(Context context) {
        super(context);
        init();
    }

    public SkyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setAntiAlias(true);
        meteorNextMs = nextMeteorInterval();
        lanternNextMs = nextLanternInterval();
        initAnimator();
    }

    // ========== 配置 API ==========

    /** 星星数量（按设备档位下发，默认 120） */
    public void setStarCount(int count) {
        this.starCount = Math.max(10, count);
        generateStars();
    }

    /** 星星抗锯齿（低端机可关闭省填充） */
    public void setStarAntiAlias(boolean aa) {
        this.starAntiAlias = aa;
        paint.setAntiAlias(aa);
    }

    /** 帧率节流 ms（0=满帧，33≈30fps）；运动速率不受影响（时间补偿） */
    public void setFrameIntervalMs(int intervalMs) {
        this.frameIntervalMs = Math.max(0, intervalMs);
    }

    /** 环境流星随机间隔（基数+随机幅度，ms）；0,0 关闭 */
    public void setAutoMeteorIntervalMs(int baseMs, int randomMs) {
        this.meteorBaseMs = Math.max(0, baseMs);
        this.meteorRandomMs = Math.max(0, randomMs);
    }

    /** 环境孔明灯自动升空间隔（基数+随机幅度，ms）；0,0 关闭 */
    public void setAutoLanternIntervalMs(int baseMs, int randomMs) {
        this.lanternBaseMs = Math.max(0, baseMs);
        this.lanternRandomMs = Math.max(0, randomMs);
    }

    /** 天气粒子：WEATHER_NONE / SNOW / SAKURA / GINKGO */
    public void setWeather(int mode) {
        this.weather = mode;
        weatherList.clear();
    }

    public int getWeather() {
        return weather;
    }

    /** 月亮（按当天真实月相绘制） */
    public void setMoonEnabled(boolean enabled) {
        this.moonEnabled = enabled;
    }

    /** 当日星座连线（按真实日期自动计算） */
    public void setConstellationEnabled(boolean enabled) {
        this.constellationEnabled = enabled;
    }

    /** 心情染色星：按概率给一部分星星上色（传 null 恢复纯白） */
    public void setMoodStarColors(int[] colors) {
        this.moodStarColors = (colors != null && colors.length > 0) ? colors : null;
        tintStars();
    }

    /** 从指定位置放一盏孔明灯（点击放灯入口） */
    public void releaseLantern(float x, float y) {
        if (fieldWidth == 0 || fieldHeight == 0) return;
        if (lanternList.size() >= 10) lanternList.remove(0);
        Lantern l = new Lantern();
        l.x = x;
        l.y = (y > fieldHeight * 0.8f) ? y : Math.min(y + fieldHeight * 0.2f, fieldHeight - 30f);
        l.vy = -(0.55f + random.nextFloat() * 0.5f);   // 60fps 基准每帧上升像素
        l.size = 9f + random.nextFloat() * 6f;
        l.swayAmp = 8f + random.nextFloat() * 14f;
        l.swayPhase = random.nextFloat() * 6.28f;
        l.swaySpeed = 0.012f + random.nextFloat() * 0.014f;
        l.alpha = 0f;
        lanternList.add(l);
    }

    /** 手动触发一颗流星 */
    public void spawnMeteor() {
        if (fieldWidth == 0 || fieldHeight == 0) return;
        if (meteorList.size() >= 3) return;
        meteorList.add(createMeteor());
    }

    /** 离屏模式：设置画布尺寸并生成星空（View 模式由 onSizeChanged 触发） */
    public void setSize(int width, int height) {
        fieldWidth = width;
        fieldHeight = height;
        generateStars();
    }

    /** View 模式启动（幂等） */
    public void startSky() {
        lastFrameNs = 0L;
        if (animator != null && !animator.isRunning()) animator.start();
        setVisibility(VISIBLE);
    }

    /** View 模式停止（取消动画并隐藏，语义同"暂停"） */
    public void stopSky() {
        if (animator != null && animator.isRunning()) animator.cancel();
        setVisibility(GONE);
    }

    // ========== 驱动 ==========

    private void initAnimator() {
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(Long.MAX_VALUE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            long now = System.nanoTime();
            if (lastFrameNs == 0L) {
                lastFrameNs = now;
                updateSky(1f);
                invalidate();
                return;
            }
            float dtMs = (now - lastFrameNs) / 1_000_000f;
            if (frameIntervalMs > 0 && dtMs < frameIntervalMs) {
                return; // 节流：dt 跨被跳过的帧继续累积
            }
            lastFrameNs = now;
            float scale = dtMs / (1000f / 60f);
            if (scale > 4f) scale = 4f;
            if (scale < 0.5f) scale = 0.5f;
            updateSky(scale);
            invalidate();
        });
    }

    /**
     * 推进一帧夜空状态（含流星/孔明灯/天气自动生成），scale=1 等价于原"每动画帧走一步"。
     * 离屏模式由调用方传入真实帧间隔比例；View 模式由内部 animator 驱动。
     */
    public void updateSky(float scale) {
        int width = fieldWidth;
        int height = fieldHeight;
        if (width == 0 || height == 0) return;

        // 星星
        for (int i = 0; i < starList.size(); i++) {
            Star star = starList.get(i);
            star.twinklePhase += star.twinkleSpeed * scale;
            if (star.twinklePhase > Math.PI * 2) star.twinklePhase -= Math.PI * 2;
            star.x += star.driftX * scale;
            star.y += star.driftY * scale;
            if (star.x < -10) star.x = width + 10;
            if (star.x > width + 10) star.x = -10;
            if (star.y < -10) star.y = height + 10;
            if (star.y > height + 10) star.y = -10;
        }

        // 环境流星计时
        meteorTimerMs += scale * (1000f / 60f);
        if (meteorTimerMs >= meteorNextMs) {
            meteorTimerMs = 0f;
            meteorNextMs = nextMeteorInterval();
            if (meteorBaseMs > 0 && meteorList.size() < 2) meteorList.add(createMeteor());
        }

        // 孔明灯自动升空计时
        lanternTimerMs += scale * (1000f / 60f);
        if (lanternTimerMs >= lanternNextMs) {
            lanternTimerMs = 0f;
            lanternNextMs = nextLanternInterval();
            if (lanternBaseMs > 0 && lanternList.size() < 8) {
                releaseLantern(width * (0.1f + random.nextFloat() * 0.8f), height - 10f);
            }
        }

        // 流星
        for (int i = meteorList.size() - 1; i >= 0; i--) {
            Meteor m = meteorList.get(i);
            m.x += m.vx * scale;
            m.y += m.vy * scale;
            m.life -= m.decay * scale;
            if (m.life <= 0f) {
                spawnSparks(m.x, m.y, m.trailColor);
                meteorList.remove(i);
            }
        }
        for (int i = sparkList.size() - 1; i >= 0; i--) {
            Spark s = sparkList.get(i);
            s.x += s.vx * scale;
            s.y += s.vy * scale;
            s.vx *= 0.90f;
            s.vy = s.vy * 0.90f + 0.05f * scale;
            s.life -= s.decay * scale;
            if (s.life <= 0f) sparkList.remove(i);
        }

        // 孔明灯
        for (int i = lanternList.size() - 1; i >= 0; i--) {
            Lantern l = lanternList.get(i);
            l.y += l.vy * scale;
            l.swayPhase += l.swaySpeed * scale;
            l.alpha = Math.min(1f, l.alpha + 0.02f * scale);
            if (l.y < height * 0.12f) {
                l.alpha -= 0.012f * scale; // 飘出顶部前淡出
            }
            if (l.alpha <= 0f || l.y < -30f) lanternList.remove(i);
        }

        // 天气粒子
        if (weather != WEATHER_NONE) {
            int target = Math.max(24, width / 34);
            while (weatherList.size() < target) weatherList.add(createWeatherP());
        }
        for (int i = weatherList.size() - 1; i >= 0; i--) {
            WeatherP p = weatherList.get(i);
            p.y += p.vy * scale;
            p.phase += p.phaseSpeed * scale;
            p.x = p.baseX + (float) Math.sin(p.phase) * p.sway;
            if (p.y > height + 20f) {
                if (weather != WEATHER_NONE) {
                    recycleWeatherTop(p);
                } else {
                    weatherList.remove(i);
                }
            }
        }
    }

    /** 绘制夜空到指定 Canvas（View 模式由 onDraw 调用；离屏模式由外部调用） */
    public void drawSky(Canvas canvas) {
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        if (w == 0 || h == 0) return;
        if (moonEnabled) drawMoon(canvas, w, h);
        if (constellationEnabled) drawConstellation(canvas, w, h);
        drawStars(canvas);
        drawWeather(canvas);
        drawMeteors(canvas);
        drawLanterns(canvas);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setSize(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawSky(canvas);
    }

    // ========== 月亮（真实月相） ==========

    /** 计算当天月龄（天）。已知新月历元 2000-01-06 18:14 UTC，朔望月 29.530588853 天 */
    private float computeMoonAge() {
        long epochMs = 947182440000L;
        double age = ((System.currentTimeMillis() - epochMs) / 86400000.0) % 29.530588853;
        if (age < 0) age += 29.530588853;
        return (float) age;
    }

    private void drawMoon(Canvas canvas, int w, int h) {
        if (moonAgeDays < 0f) moonAgeDays = computeMoonAge();
        float cx = w * 0.84f;
        float cy = h * 0.14f;
        float r = Math.max(14f, Math.min(w, h) * 0.05f);

        // 光晕
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        paint.setColor(0xFFF7F3DC);
        paint.setAlpha(26);
        canvas.drawCircle(cx, cy, r * 2.6f, paint);
        paint.setAlpha(52);
        canvas.drawCircle(cx, cy, r * 1.7f, paint);

        // 月面
        paint.setColor(0xFFF5EFCE);
        paint.setAlpha(235);
        canvas.drawCircle(cx, cy, r, paint);

        // 月相阴影：按照亮比例画偏移暗圆，近似弦月/凸月
        float frac = (1f - (float) Math.cos(2 * Math.PI * moonAgeDays / 29.530588853)) / 2f; // 0 新月 → 1 满月
        if (frac < 0.98f) {
            float offset = (1f - frac) * 2f * r + r * 0.08f; // 满月时几乎不遮挡
            boolean waxing = moonAgeDays < 14.765f;
            paint.setColor(0x14001020);
            paint.setAlpha(200);
            canvas.drawCircle(cx + (waxing ? -offset : offset), cy, r, paint);
            // 阴影收边：再补一层半透明，避免生硬圆边
            paint.setAlpha(90);
            canvas.drawCircle(cx + (waxing ? -offset * 1.06f : offset * 1.06f), cy, r * 0.98f, paint);
        }
    }

    // ========== 当日星座（按真实日期） ==========

    private static final String[] ZODIAC_NAMES = {
            "白羊", "金牛", "双子", "巨蟹", "狮子", "处女",
            "天秤", "天蝎", "射手", "摩羯", "水瓶", "双鱼"};

    private void computeZodiac() {
        if (zodiacPoints != null) return; // 按当天计算一次
        Calendar c = Calendar.getInstance();
        int idx = zodiacIndex(c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        zodiacName = ZODIAC_NAMES[idx];
        zodiacPoints = ZODIAC_POINTS[idx];
        zodiacSegments = ZODIAC_SEGMENTS[idx];
    }

    /** 当前日期所属星座名（如“狮子”），供 App 展示 */
    public String getCurrentZodiacName() {
        computeZodiac();
        return zodiacName;
    }

    private static int zodiacIndex(int m, int d) {
        int md = m * 100 + d;
        if (md >= 321 && md <= 419) return 0;   // 白羊
        if (md >= 420 && md <= 520) return 1;   // 金牛
        if (md >= 521 && md <= 621) return 2;   // 双子
        if (md >= 622 && md <= 722) return 3;   // 巨蟹
        if (md >= 723 && md <= 822) return 4;   // 狮子
        if (md >= 823 && md <= 922) return 5;   // 处女
        if (md >= 923 && md <= 1023) return 6;   // 天秤
        if (md >= 1024 && md <= 1122) return 7;  // 天蝎
        if (md >= 1123 && md <= 1221) return 8;  // 射手
        if (md >= 1222 || md <= 119) return 9;   // 摩羯（跨年）
        if (md >= 120 && md <= 218) return 10;   // 水瓶
        return 11;                                // 双鱼（219-320）
    }

    private void drawConstellation(Canvas canvas, int w, int h) {
        if (zodiacPoints == null) computeZodiac();
        float originX = w * 0.10f;
        float originY = h * 0.16f;
        float span = Math.min(w, h) * 0.22f;

        // 连线
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(0xFF9FB8D8);
        paint.setAlpha(70);
        for (int[] seg : zodiacSegments) {
            float x1 = originX + zodiacPoints[seg[0]][0] * span;
            float y1 = originY + zodiacPoints[seg[0]][1] * span;
            float x2 = originX + zodiacPoints[seg[1]][0] * span;
            float y2 = originY + zodiacPoints[seg[1]][1] * span;
            canvas.drawLine(x1, y1, x2, y2, paint);
        }
        // 星点
        paint.setStyle(Paint.Style.FILL);
        for (float[] pt : zodiacPoints) {
            paint.setColor(0xFFCFE4FF);
            paint.setAlpha(200);
            canvas.drawCircle(originX + pt[0] * span, originY + pt[1] * span, 1.6f, paint);
        }
    }

    // ========== 星空 ==========

    private void generateStars() {
        int w = Math.max(1, fieldWidth);
        int h = Math.max(1, fieldHeight);
        starList.clear();
        for (int i = 0; i < starCount; i++) {
            Star star = new Star();
            star.x = random.nextFloat() * w;
            star.y = random.nextFloat() * h;
            star.radius = 0.8f + random.nextFloat() * 2.2f;
            star.baseAlpha = 30 + random.nextInt(180);
            star.twinklePhase = random.nextFloat() * (float) Math.PI * 2;
            star.twinkleSpeed = 0.015f + random.nextFloat() * 0.025f;
            star.driftX = (random.nextFloat() - 0.5f) * 0.3f;
            star.driftY = (random.nextFloat() - 0.5f) * 0.2f;
            starList.add(star);
        }
        tintStars();
    }

    private void tintStars() {
        for (int i = 0; i < starList.size(); i++) {
            Star star = starList.get(i);
            star.color = Color.WHITE;
            if (moodStarColors != null && random.nextInt(5) == 0) {
                star.color = moodStarColors[random.nextInt(moodStarColors.length)];
            }
        }
    }

    private void drawStars(Canvas canvas) {
        paint.setAntiAlias(starAntiAlias);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < starList.size(); i++) {
            Star star = starList.get(i);
            float alpha = star.baseAlpha + (float) Math.sin(star.twinklePhase) * 40;
            alpha = Math.max(10, Math.min(255, alpha));
            paint.setColor(star.color);
            paint.setAlpha((int) alpha);
            canvas.drawCircle(star.x, star.y, star.radius, paint);
        }
    }

    // ========== 流星 ==========

    private float nextMeteorInterval() {
        return meteorBaseMs + random.nextFloat() * meteorRandomMs;
    }

    private float nextLanternInterval() {
        return lanternBaseMs + random.nextFloat() * lanternRandomMs;
    }

    private Meteor createMeteor() {
        float w = fieldWidth;
        float h = fieldHeight;
        boolean toRight = random.nextBoolean();
        float angleRad = (float) Math.toRadians(
                METEOR_MIN_ANGLE + random.nextFloat() * (METEOR_MAX_ANGLE - METEOR_MIN_ANGLE));
        float speed = (w * 1.25f / 60f) * (0.8f + random.nextFloat() * 0.4f);
        Meteor m = new Meteor();
        m.vx = (toRight ? 1f : -1f) * speed * (float) Math.cos(angleRad);
        m.vy = speed * (float) Math.sin(angleRad);
        m.totalSteps = Math.abs(m.vx) > 0.01f ? (w * 1.3f) / Math.abs(m.vx) : 90f;
        m.decay = 1f / m.totalSteps;
        m.life = 1f;
        boolean warm = random.nextInt(10) < 3;
        m.color = warm ? 0xFFFFE9B8 : 0xFFFFFFFF;
        m.trailColor = warm ? 0xFFD9A94A : 0xFFBFD8FF;
        m.trailLen = Math.min(speed * 26f, w * 0.30f);
        m.x = toRight ? -m.trailLen : w + m.trailLen;
        m.y = random.nextFloat() * h * 0.45f;
        return m;
    }

    private void drawMeteors(Canvas canvas) {
        if (meteorList.isEmpty()) return;
        for (int i = 0; i < meteorList.size(); i++) {
            Meteor m = meteorList.get(i);
            float fadeIn = Math.min(1f, (1f - m.life) / 0.15f);
            float fadeOut = Math.min(1f, m.life / 0.30f);
            float a = Math.min(fadeIn, fadeOut);
            if (a <= 0.02f) continue;

            float speed = (float) Math.sqrt(m.vx * m.vx + m.vy * m.vy);
            if (speed < 0.001f) continue;
            float dx = m.vx / speed;
            float dy = m.vy / speed;
            float segLen = m.trailLen / METEOR_SEGMENTS;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            for (int s = 0; s < METEOR_SEGMENTS; s++) {
                float t = s / (float) METEOR_SEGMENTS;
                float alpha = a * (1f - t) * (1f - t);
                paint.setColor(m.trailColor);
                paint.setAlpha((int) (alpha * 220));
                paint.setStrokeWidth(Math.max(1f, 3.2f * (1f - t * 0.65f)));
                canvas.drawLine(m.x - dx * segLen * s, m.y - dy * segLen * s,
                        m.x - dx * segLen * (s + 1), m.y - dy * segLen * (s + 1), paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(m.color);
            paint.setAlpha((int) (a * 255));
            canvas.drawCircle(m.x, m.y, 2.2f, paint);
            if (starAntiAlias) {
                paint.setAlpha((int) (a * 70));
                canvas.drawCircle(m.x, m.y, 5.5f, paint);
            }
        }
        // 星尘
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < sparkList.size(); i++) {
            Spark s = sparkList.get(i);
            paint.setColor(s.color);
            paint.setAlpha((int) (s.life * 190));
            canvas.drawCircle(s.x, s.y, 1.4f * s.life + 0.4f, paint);
        }
    }

    private void spawnSparks(float x, float y, int color) {
        int count = starAntiAlias ? 9 : 5;
        for (int i = 0; i < count && sparkList.size() < 24; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            float spd = 0.8f + random.nextFloat() * 2.2f;
            Spark s = new Spark();
            s.x = x;
            s.y = y;
            s.vx = (float) Math.cos(ang) * spd;
            s.vy = (float) Math.sin(ang) * spd;
            s.life = 1f;
            s.decay = 1f / (14f + random.nextFloat() * 8f);
            s.color = color;
            sparkList.add(s);
        }
    }

    // ========== 孔明灯 ==========

    private void drawLanterns(Canvas canvas) {
        if (lanternList.isEmpty()) return;
        for (int i = 0; i < lanternList.size(); i++) {
            Lantern l = lanternList.get(i);
            if (l.alpha <= 0f) continue;
            float drawX = l.x + (float) Math.sin(l.swayPhase) * l.swayAmp;
            float flicker = 0.85f + 0.15f * (float) Math.sin(l.swayPhase * 7f);
            int a = (int) (255 * l.alpha * flicker);

            // 外光晕
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFB24D);
            paint.setAlpha(a / 4);
            canvas.drawCircle(drawX, l.y, l.size * 2.4f, paint);
            // 灯体
            paint.setColor(0xFFFFC26B);
            paint.setAlpha(a);
            canvas.drawCircle(drawX, l.y, l.size, paint);
            // 灯芯亮点
            paint.setColor(0xFFFFE3A9);
            paint.setAlpha(Math.min(255, a * 3 / 2));
            canvas.drawCircle(drawX, l.y - l.size * 0.15f, l.size * 0.45f, paint);
        }
    }

    // ========== 天气粒子 ==========

    private WeatherP createWeatherP() {
        return recycleWeatherTop(new WeatherP());
    }

    private WeatherP recycleWeatherTop(WeatherP p) {
        p.baseX = random.nextFloat() * fieldWidth;
        p.y = -20f - random.nextFloat() * fieldHeight * 0.5f;
        p.phase = random.nextFloat() * 6.28f;
        switch (weather) {
            case WEATHER_SNOW:
                p.vy = 0.7f + random.nextFloat() * 1.1f;
                p.size = 1.5f + random.nextFloat() * 2.5f;
                p.color = 0xFFFFFFFF;
                p.sway = 10f + random.nextFloat() * 20f;
                p.phaseSpeed = 0.02f + random.nextFloat() * 0.02f;
                p.alpha = 140 + random.nextInt(100);
                break;
            case WEATHER_SAKURA:
                p.vy = 1.4f + random.nextFloat() * 1.6f;
                p.size = 3f + random.nextFloat() * 3f;
                p.color = 0xFFF8B8CC;
                p.sway = 20f + random.nextFloat() * 40f;
                p.phaseSpeed = 0.03f + random.nextFloat() * 0.03f;
                p.alpha = 170 + random.nextInt(70);
                break;
            case WEATHER_GINKGO:
                p.vy = 1.6f + random.nextFloat() * 1.8f;
                p.size = 3.5f + random.nextFloat() * 3.5f;
                p.color = 0xFFE8C24A;
                p.sway = 18f + random.nextFloat() * 36f;
                p.phaseSpeed = 0.025f + random.nextFloat() * 0.03f;
                p.alpha = 160 + random.nextInt(80);
                break;
            default:
                p.vy = 0;
                p.size = 0;
                p.color = 0;
                p.sway = 0;
                p.phaseSpeed = 0;
                p.alpha = 0;
        }
        return p;
    }

    private void drawWeather(Canvas canvas) {
        if (weatherList.isEmpty()) return;
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < weatherList.size(); i++) {
            WeatherP p = weatherList.get(i);
            paint.setColor(p.color);
            paint.setAlpha(p.alpha);
            if (weather == WEATHER_SNOW) {
                canvas.drawCircle(p.x, p.y, p.size, paint);
            } else {
                // 樱花/银杏：旋转的椭圆叶
                float rot = p.phase * 1.5f;
                canvas.save();
                canvas.translate(p.x, p.y);
                canvas.rotate((float) Math.toDegrees(rot));
                RectF oval = new RectF(-p.size, -p.size * 0.6f, p.size, p.size * 0.6f);
                canvas.drawOval(oval, paint);
                canvas.restore();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        starList.clear();
    }

    // ========== 数据结构 ==========

    private static class Star {
        float x, y, radius, twinklePhase, twinkleSpeed, driftX, driftY;
        int baseAlpha, color = Color.WHITE;
    }

    private static class Meteor {
        float x, y, vx, vy, life, decay, totalSteps, trailLen;
        int color, trailColor;
    }

    private static class Spark {
        float x, y, vx, vy, life, decay;
        int color;
    }

    private static class Lantern {
        float x, y, vy, size, swayAmp, swayPhase, swaySpeed, alpha;
    }

    private static class WeatherP {
        float baseX, x, y, vy, size, phase, phaseSpeed, sway;
        int color, alpha;
    }

    // ========== 十二星座简化连线数据（归一化 0~1 坐标 + 连线索引） ==========
    // 顺序：白羊/金牛/双子/巨蟹/狮子/处女/天秤/天蝎/射手/摩羯/水瓶/双鱼
    private static final float[][] ARIES_P = {{0.1f, 0.7f}, {0.3f, 0.5f}, {0.5f, 0.35f}, {0.75f, 0.2f}, {0.95f, 0.35f}, {0.85f, 0.6f}};
    private static final int[][] ARIES_S = {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}};
    private static final float[][] TAURUS_P = {{0.2f, 0.5f}, {0.35f, 0.4f}, {0.5f, 0.45f}, {0.35f, 0.65f}, {0.5f, 0.7f}, {0.8f, 0.25f}, {0.9f, 0.55f}};
    private static final int[][] TAURUS_S = {{0, 1}, {1, 2}, {1, 3}, {3, 4}, {2, 5}, {4, 6}};
    private static final float[][] GEMINI_P = {{0.25f, 0.2f}, {0.25f, 0.5f}, {0.25f, 0.8f}, {0.45f, 0.35f}, {0.45f, 0.65f}, {0.75f, 0.2f}, {0.75f, 0.5f}, {0.75f, 0.8f}};
    private static final int[][] GEMINI_S = {{0, 1}, {1, 2}, {3, 4}, {5, 6}, {6, 7}, {2, 4}, {4, 6}};
    private static final float[][] CANCER_P = {{0.3f, 0.3f}, {0.5f, 0.45f}, {0.45f, 0.7f}, {0.7f, 0.65f}, {0.75f, 0.85f}};
    private static final int[][] CANCER_S = {{0, 1}, {1, 2}, {1, 3}, {3, 4}};
    private static final float[][] LEO_P = {{0.15f, 0.35f}, {0.3f, 0.25f}, {0.45f, 0.35f}, {0.5f, 0.55f}, {0.7f, 0.6f}, {0.85f, 0.75f}, {0.65f, 0.8f}};
    private static final int[][] LEO_S = {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}, {4, 6}};
    private static final float[][] VIRGO_P = {{0.2f, 0.25f}, {0.4f, 0.35f}, {0.55f, 0.55f}, {0.45f, 0.75f}, {0.75f, 0.3f}, {0.9f, 0.5f}, {0.85f, 0.8f}};
    private static final int[][] VIRGO_S = {{0, 1}, {1, 2}, {2, 3}, {1, 4}, {4, 5}, {5, 6}};
    private static final float[][] LIBRA_P = {{0.3f, 0.25f}, {0.5f, 0.45f}, {0.35f, 0.7f}, {0.65f, 0.7f}, {0.7f, 0.45f}, {0.9f, 0.25f}};
    private static final int[][] LIBRA_S = {{0, 1}, {1, 2}, {1, 4}, {4, 5}, {2, 3}};
    private static final float[][] SCORPIUS_P = {{0.15f, 0.2f}, {0.35f, 0.3f}, {0.55f, 0.4f}, {0.7f, 0.55f}, {0.8f, 0.7f}, {0.9f, 0.85f}, {0.7f, 0.95f}};
    private static final int[][] SCORPIUS_S = {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 6}};
    private static final float[][] SAGITTARIUS_P = {{0.25f, 0.4f}, {0.45f, 0.3f}, {0.65f, 0.35f}, {0.55f, 0.55f}, {0.75f, 0.6f}, {0.9f, 0.75f}};
    private static final int[][] SAGITTARIUS_S = {{0, 1}, {1, 2}, {1, 3}, {3, 4}, {4, 5}};
    private static final float[][] CAPRICORNUS_P = {{0.15f, 0.5f}, {0.35f, 0.6f}, {0.55f, 0.65f}, {0.75f, 0.55f}, {0.9f, 0.4f}};
    private static final int[][] CAPRICORNUS_S = {{0, 1}, {1, 2}, {2, 3}, {3, 4}};
    private static final float[][] AQUARIUS_P = {{0.15f, 0.35f}, {0.3f, 0.5f}, {0.45f, 0.35f}, {0.6f, 0.5f}, {0.75f, 0.35f}, {0.9f, 0.5f}};
    private static final int[][] AQUARIUS_S = {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}};
    private static final float[][] PISCES_P = {{0.15f, 0.3f}, {0.35f, 0.5f}, {0.55f, 0.6f}, {0.75f, 0.5f}, {0.9f, 0.3f}, {0.85f, 0.15f}};
    private static final int[][] PISCES_S = {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}};

    private static final float[][][] ZODIAC_POINTS = {
            ARIES_P, TAURUS_P, GEMINI_P, CANCER_P, LEO_P, VIRGO_P,
            LIBRA_P, SCORPIUS_P, SAGITTARIUS_P, CAPRICORNUS_P, AQUARIUS_P, PISCES_P};
    private static final int[][][] ZODIAC_SEGMENTS = {
            ARIES_S, TAURUS_S, GEMINI_S, CANCER_S, LEO_S, VIRGO_S,
            LIBRA_S, SCORPIUS_S, SAGITTARIUS_S, CAPRICORNUS_S, AQUARIUS_S, PISCES_S};
}
