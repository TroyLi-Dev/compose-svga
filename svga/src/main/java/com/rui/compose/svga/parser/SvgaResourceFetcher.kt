package com.rui.compose.svga.parser

import android.content.Context

/**
 * 资源获取器策略接口（Strategy Pattern）
 */
interface SvgaResourceFetcher<T : Any> {
    fun handles(data: Any): Boolean
    suspend fun fetch(context: Context, data: T): SvgaFetchResult
}
