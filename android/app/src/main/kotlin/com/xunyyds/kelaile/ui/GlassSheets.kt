package com.xunyyds.kelaile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.xunyyds.kelaile.ui.glass.glassPanel
import com.xunyyds.kelaile.ui.glass.glassSheen

@Composable
internal fun kelaileDialogBlurEnabled(): Boolean =
    LocalDialogBlur.current && android.os.Build.VERSION.SDK_INT >= 31

/**
 * 全屏透明浮层宿主。
 *
 * 用 Dialog 承载而不是内联 Box：对话框是独立窗口，无论浮层被写在多深的
 * 滚动容器里（例如设置页的分组卡片内部）都能正确盖住整页，也自带返回键语义。
 * 平台默认底色与暗幕被清掉，改由我们自己画遮罩；开启「弹窗背景模糊」时
 * 用 Android 原生的窗口跨层模糊（FLAG_BLUR_BEHIND + blurBehindRadius），
 * 由系统对弹窗下面的应用内容实时模糊。
 */
@Composable
private fun OverlayHost(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    dismissOnBackPress: Boolean = true,
    content: @Composable (progress: Float) -> Unit,
) {
    val motion = kelaileMotionEnabled()
    var mounted by remember { mutableStateOf(visible) }
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            if (!motion) {
                progress.snapTo(1f)
            } else {
                progress.snapTo(0f)
                progress.animateTo(
                    1f,
                    tween(KelaileMotion.standard, easing = KelaileMotion.emphasizedEasing),
                )
            }
        } else if (mounted) {
            if (motion) {
                progress.animateTo(
                    0f,
                    tween(KelaileMotion.quick, easing = KelaileMotion.accelerateEasing),
                )
            } else {
                progress.snapTo(0f)
            }
            mounted = false
        }
    }
    if (!mounted) return
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        val blurWindow = kelaileDialogBlurEnabled()
        val blurRadiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { 28.dp.toPx() }.toInt()
        LaunchedEffect(view, blurWindow, blurRadiusPx) {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setDimAmount(0f)
                window.setBackgroundDrawable(
                    android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT),
                )
                if (blurWindow) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.attributes = window.attributes.also {
                        it.blurBehindRadius = blurRadiusPx
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value }) {
            content(progress.value)
        }
    }
}

/**
 * 弹窗外的背景：半透明遮罩。窗口背后的实时模糊由 OverlayHost 用
 * FLAG_BLUR_BEHIND 交给系统做；这里只负责压暗与拦截点击。
 */
@Composable
internal fun BoxScope.OverlayScrim(
    alpha: Float,
    onClick: (() -> Unit)? = null,
) {
    val canvas = LocalKelaileCanvas.current
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer { this.alpha = alpha }
            .background(canvas.scrim)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier,
            ),
    )
}

/**
 * 玻璃底部卡片。
 *
 * 大圆角、半透明、1dp 高光描边，顶部一枚拖动条，向下拖过阈值或点背景都能关掉。
 */
@Composable
fun GlassSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = kelaileMotionEnabled()
    val canvas = LocalKelaileCanvas.current
    var drag by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(visible) { if (visible) drag = 0f }
    val shape = RoundedCornerShape(KelaileShapes.sheet)
    OverlayHost(visible = visible, onDismissRequest = onDismiss) { progress ->
        val eased = if (motion) progress else 1f
        Box(Modifier.fillMaxSize().imePadding()) {
            OverlayScrim(
                alpha = eased,
                onClick = onDismiss,
            )
            Column(
                modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .graphicsLayer { translationY = (1f - eased) * 220.dp.toPx() + drag }
                    .then(KelaileElevation.soft(KelaileElevation.floating, shape, 0.05f, 0.12f))
                    .glassPanel(shape, canvas.sheetFill, canvas.strokeTop, canvas.strokeBottom)
                    .glassSheen(shape, Color.White),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, delta ->
                                    change.consume()
                                    drag = (drag + delta).coerceAtLeast(0f)
                                },
                                onDragEnd = {
                                    val heightPx = size.height.toFloat().coerceAtLeast(1f)
                                    if (drag > heightPx * 0.16f) onDismiss() else drag = 0f
                                },
                                onDragCancel = { drag = 0f },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .width(38.dp)
                            .height(4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f),
                                RoundedCornerShape(KelaileShapes.capsule),
                            ),
                    )
                }
                title?.let {
                    Text(
                        it,
                        Modifier
                            .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 4.dp)
                            .semantics { paneTitle = it },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 620.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 6.dp, bottom = 18.dp),
                    content = content,
                )
            }
        }
    }
}

/**
 * 居中玻璃对话框。
 *
 * 结构与 [GlassSheet] 相同，只是从中间弹出、带轻微缩放，取代系统 AlertDialog，
 * 让确认框也保持同一套材质。
 */
