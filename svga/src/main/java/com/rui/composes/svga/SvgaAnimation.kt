package com.rui.composes.svga

import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import com.opensource.svgaplayer.SVGADynamicEntity
import com.opensource.svgaplayer.SVGAParser
import com.opensource.svgaplayer.SVGAVideoEntity
import com.opensource.svgaplayer.drawer.SVGACanvasDrawer
import com.rui.composes.svga.core.LocalSvgaClock
import com.rui.composes.svga.core.LocalSystemLoad
import com.rui.composes.svga.core.SvgaEnvironment
import com.rui.composes.svga.model.SvgaLoadState
import com.rui.composes.svga.model.SvgaPriority
import com.rui.composes.svga.render.SvgaRenderEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.random.Random

private val GlobalStartTimes = ConcurrentHashMap<Any, Long>()

/**
 * 极致性能优化版 SvgaAnimation
 * 采用多目录结构组织：model(模型), core(时钟与环境), render(渲染缓存)
 */
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

    // 1. 环境绑定 (自动开启/关闭时钟)
    DisposableEffect(globalTick) {
        if (globalTick == SvgaEnvironment.tickState) {
            SvgaEnvironment.attach()
            onDispose { SvgaEnvironment.detach() }
        } else onDispose {}
    }

    // 2. 资源加载逻辑
    val initialEntity = remember(model) {
        val key = com.opensource.svgaplayer.SVGACache.buildCacheKey(model.toString())
        SVGAActivitiesCache.instance.get(key)
    }
    var videoEntity by remember(model) { mutableStateOf<SVGAVideoEntity?>(initialEntity) }
    var loadState by remember(model) {
        mutableStateOf(if (initialEntity != null) SvgaLoadState.Success else SvgaLoadState.Loading)
    }

    LaunchedEffect(model, componentSize) {
        if (componentSize.width <= 0 || videoEntity != null) return@LaunchedEffect
        delay(Random.nextLong(10, 150)); yield()
        SVGAParser(context).apply {
            setFrameSize(componentSize.width, componentSize.height)
            decodeFromURL(java.net.URL(model.toString()), object : SVGAParser.ParseCompletion {
                override fun onComplete(videoItem: SVGAVideoEntity) {
                    videoEntity = videoItem
                    loadState = SvgaLoadState.Success
                    onSuccess(videoItem); onPlay()
                }

                override fun onError(e: Exception, alias: String) {
                    loadState = SvgaLoadState.Error; onError(e)
                }
            })
        }
    }

    // 3. 动画状态驱动
    val startTime = remember(model) { GlobalStartTimes.getOrPut(model) { System.nanoTime() } }
    val drawer = remember(videoEntity, dynamicEntity) {
        videoEntity?.let { SVGACanvasDrawer(it, dynamicEntity ?: SVGADynamicEntity()) }
    }

    val frameIndex by remember(videoEntity, priority, isStop, allowStopOnCriticalLoad) {
        derivedStateOf {
            val entity = videoEntity ?: return@derivedStateOf -1
            if (isStop || (allowStopOnCriticalLoad && systemLoad.value.currentFps < 20)) return@derivedStateOf 0

            val baseFps = min(if (entity.FPS > 0) entity.FPS else 30, maxFps)
            val targetFps = when (priority) {
                SvgaPriority.High -> baseFps
                SvgaPriority.Normal -> if (systemLoad.value.currentFps < 45) baseFps / 2 else baseFps
                SvgaPriority.Low -> if (systemLoad.value.currentFps < 40) baseFps / 4 else baseFps / 2
            }.coerceAtLeast(1)

            val frameDuration = 1_000_000_000L / targetFps
            val elapsed = globalTick.value - startTime
            val loopCount = elapsed / (frameDuration * entity.frames)

            if (loops > 0 && loopCount >= loops.toLong()) -1
            else ((elapsed / frameDuration) % entity.frames).toInt()
        }
    }

    // 4. 回调通知
    LaunchedEffect(frameIndex) {
        val entity = videoEntity ?: return@LaunchedEffect
        if (frameIndex == -1) onFinished()
        else if (frameIndex >= 0) onStep(frameIndex, frameIndex.toDouble() / entity.frames)
    }

    // 5. 渲染区域
    Box(
        modifier = modifier.onSizeChanged { componentSize = it },
        contentAlignment = Alignment.Center
    ) {
        if (loadState == SvgaLoadState.Loading) loading?.invoke()
        if (loadState == SvgaLoadState.Error) failure?.invoke()

        if (frameIndex >= 0 && videoEntity != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val drawerObj = drawer ?: return@Canvas
                val normW = (componentSize.width + 7) and -8
                val normH = (componentSize.height + 7) and -8
                if (normW <= 0) return@Canvas

                val cacheKey = ((model.hashCode().toLong() and 0xFFFFFFFFL) shl 32) or
                        ((frameIndex.toLong() and 0xFFFL) shl 20) or
                        ((normW.toLong() and 0x3FFL) shl 10) or (normH.toLong() and 0x3FFL)

                val bitmap = SvgaRenderEngine.frameCache.get(cacheKey)?.takeIf { !it.isRecycled }
                    ?: SvgaRenderEngine.renderToCache(
                        cacheKey, normW, normH, frameIndex, drawerObj,
                        when (contentScale) {
                            ContentScale.FillBounds -> ImageView.ScaleType.FIT_XY
                            ContentScale.Crop -> ImageView.ScaleType.CENTER_CROP
                            ContentScale.Inside -> ImageView.ScaleType.CENTER_INSIDE
                            else -> ImageView.ScaleType.FIT_CENTER
                        }
                    )

                drawIntoCanvas { it.nativeCanvas.drawBitmap(bitmap, 0f, 0f, null) }
            }
        }
    }
}
