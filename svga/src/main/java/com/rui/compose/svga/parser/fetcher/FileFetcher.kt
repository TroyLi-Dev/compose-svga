package com.rui.compose.svga.parser.fetcher

import android.content.Context
import com.rui.compose.svga.cache.SvgaDiskCache
import com.rui.compose.svga.exception.SvgaException
import com.rui.compose.svga.parser.SvgaFetchResult
import com.rui.compose.svga.parser.SvgaResourceFetcher
import java.io.File
import java.io.FileInputStream

class FileFetcher : SvgaResourceFetcher<Any> {

    override fun handles(data: Any): Boolean {
        if (data is File) return true
        if (data is String) {
            val lower = data.lowercase()
            return lower.startsWith("file://") || data.startsWith("/")
        }
        return false
    }

    override suspend fun fetch(context: Context, data: Any): SvgaFetchResult {
        val file: File = when (data) {
            is File -> data
            is String -> {
                val path = if (data.lowercase().startsWith("file://")) data.substring(7) else data
                File(path)
            }
            else -> throw SvgaException.FetchException(data.toString(), null, "Unsupported file data type")
        }

        if (!file.exists() || !file.canRead()) {
            throw SvgaException.FetchException(data.toString(), null, "File not found or unreadable: ${file.absolutePath}")
        }

        val cacheKey = SvgaDiskCache.buildCacheKey("file://${file.absolutePath}")
        return SvgaFetchResult.FileResult(file, cacheKey)
    }
}
