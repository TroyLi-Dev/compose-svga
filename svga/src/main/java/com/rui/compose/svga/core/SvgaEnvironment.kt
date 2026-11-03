package com.rui.compose.svga.core

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.rui.compose.svga.model.SystemLoad
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 全局系统负载状态 CompositionLocal（供调试或性能仪表盘读取）
 */
val LocalSystemLoad =
    staticCompositionLocalOf<MutableState<SystemLoad>> { SvgaEnvironment.loadState }

/**
 * 全局统一中心化时钟 CompositionLocal
 */
val LocalSvgaClock = staticCompositionLocalOf<State<Long>> { SvgaEnvironment.tickState }

/**
 * 中心化高频刷新引擎（基于 Choreographer）
 *
 * 核心优化特性：
 * 1. 统一驱动所有 Compose SVGA 组件，避免每个动画起独立协程/定时器产生线程上下文切换开销。
 * 2. 引用计数与监听器双重探测：当没有任何可见或活跃动画时，立即注销 FrameCallback，CPU 占用彻底归零 (0% CPU)。
 */
internal object SvgaEnvironment {
    val tickState = mutableLongStateOf(System.nanoTime())
    val loadState = mutableStateOf(SystemLoad())

    private val listeners = CopyOnWriteArraySet<TickListener>()

    @Volatile
    private var isChoreographerScheduled = false

    interface TickListener {
        fun onTick(frameTimeNanos: Long)
    }

    private var lastFpsTime = 0L
    private var frameCount = 0

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            isChoreographerScheduled = false
            if (listeners.isEmpty()) {
                return // 无活跃监听，彻底休眠
            }

            tickState.longValue = frameTimeNanos

            for (listener in listeners) {
                try {
                    listener.onTick(frameTimeNanos)
                } catch (t: Throwable) {
                    com.rui.compose.svga.utils.log.LogUtils.error("SvgaEnvironment", "Error in tick listener", t)
                }
            }

            frameCount++
            if (lastFpsTime == 0L || frameTimeNanos - lastFpsTime > 1_500_000_000L) {
                // 如果采样间隔过大（切到后台再切回），重置采样时间戳，避免错误计算出超低 FPS
                lastFpsTime = frameTimeNanos
                frameCount = 0
            } else if (frameTimeNanos - lastFpsTime >= 1_000_000_000L) {
                val elapsedSeconds = (frameTimeNanos - lastFpsTime).toDouble() / 1_000_000_000.0
                val calculatedFps = (frameCount / elapsedSeconds).toInt()
                loadState.value = SystemLoad(currentFps = calculatedFps)
                frameCount = 0
                lastFpsTime = frameTimeNanos
            }

            if (listeners.isNotEmpty()) {
                isChoreographerScheduled = true
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    @Synchronized
    fun addListener(listener: TickListener): Boolean {
        val added = listeners.add(listener)
        ensureChoreographerScheduled()
        return added
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @Synchronized
    fun removeListener(listener: TickListener): Boolean {
        val removed = listeners.remove(listener)
        if (listeners.isEmpty() && isChoreographerScheduled) {
            val unschedule = {
                if (listeners.isEmpty() && isChoreographerScheduled) {
                    try {
                        Choreographer.getInstance().removeFrameCallback(frameCallback)
                    } catch (_: Throwable) {}
                    isChoreographerScheduled = false
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) unschedule() else mainHandler.post(unschedule)
        }
        return removed
    }

    private fun ensureChoreographerScheduled() {
        if (listeners.isNotEmpty() && !isChoreographerScheduled) {
            val schedule = {
                if (listeners.isNotEmpty() && !isChoreographerScheduled) {
                    isChoreographerScheduled = true
                    lastFpsTime = 0L
                    frameCount = 0
                    try {
                        Choreographer.getInstance().postFrameCallback(frameCallback)
                    } catch (t: Throwable) {
                        isChoreographerScheduled = false
                        com.rui.compose.svga.utils.log.LogUtils.error("SvgaEnvironment", "Failed to schedule Choreographer callback", t)
                    }
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) schedule() else mainHandler.post(schedule)
        }
    }
}
