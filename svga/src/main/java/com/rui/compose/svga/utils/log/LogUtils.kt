package com.rui.compose.svga.utils.log

import android.util.Log

/**
 * SVGA 日志记录接口定义
 */
interface ILogger {
    fun verbose(tag: String, msg: String)
    fun info(tag: String, msg: String)
    fun debug(tag: String, msg: String)
    fun warn(tag: String, msg: String)
    fun error(tag: String, msg: String?, error: Throwable? = null)
}

/**
 * 默认 Logcat 日志实现
 */
class DefaultLogCat : ILogger {
    override fun verbose(tag: String, msg: String) {
        try {
            Log.v(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    override fun info(tag: String, msg: String) {
        try {
            Log.i(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    override fun debug(tag: String, msg: String) {
        try {
            Log.d(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    override fun warn(tag: String, msg: String) {
        try {
            Log.w(tag, msg)
        } catch (_: Throwable) {
            println("WARN: [$tag] $msg")
        }
    }

    override fun error(tag: String, msg: String?, error: Throwable?) {
        try {
            Log.e(tag, msg, error)
        } catch (_: Throwable) {
            System.err.println("ERROR: [$tag] $msg ${error?.message ?: ""}")
        }
    }
}

/**
 * SVGA 全局日志配置管理器
 */
object SvgaLogger {
    private var logger: ILogger? = DefaultLogCat()
    private var isLogEnabled = true

    fun setLogger(customLogger: ILogger?) {
        logger = customLogger
    }

    fun getLogger(): ILogger? = logger

    fun setLogEnabled(enable: Boolean) {
        isLogEnabled = enable
    }

    fun isLogEnabled(): Boolean = isLogEnabled
}

/**
 * 统一日志输出门面工具
 */
object LogUtils {
    private const val DEFAULT_TAG = "SVGALog"

    fun verbose(tag: String = DEFAULT_TAG, msg: String) {
        if (!SvgaLogger.isLogEnabled()) return
        SvgaLogger.getLogger()?.verbose(tag, msg)
    }

    fun info(tag: String = DEFAULT_TAG, msg: String) {
        if (!SvgaLogger.isLogEnabled()) return
        SvgaLogger.getLogger()?.info(tag, msg)
    }

    fun debug(tag: String = DEFAULT_TAG, msg: String) {
        if (!SvgaLogger.isLogEnabled()) return
        SvgaLogger.getLogger()?.debug(tag, msg)
    }

    fun warn(tag: String = DEFAULT_TAG, msg: String) {
        if (!SvgaLogger.isLogEnabled()) return
        SvgaLogger.getLogger()?.warn(tag, msg)
    }

    fun error(tag: String = DEFAULT_TAG, msg: String) {
        if (!SvgaLogger.isLogEnabled()) return
        SvgaLogger.getLogger()?.error(tag, msg, null)
    }

    fun error(tag: String = DEFAULT_TAG, error: Throwable) {
        if (!SvgaLogger.isLogEnabled()) return
        SvgaLogger.getLogger()?.error(tag, error.message, error)
    }

    fun error(tag: String = DEFAULT_TAG, msg: String?, error: Throwable?) {
        if (!SvgaLogger.isLogEnabled()) return
        SvgaLogger.getLogger()?.error(tag, msg, error)
    }
}
