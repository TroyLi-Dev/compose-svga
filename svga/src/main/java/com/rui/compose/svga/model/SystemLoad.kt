package com.rui.compose.svga.model

import androidx.compose.runtime.Stable

/**
 * 系统瞬时负载指标
 *
 * @param currentFps 当前由中心化时钟 Choreographer 测得的瞬时刷新帧率（默认 60）
 */
@Stable
data class SystemLoad(
    val currentFps: Int = 60
)
