package com.rui.compose.svga.drawer

import android.graphics.Canvas
import android.graphics.Rect
import android.widget.ImageView
import com.rui.compose.svga.entity.SvgaVideoEntity
import com.rui.compose.svga.entity.SvgaVideoSpriteFrameEntity
import androidx.core.util.Pools
import kotlin.math.max

abstract class SvgaBaseDrawer(val videoItem: SvgaVideoEntity) {

    val scaleInfo = SvgaScaleInfo()
    private val clipRect = Rect()

    private val spritePool = Pools.SimplePool<SvgaDrawerSprite>(max(1, videoItem.spriteList.size))
    private val frameSpritesList = ArrayList<SvgaDrawerSprite>(max(1, videoItem.spriteList.size))

    inner class SvgaDrawerSprite(
        var _matteKey: String? = null,
        var _imageKey: String? = null,
        var _frameEntity: SvgaVideoSpriteFrameEntity? = null
    ) {
        val matteKey get() = _matteKey
        val imageKey get() = _imageKey
        val frameEntity get() = _frameEntity!!
    }

    internal fun requestFrameSprites(frameIndex: Int): List<SvgaDrawerSprite> {
        frameSpritesList.clear()
        val list = videoItem.spriteList
        val size = list.size
        for (i in 0 until size) {
            val spriteItem = list[i]
            val frames = spriteItem.frames
            if (frameIndex in 0 until frames.size) {
                val imageKey = spriteItem.imageKey
                if (imageKey != null) {
                    val frame = frames[frameIndex]
                    if (!imageKey.endsWith(".matte") && frame.alpha <= 0.0) {
                        continue
                    }
                    val sprite = spritePool.acquire() ?: SvgaDrawerSprite()
                    sprite._matteKey = spriteItem.matteKey
                    sprite._imageKey = imageKey
                    sprite._frameEntity = frame
                    frameSpritesList.add(sprite)
                }
            }
        }
        return frameSpritesList
    }

    internal fun releaseFrameSprites(sprites: List<SvgaDrawerSprite>) {
        val size = sprites.size
        for (i in 0 until size) {
            val s = sprites[i]
            s._matteKey = null
            s._imageKey = null
            s._frameEntity = null
            spritePool.release(s)
        }
        if (sprites === frameSpritesList) {
            frameSpritesList.clear()
        }
    }

    open fun drawFrame(
        canvas: Canvas,
        frameIndex: Int,
        scaleType: ImageView.ScaleType,
        overrideWidth: Float? = null,
        overrideHeight: Float? = null
    ) {
        val drawWidth: Float
        val drawHeight: Float

        if (overrideWidth != null && overrideHeight != null) {
            drawWidth = kotlin.math.max(1f, overrideWidth)
            drawHeight = kotlin.math.max(1f, overrideHeight)
            clipRect.set(0, 0, drawWidth.toInt(), drawHeight.toInt())
            canvas.clipRect(clipRect)
        } else {
            drawWidth = canvas.width.toFloat()
            drawHeight = canvas.height.toFloat()
        }

        this.scaleInfo.performScaleType(
            drawWidth,
            drawHeight,
            this.videoItem.videoSize.width.toFloat(),
            this.videoItem.videoSize.height.toFloat(),
            scaleType
        )
    }
}
