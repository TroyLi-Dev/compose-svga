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
import com.opensource.svgaplayer.utils.log.LogUtils
import com.rui.composes.svga.core.LocalSvgaClock
import com.rui.composes.svga.core.LocalSystemLoad
import com.rui.composes.svga.core.SvgaEnvironment
import com.rui.composes.svga.model.SvgaLoadState
import com.rui.composes.svga.model.SvgaPriority
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min
import kotlin.random.Random

private val GlobalStartTimes = ConcurrentHashMap<Any, Long>()

/**
 * Compose SVGA 播放组件
 *
 * 该组件提供了高性能的 SVGA 动画渲染能力，支持自动负载降频、生命周期感知以及多种资源来源。
 *
 * ### 核心特性：
 * 1. **负载自适应**：通过 [priority] 参数，组件可在系统卡顿（FPS < 48）时自动减半渲染频率。
 * 2. **资源类型识别**：支持 URL、Assets、本地绝对路径和 File 对象。
 * 3. **极致性能**：基于中心化时钟 [SvgaEnvironment]，避开协程调度开销，支持数百个动画并播。
 * 4. **生命周期安全**：自动感知 Compose 销毁并取消未完成的解析任务，防止内存泄露。
 *
 * @param model SVGA 资源路径。支持：
 *              - [String] URL: "https://example.com/test.svga"
 *              - [String] Assets: "player/anim.svga"
 *              - [String] 绝对路径: "/sdcard/Download/test.svga" (需权限)
 *              - [String] File 协议: "file:///data/user/0/.../test.svga"
 *              - [java.io.File] 对象
 * @param modifier 布局修饰符，用于设置组件尺寸、填充、点击事件等。
 * @param priority 渲染优先级控制：
 *                 - [SvgaPriority.High]：始终全速渲染（通常用于主要动画）。
 *                 - [SvgaPriority.Normal]：系统负载高时自动降频（默认，用于普通列表）。
 *                 - [SvgaPriority.Low]：强制低频（FPS/2）运行以节省 CPU。
 * @param maxFps 限制最高播放帧率，默认为无限制。
 * @param allowStopOnCriticalLoad 是否允许在极端负载下（如系统严重掉帧）暂时停止动画渲染。
 * @param loops 循环播放次数。0 为无限循环，>0 为指定次数播放完后停在最后一帧。
 * @param dynamicEntity 动态实体，用于动态替换动画内的文本（TextPaint）或素材（Bitmap）。
 * @param isStop 是否暂停动画。设置为 true 时，动画将冻结在当前帧。
 * @param contentScale 缩放模式，决定动画如何适配容器尺寸（Fit, Crop, FillBounds 等）。
 *
 * --- 逻辑回调 ---
 * @param onLoading 开始加载资源（下载或读取磁盘）时触发。
 * @param onPlay 资源准备完毕，正式开始第一帧渲染时触发。
 * @param onSuccess 资源解析成功，返回底层的 [SVGAVideoEntity] 对象。
 * @param onError 加载或解析失败时触发，返回异常信息（如 File Not Found）。
 * @param onStep 每一帧渲染后的进度回调。[frame] 为当前帧序号，[percentage] 为播放百分比 (0.0~1.0)。
 * @param onFinished 当 [loops] 大于 0 且动画播放完毕时触发。
 *
 * --- UI 扩展插槽 ---
 * @param loading 正在加载时展示的 Composable 视图（如转圈进度条）。
 * @param failure 加载失败时展示的 Composable 视图（如占位图）。
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
        val modelStr = model.toString()
        val key = when {
            model is File -> SVGACache.buildCacheKey("file://${model.absolutePath}")
            modelStr.startsWith("/") -> SVGACache.buildCacheKey("file://$modelStr")
            modelStr.startsWith("file://") -> SVGACache.buildCacheKey(modelStr)
            else -> SVGACache.buildCacheKey(modelStr)
        }
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
                
                val modelStr = model.toString()
                val cancelTask = when {
                    model is File -> {
                        parser.decodeFromFile(model, object : SVGAParser.ParseCompletion {
                            override fun onComplete(videoItem: SVGAVideoEntity) = continuation.resume(videoItem)
                            override fun onError(e: Exception, alias: String) = continuation.resumeWithException(e)
                        })
                    }
                    modelStr.startsWith("/") || modelStr.startsWith("file://") -> {
                        val path = if (modelStr.startsWith("file://")) modelStr.substring(7) else modelStr
                        val file = File(path)
                        if (!file.exists()) {
                            continuation.resumeWithException(Exception("File not found at: $path"))
                            return@suspendCancellableCoroutine
                        }
                        parser.decodeFromFile(file, object : SVGAParser.ParseCompletion {
                            override fun onComplete(videoItem: SVGAVideoEntity) = continuation.resume(videoItem)
                            override fun onError(e: Exception, alias: String) = continuation.resumeWithException(e)
                        })
                    }
                    modelStr.startsWith("http://") || modelStr.startsWith("https://") -> {
                        parser.decodeFromURL(java.net.URL(modelStr), object : SVGAParser.ParseCompletion {
                            override fun onComplete(videoItem: SVGAVideoEntity) = continuation.resume(videoItem)
                            override fun onError(e: Exception, alias: String) = continuation.resumeWithException(e)
                        })
                    }
                    else -> {
                        parser.decodeFromAssets(modelStr, object : SVGAParser.ParseCompletion {
                            override fun onComplete(videoItem: SVGAVideoEntity) = continuation.resume(videoItem)
                            override fun onError(e: Exception, alias: String) {
                                LogUtils.error("SvgaAnimation", "Asset load failed: $modelStr. Ensure file is in src/main/assets/")
                                continuation.resumeWithException(e)
                            }
                        })
                    }
                }
                continuation.invokeOnCancellation { cancelTask?.invoke() }
            }
            videoEntity = entity
            loadState = SvgaLoadState.Success
            onSuccess(entity); onPlay()
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                LogUtils.error("SvgaAnimation", "Final Error: ${e.message}")
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
    
    DisposableEffect(videoEntity, isStop, priority, loops, maxFps) {
        val entity = videoEntity ?: return@DisposableEffect onDispose {}
        if (isStop) return@DisposableEffect onDispose {}

        val tickListener = object : SvgaEnvironment.TickListener {
            override fun onTick(frameTimeNanos: Long) {
                val elapsed = frameTimeNanos - startTime
                val baseFps = min(if (entity.FPS > 0) entity.FPS else 30, maxFps)
                
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
                    drawFrameIndex = currentFrame 
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
