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
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * 核心烟花视图：支持物理效果、形状采样、生命周期管理
 */
public class FireworkView extends View {

    private static final int MAX_PARTICLES = 1500;
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
        if (isStopped || activeParticles.size() >= MAX_PARTICLES) return;

        final FireworkConfig cfg = (config != null) ? config : defaultConfig;

        if (cfg.launchRocket) {
            Particle p = obtainParticle();
            p.reset(x, getHeight(), 0, -28f, Color.YELLOW, 8f, 1.0f, 2, 1.0f);
            p.targetY = y;
            p.tag = cfg;
            activeParticles.add(p);
        } else {
            explode(x, y, cfg);
        }

        if (!isAnimating) {
            isAnimating = true;
            invalidate();
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

//    private void createByText(float x, float y, FireworkConfig cfg, boolean isRand, float hue) {
//        Paint textPaint = new Paint();
//        textPaint.setTextSize(cfg.textSize);
//        textPaint.setFakeBoldText(true);
//
//        Rect bounds = new Rect();
//        textPaint.getTextBounds(cfg.text, 0, cfg.text.length(), bounds);
//
//        Bitmap bitmap = Bitmap.createBitmap(bounds.width() + 20, bounds.height() + 20, Bitmap.Config.ALPHA_8);
//        Canvas canvas = new Canvas(bitmap);
//        canvas.drawText(cfg.text, 10 - bounds.left, 10 - bounds.top, textPaint);
//
//        int step = 3;
//        for (int ix = 0; ix < bitmap.getWidth(); ix += step) {
//            for (int iy = 0; iy < bitmap.getHeight(); iy += step) {
//                if (bitmap.getPixel(ix, iy) != 0) {
//                    float vx = (ix - bitmap.getWidth() / 2f) * 0.4f * cfg.explosionRange;
//                    float vy = (iy - bitmap.getHeight() / 2f) * 0.4f * cfg.explosionRange;
//                    spawn(x, y, vx, vy, cfg, isRand, hue, 0);
//                }
//            }
//        }
//        bitmap.recycle();
//    }

    private void createByText(float x, float y, FireworkConfig cfg, boolean isRand, float hue) {
        Paint textPaint = new Paint();
        textPaint.setTextSize(cfg.textSize);
        textPaint.setFakeBoldText(true);
        textPaint.setAntiAlias(true);

        Rect bounds = new Rect();
        textPaint.getTextBounds(cfg.text, 0, cfg.text.length(), bounds);

        // 适当扩大Bitmap，防止边缘截断
        Bitmap bitmap = Bitmap.createBitmap(bounds.width() + 40, bounds.height() + 40, Bitmap.Config.ALPHA_8);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawText(cfg.text, 20 - bounds.left, 20 - bounds.top, textPaint);

        int centerX = bitmap.getWidth() / 2;
        int centerY = bitmap.getHeight() / 2;

        // 动态调整采样步长，粒子多更有质感
        int step = Math.max(2, (int) (cfg.textSize / 30));

        for (int ix = 0; ix < bitmap.getWidth(); ix += step) {
            for (int iy = 0; iy < bitmap.getHeight(); iy += step) {
                int alpha = bitmap.getPixel(ix, iy);
                if (alpha > 128) { // 只采集深色部分

                    // --- 核心优化 1: 加入位置抖动，消除网格感 ---
                    float offsetX = ix + (random.nextFloat() - 0.5f) * step;
                    float offsetY = iy + (random.nextFloat() - 0.5f) * step;

                    // 计算相对于中心的方向向量
                    float dx = (offsetX - centerX);
                    float dy = (offsetY - centerY);
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);

                    // 归一化方向
                    float nx = dx / (dist + 0.1f);
                    float ny = dy / (dist + 0.1f);

                    // --- 核心优化 2: 径向速度模型 ---
                    // 基础膨胀速度 + 随机爆炸冲力
                    float baseSpeed = dist * 0.15f * cfg.explosionRange;
                    float extraBurst = (random.nextFloat() * 2.0f);

                    float vx = nx * (baseSpeed + extraBurst);
                    float vy = ny * (baseSpeed + extraBurst);

                    // --- 核心优化 3: 赋予初始速度扰动 ---
                    vx += (random.nextFloat() - 0.5f) * 2f;
                    vy += (random.nextFloat() - 0.5f) * 2f;

                    // 产生粒子
                    spawnTextParticle(x, y, vx, vy, cfg, isRand, hue);
                }
            }
        }
        bitmap.recycle();
    }

    /**
     * 专为文字定制的粒子生成，增加随机性
     */
    private void spawnTextParticle(float x, float y, float vx, float vy, FireworkConfig cfg, boolean isRand, float hue) {
        Particle p = obtainParticle();

        // 优化 4: 颜色微调，让文字更有层次感
        float brightness = 0.8f + random.nextFloat() * 0.2f; // 随机亮度
        int color = isRand ? Color.HSVToColor(new float[]{hue + random.nextInt(30) - 15, 0.7f, brightness})
                : cfg.colors[random.nextInt(cfg.colors.length)];

        // 优化 5: 不同的衰减速度，让文字消散时有参差感
        float decay = (0.008f + random.nextFloat() * 0.012f) / cfg.duration;

        // 文字粒子通常不需要太长的拖尾，0.4f 左右比较灵动
        p.reset(x, y, vx, vy, color, 3f + random.nextFloat() * 2f, decay, 0, cfg.trailLength * 0.5f);
        activeParticles.add(p);
    }

