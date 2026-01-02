package com.rui.composes.svga

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.opensource.svgaplayer.compose.LocalSvgaClock
import com.opensource.svgaplayer.compose.LocalSystemLoad
import com.opensource.svgaplayer.compose.SystemLoad
import com.rui.composes.svga.ui.theme.ComposesvgaTheme

class TestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposesvgaTheme {
                // 1. 全局 SVGA 时钟信号 (修复 No clock 报错)
                val svgaTick = remember { mutableLongStateOf(System.nanoTime()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        withFrameNanos { svgaTick.longValue = it }
                    }
                }

                // 2. 全局负载监控
                val systemLoad = remember { mutableStateOf(SystemLoad()) }

                CompositionLocalProvider(
                    LocalSvgaClock provides svgaTick,
                    LocalSystemLoad provides systemLoad
                ) {
                    val context = LocalContext.current
                    var isInterferenceEnabled by remember { mutableStateOf(false) }
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            SvgaTestScreen(
                                pageTitle = "独立 Activity 页面",
                                currentFps = systemLoad.value.currentFps,
                                onNavigateNext = { finish() },
                                onNavigateNative = {
                                    context.startActivity(
                                        Intent(
                                            context,
                                            NativeSvgaTestActivity::class.java
                                        )
                                    )
                                },
                                isInterferenceEnabled = isInterferenceEnabled,
                                onFpsUpdated = {
                                    systemLoad.value = systemLoad.value.copy(currentFps = it)
                                }
                            )
                            PerformanceDashboard(
                                isInterferenceEnabled = isInterferenceEnabled,
                                onToggleInterference = { isInterferenceEnabled = it },
                                onFpsUpdate = {
                                    systemLoad.value = systemLoad.value.copy(currentFps = it)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
