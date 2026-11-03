package com.rui.compose.svga.utils.log

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
