package com.xiaobo.fireworks;

import android.graphics.Color;

public class FireworkConfig {
    public int particleCount;     // 粒子数量
    public float explosionRange;  // 爆炸范围 (0.5 - 2.0)
    public float trailLength;     // 拖尾长度 (0.0 - 3.0)
    public float duration;        // 持续时间 (0.5 - 2.0)
    public int[] colors;          // 指定颜色组 (null为随机)
    public boolean hasSparkle;    // 是否开启闪烁微粒

    private FireworkConfig(Builder builder) {
        this.particleCount = builder.particleCount;
        this.explosionRange = builder.explosionRange;
        this.trailLength = builder.trailLength;
        this.duration = builder.duration;
        this.colors = builder.colors;
        this.hasSparkle = builder.hasSparkle;
    }

    public static class Builder {
        private int particleCount = 60;
        private float explosionRange = 1.0f;
        private float trailLength = 1.0f;
        private float duration = 1.0f;
        private int[] colors = null;
        private boolean hasSparkle = true;

        /** 粒子数量 (建议 40-100) */
        public Builder count(int count) { this.particleCount = count; return this; }
        /** 爆炸范围 (速度)，默认1.0，越大炸得越开 */
        public Builder range(float range) { this.explosionRange = range; return this; }
        /** 拖尾长度，默认1.0，0为无拖尾(圆点) */
        public Builder trail(float scale) { this.trailLength = scale; return this; }
        /** 持续时间，默认1.0，越大消失越慢 */
        public Builder duration(float scale) { this.duration = scale; return this; }
        /** 是否有闪烁粒子 */
        public Builder sparkle(boolean enable) { this.hasSparkle = enable; return this; }
        /** 指定颜色 (例如: Color.RED, Color.YELLOW)，不传则全色谱随机 */
        public Builder colors(int... colors) { this.colors = colors; return this; }

        public FireworkConfig build() { return new FireworkConfig(this); }
    }
}