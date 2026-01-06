package com.rui.composes.svga.core

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.rui.composes.svga.model.SystemLoad

val LocalSystemLoad =
    staticCompositionLocalOf<MutableState<SystemLoad>> { SvgaEnvironment.loadState }
val LocalSvgaClock = staticCompositionLocalOf<State<Long>> { SvgaEnvironment.tickState }

object SvgaEnvironment {
    private var refCount = 0
    val tickState = mutableLongStateOf(System.nanoTime())
    val loadState = mutableStateOf(SystemLoad())

    private var lastFpsTime = 0L
    private var frameCount = 0

    private val frameCallback = object : android.view.Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (refCount <= 0) return
            tickState.longValue = frameTimeNanos
            frameCount++
            if (lastFpsTime == 0L) lastFpsTime = frameTimeNanos
            if (frameTimeNanos - lastFpsTime >= 1_000_000_000L) {
                loadState.value = SystemLoad(currentFps = frameCount)
                frameCount = 0
                lastFpsTime = frameTimeNanos
            }
            android.view.Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun attach() {
        if (refCount++ == 0) {
            lastFpsTime = 0L; frameCount = 0
            android.view.Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    fun detach() {
        if (refCount > 0) {
            refCount--
        }
    }
}
