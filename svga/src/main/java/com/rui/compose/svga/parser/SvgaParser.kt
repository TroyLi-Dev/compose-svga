package com.rui.compose.svga.parser

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.rui.compose.svga.proto.MovieEntity
import com.rui.compose.svga.cache.SvgaDiskCache
import com.rui.compose.svga.cache.SvgaMemoryCache
import com.rui.compose.svga.entity.SvgaVideoEntity
import com.rui.compose.svga.exception.DecodeReason
import com.rui.compose.svga.exception.SvgaException
import com.rui.compose.svga.utils.log.LogUtils
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.Inflater
import java.util.zip.ZipInputStream

/**
 * SVGA 核心解码与解析器
 */
class SvgaParser(
    private val context: Context?,
    val needCutBitmap: Boolean = false
) {

    init {
        context?.let { ctx ->
            val appCtx = ctx.applicationContext ?: ctx
            SvgaDiskCache.init(appCtx)
            com.rui.compose.svga.utils.SvgaMemoryCalculator.instance.init(appCtx)
        }
    }

    interface ParseCompletion {
        fun onComplete(videoItem: SvgaVideoEntity)
        fun onError(e: Exception, alias: String = "")
    }

    companion object {
        private const val TAG = "SvgaParser"
        private val segmentLocks = Array(32) { Any() }

        private fun getFileLock(cacheKey: String): Any =
            segmentLocks[kotlin.math.abs(cacheKey.hashCode()) and 31]

        val threadNum = AtomicInteger(0)
        private val CPU_COUNT = Runtime.getRuntime().availableProcessors()
        private val CORE_POOL_SIZE = kotlin.math.max(2, kotlin.math.min(CPU_COUNT, 4))
        private val MAX_POOL_SIZE = CORE_POOL_SIZE * 2

        @JvmStatic
        var threadPoolExecutor: ExecutorService = ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE * 2,
            60L,
            TimeUnit.SECONDS,
            LinkedBlockingQueue<Runnable>(),
            { r -> Thread(r, "SvgaParser-Thread-${threadNum.getAndIncrement()}") }
        )

        // 在途网络下载请求合并表 (SingleFlight In-Flight Merging)，彻底消灭重复网络请求与并发资源竞争
        private val inFlightUrlTasks = ConcurrentHashMap<String, MutableList<ParseCompletion>>()
        private val inFlightLock = Any()

        // 线上安全防范阈值：防范超大恶意流、Zip Bomb 攻击 (CWE-409)
        private const val MAX_STREAM_BYTES = 60 * 1024 * 1024 // 单个 SVGA 流最大 60MB
        private const val MAX_UNZIPPED_BYTES = 100 * 1024 * 1024L // 解压总大小上限 100MB
        private const val MAX_ZIP_ENTRIES = 1000 // 解压文件条目数上限 1000
        private const val MAX_INFLATED_BYTES = 50 * 1024 * 1024 // 解压缩 Protobuf 字节上限 50MB
    }

    private var frameWidth: Int = 0
    private var frameHeight: Int = 0

    fun setFrameSize(width: Int, height: Int) {
        this.frameWidth = width
        this.frameHeight = height
    }

    /**
     * 智能自适应解码入口，支持 URL、File、Assets、RawRes 以及绝对路径
     */
    fun decodeAuto(
        model: Any,
        callback: ParseCompletion?
    ): (() -> Unit)? {
        when (model) {
            is File -> {
                decodeFromFile(model, callback)
                return null
            }
            is URL -> {
                return decodeFromURL(model, callback)
            }
            is SvgaFetchResult.FileResult -> {
                decodeFromFile(model.file, callback)
                return null
            }
            is SvgaFetchResult.Stream -> {
                decodeFromInputStream(model.inputStream, model.cacheKey, callback, true, null, "")
                return null
            }
            is String -> {
                val lower = model.lowercase()
                return when {
                    lower.startsWith("http://") || lower.startsWith("https://") -> {
                        try {
                            decodeFromURL(URL(model), callback)
                        } catch (e: Exception) {
                            callback?.onError(e, model)
                            null
                        }
                    }
                    lower.startsWith("file://") -> {
                        val file = File(model.substring(7))
                        decodeFromFile(file, callback)
                        null
                    }
                    model.startsWith("/") -> {
                        val file = File(model)
                        decodeFromFile(file, callback)
                        null
                    }
                    else -> {
                        decodeFromAssets(model, callback)
                        null
                    }
                }
            }
            else -> {
                val appContext = context
                if (appContext != null && model is Int) {
                    try {
                        val inputStream = appContext.resources.openRawResource(model)
                        val cacheKey = SvgaDiskCache.buildCacheKey("rawres://$model")
                        decodeFromInputStream(inputStream, cacheKey, callback, true, null, model.toString())
                    } catch (e: Exception) {
                        callback?.onError(e, model.toString())
                    }
                    return null
                }
                val customFetcher = SvgaFetcherRegistry.findFetcher(model)
                if (customFetcher != null && appContext != null) {
                    threadPoolExecutor.execute {
                        try {
                            kotlinx.coroutines.runBlocking {
                                val result = customFetcher.fetch(appContext, model)
                                decodeAuto(result, callback)
                            }
                        } catch (t: Throwable) {
                            val e = if (t is Exception) t else Exception(t)
                            Handler(Looper.getMainLooper()).post { callback?.onError(e, model.toString()) }
                        }
                    }
                    return null
                }
                callback?.onError(IllegalArgumentException("Unsupported SVGA model type: ${model.javaClass.name}"), model.toString())
                return null
            }
        }
    }

    fun decodeFromAssets(
        name: String,
        callback: ParseCompletion?
    ) {
        val handler = Handler(Looper.getMainLooper())
        val appContext = context ?: run {
            handler.post { callback?.onError(Exception("Context is null"), name) }
            return
        }
        var cleanPath = name.trim()
        if (cleanPath.startsWith("assets://", ignoreCase = true)) {
            cleanPath = cleanPath.substring(9)
        } else if (cleanPath.startsWith("asset://", ignoreCase = true)) {
            cleanPath = cleanPath.substring(8)
        }
        cleanPath = cleanPath.removePrefix("/")
        threadPoolExecutor.execute {
            try {
                val inputStream = appContext.assets.open(cleanPath)
                val cacheKey = SvgaDiskCache.buildCacheKey("assets://$cleanPath")
                decodeFromInputStream(inputStream, cacheKey, callback, true, null, name)
            } catch (t: Throwable) {
                val e = if (t is Exception) t else Exception(t)
                handler.post { callback?.onError(e, name) }
            }
        }
    }

    fun decodeFromURL(
        url: URL,
        callback: ParseCompletion?
    ): (() -> Unit)? {
        val cacheKey = SvgaDiskCache.buildCacheKey(url)
        val cached = SvgaMemoryCache.instance.get(cacheKey)
        if (cached != null) {
            callback?.onComplete(cached)
            return null
        }

        val handler = Handler(Looper.getMainLooper())

        // 1. 优先尝试从磁盘缓存极速命中，彻底跳过重复网络下载
        if (SvgaDiskCache.isCached(cacheKey)) {
            val cacheDir = SvgaDiskCache.buildCacheDir(cacheKey)
            threadPoolExecutor.execute {
                try {
                    val lock = getFileLock(cacheKey)
                    val entity = synchronized(lock) {
                        loadFromCacheDir(cacheDir)
                    }
                    entity.cacheKey = cacheKey
                    entity.prepare({
                        SvgaMemoryCache.instance.put(cacheKey, entity)
                        handler.post { callback?.onComplete(entity) }
                    })
                } catch (e: Exception) {
                    LogUtils.warn(TAG, "Disk cache corrupted for $url, re-downloading: ${e.message}")
                    cacheDir.deleteRecursively()
                    startDownloadUrlMerged(url, cacheKey, callback, handler)
                }
            }
            return null
        }

        // 2. 磁盘无缓存时走在途合并网络下载 (SingleFlight)
        return startDownloadUrlMerged(url, cacheKey, callback, handler)
    }

    private fun startDownloadUrlMerged(
        url: URL,
        cacheKey: String,
        callback: ParseCompletion?,
        handler: Handler
    ): (() -> Unit)? {
        var isFirst = false
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)

        synchronized(inFlightLock) {
            val list = inFlightUrlTasks[cacheKey]
            if (list != null) {
                if (callback != null) list.add(callback)
                isFirst = false
            } else {
                val newList = mutableListOf<ParseCompletion>()
                if (callback != null) newList.add(callback)
                inFlightUrlTasks[cacheKey] = newList
                isFirst = true
            }
        }

        if (isFirst) {
            threadPoolExecutor.execute {
                doDownloadUrlMerged(url, cacheKey, cancelled, handler)
            }
        }

        return {
            synchronized(inFlightLock) {
                val list = inFlightUrlTasks[cacheKey]
                if (list != null) {
                    if (callback != null) list.remove(callback)
                    if (list.isEmpty()) {
                        cancelled.set(true)
                        inFlightUrlTasks.remove(cacheKey)
                    }
                }
            }
        }
    }

    private fun doDownloadUrlMerged(
        url: URL,
        cacheKey: String,
        cancelled: java.util.concurrent.atomic.AtomicBoolean,
        handler: Handler
    ) {
        val maxRetries = 2
        var lastException: Exception? = null

        for (attempt in 0..maxRetries) {
            if (cancelled.get()) {
                synchronized(inFlightLock) { inFlightUrlTasks.remove(cacheKey) }
                return
            }

            var conn: java.net.HttpURLConnection? = null
            try {
                var currentUrl = url
                var redirects = 0
                val maxRedirects = 5

                while (true) {
                    val c = currentUrl.openConnection() as java.net.HttpURLConnection
                    conn = c
                    c.connectTimeout = 10000
                    c.readTimeout = 20000
                    c.instanceFollowRedirects = false
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 SVGA/Compose")
                    c.connect()

                    val responseCode = c.responseCode
                    if (responseCode in 300..399) {
                        val location = c.getHeaderField("Location")
                        c.disconnect()
                        if (redirects++ < maxRedirects && !location.isNullOrEmpty()) {
                            currentUrl = URL(currentUrl, location)
                            continue
                        } else {
                            throw SvgaException.FetchException(
                                source = url.toString(),
                                httpCode = responseCode,
                                message = "Too many redirects or missing Location header: $location"
                            )
                        }
                    }

                    if (responseCode !in 200..299) {
                        throw SvgaException.FetchException(
                            source = url.toString(),
                            httpCode = responseCode,
                            message = "HTTP error $responseCode: ${c.responseMessage}"
                        )
                    }
                    break
                }

                val bytes = readStreamToByteArray(conn.inputStream)
                if (bytes.isEmpty()) {
                    throw SvgaException.DecodeException(url.toString(), DecodeReason.EMPTY_STREAM, "Empty stream")
                }

                val cacheDir = SvgaDiskCache.buildCacheDir(cacheKey)
                val lock = getFileLock(cacheKey)
                val entity = synchronized(lock) {
                    if (isZipFile(bytes)) {
                        if (!cacheDir.exists() || cacheDir.listFiles().isNullOrEmpty()) {
                            unzipAtomic(ByteArrayInputStream(bytes), cacheDir)
                        }
                        try {
                            loadFromCacheDir(cacheDir)
                        } catch (e: Exception) {
                            LogUtils.warn(TAG, "Corrupted cacheDir for $url, purging and re-unzipping: ${e.message}")
                            cacheDir.deleteRecursively()
                            unzipAtomic(ByteArrayInputStream(bytes), cacheDir)
                            loadFromCacheDir(cacheDir)
                        }
                    } else {
                        val inflatedBytes = inflate(bytes) ?: throw SvgaException.DecodeException(
                            url.toString(),
                            DecodeReason.INVALID_PROTOBUF,
                            "Inflate failed"
                        )
                        val movieEntity = MovieEntity.ADAPTER.decode(inflatedBytes)
                        SvgaVideoEntity(movieEntity, cacheDir, frameWidth, frameHeight, needCutBitmap)
                    }
                }
                entity.cacheKey = cacheKey
                entity.prepare({
                    SvgaMemoryCache.instance.put(cacheKey, entity)
                    val callbacks: List<ParseCompletion>
                    synchronized(inFlightLock) {
                        callbacks = inFlightUrlTasks.remove(cacheKey)?.toList() ?: emptyList()
                    }
                    handler.post {
                        callbacks.forEach { it.onComplete(entity) }
                    }
                })
                return // 成功完成并已回调，直接返回退出重试循环！
            } catch (t: Throwable) {
                val e = if (t is Exception) t else Exception(t)
                lastException = e
                LogUtils.warn(TAG, "Download attempt ${attempt + 1}/$maxRetries failed for $url: ${e.message}")
                if (attempt < maxRetries && !cancelled.get()) {
                    try { Thread.sleep(150L * (attempt + 1)) } catch (_: InterruptedException) {}
                }
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
        }

        // 重试耗尽后向所有在途等待者分发错误
        val finalException = lastException ?: Exception("Download failed for $url")
        val callbacks: List<ParseCompletion>
        synchronized(inFlightLock) {
            callbacks = inFlightUrlTasks.remove(cacheKey)?.toList() ?: emptyList()
        }
        handler.post {
            callbacks.forEach { it.onError(finalException, url.toString()) }
        }
    }

    fun decodeFromFile(
        file: File,
        callback: ParseCompletion?
    ) {
        val handler = Handler(Looper.getMainLooper())
        if (!file.exists()) {
            handler.post { callback?.onError(Exception("File not found: ${file.absolutePath}"), file.name) }
            return
        }
        val cacheKey = SvgaDiskCache.buildCacheKey("file://${file.absolutePath}")
        threadPoolExecutor.execute {
            try {
                decodeFromInputStream(FileInputStream(file), cacheKey, callback, true, null, file.name)
            } catch (t: Throwable) {
                val e = if (t is Exception) t else Exception(t)
                handler.post { callback?.onError(e, file.name) }
            }
        }
    }

    fun decodeFromInputStream(
        inputStream: InputStream,
        cacheKey: String,
        callback: ParseCompletion?,
        closeStream: Boolean = true,
        playCallback: ((List<File>) -> Unit)? = null,
        alias: String = ""
    ) {
        val handler = Handler(Looper.getMainLooper())
        val cached = SvgaMemoryCache.instance.get(cacheKey)
        if (cached != null) {
            handler.post { callback?.onComplete(cached) }
            if (closeStream) try { inputStream.close() } catch (_: Exception) {}
            return
        }

        val decodeTask = Runnable {
            try {
                val bytes = readStreamToByteArray(inputStream)
                if (bytes.isEmpty()) throw SvgaException.DecodeException(alias, DecodeReason.EMPTY_STREAM, "Empty input stream")

                val cacheDir = SvgaDiskCache.buildCacheDir(cacheKey)
                val lock = getFileLock(cacheKey)
                synchronized(lock) {
                    if (isZipFile(bytes)) {
                        if (!cacheDir.exists() || cacheDir.listFiles().isNullOrEmpty()) {
                            unzipAtomic(ByteArrayInputStream(bytes), cacheDir)
                        }
                        val entity = try {
                            loadFromCacheDir(cacheDir)
                        } catch (e: Exception) {
                            LogUtils.warn(TAG, "Corrupted cacheDir for $cacheKey, purging and re-unzipping: ${e.message}")
                            cacheDir.deleteRecursively()
                            unzipAtomic(ByteArrayInputStream(bytes), cacheDir)
                            loadFromCacheDir(cacheDir)
                        }
                        entity.cacheKey = cacheKey
                        entity.prepare({
                            SvgaMemoryCache.instance.put(cacheKey, entity)
                            handler.post { callback?.onComplete(entity) }
                        }, playCallback)
                    } else {
                        inflate(bytes)?.let { inflatedBytes ->
                            val movieEntity = MovieEntity.ADAPTER.decode(inflatedBytes)
                            val entity = SvgaVideoEntity(
                                movieEntity,
                                cacheDir,
                                frameWidth,
                                frameHeight,
                                needCutBitmap
                            )
                            entity.cacheKey = cacheKey
                            entity.prepare({
                                SvgaMemoryCache.instance.put(cacheKey, entity)
                                handler.post { callback?.onComplete(entity) }
                            }, playCallback)
                        } ?: throw SvgaException.DecodeException(alias, DecodeReason.INVALID_PROTOBUF, "Inflate failed")
                    }
                }
            } catch (t: Throwable) {
                val e = if (t is Exception) t else Exception(t)
                handler.post { callback?.onError(e, alias) }
            } finally {
                if (closeStream) try { inputStream.close() } catch (_: Exception) {}
            }
        }

        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                threadPoolExecutor.execute(decodeTask)
            } else {
                // 已在非主线程，直接同线程同步解码，消除二次排队与线程池队列压力
                decodeTask.run()
            }
        } catch (t: Throwable) {
            if (closeStream) try { inputStream.close() } catch (_: Exception) {}
            val e = if (t is Exception) t else Exception(t)
            handler.post { callback?.onError(e, alias) }
        }
    }

    private fun loadFromCacheDir(cacheDir: File): SvgaVideoEntity {
        val binaryFile = File(cacheDir, "movie.binary")
        val specFile = File(cacheDir, "movie.spec")
        val entity = when {
            binaryFile.exists() -> {
                val movieEntity = MovieEntity.ADAPTER.decode(binaryFile.readBytes())
                SvgaVideoEntity(movieEntity, cacheDir, frameWidth, frameHeight, needCutBitmap)
            }
            specFile.exists() -> {
                SvgaVideoEntity(JSONObject(specFile.readText()), cacheDir, frameWidth, frameHeight, needCutBitmap)
            }
            else -> throw SvgaException.DecodeException(cacheDir.name, DecodeReason.MISSING_SPEC, "Metadata not found in $cacheDir")
        }
        return entity
    }

    private fun unzipAtomic(inputStream: InputStream, cacheDir: File) {
        val parent = cacheDir.parentFile ?: cacheDir
        val tmpDir = File(parent, "${cacheDir.name}_tmp_${System.nanoTime()}")
        tmpDir.mkdirs()
        try {
            unzip(inputStream, tmpDir)
            if (cacheDir.exists()) cacheDir.deleteRecursively()
            if (!tmpDir.renameTo(cacheDir)) {
                tmpDir.copyRecursively(cacheDir, overwrite = true)
                tmpDir.deleteRecursively()
            }
        } catch (t: Throwable) {
            tmpDir.deleteRecursively()
            throw t
        }
    }

    private fun unzip(inputStream: InputStream, cacheDir: File) {
        val canonicalDest = cacheDir.canonicalPath
        var totalUncompressedBytes = 0L
        var totalEntries = 0
        ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                totalEntries++
                if (totalEntries > MAX_ZIP_ENTRIES) {
                    throw SvgaException.DecodeException(
                        entry.name,
                        DecodeReason.ZIP_SLIP_SECURITY,
                        "Zip Bomb detected: exceeded $MAX_ZIP_ENTRIES entries"
                    )
                }
                val file = File(cacheDir, entry.name)
                val canonicalFile = file.canonicalPath
                if (!canonicalFile.startsWith(canonicalDest + File.separator) && canonicalFile != canonicalDest) {
                    throw SvgaException.DecodeException(entry.name, DecodeReason.ZIP_SLIP_SECURITY, "Zip Slip detected: ${entry.name}")
                }
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        val buffer = ByteArray(16384)
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            totalUncompressedBytes += len
                            if (totalUncompressedBytes > MAX_UNZIPPED_BYTES) {
                                throw SvgaException.DecodeException(
                                    entry.name,
                                    DecodeReason.ZIP_SLIP_SECURITY,
                                    "Zip Bomb detected: uncompressed size exceeded $MAX_UNZIPPED_BYTES bytes"
                                )
                            }
                            fos.write(buffer, 0, len)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun isZipFile(bytes: ByteArray): Boolean {
        return bytes.size > 4 && bytes[0].toInt() == 0x50 && bytes[1].toInt() == 0x4B &&
                bytes[2].toInt() == 0x03 && bytes[3].toInt() == 0x04
    }

    private fun inflate(byteArray: ByteArray): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(byteArray, 0, byteArray.size)
            val result = ByteArray(16384)
            val baos = ByteArrayOutputStream()
            var totalBytes = 0
            while (!inflater.finished()) {
                val count = inflater.inflate(result)
                if (count > 0) {
                    totalBytes += count
                    if (totalBytes > MAX_INFLATED_BYTES) {
                        LogUtils.error(TAG, "Inflated data exceeded maximum limit of $MAX_INFLATED_BYTES bytes")
                        return null
                    }
                    baos.write(result, 0, count)
                } else break
            }
            baos.toByteArray()
        } catch (t: Throwable) {
            LogUtils.error(TAG, "Inflate failed", t)
            null
        } finally {
            inflater.end()
        }
    }

    private fun readStreamToByteArray(inputStream: InputStream): ByteArray {
        val buffer = ByteArray(16384)
        val baos = ByteArrayOutputStream()
        var len: Int
        var totalBytes = 0
        while (inputStream.read(buffer).also { len = it } != -1) {
            totalBytes += len
            if (totalBytes > MAX_STREAM_BYTES) {
                throw SvgaException.DecodeException(
                    "stream",
                    DecodeReason.EMPTY_STREAM,
                    "SVGA stream exceeded maximum allowed size ($MAX_STREAM_BYTES bytes)"
                )
            }
            baos.write(buffer, 0, len)
        }
        return baos.toByteArray()
    }
}
