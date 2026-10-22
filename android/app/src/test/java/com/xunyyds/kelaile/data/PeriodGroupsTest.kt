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

    /** 上半天的真实作息：1-2 连堂、3-4 连堂，中间有课间。 */
    private val morning = listOf(
        block(1, "08:20", "09:10"),
        block(2, "09:10", "10:00"),
        block(3, "10:20", "11:10"),
        block(4, "11:10", "12:00"),
    )

    @Test fun adjacentPeriodsMergeIntoOneBlock() {
        val groups = PeriodGroups.of(morning)
        assertEquals(2, groups.size)
        // 1、2 节合成一段：08:20 起、10:00 止。
        assertEquals(0, groups[0].startIndex)
        assertEquals(1, groups[0].endIndex)
        assertEquals(8 * 60 + 20, groups[0].startMinutes)
        assertEquals(10 * 60, groups[0].endMinutes)
        // 3、4 节合成另一段：10:20 起、12:00 止。
        assertEquals(2, groups[1].startIndex)
        assertEquals(3, groups[1].endIndex)
        assertEquals(10 * 60 + 20, groups[1].startMinutes)
        assertEquals(12 * 60, groups[1].endMinutes)
    }

    @Test fun singlePeriodStaysAlone() {
        val periods = listOf(
            block(1, "08:20", "09:10"),
            block(2, "09:30", "10:20"), // 有空隙
        )
        val groups = PeriodGroups.of(periods)
        assertEquals(2, groups.size)
        groups.forEach {
            assertEquals(1, it.periodCount)
            assertEquals(it.startIndex, it.endIndex)
        }
    }

    @Test fun gapBreaksTheBlock() {
        // 2 节结束 10:00，3 节 10:20 才开始：必须断开。
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
        // 4 连堂首尾相接，应合成单一时间段。
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
        // 时间重叠（脏数据）也应断开，而不是合成一段错的时间。
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
        // 每组首行才标开始时间、末行才标结束时间。
        assertEquals(true, map.getValue(0).startIndex == 0)
        assertEquals(false, map.getValue(1).startIndex == 1)
    }

    @Test fun groupsCoverEveryIndexExactlyOnce() {
        val groups = PeriodGroups.of(morning)
        val covered = groups.flatMap { (it.startIndex..it.endIndex).toList() }
        assertEquals((0 until morning.size).toList(), covered)
    }
}
