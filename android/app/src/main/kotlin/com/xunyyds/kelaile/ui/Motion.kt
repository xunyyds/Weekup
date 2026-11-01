package com.xunyyds.kelaile.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

object KelaileMotion {

    const val fast = 120

    const val quick = 190

    const val standard = 280

    const val emphasized = 340

    const val slow = 460

    const val reduced = 80

    const val staggerStep = 34
    const val staggerMax = 9

    const val staggerRise = 12

    const val sheetEnterDp = 160f

    const val pressScale = 0.96f

    val standardEasing = CubicBezierEasing(0.22f, 0f, 0f, 1f)

    val emphasizedEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    val decelerateEasing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    val accelerateEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    val legacyEasing = FastOutSlowInEasing

    fun <T> settle(): androidx.compose.animation.core.FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = 350f)

    fun <T> gentle(): androidx.compose.animation.core.FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow)

    fun <T> press(): androidx.compose.animation.core.FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.7f, stiffness = 600f)
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
    var scale by remember(resolver) { mutableStateOf(readScale()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { scale = readScale() }
        }
        val registered = runCatching {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer,
            )
        }.isSuccess
        scale = readScale()
        onDispose { if (registered) resolver.unregisterContentObserver(observer) }
    }
    // 动画器缩放为 0 时系统会直接跳过 Compose 动画，这里同步降级为短淡入
    return scale > 0f
}

@Composable
internal fun kelaileMotionEnabled(): Boolean =
    LocalKelaileMotionEnabled.current ?: systemMotionEnabled()

/**
 * 系统省电模式监听：开启省电模式时返回 true。
 * PowerManager.isPowerSaveMode 在状态变化时无广播可直接观察，
 * 通过 ACTION_POWER_SAVE_MODE_CHANGED 注册广播来感知。
 */
@Composable
internal fun powerSaveModeActive(): Boolean {
    val context = LocalContext.current
    val powerManager = remember(context) {
        context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
    }
    var active by remember(context) { mutableStateOf(powerManager?.isPowerSaveMode ?: false) }
    DisposableEffect(context) {
        if (android.os.Build.VERSION.SDK_INT < 21) return@DisposableEffect onDispose { }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) {
                active = powerManager?.isPowerSaveMode ?: false
            }
        }
        val registered = runCatching {
            context.registerReceiver(
                receiver,
                android.content.IntentFilter("android.os.action.ACTION_POWER_SAVE_MODE_CHANGED"),
            )
        }.isSuccess
        active = powerManager?.isPowerSaveMode ?: false
        onDispose { if (registered) runCatching { context.unregisterReceiver(receiver) } }
    }
    return active
}

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
    val reducedMillis = kelaileMillis(KelaileMotion.reduced)
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        contentKey = { it },
        transitionSpec = {
            when {
                // 减少动画不硬切，只留一次极短淡入淡出，保留「换页了」的状态感知
                !enabled -> (
                    fadeIn(tween(reducedMillis, easing = KelaileMotion.decelerateEasing)) togetherWith
                        fadeOut(tween(reducedMillis, easing = KelaileMotion.accelerateEasing))
                    ).using(null)
                style == "fade" -> (
                    fadeIn(tween(enterMillis, easing = KelaileMotion.decelerateEasing)) togetherWith
                        fadeOut(tween(exitMillis, easing = KelaileMotion.accelerateEasing))
                    ).using(null)
                else -> {
                    val direction = navigationDirection(initialState, targetState)
                    val travel = if (style == "standard") .10f else .045f
                    // 只动位移与透明度：两者都能走合成层，不再叠一层几乎看不出的缩放
                    val enter = slideInHorizontally(
                        tween(enterMillis, easing = KelaileMotion.emphasizedEasing),
                    ) { (it * travel * direction).toInt() } +
                        fadeIn(tween(enterMillis / 2, easing = KelaileMotion.decelerateEasing))
                    val exit = slideOutHorizontally(
                        tween(exitMillis, easing = KelaileMotion.accelerateEasing),
                    ) { (-it * travel * .7f * direction).toInt() } +
                        fadeOut(tween(exitMillis, easing = KelaileMotion.accelerateEasing))
                    (enter togetherWith exit).using(
                        SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> snap() }),
                    )
                }
            }
        },
        label = "Kelaile shared axis",
    ) { destination -> content(destination) }
}

/**
 * 转场方向按页面层级而不是字符串字典序决定：进入下一层右滑入，返回上一层左滑出。
 * tab 之间仍按索引差判断，保证和底栏顺序一致。
 */
private fun navigationDirection(from: String, to: String): Int {
    if (from.startsWith("tab") && to.startsWith("tab")) {
        val a = from.removePrefix("tab").toIntOrNull() ?: 0
        val b = to.removePrefix("tab").toIntOrNull() ?: 0
        return if (b >= a) 1 else -1
    }
    return if (routeDepth(to) >= routeDepth(from)) 1 else -1
}

private fun routeDepth(route: String): Int = when (route) {
    "home", "root" -> 0
    "course", "periods", "browser",
    "appearance", "display", "semester", "courses", "reminders", "data", "about" -> 1
    else -> 2
}
