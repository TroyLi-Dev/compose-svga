package com.rui.compose.svga.ext

import kotlinx.coroutines.CancellableContinuation
import kotlin.coroutines.resumeWithException

/**
 * 安全恢复协程结果（防止由于并发或取消重复调用 resume 导致的 IllegalStateException）
 */
internal fun <T> CancellableContinuation<T>.resumeSafe(value: T) {
    if (!isCompleted && !isCancelled && isActive) {
        resumeWith(Result.success(value))
    }
}

/**
 * 安全恢复协程异常（防止由于并发或取消重复调用 resume 导致的 IllegalStateException）
 */
internal fun <T> CancellableContinuation<T>.resumeWithExceptionSafe(exception: Throwable) {
    if (!isCompleted && !isCancelled && isActive) {
        resumeWithException(exception)
    }
}
