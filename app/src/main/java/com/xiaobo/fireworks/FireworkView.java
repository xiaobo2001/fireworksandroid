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

    public void launch(float x, float y, FireworkConfig config) {
        if (isStopped) return;
        if (activeParticles.size() > MAX_PARTICLES - 200) return;

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

    // ========== 新增：手动更新一帧粒子状态（壁纸模式调用） ==========
    public void update() {
        if (isStopped || activeParticles.isEmpty()) {
            isAnimating = false;
            return;
        }

        List<ExplosionTask> pendingExplosions = new ArrayList<>();
        Iterator<Particle> iterator = activeParticles.iterator();

        while (iterator.hasNext()) {
            Particle p = iterator.next();
            boolean alive = p.update();

            if (!alive) {
                if (p.type == 2 && p.tag instanceof FireworkConfig) {
                    pendingExplosions.add(new ExplosionTask(p.x, p.y, (FireworkConfig) p.tag));
                }
                iterator.remove();
                recycleParticle(p);
            }
        }

        for (ExplosionTask task : pendingExplosions) {
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

        for (Particle p : activeParticles) {
            drawParticle(canvas, p);
        }

        if (!activeParticles.isEmpty()) {
            postInvalidateOnAnimation();
        }
    }

    // ========== 新增：仅绘制到指定 Canvas（不触发更新，壁纸模式调用） ==========
    public void drawToCanvas(Canvas canvas) {
        if (isStopped || activeParticles.isEmpty()) return;
        for (Particle p : activeParticles) {
            drawParticle(canvas, p);
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
                vx *= 0.93f;
                vy *= 0.93f;
                vy += 0.15f;
                life -= decay;
            }
            return life > 0;
        }
    }
}
