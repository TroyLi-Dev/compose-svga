package com.rui.composes.svga.render

import android.graphics.Bitmap
import android.util.LruCache
import android.widget.ImageView
import com.opensource.svgaplayer.drawer.SVGACanvasDrawer
import java.util.Collections

internal object SvgaRenderEngine {
    private val bitmapPool = Collections.synchronizedList(mutableListOf<Bitmap>())
    private const val MAX_POOL_SIZE = 60
    private val renderCanvas = android.graphics.Canvas()

    val frameCache = object : LruCache<Long, Bitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
        override fun entryRemoved(
            evicted: Boolean,
            key: Long,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            if (!oldValue.isRecycled) synchronized(bitmapPool) {
                if (bitmapPool.size < MAX_POOL_SIZE) bitmapPool.add(oldValue)
                else oldValue.recycle()
            }
        }
    }

    private fun obtainBitmap(width: Int, height: Int): Bitmap {
        synchronized(bitmapPool) {
            val it = bitmapPool.iterator()
            while (it.hasNext()) {
                val bmp = it.next()
                if (bmp.width == width && bmp.height == height && !bmp.isRecycled) {
                    it.remove(); bmp.eraseColor(0); return bmp
                }
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    fun renderToCache(
        key: Long, width: Int, height: Int,
        frameIndex: Int, drawer: SVGACanvasDrawer, scaleType: ImageView.ScaleType
    ): Bitmap {
        val bitmap = obtainBitmap(width, height)
        synchronized(renderCanvas) {
            renderCanvas.setBitmap(bitmap)
            renderCanvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR)
            drawer.drawFrame(renderCanvas, frameIndex, scaleType)
            renderCanvas.setBitmap(null)
        }
        frameCache.put(key, bitmap)
        return bitmap
    }
}
