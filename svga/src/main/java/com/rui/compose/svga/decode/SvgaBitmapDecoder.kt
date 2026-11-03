package com.rui.compose.svga.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory

abstract class SvgaBitmapDecoder<T> {

    fun decodeBitmapFrom(
        data: T,
        reqWidth: Int,
        reqHeight: Int,
        needCutBitmap: Boolean = false
    ): Bitmap? {
        return BitmapFactory.Options().run {
            if (needCutBitmap) {
                inJustDecodeBounds = true
                inPreferredConfig = Bitmap.Config.RGB_565
                inSampleSize = 2
                inJustDecodeBounds = false
                onDecode(data, this)
            } else {
                inJustDecodeBounds = (reqWidth > 0 && reqHeight > 0)
                inPreferredConfig = Bitmap.Config.RGB_565

                val bitmap = onDecode(data, this)
                if (!inJustDecodeBounds) {
                    return bitmap
                }

                inSampleSize = BitmapSampleSizeCalculator.calculate(this, reqWidth, reqHeight)
                inJustDecodeBounds = false
                onDecode(data, this)
            }
        }
    }

    abstract fun onDecode(data: T, ops: BitmapFactory.Options): Bitmap?
}
