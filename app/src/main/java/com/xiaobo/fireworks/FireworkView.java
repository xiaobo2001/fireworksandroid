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

/**
 * 极致优化版烟花
 * 1. 视觉：颜色随温度渐变 (白->彩->隐)、增加闪烁粒子
 * 2. 性能：智能限流、ArrayDeque池化
 */
public class FireworkView extends View {

    // 限制最大粒子数，防止低端机卡死
    private static final int MAX_PARTICLES = 400;

    private final List<Particle> activeParticles = new ArrayList<>(MAX_PARTICLES);
    // 使用 ArrayDeque 作为对象池，性能比 LinkedList 快 15%
    private final Queue<Particle> particlePool = new ArrayDeque<>(MAX_PARTICLES);

    private final Paint paint = new Paint();
    private final Random random = new Random();

    private boolean isAnimating = false;
    private boolean isStopped = false;

    public FireworkView(Context context) { super(context); init(); }
    public FireworkView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        // 使用 ROUND 笔触，线条更顺滑
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setAntiAlias(true);
        // 开启抖动，让渐变色更自然
        paint.setDither(true);

        setClickable(false);
        setFocusable(false);
    }

    public void stop() {
        isStopped = true;
        isAnimating = false;
        activeParticles.clear();
        invalidate();
    }

    public void resume() {
        isStopped = false;
    }

    public void launch(float x, float y) {
        if (isStopped) return;
        // 如果当前屏幕粒子太多，本次不发射，优先保证流畅度
        if (activeParticles.size() > MAX_PARTICLES) return;

        // 1. 发射核心烟花 (流光)
        int count = 40 + random.nextInt(20);
        float baseHue = random.nextInt(360); // 随机主色调

        for (int i = 0; i < count; i++) {
            Particle p = obtainParticle();
            // 颜色在主色调附近微调
            float hue = baseHue + (random.nextFloat() * 30 - 15);
            // 核心粒子：类型 0
            p.reset(x, y, hue, 0);
            activeParticles.add(p);
        }

        // 2. 发射闪耀微粒 (Sparkle) - 增加华丽感
        int sparkCount = 10 + random.nextInt(10);
        for (int i = 0; i < sparkCount; i++) {
            Particle p = obtainParticle();
            // 闪光粒子：类型 1 (金色/白色)
            p.reset(x, y, 50f, 1);
            activeParticles.add(p);
        }

        if (!isAnimating) {
            isAnimating = true;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // 移除 super.onDraw 减少一层空调用
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

        if (isAnimating) {
            // 使用 postInvalidateOnAnimation 配合 Choreographer 刷新，比 invalidate 更省电流畅
            postInvalidateOnAnimation();
        }
    }

    /**
     * 绘制单个粒子 (视觉核心)
     */
    private void drawParticle(Canvas canvas, Particle p) {
        // === 视觉优化：颜色随寿命变化 ===
        // 刚出生(life接近1.0)是白色/高亮，快死(life接近0)变暗

        paint.setStrokeWidth(p.size);

        if (p.type == 0) {
            // --- 类型0：流光烟花 ---
            // 1. 计算当前颜色
            int color = getDynamicColor(p.hue, p.life);
            paint.setColor(color);
            // 流光拖尾：速度越快，尾巴越长
            float trailLen = 3.0f + (p.totalSpeed * 0.15f);

            canvas.drawLine(p.x, p.y,
                    p.x - p.vx * trailLen,
                    p.y - p.vy * trailLen,
                    paint);

            // 2. 头部高光 (模拟燃烧核心)
            if (p.life > 0.6f) {
                paint.setColor(Color.WHITE);
                paint.setAlpha((int)(255 * p.life));
                canvas.drawCircle(p.x, p.y, p.size * 0.6f, paint);
            }
        } else {
            // --- 类型1：闪烁微粒 ---
            // 随机闪烁
            int alpha = (int) (255 * p.life * (0.5f + random.nextFloat() * 0.5f));
            paint.setColor(Color.WHITE); // 闪光总是白/金色的
            paint.setAlpha(alpha);

            // 画十字星或者圆点
            canvas.drawCircle(p.x, p.y, p.size, paint);
        }
    }

    // 根据色相和生命周期生成颜色
    private int getDynamicColor(float hue, float life) {
        float saturation = 0.8f; // 饱和度
        float value = 1.0f;      // 亮度

        // 随着生命消逝，饱和度增加(变深)，亮度降低(变暗)
        if (life < 0.5f) {
            value = life * 2.0f; // 快速变暗
        }

        // HSV转Color是耗时操作，但对于几百个粒子来说还是毫秒级的
        int color = Color.HSVToColor((int)(255 * life), new float[]{hue, saturation, value});
        return color;
    }

    private Particle obtainParticle() {
        Particle p = particlePool.poll();
        return (p == null) ? new Particle() : p;
    }

    private void recycleParticle(Particle p) {
        if (particlePool.size() < MAX_PARTICLES) particlePool.offer(p);
    }

    // --- 纯数据类 (轻量化) ---
    private static class Particle {
        float x, y, vx, vy;
        float size;
        float hue;    // 色相
        float life;   // 生命周期 1.0 -> 0.0
        float decay;  // 衰减速度
        float totalSpeed; // 速度模长缓存
        int type;     // 0=Main, 1=Spark

        static final Random R = new Random();

        void reset(float startX, float startY, float hue, int type) {
            this.x = startX; this.y = startY;
            this.hue = hue;
            this.type = type;
            this.life = 1.0f;

            double angle = Math.toRadians(R.nextInt(360));
            double speed;

            if (type == 0) {
                // 主烟花
                speed = 10 + R.nextFloat() * 18;
                this.size = 3f + R.nextFloat() * 3f;
                this.decay = 0.015f + R.nextFloat() * 0.01f; // 存活约40-60帧
            } else {
                // 闪光微粒 (炸得更开，死得更快)
                speed = 15 + R.nextFloat() * 20;
                this.size = 2f + R.nextFloat() * 2f;
                this.decay = 0.03f + R.nextFloat() * 0.02f;
            }

            this.vx = (float) (Math.cos(angle) * speed);
            this.vy = (float) (Math.sin(angle) * speed * 0.85); // 压扁Y轴做透视
        }

        boolean update() {
            x += vx; y += vy;

            if (type == 0) {
                // 主烟花物理
                vy += 0.25f;  // 重力
                vx *= 0.94f;  // 阻力
                vy *= 0.94f;
            } else {
                // 闪光微粒物理 (阻力更大，飘浮感)
                vy += 0.15f;
                vx *= 0.90f;
                vy *= 0.90f;
            }

            // 缓存速度，用于画拖尾长度
            totalSpeed = Math.abs(vx) + Math.abs(vy);

            life -= decay;
            return life > 0;
        }
    }
}