    private void spawn(float x, float y, float vx, float vy, FireworkConfig cfg, boolean isRand, float hue, int type) {
        Particle p = obtainParticle();
        int color = isRand ? Color.HSVToColor(new float[]{hue + random.nextInt(40) - 20, 0.8f, 1.0f})
                : cfg.colors[random.nextInt(cfg.colors.length)];
        float decay = (0.012f + random.nextFloat() * 0.015f) / cfg.duration;
        p.reset(x, y, vx, vy, color, 4f + random.nextFloat() * 3, decay, type, cfg.trailLength);
        activeParticles.add(p);
    }

    // ========== 核心修复：重写 onDraw，避免遍历中修改列表 ==========
    @Override
    protected void onDraw(Canvas canvas) {
        if (isStopped || activeParticles.isEmpty()) {
            isAnimating = false;
            return;
        }

        // 1. 暂存待爆炸的火箭任务（位置 + 配置）
        List<Runnable> pendingExplosions = new ArrayList<>();
        // 2. 暂存待回收的死亡粒子
        List<Particle> deadParticles = new ArrayList<>();

        // 3. 仅遍历更新状态 + 绘制，不修改原列表
        for (int i = 0; i < activeParticles.size(); i++) {
            Particle p = activeParticles.get(i);
            boolean alive = p.update();

            if (!alive) {
                deadParticles.add(p);
                // 火箭粒子死亡 → 暂存爆炸任务，遍历结束后执行
                if (p.type == 2 && p.tag instanceof FireworkConfig) {
                    final float px = p.x;
                    final float py = p.y;
                    final FireworkConfig cfg = (FireworkConfig) p.tag;
                    pendingExplosions.add(() -> explode(px, py, cfg));
                }
            } else {
                drawParticle(canvas, p);
            }
        }

        // 4. 遍历结束后，统一执行爆炸（新增粒子）
        for (Runnable task : pendingExplosions) {
            task.run();
        }

        // 5. 统一移除死亡粒子并回收
        for (Particle p : deadParticles) {
            activeParticles.remove(p);
            recycleParticle(p);
        }

        if (isAnimating) postInvalidateOnAnimation();
    }

//    private void drawParticle(Canvas canvas, Particle p) {
//        paint.setColor(p.color);
//        paint.setAlpha((int) (255 * p.life));
//        paint.setStrokeWidth(p.size);
//
//        if (p.type == 2) {
//            canvas.drawCircle(p.x, p.y, p.size, paint);
//        } else {
//            if (p.trailScale > 0) {
//                canvas.drawLine(p.x, p.y, p.x - p.vx * p.trailScale, p.y - p.vy * p.trailScale, paint);
//            } else {
//                canvas.drawCircle(p.x, p.y, p.size / 2, paint);
//            }
//        }
//    }

    private void drawParticle(Canvas canvas, Particle p) {
        paint.setColor(p.color);
        paint.setAlpha((int) (255 * p.life));

        if (p.type == 2) {
            paint.setStrokeWidth(p.size);
            canvas.drawCircle(p.x, p.y, p.size, paint);
        } else {
            // 如果是文字形状产生的粒子，可以稍微画得圆润一点
            paint.setStrokeWidth(p.size);
            if (p.trailScale > 0.1f) {
                // 绘制带有一点点位移的线，模拟动态模糊
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
        int color, type;
        Object tag;

        void reset(float x, float y, float vx, float vy, int color, float size, float decay, int type, float trailScale) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
            this.size = size;
            this.decay = decay;
            this.type = type;
            this.trailScale = trailScale;
            this.life = 1.0f;
            this.tag = null;
        }

        //        boolean update() {
//            x += vx; y += vy;
//            if (type == 2) {
//                if (y <= targetY) return false;
//                vx += (float)(Math.random() - 0.5f) * 1.5f;
//            } else {
//                vy += 0.22f;
//                vx *= 0.95f;
//                vy *= 0.95f;
//                life -= decay;
//            }
//            return life > 0;
//        }
        boolean update() {
            x += vx;
            y += vy;
            if (type == 2) { // 火箭模式
                if (y <= targetY) return false;
                vx += (float) (Math.random() - 0.5f) * 1.5f;
            } else {
                // --- 优化：增加空气阻力，使形状展开后能稍微顿一下 ---
                vx *= 0.94f; // 略微增加阻力（原为0.95f）
                vy *= 0.94f;

                // 模拟重力
                vy += 0.18f; // 略微减弱重力（原为0.22f），让文字停留更久

                life -= decay;
            }
            return life > 0;
        }
    }
}