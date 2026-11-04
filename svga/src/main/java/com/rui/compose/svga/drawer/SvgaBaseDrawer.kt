package com.rui.compose.svga.drawer

import android.graphics.Canvas
import android.widget.ImageView
import com.rui.compose.svga.entity.SvgaVideoEntity
import com.rui.compose.svga.entity.SvgaVideoSpriteFrameEntity
import androidx.core.util.Pools
import kotlin.math.max

abstract class SvgaBaseDrawer(val videoItem: SvgaVideoEntity) {

    val scaleInfo = SvgaScaleInfo()

    private val spritePool = Pools.SimplePool<SvgaDrawerSprite>(max(1, videoItem.spriteList.size))
    private val frameSpritesList = ArrayList<SvgaDrawerSprite>(max(1, videoItem.spriteList.size))

    class SvgaDrawerSprite(
        var _matteKey: String? = null,
        var _imageKey: String? = null,
        var _bitmapKey: String? = null,
        var _noExtKey: String? = null,
        var _noExtBitmapKey: String? = null,
        var _isMatte: Boolean = false,
        var _frameEntity: SvgaVideoSpriteFrameEntity? = null
    ) {
        val matteKey get() = _matteKey
        val imageKey get() = _imageKey
        val bitmapKey get() = _bitmapKey
        val noExtKey get() = _noExtKey
        val noExtBitmapKey get() = _noExtBitmapKey
        val isMatte get() = _isMatte
        val frameEntity get() = _frameEntity!!
    }

    internal fun requestFrameSprites(frameIndex: Int): List<SvgaDrawerSprite> {
        frameSpritesList.clear()
        val list = videoItem.spriteList
        val size = list.size
        for (i in 0 until size) {
            val spriteItem = list[i]
            val frames = spriteItem.frames
            if (frameIndex >= 0 && frameIndex < frames.size) {
                val imageKey = spriteItem.imageKey
                if (imageKey != null) {
                    val frame = frames[frameIndex]
                    if (!spriteItem.isMatte && frame.alpha <= 0.0) {
                        continue
                    }
                    val sprite = spritePool.acquire() ?: SvgaDrawerSprite()
                    sprite._matteKey = spriteItem.matteKey
                    sprite._imageKey = imageKey
                    sprite._bitmapKey = spriteItem.bitmapKey
                    sprite._noExtKey = spriteItem.noExtKey
                    sprite._noExtBitmapKey = spriteItem.noExtBitmapKey
                    sprite._isMatte = spriteItem.isMatte
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
            s._bitmapKey = null
            s._noExtKey = null
            s._noExtBitmapKey = null
            s._isMatte = false
            s._frameEntity = null
            try {
                spritePool.release(s)
            } catch (_: Throwable) {}
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
            canvas.clipRect(0f, 0f, drawWidth, drawHeight)
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
