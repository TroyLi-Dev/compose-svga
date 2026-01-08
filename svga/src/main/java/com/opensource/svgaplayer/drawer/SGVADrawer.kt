package com.opensource.svgaplayer.drawer

import android.graphics.Canvas
import android.graphics.Rect
import android.widget.ImageView
import com.opensource.svgaplayer.SVGAVideoEntity
import com.opensource.svgaplayer.entities.SVGAVideoSpriteFrameEntity
import com.opensource.svgaplayer.utils.Pools
import com.opensource.svgaplayer.utils.SVGAScaleInfo
import kotlin.math.max

/**
 * Created by cuiminghui on 2017/3/29.
 */

open internal class SGVADrawer(val videoItem: SVGAVideoEntity) {

    val scaleInfo = SVGAScaleInfo()
    private val mClipRect = Rect()

    private val spritePool = Pools.SimplePool<SVGADrawerSprite>(max(1, videoItem.spriteList.size))

    inner class SVGADrawerSprite(
        var _matteKey: String? = null,
        var _imageKey: String? = null,
        var _frameEntity: SVGAVideoSpriteFrameEntity? = null
    ) {
        val matteKey get() = _matteKey
        val imageKey get() = _imageKey
        val frameEntity get() = _frameEntity!!
    }

    internal fun requestFrameSprites(frameIndex: Int): List<SVGADrawerSprite> {
        return videoItem.spriteList.mapNotNull {
            if (frameIndex >= 0 && frameIndex < it.frames.size) {
                it.imageKey?.let { imageKey ->
                    if (!imageKey.endsWith(".matte") && it.frames[frameIndex].alpha <= 0.0) {
                        return@mapNotNull null
                    }
                    return@mapNotNull (spritePool.acquire() ?: SVGADrawerSprite()).apply {
                        _matteKey = it.matteKey
                        _imageKey = it.imageKey
                        _frameEntity = it.frames[frameIndex]
                    }
                }
            }
            return@mapNotNull null
        }
    }

    internal fun releaseFrameSprites(sprites: List<SVGADrawerSprite>) {
        sprites.forEach { spritePool.release(it) }
    }

    /**
     * 核心修复：增加显式宽高参数，优先于 Canvas 自带的尺寸
     */
    open fun drawFrame(canvas: Canvas, frameIndex: Int, scaleType: ImageView.ScaleType, overrideWidth: Float? = null, overrideHeight: Float? = null) {
        val drawWidth: Float
        val drawHeight: Float

        if (overrideWidth != null && overrideHeight != null) {
            drawWidth = overrideWidth
            drawHeight = overrideHeight
        } else {
            if (canvas.getClipBounds(mClipRect)) {
                drawWidth = mClipRect.width().toFloat()
                drawHeight = mClipRect.height().toFloat()
            } else {
                drawWidth = canvas.width.toFloat()
                drawHeight = canvas.height.toFloat()
            }
        }

        scaleInfo.performScaleType(
            drawWidth,
            drawHeight,
            videoItem.videoSize.width.toFloat(),
            videoItem.videoSize.height.toFloat(),
            scaleType
        )
    }

}
