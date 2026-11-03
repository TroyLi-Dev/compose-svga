package com.rui.compose.svga.entity

import android.graphics.Path
import java.util.StringTokenizer

class SvgaPathEntity(originValue: String) {

    companion object {
        private const val VALID_METHODS = "MLHVCSQRAZmlhvcsqraz"
        private val NEGATIVE_SIGN_REGEX = Regex("(?<![eE\\sMLHVCSQRAZmlhvcsqraz])\\-")
    }

    private val replacedValue: String = run {
        var str = originValue
        if (str.contains(",")) str = str.replace(",", " ")
        if (str.contains("-")) {
            str = str.replace(NEGATIVE_SIGN_REGEX, " -")
        }
        str
    }

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

    private fun safeFloat(s: String): Float {
        val f = s.toFloatOrNull() ?: 0f
        return if (f.isFinite()) f else 0f
    }

    private fun operate(finalPath: Path, method: String, args: StringTokenizer, context: PathContext) {
        val tokens = ArrayList<Float>()
        try {
            while (args.hasMoreTokens()) {
                val s = args.nextToken()
                if (s.isEmpty()) continue
                tokens.add(safeFloat(s))
            }
        } catch (_: Throwable) {}

        var i = 0
        val size = tokens.size

        when (method) {
            "M" -> {
                if (i + 1 < size) {
                    val x = tokens[i++]
                    val y = tokens[i++]
                    finalPath.moveTo(x, y)
                    context.currentX = x
                    context.currentY = y
                }
                while (i + 1 < size) {
                    val x = tokens[i++]
                    val y = tokens[i++]
                    finalPath.lineTo(x, y)
                    context.currentX = x
                    context.currentY = y
                }
            }
            "m" -> {
                if (i + 1 < size) {
                    val x = tokens[i++]
                    val y = tokens[i++]
                    finalPath.rMoveTo(x, y)
                    context.currentX += x
                    context.currentY += y
                }
                while (i + 1 < size) {
                    val x = tokens[i++]
                    val y = tokens[i++]
                    finalPath.rLineTo(x, y)
                    context.currentX += x
                    context.currentY += y
                }
            }
            "L" -> {
                while (i + 1 < size) {
                    val x = tokens[i++]
                    val y = tokens[i++]
                    finalPath.lineTo(x, y)
                    context.currentX = x
                    context.currentY = y
                }
            }
            "l" -> {
                while (i + 1 < size) {
                    val x = tokens[i++]
                    val y = tokens[i++]
                    finalPath.rLineTo(x, y)
                    context.currentX += x
                    context.currentY += y
                }
            }
            "H" -> {
                while (i < size) {
                    val x = tokens[i++]
                    finalPath.lineTo(x, context.currentY)
                    context.currentX = x
                }
            }
            "h" -> {
                while (i < size) {
                    val x = tokens[i++]
                    finalPath.rLineTo(x, 0f)
                    context.currentX += x
                }
            }
            "V" -> {
                while (i < size) {
                    val y = tokens[i++]
                    finalPath.lineTo(context.currentX, y)
                    context.currentY = y
                }
            }
            "v" -> {
                while (i < size) {
                    val y = tokens[i++]
                    finalPath.rLineTo(0f, y)
                    context.currentY += y
                }
            }
            "C" -> {
                while (i + 5 < size) {
                    val x0 = tokens[i++]
                    val y0 = tokens[i++]
                    val x1 = tokens[i++]
                    val y1 = tokens[i++]
                    val x2 = tokens[i++]
                    val y2 = tokens[i++]
                    finalPath.cubicTo(x0, y0, x1, y1, x2, y2)
                    context.currentX = x2
                    context.currentY = y2
                }
            }
            "c" -> {
                while (i + 5 < size) {
                    val x0 = tokens[i++]
                    val y0 = tokens[i++]
                    val x1 = tokens[i++]
                    val y1 = tokens[i++]
                    val x2 = tokens[i++]
                    val y2 = tokens[i++]
                    finalPath.rCubicTo(x0, y0, x1, y1, x2, y2)
                    context.currentX += x2
                    context.currentY += y2
                }
            }
            "Q" -> {
                while (i + 3 < size) {
                    val x0 = tokens[i++]
                    val y0 = tokens[i++]
                    val x1 = tokens[i++]
                    val y1 = tokens[i++]
                    finalPath.quadTo(x0, y0, x1, y1)
                    context.currentX = x1
                    context.currentY = y1
                }
            }
            "q" -> {
                while (i + 3 < size) {
                    val x0 = tokens[i++]
                    val y0 = tokens[i++]
                    val x1 = tokens[i++]
                    val y1 = tokens[i++]
                    finalPath.rQuadTo(x0, y0, x1, y1)
                    context.currentX += x1
                    context.currentY += y1
                }
            }
            "Z", "z" -> {
                finalPath.close()
            }
        }
    }
}
