package com.opensource.svgaplayer

/**
 * 转换 StackOverFlowError 为 Exception
 * created by xxxxxxx
 * at 20220525 10:08
 */
class StackOverFlowException(e: Throwable) : RuntimeException(e) {
}