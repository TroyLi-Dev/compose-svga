package com.rui.composes.svga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos

/**
 * Svga 性能环境提供者
 * 封装了：全局时钟驱动、系统负载监控、状态分发
 */
@Composable
fun SvgaProvider(content: @Composable () -> Unit) {
    // 1. 驱动全局同步时钟信号 (每秒 60 次)
    val svgaTick = remember { mutableLongStateOf(System.nanoTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { svgaTick.longValue = it }
        }
    }

    // 2. 驱动负载监控信号 (用于 Normal 优先级的自适应降频)
    val systemLoad = remember { mutableStateOf(SystemLoad()) }
    LaunchedEffect(Unit) {
        var frameCount = 0
        var lastTime = System.nanoTime()
        while (true) {
            withFrameNanos {
                frameCount++
                val now = System.nanoTime()
                if (now - lastTime >= 1_000_000_000L) {
                    // 更新全局 FPS，触发所有 SvgaAnimation 的自适应策略
                    systemLoad.value = SystemLoad(currentFps = frameCount)
                    frameCount = 0
                    lastTime = now
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalSvgaClock provides svgaTick,
        LocalSystemLoad provides systemLoad
    ) {
        content()
    }
}