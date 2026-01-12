
# 🎆 Fireworks Android Library 使用文档

这是一个高性能、轻量级、视觉效果精美的 Android 全局烟花特效库。支持流光拖尾、霓虹光晕、物理下落模拟，并内置对象池管理内存，适配 Android 5.0 - Android 16+ 的震动反馈。

## ✨ 特性 (Features)
*   **零侵入**：只需在 Application 初始化，自动附着到所有 Activity。
*   **高性能**：内置对象池 (Object Pool)，无内存抖动，拒绝卡顿。
*   **极致视觉**：流光拖尾、动态色温变化、闪烁微粒、光晕渲染。
*   **多感官反馈**：支持自定义音效 + 适配全机型的细腻震动 (Haptic Feedback)。
*   **简单易用**：一行代码触发。

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
        maven { url 'https://jitpack.io' } // 添加这一行
    }
}
```

### 第二步：添加依赖
在 app 模块的 `build.gradle` 中添加：

```groovy
dependencies {
    // 请替换 v1.0.0.0 为最新版本号
    implementation 'com.github.xiaobo2001:fireworksandroid:v1.0.0.0'
}
```

---

## 🚀 2. 初始化 (Initialization)

在你的 `Application` 类中进行初始化。这是**必须**的步骤。

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

## 🎮 3. 使用方法 (Usage)

本库提供了灵活的触发方式，你可以在任何地方调用。

### 场景一：点击特定按钮触发
适用于点赞、支付成功、领奖等场景。

```java
Button btnCelebrate = findViewById(R.id.btn_celebrate);
btnCelebrate.setOnClickListener(v -> {
    // 获取按钮中心坐标
    float x = v.getX() + v.getWidth() / 2f;
    float y = v.getY() + v.getHeight() / 2f;
    
    // 发射烟花！
    GlobalFirework.getInstance().bloom(x, y);
});
```

### 场景二：点击屏幕任意位置触发 (全局)
如果你希望用户点哪里炸哪里，可以在 `Activity` 中重写触摸分发方法：

```java
public class MainActivity extends AppCompatActivity {
    
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            // 在手指按下位置绽放
            GlobalFirework.getInstance().bloom(ev.getX(), ev.getY());
        }
        // 继续分发事件，不影响原有点击逻辑
        return super.dispatchTouchEvent(ev);
    }
}
```

### 场景三：程序自动触发 (如倒计时结束)
不需要用户点击，程序自动在屏幕中心放烟花。

```java
// 获取屏幕中心点 (示例)
View decorView = getWindow().getDecorView();
float centerX = decorView.getWidth() / 2f;
float centerY = decorView.getHeight() / 2f;

// 模拟延时触发
new Handler().postDelayed(() -> {
    GlobalFirework.getInstance().bloom(centerX, centerY);
}, 1000);
```

---

## ⚙️ 4. 进阶配置 (Configuration)

### 自定义音效
将你的 `.mp3` 或 `.wav` 文件放入 `res/raw` 目录，初始化时传入：

```java
// 使用自定义音效
GlobalFirework.getInstance().init(this, R.raw.my_custom_sound);

// 如果不想播放声音
GlobalFirework.getInstance().init(this, -1);
```

### 震动反馈
库内部已自动适配：
*   **Android 13+**: 使用 `USAGE_MEDIA` 属性，绕过系统触摸设置，提供细腻震动。
*   **Android 8-12**: 使用 `OneShot` 短震动。
*   **Android 7及以下**: 使用普通震动。

*无需额外代码，初始化后自动生效。*

---

## ⚠️ 注意事项

1.  **权限**：库内部已经声明了 `<uses-permission android:name="android.permission.VIBRATE" />`，你无需再次在清单文件中添加。
2.  **生命周期**：库会自动监听 Activity 的 `onPause`，在页面跳转或切后台时自动停止渲染，节省电量并防止卡顿。
3.  **混淆 (Proguard)**：本库代码极少且不含反射，通常无需额外混淆配置。如遇问题，可添加规则：
    ```proguard
    -keep class com.xiaobo.fireworks.** { *; }
    ```

---

## 📞 技术支持
如有问题，请在 GitHub Issues 中反馈。
