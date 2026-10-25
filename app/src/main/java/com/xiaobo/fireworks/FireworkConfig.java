package com.xiaobo.fireworks;

import android.graphics.Bitmap;
import android.graphics.Color;

public class FireworkConfig {
    public enum Shape { CIRCLE, HEART, STAR, TEXT, CUSTOM } // 圆形、爱心、五角星、文字、任意图形(位图采样)

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
    /** CUSTOM 形状采样位图（ARGB_8888，按不透明像素生成粒子）；null 时回退为圆形 */
    public Bitmap shapeBitmap;

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
        this.shapeBitmap = builder.shapeBitmap;
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
        private Bitmap shapeBitmap = null;

        public Builder count(int count) { this.particleCount = count; return this; }
        public Builder range(float range) { this.explosionRange = range; return this; }
        public Builder trail(float scale) { this.trailLength = scale; return this; }
        public Builder duration(float scale) { this.duration = scale; return this; }
        public Builder sparkle(boolean enable) { this.hasSparkle = enable; return this; }
        public Builder colors(int... colors) { this.colors = colors; return this; }

        /** 设置形状 */
        public Builder shape(Shape shape) { this.shape = shape; return this; }
        /** 文字烟花内容 (当shape为TEXT时生效) */
        public Builder text(String text) { this.text = text; return this; }
        /** 【新增】文字采样字号 (当shape为TEXT时生效，越大粒子越密) */
        public Builder textSize(float textSize) { this.textSize = textSize; return this; }
        /** 【v1.2.0】任意图形形状位图（配合 shape(Shape.CUSTOM) 使用，ARGB_8888） */
        public Builder shapeBitmap(Bitmap bitmap) { this.shapeBitmap = bitmap; return this; }
        public Builder rocket(boolean enable) { this.launchRocket = enable; return this; }

        public FireworkConfig build() { return new FireworkConfig(this); }
    }
}