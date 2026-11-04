package com.rui.compose.svga.view

/**
 * 动画播放生命周期与进度监听器
 */
interface SvgaCallback {
    fun onPause() {}
    fun onFinished() {}
    fun onRepeat() {}
    fun onStep(frame: Int, percentage: Double) {}
}

/**
 * 空实现的便捷基类
 */
open class SvgaSimpleCallback : SvgaCallback
