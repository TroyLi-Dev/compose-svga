package com.rui.compose.svga.entity

import com.rui.compose.svga.proto.SpriteEntity
import com.rui.compose.svga.utils.log.LogUtils
import org.json.JSONObject

class SvgaVideoSpriteEntity {

    val imageKey: String?
    val matteKey: String?
    val frames: List<SvgaVideoSpriteFrameEntity>

    constructor(obj: JSONObject) {
        this.imageKey = obj.optString("imageKey")
        this.matteKey = obj.optString("matteKey")
        val mutableFrames = mutableListOf<SvgaVideoSpriteFrameEntity>()
        obj.optJSONArray("frames")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { jsonObject ->
                    try {
                        val frameItem = SvgaVideoSpriteFrameEntity(jsonObject)
                        if (frameItem.shapes.isNotEmpty()) {
                            frameItem.shapes.first().let { shape ->
                                if (shape.isKeep && mutableFrames.isNotEmpty()) {
                                    frameItem.shapes = mutableFrames.last().shapes
                                }
                            }
                        }
                        mutableFrames.add(frameItem)
                    } catch (t: Throwable) {
                        LogUtils.error("SvgaVideoSpriteEntity", "Failed to parse frame $i from JSON", t)
                    }
                }
            }
        }
        frames = mutableFrames.toList()
    }

    constructor(obj: SpriteEntity) {
        this.imageKey = obj.imageKey
        this.matteKey = obj.matteKey
        var lastFrame: SvgaVideoSpriteFrameEntity? = null
        frames = obj.frames.mapNotNull { frameProto ->
            try {
                val frameItem = SvgaVideoSpriteFrameEntity(frameProto)
                if (frameItem.shapes.isNotEmpty()) {
                    frameItem.shapes.first().let { shape ->
                        if (shape.isKeep) {
                            lastFrame?.let { last ->
                                frameItem.shapes = last.shapes
                            }
                        }
                    }
                }
                lastFrame = frameItem
                frameItem
            } catch (t: Throwable) {
                LogUtils.error("SvgaVideoSpriteEntity", "Failed to parse frame from Protobuf", t)
                null
            }
        }
    }
}
