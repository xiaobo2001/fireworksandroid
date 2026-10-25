package com.xiaobo.fireworks;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 【v1.3.1】emoji/字符 图形位图缓存：为 {@link FireworkConfig.Shape#CUSTOM} 形状提供
 * 统一的一次性渲染 + LRU 复用。App 内多个模块（Activity / 壁纸服务）共享同一份缓存，
 * 避免各自重复渲染 220px ARGB 位图造成双份内存。
 *
 * <pre>
 * Bitmap rose = EmojiBitmapCache.get(context, "🌹", 220);
 * FireworkConfig cfg = new FireworkConfig.Builder()
 *         .shape(FireworkConfig.Shape.CUSTOM)
 *         .shapeBitmap(rose)
 *         .build();
 * </pre>
 */
public final class EmojiBitmapCache {

    private static final int MAX_ENTRIES = 16;

    private static final LinkedHashMap<String, Bitmap> CACHE =
            new LinkedHashMap<String, Bitmap>(8, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
                    if (size() > MAX_ENTRIES) {
                        Bitmap b = eldest.getValue();
                        if (b != null && !b.isRecycled()) {
                            try {
                                b.recycle();
                            } catch (Exception ignored) {
                            }
                        }
                        return true;
                    }
                    return false;
                }
            };

    private EmojiBitmapCache() {
    }

    /**
     * 获取 emoji/单字符图形位图（按 emoji + 尺寸缓存，重复调用零渲染开销）。
     *
     * @param emoji emoji 或任意短字符（受系统字体支持情况影响）
     * @param px    位图边长（像素）
     */
    public static synchronized Bitmap get(Context context, String emoji, int px) {
        if (emoji == null || emoji.isEmpty() || px <= 0) {
            return Bitmap.createBitmap(Math.max(8, px), Math.max(8, px), Bitmap.Config.ARGB_8888);
        }
        String key = px + ":" + emoji;
        Bitmap cached = CACHE.get(key);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        Bitmap bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(px * 0.8f);
        c.drawText(emoji, px / 2f, px * 0.76f, p);
        CACHE.put(key, bmp);
        return bmp;
    }

    /** 清空缓存并回收全部位图（一般无需手动调用，LRU 自动淘汰） */
    public static synchronized void clear() {
        for (Bitmap b : CACHE.values()) {
            if (b != null && !b.isRecycled()) {
                try {
                    b.recycle();
                } catch (Exception ignored) {
                }
            }
        }
        CACHE.clear();
    }
}
