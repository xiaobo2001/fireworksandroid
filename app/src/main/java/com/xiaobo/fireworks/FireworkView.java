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

    // 1. 显著提升粒子上限，确保多个烟花连发不卡死
    private static final int MAX_PARTICLES = 5000;
    private final List<Particle> activeParticles = new ArrayList<>(MAX_PARTICLES);
    private final Queue<Particle> particlePool = new ArrayDeque<>(MAX_PARTICLES);
    private final Paint paint = new Paint();
    private final Random random = new Random();

    private final FireworkConfig defaultConfig = new FireworkConfig.Builder().build();
    private boolean isAnimating = false;
    private boolean isStopped = false;

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
    }

    public void stop() {
        isStopped = true;
        activeParticles.clear();
        isAnimating = false;
        invalidate();
    }

    public void resume() {
        isStopped = false;
    }

    /**
     * 核心修复：即使粒子快满了，也允许发射火箭，只是限制爆炸产生的碎片数
     */
    public void launch(float x, float y, FireworkConfig config) {
        if (isStopped) return;

        // 如果粒子快满了，不再产生新粒子，保护性能
        if (activeParticles.size() > MAX_PARTICLES - 200) return;

        final FireworkConfig cfg = (config != null) ? config : defaultConfig;

        if (cfg.launchRocket) {
            Particle p = obtainParticle();
            // 火箭上升速度和位置
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

    // --- 优化：根据当前剩余空间动态调整文字采样步长 ---
    private void createByText(float x, float y, FireworkConfig cfg, boolean isRand, float hue) {
        Paint textPaint = new Paint();
        textPaint.setTextSize(cfg.textSize);
        textPaint.setFakeBoldText(true);
        textPaint.setAntiAlias(true);

        Rect bounds = new Rect();
        textPaint.getTextBounds(cfg.text, 0, cfg.text.length(), bounds);

        Bitmap bitmap = Bitmap.createBitmap(bounds.width() + 40, bounds.height() + 40, Bitmap.Config.ALPHA_8);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawText(cfg.text, 20 - bounds.left, 20 - bounds.top, textPaint);

        int centerX = bitmap.getWidth() / 2;
        int centerY = bitmap.getHeight() / 2;

        // 核心修复：动态计算步长。如果当前屏幕粒子多，就采样稀疏点，防止后续点击失效
        int currentCount = activeParticles.size();
        int stepBase = (int) (cfg.textSize / 35);
        int step = (currentCount > MAX_PARTICLES * 0.7) ? stepBase + 3 : Math.max(2, stepBase);

        for (int ix = 0; ix < bitmap.getWidth(); ix += step) {
            for (int iy = 0; iy < bitmap.getHeight(); iy += step) {
                if (bitmap.getPixel(ix, iy) != 0) {
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
        bitmap.recycle();
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

    @Override
    protected void onDraw(Canvas canvas) {
        if (isStopped || activeParticles.isEmpty()) {
            isAnimating = false;
            return;
        }

        // 使用待爆炸任务列表，避免在迭代时修改 activeParticles
        List<ExplosionTask> pendingExplosions = new ArrayList<>();

        // 使用 Iterator 可以在遍历时安全地 remove 死亡粒子
        Iterator<Particle> iterator = activeParticles.iterator();
        while (iterator.hasNext()) {
            Particle p = iterator.next();
            boolean alive = p.update();

            if (!alive) {
                // 如果是火箭到达目标点，记录爆炸位置
                if (p.type == 2 && p.tag instanceof FireworkConfig) {
                    pendingExplosions.add(new ExplosionTask(p.x, p.y, (FireworkConfig) p.tag));
                }
                iterator.remove();
                recycleParticle(p);
            } else {
                drawParticle(canvas, p);
            }
        }

        // 执行爆炸
        for (ExplosionTask task : pendingExplosions) {
            explode(task.x, task.y, task.cfg);
        }

        if (!activeParticles.isEmpty()) {
            postInvalidateOnAnimation();
        } else {
            isAnimating = false;
        }
    }

    private void drawParticle(Canvas canvas, Particle p) {
        paint.setColor(p.color);
        paint.setAlpha((int) (255 * p.life));
        paint.setStrokeWidth(p.size);

        if (p.type == 2) {
            canvas.drawCircle(p.x, p.y, p.size, paint);
        } else {
            if (p.trailScale > 0.1f) {
                canvas.drawLine(p.x, p.y, p.x - p.vx * p.trailScale, p.y - p.vy * p.trailScale, paint);
            } else {
                canvas.drawCircle(p.x, p.y, p.size / 2, paint);
            }
        }
    }

    private Particle obtainParticle() {
        Particle p = particlePool.poll();
        return (p == null) ? new Particle() : p;
    }

    private void recycleParticle(Particle p) {
        if (particlePool.size() < MAX_PARTICLES) {
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

        boolean update() {
            x += vx;
            y += vy;
            if (type == 2) {
                if (y <= targetY) return false;
                vx += (float) (Math.random() - 0.5f) * 2f;
            } else {
                vx *= 0.93f; // 空气阻力
                vy *= 0.93f;
                vy += 0.15f; // 重力：让文字消散更有流动感
                life -= decay;
            }
            return life > 0;
        }
    }
}
