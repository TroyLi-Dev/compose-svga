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
    fun buildCacheKeyForModel(model: Any?): String {
        if (model == null) return ""
        return when (model) {
            is com.rui.compose.svga.parser.SvgaFetchResult.Stream -> model.cacheKey
            is com.rui.compose.svga.parser.SvgaFetchResult.FileResult -> model.cacheKey
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

    @JvmOverloads
    fun buildAudioFile(audioKey: String, parentDir: File? = null): File {
        val audioDir = parentDir ?: File(getCacheDir(), "audios").apply {
            if (!exists()) mkdirs()
        }
        val safeKey = buildCacheKey(audioKey)
        return File(audioDir, "$safeKey.mp3")
    }

    fun isCached(cacheKey: String): Boolean {
        if (cacheKey.isEmpty()) return false
        val dir = File(getCacheDir(), cacheKey)
        return dir.exists() && !dir.listFiles().isNullOrEmpty()
    }

    /**
     * 自动淘汰超限的磁盘缓存（默认上限 256MB，淘汰至 75%）
     */
    fun trimDiskCache(
        maxBytes: Long = 256 * 1024 * 1024L,
        maxAudioBytes: Long = 64 * 1024 * 1024L
    ) {
        try {
            val root = getCacheDir()
            if (!root.exists() || !root.isDirectory) return
            val dirs = root.listFiles()?.filter { it.isDirectory && it.name != "audios" } ?: return
            var totalSize = dirs.sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
            if (totalSize > maxBytes) {
                val sortedDirs = dirs.sortedBy { it.lastModified() }
                val targetSize = (maxBytes * 0.75).toLong()
                for (dir in sortedDirs) {
                    val dirSize = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    dir.deleteRecursively()
                    totalSize -= dirSize
                    if (totalSize <= targetSize) break
                }
            }

            val audioDir = File(root, "audios")
            if (audioDir.exists() && audioDir.isDirectory) {
                val audioFiles = audioDir.listFiles()?.filter { it.isFile } ?: emptyList()
                var audioSize = audioFiles.sumOf { it.length() }
                if (audioSize > maxAudioBytes) {
                    val sortedFiles = audioFiles.sortedBy { it.lastModified() }
                    val targetAudioSize = (maxAudioBytes * 0.75).toLong()
                    for (file in sortedFiles) {
                        val len = file.length()
                        file.delete()
                        audioSize -= len
                        if (audioSize <= targetAudioSize) break
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    fun clearAll() {
        val dir = getCacheDir()
        dir.deleteRecursively()
        dir.mkdirs()
    }
}
