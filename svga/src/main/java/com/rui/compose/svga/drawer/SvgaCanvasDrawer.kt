@file:Suppress("UseKtx")

package com.rui.compose.svga.drawer

import android.annotation.SuppressLint

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.text.StaticLayout
import android.text.TextUtils
import android.widget.ImageView
import com.rui.compose.svga.audio.SvgaSoundManager
import com.rui.compose.svga.entity.SvgaVideoEntity
import com.rui.compose.svga.entity.SvgaVideoShapeEntity

class SvgaCanvasDrawer(
    videoItem: SvgaVideoEntity,
    val dynamicItem: SvgaDynamicEntity
) : SvgaBaseDrawer(videoItem) {

    private class TextCacheEntry(
        val text: CharSequence,
        val width: Int,
        val height: Int,
        val bitmap: Bitmap
    )

    private val sharedValues = ShareValues()
    private val drawTextCache: HashMap<String, TextCacheEntry> = hashMapOf()

    val matteSprites = mutableMapOf<String, SvgaDrawerSprite>()
    private val matrixScaleTempValues = FloatArray(9)
    private val clickAreaTempValues = FloatArray(9)

    override fun drawFrame(
        canvas: Canvas,
        frameIndex: Int,
        scaleType: ImageView.ScaleType,
        overrideWidth: Float?,
        overrideHeight: Float?
    ) {
        super.drawFrame(canvas, frameIndex, scaleType, overrideWidth, overrideHeight)

        playAudio(frameIndex)
        val sprites = requestFrameSprites(frameIndex)
        if (sprites.isEmpty()) return

        var saveID = -1
        try {
            matteSprites.clear()
            for (i in 0 until sprites.size) {
                val s = sprites[i]
                val key = s.imageKey
                if (key != null && key.endsWith(".matte")) {
                    matteSprites[key] = s
                }
            }

            val drawWidth = (overrideWidth ?: canvas.width.toFloat()).coerceIn(1f, 4096f)
            val drawHeight = (overrideHeight ?: canvas.height.toFloat()).coerceIn(1f, 4096f)

            sprites.forEachIndexed { index, svgaDrawerSprite ->
                svgaDrawerSprite.imageKey?.let {
                    if (it.endsWith(".matte")) {
                        // 遮罩图层仅用于合成蒙版，不直接绘制到主画布
                        return@forEachIndexed
                    }
                }
                if (isMatteBegin(index, sprites)) {
                    if (saveID != -1) {
                        try { canvas.restoreToCount(saveID) } catch (_: Throwable) {}
                    }
                    saveID = canvas.saveLayer(0f, 0f, drawWidth, drawHeight, null)
                }
                drawSprite(svgaDrawerSprite, canvas, frameIndex)

                if (isMatteEnd(index, sprites)) {
                    val matteSprite = matteSprites[svgaDrawerSprite.matteKey]
                    if (matteSprite != null) {
                        val matteCanvas = this.sharedValues.obtainMatteCanvas(drawWidth.toInt(), drawHeight.toInt())
                        val matteBitmap = this.sharedValues.sharedMatteBitmap()
                        if (matteCanvas != null && matteBitmap != null && !matteBitmap.isRecycled) {
                            drawSprite(matteSprite, matteCanvas, frameIndex)
                            val srcRect = this.sharedValues.sharedRect()
                            srcRect.set(0, 0, drawWidth.toInt(), drawHeight.toInt())
                            val dstRect = this.sharedValues.sharedRectF()
                            dstRect.set(0f, 0f, drawWidth, drawHeight)
                            canvas.drawBitmap(
                                matteBitmap,
                                srcRect,
                                dstRect,
                                this.sharedValues.shareMattePaint()
                            )
                        }
                    }
                    if (saveID != -1) {
                        try { canvas.restoreToCount(saveID) } catch (_: Throwable) {}
                        saveID = -1
                    }
                    return@forEachIndexed
                }
            }
        } finally {
            if (saveID != -1) {
                try { canvas.restoreToCount(saveID) } catch (_: Throwable) {}
                saveID = -1
            }
            releaseFrameSprites(sprites)
        }
    }

    private fun isMatteBegin(spriteIndex: Int, sprites: List<SvgaDrawerSprite>): Boolean {
        val current = sprites.getOrNull(spriteIndex) ?: return false
        val imageKey = current.imageKey
        if (imageKey != null && imageKey.endsWith(".matte")) return false
        val matteKey = current.matteKey
        if (matteKey.isNullOrEmpty()) return false
        if (spriteIndex == 0) return true
        val lastSprite = sprites.getOrNull(spriteIndex - 1) ?: return true
        return lastSprite.matteKey.isNullOrEmpty() || lastSprite.matteKey != matteKey
    }

    private fun isMatteEnd(spriteIndex: Int, sprites: List<SvgaDrawerSprite>): Boolean {
        val current = sprites.getOrNull(spriteIndex) ?: return false
        val imageKey = current.imageKey
        if (imageKey != null && imageKey.endsWith(".matte")) return false
        val matteKey = current.matteKey
        if (matteKey.isNullOrEmpty()) return false
        if (spriteIndex == sprites.size - 1) return true
        val nextSprite = sprites.getOrNull(spriteIndex + 1) ?: return true
        return nextSprite.matteKey.isNullOrEmpty() || nextSprite.matteKey != matteKey
    }

    private var lastAudioFrame = -1

    private fun playAudio(frameIndex: Int) {
        if (this.videoItem.audioList.isEmpty()) return
        // 动画倒带、循环或重播时，强制停播上一轮遗留的音频流
        if (frameIndex < lastAudioFrame) {
            this.videoItem.audioList.forEach { audio ->
                audio.playID?.let {
                    try {
                        if (SvgaSoundManager.isInit()) SvgaSoundManager.stop(it)
                        else this.videoItem.soundPool?.stop(it)
                    } catch (_: Throwable) {}
                }
                audio.playID = null
            }
            lastAudioFrame = -1
        }

        val previousFrame = lastAudioFrame
        lastAudioFrame = frameIndex

        this.videoItem.audioList.forEach { audio ->
            val shouldStart = if (previousFrame == -1) {
                audio.startFrame == frameIndex
            } else {
                audio.startFrame in (previousFrame + 1)..frameIndex && frameIndex < audio.endFrame
            }
            if (shouldStart) {
                val currentVol = if (this.videoItem.volume.isFinite()) this.videoItem.volume.coerceIn(0f, 1f) else 0f
                if (currentVol > 0f) {
                    audio.soundID?.let { soundID ->
                        audio.playID?.let { oldId ->
                            try {
                                if (SvgaSoundManager.isInit()) SvgaSoundManager.stop(oldId)
                                else this.videoItem.soundPool?.stop(oldId)
                            } catch (_: Throwable) {}
                        }
                        audio.playID = try {
                            if (SvgaSoundManager.isInit()) {
                                SvgaSoundManager.play(soundID, currentVol)
                            } else {
                                this.videoItem.soundPool?.play(soundID, currentVol, currentVol, 1, 0, 1.0f)
                            }
                        } catch (t: Throwable) {
                            com.rui.compose.svga.utils.log.LogUtils.error("SvgaCanvasDrawer", "playAudio failed", t)
                            null
                        }
                    }
                }
            }
            if (audio.endFrame <= frameIndex) {
                audio.playID?.let {
                    try {
                        if (SvgaSoundManager.isInit()) SvgaSoundManager.stop(it)
                        else this.videoItem.soundPool?.stop(it)
                    } catch (_: Throwable) {}
                }
                audio.playID = null
            }
        }
    }

    fun clear() {
        drawTextCache.values.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        drawTextCache.clear()
        sharedValues.release()
        videoItem.audioList.forEach { audio ->
            audio.playID?.let {
                try {
                    if (SvgaSoundManager.isInit()) SvgaSoundManager.stop(it)
                    else videoItem.soundPool?.stop(it)
                } catch (_: Throwable) {}
            }
            audio.playID = null
        }
    }

    private fun obtainFrameMatrix(transform: Matrix): Matrix {
        val matrix = this.sharedValues.sharedMatrix()
        matrix.postScale(scaleInfo.scaleFx, scaleInfo.scaleFy)
        matrix.postTranslate(scaleInfo.tranFx, scaleInfo.tranFy)
        matrix.preConcat(transform)
        return matrix
    }

    private fun drawSprite(sprite: SvgaDrawerSprite, canvas: Canvas, frameIndex: Int) {
        drawImage(sprite, canvas)
        drawShape(sprite, canvas)
        drawDynamic(sprite, canvas, frameIndex)
    }

    private fun drawImage(sprite: SvgaDrawerSprite, canvas: Canvas) {
        val imageKey = sprite.imageKey ?: return
        if (dynamicItem.dynamicHidden[imageKey] == true) return

        val bitmapKey = if (imageKey.endsWith(".matte")) {
            imageKey.substring(0, imageKey.length - 6)
        } else {
            imageKey
        }
        val drawingBitmap = dynamicItem.dynamicImage[bitmapKey]
            ?: dynamicItem.dynamicImage[imageKey]
            ?: videoItem.imageMap[bitmapKey]
            ?: videoItem.imageMap[imageKey]
            ?: return
        if (drawingBitmap.isRecycled || drawingBitmap.width <= 0 || drawingBitmap.height <= 0) return

        val frameMatrix = obtainFrameMatrix(sprite.frameEntity.transform)
        val paint = this.sharedValues.sharedPaint()
        paint.isAntiAlias = videoItem.antiAlias
        paint.isFilterBitmap = videoItem.antiAlias
        paint.alpha = (sprite.frameEntity.alpha * 255).toInt()

        if (sprite.frameEntity.maskPath != null) {
            val maskPath = sprite.frameEntity.maskPath ?: return
            val count = canvas.save()
            try {
                val path = this.sharedValues.sharedPath()
                maskPath.buildPath(path)
                path.transform(frameMatrix)
                canvas.clipPath(path)
                val sx = (sprite.frameEntity.layout.width / drawingBitmap.width).toFloat()
                val sy = (sprite.frameEntity.layout.height / drawingBitmap.height).toFloat()
                if (sx.isFinite() && sy.isFinite() && sx > 0f && sy > 0f) {
                    frameMatrix.preScale(sx, sy)
                }
                if (!drawingBitmap.isRecycled) canvas.drawBitmap(drawingBitmap, frameMatrix, paint)
            } finally {
                canvas.restoreToCount(count)
            }
        } else {
            val sx = (sprite.frameEntity.layout.width / drawingBitmap.width).toFloat()
            val sy = (sprite.frameEntity.layout.height / drawingBitmap.height).toFloat()
            if (sx.isFinite() && sy.isFinite() && sx > 0f && sy > 0f) {
                frameMatrix.preScale(sx, sy)
            }
            if (!drawingBitmap.isRecycled) canvas.drawBitmap(drawingBitmap, frameMatrix, paint)
        }

        dynamicItem.dynamicClickArea[imageKey]?.let { listener ->
            val rect = this.sharedValues.sharedRectF()
            rect.set(0f, 0f, drawingBitmap.width.toFloat(), drawingBitmap.height.toFloat())
            frameMatrix.mapRect(rect)
            listener.onResponseArea(
                imageKey,
                rect.left.toInt(),
                rect.top.toInt(),
                rect.right.toInt(),
                rect.bottom.toInt()
            )
        }
        drawTextOnBitmap(canvas, drawingBitmap, sprite, frameMatrix)
    }

    private fun drawTextOnBitmap(
        canvas: Canvas,
        drawingBitmap: Bitmap,
        sprite: SvgaDrawerSprite,
        frameMatrix: Matrix
    ) {
        if (drawingBitmap.width <= 0 || drawingBitmap.height <= 0) return
        if (dynamicItem.isTextDirty) {
            this.drawTextCache.values.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
            this.drawTextCache.clear()
            dynamicItem.isTextDirty = false
        }
        val imageKey = sprite.imageKey ?: return
        var textBitmap: Bitmap? = null

        dynamicItem.dynamicText[imageKey]?.let { drawingText ->
            dynamicItem.dynamicTextPaint[imageKey]?.let { drawingTextPaint ->
                val cached = drawTextCache[imageKey]
                if (cached != null && !cached.bitmap.isRecycled &&
                    cached.text == drawingText &&
                    cached.width == drawingBitmap.width &&
                    cached.height == drawingBitmap.height
                ) {
                    textBitmap = cached.bitmap
                } else {
                    cached?.bitmap?.let { if (!it.isRecycled) it.recycle() }
                    try {
                        val tb = Bitmap.createBitmap(
                            drawingBitmap.width,
                            drawingBitmap.height,
                            Bitmap.Config.ARGB_8888
                        )
                        val textCanvas = Canvas(tb)
                        drawingTextPaint.isAntiAlias = true
                        val fontMetrics = this.sharedValues.sharedFontMetrics
                        drawingTextPaint.getFontMetrics(fontMetrics)
                        val baseLineY =
                            (drawingBitmap.height / 2f) - (fontMetrics.top + fontMetrics.bottom) / 2f
                        val x = when (drawingTextPaint.textAlign) {
                            Paint.Align.CENTER -> drawingBitmap.width / 2f
                            Paint.Align.RIGHT -> drawingBitmap.width.toFloat()
                            else -> (drawingBitmap.width - drawingTextPaint.measureText(drawingText)) / 2f
                        }
                        textCanvas.drawText(
                            drawingText,
                            x,
                            baseLineY,
                            drawingTextPaint
                        )
                        drawTextCache[imageKey] = TextCacheEntry(drawingText, drawingBitmap.width, drawingBitmap.height, tb)
                        textBitmap = tb
                    } catch (t: Throwable) {
                        com.rui.compose.svga.utils.log.LogUtils.error("SvgaCanvasDrawer", "Failed to create dynamicText bitmap", t)
                        textBitmap = null
                    }
                }
            }
        }

        dynamicItem.dynamicBoringLayoutText[imageKey]?.let {
            val cached = drawTextCache[imageKey]
            if (cached != null && !cached.bitmap.isRecycled &&
                cached.text == it.text &&
                cached.width == drawingBitmap.width &&
                cached.height == drawingBitmap.height
            ) {
                textBitmap = cached.bitmap
            } else {
                cached?.bitmap?.let { old -> if (!old.isRecycled) old.recycle() }
                try {
                    it.paint.isAntiAlias = true
                    val tb = Bitmap.createBitmap(
                        drawingBitmap.width,
                        drawingBitmap.height,
                        Bitmap.Config.ARGB_8888
                    )
                    val textCanvas = Canvas(tb)
                    textCanvas.translate(0f, ((drawingBitmap.height - it.height) / 2).toFloat())
                    it.draw(textCanvas)
                    drawTextCache[imageKey] = TextCacheEntry(it.text, drawingBitmap.width, drawingBitmap.height, tb)
                    textBitmap = tb
                } catch (t: Throwable) {
                    com.rui.compose.svga.utils.log.LogUtils.error("SvgaCanvasDrawer", "Failed to create dynamicBoringLayoutText bitmap", t)
                    textBitmap = null
                }
            }
        }

        dynamicItem.dynamicStaticLayoutText[imageKey]?.let {
            val cached = drawTextCache[imageKey]
            if (cached != null && !cached.bitmap.isRecycled &&
                cached.text == it.text &&
                cached.width == drawingBitmap.width &&
                cached.height == drawingBitmap.height
            ) {
                textBitmap = cached.bitmap
            } else {
                cached?.bitmap?.let { old -> if (!old.isRecycled) old.recycle() }
                it.paint.isAntiAlias = true
                val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val lineMax = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        it.maxLines
                    } else {
                        try {
                            @SuppressLint("PrivateApi")
                            val field = StaticLayout::class.java.getDeclaredField("mMaximumVisibleLineCount")
                            field.isAccessible = true
                            field.getInt(it)
                        } catch (_: Exception) {
                            Int.MAX_VALUE
                        }
                    }
                    StaticLayout.Builder.obtain(
                        it.text,
                        0,
                        it.text.length,
                        it.paint,
                        drawingBitmap.width
                    )
                        .setAlignment(it.alignment)
                        .setMaxLines(lineMax)
                        .setEllipsize(TextUtils.TruncateAt.END)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(
                        it.text,
                        0,
                        it.text.length,
                        it.paint,
                        drawingBitmap.width,
                        it.alignment,
                        it.spacingMultiplier,
                        it.spacingAdd,
                        false
                    )
                }
                try {
                    val tb = Bitmap.createBitmap(
                        drawingBitmap.width,
                        drawingBitmap.height,
                        Bitmap.Config.ARGB_8888
                    )
                    val textCanvas = Canvas(tb)
                    textCanvas.translate(0f, ((drawingBitmap.height - layout.height) / 2).toFloat())
                    layout.draw(textCanvas)
                    drawTextCache[imageKey] = TextCacheEntry(it.text, drawingBitmap.width, drawingBitmap.height, tb)
                    textBitmap = tb
                } catch (t: Throwable) {
                    com.rui.compose.svga.utils.log.LogUtils.error("SvgaCanvasDrawer", "Failed to create text bitmap", t)
                    textBitmap = null
                }
            }
        }

        textBitmap?.let { tb ->
            if (tb.isRecycled) return@let
            val paint = this.sharedValues.sharedPaint()
            paint.isAntiAlias = videoItem.antiAlias
            paint.isFilterBitmap = videoItem.antiAlias
            paint.alpha = (sprite.frameEntity.alpha * 255).toInt()
            if (sprite.frameEntity.maskPath != null) {
                val maskPath = sprite.frameEntity.maskPath ?: return@let
                val count = canvas.save()
                try {
                    val originMatrix = obtainFrameMatrix(sprite.frameEntity.transform)
                    val path = this.sharedValues.sharedPath()
                    maskPath.buildPath(path)
                    path.transform(originMatrix)
                    canvas.clipPath(path)
                    if (!tb.isRecycled) {
                        canvas.drawBitmap(tb, frameMatrix, paint)
                    }
                } finally {
                    canvas.restoreToCount(count)
                }
            } else {
                if (!tb.isRecycled) {
                    canvas.drawBitmap(tb, frameMatrix, paint)
                }
            }
        }
    }

    private fun drawShape(sprite: SvgaDrawerSprite, canvas: Canvas) {
        val frameMatrix = obtainFrameMatrix(sprite.frameEntity.transform)
        sprite.frameEntity.shapes.forEach { shape ->
            shape.buildPath()
            shape.shapePath?.let { shapePath ->
                val paint = this.sharedValues.sharedPaint()
                paint.reset()
                paint.isAntiAlias = videoItem.antiAlias
                paint.alpha = (sprite.frameEntity.alpha * 255).toInt()
                val path = this.sharedValues.sharedPath()
                path.set(shapePath)
                val shapeMatrix = this.sharedValues.sharedMatrix2()
                shapeMatrix.reset()
                shape.transform?.let { t -> shapeMatrix.postConcat(t) }
                shapeMatrix.postConcat(frameMatrix)
                path.transform(shapeMatrix)

                shape.styles?.fill?.let { color ->
                    if (color != 0x00000000) {
                        paint.style = Paint.Style.FILL
                        paint.color = color
                        val alpha = (sprite.frameEntity.alpha * 255).toInt().coerceIn(0, 255)
                        if (alpha != 255) paint.alpha = alpha
                        if (sprite.frameEntity.maskPath != null) {
                            val count = canvas.save()
                            try {
                                val path2 = this.sharedValues.sharedPath2()
                                sprite.frameEntity.maskPath?.buildPath(path2)
                                path2.transform(frameMatrix)
                                canvas.clipPath(path2)
                                canvas.drawPath(path, paint)
                            } finally {
                                canvas.restoreToCount(count)
                            }
                        } else {
                            canvas.drawPath(path, paint)
                        }
                    }
                }
                shape.styles?.strokeWidth?.let { width ->
                    if (width > 0) {
                        paint.alpha = (sprite.frameEntity.alpha * 255).toInt()
                        paint.style = Paint.Style.STROKE
                        shape.styles?.stroke?.let { color ->
                            paint.color = color
                            val alpha = (sprite.frameEntity.alpha * 255).toInt().coerceIn(0, 255)
                            if (alpha != 255) paint.alpha = alpha
                        }
                        val scale = matrixScale(frameMatrix)
                        paint.strokeWidth = width * scale
                        shape.styles?.lineCap?.let { cap ->
                            when {
                                cap.equals("butt", true) -> paint.strokeCap = Paint.Cap.BUTT
                                cap.equals("round", true) -> paint.strokeCap = Paint.Cap.ROUND
                                cap.equals("square", true) -> paint.strokeCap = Paint.Cap.SQUARE
                            }
                        }
                        shape.styles?.lineJoin?.let { join ->
                            when {
                                join.equals("miter", true) -> paint.strokeJoin = Paint.Join.MITER
                                join.equals("round", true) -> paint.strokeJoin = Paint.Join.ROUND
                                join.equals("bevel", true) -> paint.strokeJoin = Paint.Join.BEVEL
                            }
                        }
                        shape.styles?.miterLimit?.let { paint.strokeMiter = it.toFloat() * scale }
                        shape.styles?.lineDash?.let { dash ->
                            if (scale > 0f && dash.size == 3 && (dash[0] > 0 || dash[1] > 0)) {
                                val interval0 = (dash[0] * scale).coerceAtLeast(1.0f)
                                val interval1 = (dash[1] * scale).coerceAtLeast(0.1f)
                                try {
                                    paint.pathEffect = DashPathEffect(
                                        this.sharedValues.sharedDashIntervals(interval0, interval1),
                                        dash[2] * scale
                                    )
                                } catch (_: Throwable) {}
                            }
                        }
                        if (sprite.frameEntity.maskPath != null) {
                            val count = canvas.save()
                            try {
                                val path2 = this.sharedValues.sharedPath2()
                                sprite.frameEntity.maskPath?.buildPath(path2)
                                path2.transform(frameMatrix)
                                canvas.clipPath(path2)
                                canvas.drawPath(path, paint)
                            } finally {
                                canvas.restoreToCount(count)
                            }
                        } else {
                            canvas.drawPath(path, paint)
                        }
                    }
                }
            }
        }
    }

    private fun matrixScale(matrix: Matrix): Float {
        matrix.getValues(matrixScaleTempValues)
        var a = matrixScaleTempValues[0].toDouble()
        var b = matrixScaleTempValues[3].toDouble()
        var c = matrixScaleTempValues[1].toDouble()
        var d = matrixScaleTempValues[4].toDouble()
        if (a * d == b * c) return 0f
        val scaleX = Math.sqrt(a * a + b * b)
        if (scaleX == 0.0) return 0f
        a /= scaleX; b /= scaleX
        val skew = a * c + b * d
        c -= a * skew; d -= b * skew
        val scaleY = Math.sqrt(c * c + d * d)
        val result = if (scaleInfo.ratioX) Math.abs(scaleX.toFloat()) else Math.abs(scaleY.toFloat())
        return if (result.isFinite() && result > 0f) result else 0f
    }

    private fun drawDynamic(sprite: SvgaDrawerSprite, canvas: Canvas, frameIndex: Int) {
        val imageKey = sprite.imageKey ?: return
        val drawer1 = dynamicItem.dynamicDrawer[imageKey]
        val drawer2 = dynamicItem.dynamicDrawerSized[imageKey]
        if (drawer1 == null && drawer2 == null) return

        val frameMatrix = obtainFrameMatrix(sprite.frameEntity.transform)
        if (drawer1 != null) {
            val count = canvas.save()
            try {
                sprite.frameEntity.maskPath?.let { maskPath ->
                    val path = this.sharedValues.sharedPath()
                    maskPath.buildPath(path)
                    path.transform(frameMatrix)
                    canvas.clipPath(path)
                }
                canvas.concat(frameMatrix)
                drawer1.invoke(canvas, frameIndex)
            } catch (t: Throwable) {
                com.rui.compose.svga.utils.log.LogUtils.error("SvgaCanvasDrawer", "Custom dynamicDrawer crashed for $imageKey", t)
            } finally {
                canvas.restoreToCount(count)
            }
        }
        if (drawer2 != null) {
            val count = canvas.save()
            try {
                sprite.frameEntity.maskPath?.let { maskPath ->
                    val path = this.sharedValues.sharedPath()
                    maskPath.buildPath(path)
                    path.transform(frameMatrix)
                    canvas.clipPath(path)
                }
                canvas.concat(frameMatrix)
                drawer2.invoke(
                    canvas,
                    frameIndex,
                    sprite.frameEntity.layout.width.toInt(),
                    sprite.frameEntity.layout.height.toInt()
                )
            } catch (t: Throwable) {
                com.rui.compose.svga.utils.log.LogUtils.error("SvgaCanvasDrawer", "Custom dynamicDrawerSized crashed for $imageKey", t)
            } finally {
                canvas.restoreToCount(count)
            }
        }
    }

    class ShareValues {
        private val sharedPaint = Paint()
        private val sharedPath = Path()
        private val sharedPath2 = Path()
        private val sharedMatrix = Matrix()
        private val sharedMatrix2 = Matrix()
        private val sharedRect = android.graphics.Rect()
        private val sharedRectF = RectF()
        private val sharedDashIntervals = FloatArray(2)
        val sharedFontMetrics = Paint.FontMetrics()
        private val matteXfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        private val shareMattePaint = Paint().apply { xfermode = matteXfermode }
        private var sharedMatteBitmap: Bitmap? = null
        private var cachedMatteCanvas: Canvas? = null

        fun sharedDashIntervals(interval0: Float, interval1: Float): FloatArray {
            sharedDashIntervals[0] = interval0
            sharedDashIntervals[1] = interval1
            return sharedDashIntervals
        }

        fun sharedRect(): android.graphics.Rect {
            sharedRect.setEmpty(); return sharedRect
        }

        fun sharedRectF(): RectF {
            sharedRectF.setEmpty(); return sharedRectF
        }

        fun sharedPaint(): Paint {
            sharedPaint.reset(); return sharedPaint
        }

        fun sharedPath(): Path {
            sharedPath.reset(); return sharedPath
        }

        fun sharedPath2(): Path {
            sharedPath2.reset(); return sharedPath2
        }

        fun sharedMatrix(): Matrix {
            sharedMatrix.reset(); return sharedMatrix
        }

        fun sharedMatrix2(): Matrix {
            sharedMatrix2.reset(); return sharedMatrix2
        }

        fun shareMattePaint(): Paint = shareMattePaint

        fun sharedMatteBitmap(): Bitmap? {
            return sharedMatteBitmap
        }

        fun obtainMatteCanvas(width: Int, height: Int): Canvas? {
            val w = width.coerceIn(1, 4096)
            val h = height.coerceIn(1, 4096)
            var bmp = sharedMatteBitmap
            if (bmp == null || bmp.isRecycled || bmp.width < w || bmp.height < h) {
                try {
                    val newBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
                    bmp?.recycle()
                    bmp = newBmp
                    sharedMatteBitmap = newBmp
                    cachedMatteCanvas = Canvas(newBmp)
                } catch (t: Throwable) {
                    com.rui.compose.svga.utils.log.LogUtils.error("SvgaCanvasDrawer", "Failed to allocate matte bitmap", t)
                    return null
                }
            }
            val canvas = cachedMatteCanvas ?: Canvas(bmp).also { cachedMatteCanvas = it }
            canvas.drawColor(0, PorterDuff.Mode.CLEAR)
            return canvas
        }

        fun release() {
            try {
                sharedMatteBitmap?.let { if (!it.isRecycled) it.recycle() }
            } catch (_: Throwable) {}
            sharedMatteBitmap = null
            cachedMatteCanvas = null
        }
    }
}
