package com.xunyyds.kelaile.data

import java.time.LocalDate

object SemesterLink {

    data class Plan(
        val start: LocalDate,
        val end: LocalDate,
        val currentWeek: Int,
        val weekCount: Int,

        val changes: List<String>,
    ) {
        val isEmpty: Boolean get() = changes.isEmpty()
    }

    fun endFor(start: LocalDate, weekCount: Int): LocalDate =
        WeekDates.range(start, weekCount.coerceAtLeast(1)).second

    fun weekCountFor(start: LocalDate, end: LocalDate): Int {
        val firstMonday = WeekDates.weekAnchor(start)
        val days = java.time.temporal.ChronoUnit.DAYS.between(firstMonday, end)
        if (days < 0) return 1
        return (days / 7).toInt() + 1
    }

    fun fromCurrentWeek(
        settings: AppSettings,
        newCurrentWeek: Int,
        today: LocalDate = LocalDate.now(),
    ): Plan {
        val week = newCurrentWeek.coerceIn(1, settings.weekCount)
        val start = WeekDates.startForWeek(week, today)
        val end = endFor(start, settings.weekCount)
        val changes = buildList {
            if (start != settings.start) {
                add("学期开始：${fmt(settings.start)} → ${fmt(start)}")
            }
            if (end != settings.end) {
                add("学期结束：${fmt(settings.end)} → ${fmt(end)}（总周数不变，随开学日平移）")
            }
            val derivedWeek = WeekDates.weekOf(start, today)
            if (derivedWeek != settings.currentWeek) {
                add("当前周：第 ${settings.currentWeek} 周 → 第 $derivedWeek 周")
            }
        }
        return Plan(start, end, week, settings.weekCount, changes)
    }

    fun fromStart(
        settings: AppSettings,
        newStart: LocalDate,
        today: LocalDate = LocalDate.now(),
    ): Plan {
        val week = WeekDates.weekOf(newStart, today).coerceIn(1, settings.weekCount)
        val end = endFor(newStart, settings.weekCount)
        val changes = buildList {
            if (newStart != settings.start) {
                add("学期开始：${fmt(settings.start)} → ${fmt(newStart)}")
            }
            if (week != settings.currentWeek) {
                add("当前周：第 ${settings.currentWeek} 周 → 第 $week 周")
            }
            if (end != settings.end) {
                add("学期结束：${fmt(settings.end)} → ${fmt(end)}")
            }
        }
        return Plan(newStart, end, week, settings.weekCount, changes)
    }

    fun fromWeekCount(
        settings: AppSettings,
        newWeekCount: Int,
    ): Plan {
        val count = newWeekCount.coerceAtLeast(1)
        val end = endFor(settings.start, count)
        val week = settings.currentWeek.coerceIn(1, count)
        val changes = buildList {
            if (count != settings.weekCount) {
                add("总周数：${settings.weekCount} 周 → $count 周")
            }
            if (end != settings.end) {
                add("学期结束：${fmt(settings.end)} → ${fmt(end)}")
            }
            if (week != settings.currentWeek) {
                add("当前周：第 ${settings.currentWeek} 周 → 第 $week 周")
            }
        }
        return Plan(settings.start, end, week, count, changes)
    }

    fun fromEnd(
        settings: AppSettings,
        newEnd: LocalDate,
        today: LocalDate = LocalDate.now(),
    ): Plan {
        val count = weekCountFor(settings.start, newEnd).coerceIn(1, 60)
        val end = endFor(settings.start, count)
        val week = WeekDates.weekOf(settings.start, today).coerceIn(1, count)
        val changes = buildList {
            if (newEnd != settings.end) {
                add("学期结束：${fmt(settings.end)} → ${fmt(end)}")
            }
            if (count != settings.weekCount) {
                add("总周数：${settings.weekCount} 周 → $count 周")
            }
            if (week != settings.currentWeek) {
                add("当前周：第 ${settings.currentWeek} 周 → 第 $week 周")
            }
        }
        return Plan(settings.start, end, week, count, changes)
    }

    fun issues(settings: AppSettings, today: LocalDate = LocalDate.now()): List<String> =
        buildList {
            val expectedEnd = endFor(settings.start, settings.weekCount)
            if (expectedEnd != settings.end) {
                add("学期结束与总周数不一致：按 ${settings.weekCount} 周应为 ${fmt(expectedEnd)}")
            }
            val week = WeekDates.weekOf(settings.start, today)
            if (today.isBefore(WeekDates.weekAnchor(settings.start))) {
                add("今天早于学期开始，当前周暂按第 1 周显示")
            } else if (week != settings.currentWeek) {
                add("当前周与开学日期不一致：按开学日期今天应是第 $week 周")
            }
        }

    fun apply(settings: AppSettings, plan: Plan): AppSettings = settings.copy(
        start = plan.start,
        end = plan.end,
        currentWeek = plan.currentWeek.coerceIn(1, plan.weekCount),
        weekCount = plan.weekCount,
    )

    private fun fmt(date: LocalDate): String =
        "${date.monthValue}/${date.dayOfMonth}"
}
