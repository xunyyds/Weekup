package com.xunyyds.kelaile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.EaseOut
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.xunyyds.kelaile.ui.glass.DampedDragAnimation
import com.xunyyds.kelaile.ui.glass.InteractiveHighlight
import com.xunyyds.kelaile.ui.glass.rememberContentLayerBackdrop
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xunyyds.kelaile.ui.glass.glassPanel
import com.xunyyds.kelaile.ui.glass.glassSheen
import kotlin.math.sign
import kotlin.math.abs
import kotlin.math.roundToInt

private class SectionCursor { var next = 0 }

private val LocalSectionCursor = staticCompositionLocalOf<SectionCursor?> { null }

private class StaggerCursor { var next = 0 }

private val LocalStaggerCursor = staticCompositionLocalOf<StaggerCursor?> { null }

private const val StaggerRiseDp = 12f

@Composable
private fun Modifier.staggerEnter(delayMillis: Int): Modifier {
    val motion = kelaileMotionEnabled()
    val appear = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(motion) {
        if (motion) appear.animateTo(1f, tween(delayMillis, easing = KelaileMotion.decelerateEasing))
        else appear.snapTo(1f)
    }
    val rise = with(LocalDensity.current) { StaggerRiseDp.dp.toPx() }
    return this.graphicsLayer {
        alpha = appear.value
        translationY = (1f - appear.value) * rise
    }
}

/**
 * 分组玻璃卡片。
 *
 * 分类标题留在卡片外（iOS 设置页的分组语义），组内条目共用一张大圆角磨砂卡。
 * 组内相邻条目之间由 [SettingRow] 自己画出内缩的发丝分隔线，因此调用方
 * 仍然可以像以前那样直接列条目，不需要手工插分隔。
 */
@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val canvas = LocalKelaileCanvas.current
    val shape = KelaileShapes.rounded(KelaileShapes.card)
    val motion = kelaileMotionEnabled()
    val cursor = remember(motion) { if (motion) StaggerCursor() else null }
    val rows = remember { SectionCursor() }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KelaileSpacing.sm),
    ) {
        if (title.isNotBlank()) {
            Text(
                title,
                modifier = Modifier
                    .padding(horizontal = 18.dp)
                    .semantics { heading() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .then(KelaileElevation.soft(KelaileElevation.card, shape))
                .glassPanel(shape, canvas.fill, canvas.strokeTop, canvas.strokeBottom)
                .glassSheen(shape, Color.White),
        ) {
            CompositionLocalProvider(
                LocalStaggerCursor provides cursor,
                LocalSectionCursor provides rows,
            ) {
                Column(content = content)
            }
        }
    }
}

/** 组内发丝分隔线：只有非首行才画，并左右内缩到文字对齐的位置。 */
@Composable
private fun SectionDivider() {
    val canvas = LocalKelaileCanvas.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp)
            .height(1.dp)
            .background(canvas.divider),
    )
}

@Composable
private fun rememberRowSlot(): Pair<Int, Boolean> {
    val cursor = LocalSectionCursor.current
    val stagger = LocalStaggerCursor.current
    val isFirst = remember(cursor) {
        val index = cursor?.next ?: 0
        if (cursor != null) cursor.next = index + 1
        index == 0
    }
    val staggerSlot = remember(stagger) { stagger?.next?.also { stagger.next = it + 1 } ?: 0 }
    return staggerSlot to isFirst
}

/**
 * 自定义内容的分组行。
 *
 * 统一处理「非首行画分隔线」与「错峰入场」，让开关行、滑条行、颜色行
 * 与普通 [SettingRow] 在同一张玻璃卡里视觉一致。
 *
 * [alwaysDivider] 用于卡片里第一个条目也要分隔线的情况（例如自由输入框之后接的下拉行）。
 */
@Composable
internal fun SectionRow(alwaysDivider: Boolean = false, content: @Composable () -> Unit) {
    val (slot, isFirst) = rememberRowSlot()
    Column(Modifier.fillMaxWidth().staggerEnter(kelaileStaggerDelay(slot))) {
        if (alwaysDivider || !isFirst) SectionDivider()
        content()
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String = "",
    glyph: KelaileGlyphKind? = null,
    glyphTint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val motion = kelaileMotionEnabled()
    val (slot, isFirst) = rememberRowSlot()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val canvas = LocalKelaileCanvas.current
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = if (pressed) 0.10f else 0f)
    val tone by animateColorAsState(
        highlight,
        animationSpec = if (motion) tween(KelaileMotion.fast) else snap(),
        label = "Row tone",
    )
    Column(Modifier.fillMaxWidth().staggerEnter(kelaileStaggerDelay(slot))) {
        if (!isFirst) SectionDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(tone)
                .clickable(
                    interactionSource = source,
                    indication = null,
                    role = Role.Button,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
                .heightIn(min = if (subtitle.isBlank()) 54.dp else 70.dp)
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (glyph != null) {
                SettingIcon(glyph, glyphTint)
                Spacer(Modifier.width(14.dp))
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(KelaileSpacing.lg))
            Chevron(canvas.divider)
        }
    }
}

