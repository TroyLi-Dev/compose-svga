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
import androidx.compose.runtime.*
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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        SvgaTestScreen(
                            pageTitle = "独立 Activity 页面",
                            onNavigateNext = { finish() }, // 退出当前 Activity 返回上一页
                            onNavigateNative = {
                                // 补全参数：跳转到原生 View 系统测试页
                                context.startActivity(Intent(context, NativeSvgaTestActivity::class.java))
                            },
                            isInterferenceEnabled = isInterferenceEnabled
                        )
                        PerformanceDashboard(
                            isInterferenceEnabled = isInterferenceEnabled,
                            onToggleInterference = { isInterferenceEnabled = it }
                        )
                    }
                }
            }
        }
    }
}
