package com.rui.compose.svga.cache

import android.content.Context
import com.rui.compose.svga.entity.SvgaVideoEntity

/**
 * SVGA 双级缓存统一仓库（Repository Pattern）
 */
object SvgaCacheRepository {

    fun init(context: Context) {
        SvgaDiskCache.init(context)
    }

    fun getFromMemory(cacheKey: String): SvgaVideoEntity? {
        return SvgaMemoryCache.instance.get(cacheKey)
    }

    fun putMemory(cacheKey: String, entity: SvgaVideoEntity) {
        SvgaMemoryCache.instance.put(cacheKey, entity)
    }

    fun isDiskCached(cacheKey: String): Boolean {
        return SvgaDiskCache.isCached(cacheKey)
    }

    fun buildCacheKey(str: String): String {
        return SvgaDiskCache.buildCacheKey(str)
    }

    fun clearAll() {
        SvgaMemoryCache.instance.clearAllCache()
        SvgaDiskCache.clearAll()
    }
}
