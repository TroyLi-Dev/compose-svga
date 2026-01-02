package com.opensource.svgaplayer

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import com.opensource.svgaplayer.utils.log.LogUtils
import java.io.FileDescriptor

/**
 * Author : llk
 * Time : 2020/10/24
 * Description : svga 音频加载管理类
 */
object SVGASoundManager {

    private val TAG = SVGASoundManager::class.java.simpleName

    private var soundPool: SoundPool? = null

    private val soundCallBackMap: MutableMap<Int, SVGASoundCallBack> = mutableMapOf()

    /**
     * 音量设置，范围在 [0, 1] 之间
     */
    private var volume: Float = 1f

    /**
     * 音频回调
     */
    internal interface SVGASoundCallBack {
        fun onVolumeChange(value: Float)
        fun onComplete()
    }

    fun init() {
        init(20)
    }

    fun init(maxStreams: Int) {
        LogUtils.debug(TAG, "**************** init **************** $maxStreams")
        if (soundPool != null) return
        soundPool = getSoundPool(maxStreams)
        soundPool?.setOnLoadCompleteListener { _, soundId, status ->
            LogUtils.debug(TAG, "SoundPool onLoadComplete soundId=$soundId status=$status")
            if (status == 0) {
                soundCallBackMap[soundId]?.onComplete()
            }
        }
    }

    fun release() {
        LogUtils.debug(TAG, "**************** release ****************")
        if (soundCallBackMap.isNotEmpty()) {
            soundCallBackMap.clear()
        }
    }

    fun setVolume(volume: Float, entity: SVGAVideoEntity? = null) {
        if (!checkInit()) return

        if (volume < 0f || volume > 1f) {
            LogUtils.error(TAG, "The volume level is in the range of 0 to 1 ")
            return
        }

        if (entity == null) {
            this.volume = volume
            soundCallBackMap.values.forEach { it.onVolumeChange(volume) }
            return
        }

        val pool = soundPool ?: return
        entity.audioList.forEach { audio ->
            audio.playID?.let { pool.setVolume(it, volume, volume) }
        }
    }

    internal fun isInit(): Boolean = soundPool != null

    private fun checkInit(): Boolean {
        if (!isInit()) {
            LogUtils.error(TAG, "soundPool is null, you need call init() !!!")
            return false
        }
        return true
    }

    private fun getSoundPool(maxStreams: Int) = if (Build.VERSION.SDK_INT >= 21) {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        SoundPool.Builder().setAudioAttributes(attributes)
            .setMaxStreams(maxStreams)
            .build()
    } else {
        @Suppress("DEPRECATION")
        SoundPool(maxStreams, AudioManager.STREAM_MUSIC, 0)
    }

    internal fun load(
        callBack: SVGASoundCallBack?,
        fd: FileDescriptor?,
        offset: Long,
        length: Long,
        priority: Int
    ): Int {
        if (!checkInit()) return -1
        val soundId = soundPool!!.load(fd, offset, length, priority)
        if (callBack != null) soundCallBackMap[soundId] = callBack
        return soundId
    }

    internal fun unload(soundId: Int) {
        if (!checkInit()) return
        soundPool!!.unload(soundId)
        soundCallBackMap.remove(soundId)
    }

    internal fun play(soundId: Int): Int {
        if (!checkInit()) return -1
        return soundPool!!.play(soundId, volume, volume, 1, 0, 1.0f)
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
