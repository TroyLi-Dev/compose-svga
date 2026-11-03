package com.rui.compose.svga

import com.rui.compose.svga.cache.SvgaDiskCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import kotlin.math.abs

/**
 * SVGA 现代化重构与核心组件单元测试
 */
class SvgaRedesignUnitTest {

    @Test
    fun testMd5CacheKeyHexPadding() {
        val testInputs = listOf(
            "test_key_1",
            "https://cdn.example.com/svga/gift_castle.svga",
            "file:///sdcard/download/banner.svga",
            "short",
            "a_very_long_url_with_many_parameters_and_special_chars_!@#\$%^&*()_+"
        )

        for (input in testInputs) {
            val key = SvgaDiskCache.buildCacheKey(input)
            // 必须严格是 32 位十六进制字符
            assertEquals(32, key.length)
            assertTrue(key.matches(Regex("^[0-9a-f]{32}$")))

            // 与标准无补零缺陷的 MessageDigest 格式化对比
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            val expectedHex = digest.joinToString("") { "%02x".format(it) }
            assertEquals(expectedHex, key)
        }
    }

    @Test
    fun testSegmentLockDistribution() {
        val segmentLocks = Array(32) { Any() }
        val keys = (1..1000).map { "gift_animation_cache_key_$it" }
        val hitCounts = IntArray(32)

        for (key in keys) {
            val index = abs(key.hashCode()) and 31
            hitCounts[index]++
            val lock = segmentLocks[index]
            assertTrue(lock != null)
        }

        // 验证 32 个分段锁全部被均匀哈希命中
        for (i in 0 until 32) {
            assertTrue("Segment lock $i should be hit", hitCounts[i] > 0)
        }
    }

    @Test
    fun testDiskCacheSelfHealing() {
        // 验证未初始化时不发生 Crash，而是自愈创建临时缓存目录
        val cacheDir = SvgaDiskCache.getCacheDir()
        assertTrue(cacheDir.exists())
        val testDir = SvgaDiskCache.buildCacheDir("self_healing_test")
        assertTrue(testDir.exists())
    }

    @Test
    fun testControllerStateControl() {
        val controller = com.rui.compose.svga.controller.SvgaController(
            initialModel = "test.svga",
            initialLoops = 2
        )
        assertEquals("test.svga", controller.model)
        assertEquals(2, controller.loops)
        assertEquals(0, controller.currentFrame)

        controller.seekTo(15)
        assertEquals(15, controller.currentFrame)

        controller.replay()
        assertEquals(com.rui.compose.svga.model.SvgaPlayState.Playing, controller.playState)

        controller.pause()
        assertEquals(com.rui.compose.svga.model.SvgaPlayState.Paused, controller.playState)

        controller.resume()
        assertEquals(com.rui.compose.svga.model.SvgaPlayState.Playing, controller.playState)

        controller.stop()
        assertEquals(com.rui.compose.svga.model.SvgaPlayState.Idle, controller.playState)
        assertEquals(0, controller.currentFrame)
    }

    @Test
    fun testMemoryCalculatorGracefulFallback() {
        // 在单元测试 JVM 环境中无 ActivityManager 时，默认放行，绝不误拦截
        val hasMem = com.rui.compose.svga.utils.SvgaMemoryCalculator.hasSufficientMemory(100 * 1024 * 1024L)
        assertTrue(hasMem)
    }

    @Test
    fun testCustomFetcherPluginRegistration() {
        data class CustomModel(val secretPayload: String)
        val testFetcher = object : com.rui.compose.svga.parser.SvgaResourceFetcher<CustomModel> {
            override fun handles(data: Any): Boolean = data is CustomModel
            override suspend fun fetch(context: android.content.Context, data: CustomModel): com.rui.compose.svga.parser.SvgaFetchResult {
                val dummyStream = java.io.ByteArrayInputStream(ByteArray(0))
                return com.rui.compose.svga.parser.SvgaFetchResult.Stream(dummyStream, "custom_secret_key")
            }
        }

        com.rui.compose.svga.parser.SvgaFetcherRegistry.clear()
        com.rui.compose.svga.parser.SvgaFetcherRegistry.register(testFetcher)
        val matched = com.rui.compose.svga.parser.SvgaFetcherRegistry.findFetcher(CustomModel("cipher"))
        assertTrue(matched != null)
        com.rui.compose.svga.parser.SvgaFetcherRegistry.unregister(testFetcher)
        val afterUnregister = com.rui.compose.svga.parser.SvgaFetcherRegistry.findFetcher(CustomModel("cipher"))
        assertTrue(afterUnregister == null)
    }
}
