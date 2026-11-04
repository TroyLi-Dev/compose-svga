package com.rui.compose.svga.entity

import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.rui.compose.svga.proto.AudioEntity
import com.rui.compose.svga.proto.MovieEntity
import com.rui.compose.svga.proto.MovieParams
import com.rui.compose.svga.audio.SvgaSoundCallback
import com.rui.compose.svga.audio.SvgaSoundManager
import com.rui.compose.svga.cache.SvgaDiskCache
import com.rui.compose.svga.cache.SvgaMemoryCache
import com.rui.compose.svga.decode.SvgaBitmapByteArrayDecoder
import com.rui.compose.svga.decode.SvgaBitmapFileDecoder
import com.rui.compose.svga.utils.SvgaRect
import com.rui.compose.svga.utils.log.LogUtils
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SvgaVideoEntity {

    private val TAG = "SvgaVideoEntity"

    var antiAlias = true
    private var movieItem: MovieEntity? = null

    var videoSize = SvgaRect(0.0, 0.0, 0.0, 0.0)
        private set

    var fps = 15
        private set

    // 向下兼容大写 FPS 属性
    val FPS: Int get() = fps

    var frames: Int = 0
        private set

    var cacheKey: String = ""

    var isCleared = false

    @Volatile
    var volume: Float = 1.0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            field = clamped
            if (SvgaSoundManager.isInit()) {
                SvgaSoundManager.setVolume(clamped, this)
            } else {
                val pool = soundPool ?: return
                audioList.forEach { audio ->
                    audio.playID?.let {
                        try {
                            pool.setVolume(it, clamped, clamped)
                        } catch (_: Throwable) {}
                    }
                }
            }
        }

    internal var spriteList: List<SvgaVideoSpriteEntity> = emptyList()
    internal var audioList: List<SvgaAudioEntity> = emptyList()
    internal var soundPool: SoundPool? = null
    private var soundCallback: SvgaSoundCallback? = null
    internal var imageMap = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

    val memoryByteSize: Int by lazy {
        var size = 0
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Bitmap, Boolean>())
        imageMap.values.forEach { bitmap ->
            if (!bitmap.isRecycled && visited.add(bitmap)) {
                size += bitmap.byteCount
            }
        }
        if (size <= 0) 1024 else size
    }
    private var cacheDir: File
    private var frameHeight = 0
    private var frameWidth = 0
    private var playCallback: ((List<File>) -> Unit)? = null
    private var callback: (() -> Unit)? = null
    private var needCutBitmap: Boolean = false

    constructor(json: JSONObject, cacheDir: File, needCutBitmap: Boolean = false) : this(
        json,
        cacheDir,
        0,
        0,
        needCutBitmap
    )

    constructor(
        json: JSONObject,
        cacheDir: File,
        frameWidth: Int,
        frameHeight: Int,
        needCutBitmap: Boolean = false
    ) {
        this.frameWidth = frameWidth
        this.frameHeight = frameHeight
        this.cacheDir = cacheDir
        this.needCutBitmap = needCutBitmap
        try {
            json.optJSONObject("movie")?.let { setupByJson(it) }
        } catch (_: Throwable) {
        }

        try {
            parseImages(json)
        } catch (_: Throwable) {
        }
        resetSprites(json)
    }

    private fun setupByJson(movieObject: JSONObject) {
        movieObject.optJSONObject("viewBox")?.let { viewBoxObject ->
            val width = kotlin.math.max(0.0, viewBoxObject.optDouble("width", 0.0))
            val height = kotlin.math.max(0.0, viewBoxObject.optDouble("height", 0.0))
            videoSize = SvgaRect(0.0, 0.0, width, height)
        }
        val readFps = movieObject.optInt("fps", 20)
        fps = if (readFps > 0) readFps else 20
        frames = kotlin.math.max(0, movieObject.optInt("frames", 0))
    }

    constructor(entity: MovieEntity, cacheDir: File, needCutBitmap: Boolean = false) : this(
        entity,
        cacheDir,
        0,
        0,
        needCutBitmap
    )

    constructor(
        entity: MovieEntity,
        cacheDir: File,
        frameWidth: Int,
        frameHeight: Int,
        needCutBitmap: Boolean = false
    ) {
        this.frameWidth = frameWidth
        this.frameHeight = frameHeight
        this.cacheDir = cacheDir
        this.movieItem = entity
        this.needCutBitmap = needCutBitmap
        entity.params?.let(this::setupByMovie)
        try {
            parseImages(entity)
        } catch (_: Throwable) {
        }
        resetSprites(entity)
        if (entity.audios.isEmpty()) {
            movieItem = null
        }
    }

    private fun setupByMovie(movieParams: MovieParams) {
        val width = kotlin.math.max(0.0, movieParams.viewBoxWidth.toDouble())
        val height = kotlin.math.max(0.0, movieParams.viewBoxHeight.toDouble())
        videoSize = SvgaRect(0.0, 0.0, width, height)
        fps = if (movieParams.fps > 0) movieParams.fps else 20
        frames = kotlin.math.max(0, movieParams.frames)
    }

    fun prepare(callback: () -> Unit, playCallback: ((List<File>) -> Unit)? = null) {
        this.callback = callback
        this.playCallback = playCallback
        precompileShapePaths()
        val movie = movieItem
        if (movie == null) {
            this.callback?.invoke()
        } else {
            setupAudios(movie) {
                this.callback?.invoke()
                movieItem = null
            }
        }
    }

    /**
     * 在后台准备阶段预先编译解析矢量 Shape Path 和 Mask Path，彻底避免在 120FPS 渲染主线程中解析 SVG 字符串
     */
    private fun precompileShapePaths() {
        try {
            val dummyPath = android.graphics.Path()
            spriteList.forEach { sprite ->
                sprite.frames.forEach { frame ->
                    frame.maskPath?.buildPath(dummyPath)
                    frame.shapes.forEach { shape ->
                        shape.buildPath()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun parseImages(json: JSONObject) {
        val imgJson = json.optJSONObject("images") ?: return
        imgJson.keys().forEach { imgKey ->
            val filePath = resolveBitmapFilePath(imgJson[imgKey].toString(), imgKey)
            if (filePath.isNotEmpty()) {
                val bitmapKey = imgKey.replace(".matte", "")
                createBitmap(filePath)?.let { bmp ->
                    imageMap[imgKey] = bmp
                    if (bitmapKey != imgKey) {
                        imageMap[bitmapKey] = bmp
                    }
                }
            }
        }
    }

    private fun resolveBitmapFilePath(imgName: String, imgKey: String): String {
        val path = "${cacheDir.absolutePath}/$imgName"
        return when {
            File(path).exists() -> path
            File("$path.png").exists() -> "$path.png"
            File("${cacheDir.absolutePath}/$imgKey.png").exists() -> "${cacheDir.absolutePath}/$imgKey.png"
            else -> ""
        }
    }

    private fun createBitmap(filePath: String): Bitmap? {
        return SvgaBitmapFileDecoder.decodeBitmapFrom(
            filePath,
            frameWidth,
            frameHeight,
            needCutBitmap = needCutBitmap
        )
    }

    private fun isAudioData(imageKey: String, byteArray: ByteArray, audioKeys: Set<String>): Boolean {
        if (audioKeys.contains(imageKey)) return true
        if (byteArray.size < 4) return false
        val b0 = byteArray[0].toInt() and 0xFF
        val b1 = byteArray[1].toInt() and 0xFF
        val b2 = byteArray[2].toInt() and 0xFF
        val b3 = byteArray[3].toInt() and 0xFF
        // ID3v2 头部: "ID3"
        if (b0 == 0x49 && b1 == 0x44 && b2 == 0x33) return true
        // MPEG 同步字: 11 bits 全部置 1 (0xFFE0) 适配所有比特率与采样率的 MP3 裸流
        if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) return true
        // OggS 容器头部: "OggS"
        if (b0 == 0x4F && b1 == 0x67 && b2 == 0x67 && b3 == 0x53) return true
        // RIFF/WAV 容器头部: "RIFF"
        if (b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46) return true
        return false
    }

    private fun parseImages(obj: MovieEntity) {
        val audioKeys = obj.audios.mapNotNull { it.audioKey }.toSet()
        obj.images.entries.forEach { entry ->
            val byteArray = entry.value.toByteArray()
            if (byteArray.size < 4) return@forEach
            // 过滤音频数据，避免将音频字节流误解析为位图引发解码报错
            if (isAudioData(entry.key, byteArray, audioKeys)) {
                return@forEach
            }
            val bitmap = SvgaBitmapByteArrayDecoder.decodeBitmapFrom(
                byteArray,
                frameWidth,
                frameHeight,
                needCutBitmap = needCutBitmap
            ) ?: run {
                // 仅当直接内存解码失败时，作为降级策略才尝试从缓存目录解析关联文件
                val imgName = try { entry.value.utf8() } catch (_: Exception) { "" }
                val filePath = resolveBitmapFilePath(imgName, entry.key)
                createBitmap(filePath)
            }

            val bitmapKey = entry.key.replace(".matte", "")
            bitmap?.let { bmp ->
                imageMap[entry.key] = bmp
                if (bitmapKey != entry.key) {
                    imageMap[bitmapKey] = bmp
                }
            }
        }
    }

    private fun resetSprites(json: JSONObject) {
        val list = mutableListOf<SvgaVideoSpriteEntity>()
        json.optJSONArray("sprites")?.let { item ->
            for (i in 0 until item.length()) {
                item.optJSONObject(i)?.let { entryJson ->
                    list.add(SvgaVideoSpriteEntity(entryJson))
                }
            }
        }
        spriteList = list
    }

    private fun resetSprites(entity: MovieEntity) {
        spriteList = entity.sprites.map {
            SvgaVideoSpriteEntity(it)
        }
    }

    private var audioTimeoutRunnable: Runnable? = null

    private fun setupAudios(entity: MovieEntity, completionBlock: () -> Unit) {
        if (entity.audios.isEmpty()) {
            completionBlock()
            return
        }

        val audiosFileMap = generateAudioFileMap(entity)
        if (audiosFileMap.isEmpty()) {
            completionBlock()
            return
        }

        val currentPlayCallback = playCallback
        if (currentPlayCallback != null) {
            val fileList = audiosFileMap.values.toList()
            currentPlayCallback.invoke(fileList)
            this.audioList = entity.audios.map { SvgaAudioEntity(it) }
            completionBlock()
            return
        }

        val hasCompleted = AtomicBoolean(false)
        val mainHandler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (hasCompleted.compareAndSet(false, true)) {
                LogUtils.warn(TAG, "setupAudios timeout (2000ms), forcing completion")
                completionBlock()
            }
        }
        audioTimeoutRunnable = timeoutRunnable
        mainHandler.postDelayed(timeoutRunnable, 2000L)

        val safeComplete: () -> Unit = {
            if (hasCompleted.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeoutRunnable)
                audioTimeoutRunnable = null
                completionBlock()
            }
        }

        val validAudios = entity.audios.filter { audio ->
            audio.totalTime.toDouble().toInt() > 0 &&
            audiosFileMap[audio.audioKey]?.let { it.exists() && it.length() > 0 } == true
        }
        val uniqueAudioFiles = validAudios.mapNotNull { it.audioKey }
            .distinct()
            .mapNotNull { audiosFileMap[it] }
            .filter { it.exists() && it.length() > 0 }
            .distinctBy { it.absolutePath }

        val targetCount = uniqueAudioFiles.size
        if (targetCount == 0) {
            this.audioList = entity.audios.map { SvgaAudioEntity(it) }
            safeComplete()
            return
        }

        val soundLoaded = AtomicInteger(0)
        setupSoundPool(entity) {
            if (soundLoaded.incrementAndGet() >= targetCount) {
                safeComplete()
            }
        }

        val soundIdMap = HashMap<String, Int>()
        uniqueAudioFiles.forEach { file ->
            val soundId = if (SvgaSoundManager.isInit()) {
                SvgaSoundManager.load(soundCallback, file.absolutePath, 1)
            } else {
                try {
                    soundPool?.load(file.absolutePath, 1)
                } catch (t: Throwable) {
                    LogUtils.error(TAG, "SoundPool load failed for ${file.absolutePath}", t)
                    null
                }
            }
            if (soundId != null && soundId > 0) {
                soundIdMap[file.absolutePath] = soundId
            } else {
                if (soundLoaded.incrementAndGet() >= targetCount) {
                    safeComplete()
                }
            }
        }

        this.audioList = entity.audios.map { audio ->
            val item = SvgaAudioEntity(audio)
            if (audio.totalTime.toDouble().toInt() > 0) {
                audiosFileMap[audio.audioKey]?.let { file ->
                    item.soundID = soundIdMap[file.absolutePath]
                }
            }
            item
        }
    }

    private fun generateAudioFileMap(entity: MovieEntity): HashMap<String, File> {
        val audiosDataMap = generateAudioMap(entity)
        val audiosFileMap = HashMap<String, File>()
        audiosDataMap.forEach { (key, bytes) ->
            val audioCache = SvgaDiskCache.buildAudioFile(key, cacheDir)
            if (!audioCache.exists() || audioCache.length() == 0L) {
                val audioDir = audioCache.parentFile ?: cacheDir
                val tmpFile = File(audioDir, "${audioCache.name}.${System.nanoTime()}.tmp")
                try {
                    FileOutputStream(tmpFile).use { fos ->
                        fos.write(bytes)
                        fos.flush()
                    }
                    if (!tmpFile.renameTo(audioCache)) {
                        if (!audioCache.exists()) {
                            try { tmpFile.copyTo(audioCache, overwrite = true) } catch (_: Throwable) {}
                        }
                        tmpFile.delete()
                    }
                } catch (e: Exception) {
                    tmpFile.delete()
                    LogUtils.error(TAG, e)
                }
            }
            if (audioCache.exists() && audioCache.length() > 0L) {
                audiosFileMap[key] = audioCache
            }
        }
        return audiosFileMap
    }

    private fun generateAudioMap(entity: MovieEntity): HashMap<String, ByteArray> {
        val audiosDataMap = HashMap<String, ByteArray>()
        val audioKeys = entity.audios.mapNotNull { it.audioKey }.toSet()
        entity.images.entries.forEach {
            val imageKey = it.key
            val byteArray = it.value.toByteArray()
            if (isAudioData(imageKey, byteArray, audioKeys)) {
                audiosDataMap[imageKey] = byteArray
            }
        }
        return audiosDataMap
    }

    private fun setupSoundPool(entity: MovieEntity, onOneSoundLoaded: () -> Unit) {
        if (SvgaSoundManager.isInit()) {
            soundCallback = object : SvgaSoundCallback {
                override fun onVolumeChange(value: Float) {
                    SvgaSoundManager.setVolume(value, this@SvgaVideoEntity)
                }

                override fun onComplete() {
                    onOneSoundLoaded()
                }
            }
            return
        }
        val streamCount = 12.coerceAtMost(kotlin.math.max(1, entity.audios.size))
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        val pool = SoundPool.Builder()
            .setAudioAttributes(attributes)
            .setMaxStreams(streamCount)
            .build()
        pool.setOnLoadCompleteListener { _, _, _ ->
            onOneSoundLoaded()
        }
        soundPool = pool
    }

    fun clear() {
        isCleared = true
        audioTimeoutRunnable?.let {
            Handler(Looper.getMainLooper()).removeCallbacks(it)
            audioTimeoutRunnable = null
        }
        this.audioList.forEach { audio ->
            audio.playID?.let { streamId ->
                try {
                    if (SvgaSoundManager.isInit()) SvgaSoundManager.stop(streamId)
                    else soundPool?.stop(streamId)
                } catch (_: Throwable) {}
                audio.playID = null
            }
        }
        if (SvgaSoundManager.isInit()) {
            this.audioList.mapNotNull { it.soundID }.distinct().forEach { id ->
                SvgaSoundManager.unload(id)
            }
            soundCallback = null
        }
        try {
            soundPool?.setOnLoadCompleteListener(null)
            soundPool?.release()
        } catch (_: Throwable) {}
        soundPool = null
        playCallback = null
        soundCallback = null
        callback = null
        val isCached = cacheKey.isNotEmpty() && SvgaMemoryCache.instance.get(cacheKey) === this
        if (isCached) {
            SvgaMemoryCache.instance.remove(cacheKey)
        }
        audioList = emptyList()
        spriteList = emptyList()
        imageMap.clear()
    }

    fun clearCallback() {
        callback = null
    }
}
