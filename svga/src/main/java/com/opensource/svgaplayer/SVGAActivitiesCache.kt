package com.opensource.svgaplayer

import android.util.LruCache

/**
 * 优化后的 SVGA 活动缓存
 */
class SVGAActivitiesCache {
    companion object {
        const val TAG = "SVGAParser-Act-Cache"
        val instance by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) { SVGAActivitiesCache() }

        // 增大缓存容量到 64M，确保更多动画能驻留在内存中
        private const val DEFAULT_MAX_SIZE = 64 * 1024 * 1024
    }

    private var mCache: LruCache<String, SVGAVideoEntity> =
        object : LruCache<String, SVGAVideoEntity>(DEFAULT_MAX_SIZE) {
            override fun sizeOf(key: String, value: SVGAVideoEntity): Int {
                return getImageSize(value)
            }
        }

    private fun getImageSize(value: SVGAVideoEntity?): Int {
        var size = 0
        value?.imageMap?.values?.forEach { bitmap ->
            size += if (bitmap.isRecycled) 0 else bitmap.byteCount
        }
        return if (size <= 0) 1024 else size
    }

    fun put(key: String, entity: SVGAVideoEntity) {
        mCache.put(key, entity)
    }

    fun get(key: String): SVGAVideoEntity? {
        return mCache.get(key)
    }

    fun remove(cacheKey: String) {
        mCache.remove(cacheKey)
    }

    fun clearAllCache() {
        mCache.evictAll()
    }
}
