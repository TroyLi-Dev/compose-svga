package com.rui.compose.svga.controller

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rui.compose.svga.entity.SvgaVideoEntity
import com.rui.compose.svga.model.SvgaLoadState
import com.rui.compose.svga.model.SvgaPlayState
import com.rui.compose.svga.model.SvgaPriority

/**
 * SVGA 动效状态提升控制器（Controller Pattern）
 *
 * 允许外部以命令式或响应式风格精确驱动动效播放、暂停、跳转、静音与循环模式控制。
 */
@Stable
class SvgaController(
    initialModel: Any? = null,
    initialLoops: Int = 0,
    initialMute: Boolean = false,
    initialPriority: SvgaPriority = SvgaPriority.Normal,
    initialMaxFps: Int = Int.MAX_VALUE
) {
    /** 绑定的动画数据源 */
    var model by mutableStateOf<Any?>(initialModel)

    /** 加载解析成功后的视频实体对象 */
    var videoEntity by mutableStateOf<SvgaVideoEntity?>(null)
        internal set

    /** 当前资源加载状态 */
    var loadState by mutableStateOf(if (initialModel != null) SvgaLoadState.Loading else SvgaLoadState.Idle)
        internal set

    /** 当前播放状态 */
    var playState by mutableStateOf(SvgaPlayState.Idle)
        internal set

    /** 当前播放的帧序号（0-based） */
    var currentFrame by mutableIntStateOf(0)
        internal set

    /** 循环播放次数（0 表示无限循环，>0 表示播放指定次数后停在末帧） */
    var loops by mutableIntStateOf(initialLoops)

    /** 是否静音 */
    var isMuted by mutableStateOf(initialMute)

    /** 渲染优先级策略 */
    var priority by mutableStateOf(initialPriority)

    /** 最大帧率上限限制 */
    var maxFps by mutableIntStateOf(initialMaxFps)

    /** 总帧数 */
    val totalFrames: Int
        get() = videoEntity?.frames ?: 0

    /** 当前播放进度 (0.0f ~ 1.0f) */
    val progress: Float
        get() = if (totalFrames > 0) (currentFrame.toFloat() / totalFrames).coerceIn(0f, 1f) else 0f

    /** 是否正在播放中 */
    val isPlaying: Boolean
        get() = playState == SvgaPlayState.Playing

    /** 是否处于暂停状态 */
    val isPaused: Boolean
        get() = playState == SvgaPlayState.Paused

    /** 是否已播放完毕 */
    val isFinished: Boolean
        get() = playState == SvgaPlayState.Finished

    // 内部控制信标，用于通知驱动层做重置或跳转
    internal var seekFrameTarget by mutableStateOf<Int?>(null)
    internal var commandTrigger by mutableIntStateOf(0)

    /**
     * 开始播放。可传入新的 model 切换资源。
     */
    fun play(newModel: Any? = null) {
        if (newModel != null && newModel != model) {
            model = newModel
            loadState = SvgaLoadState.Loading
            videoEntity = null
            currentFrame = 0
        }
        playState = SvgaPlayState.Playing
        commandTrigger++
    }

    /**
     * 暂停播放，停留在当前帧
     */
    fun pause() {
        if (playState == SvgaPlayState.Playing) {
            playState = SvgaPlayState.Paused
        }
    }

    /**
     * 从暂停状态继续播放
     */
    fun resume() {
        if (playState == SvgaPlayState.Paused) {
            playState = SvgaPlayState.Playing
        }
    }

    /**
     * 重新从第 0 帧开始播放
     */
    fun replay() {
        currentFrame = 0
        seekFrameTarget = 0
        playState = SvgaPlayState.Playing
        commandTrigger++
    }

    /**
     * 停止播放并将画面重置回初始帧
     */
    fun stop() {
        playState = SvgaPlayState.Idle
        currentFrame = 0
        seekFrameTarget = 0
        commandTrigger++
    }

    /**
     * 跳转至指定帧序号
     */
    fun seekTo(frame: Int) {
        val target = if (totalFrames > 0) {
            frame.coerceIn(0, totalFrames - 1)
        } else {
            frame.coerceAtLeast(0)
        }
        currentFrame = target
        seekFrameTarget = target
        commandTrigger++
    }

    /**
     * 按进度比例跳转 (0.0f ~ 1.0f)
     */
    fun seekToProgress(fraction: Float) {
        val maxFrame = (totalFrames - 1).coerceAtLeast(0)
        val targetFrame = (maxFrame * fraction.coerceIn(0f, 1f)).toInt()
        seekTo(targetFrame)
    }
}

/**
 * 创建并记住一个 [SvgaController]
 */
@Composable
fun rememberSvgaController(
    initialModel: Any? = null,
    initialLoops: Int = 0,
    initialMute: Boolean = false,
    initialPriority: SvgaPriority = SvgaPriority.Normal,
    initialMaxFps: Int = Int.MAX_VALUE
): SvgaController {
    return remember {
        SvgaController(
            initialModel = initialModel,
            initialLoops = initialLoops,
            initialMute = initialMute,
            initialPriority = initialPriority,
            initialMaxFps = initialMaxFps
        )
    }
}
