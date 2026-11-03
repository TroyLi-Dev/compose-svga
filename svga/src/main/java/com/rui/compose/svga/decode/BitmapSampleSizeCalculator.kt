package com.rui.compose.svga.decode

import android.graphics.BitmapFactory

object BitmapSampleSizeCalculator {

    fun calculate(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (reqHeight <= 0 || reqWidth <= 0 || height <= 0 || width <= 0) {
            return inSampleSize
        }
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (inSampleSize <= 64 && halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize.coerceAtLeast(1)
    }
}
