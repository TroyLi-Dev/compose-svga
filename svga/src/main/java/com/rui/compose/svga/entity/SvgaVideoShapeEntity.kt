package com.rui.compose.svga.entity

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import com.rui.compose.svga.proto.ShapeEntity
import org.json.JSONArray
import org.json.JSONObject

class SvgaVideoShapeEntity {

    enum class Type {
        shape,
        rect,
        ellipse,
        keep
    }

    class Styles {
        var fill = 0x00000000
            internal set
        var stroke = 0x00000000
            internal set
        var strokeWidth = 0.0f
            internal set
        var lineCap = "butt"
            internal set
        var lineJoin = "miter"
            internal set
        var miterLimit = 0
            internal set
        var lineDash = FloatArray(0)
            internal set
        var paintCap: Paint.Cap = Paint.Cap.BUTT
            internal set
        var paintJoin: Paint.Join = Paint.Join.MITER
            internal set
        @Volatile
        var cachedDashEffect: android.graphics.DashPathEffect? = null
            internal set
        @Volatile
        var cachedDashScale: Float = -1f
            internal set
    }

    var type = Type.shape
        private set

    var args: Map<String, Any>? = null
        private set

    var styles: Styles? = null
        private set

    var transform: Matrix? = null
        private set

    val isKeep: Boolean
        get() = type == Type.keep

    @Volatile
    var shapePath: Path? = null

    constructor(obj: JSONObject) {
        parseType(obj)
        parseArgs(obj)
        parseStyles(obj)
        parseTransform(obj)
    }

    constructor(obj: ShapeEntity) {
        parseType(obj)
        parseArgs(obj)
        parseStyles(obj)
        parseTransform(obj)
    }

    private fun parseType(obj: JSONObject) {
        obj.optString("type").let {
            when {
                it.equals("shape", ignoreCase = true) -> type = Type.shape
                it.equals("rect", ignoreCase = true) -> type = Type.rect
                it.equals("ellipse", ignoreCase = true) -> type = Type.ellipse
                it.equals("keep", ignoreCase = true) -> type = Type.keep
            }
        }
    }

    private fun parseType(obj: ShapeEntity) {
        type = when (obj.type) {
            ShapeEntity.ShapeType.SHAPE -> Type.shape
            ShapeEntity.ShapeType.RECT -> Type.rect
            ShapeEntity.ShapeType.ELLIPSE -> Type.ellipse
            ShapeEntity.ShapeType.KEEP -> Type.keep
        }
    }

    private fun parseArgs(obj: JSONObject) {
        val args = HashMap<String, Any>()
        obj.optJSONObject("args")?.let { values ->
            values.keys().forEach { key ->
                values.opt(key)?.let {
                    args[key] = it
                }
            }
            this.args = args
        }
    }

    private fun parseArgs(obj: ShapeEntity) {
        val args = HashMap<String, Any>()
        obj.shape?.let {
            if (it.d.isNotEmpty()) args["d"] = it.d
        }
        obj.ellipse?.let {
            args["x"] = it.x
            args["y"] = it.y
            args["radiusX"] = it.radiusX
            args["radiusY"] = it.radiusY
        }
        obj.rect?.let {
            args["x"] = it.x
            args["y"] = it.y
            args["width"] = it.width
            args["height"] = it.height
            args["cornerRadius"] = it.cornerRadius
        }
        this.args = args
    }

    private fun checkValueRange(obj: JSONArray): Float {
        return if (obj.optDouble(0) <= 1 && obj.optDouble(1) <= 1 && obj.optDouble(2) <= 1) 255f else 1f
    }

    private fun checkAlphaValueRange(obj: JSONArray): Float {
        return if (obj.optDouble(3) <= 1) 255f else 1f
    }