@Composable
internal fun Chevron(tint: Color) {
    Canvas(Modifier.size(7.dp, 12.dp)) {
        drawPath(
            Path().apply {
                moveTo(size.width * 0.18f, size.height * 0.14f)
                lineTo(size.width * 0.82f, size.height * 0.5f)
                lineTo(size.width * 0.18f, size.height * 0.86f)
            },
            tint,
            style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * 底栏入口文案。顺序即页面索引：0 课表、1 日程、2 备忘、3 设置。
 * 两个 Dock 实现共用这一份，图标由 [DockGlyph] 按同一索引取形。
 */
private val KelaileDockLabels = listOf("课表", "日程", "备忘", "设置")

@Composable
fun KelaileDock(
    style: String,
    index: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (style == "liquid") {
        LiquidDock(index = index, onSelect = onSelect, modifier = modifier)
    } else {
        PanelDock(style = style, index = index, onSelect = onSelect, modifier = modifier)
    }
}

/**
 * 液态玻璃底栏：折射页面内容采样层，拖拽滑块带速度挤压与色散。
 *
 * 材质与阻尼手感取自 kyant backdrop 官方 catalog（About 页已署名该库），
 * 并按 qfun 资源管理页的做法拆成「面板 + 隐形图标层 + 滑块」三层：
 * 图标层录进 tabsBackdrop，滑块折射 combined(内容, 图标层)，所以滑块经过时
 * 图标会被真实地「推鼓」，而不是叠一层纯色。不支持时退化为悬浮胶囊栏。
 */
@Composable
private fun LiquidDock(
    index: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 底栏四入口：0 课表 / 1 日程 / 2 备忘 / 3 设置（顺序与 DockGlyph 分支一一对应）
    val labels = KelaileDockLabels
    val selected = index.coerceIn(labels.indices)
    val canvas = LocalKelaileCanvas.current
    val colors = MaterialTheme.colorScheme
    val backdrop = rememberContentLayerBackdrop()
    if (backdrop == null || !isRuntimeShaderSupported()) {
        PanelDock(style = "floating", index = selected, onSelect = onSelect, modifier = modifier)
        return
    }
    val haptic = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val panelWidth = constraints.maxWidth.toFloat()
        val slotWidth = if (labels.isNotEmpty()) with(density) { (panelWidth - 8f.dp.toPx()) / labels.size } else 0f
        val scope = rememberCoroutineScope()
        var dragStartIndex by remember { mutableIntStateOf(selected) }
        var currentIndex by remember { mutableIntStateOf(selected) }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = if (panelWidth > 0f) (offsetAnimation.value / panelWidth).coerceIn(-1f, 1f) else 0f
                with(density) { 4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }

        val damped = remember(scope, labels.size, slotWidth) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = selected.toFloat(),
                valueRange = 0f..(labels.size - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {
                    dragStartIndex = value.roundToInt().coerceIn(labels.indices)
                },
                onDragStopped = {
                    val destination = targetValue.roundToInt().coerceIn(labels.indices)
                    currentIndex = destination
                    animateToValue(destination.toFloat())
                    if (destination != dragStartIndex) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(destination)
                    }
                    scope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, dragAmount ->
                    val step = if (slotWidth > 0f) dragAmount.x / slotWidth else 0f
                    updateValue(
                        (targetValue + if (rtl) -step else step)
                            .coerceIn(0f, (labels.size - 1).toFloat()),
                    )
                    scope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                },
            )
        }
        LaunchedEffect(selected, labels.size) {
            val newIndex = selected.coerceIn(labels.indices)
            if (currentIndex != newIndex) {
                currentIndex = newIndex
                damped.animateToValue(newIndex.toFloat())
            }
        }

        val highlight = remember(scope, slotWidth) {
            InteractiveHighlight(
                animationScope = scope,
                position = { size, _ ->
                    Offset(
                        if (rtl) size.width - (damped.value + 0.5f) * slotWidth + panelOffset
                        else (damped.value + 0.5f) * slotWidth + panelOffset,
                        size.height / 2f,
                    )
                },
            )
        }
        val panelShape = { RoundedCornerShape(KelaileShapes.dock) }
        val fill = canvas.fillElevated.copy(alpha = if (canvas.isDark) 0.42f else 0.34f)

        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = panelShape,
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        val scale = 1f + 0.05f * damped.pressProgress
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(fill) },
                )
                .then(highlight.modifier)
                .height(64f.dp)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockTabRow(labels, selected, onSelect, tinted = false)
        }

        CompositionLocalProvider(LocalLiquidIconScale provides { 1f + 0.2f * damped.pressProgress }) {
            Row(
                Modifier
                    .clearAndSetSemantics { }
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .height(56f.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(colors.primary)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DockTabRow(labels, selected, onSelect, tinted = true)
            }
        }

        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX = if (rtl) {
                        panelWidth - (damped.value + 1f) * slotWidth + panelOffset
                    } else {
                        damped.value * slotWidth + panelOffset
                    }
                }
                .then(highlight.gestureModifier)
                .then(damped.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = damped.pressProgress
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = true,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = damped.pressProgress) },
                    shadow = { Shadow(alpha = damped.pressProgress) },
                    innerShadow = {
                        InnerShadow(radius = 8f.dp * damped.pressProgress, alpha = damped.pressProgress)
                    },
                    layerBlock = {
                        val velocity = damped.velocity / 10f
                        scaleX = damped.scaleX / (1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f))
                        scaleY = damped.scaleY * (1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f))
                    },
                    onDrawSurface = {
                        drawRect(
                            if (canvas.isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f),
                            alpha = 1f - damped.pressProgress,
                        )
                    },
                )
                .height(56f.dp)
                .fillMaxWidth(1f / labels.size),
        )
    }
}

