package com.rui.composes.svga

import kotlinx.coroutines.CancellableContinuation
import kotlin.coroutines.resumeWithException

internal fun <T> CancellableContinuation<T>.resumeSafe(value: T) {
    if (!isCompleted && !isCancelled && isActive) {
        resumeWith(Result.success(value))
    }
}

internal fun <T> CancellableContinuation<T>.resumeWithExceptionSafe(exception: Throwable) {
    if (!isCompleted && !isCancelled && isActive) {
        resumeWithException(exception)
    }
}