package com.rui.compose.svga.utils.log

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
