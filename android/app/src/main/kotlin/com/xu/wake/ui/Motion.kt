package com.xu.wake.ui

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect

// ---------------------------------------------------------------------------------------------
// Design tokens
//
// OriginOS-flavoured layering: three corner tiers (24 / 28 / 32) that always nest inward by a
// fixed inset, an 8dp spacing scale with 4dp half-steps, and soft low-alpha shadows instead of
// the hard default elevation. Every visual constant in the UI layer should reference these.
// ---------------------------------------------------------------------------------------------

object XwakeShapes {
    /** Hairline rounding: chips, swatches, progress tracks. */
    val hairline = 2.dp
    val tiny = 4.dp
    val xs = 8.dp
    val small = 12.dp
    val medium = 16.dp
    /** Primary card tier (Section surface, PressCard, tab pill). */
    val card = 24.dp
    /** Sheet / dialog / large container tier. */
    val sheet = 28.dp
    /** Floating dock tier. */
    val dock = 32.dp

    /** Corner radius of a surface nested inside [radius] with [inset] padding. */
    fun nested(radius: Dp, inset: Dp): Dp = (radius - inset).coerceAtLeast(tiny)

    fun rounded(radius: Dp): Shape = RoundedCornerShape(radius)
}

object XwakeSpacing {
    val hairline = 2.dp
    val xs = 4.dp
    /** Base unit of the spacing scale. */
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
}

object XwakeElevation {
    val card = 4.dp
    val floating = 10.dp
    val dock = 14.dp

    /**
     * Soft, deliberately faint shadow. Android's default elevation shadow is opaque and hard
     * edged; tinting the ambient/spot colours down to single-digit alpha keeps the depth cue
     * without the "sticker" look.
     */
    fun soft(
        elevation: Dp,
        shape: Shape,
        ambientAlpha: Float = 0.045f,
        spotAlpha: Float = 0.085f,
    ): Modifier = Modifier.shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = ambientAlpha),
        spotColor = Color.Black.copy(alpha = spotAlpha),
    )
}

object XwakeMotion {
    /** Durations in milliseconds, before the user's motion-speed multiplier. */
    const val fast = 120
    const val quick = 180
    const val standard = 260
    const val emphasized = 320
    const val slow = 420

    /** Stagger entrance: per-item delay and the point where the cascade stops growing. */
    const val staggerStep = 32
    const val staggerMax = 8
    /** Vertical rise of a staggered list item, in dp. */
    const val staggerRise = 10

    /** Material 3 standard easing. */
    val standardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Material 3 emphasized-decelerate: the expressive "arrive fast, settle slow" curve. */
    val emphasizedEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** Material 3 emphasized-accelerate, for exits. */
    val accelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val legacyEasing = FastOutSlowInEasing
}

private val LocalXwakeMotionEnabled = staticCompositionLocalOf<Boolean?> { null }
private val LocalMotionSpeed = staticCompositionLocalOf { 1.0 }
private val LocalTransitionStyle = staticCompositionLocalOf { "shared" }

/** Scales a base duration by the user's motion-speed setting. */
@Composable
internal fun xwakeMillis(base: Int): Int =
    (base / LocalMotionSpeed.current).toInt().coerceAtLeast(1)

/** Entry delay for the item at [index] in a staggered list. */
@Composable
internal fun xwakeStaggerDelay(index: Int): Int =
    xwakeMillis(XwakeMotion.staggerStep * index.coerceIn(0, XwakeMotion.staggerMax))

/**
 * Tracks whether the platform currently allows animation. Both the system animator scale and the
 * Compose [MotionDurationScale] must be non-zero.
 */
@Composable
private fun systemMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun readScale(): Float = runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    var globalScale by remember(resolver) { mutableStateOf(readScale()) }
    var composeScale by remember { mutableStateOf(1f) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { globalScale = readScale() }
        }
        val registered = runCatching {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer,
            )
        }.isSuccess
        globalScale = readScale()
        onDispose { if (registered) resolver.unregisterContentObserver(observer) }
    }
    LaunchedEffect(Unit) {
        val durationScale = currentCoroutineContext()[MotionDurationScale]
        snapshotFlow { durationScale?.scaleFactor ?: 1f }.collect { composeScale = it }
    }
    return globalScale > 0f && composeScale > 0f
}

@Composable
internal fun xwakeMotionEnabled(): Boolean =
    LocalXwakeMotionEnabled.current ?: systemMotionEnabled()

// ---------------------------------------------------------------------------------------------
// Theme
// ---------------------------------------------------------------------------------------------

/** Neutral surfaces, an explicit tonal ladder, and hue-matched outlines for both schemes. */
private fun darkScheme() = darkColorScheme(
    primary = Color(0xFF91B7FF),
    onPrimary = Color(0xFF092D61),
    primaryContainer = Color(0xFF203D65),
    onPrimaryContainer = Color(0xFFD8E6FF),
    background = Color(0xFF101216),
    onBackground = Color(0xFFE9EBF0),
    surface = Color(0xFF101216),
    onSurface = Color(0xFFE9EBF0),
    onSurfaceVariant = Color(0xFFB4BAC6),
    surfaceContainerLowest = Color(0xFF0B0D10),
    surfaceContainerLow = Color(0xFF171A1F),
    surfaceContainer = Color(0xFF1D2026),
    surfaceContainerHigh = Color(0xFF262A31),
    surfaceContainerHighest = Color(0xFF31353D),
    outline = Color(0xFF6E7684),
    outlineVariant = Color(0xFF3F4653),
)

