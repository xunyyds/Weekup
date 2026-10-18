package com.xu.wake.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodResizeTest {

    @Test fun defaultIsTwentyPeriods() {
        val periods = defaultDayPeriods()
        assertEquals(20, periods.size)
        assertEquals(20, DEFAULT_PERIOD_COUNT)
        assertEquals(listOf(1), periods.map { it.period }.distinct().take(1))
    }

    @Test fun defaultIsMonotonicAndInsideTheDay() {
        val periods = defaultDayPeriods()
        periods.zipWithNext().forEach { (a, b) ->
            assertTrue("第 ${a.period} 节结束应不晚于下一节开始", a.endMinutes <= b.startMinutes)
        }
        assertTrue("不得越过当天", periods.all { it.endMinutes <= 1440 })
        assertTrue("第一节应在上午", periods.first().startMinutes == 480)
    }

    @Test fun growingKeepsExistingTimes() {
        val base = defaultDayPeriods().take(6)
        val grown = resizePeriods(base, 9)
        assertEquals(9, grown.size)
        assertEquals("前 6 节应保持不变", base, grown.take(6))
    }

    @Test fun shrinkingTruncatesTheTail() {
        val shrunk = resizePeriods(defaultDayPeriods(), 5)
        assertEquals(5, shrunk.size)
        assertEquals(listOf(1, 2, 3, 4, 5), shrunk.map { it.period })
    }

    @Test fun refusesToRunPastMidnight() {
        val late = listOf(PeriodTime(1, 1380, 1425))
        assertThrows(IllegalArgumentException::class.java) { resizePeriods(late, 3) }
    }

    @Test fun countBoundsAreEnforced() {
        assertThrows(IllegalArgumentException::class.java) { resizePeriods(defaultDayPeriods(), 0) }
        assertThrows(IllegalArgumentException::class.java) { resizePeriods(defaultDayPeriods(), 101) }
    }
}
