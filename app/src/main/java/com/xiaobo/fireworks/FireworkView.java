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

/**
 * 核心烟花视图：支持物理效果、形状采样、生命周期管理
 */
public class FireworkView extends View {

    private static final int MAX_PARTICLES = 1500; // 调高上限以支持文字采样
    private final List<Particle> activeParticles = new ArrayList<>(MAX_PARTICLES);
    private final Queue<Particle> particlePool = new ArrayDeque<>(MAX_PARTICLES);
    private final Paint paint = new Paint();
    private final Random random = new Random();

    private final FireworkConfig defaultConfig = new FireworkConfig.Builder().build();
    private boolean isAnimating = false;
    private boolean isStopped = false;

    public FireworkView(Context context) { super(context); init(); }
    public FireworkView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setAntiAlias(true);
        paint.setDither(true);
    }

    /**
     * 对应 GlobalFirework 中的调用，停止动画并清空
     */
    public void stop() {
        isStopped = true;
        activeParticles.clear();
        isAnimating = false;
        invalidate();
    }

    /**
     * 对应 GlobalFirework 中的调用，恢复可用状态
     */
    public void resume() {
        isStopped = false;
    }

    /**
     * 发射烟花入口
     */
    public void launch(float x, float y, FireworkConfig config) {
        if (isStopped || activeParticles.size() >= MAX_PARTICLES) return;

        final FireworkConfig cfg = (config != null) ? config : defaultConfig;

        if (cfg.launchRocket) {
            // 创建火箭粒子：从屏幕底部飞向目标 Y
            Particle p = obtainParticle();
            p.reset(x, getHeight(), 0, -28f, Color.YELLOW, 8f, 1.0f, 2, 1.0f);
            p.targetY = y;
            p.tag = cfg; // 携带配置，到达位置后触发爆炸
            activeParticles.add(p);
        } else {
            explode(x, y, cfg);
        }

        if (!isAnimating) {
            isAnimating = true;
            invalidate();
        }
    }

    /**
     * 真正的爆炸逻辑：根据形状生成粒子组
     */
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

    // 1. 经典圆形喷射
    private void createByCircle(float x, float y, FireworkConfig cfg, boolean isRand, float hue) {
        for (int i = 0; i < cfg.particleCount; i++) {
            double angle = Math.toRadians(random.nextInt(360));
            float speed = (8 + random.nextFloat() * 18) * cfg.explosionRange;
            spawn(x, y, (float)(Math.cos(angle)*speed), (float)(Math.sin(angle)*speed*0.8f), cfg, isRand, hue, 0);
        }
    }

    // 2. 数学形状（爱心、星形）
    private void createByMath(float x, float y, FireworkConfig cfg, boolean isHeart, boolean isRand, float hue) {
        int count = cfg.particleCount * 2;
        for (int i = 0; i < count; i++) {
            double t = 2 * Math.PI * i / count;
            float vx, vy;
            if (isHeart) {
                vx = (float) (16 * Math.pow(Math.sin(t), 3));
                vy = (float) -(13 * Math.cos(t) - 5 * Math.cos(2*t) - 2 * Math.cos(3*t) - Math.cos(4*t));
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

    // 3. 字符采样（将文字转为像素点粒子）
    private void createByText(float x, float y, FireworkConfig cfg, boolean isRand, float hue) {
        Paint textPaint = new Paint();
        textPaint.setTextSize(cfg.textSize);
        textPaint.setFakeBoldText(true);

        Rect bounds = new Rect();
        textPaint.getTextBounds(cfg.text, 0, cfg.text.length(), bounds);

        // 创建位图提取像素信息
        Bitmap bitmap = Bitmap.createBitmap(bounds.width() + 20, bounds.height() + 20, Bitmap.Config.ALPHA_8);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawText(cfg.text, 10 - bounds.left, 10 - bounds.top, textPaint);

        int step = 3; // 步长，越小粒子越密集，建议保持在3-5
        for (int ix = 0; ix < bitmap.getWidth(); ix += step) {
            for (int iy = 0; iy < bitmap.getHeight(); iy += step) {
                if (bitmap.getPixel(ix, iy) != 0) {
                    float vx = (ix - bitmap.getWidth() / 2f) * 0.4f * cfg.explosionRange;
                    float vy = (iy - bitmap.getHeight() / 2f) * 0.4f * cfg.explosionRange;
                    spawn(x, y, vx, vy, cfg, isRand, hue, 0);
                }
            }
        }
        bitmap.recycle();
    }

    private void spawn(float x, float y, float vx, float vy, FireworkConfig cfg, boolean isRand, float hue, int type) {
        Particle p = obtainParticle();
        int color = isRand ? Color.HSVToColor(new float[]{hue + random.nextInt(40) - 20, 0.8f, 1.0f})
                : cfg.colors[random.nextInt(cfg.colors.length)];
        float decay = (0.012f + random.nextFloat() * 0.015f) / cfg.duration;
        p.reset(x, y, vx, vy, color, 4f + random.nextFloat() * 3, decay, type, cfg.trailLength);
        activeParticles.add(p);
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
            if (!p.update()) {
                // 如果是火箭，到达高度后触发爆炸
                if (p.type == 2 && p.tag instanceof FireworkConfig) {
                    explode(p.x, p.y, (FireworkConfig) p.tag);
                }
                iterator.remove();
                recycleParticle(p);
            } else {
                drawParticle(canvas, p);
            }
        }

        if (isAnimating) postInvalidateOnAnimation();
    }

    private void drawParticle(Canvas canvas, Particle p) {
        paint.setColor(p.color);
        paint.setAlpha((int) (255 * p.life));
        paint.setStrokeWidth(p.size);

        if (p.type == 2) { // 火箭
            canvas.drawCircle(p.x, p.y, p.size, paint);
        } else { // 烟花粒子带拖尾
            if (p.trailScale > 0) {
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
        if (particlePool.size() < MAX_PARTICLES) particlePool.offer(p);
    }

    private static class Particle {
        float x, y, vx, vy, size, life, decay, trailScale, targetY;
        int color, type; // 0:普通, 1:闪烁, 2:火箭
        Object tag;

        void reset(float x, float y, float vx, float vy, int color, float size, float decay, int type, float trailScale) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.color = color; this.size = size; this.decay = decay;
            this.type = type; this.trailScale = trailScale;
            this.life = 1.0f;
            this.tag = null;
        }

        boolean update() {
            x += vx; y += vy;
            if (type == 2) { // 火箭逻辑
                if (y <= targetY) return false;
                vx += (float)(Math.random() - 0.5f) * 1.5f; // 轻微摆动
            } else {
                vy += 0.22f; // 重力
                vx *= 0.95f; // 空气阻力
                vy *= 0.95f;
                life -= decay;
            }
            return life > 0;
        }
    }
}