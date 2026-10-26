package com.xunyyds.kelaile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xunyyds.kelaile.ui.glass.KelaileBackdrop
import com.xunyyds.kelaile.ui.glass.glassPanel
import com.xunyyds.kelaile.ui.glass.glassSheen
import kotlinx.coroutines.delay

/**
 * 启动页。
 *
 * 底色沿用全局渐变壁纸，中间是一块玻璃卡：图标 + 名称 + 一句副标题，
 * 底部的进度点表现为三颗呼吸的圆点，而不是文字省略号。
 */
@Composable
fun KelaileSplash(visible: Boolean, modifier: Modifier = Modifier) {
    val motion = kelaileMotionEnabled()
    val canvas = LocalKelaileCanvas.current
    var drawn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (motion) delay(80)
        drawn = true
    }
    val entrance by animateFloatAsState(
        if (drawn) 1f else .84f,
        animationSpec = if (motion) {
            tween(kelaileMillis(KelaileMotion.slow), easing = KelaileMotion.emphasizedEasing)
        } else tween(0),
        label = "Splash entrance",
    )
    val alpha by animateFloatAsState(
        if (drawn) 1f else 0f,
        animationSpec = if (motion) tween(kelaileMillis(KelaileMotion.emphasized)) else tween(0),
        label = "Splash alpha",
    )
    val ink = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.onPrimaryContainer
    val shape = RoundedCornerShape(KelaileShapes.sheet)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (motion) fadeIn(tween(200)) else fadeIn(tween(0)),
        exit = if (motion) fadeOut(tween(320)) + scaleOut(tween(320), targetScale = 1.04f) else fadeOut(tween(0)),
    ) {
        Box(Modifier.fillMaxSize()) {
            KelaileBackdrop(Modifier.matchParentSize())
            Column(
                Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = entrance
                        scaleY = entrance
                        this.alpha = alpha
                    }
                    .padding(horizontal = 40.dp)
                    .then(KelaileElevation.soft(KelaileElevation.floating, shape, 0.05f, 0.12f))
                    .glassPanel(shape, canvas.fillElevated, canvas.strokeTop, canvas.strokeBottom)
                    .glassSheen(shape, Color.White)
                    .padding(horizontal = 34.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(KelaileSpacing.lg),
            ) {
                Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                    val pulse by rememberInfiniteTransition(label = "Splash pulse").animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
                        label = "Splash pulse value",
                    )
                    Canvas(Modifier.fillMaxSize()) {
                        val unit = size.minDimension / 24f
                        val pen = Stroke(width = 1.7f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(
                                    ink.copy(alpha = if (motion) .14f + .10f * pulse else .16f),
                                    ink.copy(alpha = .05f),
                                ),
                            ),
                            topLeft = Offset(1f * unit, 1f * unit),
                            size = Size(22f * unit, 22f * unit),
                            cornerRadius = CornerRadius(7.4f * unit),
                        )
                        drawRoundRect(
                            color = ink,
                            topLeft = Offset(4f * unit, 5.2f * unit),
                            size = Size(16f * unit, 15f * unit),
                            cornerRadius = CornerRadius(3.4f * unit),
                            style = pen,
                        )
                        drawLine(ink, Offset(4f * unit, 9.6f * unit), Offset(20f * unit, 9.6f * unit), pen.width, StrokeCap.Round)
                        drawLine(ink, Offset(8.6f * unit, 3.2f * unit), Offset(8.6f * unit, 6.4f * unit), pen.width, StrokeCap.Round)
                        drawLine(ink, Offset(15.4f * unit, 3.2f * unit), Offset(15.4f * unit, 6.4f * unit), pen.width, StrokeCap.Round)
                        val tick = Path().apply {
                            moveTo(8.2f * unit, 14.2f * unit)
                            lineTo(10.9f * unit, 16.9f * unit)
                            lineTo(15.8f * unit, 11.6f * unit)
                        }
                        drawPath(tick, accent, style = Stroke(width = 2.1f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(KelaileSpacing.xs),
                ) {
                    Text(
                        "课来了",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "每一周，都从容一点",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(2.dp))
                SplashDots(ink, motion)
            }
        }
    }
}

@Composable
private fun SplashDots(tint: Color, motion: Boolean) {
    if (!motion) {
        RowDots(tint, 1f)
        return
    }
    val wave by rememberInfiniteTransition(label = "Splash dots").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "Splash dot wave",
    )
    RowDots(tint, wave)
}

@Composable
private fun RowDots(tint: Color, phase: Float) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            val offset = ((phase + index * 0.22f) % 1f)
            val amp = 0.35f + 0.65f * (1f - kotlin.math.abs(offset * 2f - 1f))
            Box(
                Modifier
                    .size(5.dp)
                    .clip(RoundedCornerShape(KelaileShapes.capsule))
                    .graphicsLayer { alpha = amp }
                    .background(tint),
            )
        }
    }
}
