package com.rui.compose.svga

import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.withSave
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rui.compose.svga.audio.SvgaSoundManager
import com.rui.compose.svga.cache.SvgaDiskCache
import com.rui.compose.svga.cache.SvgaMemoryCache
import com.rui.compose.svga.controller.SvgaController
import com.rui.compose.svga.controller.rememberSvgaController
import com.rui.compose.svga.core.LocalSystemLoad
import com.rui.compose.svga.core.SvgaEnvironment
import com.rui.compose.svga.drawer.SvgaCanvasDrawer
import com.rui.compose.svga.drawer.SvgaDynamicEntity
import com.rui.compose.svga.entity.SvgaVideoEntity
import com.rui.compose.svga.ext.resumeSafe
import com.rui.compose.svga.ext.resumeWithExceptionSafe
import com.rui.compose.svga.model.SvgaLoadState
import com.rui.compose.svga.model.SvgaPlayState
import com.rui.compose.svga.model.SvgaPriority
import com.rui.compose.svga.parser.SvgaParser
import com.rui.compose.svga.utils.SvgaMemoryCalculator
import com.rui.compose.svga.utils.log.LogUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.math.min

internal val DEFAULT_LAMBDA: () -> Unit = {}
internal val DEFAULT_ON_STEP: (frame: Int, percentage: Double) -> Unit = { _, _ -> }
internal val DEFAULT_ON_SUCCESS: (SvgaVideoEntity) -> Unit = {}
internal val DEFAULT_ON_ERROR: (Throwable) -> Unit = {}

private class ComponentSizeRef(var width: Int = 0, var height: Int = 0)

private class AnimationTimeline(
    var accumulatedTimeNanos: Long = 0L,
    var lastTickTimeNanos: Long = 0L,
    var lastRenderNanos: Long = 0L,
    var lastFrame: Int = -2,
    var nominalFps: Int = 30,
    var nominalFrameDurationNanos: Long = 33_333_333L,
    var loopDurationNanos: Long = 0L,
    var throttleIntervalNanos: Long = 33_333_333L,
    var throttledNormalIntervalNanos: Long = 66_666_666L
)

@Volatile
private var isEnvironmentInitialized = false

private fun ensureEnvironmentInit(context: android.content.Context) {
    if (!isEnvironmentInitialized) {
        synchronized(SvgaEnvironment::class.java) {
            if (!isEnvironmentInitialized) {
                SvgaDiskCache.init(context)
                com.rui.compose.svga.cache.SvgaMemoryCache.instance.init(context)
                SvgaMemoryCalculator.instance.init(context)
                isEnvironmentInitialized = true
            }
        }
    }
}

/**
 * Compose SVGA 核心播放组件
 *
 * 该组件基于 Jetpack Compose 现代化标准构建，具有以下特性：
 * 1. **中心化时钟驱动**：基于统一的 Choreographer [SvgaEnvironment]，无活跃动画时 0% CPU 零开销休眠。
 * 2. **自适应降频门禁**：通过 [priority] 控制，在系统帧率下降时智能节流抽帧，保护主线程交互流畅。
 * 3. **全格式自适应**：支持 URL、Assets (自动去除 assets:// 前缀)、本地文件 File、绝对路径与 Raw 资源 ID。
 * 4. **双级缓存加速**：内存 LRU 命中即时首帧渲染，磁盘透明原子解压。
 * 5. **零 GC 渲染热路径**：原生 nativeCanvas 直绘，避免中转 Bitmap 创建。
 *
 * @param model 资源对象 (String 路径/URL、File、资源 ID 等)
 * @param modifier 修饰符
 * @param priority 渲染优先级策略，默认 [SvgaPriority.Normal]
 * @param maxFps 帧率上限限制
 * @param loops 循环次数。0 为无限循环，>0 为指定次数播放完停在末帧
 * @param dynamicEntity 动态元素注入实体（文本、自定义位图等）
 * @param isStop 是否暂停动画
 * @param isMute 是否静音
 * @param contentScale 画布缩放模式
 * @param onLoading 资源开始加载时回调
 * @param onPlay 正式开始第一帧渲染时回调
 * @param onSuccess 资源解析成功回调，携带 [SvgaVideoEntity]
 * @param onError 加载或解码失败异常回调
 * @param onStep 逐帧播放进度回调，[frame] 为帧序，[percentage] 为播放百分比 (0.0~1.0)
 * @param onFinished 动画完全播毕回调（仅 loops > 0 时触发）
 * @param loading 加载过程中展示的 Compose 占位插槽
 * @param failure 失败时展示的 Compose 占位插槽
 */
