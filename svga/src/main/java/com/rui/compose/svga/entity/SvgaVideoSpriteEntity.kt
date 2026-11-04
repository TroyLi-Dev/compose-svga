package com.rui.compose.svga.entity

import android.graphics.Bitmap
import com.rui.compose.svga.proto.SpriteEntity
import com.rui.compose.svga.utils.log.LogUtils
import org.json.JSONObject

class SvgaVideoSpriteEntity {

    val imageKey: String?
    val matteKey: String?
    val frames: List<SvgaVideoSpriteFrameEntity>

    val isMatte: Boolean
    val bitmapKey: String?
    val noExtKey: String?
    val noExtBitmapKey: String?
    var defaultBitmap: Bitmap? = null
        internal set

    constructor(obj: JSONObject) {
        val rawImageKey = obj.optString("imageKey")
        val parsedImageKey = if (rawImageKey.isNotEmpty()) rawImageKey else null
        this.imageKey = parsedImageKey
        val rawMatteKey = obj.optString("matteKey")
        this.matteKey = if (rawMatteKey.isNotEmpty()) rawMatteKey else null
        val matte = parsedImageKey?.endsWith(".matte") == true
        this.isMatte = matte
        val bKey = if (parsedImageKey != null && matte) parsedImageKey.substring(0, parsedImageKey.length - 6) else parsedImageKey
        this.bitmapKey = bKey
        this.noExtKey = if (parsedImageKey != null && parsedImageKey.length > 4 && parsedImageKey.endsWith(".png", ignoreCase = true)) parsedImageKey.substring(0, parsedImageKey.length - 4) else null
        this.noExtBitmapKey = if (bKey != null && bKey.length > 4 && bKey.endsWith(".png", ignoreCase = true)) bKey.substring(0, bKey.length - 4) else null
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
        val parsedImageKey = if (obj.imageKey.isNotEmpty()) obj.imageKey else null
        this.imageKey = parsedImageKey
        val rawMatteKey = obj.matteKey
        this.matteKey = if (rawMatteKey.isNotEmpty()) rawMatteKey else null
        val matte = parsedImageKey?.endsWith(".matte") == true
        this.isMatte = matte
        val bKey = if (parsedImageKey != null && matte) parsedImageKey.substring(0, parsedImageKey.length - 6) else parsedImageKey
        this.bitmapKey = bKey
        this.noExtKey = if (parsedImageKey != null && parsedImageKey.length > 4 && parsedImageKey.endsWith(".png", ignoreCase = true)) parsedImageKey.substring(0, parsedImageKey.length - 4) else null
        this.noExtBitmapKey = if (bKey != null && bKey.length > 4 && bKey.endsWith(".png", ignoreCase = true)) bKey.substring(0, bKey.length - 4) else null
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
