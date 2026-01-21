package com.xiaobo.fireworks;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Random;

public class FireworkView extends View {

    private static final int MAX_PARTICLES = 800; // 提升上限以支持高密度
    private final List<Particle> activeParticles = new ArrayList<>(MAX_PARTICLES);
    private final Queue<Particle> particlePool = new ArrayDeque<>(MAX_PARTICLES);
    private final Paint paint = new Paint();
    private final Random random = new Random();

    // 默认兜底配置
    private final FireworkConfig defaultConfig = new FireworkConfig.Builder().build();
    private boolean isAnimating = false;
    private boolean isStopped = false;

    public FireworkView(Context context) { super(context); init(); }
    public FireworkView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setAntiAlias(true);
        paint.setDither(true);
        setClickable(false);
        setFocusable(false);
    }

    public void stop() {
        isStopped = true;
        activeParticles.clear();
        invalidate();
    }
    public void resume() { isStopped = false; }

    /**
     * 发射烟花的核心方法
     */
    public void launch(float x, float y, FireworkConfig config) {
        if (isStopped || activeParticles.size() >= MAX_PARTICLES) return;

        FireworkConfig cfg = (config != null) ? config : defaultConfig;
        boolean isRandomColor = (cfg.colors == null || cfg.colors.length == 0);
        float baseHue = isRandomColor ? random.nextInt(360) : 0;

        // 1. 生成主粒子
        for (int i = 0; i < cfg.particleCount; i++) {
            Particle p = obtainParticle();

            // 颜色处理
            int color;
            if (isRandomColor) {
                float hue = baseHue + (random.nextFloat() * 40 - 20);
                color = Color.HSVToColor(new float[]{hue, 0.8f, 1.0f});
            } else {
                color = cfg.colors[random.nextInt(cfg.colors.length)];
            }

            // 物理属性计算
            double angle = Math.toRadians(random.nextInt(360));
            float speed = (10 + random.nextFloat() * 20) * cfg.explosionRange;
            float vx = (float) (Math.cos(angle) * speed);
            float vy = (float) (Math.sin(angle) * speed * 0.75); // 压扁Y轴做透视
            float size = 3f + random.nextFloat() * 4f;
            float decay = (0.015f + random.nextFloat() * 0.01f) / cfg.duration;

            p.reset(x, y, vx, vy, color, size, decay, 0, cfg.trailLength);
            activeParticles.add(p);
        }

        // 2. 生成闪烁微粒
        if (cfg.hasSparkle) {
            int sparkCount = cfg.particleCount / 3;
            for (int i = 0; i < sparkCount; i++) {
                Particle p = obtainParticle();
                double angle = Math.toRadians(random.nextInt(360));
                float speed = (15 + random.nextFloat() * 25) * cfg.explosionRange;
                float decay = (0.03f + random.nextFloat() * 0.02f) / cfg.duration;
                p.reset(x, y,
                        (float)(Math.cos(angle)*speed),
                        (float)(Math.sin(angle)*speed*0.75),
                        Color.WHITE, 2f, decay, 1, 0);
                activeParticles.add(p);
            }
        }

        if (!isAnimating) {
            isAnimating = true;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isStopped || activeParticles.isEmpty()) {
            isAnimating = false;
            return;
        }

        Iterator<Particle> iterator = activeParticles.iterator();
        while (iterator.hasNext()) {
            Particle p = iterator.next();
            boolean alive = p.update();
            if (!alive) {
                iterator.remove();
                recycleParticle(p);
            } else {
                drawParticle(canvas, p);
            }
        }

        if (isAnimating) postInvalidateOnAnimation();
    }

    private void drawParticle(Canvas canvas, Particle p) {
        paint.setStrokeWidth(p.size);
        int alpha = (int) (255 * p.life);

        if (p.type == 0) { // 主粒子
            paint.setColor(p.color);
            paint.setAlpha(alpha);

            if (p.trailScale > 0) {
                // 绘制流光拖尾
                float trailLen = 1.0f + (p.totalSpeed * 0.2f * p.trailScale);
                canvas.drawLine(p.x, p.y, p.x - p.vx * trailLen, p.y - p.vy * trailLen, paint);
            } else {
                canvas.drawCircle(p.x, p.y, p.size / 2, paint);
            }

            // 核心高光
            if (p.life > 0.7f) {
                paint.setColor(Color.WHITE);
                paint.setAlpha((int)(255 * p.life));
                canvas.drawCircle(p.x, p.y, p.size * 0.4f, paint);
            }
        } else { // 闪烁粒子
            int flashAlpha = (int) (255 * p.life * (0.5f + random.nextFloat() * 0.5f));
            paint.setColor(Color.WHITE);
            paint.setAlpha(flashAlpha);
            canvas.drawCircle(p.x, p.y, p.size, paint);
        }
    }

    private Particle obtainParticle() {
        Particle p = particlePool.poll();
        return (p == null) ? new Particle() : p;
    }
    private void recycleParticle(Particle p) {
        if (particlePool.size() < MAX_PARTICLES) particlePool.offer(p);
    }

    private static class Particle {
        float x, y, vx, vy, size, life, decay, totalSpeed, trailScale;
        int color, type; // type 0=Main, 1=Sparkle

        void reset(float x, float y, float vx, float vy, int color, float size, float decay, int type, float trailScale) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.color = color; this.size = size; this.decay = decay;
            this.type = type; this.trailScale = trailScale;
            this.life = 1.0f;
        }

        boolean update() {
            x += vx; y += vy;
            if (type == 0) { vy += 0.25f; vx *= 0.95f; vy *= 0.95f; } // 重力与阻力
            else { vy += 0.15f; vx *= 0.92f; vy *= 0.92f; }
            totalSpeed = Math.abs(vx) + Math.abs(vy);
            life -= decay;
            return life > 0;
        }
    }
}