/**
 * 底栏标签内容：可见层用正常墨色；隐形层全用白色，再经主色 colorFilter 变成折射源。
 */
@Composable
private fun RowScope.DockTabRow(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    tinted: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val scale = LocalLiquidIconScale.current
    labels.forEachIndexed { item, title ->
        val ink = when {
            tinted -> Color.White
            item == selected -> colors.primary
            else -> colors.onSurfaceVariant
        }
        Column(
            Modifier
                .clip(Capsule())
                .clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Tab,
                    onClick = { if (item != selected) onSelect(item) },
                )
                .fillMaxHeight()
                .weight(1f)
                .graphicsLayer { scaleX = scale(); scaleY = scale() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            DockGlyph(item, ink)
            Text(
                title,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontWeight = if (item == selected) FontWeight.SemiBold else FontWeight.Normal,
                color = ink,
            )
        }
    }
}

private val LocalLiquidIconScale = staticCompositionLocalOf { { 1f } }

/**
 * 实体/悬浮玻璃底栏：贴底面板或离地胶囊，滑块只做位置移动。
 */
@Composable
private fun PanelDock(
    style: String,
    index: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = KelaileDockLabels
    val selected = index.coerceIn(labels.indices)
    val lastIndex = labels.lastIndex
    val solid = style == "default"
    val floating = !solid
    val colors = MaterialTheme.colorScheme
    val canvas = LocalKelaileCanvas.current
    val outerRadius = if (floating) KelaileShapes.dock else KelaileShapes.tiny
    val dockInset = 5.dp
    val shape: Shape = if (solid) {
        RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    } else {
        KelaileShapes.rounded(outerRadius)
    }

    val indicatorShape = KelaileShapes.rounded(KelaileShapes.nested(outerRadius, dockInset))
    val motion = kelaileMotionEnabled()
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(selected.toFloat()) }
    var dragStretch by remember { mutableStateOf(0f) }
    var measuredWidth by remember { mutableStateOf(0) }
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val settledPosition by animateFloatAsState(
        if (dragging) dragPosition else selected.toFloat(),
        animationSpec = if (motion && !dragging) spring(dampingRatio = 0.78f, stiffness = 360f) else snap(),
        label = "Dock indicator position",
    )
    // position 是「浮点索引」（0f..lastIndex），不是 0..1 的比例，槽宽换算交给上层
    val position = (if (dragging) dragPosition else settledPosition).coerceIn(0f, lastIndex.toFloat())
    // 拖拽挤压只在中间槽位最明显，所以需要归一到 0..1 再算
    val positionProgress = if (lastIndex > 0) (position / lastIndex).coerceIn(0f, 1f) else 0f
    val haptic = LocalHapticFeedback.current
    val base = if (solid) {
        Modifier.glassPanel(shape, canvas.fillElevated, canvas.strokeTop, canvas.strokeBottom, 0.dp)
    } else {
        Modifier
            .glassPanel(shape, canvas.fillElevated, canvas.strokeTop, canvas.strokeBottom)
            .glassSheen(shape, Color.White)
    }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            Modifier.widthIn(max = if (floating) 320.dp else Dp.Infinity)
                .fillMaxWidth()
                .then(
                    if (floating) {
                        KelaileElevation.soft(KelaileElevation.dock, shape, 0.06f, 0.12f)
                    } else Modifier,
                )
                .clip(shape).then(base).padding(if (floating) dockInset else 0.dp)
                .onSizeChanged { measuredWidth = it.width }
                .pointerInput(rtl, labels.size) {
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            // 槽宽按入口数量均分，不能写死成 /2f
                            val slotPx = measuredWidth.toFloat() / labels.size
                            if (slotPx > 0f) {
                                val physical = (start.x / slotPx - 0.5f).coerceIn(0f, lastIndex.toFloat())
                                dragPosition = if (rtl) lastIndex - physical else physical
                                dragging = true
                            }
                        },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            val slotPx = measuredWidth.toFloat() / labels.size
                            if (slotPx > 0f && dragging) {
                                val logicalDelta = delta / slotPx * if (rtl) -1f else 1f
                                dragPosition = (dragPosition + logicalDelta).coerceIn(0f, lastIndex.toFloat())
                                dragStretch = abs(logicalDelta).coerceIn(0f, 0.08f)
                            }
                        },
                        onDragEnd = {
                            val destination = dragPosition.roundToInt().coerceIn(0, lastIndex)
                            dragging = false
                            dragStretch = 0f
                            if (destination != currentSelected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnSelect(destination)
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            dragStretch = 0f
                        },
                    )
                }
                .selectableGroup(),
        ) {
            val slot = maxWidth / labels.size

            if (floating) {
                Box(Modifier.matchParentSize()) {
                    val indicatorMaterial = Modifier.glassPanel(
                        indicatorShape,
                        colors.primaryContainer.copy(alpha = if (canvas.isDark) .82f else .92f),
                        Color.White.copy(alpha = .40f),
                        Color.White.copy(alpha = .10f),
                    )
                    Box(
                        Modifier.offset(x = slot * position).width(slot).fillMaxSize()
                            .padding(KelaileSpacing.hairline)
                            .graphicsLayer {
                                // position 已是浮点索引，挤压强度按全栏归一化后的位置算
                                val stretch = if (motion && dragging) {
                                    dragStretch * (1f - abs(2f * positionProgress - 1f))
                                } else 0f
                                scaleX = 1f + stretch
                                scaleY = 1f - stretch * 0.35f
                            }
                            .clip(indicatorShape).then(indicatorMaterial),
                    )
                }
            }

            if (solid) Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(1.dp)
                    .background(canvas.strokeTop.copy(alpha = .8f)),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                labels.forEachIndexed { item, title ->
                    val source = remember { MutableInteractionSource() }
                    val pressed by source.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        if (pressed && motion) 0.94f else 1f,
                        animationSpec = if (motion) {
                            tween(KelaileMotion.fast, easing = KelaileMotion.emphasizedEasing)
                        } else snap(),
                        label = "Dock tab press",
                    )
                    val ink = when {
                        item != selected -> colors.onSurfaceVariant
                        solid -> colors.primary
                        else -> colors.onPrimaryContainer
                    }
                    Column(
                        Modifier.weight(1f).clip(KelaileShapes.rounded(KelaileShapes.card))
                            .selectable(
                                selected = item == selected,
                                interactionSource = source,
                                indication = null,
                                role = Role.Tab,
                                onClick = {
                                    if (item != selected) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelect(item)
                                    }
                                },
                            )
                            .heightIn(min = 58.dp)
                            .padding(horizontal = KelaileSpacing.xs, vertical = KelaileSpacing.sm)
                            .graphicsLayer { scaleX = scale; scaleY = scale },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(KelaileSpacing.xs),
                    ) {
                        DockGlyph(item, ink)
                        Text(
                            title,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontWeight = if (item == selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = ink,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 底栏手绘图标。索引与 [KelaileDockLabels] 一一对应：
 * 0 课表（笔记本 + 横线）、1 日程（日历 + 装订环）、2 备忘（折角便签）、3 设置（滑杆）。
 * 统一 24 网格、1.8u 描边、圆头圆角，保证四个图标观感一致。
 */
@Composable
private fun DockGlyph(index: Int, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val u = size.width / 24f
        val pen = Stroke(width = 1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (index) {
            0 -> {
                // 课表：竖版笔记本，内部三条横线当作课程行
                drawRoundRect(color, Offset(5f * u, 3f * u), Size(14f * u, 18f * u), CornerRadius(2f * u), style = pen)
                for (y in listOf(8f, 12f, 16f)) drawLine(
                    color, Offset(9f * u, y * u), Offset(15f * u, y * u), pen.width, StrokeCap.Round,
                )
            }
            1 -> {
                // 日程：日历外框 + 顶部两个装订环 + 一条日期分隔线
                drawRoundRect(color, Offset(3.5f * u, 5f * u), Size(17f * u, 15.5f * u), CornerRadius(2.5f * u), style = pen)
                drawLine(color, Offset(8.5f * u, 2.5f * u), Offset(8.5f * u, 5.5f * u), pen.width, StrokeCap.Round)
                drawLine(color, Offset(15.5f * u, 2.5f * u), Offset(15.5f * u, 5.5f * u), pen.width, StrokeCap.Round)
                drawLine(color, Offset(4f * u, 9.5f * u), Offset(20f * u, 9.5f * u), pen.width, StrokeCap.Round)
            }
            2 -> {
                // 备忘：纸张 + 右下折角 + 两条正文线
                val folded = Path().apply {
                    moveTo(5f * u, 3f * u)
                    lineTo(14.5f * u, 3f * u)
                    lineTo(19f * u, 7.5f * u)
                    lineTo(19f * u, 21f * u)
                    lineTo(5f * u, 21f * u)
                    close()
                }
                drawPath(folded, color, style = pen)
                drawPath(
                    Path().apply {
                        moveTo(14.5f * u, 3f * u)
                        lineTo(14.5f * u, 7.5f * u)
                        lineTo(19f * u, 7.5f * u)
                    },
                    color,
                    style = pen,
                )
                for (y in listOf(12f, 16f)) drawLine(
                    color, Offset(8f * u, y * u), Offset(16f * u, y * u), pen.width, StrokeCap.Round,
                )
            }
            else -> {
                // 设置：三条滑杆 + 圆点
                for ((y, x) in listOf(6f to 9f, 12f to 16f, 18f to 10f)) {
                    drawLine(color, Offset(3f * u, y * u), Offset(21f * u, y * u), pen.width, StrokeCap.Round)
                    drawCircle(color, 2.3f * u, Offset(x * u, y * u))
                }
            }
        }
    }
}

fun highlightMatches(
    text: String,
    terms: List<String>,
    highlight: SpanStyle,
): AnnotatedString {
    if (text.isEmpty() || terms.isEmpty()) return AnnotatedString(text)
    val ranges = mutableListOf<IntRange>()
    terms.forEach { term ->
        if (term.isEmpty()) return@forEach
        var from = 0
        while (from <= text.length - term.length) {
            val at = text.indexOf(term, from, ignoreCase = true)
            if (at < 0) break
            ranges.add(at until at + term.length)
            from = at + term.length
        }
    }
    if (ranges.isEmpty()) return AnnotatedString(text)

    val merged = mutableListOf<IntRange>()
    ranges.sortedBy { it.first }.forEach { range ->
        val last = merged.lastOrNull()
        if (last != null && range.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
        } else merged.add(range)
    }
    return buildAnnotatedString {
        append(text)
        merged.forEach { addStyle(highlight, it.first, it.last + 1) }
    }
}


enum class KelaileGlyphKind {
    Search, Close, Back,
    Palette, Monitor, Calendar, Book, Bell, Database, Info,
    Brush, Layout, Speed, Clock, Tag, Image, Link, Check, Plus,
    Code, Bug, Heart, Balance, Doc,
}

@Composable
fun KelaileGlyph(
    kind: KelaileGlyphKind,
    tint: Color = LocalContentColor.current,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val pen = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            KelaileGlyphKind.Search -> {
                drawCircle(tint, 6.4f * u, Offset(10.4f * u, 10.4f * u), style = pen)
                drawLine(tint, Offset(15.2f * u, 15.2f * u), Offset(20.2f * u, 20.2f * u), pen.width, StrokeCap.Round)
            }
            KelaileGlyphKind.Close -> {
                drawLine(tint, Offset(6.5f * u, 6.5f * u), Offset(17.5f * u, 17.5f * u), pen.width, StrokeCap.Round)
                drawLine(tint, Offset(17.5f * u, 6.5f * u), Offset(6.5f * u, 17.5f * u), pen.width, StrokeCap.Round)
            }
            KelaileGlyphKind.Back -> {
                drawLine(tint, Offset(14.5f * u, 4.5f * u), Offset(7f * u, 12f * u), pen.width, StrokeCap.Round)
                drawLine(tint, Offset(7f * u, 12f * u), Offset(14.5f * u, 19.5f * u), pen.width, StrokeCap.Round)
            }
            KelaileGlyphKind.Palette -> {
                drawCircle(tint, 8f * u, Offset(12f * u, 12f * u), style = pen)
                drawCircle(tint, 1.5f * u, Offset(9f * u, 9f * u))
                drawCircle(tint, 1.5f * u, Offset(15f * u, 9f * u))
                drawCircle(tint, 1.5f * u, Offset(9.5f * u, 15f * u))
            }
            KelaileGlyphKind.Monitor -> {
                drawRoundRect(tint, Offset(3.5f * u, 5f * u), Size(17f * u, 11f * u), CornerRadius(2f * u), style = pen)
                drawLine(tint, Offset(12f * u, 16f * u), Offset(12f * u, 20f * u), pen.width, StrokeCap.Round)
                drawLine(tint, Offset(8f * u, 20f * u), Offset(16f * u, 20f * u), pen.width, StrokeCap.Round)
            }
            KelaileGlyphKind.Calendar -> {
                drawRoundRect(tint, Offset(4f * u, 5.5f * u), Size(16f * u, 14f * u), CornerRadius(2f * u), style = pen)
                drawLine(tint, Offset(4f * u, 9.5f * u), Offset(20f * u, 9.5f * u), pen.width, StrokeCap.Round)
                drawLine(tint, Offset(8f * u, 3.5f * u), Offset(8f * u, 7f * u), pen.width, StrokeCap.Round)
                drawLine(tint, Offset(16f * u, 3.5f * u), Offset(16f * u, 7f * u), pen.width, StrokeCap.Round)
            }
            KelaileGlyphKind.Book -> {
                drawRoundRect(tint, Offset(5f * u, 4f * u), Size(14f * u, 16f * u), CornerRadius(1.5f * u), style = pen)
                drawLine(tint, Offset(9f * u, 4f * u), Offset(9f * u, 20f * u), pen.width, StrokeCap.Round)
            }
            KelaileGlyphKind.Bell -> {
                drawPath(
                    Path().apply {
                        moveTo(7f * u, 16f * u)
                        cubicTo(7f * u, 8f * u, 9f * u, 5f * u, 12f * u, 5f * u)
                        cubicTo(15f * u, 5f * u, 17f * u, 8f * u, 17f * u, 16f * u)
                        close()
                    },
                    tint, style = pen,
                )
                drawLine(tint, Offset(6f * u, 16f * u), Offset(18f * u, 16f * u), pen.width, StrokeCap.Round)
                drawCircle(tint, 1.6f * u, Offset(12f * u, 19.5f * u))
            }
            KelaileGlyphKind.Database -> {
                for (y in listOf(6f, 12f, 18f)) {
                    drawOval(tint, Offset(4f * u, (y - 2.2f) * u), Size(16f * u, 4.4f * u), style = pen)
                }
            }
            KelaileGlyphKind.Info -> {
                drawCircle(tint, 8f * u, Offset(12f * u, 12f * u), style = pen)
                drawLine(tint, Offset(12f * u, 11f * u), Offset(12f * u, 16f * u), pen.width, StrokeCap.Round)
                drawCircle(tint, 1.1f * u, Offset(12f * u, 8f * u))
            }
            KelaileGlyphKind.Brush -> {
                drawLine(tint, Offset(15f * u, 5f * u), Offset(9f * u, 11f * u), pen.width * 2.2f, StrokeCap.Round)
                drawPath(
                    Path().apply {
                        moveTo(9f * u, 11f * u)
                        cubicTo(6f * u, 14f * u, 6f * u, 18f * u, 5f * u, 19f * u)
                        cubicTo(8f * u, 19f * u, 10f * u, 17f * u, 11f * u, 13f * u)
                    },
                    tint, style = pen,
                )
            }
            KelaileGlyphKind.Layout -> {
                drawRoundRect(tint, Offset(4f * u, 4f * u), Size(16f * u, 16f * u), CornerRadius(2f * u), style = pen)
                drawLine(tint, Offset(4f * u, 10f * u), Offset(20f * u, 10f * u), pen.width, StrokeCap.Round)
                drawLine(tint, Offset(12f * u, 10f * u), Offset(12f * u, 20f * u), pen.width, StrokeCap.Round)
            }
            KelaileGlyphKind.Speed -> {
                drawArc(
                    tint, 150f, 240f, useCenter = false,
                    topLeft = Offset(4f * u, 5f * u), size = Size(16f * u, 16f * u),
                    style = pen,
                )
                drawLine(tint, Offset(12f * u, 13f * u), Offset(16f * u, 9f * u), pen.width, StrokeCap.Round)
            }
            KelaileGlyphKind.Clock -> {
                drawCircle(tint, 8f * u, Offset(12f * u, 12f * u), style = pen)
                drawLine(tint, Offset(12f * u, 12f * u), Offset(12f * u, 7.5f * u), pen.width, StrokeCap.Round)
                drawLine(tint, Offset(12f * u, 12f * u), Offset(15.5f * u, 13.5f * u), pen.width, StrokeCap.Round)
            }
            KelaileGlyphKind.Tag -> {
                drawPath(
                    Path().apply {
                        moveTo(4f * u, 4f * u); lineTo(12f * u, 4f * u); lineTo(20f * u, 12f * u)
                        lineTo(12f * u, 20f * u); lineTo(4f * u, 12f * u); close()
                    },
                    tint, style = pen,
                )
                drawCircle(tint, 1.4f * u, Offset(8f * u, 8f * u))
            }
            KelaileGlyphKind.Image -> {
                drawRoundRect(tint, Offset(4f * u, 5f * u), Size(16f * u, 14f * u), CornerRadius(2f * u), style = pen)
                drawCircle(tint, 1.8f * u, Offset(9f * u, 10f * u))
                drawPath(
                    Path().apply {
                        moveTo(5f * u, 18f * u); lineTo(10f * u, 13f * u); lineTo(14f * u, 16f * u); lineTo(17f * u, 13f * u); lineTo(19f * u, 15f * u)
                    },
                    tint, style = pen,
                )
            }
            KelaileGlyphKind.Link -> {
                drawLine(tint, Offset(9f * u, 15f * u), Offset(15f * u, 9f * u), pen.width, StrokeCap.Round)
                drawArc(tint, 90f, 180f, useCenter = false, topLeft = Offset(4f * u, 8f * u), size = Size(8f * u, 8f * u), style = pen)
                drawArc(tint, 270f, 180f, useCenter = false, topLeft = Offset(12f * u, 8f * u), size = Size(8f * u, 8f * u), style = pen)
            }
            KelaileGlyphKind.Check -> {
                drawLine(tint, Offset(5f * u, 12.5f * u), Offset(10f * u, 17.5f * u), pen.width * 1.3f, StrokeCap.Round)
                drawLine(tint, Offset(10f * u, 17.5f * u), Offset(19f * u, 6.5f * u), pen.width * 1.3f, StrokeCap.Round)
            }
            KelaileGlyphKind.Plus -> {
                drawLine(tint, Offset(12f * u, 5f * u), Offset(12f * u, 19f * u), pen.width, StrokeCap.Round)
                drawLine(tint, Offset(5f * u, 12f * u), Offset(19f * u, 12f * u), pen.width, StrokeCap.Round)
            }
            KelaileGlyphKind.Code -> {
                drawPath(
                    Path().apply {
                        moveTo(9f * u, 6f * u); lineTo(4f * u, 12f * u); lineTo(9f * u, 18f * u)
                    },
                    tint, style = pen,
                )
                drawPath(
                    Path().apply {
                        moveTo(15f * u, 6f * u); lineTo(20f * u, 12f * u); lineTo(15f * u, 18f * u)
                    },
                    tint, style = pen,
                )
            }
            KelaileGlyphKind.Bug -> {
                drawRoundRect(tint, Offset(7.5f * u, 8f * u), Size(9f * u, 11f * u), CornerRadius(4.5f * u), style = pen)
                drawLine(tint, Offset(9f * u, 6.5f * u), Offset(11f * u, 8f * u), pen.width, StrokeCap.Round)
                drawLine(tint, Offset(15f * u, 6.5f * u), Offset(13f * u, 8f * u), pen.width, StrokeCap.Round)
                drawLine(tint, Offset(7.5f * u, 12f * u), Offset(3.5f * u, 10.5f * u), pen.width, StrokeCap.Round)
                drawLine(tint, Offset(7.5f * u, 15f * u), Offset(4f * u, 16.5f * u), pen.width, StrokeCap.Round)
                drawLine(tint, Offset(16.5f * u, 12f * u), Offset(20.5f * u, 10.5f * u), pen.width, StrokeCap.Round)
                drawLine(tint, Offset(16.5f * u, 15f * u), Offset(20f * u, 16.5f * u), pen.width, StrokeCap.Round)
            }
            KelaileGlyphKind.Heart -> {
                drawPath(
                    Path().apply {
                        moveTo(12f * u, 19.5f * u)
                        cubicTo(4.5f * u, 14f * u, 4.5f * u, 8.5f * u, 8f * u, 6.5f * u)
                        cubicTo(10f * u, 5.4f * u, 12f * u, 7f * u, 12f * u, 8.6f * u)
                        cubicTo(12f * u, 7f * u, 14f * u, 5.4f * u, 16f * u, 6.5f * u)
                        cubicTo(19.5f * u, 8.5f * u, 19.5f * u, 14f * u, 12f * u, 19.5f * u)
                        close()
                    },
                    tint, style = pen,
                )
            }
            KelaileGlyphKind.Balance -> {
                drawLine(tint, Offset(12f * u, 4f * u), Offset(12f * u, 20f * u), pen.width, StrokeCap.Round)
                drawLine(tint, Offset(5f * u, 20f * u), Offset(19f * u, 20f * u), pen.width, StrokeCap.Round)
                drawLine(tint, Offset(4.5f * u, 7f * u), Offset(19.5f * u, 7f * u), pen.width, StrokeCap.Round)
                drawArc(
                    tint, 180f, 180f, useCenter = false,
                    topLeft = Offset(2.5f * u, 7f * u), size = Size(4.6f * u, 4.6f * u), style = pen,
                )
                drawArc(
                    tint, 180f, 180f, useCenter = false,
                    topLeft = Offset(16.9f * u, 7f * u), size = Size(4.6f * u, 4.6f * u), style = pen,
                )
            }
            KelaileGlyphKind.Doc -> {
                drawPath(
                    Path().apply {
                        moveTo(6f * u, 3.5f * u); lineTo(14f * u, 3.5f * u); lineTo(18f * u, 7.5f * u)
                        lineTo(18f * u, 20.5f * u); lineTo(6f * u, 20.5f * u); close()
                    },
                    tint, style = pen,
                )
                drawPath(
                    Path().apply {
                        moveTo(14f * u, 3.5f * u); lineTo(14f * u, 7.5f * u); lineTo(18f * u, 7.5f * u)
                    },
                    tint, style = pen,
                )
            }
        }
    }
}

/**
 * 条目左侧的圆角图标块：底色是 tint 的淡色，字形用 tint 本身，与图三那种分组列表一致。
 */
@Composable
fun SettingIcon(kind: KelaileGlyphKind, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) { KelaileGlyph(kind, tint, size = 20.dp) }
}