    private fun parseStyles(obj: JSONObject) {
        obj.optJSONObject("styles")?.let {
            val styles = Styles()
            it.optJSONArray("fill")?.let { arr ->
                if (arr.length() == 4) {
                    val mulValue = checkValueRange(arr)
                    val alphaRangeValue = checkAlphaValueRange(arr)
                    styles.fill = Color.argb(
                        (arr.optDouble(3) * alphaRangeValue).toInt().coerceIn(0, 255),
                        (arr.optDouble(0) * mulValue).toInt().coerceIn(0, 255),
                        (arr.optDouble(1) * mulValue).toInt().coerceIn(0, 255),
                        (arr.optDouble(2) * mulValue).toInt().coerceIn(0, 255)
                    )
                }
            }
            it.optJSONArray("stroke")?.let { arr ->
                if (arr.length() == 4) {
                    val mulValue = checkValueRange(arr)
                    val alphaRangeValue = checkAlphaValueRange(arr)
                    styles.stroke = Color.argb(
                        (arr.optDouble(3) * alphaRangeValue).toInt().coerceIn(0, 255),
                        (arr.optDouble(0) * mulValue).toInt().coerceIn(0, 255),
                        (arr.optDouble(1) * mulValue).toInt().coerceIn(0, 255),
                        (arr.optDouble(2) * mulValue).toInt().coerceIn(0, 255)
                    )
                }
            }
            styles.strokeWidth = it.optDouble("strokeWidth", 0.0).toFloat()
            styles.lineCap = it.optString("lineCap", "butt")
            styles.paintCap = when {
                styles.lineCap.equals("round", true) -> Paint.Cap.ROUND
                styles.lineCap.equals("square", true) -> Paint.Cap.SQUARE
                else -> Paint.Cap.BUTT
            }
            styles.lineJoin = it.optString("lineJoin", "miter")
            styles.paintJoin = when {
                styles.lineJoin.equals("bevel", true) -> Paint.Join.BEVEL
                styles.lineJoin.equals("round", true) -> Paint.Join.ROUND
                else -> Paint.Join.MITER
            }
            styles.miterLimit = it.optInt("miterLimit", 0)
            it.optJSONArray("lineDash")?.let { dashes ->
                styles.lineDash = FloatArray(dashes.length())
                for (i in 0 until dashes.length()) {
                    styles.lineDash[i] = dashes.optDouble(i, 0.0).toFloat()
                }
            }
            this.styles = styles
        }
    }

    private fun checkValueRange(color: ShapeEntity.ShapeStyle.RGBAColor): Float {
        return if (color.r <= 1 && color.g <= 1 && color.b <= 1) 255f else 1f
    }

    private fun checkAlphaValueRange(color: ShapeEntity.ShapeStyle.RGBAColor): Float {
        return if (color.a <= 1f) 255f else 1f
    }

    private fun parseStyles(obj: ShapeEntity) {
        obj.styles?.let {
            val styles = Styles()
            it.fill?.let { color ->
                val mulValue = checkValueRange(color)
                val alphaRangeValue = checkAlphaValueRange(color)
                styles.fill = Color.argb(
                    (color.a * alphaRangeValue).toInt().coerceIn(0, 255),
                    (color.r * mulValue).toInt().coerceIn(0, 255),
                    (color.g * mulValue).toInt().coerceIn(0, 255),
                    (color.b * mulValue).toInt().coerceIn(0, 255)
                )
            }
            it.stroke?.let { color ->
                val mulValue = checkValueRange(color)
                val alphaRangeValue = checkAlphaValueRange(color)
                styles.stroke = Color.argb(
                    (color.a * alphaRangeValue).toInt().coerceIn(0, 255),
                    (color.r * mulValue).toInt().coerceIn(0, 255),
                    (color.g * mulValue).toInt().coerceIn(0, 255),
                    (color.b * mulValue).toInt().coerceIn(0, 255)
                )
            }
            styles.strokeWidth = it.strokeWidth
            when (it.lineCap) {
                ShapeEntity.ShapeStyle.LineCap.LineCap_ROUND -> {
                    styles.lineCap = "round"
                    styles.paintCap = Paint.Cap.ROUND
                }
                ShapeEntity.ShapeStyle.LineCap.LineCap_SQUARE -> {
                    styles.lineCap = "square"
                    styles.paintCap = Paint.Cap.SQUARE
                }
                else -> {
                    styles.lineCap = "butt"
                    styles.paintCap = Paint.Cap.BUTT
                }
            }
            when (it.lineJoin) {
                ShapeEntity.ShapeStyle.LineJoin.LineJoin_BEVEL -> {
                    styles.lineJoin = "bevel"
                    styles.paintJoin = Paint.Join.BEVEL
                }
                ShapeEntity.ShapeStyle.LineJoin.LineJoin_ROUND -> {
                    styles.lineJoin = "round"
                    styles.paintJoin = Paint.Join.ROUND
                }
                else -> {
                    styles.lineJoin = "miter"
                    styles.paintJoin = Paint.Join.MITER
                }
            }
            styles.miterLimit = it.miterLimit.toInt()
            styles.lineDash = floatArrayOf(it.lineDashI, it.lineDashII, it.lineDashIII)
            this.styles = styles
        }
    }

