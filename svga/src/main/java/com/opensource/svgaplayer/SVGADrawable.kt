package com.opensource.svgaplayer

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.opensource.svgaplayer.drawer.SVGACanvasDrawer

class SVGADrawable(val videoItem: SVGAVideoEntity, val dynamicItem: SVGADynamicEntity) :
    Drawable() {

    constructor(videoItem: SVGAVideoEntity) : this(videoItem, SVGADynamicEntity())

    var cleared = true
        internal set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateSelf()
        }

    var currentFrame = 0
        internal set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateSelf()
        }

    var scaleType: ImageView.ScaleType = ImageView.ScaleType.MATRIX

    private val drawer = SVGACanvasDrawer(videoItem, dynamicItem)

    override fun draw(canvas: Canvas) {
        if (cleared) {
            return
        }
        // 修复：移除对非空 Canvas 的冗余安全调用
        drawer.drawFrame(canvas, currentFrame, scaleType)
    }

    override fun setAlpha(alpha: Int) {

    }

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {

    }

    fun resume() {
        videoItem.audioList.forEach { audio ->
            audio.playID?.let {
                if (SVGASoundManager.isInit()) {
                    SVGASoundManager.resume(it)
                } else {
                    videoItem.soundPool?.resume(it)
                }
            }
        }
    }

    fun pause() {
        videoItem.audioList.forEach { audio ->
            audio.playID?.let {
                if (SVGASoundManager.isInit()) {
                    SVGASoundManager.pause(it)
                } else {
                    videoItem.soundPool?.pause(it)
                }
            }
        }
    }

    fun stop() {
        videoItem.audioList.forEach { audio ->
            audio.playID?.let {
                if (SVGASoundManager.isInit()) {
                    SVGASoundManager.stop(it)
                } else {
                    videoItem.soundPool?.stop(it)
                }
            }
        }
    }

    fun clear() {
        videoItem.audioList.forEach { audio ->
            audio.playID?.let {
                if (SVGASoundManager.isInit()) {
                    SVGASoundManager.stop(it)
                } else {
                    videoItem.soundPool?.stop(it)
                }
            }
            audio.playID = null
        }
        videoItem.clear()
    }
}
