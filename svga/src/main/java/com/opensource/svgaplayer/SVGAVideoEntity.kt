package com.opensource.svgaplayer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import com.opensource.svgaplayer.bitmap.SVGABitmapByteArrayDecoder
import com.opensource.svgaplayer.bitmap.SVGABitmapFileDecoder
import com.opensource.svgaplayer.entities.SVGAAudioEntity
import com.opensource.svgaplayer.entities.SVGAVideoSpriteEntity
import com.opensource.svgaplayer.proto.AudioEntity
import com.opensource.svgaplayer.proto.MovieEntity
import com.opensource.svgaplayer.proto.MovieParams
import com.opensource.svgaplayer.utils.SVGARect
import com.opensource.svgaplayer.utils.log.LogUtils
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Created by PonyCui on 16/6/18.
 */
class SVGAVideoEntity {

    private val TAG = "SVGAVideoEntity"

    var antiAlias = true
    private var movieItem: MovieEntity? = null

    var videoSize = SVGARect(0.0, 0.0, 0.0, 0.0)
        private set

    var FPS = 15
        private set

    var frames: Int = 0
        private set
    var cacheKey: String = ""

    var isCleared = false

    internal var spriteList: List<SVGAVideoSpriteEntity> = emptyList()
    internal var audioList: List<SVGAAudioEntity> = emptyList()
    internal var soundPool: SoundPool? = null
    private var soundCallback: SVGASoundManager.SVGASoundCallBack? = null
    internal var imageMap = HashMap<String, Bitmap>()
    private var mCacheDir: File
    private var mFrameHeight = 0
    private var mFrameWidth = 0
    private var mPlayCallback: SVGAParser.PlayCallback? = null
    private var mCallback: (() -> Unit)? = null
    private lateinit var mByteArray: ByteArray
    private var mNeedCutBitmap: Boolean = false

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
        mFrameWidth = frameWidth
        mFrameHeight = frameHeight
        mCacheDir = cacheDir
        mNeedCutBitmap = needCutBitmap
        try {
            json.optJSONObject("movie")?.let { setupByJson(it) }
        } catch (e: Exception) {}