/**
 * 课程玻璃卡片。
 *
 * 底色是课程分类色与磨砂面板的混合，上面再压一层自上而下的白色高光和 1dp 描边，
 * 这样既保留了课程自己的色相，又不会变成一块不透明的纯色块。
 */
@Composable
fun GlassCourseCard(
    onClick: () -> Unit,
    tint: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 6.dp,
    contentAlignment: Alignment.Horizontal = Alignment.Start,
    contentVerticalArrangement: Alignment.Vertical = Alignment.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val motion = kelaileMotionEnabled()
    val canvas = LocalKelaileCanvas.current
    val scale by animateFloatAsState(
        if (pressed && motion) 0.968f else 1f,
        animationSpec = if (motion) {
            spring(dampingRatio = 0.72f, stiffness = 620f)
        } else snap(),
        label = "Course card press",
    )
    val haptic = LocalHapticFeedback.current
    val topSheen = if (canvas.isDark) 0.12f else 0.38f
    val topStroke = if (canvas.isDark) 0.26f else 0.72f
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(tint)
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = topSheen),
                    0.42f to Color.White.copy(alpha = topSheen * 0.12f),
                    1f to Color.Black.copy(alpha = if (canvas.isDark) 0.10f else 0.035f),
                ),
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = topStroke),
                    0.6f to Color.White.copy(alpha = 0.10f),
                    1f to Color.White.copy(alpha = 0.04f),
                ),
                shape,
            )
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
    ) {
        Column(
            Modifier.fillMaxSize().padding(contentPadding),
            horizontalAlignment = contentAlignment,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.aligned(contentVerticalArrangement),
            content = content,
        )
    }
}

