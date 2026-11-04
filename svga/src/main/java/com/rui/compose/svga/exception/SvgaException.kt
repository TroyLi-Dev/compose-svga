package com.rui.compose.svga.exception

/**
 * SVGA 统一强类型异常体系
 */
sealed class SvgaException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    /**
     * 资源获取异常（网络错误、HTTP 状态码异常、超时等）
     */
    class FetchException(
        val source: String,
        val httpCode: Int? = null,
        message: String,
        cause: Throwable? = null
    ) : SvgaException("[$source] Fetch failed${if (httpCode != null) " (HTTP $httpCode)" else ""}: $message", cause)

    /**
     * 数据解码与解析异常（文件格式错误、Zip 损坏、元数据缺失等）
     */
    class DecodeException(
        val source: String,
        val reason: DecodeReason,
        message: String,
        cause: Throwable? = null
    ) : SvgaException("[$source] Decode failed ($reason): $message", cause)

    /**
     * 音频加载与配置异常
     */
    class AudioException(
        val audioKey: String,
        message: String,
        cause: Throwable? = null
    ) : SvgaException("Audio [$audioKey] failed: $message", cause)
}

enum class DecodeReason {
    EMPTY_STREAM,
    STREAM_OVERFLOW,
    CORRUPTED_ZIP,
    ZIP_SLIP_SECURITY,
    MISSING_SPEC,
    INVALID_PROTOBUF,
    UNKNOWN
}
