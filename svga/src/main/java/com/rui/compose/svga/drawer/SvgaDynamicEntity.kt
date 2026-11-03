package com.rui.compose.svga.drawer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.text.BoringLayout
import android.text.StaticLayout
import android.text.TextPaint
import com.rui.compose.svga.parser.SvgaParser
import com.rui.compose.svga.utils.log.LogUtils
import java.net.HttpURLConnection
import java.net.URL

interface SvgaClickAreaListener {
    fun onResponseArea(key: String, x0: Int, y0: Int, x1: Int, y1: Int)
}

/**
 * SVGA 动态素材与文字替换实体
 */
class SvgaDynamicEntity {

    internal var dynamicHidden: HashMap<String, Boolean> = hashMapOf()
    internal var dynamicImage: HashMap<String, Bitmap> = hashMapOf()
    internal var dynamicText: HashMap<String, String> = hashMapOf()
    internal var dynamicTextPaint: HashMap<String, TextPaint> = hashMapOf()
    internal var dynamicStaticLayoutText: HashMap<String, StaticLayout> = hashMapOf()
    internal var dynamicBoringLayoutText: HashMap<String, BoringLayout> = hashMapOf()
    internal var dynamicDrawer: HashMap<String, (canvas: Canvas, frameIndex: Int) -> Boolean> = hashMapOf()
    internal var dynamicClickArea: HashMap<String, SvgaClickAreaListener> = hashMapOf()
    internal var dynamicDrawerSized: HashMap<String, (canvas: Canvas, frameIndex: Int, width: Int, height: Int) -> Boolean> = hashMapOf()
    internal var clickMap: HashMap<String, IntArray> = hashMapOf()
    internal var isTextDirty = false

    var dynamicSize = 0
    var loadOkSize = 0
    private var replaceFinish: ReplaceFinish? = null

    interface ReplaceFinish {
        fun replaceFinish()
    }

    fun setFinishListener(replaceFinish: ReplaceFinish?) {
        this.replaceFinish = replaceFinish
    }

    fun setHidden(value: Boolean, forKey: String): SvgaDynamicEntity {
        this.dynamicHidden[forKey] = value
        return this
    }

    fun setDynamicImage(bitmap: Bitmap, forKey: String): SvgaDynamicEntity {
        this.dynamicImage[forKey] = bitmap
        notifyResReplaced()
        return this
    }

    private fun notifyResReplaced() {
        loadOkSize++
        if (loadOkSize == dynamicSize) {
            replaceFinish?.replaceFinish()
        }
    }

    fun setDynamicImage(url: String, forKey: String): SvgaDynamicEntity {
        val handler = Handler(Looper.getMainLooper())
        SvgaParser.threadPoolExecutor.execute {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection() as? HttpURLConnection
                conn?.connectTimeout = 20000
                conn?.requestMethod = "GET"
                conn?.connect()
                conn?.inputStream?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { bitmap ->
                        handler.post { setDynamicImage(bitmap, forKey) }
                    }
                }
            } catch (e: Exception) {
                LogUtils.error("SvgaDynamicEntity", "Failed to load dynamic image from $url", e)
            } finally {
                try { conn?.disconnect() } catch (_: Throwable) {}
            }
        }
        return this
    }

    fun setDynamicText(text: String, textPaint: TextPaint, forKey: String): SvgaDynamicEntity {
        this.isTextDirty = true
        this.dynamicText[forKey] = text
        this.dynamicTextPaint[forKey] = textPaint
        notifyResReplaced()
        return this
    }

    fun setDynamicText(layoutText: StaticLayout, forKey: String): SvgaDynamicEntity {
        this.isTextDirty = true
        this.dynamicStaticLayoutText[forKey] = layoutText
        return this
    }

    fun setDynamicText(layoutText: BoringLayout, forKey: String): SvgaDynamicEntity {
        this.isTextDirty = true
        BoringLayout.isBoring(layoutText.text, layoutText.paint)?.let {
            this.dynamicBoringLayoutText[forKey] = layoutText
        }
        return this
    }

    fun setDynamicDrawer(drawer: (canvas: Canvas, frameIndex: Int) -> Boolean, forKey: String): SvgaDynamicEntity {
        this.dynamicDrawer[forKey] = drawer
        return this
    }

    fun setClickArea(clickKey: List<String>): SvgaDynamicEntity {
        for (itemKey in clickKey) {
            setClickArea(itemKey)
        }
        return this
    }

    fun setClickArea(clickKey: String): SvgaDynamicEntity {
        dynamicClickArea[clickKey] = object : SvgaClickAreaListener {
            override fun onResponseArea(key: String, x0: Int, y0: Int, x1: Int, y1: Int) {
                val area = clickMap[key]
                if (area == null) {
                    clickMap[key] = intArrayOf(x0, y0, x1, y1)
                } else {
                    area[0] = x0
                    area[1] = y0
                    area[2] = x1
                    area[3] = y1
                }
            }
        }
        return this
    }

    fun setDynamicDrawerSized(
        drawer: (canvas: Canvas, frameIndex: Int, width: Int, height: Int) -> Boolean,
        forKey: String
    ): SvgaDynamicEntity {
        this.dynamicDrawerSized[forKey] = drawer
        return this
    }

    fun clearDynamicObjects() {
        this.isTextDirty = true
        this.dynamicHidden.clear()
        this.dynamicImage.clear()
        this.dynamicText.clear()
        this.dynamicTextPaint.clear()
        this.dynamicStaticLayoutText.clear()
        this.dynamicBoringLayoutText.clear()
        this.dynamicDrawer.clear()
        this.dynamicClickArea.clear()
        this.clickMap.clear()
        this.dynamicDrawerSized.clear()
    }
}

/**
 * 现代 Kotlin DSL 建造者
 */
inline fun buildSvgaDynamic(block: SvgaDynamicDsl.() -> Unit): SvgaDynamicEntity {
    val dsl = SvgaDynamicDsl()
    dsl.block()
    return dsl.build()
}

class SvgaDynamicDsl {
    private val entity = SvgaDynamicEntity()

    fun image(key: String, bitmap: Bitmap) {
        entity.setDynamicImage(bitmap, key)
    }

    fun image(key: String, url: String) {
        entity.setDynamicImage(url, key)
    }

    fun text(key: String, content: String, textPaint: TextPaint) {
        entity.setDynamicText(content, textPaint, key)
    }

    fun click(key: String) {
        entity.setClickArea(key)
    }

    fun hidden(key: String, isHidden: Boolean = true) {
        entity.setHidden(isHidden, key)
    }

    fun build(): SvgaDynamicEntity = entity
}
