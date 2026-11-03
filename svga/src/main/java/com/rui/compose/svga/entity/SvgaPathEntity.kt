package com.rui.compose.svga.entity

import android.graphics.Path
import com.rui.compose.svga.utils.SvgaPoint
import java.util.StringTokenizer

class SvgaPathEntity(originValue: String) {

    companion object {
        private const val VALID_METHODS = "MLHVCSQRAZmlhvcsqraz"
    }

    private val replacedValue: String =
        if (originValue.contains(",")) originValue.replace(",", " ") else originValue

    @Volatile
    private var cachedPath: Path? = null

    private class PathContext {
        var currentX: Float = 0.0f
        var currentY: Float = 0.0f
    }

    fun buildPath(toPath: Path) {
        cachedPath?.let {
            toPath.set(it)
            return
        }
        synchronized(this) {
            cachedPath?.let {
                toPath.set(it)
                return
            }
            val localPath = Path()
            val context = PathContext()
            val segments = StringTokenizer(this.replacedValue, VALID_METHODS, true)
            var currentMethod = ""
            while (segments.hasMoreTokens()) {
                val segment = segments.nextToken()
                if (segment.isEmpty()) continue
                if (VALID_METHODS.contains(segment)) {
                    currentMethod = segment
                    if (currentMethod == "Z" || currentMethod == "z") {
                        operate(localPath, currentMethod, StringTokenizer("", ""), context)
                    }
                } else {
                    operate(localPath, currentMethod, StringTokenizer(segment, " "), context)
                }
            }
            this.cachedPath = localPath
            toPath.set(localPath)
        }
    }

    private fun operate(finalPath: Path, method: String, args: StringTokenizer, context: PathContext) {
        var x0 = 0.0f
        var y0 = 0.0f
        var x1 = 0.0f
        var y1 = 0.0f
        var x2 = 0.0f
        var y2 = 0.0f

        try {
            var index = 0
            while (args.hasMoreTokens()) {
                val s = args.nextToken()
                if (s.isEmpty()) continue
                if (index == 0) x0 = s.toFloat()
                if (index == 1) y0 = s.toFloat()
                if (index == 2) x1 = s.toFloat()
                if (index == 3) y1 = s.toFloat()
                if (index == 4) x2 = s.toFloat()
                if (index == 5) y2 = s.toFloat()
                index++
            }
        } catch (_: Exception) {
        }

        when (method) {
            "M" -> {
                finalPath.moveTo(x0, y0)
                context.currentX = x0
                context.currentY = y0
            }
            "m" -> {
                finalPath.rMoveTo(x0, y0)
                context.currentX += x0
                context.currentY += y0
            }
            "L" -> {
                finalPath.lineTo(x0, y0)
                context.currentX = x0
                context.currentY = y0
            }
            "l" -> {
                finalPath.rLineTo(x0, y0)
                context.currentX += x0
                context.currentY += y0
            }
            "C" -> {
                finalPath.cubicTo(x0, y0, x1, y1, x2, y2)
                context.currentX = x2
                context.currentY = y2
            }
            "c" -> {
                finalPath.rCubicTo(x0, y0, x1, y1, x2, y2)
                context.currentX += x2
                context.currentY += y2
            }
            "Q" -> {
                finalPath.quadTo(x0, y0, x1, y1)
                context.currentX = x1
                context.currentY = y1
            }
            "q" -> {
                finalPath.rQuadTo(x0, y0, x1, y1)
                context.currentX += x1
                context.currentY += y1
            }
            "H" -> {
                finalPath.lineTo(x0, context.currentY)
                context.currentX = x0
            }
            "h" -> {
                finalPath.rLineTo(x0, 0f)
                context.currentX += x0
            }
            "V" -> {
                finalPath.lineTo(context.currentX, x0)
                context.currentY = x0
            }
            "v" -> {
                finalPath.rLineTo(0f, x0)
                context.currentY += x0
            }
            "Z", "z" -> {
                finalPath.close()
            }
        }
    }
}
