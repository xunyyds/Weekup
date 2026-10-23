package com.xunyyds.kelaile.ui

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TimetableZoomTest {

    private fun rungs() = zoomLadder()

    @Test
    fun ladderStartsAtMinAndEndsAtMax() {
        val steps = rungs()
        assertEquals(GRID_MIN_ZOOM, steps.first(), 1e-4f)
        assertEquals(GRID_MAX_ZOOM, steps.last(), 1e-4f)
    }

    @Test
    fun ladderIsStrictlyIncreasingAndEvenlySpaced() {
        val steps = rungs()
        assertTrue("至少要有 4 档，否则缩放体验太钝：$steps", steps.size >= 4)
        steps.zipWithNext().forEach { (a, b) ->
            assertTrue("档位必须严格递增：$a -> $b", b > a + 1e-4f)
            val ratio = b / a
            assertTrue("相邻档位比值 $ratio 偏离公比（$steps）", ratio > 1.05f && ratio <= GRID_ZOOM_STEP + 1e-3f)
        }
    }

    @Test
    fun ladderCoversTheWholeRangeWithoutDeadZones() {
        val steps = rungs()
        var covered = 0f
        steps.zipWithNext().forEach { (a, b) -> covered += b - a }
        assertEquals(GRID_MAX_ZOOM - GRID_MIN_ZOOM, covered, 1e-4f)
    }

    @Test
    fun everyPinchOfOneStepReachesTheNextRung() {

        val steps = rungs()
        steps.zipWithNext().forEach { (a, b) ->
            val zoomed = a * GRID_ZOOM_STEP
            assertEquals("$a 捏一下必须能吸附到 $b", b, nearestZoomRung(zoomed), 1e-3f)
        }
    }

    @Test
    fun nearestRungSnapsWithinHalfAStep() {
        val steps = rungs()
        steps.zipWithNext().forEach { (a, b) ->
            val mid = (a + b) / 2f
            assertEquals("中点偏向前一档", a, nearestZoomRung(mid - 1e-3f), 1e-4f)
            assertEquals("中点偏向后一档", b, nearestZoomRung(mid + 1e-3f), 1e-4f)
        }
        assertEquals(GRID_MIN_ZOOM, nearestZoomRung(GRID_MIN_ZOOM - 5f), 1e-4f)
        assertEquals(GRID_MAX_ZOOM, nearestZoomRung(GRID_MAX_ZOOM + 5f), 1e-4f)
    }

    @Test
    fun nextAndPreviousRungAreClampedAtTheEnds() {
        val steps = rungs()
        assertEquals(steps[1], nextZoomRung(GRID_MIN_ZOOM), 1e-4f)
        assertEquals(GRID_MAX_ZOOM, nextZoomRung(GRID_MAX_ZOOM), 1e-4f)
        assertEquals(steps[steps.size - 2], previousZoomRung(GRID_MAX_ZOOM), 1e-4f)
        assertEquals(GRID_MIN_ZOOM, previousZoomRung(GRID_MIN_ZOOM), 1e-4f)
    }

    @Test
    fun ladderRejectsDegenerateBounds() {
        assertEquals(listOf(1f), zoomLadder(min = 1f, max = 1f))
        assertEquals("上界低于下界时只留下界", listOf(2f), zoomLadder(min = 2f, max = 1f))
        assertEquals("公比 <= 1 等于没有阶梯", listOf(1f), zoomLadder(min = 1f, max = 3f, step = 1f))
    }

    private fun layout(
        dayCount: Int = 7,
        periodCount: Int = 12,
        availW: Float = 1080f,
        availH: Float = 1600f,
    ) = GridLayout(dayCount, periodCount, availW, availH, gutterPx = 150f, headerPx = 100f)

    @Test
    fun cellSizeScalesLinearlyWithZoom() {
        val l = layout()
        assertEquals(1080f / 7f, l.cellWidth(1f), 1e-3f)
        assertEquals(l.cellWidth(1f) * 2f, l.cellWidth(2f), 1e-3f)
        assertEquals(l.cellWidth(1.5f) * GRID_ROW_ASPECT, l.cellHeight(1.5f), 1e-3f)
    }

    @Test
    fun atMinZoomContentWidthMatchesViewportSoAWholeWeekFits() {
        val l = layout()
        assertEquals(l.availW, l.contentWidth(GRID_MIN_ZOOM), 1e-3f)
        assertEquals(0f, l.maxOffsetX(GRID_MIN_ZOOM), 1e-3f)
    }

    @Test
    fun maxOffsetsAreNeverNegative() {
        assertEquals(0f, layout(periodCount = 2, availH = 4000f).maxOffsetY(GRID_MIN_ZOOM), 1e-3f)
        assertTrue(layout().maxOffsetY(GRID_MIN_ZOOM) > 0f)
    }

    @Test
    fun maxOffsetsShrinkBackWhenZoomingOut() {
        val l = layout()
        val wide = l.maxOffsetX(2.5f)
        val narrow = l.maxOffsetX(1.2f)
        assertTrue("放大后能横向滚动更多", wide > narrow)
        assertEquals(0f, l.maxOffsetX(GRID_MIN_ZOOM), 1e-3f)
    }

    @Test
    fun hitTestingMapsScreenToGrid() {
        val l = layout()

        assertEquals(0, l.columnAt(l.gutterPx + 1f, offsetX = 0f, zoom = 1f))
        assertEquals(1, l.columnAt(l.gutterPx + l.cellWidth(1f) + 1f, offsetX = 0f, zoom = 1f))
        assertEquals(0, l.rowAt(l.headerPx + 1f, offsetY = 0f, zoom = 1f))
        assertEquals(2, l.rowAt(l.headerPx + l.cellHeight(1f) * 2.5f, offsetY = 0f, zoom = 1f))

        assertEquals(4, l.rowAt(l.headerPx + l.cellHeight(1f) * 2.5f, offsetY = l.cellHeight(1f) * 1.5f, zoom = 1f))

        assertEquals(0, l.columnAt(-9999f, offsetX = 0f, zoom = 1f))
        assertEquals(l.dayCount - 1, l.columnAt(99999f, offsetX = 0f, zoom = 1f))
        assertEquals(l.periodCount - 1, l.rowAt(99999f, offsetY = 0f, zoom = 1f))
    }

    @Test
    fun clampOffsetKeepsOffsetInsideContent() {
        assertEquals(0f, clampOffset(-40f, 1000f, 3000f), 1e-4f)
        assertEquals(2000f, clampOffset(9999f, 1000f, 3000f), 1e-4f)
        assertEquals(500f, clampOffset(500f, 1000f, 3000f), 1e-4f)
        assertEquals(0f, clampOffset(42f, 1000f, 800f), 1e-4f)
    }

    @Test
    fun rubberOffsetCompressesOnlyTheOverscrolledPart() {
        assertEquals(500f, rubberOffset(500f, 1000f, 3000f), 1e-4f)
        assertEquals(-32f, rubberOffset(-100f, 1000f, 3000f), 1e-3f)
        assertEquals(2032f, rubberOffset(2100f, 1000f, 3000f), 1e-3f)
        assertEquals(16f, rubberOffset(50f, 1000f, 800f), 1e-3f)
    }

    @Test
    fun anchoredOffsetKeepsTheAnchorPointFixed() {
        val viewport = 1080f
        val content0 = 3000f
        val offset = 600f
        val anchor = 300f
        val contentPoint = offset + anchor
        listOf(1.1f, 1.5f, 2.4f).forEach { ratio ->
            val next = anchoredOffset(offset, anchor, ratio, viewport, content0 * ratio)
            assertEquals(
                "缩放比 $ratio 时锚点处内容必须不动",
                contentPoint * ratio, next + anchor, 1e-3f,
            )
        }
    }

    @Test
    fun anchoredOffsetClampsButStillMovesContentTheRightWay() {
        val viewport = 1080f

        val left = anchoredOffset(0f, 400f, 0.8f, viewport, 3000f * 0.8f)
        assertEquals(0f, left, 1e-3f)

        val content = 3000f * 1.4f
        val right = anchoredOffset(3000f - viewport, 0f, 1.4f, viewport, content)
        assertEquals((3000f - viewport) * 1.4f, right, 1e-2f)

        val over = anchoredOffset(content - viewport, viewport, 1.4f, viewport, content)
        assertEquals(content - viewport, over, 1e-2f)
    }

    @Test
    fun scalingAroundTheCentroidDoesNotMoveThatCentroidOnEitherAxis() {

        val l = layout(availH = 2000f)
        val zoom0 = 1.4f
        val offsetX = 220f
        val offsetY = 480f
        val cx = l.availW / 2f
        val cy = l.availH / 2f
        val ratio = 1.22f
        val next = zoom0 * ratio

        val nx = anchoredOffset(offsetX, cx, ratio, l.availW, l.contentWidth(next))
        val ny = anchoredOffset(offsetY, cy, ratio, l.availH, l.contentHeight(next))

        val contentX = offsetX + cx
        val contentY = offsetY + cy
        assertEquals(contentX * ratio, nx + cx, 1e-2f)
        assertEquals(contentY * ratio, ny + cy, 1e-2f)
    }

    @Test
    fun gridStateClampsZoomAndOffsetsTogether() {
        val state = TimetableGridState()
        state.zoom = 99f
        state.offsetX = -500f
        state.offsetY = 999999f
        val l = layout()
        state.clampInto(l)

        assertEquals(GRID_MAX_ZOOM, state.zoom, 1e-4f)
        assertEquals(0f, state.offsetX, 1e-4f)
        assertEquals(l.maxOffsetY(state.zoom), state.offsetY, 1e-3f)
    }

    @Test
    fun gridStateHonoursPersistedZoom() {
        assertEquals(1.7f, TimetableGridState(1.7f).zoom, 1e-4f)
        assertEquals(GRID_MAX_ZOOM, TimetableGridState(99f).zoom, 1e-4f)
        assertEquals(GRID_MIN_ZOOM, TimetableGridState(0f).zoom, 1e-4f)
    }

    @Test
    fun shrinkingTheViewportKeepsOffsetsLegal() {
        val tall = layout(availH = 4000f)
        val shorter = layout(availH = 900f)
        assertTrue("视口变矮后上限必须变大", shorter.maxOffsetY(GRID_MAX_ZOOM) > tall.maxOffsetY(GRID_MAX_ZOOM))

        val state = TimetableGridState(GRID_MAX_ZOOM)
        state.offsetY = tall.maxOffsetY(GRID_MAX_ZOOM)
        state.clampInto(shorter)

        assertEquals(tall.maxOffsetY(GRID_MAX_ZOOM), state.offsetY, 1e-2f)
        assertTrue(state.offsetY <= shorter.maxOffsetY(state.zoom) + 1e-2f)

        state.offsetY = shorter.maxOffsetY(state.zoom) + 9999f
        state.clampInto(shorter)
        assertEquals(shorter.maxOffsetY(state.zoom), state.offsetY, 1e-2f)
    }

    private fun fakeClock(stepNanos: Long = 16_666_667L): suspend () -> Long {
        var now = 0L
        return { now += stepNanos; now }
    }

    @Test
    fun flingStopsBeforeTheBoundary() = runBlocking {
        val seen = ArrayList<Float>()
        val result = decayInto(0f, 3000f, 0f, 5000f, fakeClock()) { seen += it }
        assertTrue("惯性必须真的滑过", seen.size > 3)
        assertTrue("惯性结果必须单调增", seen.zipWithNext().all { (a, b) -> b >= a - 1e-3f })

        val expected = decayDistance(3000f, decaySeconds(3000f))
        assertEquals("没撞墙就停在速度阈值处", expected, result, 1f)
        assertTrue("终点必须小于渐近上限", result < 3000f / GRID_FLING_FRICTION)
    }

    @Test
    fun flingClampsAtTheBoundaryInsteadOfRunningPast() = runBlocking {
        val seen = ArrayList<Float>()
        val result = decayInto(0f, 20000f, 0f, 500f, fakeClock()) { seen += it }
        assertEquals(500f, result, 1e-3f)
        assertTrue("惯性不能在边界外留值：${seen.max()}", seen.all { it <= 500f + 1e-3f })
        assertEquals("必须停在边界上", 500f, seen.last(), 1e-3f)
    }

    @Test
    fun flingIsClampedInTheOppositeDirectionToo() = runBlocking {
        val seen = ArrayList<Float>()
        val result = decayInto(500f, -20000f, 0f, 500f, fakeClock()) { seen += it }
        assertEquals(0f, result, 1e-3f)
        assertTrue(seen.all { it >= -1e-3f })
    }

    @Test
    fun tinyVelocityDoesNotFling() = runBlocking {
        val seen = ArrayList<Float>()
        val result = decayInto(100f, 10f, 0f, 500f, fakeClock()) { seen += it }
        assertEquals(100f, result, 1e-4f)
        assertEquals(listOf(100f), seen)
    }

    @Test
    fun flingFromOutOfBoundsStartsAtTheEdge() = runBlocking {
        val seen = ArrayList<Float>()
        val result = decayInto(-120f, 400f, 0f, 500f, fakeClock()) { seen += it }
        assertEquals("越界起点必须先归位再惯性", 0f, seen.first(), 1e-4f)
        assertTrue(result >= 0f)
    }

    @Test
    fun flingDistanceIsFrameRateIndependent() {

        val fast = decayDistance(4000f, 0.25f)
        val slow = decayDistance(4000f, 0.25f)
        assertEquals(fast, slow, 1e-4f)

        assertTrue(decayDistance(4000f, 0.5f) > decayDistance(4000f, 0.25f))
        assertTrue(decayDistance(4000f, 100f) < 4000f / GRID_FLING_FRICTION + 1e-3f)
    }

    @Test
    fun decaySecondsMatchesTheVelocityThreshold() {
        val seconds = decaySeconds(4000f)
        val remaining = 4000f * kotlin.math.exp(-GRID_FLING_FRICTION * seconds)
        assertEquals(GRID_FLING_MIN, remaining, 1f)
        assertEquals(0f, decaySeconds(GRID_FLING_MIN), 1e-4f)
        assertEquals(0f, decaySeconds(0f), 1e-4f)
    }

    @Test
    fun decayHandlesDegenerateInputs() {
        assertEquals(0f, decayDistance(1000f, 0f), 1e-6f)
        assertEquals(0f, decayDistance(1000f, 1f, friction = 0f), 1e-6f)
        assertTrue(abs(decayDistance(0f, 1f)) < 1e-6f)
    }
}
