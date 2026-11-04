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
    @Volatile
    var currentFps: Int = 60
        private set

    private val listeners = ArrayList<TickListener>()
    @Volatile
    private var listenerArray = emptyArray<TickListener>()

    @Volatile
    private var isChoreographerScheduled = false

    interface TickListener {
        fun onTick(frameTimeNanos: Long)
    }

    private var lastFpsTime = 0L
    private var frameCount = 0

    private val mainHandler = Handler(Looper.getMainLooper())

    private val scheduleRunnable = Runnable {
        ensureChoreographerScheduledInternal()
    }

    private val unscheduleRunnable = Runnable {
        unscheduleChoreographerInternal()
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            isChoreographerScheduled = false
            val array = listenerArray
            val len = array.size
            if (len == 0) {
                return // 无活跃监听，彻底休眠
            }

            for (i in 0 until len) {
                try {
                    array[i].onTick(frameTimeNanos)
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
                currentFps = calculatedFps
                loadState.value = SystemLoad(currentFps = calculatedFps)
                frameCount = 0
                lastFpsTime = frameTimeNanos
            }

            if (listenerArray.isNotEmpty()) {
                isChoreographerScheduled = true
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    @Synchronized
    fun addListener(listener: TickListener): Boolean {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            listenerArray = listeners.toTypedArray()
            ensureChoreographerScheduled()
            return true
        }
        return false
    }

    @Synchronized
    fun removeListener(listener: TickListener): Boolean {
        val removed = listeners.remove(listener)
        if (removed) {
            listenerArray = if (listeners.isEmpty()) emptyArray() else listeners.toTypedArray()
            if (listeners.isEmpty() && isChoreographerScheduled) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    unscheduleChoreographerInternal()
                } else {
                    mainHandler.post(unscheduleRunnable)
                }
            }
        }
        return removed
    }

    private fun unscheduleChoreographerInternal() {
        if (listeners.isEmpty() && isChoreographerScheduled) {
            try {
                Choreographer.getInstance().removeFrameCallback(frameCallback)
            } catch (_: Throwable) {}
            isChoreographerScheduled = false
        }
    }

    private fun ensureChoreographerScheduled() {
        if (listeners.isNotEmpty() && !isChoreographerScheduled) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                ensureChoreographerScheduledInternal()
            } else {
                mainHandler.post(scheduleRunnable)
            }
        }
    }

    private fun ensureChoreographerScheduledInternal() {
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
}
