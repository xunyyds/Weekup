package com.xunyyds.kelaile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodGroupsTest {

    private fun block(startPeriod: Int, from: String, to: String): PeriodTime {
        fun m(s: String): Int {
            val (h, mi) = s.split(":").map { it.toInt() }
            return h * 60 + mi
        }
        return PeriodTime(startPeriod, m(from), m(to))
    }

    private val morning = listOf(
        block(1, "08:20", "09:10"),
        block(2, "09:10", "10:00"),
        block(3, "10:20", "11:10"),
        block(4, "11:10", "12:00"),
    )

    @Test fun adjacentPeriodsMergeIntoOneBlock() {
        val groups = PeriodGroups.of(morning)
        assertEquals(2, groups.size)

        assertEquals(0, groups[0].startIndex)
        assertEquals(1, groups[0].endIndex)
        assertEquals(8 * 60 + 20, groups[0].startMinutes)
        assertEquals(10 * 60, groups[0].endMinutes)

        assertEquals(2, groups[1].startIndex)
        assertEquals(3, groups[1].endIndex)
        assertEquals(10 * 60 + 20, groups[1].startMinutes)
        assertEquals(12 * 60, groups[1].endMinutes)
    }

    @Test fun singlePeriodStaysAlone() {
        val periods = listOf(
            block(1, "08:20", "09:10"),
            block(2, "09:30", "10:20"),
        )
        val groups = PeriodGroups.of(periods)
        assertEquals(2, groups.size)
        groups.forEach {
            assertEquals(1, it.periodCount)
            assertEquals(it.startIndex, it.endIndex)
        }
    }

    @Test fun gapBreaksTheBlock() {

        val groups = PeriodGroups.of(morning)
        assertTrue(groups.none { it.startIndex == 1 && it.endIndex >= 2 })
    }

    @Test fun fullLaterBlockMerges() {
        val periods = listOf(
            block(5, "14:20", "15:10"),
            block(6, "15:10", "16:00"),
            block(7, "16:20", "17:10"),
            block(8, "17:10", "18:00"),
        )
        val groups = PeriodGroups.of(periods)
        assertEquals(2, groups.size)
        assertEquals(14 * 60 + 20, groups[0].startMinutes)
        assertEquals(16 * 60, groups[0].endMinutes)
        assertEquals(16 * 60 + 20, groups[1].startMinutes)
        assertEquals(18 * 60, groups[1].endMinutes)
    }

    @Test fun longRunMergesIntoOne() {

        val chained = listOf(
            block(1, "08:20", "09:10"),
            block(2, "09:10", "10:00"),
            block(3, "10:00", "10:50"),
            block(4, "10:50", "11:40"),
        )
        val groups = PeriodGroups.of(chained)
        assertEquals(1, groups.size)
        assertEquals(4, groups[0].periodCount)
        assertEquals(8 * 60 + 20, groups[0].startMinutes)
        assertEquals(11 * 60 + 40, groups[0].endMinutes)
    }

    @Test fun emptyInputYieldsNoGroups() {
        assertTrue(PeriodGroups.of(emptyList()).isEmpty())
    }

    @Test fun singleElementInputYieldsOneGroup() {
        val groups = PeriodGroups.of(listOf(block(1, "08:20", "09:10")))
        assertEquals(1, groups.size)
        assertEquals(0, groups[0].startIndex)
        assertEquals(0, groups[0].endIndex)
        assertEquals(1, groups[0].periodCount)
    }

    @Test fun overlappingTimesBreakTheBlock() {

        val periods = listOf(
            block(1, "08:20", "09:30"),
            block(2, "09:10", "10:00"),
        )
        assertEquals(2, PeriodGroups.of(periods).size)
    }

    @Test fun indexPathMapsEveryRowToItsGroup() {
        val map = PeriodGroups.indexMap(morning)
        assertEquals(4, map.size)
        assertEquals(0, map.getValue(0).startIndex)
        assertEquals(0, map.getValue(1).startIndex)
        assertEquals(2, map.getValue(2).startIndex)
        assertEquals(2, map.getValue(3).startIndex)

        assertEquals(true, map.getValue(0).startIndex == 0)
        assertEquals(false, map.getValue(1).startIndex == 1)
    }

    @Test fun groupsCoverEveryIndexExactlyOnce() {
        val groups = PeriodGroups.of(morning)
        val covered = groups.flatMap { (it.startIndex..it.endIndex).toList() }
        assertEquals((0 until morning.size).toList(), covered)
    }
}
