package com.rui.compose.svga.view

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import com.rui.compose.svga.audio.SvgaSoundManager
import com.rui.compose.svga.drawer.SvgaCanvasDrawer
import com.rui.compose.svga.drawer.SvgaDynamicEntity
import com.rui.compose.svga.entity.SvgaVideoEntity

/**
 * 现代化的 SVGA Android Drawable 包装类
 *
 * 包装 [SvgaCanvasDrawer] 实现高效的 Canvas 逐帧绘制与音频同步
 */
open class SvgaDrawable(
    val videoEntity: SvgaVideoEntity,
    val dynamicEntity: SvgaDynamicEntity = SvgaDynamicEntity()
) : Drawable() {

    /** 兼容历史属性名称 */
    val videoItem: SvgaVideoEntity get() = videoEntity
    val dynamicItem: SvgaDynamicEntity get() = dynamicEntity

    var cleared = true
        internal set(value) {
            if (field == value) return
            field = value
            invalidateSelf()
        }

    var currentFrame = 0
        internal set(value) {
            if (field == value) return
            field = value
            invalidateSelf()
        }

    var scaleType: ImageView.ScaleType = ImageView.ScaleType.FIT_CENTER

    private val drawer = SvgaCanvasDrawer(videoEntity, dynamicEntity)

    private var alphaVal = 255

    override fun draw(canvas: Canvas) {
        if (cleared) return
        val w = if (bounds.width() > 0) bounds.width().toFloat() else videoEntity.videoSize.width.toFloat()
        val h = if (bounds.height() > 0) bounds.height().toFloat() else videoEntity.videoSize.height.toFloat()
        if (w <= 0f || h <= 0f) return
        val saveCount = if (alphaVal < 255 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            canvas.saveLayerAlpha(bounds.left.toFloat(), bounds.top.toFloat(), bounds.left.toFloat() + w, bounds.top.toFloat() + h, alphaVal)
        } else {
            canvas.save()
        }
        try {
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            drawer.drawFrame(canvas, currentFrame, scaleType, w, h)
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    override fun setAlpha(alpha: Int) {
        val clamped = alpha.coerceIn(0, 255)
        if (this.alphaVal != clamped) {
            this.alphaVal = clamped
            invalidateSelf()
        }
    }

    override fun getAlpha(): Int = alphaVal
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    override fun getIntrinsicWidth(): Int {
        val w = videoEntity.videoSize.width.toInt()
        return if (w > 0) w else -1
    }

    override fun getIntrinsicHeight(): Int {
        val h = videoEntity.videoSize.height.toInt()
        return if (h > 0) h else -1
    }

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    fun resume() {
        videoEntity.audioList.forEach { audio ->
            audio.playID?.let {
                try {
                    if (SvgaSoundManager.isInit()) SvgaSoundManager.resume(it)
                    else videoEntity.soundPool?.resume(it)
                } catch (_: Throwable) {}
            }
        }
    }

    fun pause() {
        videoEntity.audioList.forEach { audio ->
            audio.playID?.let {
                try {
                    if (SvgaSoundManager.isInit()) SvgaSoundManager.pause(it)
                    else videoEntity.soundPool?.pause(it)
                } catch (_: Throwable) {}
            }
        }
    }

    fun stop() {
        videoEntity.audioList.forEach { audio ->
            audio.playID?.let {
                try {
                    if (SvgaSoundManager.isInit()) SvgaSoundManager.stop(it)
                    else videoEntity.soundPool?.stop(it)
                } catch (_: Throwable) {}
            }
            audio.playID = null
        }
    }

    fun clear() {
        stop()
        cleared = true
        drawer.clear()
    }
}
