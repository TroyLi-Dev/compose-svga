package com.opensource.svgaplayer

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.opensource.svgaplayer.proto.MovieEntity
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.Inflater

class SVGAParser(context: Context?, val needCutBitmap: Boolean = false) {
    private var mContext = context?.applicationContext
    
    init {
        SVGACache.onCreate(context)
        SVGAMemCalculator.instance.init(context?.applicationContext)
    }

    @Volatile private var mFrameWidth: Int = 0
    @Volatile private var mFrameHeight: Int = 0

    interface ParseCompletion {
        fun onComplete(videoItem: SVGAVideoEntity)
        fun onError(e: Exception, alias: String)
    }

    interface PlayCallback {
        fun onPlay(file: List<File>)
    }

    // 修复编译错误：确保 Companion object 是公开的，且 threadNum 和线程池可访问
    companion object {
        private val ongoingTasks = ConcurrentHashMap<String, MutableList<ParseCompletion>>()
        @JvmField val threadNum = AtomicInteger(0)
        
        @JvmStatic
        @Volatile
        internal var threadPoolExecutor: ExecutorService = ThreadPoolExecutor(
            4, 8, 30L, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>()
        ) { r -> Thread(r, "SVGAParser-Thread-${threadNum.getAndIncrement()}") }
        
        @JvmStatic
        fun setThreadPoolExecutor(executor: ExecutorService) {
            threadPoolExecutor = executor
        }

        private val mShareParser = SVGAParser(null)
        @JvmStatic
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

    fun decodeFromURL(url: URL, callback: ParseCompletion?, playCallback: PlayCallback? = null): (() -> Unit)? {
        if (mContext == null) return null
        val cacheKey = SVGACache.buildCacheKey(url)
        
        val cachedEntity = SVGAActivitiesCache.instance.get(cacheKey)
        if (cachedEntity != null) {
            handler.post { callback?.onComplete(cachedEntity) }
            return null
        }

        synchronized(ongoingTasks) {
            val list = ongoingTasks[cacheKey]
            if (list != null) {
                callback?.let { list.add(it) }
                return null
            }
            ongoingTasks[cacheKey] = mutableListOf<ParseCompletion>().apply { callback?.let { add(it) } }
        }

        return FileDownloader().resume(url, { inputStream ->
            decodeFromInputStream(inputStream, cacheKey, object : ParseCompletion {
                override fun onComplete(videoItem: SVGAVideoEntity) { notifyResult(cacheKey, videoItem, null) }
                override fun onError(e: Exception, alias: String) { notifyResult(cacheKey, null, e) }
            }, false, playCallback, url.toString())
        }, { e -> notifyResult(cacheKey, null, e) })
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

    fun decodeFromAssets(name: String, callback: ParseCompletion?, playCallback: PlayCallback? = null) {
        val cacheKey = SVGACache.buildCacheKey("file:///assets/$name")
        threadPoolExecutor.execute {
            try {
                mContext?.assets?.open(name)?.let {
                    decodeFromInputStream(it, cacheKey, callback, true, playCallback, name)
                }
            } catch (e: Exception) { handler.post { callback?.onError(e, name) } }
        }
    }

    fun decodeFromInputStream(inputStream: InputStream, cacheKey: String, callback: ParseCompletion?, closeInputStream: Boolean = false, playCallback: PlayCallback? = null, alias: String? = null) {
        threadPoolExecutor.execute {
            try {
                val bytes = inputStream.readBytes()
                inflate(bytes)?.let { decoded ->
                    val entity = SVGAVideoEntity(MovieEntity.ADAPTER.decode(decoded), File(cacheKey), mFrameWidth, mFrameHeight, false)
                    entity.prepare({
                        SVGAActivitiesCache.instance.put(cacheKey, entity)
                        handler.post { callback?.onComplete(entity) }
                    }, playCallback)
                }
            } catch (e: Exception) { handler.post { callback?.onError(e, alias ?: "") } }
            finally { if (closeInputStream) inputStream.close() }
        }
    }

    private fun inflate(byteArray: ByteArray): ByteArray? {
        val inflater = Inflater()
        inflater.setInput(byteArray)
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0 && inflater.needsInput()) break
            baos.write(buffer, 0, count)
        }
        inflater.end()
        return baos.toByteArray()
    }

    private class FileDownloader {
        fun resume(url: URL, complete: (InputStream) -> Unit, failure: (Exception) -> Unit): () -> Unit {
            var cancelled = false
            threadPoolExecutor.execute {
                try {
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 SVGA/Compose")
                    conn.connect()
                    if (!cancelled) complete(conn.inputStream)
                } catch (e: Exception) { if (!cancelled) failure(e) }
            }
            return { cancelled = true }
        }
    }
}
