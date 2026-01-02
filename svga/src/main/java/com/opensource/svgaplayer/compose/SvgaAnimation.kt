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
import kotlin.random.Random

// 使用 LruCache 管理帧缓存，防止缓存过多导致 OOM
private val frameLruCache = object : LruCache<String, Bitmap>(30 * 1024 * 1024) { // 30MB 帧缓存
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

@Composable
fun SvgaAnimation(
    model: Any,
    modifier: Modifier = Modifier,
    loops: Int = 0,
    dynamicEntity: SVGADynamicEntity? = null,
    isStop: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current.applicationContext
    var videoEntity by remember(model) { mutableStateOf<SVGAVideoEntity?>(null) }
    var currentFrame by remember { mutableIntStateOf(0) }
    var componentSize by remember { mutableStateOf(IntSize.Zero) }
    
    val drawer = remember(videoEntity, dynamicEntity) {
        videoEntity?.let { SVGACanvasDrawer(it, dynamicEntity ?: SVGADynamicEntity()) }
    }

    LaunchedEffect(model, componentSize) {
        if (componentSize.width <= 0) return@LaunchedEffect
        delay(Random.nextLong(5, 100))
        yield()

        val parser = SVGAParser(context) 
        parser.setFrameSize(componentSize.width, componentSize.height)
        
        val callback = object : SVGAParser.ParseCompletion {
            override fun onComplete(videoItem: SVGAVideoEntity) { videoEntity = videoItem }
            override fun onError(e: Exception, alias: String) {}
        }
        
        if (model is String) {
            if (model.startsWith("http")) parser.decodeFromURL(URL(model), callback)
            else parser.decodeFromAssets(model, callback)
        }
    }

    LaunchedEffect(videoEntity, isStop) {
        val entity = videoEntity ?: return@LaunchedEffect
        if (isStop) return@LaunchedEffect
        val startTime = System.nanoTime()
        val totalFrames = entity.frames
        val fps = if (entity.FPS > 0) entity.FPS else 30
        val frameDurationNanos = 1_000_000_000L / fps
        
        while (true) {
            val now = withFrameNanos { it }
            currentFrame = (((now - startTime) / frameDurationNanos) % totalFrames).toInt()
            if (loops > 0 && (now - startTime) / frameDurationNanos >= loops * totalFrames) {
                currentFrame = totalFrames - 1; break
            }
        }
    }

    Box(modifier = modifier.onSizeChanged { componentSize = it }) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val entity = videoEntity ?: return@Canvas
            val canvasDrawer = drawer ?: return@Canvas
            if (componentSize.width <= 0) return@Canvas

            // 修复 Key：必须包含 URL、帧号和分辨率
            val cacheKey = "${model.hashCode()}_${currentFrame}_${componentSize.width}_${componentSize.height}"
            var frameBitmap = frameLruCache.get(cacheKey)
            
            if (frameBitmap == null || frameBitmap.isRecycled) {
                frameBitmap = Bitmap.createBitmap(componentSize.width, componentSize.height, Bitmap.Config.ARGB_8888)
                val tempCanvas = android.graphics.Canvas(frameBitmap!!)
                
                // 修复：移除手动 tempCanvas.scale，利用 SVGA 内部 performScaleType 实现精准缩放
                val svgaScaleType = when(contentScale) {
                    ContentScale.Crop -> ImageView.ScaleType.CENTER_CROP
                    ContentScale.FillBounds -> ImageView.ScaleType.FIT_XY
                    ContentScale.Inside -> ImageView.ScaleType.CENTER_INSIDE
                    else -> ImageView.ScaleType.FIT_CENTER
                }
                
                // 绘制到 Bitmap
                canvasDrawer.drawFrame(tempCanvas, currentFrame, svgaScaleType)
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
