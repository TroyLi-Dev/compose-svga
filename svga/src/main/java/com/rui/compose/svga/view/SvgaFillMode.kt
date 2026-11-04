package com.rui.compose.svga.view

/**
 * 动画播放停止后的填充模式
 */
enum class SvgaFillMode {
    /** 停留在起始帧 */
    Backward,

    /** 停留在结束帧（默认） */
    Forward,

    /** 播放完成后清除画布 */
    Clear
}
