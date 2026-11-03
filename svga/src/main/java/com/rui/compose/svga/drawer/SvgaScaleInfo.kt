package com.rui.compose.svga.drawer

import android.widget.ImageView

class SvgaScaleInfo {

    var tranFx: Float = 0.0f
    var tranFy: Float = 0.0f
    var scaleFx: Float = 1.0f
    var scaleFy: Float = 1.0f
    var ratio: Float = 1.0f
    var ratioX: Boolean = false

    private fun resetVar() {
        tranFx = 0.0f
        tranFy = 0.0f
        scaleFx = 1.0f
        scaleFy = 1.0f
        ratio = 1.0f
        ratioX = false
    }

    fun performScaleType(
        canvasWidth: Float,
        canvasHeight: Float,
        videoWidth: Float,
        videoHeight: Float,
        scaleType: ImageView.ScaleType
    ) {
        if (canvasWidth == 0.0f || canvasHeight == 0.0f || videoWidth == 0.0f || videoHeight == 0.0f) {
            return
        }

        resetVar()
        val canWVidWF = (canvasWidth - videoWidth) / 2.0f
        val canHVidHF = (canvasHeight - videoHeight) / 2.0f

        val videoRatio = videoWidth / videoHeight
        val canvasRatio = canvasWidth / canvasHeight

        val canHDVidH = canvasHeight / videoHeight
        val canWDVidW = canvasWidth / videoWidth

        when (scaleType) {
            ImageView.ScaleType.CENTER -> {
                tranFx = canWVidWF
                tranFy = canHVidHF
            }

            ImageView.ScaleType.CENTER_CROP -> {
                if (videoRatio > canvasRatio) {
                    ratio = canHDVidH
                    ratioX = false
                    scaleFx = canHDVidH
                    scaleFy = canHDVidH
                    tranFx = (canvasWidth - videoWidth * canHDVidH) / 2.0f
                } else {
                    ratio = canWDVidW
                    ratioX = true
                    scaleFx = canWDVidW
                    scaleFy = canWDVidW
                    tranFy = (canvasHeight - videoHeight * canWDVidW) / 2.0f
                }
            }

            ImageView.ScaleType.CENTER_INSIDE -> {
                if (videoWidth < canvasWidth && videoHeight < canvasHeight) {
                    tranFx = canWVidWF
                    tranFy = canHVidHF
                } else {
                    if (videoRatio > canvasRatio) {
                        ratio = canWDVidW
                        ratioX = true
                        scaleFx = canWDVidW
                        scaleFy = canWDVidW
                        tranFy = (canvasHeight - videoHeight * canWDVidW) / 2.0f
                    } else {
                        ratio = canHDVidH
                        ratioX = false
                        scaleFx = canHDVidH
                        scaleFy = canHDVidH
                        tranFx = (canvasWidth - videoWidth * canHDVidH) / 2.0f
                    }
                }
            }

            ImageView.ScaleType.FIT_CENTER -> {
                if (videoRatio > canvasRatio) {
                    ratio = canWDVidW
                    ratioX = true
                    scaleFx = canWDVidW
                    scaleFy = canWDVidW
                    tranFy = (canvasHeight - videoHeight * canWDVidW) / 2.0f
                } else {
                    ratio = canHDVidH
                    ratioX = false
                    scaleFx = canHDVidH
                    scaleFy = canHDVidH
                    tranFx = (canvasWidth - videoWidth * canHDVidH) / 2.0f
                }
            }

            ImageView.ScaleType.FIT_START -> {
                if (videoRatio > canvasRatio) {
                    ratio = canWDVidW
                    ratioX = true
                    scaleFx = canWDVidW
                    scaleFy = canWDVidW
                } else {
                    ratio = canHDVidH
                    ratioX = false
                    scaleFx = canHDVidH
                    scaleFy = canHDVidH
                }
            }

            ImageView.ScaleType.FIT_END -> {
                if (videoRatio > canvasRatio) {
                    ratio = canWDVidW
                    ratioX = true
                    scaleFx = canWDVidW
                    scaleFy = canWDVidW
                    tranFy = canvasHeight - videoHeight * canWDVidW
                } else {
                    ratio = canHDVidH
                    ratioX = false
                    scaleFx = canHDVidH
                    scaleFy = canHDVidH
                    tranFx = canvasWidth - videoWidth * canHDVidH
                }
            }

            ImageView.ScaleType.FIT_XY -> {
                ratio = kotlin.math.max(canWDVidW, canHDVidH)
                ratioX = canWDVidW > canHDVidH
                scaleFx = canWDVidW
                scaleFy = canHDVidH
            }

            else -> {
                ratio = canWDVidW
                ratioX = true
                scaleFx = canWDVidW
                scaleFy = canWDVidW
            }
        }
    }
}
