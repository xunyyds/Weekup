package com.xunyyds.kelaile.ui.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
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
import kotlin.math.ceil

private class GlassBackdrop(val layer: GraphicsLayer) {
    var coordinates: LayoutCoordinates? by mutableStateOf(null)
    var ready by mutableStateOf(false)
}

private val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

   
                                                                                            
                                                                                                  
                                                                              
   
@Composable
fun GlassHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val backdrop = remember(layer) { GlassBackdrop(layer) }
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
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

   
                                                                                             
                                                                                                   
                                                                                               
   


@Composable
internal fun Modifier.softGlass(
    radiusDp: Float,
    tint: Color,
    fallback: Color,
    shape: Shape = RoundedCornerShape(radiusDp.dp),
): Modifier = this
    .clip(shape)
    .background(fallback)
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(tint.copy(alpha = tint.alpha + 0.18f), tint.copy(alpha = 0f)),
        ),
        shape = shape,
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
                layer.record(size = IntSize(
                    ceil(size.width + 2 * margin).toInt(),
                    ceil(size.height + 2 * margin).toInt(),
                )) {
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
            android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content"), blur,
        ).asComposeRenderEffect()
        cachedKey = key
        cached = effect
        return effect
    }
}







private const val BlurRadius = 16f
private const val DepthRatio = 0.34f
private const val RefractionRatio = 0.20f

private const val LensShader = """
uniform shader content;
uniform float2 size;
uniform float margin;
uniform float radius;
uniform float depth;
uniform float amount;
half4 main(float2 coord) {
    float2 p = coord - float2(margin) - size * 0.5;
    float2 q = abs(p) - (size * 0.5 - float2(radius));
    float sd = length(max(q, 0.0)) - radius + min(max(q.x, q.y), 0.0);
    float2 outside = max(q, 0.0);
    float2 grad;
    if (length(outside) > 0.001) grad = sign(p) * normalize(outside);
    else grad = sign(p) * (q.x > q.y ? float2(1.0, 0.0) : float2(0.0, 1.0));
    float edge = clamp(1.0 + min(sd, 0.0) / depth, 0.0, 1.0);
    float bend = (1.0 - sqrt(max(0.0, 1.0 - edge * edge))) * amount;
    half4 color = content.eval(coord - grad * bend);
    float rim = (1.0 - smoothstep(0.0, 1.9, abs(sd))) *
        (0.26 + 0.62 * pow(max(dot(grad, float2(-0.707, -0.707)), 0.0), 2.0));
    return half4(color.rgb + half3(rim) * color.a, color.a);
}
"""
