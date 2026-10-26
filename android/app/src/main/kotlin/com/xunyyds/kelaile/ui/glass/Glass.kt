package com.xunyyds.kelaile.ui.glass

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xunyyds.kelaile.ui.KelaileBlob
import com.xunyyds.kelaile.ui.LocalKelaileCanvas
import kotlin.math.ceil

internal class GlassBackdrop(val layer: GraphicsLayer) {
    var coordinates: LayoutCoordinates? by mutableStateOf(null)
    var ready by mutableStateOf(false)
}

internal val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/**
 * 页面内容采样层（壁纸 + 正文，不含悬浮件）。
 *
 * 液态底栏要折射的是「正在滚动的课表」，不只是静态壁纸；
 * 由 [GlassHost] 提供、[contentSource] 挂载、底栏用 drawBackdrop 消费。
 */
private val LocalContentLayerBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** 把自身及其子树的绘制录进内容采样层。 */
@Composable
fun Modifier.contentSource(): Modifier {
    val layer = LocalContentLayerBackdrop.current ?: return this
    return layerBackdrop(layer)
}

/**
 * 背景墙纸。
 *
 * 纵向三段渐变打底，再叠三团大半径径向渐变当作「散景光斑」。
 * 深色模式下光斑同样存在，只是透明度更低，用来托住玻璃卡片的层次。
 */
@Composable
fun KelaileBackdrop(modifier: Modifier = Modifier) {
    val canvas = LocalKelaileCanvas.current
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        drawRect(
            brush = Brush.verticalGradient(
                0f to canvas.top,
                0.46f to canvas.middle,
                1f to canvas.bottom,
            ),
            size = Size(w, h),
        )
        canvas.blobs.forEach { blob: KelaileBlob ->
            val radius = h * blob.radius.coerceAtLeast(0.1f)
            val center = Offset(w * blob.centerX, h * blob.centerY)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(blob.color, blob.color.copy(alpha = 0f)),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}

@Composable
fun GlassHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val backdrop = remember(layer) { GlassBackdrop(layer) }
    val contentLayer = rememberLayerBackdrop()
    CompositionLocalProvider(
        LocalGlassBackdrop provides backdrop,
        LocalContentLayerBackdrop provides contentLayer,
    ) {
        Box(modifier) { content() }
    }
}

@Composable
fun Modifier.glassSource(): Modifier {
    val backdrop = LocalGlassBackdrop.current ?: return this
    return onGloballyPositioned { backdrop.coordinates = it }.drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        backdrop.ready = true
        drawLayer(backdrop.layer)
    }
}

/**
 * 毛玻璃面板。
 *
 * 只做三件事：半透明填充、自上而下的高光描边、顶部一层极淡的内侧提亮。
 * 真正的背景模糊交给 [liquidGlass]（Android 13+ 的镜头材质）；这里用半透明 + 描边
 * 表达「玻璃压住壁纸」的观感，成本为零，低版本观感也不会塌。
 */
internal fun Modifier.glassPanel(
    shape: Shape,
    fill: Color,
    strokeTop: Color,
    strokeBottom: Color,
    strokeWidth: androidx.compose.ui.unit.Dp = 1.dp,
): Modifier = this
    .clip(shape)
    .background(fill)
    .border(
        width = strokeWidth,
        brush = Brush.verticalGradient(
            0f to strokeTop,
            0.5f to strokeTop.copy(alpha = strokeTop.alpha * 0.45f),
            1f to strokeBottom,
        ),
        shape = shape,
    )

internal fun Modifier.glassSheen(shape: Shape, tint: Color): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            0f to tint.copy(alpha = 0.10f),
            0.28f to tint.copy(alpha = 0.02f),
            1f to Color.Transparent,
        ),
    )

