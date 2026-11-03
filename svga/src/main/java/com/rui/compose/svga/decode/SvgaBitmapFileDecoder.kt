package com.rui.compose.svga.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory

object SvgaBitmapFileDecoder : SvgaBitmapDecoder<String>() {
    override fun onDecode(data: String, ops: BitmapFactory.Options): Bitmap? {
        return try {
            BitmapFactory.decodeFile(data, ops)
        } catch (t: Throwable) {
            null
        }
    }
}
