package com.rui.compose.svga.utils

import android.app.ActivityManager
import android.content.Context
import com.rui.compose.svga.utils.log.LogUtils

/**
 * 设备内存与可用内存测量工具
 */
class SvgaMemoryCalculator private constructor() {

    companion object {
        private const val TAG = "SvgaMemoryCalculator"
        private const val TOTAL_MEMORY_KEY = "totalMemoryKey"
        private const val PREFS_NAME = "svga-parser_sp"
        private const val VALID_MIN_MEMORY_SIZE: Long = 100
        private const val DEFAULT_MEMORY_THRESHOLD: Long = 2048000000L // 2048MB
        private const val AVAILABLE_MEMORY_THRESHOLD: Float = 0.95F

        @JvmStatic
        var activityManager: ActivityManager? = null

        @JvmStatic
        var isLowMemoryDevice: Boolean = false

        @JvmStatic
        var lowMemoryThreshold: Long = DEFAULT_MEMORY_THRESHOLD

        @JvmStatic
        var deviceTotalMemory: Long = 0L

        @JvmStatic
        val instance by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { SvgaMemoryCalculator() }

        @JvmStatic
        fun hasSufficientMemory(requiredBytes: Long): Boolean {
            val available = getAvailableMemoryBytes()
            if (available == -1L) {
                // 未能获取系统内存状态时默认放行，避免误拦截动效播放
                return true
            }
            val sufficient = requiredBytes < available * AVAILABLE_MEMORY_THRESHOLD
            LogUtils.info(
                TAG,
                "hasSufficientMemory: required=$requiredBytes, available=$available, sufficient=$sufficient"
            )
            return sufficient
        }

        @JvmStatic
        fun getAvailableMemoryBytes(): Long {
            var availableMem = -1L
            activityManager?.let {
                try {
                    val memoryInfo = ActivityManager.MemoryInfo()
                    it.getMemoryInfo(memoryInfo)
                    if (memoryInfo.totalMem > VALID_MIN_MEMORY_SIZE) {
                        availableMem = memoryInfo.availMem
                    }
                } catch (_: Throwable) {}
            }
            return availableMem
        }

        @JvmStatic
        fun hasAvailableMemoryGb(context: Context?, gb: Long): Boolean {
            if (activityManager == null && context != null) {
                val appCtx = context.applicationContext ?: context
                activityManager = appCtx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            }
            return getAvailableMemoryBytes() >= gb * 1024L * 1024L * 1024L
        }

        // --- 向下兼容旧方法签名 (针对 yayya-android 渐进式平滑迁移) ---

        @JvmStatic
        fun hasMoreFreeMem(needSize: Long): Boolean = hasSufficientMemory(needSize)

        @JvmStatic
        fun getAvailableMem(): Long = getAvailableMemoryBytes()

        @JvmStatic
        fun hasAvailableMemAtLeastGB(context: Context?, gb: Long): Boolean = hasAvailableMemoryGb(context, gb)
    }

    var isAbsLowMemoryDevice: Boolean = false

    fun init(context: Context?) {
        if (context == null) return
        val appContext = context.applicationContext ?: context
        if (activityManager == null) {
            activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        }
        if (deviceTotalMemory > 0L) return
        queryDeviceTotalRam(appContext)
    }

    fun queryDeviceTotalRam(context: Context): Long {
        if (deviceTotalMemory > 0L) return deviceTotalMemory
        val appCtx = context.applicationContext ?: context
        val sp = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = sp.getLong(TOTAL_MEMORY_KEY, 0L)
        if (cached > 0L) {
            deviceTotalMemory = cached
            isLowMemoryDevice = deviceTotalMemory <= lowMemoryThreshold
            return deviceTotalMemory
        }
        val am = appCtx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memoryInfo)
        if (memoryInfo.totalMem > VALID_MIN_MEMORY_SIZE) {
            deviceTotalMemory = memoryInfo.totalMem
            isLowMemoryDevice = deviceTotalMemory <= lowMemoryThreshold
            sp.edit().putLong(TOTAL_MEMORY_KEY, deviceTotalMemory).apply()
        }
        return deviceTotalMemory
    }

    // --- 向下兼容旧方法签名 ---
    fun getDeviceRAM(context: Context): Long = queryDeviceTotalRam(context)
}
