package com.xunyyds.kelaile.data

import java.time.ZoneId

data class ReminderEvent(val course: CourseSession, val startsAtMillis: Long, val triggerAtMillis: Long)

object ReminderPlan {
    fun events(data: AppData, nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault(), limit: Int = 300): List<ReminderEvent> {
        data.validate()
        require(limit in 0..300)
        if (!data.settings.reminders) return emptyList()
        val s = data.settings
        val periods = s.periods.associateBy { it.period }
        return data.courses.flatMap { course ->
            val period = periods.getValue(course.startPeriod)
            course.weeks.distinct().mapNotNull { week ->
                val day = s.start.plusDays((week - 1) * 7L + (course.weekday - s.start.dayOfWeek.value + 7) % 7)
                if (day.isAfter(s.end)) return@mapNotNull null
                val start = day.atStartOfDay().plusMinutes(period.startMinutes.toLong()).atZone(zone).toInstant().toEpochMilli()
                val trigger = start - s.reminderMinutes * 60_000L
                if (trigger <= nowMillis) null else ReminderEvent(course, start, trigger)
            }
        }.sortedWith(compareBy<ReminderEvent> { it.triggerAtMillis }.thenBy { it.course.id }).take(limit)
    }
}
