package com.rui.compose.svga.cache

import android.util.LruCache
import com.rui.compose.svga.entity.SvgaVideoEntity

/**
 * SVGA 内存 LRU 缓存（原 SVGAActivitiesCache 规范化命名）
 */
class SvgaMemoryCache private constructor() {

    companion object {
        const val TAG = "SvgaMemoryCache"
        
        /**
         * 动态基于可用堆内存的 1/8 计算缓存上限（下限 16MB，上限 64MB），防止低端机 OOM
         */
        private val DEFAULT_MAX_SIZE: Int by lazy {
            val maxMemory = Runtime.getRuntime().maxMemory()
            (maxMemory / 8).coerceIn(16L * 1024 * 1024, 64L * 1024 * 1024).toInt()
        }

        val instance by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { SvgaMemoryCache() }
    }

    private val cache = object : LruCache<String, SvgaVideoEntity>(DEFAULT_MAX_SIZE) {
        override fun sizeOf(key: String, value: SvgaVideoEntity): Int {
            return getImageSize(value)
        }
    }

    fun setMaxSize(maxBytes: Int) {
        if (maxBytes > 0) {
            cache.resize(maxBytes)
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
