package com.rui.compose.svga.drawer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.ImageView
import com.rui.compose.svga.entity.SvgaVideoEntity
import com.rui.compose.svga.entity.SvgaVideoSpriteFrameEntity
import kotlin.math.max

abstract class SvgaBaseDrawer(val videoItem: SvgaVideoEntity) {

    val scaleInfo = SvgaScaleInfo()

    protected val drawerSprites: Array<SvgaDrawerSprite> = Array(videoItem.spriteList.size) { i ->
        SvgaDrawerSprite(videoItem.spriteList[i])
    }
    private val frameSpritesList = ArrayList<SvgaDrawerSprite>(max(1, videoItem.spriteList.size))

    class SvgaDrawerSprite(
        val spriteEntity: com.rui.compose.svga.entity.SvgaVideoSpriteEntity? = null,
        var _frameEntity: SvgaVideoSpriteFrameEntity? = null
    ) {
        var _matteKey: String? = null
        var _imageKey: String? = null
        var _bitmapKey: String? = null
        var _noExtKey: String? = null
        var _noExtBitmapKey: String? = null
        var _isMatte: Boolean = false
        var _defaultBitmap: Bitmap? = null

        val matteKey: String? get() = spriteEntity?.matteKey ?: _matteKey
        val imageKey: String? get() = spriteEntity?.imageKey ?: _imageKey
        val bitmapKey: String? get() = spriteEntity?.bitmapKey ?: _bitmapKey
        val noExtKey: String? get() = spriteEntity?.noExtKey ?: _noExtKey
        val noExtBitmapKey: String? get() = spriteEntity?.noExtBitmapKey ?: _noExtBitmapKey
        val isMatte: Boolean get() = spriteEntity?.isMatte ?: _isMatte
        val defaultBitmap: Bitmap? get() = spriteEntity?.defaultBitmap ?: _defaultBitmap
        val frameEntity: SvgaVideoSpriteFrameEntity get() = _frameEntity!!
    }

    internal fun requestFrameSprites(frameIndex: Int): List<SvgaDrawerSprite> {
        frameSpritesList.clear()
        val list = videoItem.spriteList
        val size = list.size
        for (i in 0 until size) {
            val spriteItem = list[i]
            val frames = spriteItem.frames
            if (frameIndex in frames.indices) {
                if (spriteItem.imageKey != null) {
                    val frame = frames[frameIndex]
                    if (!spriteItem.isMatte && frame.alpha <= 0.0) {
                        continue
                    }
                    val drawerSprite = drawerSprites[i]
                    drawerSprite._frameEntity = frame
                    frameSpritesList.add(drawerSprite)
                }
            }
        }
        return frameSpritesList
    }

    internal fun releaseFrameSprites(sprites: List<SvgaDrawerSprite>) {
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
