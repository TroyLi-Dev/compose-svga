package com.rui.compose.svga.view

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import com.rui.compose.svga.R
import com.rui.compose.svga.drawer.SvgaDynamicEntity
import com.rui.compose.svga.entity.SvgaVideoEntity
import com.rui.compose.svga.parser.SvgaParser
import com.rui.compose.svga.utils.SvgaRange
import com.rui.compose.svga.utils.log.LogUtils
import com.rui.compose.svga.utils.log.SvgaLogger
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min

/**
 * 现代化的标准 Android View SVGA 播放组件
 *
 * 核心特性：
 * 1. 与 Compose 版统一共享底层高性能 [SvgaParser]、双级缓存与 [SvgaCanvasDrawer]。
 * 2. 彻底治理生命周期管理，在 [onDetachedFromWindow] 时自动释放动画与任务，杜绝内存泄漏。
 * 3. 支持 XML 属性布局配置与一键式 [load] 方法。
 */
open class SvgaImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImageView(context, attrs, defStyleAttr) {

    private val TAG = "SvgaImageView"

    var isAnimating = false
        private set

    var loops = 0
    var clearsAfterStop = false
    var clearsAfterDetached = false
    var fillMode: SvgaFillMode = SvgaFillMode.Forward
    var callback: SvgaCallback? = null

    private var autoPlay = true
    private var antiAlias = true
    private var startFrame = 0
    private var endFrame = 0

    private var animator: ValueAnimator? = null
    private var currentParseCancelTask: (() -> Unit)? = null
    private var itemClickAreaListener: SvgaItemClickListener? = null
    private var currentLoadToken = 0L
    private var isUserPaused = false
    private var downKey: String? = null

    private val animatorListener = SvgaAnimatorListener(this)
    private val animatorUpdateListener = SvgaAnimatorUpdateListener(this)

    init {
        if (!isInEditMode) {
            val appCtx = context.applicationContext ?: context
            com.rui.compose.svga.cache.SvgaDiskCache.init(appCtx)
            com.rui.compose.svga.utils.SvgaMemoryCalculator.instance.init(appCtx)
        }
        attrs?.let { loadAttrs(it) }
    }

