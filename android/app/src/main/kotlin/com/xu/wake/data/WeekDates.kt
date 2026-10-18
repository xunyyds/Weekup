package com.xu.wake.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object WeekDates {

    private val md = DateTimeFormatter.ofPattern("M/d")

    fun on(start: LocalDate, week: Int, weekday: Int): LocalDate {
        val startDow = start.dayOfWeek.value
        val offset = ((weekday - startDow) % 7 + 7) % 7
        return start.plusDays(((week - 1).toLong()) * 7 + offset)
    }

    fun range(start: LocalDate, week: Int): Pair<LocalDate, LocalDate> {
        val from = start.plusDays(((week - 1).toLong()) * 7)
        return from to from.plusDays(6)
    }

    fun weekOf(start: LocalDate, today: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(start, today)
        if (days < 0) return 1
        return (days / 7).toInt() + 1
    }

    fun rangeLabel(start: LocalDate, week: Int): String {
        val (from, to) = range(start, week)
        return from.format(md) + "–" + to.format(md)
    }

    fun dayLabel(start: LocalDate, week: Int, weekday: Int): String =
        on(start, week, weekday).format(md)
}
