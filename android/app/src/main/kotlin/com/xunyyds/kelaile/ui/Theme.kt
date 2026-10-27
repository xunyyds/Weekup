package com.xunyyds.kelaile.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

object KelaileShapes {

    val hairline = 2.dp

    val tiny = 6.dp

    val xs = 10.dp

    val small = 14.dp

    val medium = 18.dp

    val card = 26.dp

    val sheet = 34.dp

    val dock = 38.dp

    val capsule = 999.dp

    fun nested(radius: Dp, inset: Dp): Dp = (radius - inset).coerceAtLeast(tiny)

    fun rounded(radius: Dp) = RoundedCornerShape(radius)
}

object KelaileSpacing {
    val hairline = 2.dp
    val xs = 4.dp

    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp

    val xl = 22.dp

    val xxl = 28.dp

    val xxxl = 36.dp
}

object KelaileElevation {

    val card = 6.dp

    val floating = 14.dp

    val dock = 20.dp

    fun soft(
        elevation: Dp,
        shape: androidx.compose.ui.graphics.Shape,
        ambientAlpha: Float = 0.05f,
        spotAlpha: Float = 0.11f,
    ): androidx.compose.ui.Modifier = Modifier.shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = ambientAlpha),
        spotColor = Color.Black.copy(alpha = spotAlpha),
    )
}

data class KelaileBlob(
    val color: Color,
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
)

data class KelaileCanvas(
    val top: Color,
    val middle: Color,
    val bottom: Color,
    val blobs: List<KelaileBlob>,
    val fill: Color,
    val fillElevated: Color,
    val fillSunken: Color,
    val sheetFill: Color,
    val strokeTop: Color,
    val strokeBottom: Color,
    val divider: Color,
    val scrim: Color,
    val isDark: Boolean,
)

internal val LocalDialogBlur = staticCompositionLocalOf { true }

internal val LocalKelaileCanvas = staticCompositionLocalOf {
    KelaileCanvas(
        top = Color(0xFFE7F0FF),
        middle = Color(0xFFF4F1FB),
        bottom = Color(0xFFFFF4E9),
        blobs = emptyList(),
        fill = Color.White.copy(alpha = 0.62f),
        fillElevated = Color.White.copy(alpha = 0.74f),
        fillSunken = Color(0xFF12161F).copy(alpha = 0.05f),
        sheetFill = Color(0xFFF5F7FB),
        strokeTop = Color.White.copy(alpha = 0.92f),
        strokeBottom = Color.White.copy(alpha = 0.28f),
        divider = Color(0xFF1B2230).copy(alpha = 0.07f),
        scrim = Color(0xFF0B1220).copy(alpha = 0.22f),
        isDark = false,
    )
}

private object GradientStops {
    val light = mapOf(
        "blue" to Triple(Color(0xFFE7F0FF), Color(0xFFF4F1FB), Color(0xFFFFF4E9)),
        "green" to Triple(Color(0xFFE6F2EB), Color(0xFFF2F3F8), Color(0xFFFFF6E7)),
        "purple" to Triple(Color(0xFFEDEAFE), Color(0xFFF5EFFA), Color(0xFFFFF3EA)),
        "orange" to Triple(Color(0xFFFFF0E2), Color(0xFFFAF2EC), Color(0xFFFFF8E6)),
        "rose" to Triple(Color(0xFFFFEAF1), Color(0xFFF7EEF6), Color(0xFFFFF4EB)),
        "teal" to Triple(Color(0xFFE1F3F5), Color(0xFFEFF2F7), Color(0xFFFFF5E8)),
    )

    val dark = mapOf(
        "blue" to Triple(Color(0xFF091120), Color(0xFF101A2C), Color(0xFF191426)),
        "green" to Triple(Color(0xFF0A1512), Color(0xFF101A1D), Color(0xFF181722)),
        "purple" to Triple(Color(0xFF0D0E1E), Color(0xFF141227), Color(0xFF1B1424)),
        "orange" to Triple(Color(0xFF191210), Color(0xFF161321), Color(0xFF101725)),
        "rose" to Triple(Color(0xFF190F16), Color(0xFF16111E), Color(0xFF101725)),
        "teal" to Triple(Color(0xFF071518), Color(0xFF0E1922), Color(0xFF161420)),
    )
}