private fun lightScheme() = lightColorScheme(
    primary = Color(0xFF2869D8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1EBFF),
    onPrimaryContainer = Color(0xFF17427F),
    background = Color(0xFFF3F5F8),
    onBackground = Color(0xFF20242B),
    surface = Color(0xFFF3F5F8),
    onSurface = Color(0xFF20242B),
    onSurfaceVariant = Color(0xFF5A6472),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFBFD),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFE9EDF4),
    surfaceContainerHighest = Color(0xFFDFE4EC),
    outline = Color(0xFFA7AEBB),
    outlineVariant = Color(0xFFDCE1EA),
)

@Composable
fun XwakeTheme(
    theme: String = "system",
    fontScale: Float = 1f,
    palette: String = "blue",
    dynamicColor: Boolean = false,
    motionSpeed: Double = 1.0,
    reduceMotion: Boolean = false,
    transitionStyle: String = "shared",
    content: @Composable () -> Unit,
) {
    val dark = when (theme.lowercase()) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val scheme = if (dark) darkScheme() else lightScheme()
    val rawSeed = when (palette) {
        "green" -> Color(0xFF477A69)
        "purple" -> Color(0xFF7954A1)
        "orange" -> Color(0xFF9C6128)
        "rose" -> Color(0xFFAD4B70)
        "teal" -> Color(0xFF167F86)
        else -> Color(0xFF2869D8)
    }
    // Light-mode seeds are pulled 12% toward black: every palette then clears 4.5:1 for both
    // white-on-primary fill and primary-as-body-text on the tonal surfaces.
    val seed = if (dark) rawSeed else lerp(rawSeed, Color.Black, .12f)
    val effectiveScheme = if (dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else scheme.copy(
        primary = if (dark) lerp(seed, Color.White, .5f) else seed,
        onPrimary = if (dark) Color(0xFF101216) else Color.White,
        primaryContainer = lerp(seed, if (dark) Color(0xFF15171B) else Color.White, if (dark) .62f else .86f),
        onPrimaryContainer = if (dark) lerp(seed, Color.White, .82f) else lerp(seed, Color.Black, .38f),
        secondary = if (dark) lerp(seed, Color.White, .64f) else seed,
        secondaryContainer = lerp(seed, if (dark) Color(0xFF202329) else Color.White, .82f),
        onSecondaryContainer = if (dark) Color.White else lerp(seed, Color.Black, .45f),
    )
    val density = LocalDensity.current
    val multiplier = if (fontScale.isFinite()) fontScale.coerceIn(0.5f, 2f) else 1f
    val enabled = systemMotionEnabled()
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * multiplier),
        LocalXwakeMotionEnabled provides (enabled && !reduceMotion),
        LocalMotionSpeed provides motionSpeed.coerceIn(.5, 2.0),
        LocalTransitionStyle provides transitionStyle,
    ) {
        MaterialTheme(colorScheme = effectiveScheme, typography = Typography(), content = content)
    }
}

// ---------------------------------------------------------------------------------------------
// Page transitions
//
// Three styles keep their semantic: "standard" = full shared-axis travel, "shared" = the tight
// default used between top-level pages, "fade" = cross dissolve without displacement. Only the
// curves and durations changed: M3 emphasized-decelerate on entry, emphasized-accelerate on exit.
// ---------------------------------------------------------------------------------------------

@Composable
fun MotionContent(
    target: String,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit,
) {
    val enabled = xwakeMotionEnabled()
    val style = LocalTransitionStyle.current
    val enterMillis = xwakeMillis(if (style == "fade") XwakeMotion.quick else XwakeMotion.emphasized)
    val exitMillis = xwakeMillis(XwakeMotion.quick)
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        contentKey = { it },
        transitionSpec = {
            when {
                !enabled -> (EnterTransition.None togetherWith ExitTransition.None).using(null)
                style == "fade" -> (
                    fadeIn(tween(enterMillis, easing = XwakeMotion.standardEasing)) togetherWith
                        fadeOut(tween(exitMillis, easing = XwakeMotion.accelerateEasing))
                    ).using(null)
                else -> {
                    val direction = if (targetState >= initialState) 1 else -1
                    val travel = if (style == "standard") .12f else .06f
                    /* The incoming page fades in faster than the outgoing one fades out, so the two
                       layers are never both mid-opacity for long: that overlap is what read as a
                       ghost/double image. Exit is deliberately shorter than enter for the same
                       reason, and the incoming layer clips its own bounds so a sliding page cannot
                       paint outside the frame while the swap is in flight. */
                    val enter = slideInHorizontally(
                        tween(enterMillis, easing = XwakeMotion.emphasizedEasing),
                    ) { (it * travel * direction).toInt() } +
                        fadeIn(tween(enterMillis / 3, easing = XwakeMotion.standardEasing))
                    val exit = slideOutHorizontally(
                        tween(exitMillis, easing = XwakeMotion.accelerateEasing),
                    ) { (-it * travel * direction).toInt() } +
                        fadeOut(tween(exitMillis / 2, easing = XwakeMotion.accelerateEasing))
                    (enter togetherWith exit).using(
                        SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> snap() }),
                    )
                }
            }
        },
        label = "Xwake shared axis",
    ) { destination -> content(destination) }
}
