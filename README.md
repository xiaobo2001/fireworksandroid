# 🎆 Fireworks Android Library 使用文档

这是一个高度可定制、高性能的 Android 全局烟花特效库。

它不仅支持物理下落、流光拖尾、光晕渲染等视觉效果，还引入了 **Builder 模式配置**，允许你精确控制烟花的**颜色、粒子数量、爆炸范围、拖尾长度**等参数。内置对象池管理内存，并适配了 Android 全版本的震动反馈。同时支持爱心、五角星、自定义文字等多种爆炸形态，以及火箭升空动画效果。

## ✨ 特性 (Features)

*   **🎨 高度自定义**：通过 `FireworkConfig` 配置颜色、大小、速度、拖尾、闪烁等。
*   **🎯 多元爆炸形态**：内置圆形、爱心、五角星、自定义文字 4 种烟花形状，适配节日、告白、祝福等不同场景。
*   **✍️ 文字烟花特效**：通过像素采样将任意文字/字符转化为粒子烟花，支持自定义内容与采样密度。
*   **🚀 真实发射动画**：可选火箭上升效果，还原烟花从底部升空后爆炸的完整视觉流程。
*   **⚡ 零侵入 & 自动化**：Application 初始化后自动附着 Activity，自动管理生命周期。
*   **⚙️ 极致性能**：内置 `ArrayDeque` 对象池 (Object Pool)，无内存抖动 (Zero Allocation on Draw)。
*   **📳 多感官反馈**：支持自定义音效 + 适配 Android 5.0 - 16+ 的细腻震动 (Haptic Feedback)。
*   **🛠️ 简单易用**：支持一行代码默认触发，也支持高阶配置触发。

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
    implementation 'com.github.xiaobo2001:fireworksandroid:v1.0.1' // 请使用最新版本
}
```

---

## 🚀 2. 初始化 (Initialization)

在你的 `Application` 类中进行初始化。

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
        .sparkle(true)     // 是否开启闪烁微粒
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

```java
// 爱心形状烟花
FireworkConfig heartConfig = new FireworkConfig.Builder()
        .shape(FireworkConfig.Shape.HEART)
        .colors(Color.RED, 0xFFFF69B4)
        .build();
```

#### 4.3.2 文字烟花
当形状设置为 `Shape.TEXT` 时，可通过 `text()` 自定义烟花文字内容，库会自动对文字进行像素采样并生成对应粒子。

```java
// 文字形状烟花
FireworkConfig textConfig = new FireworkConfig.Builder()
        .shape(FireworkConfig.Shape.TEXT)
        .text("新年快乐")
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

### 4.4 参数详解

| 方法 | 描述 | 默认值 | 推荐范围 |
| :--- | :--- | :--- | :--- |
| `colors(int...)` | 指定粒子颜色数组，不传则随机全色谱 | null (随机) | ARGB 颜色值 |
| `count(int)` | 爆炸产生的粒子数量 | 60 | 30 - 100 |
| `range(float)` | 爆炸范围系数（影响初速度） | 1.0f | 0.5f - 2.0f |
| `trail(float)` | 粒子拖尾长度系数，0为无拖尾（圆点） | 1.0f | 0.0f - 3.0f |
| `duration(float)` | 持续时间系数（影响衰减），值越大越持久 | 1.0f | 0.8f - 1.5f |
| `sparkle(boolean)` | 是否生成额外的白色闪烁微粒 | true | true / false |
| `shape(Shape)` | 设置烟花爆炸形状 | `Shape.CIRCLE` | CIRCLE / HEART / STAR / TEXT |
| `text(String)` | 文字烟花的显示内容（仅 TEXT 形状生效） | `"A"` | 任意字符串 |
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
特点：青紫配色、无拖尾（点阵感）、范围小而精、无闪烁。

```java
FireworkConfig cyberConfig = new FireworkConfig.Builder()
    .colors(Color.CYAN, Color.MAGENTA) // 青色 + 洋红
    .range(0.8f)
    .trail(0f)         // 无拖尾，纯圆点
    .sparkle(false)    // 关闭闪烁，画面更纯净
    .build();

GlobalFirework.getInstance().bloom(x, y, cyberConfig);
```

### 💖 场景三：情人节爱心烟花
特点：爱心形状、粉红红配色、柔和扩散效果。

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

---

## ⚠️ 注意事项 (Notes)

1.  **权限**：库内部已包含 `<uses-permission android:name="android.permission.VIBRATE" />`，无需手动添加。
2.  **生命周期**：库会自动监听 Activity 的 `onPause`，页面不可见时自动停止渲染循环，无需担心耗电。
3.  **文字烟花性能**：文字内容越长、字体越大，生成的粒子数量越多，建议单字或短文本效果最佳，避免过长文字导致粒子数超出上限。
4.  **混淆 (Proguard)**：本库不含反射，一般无需配置。如遇问题可添加：
    ```proguard
    -keep class com.xiaobo.fireworks.** { *; }
    ```
5.  **Z轴层级**：在 Android 5.0+ 设备上，烟花视图会自动设置 `elevation` 为 100f，确保浮在大部分 UI 之上。
6.  **粒子上限**：全局最大粒子数为 1500，超出时新的烟花会被丢弃，避免同时触发大量烟花造成卡顿。

---

## 📞 技术支持
如有 Bug 或建议，请在 GitHub Issues 中反馈。
