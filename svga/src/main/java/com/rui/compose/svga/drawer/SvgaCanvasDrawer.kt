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

    private val sharedValues = ShareValues()
    private val dynamicStaticLayoutCache: HashMap<String, StaticLayout> = hashMapOf()

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

        if (dynamicItem.dynamicClickArea.isNotEmpty()) {
            dynamicItem.clickMap.clear()
        }

        playAudio(frameIndex)
        val sprites = requestFrameSprites(frameIndex)
        if (sprites.isEmpty()) return

        var saveID = -1
        try {
            matteSprites.clear()
            val spriteCount = sprites.size
            for (i in 0 until spriteCount) {
                val s = sprites[i]
                if (s.isMatte && s.imageKey != null) {
                    matteSprites[s.imageKey!!] = s
                }
            }

            val drawWidth = (overrideWidth ?: canvas.width.toFloat()).coerceAtLeast(1f)
            val drawHeight = (overrideHeight ?: canvas.height.toFloat()).coerceAtLeast(1f)

            for (index in 0 until spriteCount) {
                val svgaDrawerSprite = sprites[index]
                if (svgaDrawerSprite.isMatte) {
                    // 遮罩图层仅用于合成蒙版，不直接绘制到主画布
                    continue
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
                    continue
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
        if (matteSprites.isEmpty()) return false
        val current = sprites.getOrNull(spriteIndex) ?: return false
        if (current.isMatte) return false
        val matteKey = current.matteKey
        if (matteKey.isNullOrEmpty()) return false

        var prevIndex = spriteIndex - 1
        while (prevIndex >= 0) {
            val prev = sprites[prevIndex]
            if (!prev.isMatte) {
                return prev.matteKey.isNullOrEmpty() || prev.matteKey != matteKey
            }
            prevIndex--
        }
        return true
    }

    private fun isMatteEnd(spriteIndex: Int, sprites: List<SvgaDrawerSprite>): Boolean {
        if (matteSprites.isEmpty()) return false
        val current = sprites.getOrNull(spriteIndex) ?: return false
        if (current.isMatte) return false
        val matteKey = current.matteKey
        if (matteKey.isNullOrEmpty()) return false

        var nextIndex = spriteIndex + 1
        while (nextIndex < sprites.size) {
            val next = sprites[nextIndex]
            if (!next.isMatte) {
                return next.matteKey.isNullOrEmpty() || next.matteKey != matteKey
            }
            nextIndex++
        }
        return true
    }

    private var lastAudioFrame = -1

    private val activeStreamIds = HashMap<com.rui.compose.svga.entity.SvgaAudioEntity, Int>()
    var volume: Float = 1.0f

    fun setAudioVolume(vol: Float) {
        val clamped = if (vol.isFinite()) vol.coerceIn(0f, 1f) else 0f
        this.volume = clamped
        activeStreamIds.values.forEach { streamId ->
            try {
                if (SvgaSoundManager.isInit()) {
                    SvgaSoundManager.setStreamVolume(streamId, clamped)
                } else {
                    this.videoItem.soundPool?.setVolume(streamId, clamped, clamped)
                }
            } catch (_: Throwable) {}
        }
    }

    fun pauseAudio() {
        activeStreamIds.values.forEach { streamId ->
            try {
                if (SvgaSoundManager.isInit()) SvgaSoundManager.pause(streamId)
                else this.videoItem.soundPool?.pause(streamId)
            } catch (_: Throwable) {}
        }
    }

    fun resumeAudio() {
        activeStreamIds.values.forEach { streamId ->
            try {
                if (SvgaSoundManager.isInit()) SvgaSoundManager.resume(streamId)
                else this.videoItem.soundPool?.resume(streamId)
            } catch (_: Throwable) {}
        }
    }

    fun stopAudio() {
        activeStreamIds.values.forEach { streamId ->
            try {
                if (SvgaSoundManager.isInit()) SvgaSoundManager.stop(streamId)
                else this.videoItem.soundPool?.stop(streamId)
            } catch (_: Throwable) {}
        }
        activeStreamIds.clear()
    }

    private fun playAudio(frameIndex: Int) {
        if (this.videoItem.audioList.isEmpty()) return
        // 动画倒带、循环或重播时，强制停播上一轮遗留的音频流
        if (frameIndex < lastAudioFrame) {
            stopAudio()
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
                val currentVol = if (this.volume.isFinite()) this.volume.coerceIn(0f, 1f) else 0f
                if (currentVol >= 0f) {
                    audio.soundID?.let { soundID ->
                        val oldStreamId = activeStreamIds.remove(audio)
                        if (oldStreamId != null) {
                            try {
                                if (SvgaSoundManager.isInit()) SvgaSoundManager.stop(oldStreamId)
                                else this.videoItem.soundPool?.stop(oldStreamId)
                            } catch (_: Throwable) {}
                        }
                        val streamId = try {
                            if (SvgaSoundManager.isInit()) {
                                SvgaSoundManager.play(soundID, currentVol)
                            } else {
                                this.videoItem.soundPool?.play(soundID, currentVol, currentVol, 1, 0, 1.0f)
                            }
                        } catch (t: Throwable) {
                            com.rui.compose.svga.utils.log.LogUtils.error("SvgaCanvasDrawer", "playAudio failed", t)
                            null
                        }
                        if (streamId != null && streamId > 0) {
                            activeStreamIds[audio] = streamId
                            audio.playID = streamId // 向下兼容外部直接读取
                        }
                    }
                }
            }
            if (audio.endFrame <= frameIndex) {
                val streamId = activeStreamIds.remove(audio)
                if (streamId != null) {
                    try {
                        if (SvgaSoundManager.isInit()) SvgaSoundManager.stop(streamId)
                        else this.videoItem.soundPool?.stop(streamId)
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    fun clear() {
        dynamicStaticLayoutCache.clear()
        sharedValues.release()
        stopAudio()
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
        val noExtKey = sprite.noExtKey
        if (dynamicItem.dynamicHidden.isNotEmpty()) {
            if (dynamicItem.dynamicHidden[imageKey] == true || (noExtKey != null && dynamicItem.dynamicHidden[noExtKey] == true)) return
        }

        val bitmapKey = sprite.bitmapKey ?: imageKey
        val noExtBitmapKey = sprite.noExtBitmapKey

        val drawingBitmap = (if (dynamicItem.dynamicImage.isNotEmpty()) {
            dynamicItem.dynamicImage[bitmapKey]
                ?: (if (noExtBitmapKey != null) dynamicItem.dynamicImage[noExtBitmapKey] else null)
                ?: (if (noExtKey != null) dynamicItem.dynamicImage[noExtKey] else null)
                ?: dynamicItem.dynamicImage[imageKey]
        } else null)
            ?: videoItem.imageMap[bitmapKey]
            ?: (if (noExtBitmapKey != null) videoItem.imageMap[noExtBitmapKey] else null)
            ?: (if (noExtKey != null) videoItem.imageMap[noExtKey] else null)
            ?: videoItem.imageMap[imageKey]
            ?: return
        if (drawingBitmap.isRecycled || drawingBitmap.width <= 0 || drawingBitmap.height <= 0) return

        val frameMatrix = obtainFrameMatrix(sprite.frameEntity.transform)
        val paint = this.sharedValues.sharedPaint()
        paint.isAntiAlias = videoItem.antiAlias
        paint.isFilterBitmap = videoItem.antiAlias
        paint.alpha = (sprite.frameEntity.alpha * 255).toInt()

        val maskPath = sprite.frameEntity.maskPath
        val count = if (maskPath != null) {
            val c = canvas.save()
            val path = this.sharedValues.sharedPath()
            maskPath.buildPath(path)
            path.transform(frameMatrix)
            canvas.clipPath(path)
            c
        } else {
            -1
        }
        try {
            val sx = (sprite.frameEntity.layout.width / drawingBitmap.width).toFloat()
            val sy = (sprite.frameEntity.layout.height / drawingBitmap.height).toFloat()
            if (sx.isFinite() && sy.isFinite() && sx > 0f && sy > 0f) {
                frameMatrix.preScale(sx, sy)
            }
            if (!drawingBitmap.isRecycled) {
                canvas.drawBitmap(drawingBitmap, frameMatrix, paint)
            }

            if (dynamicItem.dynamicClickArea.isNotEmpty()) {
                val clickListener = dynamicItem.dynamicClickArea[imageKey]
                    ?: (if (noExtKey != null) dynamicItem.dynamicClickArea[noExtKey] else null)
                clickListener?.let { listener ->
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
            }

            if (dynamicItem.hasDynamicText()) {
                drawTextOnBitmap(canvas, drawingBitmap, sprite, frameMatrix)
            }
        } finally {
            if (count != -1) {
                canvas.restoreToCount(count)
            }
        }
    }

    private fun drawTextOnBitmap(
        canvas: Canvas,
        drawingBitmap: Bitmap,
        sprite: SvgaDrawerSprite,
        frameMatrix: Matrix
    ) {
        if (!dynamicItem.hasDynamicText()) return
        if (drawingBitmap.width <= 0 || drawingBitmap.height <= 0) return
        if (dynamicItem.isTextDirty) {
            dynamicStaticLayoutCache.clear()
            dynamicItem.isTextDirty = false
        }
        val imageKey = sprite.imageKey ?: return
        val noExtKey = sprite.noExtKey

        val drawingText = dynamicItem.dynamicText[imageKey]
            ?: (if (noExtKey != null) dynamicItem.dynamicText[noExtKey] else null)
        val drawingTextPaint = dynamicItem.dynamicTextPaint[imageKey]
            ?: (if (noExtKey != null) dynamicItem.dynamicTextPaint[noExtKey] else null)
        val boringLayout = dynamicItem.dynamicBoringLayoutText[imageKey]
            ?: (if (noExtKey != null) dynamicItem.dynamicBoringLayoutText[noExtKey] else null)
        val staticLayout = dynamicItem.dynamicStaticLayoutText[imageKey]
            ?: (if (noExtKey != null) dynamicItem.dynamicStaticLayoutText[noExtKey] else null)

        if (drawingText == null && boringLayout == null && staticLayout == null) return

        val alpha = (sprite.frameEntity.alpha * 255).toInt().coerceIn(0, 255)
        if (alpha <= 0) return

        val textCount = canvas.save()
        try {
            canvas.concat(frameMatrix)

            if (drawingText != null && drawingTextPaint != null) {
                drawingTextPaint.isAntiAlias = true
                val originalAlpha = drawingTextPaint.alpha
                val finalAlpha = if (alpha == 255) originalAlpha else ((originalAlpha * alpha) / 255)
                try {
                    drawingTextPaint.alpha = finalAlpha
                    val fontMetrics = this.sharedValues.sharedFontMetrics
                    drawingTextPaint.getFontMetrics(fontMetrics)
                    val baseLineY =
                        (drawingBitmap.height / 2f) - (fontMetrics.top + fontMetrics.bottom) / 2f
                    val x = when (drawingTextPaint.textAlign) {
                        Paint.Align.CENTER -> drawingBitmap.width / 2f
                        Paint.Align.RIGHT -> drawingBitmap.width.toFloat()
                        else -> (drawingBitmap.width - drawingTextPaint.measureText(drawingText)) / 2f
                    }
                    canvas.drawText(drawingText, x, baseLineY, drawingTextPaint)
                } finally {
                    drawingTextPaint.alpha = originalAlpha
                }
            } else if (boringLayout != null) {
                boringLayout.paint.isAntiAlias = true
                val originalAlpha = boringLayout.paint.alpha
                val finalAlpha = if (alpha == 255) originalAlpha else ((originalAlpha * alpha) / 255)
                val c = canvas.save()
                try {
                    boringLayout.paint.alpha = finalAlpha
                    canvas.translate(0f, ((drawingBitmap.height - boringLayout.height) / 2).toFloat())
                    boringLayout.draw(canvas)
                } finally {
                    canvas.restoreToCount(c)
                    boringLayout.paint.alpha = originalAlpha
                }
            } else if (staticLayout != null) {
                staticLayout.paint.isAntiAlias = true
                val originalAlpha = staticLayout.paint.alpha
                val finalAlpha = if (alpha == 255) originalAlpha else ((originalAlpha * alpha) / 255)

                val layout = if (staticLayout.width == drawingBitmap.width) {
                    staticLayout
                } else {
                    dynamicStaticLayoutCache.getOrPut(imageKey) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val lineMax = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                                staticLayout.maxLines
                            } else {
                                try {
                                    @SuppressLint("PrivateApi")
                                    val field = StaticLayout::class.java.getDeclaredField("mMaximumVisibleLineCount")
                                    field.isAccessible = true
                                    field.getInt(staticLayout)
                                } catch (_: Exception) {
                                    Int.MAX_VALUE
                                }
                            }
                            StaticLayout.Builder.obtain(
                                staticLayout.text,
                                0,
                                staticLayout.text.length,
                                staticLayout.paint,
                                drawingBitmap.width
                            )
                                .setAlignment(staticLayout.alignment)
                                .setMaxLines(lineMax)
                                .setEllipsize(TextUtils.TruncateAt.END)
                                .build()
                        } else {
                            @Suppress("DEPRECATION")
                            StaticLayout(
                                staticLayout.text,
                                0,
                                staticLayout.text.length,
                                staticLayout.paint,
                                drawingBitmap.width,
                                staticLayout.alignment,
                                staticLayout.spacingMultiplier,
                                staticLayout.spacingAdd,
                                false
                            )
                        }
                    }
                }

                val c = canvas.save()
                try {
                    layout.paint.alpha = finalAlpha
                    canvas.translate(0f, ((drawingBitmap.height - layout.height) / 2).toFloat())
                    layout.draw(canvas)
                } finally {
                    canvas.restoreToCount(c)
                    layout.paint.alpha = originalAlpha
                }
            }
        } finally {
            canvas.restoreToCount(textCount)
        }
    }

    private fun drawShape(sprite: SvgaDrawerSprite, canvas: Canvas) {
        val shapeList = sprite.frameEntity.shapes
        val shapeCount = shapeList.size
        if (shapeCount == 0) return
        val frameMatrix = obtainFrameMatrix(sprite.frameEntity.transform)
        val maskPath = sprite.frameEntity.maskPath
        val saveCount = if (maskPath != null) {
            val count = canvas.save()
            val path2 = this.sharedValues.sharedPath2()
            maskPath.buildPath(path2)
            path2.transform(frameMatrix)
            canvas.clipPath(path2)
            count
        } else {
            -1
        }
        try {
            for (i in 0 until shapeCount) {
                val shape = shapeList[i]
                shape.buildPath()
                shape.shapePath?.let { shapePath ->
                    val paint = this.sharedValues.sharedPaint()
                    paint.reset()
                    paint.isAntiAlias = videoItem.antiAlias
                    val alpha = (sprite.frameEntity.alpha * 255).toInt().coerceIn(0, 255)
                    paint.alpha = alpha
                    val path = this.sharedValues.sharedPath()
                    path.set(shapePath)
                    val shapeMatrix = this.sharedValues.sharedMatrix2()
                    shapeMatrix.reset()
                    shape.transform?.let { t -> shapeMatrix.postConcat(t) }
                    shapeMatrix.postConcat(frameMatrix)
                    path.transform(shapeMatrix)

                    shape.styles?.fill?.let { color ->
                        val colorAlpha = (color ushr 24) and 0xFF
                        if (colorAlpha > 0 && alpha > 0) {
                            paint.style = Paint.Style.FILL
                            paint.color = color
                            val finalAlpha = if (alpha == 255) colorAlpha else ((colorAlpha * alpha) / 255)
                            paint.alpha = finalAlpha
                            canvas.drawPath(path, paint)
                        }
                    }
                    shape.styles?.strokeWidth?.let { width ->
                        if (width > 0 && alpha > 0) {
                            paint.style = Paint.Style.STROKE
                            shape.styles?.stroke?.let { color ->
                                val colorAlpha = (color ushr 24) and 0xFF
                                paint.color = color
                                val finalAlpha = if (alpha == 255) colorAlpha else ((colorAlpha * alpha) / 255)
                                paint.alpha = finalAlpha
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
                            paint.pathEffect = null
                            shape.styles?.lineDash?.let { dash ->
                                if (scale > 0f && dash.size >= 2 && (dash[0] > 0 || dash[1] > 0)) {
                                    val interval0 = (dash[0] * scale).coerceAtLeast(1.0f)
                                    val interval1 = (dash[1] * scale).coerceAtLeast(0.1f)
                                    val phase = if (dash.size >= 3) dash[2] * scale else 0f
                                    try {
                                        paint.pathEffect = DashPathEffect(
                                            this.sharedValues.sharedDashIntervals(interval0, interval1),
                                            phase
                                        )
                                    } catch (_: Throwable) {}
                                }
                            }
                            canvas.drawPath(path, paint)
                            paint.pathEffect = null
                        }
                    }
                }
            }
        } finally {
            if (saveCount != -1) {
                canvas.restoreToCount(saveCount)
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
        if (dynamicItem.dynamicDrawer.isEmpty() && dynamicItem.dynamicDrawerSized.isEmpty()) return
        val imageKey = sprite.imageKey ?: return
        val noExtKey = sprite.noExtKey
        val drawer1 = dynamicItem.dynamicDrawer[imageKey]
            ?: (if (noExtKey != null) dynamicItem.dynamicDrawer[noExtKey] else null)
        val drawer2 = dynamicItem.dynamicDrawerSized[imageKey]
            ?: (if (noExtKey != null) dynamicItem.dynamicDrawerSized[noExtKey] else null)
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
                    sprite.frameEntity.layout.width.toInt().coerceAtLeast(0),
                    sprite.frameEntity.layout.height.toInt().coerceAtLeast(0)
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
            val w = width.coerceIn(1, 8192)
            val h = height.coerceIn(1, 8192)
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
