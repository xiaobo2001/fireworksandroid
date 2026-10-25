# 🎆 Fireworks Android Library 使用文档

[![](https://jitpack.io/v/com.github.xiaobo2001/fireworksandroid.svg)](https://jitpack.io/#xiaobo2001/fireworksandroid)

这是一个高度可定制、高性能的 Android 全局烟花特效库。

它支持物理下落模拟、流光拖尾、火箭升空等视觉效果，引入 **Builder 模式配置**，允许你精确控制烟花的**颜色、粒子数量、爆炸范围、拖尾长度、持续时长**等参数。内置对象池管理内存，绘制路径零分配，物理步进按真实帧间隔自动补偿（高刷屏不加速、低帧率不减速），并适配 Android 5.0 - 16+ 的震动反馈。支持圆形、爱心、五角星、自定义文字四种爆炸形态，提供无反馈发射与反馈防抖，内置动态壁纸模式。

## ✨ 特性 (Features)

*   **🎨 高度自定义**：通过 `FireworkConfig` 配置颜色、数量、范围、拖尾、时长、形状等。
*   **🎯 多元爆炸形态**：内置圆形、爱心、五角星、自定义文字 4 种烟花形状，另支持任意位图图形采样（Shape.CUSTOM），适配节日、告白、祝福等不同场景。
*   **✍️ 文字烟花特效**：通过像素采样将任意文字/字符转化为粒子烟花，相同文字自动复用采样缓存，开销极低。
*   **🚀 真实发射动画**：可选火箭上升效果，还原烟花从底部升空后爆炸的完整视觉流程。
*   **⚡ 零侵入 & 自动化**：初始化后自动附着 Activity，自动管理生命周期。
*   **⚙️ 极致性能**：对象池 + 绘制路径零分配 + 屏外粒子自动裁剪 + 物理步进帧率时间补偿（60fps 基准，90/120Hz 屏幕不加速）。
*   **🔇 无反馈发射**：`bloomSilently` 适合轨迹动画、粒子雨等高频连续发射场景，免除逐次系统调用。
*   **📳 多感官反馈**：自定义音效 + 细腻震动，内置防抖与音效/震动/音量独立开关。
*   **🖼️ 动态壁纸支持**：离屏渲染 API，可与 `WallpaperService` 无缝集成。
*   **🌌 夜空大气层（v1.2.0）**：`SkyView` 一行接入星空+流星+孔明灯+天气粒子(雪/樱花/银杏)+真实月相月亮+当日星座连线，App/壁纸双模式，帧率时间补偿。
*   **🎵 音乐律动（v1.2.0）**：`RhythmFirework` 监听麦克风振幅自适应检测节拍，随背景音乐自动绽放烟花。
*   **🛠️ 简单易用**：一行代码默认触发，也支持高阶配置触发。

---

## 📦 1. 引入依赖 (Installation)

### 第一步：添加 JitPack 仓库
在项目根目录的 `settings.gradle` (或项目级 `build.gradle`) 中添加：

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### 第二步：添加依赖
在 app 模块的 `build.gradle` 中添加：

```groovy
dependencies {
    implementation 'com.github.xiaobo2001:fireworksandroid:v1.2.0' // 请使用最新版本
}
```

---

## 🚀 2. 初始化 (Initialization)

在你的 `Application` 类中进行初始化：

```java
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // 初始化烟花库
        // 参数 1: Application 实例
        // 参数 2: 音效资源 ID (传 -1 则无声，或传入 R.raw.xxx)
        GlobalFirework.getInstance().init(this, R.raw.firework_sound);
    }
}
```

> **⏰ 初始化时机**：`init()` 依赖 `onActivityResumed` 回调把全屏烟花视图挂载到当前 Activity。请务必在**首个 Activity 的 onResume 派发之前**完成初始化（推荐 `Application.onCreate`）。若推迟到首帧之后才调用，首个前台周期内 `bloom` 将静默无效，切后台再回来才会恢复。

---

## 🎮 3. 基础使用 (Basic Usage)

最简单的调用方式，使用库内置的默认配置（随机颜色、标准拖尾、圆形爆炸）。

```java
// 在屏幕坐标 (x, y) 处绽放
GlobalFirework.getInstance().bloom(500, 800);

// 或者在触摸事件中触发
view.setOnTouchListener((v, event) -> {
    if (event.getAction() == MotionEvent.ACTION_DOWN) {
        GlobalFirework.getInstance().bloom(event.getRawX(), event.getRawY());
    }
    return true;
});
```

### 3.1 无反馈发射（高频连续场景推荐）

`bloomSilently` 的粒子效果与 `bloom` 完全一致，但不触发音效与震动，适合轨迹动画、粒子雨等每秒数十次发射的场景，彻底免除逐次 SoundPool/震动 Binder 调用带来的卡顿与耗电：

```java
GlobalFirework.getInstance().bloomSilently(x, y, config);
```

### 3.2 反馈控制（音效 / 震动 / 音量 / 防抖）

```java
GlobalFirework gf = GlobalFirework.getInstance();

gf.setSoundEnabled(false);          // 单独关闭音效
gf.setVibrationEnabled(false);      // 单独关闭震动
gf.setSoundVolume(0.8f);            // 音效音量 0~1，默认 0.5
gf.setFeedbackIntervalMs(120);      // 反馈防抖间隔 ms，默认 120；设 0 恢复旧版逐次反馈
gf.setMaxParticles(3000);           // 粒子上限（默认 5000，低配设备可调小降负载，最小 200）
```

> 防抖说明：连续绽放频繁时（如轨迹动画），反馈最多每 120ms 触发一次；**单次点击完全不受影响**。

---

## 🎨 4. 高级自定义 (Advanced Configuration)

通过 `FireworkConfig` 可以创建千变万化的烟花效果。

### 4.1 创建配置对象

```java
FireworkConfig config = new FireworkConfig.Builder()
        .colors(Color.RED, Color.YELLOW) // 指定颜色组
        .count(50)         // 粒子数量
        .range(1.2f)       // 爆炸范围/速度倍率
        .trail(1.5f)       // 拖尾长度倍率
        .duration(1.0f)    // 持续时间倍率
        .build();
```

### 4.2 应用配置

将配置对象传入 `bloom` 方法即可：

```java
GlobalFirework.getInstance().bloom(x, y, config);
```

### 4.3 形状与发射效果配置

#### 4.3.1 爆炸形状
库内置 4 种爆炸形态，通过 `shape()` 方法指定：
- `Shape.CIRCLE`：经典圆形扩散（默认）
- `Shape.HEART`：爱心形状烟花
- `Shape.STAR`：五角星形状烟花
- `Shape.TEXT`：自定义文字烟花
- `Shape.CUSTOM`：任意图形烟花（v1.2.0 新增，按位图采样）

```java
// 爱心形状烟花
FireworkConfig heartConfig = new FireworkConfig.Builder()
        .shape(FireworkConfig.Shape.HEART)
        .colors(Color.RED, 0xFFFF69B4)
        .build();
```

#### 4.3.2 文字烟花
当形状设置为 `Shape.TEXT` 时，可通过 `text()` 自定义烟花文字内容、`textSize()` 控制采样字号，库会自动对文字进行像素采样并生成对应粒子。相同文字/字号的连续爆炸会自动复用采样缓存，开销极低。

```java
// 文字形状烟花
FireworkConfig textConfig = new FireworkConfig.Builder()
        .shape(FireworkConfig.Shape.TEXT)
        .text("新年快乐")
        .textSize(100f)   // 采样字号，越大粒子越密（默认 100f）
        .colors(Color.RED, Color.YELLOW)
        .range(1.2f)
        .build();
```

#### 4.3.3 火箭升空效果
开启后烟花会从屏幕底部上升到指定坐标后再爆炸，还原真实烟花的发射过程。

```java
// 带火箭升空的烟花
FireworkConfig rocketConfig = new FireworkConfig.Builder()
        .rocket(true) // 开启上升动画
        .colors(Color.GREEN, Color.CYAN)
        .build();
```

#### 4.3.4 任意图形烟花（v1.2.0）
`Shape.CUSTOM` 按位图不透明像素生成粒子，可以把任何图形（emoji、画出的蛋糕/圣诞树、照片剪影等）变成烟花形状；同一位图只采样一次，重复绽放零采样开销。

```java
// 例：把 emoji 渲染成位图后绽放
Bitmap bmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
Canvas c = new Canvas(bmp);
Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
p.setTextSize(180f);
p.setTextAlign(Paint.Align.CENTER);
c.drawText("🎂", 100, 165, p);

FireworkConfig cakeConfig = new FireworkConfig.Builder()
        .shape(FireworkConfig.Shape.CUSTOM)
        .shapeBitmap(bmp)
        .colors(0xFFFFD700, 0xFFFF69B4)
        .count(200)
        .build();

GlobalFirework.getInstance().bloom(x, y, cakeConfig);
```

### 4.4 参数详解

| 方法 | 描述 | 默认值 | 推荐范围 |
| :--- | :--- | :--- | :--- |
| `colors(int...)` | 指定粒子颜色数组，不传则随机全色谱 | null (随机) | ARGB 颜色值 |
| `count(int)` | 爆炸产生的粒子数量 | 60 | 30 - 100 |
| `range(float)` | 爆炸范围系数（影响初速度） | 1.0f | 0.5f - 2.0f |
| `trail(float)` | 粒子拖尾长度系数，0为无拖尾（圆点） | 1.0f | 0.0f - 3.0f |
| `duration(float)` | 持续时间系数（影响衰减），值越大越持久 | 1.0f | 0.8f - 1.5f |
| `sparkle(boolean)` | 保留参数（当前版本暂未产生视觉差异） | true | — |
| `shape(Shape)` | 设置烟花爆炸形状 | `Shape.CIRCLE` | CIRCLE / HEART / STAR / TEXT / CUSTOM |
| `shapeBitmap(Bitmap)` | CUSTOM 形状采样位图（ARGB_8888；null 回退圆形） | null | 任意位图 |
| `text(String)` | 文字烟花的显示内容（仅 TEXT 形状生效） | `"A"` | 任意字符串 |
| `textSize(float)` | 文字采样字号（仅 TEXT 形状生效，越大粒子越密） | `100f` | 60 - 240 |
| `rocket(boolean)` | 是否开启火箭上升后爆炸的发射动画 | `false` | true / false |

---

## 💡 5. 风格示例 (Examples)

### 🧧 场景一：春节/喜庆风格
特点：红金配色、粒子多、炸得开、长拖尾。

```java
FireworkConfig richConfig = new FireworkConfig.Builder()
    .colors(Color.RED, 0xFFFFD700) // 红色 + 金色
    .count(80)
    .range(1.5f)
    .trail(1.5f)
    .rocket(true) // 带升空效果更有节日氛围
    .build();

GlobalFirework.getInstance().bloom(x, y, richConfig);
```

### 👾 场景二：赛博朋克/科技风格
特点：青紫配色、无拖尾（点阵感）、范围小而精。

```java
FireworkConfig cyberConfig = new FireworkConfig.Builder()
    .colors(Color.CYAN, Color.MAGENTA) // 青色 + 洋红
    .range(0.8f)
    .trail(0f)         // 无拖尾，纯圆点
    .build();

GlobalFirework.getInstance().bloom(x, y, cyberConfig);
```

### 💖 场景三：情人节爱心烟花
特点：爱心形状、粉红配色、柔和扩散效果。

```java
FireworkConfig heartConfig = new FireworkConfig.Builder()
    .shape(FireworkConfig.Shape.HEART)
    .colors(Color.RED, 0xFFFF69B4, 0xFFFFC0CB)
    .count(120)
    .range(0.9f)
    .trail(1.2f)
    .build();

GlobalFirework.getInstance().bloom(x, y, heartConfig);
```

### 🎉 场景四：祝福文字烟花
特点：自定义文字内容，适合节日祝福、活动庆典。

```java
FireworkConfig textConfig = new FireworkConfig.Builder()
    .shape(FireworkConfig.Shape.TEXT)
    .text("暴富")
    .colors(Color.RED, 0xFFFFD700)
    .range(1.1f)
    .count(100)
    .rocket(true)
    .build();

GlobalFirework.getInstance().bloom(x, y, textConfig);
```

### 🎲 场景五：随机混合
在点击时随机切换不同的配置，增加趣味性。

```java
btn.setOnClickListener(v -> {
    double random = Math.random();
    if (random < 0.3) {
        GlobalFirework.getInstance().bloom(x, y, richConfig);
    } else if (random < 0.6) {
        GlobalFirework.getInstance().bloom(x, y, heartConfig);
    } else {
        GlobalFirework.getInstance().bloom(x, y, cyberConfig);
    }
});
```

### 💫 场景六：高频轨迹动画（v1.1.0 推荐）
特点：用 `bloomSilently` 每秒发射数十枚小型烟花勾画轨迹/图案，无逐次音效震动开销，性能远优于循环调用 `bloom`。

```java
// 例：用小爱心烟花沿心形曲线每 80ms 打一个点
handler.post(new Runnable() {
    double angle = 0;
    @Override public void run() {
        float px = cx + (float) (16 * Math.pow(Math.sin(angle), 3) * scale);
        float py = cy - (float) (13 * Math.cos(angle) - 5 * Math.cos(2 * angle)
                - 2 * Math.cos(3 * angle) - Math.cos(4 * angle)) * scale;
        GlobalFirework.getInstance().bloomSilently(px, py, smallHeartConfig);
        angle += 0.12;
        handler.postDelayed(this, 80);
    }
});
```

---

## 🖼️ 6. 动态壁纸模式 (Live Wallpaper)

库提供离屏渲染 API，可与 `WallpaperService` 集成，把烟花铺到桌面壁纸：

```java
public class FireworkWallpaperService extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new FireworkEngine();
    }

    private class FireworkEngine extends Engine {
        private HandlerThread drawThread;
        private Handler drawHandler;
        private boolean drawing = false;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setTouchEventsEnabled(true);
            // 壁纸专属初始化（音效传 -1 可静音；与 App 内模式互不干扰）
            GlobalFirework.getInstance().initForWallpaper(getApplicationContext(), -1);
            drawThread = new HandlerThread("FireworkWallpaper");
            drawThread.start();
            drawHandler = new Handler(drawThread.getLooper());
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            // 通知库壁纸画布尺寸
            GlobalFirework.getInstance().setWallpaperSize(width, height);
        }

        private final Runnable drawTask = new Runnable() {
            @Override public void run() {
                if (!drawing || !isVisible()) return;
                SurfaceHolder holder = getSurfaceHolder();
                Canvas canvas = holder.lockCanvas();
                try {
                    if (canvas != null) {
                        canvas.drawColor(Color.BLACK);
                        // 推进一帧并绘制到画布（库内部已按真实帧间隔做时间补偿）
                        GlobalFirework.getInstance().updateAndDrawWallpaper(canvas);
                    }
                } finally {
                    if (canvas != null) holder.unlockCanvasAndPost(canvas);
                }
                drawHandler.postDelayed(this, 16); // 约 60fps，低端机可放宽到 33
            }
        };

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            drawing = visible;
            if (visible) {
                drawHandler.removeCallbacks(drawTask);
                drawHandler.post(drawTask);
            }
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            super.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP) {
                // 壁纸专属发射（带反馈，防抖同样生效）
                GlobalFirework.getInstance().bloomWallpaper(event.getX(), event.getY(), null);
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            drawing = false;
            if (drawHandler != null) drawHandler.removeCallbacksAndMessages(null);
            if (drawThread != null) drawThread.quitSafely();
            // 释放壁纸专属资源（不影响 App 内公共资源）
            GlobalFirework.getInstance().releaseWallpaper();
        }
    }
}
```

同时在 `AndroidManifest.xml` 中注册壁纸服务：

```xml
<service
    android:name=".FireworkWallpaperService"
    android:exported="true"
    android:label="烟花动态壁纸"
    android:permission="android.permission.BIND_WALLPAPER">
    <intent-filter>
        <action android:name="android.service.wallpaper.WallpaperService" />
    </intent-filter>
    <meta-data
        android:name="android.service.wallpaper"
        android:resource="@xml/wallpaper_info" />
</service>
```

---

## ⏱️ 7. 倒计时特效 (Countdown)

`startCountdown` 会在当前 Activity 视图中心依次绽放 `3 → 2 → 1 → GO!` 的文字烟花，每秒一个数字，结束后回调（需保证初始化已完成且 Activity 处于前台）：

```java
GlobalFirework.getInstance().startCountdown(3, () -> {
    // 倒计时结束，开始你的正式环节（如告白文字、全屏动画等）
});
```

---

## 🧩 8. 直接使用 FireworkView (Standalone View)

除了全局模式，也可以把 `FireworkView` 当普通控件使用（同样支持 XML 布局）：

```java
// 代码方式
FireworkView fireworkView = new FireworkView(context);
fireworkView.setMaxParticles(3000); // 低配设备可调小
container.addView(fireworkView, new FrameLayout.LayoutParams(-1, -1));

fireworkView.launch(x, y, config); // 直接发射
fireworkView.stop();               // 停止并清空粒子
fireworkView.resume();             // 恢复
```

```xml
<!-- XML 方式 -->
<com.xiaobo.fireworks.FireworkView
    android:id="@+id/firework_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

> 加入布局后自动驱动动画；离屏/自绘场景（如壁纸、视频合成）可手动调用 `fireworkView.update()` + `fireworkView.drawToCanvas(canvas)` 每帧推进与绘制。

---

## ⚠️ 注意事项 (Notes)

1.  **权限**：库内部已包含 `<uses-permission android:name="android.permission.VIBRATE" />`，无需手动添加。
2.  **生命周期**：库会自动监听 Activity 的 `onPause`，页面不可见时自动停止渲染循环，无需担心耗电。
3.  **初始化时机**：务必在首个 Activity 的 `onResume` 之前完成 `init()`（推荐 `Application.onCreate`），否则首个前台周期内 `bloom` 将拿不到渲染视图而静默无效。
4.  **文字烟花性能**：文字内容越长、字体越大，生成的粒子数量越多，建议单字或短文本效果最佳，避免过长文字导致粒子数超出上限。相同文字/字号的连续爆炸自动复用采样缓存，开销很低。
5.  **混淆 (Proguard)**：本库不含反射，一般无需配置。如遇问题可添加：
    ```proguard
    -keep class com.xiaobo.fireworks.** { *; }
    ```
6.  **Z轴层级**：在 Android 5.0+ 设备上，烟花视图会自动设置 `elevation` 为 100f，确保浮在大部分 UI 之上。
7.  **粒子上限**：默认全局最大粒子数为 5000（可通过 `setMaxParticles` 调整，最小 200），超出时新的烟花会被丢弃，避免同时触发大量烟花造成卡顿。
8.  **刷新率适配**：物理步进按真实帧间隔自动补偿，以 60fps 为基准：90/120Hz 高刷屏上烟花速度/衰减与 60Hz 完全一致，低帧率下也不会变慢。
9.  **反馈防抖**：音效/震动默认带 120ms 防抖，单次点击不受影响，高频连续绽放自动合并，显著降低主线程系统调用；`setFeedbackIntervalMs(0)` 可恢复 v1.0.9 及以前的逐次反馈行为。

---

## 🌌 9. 夜空大气层 SkyView (v1.2.0)

`SkyView` 提供一整套夜空氛围效果，可直接替换/叠加在烟花层之下：星空（闪烁漂移）、随机流星、孔明灯、天气粒子（雪/樱花/银杏）、按当天真实月相绘制的月亮、当日星座连线。加入布局后自动驱动（帧率时间补偿，高刷屏不加速）。

```java
SkyView skyView = findViewById(R.id.sky_view);
// 可选调优（也可全部默认）
skyView.setStarCount(120);
skyView.setFrameIntervalMs(33);            // 低端机 30fps 节流，速率不变
skyView.setAutoMeteorIntervalMs(5000, 5000);  // 随机流星：5-10 秒一颗
skyView.setAutoLanternIntervalMs(45000, 30000); // 环境孔明灯自动升空
skyView.setWeather(SkyView.WEATHER_SNOW); // NONE / SNOW / SAKURA / GINKGO
skyView.setMoonEnabled(true);             // 真实月相月亮
skyView.setConstellationEnabled(true);    // 当日星座连线
skyView.setMoodStarColors(new int[]{0xFFFF6B81, 0xFF6BC5FF}); // 心情染色星

// 点击放灯：在你的触摸事件里调
skyView.releaseLantern(event.getX(), event.getY());
// 手动触发一颗流星
skyView.spawnMeteor();
// 当前星座名（如“狮子”）
String zodiac = skyView.getCurrentZodiacName();

// 生命周期
skyView.startSky();   // 显示并继续
skyView.stopSky();    // 暂停并隐藏

// 离屏/壁纸模式：setSize(w,h) + updateSky(scale) + drawSky(canvas)（scale=真实帧间隔/16.67ms）
```

## 🎵 10. 音乐律动 RhythmFirework (v1.2.0)

监听麦克风振幅，自适应阈值检测音乐节拍，节拍时自动绽放烟花（外放背景音乐即可触发；需 RECORD_AUDIO 运行时权限，库已在 Manifest 声明）。

```java
// 权限需已授予（ActivityCompat.checkSelfPermission + requestPermissions）
RhythmFirework rhythm = RhythmFirework.getInstance();
rhythm.setSensitivity(1.2f);   // 0.5 钝 ~ 2 灵敏
rhythm.setAutoBloom(true);    // 节拍自动绽放（也可 setBloomConfig 自定义样式）
rhythm.start(context);        // 返回 false = 权限/设备不可用
...
rhythm.stop();
// 或仅监听节拍自定义联动：rhythm.setOnBeatListener(amp -> ...)
```

---

## 📋 版本更新记录 (Changelog)

### v1.3.0（2026-10-25）

**新 API**
- `FireworkView.setAfterglow(boolean)`：烟花余辉——粒子熄灭时化为约 1 秒渐隐残影，天空蒙上薄纱
- `GlobalFirework.startAutoShow(pattern, intervalMs)` / `stopAutoShow()`：编舞化随机播放，四种剧本——经典随机 / 波浪扫射 / 环形聚拢 / 跟随 RhythmFirework 音乐节拍
- `GlobalFirework.playHeartbeatVibration()`："咚-咚"心跳节奏震动（告白场景）
- `SkyView.setTimeTintAuto(boolean)`：按时段自动叠色（黄昏暖金 / 深夜冷蓝 / 黎明淡青），Default 开启，App 与壁纸同时生效
- 算法静态化 + 单元测试：星座日期分界与朔望月月龄（`SkyAlgorithmTest`）

### v1.2.4（2026-10-25）

- 孔明灯体放大 40%：灯口暗边、竹纹骨架、火芯等精灵细节在运行尺寸下清晰可辨

### v1.2.3（2026-10-25）

**视觉重制（孔明灯 / 天气粒子 / 音乐律动）**
- 孔明灯：真实纸灯造型（渐变灯体 + 火芯 + 灯口暗边 + 竹纹骨架 + 烘焙光晕），预渲染精灵一次绘制，放灯位置即点击处，灯体呼吸摇曳
- 雪花：柔光雪绒（径向渐变）+ 六臂晶体（含分叉与蓝调反光）两种精灵，三层景深视差（远小暗慢 / 近大亮快），大颗晶体缓慢旋转
- 樱花/银杏同步升级为带渐变与叶脉的精灵
- 音乐律动重制：MediaRecorder 轮询 → AudioRecord 实时 RMS（后台线程）；节拍编舞——色相逐拍流转、位置沿平滑轨迹游走、强弱拍交替、每 8 拍高潮双爆、音量映射绽放大小

### v1.2.2（2026-10-25）

**月亮重制（真实感 + 生命周期）**
- 真实明暗界线：外半圆 + 椭圆弧界线算法，自动覆盖新月→娥眉月→上弦→盈凸→满月→亏凸→下弦→残月全部 8 个阶段（弦月时界线退化为真正的直线）
- 月海纹理：亮面内绘制环形山阴影，只出现在受光侧
- 地照：暗面隐约可见（"新月抱旧月"），新月时月亮不再完全消失
- **月亮生命周期**：`setMoonMotionPeriodMs(int)` 月升→拱形轨迹→月落（默认 8 分钟一趟，升起/落下渐隐；0 = 固定右上角）

### v1.2.1（2026-10-25）

**性能优化（视觉与功能完全不变）**
- SkyView：月相/星座预渲染为小位图缓存（尺寸/开关变化时重建），每帧 30+ 次绘制调用降为 2 次 drawBitmap，App 与壁纸同时受益
- SkyView：天气粒子消除每帧 RectF 分配（约 4200 个/秒），星点无染色时 setColor 提出循环，流星 StrokeCap 提出循环
- 缓存位图随 detach 正确回收

### v1.2.0（2026-10-25）

**新增 API**
- `FireworkConfig.Shape.CUSTOM` + `Builder.shapeBitmap(Bitmap)`：任意图形烟花，按位图不透明像素采样生成粒子（同一位图缓存采样结果，重复绽放零开销）
- `SkyView`：夜空大气层（星空+流星+孔明灯+天气粒子雪/樱花/银杏+真实月相月亮+当日星座连线），View/离屏双模式，全套配置 API
- `RhythmFirework`：麦克风振幅节拍检测 + 自动绽放，自适应阈值，支持灵敏度/样式/回调定制
- `GlobalFirework.getFireworkView()`：获取当前附着的渲染器

### v1.1.0（2026-10-25）

**性能优化**
- 物理步进按真实帧间隔时间补偿（60fps 基准）：修复高刷屏动画成倍加速、低帧率下变慢的问题
- 反馈（音效/震动）防抖 + `hasVibrator` 探测缓存：消除高频绽放时的逐次 Binder 调用
- 文字烟花：栅格与采样像素缓存 + ARGB_8888 整块读取，替代逐点 `getPixel` 的数千次原生调用
- 屏外粒子自动裁剪（视觉不变）；绘制路径零分配；对象池按需扩容

**新增 API**
- `GlobalFirework.bloomSilently(x, y[, config])`：无反馈发射
- `GlobalFirework.setFeedbackIntervalMs(int)` / `setSoundEnabled(boolean)` / `setVibrationEnabled(boolean)` / `setSoundVolume(float)`
- `GlobalFirework.setMaxParticles(int)` 与 `FireworkView.setMaxParticles(int)`：可调粒子上限
- `FireworkConfig.Builder.textSize(float)`：文字采样字号

### v1.0.9 及更早

动态壁纸离屏 API、粒子对象池、四形态烟花、火箭升空、震动适配等，详见 [提交历史](https://github.com/xiaobo2001/fireworksandroid/commits/master)。

---

## 📞 技术支持

如有 Bug 或建议，请在 [GitHub Issues](https://github.com/xiaobo2001/fireworksandroid/issues) 中反馈。
