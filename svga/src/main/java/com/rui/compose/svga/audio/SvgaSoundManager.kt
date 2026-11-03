package com.rui.compose.svga.audio

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import com.rui.compose.svga.entity.SvgaVideoEntity
import com.rui.compose.svga.utils.log.LogUtils
import java.io.FileDescriptor
import java.util.concurrent.ConcurrentHashMap

/**
 * SVGA 音频全局管理器
 */
object SvgaSoundManager {

    private const val TAG = "SvgaSoundManager"

    private var soundPool: SoundPool? = null
    private val soundCallbackMap = ConcurrentHashMap<Int, SvgaSoundCallback>()
    private var volume: Float = 1f

    fun init(maxStreams: Int = 20) {
        LogUtils.debug(TAG, "init maxStreams=$maxStreams")
        if (soundPool != null) return
        soundPool = createSoundPool(maxStreams).apply {
            setOnLoadCompleteListener { _, soundId, status ->
                LogUtils.debug(TAG, "SoundPool onLoadComplete soundId=$soundId status=$status")
                soundCallbackMap[soundId]?.onComplete()
            }
        }
    }

    fun release() {
        LogUtils.debug(TAG, "release")
        soundCallbackMap.clear()
        soundPool?.release()
        soundPool = null
    }

    fun setVolume(volume: Float, entity: SvgaVideoEntity? = null) {
        if (!checkInit()) return
        val clamped = volume.coerceIn(0f, 1f)

        if (entity == null) {
            this.volume = clamped
            soundCallbackMap.values.forEach { it.onVolumeChange(clamped) }
            return
        }

        val pool = soundPool ?: return
        entity.audioList.forEach { audio ->
            audio.playID?.let { pool.setVolume(it, clamped, clamped) }
        }
    }

    fun isInit(): Boolean = soundPool != null

    private fun checkInit(): Boolean {
        if (!isInit()) {
            LogUtils.error(TAG, "soundPool is null, you need call init() first!")
            return false
        }
        return true
    }

    private fun createSoundPool(maxStreams: Int): SoundPool {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
            SoundPool.Builder()
                .setAudioAttributes(attributes)
                .setMaxStreams(maxStreams)
                .build()
        } else {
            @Suppress("DEPRECATION")
            SoundPool(maxStreams, AudioManager.STREAM_MUSIC, 0)
        }
    }

    internal fun load(
        callback: SvgaSoundCallback?,
        path: String,
        priority: Int = 1
    ): Int {
        if (!checkInit()) return -1
        val soundId = soundPool!!.load(path, priority)
        if (callback != null) soundCallbackMap[soundId] = callback
        return soundId
    }

    internal fun load(
        callback: SvgaSoundCallback?,
        fd: FileDescriptor?,
        offset: Long,
        length: Long,
        priority: Int = 1
    ): Int {
        if (!checkInit()) return -1
        val soundId = soundPool!!.load(fd, offset, length, priority)
        if (callback != null) soundCallbackMap[soundId] = callback
        return soundId
    }

    internal fun unload(soundId: Int) {
        if (!checkInit()) return
        soundPool!!.unload(soundId)
        soundCallbackMap.remove(soundId)
    }

    internal fun play(soundId: Int, playVolume: Float = volume): Int {
        if (!checkInit()) return -1
        return soundPool!!.play(soundId, playVolume, playVolume, 1, 0, 1.0f)
    }

    internal fun stop(soundId: Int) {
        if (!checkInit()) return
        soundPool!!.stop(soundId)
    }

    internal fun resume(soundId: Int) {
        if (!checkInit()) return
        soundPool!!.resume(soundId)
    }

    internal fun pause(soundId: Int) {
        if (!checkInit()) return
        soundPool!!.pause(soundId)
    }
}