        try {
            parserImages(json)
        } catch (e: Exception) {}
        resetSprites(json)
    }

    private fun setupByJson(movieObject: JSONObject) {
        movieObject.optJSONObject("viewBox")?.let { viewBoxObject ->
            val width = viewBoxObject.optDouble("width", 0.0)
            val height = viewBoxObject.optDouble("height", 0.0)
            videoSize = SVGARect(0.0, 0.0, width, height)
        }
        FPS = movieObject.optInt("fps", 20)
        frames = movieObject.optInt("frames", 0)
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
        this.mFrameWidth = frameWidth
        this.mFrameHeight = frameHeight
        this.mCacheDir = cacheDir
        this.movieItem = entity
        mNeedCutBitmap = needCutBitmap
        entity.params?.let(this::setupByMovie)
        try {
            parserImages(entity)
        } catch (e: Exception) {}
        resetSprites(entity)
        if (entity.audios == null || entity.audios.isEmpty()) {
            movieItem = null
        }
    }

    private fun setupByMovie(movieParams: MovieParams) {
        val width = (movieParams.viewBoxWidth ?: 0.0f).toDouble()
        val height = (movieParams.viewBoxHeight ?: 0.0f).toDouble()
        videoSize = SVGARect(0.0, 0.0, width, height)
        FPS = movieParams.fps ?: 20
        frames = movieParams.frames ?: 0
    }

    internal fun prepare(callback: () -> Unit, playCallback: SVGAParser.PlayCallback?) {
        mCallback = callback
        mPlayCallback = playCallback
        if (movieItem == null) {
            mCallback?.invoke()
        } else {
            setupAudios(movieItem!!) {
                mCallback?.invoke()
                movieItem = null
            }
        }
    }

    private fun parserImages(json: JSONObject) {
        val imgJson = json.optJSONObject("images") ?: return
        imgJson.keys().forEach { imgKey ->
            val filePath = generateBitmapFilePath(imgJson[imgKey].toString(), imgKey)
            if (filePath.isNotEmpty()) {
                val bitmapKey = imgKey.replace(".matte", "")
                createBitmap(filePath)?.let { imageMap[bitmapKey] = it }
            }
        }
    }

    private fun generateBitmapFilePath(imgName: String, imgKey: String): String {
        val path = mCacheDir.absolutePath + "/" + imgName
        return when {
            File(path).exists() -> path
            File("$path.png").exists() -> "$path.png"
            File(mCacheDir.absolutePath + "/" + imgKey + ".png").exists() -> mCacheDir.absolutePath + "/" + imgKey + ".png"
            else -> ""
        }
    }

    private fun createBitmap(filePath: String): Bitmap? {
        return SVGABitmapFileDecoder.decodeBitmapFrom(
            filePath,
            mFrameWidth,
            mFrameHeight,
            needCutBitmap = mNeedCutBitmap
        )
    }

    private fun parserImages(obj: MovieEntity) {
        obj.images?.entries?.forEach { entry ->
            val byteArray = entry.value.toByteArray()
            if (byteArray.size < 4) {
                return@forEach
            }
            val filePath = generateBitmapFilePath(entry.value.utf8(), entry.key)
            val bitmap = SVGABitmapByteArrayDecoder.decodeBitmapFrom(
                byteArray,
                mFrameWidth,
                mFrameHeight,
                needCutBitmap = mNeedCutBitmap
            ) ?: createBitmap(filePath)
            
            bitmap?.let { imageMap[entry.key] = it }
        }
    }

    private fun resetSprites(json: JSONObject) {
        val mutableList: MutableList<SVGAVideoSpriteEntity> = mutableListOf()
        json.optJSONArray("sprites")?.let { item ->
            for (i in 0 until item.length()) {
                item.optJSONObject(i)?.let { entryJson ->
                    mutableList.add(SVGAVideoSpriteEntity(entryJson))
                }
            }
        }
        spriteList = mutableList
    }

    private fun resetSprites(entity: MovieEntity) {
        spriteList = entity.sprites?.map {
            return@map SVGAVideoSpriteEntity(it)
        } ?: listOf()
    }

    private fun setupAudios(entity: MovieEntity, completionBlock: () -> Unit) {
        if (entity.audios == null || entity.audios.isEmpty()) {
            completionBlock()
            return
        }
        setupSoundPool(entity, completionBlock)
        val audiosFileMap = generateAudioFileMap(entity)
        if (audiosFileMap.isEmpty()) {
            completionBlock()
            return
        }
        this.audioList = entity.audios.map { audio ->
            return@map createSvgaAudioEntity(audio, audiosFileMap)
        }
    }

    private fun createSvgaAudioEntity(
        audio: AudioEntity,
        audiosFileMap: HashMap<String, File>
    ): SVGAAudioEntity {
        val item = SVGAAudioEntity(audio)
        val startTime = (audio.startTime ?: 0).toDouble()
        val totalTime = (audio.totalTime ?: 0).toDouble()
        if (totalTime.toInt() == 0) {
            return item
        }
        
        val currentPlayCallback = mPlayCallback
        if (currentPlayCallback != null) {
            val fileList = audiosFileMap.values.toList()
            currentPlayCallback.onPlay(fileList)
            mCallback?.invoke()
            return item
        }

        audiosFileMap[audio.audioKey]?.let { file ->
            FileInputStream(file).use {
                val length = it.available().toDouble()
                val offset = ((startTime / totalTime) * length).toLong()
                if (SVGASoundManager.isInit()) {
                    item.soundID = SVGASoundManager.load(
                        soundCallback,
                        it.fd,
                        offset,
                        length.toLong(),
                        1
                    )
                } else {
                    item.soundID = soundPool?.load(it.fd, offset, length.toLong(), 1)
                }
            }
        }
        return item
    }

    private fun generateAudioFileMap(entity: MovieEntity): HashMap<String, File> {
        val audiosDataMap = generateAudioMap(entity)
        val audiosFileMap = HashMap<String, File>()
        audiosDataMap.forEach {
            val audioCache = SVGACache.buildAudioFile(it.key)
            if (!audioCache.exists()) {
                audioCache.createNewFile()
                FileOutputStream(audioCache).write(it.value)
            }
            audiosFileMap[it.key] = audioCache
        }
        return audiosFileMap
    }

    private fun generateAudioMap(entity: MovieEntity): HashMap<String, ByteArray> {
        val audiosDataMap = HashMap<String, ByteArray>()
        entity.images?.entries?.forEach {
            val imageKey = it.key
            val byteArray = it.value.toByteArray()
            if (byteArray.size < 4) {
                return@forEach
            }
            val b0 = byteArray[0].toInt()
            val b1 = byteArray[1].toInt()
            val b2 = byteArray[2].toInt()
            if ((b0 == 73 && b1 == 68 && b2 == 51) || (b0 == -1 && b1 == -5 && b2 == -108)) {
                audiosDataMap[imageKey] = byteArray
            }
        }
        return audiosDataMap
    }

    private fun setupSoundPool(entity: MovieEntity, completionBlock: () -> Unit) {
        var soundLoaded = 0
        if (SVGASoundManager.isInit()) {
            soundCallback = object : SVGASoundManager.SVGASoundCallBack {
                override fun onVolumeChange(value: Float) {
                    SVGASoundManager.setVolume(value, this@SVGAVideoEntity)
                }

                override fun onComplete() {
                    soundLoaded++
                    if (soundLoaded >= entity.audios.size) {
                        completionBlock()
                    }
                }
            }
            return
        }
        soundPool = if (Build.VERSION.SDK_INT >= 21) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
            SoundPool.Builder().setAudioAttributes(attributes)
                .setMaxStreams(12.coerceAtMost(entity.audios.size))
                .build()
        } else {
            SoundPool(12.coerceAtMost(entity.audios.size), AudioManager.STREAM_MUSIC, 0)
        }
        soundPool?.setOnLoadCompleteListener { _, _, _ ->
            soundLoaded++
            if (soundLoaded >= entity.audios.size) {
                completionBlock()
            }
        }
    }

    fun clear() {
        isCleared = true
        if (SVGASoundManager.isInit()) {
            this.audioList.forEach {
                it.soundID?.let { id -> SVGASoundManager.unload(id) }
            }
            soundCallback = null
        }
        soundPool?.release()
        soundPool = null
        mPlayCallback = null
        soundCallback = null
        mCallback = null
        if (cacheKey.isEmpty()) {
            audioList = emptyList()
            spriteList = emptyList()
            imageMap.clear()
        }
    }

    fun clearCallback() {
        mCallback = null
    }
}
