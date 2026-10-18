package com.xu.wake.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 周次与真实日期的换算。
 *
 * 约定：[AppSettings.start] 是第 1 周的第一天，各周按 7 天连续排布，
 * 因此第 N 周就是从 start 起第 (N-1)*7 天开始的那一段。
 *
 * 全部为纯函数：课表、表头日期、选周面板与「回到当前周」都读同一套换算，
 * 不会出现某处按周算、另一处按日期算而彼此错位。
 */
object WeekDates {

    private val md = DateTimeFormatter.ofPattern("M/d")

    /** 第 [week] 周内、星期 [weekday]（1=周一）对应的真实日期。 */
    fun on(start: LocalDate, week: Int, weekday: Int): LocalDate {
        val startDow = start.dayOfWeek.value
        val offset = ((weekday - startDow) % 7 + 7) % 7
        return start.plusDays(((week - 1).toLong()) * 7 + offset)
    }

    /** 第 [week] 周的起止日期。 */
    fun range(start: LocalDate, week: Int): Pair<LocalDate, LocalDate> {
        val from = start.plusDays(((week - 1).toLong()) * 7)
        return from to from.plusDays(6)
    }

    /** [today] 落在第几周。早于开学日期时归到第 1 周，避免出现 0 或负数。 */
    fun weekOf(start: LocalDate, today: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(start, today)
        if (days < 0) return 1
        return (days / 7).toInt() + 1
    }

    /** 「10/20–10/26」这样的短区间标签。 */
    fun rangeLabel(start: LocalDate, week: Int): String {
        val (from, to) = range(start, week)
        return from.format(md) + "–" + to.format(md)
    }

    /** 表头用的单日短标签，例如「10/20」。 */
    fun dayLabel(start: LocalDate, week: Int, weekday: Int): String =
        on(start, week, weekday).format(md)
}
