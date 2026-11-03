package com.rui.compose.svga.cache

import android.content.Context
import java.io.File
import java.net.URL
import java.security.MessageDigest

/**
 * SVGA 磁盘缓存管理
 */
object SvgaDiskCache {

    private var cacheDir: File? = null
    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    fun init(context: Context) {
        val root = context.cacheDir ?: context.filesDir
        cacheDir = File(root, "svga_cache").apply {
            if (!exists()) mkdirs()
        }
    }

    fun isInitialized(): Boolean = cacheDir != null

    fun getCacheDir(): File {
        var dir = cacheDir
        if (dir == null) {
            val tmp = System.getProperty("java.io.tmpdir")?.let { File(it, "svga_cache") }
                ?: File("/tmp/svga_cache")
            if (!tmp.exists()) tmp.mkdirs()
            cacheDir = tmp
            dir = tmp
            com.rui.compose.svga.utils.log.LogUtils.warn("SvgaDiskCache", "SvgaDiskCache.init(context) was not called; auto-fallback to ${tmp.absolutePath}")
        }
        return dir
    }

    fun buildCacheKey(str: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            md.update(str.toByteArray(Charsets.UTF_8))
            val digest = md.digest()
            val result = CharArray(digest.size * 2)
            for (i in digest.indices) {
                val v = digest[i].toInt() and 0xFF
                result[i * 2] = HEX_CHARS[v ushr 4]
                result[i * 2 + 1] = HEX_CHARS[v and 0x0F]
            }
            String(result)
        } catch (e: Exception) {
            str.hashCode().toString()
        }
    }

    fun buildCacheKey(url: URL): String = buildCacheKey(url.toString())

    /**
     * 针对任意类型的资源输入（URL, File, Int rawres, Assets string 等）计算全工程统一的缓存键
     */
    fun buildCacheKeyForModel(model: Any): String {
        return when (model) {
            is URL -> buildCacheKey(model.toString())
            is File -> buildCacheKey("file://${model.absolutePath}")
            is Int -> buildCacheKey("rawres://$model")
            is String -> {
                val lower = model.lowercase()
                when {
                    lower.startsWith("http://") || lower.startsWith("https://") -> buildCacheKey(model)
                    lower.startsWith("file://") -> buildCacheKey(model)
                    model.startsWith("/") -> buildCacheKey("file://$model")
                    lower.startsWith("assets://") || lower.startsWith("asset://") -> {
                        val clean = model.removePrefix("assets://").removePrefix("asset://").removePrefix("/")
                        buildCacheKey("assets://$clean")
                    }
                    else -> buildCacheKey("assets://$model")
                }
            }
            else -> buildCacheKey(model.toString())
        }
    }

    fun buildCacheDir(cacheKey: String): File {
        return File(getCacheDir(), cacheKey).apply {
            if (!exists()) mkdirs()
        }
    }

    fun buildAudioFile(audioKey: String): File {
        val audioDir = File(getCacheDir(), "audios").apply {
            if (!exists()) mkdirs()
        }
        return File(audioDir, "$audioKey.mp3")
    }

    fun isCached(cacheKey: String): Boolean {
        val dir = File(getCacheDir(), cacheKey)
        return dir.exists() && !dir.listFiles().isNullOrEmpty()
    }

    fun clearAll() {
        cacheDir?.deleteRecursively()
        cacheDir?.mkdirs()
    }
}
