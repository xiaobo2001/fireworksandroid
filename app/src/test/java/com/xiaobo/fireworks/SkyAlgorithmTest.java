package com.xiaobo.fireworks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 【v1.3.0】夜空关键算法单元测试：星座日期分界 + 朔望月月龄。
 */
public class SkyAlgorithmTest {

    // ===== 星座日期分界 =====

    @Test
    public void zodiac_boundaries() {
        // 白羊 3.21 - 4.19
        assertEquals(0, SkyView.zodiacIndex(3, 21));
        assertEquals(0, SkyView.zodiacIndex(3, 31));
        assertEquals(0, SkyView.zodiacIndex(4, 19));
        assertEquals(1, SkyView.zodiacIndex(4, 20));
        // 双子 5.21 - 6.21
        assertEquals(2, SkyView.zodiacIndex(5, 21));
        assertEquals(2, SkyView.zodiacIndex(6, 21));
        assertEquals(3, SkyView.zodiacIndex(6, 22));
        // 天秤 9.23 - 10.23
        assertEquals(6, SkyView.zodiacIndex(9, 23));
        assertEquals(6, SkyView.zodiacIndex(10, 23));
        assertEquals(7, SkyView.zodiacIndex(10, 24));
        // 射手 11.23 - 12.21
        assertEquals(8, SkyView.zodiacIndex(11, 23));
        assertEquals(8, SkyView.zodiacIndex(12, 21));
        assertEquals(9, SkyView.zodiacIndex(12, 22));
        // 摩羯 12.22 - 1.19（跨年）
        assertEquals(9, SkyView.zodiacIndex(12, 31));
        assertEquals(9, SkyView.zodiacIndex(1, 1));
        assertEquals(9, SkyView.zodiacIndex(1, 19));
        // 水瓶 1.20 - 2.18
        assertEquals(10, SkyView.zodiacIndex(1, 20));
        assertEquals(10, SkyView.zodiacIndex(2, 18));
        // 双鱼 2.19 - 3.20
        assertEquals(11, SkyView.zodiacIndex(2, 19));
        assertEquals(11, SkyView.zodiacIndex(3, 20));
    }

    // ===== 朔望月月龄 =====

    private static final long EPOCH_MS = 947182440000L; // 2000-01-06 18:14 UTC 新月
    private static final double SYNODIC = 29.530588853;
    private static final long DAY_MS = 86400000L;

    @Test
    public void moonAge_epochIsNewMoon() {
        assertEquals(0f, SkyView.moonAgeFromMillis(EPOCH_MS), 0.02f);
    }

    @Test
    public void moonAge_fullMoonHalfCycle() {
        assertEquals((float) (SYNODIC / 2),
                SkyView.moonAgeFromMillis(EPOCH_MS + Math.round(SYNODIC / 2 * DAY_MS)), 0.1f);
    }

    @Test
    public void moonAge_wrapsInSynodicMonth() {
        float age = SkyView.moonAgeFromMillis(EPOCH_MS + Math.round(SYNODIC * 3 * DAY_MS));
        assertEquals(0f, age, 0.5f);
        assertTrue(age >= 0f && age < SYNODIC);
    }

    @Test
    public void moonAge_alwaysInRange() {
        // 任意时刻月龄落在 [0, 29.53)
        for (long offsetDays = 0; offsetDays < 400; offsetDays += 3) {
            float age = SkyView.moonAgeFromMillis(EPOCH_MS + offsetDays * DAY_MS + 1234567L);
            assertTrue(age >= 0f && age < SYNODIC);
        }
    }
}
