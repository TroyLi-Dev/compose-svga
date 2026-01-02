package com.opensource.svgaplayer

import android.util.LruCache

/**
 * 优化后的 SVGA 活动缓存
 * 支持跨组件复用 SVGAVideoEntity，极大降低 100+ 实例下的内存占用
 */
class SVGAActivitiesCache {
    companion object {
        const val TAG = "SVGAParser-Act-Cache"
        val instance by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) { SVGAActivitiesCache() }

        // 增大缓存容量到 20M，因为我们现在支持全局复用
        private const val DEFAULT_MAX_SIZE = 20 * 1024 * 1024
    }

    private var mCache: LruCache<String, SVGAVideoEntity> =
        object : LruCache<String, SVGAVideoEntity>(DEFAULT_MAX_SIZE) {
            override fun sizeOf(key: String, value: SVGAVideoEntity): Int {
                return getImageSize(value)
            }

            override fun entryRemoved(
                evicted: Boolean,
                key: String,
                oldValue: SVGAVideoEntity,
                newValue: SVGAVideoEntity?
            ) {
                // 只有当真正被挤出缓存且没有活跃引用时才清理
                // 这里我们依赖外部的引用计数管理，LruCache 仅作为二级缓存
            }
        }

    private fun getImageSize(value: SVGAVideoEntity?): Int {
        var size = 0
        value?.imageMap?.values?.forEach { bitmap ->
            size += if (bitmap.isRecycled) 0 else bitmap.byteCount
        }
        return if (size <= 0) 1024 else size // 给予基础权重
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
