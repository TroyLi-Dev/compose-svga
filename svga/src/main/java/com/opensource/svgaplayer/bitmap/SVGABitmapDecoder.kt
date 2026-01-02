package com.opensource.svgaplayer.bitmap

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Bitmap 解码器
 *
 * <T> 需要加载的数据类型
 *
 * Create by im_dsd 2020/7/7 17:39
 */
internal abstract class SVGABitmapDecoder<T> {

    fun decodeBitmapFrom(
        data: T,
        reqWidth: Int,
        reqHeight: Int,
        needCutBitmap: Boolean = false
    ): Bitmap? {
        return BitmapFactory.Options().run {
            if (needCutBitmap) {
                // 如果期望的宽高是合法的, 则开启检测尺寸模式
                inJustDecodeBounds = true
                inPreferredConfig = Bitmap.Config.RGB_565

                // Calculate inSampleSize
                inSampleSize = 2
                // Decode bitmap with inSampleSize set
                inJustDecodeBounds = false
                val bitmapResult = onDecode(data, this)
                bitmapResult
            } else {

                // 如果期望的宽高是合法的, 则开启检测尺寸模式
                inJustDecodeBounds = (reqWidth > 0 && reqHeight > 0)
                inPreferredConfig = Bitmap.Config.RGB_565

                val bitmap = onDecode(data, this)
                if (!inJustDecodeBounds) {
                    return bitmap
                }

                // Calculate inSampleSize
                inSampleSize = BitmapSampleSizeCalculator.calculate(this, reqWidth, reqHeight)
                // Decode bitmap with inSampleSize set
                inJustDecodeBounds = false
                onDecode(data, this)
            }


        }
    }

    abstract fun onDecode(data: T, ops: BitmapFactory.Options): Bitmap?
}