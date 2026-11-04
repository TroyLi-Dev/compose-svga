package com.rui.compose.svga

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.rui.compose.svga.controller.SvgaController
import com.rui.compose.svga.drawer.SvgaDynamicEntity
import com.rui.compose.svga.entity.SvgaVideoEntity
import com.rui.compose.svga.model.SvgaPriority

/**
 * [SvgaAnimation] 的别名组件，满足习惯 "Player" 命名的开发者
 */
@Composable
fun SvgaPlayer(
    model: Any?,
    modifier: Modifier = Modifier,
    priority: SvgaPriority = SvgaPriority.Normal,
    maxFps: Int = Int.MAX_VALUE,
    loops: Int = 0,
    dynamicEntity: SvgaDynamicEntity? = null,
    isStop: Boolean = false,
    isMute: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
    needCutBitmap: Boolean = (priority == SvgaPriority.Low),
    onLoading: () -> Unit = DEFAULT_LAMBDA,
    onPlay: () -> Unit = DEFAULT_LAMBDA,
    onSuccess: (SvgaVideoEntity) -> Unit = DEFAULT_ON_SUCCESS,
    onError: (Throwable) -> Unit = DEFAULT_ON_ERROR,
    onStep: (frame: Int, percentage: Double) -> Unit = DEFAULT_ON_STEP,
    onFinished: () -> Unit = DEFAULT_LAMBDA,
    onItemClick: ((key: String) -> Unit)? = null,
    loading: @Composable (() -> Unit)? = null,
    failure: @Composable (() -> Unit)? = null
) = SvgaAnimation(
    model = model,
    modifier = modifier,
    priority = priority,
    maxFps = maxFps,
    loops = loops,
    dynamicEntity = dynamicEntity,
    isStop = isStop,
    isMute = isMute,
    contentScale = contentScale,
    needCutBitmap = needCutBitmap,
    onLoading = onLoading,
    onPlay = onPlay,
    onSuccess = onSuccess,
    onError = onError,
    onStep = onStep,
    onFinished = onFinished,
    onItemClick = onItemClick,
    loading = loading,
    failure = failure
)

/**
 * 带有控制器驱动的 [SvgaPlayer] 别名组件
 */
@Composable
fun SvgaPlayer(
    controller: SvgaController,
    modifier: Modifier = Modifier,
    dynamicEntity: SvgaDynamicEntity? = null,
    contentScale: ContentScale = ContentScale.Fit,
    onLoading: () -> Unit = DEFAULT_LAMBDA,
    onPlay: () -> Unit = DEFAULT_LAMBDA,
    onSuccess: (SvgaVideoEntity) -> Unit = DEFAULT_ON_SUCCESS,
    onError: (Throwable) -> Unit = DEFAULT_ON_ERROR,
    onStep: (frame: Int, percentage: Double) -> Unit = DEFAULT_ON_STEP,
    onFinished: () -> Unit = DEFAULT_LAMBDA,
    onItemClick: ((key: String) -> Unit)? = null,
    loading: @Composable (() -> Unit)? = null,
    failure: @Composable (() -> Unit)? = null
) = SvgaAnimation(
    controller = controller,
    modifier = modifier,
    dynamicEntity = dynamicEntity,
    contentScale = contentScale,
    onLoading = onLoading,
    onPlay = onPlay,
    onSuccess = onSuccess,
    onError = onError,
    onStep = onStep,
    onFinished = onFinished,
    onItemClick = onItemClick,
    loading = loading,
    failure = failure
)

/**
 * 带有 Kotlin DSL 动态配置支持的 [SvgaPlayer] 便捷重载
 */
@Composable
fun SvgaPlayer(
    model: Any?,
    modifier: Modifier = Modifier,
    priority: SvgaPriority = SvgaPriority.Normal,
    maxFps: Int = Int.MAX_VALUE,
    loops: Int = 0,
    isStop: Boolean = false,
    isMute: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
    onLoading: () -> Unit = DEFAULT_LAMBDA,
    onPlay: () -> Unit = DEFAULT_LAMBDA,
    onSuccess: (SvgaVideoEntity) -> Unit = DEFAULT_ON_SUCCESS,
    onError: (Throwable) -> Unit = DEFAULT_ON_ERROR,
    onStep: (frame: Int, percentage: Double) -> Unit = DEFAULT_ON_STEP,
    onFinished: () -> Unit = DEFAULT_LAMBDA,
    onItemClick: ((key: String) -> Unit)? = null,
    loading: @Composable (() -> Unit)? = null,
    failure: @Composable (() -> Unit)? = null,
    dynamicBuilder: SvgaDynamicEntity.() -> Unit
) = SvgaAnimation(
    model = model,
    modifier = modifier,
    priority = priority,
    maxFps = maxFps,
    loops = loops,
    isStop = isStop,
    isMute = isMute,
    contentScale = contentScale,
    onLoading = onLoading,
    onPlay = onPlay,
    onSuccess = onSuccess,
    onError = onError,
    onStep = onStep,
    onFinished = onFinished,
    onItemClick = onItemClick,
    loading = loading,
    failure = failure,
    dynamicBuilder = dynamicBuilder
)

/**
 * 由 [SvgaController] 控制并带有 Kotlin DSL 动态配置支持的 [SvgaPlayer] 便捷重载
 */
@Composable
fun SvgaPlayer(
    controller: SvgaController,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onLoading: () -> Unit = DEFAULT_LAMBDA,
    onPlay: () -> Unit = DEFAULT_LAMBDA,
    onSuccess: (SvgaVideoEntity) -> Unit = DEFAULT_ON_SUCCESS,
    onError: (Throwable) -> Unit = DEFAULT_ON_ERROR,
    onStep: (frame: Int, percentage: Double) -> Unit = DEFAULT_ON_STEP,
    onFinished: () -> Unit = DEFAULT_LAMBDA,
    onItemClick: ((key: String) -> Unit)? = null,
    loading: @Composable (() -> Unit)? = null,
    failure: @Composable (() -> Unit)? = null,
    dynamicBuilder: SvgaDynamicEntity.() -> Unit
) = SvgaAnimation(
    controller = controller,
    modifier = modifier,
    contentScale = contentScale,
    onLoading = onLoading,
    onPlay = onPlay,
    onSuccess = onSuccess,
    onError = onError,
    onStep = onStep,
    onFinished = onFinished,
    onItemClick = onItemClick,
    loading = loading,
    failure = failure,
    dynamicBuilder = dynamicBuilder
)
