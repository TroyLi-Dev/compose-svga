package com.rui.compose.svga.parser

import com.rui.compose.svga.parser.fetcher.AssetFetcher
import com.rui.compose.svga.parser.fetcher.FileFetcher
import com.rui.compose.svga.parser.fetcher.HttpUrlFetcher
import com.rui.compose.svga.parser.fetcher.RawResFetcher
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 资源获取器责任链注册中心（Chain of Responsibility Pattern）
 */
object SvgaFetcherRegistry {

    private val fetchers = CopyOnWriteArrayList<SvgaResourceFetcher<*>>().apply {
        add(FileFetcher())
        add(HttpUrlFetcher())
        add(RawResFetcher())
        add(AssetFetcher()) // Asset 作为兜底探测
    }

    fun register(fetcher: SvgaResourceFetcher<*>, priorityFirst: Boolean = true) {
        if (priorityFirst) {
            fetchers.add(0, fetcher)
        } else {
            fetchers.add(fetcher)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> findFetcher(data: T): SvgaResourceFetcher<T>? {
        return fetchers.firstOrNull { it.handles(data) } as? SvgaResourceFetcher<T>
    }
}
