/**
 * Created by zhuorui li
 * 专为 Android View 系统打造的高性能 SVGA 压力测试页面
 * 已加固生命周期管理，彻底解决内存泄漏问题
 */
package com.rui.composes.svga

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Process
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.opensource.svgaplayer.SVGAImageView
import com.opensource.svgaplayer.SVGAParser
import com.opensource.svgaplayer.SVGAVideoEntity
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.*
import java.net.URL
import java.util.*
import kotlin.collections.HashMap
import kotlin.random.Random

class NativeSvgaTestActivity : ComponentActivity() {

    private val svgaUrls = listOf(
        "https://d2180mnhafnhva.cloudfront.net/05213178614dfb7b0bdd9d19f82c9f5d.svga",
        "https://d2180mnhafnhva.cloudfront.net/IMNR0BjLFm0GCGtK27QzT9qRZCZjaoQp.svga",
        "https://img.chatie.live/app%2Fcard%2Fani_profilecard_aristocracy_lv1.svga",
        "https://d2180mnhafnhva.cloudfront.net/QmDvo89m0jJt2ctcqJwl8EZdCP9Pu2qD.svga"
    )

    private val iconUrls = listOf(
        "https://d2180mnhafnhva.cloudfront.net/7e38463dba12e58d71eb947bb7118cce.png",
        "https://d2180mnhafnhva.cloudfront.net/pnuvxRu0rF4H03ggtOXml3EO5ldekxdt.png",
        "https://d2180mnhafnhva.cloudfront.net/Zy58vmtMAfYXL5GXdxNwLHU8Gi66mstL.png"
    )

    private val preloadedBitmaps = HashMap<String, Bitmap>()
    private var isInterferenceEnabled = false
    private val interferencePool = Collections.synchronizedList(mutableListOf<List<String>>())
    private val flyingIcons = mutableListOf<NativeFlyingData>()
    private var overlayCanvas: FlyingIconOverlay? = null
    
