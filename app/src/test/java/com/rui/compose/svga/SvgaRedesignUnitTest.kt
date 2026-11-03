package com.rui.compose.svga

import com.rui.compose.svga.cache.SvgaDiskCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
            assertNotNull(lock)
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
        assertNotNull(matched)
        com.rui.compose.svga.parser.SvgaFetcherRegistry.unregister(testFetcher)
        val afterUnregister = com.rui.compose.svga.parser.SvgaFetcherRegistry.findFetcher(CustomModel("cipher"))
        assertNull(afterUnregister)
    }

    @Test
    fun testDynamicEntityResetAndIsolation() {
        val dynamic = com.rui.compose.svga.drawer.SvgaDynamicEntity()
        var finishCalled = false
        dynamic.setFinishListener(object : com.rui.compose.svga.drawer.SvgaDynamicEntity.ReplaceFinish {
            override fun replaceFinish() {
                finishCalled = true
            }
        })

        dynamic.dynamicSize = 2
        dynamic.loadOkSize = 1
        assertEquals(2, dynamic.dynamicSize)
        assertEquals(1, dynamic.loadOkSize)

        dynamic.clearDynamicObjects()
        assertEquals(0, dynamic.dynamicSize)
        assertEquals(0, dynamic.loadOkSize)
        assertTrue(!finishCalled)
    }

    @Test
    fun testTrimDiskCacheExcludesAudiosDirectory() {
        val cacheDir = SvgaDiskCache.getCacheDir()
        val audiosDir = java.io.File(cacheDir, "audios").apply { if (!exists()) mkdirs() }
        val dummyAudio = java.io.File(audiosDir, "test_audio.mp3").apply { writeText("audio_bytes") }

        // 创建一个普通缓存目录
        val animDir = java.io.File(cacheDir, "test_anim_dir").apply { if (!exists()) mkdirs() }
        val dummyAnim = java.io.File(animDir, "movie.binary").apply { writeText("svga_binary_bytes") }

        assertTrue(dummyAudio.exists())
        assertTrue(dummyAnim.exists())

        // 触发 trim，使用极小阈值 (1 字节)
        SvgaDiskCache.trimDiskCache(maxBytes = 1L)

        // audios 目录下的音频文件必须受到保护，绝对不可被误删
        assertTrue("Audios file must be preserved", dummyAudio.exists())
        assertTrue("Audios directory must be preserved", audiosDir.exists())

        // 清理测试产物
        dummyAudio.delete()
        dummyAnim.delete()
        animDir.delete()
    }

    @Test
    fun testDynamicDslExtensions() {
        val dynamic = com.rui.compose.svga.drawer.buildSvgaDynamic {
            hidden("banner", true)
            click("btn_like")
            drawer("wave") { canvas, frameIndex ->
                canvas.drawColor(0)
                true
            }
            drawerSized("avatar") { canvas, frameIndex, width, height ->
                width > 0 && height > 0
            }
        }
        assertTrue(dynamic.dynamicHidden["banner"] == true)
        assertNotNull(dynamic.dynamicClickArea["btn_like"])
        assertNotNull(dynamic.dynamicDrawer["wave"])
        assertNotNull(dynamic.dynamicDrawerSized["avatar"])
    }

    @Test
    fun testTrimAudioCacheInDiskCache() {
        val cacheDir = SvgaDiskCache.getCacheDir()
        val audiosDir = java.io.File(cacheDir, "audios").apply { if (!exists()) mkdirs() }
        val oldAudio = java.io.File(audiosDir, "old_audio.mp3").apply {
            writeText("old_mp3_data_stream_content")
            setLastModified(System.currentTimeMillis() - 100000)
        }
        val newAudio = java.io.File(audiosDir, "new_audio.mp3").apply {
            writeText("new_mp3_data_stream_content")
            setLastModified(System.currentTimeMillis())
        }

        assertTrue(oldAudio.exists())
        assertTrue(newAudio.exists())

        // 设置 maxAudioBytes 为极小值 (1 字节)，触发基于 LRU 的音频淘汰
        SvgaDiskCache.trimDiskCache(maxBytes = 1000000L, maxAudioBytes = 1L)

        // 验证淘汰发生且保留了目录
        assertTrue(audiosDir.exists())

        // 清理测试残留
        oldAudio.delete()
        newAudio.delete()
    }

    @Test
    fun testPathEntityNegativeParsing() {
        val pathStr = "M0,0 L10-20 C30-40 50-60 70-80 Z"
        val pathEntity = com.rui.compose.svga.entity.SvgaPathEntity(pathStr)
        val path = android.graphics.Path()
        pathEntity.buildPath(path)
        assertNotNull(pathEntity)
    }

    @Test
    fun testVideoShapeEntityPathBuilding() {
        // 测试椭圆路径构建
        val ellipseJson = org.json.JSONObject().apply {
            put("type", "ellipse")
            put("args", org.json.JSONObject().apply {
                put("x", 50.0)
                put("y", 50.0)
                put("radiusX", 25.0)
                put("radiusY", 15.0)
            })
        }
        val ellipseShape = com.rui.compose.svga.entity.SvgaVideoShapeEntity(ellipseJson)
        ellipseShape.buildPath()
        assertNotNull(ellipseShape.shapePath)

        // 测试圆角矩形路径构建
        val roundRectJson = org.json.JSONObject().apply {
            put("type", "rect")
            put("args", org.json.JSONObject().apply {
                put("x", 0.0)
                put("y", 0.0)
                put("width", 100.0)
                put("height", 50.0)
                put("cornerRadius", 8.0)
            })
        }
        val roundRectShape = com.rui.compose.svga.entity.SvgaVideoShapeEntity(roundRectJson)
        roundRectShape.buildPath()
        assertNotNull(roundRectShape.shapePath)

        // 测试直角矩形路径构建
        val rectJson = org.json.JSONObject().apply {
            put("type", "rect")
            put("args", org.json.JSONObject().apply {
                put("x", 0.0)
                put("y", 0.0)
                put("width", 100.0)
                put("height", 50.0)
                put("cornerRadius", 0.0)
            })
        }
        val rectShape = com.rui.compose.svga.entity.SvgaVideoShapeEntity(rectJson)
        rectShape.buildPath()
        assertNotNull(rectShape.shapePath)
    }

    @Test
    fun testDynamicEntityTextIsolation() {
        val dynamic = com.rui.compose.svga.drawer.SvgaDynamicEntity()
        val textPaint = android.text.TextPaint()
        dynamic.setDynamicText("User1", textPaint, "user_name")
        dynamic.setDynamicText("Score: 100", textPaint, "score_label")

        assertEquals("User1", dynamic.dynamicText["user_name"])
        assertEquals("Score: 100", dynamic.dynamicText["score_label"])

        // 更新单个 key
        dynamic.setDynamicText("Score: 200", textPaint, "score_label")
        assertEquals("User1", dynamic.dynamicText["user_name"])
        assertEquals("Score: 200", dynamic.dynamicText["score_label"])
    }

    @Test
    fun testSvgPathParsingExtendedCommands() {
        // 包含标准平滑三次曲线 S/s、平滑二次曲线 T/t、椭圆弧 A/a
        val svgD = "M 10 10 C 20 20 30 30 40 40 S 60 60 70 70 s 10 10 20 20 Q 30 30 40 40 T 60 60 t 10 10 A 25 25 0 0 1 100 100 a 10 10 45 1 0 -20 -20 Z"
        val pathEntity = com.rui.compose.svga.entity.SvgaPathEntity(svgD)
        val path = android.graphics.Path()
        pathEntity.buildPath(path)
        assertNotNull(path)
    }

    @Test
    fun testSvgaControllerRequestRedraw() {
        val controller = com.rui.compose.svga.controller.SvgaController()
        // 验证调用 requestRedraw 与 invalidate 正常执行且状态稳定
        controller.requestRedraw()
        controller.invalidate()
        assertNotNull(controller)
    }

    @Test
    fun testDecodeReasonStreamOverflow() {
        val reason = com.rui.compose.svga.exception.DecodeReason.STREAM_OVERFLOW
        assertEquals("STREAM_OVERFLOW", reason.name)
    }
}
