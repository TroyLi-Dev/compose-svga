package com.opensource.svgaplayer.compose

import android.graphics.Bitmap
import android.util.LruCache
import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
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
import kotlin.math.min
import kotlin.random.Random

// --- 性能优化：Bitmap 复用池 (杜绝 GC) ---
private val bitmapPool = Collections.synchronizedList(mutableListOf<Bitmap>())

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

// 渲染帧缓存：淘汰时自动回收到池中
private val frameLruCache = object : LruCache<Long, Bitmap>(40 * 1024 * 1024) { 
    override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
    override fun entryRemoved(evicted: Boolean, key: Long, oldValue: Bitmap, newValue: Bitmap?) {
        if (!oldValue.isRecycled) synchronized(bitmapPool) { bitmapPool.add(oldValue) }
    }
}

private val sharedRenderCanvas = android.graphics.Canvas()

enum class SvgaPriority { High, Normal, Low }

@Stable
data class SystemLoad(val currentFps: Int = 60)

val LocalSystemLoad = staticCompositionLocalOf { mutableStateOf(SystemLoad()) }
val LocalSvgaClock = staticCompositionLocalOf<State<Long>> { error("No clock") }

/**
 * 极致性能 Compose SVGA 组件
 * 1. 自动可见性暂停：不可见时完全不产生计算和重绘压力
 * 2. Bitmap 复用池：彻底解决频繁 GC 导致的卡顿
 * 3. 负载均衡：解决 100+ 瞬间并发导致的 ANR
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
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current.applicationContext
    val globalTickState = LocalSvgaClock.current
    val systemLoad by LocalSystemLoad.current
    
    var videoEntity by remember(model) { mutableStateOf<SVGAVideoEntity?>(null) }
    var componentSize by remember { mutableStateOf(IntSize.Zero) }
    val startTime = remember(videoEntity) { System.nanoTime() }
    
    val drawer = remember(videoEntity, dynamicEntity) {
        videoEntity?.let { SVGACanvasDrawer(it, dynamicEntity ?: SVGADynamicEntity()) }
    }

    // 暂停标记：用于判定是否在屏幕外
    var isVisible by remember { mutableStateOf(true) }

    LaunchedEffect(model, componentSize) {
        if (componentSize.width <= 0) return@LaunchedEffect
        delay(Random.nextLong(10, 150))
        yield()
        val parser = SVGAParser(context) 
        parser.setFrameSize(componentSize.width, componentSize.height)
        val callback = object : SVGAParser.ParseCompletion {
            override fun onComplete(videoItem: SVGAVideoEntity) { videoEntity = videoItem }
            override fun onError(e: Exception, alias: String) {}
        }
        val url = model as? String ?: return@LaunchedEffect
        if (url.startsWith("http")) parser.decodeFromURL(URL(url), callback)
        else parser.decodeFromAssets(url, callback)
    }

    Box(modifier = modifier
        .onSizeChanged { componentSize = it }
        .onGloballyPositioned { coords ->
            // --- 暂停逻辑：检测是否在可视区域内 ---
            val y = coords.positionInWindow().y
            // 简单判定：如果在屏幕上下各 500dp 范围内则运行
            isVisible = y > -1000 && y < 4000
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // --- 极其关键：可见性静默 ---
            // 如果不可见，不读取 tick 状态。这会让 Compose 自动取消该组件对时钟的订阅。
            if (!isVisible) return@Canvas
            
            val tick = globalTickState.value // 获取时钟信号
            
            val entity = videoEntity ?: return@Canvas
            val canvasDrawer = drawer ?: return@Canvas
            val w = componentSize.width
            val h = componentSize.height
            if (w <= 0 || h <= 0) return@Canvas

            // 1. 降级渲染逻辑
            val baseFps = min(if (entity.FPS > 0) entity.FPS else 30, maxFps)
            val currentLoadFps = systemLoad.currentFps
            val isCriticalLoad = allowStopOnCriticalLoad && currentLoadFps < 20

            val targetFps = when (priority) {
                SvgaPriority.High -> baseFps
                SvgaPriority.Normal -> if (currentLoadFps < 45) baseFps / 2 else baseFps
                SvgaPriority.Low -> when {
                    currentLoadFps < 40 -> baseFps / 4
                    currentLoadFps < 50 -> baseFps / 3
                    else -> baseFps / 2
                }
            }.coerceAtLeast(1)

            val frameDurationNanos = 1_000_000_000L / targetFps
            val elapsedNanos = tick - startTime
            val totalFrames = entity.frames
            
            val frameIndex = if (isStop || isCriticalLoad) 0 else {
                val current = (elapsedNanos / frameDurationNanos).toInt()
                if (loops > 0 && current >= loops * totalFrames) totalFrames - 1
                else current % totalFrames
            }

            // 2. 零 GC Key
            val cacheKey = (model.hashCode().toLong() shl 32) or 
                           ((frameIndex and 0xFFF).toLong() shl 20) or 
                           ((w and 0x3FF).toLong() shl 10) or 
                           (h and 0x3FF).toLong()

            var frameBitmap = frameLruCache.get(cacheKey)
            
            if (frameBitmap == null || frameBitmap.isRecycled) {
                frameBitmap = obtainBitmap(w, h)
                synchronized(sharedRenderCanvas) {
                    sharedRenderCanvas.setBitmap(frameBitmap)
                    sharedRenderCanvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                    
                    val svgaScaleType = when(contentScale) {
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
