package com.xunyyds.kelaile.data

import java.time.LocalDate

/**
 * 学期设置之间的联动推算（纯逻辑，无 Compose / Android 依赖）。
 *
 * 「学期开始 / 学期结束 / 当前周 / 总周数」本来就互相牵制，但旧实现让用户
 * 各填各的，填错也没有任何提示 —— 于是会出现「现在是第 7 周，开学却填在
 * 两周后」这种自相矛盾的状态，课表日期一路错下去。
 *
 * 这里不做任何自动改写：所有函数只负责*算出联动结果*供 UI 展示，由用户
 * 二次确认后才落地。设置项本身全部保留。
 */
object SemesterLink {

    /** 一次联动推算的结果；[changes] 为空表示四个字段本来就是自洽的。 */
    data class Plan(
        val start: LocalDate,
        val end: LocalDate,
        val currentWeek: Int,
        val weekCount: Int,
        /** 人类可读的变更说明，逐条给确认弹窗用。 */
        val changes: List<String>,
    ) {
        val isEmpty: Boolean get() = changes.isEmpty()
    }

    /** 只看日期：按 [start] + [weekCount] 推出结束日（末周周日）。 */
    fun endFor(start: LocalDate, weekCount: Int): LocalDate =
        WeekDates.range(start, weekCount.coerceAtLeast(1)).second

    /** 只看日期：由 [start] 与 [end] 反推能容纳多少周。 */
    fun weekCountFor(start: LocalDate, end: LocalDate): Int {
        val firstMonday = WeekDates.weekAnchor(start)
        val days = java.time.temporal.ChronoUnit.DAYS.between(firstMonday, end)
        if (days < 0) return 1
        return (days / 7).toInt() + 1
    }

    /**
     * 用户改了「当前周」：以今天所在周为锚，反推开学的周一。
     *
     * [today] 必须在目标周内，因此开始日取 [WeekDates.startForWeek]。
     * 总周数不变，结束日按新的开始日重推 —— 否则改了开学日、结束日却留在原处，
     * 学期长度会被悄悄改掉。
     */
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

    /**
     * 用户改了「学期开始」：重算今天是第几周，并把结束日对齐到末周周日。
     *
     * 这是最常见的场景 —— 用户拿到校历上的开学日期直接填，不希望还要手动
     * 数「今天上到第几周」。
     */
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

    /**
     * 用户改了「总周数」：结束日跟着改，当前周按新周期钳制。
     */
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

    /**
     * 用户改了「学期结束」：总周数跟着变，当前周按新周期钳制。
     */
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

    /**
     * 一致性检查：找出当前设置里互相矛盾的地方（不改动任何值）。
     *
     * 例如结束日与总周数对不上、当前周与开学日期对不上。UI 用它显示提示。
     */
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

    /** 把 [Plan] 落到设置上；只在用户确认后调用。 */
    fun apply(settings: AppSettings, plan: Plan): AppSettings = settings.copy(
        start = plan.start,
        end = plan.end,
        currentWeek = plan.currentWeek.coerceIn(1, plan.weekCount),
        weekCount = plan.weekCount,
    )

    private fun fmt(date: LocalDate): String =
        "${date.monthValue}/${date.dayOfMonth}"
}
