package com.rui.compose.svga.audio

interface SvgaSoundCallback {
    fun onVolumeChange(value: Float)
    fun onComplete()
}
