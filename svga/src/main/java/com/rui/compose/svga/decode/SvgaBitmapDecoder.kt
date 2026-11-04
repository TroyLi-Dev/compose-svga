package com.rui.compose.svga.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.rui.compose.svga.utils.log.LogUtils

abstract class SvgaBitmapDecoder<T> {

    fun decodeBitmapFrom(
        data: T,
        reqWidth: Int,
        reqHeight: Int,
        needCutBitmap: Boolean = false
    ): Bitmap? {
        return try {
            BitmapFactory.Options().run {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = false
                if (reqWidth > 0 && reqHeight > 0) {
                    inJustDecodeBounds = true
                    onDecode(data, this)
                    val calculated = BitmapSampleSizeCalculator.calculate(this, reqWidth, reqHeight)
                    inSampleSize = if (needCutBitmap) kotlin.math.max(2, calculated) else calculated
                    inJustDecodeBounds = false
                    onDecode(data, this)
                } else if (needCutBitmap) {
                    inSampleSize = 2
                    onDecode(data, this)
                } else {
                    onDecode(data, this)
                }
            }
        } catch (t: Throwable) {
            LogUtils.error("SvgaBitmapDecoder", "OOM or error during bitmap decode", t)
            null
        }
    }

    abstract fun onDecode(data: T, ops: BitmapFactory.Options): Bitmap?
}
