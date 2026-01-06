package com.rui.composes.svga

import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.opensource.svgaplayer.SVGAActivitiesCache
import com.opensource.svgaplayer.SVGACache
import com.opensource.svgaplayer.SVGADynamicEntity
import com.opensource.svgaplayer.SVGAParser
import com.opensource.svgaplayer.SVGAVideoEntity
import com.opensource.svgaplayer.drawer.SVGACanvasDrawer
import com.rui.composes.svga.core.LocalSvgaClock
import com.rui.composes.svga.core.LocalSystemLoad
import com.rui.composes.svga.core.SvgaEnvironment
import com.rui.composes.svga.model.SvgaLoadState
import com.rui.composes.svga.model.SvgaPriority
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min
import kotlin.random.Random

private val GlobalStartTimes = ConcurrentHashMap<Any, Long>()

@Composable
fun SvgaAnimation(
    model: Any,
    modifier: Modifier = Modifier,
    priority: SvgaPriority = SvgaPriority.Normal,
    maxFps: Int = Int.MAX_VALUE,
    allowStopOnCriticalLoad: Boolean = false,
    loops: Int = 0,
    dynamicEntity: SVGADynamicEntity? = null,
    isStop: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
    onLoading: () -> Unit = {},
    onPlay: () -> Unit = {},
    onSuccess: (SVGAVideoEntity) -> Unit = {},
    onError: (Throwable) -> Unit = {},
    onStep: (frame: Int, percentage: Double) -> Unit = { _, _ -> },
    onFinished: () -> Unit = {},
    loading: @Composable (() -> Unit)? = null,
    failure: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current.applicationContext
    val globalTick = LocalSvgaClock.current
    val systemLoad = LocalSystemLoad.current
    var componentSize by remember { mutableStateOf(IntSize.Zero) }

    DisposableEffect(globalTick) {
        if (globalTick == SvgaEnvironment.tickState) {
            SvgaEnvironment.attach()
            onDispose { SvgaEnvironment.detach() }
        } else onDispose {}
    }

    val initialEntity = remember(model) {
        val key = SVGACache.buildCacheKey(model.toString())
        SVGAActivitiesCache.instance.get(key)
    }
    var videoEntity by remember(model) { mutableStateOf<SVGAVideoEntity?>(initialEntity) }
    var loadState by remember(model) {
        mutableStateOf(if (initialEntity != null) SvgaLoadState.Success else SvgaLoadState.Loading)
    }

    LaunchedEffect(model, componentSize) {
        if (componentSize.width <= 0 || videoEntity != null) {
            if (videoEntity != null && loadState == SvgaLoadState.Success) {
                onSuccess(videoEntity!!); onPlay()
            }
            return@LaunchedEffect
        }
        onLoading()
        delay(Random.nextLong(10, 100)); yield()
        try {
            val entity = suspendCancellableCoroutine<SVGAVideoEntity> { continuation ->
                val parser = SVGAParser(context)
                parser.setFrameSize(componentSize.width, componentSize.height)
                val cancelTask = parser.decodeFromURL(java.net.URL(model.toString()), object : SVGAParser.ParseCompletion {
                    override fun onComplete(videoItem: SVGAVideoEntity) {
                        if (continuation.isActive) continuation.resume(videoItem)
                    }
                    override fun onError(e: Exception, alias: String) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                })
                continuation.invokeOnCancellation { cancelTask?.invoke() }
            }
            videoEntity = entity
            loadState = SvgaLoadState.Success
            onSuccess(entity); onPlay()
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                loadState = SvgaLoadState.Error; onError(e)
            }
        }
    }

    val startTime = remember(model) { GlobalStartTimes.getOrPut(model) { System.nanoTime() } }
    val drawer = remember(videoEntity, dynamicEntity) {
        videoEntity?.let { SVGACanvasDrawer(it, dynamicEntity ?: SVGADynamicEntity()) }
    }
    val nativeScaleType = remember(contentScale) {
        when (contentScale) {
            ContentScale.FillBounds -> ImageView.ScaleType.FIT_XY
            ContentScale.Crop -> ImageView.ScaleType.CENTER_CROP
            ContentScale.Inside -> ImageView.ScaleType.CENTER_INSIDE
            else -> ImageView.ScaleType.FIT_CENTER
        }
    }

    val lastFrameRef = remember(model) { object { var value = -2 } }
    var drawFrameIndex by remember { mutableIntStateOf(0) }
    
    // 极致性能重构：使用中心化 Tick 监听，完全跳过协程调度
    DisposableEffect(videoEntity, isStop, priority, loops, maxFps) {
        val entity = videoEntity ?: return@DisposableEffect onDispose {}
        if (isStop) return@DisposableEffect onDispose {}

        val tickListener = object : SvgaEnvironment.TickListener {
            override fun onTick(frameTimeNanos: Long) {
                val elapsed = frameTimeNanos - startTime
                val baseFps = min(if (entity.FPS > 0) entity.FPS else 30, maxFps)
                
                // 优先级逻辑：High 强制全速，Normal 根据负载动态降频
                val targetFps = when (priority) {
                    SvgaPriority.High -> baseFps
                    SvgaPriority.Normal -> if (systemLoad.value.currentFps < 48) baseFps / 2 else baseFps
                    SvgaPriority.Low -> baseFps / 2
                }.coerceAtMost(maxFps).coerceAtLeast(1)

                val frameDuration = 1_000_000_000L / targetFps
                val currentFrame = if (loops > 0 && (elapsed / (frameDuration * entity.frames)) >= loops.toLong()) -1
                else ((elapsed / frameDuration) % entity.frames).toInt()

                if (currentFrame != lastFrameRef.value) {
                    lastFrameRef.value = currentFrame
                    drawFrameIndex = currentFrame // 触发 Canvas 重绘
                    if (currentFrame == -1) onFinished()
                    else if (currentFrame >= 0) onStep(currentFrame, currentFrame.toDouble() / entity.frames)
                }
            }
        }
        
        SvgaEnvironment.addListener(tickListener)
        onDispose { SvgaEnvironment.removeListener(tickListener) }
    }

    Box(
        modifier = modifier.onSizeChanged { componentSize = it },
        contentAlignment = Alignment.Center
    ) {
        if (loadState == SvgaLoadState.Loading) loading?.invoke()
        if (loadState == SvgaLoadState.Error) failure?.invoke()

        if (videoEntity != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val drawerObj = drawer ?: return@Canvas
                if (drawFrameIndex >= 0) {
                    drawIntoCanvas { canvas ->
                        drawerObj.drawFrame(canvas.nativeCanvas, drawFrameIndex, nativeScaleType)
                    }
                }
            }
        }
    }
}