    private fun parseTransform(obj: JSONObject) {
        obj.optJSONObject("transform")?.let {
            val a = it.optDouble("a", 1.0).toFloat()
            val b = it.optDouble("b", 0.0).toFloat()
            val c = it.optDouble("c", 0.0).toFloat()
            val d = it.optDouble("d", 1.0).toFloat()
            val tx = it.optDouble("tx", 0.0).toFloat()
            val ty = it.optDouble("ty", 0.0).toFloat()
            if (a.isFinite() && b.isFinite() && c.isFinite() && d.isFinite() && tx.isFinite() && ty.isFinite()) {
                val transform = Matrix()
                val arr = FloatArray(9)
                arr[0] = a
                arr[1] = c
                arr[2] = tx
                arr[3] = b
                arr[4] = d
                arr[5] = ty
                arr[6] = 0.0f
                arr[7] = 0.0f
                arr[8] = 1.0f
                transform.setValues(arr)
                this.transform = transform
            }
        }
    }

    private fun parseTransform(obj: ShapeEntity) {
        obj.transform?.let {
            if (it.a.isFinite() && it.b.isFinite() && it.c.isFinite() && it.d.isFinite() && it.tx.isFinite() && it.ty.isFinite()) {
                val transform = Matrix()
                val arr = FloatArray(9)
                arr[0] = it.a
                arr[1] = it.c
                arr[2] = it.tx
                arr[3] = it.b
                arr[4] = it.d
                arr[5] = it.ty
                arr[6] = 0.0f
                arr[7] = 0.0f
                arr[8] = 1.0f
                transform.setValues(arr)
                this.transform = transform
            }
        }
    }

    fun buildPath() {
        if (this.shapePath != null) return
        synchronized(this) {
            if (this.shapePath != null) return
            val localPath = Path()
            when (this.type) {
                Type.shape -> {
                    (this.args?.get("d") as? String)?.let {
                        SvgaPathEntity(it).buildPath(localPath)
                    }
                }
                Type.ellipse -> {
                    val x = (this.args?.get("x") as? Number)?.toFloat() ?: 0f
                    val y = (this.args?.get("y") as? Number)?.toFloat() ?: 0f
                    val rx = (this.args?.get("radiusX") as? Number)?.toFloat() ?: return
                    val ry = (this.args?.get("radiusY") as? Number)?.toFloat() ?: return
                    if (rx <= 0f || ry <= 0f || !x.isFinite() || !y.isFinite() || !rx.isFinite() || !ry.isFinite()) return
                    localPath.addOval(x - rx, y - ry, x + rx, y + ry, Path.Direction.CW)
                }
                Type.rect -> {
                    val x = (this.args?.get("x") as? Number)?.toFloat() ?: 0f
                    val y = (this.args?.get("y") as? Number)?.toFloat() ?: 0f
                    val width = (this.args?.get("width") as? Number)?.toFloat() ?: return
                    val height = (this.args?.get("height") as? Number)?.toFloat() ?: return
                    val cornerRadius = (this.args?.get("cornerRadius") as? Number)?.toFloat() ?: 0f
                    if (width <= 0f || height <= 0f || !x.isFinite() || !y.isFinite() || !width.isFinite() || !height.isFinite()) return
                    if (cornerRadius > 0f) {
                        localPath.addRoundRect(
                            x,
                            y,
                            x + width,
                            y + height,
                            cornerRadius,
                            cornerRadius,
                            Path.Direction.CW
                        )
                    } else {
                        localPath.addRect(
                            x,
                            y,
                            x + width,
                            y + height,
                            Path.Direction.CW
                        )
                    }
                }
                Type.keep -> {}
            }
            this.shapePath = localPath
        }
    }
}
