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
import androidx.compose.ui.layout.onSizeChanged
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

// --- 极致性能底层池 ---
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
        if (!oldValue.isRecycled) synchronized(bitmapPool) { if (bitmapPool.size < MAX_BITMAP_POOL_SIZE) bitmapPool.add(oldValue) else oldValue.recycle() }
    }
}

private val GlobalStartTimes = ConcurrentHashMap<Any, Long>()
private val sharedRenderCanvas = android.graphics.Canvas()

enum class SvgaPriority { High, Normal, Low }

@Stable
data class SystemLoad(val currentFps: Int = 60)

val LocalSystemLoad = staticCompositionLocalOf { mutableStateOf(SystemLoad()) }
val LocalSvgaClock = staticCompositionLocalOf<State<Long>> { error("No clock") }

enum class SvgaLoadState { Loading, Success, Error }

/**
 * 极致性能优化版：解决 20 个与 100 个消耗一致的问题
 * 1. 使用 derivedStateOf 实现按需重绘，低优先级组件大幅降低绘制频率。
 * 2. 修复 onPlay, onStep, onFinished 等生命周期回调。
 * 3. 保持 BitmapPool 和 LruCache 以杜绝 GC 和 OOM。
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
    onError: (Exception) -> Unit = {},
    onStep: (frame: Int, percentage: Double) -> Unit = { _, _ -> },
    onFinished: () -> Unit = {},
    loading: @Composable (() -> Unit)? = null,
    failure: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current.applicationContext
    val globalTickState = LocalSvgaClock.current
    val systemLoad by LocalSystemLoad.current

    var videoEntity by remember(model) { mutableStateOf<SVGAVideoEntity?>(null) }
    var loadState by remember(model) { mutableStateOf(SvgaLoadState.Loading) }
    var componentSize by remember { mutableStateOf(IntSize.Zero) }
    
    val startTime = remember(model) { GlobalStartTimes.getOrPut(model) { System.nanoTime() } }
    val drawer = remember(videoEntity, dynamicEntity) {
        videoEntity?.let { SVGACanvasDrawer(it, dynamicEntity ?: SVGADynamicEntity()) }
    }

    // 1. 按需重绘驱动：只有帧号改变时才触发重绘
    val currentFrameIndex by remember(videoEntity, priority, systemLoad, isStop, allowStopOnCriticalLoad) {
        derivedStateOf {
            val entity = videoEntity ?: return@derivedStateOf -1
            val tick = globalTickState.value 
            
            val baseFps = min(if (entity.FPS > 0) entity.FPS else 30, maxFps)
            val isCriticalLoad = allowStopOnCriticalLoad && systemLoad.currentFps < 20
            if (isStop || isCriticalLoad) return@derivedStateOf 0

            val targetFps = when (priority) {
                SvgaPriority.High -> baseFps
                SvgaPriority.Normal -> if (systemLoad.currentFps < 45) baseFps / 2 else baseFps
                SvgaPriority.Low -> if (systemLoad.currentFps < 40) baseFps / 4 else baseFps / 2
            }.coerceAtLeast(1)

            val frameDurationNanos = 1_000_000_000L / targetFps
            val totalElapsedFrames = ((tick - startTime) / frameDurationNanos).toInt()
            val totalFrames = entity.frames
            
            if (loops > 0 && totalElapsedFrames / totalFrames >= loops) totalFrames - 1
            else totalElapsedFrames % totalFrames
        }
    }

    // 2. 播放结束判定
    val isAnimationFinished by remember(videoEntity, loops) {
        derivedStateOf {
            val entity = videoEntity ?: return@derivedStateOf false
            if (loops <= 0) return@derivedStateOf false
            val tick = globalTickState.value
            val baseFps = min(entity.FPS, maxFps)
            val frameDurationNanos = 1_000_000_000L / baseFps
            ((tick - startTime) / (frameDurationNanos * entity.frames)) >= loops
        }
    }

    // --- 回调修复：使用 LaunchedEffect 监听派生状态变化 ---
    LaunchedEffect(videoEntity) {
        if (videoEntity != null) onPlay()
    }

    LaunchedEffect(currentFrameIndex) {
        val entity = videoEntity ?: return@LaunchedEffect
        if (currentFrameIndex >= 0 && !isAnimationFinished) {
            onStep(currentFrameIndex, (currentFrameIndex + 1).toDouble() / entity.frames)
        }
    }

    LaunchedEffect(isAnimationFinished) {
        if (isAnimationFinished) onFinished()
    }

    LaunchedEffect(model, componentSize) {
        if (componentSize.width <= 0) return@LaunchedEffect
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
        if (model is String) {
            if (model.startsWith("http")) parser.decodeFromURL(URL(model), callback)
            else parser.decodeFromAssets(model, callback)
        }
    }

    Box(modifier = modifier.onSizeChanged { componentSize = it }, contentAlignment = Alignment.Center) {
        if (loadState == SvgaLoadState.Loading) loading?.invoke()
        if (loadState == SvgaLoadState.Error) failure?.invoke()

        Canvas(modifier = Modifier.fillMaxSize()) {
            val frameIndex = currentFrameIndex 
            val entity = videoEntity ?: return@Canvas
            val canvasDrawer = drawer ?: return@Canvas
            val w = componentSize.width
            val h = componentSize.height
            if (frameIndex < 0 || w <= 0 || h <= 0) return@Canvas

            // 零 GC Key
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
