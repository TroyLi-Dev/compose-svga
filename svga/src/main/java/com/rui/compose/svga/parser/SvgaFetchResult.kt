package com.rui.compose.svga.parser

import java.io.InputStream

sealed class SvgaFetchResult {
    class Stream(val inputStream: InputStream, val cacheKey: String) : SvgaFetchResult()
    class FileResult(val file: java.io.File, val cacheKey: String) : SvgaFetchResult()
}
