package com.xiaobo.fireworks;

import android.graphics.Color;

public class FireworkConfig {
    public enum Shape { CIRCLE, HEART, STAR, TEXT } // 支持形状：圆形、爱心、五角星、文字

    public int particleCount;
    public float explosionRange;
    public float trailLength;
    public float duration;
    public int[] colors;
    public boolean hasSparkle;

    // 新增自定义项
    public Shape shape = Shape.CIRCLE;
    public String text = "❤";       // 字符烟花的内容
    public float textSize = 100f;    // 字符采样大小
    public boolean launchRocket = false; // 是否先有一个上升过程

    private FireworkConfig(Builder builder) {
        this.particleCount = builder.particleCount;
        this.explosionRange = builder.explosionRange;
        this.trailLength = builder.trailLength;
        this.duration = builder.duration;
        this.colors = builder.colors;
        this.hasSparkle = builder.hasSparkle;
        this.shape = builder.shape;
        this.text = builder.text;
        this.textSize = builder.textSize;
        this.launchRocket = builder.launchRocket;
    }

    public static class Builder {
        private int particleCount = 60;
        private float explosionRange = 1.0f;
        private float trailLength = 1.0f;
        private float duration = 1.0f;
        private int[] colors = null;
        private boolean hasSparkle = true;
        private Shape shape = Shape.CIRCLE;
        private String text = "A";
        private float textSize = 100f;
        private boolean launchRocket = false;

        public Builder count(int count) { this.particleCount = count; return this; }
        public Builder range(float range) { this.explosionRange = range; return this; }
        public Builder trail(float scale) { this.trailLength = scale; return this; }
        public Builder duration(float scale) { this.duration = scale; return this; }
        public Builder sparkle(boolean enable) { this.hasSparkle = enable; return this; }
        public Builder colors(int... colors) { this.colors = colors; return this; }

        /** 设置形状 */
        public Builder shape(Shape shape) { this.shape = shape; return this; }
        /** 设置烟花文字内容 (当shape为TEXT时生效) */
        public Builder text(String text) { this.text = text; return this; }
        public Builder rocket(boolean enable) { this.launchRocket = enable; return this; }

        public FireworkConfig build() { return new FireworkConfig(this); }
    }
}