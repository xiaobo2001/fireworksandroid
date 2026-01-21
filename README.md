
# 🎆 Fireworks Android Library 使用文档


这是一个高度可定制、高性能的 Android 全局烟花特效库。

它不仅支持物理下落、流光拖尾、光晕渲染等视觉效果，还引入了 **Builder 模式配置**，允许你精确控制烟花的**颜色、粒子数量、爆炸范围、拖尾长度**等参数。内置对象池管理内存，并适配了 Android 全全版本的震动反馈。

## ✨ 特性 (Features)

*   **🎨 高度自定义**：通过 `FireworkConfig` 配置颜色、大小、速度、拖尾、闪烁等。
*   **⚡ 零侵入 & 自动化**：Application 初始化后自动附着 Activity，自动管理生命周期。
*   **🚀 极致性能**：内置 `ArrayDeque` 对象池 (Object Pool)，无内存抖动 (Zero Allocation on Draw)。
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

最简单的调用方式，使用库内置的默认配置（随机颜色、标准拖尾）。

```java
// 在屏幕坐标 (x, y) 处绽放
GlobalFirework.getInstance().bloom(500, 800);

// 或者在触摸事件中
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

### 4.3 参数详解

| 方法 | 描述 | 默认值 | 推荐范围 |
| :--- | :--- | :--- | :--- |
| `colors(int...)` | 指定粒子颜色数组，不传则随机全色谱 | null (随机) | ARGB 颜色值 |
| `count(int)` | 爆炸产生的粒子数量 | 60 | 30 - 100 |
| `range(float)` | 爆炸范围系数（影响初速度） | 1.0f | 0.5f - 2.0f |
| `trail(float)` | 粒子拖尾长度系数，0为无拖尾（圆点） | 1.0f | 0.0f - 3.0f |
| `duration(float)` | 持续时间系数（影响衰减），值越大越持久 | 1.0f | 0.8f - 1.5f |
| `sparkle(boolean)` | 是否生成额外的白色闪烁微粒 | true | true / false |

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

### 🎲 场景三：随机混合
在点击时随机切换不同的配置，增加趣味性。

```java
btn.setOnClickListener(v -> {
    if (Math.random() > 0.5) {
        GlobalFirework.getInstance().bloom(x, y, richConfig);
    } else {
        GlobalFirework.getInstance().bloom(x, y, cyberConfig);
    }
});
```

---

## ⚠️ 注意事项 (Notes)

1.  **权限**：库内部已包含 `<uses-permission android:name="android.permission.VIBRATE" />`，无需手动添加。
2.  **生命周期**：库会自动监听 Activity 的 `onPause`，页面不可见时自动停止渲染循环，无需担心耗电。
3.  **混淆 (Proguard)**：本库不含反射，一般无需配置。如遇问题可添加：
    ```proguard
    -keep class com.xiaobo.fireworks.** { *; }
    ```
4.  **Z轴层级**：在 Android 5.0+ 设备上，烟花视图会自动设置 `elevation` 为 100f，确保浮在大部分 UI 之上。

---

## 📞 技术支持
如有 Bug 或建议，请在 GitHub Issues 中反馈。