internal fun kelaileCanvas(palette: String, dark: Boolean, accent: Color): KelaileCanvas {
    val table = if (dark) GradientStops.dark else GradientStops.light
    val stops = table[palette] ?: table.getValue("blue")
    val wash = if (dark) 0.10f else 0.06f
    val top = lerp(stops.first, accent, wash)
    val bottom = lerp(stops.third, accent, wash * 0.5f)
    val blobs = if (dark) {
        listOf(
            KelaileBlob(accent.copy(alpha = 0.16f), 0.16f, 0.06f, 0.78f),
            KelaileBlob(Color(0xFF2A2B5E).copy(alpha = 0.34f), 0.92f, 0.84f, 0.66f),
            KelaileBlob(accent.copy(alpha = 0.10f), 0.58f, 0.98f, 0.72f),
        )
    } else {
        listOf(
            KelaileBlob(accent.copy(alpha = 0.14f), 0.14f, 0.05f, 0.86f),
            KelaileBlob(Color(0xFFFFD9A6).copy(alpha = 0.40f), 0.94f, 0.80f, 0.74f),
            KelaileBlob(Color.White.copy(alpha = 0.55f), 0.52f, 0.62f, 0.92f),
        )
    }
    return if (dark) {
        KelaileCanvas(
            top = top,
            middle = stops.second,
            bottom = bottom,
            blobs = blobs,
            fill = Color.White.copy(alpha = 0.055f),
            fillElevated = Color.White.copy(alpha = 0.09f),
            fillSunken = Color.Black.copy(alpha = 0.18f),
            sheetFill = Color(0xFF151B26),
            strokeTop = Color.White.copy(alpha = 0.16f),
            strokeBottom = Color.White.copy(alpha = 0.03f),
            divider = Color.White.copy(alpha = 0.08f),
            scrim = Color.Black.copy(alpha = 0.46f),
            isDark = true,
        )
    } else {
        KelaileCanvas(
            top = top,
            middle = stops.second,
            bottom = bottom,
            blobs = blobs,
            fill = Color.White.copy(alpha = 0.62f),
            fillElevated = Color.White.copy(alpha = 0.74f),
            fillSunken = Color(0xFF12161F).copy(alpha = 0.05f),
            sheetFill = Color(0xFFF5F7FB),
            strokeTop = Color.White.copy(alpha = 0.95f),
            strokeBottom = Color.White.copy(alpha = 0.30f),
            divider = Color(0xFF1B2230).copy(alpha = 0.07f),
            scrim = Color(0xFF0B1220).copy(alpha = 0.22f),
            isDark = false,
        )
    }
}

private fun darkScheme() = darkColorScheme(
    primary = Color(0xFF8FB6FF),
    onPrimary = Color(0xFF06101F),
    primaryContainer = Color(0xFF21344F),
    onPrimaryContainer = Color(0xFFD9E6FF),
    secondary = Color(0xFFA9B6CC),
    onSecondary = Color(0xFF101722),
    secondaryContainer = Color(0xFF27313F),
    onSecondaryContainer = Color(0xFFE2E8F4),
    background = Color(0xFF0B0F16),
    onBackground = Color(0xFFEDF0F6),
    surface = Color(0xFF10151D),
    onSurface = Color(0xFFEDF0F6),
    surfaceVariant = Color(0xFF1C222C),
    onSurfaceVariant = Color(0xFFA9B2C2),
    surfaceContainerLowest = Color(0xFF080B11),
    surfaceContainerLow = Color(0xFF141A23),
    surfaceContainer = Color(0xFF171D27),
    surfaceContainerHigh = Color(0xFF202733),
    surfaceContainerHighest = Color(0xFF2A3240),
    outline = Color(0xFF6C7787),
    outlineVariant = Color(0xFF333B49),
)

private fun lightScheme() = lightColorScheme(
    primary = Color(0xFF2C6BEE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2ECFF),
    onPrimaryContainer = Color(0xFF123B8C),
    secondary = Color(0xFF5C6675),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9EDF4),
    onSecondaryContainer = Color(0xFF2B3442),
    background = Color(0xFFF2F5FA),
    onBackground = Color(0xFF161B24),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF161B24),
    surfaceVariant = Color(0xFFEDF1F7),
    onSurfaceVariant = Color(0xFF5C6675),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFCFE),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFF3F9),
    surfaceContainerHighest = Color(0xFFE7ECF4),
    outline = Color(0xFFB9C1CE),
    outlineVariant = Color(0xFFDFE5EE),
)

private fun kelaileTypography(): Typography {
    val base = Typography()
    return base.copy(
        displaySmall = base.displaySmall.copy(
            fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp,
        ),
        headlineMedium = base.headlineMedium.copy(
            fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp,
        ),
        headlineSmall = base.headlineSmall.copy(
            fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp,
        ),
        titleLarge = base.titleLarge.copy(
            fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp,
        ),
        titleMedium = base.titleMedium.copy(
            fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp,
        ),
        titleSmall = base.titleSmall.copy(
            fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
        ),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
        bodySmall = base.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
        labelLarge = base.labelLarge.copy(
            fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp,
        ),
        labelMedium = base.labelMedium.copy(
            fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp,
        ),
        labelSmall = base.labelSmall.copy(fontSize = 10.5.sp, lineHeight = 14.sp, letterSpacing = 0.2.sp),
    )
}

