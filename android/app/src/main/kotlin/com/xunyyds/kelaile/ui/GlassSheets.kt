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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.xunyyds.kelaile.ui.glass.glassPanel
import com.xunyyds.kelaile.ui.glass.glassSheen

/**
 * 全屏透明浮层宿主。
 *
 * 用 Dialog 承载而不是内联 Box：对话框是独立窗口，无论浮层被写在多深的
 * 滚动容器里（例如设置页的分组卡片内部）都能正确盖住整页，也自带返回键语义。
 * 平台默认底色与暗幕被清掉，改由我们自己画半透明遮罩，才能叠出玻璃质感。
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
        LaunchedEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setDimAmount(0f)
                window.setBackgroundDrawable(
                    android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT),
                )
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value }) {
            content(progress.value)
        }
    }
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
    val shape = RoundedCornerShape(
        topStart = KelaileShapes.sheet,
        topEnd = KelaileShapes.sheet,
        bottomStart = 0.dp,
        bottomEnd = 0.dp,
    )
    OverlayHost(visible = visible, onDismissRequest = onDismiss) { progress ->
        val eased = if (motion) progress else 1f
        Box(Modifier.fillMaxSize().imePadding()) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(canvas.scrim)
                    .graphicsLayer { alpha = eased }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
                    .semantics { contentDescription = "关闭浮层" },
            )
            Column(
                modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .graphicsLayer { translationY = (1f - eased) * 220.dp.toPx() + drag }
                    .then(KelaileElevation.soft(KelaileElevation.floating, shape, 0.05f, 0.12f))
                    .glassPanel(shape, canvas.fillElevated, canvas.strokeTop, canvas.strokeBottom)
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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(canvas.scrim)
                    .graphicsLayer { alpha = eased }
                    .then(
                        if (dismissOnOutside) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss,
                            )
                        } else Modifier,
                    ),
            )
            Column(
                modifier
                    .padding(horizontal = 26.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = eased
                        val s = 0.94f + 0.06f * eased
                        scaleX = s
                        scaleY = s
                    }
                    .then(KelaileElevation.soft(KelaileElevation.floating, shape, 0.06f, 0.14f))
                    .glassPanel(shape, canvas.fillElevated, canvas.strokeTop, canvas.strokeBottom)
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
                content?.let { Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = it) }
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
            Box(
                Modifier
                    .matchParentSize()
                    .background(canvas.scrim)
                    .graphicsLayer { alpha = eased },
            )
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
                    .glassPanel(shape, canvas.fillElevated, canvas.strokeTop, canvas.strokeBottom)
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

/** 全屏忙碌遮罩：玻璃卡片 + 进度环，取代原来的半透明黑幕。 */
@Composable
fun BoxScope.GlassBusyOverlay(visible: Boolean) {
    if (!visible) return
    val canvas = LocalKelaileCanvas.current
    val shape = RoundedCornerShape(KelaileShapes.card)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(KelaileMotion.quick)) }
    Box(
        Modifier
            .fillMaxSize()
            .background(canvas.scrim.copy(alpha = 0.35f * appear.value))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .graphicsLayer { alpha = appear.value },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .then(KelaileElevation.soft(KelaileElevation.floating, shape))
                .glassPanel(shape, canvas.fillElevated, canvas.strokeTop, canvas.strokeBottom)
                .padding(horizontal = 26.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                Modifier.size(30.dp),
                strokeWidth = 2.4.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("正在保存…", style = MaterialTheme.typography.labelMedium)
        }
    }
}
