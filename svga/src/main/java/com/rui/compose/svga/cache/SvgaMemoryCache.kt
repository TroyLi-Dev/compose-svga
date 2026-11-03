package com.rui.compose.svga.cache

import android.util.LruCache
import com.rui.compose.svga.entity.SvgaVideoEntity

/**
 * SVGA 内存 LRU 缓存（原 SVGAActivitiesCache 规范化命名）
 */
class SvgaMemoryCache private constructor() {

    companion object {
        const val TAG = "SvgaMemoryCache"
        private const val DEFAULT_MAX_SIZE = 64 * 1024 * 1024 // 64MB

        val instance by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { SvgaMemoryCache() }
    }

    private val cache = object : LruCache<String, SvgaVideoEntity>(DEFAULT_MAX_SIZE) {
        override fun sizeOf(key: String, value: SvgaVideoEntity): Int {
            return getImageSize(value)
        }
    }

    private fun getImageSize(entity: SvgaVideoEntity?): Int {
        var size = 0
        entity?.imageMap?.values?.forEach { bitmap ->
            size += if (bitmap.isRecycled) 0 else bitmap.byteCount
        }
        return if (size <= 0) 1024 else size
    }

    fun put(key: String, entity: SvgaVideoEntity) {
        cache.put(key, entity)
    }

    fun get(key: String): SvgaVideoEntity? {
        return cache.get(key)
    }

    fun remove(cacheKey: String) {
        cache.remove(cacheKey)
    }

    fun clearAllCache() {
        cache.evictAll()
    }
}
