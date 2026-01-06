package com.rui.composes.svga

import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

    // 环境绑定
    DisposableEffect(globalTick) {
        if (globalTick == SvgaEnvironment.tickState) {
            SvgaEnvironment.attach()
            onDispose { SvgaEnvironment.detach() }
        } else onDispose {}
    }

    // 资源同步/异步加载
    val initialEntity = remember(model) {
        val key = SVGACache.buildCacheKey(model.toString())
        SVGAActivitiesCache.instance.get(key)
    }
    var videoEntity by remember(model) { mutableStateOf<SVGAVideoEntity?>(initialEntity) }
    var loadState by remember(model) {
        mutableStateOf(if (initialEntity != null) SvgaLoadState.Success else SvgaLoadState.Loading)
    }

    LaunchedEffect(model, componentSize) {
        if (componentSize.width <= 0 || videoEntity != null) return@LaunchedEffect
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

    Box(
        modifier = modifier.onSizeChanged { componentSize = it },
        contentAlignment = Alignment.Center
    ) {
        if (loadState == SvgaLoadState.Loading) loading?.invoke()
        if (loadState == SvgaLoadState.Error) failure?.invoke()

        if (videoEntity != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val entity = videoEntity ?: return@Canvas
                val drawerObj = drawer ?: return@Canvas

                // 核心性能修复：在 DrawScope 内部读取 globalTick.value
                // 这将使动画刷新仅触发绘制阶段，跳过重组阶段
                val elapsed = globalTick.value - startTime
                val baseFps = min(if (entity.FPS > 0) entity.FPS else 30, maxFps)
                val targetFps = when (priority) {
                    SvgaPriority.High -> baseFps
                    SvgaPriority.Normal -> if (systemLoad.value.currentFps < 45) baseFps / 2 else baseFps
                    SvgaPriority.Low -> if (systemLoad.value.currentFps < 40) baseFps / 4 else baseFps / 2
                }.coerceAtLeast(1)

                val frameDuration = 1_000_000_000L / targetFps
                val loopCount = elapsed / (frameDuration * entity.frames)
                val frameIndex = if (loops > 0 && loopCount >= loops.toLong()) -1
                else ((elapsed / frameDuration) % entity.frames).toInt()

                if (frameIndex >= 0) {
                    drawIntoCanvas { canvas ->
                        drawerObj.drawFrame(canvas.nativeCanvas, frameIndex, nativeScaleType)
                    }
                }
            }
        }
    }
}
