package com.xiaobo.fireworks;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Random;

public class FireworkView extends View {

    private static final int DEFAULT_MAX_PARTICLES = 5000;
    /** 可配置的粒子数上限（低配设备可调小以降低满帧绘制压力），最小 200 */
    private int maxParticles = DEFAULT_MAX_PARTICLES;
    private final List<Particle> activeParticles = new ArrayList<>(512);
    private final Queue<Particle> particlePool = new ArrayDeque<>(256);
    private final Paint paint = new Paint();
    private final Random random = new Random();

    private final FireworkConfig defaultConfig = new FireworkConfig.Builder().build();
    private boolean isAnimating = false;
    private boolean isStopped = false;

    // ===== 性能优化：物理步进时间补偿 =====
    // 以 60fps 为基准按真实帧间隔缩放步长：60Hz 下与原版完全一致，
    // 90/120Hz 高刷屏不再成倍加速，壁纸低帧率模式不再变慢。
    private long lastStepNanos = 0L;

    // ===== 性能优化：消除每帧分配 =====
    private final List<ExplosionTask> pendingExplosions = new ArrayList<>(4);

    // ===== 性能优化：文字烟花栅格缓存与采样像素缓存 =====
    // 相同文字+字号的连续爆炸直接复用缓存的采样像素（零原生调用），
    // 未命中时才栅格化并拷贝为 ARGB_8888 整块读取
    // （规避 ALPHA_8 在 getPixels 上的编码差异，且远快于原实现的逐点 getPixel）。
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect textBounds = new Rect();
    private String cachedText;
    private float cachedTextSize;
    private int cachedTextWidth;
    private int cachedTextHeight;
    private int[] textPixels;

    public FireworkView(Context context) {
        super(context);
        init();
    }

