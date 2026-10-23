package com.xunyyds.kelaile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

/** 课表网格允许的最小缩放：1f = 一周的所有天刚好铺满可视宽度。 */
internal const val GRID_MIN_ZOOM = 1f

/**
 * 最大缩放。3f 意味着横向最多只看到约 2.3 天，
 * 再放大列宽超过屏幕、单张卡片本身也没有更多信息可看。
 */
internal const val GRID_MAX_ZOOM = 3f

/** 缩放档位的公比：1.22 倍一档，任何一次捏合都能跨过去。 */
internal const val GRID_ZOOM_STEP = 1.22f

/** 列宽 : 行高。行高跟随列宽变化，保证格子始终接近正方形。 */
internal const val GRID_ROW_ASPECT = 1.02f

/** 越界拖拽的阻尼系数：手指拖 100px，内容只走 32px，松手后回弹。 */
internal const val GRID_RUBBER = 0.32f

/** 低于这个速度（px/s）不再触发惯性滑动。 */
internal const val GRID_FLING_MIN = 80f

/** 指数衰减的摩擦系数（与 Compose 默认 fling 的 4.9 一致），单位 1/s。 */
internal const val GRID_FLING_FRICTION = 4.9f

/**
 * 网格的几何信息。所有尺寸都以像素表示，缩放只影响 [cellWidth] / [cellHeight]。
 *
 * 以前这些量是「每次重组时算出来的局部 val」，手势闭包按 pointerInput 的 key 缓存，
 * 于是缩放之后手势里读到的还是旧的 cellW / maxX / maxY —— 放大后拖不到边缘、
 * 缩小后拖过头，都是这个原因。现在统一放进 [GridLayout]，手势要用时现算。
 */
internal data class GridLayout(
    val dayCount: Int,
    val periodCount: Int,
    val availW: Float,
    val availH: Float,
    val gutterPx: Float,
    val headerPx: Float,
) {
    fun cellWidth(zoom: Float): Float = availW / dayCount.coerceAtLeast(1) * zoom

    fun cellHeight(zoom: Float): Float = cellWidth(zoom) * GRID_ROW_ASPECT

    fun contentWidth(zoom: Float): Float = cellWidth(zoom) * dayCount.coerceAtLeast(1)

    fun contentHeight(zoom: Float): Float = cellHeight(zoom) * periodCount.coerceAtLeast(1)

    fun maxOffsetX(zoom: Float): Float = (contentWidth(zoom) - availW).coerceAtLeast(0f)

    fun maxOffsetY(zoom: Float): Float = (contentHeight(zoom) - availH).coerceAtLeast(0f)

    /** 屏幕坐标 → 网格列号（用于拖动排课的落点判定）。 */
    fun columnAt(x: Float, offsetX: Float, zoom: Float): Int {
        val cell = cellWidth(zoom)
        if (cell <= 0f) return 0
        return ((x - gutterPx + offsetX) / cell).toInt().coerceIn(0, dayCount - 1)
    }

    fun rowAt(y: Float, offsetY: Float, zoom: Float): Int {
        val cell = cellHeight(zoom)
        if (cell <= 0f) return 0
        return ((y - headerPx + offsetY) / cell).toInt().coerceIn(0, periodCount - 1)
    }
}

/**
 * 手势与界面共享的可观察状态。用 snapshot state 保存，
 * 这样「手势协程写、组合读」不需要任何额外的重组触发。
 */
internal class TimetableGridState(initialZoom: Float = GRID_MIN_ZOOM) {
    var zoom by mutableFloatStateOf(initialZoom.coerceIn(GRID_MIN_ZOOM, GRID_MAX_ZOOM))
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)

    /** 把手势中途可能留下的越界 / 超范围值收敛回合法区间。 */
    fun clampInto(layout: GridLayout) {
        zoom = zoom.coerceIn(GRID_MIN_ZOOM, GRID_MAX_ZOOM)
        offsetX = clampOffset(offsetX, layout.availW, layout.contentWidth(zoom))
        offsetY = clampOffset(offsetY, layout.availH, layout.contentHeight(zoom))
    }
}

/**
 * 缩放档位阶梯。固定公比而不是「可见天数必须是整数」，
 * 因为按天数取整会得到 1 / 1.14 / 1.4 / 1.75 / 2.33 / 2.6 这种疏密不均的档位，
 * 捏合一点点就被拽回上一档，观感就是「到不了下一档」。
 */
internal fun zoomLadder(
    min: Float = GRID_MIN_ZOOM,
    max: Float = GRID_MAX_ZOOM,
    step: Float = GRID_ZOOM_STEP,
): List<Float> {
    if (step <= 1f) return listOf(min)
    if (!(max > min)) return listOf(min)
    val rungs = ArrayList<Float>()
    var value = min
    while (value < max * 0.999f) {
        rungs += value
        value *= step
    }
    // 末档离上一个档位太近就并进 MAX，避免出现一段「怎么捏都不动」的死区。
    if (rungs.isNotEmpty() && max / rungs.last() < 1.06f) {
        rungs[rungs.size - 1] = max
    } else {
        rungs += max
    }
    return rungs
}

