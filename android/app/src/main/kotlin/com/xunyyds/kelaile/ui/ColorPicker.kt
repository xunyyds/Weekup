package com.xunyyds.kelaile.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

private fun argbLabel(color: Long): String = String.format(Locale.ROOT, "#%08X", color and 0xffffffffL)
private fun parseArgb(text: String): Long? =
    if (Regex("^#[0-9a-fA-F]{8}$").matches(text)) text.substring(1).toLongOrNull(16) else null

@Composable
fun ColorPickerDialog(
    initialColor: Long,
    onDismiss: () -> Unit,
    visible: Boolean = true,
    onConfirm: (Long) -> Unit,
) {
    if (!visible) return
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColor.toInt(), it) }
    }
    var hue by rememberSaveable(initialColor) { mutableStateOf(initialHsv[0]) }
    var saturation by rememberSaveable(initialColor) { mutableStateOf(initialHsv[1]) }
    var brightness by rememberSaveable(initialColor) { mutableStateOf(initialHsv[2]) }
    var alpha by rememberSaveable(initialColor) { mutableStateOf(((initialColor ushr 24) and 255).toFloat() / 255f) }
    var text by rememberSaveable(initialColor) { mutableStateOf(argbLabel(initialColor)) }
    val computedColor = AndroidColor.HSVToColor((alpha * 255).roundToInt(), floatArrayOf(hue, saturation, brightness)).toLong() and 0xffffffffL
    val valid = parseArgb(text)
    val color = valid ?: computedColor
    fun syncText() { text = argbLabel(AndroidColor.HSVToColor((alpha * 255).roundToInt(), floatArrayOf(hue, saturation, brightness)).toLong()) }
    fun select(value: Long) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(value.toInt(), hsv)
        hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
        alpha = ((value ushr 24) and 255).toFloat() / 255f
    }
    GlassDialogScaffold(
            onDismiss = onDismiss,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "课程颜色",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                    Text(
                        "半透明颜色会与玻璃底叠加",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(KelaileSpacing.md),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KelaileSpacing.md)) {
                        ColorSwatch(color, Modifier.size(54.dp).clip(KelaileShapes.rounded(KelaileShapes.small)))
                        Column {
                            Text(argbLabel(color), style = MaterialTheme.typography.titleMedium)
                            Text(
                                "棋盘表示透明区域",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val hueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                    Canvas(
                        Modifier.fillMaxWidth().height(132.dp)
                            .clip(KelaileShapes.rounded(KelaileShapes.small))
                            .semantics { contentDescription = "饱和度与亮度调色盘；也可使用下方滑条精确调整" }
                            .pointerInput(hue) {
                                detectTapGestures { point ->
                                    saturation = (point.x / size.width).coerceIn(0f, 1f)
                                    brightness = (1f - point.y / size.height).coerceIn(0f, 1f)
                                    syncText()
                                }
                            }
                            .pointerInput(hue) {
                                detectDragGestures(onDragStart = { point ->
                                    saturation = (point.x / size.width).coerceIn(0f, 1f)
                                    brightness = (1f - point.y / size.height).coerceIn(0f, 1f)
                                    syncText()
                                }) { change, _ ->
                                    change.consume()
                                    saturation = (change.position.x / size.width).coerceIn(0f, 1f)
                                    brightness = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                                    syncText()
                                }
                            },
                    ) {
                        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        val point = Offset(saturation * size.width, (1f - brightness) * size.height)
                        drawCircle(Color.Black, 7.dp.toPx(), point)
                        drawCircle(Color.White, 5.dp.toPx(), point)
                        drawCircle(Color(color.toInt()), 3.dp.toPx(), point)
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(KelaileShapes.rounded(KelaileShapes.tiny))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
                                ),
                            ),
                    )
                    ColorChannelSlider("色相", hue, 0f..360f, "${hue.roundToInt()}°") { hue = it; syncText() }
                    ColorChannelSlider("饱和度", saturation, 0f..1f, "${(saturation * 100).roundToInt()}%") { saturation = it; syncText() }
                    ColorChannelSlider("亮度", brightness, 0f..1f, "${(brightness * 100).roundToInt()}%") { brightness = it; syncText() }
                    ColorChannelSlider("不透明度", alpha, 0f..1f, "${(alpha * 100).roundToInt()}%") { alpha = it; syncText() }
                    Text("预设颜色", style = MaterialTheme.typography.labelLarge)
                    listOf(
                        0xffd9e9deL, 0xffe2e5f5L, 0xfff1e4d3L, 0xffe3eaf2L, 0xfff3dce4L, 0xffd6ebeaL,
                        0xff6d8dceL, 0xff8a68a5L, 0xffcf6b71L, 0xffe5a34dL, 0xff528d73L, 0x00000000L,
                    ).chunked(6).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KelaileSpacing.sm)) {
                            row.forEach { preset ->
                                ColorSwatch(
                                    preset,
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(KelaileShapes.rounded(KelaileShapes.xs))
                                        .border(
                                            if (color == preset) 3.dp else 1.dp,
                                            if (color == preset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            KelaileShapes.rounded(KelaileShapes.xs),
                                        )
                                        .clickable { select(preset); text = argbLabel(preset) }
                                        .semantics { contentDescription = "预设颜色 ${argbLabel(preset)}" },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { input -> text = input; parseArgb(input)?.let { select(it) } },
                        label = { Text("#AARRGGBB") },
                        supportingText = {
                            Text(if (valid == null) "请输入 # 加 8 位十六进制：AA 透明度，RR 红，GG 绿，BB 蓝" else "有效 ARGB · 已实时更新预览")
                        },
                        isError = valid == null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                GlassPillButton(
                    "使用此颜色",
                    onClick = { parseArgb(text)?.let(onConfirm) },
                    tint = if (valid != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ColorChannelSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(display, style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
    }
}

@Composable
internal fun ColorSwatch(color: Long, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cell = 8.dp.toPx()
        val columns = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        for (y in 0 until rows) for (x in 0 until columns) {
            drawRect(if ((x + y) % 2 == 0) Color(0xffeeeeee) else Color(0xffc7c7c7),
                topLeft = Offset(x * cell, y * cell), size = Size(cell, cell))
        }
        drawRect(Color(color.toInt()))
    }
}
