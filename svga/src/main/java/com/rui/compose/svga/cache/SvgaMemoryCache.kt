package com.rui.compose.svga.cache

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.util.LruCache
import com.rui.compose.svga.entity.SvgaVideoEntity

/**
 * SVGA 内存 LRU 缓存（原 SVGAActivitiesCache 规范化命名）
 */
class SvgaMemoryCache private constructor() {

    companion object {
        const val TAG = "SvgaMemoryCache"
        
        /**
         * 动态基于可用堆内存的 1/6 计算缓存上限（下限 32MB，上限 128MB），防止低端机 OOM 并消除长列表频繁换页颠簸
         */
        private val DEFAULT_MAX_SIZE: Int by lazy {
            val maxMemory = Runtime.getRuntime().maxMemory()
            (maxMemory / 6).coerceIn(32L * 1024 * 1024, 128L * 1024 * 1024).toInt()
        }

        val instance by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { SvgaMemoryCache() }
    }

    private val cache = object : LruCache<String, SvgaVideoEntity>(DEFAULT_MAX_SIZE) {
        override fun sizeOf(key: String, value: SvgaVideoEntity): Int {
            return value.memoryByteSize
        }
    }

    @Volatile
    private var isRegistered = false

    fun init(context: Context) {
        if (!isRegistered) {
            val app = context.applicationContext as? Application ?: return
            synchronized(this) {
                if (!isRegistered) {
                    try {
                        app.registerComponentCallbacks(object : ComponentCallbacks2 {
                            override fun onTrimMemory(level: Int) {
                                trimMemory(level)
                            }
                            override fun onConfigurationChanged(newConfig: Configuration) {}
                            @Deprecated("Deprecated in Java")
                            override fun onLowMemory() {
                                clearAllCache()
                            }
                        })
                        isRegistered = true
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    fun trimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            clearAllCache()
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            cache.trimToSize(cache.maxSize() / 2)
        }
    }

    fun setMaxSize(maxBytes: Int) {
        if (maxBytes > 0) {
            cache.resize(maxBytes)
        }
    }

    fun put(key: String, entity: SvgaVideoEntity) {
        if (key.isEmpty()) return
        cache.put(key, entity)
    }

    fun get(key: String): SvgaVideoEntity? {
        if (key.isEmpty()) return null
        return cache.get(key)
    }

    fun remove(cacheKey: String) {
        if (cacheKey.isEmpty()) return
        cache.remove(cacheKey)
    }

    fun clearAllCache() {
        cache.evictAll()
    }
}
