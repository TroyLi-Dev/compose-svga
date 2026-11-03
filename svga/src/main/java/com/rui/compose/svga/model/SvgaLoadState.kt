package com.rui.compose.svga.model

import androidx.compose.runtime.Immutable

/**
 * 资源加载与解析状态
 */
@Immutable
enum class SvgaLoadState {
    /** 初始空闲状态 */
    Idle,

    /** 正在加载或解析资源 */
    Loading,

    /** 资源加载解析成功 */
    Success,

    /** 资源加载或解析失败 */
    Error
}