private val kelaileMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun KelaileTheme(
    theme: String = "system",
    fontScale: Float = 1f,
    palette: String = "blue",
    dynamicColor: Boolean = false,
    motionSpeed: Double = 1.0,
    reduceMotion: Boolean = false,
    transitionStyle: String = "shared",
    dialogBlur: Boolean = true,
    powerSaverMotion: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (theme.lowercase()) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val baseScheme = if (dark) darkScheme() else lightScheme()
    val rawSeed = when (palette) {
        "green" -> Color(0xFF2F7D5D)
        "purple" -> Color(0xFF6B4FC4)
        "orange" -> Color(0xFFC2701E)
        "rose" -> Color(0xFFC2406E)
        "teal" -> Color(0xFF137D86)
        else -> Color(0xFF2C6BEE)
    }
    val seed = if (dark) lerp(rawSeed, Color.White, .58f) else rawSeed
    val effectiveScheme = if (dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        baseScheme.copy(
            primary = seed,
            onPrimary = if (dark) Color(0xFF06101F) else Color.White,
            primaryContainer = lerp(seed, if (dark) Color(0xFF15171B) else Color.White, if (dark) .66f else .86f),
            onPrimaryContainer = if (dark) lerp(seed, Color.White, .84f) else lerp(seed, Color.Black, .40f),
            secondary = if (dark) lerp(seed, Color.White, .62f) else lerp(seed, Color.Black, .30f),
            secondaryContainer = lerp(seed, if (dark) Color(0xFF202329) else Color.White, .84f),
            onSecondaryContainer = if (dark) Color.White else lerp(seed, Color.Black, .42f),
        )
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val multiplier = if (fontScale.isFinite()) fontScale.coerceIn(0.5f, 2f) else 1f
    val systemMotion = systemMotionEnabled()
    val powerSaver = powerSaveModeActive()
    val enabled = systemMotion && (!powerSaver || !powerSaverMotion)
    val canvas = remember(palette, dark, effectiveScheme.primary, dynamicColor) {
        kelaileCanvas(palette, dark, effectiveScheme.primary)
    }
    val view = LocalView.current
    val window = remember(context) { context.kelaileHostActivity()?.window }
    DisposableEffect(window, dark) {
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
        onDispose { }
    }
    CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides
            androidx.compose.ui.unit.Density(density.density, density.fontScale * multiplier),
        LocalKelaileMotionEnabled provides (enabled && !reduceMotion),
        LocalMotionSpeed provides motionSpeed.coerceIn(.5, 2.0),
        LocalTransitionStyle provides transitionStyle,
        LocalDialogBlur provides dialogBlur,
        LocalKelaileCanvas provides canvas,
    ) {
        MaterialTheme(
            colorScheme = effectiveScheme,
            typography = kelaileTypography(),
            shapes = kelaileMaterialShapes,
            content = content,
        )
    }
}

internal fun Context.kelaileHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.kelaileHostActivity() else null
    else -> null
}

/**
 * 依据课表背景图的整体亮度决定状态栏字色：图亮用深字、图暗用浅字，
 * 没有背景图时跟随主题明暗。亮度用降采样抽样求平均，避免整图解码。
 */
internal fun statusBarInkDarkFor(backgroundPath: String?, darkTheme: Boolean): Boolean {
    if (backgroundPath.isNullOrBlank()) return darkTheme
    val file = java.io.File(backgroundPath)
    if (!file.isFile || file.length() > 24L * 1024 * 1024) return darkTheme
    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = 24 }
    val bitmap = runCatching { android.graphics.BitmapFactory.decodeFile(file.absolutePath, options) }
        .getOrNull() ?: return darkTheme
    val luminance = runCatching {
        val step = maxOf(1, bitmap.width / 32)
        var total = 0f
        var count = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val c = bitmap.getPixel(x, y)
                total += 0.2126f * android.graphics.Color.red(c) +
                    0.7152f * android.graphics.Color.green(c) +
                    0.0722f * android.graphics.Color.blue(c)
                count++
                x += step
            }
            y += step
        }
        if (count == 0) 0f else total / count
    }.getOrDefault(0f)
    bitmap.recycle()
    return luminance < 128f
}