@Composable
fun KelaileDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    dismissOnOutside: Boolean = true,
    confirmLabel: String = "好",
    destructive: Boolean = false,
    onConfirm: (() -> Unit)? = null,
    dismissLabel: String? = null,
    onDismissAction: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val motion = kelaileMotionEnabled()
    val canvas = LocalKelaileCanvas.current
    val shape = RoundedCornerShape(KelaileShapes.card)
    OverlayHost(
        visible = visible,
        onDismissRequest = onDismiss,
        dismissOnBackPress = true,
    ) { progress ->
        val eased = if (motion) progress else 1f
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            OverlayScrim(
                alpha = eased,
                onClick = if (dismissOnOutside) onDismiss else null,
            )
            Column(
                modifier
                    .padding(horizontal = 26.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .graphicsLayer {
                        alpha = eased
                        val s = 0.94f + 0.06f * eased
                        scaleX = s
                        scaleY = s
                    }
                    .then(KelaileElevation.soft(KelaileElevation.floating, shape, 0.06f, 0.14f))
                    .glassPanel(shape, canvas.sheetFill, canvas.strokeTop, canvas.strokeBottom)
                    .glassSheen(shape, Color.White)
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                content?.let {
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        content = it,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (dismissLabel != null) {
                        TextButton(onClick = { onDismissAction?.invoke() ?: onDismiss() }) { Text(dismissLabel) }
                    }
                    Spacer(Modifier.width(2.dp))
                    GlassPillButton(
                        confirmLabel,
                        onClick = { onConfirm?.invoke() ?: onDismiss() },
                        tint = if (destructive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * 带插槽的玻璃对话框。
 *
 * 与 [KelaileDialog] 同一材质与动画，但标题、正文与按钮都由调用方给出，
 * 供更新弹窗这类需要自定义按钮组合的场景使用。
 */
@Composable
fun GlassDialogScaffold(
    onDismiss: () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    dismissOnBackPress: Boolean = true,
    text: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    val motion = kelaileMotionEnabled()
    val canvas = LocalKelaileCanvas.current
    val shape = RoundedCornerShape(KelaileShapes.card)
    OverlayHost(
        visible = visible,
        onDismissRequest = onDismiss,
        dismissOnBackPress = dismissOnBackPress,
    ) { progress ->
        val eased = if (motion) progress else 1f
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            OverlayScrim(alpha = eased)
            Column(
                modifier
                    .padding(horizontal = 22.dp)
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = eased
                        val s = 0.94f + 0.06f * eased
                        scaleX = s
                        scaleY = s
                    }
                    .then(KelaileElevation.soft(KelaileElevation.floating, shape, 0.06f, 0.14f))
                    .glassPanel(shape, canvas.sheetFill, canvas.strokeTop, canvas.strokeBottom)
                    .glassSheen(shape, Color.White)
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                title()
                text?.let {
                    Box(Modifier.fillMaxWidth()) { it() }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

/**
 * 「正在保存」悬浮通知：从右上角滑入弹出，保存结束后右滑退出。
 *
 * 入场走轻微回弹的弹簧（滑入 + 淡入 + 微缩放），退场走加速右滑淡出；
 * 退场播完才卸载，避免闪一下就消失。
 */
@Composable
fun BoxScope.SavingToast(visible: Boolean, bottomInset: Dp = 16.dp) {
    var mounted by remember { mutableStateOf(visible) }
    var shown by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            shown = true
        } else if (mounted) {
            // 保存可能只花几十毫秒；给提示一个最短展示时间，避免一闪而过
            delay(900)
            shown = false
        }
    }
    if (!mounted) return
    val canvas = LocalKelaileCanvas.current
    val shape = RoundedCornerShape(KelaileShapes.capsule)
    val motion = kelaileMotionEnabled()
    val exitMillis = kelaileMillis(KelaileMotion.quick)
    val progress = remember { Animatable(if (shown) 0f else 1f) }
    LaunchedEffect(shown) {
        if (shown) {
            if (!motion) progress.snapTo(1f)
            else progress.animateTo(
                1f,
                androidx.compose.animation.core.spring(
                    dampingRatio = 0.9f,
                    stiffness = 320f,
                ),
            )
        } else {
            if (!motion) progress.snapTo(0f)
            else progress.animateTo(0f, tween(exitMillis, easing = KelaileMotion.accelerateEasing))
            mounted = false
        }
    }
    val eased = progress.value
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .padding(bottom = bottomInset)
            .graphicsLayer {
                alpha = eased
                translationY = (1f - eased) * 18.dp.toPx()
                val s = 0.92f + 0.08f * eased
                scaleX = s
                scaleY = s
            }
            .then(KelaileElevation.soft(KelaileElevation.floating, shape, 0.05f, 0.12f))
            .glassPanel(shape, canvas.sheetFill, canvas.strokeTop, canvas.strokeBottom)
            .glassSheen(shape, Color.White)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.CircularProgressIndicator(
                Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text("正在保存…", style = MaterialTheme.typography.labelLarge)
        }
    }
}
