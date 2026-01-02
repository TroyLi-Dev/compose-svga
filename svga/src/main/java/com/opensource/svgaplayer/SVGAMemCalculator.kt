package com.opensource.svgaplayer

import android.app.ActivityManager
import android.content.Context
import com.opensource.svgaplayer.utils.log.LogUtils
import java.util.concurrent.Executors
import kotlin.math.ceil

class SVGAMemCalculator private constructor() {
    companion object {
        private const val TAG = "SVGAParser-mem-cache"
        private const val TOTAL_MEMORY_KEY = "totalMemoryKey"
        private const val svgaSharedPreferenceName = "svga-parser_sp"

        /**
         * 有效的最小内存，防止可能有部分机型返回假参数、可能存在机型返回大于 0 但并非内存大小的值
         */
        private const val VALID_MIN_MEMORY_SIZE: Long = 100
        private const val DEFAULT_MEMORY_THRESHOLD: Long = 2048000000// 2048MB
        var activityManager: ActivityManager? = null
        var isLowMemoryDevice = false
        var lowMemoryThreshold: Long = DEFAULT_MEMORY_THRESHOLD// 默认 2048MB，可配置

        // 0 代表还没获取过设备内存，小于 0 代表获取有误
        var deviceTotalMemory: Long = 0

        private const val AVAILABLE_MEMORY_THRESHOLD: Float = 0.95F
        private val MID_LOW_MEMORY_DEVICE_THREAD_COUNT = 2 // 针对内存少于4GB，但是大于2GB的设备，用2个核心线程
        private val MID_MEMORY = 3 // 3gb内存

        fun hasMoreFreeMem(needSize: Long): Boolean {
            val availableMem = getAvailableMem()
            var hasMoreMem = true
            if (availableMem != -1L && needSize >= availableMem * AVAILABLE_MEMORY_THRESHOLD) {
                hasMoreMem = false
            }
            LogUtils.info(
                TAG,
                "hasMoreFreeMem, needSize: $needSize, availableMem: $availableMem, hasMoreMem: [$hasMoreMem]"
            )
            return hasMoreMem
        }

        fun getAvailableMem(): Long {
            var availableMem = -1L
            activityManager?.let {
                val memoryInfo = ActivityManager.MemoryInfo()
                it.getMemoryInfo(memoryInfo)
                if (memoryInfo.totalMem > VALID_MIN_MEMORY_SIZE) {
                    availableMem = memoryInfo.availMem
                }
            }
            return availableMem
        }

        fun hasAvailableMemAtLeastGB(context: Context?, gb: Long): Boolean {
            if (activityManager == null) {
                activityManager =
                    context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            }
            return getAvailableMem() >= gb * 1024 * 1024 * 1024
        }

        val instance by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) { SVGAMemCalculator() }
    }

    var isAbsLowMemoryDevice = false

    fun init(context: Context?) {

        if (deviceTotalMemory == 0L) {
            context?.let {
                getDeviceRAM(it)
            }
            context?.let {
                val svgaSp = it.getSharedPreferences(svgaSharedPreferenceName, Context.MODE_PRIVATE)
                val cacheDeviceMem = svgaSp.getLong(TOTAL_MEMORY_KEY, 0)
                activityManager = it.getSystemService(Context.ACTIVITY_SERVICE)
                        as ActivityManager
                val memoryInfo = ActivityManager.MemoryInfo()
                if (cacheDeviceMem > 0) {
                    deviceTotalMemory = cacheDeviceMem
                    isLowMemoryDevice = deviceTotalMemory <= lowMemoryThreshold
                    LogUtils.info(
                        TAG, "has cache:==========device total:$cacheDeviceMem," +
                                " isLowMemoryDevice:${isLowMemoryDevice}"
                    )
                    return@let
                }
                activityManager?.let { am ->
                    am.getMemoryInfo(memoryInfo)
                    val newTotalMem = memoryInfo.totalMem
                    if (newTotalMem > VALID_MIN_MEMORY_SIZE) {
                        val editor = svgaSp.edit()
                        editor.putLong(TOTAL_MEMORY_KEY, newTotalMem)
                        editor.apply()
                        deviceTotalMemory = newTotalMem
                        isLowMemoryDevice = deviceTotalMemory <= lowMemoryThreshold
                    }
                    LogUtils.info(
                        TAG, "==========device total:$newTotalMem," +
                                "device free: ${memoryInfo.availMem}, isLowMemoryDevice:${isLowMemoryDevice}"
                    )
                }
            }

            if (isLowMemoryDevice) {
                val executor = Executors.newSingleThreadExecutor() { r ->
                    LogUtils.info(
                        TAG,
                        "single executor:create SVGA thread: ${SVGAParser.threadNum}"
                    )
                    Thread(r, "SVGAParser-Thread-${SVGAParser.threadNum.getAndIncrement()}").apply {
                        // 可以考虑降低优先级的方式进一步降低 svga  加载对低端机的影响
//                        priority = 4
//                        priority = Thread.MIN_PRIORITY
                    }
                }
                SVGAParser.setThreadPoolExecutor(executor)
            } else if (context != null && getDeviceRAM(context) <= MID_MEMORY) { // 可用内存大于2gb,并且总内存小于3GB的情况，线程池线程数设置为2
                LogUtils.info(
                    TAG,
                    "two core thread executor:create SVGA thread $MID_LOW_MEMORY_DEVICE_THREAD_COUNT "
                )
                val executor =
                    Executors.newFixedThreadPool(MID_LOW_MEMORY_DEVICE_THREAD_COUNT) { r ->
                        Thread(r, "SVGAParser-Thread-${SVGAParser.threadNum.getAndIncrement()}")
                    }
                SVGAParser.setThreadPoolExecutor(executor)
            }
        }
    }


    var totalMemory: Int = -1

    private fun getMemoryInfo(context: Context): ActivityManager.MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo
    }

    fun getDeviceRAM(context: Context): Int {
        if (totalMemory == -1) {
            val memoryInfo = getMemoryInfo(context)
            totalMemory = ceil(memoryInfo.totalMem / (1000 * 1000 * 1000.0)).toInt()
        }
        LogUtils.info(TAG, "当前设备的RAM:$totalMemory")
        return totalMemory
    }


}