internal fun nearestZoomRung(zoom: Float, rungs: List<Float> = zoomLadder()): Float {
    if (rungs.isEmpty()) return GRID_MIN_ZOOM
    var best = rungs.first()
    var bestDistance = abs(best - zoom)
    rungs.forEach { rung ->
        val distance = abs(rung - zoom)
        if (distance < bestDistance - 1e-4f) {
            best = rung
            bestDistance = distance
        }
    }
    return best
}

internal fun nextZoomRung(zoom: Float, rungs: List<Float> = zoomLadder()): Float =
    rungs.firstOrNull { it > zoom + 1e-3f } ?: rungs.last()

internal fun previousZoomRung(zoom: Float, rungs: List<Float> = zoomLadder()): Float =
    rungs.lastOrNull { it < zoom - 1e-3f } ?: rungs.first()

/** 把偏移夹进 [0, content - viewport]；内容比视口短时恒为 0。 */
internal fun clampOffset(offset: Float, viewport: Float, content: Float): Float {
    val max = (content - viewport).coerceAtLeast(0f)
    return offset.coerceIn(0f, max)
}

/** 越界时把超出的部分压缩 [factor] 倍，松手后由回弹动画归零。 */
internal fun rubberOffset(
    offset: Float,
    viewport: Float,
    content: Float,
    factor: Float = GRID_RUBBER,
): Float {
    val max = (content - viewport).coerceAtLeast(0f)
    return when {
        offset < 0f -> offset * factor
        offset > max -> max + (offset - max) * factor
        else -> offset
    }
}

/**
 * 缩放锚点补偿：让 [anchor] 处的内容在缩放前后停在同一个网格位置。
 *
 * 旧实现只补偿了 X，Y 完全没管，而 cellH 又是按 cellW 等比算的，
 * 于是每次捏合内容都会上下窜——这是「捏合时内容乱跳」的直接原因。
 */
internal fun anchoredOffset(
    offset: Float,
    anchor: Float,
    ratio: Float,
    viewport: Float,
    content: Float,
): Float = clampOffset((offset + anchor) * ratio - anchor, viewport, content)

/**
 * 一次性动画协程的槽位：新手势开始前必须取消上一个，
 * 否则松手吸附动画和手指的实时写入会互相覆盖，表现为「滚动抖动 / 松手跳一下」。
 */
internal class JobSlot {
    private var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun launch(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit) {
        job?.cancel()
        job = scope.launch(block = block)
    }
}

/**
 * 指数衰减的位置曲线，t 以秒计。
 *
 * v(t) = v0·e^(-k·t)，位移 = ∫v = v0·(1 - e^(-k·t)) / k。
 * 用解析式而不是逐帧累加速度，结果与帧率无关，也就不会在掉帧时"滑多远全看运气"。
 */
internal fun decayDistance(velocity: Float, seconds: Float, friction: Float = GRID_FLING_FRICTION): Float {
    if (friction <= 0f || seconds <= 0f) return 0f
    return velocity * (1f - exp(-friction * seconds)) / friction
}

/** 速度衰减到 [GRID_FLING_MIN] 所需的秒数。 */
internal fun decaySeconds(velocity: Float, friction: Float = GRID_FLING_FRICTION): Float {
    val v = abs(velocity)
    if (v <= GRID_FLING_MIN || friction <= 0f) return 0f
    return kotlin.math.ln(v / GRID_FLING_MIN) / friction
}

/**
 * 惯性滑动一个轴，撞到边界就停在边界。
 *
 * [onChange] 负责把中间值写回状态；返回最终落点。
 * [frameNanos] 可注入假时钟，纯单测就能跑完整条惯性曲线。
 */
internal suspend fun decayInto(
    start: Float,
    velocity: Float,
    min: Float,
    max: Float,
    frameNanos: suspend () -> Long = { withFrameNanos { it } },
    friction: Float = GRID_FLING_FRICTION,
    onChange: (Float) -> Unit,
): Float {
    val bounded = start.coerceIn(min, max)
    onChange(bounded)
    if (abs(velocity) < GRID_FLING_MIN) return bounded

    // 整条曲线在出发前就能算完：指数衰减单调，所以终点就是落点。
    val seconds = decaySeconds(velocity, friction)
    if (seconds <= 0f) return bounded
    val target = bounded + decayDistance(velocity, seconds, friction)
    val clampedTarget = target.coerceIn(min, max)

    val begin = frameNanos()
    val deadline = begin + (seconds * 1_000_000_000L).toLong()
    var last = bounded
    while (true) {
        val now = frameNanos()
        val elapsed = (now - begin).coerceAtLeast(0L) / 1_000_000_000f
        val value = (bounded + decayDistance(velocity, elapsed.coerceAtMost(seconds), friction))
        val next = when {
            // 已经撞到边界：停在边界上收工。
            (velocity > 0f && value >= max) || (velocity < 0f && value <= min) -> clampedTarget
            else -> value.coerceIn(min, max)
        }
        if (next != last) {
            last = next
            onChange(next)
        }
        if (now >= deadline || next == clampedTarget) break
    }
    return last
}
