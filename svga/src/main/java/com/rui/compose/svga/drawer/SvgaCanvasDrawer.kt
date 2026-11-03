package com.rui.compose.svga.drawer

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
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
    private val drawTextCache: HashMap<String, Bitmap> = hashMapOf()
    private val pathCache = PathCache()

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
        this.pathCache.onSizeChanged(canvas)
        val sprites = requestFrameSprites(frameIndex)
        if (sprites.isEmpty()) return

        try {
            matteSprites.clear()
            var saveID = -1

            val drawWidth = (overrideWidth ?: canvas.width.toFloat()).coerceIn(1f, 2048f)
            val drawHeight = (overrideHeight ?: canvas.height.toFloat()).coerceIn(1f, 2048f)

            var hasMatteLayer = false
            sprites.getOrNull(0)?.imageKey?.let {
                if (it.endsWith(".matte")) {
                    hasMatteLayer = true
                }
            }

            sprites.forEachIndexed { index, svgaDrawerSprite ->
                svgaDrawerSprite.imageKey?.let {
                    if (!hasMatteLayer || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        drawSprite(svgaDrawerSprite, canvas, frameIndex)
                        return@forEachIndexed
                    }
                    if (it.endsWith(".matte")) {
                        matteSprites[it] = svgaDrawerSprite
                        return@forEachIndexed
                    }
                }
                if (isMatteBegin(index, sprites)) {
                    saveID = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        canvas.saveLayer(0f, 0f, drawWidth, drawHeight, null)
                    } else {
                        canvas.save()
                    }
                }
                drawSprite(svgaDrawerSprite, canvas, frameIndex)

                if (isMatteEnd(index, sprites)) {
                    matteSprites[svgaDrawerSprite.matteKey]?.let {
                        drawSprite(
                            it,
                            this.sharedValues.obtainMatteCanvas(drawWidth.toInt(), drawHeight.toInt()),
                            frameIndex
                        )
                        canvas.drawBitmap(
                            this.sharedValues.sharedMatteBitmap(),
                            0f, 0f, this.sharedValues.shareMattePaint()
                        )
                        if (saveID != -1) {
                            canvas.restoreToCount(saveID)
                        } else {
                            canvas.restore()
                        }
                        return@forEachIndexed
                    }
                }
            }
        } finally {
            releaseFrameSprites(sprites)
        }
    }

    private fun isMatteBegin(spriteIndex: Int, sprites: List<SvgaDrawerSprite>): Boolean {
        val current = sprites.getOrNull(spriteIndex) ?: return false
        val imageKey = current.imageKey
        if (imageKey != null && imageKey.endsWith(".matte")) return false
        val matteKey = current.matteKey
        if (matteKey.isNullOrEmpty() || spriteIndex <= 0) return false
        val lastSprite = sprites.getOrNull(spriteIndex - 1) ?: return false
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

    private fun playAudio(frameIndex: Int) {
        if (this.videoItem.audioList.isEmpty()) return
        this.videoItem.audioList.forEach { audio ->
            if (audio.startFrame == frameIndex) {
                val currentVol = this.videoItem.volume
                if (currentVol > 0f) {
                    if (SvgaSoundManager.isInit()) {
                        audio.soundID?.let { soundID ->
                            audio.playID = SvgaSoundManager.play(soundID, currentVol)
                        }
                    } else {
                        this.videoItem.soundPool?.let { soundPool ->
                            audio.soundID?.let { soundID ->
                                audio.playID = soundPool.play(soundID, currentVol, currentVol, 1, 0, 1.0f)
                            }
                        }
                    }
                }
            }
            if (audio.endFrame <= frameIndex) {
                audio.playID?.let {
                    if (SvgaSoundManager.isInit()) SvgaSoundManager.stop(it)
                    else this.videoItem.soundPool?.stop(it)
                }
                audio.playID = null
            }
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
        val drawingBitmap =
            (dynamicItem.dynamicImage[bitmapKey] ?: videoItem.imageMap[bitmapKey]) ?: return

        val frameMatrix = obtainFrameMatrix(sprite.frameEntity.transform)
        val paint = this.sharedValues.sharedPaint()
        paint.isAntiAlias = videoItem.antiAlias
        paint.isFilterBitmap = videoItem.antiAlias
        paint.alpha = (sprite.frameEntity.alpha * 255).toInt()

        if (sprite.frameEntity.maskPath != null) {
            val maskPath = sprite.frameEntity.maskPath ?: return
            canvas.save()
            val path = this.sharedValues.sharedPath()
            maskPath.buildPath(path)
            path.transform(frameMatrix)
            canvas.clipPath(path)
            frameMatrix.preScale(
                (sprite.frameEntity.layout.width / drawingBitmap.width).toFloat(),
                (sprite.frameEntity.layout.height / drawingBitmap.height).toFloat()
            )
            if (!drawingBitmap.isRecycled) canvas.drawBitmap(drawingBitmap, frameMatrix, paint)
            canvas.restore()
        } else {
            frameMatrix.preScale(
                (sprite.frameEntity.layout.width / drawingBitmap.width).toFloat(),
                (sprite.frameEntity.layout.height / drawingBitmap.height).toFloat()
            )
            if (!drawingBitmap.isRecycled) canvas.drawBitmap(drawingBitmap, frameMatrix, paint)
        }

        dynamicItem.dynamicClickArea[imageKey]?.let { listener ->
            frameMatrix.getValues(clickAreaTempValues)
            listener.onResponseArea(
                imageKey,
                clickAreaTempValues[2].toInt(),
                clickAreaTempValues[5].toInt(),
                (drawingBitmap.width * clickAreaTempValues[0] + clickAreaTempValues[2]).toInt(),
                (drawingBitmap.height * clickAreaTempValues[4] + clickAreaTempValues[5]).toInt()
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
            this.drawTextCache.clear()
            dynamicItem.isTextDirty = false
        }
        val imageKey = sprite.imageKey ?: return
        var textBitmap: Bitmap? = null

        dynamicItem.dynamicText[imageKey]?.let { drawingText ->
            dynamicItem.dynamicTextPaint[imageKey]?.let { drawingTextPaint ->
                drawTextCache[imageKey]?.let { textBitmap = it } ?: run {
                    textBitmap = Bitmap.createBitmap(
                        drawingBitmap.width,
                        drawingBitmap.height,
                        Bitmap.Config.ARGB_8888
                    )
                    val textCanvas = Canvas(textBitmap!!)
                    drawingTextPaint.isAntiAlias = true
                    val fontMetrics = drawingTextPaint.fontMetrics
                    val baseLineY =
                        (drawingBitmap.height / 2f) - (fontMetrics.top + fontMetrics.bottom) / 2f
                    textCanvas.drawText(
                        drawingText,
                        drawingBitmap.width / 2f,
                        baseLineY,
                        drawingTextPaint
                    )
                    drawTextCache[imageKey] = textBitmap!!
                }
            }
        }

        dynamicItem.dynamicBoringLayoutText[imageKey]?.let {
            drawTextCache[imageKey]?.let { textBitmap = it } ?: run {
                it.paint.isAntiAlias = true
                textBitmap = Bitmap.createBitmap(
                    drawingBitmap.width,
                    drawingBitmap.height,
                    Bitmap.Config.ARGB_8888
                )
                val textCanvas = Canvas(textBitmap!!)
                textCanvas.translate(0f, ((drawingBitmap.height - it.height) / 2).toFloat())
                it.draw(textCanvas)
                drawTextCache[imageKey] = textBitmap!!
            }
        }

        dynamicItem.dynamicStaticLayoutText[imageKey]?.let {
            drawTextCache[imageKey]?.let { textBitmap = it } ?: run {
                it.paint.isAntiAlias = true
                val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val lineMax = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        it.maxLines
                    } else {
                        try {
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
                textBitmap = Bitmap.createBitmap(
                    drawingBitmap.width,
                    drawingBitmap.height,
                    Bitmap.Config.ARGB_8888
                )
                val textCanvas = Canvas(textBitmap!!)
                textCanvas.translate(0f, ((drawingBitmap.height - layout.height) / 2).toFloat())
                layout.draw(textCanvas)
                drawTextCache[imageKey] = textBitmap!!
            }
        }

        textBitmap?.let { tb ->
            val paint = this.sharedValues.sharedPaint()
            paint.isAntiAlias = videoItem.antiAlias
            paint.alpha = (sprite.frameEntity.alpha * 255).toInt()
            if (sprite.frameEntity.maskPath != null) {
                val maskPath = sprite.frameEntity.maskPath ?: return@let
                canvas.save()
                canvas.concat(frameMatrix)
                canvas.clipRect(0, 0, drawingBitmap.width, drawingBitmap.height)
                paint.shader = BitmapShader(tb, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                val path = this.sharedValues.sharedPath()
                maskPath.buildPath(path)
                canvas.drawPath(path, paint)
                canvas.restore()
            } else {
                paint.isFilterBitmap = videoItem.antiAlias
                canvas.drawBitmap(tb, frameMatrix, paint)
            }
        }
    }

    private fun drawShape(sprite: SvgaDrawerSprite, canvas: Canvas) {
        val frameMatrix = obtainFrameMatrix(sprite.frameEntity.transform)
        sprite.frameEntity.shapes.forEach { shape ->
            shape.buildPath()
            shape.shapePath?.let {
                val paint = this.sharedValues.sharedPaint()
                paint.reset()
                paint.isAntiAlias = videoItem.antiAlias
                paint.alpha = (sprite.frameEntity.alpha * 255).toInt()
                val path = this.sharedValues.sharedPath()
                path.reset()
                path.addPath(this.pathCache.buildPath(shape))
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
                        if (sprite.frameEntity.maskPath != null) canvas.save()
                        sprite.frameEntity.maskPath?.let { maskPath ->
                            val path2 = this.sharedValues.sharedPath2()
                            maskPath.buildPath(path2)
                            path2.transform(frameMatrix)
                            canvas.clipPath(path2)
                        }
                        canvas.drawPath(path, paint)
                        if (sprite.frameEntity.maskPath != null) canvas.restore()
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
                            if (dash.size == 3 && (dash[0] > 0 || dash[1] > 0)) {
                                paint.pathEffect = DashPathEffect(
                                    floatArrayOf(
                                        dash[0].coerceAtLeast(1.0f) * scale,
                                        dash[1].coerceAtLeast(0.1f) * scale
                                    ), dash[2] * scale
                                )
                            }
                        }
                        if (sprite.frameEntity.maskPath != null) canvas.save()
                        sprite.frameEntity.maskPath?.let { maskPath ->
                            val path2 = this.sharedValues.sharedPath2()
                            maskPath.buildPath(path2)
                            path2.transform(frameMatrix)
                            canvas.clipPath(path2)
                        }
                        canvas.drawPath(path, paint)
                        if (sprite.frameEntity.maskPath != null) canvas.restore()
                    }
                }
            }
        }
    }

    private fun matrixScale(matrix: Matrix): Float {
        matrix.getValues(matrixScaleTempValues)
        if (matrixScaleTempValues[0] == 0f) return 0f
        var a = matrixScaleTempValues[0].toDouble()
        var b = matrixScaleTempValues[3].toDouble()
        var c = matrixScaleTempValues[1].toDouble()
        var d = matrixScaleTempValues[4].toDouble()
        if (a * d == b * c) return 0f
        val scaleX = Math.sqrt(a * a + b * b)
        a /= scaleX; b /= scaleX
        val skew = a * c + b * d
        c -= a * skew; d -= b * skew
        val scaleY = Math.sqrt(c * c + d * d)
        return if (scaleInfo.ratioX) Math.abs(scaleX.toFloat()) else Math.abs(scaleY.toFloat())
    }

    private fun drawDynamic(sprite: SvgaDrawerSprite, canvas: Canvas, frameIndex: Int) {
        val imageKey = sprite.imageKey ?: return
        dynamicItem.dynamicDrawer[imageKey]?.let {
            val frameMatrix = obtainFrameMatrix(sprite.frameEntity.transform)
            canvas.save()
            canvas.concat(frameMatrix)
            it.invoke(canvas, frameIndex)
            canvas.restore()
        }
        dynamicItem.dynamicDrawerSized[imageKey]?.let {
            val frameMatrix = obtainFrameMatrix(sprite.frameEntity.transform)
            canvas.save()
            canvas.concat(frameMatrix)
            it.invoke(
                canvas,
                frameIndex,
                sprite.frameEntity.layout.width.toInt(),
                sprite.frameEntity.layout.height.toInt()
            )
            canvas.restore()
        }
    }

    class ShareValues {
        private val sharedPaint = Paint()
        private val sharedPath = Path()
        private val sharedPath2 = Path()
        private val sharedMatrix = Matrix()
        private val sharedMatrix2 = Matrix()
        private val matteXfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        private val shareMattePaint = Paint().apply { xfermode = matteXfermode }
        private var sharedMatteBitmap: Bitmap? = null
        private var cachedMatteCanvas: Canvas? = null

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

        fun sharedMatteBitmap(): Bitmap {
            return sharedMatteBitmap!!
        }

        fun obtainMatteCanvas(width: Int, height: Int): Canvas {
            val w = width.coerceIn(1, 2048)
            val h = height.coerceIn(1, 2048)
            var bmp = sharedMatteBitmap
            if (bmp == null || bmp.isRecycled || bmp.width < w || bmp.height < h) {
                bmp?.recycle()
                bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
                sharedMatteBitmap = bmp
                cachedMatteCanvas = Canvas(bmp)
            }
            val canvas = cachedMatteCanvas ?: Canvas(bmp).also { cachedMatteCanvas = it }
            canvas.drawColor(0, PorterDuff.Mode.CLEAR)
            return canvas
        }
    }

    class PathCache {
        private var canvasWidth: Int = 0
        private var canvasHeight: Int = 0
        private val cache = HashMap<SvgaVideoShapeEntity, Path>()

        fun onSizeChanged(canvas: Canvas) {
            if (this.canvasWidth != canvas.width || this.canvasHeight != canvas.height) {
                this.cache.clear()
            }
            this.canvasWidth = canvas.width
            this.canvasHeight = canvas.height
        }

        fun buildPath(shape: SvgaVideoShapeEntity): Path {
            return cache.getOrPut(shape) {
                Path().apply { shape.shapePath?.let { set(it) } }
            }
        }
    }
}
