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
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
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
import com.rui.composes.svga.core.LocalSystemLoad
import com.rui.composes.svga.model.SvgaPriority
import com.rui.composes.svga.ui.theme.ComposesvgaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

data class FlyingIconData(
    val url: String,
    val anim: Animatable<Offset, AnimationVector2D>,
    val scale: Animatable<Float, AnimationVector1D>
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SVGALogger.setLogEnabled(true)
        setContent {
            ComposesvgaTheme {
                val context = LocalContext.current
                var isInterferenceEnabled by remember { mutableStateOf(false) }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        SvgaTestScreen(
                            pageTitle = "Compose 极致 60FPS 压测",
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
                            }
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

@Composable
fun PerformanceDashboard(
    isInterferenceEnabled: Boolean,
    onToggleInterference: (Boolean) -> Unit,
) {
    var memoryInfo by remember { mutableStateOf("") }
    var cpuInfo by remember { mutableStateOf("CPU: 0%") }
    val systemLoad = LocalSystemLoad.current
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
                        text = "FPS: ${systemLoad.value.currentFps}",
                        color = if (systemLoad.value.currentFps > 45) Color.Green else if (systemLoad.value.currentFps > 20) Color.Yellow else Color.Red,
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
    onNavigateNext: () -> Unit,
    onNavigateNative: () -> Unit,
) {
    val context = LocalContext.current
    val svgaUrls = remember {
        AppConstants.svgaUrls
    }

    val assetPaths = remember {
        listOf(
            "player/voice_room_game_button.svga",
            "player/voice_room_gift_panel_button.svga"
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

    // 用于混合展示的资源列表：包含 Assets 路径和 File 路径
    val mixedResources = remember { mutableStateListOf<Any>() }
    
    LaunchedEffect(Unit) {
        // 先添加 Assets 资源
        mixedResources.addAll(assetPaths)
        
        // 将 Assets 资源复制到本地私有目录以测试 File 路径播放
        withContext(Dispatchers.IO) {
            assetPaths.forEach { path ->
                try {
                    val fileName = path.substringAfterLast("/")
                    val targetFile = File(context.filesDir, "test_files/$fileName")
                    if (!targetFile.exists()) {
                        targetFile.parentFile?.mkdirs()
                        context.assets.open(path).use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    // 添加本地文件绝对路径到混合列表
                    withContext(Dispatchers.Main) {
                        mixedResources.add(targetFile.absolutePath)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
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
            text = "混合资源预览 (Assets & Local File)",
            modifier = Modifier.padding(16.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(mixedResources) { model ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SvgaAnimation(
                        model = model,
                        priority = SvgaPriority.High,
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color.LightGray.copy(0.2f))
                    )
                    Text(
                        text = if (model.toString().startsWith("/")) "File" else "Asset",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
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
                                gridItemPositions[index] = coords.positionInWindow()
                            }
                        }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = {
                    scope.launch {
                        repeat(15) {
                            val startIdx = Random.nextInt(0, 50)
                            val startPos = gridItemPositions[startIdx] ?: Offset.Zero
                            val iconUrl = iconUrls.random()

                            val anim = Animatable(startPos, Offset.VectorConverter)
                            val scale = Animatable(0.5f)

                            val data = FlyingIconData(iconUrl, anim, scale)
                            flyingData.add(data)

                            launch {
                                launch {
                                    anim.animateTo(
                                        targetValue = buttonCenter,
                                        animationSpec = tween(800)
                                    )
                                }
                                launch {
                                    scale.animateTo(1.2f, tween(400))
                                    scale.animateTo(0.8f, tween(400))
                                }
                                delay(800)
                                flyingData.remove(data)
                            }
                            delay(50)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        buttonCenter = Offset(
                            pos.x + coords.size.width / 2,
                            pos.y + coords.size.height / 2
                        )
                    },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
            ) {
                Text("触发 30 个并行动画 (渲染压力测试)")
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        flyingData.forEach { data ->
            preloadedBitmaps[data.url]?.let { bmp ->
                withTransform({
                    translate(
                        data.anim.value.x - (bmp.width * data.scale.value) / 2,
                        data.anim.value.y - (bmp.height * data.scale.value) / 2
                    )
                    scale(data.scale.value, data.scale.value, Offset.Zero)
                }) {
                    drawImage(bmp.asImageBitmap())
                }
            }
        }
    }
}
