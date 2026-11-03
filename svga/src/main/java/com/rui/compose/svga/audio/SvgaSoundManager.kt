package com.rui.compose.svga.audio

import android.media.AudioAttributes
import android.media.SoundPool
import com.rui.compose.svga.entity.SvgaVideoEntity
import com.rui.compose.svga.utils.log.LogUtils
import java.io.FileDescriptor
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * SVGA 音频全局管理器
 */
object SvgaSoundManager {

    private const val TAG = "SvgaSoundManager"

    private var soundPool: SoundPool? = null
    private val soundCallbackMap = ConcurrentHashMap<Int, WeakReference<SvgaSoundCallback>>()
    private var volume: Float = 1f

    fun init(maxStreams: Int = 20) {
        LogUtils.debug(TAG, "init maxStreams=$maxStreams")
        if (soundPool != null) return
        soundPool = createSoundPool(maxStreams).apply {
            setOnLoadCompleteListener { _, soundId, status ->
                LogUtils.debug(TAG, "SoundPool onLoadComplete soundId=$soundId status=$status")
                soundCallbackMap[soundId]?.get()?.onComplete()
            }
        }
    }

    fun release() {
        LogUtils.debug(TAG, "release")
        soundCallbackMap.clear()
        try {
            soundPool?.setOnLoadCompleteListener(null)
            soundPool?.release()
        } catch (_: Throwable) {}
        soundPool = null
    }

    fun setVolume(volume: Float, entity: SvgaVideoEntity? = null) {
        if (!checkInit()) return
        val clamped = volume.coerceIn(0f, 1f)

        if (entity == null) {
            this.volume = clamped
            val iterator = soundCallbackMap.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val cb = entry.value.get()
                if (cb == null) {
                    iterator.remove()
                } else {
                    cb.onVolumeChange(clamped)
                }
            }
            return
        }

        val pool = soundPool ?: return
        entity.audioList.forEach { audio ->
            audio.playID?.let {
                try {
                    pool.setVolume(it, clamped, clamped)
                } catch (_: Throwable) {}
            }
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
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        return SoundPool.Builder()
            .setAudioAttributes(attributes)
            .setMaxStreams(maxStreams)
            .build()
    }

    internal fun load(
        callback: SvgaSoundCallback?,
        path: String,
        priority: Int = 1
    ): Int {
        val pool = soundPool ?: return -1
        return try {
            val soundId = pool.load(path, priority)
            if (callback != null && soundId > 0) {
                soundCallbackMap[soundId] = WeakReference(callback)
            }
            soundId
        } catch (t: Throwable) {
            LogUtils.error(TAG, "SoundPool load failed for $path", t)
            -1
        }
    }

    internal fun load(
        callback: SvgaSoundCallback?,
        fd: FileDescriptor?,
        offset: Long,
        length: Long,
        priority: Int = 1
    ): Int {
        val pool = soundPool ?: return -1
        return try {
            val soundId = pool.load(fd, offset, length, priority)
            if (callback != null && soundId > 0) {
                soundCallbackMap[soundId] = WeakReference(callback)
            }
            soundId
        } catch (t: Throwable) {
            LogUtils.error(TAG, "SoundPool load fd failed", t)
            -1
        }
    }

    internal fun unload(soundId: Int) {
        val pool = soundPool ?: return
        try {
            pool.unload(soundId)
        } catch (t: Throwable) {
            LogUtils.error(TAG, "SoundPool unload failed for soundId=$soundId", t)
        }
        soundCallbackMap.remove(soundId)
    }

    internal fun play(soundId: Int, playVolume: Float = volume): Int {
        val pool = soundPool ?: return -1
        val vol = if (playVolume.isFinite()) playVolume.coerceIn(0f, 1f) else 0f
        return try {
            pool.play(soundId, vol, vol, 1, 0, 1.0f)
        } catch (t: Throwable) {
            LogUtils.error(TAG, "SoundPool play failed for soundId=$soundId", t)
            -1
        }
    }

    internal fun stop(streamId: Int) {
        val pool = soundPool ?: return
        try {
            pool.stop(streamId)
        } catch (t: Throwable) {
            LogUtils.error(TAG, "SoundPool stop failed for streamId=$streamId", t)
        }
    }

    internal fun resume(streamId: Int) {
        val pool = soundPool ?: return
        try {
            pool.resume(streamId)
        } catch (t: Throwable) {
            LogUtils.error(TAG, "SoundPool resume failed for streamId=$streamId", t)
        }
    }

    internal fun pause(streamId: Int) {
        val pool = soundPool ?: return
        try {
            pool.pause(streamId)
        } catch (t: Throwable) {
            LogUtils.error(TAG, "SoundPool pause failed for streamId=$streamId", t)
        }
    }
}
