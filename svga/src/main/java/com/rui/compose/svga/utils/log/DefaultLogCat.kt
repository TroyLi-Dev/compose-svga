package com.rui.compose.svga.utils.log

import android.util.Log

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