    private var fpsCallback: android.view.Choreographer.FrameCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        val mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }

        mainContent.addView(createDashboard())

        mainContent.addView(RowLayout(this).apply {
            setPadding(dp2px(context, 16f))
            addView(TextView(context).apply { text = "Native 压力测试"; textSize = 18f; setTypeface(null, android.graphics.Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Button(context).apply { text = "关闭页面"; setOnClickListener { finish() } })
        })

        mainContent.addView(TextView(this).apply { text = "资源单项预览"; setPadding(dp2px(context, 16f), dp2px(context, 8f), dp2px(context, 16f), dp2px(context, 8f)) })
        val previewRecycler = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp2px(context, 100f))
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = PreviewAdapter(svgaUrls)
        }
        mainContent.addView(previewRecycler)

        mainContent.addView(TextView(this).apply { text = "300 个极限测试网格 (原生)"; setPadding(dp2px(context, 16f), dp2px(context, 8f), dp2px(context, 16f), dp2px(context, 8f)) })
        val gridRecycler = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            layoutManager = GridLayoutManager(context, 10)
            adapter = GridAdapter(svgaUrls)
            setHasFixedSize(true)
            setItemViewCacheSize(30)
        }
        mainContent.addView(gridRecycler)

        mainContent.addView(FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp2px(context, 100f))
            addView(Button(context).apply {
                text = "点击穿透 SVGA 触发飞行 🚀"
                layoutParams = FrameLayout.LayoutParams(-1, dp2px(context, 50f), Gravity.CENTER)
                setOnClickListener { fireIcons(gridRecycler, this) }
            })
            addView(SVGAImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(-1, -1)
                scaleType = ImageView.ScaleType.CENTER_CROP
                load("https://d2180mnhafnhva.cloudfront.net/05213178614dfb7b0bdd9d19f82c9f5d.svga")
            })
        })

        root.addView(mainContent)
        overlayCanvas = FlyingIconOverlay(this)
        root.addView(overlayCanvas, FrameLayout.LayoutParams(-1, -1))

        setContentView(root)
        preloadResources()
    }

    private fun createDashboard(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#99000000"))
            setPadding(dp2px(context, 8f))
        }

        val fpsTxt = TextView(this).apply { text = "FPS: 0"; setTextColor(Color.GREEN); textSize = 16f }
        val cpuTxt = TextView(this).apply { text = "CPU: 0%"; setTextColor(Color.WHITE); textSize = 14f }
        container.addView(RowLayout(this).apply {
            addView(fpsTxt, LinearLayout.LayoutParams(0, -2, 1f))
            addView(cpuTxt)
        })

        val memTxt = TextView(this).apply { text = "MEM: 0/0"; setTextColor(Color.WHITE); textSize = 12f }
        val interferenceSwitch = Switch(this).apply {
            setOnCheckedChangeListener { _, isChecked -> 
                isInterferenceEnabled = isChecked 
                if (isChecked) startInterference() else interferencePool.clear()
            }
        }
        container.addView(RowLayout(this).apply {
            addView(memTxt, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(context).apply { text = "干扰"; setTextColor(Color.WHITE); textSize = 12f })
            addView(interferenceSwitch)
        })

        lifecycleScope.launch {
            var lastCpuTime = Process.getElapsedCpuTime()
            var lastTime = System.currentTimeMillis()
            val numCores = Runtime.getRuntime().availableProcessors()
            var frames = 0
            var lastFpsTime = System.nanoTime()

            fpsCallback = object : android.view.Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    frames++
                    val now = System.nanoTime()
                    if (now - lastFpsTime >= 1_000_000_000L) {
                        fpsTxt.text = "FPS: $frames"
                        fpsTxt.setTextColor(if (frames > 45) Color.GREEN else Color.RED)
                        frames = 0; lastFpsTime = now
                    }
                    if (fpsCallback != null) android.view.Choreographer.getInstance().postFrameCallback(this)
                }
            }
            android.view.Choreographer.getInstance().postFrameCallback(fpsCallback!!)

            while (isActive) {
                val runtime = Runtime.getRuntime()
                memTxt.text = "MEM: ${(runtime.totalMemory() - runtime.freeMemory()) / 1048576}MB / ${runtime.maxMemory() / 1048576}MB"
                val currentCpuTime = Process.getElapsedCpuTime()
                val currentTime = System.currentTimeMillis()
                val timeDelta = currentTime - lastTime
                if (timeDelta > 0) {
                    val cpuUsage = ((currentCpuTime - lastCpuTime).toFloat() / (timeDelta * numCores) * 100).coerceIn(0f, 100f)
                    cpuTxt.text = "CPU: ${String.format("%.1f", cpuUsage)}%"
                }
                lastCpuTime = currentCpuTime; lastTime = currentTime
                delay(1000)
            }
        }
        return container
    }

    private fun startInterference() {
        lifecycleScope.launch(Dispatchers.Default) {
            while (isInterferenceEnabled && isActive) {
                val runtime = Runtime.getRuntime()
                if ((runtime.totalMemory() - runtime.freeMemory()).toFloat() / runtime.maxMemory().toFloat() < 0.85f) {
                    interferencePool.add(List(20000) { UUID.randomUUID().toString() })
                } else if (interferencePool.isNotEmpty()) {
                    interferencePool.removeAt(0)
                }
                interferencePool.lastOrNull()?.sortedByDescending { it.reversed() }
                delay(100)
            }
        }
    }

    private fun preloadResources() {
        val loader = ImageLoader(this)
        lifecycleScope.launch {
            iconUrls.forEach { url ->
                val request = ImageRequest.Builder(this@NativeSvgaTestActivity).data(url).allowHardware(false).build()
                val result = loader.execute(request)
                (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { preloadedBitmaps[url] = it }
            }
        }
    }

    private fun fireIcons(targetRecycler: RecyclerView, anchor: View) {
        val startPos = IntArray(2).apply { anchor.getLocationInWindow(this) }
        val startOffset = Offset(startPos[0] + anchor.width / 2f, startPos[1] + anchor.height / 2f)
        for (i in 0 until 50) {
            val holder = targetRecycler.findViewHolderForAdapterPosition(i) ?: continue
            val view = holder.itemView
            val pos = IntArray(2).apply { view.getLocationInWindow(this) }
            val targetOffset = Offset(pos[0] + view.width / 2f, pos[1] + view.height / 2f)
            lifecycleScope.launch {
                val data = NativeFlyingData(startOffset, iconUrls.random())
                synchronized(flyingIcons) { flyingIcons.add(data) }
                delay(i * 2L)
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = (600 + Random.nextInt(400)).toLong()
                    interpolator = LinearInterpolator()
                    addUpdateListener {
                        val fraction = it.animatedFraction
                        data.currentPos = Offset(startOffset.x + (targetOffset.x - startOffset.x) * fraction, startOffset.y + (targetOffset.y - startOffset.y) * fraction)
                        data.scale = 1.0f - (0.5f * fraction)
                        overlayCanvas?.invalidate()
                    }
                    start()
                }
                delay(1200)
                synchronized(flyingIcons) { flyingIcons.remove(data) }
                overlayCanvas?.invalidate()
            }
        }
    }

    override fun onDestroy() {
        fpsCallback = null
        isInterferenceEnabled = false
        interferencePool.clear()
        flyingIcons.clear()
        overlayCanvas = null
        super.onDestroy()
    }

    inner class FlyingIconOverlay(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            synchronized(flyingIcons) {
                flyingIcons.forEach { data ->
                    preloadedBitmaps[data.url]?.let { bitmap ->
                        canvas.save()
                        canvas.translate(data.currentPos.x, data.currentPos.y)
                        canvas.scale(data.scale, data.scale)
                        canvas.drawBitmap(bitmap, -bitmap.width / 2f, -bitmap.height / 2f, paint)
                        canvas.restore()
                    }
                }
            }
        }
    }

    class PreviewAdapter(val urls: List<String>) : RecyclerView.Adapter<PreviewAdapter.VH>() {
        class VH(val v: SVGAImageView) : RecyclerView.ViewHolder(v)
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val size = dp2px(p.context, 100f)
            return VH(SVGAImageView(p.context).apply { layoutParams = ViewGroup.LayoutParams(size, size) })
        }
        override fun onBindViewHolder(h: VH, p: Int) = h.v.load(urls[p % urls.size])
        override fun getItemCount() = urls.size
    }

    class GridAdapter(val urls: List<String>) : RecyclerView.Adapter<GridAdapter.VH>() {
        class VH(val v: SVGAImageView) : RecyclerView.ViewHolder(v)
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val size = p.resources.displayMetrics.widthPixels / 10
            return VH(SVGAImageView(p.context).apply { layoutParams = ViewGroup.LayoutParams(size, size); scaleType = ImageView.ScaleType.CENTER_CROP })
        }
        override fun onBindViewHolder(h: VH, p: Int) = h.v.load(urls[p % urls.size])
        override fun getItemCount() = 300
    }

    class RowLayout(context: Context) : LinearLayout(context) { init { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL } }
    class NativeFlyingData(val start: Offset, val url: String) { var currentPos = start; var scale = 1.0f }
}

fun SVGAImageView.load(source: String) {
    val parser = SVGAParser(context)
    val callback = object : SVGAParser.ParseCompletion {
        override fun onComplete(videoItem: SVGAVideoEntity) {
            setVideoItem(videoItem)
            startAnimation()
        }
        override fun onError(e: Exception, alias: String) {}
    }
    if (source.startsWith("http://") || source.startsWith("https://")) {
        parser.decodeFromURL(URL(source), callback)
    } else {
        parser.decodeFromAssets(source, callback)
    }
}

fun View.onClick(action: () -> Unit) = setOnClickListener { action() }
fun View.postFrameCallback(callback: android.view.Choreographer.FrameCallback) = android.view.Choreographer.getInstance().postFrameCallback(callback)
fun dp2px(context: Context, dp: Float): Int = (dp * context.resources.displayMetrics.density).toInt()
