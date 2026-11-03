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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

interface SvgaClickAreaListener {
    fun onResponseArea(key: String, x0: Int, y0: Int, x1: Int, y1: Int)
}

/**
 * SVGA 动态素材与文字替换实体
 */
class SvgaDynamicEntity {

    internal var dynamicHidden: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()
    internal var dynamicImage: ConcurrentHashMap<String, Bitmap> = ConcurrentHashMap()
    internal var dynamicText: ConcurrentHashMap<String, String> = ConcurrentHashMap()
    internal var dynamicTextPaint: ConcurrentHashMap<String, TextPaint> = ConcurrentHashMap()
    internal var dynamicStaticLayoutText: ConcurrentHashMap<String, StaticLayout> = ConcurrentHashMap()
    internal var dynamicBoringLayoutText: ConcurrentHashMap<String, BoringLayout> = ConcurrentHashMap()
    internal var dynamicDrawer: ConcurrentHashMap<String, (canvas: Canvas, frameIndex: Int) -> Boolean> = ConcurrentHashMap()
    internal var dynamicClickArea: ConcurrentHashMap<String, SvgaClickAreaListener> = ConcurrentHashMap()
    internal var dynamicDrawerSized: ConcurrentHashMap<String, (canvas: Canvas, frameIndex: Int, width: Int, height: Int) -> Boolean> = ConcurrentHashMap()
    internal var clickMap: ConcurrentHashMap<String, IntArray> = ConcurrentHashMap()
    @Volatile
    internal var isTextDirty = false

    private val _dynamicSize = AtomicInteger(0)
    private val _loadOkSize = AtomicInteger(0)

    var dynamicSize: Int
        get() = _dynamicSize.get()
        set(value) = _dynamicSize.set(value)

    var loadOkSize: Int
        get() = _loadOkSize.get()
        set(value) = _loadOkSize.set(value)

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
        val total = _dynamicSize.get()
        if (total > 0 && _loadOkSize.incrementAndGet() >= total) {
            replaceFinish?.replaceFinish()
        }
    }

    fun setDynamicImage(url: String, forKey: String): SvgaDynamicEntity {
        _dynamicSize.incrementAndGet()
        val handler = Handler(Looper.getMainLooper())
        SvgaParser.threadPoolExecutor.execute {
            var conn: HttpURLConnection? = null
            try {
                var currentUrl = URL(url)
                var redirects = 0
                val maxRedirects = 5
                while (true) {
                    val c = currentUrl.openConnection() as? HttpURLConnection ?: break
                    conn = c
                    c.connectTimeout = 15000
                    c.readTimeout = 20000
                    c.instanceFollowRedirects = false
                    c.requestMethod = "GET"
                    c.connect()

                    val responseCode = c.responseCode
                    if (responseCode in 300..399) {
                        val location = c.getHeaderField("Location")
                        c.disconnect()
                        if (redirects++ < maxRedirects && !location.isNullOrEmpty()) {
                            currentUrl = URL(currentUrl, location)
                            continue
                        } else {
                            break
                        }
                    }
                    if (responseCode !in 200..299) {
                        break
                    }
                    break
                }

                conn?.inputStream?.use { stream ->
                    val bitmap = try {
                        BitmapFactory.decodeStream(stream)
                    } catch (t: Throwable) {
                        LogUtils.error("SvgaDynamicEntity", "OOM decoding dynamic image from $url", t)
                        null
                    }
                    if (bitmap != null) {
                        handler.post { setDynamicImage(bitmap, forKey) }
                    } else {
                        handler.post { notifyResReplaced() }
                    }
                } ?: run {
                    handler.post { notifyResReplaced() }
                }
            } catch (t: Throwable) {
                LogUtils.error("SvgaDynamicEntity", "Failed to load dynamic image from $url", t)
                handler.post { notifyResReplaced() }
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
                val minX = kotlin.math.min(x0, x1)
                val maxX = kotlin.math.max(x0, x1)
                val minY = kotlin.math.min(y0, y1)
                val maxY = kotlin.math.max(y0, y1)
                val area = clickMap[key]
                if (area == null) {
                    clickMap[key] = intArrayOf(minX, minY, maxX, maxY)
                } else {
                    area[0] = minX
                    area[1] = minY
                    area[2] = maxX
                    area[3] = maxY
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
