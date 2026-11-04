package com.rui.compose.svga.utils

class SvgaPoint(val x: Float, val y: Float, val value: Float = 0.0f)

class SvgaRect(val x: Double, val y: Double, val width: Double, val height: Double) {
    companion object {
        val EMPTY = SvgaRect(0.0, 0.0, 0.0, 0.0)
    }
}

class SvgaRange(val location: Int, val length: Int)
