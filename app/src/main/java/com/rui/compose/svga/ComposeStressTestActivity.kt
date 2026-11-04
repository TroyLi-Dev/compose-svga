package com.rui.compose.svga

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
import com.rui.compose.svga.ui.theme.ComposeSvgaTheme

/**
 * 独立的 Compose 页面压测 Activity
 */
class ComposeStressTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposeSvgaTheme {
                val context = LocalContext.current
                var isInterferenceEnabled by remember { mutableStateOf(false) }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        SvgaTestScreen(
                            pageTitle = "独立 Activity 压测页面",
                            onNavigateNext = { finish() },
                            onNavigateNative = {
                                context.startActivity(
                                    Intent(
                                        context,
                                        ViewStressTestActivity::class.java
                                    )
                                )
                            },
                        )
                        PerformanceDashboard(
                            isInterferenceEnabled = isInterferenceEnabled,
                            onToggleInterference = { isInterferenceEnabled = it },
                        )
                    }
                }
            }
        }
    }
}

/** 兼容旧类名的别名 */
typealias TestActivity = ComposeStressTestActivity
