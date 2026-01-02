package com.opensource.svgaplayer.entities

import android.graphics.Matrix
import com.opensource.svgaplayer.proto.FrameEntity
import com.opensource.svgaplayer.utils.SVGARect
import org.json.JSONObject

/**
 * Created by cuiminghui on 2017/2/22.
 */
internal class SVGAVideoSpriteFrameEntity {

    var alpha = 0.0
        private set

    var layout = SVGARect(0.0, 0.0, 0.0, 0.0)
        private set

    var transform = Matrix()
        private set

    var maskPath: SVGAPathEntity? = null
        private set

    // 修复：移除 private set，允许 SVGAVideoSpriteEntity 访问并实现 isKeep 复用逻辑
    var shapes: List<SVGAVideoShapeEntity> = emptyList()

    constructor(obj: JSONObject) {
        this.alpha = obj.optDouble("alpha", 0.0)
        obj.optJSONObject("layout")?.let {
            this.layout = SVGARect(
                it.optDouble("x", 0.0),
                it.optDouble("y", 0.0),
                it.optDouble("width", 0.0),
                it.optDouble("height", 0.0)
            )
        }
        obj.optJSONObject("transform")?.let {
            val arr = FloatArray(9)
            val a = it.optDouble("a", 1.0)
            val b = it.optDouble("b", 0.0)
            val c = it.optDouble("c", 0.0)
            val d = it.optDouble("d", 1.0)
            val tx = it.optDouble("tx", 0.0)
            val ty = it.optDouble("ty", 0.0)
            arr[0] = a.toFloat() // a
            arr[1] = c.toFloat() // c
            arr[2] = tx.toFloat() // tx
            arr[3] = b.toFloat() // b
            arr[4] = d.toFloat() // d
            arr[5] = ty.toFloat() // ty
            arr[6] = 0.0.toFloat()
            arr[7] = 0.0.toFloat()
            arr[8] = 1.0.toFloat()
            this.transform.setValues(arr)
        }
        obj.optString("clipPath").let {
            if (it.isNotEmpty()) {
                this.maskPath = SVGAPathEntity(it)
            }
        }
        val shapes = mutableListOf<SVGAVideoShapeEntity>()
        obj.optJSONArray("shapes")?.let {
            for (i in 0 until it.length()) {
                it.optJSONObject(i)?.let {
                    shapes.add(SVGAVideoShapeEntity(it))
                }
            }
        }
        this.shapes = shapes
    }

    constructor(obj: FrameEntity) {
        this.alpha = (obj.alpha ?: 0.0f).toDouble()
        obj.layout?.let {
            this.layout = SVGARect(
                (it.x ?: 0.0f).toDouble(),
                (it.y ?: 0.0f).toDouble(),
                (it.width ?: 0.0f).toDouble(),
                (it.height ?: 0.0f).toDouble()
            )
        }
        obj.transform?.let {
            val arr = FloatArray(9)
            val a = it.a ?: 1.0f
            val b = it.b ?: 0.0f
            val c = it.c ?: 0.0f
            val d = it.d ?: 1.0f
            val tx = it.tx ?: 0.0f
            val ty = it.ty ?: 0.0f
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
        }
        obj.clipPath?.let {
            if (it.isNotEmpty()) {
                this.maskPath = SVGAPathEntity(it)
            }
        }
        this.shapes = obj.shapes?.map { SVGAVideoShapeEntity(it) } ?: emptyList()
    }

}