@Composable
internal fun Modifier.liquidGlass(radiusDp: Float, tint: Color, fallback: Color): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val layer = rememberGraphicsLayer()
    var coordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
    val lens = remember {
        if (Build.VERSION.SDK_INT >= 33) runCatching { AndroidLens() }.getOrNull() else null
    }
    return onGloballyPositioned { coordinates = it }.drawWithContent {
        val source = backdrop?.coordinates
        val target = coordinates
        val sampled = if (Build.VERSION.SDK_INT >= 33 && lens != null &&
            backdrop != null && backdrop.ready && source?.isAttached == true &&
            target?.isAttached == true && drawContext.canvas.nativeCanvas.isHardwareAccelerated
        ) {
            runCatching {
                val offset = source.localPositionOf(target)
                val margin = 24.dp.toPx()
                layer.renderEffect = lens.effect(size.width, size.height, radiusDp.dp.toPx(), margin)
                layer.record(
                    size = IntSize(
                        ceil(size.width + 2 * margin).toInt(),
                        ceil(size.height + 2 * margin).toInt(),
                    ),
                ) {
                    translate(margin - offset.x, margin - offset.y) { drawLayer(backdrop.layer) }
                }
                translate(-margin, -margin) { drawLayer(layer) }
                true
            }.getOrDefault(false)
        } else false
        drawRect(if (sampled) tint else fallback)
        drawContent()
    }
}

/** 内容采样层（壁纸 + 正文，不含悬浮件）；液态底栏从它折射真实页面。 */
@Composable
fun rememberContentLayerBackdrop(): LayerBackdrop? = LocalContentLayerBackdrop.current

@androidx.annotation.RequiresApi(33)
private class AndroidLens {
    private val shader = android.graphics.RuntimeShader(LensShader)

    private var cachedKey: Long = Long.MIN_VALUE
    private var cached: androidx.compose.ui.graphics.RenderEffect? = null

    fun effect(width: Float, height: Float, radius: Float, margin: Float): androidx.compose.ui.graphics.RenderEffect {
        val key = (((width * 4).toLong() and 0xFFFF) shl 48) or
            (((height * 4).toLong() and 0xFFFF) shl 32) or
            (((radius * 4).toLong() and 0xFFFF) shl 16) or
            ((margin * 4).toLong() and 0xFFFF)
        cached?.let { if (key == cachedKey) return it }

        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("margin", margin)
        shader.setFloatUniform("radius", radius.coerceAtMost(height / 2f))
        shader.setFloatUniform("depth", (height * DepthRatio).coerceAtLeast(1f))
        shader.setFloatUniform("amount", height * RefractionRatio)
        val blur = android.graphics.RenderEffect.createBlurEffect(
            BlurRadius,
            BlurRadius,
            android.graphics.Shader.TileMode.CLAMP,
        )
        val effect = android.graphics.RenderEffect.createChainEffect(
            android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content"),
            blur,
        ).asComposeRenderEffect()
        cachedKey = key
        cached = effect
        return effect
    }
}

private const val BlurRadius = 20f

private const val DepthRatio = 0.30f

private const val RefractionRatio = 0.14f

private const val LensShader = """
uniform shader content;
uniform float2 size;
uniform float margin;
uniform float radius;
uniform float depth;
uniform float amount;
half4 main(float2 coord) {
    float2 p = coord - float2(margin) - size * 0.5;
    float2 halfBox = size * 0.5;
    float2 q = abs(p) - (halfBox - float2(radius));
    float sd = length(max(q, 0.0)) - radius + min(max(q.x, q.y), 0.0);
    float2 outside = max(q, 0.0);
    float2 grad;
    if (length(outside) > 0.001) grad = sign(p) * normalize(outside);
    else grad = sign(p) * (q.x > q.y ? float2(1.0, 0.0) : float2(0.0, 1.0));
    float edge = clamp(1.0 + min(sd, 0.0) / depth, 0.0, 1.0);
    float bend = (1.0 - sqrt(max(0.0, 1.0 - edge * edge))) * amount;
    half4 color = content.eval(coord - grad * bend);
    float rim = (1.0 - smoothstep(0.0, 2.2, abs(sd))) *
        (0.30 + 0.55 * pow(max(dot(grad, float2(-0.707, -0.707)), 0.0), 2.0));
    return half4(color.rgb + half3(rim) * color.a, color.a);
}
"""
