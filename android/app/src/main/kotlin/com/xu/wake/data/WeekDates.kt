package com.xu.wake.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object WeekDates {

    private val md = DateTimeFormatter.ofPattern("M/d")

    /**
     * 学期第 1 周的周一。
     *
     * 学期开始日不一定是周一（用户可能填开学当天、报到日或某个周三）。
     * 所有「第 N 周」的日期都必须以这个周一为基准，否则 [range] 会把开始日
     * 当成周一往后推，而 [on] 又按真实星期对齐 —— 同一个周次会算出两套互相
     * 矛盾的日期（例如周一是 11/2、周二却是 10/27）。
     */
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

    /**
     * 由「今天是第 [week] 周」反推学期开始日。
     *
     * 这是 [weekOf] 的逆运算：用户只知道现在上到第几周，不该被迫去翻校历填
     * 开学日期。返回的日期取该周的周一，保证 [weekOf] 能原样算回 [week]。
     *
     * [week] 小于 1 时按第 1 周处理，避免算出未来的「开学日」。
     */
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
