<div align="center">

# Compose SVGA 🚀

**专为 Jetpack Compose 与现代化 Android 架构量身打造的高性能、轻量级 SVGA 动画播放引擎**

[![JitPack](https://jitpack.io/v/TroyLi-Dev/compose-svga.svg)](https://jitpack.io/#TroyLi-Dev/compose-svga)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-21+-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-Jetpack-purple.svg)](https://developer.android.com/jetpack/compose)
[![Wire](https://img.shields.io/badge/Wire-5.4.0-orange.svg)](https://github.com/square/wire)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

[English](./README_EN.md) | **简体中文**

</div>

---

## 📖 简介 (Introduction)

**Compose SVGA** 是针对 Android 平台深度重构的新一代 SVGA 动画播放器。彻底告别传统 `SVGAPlayer-Android` 依赖沉重、CPU 唤醒频繁、内存占用高以及对 Jetpack Compose 支持缺失的痛点。

本项目基于 **Jetpack Compose 现代化标准** 重建，由统一的 **Choreographer 中心化时钟引擎** 驱动，采用 **Square Wire 5.x** 极轻量运行时，支持 **优先积分级调度（自适应降频）**、**Zero-GC 原生画布直绘**、**32 分段锁两级缓存体系** 及 **SingleFlight 网络请求去重**。同时提供 100% 兼容的 `SvgaImageView`，实现传统 Android View 与 Compose 的无缝双向兼容。

---

## ✨ 核心特性 (Key Features)

- 🎨 **原生 Jetpack Compose 优先**：无需套壳 `AndroidView`，提供声明式 `SvgaAnimation` / `SvgaPlayer`，深度融入 Compose 重组与生命周期。
- 🎮 **状态提升控制器 (`SvgaController`)**：支持声明式与命令式播放、暂停、跳转、重播、单向数据流与播放进度响应式绑定。
- ⏱️ **中心化 Choreographer 时钟引擎**：全局唯一定时驱动，无动画播放时自动注销回调，**闲置 CPU 占用彻底归零 (0% CPU)**。
- 🎯 **自适应优先级调度 (`SvgaPriority`)**：内置 `High`、`Normal`、`Low` 策略，高负载（如 FPS < 48）时自适应抽半频，300+ 动画同屏依然流畅防卡顿。
- 🪶 **超轻量 Wire Protobuf 运行时**：替换原版臃肿庞大的 `protobuf-java`，包体积瘦身 70%+，消减数千个无用方法。
- ⚡ **Zero-GC 原生直绘与对象池**：直接在 `nativeCanvas` 上绘制，内置 Matrix / Path / Paint 缓存池，拒绝频繁内存抖动与 GC 停顿。
- 🚀 **双级缓存与 SingleFlight 去重**：L1 内存 LRU 缓存秒开首帧，L2 磁盘缓存采用 32 分段锁并发原子写入；相同 URL 在途网络请求智能合并，绝不重复下载。
- 🛡️ **智能动态下采样与内存安全防护**：按组件渲染尺寸自动计算 `inSampleSize` 解码，杜绝小图占大内存；内建 Zip Bomb（CWE-409）解压边界防御。
- 🔤 **现代 Kotlin DSL 动态元素**：支持动态替换文字、动态图层隐藏、动态图片替换（本地 Bitmap 或远程 URL 自动下载）、热区点击监听与逐帧 Canvas 自定义绘制。
- 🔊 **健全的音频生命周期管理**：内置 `SoundPool` 音效管理，支持音量调节、一键静音（`isMute`），跟随页面后台自动暂停/释放。
- 📱 **完美兼容传统 View 体系**：保留优化后的 `SvgaImageView` 与 `SvgaDrawable`，支持 XML 属性配置，彻底治理 `onDetachedFromWindow` 内存泄漏。

---

## 📊 架构特性对比 (Comparison)

| 核心维度 | **compose-svga (本项目)** 🚀 | **SVGAPlayer-Android (官方旧版)** 👴 |
| :--- | :--- | :--- |
| **Compose 原生支持** | ✅ 原生可组合项，支持 Slot 插槽与状态流 | ❌ 只能通过 `AndroidView` 机械包装，事件同步困难 |
| **状态驱动方式** | ✅ `SvgaController` 响应式单向数据流 | ❌ 仅传统命令式监听与回调 |
| **Proto 序列化引擎** | 🚀 **Square Wire 5.x**（极轻量、体积微小） | ⚠️ Google `protobuf-java`（体积庞大、方法数暴增） |
| **时钟调度引擎** | ⚡ **中心化 Choreographer 引擎**，闲置时 **0% CPU** | ❌ 每个动画起独立 ValueAnimator，线程与 CPU 开销大 |
| **高负载智能调度** | 🎯 **优先级动态降频策略**（High / Normal / Low） | ❌ 无降级机制，动画堆叠时主线程易卡死掉帧 |
| **网络请求合并** | 🛡 **SingleFlight 机制**，并发同 URL 自动合并请求 | ❌ 重复下载相同资源，浪费流量与 IO 带宽 |
| **磁盘缓存安全** | 🔒 **32 分段哈希锁**，杜绝并发竞争，异常自愈降级 | ⚠️ 简易单锁/无锁，并发场景偶现文件损坏或锁死 |
| **防 OOM 下采样** | 🛡 **按目标尺寸动态计算 `inSampleSize` 下采样** | ❌ 始终按原始素材分辨率解码，极易触发 OOM |
| **安全防护** | 🛡 **内建 Zip Bomb (CWE-409) 恶意流防御** | ❌ 无解压边界校验 |
| **内存泄漏防范** | 🧼 自动感知 Lifecycle / Detached 生命周期即时释放 | ⚠️ 历史代码中存在多处静态持有或未注销泄漏风险 |
| **动态替换 API** | ✨ 原生 API + **现代化 Kotlin DSL 建造者** | ⚠️ 仅基础命令式 API |
| **传统 View 兼容** | ✅ 完美保留并优化 `SvgaImageView` | ✅ 支持 |

---

## 📦 依赖引入 (Installation)

### 1. 添加 JitPack 仓库

在工程根目录的 `settings.gradle.kts`（或 `settings.gradle`）中配置：

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. 添加依赖

在模块的 `build.gradle.kts` 中添加：

```kotlin
dependencies {
    implementation("com.github.TroyLi-Dev:compose-svga:1.0.1-rc02")
}
```

> **提示**：如果使用 Groovy DSL (`build.gradle`)：
> ```groovy
> implementation 'com.github.TroyLi-Dev:compose-svga:1.0.1-rc02'
> ```

---

## 🚀 快速上手 (Quick Start)

### 1. Jetpack Compose 基础使用

只需一行代码，即可在 Compose 中优雅播放 SVGA 动效：

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rui.compose.svga.SvgaAnimation

@Composable
fun SimpleSvgaDemo() {
    // 1. 播放网络动画 URL
    SvgaAnimation(
        model = "https://example.com/gift.svga",
        modifier = Modifier.size(200.dp)
    )

    // 2. 播放 Assets 资源 (支持 "anim.svga" 或 "assets://anim.svga")
    SvgaAnimation(
        model = "player/heart_burst.svga",
        modifier = Modifier.size(200.dp)
    )

    // 3. 播放本地文件 File 或绝对路径
    SvgaAnimation(
        model = File(context.filesDir, "custom.svga"), // 或 "/sdcard/anim.svga"
        modifier = Modifier.size(200.dp)
    )

    // 4. 播放 Raw 资源 ID
    SvgaAnimation(
        model = R.raw.celebration,
        modifier = Modifier.size(200.dp)
    )
}
```

> 💡 **提示**：你也可以使用 `SvgaPlayer(...)`，它是 `SvgaAnimation(...)` 的别名组件，满足不同开发者的命名偏好。

---

### 2. 控制器驱动播放 (`SvgaController`)

通过 `rememberSvgaController` 获得响应式控制器，实现对动效生命周期与帧跳转的精确控制：

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rui.compose.svga.SvgaAnimation
import com.rui.compose.svga.controller.rememberSvgaController

@Composable
fun ControlledSvgaDemo() {
    val controller = rememberSvgaController(
        initialModel = "https://example.com/gift.svga",
        initialLoops = 1 // 播放 1 次后停留在最后一帧；0 为无限循环
    )

    Column {
        SvgaAnimation(
            controller = controller,
            modifier = Modifier.size(240.dp)
        )

        // 响应式读取当前帧序与进度百分比
        Text("当前帧: ${controller.currentFrame} / ${controller.totalFrames}")
        Text("播放进度: ${(controller.progress * 100).toInt()}%")
        Text("状态: ${controller.playState}")

        Row {
            Button(onClick = { controller.play() }) { Text("播放") }
            Button(onClick = { controller.pause() }) { Text("暂停") }
            Button(onClick = { controller.resume() }) { Text("恢复") }
            Button(onClick = { controller.replay() }) { Text("重播") }
            Button(onClick = { controller.seekToProgress(0.5f) }) { Text("跳至 50%") }
        }
    }
}
```

---

### 3. 插槽（Slot）与生命周期回调

```kotlin
SvgaAnimation(
    model = "https://example.com/gift.svga",
    modifier = Modifier.size(200.dp),
    // 加载中 Compose 插槽
    loading = {
        CircularProgressIndicator(modifier = Modifier.size(36.dp))
    },
    // 加载/解码失败 Compose 插槽
    failure = {
        Text("动画加载失败", color = Color.Red)
    },
    onLoading = { Log.d("SVGA", "资源加载中...") },
    onPlay = { Log.d("SVGA", "首帧已上屏渲染") },
    onSuccess = { entity -> Log.d("SVGA", "解码成功, 总帧数: ${entity.frames}") },
    onError = { error -> Log.e("SVGA", "加载异常", error) },
    onStep = { frame, percentage ->
        // 逐帧进度回调 (0.0 ~ 1.0)
    },
    onFinished = { Log.d("SVGA", "动画播放完毕") }
)
```

---

## 🎨 动态元素替换 (Dynamic Entity & DSL)

SVGA 支持在运行时动态替换动画中的文本、位图头像、隐藏指定图层或监听元素点击。

### 方式 A：现代化 Kotlin DSL 建造者

```kotlin
import android.graphics.Color
import android.text.TextPaint
import com.rui.compose.svga.drawer.buildSvgaDynamic

val dynamicEntity = remember {
    buildSvgaDynamic {
        // 1. 动态替换文本（支持自定义 Paint）
        text(
            key = "banner_text",
            content = "祝 Troy Li 生日快乐 🎉",
            textPaint = TextPaint().apply {
                color = Color.YELLOW
                textSize = 38f
                isFakeBoldText = true
            }
        )

        // 2. 动态替换头像图片（支持直接传 Bitmap 或 远程图片 URL）
        image(key = "user_avatar", bitmap = avatarBitmap)
        image(key = "badge_icon", url = "https://example.com/badge.png")

        // 3. 动态隐藏指定图层
        hidden(key = "legacy_watermark", isHidden = true)

        // 4. 监听特定元素区域点击
        click(key = "action_button")
    }
}

SvgaAnimation(
    model = "https://example.com/gift.svga",
    dynamicEntity = dynamicEntity,
    modifier = Modifier.size(260.dp)
)
```

### 方式 B：内联便捷重载

```kotlin
SvgaPlayer(
    model = "https://example.com/gift.svga",
    modifier = Modifier.size(200.dp)
) {
    // this 为 SvgaDynamicEntity
    setDynamicText("Hello Compose", TextPaint().apply { color = Color.RED; textSize = 32f }, "text_key")
    setDynamicImage(avatarBitmap, "avatar_key")
}
```

---

## ⚡ 高级进阶特性 (Advanced Topics)

### 1. 优先级自适应调度 (`SvgaPriority`)

面对直播间多礼物并发或极值列表（如同屏数百个动画），通过设定 `priority` 实现性能与功耗的最佳平衡：

```kotlin
// 1. 高优先级：全屏豪华礼物/主视觉动画，全速 60FPS 渲染，绝不降频
SvgaAnimation(
    model = bigGiftUrl,
    priority = SvgaPriority.High,
    modifier = Modifier.fillMaxSize()
)

// 2. 普通优先级（默认）：系统高负载 (FPS < 48) 时自动降为半频，保障主交互流畅
SvgaAnimation(
    model = normalGiftUrl,
    priority = SvgaPriority.Normal,
    modifier = Modifier.size(100.dp)
)

// 3. 低优先级：背景/挂件动效，上限限制 15 FPS，大幅节省 CPU 和电量
LazyVerticalGrid(columns = GridCells.Fixed(5)) {
    items(badgeList) { item ->
        SvgaAnimation(
            model = item.svgaUrl,
            priority = SvgaPriority.Low,
            modifier = Modifier.size(60.dp)
        )
    }
}
```

### 2. 音频控制与全局音效管理器 (`SvgaSoundManager`)

SVGA 素材内嵌的音效由内建的 `SvgaSoundManager` 统一调度：

```kotlin
// 局部组件静音
SvgaAnimation(
    model = soundGiftUrl,
    isMute = true, // 开启静音
    modifier = Modifier.size(200.dp)
)

// 通过控制器动态切换静音
controller.isMuted = true

// 全局音量设置 (0.0f ~ 1.0f)
SvgaSoundManager.setVolume(0.8f)

// 离开页面时彻底释放音效池（可选）
SvgaSoundManager.release()
```

### 3. 扩展自定义资源获取器 (`SvgaFetcherRegistry`)

如果项目中有私有加密协议或自定义网络协议，可直接插拔注册自定义 `SvgaResourceFetcher`：

```kotlin
class EncryptedSvgaFetcher : SvgaResourceFetcher<MySecretModel> {
    override fun handles(data: Any): Boolean = data is MySecretModel
    override fun fetch(context: Context, data: MySecretModel): SvgaFetchResult {
        val decryptedStream = decryptStream(data.encryptedBytes)
        return SvgaFetchResult(inputStream = decryptedStream, cacheKey = data.id)
    }
}

// 在 Application 启动时注册
SvgaFetcherRegistry.register(EncryptedSvgaFetcher(), priorityFirst = true)
```

---

## 📱 传统 Android View 使用指南 (`SvgaImageView`)

为平滑迁移老旧业务，库中包含全面升级后的 `SvgaImageView`，与 Compose 共享底层解码、两级缓存与直绘流水线：

### XML 布局配置

```xml
<com.rui.compose.svga.view.SvgaImageView
    android:id="@+id/svgaImageView"
    android:layout_width="200dp"
    android:layout_height="200dp"
    app:source="player/voice_room_game_button.svga"
    app:autoPlay="true"
    app:loopCount="0"
    app:antiAlias="true"
    app:clearsAfterStop="false"
    app:clearsAfterDetached="true"
    app:fillMode="Forward" />
```

### Kotlin 代码使用

```kotlin
val svgaImageView = findViewById<SvgaImageView>(R.id.svgaImageView)

// 智能加载并播放（支持 URL / Assets / File / RawRes）
svgaImageView.load("https://example.com/gift.svga") { videoEntity ->
    Log.d("SVGA", "加载完成")
}

// 播放控制
svgaImageView.startAnimation()
svgaImageView.pauseAnimation()
svgaImageView.stopAnimation()

// 生命周期监听
svgaImageView.callback = object : SvgaCallback {
    override fun onPause() {}
    override fun onFinished() {}
    override fun onRepeat() {}
    override fun onStep(frame: Int, percentage: Double) {}
}

// 元素点击监听
svgaImageView.setOnItemClickListener(object : SvgaItemClickListener {
    override fun onItemClick(clickKey: String) {
        Toast.makeText(context, "点击了图层: $clickKey", Toast.LENGTH_SHORT).show()
    }
})
```

---

## ⚙️ 全局初始化与配置 (Configuration)

绝大部分情况下**无需任何额外初始化代码**，所有缓存与计算器均会在首帧按需自愈式惰性初始化。

如果你希望在应用启动时做预热或开启日志：

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // 开启调试日志
        SvgaLogger.setLogEnabled(BuildConfig.DEBUG)

        // 可选：提前预热磁盘缓存与系统内存探测器
        SvgaDiskCache.init(this)
        SvgaMemoryCalculator.instance.init(this)

        // 可选：预设全局音效池最大流数
        SvgaSoundManager.init(maxStreams = 16)
    }
}
```

---

## 🛡️ ProGuard / 混淆规则

本库自带 `consumerProguardFiles`，通常无需手动配置。若项目需要显式指定：

```proguard
# Wire Runtime
-keep class com.squareup.wire.** { *; }

# Compose SVGA 核心组件与 Proto 模型
-keep class com.rui.compose.svga.** { *; }
-keepclassmembers class com.rui.compose.svga.** { *; }
```

---

## 💡 示例应用 (Demo)

本项目附带完整的示例与压力测试应用：

- 🚀 [MainActivity.kt](app/src/main/java/com/rui/compose/svga/MainActivity.kt) - 包含 300+ 极端网格动效压力测试、FPS/CPU/MEM 实时仪表盘、动态干扰模拟与飞行位图。
- 📱 [ComposeStressTestActivity.kt](app/src/main/java/com/rui/compose/svga/ComposeStressTestActivity.kt) - Compose 极端多图层压测。
- 🏛️ [ViewStressTestActivity.kt](app/src/main/java/com/rui/compose/svga/ViewStressTestActivity.kt) - 传统 Android View 对照压测。

---

## 📄 开源许可证 (License)

```
Copyright 2026 TroyLi-Dev (compose-svga)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
