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

internal const val GRID_MIN_ZOOM = 1f

internal const val GRID_MAX_ZOOM = 3f

internal const val GRID_ZOOM_STEP = 1.22f

internal const val GRID_ROW_ASPECT = 1.02f

internal const val GRID_RUBBER = 0.32f

internal const val GRID_FLING_MIN = 80f

internal const val GRID_FLING_FRICTION = 4.9f

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

internal class TimetableGridState(initialZoom: Float = GRID_MIN_ZOOM) {
    var zoom by mutableFloatStateOf(initialZoom.coerceIn(GRID_MIN_ZOOM, GRID_MAX_ZOOM))
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)

    fun clampInto(layout: GridLayout) {
        zoom = zoom.coerceIn(GRID_MIN_ZOOM, GRID_MAX_ZOOM)
        offsetX = clampOffset(offsetX, layout.availW, layout.contentWidth(zoom))
        offsetY = clampOffset(offsetY, layout.availH, layout.contentHeight(zoom))
    }
}

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

internal fun clampOffset(offset: Float, viewport: Float, content: Float): Float {
    val max = (content - viewport).coerceAtLeast(0f)
    return offset.coerceIn(0f, max)
}

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

internal fun anchoredOffset(
    offset: Float,
    anchor: Float,
    ratio: Float,
    viewport: Float,
    content: Float,
): Float = clampOffset((offset + anchor) * ratio - anchor, viewport, content)

/**
 * 缩放越界的橡皮筋：捏到最大/最小档还想继续捏时只允许超出一点点。
 *
 * 原来直接 `coerceIn`，捏合在最档位处"硬碰硬"停住，手感像卡住；
 * 给它一点弹性余量，松手后由档位吸附动画收回。
 */
internal fun rubberZoom(
    raw: Float,
    min: Float = GRID_MIN_ZOOM,
    max: Float = GRID_MAX_ZOOM,
    factor: Float = GRID_RUBBER,
): Float {
    if (!raw.isFinite()) return min
    return when {
        raw < min -> min - (min - raw) * factor
        raw > max -> max + (raw - max) * factor
        else -> raw
    }
}

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

internal fun decayDistance(velocity: Float, seconds: Float, friction: Float = GRID_FLING_FRICTION): Float {
    if (friction <= 0f || seconds <= 0f) return 0f
    return velocity * (1f - exp(-friction * seconds)) / friction
}

internal fun decaySeconds(velocity: Float, friction: Float = GRID_FLING_FRICTION): Float {
    val v = abs(velocity)
    if (v <= GRID_FLING_MIN || friction <= 0f) return 0f
    return kotlin.math.ln(v / GRID_FLING_MIN) / friction
}

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
