package com.rui.compose.svga.model

import androidx.compose.runtime.Immutable

/**
 * 动效渲染优先级
 * 控制在系统负载不同时的自适应调度策略
 */
@Immutable
enum class SvgaPriority {
    /**
     * 高优先级：核心动效（如直播间全屏大礼物、主视觉动画），始终全速渲染，不进行自动降频。
     */
    High,

    /**
     * 普通优先级：默认策略，系统处于高负载（FPS < 48）时自动降半频渲染，避免卡顿。
     */
    Normal,

    /**
     * 低优先级：背景/挂件动效，限制最高 15 FPS 运行以节省 CPU 与电量。
     */
    Low
}