    public FireworkView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setAntiAlias(true);
        paint.setDither(true);
        textPaint.setFakeBoldText(true);
    }

    /**
     * 设置粒子数上限，超出时新烟花会被丢弃。低配设备可调小（最小 200）以降低满帧绘制压力。
     */
    public void setMaxParticles(int max) {
        this.maxParticles = Math.max(200, max);
    }

    public int getMaxParticles() {
        return maxParticles;
    }

    public void stop() {
        isStopped = true;
        activeParticles.clear();
        isAnimating = false;
        invalidate();
    }

    public void resume() {
        isStopped = false;
        lastStepNanos = 0L; // 恢复后重置计时基准，避免首帧步长补偿跳变
    }

    public void launch(float x, float y, FireworkConfig config) {
        if (isStopped) return;
        if (activeParticles.size() > maxParticles - 200) return;

        final FireworkConfig cfg = (config != null) ? config : defaultConfig;

        if (cfg.launchRocket) {
            Particle p = obtainParticle();
            p.reset(x, getHeight(), 0, -25f - random.nextFloat() * 10, Color.YELLOW, 8f, 1.0f, 2, 1.0f);
            p.targetY = y;
            p.tag = cfg;
            activeParticles.add(p);
        } else {
            explode(x, y, cfg);
        }

        if (!isAnimating) {
            isAnimating = true;
            postInvalidateOnAnimation();
        }
    }

    // ========== 手动更新一帧粒子状态（壁纸模式调用；View 模式由 onDraw 驱动） ==========
    public void update() {
        if (isStopped || activeParticles.isEmpty()) {
            isAnimating = false;
            lastStepNanos = 0L;
            return;
        }

        // 时间补偿：按真实帧间隔缩放物理步长（60fps 基准）
        long now = System.nanoTime();
        float step = 1f;
        if (lastStepNanos == 0L) {
            lastStepNanos = now;
        } else {
            float dtMs = (now - lastStepNanos) / 1_000_000f;
            lastStepNanos = now;
            step = dtMs / (1000f / 60f);
            if (step > 4f) step = 4f;
            if (step < 0.25f) step = 0.25f;
        }
        // 阻尼按步长折算，保证任意帧率下减速节奏一致（step=1 时即原版 0.93）
        float dampFactor = (float) Math.pow(0.93, step);

        pendingExplosions.clear();
        Iterator<Particle> iterator = activeParticles.iterator();

        while (iterator.hasNext()) {
            Particle p = iterator.next();
            boolean alive = p.update(random, step, dampFactor);

            if (!alive) {
                if (p.type == 2 && p.tag instanceof FireworkConfig) {
                    pendingExplosions.add(new ExplosionTask(p.x, p.y, (FireworkConfig) p.tag));
                }
                iterator.remove();
                recycleParticle(p);
            }
        }

        for (int i = 0; i < pendingExplosions.size(); i++) {
            ExplosionTask task = pendingExplosions.get(i);
            explode(task.x, task.y, task.cfg);
        }

        if (activeParticles.isEmpty()) {
            isAnimating = false;
        }
    }

    // ========== 原有 onDraw 改造：先更新再绘制，逻辑复用 ==========
    @Override
    protected void onDraw(Canvas canvas) {
        // View 模式下自动驱动更新，保证原有效果不变
        update();
        if (isStopped || activeParticles.isEmpty()) return;

        drawToCanvas(canvas);

        postInvalidateOnAnimation();
    }

    // ========== 仅绘制到指定 Canvas（不触发更新，壁纸模式调用） ==========
    public void drawToCanvas(Canvas canvas) {
        if (isStopped || activeParticles.isEmpty()) return;
        final int canvasWidth = canvas.getWidth();
        final int canvasHeight = canvas.getHeight();
        for (int i = 0; i < activeParticles.size(); i++) {
            drawParticle(canvas, activeParticles.get(i), canvasWidth, canvasHeight);
        }
    }

    private void drawParticle(Canvas canvas, Particle p, int canvasWidth, int canvasHeight) {
        float trailX = p.x;
        float trailY = p.y;
        boolean drawTrail = (p.type != 2) && (p.trailScale > 0.1f);
        if (drawTrail) {
            trailX = p.x - p.vx * p.trailScale;
            trailY = p.y - p.vy * p.trailScale;
        }

        // 屏外裁剪：头尾整体落在画布之外时跳过本粒子的绘制调用（视觉不变，省无效 draw）
        float pad = p.size + 4f;
        float minX = Math.min(p.x, trailX) - pad;
        float maxX = Math.max(p.x, trailX) + pad;
        float minY = Math.min(p.y, trailY) - pad;
        float maxY = Math.max(p.y, trailY) + pad;
        if (maxX < 0f || minX > canvasWidth || maxY < 0f || minY > canvasHeight) return;

        paint.setColor(p.color);
        paint.setAlpha((int) (255 * p.life));
        paint.setStrokeWidth(p.size);

        if (p.type == 2) {
            canvas.drawCircle(p.x, p.y, p.size, paint);
        } else if (drawTrail) {
            canvas.drawLine(p.x, p.y, trailX, trailY, paint);
        } else {
            canvas.drawCircle(p.x, p.y, p.size / 2, paint);
        }
    }

    private void explode(float x, float y, FireworkConfig cfg) {
        boolean isRandomColor = (cfg.colors == null || cfg.colors.length == 0);
        float baseHue = random.nextInt(360);

        if (cfg.shape == FireworkConfig.Shape.TEXT) {
            createByText(x, y, cfg, isRandomColor, baseHue);
        } else if (cfg.shape == FireworkConfig.Shape.HEART) {
            createByMath(x, y, cfg, true, isRandomColor, baseHue);
        } else if (cfg.shape == FireworkConfig.Shape.STAR) {
            createByMath(x, y, cfg, false, isRandomColor, baseHue);
        } else {
            createByCircle(x, y, cfg, isRandomColor, baseHue);
        }
    }

    private void createByText(float x, float y, FireworkConfig cfg, boolean isRand, float hue) {
        String text = (cfg.text == null) ? "" : cfg.text;

        // 采样像素缓存：相同文字+字号直接复用（零栅格化、零原生调用）
        if (textPixels == null || !text.equals(cachedText) || cachedTextSize != cfg.textSize) {
            textPaint.setTextSize(cfg.textSize);
            textPaint.getTextBounds(text, 0, text.length(), textBounds);
            int bw = textBounds.width() + 40;
            int bh = textBounds.height() + 40;
            if (bw <= 40 || bh <= 40) return; // 空内容保护：与原实现同样不产生粒子

            // 栅格化为 ALPHA_8，再拷贝为 ARGB_8888 整块读取像素
            // （ARGB_8888 的 getPixels 语义明确，与原逐点 getPixel 的判空结果一致）
            Bitmap alphaBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ALPHA_8);
            Canvas textCanvas = new Canvas(alphaBitmap);
            textCanvas.drawText(text, 20 - textBounds.left, 20 - textBounds.top, textPaint);

            Bitmap argbBitmap = alphaBitmap.copy(Bitmap.Config.ARGB_8888, false);
            alphaBitmap.recycle();
            int[] pixels = new int[bw * bh];
            argbBitmap.getPixels(pixels, 0, bw, 0, 0, bw, bh);
            argbBitmap.recycle();

            textPixels = pixels;
            cachedText = text;
            cachedTextSize = cfg.textSize;
            cachedTextWidth = bw;
            cachedTextHeight = bh;
        }

        final int bw = cachedTextWidth;
        final int bh = cachedTextHeight;
        final int[] pixels = textPixels;

        final int centerX = bw / 2;
        final int centerY = bh / 2;
        final int currentCount = activeParticles.size();
        final int stepBase = (int) (cfg.textSize / 35);
        final int step = (currentCount > maxParticles * 0.7) ? stepBase + 3 : Math.max(2, stepBase);

        for (int ix = 0; ix < bw; ix += step) {
            for (int iy = 0; iy < bh; iy += step) {
                if (pixels[iy * bw + ix] != 0) {
                    float rx = ix + (random.nextFloat() - 0.5f) * step;
                    float ry = iy + (random.nextFloat() - 0.5f) * step;

                    float dx = rx - centerX;
                    float dy = ry - centerY;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    float nx = dx / (dist + 1f);
                    float ny = dy / (dist + 1f);

                    float force = (dist * 0.12f + 2f) * cfg.explosionRange;
                    float vx = nx * force + (random.nextFloat() - 0.5f) * 2f;
                    float vy = ny * force + (random.nextFloat() - 0.5f) * 2f;

                    spawnTextParticle(x, y, vx, vy, cfg, isRand, hue);
                }
            }
        }
    }

    private void spawnTextParticle(float x, float y, float vx, float vy, FireworkConfig cfg, boolean isRand, float hue) {
        Particle p = obtainParticle();
        float brightness = 0.8f + random.nextFloat() * 0.2f;
        int color = isRand ? Color.HSVToColor(new float[]{hue + random.nextInt(30) - 15, 0.7f, brightness})
                : cfg.colors[random.nextInt(cfg.colors.length)];

        float decay = (0.006f + random.nextFloat() * 0.01f) / cfg.duration;
        p.reset(x, y, vx, vy, color, 3f + random.nextFloat() * 2f, decay, 0, cfg.trailLength * 0.4f);
        activeParticles.add(p);
    }

    private void createByCircle(float x, float y, FireworkConfig cfg, boolean isRand, float hue) {
        for (int i = 0; i < cfg.particleCount; i++) {
            double angle = Math.toRadians(random.nextInt(360));
            float speed = (8 + random.nextFloat() * 18) * cfg.explosionRange;
            spawn(x, y, (float) (Math.cos(angle) * speed), (float) (Math.sin(angle) * speed * 0.8f), cfg, isRand, hue, 0);
        }
    }

    private void createByMath(float x, float y, FireworkConfig cfg, boolean isHeart, boolean isRand, float hue) {
        int count = cfg.particleCount * 2;
        for (int i = 0; i < count; i++) {
            double t = 2 * Math.PI * i / count;
            float vx, vy;
            if (isHeart) {
                vx = (float) (16 * Math.pow(Math.sin(t), 3));
                vy = (float) -(13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t));
            } else {
                double r = 15 * (Math.abs(Math.cos(2.5 * t)) + 0.5);
                vx = (float) (r * Math.cos(t));
                vy = (float) (r * Math.sin(t));
            }
            vx *= cfg.explosionRange;
            vy *= cfg.explosionRange;
            spawn(x, y, vx, vy, cfg, isRand, hue, 0);
        }
    }

    private void spawn(float x, float y, float vx, float vy, FireworkConfig cfg, boolean isRand, float hue, int type) {
        Particle p = obtainParticle();
        int color = isRand ? Color.HSVToColor(new float[]{hue + random.nextInt(40) - 20, 0.8f, 1.0f})
                : cfg.colors[random.nextInt(cfg.colors.length)];
        float decay = (0.01f + random.nextFloat() * 0.015f) / cfg.duration;
        p.reset(x, y, vx, vy, color, 4f + random.nextFloat() * 3, decay, type, cfg.trailLength);
        activeParticles.add(p);
    }

    private Particle obtainParticle() {
        Particle p = particlePool.poll();
        return (p == null) ? new Particle() : p;
    }

    private void recycleParticle(Particle p) {
        if (particlePool.size() < maxParticles) {
            particlePool.offer(p);
        }
    }

    private static class ExplosionTask {
        float x, y;
        FireworkConfig cfg;
        ExplosionTask(float x, float y, FireworkConfig cfg) {
            this.x = x; this.y = y; this.cfg = cfg;
        }
    }

    private static class Particle {
        float x, y, vx, vy, size, life, decay, trailScale, targetY;
        int color, type;
        Object tag;

        void reset(float x, float y, float vx, float vy, int color, float size, float decay, int type, float trailScale) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.color = color; this.size = size; this.decay = decay;
            this.type = type; this.trailScale = trailScale;
            this.life = 1.0f;
            this.tag = null;
        }

        /**
         * 按时间补偿步进。step=1 与原版"每动画帧走一步"完全等价；
         * step 为真实帧间隔相对 60fps 的倍率，dampFactor 为按 step 折算后的阻尼系数。
         */
        boolean update(Random random, float step, float dampFactor) {
            x += vx * step;
            y += vy * step;
            if (type == 2) {
                if (y <= targetY) return false;
                vx += (random.nextFloat() - 0.5f) * 2f * step;
            } else {
                vx *= dampFactor;
                vy = vy * dampFactor + 0.15f * step;
                life -= decay * step;
            }
            return life > 0;
        }
    }
}
