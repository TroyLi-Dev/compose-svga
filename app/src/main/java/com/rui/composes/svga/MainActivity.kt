package com.rui.composes.svga

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Process
import android.text.TextPaint
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.request.ImageRequest
import com.opensource.svgaplayer.SVGADynamicEntity
import com.opensource.svgaplayer.utils.log.SVGALogger
import com.rui.composes.svga.ui.theme.ComposesvgaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

data class FlyingIconData(
    val url: String,
    val anim: Animatable<Offset, androidx.compose.animation.core.AnimationVector2D>,
    val scale: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SVGALogger.setLogEnabled(true)
        setContent {
            ComposesvgaTheme {
                SvgaProvider {
                    val context = LocalContext.current
                    var isInterferenceEnabled by remember { mutableStateOf(false) }
                    val systemLoad = LocalSystemLoad.current
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            SvgaTestScreen(
                                pageTitle = "Compose 极致 60FPS 压测",
                                currentFps = systemLoad.value.currentFps,
                                onNavigateNext = {
                                    context.startActivity(
                                        Intent(
                                            context,
                                            TestActivity::class.java
                                        )
                                    )
                                },
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

@Composable
fun PerformanceDashboard(
    isInterferenceEnabled: Boolean,
    onToggleInterference: (Boolean) -> Unit,
    onFpsUpdate: (Int) -> Unit
) {
    var memoryInfo by remember { mutableStateOf("") }
    var cpuInfo by remember { mutableStateOf("CPU: 0%") }
    var fps by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        var lastCpuTime = Process.getElapsedCpuTime()
        var lastTime = System.currentTimeMillis()
        val numCores = Runtime.getRuntime().availableProcessors()
        while (true) {
            val runtime = Runtime.getRuntime()
            val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            val maxMem = runtime.maxMemory() / 1024 / 1024
            memoryInfo = "MEM: ${usedMem}MB / ${maxMem}MB"
            val currentCpuTime = Process.getElapsedCpuTime()
            val currentTime = System.currentTimeMillis()
            val timeDelta = currentTime - lastTime
            if (timeDelta > 0) {
                val cpuUsage =
                    ((currentCpuTime - lastCpuTime).toFloat() / (timeDelta * numCores) * 100).coerceIn(
                        0f,
                        100f
                    )
                cpuInfo = "CPU: ${String.format("%.1f", cpuUsage)}%"
            }
            lastCpuTime = currentCpuTime; lastTime = currentTime
            delay(1000)
        }
    }

    LaunchedEffect(Unit) {
        var frameCount = 0;
        var lastTime = System.nanoTime()
        while (true) {
            withFrameNanos {
                frameCount++
                val currentTime = System.nanoTime()
                if (currentTime - lastTime >= 1_000_000_000L) {
                    fps = frameCount
                    onFpsUpdate(fps) // 通知全局
                    frameCount = 0; lastTime = currentTime
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(8.dp)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "FPS: $fps",
                        color = if (fps > 45) Color.Green else if (fps > 20) Color.Yellow else Color.Red,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = cpuInfo,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = memoryInfo,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "干扰",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Switch(checked = isInterferenceEnabled, onCheckedChange = onToggleInterference)
            }
        }
    }
}

@Composable
fun SvgaTestScreen(
    pageTitle: String,
    currentFps: Int,
    onNavigateNext: () -> Unit,
    onNavigateNative: () -> Unit,
    isInterferenceEnabled: Boolean,
    onFpsUpdated: (Int) -> Unit
) {
    val context = LocalContext.current
    val svgaUrls = remember {
        listOf(
            "https://d2180mnhafnhva.cloudfront.net/05213178614dfb7b0bdd9d19f82c9f5d.svga",
            "https://d2180mnhafnhva.cloudfront.net/IMNR0BjLFm0GCGtK27QzT9qRZCZjaoQp.svga",
            "https://img.chatie.live/app%2Fcard%2Fani_profilecard_aristocracy_lv1.svga",
            "https://d2180mnhafnhva.cloudfront.net/QmDvo89m0jJt2ctcqJwl8EZdCP9Pu2qD.svga"
        )
    }

    val iconUrls = remember {
        listOf(
            "https://d2180mnhafnhva.cloudfront.net/7e38463dba12e58d71eb947bb7118cce.png",
            "https://d2180mnhafnhva.cloudfront.net/pnuvxRu0rF4H03ggtOXml3EO5ldekxdt.png",
            "https://d2180mnhafnhva.cloudfront.net/Zy58vmtMAfYXL5GXdxNwLHU8Gi66mstL.png"
        )
    }

    val testDynamicEntity = remember {
        SVGADynamicEntity().apply {
            setDynamicText("zhuorui li", TextPaint().apply {
                color = android.graphics.Color.RED
                textSize = 40f
                isFakeBoldText = true
            }, "banner_text")
        }
    }

    val preloadedBitmaps = remember { mutableStateMapOf<String, Bitmap>() }
    LaunchedEffect(Unit) {
        val loader = ImageLoader(context)
        iconUrls.forEach { url ->
            val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
            val result = loader.execute(request)
            (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let {
                preloadedBitmaps[url] = it
            }
        }
    }

    val flyingData = remember { mutableStateListOf<FlyingIconData>() }
    val gridItemPositions = remember { mutableStateMapOf<Int, Offset>() }
    var buttonCenter by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(85.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = pageTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Button(
                    onClick = onNavigateNative,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("测试原生 View", fontSize = 10.sp)
                }
            }
            Button(onClick = onNavigateNext) {
                Text(if (pageTitle.contains("Activity")) "关闭当前页" else "跳转 Activity")
            }
        }

        Text(
            text = "资源预览 (High Priority)",
            modifier = Modifier.padding(16.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(svgaUrls) { url ->
                SvgaAnimation(
                    model = url,
                    priority = SvgaPriority.High, // 始终 60FPS
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.LightGray.copy(0.2f))
                )
            }
        }

        Text(
            text = "300 个极限网格 (Low/Normal Priority)",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 14.sp
        )

        val items = remember { (0..300).map { svgaUrls.random() } }

        LazyVerticalGrid(
            columns = GridCells.Fixed(10),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(items) { index, randomUrl ->
                SvgaAnimation(
                    model = randomUrl,
                    priority = SvgaPriority.Low,
                    dynamicEntity = if (index % 5 == 0) testDynamicEntity else null,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .fillMaxWidth()
                        .background(Color.LightGray.copy(0.1f))
                        .onGloballyPositioned { coords ->
                            if (index < 50) {
                                val windowPos = coords.positionInWindow()
                                gridItemPositions[index] = Offset(
                                    windowPos.x + coords.size.width / 2f,
                                    windowPos.y + coords.size.height / 2f
                                )
                            }
                        },
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .onGloballyPositioned { coords ->
                    val windowPos = coords.positionInWindow()
                    buttonCenter = Offset(
                        windowPos.x + coords.size.width / 2f,
                        windowPos.y + coords.size.height / 2f
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = {
                    scope.launch {
                        for (i in 0 until 50) {
                            val targetPos = gridItemPositions[i] ?: continue
                            val url = iconUrls.random()
                            launch {
                                val anim = Animatable(buttonCenter, Offset.VectorConverter)
                                val scale = Animatable(1f)
                                val data = FlyingIconData(url, anim, scale)
                                flyingData.add(data)
                                delay(i * 15L)
                                launch {
                                    anim.animateTo(
                                        targetPos,
                                        tween(600 + Random.nextInt(400))
                                    )
                                }
                                launch { scale.animateTo(0.5f, tween(800)) }
                                delay(1200); flyingData.remove(data)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f))
            ) { Text("点击触发飞行 🚀", color = Color.White) }

            SvgaAnimation(
                model = "https://d2180mnhafnhva.cloudfront.net/05213178614dfb7b0bdd9d19f82c9f5d.svga",
                priority = SvgaPriority.Normal,
                dynamicEntity = testDynamicEntity,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentScale = ContentScale.FillHeight
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        flyingData.forEach { data ->
            preloadedBitmaps[data.url]?.let { bitmap ->
                withTransform({
                    translate(
                        data.anim.value.x,
                        data.anim.value.y
                    ); scale(data.scale.value, data.scale.value, pivot = Offset.Zero)
                }) {
                    drawImage(
                        image = bitmap.asImageBitmap(),
                        topLeft = Offset(-bitmap.width / 2f, -bitmap.height / 2f)
                    )
                }
            }
        }
    }
}
