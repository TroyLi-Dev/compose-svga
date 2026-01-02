package com.rui.composes.svga

import android.graphics.Bitmap
import android.util.LruCache
import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.opensource.svgaplayer.SVGAActivitiesCache
import com.opensource.svgaplayer.SVGADynamicEntity
import com.opensource.svgaplayer.SVGAParser
import com.opensource.svgaplayer.SVGAVideoEntity
import com.opensource.svgaplayer.drawer.SVGACanvasDrawer
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.random.Random

// --- 极致性能渲染引擎底层池 ---
private val bitmapPool = Collections.synchronizedList(mutableListOf<Bitmap>())
private const val MAX_BITMAP_POOL_SIZE = 60

private fun obtainBitmap(width: Int, height: Int): Bitmap {
    synchronized(bitmapPool) {
        val iterator = bitmapPool.iterator()
        while (iterator.hasNext()) {
            val bmp = iterator.next()
            if (bmp.width == width && bmp.height == height && !bmp.isRecycled) {
                iterator.remove()
                bmp.eraseColor(0)
                return bmp
            }
        }
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
}

private val frameLruCache = object : LruCache<Long, Bitmap>(40 * 1024 * 1024) {
    override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
    override fun entryRemoved(evicted: Boolean, key: Long, oldValue: Bitmap, newValue: Bitmap?) {
        if (!oldValue.isRecycled) synchronized(bitmapPool) {
            if (bitmapPool.size < MAX_BITMAP_POOL_SIZE) bitmapPool.add(oldValue)
            else oldValue.recycle()
        }
    }
}

private val RenderJobMap = ConcurrentHashMap<Long, kotlinx.coroutines.Deferred<Bitmap>>()
private val GlobalStartTimes = ConcurrentHashMap<Any, Long>()
private val sharedRenderCanvas = android.graphics.Canvas()

enum class SvgaPriority { High, Normal, Low }

@Stable
data class SystemLoad(val currentFps: Int = 60)

val LocalSystemLoad = staticCompositionLocalOf { mutableStateOf(SystemLoad()) }
val LocalSvgaClock = staticCompositionLocalOf<State<Long>> { error("No clock") }

enum class SvgaLoadState { Loading, Success, Error }

/**
 * 极致性能优化版 SvgaAnimation
 * 修复变形问题：正确处理 ContentScale 映射
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
    val density = LocalDensity.current
    val globalTickState = LocalSvgaClock.current
    val systemLoadState = LocalSystemLoad.current

    val initialEntity = remember(model) {
        val key = com.opensource.svgaplayer.SVGACache.buildCacheKey(model.toString())
        SVGAActivitiesCache.instance.get(key)
    }

    var videoEntity by remember(model) { mutableStateOf<SVGAVideoEntity?>(initialEntity) }
    var loadState by remember(model) {
        mutableStateOf(if (initialEntity != null) SvgaLoadState.Success else SvgaLoadState.Loading)
    }
    var componentSize by remember { mutableStateOf(IntSize.Zero) }

    val startTime = remember(model) { GlobalStartTimes.getOrPut(model) { System.nanoTime() } }
    val drawer = remember(videoEntity, dynamicEntity) {
        videoEntity?.let { SVGACanvasDrawer(it, dynamicEntity ?: SVGADynamicEntity()) }
    }

    val currentFrameIndex by remember(videoEntity, priority, isStop, allowStopOnCriticalLoad) {
        derivedStateOf {
            val entity = videoEntity ?: return@derivedStateOf -1
            val systemLoad = systemLoadState.value
            val baseFps = min(if (entity.FPS > 0) entity.FPS else 30, maxFps)

            if (isStop || (allowStopOnCriticalLoad && systemLoad.currentFps < 20)) return@derivedStateOf 0

            val targetFps = when (priority) {
                SvgaPriority.High -> baseFps
                SvgaPriority.Normal -> if (systemLoad.currentFps < 45) baseFps / 2 else baseFps
                SvgaPriority.Low -> if (systemLoad.currentFps < 40) baseFps / 4 else baseFps / 2
            }.coerceAtLeast(1)

            val frameDurationNanos = 1_000_000_000L / targetFps
            val elapsedNanos = globalTickState.value - startTime
            if (loops > 0 && (elapsedNanos / (frameDurationNanos * entity.frames)) >= loops.toLong()) -1
            else ((elapsedNanos / frameDurationNanos) % entity.frames).toInt()
        }
    }

    LaunchedEffect(model, componentSize) {
        if (componentSize.width <= 0 || videoEntity != null) return@LaunchedEffect
        delay(Random.nextLong(10, 150))
        yield()
        val parser = SVGAParser(context)
        parser.setFrameSize(componentSize.width, componentSize.height)
        parser.decodeFromURL(java.net.URL(model.toString()), object : SVGAParser.ParseCompletion {
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

    Box(
        modifier = modifier.onSizeChanged { componentSize = it },
        contentAlignment = Alignment.Center
    ) {
        if (loadState == SvgaLoadState.Loading) loading?.invoke()
        if (loadState == SvgaLoadState.Error) failure?.invoke()

        Canvas(modifier = Modifier.fillMaxSize()) {
            val frameIndex = currentFrameIndex
            val entity = videoEntity ?: return@Canvas
            val canvasDrawer = drawer ?: return@Canvas

            val normW = (componentSize.width + 7) and -8
            val normH = (componentSize.height + 7) and -8

            if (frameIndex < 0 || normW <= 0) return@Canvas

            val cacheKey = ((model.hashCode().toLong() and 0xFFFFFFFFL) shl 32) or
                    ((frameIndex.toLong() and 0xFFFL) shl 20) or
                    ((normW.toLong() and 0x3FFL) shl 10) or
                    (normH.toLong() and 0x3FFL)

            var frameBitmap = frameLruCache.get(cacheKey)

            if (frameBitmap == null || frameBitmap.isRecycled) {
                frameBitmap = obtainBitmap(normW, normH)
                synchronized(sharedRenderCanvas) {
                    sharedRenderCanvas.setBitmap(frameBitmap)
                    sharedRenderCanvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR)

                    // 修复：将 Compose 的 ContentScale 映射到 SVGA 原生的 ScaleType
                    val svgaScaleType = when (contentScale) {
                        ContentScale.FillBounds -> ImageView.ScaleType.FIT_XY
                        ContentScale.Crop -> ImageView.ScaleType.CENTER_CROP
                        ContentScale.Inside -> ImageView.ScaleType.CENTER_INSIDE
                        ContentScale.Fit -> ImageView.ScaleType.FIT_CENTER
                        else -> ImageView.ScaleType.FIT_CENTER
                    }

                    canvasDrawer.drawFrame(sharedRenderCanvas, frameIndex, svgaScaleType)
                    sharedRenderCanvas.setBitmap(null)
                }
                frameLruCache.put(cacheKey, frameBitmap)
            }

            drawIntoCanvas { composeCanvas ->
                composeCanvas.nativeCanvas.drawBitmap(frameBitmap!!, 0f, 0f, null)
            }
        }
    }
}
