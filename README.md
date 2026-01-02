Compose SVGA Player 🚀
Compose SVGA Player 是一个专为 Jetpack Compose 打造的高性能 SVGA 动画播放组件。它针对直播间、礼物列表等 100+ 实例同屏展示 的严苛场景进行了极致优化，支持自适应降频和异步双缓冲渲染技术，确保在 300 个格子的列表滚动中依然保持 60 FPS 的丝滑体验。
✨ 核心特性
•⚡ 极致性能：
◦全局同步时钟：消除数百个独立协程带来的调度开销。
◦异步离屏渲染：将复杂的矢量路径计算移出 UI 线程，彻底杜绝主线程卡顿。
◦渲染分桶 (16px/32px Bucketization)：自动归一化细微的尺寸差异，合并重复的渲染任务，CPU 压力降低 80%+。
•📉 自适应策略：
◦优先级调度 (SvgaPriority)：支持 High/Normal/Low 模式，在系统高负载时自动降帧。
◦极端负载保护：系统 FPS 极低时可自动停止在第一帧，保护核心交互不卡死。
◦自动可见性静默：移出屏幕的组件自动断开时钟订阅，实现真正的 零 CPU 占用暂停。
•♻️ 内存与 GC 优化：
◦Bitmap 复用池：强制回收并擦除离屏缓冲，彻底解决高并发下的 GC 卡顿和资源重叠 Bug。
◦自动下采样：根据视图实际像素大小解码，大幅节省内存，杜绝 OOM。
•🤝 开发友好：
◦Coil 风格 API：提供 loading、failure 占位槽位。
◦完整生命周期：支持 onPlay、onSuccess、onStep、onFinished 等精准回调。

🛠️ 快速开始
1. 初始化全局环境
在 MainActivity 或 Application 中，设置 Compose 内容前初始化解析器：
...
class MainActivity : ComponentActivity() {override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化全局解析器，建议在 onCreate 最开始执行
        SVGAParser.shareParser().init(this)
        
        setContent {
            // 驱动全局同步时钟信号
            val svgaTick = remember { mutableLongStateOf(System.nanoTime()) }
            LaunchedEffect(Unit) {
                while (true) { withFrameNanos { svgaTick.longValue = it } }
            }

            // 注入全局配置
            CompositionLocalProvider(LocalSvgaClock provides svgaTick) {
                AppTheme {
                    // 你的业务代码
                }
            }
        }
    }
}
...
