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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.rui.composes.svga.ui.theme.ComposesvgaTheme

class TestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposesvgaTheme {
                val context = LocalContext.current
                var isInterferenceEnabled by remember { mutableStateOf(false) }
                val systemLoad = LocalSystemLoad.current

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
