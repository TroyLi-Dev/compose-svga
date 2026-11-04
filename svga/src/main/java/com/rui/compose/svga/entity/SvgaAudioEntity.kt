package com.rui.compose.svga.entity

import com.rui.compose.svga.proto.AudioEntity

class SvgaAudioEntity(audioItem: AudioEntity) {
    val audioKey: String? = audioItem.audioKey
    val startFrame: Int = audioItem.startFrame.coerceAtLeast(0)
    val endFrame: Int = audioItem.endFrame.coerceAtLeast(startFrame)
    val startTime: Int = audioItem.startTime.coerceAtLeast(0)
    val totalTime: Int = audioItem.totalTime.coerceAtLeast(0)
    var soundID: Int? = null
    var playID: Int? = null
}
