package com.xunyyds.kelaile.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xunyyds.kelaile.ui.glass.glassPanel

@Composable
fun CheckMark(
    selected: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 18.dp,
) {
    val progress by animateFloatAsState(
        if (selected) 1f else 0f,
        animationSpec = if (kelaileMotionEnabled()) {
            tween(kelaileMillis(KelaileMotion.quick), easing = KelaileMotion.emphasizedEasing)
        } else snap(),
        label = "Check mark",
    )
    Canvas(modifier.size(size).semantics { contentDescription = if (selected) "已选中" else "未选中" }) {
        if (progress <= 0.01f) return@Canvas
        val unit = this.size.width / 18f
        val first = Offset(3.6f * unit, 9.4f * unit)
        val corner = Offset(7.2f * unit, 13.1f * unit)
        val last = Offset(14.6f * unit, 5.3f * unit)
        val path = Path().apply {
            moveTo(first.x, first.y)
            if (progress <= 0.5f) {
                val head = (corner - first) * (progress / 0.5f) + first
                lineTo(head.x, head.y)
            } else {
                lineTo(corner.x, corner.y)
                val tail = (last - corner) * ((progress - 0.5f) / 0.5f) + corner
                lineTo(tail.x, tail.y)
            }
        }
        drawPath(path, color, style = Stroke(width = 2.2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun CheckRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String = "",
    showDivider: Boolean = false,
) {
    val canvas = LocalKelaileCanvas.current
    Column(Modifier.fillMaxWidth()) {
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(1.dp)
                    .background(canvas.divider),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KelaileSpacing.hairline)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(KelaileSpacing.md))
            CheckMark(selected)
        }
    }
}

/**
 * 选项面板：玻璃底部卡片 + 勾选行。
 *
 * 与设置页的 [Section] 共用圆角与描边体系，避免出现 Material 与 iOS 两种材质并存。
 */
@Composable
fun ChoiceSheet(
    title: String,
    value: String,
    options: Map<String, String>,
    visible: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val canvas = LocalKelaileCanvas.current
    GlassSheet(
        visible = visible,
        onDismiss = onDismiss,
        title = title,
    ) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(KelaileShapes.rounded(KelaileShapes.card))
                    .glassPanel(
                        KelaileShapes.rounded(KelaileShapes.card),
                        canvas.fill,
                        canvas.strokeTop,
                        canvas.strokeBottom,
                    ),
            ) {
                options.entries.forEachIndexed { index, (key, label) ->
                    CheckRow(
                        title = label,
                        selected = key == value,
                        showDivider = index > 0,
                        onClick = { onSelect(key) },
                    )
                }
            }
        }
    }
}
