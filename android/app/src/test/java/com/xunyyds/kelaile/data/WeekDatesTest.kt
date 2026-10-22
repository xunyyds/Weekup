package com.xunyyds.kelaile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekDatesTest {

    private val monday = LocalDate.of(2026, 9, 7)

    @Test fun weekOneStartsAtStartDate() {
        assertEquals(monday, WeekDates.on(monday, 1, 1))
        assertEquals(monday.plusDays(6), WeekDates.on(monday, 1, 7))
    }

    @Test fun weeksAdvanceBySevenDays() {
        assertEquals(monday.plusDays(7), WeekDates.on(monday, 2, 1))
        assertEquals(monday.plusDays(7 * 7), WeekDates.on(monday, 8, 1))
        assertEquals(monday.plusDays(7 * 7 + 3), WeekDates.on(monday, 8, 4))
    }

    /**
     * 周三开学时，第 1 周仍从该周周一算起，列内日期必须是升序。
     *
     * 旧实现把周三当成「第 1 周的周一」，于是第 1 周的周一列被算到下一周
     * （9/14），比周三列（9/9）还晚 —— 表头自相矛盾。这里断言修复后的口径。
     */
    @Test fun startOnWednesdayShiftsColumns() {
        val wed = LocalDate.of(2026, 9, 9)
        // 第 1 周 = 9/7(一)～9/13(日)，周三落在第 3 列。
        assertEquals(LocalDate.of(2026, 9, 7), WeekDates.on(wed, 1, 1))
        assertEquals(wed, WeekDates.on(wed, 1, 3))
        assertEquals("9/7–9/13", WeekDates.rangeLabel(wed, 1))

        // 同一周内，列序必须与日期同向递增。
        for (d in 1 until 7) {
            assertTrue(WeekDates.on(wed, 1, d).isBefore(WeekDates.on(wed, 1, d + 1)))
        }
    }

    @Test fun weekRangeSpansSevenDays() {
        val (from, to) = WeekDates.range(monday, 3)
        assertEquals(monday.plusDays(14), from)
        assertEquals(monday.plusDays(20), to)
        assertEquals(6, java.time.temporal.ChronoUnit.DAYS.between(from, to).toInt())
    }

    @Test fun weekOfTracksToday() {
        assertEquals(1, WeekDates.weekOf(monday, monday))
        assertEquals(1, WeekDates.weekOf(monday, monday.plusDays(6)))
        assertEquals(2, WeekDates.weekOf(monday, monday.plusDays(7)))
        assertEquals(8, WeekDates.weekOf(monday, monday.plusDays(49)))
    }

    @Test fun beforeStartClampsToOne() {
        assertEquals(1, WeekDates.weekOf(monday, monday.minusDays(30)))
    }

    @Test fun labelsAreShort() {
        assertEquals("9/7–9/13", WeekDates.rangeLabel(monday, 1))
        assertEquals("9/14–9/20", WeekDates.rangeLabel(monday, 2))
        assertEquals("9/7", WeekDates.dayLabel(monday, 1, 1))
        assertTrue(WeekDates.dayLabel(monday, 1, 1).length <= 5)
    }

    @Test fun onAndWeekOfAreConsistent() {
        for (w in 1..20) for (d in 1..7) {
            assertEquals(w, WeekDates.weekOf(monday, WeekDates.on(monday, w, d)))
        }
    }

    /**
     * 学期开始日不是周一时（开学当天是周二），周标题与各列日期必须一致。
     *
     * 旧实现里 [WeekDates.range] 直接把开始日当周一，而 [WeekDates.on] 按真实
     * 星期对齐，于是同一周算出「10/27–11/2」的标题，却把周一标成 11/2、
     * 周二标成 10/27 —— 列内日期倒序。
     */
    @Test fun rangeAndOnAgreeWhenStartIsNotMonday() {
        val tuesday = LocalDate.of(2026, 9, 15)
        assertEquals(java.time.DayOfWeek.TUESDAY, tuesday.dayOfWeek)

        // 第 7 周：周一是 10/26，周日是 11/1，标题也不能是 10/27–11/2。
        assertEquals(LocalDate.of(2026, 10, 26), WeekDates.on(tuesday, 7, 1))
        assertEquals("10/26–11/1", WeekDates.rangeLabel(tuesday, 7))

        // 标题的 7 天必须与 7 列日期逐一对应。
        val (from, _) = WeekDates.range(tuesday, 7)
        for (d in 1..7) {
            assertEquals(from.plusDays((d - 1).toLong()), WeekDates.on(tuesday, 7, d))
        }
    }

    @Test fun everyWeekStartsOnMondayForAnyStartDay() {
        val start = LocalDate.of(2026, 9, 15) // 周二
        for (w in 1..20) {
            val (from, to) = WeekDates.range(start, w)
            assertEquals(java.time.DayOfWeek.MONDAY, from.dayOfWeek)
            assertEquals(java.time.DayOfWeek.SUNDAY, to.dayOfWeek)
            assertEquals(6, java.time.temporal.ChronoUnit.DAYS.between(from, to).toInt())
            assertEquals(from, WeekDates.on(start, w, 1))
        }
    }

    @Test fun weekOfStaysConsistentForAnyStartDay() {
        val start = LocalDate.of(2026, 9, 15) // 周二
        for (w in 1..20) for (d in 1..7) {
            assertEquals(w, WeekDates.weekOf(start, WeekDates.on(start, w, d)))
        }
    }

    @Test fun startOnSundayBelongsToItsOwnWeek() {
        // 周日开学时，该周周一仍算第 1 周的第一天，而不是把周日算成第 1 天。
        val sunday = LocalDate.of(2026, 9, 13)
        assertEquals(java.time.DayOfWeek.SUNDAY, sunday.dayOfWeek)
        val (from, _) = WeekDates.range(sunday, 1)
        assertEquals(LocalDate.of(2026, 9, 7), from)
        assertEquals(java.time.DayOfWeek.MONDAY, from.dayOfWeek)
        assertEquals(sunday, WeekDates.on(sunday, 1, 7))
    }

    /**
     * 由「今天是第 N 周」反推开学的日期，必须能被 [WeekDates.weekOf] 原样算回 N。
     * 这是「只填周数、不翻校历」这条设置路径的正确性前提。
     */
    @Test fun startForWeekIsInverseOfWeekOf() {
        val today = LocalDate.of(2026, 10, 20) // 周二
        for (w in 1..20) {
            val start = WeekDates.startForWeek(w, today)
            assertEquals(java.time.DayOfWeek.MONDAY, start.dayOfWeek)
            assertEquals(w, WeekDates.weekOf(start, today))
        }
    }

    @Test fun startForWeekPutsTodayInTheRequestedWeek() {
        val today = LocalDate.of(2026, 10, 20)
        // 今天在第 7 周，则第 7 周覆盖 10/19(一)～10/25(日)。
        val start = WeekDates.startForWeek(7, today)
        assertEquals("10/19–10/25", WeekDates.rangeLabel(start, 7))
        assertEquals(today, WeekDates.on(start, 7, 2))
    }

    @Test fun startForWeekClampsInvalidWeekToOne() {
        val today = LocalDate.of(2026, 10, 20)
        assertEquals(WeekDates.startForWeek(1, today), WeekDates.startForWeek(0, today))
        assertEquals(WeekDates.startForWeek(1, today), WeekDates.startForWeek(-5, today))
        assertEquals(1, WeekDates.weekOf(WeekDates.startForWeek(1, today), today))
    }
}
