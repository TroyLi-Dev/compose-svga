package com.rui.compose.svga.view

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
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

    override fun draw(canvas: Canvas) {
        if (cleared) return
        val w = if (bounds.width() > 0) bounds.width().toFloat() else videoEntity.videoSize.width.toFloat()
        val h = if (bounds.height() > 0) bounds.height().toFloat() else videoEntity.videoSize.height.toFloat()
        drawer.drawFrame(canvas, currentFrame, scaleType, w, h)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    fun resume() {
        videoEntity.audioList.forEach { audio ->
            audio.playID?.let { SvgaSoundManager.resume(it) }
        }
    }

    fun pause() {
        videoEntity.audioList.forEach { audio ->
            audio.playID?.let { SvgaSoundManager.pause(it) }
        }
    }

    fun stop() {
        videoEntity.audioList.forEach { audio ->
            audio.playID?.let { SvgaSoundManager.stop(it) }
        }
    }

    fun clear() {
        stop()
        cleared = true
    }
}