@Composable
fun SvgaAnimation(
    model: Any?,
    modifier: Modifier = Modifier,
    controller: SvgaController? = null,
    priority: SvgaPriority = controller?.priority ?: SvgaPriority.Normal,
    maxFps: Int = controller?.maxFps ?: Int.MAX_VALUE,
    loops: Int = controller?.loops ?: 0,
    dynamicEntity: SvgaDynamicEntity? = null,
    isStop: Boolean = controller?.let { it.isPaused || it.playState == SvgaPlayState.Idle } ?: false,
    isMute: Boolean = controller?.isMuted ?: false,
    contentScale: ContentScale = ContentScale.Fit,
    needCutBitmap: Boolean = (priority == SvgaPriority.Low),
    onLoading: () -> Unit = DEFAULT_LAMBDA,
    onPlay: () -> Unit = DEFAULT_LAMBDA,
    onSuccess: (SvgaVideoEntity) -> Unit = DEFAULT_ON_SUCCESS,
    onError: (Throwable) -> Unit = DEFAULT_ON_ERROR,
    onStep: (frame: Int, percentage: Double) -> Unit = DEFAULT_ON_STEP,
    onFinished: () -> Unit = DEFAULT_LAMBDA,
    onItemClick: ((key: String) -> Unit)? = null,
    loading: @Composable (() -> Unit)? = null,
    failure: @Composable (() -> Unit)? = null
) {
    if (model == null) {
        controller?.loadState = SvgaLoadState.Idle
        controller?.videoEntity = null
        Box(modifier = modifier)
        return
    }

    val context = LocalContext.current.applicationContext
    ensureEnvironmentInit(context)
    val systemLoad = LocalSystemLoad.current
    val componentSize = remember { ComponentSizeRef() }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isStarted by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> isStarted = true
                Lifecycle.Event.ON_STOP -> isStarted = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val currentOnLoading by rememberUpdatedState(onLoading)
    val currentOnPlay by rememberUpdatedState(onPlay)
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnStep by rememberUpdatedState(onStep)
    val currentOnFinished by rememberUpdatedState(onFinished)

    val cacheKey = remember(model) {
        SvgaDiskCache.buildCacheKeyForModel(model)
    }

    val initialEntity = remember(cacheKey) {
        SvgaMemoryCache.instance.get(cacheKey)?.also { cached ->
            controller?.videoEntity = cached
            controller?.loadState = SvgaLoadState.Success
            if (controller?.playState != SvgaPlayState.Paused && controller?.playState != SvgaPlayState.Idle) {
                controller?.playState = SvgaPlayState.Playing
            }
        }
    }

    var videoEntity by remember(cacheKey) {
        mutableStateOf(initialEntity)
    }
    var loadState by remember(cacheKey) {
        mutableStateOf(if (videoEntity != null) SvgaLoadState.Success else SvgaLoadState.Loading)
    }
    var redrawTrigger by remember(cacheKey) { mutableIntStateOf(0) }

    DisposableEffect(dynamicEntity) {
        val dynamic = dynamicEntity ?: return@DisposableEffect onDispose {}
        val listener: () -> Unit = { redrawTrigger++ }
        dynamic.addOnDynamicChangedListener(listener)
        onDispose {
            dynamic.removeOnDynamicChangedListener(listener)
        }
    }

    LaunchedEffect(cacheKey, controller?.commandTrigger) {
        // 1. 若内存缓存已命中，直接完成复用
        val cached = SvgaMemoryCache.instance.get(cacheKey)
        if (cached != null) {
            val wasAlreadySet = (videoEntity === cached)
            if (!wasAlreadySet) {
                videoEntity = cached
                loadState = SvgaLoadState.Success
                redrawTrigger++
            }
            controller?.videoEntity = cached
            controller?.loadState = SvgaLoadState.Success
            if (controller?.playState != SvgaPlayState.Paused && controller?.playState != SvgaPlayState.Idle) {
                controller?.playState = SvgaPlayState.Playing
            }
            currentOnSuccess(cached)
            currentOnPlay()
            return@LaunchedEffect
        }

        if (videoEntity != null) {
            if (loadState == SvgaLoadState.Success) {
                controller?.videoEntity = videoEntity
                controller?.loadState = SvgaLoadState.Success
                if (controller?.playState != SvgaPlayState.Paused && controller?.playState != SvgaPlayState.Idle) {
                    controller?.playState = SvgaPlayState.Playing
                }
                redrawTrigger++
                currentOnSuccess(videoEntity!!)
                currentOnPlay()
            }
            return@LaunchedEffect
        }
        controller?.loadState = SvgaLoadState.Loading
        currentOnLoading()
        try {
            val entity = suspendCancellableCoroutine<SvgaVideoEntity> { continuation ->
                val parser = SvgaParser(context, needCutBitmap = needCutBitmap)
                if (componentSize.width > 0 && componentSize.height > 0) {
                    parser.setFrameSize(componentSize.width, componentSize.height)
                }

                val cancelTask = parser.decodeAuto(model, object : SvgaParser.ParseCompletion {
                    override fun onComplete(videoItem: SvgaVideoEntity) =
                        continuation.resumeSafe(videoItem)

                    override fun onError(e: Exception, alias: String) =
                        continuation.resumeWithExceptionSafe(e)
                })

                continuation.invokeOnCancellation { cancelTask?.invoke() }
            }
            videoEntity = entity
            loadState = SvgaLoadState.Success
            controller?.videoEntity = entity
            controller?.loadState = SvgaLoadState.Success
            if (controller?.playState != SvgaPlayState.Paused && controller?.playState != SvgaPlayState.Idle) {
                controller?.playState = SvgaPlayState.Playing
            }
            redrawTrigger++
            currentOnSuccess(entity)
            currentOnPlay()
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                LogUtils.error("SvgaAnimation", "Decode failed for model: $model", e)
                loadState = SvgaLoadState.Error
                controller?.loadState = SvgaLoadState.Error
                currentOnError(e)
            }
        }
    }

    val drawer = remember(videoEntity, dynamicEntity) {
        videoEntity?.let { SvgaCanvasDrawer(it, dynamicEntity ?: com.rui.compose.svga.drawer.EMPTY_DYNAMIC_ENTITY) }
    }
    // 离开组件或切换动画实体时，彻底停播当前实例的音频流并清理资源
    DisposableEffect(drawer) {
        onDispose {
            drawer?.stopAudio()
            drawer?.clear()
        }
    }

    // 音频播放生命周期控制：暂停或置于后台时暂停音频，前台继续播放时恢复（仅对含音频的实体启动协程）
    val hasAudio = remember(videoEntity) { videoEntity?.audioList?.isNotEmpty() == true }
    if (hasAudio) {
        LaunchedEffect(drawer, isStop, isStarted, isMute) {
            val d = drawer ?: return@LaunchedEffect
            val vol = if (isMute) 0.0f else 1.0f
            d.setAudioVolume(vol)
            val shouldPauseAudio = isStop || !isStarted
            if (shouldPauseAudio) {
                d.pauseAudio()
            } else {
                d.resumeAudio()
            }
        }
    }

    val nativeScaleType = remember(contentScale) {
        when (contentScale) {
            ContentScale.FillBounds -> ImageView.ScaleType.FIT_XY
            ContentScale.Crop -> ImageView.ScaleType.CENTER_CROP
            ContentScale.Inside -> ImageView.ScaleType.CENTER_INSIDE
            else -> ImageView.ScaleType.FIT_CENTER
        }
    }

    val timeline = remember(cacheKey) { AnimationTimeline() }
    var drawFrameIndex by remember(cacheKey) { mutableIntStateOf(0) }
    var isFinished by remember(cacheKey) { mutableStateOf(false) }

    // 响应 Controller 命令控制（跳转、重播、停止、强制刷新）
    if (controller != null) {
        LaunchedEffect(controller.commandTrigger, videoEntity) {
            val entity = videoEntity ?: return@LaunchedEffect
            redrawTrigger++
            val targetFrame = controller.seekFrameTarget
            val nominalFps = (if (entity.fps > 0) entity.fps else 30).coerceAtMost(maxFps).coerceAtLeast(1)
            val nominalFrameDurationNanos = 1_000_000_000L / nominalFps

            if (targetFrame != null) {
                val clampedFrame = targetFrame.coerceIn(0, (entity.frames - 1).coerceAtLeast(0))
                timeline.accumulatedTimeNanos = clampedFrame.toLong() * nominalFrameDurationNanos
                drawFrameIndex = clampedFrame
                timeline.lastFrame = clampedFrame
                controller.currentFrame = clampedFrame
                controller.seekFrameTarget = null
                timeline.lastTickTimeNanos = 0L
                if (isFinished && controller.isPlaying) {
                    isFinished = false
                }
            } else if (controller.isPlaying && isFinished) {
                timeline.accumulatedTimeNanos = 0L
                drawFrameIndex = 0
                timeline.lastFrame = -2
                controller.currentFrame = 0
                isFinished = false
                timeline.lastTickTimeNanos = 0L
            } else if (controller.playState == SvgaPlayState.Idle) {
                timeline.accumulatedTimeNanos = 0L
                drawFrameIndex = 0
                timeline.lastFrame = -2
                controller.currentFrame = 0
            }
        }
    }

    if (loops > 0) {
        LaunchedEffect(isStop, loops) {
            if (!isStop && isFinished) {
                isFinished = false
                timeline.accumulatedTimeNanos = 0L
                drawFrameIndex = 0
                timeline.lastFrame = -2
            }
        }
    }

    DisposableEffect(videoEntity, isStop, priority, loops, maxFps, isFinished, isStarted) {
        val entity = videoEntity ?: return@DisposableEffect onDispose {}
        if (isStop || isFinished || !isStarted) return@DisposableEffect onDispose {}

        val numFrames = entity.frames
        if (numFrames <= 0) return@DisposableEffect onDispose {}

        val nominalFps = (if (entity.fps > 0) entity.fps else 30).coerceAtMost(maxFps).coerceAtLeast(1)
        val nominalFrameDurationNanos = 1_000_000_000L / nominalFps
        val loopDurationNanos = nominalFrameDurationNanos * numFrames

        val targetFps = when (priority) {
            SvgaPriority.High -> nominalFps
            SvgaPriority.Normal -> nominalFps
            SvgaPriority.Low -> min(nominalFps / 2, 15).coerceAtLeast(1)
        }
        val throttleIntervalNanos = 1_000_000_000L / targetFps
        val throttledNormalIntervalNanos = 1_000_000_000L / (nominalFps / 2).coerceAtLeast(1)

        timeline.nominalFps = nominalFps
        timeline.nominalFrameDurationNanos = nominalFrameDurationNanos
        timeline.loopDurationNanos = loopDurationNanos
        timeline.throttleIntervalNanos = throttleIntervalNanos
        timeline.throttledNormalIntervalNanos = throttledNormalIntervalNanos
        timeline.lastTickTimeNanos = 0L // 重置采样时间戳，避免前后台切换时发生时间跳跃 (Anti-Jump)

        val tickListener = object : SvgaEnvironment.TickListener {
            override fun onTick(frameTimeNanos: Long) {
                if (timeline.lastTickTimeNanos == 0L) {
                    timeline.lastTickTimeNanos = frameTimeNanos
                    return
                }
                val delta = frameTimeNanos - timeline.lastTickTimeNanos
                timeline.lastTickTimeNanos = frameTimeNanos
                timeline.accumulatedTimeNanos += delta
                val elapsedNanos = timeline.accumulatedTimeNanos

                // 1. 判定是否播放完毕（loops 限制）
                if (loops > 0 && elapsedNanos >= timeline.loopDurationNanos * loops) {
                    val lastFrame = numFrames - 1
                    if (drawFrameIndex != lastFrame) {
                        drawFrameIndex = lastFrame
                        timeline.lastFrame = lastFrame
                        controller?.currentFrame = lastFrame
                        if (currentOnStep !== DEFAULT_ON_STEP) {
                            currentOnStep(lastFrame, 1.0)
                        }
                    }
                    if (!isFinished) {
                        isFinished = true
                        controller?.playState = SvgaPlayState.Finished
                        currentOnFinished()
                    }
                    SvgaEnvironment.removeListener(this)
                    return
                }

                // 2. 分级抽帧节流门禁 (Throttle Gate)
                val throttleInterval = if (priority == SvgaPriority.Normal && SvgaEnvironment.currentFps < 48) {
                    timeline.throttledNormalIntervalNanos
                } else {
                    timeline.throttleIntervalNanos
                }
                if (frameTimeNanos - timeline.lastRenderNanos < throttleInterval) {
                    return // 节流跳过，保护刷新流水线
                }
                timeline.lastRenderNanos = frameTimeNanos

                // 3. 计算真实时间轴进度（单调递增）
                val currentFrame = ((elapsedNanos / timeline.nominalFrameDurationNanos) % numFrames).toInt()
                if (currentFrame != timeline.lastFrame) {
                    timeline.lastFrame = currentFrame
                    drawFrameIndex = currentFrame
                    controller?.currentFrame = currentFrame
                    if (currentOnStep !== DEFAULT_ON_STEP) {
                        currentOnStep(currentFrame, ((currentFrame + 1).toDouble() / numFrames).coerceIn(0.0, 1.0))
                    }
                }
            }
        }
        SvgaEnvironment.addListener(tickListener)
        onDispose { SvgaEnvironment.removeListener(tickListener) }
    }

    val currentOnItemClick by rememberUpdatedState(onItemClick)
    val clickModifier = if (onItemClick != null) {
        Modifier.pointerInput(dynamicEntity, currentOnItemClick) {
            detectTapGestures { offset ->
                val dynamic = dynamicEntity ?: return@detectTapGestures
                for ((key, value) in dynamic.clickMap) {
                    val minX = kotlin.math.min(value[0], value[2])
                    val maxX = kotlin.math.max(value[0], value[2])
                    val minY = kotlin.math.min(value[1], value[3])
                    val maxY = kotlin.math.max(value[1], value[3])
                    if (offset.x >= minX && offset.x <= maxX && offset.y >= minY && offset.y <= maxY) {
                        currentOnItemClick?.invoke(key)
                        break
                    }
                }
            }
        }
    } else Modifier

    Box(
        modifier = modifier
            .graphicsLayer { clip = true }
            .onSizeChanged {
                componentSize.width = it.width
                componentSize.height = it.height
            }
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        if (loadState == SvgaLoadState.Loading) loading?.invoke()
        if (loadState == SvgaLoadState.Error) failure?.invoke()

        if (videoEntity != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trigger = redrawTrigger
                val drawerObj = drawer ?: return@Canvas
                if (drawFrameIndex >= 0 && size.width > 0f && size.height > 0f) {
                    drawIntoCanvas { canvas ->
                        val nativeCanvas = canvas.nativeCanvas
                        nativeCanvas.withSave {
                            drawerObj.drawFrame(
                                this,
                                drawFrameIndex,
                                nativeScaleType,
                                size.width,
                                size.height
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 带有 Kotlin DSL 动态配置支持的 [SvgaAnimation] 便捷重载
 */
@Composable
fun SvgaAnimation(
    model: Any?,
    modifier: Modifier = Modifier,
    priority: SvgaPriority = SvgaPriority.Normal,
    maxFps: Int = Int.MAX_VALUE,
    loops: Int = 0,
    isStop: Boolean = false,
    isMute: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
    onLoading: () -> Unit = DEFAULT_LAMBDA,
    onPlay: () -> Unit = DEFAULT_LAMBDA,
    onSuccess: (SvgaVideoEntity) -> Unit = DEFAULT_ON_SUCCESS,
    onError: (Throwable) -> Unit = DEFAULT_ON_ERROR,
    onStep: (frame: Int, percentage: Double) -> Unit = DEFAULT_ON_STEP,
    onFinished: () -> Unit = DEFAULT_LAMBDA,
    onItemClick: ((key: String) -> Unit)? = null,
    loading: @Composable (() -> Unit)? = null,
    failure: @Composable (() -> Unit)? = null,
    dynamicBuilder: SvgaDynamicEntity.() -> Unit
) {
    val dynamicEntity = remember(dynamicBuilder) {
        SvgaDynamicEntity().apply(dynamicBuilder)
    }

    SvgaAnimation(
        model = model,
        modifier = modifier,
        priority = priority,
        maxFps = maxFps,
        loops = loops,
        dynamicEntity = dynamicEntity,
        isStop = isStop,
        isMute = isMute,
        contentScale = contentScale,
        onLoading = onLoading,
        onPlay = onPlay,
        onSuccess = onSuccess,
        onError = onError,
        onStep = onStep,
        onFinished = onFinished,
        onItemClick = onItemClick,
        loading = loading,
        failure = failure
    )
}

/**
 * 由 [SvgaController] 精确控制播放生命周期的 [SvgaAnimation] 重载
 */
@Composable
fun SvgaAnimation(
    controller: SvgaController,
    modifier: Modifier = Modifier,
    dynamicEntity: SvgaDynamicEntity? = null,
    contentScale: ContentScale = ContentScale.Fit,
    onLoading: () -> Unit = DEFAULT_LAMBDA,
    onPlay: () -> Unit = DEFAULT_LAMBDA,
    onSuccess: (SvgaVideoEntity) -> Unit = DEFAULT_ON_SUCCESS,
    onError: (Throwable) -> Unit = DEFAULT_ON_ERROR,
    onStep: (frame: Int, percentage: Double) -> Unit = DEFAULT_ON_STEP,
    onFinished: () -> Unit = DEFAULT_LAMBDA,
    onItemClick: ((key: String) -> Unit)? = null,
    loading: @Composable (() -> Unit)? = null,
    failure: @Composable (() -> Unit)? = null
) {
    SvgaAnimation(
        model = controller.model,
        modifier = modifier,
        controller = controller,
        priority = controller.priority,
        maxFps = controller.maxFps,
        loops = controller.loops,
        dynamicEntity = dynamicEntity,
        isStop = controller.isPaused || controller.playState == SvgaPlayState.Idle,
        isMute = controller.isMuted,
        contentScale = contentScale,
        onLoading = {
            controller.loadState = SvgaLoadState.Loading
            onLoading()
        },
        onPlay = {
            controller.playState = SvgaPlayState.Playing
            onPlay()
        },
        onSuccess = { entity ->
            controller.videoEntity = entity
            controller.loadState = SvgaLoadState.Success
            onSuccess(entity)
        },
        onError = { err ->
            controller.loadState = SvgaLoadState.Error
            onError(err)
        },
        onStep = { frame, percentage ->
            controller.currentFrame = frame
            onStep(frame, percentage)
        },
        onFinished = {
            controller.playState = SvgaPlayState.Finished
            onFinished()
        },
        onItemClick = onItemClick,
        loading = loading,
        failure = failure
    )
}

/**
 * 由 [SvgaController] 控制并带有 Kotlin DSL 动态配置支持的 [SvgaAnimation] 便捷重载
 */
@Composable
fun SvgaAnimation(
    controller: SvgaController,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onLoading: () -> Unit = DEFAULT_LAMBDA,
    onPlay: () -> Unit = DEFAULT_LAMBDA,
    onSuccess: (SvgaVideoEntity) -> Unit = DEFAULT_ON_SUCCESS,
    onError: (Throwable) -> Unit = DEFAULT_ON_ERROR,
    onStep: (frame: Int, percentage: Double) -> Unit = DEFAULT_ON_STEP,
    onFinished: () -> Unit = DEFAULT_LAMBDA,
    onItemClick: ((key: String) -> Unit)? = null,
    loading: @Composable (() -> Unit)? = null,
    failure: @Composable (() -> Unit)? = null,
    dynamicBuilder: SvgaDynamicEntity.() -> Unit
) {
    val dynamicEntity = remember(dynamicBuilder) {
        SvgaDynamicEntity().apply(dynamicBuilder)
    }
    SvgaAnimation(
        controller = controller,
        modifier = modifier,
        dynamicEntity = dynamicEntity,
        contentScale = contentScale,
        onLoading = onLoading,
        onPlay = onPlay,
        onSuccess = onSuccess,
        onError = onError,
        onStep = onStep,
        onFinished = onFinished,
        onItemClick = onItemClick,
        loading = loading,
        failure = failure
    )
}
