package com.rui.compose.svga.model

import androidx.compose.runtime.Immutable

/**
 * 动效播放状态机状态
 */
@Immutable
enum class SvgaPlayState {
    /** 初始空闲或未就绪 */
    Idle,

    /** 正在播放 */
    Playing,

    /** 暂停中 */
    Paused,

    /** 播放完毕（达到指定 loops 次数） */
    Finished
}