/** 玻璃胶囊按钮，用于顶栏与卡片内的轻量动作。 */
@Composable
fun GlassPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    compact: Boolean = false,
    enabled: Boolean = true,
) {
    val canvas = LocalKelaileCanvas.current
    val shape = RoundedCornerShape(KelaileShapes.capsule)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .then(KelaileElevation.soft(4.dp, shape, 0.02f, 0.05f))
            .glassPanel(shape, canvas.fillElevated, canvas.strokeTop, canvas.strokeBottom)
            .background(tint.copy(alpha = when {
                !enabled -> .04f
                pressed -> .16f
                else -> .08f
            }))
            .clickable(
                enabled = enabled,
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = if (compact) 40.dp else 44.dp)
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 6.dp else 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) tint else tint.copy(alpha = .42f),
            maxLines = 1,
        )
    }
}

internal val GlassDividerTint: Color
    @Composable get() = LocalKelaileCanvas.current.divider

internal fun blended(base: Color, overlay: Color, amount: Float): Color = lerp(base, overlay, amount)

/**
 * 课程色转玻璃卡底色。
 *
 * 用户选的颜色当透光色用：不透明颜色按主题给定透明度（浅色更实、深色更透），
 * 已经手动调过透明度的颜色则原样尊重，只夹在可读区间内。
 */
internal fun courseCardTint(raw: Color, dark: Boolean): Color {
    val alpha = if (raw.alpha >= 0.999f) {
        if (dark) 0.34f else 0.46f
    } else {
        raw.alpha.coerceIn(0.12f, 0.92f)
    }
    val rgb = raw.copy(alpha = 1f)
    val shifted = if (dark) lerp(rgb, Color.White, 0.06f) else lerp(rgb, Color.Black, 0.05f)
    return shifted.copy(alpha = alpha)
}

/** 在给定玻璃底色上选一组能读清楚的文字色。 */
internal fun courseCardInk(tint: Color, dark: Boolean): Color {
    val under = if (dark) Color(0xFF111823) else Color(0xFFF6F9FD)
    val composed = tint.compositeOver(under)
    return if (composed.luminance() > 0.56f) Color(0xFF15211B) else Color.White
}
