package com.rui.compose.svga.parser.fetcher

import android.content.Context
import com.rui.compose.svga.cache.SvgaDiskCache
import com.rui.compose.svga.exception.SvgaException
import com.rui.compose.svga.parser.SvgaFetchResult
import com.rui.compose.svga.parser.SvgaResourceFetcher

class RawResFetcher : SvgaResourceFetcher<Int> {

    override fun handles(data: Any): Boolean {
        return data is Int && data > 0
    }

    override suspend fun fetch(context: Context, data: Int): SvgaFetchResult {
        return try {
            val inputStream = context.resources.openRawResource(data)
            val cacheKey = SvgaDiskCache.buildCacheKey("rawres://$data")
            SvgaFetchResult.Stream(inputStream, cacheKey)
        } catch (e: Exception) {
            throw SvgaException.FetchException("rawres://$data", null, "Failed to open raw resource: $data", e)
        }
    }
}
