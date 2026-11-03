package com.rui.compose.svga.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory

object SvgaBitmapByteArrayDecoder : SvgaBitmapDecoder<ByteArray>() {
    override fun onDecode(data: ByteArray, ops: BitmapFactory.Options): Bitmap? {
        return BitmapFactory.decodeByteArray(data, 0, data.size, ops)
    }
}
