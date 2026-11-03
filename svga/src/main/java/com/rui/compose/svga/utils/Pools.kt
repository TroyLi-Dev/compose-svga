package com.rui.compose.svga.utils

class Pools private constructor() {

    interface Pool<T> {
        fun acquire(): T?
        fun release(instance: T): Boolean
    }

    open class SimplePool<T>(maxPoolSize: Int) : Pool<T> {
        private val pool: Array<Any?>
        private var poolSize = 0

        init {
            require(maxPoolSize > 0) { "The max pool size must be > 0" }
            pool = arrayOfNulls(maxPoolSize)
        }

        @Suppress("UNCHECKED_CAST")
        override fun acquire(): T? {
            if (poolSize > 0) {
                val lastPooledIndex = poolSize - 1
                val instance = pool[lastPooledIndex] as T
                pool[lastPooledIndex] = null
                poolSize--
                return instance
            }
            return null
        }

        override fun release(instance: T): Boolean {
            check(!isInPool(instance)) { "Already in the pool!" }
            if (poolSize < pool.size) {
                pool[poolSize] = instance
                poolSize++
                return true
            }
            return false
        }

        private fun isInPool(instance: T): Boolean {
            for (i in 0 until poolSize) {
                if (pool[i] === instance) {
                    return true
                }
            }
            return false
        }
    }

    class SynchronizedPool<T>(maxPoolSize: Int, private val lock: Any = Any()) :
        SimplePool<T>(maxPoolSize) {

        override fun acquire(): T? {
            synchronized(lock) {
                return super.acquire()
            }
        }

        override fun release(instance: T): Boolean {
            synchronized(lock) {
                return super.release(instance)
            }
        }
    }
}
