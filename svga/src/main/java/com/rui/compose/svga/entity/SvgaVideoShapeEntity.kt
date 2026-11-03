package com.rui.compose.svga.entity

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
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
                        (arr.optDouble(3) * alphaRangeValue).toInt(),
                        (arr.optDouble(0) * mulValue).toInt(),
                        (arr.optDouble(1) * mulValue).toInt(),
                        (arr.optDouble(2) * mulValue).toInt()
                    )
                }
            }
            it.optJSONArray("stroke")?.let { arr ->
                if (arr.length() == 4) {
                    val mulValue = checkValueRange(arr)
                    val alphaRangeValue = checkAlphaValueRange(arr)
                    styles.stroke = Color.argb(
                        (arr.optDouble(3) * alphaRangeValue).toInt(),
                        (arr.optDouble(0) * mulValue).toInt(),
                        (arr.optDouble(1) * mulValue).toInt(),
                        (arr.optDouble(2) * mulValue).toInt()
                    )
                }
            }
            styles.strokeWidth = it.optDouble("strokeWidth", 0.0).toFloat()
            styles.lineCap = it.optString("lineCap", "butt")
            styles.lineJoin = it.optString("lineJoin", "miter")
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
                    (color.a * alphaRangeValue).toInt(),
                    (color.r * mulValue).toInt(),
                    (color.g * mulValue).toInt(),
                    (color.b * mulValue).toInt()
                )
            }
            it.stroke?.let { color ->
                val mulValue = checkValueRange(color)
                val alphaRangeValue = checkAlphaValueRange(color)
                styles.stroke = Color.argb(
                    (color.a * alphaRangeValue).toInt(),
                    (color.r * mulValue).toInt(),
                    (color.g * mulValue).toInt(),
                    (color.b * mulValue).toInt()
                )
            }
            styles.strokeWidth = it.strokeWidth
            when (it.lineCap) {
                ShapeEntity.ShapeStyle.LineCap.LineCap_BUTT -> styles.lineCap = "butt"
                ShapeEntity.ShapeStyle.LineCap.LineCap_ROUND -> styles.lineCap = "round"
                ShapeEntity.ShapeStyle.LineCap.LineCap_SQUARE -> styles.lineCap = "square"
            }
            when (it.lineJoin) {
                ShapeEntity.ShapeStyle.LineJoin.LineJoin_BEVEL -> styles.lineJoin = "bevel"
                ShapeEntity.ShapeStyle.LineJoin.LineJoin_MITER -> styles.lineJoin = "miter"
                ShapeEntity.ShapeStyle.LineJoin.LineJoin_ROUND -> styles.lineJoin = "round"
            }
            styles.miterLimit = it.miterLimit.toInt()
            styles.lineDash = floatArrayOf(it.lineDashI, it.lineDashII, it.lineDashIII)
            this.styles = styles
        }
    }

    private fun parseTransform(obj: JSONObject) {
        obj.optJSONObject("transform")?.let {
            val transform = Matrix()
            val arr = FloatArray(9)
            val a = it.optDouble("a", 1.0)
            val b = it.optDouble("b", 0.0)
            val c = it.optDouble("c", 0.0)
            val d = it.optDouble("d", 1.0)
            val tx = it.optDouble("tx", 0.0)
            val ty = it.optDouble("ty", 0.0)
            arr[0] = a.toFloat()
            arr[1] = c.toFloat()
            arr[2] = tx.toFloat()
            arr[3] = b.toFloat()
            arr[4] = d.toFloat()
            arr[5] = ty.toFloat()
            arr[6] = 0.0f
            arr[7] = 0.0f
            arr[8] = 1.0f
            transform.setValues(arr)
            this.transform = transform
        }
    }

    private fun parseTransform(obj: ShapeEntity) {
        obj.transform?.let {
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
                    val xv = this.args?.get("x") as? Number ?: return
                    val yv = this.args?.get("y") as? Number ?: return
                    val rxv = this.args?.get("radiusX") as? Number ?: return
                    val ryv = this.args?.get("radiusY") as? Number ?: return
                    val x = xv.toFloat()
                    val y = yv.toFloat()
                    val rx = rxv.toFloat()
                    val ry = ryv.toFloat()
                    localPath.addOval(RectF(x - rx, y - ry, x + rx, y + ry), Path.Direction.CW)
                }
                Type.rect -> {
                    val xv = this.args?.get("x") as? Number ?: return
                    val yv = this.args?.get("y") as? Number ?: return
                    val wv = this.args?.get("width") as? Number ?: return
                    val hv = this.args?.get("height") as? Number ?: return
                    val crv = this.args?.get("cornerRadius") as? Number ?: return
                    val x = xv.toFloat()
                    val y = yv.toFloat()
                    val width = wv.toFloat()
                    val height = hv.toFloat()
                    val cornerRadius = crv.toFloat()
                    localPath.addRoundRect(
                        RectF(x, y, x + width, y + height),
                        cornerRadius,
                        cornerRadius,
                        Path.Direction.CW
                    )
                }
                Type.keep -> {}
            }
            this.shapePath = localPath
        }
    }
}
