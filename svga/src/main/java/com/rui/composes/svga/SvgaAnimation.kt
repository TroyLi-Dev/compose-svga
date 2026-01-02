package com.rui.composes.svga

import android.graphics.Bitmap
import android.util.LruCache
import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.opensource.svgaplayer.SVGADynamicEntity
import com.opensource.svgaplayer.SVGAParser
import com.opensource.svgaplayer.SVGAVideoEntity
import com.opensource.svgaplayer.drawer.SVGACanvasDrawer
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import java.net.URL
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.random.Random

// --- 基础性能组件 ---
private val bitmapPool = Collections.synchronizedList(mutableListOf<Bitmap>())
private const val MAX_BITMAP_POOL_SIZE = 80

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
        if (!oldValue.isRecycled) {
            synchronized(bitmapPool) {
                if (bitmapPool.size < MAX_BITMAP_POOL_SIZE) bitmapPool.add(oldValue)
                else oldValue.recycle()
            }
        }
    }
}

private val GlobalStartTimes = ConcurrentHashMap<Any, Long>()
private val sharedRenderCanvas = android.graphics.Canvas()

enum class SvgaPriority { High, Normal, Low }

@Stable
data class SystemLoad(val currentFps: Int = 60)

val LocalSystemLoad = staticCompositionLocalOf { mutableStateOf(SystemLoad()) }
val LocalSvgaClock = staticCompositionLocalOf<State<Long>> { error("No clock") }

/**
 * 动画状态枚举
 */
enum class SvgaLoadState {
    Loading, Success, Error
}

/**
 * 极致性能、使用友好的 Compose SVGA 组件
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
    // 生命周期回调
    onPlay: () -> Unit = {},
    onSuccess: (SVGAVideoEntity) -> Unit = {},
    onError: (Exception) -> Unit = {},
    onStep: (frame: Int, percentage: Double) -> Unit = { _, _ -> },
    onFinished: () -> Unit = {},
    // 占位 UI
    loading: @Composable (() -> Unit)? = null,
    failure: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current.applicationContext
    val globalTickState = LocalSvgaClock.current
    val systemLoad by LocalSystemLoad.current

    var videoEntity by remember(model) { mutableStateOf<SVGAVideoEntity?>(null) }
    var loadState by remember(model) { mutableStateOf(SvgaLoadState.Loading) }
    var componentSize by remember { mutableStateOf(IntSize.Zero) }
    var isVisible by remember { mutableStateOf(true) }
    
    var playTriggered by remember(model) { mutableStateOf(false) }
    var finishedTriggered by remember(model) { mutableStateOf(false) }

    val startTime = remember(model) { GlobalStartTimes.getOrPut(model) { System.nanoTime() } }
    val drawer = remember(videoEntity, dynamicEntity) {
        videoEntity?.let { SVGACanvasDrawer(it, dynamicEntity ?: SVGADynamicEntity()) }
    }

    LaunchedEffect(model, componentSize) {
        if (componentSize.width <= 0 || componentSize.height <= 0) return@LaunchedEffect
        delay(Random.nextLong(10, 150))
        yield()

        val parser = SVGAParser(context)
        parser.setFrameSize(componentSize.width, componentSize.height)

        val callback = object : SVGAParser.ParseCompletion {
            override fun onComplete(videoItem: SVGAVideoEntity) {
                videoEntity = videoItem
                loadState = SvgaLoadState.Success
                onSuccess(videoItem)
            }
            override fun onError(e: Exception, alias: String) {
                loadState = SvgaLoadState.Error
                onError(e)
            }
        }

        val url = model as? String ?: return@LaunchedEffect
        if (url.startsWith("http")) parser.decodeFromURL(URL(url), callback)
        else parser.decodeFromAssets(url, callback)
    }

    Box(
        modifier = modifier
            .onSizeChanged { componentSize = it }
            .onGloballyPositioned { coords ->
                val y = coords.positionInWindow().y
                isVisible = y > -1500 && y < 5000 
            },
        contentAlignment = Alignment.Center
    ) {
        when (loadState) {
            SvgaLoadState.Loading -> loading?.invoke()
            SvgaLoadState.Error -> failure?.invoke()
            SvgaLoadState.Success -> {} 
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!isVisible || loadState != SvgaLoadState.Success) return@Canvas

            val tick = globalTickState.value
            val entity = videoEntity ?: return@Canvas
            val canvasDrawer = drawer ?: return@Canvas
            val w = componentSize.width
            val h = componentSize.height
            if (w <= 0 || h <= 0) return@Canvas

            if (!playTriggered) {
                playTriggered = true
                onPlay()
            }

            val baseFps = min(if (entity.FPS > 0) entity.FPS else 30, maxFps)
            val isCriticalLoad = allowStopOnCriticalLoad && systemLoad.currentFps < 20
            val targetFps = when (priority) {
                SvgaPriority.High -> baseFps
                SvgaPriority.Normal -> if (systemLoad.currentFps < 45) baseFps / 2 else baseFps
                SvgaPriority.Low -> if (systemLoad.currentFps < 40) baseFps / 4 else baseFps / 2
            }.coerceAtLeast(1)

            val frameDurationNanos = 1_000_000_000L / targetFps
            val elapsedNanos = tick - startTime
            val totalFrames = entity.frames

            val totalElapsedFrames = (elapsedNanos / frameDurationNanos).toInt()
            val currentIteration = totalElapsedFrames / totalFrames
            val frameIndex: Int

            if (isStop || isCriticalLoad) {
                frameIndex = 0
            } else {
                if (loops > 0 && currentIteration >= loops) {
                    frameIndex = totalFrames - 1
                    if (!finishedTriggered) {
                        finishedTriggered = true
                        onFinished()
                    }
                } else {
                    frameIndex = (totalElapsedFrames % totalFrames)
                    if (priority != SvgaPriority.Low) {
                        onStep(frameIndex, (frameIndex + 1).toDouble() / totalFrames)
                    }
                }
            }

            val cacheKey = ((model.hashCode().toLong() and 0xFFFFFFFFL) shl 32) or
                           ((frameIndex and 0xFFF).toLong() shl 20) or
                           ((w and 0x3FF).toLong() shl 10) or
                           (h and 0x3FF).toLong()

            var frameBitmap = frameLruCache.get(cacheKey)

            if (frameBitmap == null || frameBitmap.isRecycled) {
                frameBitmap = obtainBitmap(w, h)
                synchronized(sharedRenderCanvas) {
                    sharedRenderCanvas.setBitmap(frameBitmap)
                    sharedRenderCanvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR)
                    val svgaScaleType = when (contentScale) {
                        ContentScale.Crop -> ImageView.ScaleType.CENTER_CROP
                        ContentScale.FillBounds -> ImageView.ScaleType.FIT_XY
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

    DisposableEffect(model) {
        onDispose { videoEntity?.clearCallback() }
    }
}
