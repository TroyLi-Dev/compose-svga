package com.opensource.svgaplayer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.opensource.svgaplayer.proto.MovieEntity
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.Inflater
import java.util.zip.ZipInputStream

class SVGAParser(context: Context?, val needCutBitmap: Boolean = false) {
    private var mContext = context?.applicationContext

    init {
        SVGACache.onCreate(context)
        SVGAMemCalculator.instance.init(context?.applicationContext)
    }

    @Volatile
    private var mFrameWidth: Int = 0
    @Volatile
    private var mFrameHeight: Int = 0

    interface ParseCompletion {
        fun onComplete(videoItem: SVGAVideoEntity)
        fun onError(e: Exception, alias: String)
    }

    interface PlayCallback {
        fun onPlay(file: List<File>)
    }

    companion object {
        private val ongoingTasks = ConcurrentHashMap<String, MutableList<ParseCompletion>>()
        private val fileLocks = ConcurrentHashMap<String, Any>()
        val threadNum = AtomicInteger(0)

        @JvmStatic
        internal var threadPoolExecutor: ExecutorService = ThreadPoolExecutor(
            4, 12, 30L, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>()
        ) { r -> Thread(r, "SVGAParser-Thread-${threadNum.getAndIncrement()}") }

        fun setThreadPoolExecutor(executor: ExecutorService) {
            threadPoolExecutor = executor
        }

        private val mShareParser = SVGAParser(null)
        fun shareParser(): SVGAParser = mShareParser
    }

    fun init(context: Context) {
        mContext = context.applicationContext
        SVGACache.onCreate(mContext)
    }

    fun setFrameSize(frameWidth: Int, frameHeight: Int) {
        mFrameWidth = frameWidth
        mFrameHeight = frameHeight
    }

    private val handler = Handler(Looper.getMainLooper())

    fun decodeFromURL(
        url: URL,
        callback: ParseCompletion?,
        playCallback: PlayCallback? = null
    ): (() -> Unit)? {
        if (mContext == null) return null
        val cacheKey = SVGACache.buildCacheKey(url)
        
        val cachedEntity = SVGAActivitiesCache.instance.get(cacheKey)
        if (cachedEntity != null) {
            callback?.onComplete(cachedEntity)
            return null
        }

        synchronized(ongoingTasks) {
            val list = ongoingTasks[cacheKey]
            if (list != null) {
                callback?.let { list.add(it) }
                return {
                    synchronized(ongoingTasks) {
                        ongoingTasks[cacheKey]?.remove(callback)
                    }
                }
            }
            ongoingTasks[cacheKey] = mutableListOf<ParseCompletion>().apply { callback?.let { add(it) } }
        }

        val internalCallback = object : ParseCompletion {
            override fun onComplete(videoItem: SVGAVideoEntity) = notifyResult(cacheKey, videoItem, null)
            override fun onError(e: Exception, alias: String) = notifyResult(cacheKey, null, e)
        }

        val cacheDir = SVGACache.buildCacheDir(cacheKey)
        var cancelAction: (() -> Unit)? = null

        if (cacheDir.exists() && !cacheDir.listFiles().isNullOrEmpty()) {
            threadPoolExecutor.execute {
                try {
                    decodeFromCacheDir(cacheDir, cacheKey, object : ParseCompletion {
                        override fun onComplete(videoItem: SVGAVideoEntity) = internalCallback.onComplete(videoItem)
                        override fun onError(e: Exception, alias: String) {
                            cancelAction = startNetworkDecodeInternal(url, cacheKey, internalCallback, playCallback)
                        }
                    }, url.toString())
                } catch (e: Exception) {
                    cancelAction = startNetworkDecodeInternal(url, cacheKey, internalCallback, playCallback)
                }
            }
        } else {
            cancelAction = startNetworkDecodeInternal(url, cacheKey, internalCallback, playCallback)
        }

        return {
            synchronized(ongoingTasks) {
                val list = ongoingTasks[cacheKey]
                list?.remove(callback)
                if (list.isNullOrEmpty()) {
                    ongoingTasks.remove(cacheKey)
                    cancelAction?.invoke()
                }
            }
        }
    }

    private fun startNetworkDecodeInternal(url: URL, cacheKey: String, callback: ParseCompletion, playCallback: PlayCallback?): (() -> Unit)? {
        return FileDownloader().resume(url, { inputStream ->
            decodeFromInputStream(inputStream, cacheKey, callback, false, playCallback, url.toString())
        }, { e -> callback.onError(e, url.toString()) })
    }

    private fun notifyResult(cacheKey: String, entity: SVGAVideoEntity?, e: Exception?) {
        val callbacks = synchronized(ongoingTasks) { ongoingTasks.remove(cacheKey) }
        handler.post {
            callbacks?.forEach {
                if (entity != null) it.onComplete(entity)
                else it.onError(e ?: Exception("Load failed"), cacheKey)
            }
        }
    }

    fun decodeFromFile(
        file: File,
        callback: ParseCompletion?,
        playCallback: PlayCallback? = null
    ): (() -> Unit)? {
        val cacheKey = SVGACache.buildCacheKey("file://${file.absolutePath}")
        val cachedEntity = SVGAActivitiesCache.instance.get(cacheKey)
        if (cachedEntity != null) {
            callback?.onComplete(cachedEntity)
            return null
        }

        val future = threadPoolExecutor.submit {
            try {
                if (!file.exists()) throw Exception("File not found")
                // 修正点：不要在这里使用 use 自动关闭流，
                // 而是交给底层的异步解析任务在 readBytes 完成后再关闭。
                val inputStream = FileInputStream(file)
                decodeFromInputStream(inputStream, cacheKey, callback, true, playCallback, file.absolutePath)
            } catch (e: Exception) {
                handler.post { callback?.onError(e, file.absolutePath) }
            }
        }
        return { future.cancel(true) }
    }

