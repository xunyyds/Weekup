package com.xu.wake.data

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

    @Test fun startOnWednesdayShiftsColumns() {
        val wed = LocalDate.of(2026, 9, 9)
        assertEquals(wed, WeekDates.on(wed, 1, 3))

        assertEquals(wed.plusDays(5), WeekDates.on(wed, 1, 1))

        assertEquals(wed.minusDays(2), WeekDates.on(wed, 1, 1).minusDays(7))
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
}
