package com.rui.compose.svga.entity

import android.graphics.Matrix
import com.rui.compose.svga.proto.FrameEntity
import com.rui.compose.svga.utils.SvgaRect
import org.json.JSONObject

class SvgaVideoSpriteFrameEntity {

    var alpha = 0.0
        private set

    var layout = SvgaRect(0.0, 0.0, 0.0, 0.0)
        private set

    var transform = Matrix()
        private set

    var maskPath: SvgaPathEntity? = null
        private set

    var shapes: List<SvgaVideoShapeEntity> = emptyList()

    constructor(obj: JSONObject) {
        this.alpha = obj.optDouble("alpha", 0.0).coerceIn(0.0, 1.0)
        obj.optJSONObject("layout")?.let {
            this.layout = SvgaRect(
                it.optDouble("x", 0.0),
                it.optDouble("y", 0.0),
                it.optDouble("width", 0.0),
                it.optDouble("height", 0.0)
            )
        }
        obj.optJSONObject("transform")?.let {
            val a = it.optDouble("a", 1.0).toFloat()
            val c = it.optDouble("c", 0.0).toFloat()
            val tx = it.optDouble("tx", 0.0).toFloat()
            val b = it.optDouble("b", 0.0).toFloat()
            val d = it.optDouble("d", 1.0).toFloat()
            val ty = it.optDouble("ty", 0.0).toFloat()
            if (a.isFinite() && b.isFinite() && c.isFinite() && d.isFinite() && tx.isFinite() && ty.isFinite()) {
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
                this.transform.setValues(arr)
            } else {
                this.transform.reset()
            }
        }
        obj.optString("clipPath").let {
            if (it.isNotEmpty()) {
                this.maskPath = SvgaPathEntity(it)
            }
        }
        val shapeList = mutableListOf<SvgaVideoShapeEntity>()
        obj.optJSONArray("shapes")?.let {
            for (i in 0 until it.length()) {
                it.optJSONObject(i)?.let { shapeObj ->
                    shapeList.add(SvgaVideoShapeEntity(shapeObj))
                }
            }
        }
        this.shapes = shapeList
    }

    constructor(obj: FrameEntity) {
        this.alpha = obj.alpha.toDouble().coerceIn(0.0, 1.0)
        obj.layout?.let {
            this.layout = SvgaRect(
                it.x.toDouble(),
                it.y.toDouble(),
                it.width.toDouble(),
                it.height.toDouble()
            )
        }
        obj.transform?.let {
            if (it.a.isFinite() && it.b.isFinite() && it.c.isFinite() && it.d.isFinite() && it.tx.isFinite() && it.ty.isFinite()) {
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
                this.transform.setValues(arr)
            } else {
                this.transform.reset()
            }
        }
        if (!obj.clipPath.isNullOrEmpty()) {
            this.maskPath = SvgaPathEntity(obj.clipPath)
        }
        this.shapes = obj.shapes.map { SvgaVideoShapeEntity(it) }
    }
}
