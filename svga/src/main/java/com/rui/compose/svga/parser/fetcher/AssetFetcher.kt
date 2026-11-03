package com.rui.compose.svga.parser.fetcher

import android.content.Context
import com.rui.compose.svga.cache.SvgaDiskCache
import com.rui.compose.svga.exception.DecodeReason
import com.rui.compose.svga.exception.SvgaException
import com.rui.compose.svga.parser.SvgaFetchResult
import com.rui.compose.svga.parser.SvgaResourceFetcher

class AssetFetcher : SvgaResourceFetcher<String> {

    override fun handles(data: Any): Boolean {
        if (data !is String) return false
        val lower = data.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return false
        if (lower.startsWith("file://") || data.startsWith("/")) return false
        return true
    }

    override suspend fun fetch(context: Context, data: String): SvgaFetchResult {
        val cleanPath = data.removePrefix("assets://").removePrefix("asset://").removePrefix("/")
        return try {
            val inputStream = context.assets.open(cleanPath)
            val cacheKey = SvgaDiskCache.buildCacheKey("assets://$cleanPath")
            SvgaFetchResult.Stream(inputStream, cacheKey)
        } catch (e: Exception) {
            throw SvgaException.FetchException(data, null, "Failed to open asset: $cleanPath", e)
        }
    }
}
