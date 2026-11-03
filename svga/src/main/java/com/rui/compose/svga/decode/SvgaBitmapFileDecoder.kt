package com.rui.compose.svga.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory

object SvgaBitmapFileDecoder : SvgaBitmapDecoder<String>() {
    override fun onDecode(data: String, ops: BitmapFactory.Options): Bitmap? {
        return BitmapFactory.decodeFile(data, ops)
    }
}
