package com.rui.compose.svga.entity

import android.graphics.Path
import java.util.StringTokenizer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class SvgaPathEntity(originValue: String) {

    companion object {
        private const val VALID_METHODS = "MLHVCSQRTAZmlhvcsqrtaz"

        /**
         * W3C SVG 规范标准椭圆弧 (Elliptical Arc) 转三次贝塞尔曲线分解算法
         */
        private fun drawArc(
            path: Path,
            x0: Float,
            y0: Float,
            x1: Float,
            y1: Float,
            rxInput: Float,
            ryInput: Float,
            angle: Float,
            largeArcFlag: Boolean,
            sweepFlag: Boolean
        ) {
            if (x0 == x1 && y0 == y1) return
            var rx = abs(rxInput)
            var ry = abs(ryInput)
            if (rx <= 0f || ry <= 0f) {
                path.lineTo(x1, y1)
                return
            }

            val phi = Math.toRadians(angle.toDouble())
            val cosPhi = cos(phi)
            val sinPhi = sin(phi)

            val dx2 = (x0 - x1) / 2.0
            val dy2 = (y0 - y1) / 2.0
            val x1Prime = cosPhi * dx2 + sinPhi * dy2
            val y1Prime = -sinPhi * dx2 + cosPhi * dy2

            var rxSq = (rx * rx).toDouble()
            var rySq = (ry * ry).toDouble()
            val x1PrimeSq = x1Prime * x1Prime
            val y1PrimeSq = y1Prime * y1Prime

            val radiiCheck = x1PrimeSq / rxSq + y1PrimeSq / rySq
            if (radiiCheck > 1.0) {
                val scale = sqrt(radiiCheck)
                rx = (rx * scale).toFloat()
                ry = (ry * scale).toFloat()
                rxSq = (rx * rx).toDouble()
                rySq = (ry * ry).toDouble()
            }

            val sign = if (largeArcFlag == sweepFlag) -1.0 else 1.0
            val num = rxSq * rySq - rxSq * y1PrimeSq - rySq * x1PrimeSq
            val denom = rxSq * y1PrimeSq + rySq * x1PrimeSq
            val sq = if (denom > 0.0) (num / denom).coerceAtLeast(0.0) else 0.0
            val coef = sign * sqrt(sq)
            val cxPrime = coef * ((rx * y1Prime) / ry)
            val cyPrime = coef * (-(ry * x1Prime) / rx)

            val cx = cosPhi * cxPrime - sinPhi * cyPrime + (x0 + x1) / 2.0
            val cy = sinPhi * cxPrime + cosPhi * cyPrime + (y0 + y1) / 2.0

            val ux = (x1Prime - cxPrime) / rx
            val uy = (y1Prime - cyPrime) / ry
            val vx = (-x1Prime - cxPrime) / rx
            val vy = (-y1Prime - cyPrime) / ry

            val startAngle = Math.toDegrees(atan2(uy, ux))
            var sweepAngle = Math.toDegrees(atan2(ux * vy - uy * vx, ux * vx + uy * vy))

            if (!sweepFlag && sweepAngle > 0) {
                sweepAngle -= 360.0
            } else if (sweepFlag && sweepAngle < 0) {
                sweepAngle += 360.0
            }

            val numSegments = ceil(abs(sweepAngle) / 90.0).toInt().coerceAtLeast(1)
            val anglePerSegment = sweepAngle / numSegments
            var currentAngle = startAngle

            for (seg in 0 until numSegments) {
                val nextAngle = currentAngle + anglePerSegment
                val theta1 = Math.toRadians(currentAngle)
                val theta2 = Math.toRadians(nextAngle)
                val deltaTheta = theta2 - theta1

                val halfDelta = deltaTheta / 2.0
                val tanHalf = tan(halfDelta)
                val alpha = sin(deltaTheta) * (sqrt(4.0 + 3.0 * tanHalf * tanHalf) - 1.0) / 3.0

                val cosTheta1 = cos(theta1)
                val sinTheta1 = sin(theta1)
                val cosTheta2 = cos(theta2)
                val sinTheta2 = sin(theta2)

                val ep1x = cosPhi * rx * cosTheta1 - sinPhi * ry * sinTheta1 + cx
                val ep1y = sinPhi * rx * cosTheta1 + cosPhi * ry * sinTheta1 + cy

                val ep2x = cosPhi * rx * cosTheta2 - sinPhi * ry * sinTheta2 + cx
                val ep2y = sinPhi * rx * cosTheta2 + cosPhi * ry * sinTheta2 + cy

                val d1x = -cosPhi * rx * sinTheta1 - sinPhi * ry * cosTheta1
                val d1y = -sinPhi * rx * sinTheta1 + cosPhi * ry * cosTheta1

                val d2x = -cosPhi * rx * sinTheta2 - sinPhi * ry * cosTheta2
                val d2y = -sinPhi * rx * sinTheta2 + cosPhi * ry * cosTheta2

                val cp1x = ep1x + alpha * d1x
                val cp1y = ep1y + alpha * d1y
                val cp2x = ep2x - alpha * d2x
                val cp2y = ep2y - alpha * d2y

                val endX = if (seg == numSegments - 1) x1 else ep2x.toFloat()
                val endY = if (seg == numSegments - 1) y1 else ep2y.toFloat()

                path.cubicTo(
                    cp1x.toFloat(), cp1y.toFloat(),
                    cp2x.toFloat(), cp2y.toFloat(),
                    endX, endY
                )
                currentAngle = nextAngle
            }
        }
    }

    private val replacedValue: String = run {
        val len = originValue.length
        val sb = java.lang.StringBuilder(len + (len shr 3))
        var hasDot = false
        var lastChar = ' '

        for (i in 0 until len) {
            val c = originValue[i]
            when {
                c == ',' || c == ' ' || c == '\t' || c == '\r' || c == '\n' -> {
                    if (lastChar != ' ') {
                        sb.append(' ')
                        lastChar = ' '
                    }
                    hasDot = false
                }
                c == '-' -> {
                    if (lastChar != ' ' && lastChar != 'e' && lastChar != 'E' && VALID_METHODS.indexOf(lastChar) < 0) {
                        sb.append(' ')
                    }
                    sb.append('-')
                    lastChar = '-'
                    hasDot = false
                }
                c == '.' -> {
                    if (hasDot) {
                        sb.append(' ')
                    }
                    sb.append('.')
                    lastChar = '.'
                    hasDot = true
                }
                VALID_METHODS.indexOf(c) >= 0 -> {
                    hasDot = false
                    sb.append(c)
                    lastChar = c
                }
                else -> {
                    sb.append(c)
                    lastChar = c
                }
            }
        }
        sb.toString()
    }

    @Volatile
    private var cachedPath: Path? = null

    private class PathContext {
        var currentX: Float = 0.0f
        var currentY: Float = 0.0f
        var lastControlX: Float = 0.0f
        var lastControlY: Float = 0.0f
        var lastMethod: String = ""
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
                    operate(localPath, currentMethod, StringTokenizer(segment, " \t\r\n"), context)
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
                    context.lastControlX = x
                    context.lastControlY = y
                    context.lastMethod = "M"
                }
                while (i + 1 < size) {
                    val x = tokens[i++]
                    val y = tokens[i++]
                    finalPath.lineTo(x, y)
                    context.currentX = x
                    context.currentY = y
                    context.lastControlX = x
                    context.lastControlY = y
                    context.lastMethod = "M"
                }
            }
            "m" -> {
                if (i + 1 < size) {
                    val x = tokens[i++]
                    val y = tokens[i++]
                    finalPath.rMoveTo(x, y)
                    context.currentX += x
                    context.currentY += y
                    context.lastControlX = context.currentX
                    context.lastControlY = context.currentY
                    context.lastMethod = "m"
                }
                while (i + 1 < size) {
                    val x = tokens[i++]
                    val y = tokens[i++]
                    finalPath.rLineTo(x, y)
                    context.currentX += x
                    context.currentY += y
                    context.lastControlX = context.currentX
                    context.lastControlY = context.currentY
                    context.lastMethod = "m"
                }
            }
            "L" -> {
                while (i + 1 < size) {
                    val x = tokens[i++]
                    val y = tokens[i++]
                    finalPath.lineTo(x, y)
                    context.currentX = x
                    context.currentY = y
                    context.lastControlX = x
                    context.lastControlY = y
                    context.lastMethod = "L"
                }
            }
            "l" -> {
                while (i + 1 < size) {
                    val x = tokens[i++]
                    val y = tokens[i++]
                    finalPath.rLineTo(x, y)
                    context.currentX += x
                    context.currentY += y
                    context.lastControlX = context.currentX
                    context.lastControlY = context.currentY
                    context.lastMethod = "l"
                }
            }
            "H" -> {
                while (i < size) {
                    val x = tokens[i++]
                    finalPath.lineTo(x, context.currentY)
                    context.currentX = x
                    context.lastControlX = x
                    context.lastControlY = context.currentY
                    context.lastMethod = "H"
                }
            }
            "h" -> {
                while (i < size) {
                    val x = tokens[i++]
                    finalPath.rLineTo(x, 0f)
                    context.currentX += x
                    context.lastControlX = context.currentX
                    context.lastControlY = context.currentY
                    context.lastMethod = "h"
                }
            }
            "V" -> {
                while (i < size) {
                    val y = tokens[i++]
                    finalPath.lineTo(context.currentX, y)
                    context.currentY = y
                    context.lastControlX = context.currentX
                    context.lastControlY = y
                    context.lastMethod = "V"
                }
            }
            "v" -> {
                while (i < size) {
                    val y = tokens[i++]
                    finalPath.rLineTo(0f, y)
                    context.currentY += y
                    context.lastControlX = context.currentX
                    context.lastControlY = context.currentY
                    context.lastMethod = "v"
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
                    context.lastControlX = x1
                    context.lastControlY = y1
                    context.currentX = x2
                    context.currentY = y2
                    context.lastMethod = "C"
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
                    context.lastControlX = context.currentX + x1
                    context.lastControlY = context.currentY + y1
                    context.currentX += x2
                    context.currentY += y2
                    context.lastMethod = "c"
                }
            }
            "S" -> {
                while (i + 3 < size) {
                    val x1 = tokens[i++]
                    val y1 = tokens[i++]
                    val x2 = tokens[i++]
                    val y2 = tokens[i++]
                    val x0 = if (context.lastMethod == "C" || context.lastMethod == "c" || context.lastMethod == "S" || context.lastMethod == "s") {
                        2f * context.currentX - context.lastControlX
                    } else {
                        context.currentX
                    }
                    val y0 = if (context.lastMethod == "C" || context.lastMethod == "c" || context.lastMethod == "S" || context.lastMethod == "s") {
                        2f * context.currentY - context.lastControlY
                    } else {
                        context.currentY
                    }
                    finalPath.cubicTo(x0, y0, x1, y1, x2, y2)
                    context.lastControlX = x1
                    context.lastControlY = y1
                    context.currentX = x2
                    context.currentY = y2
                    context.lastMethod = "S"
                }
            }
            "s" -> {
                while (i + 3 < size) {
                    val dx1 = tokens[i++]
                    val dy1 = tokens[i++]
                    val dx2 = tokens[i++]
                    val dy2 = tokens[i++]
                    val x0 = if (context.lastMethod == "C" || context.lastMethod == "c" || context.lastMethod == "S" || context.lastMethod == "s") {
                        2f * context.currentX - context.lastControlX
                    } else {
                        context.currentX
                    }
                    val y0 = if (context.lastMethod == "C" || context.lastMethod == "c" || context.lastMethod == "S" || context.lastMethod == "s") {
                        2f * context.currentY - context.lastControlY
                    } else {
                        context.currentY
                    }
                    val x1 = context.currentX + dx1
                    val y1 = context.currentY + dy1
                    val x2 = context.currentX + dx2
                    val y2 = context.currentY + dy2
                    finalPath.cubicTo(x0, y0, x1, y1, x2, y2)
                    context.lastControlX = x1
                    context.lastControlY = y1
                    context.currentX = x2
                    context.currentY = y2
                    context.lastMethod = "s"
                }
            }
            "Q" -> {
                while (i + 3 < size) {
                    val x0 = tokens[i++]
                    val y0 = tokens[i++]
                    val x1 = tokens[i++]
                    val y1 = tokens[i++]
                    finalPath.quadTo(x0, y0, x1, y1)
                    context.lastControlX = x0
                    context.lastControlY = y0
                    context.currentX = x1
                    context.currentY = y1
                    context.lastMethod = "Q"
                }
            }
            "q" -> {
                while (i + 3 < size) {
                    val x0 = tokens[i++]
                    val y0 = tokens[i++]
                    val x1 = tokens[i++]
                    val y1 = tokens[i++]
                    finalPath.rQuadTo(x0, y0, x1, y1)
                    context.lastControlX = context.currentX + x0
                    context.lastControlY = context.currentY + y0
                    context.currentX += x1
                    context.currentY += y1
                    context.lastMethod = "q"
                }
            }
            "T" -> {
                while (i + 1 < size) {
                    val x1 = tokens[i++]
                    val y1 = tokens[i++]
                    val x0 = if (context.lastMethod == "Q" || context.lastMethod == "q" || context.lastMethod == "T" || context.lastMethod == "t") {
                        2f * context.currentX - context.lastControlX
                    } else {
                        context.currentX
                    }
                    val y0 = if (context.lastMethod == "Q" || context.lastMethod == "q" || context.lastMethod == "T" || context.lastMethod == "t") {
                        2f * context.currentY - context.lastControlY
                    } else {
                        context.currentY
                    }
                    finalPath.quadTo(x0, y0, x1, y1)
                    context.lastControlX = x0
                    context.lastControlY = y0
                    context.currentX = x1
                    context.currentY = y1
                    context.lastMethod = "T"
                }
            }
            "t" -> {
                while (i + 1 < size) {
                    val dx1 = tokens[i++]
                    val dy1 = tokens[i++]
                    val x0 = if (context.lastMethod == "Q" || context.lastMethod == "q" || context.lastMethod == "T" || context.lastMethod == "t") {
                        2f * context.currentX - context.lastControlX
                    } else {
                        context.currentX
                    }
                    val y0 = if (context.lastMethod == "Q" || context.lastMethod == "q" || context.lastMethod == "T" || context.lastMethod == "t") {
                        2f * context.currentY - context.lastControlY
                    } else {
                        context.currentY
                    }
                    val x1 = context.currentX + dx1
                    val y1 = context.currentY + dy1
                    finalPath.quadTo(x0, y0, x1, y1)
                    context.lastControlX = x0
                    context.lastControlY = y0
                    context.currentX = x1
                    context.currentY = y1
                    context.lastMethod = "t"
                }
            }
            "A" -> {
                while (i + 6 < size) {
                    val rx = tokens[i++]
                    val ry = tokens[i++]
                    val angle = tokens[i++]
                    val largeArcFlag = tokens[i++] != 0f
                    val sweepFlag = tokens[i++] != 0f
                    val x = tokens[i++]
                    val y = tokens[i++]
                    drawArc(finalPath, context.currentX, context.currentY, x, y, rx, ry, angle, largeArcFlag, sweepFlag)
                    context.currentX = x
                    context.currentY = y
                    context.lastControlX = x
                    context.lastControlY = y
                    context.lastMethod = "A"
                }
            }
            "a" -> {
                while (i + 6 < size) {
                    val rx = tokens[i++]
                    val ry = tokens[i++]
                    val angle = tokens[i++]
                    val largeArcFlag = tokens[i++] != 0f
                    val sweepFlag = tokens[i++] != 0f
                    val dx = tokens[i++]
                    val dy = tokens[i++]
                    val x = context.currentX + dx
                    val y = context.currentY + dy
                    drawArc(finalPath, context.currentX, context.currentY, x, y, rx, ry, angle, largeArcFlag, sweepFlag)
                    context.currentX = x
                    context.currentY = y
                    context.lastControlX = x
                    context.lastControlY = y
                    context.lastMethod = "a"
                }
            }
            "Z", "z" -> {
                finalPath.close()
                context.lastControlX = context.currentX
                context.lastControlY = context.currentY
                context.lastMethod = "Z"
            }
        }
    }
}