    private fun loadAttrs(attrs: AttributeSet) {
        val typedArray = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.SvgaImageView,
            0,
            0
        )
        try {
            loops = typedArray.getInt(R.styleable.SvgaImageView_loopCount, 0)
            clearsAfterStop = typedArray.getBoolean(R.styleable.SvgaImageView_clearsAfterStop, false)
            clearsAfterDetached = typedArray.getBoolean(R.styleable.SvgaImageView_clearsAfterDetached, false)
            antiAlias = typedArray.getBoolean(R.styleable.SvgaImageView_antiAlias, true)
            autoPlay = typedArray.getBoolean(R.styleable.SvgaImageView_autoPlay, true)

            when (typedArray.getInt(R.styleable.SvgaImageView_fillMode, 1)) {
                0 -> fillMode = SvgaFillMode.Backward
                1 -> fillMode = SvgaFillMode.Forward
                2 -> fillMode = SvgaFillMode.Clear
            }

            if (!isInEditMode) {
                typedArray.getString(R.styleable.SvgaImageView_source)?.let { source ->
                    load(source)
                }
            }
        } finally {
            typedArray.recycle()
        }
    }

    /**
     * 智能异步加载并播放资源（支持 URL、本地绝对路径、Assets 路径、File 对象、Raw 资源）
     */
    fun load(source: Any?, onComplete: ((SvgaVideoEntity) -> Unit)? = null) {
        load(source, onError = null, onComplete = onComplete)
    }

    /**
     * 智能异步加载并播放资源（支持 URL、本地绝对路径、Assets 路径、File 对象、Raw 资源）
     */
    fun load(
        source: Any?,
        onError: ((Exception) -> Unit)? = null,
        onComplete: ((SvgaVideoEntity) -> Unit)? = null
    ) {
        val loadToken = ++currentLoadToken
        currentParseCancelTask?.invoke()
        currentParseCancelTask = null
        if (source == null) {
            clear()
            return
        }
        val cacheKey = com.rui.compose.svga.cache.SvgaDiskCache.buildCacheKeyForModel(source)
        val cached = com.rui.compose.svga.cache.SvgaMemoryCache.instance.get(cacheKey)
        if (cached != null) {
            cached.antiAlias = antiAlias
            setVideoItem(cached)
            onComplete?.invoke(cached)
            if (autoPlay && isAttachedToWindow) {
                startAnimation()
            }
            return
        }
        val parser = SvgaParser(context)
        val cancelTask = parser.decodeAuto(source, object : SvgaParser.ParseCompletion {
            override fun onComplete(videoItem: SvgaVideoEntity) {
                post {
                    if (currentLoadToken != loadToken) return@post
                    videoItem.antiAlias = antiAlias
                    setVideoItem(videoItem)
                    onComplete?.invoke(videoItem)
                    if (autoPlay && isAttachedToWindow) {
                        startAnimation()
                    }
                }
            }

            override fun onError(e: Exception, alias: String) {
                post {
                    if (currentLoadToken != loadToken) return@post
                    LogUtils.error(TAG, "Load SVGA failed for: $source", e)
                    onError?.invoke(e)
                }
            }
        })
        currentParseCancelTask = cancelTask
    }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        val oldDrawable = getSvgaDrawable()
        if (oldDrawable != null && oldDrawable !== drawable) {
            stopAnimation(false)
            oldDrawable.clear()
        }
        super.setImageDrawable(drawable)
        (drawable as? SvgaDrawable)?.scaleType = this.scaleType
    }

    override fun setScaleType(scaleType: ScaleType?) {
        super.setScaleType(scaleType)
        scaleType?.let { getSvgaDrawable()?.scaleType = it }
    }

    fun setVideoItem(videoItem: SvgaVideoEntity?) {
        setVideoItem(videoItem, SvgaDynamicEntity())
    }

    fun setVideoItem(videoItem: SvgaVideoEntity?, dynamicItem: SvgaDynamicEntity?) {
        if (videoItem == null) {
            setImageDrawable(null)
        } else {
            val drawable = SvgaDrawable(videoItem, dynamicItem ?: SvgaDynamicEntity()).apply {
                this.scaleType = this@SvgaImageView.scaleType
                this.cleared = true
            }
            setImageDrawable(drawable)
        }
    }

    fun startAnimation() {
        startAnimation(null, false)
    }

    fun startAnimation(range: SvgaRange?, reverse: Boolean = false) {
        isUserPaused = false
        stopAnimation(false)
        val drawable = getSvgaDrawable() ?: return
        drawable.cleared = false
        drawable.scaleType = scaleType

        val videoItem = drawable.videoEntity
        if (videoItem.frames <= 0) return

        startFrame = max(0, range?.location ?: 0)
        endFrame = min(
            videoItem.frames - 1,
            ((range?.location ?: 0) + (range?.length ?: Int.MAX_VALUE) - 1)
        )
        if (startFrame > endFrame) return

        val fps = max(1, videoItem.fps)
        val durationMs = ((endFrame - startFrame + 1) * 1000L / fps)

        animator = ValueAnimator.ofInt(startFrame, endFrame).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            repeatCount = if (loops <= 0) ValueAnimator.INFINITE else loops - 1
            addUpdateListener(animatorUpdateListener)
            addListener(animatorListener)
            if (reverse) reverse() else start()
        }
    }

    fun pauseAnimation() {
        isUserPaused = true
        if (animator?.isRunning == true) {
            animator?.pause()
            getSvgaDrawable()?.pause()
            isAnimating = false
            callback?.onPause()
        } else {
            stopAnimation(false)
            callback?.onPause()
        }
    }

    fun resumeAnimation() {
        isUserPaused = false
        if (animator?.isPaused == true) {
            animator?.resume()
            getSvgaDrawable()?.resume()
            isAnimating = true
        } else if (!isAnimating) {
            startAnimation()
        }
    }

    fun stopAnimation() {
        stopAnimation(clearsAfterStop)
    }

    fun stopAnimation(clear: Boolean) {
        isUserPaused = false
        animator?.apply {
            removeListener(animatorListener)
            removeUpdateListener(animatorUpdateListener)
            cancel()
        }
        animator = null
        isAnimating = false
        getSvgaDrawable()?.apply {
            stop()
            cleared = clear
        }
    }

    fun stepToFrame(frame: Int, andPlay: Boolean = false) {
        pauseAnimation()
        val drawable = getSvgaDrawable() ?: return
        val targetFrame = frame.coerceIn(0, max(0, drawable.videoEntity.frames - 1))
        drawable.cleared = false
        drawable.currentFrame = targetFrame
        drawable.invalidateSelf()
        invalidate()
        if (andPlay) {
            startAnimation()
            animator?.let { anim ->
                val totalSpan = max(1, endFrame - startFrame + 1)
                val offsetInSpan = (targetFrame - startFrame).coerceIn(0, totalSpan - 1)
                val ratio = offsetInSpan.toFloat() / totalSpan
                anim.currentPlayTime = (ratio * anim.duration).toLong()
            }
        }
    }

    fun stepToPercentage(percentage: Double, andPlay: Boolean = false) {
        val drawable = getSvgaDrawable() ?: return
        val totalFrames = drawable.videoEntity.frames
        val frame = (totalFrames * percentage).toInt().coerceIn(0, max(0, totalFrames - 1))
        stepToFrame(frame, andPlay)
    }

    fun clear() {
        stopAnimation(true)
        setImageDrawable(null)
    }

    fun clearSVGADrawable() {
        clear()
    }

    fun resetit() {
        currentParseCancelTask?.invoke()
        currentParseCancelTask = null
        clear()
        loops = 0
        clearsAfterStop = false
        clearsAfterDetached = false
        fillMode = SvgaFillMode.Forward
        callback = null
        itemClickAreaListener = null
    }

    fun setOnAnimKeyClickListener(clickListener: SvgaItemClickListener) {
        itemClickAreaListener = clickListener
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val ev = event ?: return super.onTouchEvent(event)
        val listener = itemClickAreaListener ?: return super.onTouchEvent(ev)
        val drawable = getSvgaDrawable() ?: return super.onTouchEvent(ev)

        val localX = ev.x - drawable.bounds.left
        val localY = ev.y - drawable.bounds.top

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                downKey = null
                for ((key, value) in drawable.dynamicEntity.clickMap) {
                    val minX = kotlin.math.min(value[0], value[2])
                    val maxX = kotlin.math.max(value[0], value[2])
                    val minY = kotlin.math.min(value[1], value[3])
                    val maxY = kotlin.math.max(value[1], value[3])
                    if (localX >= minX && localX <= maxX && localY >= minY && localY <= maxY) {
                        downKey = key
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val targetKey = downKey
                downKey = null
                if (targetKey != null) {
                    val value = drawable.dynamicEntity.clickMap[targetKey]
                    if (value != null) {
                        val minX = kotlin.math.min(value[0], value[2])
                        val maxX = kotlin.math.max(value[0], value[2])
                        val minY = kotlin.math.min(value[1], value[3])
                        val maxY = kotlin.math.max(value[1], value[3])
                        if (localX >= minX && localX <= maxX && localY >= minY && localY <= maxY) {
                            listener.onClick(targetKey)
                            return true
                        }
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                downKey = null
            }
        }
        return super.onTouchEvent(ev)
    }

    private var wasAnimatingBeforeHide = false
    private var wasAnimatingBeforeDetach = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isUserPaused && (autoPlay || wasAnimatingBeforeDetach) && getSvgaDrawable() != null && !isAnimating) {
            wasAnimatingBeforeDetach = false
            startAnimation()
        }
    }

    override fun onVisibilityChanged(changedView: android.view.View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isShown) {
            if (wasAnimatingBeforeHide && isAttachedToWindow) {
                wasAnimatingBeforeHide = false
                if (animator?.isPaused == true) {
                    animator?.resume()
                    getSvgaDrawable()?.resume()
                    isAnimating = true
                } else if (!isAnimating && autoPlay) {
                    startAnimation()
                }
            }
        } else {
            if (isAnimating) {
                wasAnimatingBeforeHide = true
                animator?.pause()
                getSvgaDrawable()?.pause()
                isAnimating = false
            }
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            if (wasAnimatingBeforeHide) {
                wasAnimatingBeforeHide = false
                if (animator?.isPaused == true) {
                    animator?.resume()
                    getSvgaDrawable()?.resume()
                    isAnimating = true
                } else if (!isAnimating) {
                    startAnimation()
                }
            }
        } else {
            if (isAnimating) {
                wasAnimatingBeforeHide = true
                animator?.pause()
                getSvgaDrawable()?.pause()
                isAnimating = false
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        currentParseCancelTask?.invoke()
        currentParseCancelTask = null
        wasAnimatingBeforeDetach = isAnimating
        stopAnimation(clearsAfterDetached)
        if (clearsAfterDetached) {
            clear()
        }
    }

    private fun getSvgaDrawable(): SvgaDrawable? = drawable as? SvgaDrawable

    private fun handleAnimationUpdate(anim: ValueAnimator) {
        val drawable = getSvgaDrawable() ?: return
        val frame = anim.animatedValue as? Int ?: return
        drawable.currentFrame = frame
        val totalFrames = drawable.videoEntity.frames
        val percentage = if (totalFrames > 0) (frame + 1).toDouble() / totalFrames else 0.0
        callback?.onStep(frame, percentage)
    }

    private fun handleAnimationEnd() {
        isAnimating = false
        stopAnimation(clearsAfterStop)
        val drawable = getSvgaDrawable()
        if (drawable != null) {
            when (fillMode) {
                SvgaFillMode.Backward -> drawable.currentFrame = startFrame
                SvgaFillMode.Forward -> drawable.currentFrame = endFrame
                SvgaFillMode.Clear -> drawable.cleared = true
            }
        }
        callback?.onFinished()
    }

    private class SvgaAnimatorListener(view: SvgaImageView) : Animator.AnimatorListener {
        private val ref = WeakReference(view)
        private var isCanceled = false

        override fun onAnimationStart(animation: Animator) {
            isCanceled = false
            ref.get()?.isAnimating = true
        }

        override fun onAnimationRepeat(animation: Animator) {
            ref.get()?.callback?.onRepeat()
        }

        override fun onAnimationCancel(animation: Animator) {
            isCanceled = true
            ref.get()?.isAnimating = false
        }

        override fun onAnimationEnd(animation: Animator) {
            val wasCanceled = isCanceled
            isCanceled = false
            if (!wasCanceled) {
                ref.get()?.handleAnimationEnd()
            }
        }
    }

    private class SvgaAnimatorUpdateListener(view: SvgaImageView) : ValueAnimator.AnimatorUpdateListener {
        private val ref = WeakReference(view)
        override fun onAnimationUpdate(animation: ValueAnimator) { ref.get()?.handleAnimationUpdate(animation) }
    }
}
