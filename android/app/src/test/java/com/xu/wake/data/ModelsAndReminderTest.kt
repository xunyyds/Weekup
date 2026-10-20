package com.xu.wake.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ModelsAndReminderTest {
    private fun settings() = AppSettings(start = LocalDate.of(2026, 2, 23), end = LocalDate.of(2026, 3, 8), weekCount = 2, reminders = true)
    private fun course(weeks: List<Int> = listOf(1, 2)) = CourseSession("id", "数学", 1, 1, 2, weeks)

    @Test fun defaultPeriodsAreTwenty() {
        val periods = defaultDayPeriods()
        assertEquals(20, periods.size)
        assertEquals(480, periods.first().startMinutes)
        assertEquals(520, periods.first().endMinutes)
        assertEquals(480 + 19 * 45, periods.last().startMinutes)
        assertEquals(480 + 19 * 45 + 40, periods.last().endMinutes)
    }
    @Test fun rejectsInvalidData() {
        assertThrows(IllegalArgumentException::class.java) { AppSettings(fontScale = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { AppSettings(firstDay = 6, lastDay = 5) }
        assertThrows(IllegalArgumentException::class.java) { AppSettings(reminderMinutes = -1) }
        assertThrows(IllegalArgumentException::class.java) { AppSettings(cellTextAlign = "middle") }
        assertThrows(IllegalArgumentException::class.java) { PeriodTime(1, 700, 700) }
        assertThrows(IllegalArgumentException::class.java) { course(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { AppData(settings(), listOf(course(), course())) }
        assertThrows(IllegalArgumentException::class.java) { AppData(settings(), listOf(course(listOf(3)))) }
        assertThrows(IllegalArgumentException::class.java) { AppData(settings(), colors = mapOf("bad" to -1L)) }
    }
    @Test fun scheduleUsesSemesterDatesAndLeadTime() {
        val data = AppData(settings(), listOf(course()))
        val zone = ZoneId.of("Asia/Shanghai")
        val events = ReminderPlan.events(data, LocalDate.of(2026, 2, 1).atStartOfDay(zone).toInstant().toEpochMilli(), zone)
        assertEquals(2, events.size)
        assertEquals(LocalDate.of(2026, 2, 23).atTime(8, 0).atZone(zone).toInstant().toEpochMilli(), events[0].startsAtMillis)
        assertEquals(600_000L, events[0].startsAtMillis - events[0].triggerAtMillis)
        assertEquals(1, ReminderPlan.events(data, events[0].triggerAtMillis, zone).size)
        assertEquals(1, ReminderPlan.events(data.copy(settings = settings().copy(end = LocalDate.of(2026, 3, 1))), 0, zone).size)
        assertTrue(ReminderPlan.events(data.copy(settings = settings().copy(reminders = false)), 0, zone).isEmpty())
    }
    @Test fun startNeedNotBeMondayForFlutterCompatibility() {
        val s = settings().copy(start = LocalDate.of(2026, 2, 25))
        val events = ReminderPlan.events(AppData(s, listOf(course(listOf(1)))), 0, ZoneId.of("UTC"))
        assertEquals(LocalDate.of(2026, 3, 2).atTime(8, 0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli(), events.single().startsAtMillis)
    }
    @Test fun timeZoneAndDstUseCalendarDays() {
        val s = AppSettings(start = LocalDate.of(2026, 3, 2), end = LocalDate.of(2026, 3, 15), weekCount = 2, reminders = true)
        val events = ReminderPlan.events(AppData(s, listOf(course())), 0, ZoneId.of("America/New_York"))
        assertEquals(167 * 60 * 60 * 1000L, events[1].startsAtMillis - events[0].startsAtMillis)
    }
    @Test fun cascadeRejectsOverlap() {
        assertThrows(IllegalArgumentException::class.java) { adjustPeriods(defaultDayPeriods(), 0, 480, 600, false) }

        assertEquals(545, adjustPeriods(defaultDayPeriods(), 0, 480, 540)[1].startMinutes)

        val overflow = assertThrows(IllegalArgumentException::class.java) {
            adjustPeriods(defaultDayPeriods(), 0, 480, 600)
        }
        assertTrue("溢出提示应说明原因", overflow.message?.contains("超出当天") == true)
    }
}
