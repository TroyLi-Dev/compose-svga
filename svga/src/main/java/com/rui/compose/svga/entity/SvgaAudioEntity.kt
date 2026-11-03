package com.rui.compose.svga.entity

import com.rui.compose.svga.proto.AudioEntity

class SvgaAudioEntity(audioItem: AudioEntity) {
    val audioKey: String? = audioItem.audioKey
    val startFrame: Int = audioItem.startFrame
    val endFrame: Int = audioItem.endFrame
    val startTime: Int = audioItem.startTime
    val totalTime: Int = audioItem.totalTime
    var soundID: Int? = null
    var playID: Int? = null
}
