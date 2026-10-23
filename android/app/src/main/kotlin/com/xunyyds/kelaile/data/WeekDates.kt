package com.xunyyds.kelaile.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object WeekDates {

    private val md = DateTimeFormatter.ofPattern("M/d")

    fun weekAnchor(start: LocalDate): LocalDate =
        start.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

    fun on(start: LocalDate, week: Int, weekday: Int): LocalDate {
        val anchor = weekAnchor(start)
        return anchor.plusDays(((week - 1).toLong()) * 7 + (weekday - 1))
    }

    fun range(start: LocalDate, week: Int): Pair<LocalDate, LocalDate> {
        val anchor = weekAnchor(start)
        val from = anchor.plusDays(((week - 1).toLong()) * 7)
        return from to from.plusDays(6)
    }

    fun weekOf(start: LocalDate, today: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(weekAnchor(start), today)
        if (days < 0) return 1
        return (days / 7).toInt() + 1
    }

    fun startForWeek(week: Int, today: LocalDate = LocalDate.now()): LocalDate {
        val safeWeek = week.coerceAtLeast(1)
        val currentMonday = weekAnchor(today)
        return currentMonday.minusWeeks((safeWeek - 1).toLong())
    }

    fun rangeLabel(start: LocalDate, week: Int): String {
        val (from, to) = range(start, week)
        return from.format(md) + "–" + to.format(md)
    }

    fun dayLabel(start: LocalDate, week: Int, weekday: Int): String =
        on(start, week, weekday).format(md)
}
