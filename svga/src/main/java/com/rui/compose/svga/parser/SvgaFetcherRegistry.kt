package com.rui.compose.svga.parser

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 资源获取器插件注册中心（Strategy Pattern）
 */
object SvgaFetcherRegistry {

    private val fetchers = CopyOnWriteArrayList<SvgaResourceFetcher<*>>()

    fun register(fetcher: SvgaResourceFetcher<*>, priorityFirst: Boolean = true) {
        if (priorityFirst) {
            fetchers.add(0, fetcher)
        } else {
            fetchers.add(fetcher)
        }
    }

    fun unregister(fetcher: SvgaResourceFetcher<*>) {
        fetchers.remove(fetcher)
    }

    fun clear() {
        fetchers.clear()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> findFetcher(data: T): SvgaResourceFetcher<T>? {
        return fetchers.firstOrNull { it.handles(data) } as? SvgaResourceFetcher<T>
    }
}
