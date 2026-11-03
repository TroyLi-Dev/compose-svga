<div align="center">

# Compose SVGA 🚀

**A modern, high-performance, and lightweight SVGA animation engine engineered for Jetpack Compose & Android View**

[![JitPack](https://jitpack.io/v/TroyLi-Dev/compose-svga.svg)](https://jitpack.io/#TroyLi-Dev/compose-svga)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-21+-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-Jetpack-purple.svg)](https://developer.android.com/jetpack/compose)
[![Wire](https://img.shields.io/badge/Wire-5.4.0-orange.svg)](https://github.com/square/wire)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**English** | [简体中文](./README.md)

</div>

---

## 📖 Introduction

**Compose SVGA** is a next-generation SVGA animation player designed from the ground up for modern Android development. It completely eliminates the pitfalls of legacy `SVGAPlayer-Android`, such as bulky dependencies, frequent CPU wakeups, severe memory churn, and lack of native Jetpack Compose integration.

Built with **Jetpack Compose native standards**, driven by a unified **Choreographer-based centralized clock engine**, powered by the ultra-lightweight **Square Wire 5.x** protobuf runtime, and featuring **adaptive priority scheduling**, **zero-GC canvas direct rendering**, **32-segment-locked two-tier caching**, and **SingleFlight in-flight request deduplication**. Furthermore, it provides full backward compatibility with traditional Android Views via `SvgaImageView`.

---

## ✨ Key Features

- 🎨 **Jetpack Compose Native**: No clumsy `AndroidView` wrappers. Direct declarative composables (`SvgaAnimation` & `SvgaPlayer`) deeply integrated with Compose recomposition and lifecycle.
- 🎮 **State-Hoisting Controller (`SvgaController`)**: Both declarative and imperative playback, pause, resume, replay, seekTo, and reactive progress tracking.
- ⏱️ **Centralized Choreographer Engine**: Single global clock ticker. Automatically detaches frame callbacks when idle, achieving **0% CPU consumption**.
- 🎯 **Adaptive Priority Scheduling (`SvgaPriority`)**: `High`, `Normal`, and `Low` policies with intelligent frame-skipping under heavy load (FPS < 48) to keep UI smooth even with 300+ simultaneous animations.
- 🪶 **Ultra-lightweight Wire Protobuf Runtime**: Replaced bloated `protobuf-java` with Square Wire 5.4.0, cutting binary size by 70%+ and removing thousands of unused methods.
- ⚡ **Zero-GC Canvas Direct Rendering**: Draws directly onto `nativeCanvas` with internal object pools (Matrix, Path, Paint), preventing GC pauses and frame drops.
- 🚀 **Two-Tier Cache & SingleFlight**: L1 Memory LRU cache for instant first frame; L2 Disk cache with 32 segment locks for high concurrency without lock contention; automatic merging of identical in-flight network requests.
- 🛡️ **Dynamic Downsampling & Security Guards**: Decodes bitmaps with `inSampleSize` tailored to view dimensions, preventing OOM. Built-in Zip Bomb (CWE-409) decompression limits.
- 🔤 **Modern Kotlin DSL for Dynamic Entities**: Dynamically replace text (with custom `TextPaint`), images (local `Bitmap` or remote `url`), toggle layer visibility, listen for area clicks, and draw custom canvas slots.
- 🔊 **Comprehensive Audio Lifecycle**: Built-in `SoundPool` manager supporting volume adjustment, instant mute (`isMute`), and automatic lifecycle-aware pause/resume.
- 📱 **Complete View Backward Compatibility**: Fully updated `SvgaImageView` and `SvgaDrawable` with XML attribute support and leak-free `onDetachedFromWindow` handling.

---

## 📊 Feature Comparison

| Dimension | **compose-svga (This Project)** 🚀 | **SVGAPlayer-Android (Legacy)** 👴 |
| :--- | :--- | :--- |
| **Compose Support** | ✅ Native composables with slot API and state streams | ❌ Forced wrapping via `AndroidView` |
| **State Driving** | ✅ `SvgaController` reactive unidirectional data flow | ❌ Imperative callbacks only |
| **Proto Serialization** | 🚀 **Square Wire 5.x** (ultra-lightweight, small footprint) | ⚠️ Google `protobuf-java` (bloated, massive method count) |
| **Clock Engine** | ⚡ **Centralized Choreographer engine**, **0% idle CPU** | ❌ Independent ValueAnimators per view; heavy CPU wakeups |
| **Load Scheduling** | 🎯 **Adaptive Priority Throttling** (High / Normal / Low) | ❌ No throttling, severe frame drops when stacking animations |
| **Network Deduplication** | 🛡 **SingleFlight mechanism** coalesces concurrent requests | ❌ Redundant downloads of identical URLs |
| **Disk Cache Safety** | 🔒 **32-segment hash locks**, zero lock contention | ⚠️ Basic single lock/no-lock; prone to collisions |
| **OOM Prevention** | 🛡 **Dynamic downsampling (`inSampleSize`) based on target bounds** | ❌ Always decodes full-sized bitmaps; frequent OOMs |
| **Security Defense** | 🛡 **Built-in Zip Bomb (CWE-409) mitigation** | ❌ No decompression boundary safety checks |
| **Memory Leak Cleanup** | 🧼 Lifecycle-bound auto release on detached | ⚠️ Known historic memory leak issues |
| **Dynamic API** | ✨ Standard API + **Declarative Kotlin DSL builder** | ⚠️ Basic imperative API only |
| **Classic View Support**| ✅ Fully supported & upgraded `SvgaImageView` | ✅ Supported |

---

## 📦 Installation

### 1. Add JitPack Repository

In your root `settings.gradle.kts` (or `settings.gradle`):

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Add Dependency

In your app module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.TroyLi-Dev:compose-svga:1.0.1-rc01")
}
```

> **Groovy DSL (`build.gradle`)**:
> ```groovy
> implementation 'com.github.TroyLi-Dev:compose-svga:1.0.1-rc01'
> ```

---

## 🚀 Quick Start

### 1. Jetpack Compose Basic Usage

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rui.compose.svga.SvgaAnimation

@Composable
fun SimpleSvgaDemo() {
    // 1. Remote HTTP/HTTPS URL
    SvgaAnimation(
        model = "https://example.com/gift.svga",
        modifier = Modifier.size(200.dp)
    )

    // 2. Android Assets ("anim.svga" or "assets://anim.svga")
    SvgaAnimation(
        model = "player/heart_burst.svga",
        modifier = Modifier.size(200.dp)
    )

    // 3. Local File object or absolute file path
    SvgaAnimation(
        model = File(context.filesDir, "custom.svga"),
        modifier = Modifier.size(200.dp)
    )

    // 4. Android Raw Resource ID
    SvgaAnimation(
        model = R.raw.celebration,
        modifier = Modifier.size(200.dp)
    )
}
```

> 💡 **Tip**: You can also use `SvgaPlayer(...)`, which is a 100% equivalent alias for `SvgaAnimation(...)`.

---

### 2. Controller-Driven Playback (`SvgaController`)

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
        initialLoops = 1 // 1 for single play stopping at end frame; 0 for infinite loop
    )

    Column {
        SvgaAnimation(
            controller = controller,
            modifier = Modifier.size(240.dp)
        )

        Text("Frame: ${controller.currentFrame} / ${controller.totalFrames}")
        Text("Progress: ${(controller.progress * 100).toInt()}%")
        Text("State: ${controller.playState}")

        Row {
            Button(onClick = { controller.play() }) { Text("Play") }
            Button(onClick = { controller.pause() }) { Text("Pause") }
            Button(onClick = { controller.resume() }) { Text("Resume") }
            Button(onClick = { controller.replay() }) { Text("Replay") }
            Button(onClick = { controller.seekToProgress(0.5f) }) { Text("Seek 50%") }
        }
    }
}
```

---

### 3. Slots & Lifecycle Callbacks

```kotlin
SvgaAnimation(
    model = "https://example.com/gift.svga",
    modifier = Modifier.size(200.dp),
    loading = {
        CircularProgressIndicator(modifier = Modifier.size(36.dp))
    },
    failure = {
        Text("Failed to load SVGA", color = Color.Red)
    },
    onLoading = { Log.d("SVGA", "Loading started") },
    onPlay = { Log.d("SVGA", "First frame rendered") },
    onSuccess = { entity -> Log.d("SVGA", "Parsed, frames=${entity.frames}") },
    onError = { error -> Log.e("SVGA", "Error decoding", error) },
    onStep = { frame, percentage -> /* frame progress callback */ },
    onFinished = { Log.d("SVGA", "Playback finished") }
)
```

---

## 🎨 Dynamic Elements (Dynamic Entity & DSL)

### Kotlin DSL Builder

```kotlin
import android.graphics.Color
import android.text.TextPaint
import com.rui.compose.svga.drawer.buildSvgaDynamic

val dynamicEntity = remember {
    buildSvgaDynamic {
        // 1. Dynamic Text Replacement
        text(
            key = "banner_text",
            content = "Happy Birthday Troy Li 🎉",
            textPaint = TextPaint().apply {
                color = Color.YELLOW
                textSize = 38f
                isFakeBoldText = true
            }
        )

        // 2. Dynamic Image Replacement (Bitmap or remote URL)
        image(key = "user_avatar", bitmap = avatarBitmap)
        image(key = "badge_icon", url = "https://example.com/badge.png")

        // 3. Dynamic Layer Hiding
        hidden(key = "legacy_watermark", isHidden = true)

        // 4. Hotspot Click Area Listener
        click(key = "action_button")
    }
}

SvgaAnimation(
    model = "https://example.com/gift.svga",
    dynamicEntity = dynamicEntity,
    modifier = Modifier.size(260.dp)
)
```

---

## ⚡ Advanced Capabilities

### 1. Adaptive Priority Scheduling (`SvgaPriority`)

```kotlin
// High Priority: Full-screen gift effects, guaranteed 60FPS without frame dropping
SvgaAnimation(
    model = bigGiftUrl,
    priority = SvgaPriority.High,
    modifier = Modifier.fillMaxSize()
)

// Normal Priority (Default): Drops to half rate during heavy system load (FPS < 48)
SvgaAnimation(
    model = normalGiftUrl,
    priority = SvgaPriority.Normal,
    modifier = Modifier.size(100.dp)
)

// Low Priority: Background decor / list items, throttled to 15 FPS to conserve CPU and battery
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

### 2. Audio Control & Sound Manager (`SvgaSoundManager`)

```kotlin
// Mute specific component
SvgaAnimation(
    model = soundGiftUrl,
    isMute = true,
    modifier = Modifier.size(200.dp)
)

// Global sound volume (0.0f ~ 1.0f)
SvgaSoundManager.setVolume(0.8f)

// Release sound pool
SvgaSoundManager.release()
```

---

## 📱 Android View Support (`SvgaImageView`)

### XML Layout

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

### Kotlin Code

```kotlin
val svgaImageView = findViewById<SvgaImageView>(R.id.svgaImageView)

svgaImageView.load("https://example.com/gift.svga") { videoEntity ->
    Log.d("SVGA", "Loaded successfully")
}

svgaImageView.startAnimation()
svgaImageView.pauseAnimation()
svgaImageView.stopAnimation()

svgaImageView.callback = object : SvgaCallback {
    override fun onPause() {}
    override fun onFinished() {}
    override fun onRepeat() {}
    override fun onStep(frame: Int, percentage: Double) {}
}
```

---

## 🛡️ ProGuard / R8 Rules

Consumer ProGuard rules are bundled with the AAR automatically:

```proguard
# Wire Runtime
-keep class com.squareup.wire.** { *; }

# Compose SVGA Components & Proto Models
-keep class com.rui.compose.svga.** { *; }
-keepclassmembers class com.rui.compose.svga.** { *; }
```

---

## 💡 Demo Application

- 🚀 [MainActivity.kt](app/src/main/java/com/rui/compose/svga/MainActivity.kt) - 300+ item extreme grid stress test, real-time FPS/CPU/Memory dashboard, dynamic interference test.
- 📱 [ComposeStressTestActivity.kt](app/src/main/java/com/rui/compose/svga/ComposeStressTestActivity.kt) - Multi-layer Compose stress test.
- 🏛️ [ViewStressTestActivity.kt](app/src/main/java/com/rui/compose/svga/ViewStressTestActivity.kt) - Traditional View stress test comparison.

---

## 📄 License

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
