package com.rui.compose.svga.parser.fetcher

import android.content.Context
import com.rui.compose.svga.cache.SvgaDiskCache
import com.rui.compose.svga.exception.SvgaException
import com.rui.compose.svga.parser.SvgaFetchResult
import com.rui.compose.svga.parser.SvgaResourceFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class HttpUrlFetcher : SvgaResourceFetcher<Any> {

    override fun handles(data: Any): Boolean {
        if (data is URL) return true
        if (data is String) {
            val lower = data.lowercase()
            return lower.startsWith("http://") || lower.startsWith("https://")
        }
        return false
    }

    override suspend fun fetch(context: Context, data: Any): SvgaFetchResult = withContext(Dispatchers.IO) {
        val url: URL = when (data) {
            is URL -> data
            is String -> try {
                URL(data)
            } catch (e: Exception) {
                throw SvgaException.FetchException(data, null, "Malformed URL: $data", e)
            }
            else -> throw SvgaException.FetchException(data.toString(), null, "Invalid URL input")
        }

        val cacheKey = SvgaDiskCache.buildCacheKey(url)
        try {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 SVGA/Compose")
            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                conn.disconnect()
                throw SvgaException.FetchException(url.toString(), responseCode, "HTTP error response $responseCode")
            }

            val inputStream: InputStream = conn.inputStream
            SvgaFetchResult.Stream(inputStream, cacheKey)
        } catch (e: SvgaException) {
            throw e
        } catch (e: Exception) {
            throw SvgaException.FetchException(url.toString(), null, "Network connection error: ${e.message}", e)
        }
    }
}
