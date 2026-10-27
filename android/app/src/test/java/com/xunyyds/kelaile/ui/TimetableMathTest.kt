package com.xunyyds.kelaile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableMathTest {
    private val layout = GridLayout(
        dayCount = 7,
        periodCount = 12,
        availW = 700f,
        availH = 800f,
        gutterPx = 42f,
        headerPx = 38f,
        bottomRows = 2,
    )

    @Test fun cellWidthSplitsAvailableWidth() {
        assertEquals(100f, layout.cellWidth(1f), 0.01f)
    }

    @Test fun contentHeightIncludesBottomSpacerRows() {
        val oneRow = layout.cellHeight(1f)
        val expected = oneRow * (layout.periodCount + 2)
        assertEquals(expected, layout.contentHeight(1f), 0.01f)
    }

    @Test fun bottomSpacerGivesTwoRowsOfScrollRoom() {
        val withoutSpacer = GridLayout(7, 12, 700f, 800f, 42f, 38f, bottomRows = 0)
        val withSpacer = GridLayout(7, 12, 700f, 800f, 42f, 38f, bottomRows = 2)
        val extra = withSpacer.contentHeight(1f) - withoutSpacer.contentHeight(1f)
        assertEquals(withSpacer.cellHeight(1f) * 2, extra, 0.01f)
    }

    @Test fun maxOffsetYGrowsWithBottomSpacer() {
        val withoutSpacer = GridLayout(7, 12, 700f, 800f, 42f, 38f, bottomRows = 0)
        val withSpacer = GridLayout(7, 12, 700f, 800f, 42f, 38f, bottomRows = 2)
        assertTrue(withSpacer.maxOffsetY(1f) > withoutSpacer.maxOffsetY(1f))
    }

    @Test fun pinnedTopRubberNeverGoesNegative() {
        assertEquals(0f, rubberOffsetPinnedTop(-120f, 800f, 1400f), 0.001f)
        assertEquals(0f, rubberOffsetPinnedTop(0f, 800f, 1400f), 0.001f)
    }

    @Test fun pinnedTopRubberStillBouncesAtBottom() {
        val max = layout.maxOffsetY(1f)
        val pulled = rubberOffsetPinnedTop(max + 200f, 800f, layout.contentHeight(1f))
        assertTrue(pulled > max)
        assertTrue(pulled < max + 200f)
    }

    @Test fun pinnedTopRubberPassesInteriorThrough() {
        val inside = layout.maxOffsetY(1f) / 2f
        assertEquals(inside, rubberOffsetPinnedTop(inside, 800f, layout.contentHeight(1f)), 0.001f)
    }

    @Test fun rowAtMapsTopCellToZero() {
        // 表头 38px 之下、第一行格子之内，应落在第 0 行
        assertEquals(0, layout.rowAt(50f, 0f, 1f))
    }

    @Test fun rowAtAdvancesWithCellHeight() {
        val cell = layout.cellHeight(1f)
        assertEquals(1, layout.rowAt(38f + cell + 1f, 0f, 1f))
        assertEquals(2, layout.rowAt(38f + cell * 2f + 1f, 0f, 1f))
    }

    @Test fun columnAtStaysInsideDayRange() {
        assertEquals(0, layout.columnAt(0f, 0f, 1f))
        assertEquals(layout.dayCount - 1, layout.columnAt(10_000f, 0f, 1f))
    }
}
