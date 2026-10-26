package com.xunyyds.kelaile.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect

object KelaileMotion {

    const val fast = 120

    const val quick = 190

    const val standard = 280

    const val emphasized = 340

    const val slow = 460

    const val staggerStep = 34
    const val staggerMax = 9

    const val staggerRise = 12

    val standardEasing = CubicBezierEasing(0.22f, 0f, 0f, 1f)

    val emphasizedEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    val decelerateEasing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    val accelerateEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    val legacyEasing = FastOutSlowInEasing

    fun <T> settle(): androidx.compose.animation.core.FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.82f, stiffness = 380f)

    fun <T> gentle(): androidx.compose.animation.core.FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow)
}

internal val LocalKelaileMotionEnabled = staticCompositionLocalOf<Boolean?> { null }
internal val LocalMotionSpeed = staticCompositionLocalOf { 1.0 }
internal val LocalTransitionStyle = staticCompositionLocalOf { "shared" }

@Composable
internal fun kelaileMillis(base: Int): Int =
    (base / LocalMotionSpeed.current).toInt().coerceAtLeast(1)

@Composable
internal fun kelaileStaggerDelay(index: Int): Int =
    kelaileMillis(KelaileMotion.staggerStep * index.coerceIn(0, KelaileMotion.staggerMax))

@Composable
internal fun systemMotionEnabled(): Boolean {
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
internal fun kelaileMotionEnabled(): Boolean =
    LocalKelaileMotionEnabled.current ?: systemMotionEnabled()

@Composable
fun MotionContent(
    target: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    content: @Composable (String) -> Unit,
) {
    val enabled = kelaileMotionEnabled()
    val style = LocalTransitionStyle.current
    val enterMillis = kelaileMillis(if (style == "fade") KelaileMotion.quick else KelaileMotion.emphasized)
    val exitMillis = kelaileMillis(KelaileMotion.quick)
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        contentKey = { it },
        transitionSpec = {
            when {
                !enabled -> (EnterTransition.None togetherWith ExitTransition.None).using(null)
                style == "fade" -> (
                    fadeIn(tween(enterMillis, easing = KelaileMotion.decelerateEasing)) togetherWith
                        fadeOut(tween(exitMillis, easing = KelaileMotion.accelerateEasing))
                    ).using(null)
                else -> {
                    val direction = if (targetState >= initialState) 1 else -1
                    val travel = if (style == "standard") .10f else .045f
                    val enter = slideInHorizontally(
                        tween(enterMillis, easing = KelaileMotion.emphasizedEasing),
                    ) { (it * travel * direction).toInt() } +
                        fadeIn(tween(enterMillis / 2, easing = KelaileMotion.decelerateEasing)) +
                        scaleIn(tween(enterMillis, easing = KelaileMotion.emphasizedEasing), initialScale = .985f)
                    val exit = slideOutHorizontally(
                        tween(exitMillis, easing = KelaileMotion.accelerateEasing),
                    ) { (-it * travel * .7f * direction).toInt() } +
                        fadeOut(tween(exitMillis / 2, easing = KelaileMotion.accelerateEasing)) +
                        scaleOut(tween(exitMillis, easing = KelaileMotion.accelerateEasing), targetScale = .992f)
                    (enter togetherWith exit).using(
                        SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> snap() }),
                    )
                }
            }
        },
        label = "Kelaile shared axis",
    ) { destination -> content(destination) }
}