    fun decodeFromAssets(
        name: String,
        callback: ParseCompletion?,
        playCallback: PlayCallback? = null
    ): (() -> Unit)? {
        val alias = if (name.startsWith("file:///assets/")) name else "file:///assets/$name"
        val cacheKey = SVGACache.buildCacheKey(alias)
        val cachedEntity = SVGAActivitiesCache.instance.get(cacheKey)
        if (cachedEntity != null) {
            callback?.onComplete(cachedEntity)
            return null
        }

        val future = threadPoolExecutor.submit {
            try {
                val assetName = if (name.startsWith("file:///assets/")) name.substring(15) else name
                mContext?.assets?.open(assetName)?.let {
                    decodeFromInputStream(it, cacheKey, callback, true, playCallback, alias)
                }
            } catch (e: Exception) {
                Log.e("SVGAParser", "Asset open failed: $name", e)
                handler.post { callback?.onError(e, name) }
            }
        }
        return { future.cancel(true) }
    }

    fun decodeFromInputStream(
        inputStream: InputStream, cacheKey: String, callback: ParseCompletion?,
        closeInputStream: Boolean = false, playCallback: PlayCallback? = null, alias: String? = null
    ) {
        threadPoolExecutor.execute {
            try {
                val bytes = inputStream.readBytes()
                if (bytes.isEmpty()) throw Exception("Empty input stream")
                
                val cacheDir = SVGACache.buildCacheDir(cacheKey)
                val lock = fileLocks.getOrPut(cacheKey) { Any() }
                synchronized(lock) {
                    if (isZipFile(bytes)) {
                        if (!cacheDir.exists() || cacheDir.listFiles().isNullOrEmpty()) {
                            unzip(ByteArrayInputStream(bytes), cacheKey)
                        }
                        decodeFromCacheDir(cacheDir, cacheKey, callback, alias)
                    } else {
                        inflate(bytes)?.let { decoded ->
                            val entity = SVGAVideoEntity(
                                MovieEntity.ADAPTER.decode(decoded),
                                cacheDir,
                                mFrameWidth,
                                mFrameHeight,
                                false
                            )
                            entity.prepare({
                                SVGAActivitiesCache.instance.put(cacheKey, entity)
                                handler.post { callback?.onComplete(entity) }
                            }, playCallback)
                        } ?: throw Exception("Inflate failed")
                    }
                }
            } catch (e: Exception) {
                Log.e("SVGAParser", "Decode failed: $alias", e)
                handler.post { callback?.onError(e, alias ?: "") }
            } finally {
                if (closeInputStream) try { inputStream.close() } catch (e: Exception) {}
            }
        }
    }

    private fun decodeFromCacheDir(cacheDir: File, cacheKey: String, callback: ParseCompletion?, alias: String?) {
        val binaryFile = File(cacheDir, "movie.binary")
        val specFile = File(cacheDir, "movie.spec")
        try {
            val entity = when {
                binaryFile.exists() -> SVGAVideoEntity(MovieEntity.ADAPTER.decode(binaryFile.readBytes()), cacheDir, mFrameWidth, mFrameHeight, false)
                specFile.exists() -> SVGAVideoEntity(JSONObject(specFile.readText()), cacheDir, mFrameWidth, mFrameHeight, false)
                else -> throw Exception("Metadata not found in $cacheDir")
            }
            entity.prepare({
                SVGAActivitiesCache.instance.put(cacheKey, entity)
                handler.post { callback?.onComplete(entity) }
            }, null)
        } catch (e: Exception) {
            Log.e("SVGAParser", "CacheDir decode failed", e)
            handler.post { callback?.onError(e, alias ?: "") }
        }
    }

    private fun isZipFile(bytes: ByteArray): Boolean =
        bytes.size > 4 && bytes[0].toInt() == 80 && bytes[1].toInt() == 75 && bytes[2].toInt() == 3 && bytes[3].toInt() == 4

    private fun unzip(inputStream: InputStream, cacheKey: String) {
        val cacheDir = SVGACache.buildCacheDir(cacheKey).apply { if (!exists()) mkdirs() }
        val canonicalDestPath = cacheDir.canonicalPath
        ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.contains("../")) throw SecurityException("Zip Slip!")
                val file = File(cacheDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos -> zis.copyTo(fos) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun inflate(byteArray: ByteArray): ByteArray? {
        val inflater = Inflater()
        inflater.setInput(byteArray)
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        try {
            while (!inflater.finished()) {
                val count = try { inflater.inflate(buffer) } catch (e: Exception) { 0 }
                if (count == 0 && (inflater.needsInput() || inflater.finished())) break
                baos.write(buffer, 0, count)
            }
        } finally {
            inflater.end()
        }
        return baos.toByteArray()
    }

    private class FileDownloader {
        fun resume(url: URL, complete: (InputStream) -> Unit, failure: (Exception) -> Unit): () -> Unit {
            var cancelled = false
            threadPoolExecutor.execute {
                try {
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 SVGA/Compose")
                    conn.connect()
                    if (!cancelled) complete(conn.inputStream)
                } catch (e: Exception) {
                    if (!cancelled) failure(e)
                }
            }
            return { cancelled = true }
        }
    }
}
