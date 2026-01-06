package com.rui.composes.svga.model

import androidx.compose.runtime.Stable

enum class SvgaPriority { High, Normal, Low }

enum class SvgaLoadState { Loading, Success, Error }

@Stable
data class SystemLoad(val currentFps: Int = 60)
