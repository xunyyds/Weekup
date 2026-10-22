package com.xunyyds.kelaile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SemesterLinkTest {

    private val today = LocalDate.of(2026, 10, 20) // 周二

    private fun settings(
        start: LocalDate = LocalDate.of(2026, 9, 15), // 周二开学（真实 bug 场景）
        end: LocalDate = LocalDate.of(2027, 2, 1),
        currentWeek: Int = 6,
        weekCount: Int = 20,
    ) = AppSettings(
        start = start,
        end = end,
        currentWeek = currentWeek,
        weekCount = weekCount,
        periods = defaultDayPeriods(),
    )

    /** 确认后的设置必须自洽：结束日=末周周日、当前周与开学日一致。 */
    private fun assertCoherent(s: AppSettings) {
        assertEquals(
            WeekDates.range(s.start, s.weekCount).second,
            s.end,
        )
        assertEquals(WeekDates.weekOf(s.start, today), s.currentWeek)
        assertTrue(s.currentWeek in 1..s.weekCount)
    }

    @Test fun fromCurrentWeekDerivesStartFromWeek() {
        val s = settings()
        val plan = SemesterLink.fromCurrentWeek(s, 7, today)

        // 今天在第 7 周 → 第 7 周周一 = 10/19，往前推 6 周 = 9/7。
        assertEquals(LocalDate.of(2026, 9, 7), plan.start)
        assertEquals(7, plan.currentWeek)
        assertEquals(WeekDates.weekOf(plan.start, today), 7)
        assertTrue(plan.changes.any { it.startsWith("学期开始") })
    }

    @Test fun fromCurrentWeekShiftsEndToKeepWeekCount() {
        val s = settings()
        val plan = SemesterLink.fromCurrentWeek(s, 3, today)
        // 总周数保留，结束日随开学日平移，学期长度不会被悄悄改掉。
        assertEquals(s.weekCount, plan.weekCount)
        assertEquals(WeekDates.range(plan.start, s.weekCount).second, plan.end)
    }

    @Test fun fromStartComputesWeekAndEnd() {
        val s = settings()
        val start = LocalDate.of(2026, 9, 7) // 周一
        val plan = SemesterLink.fromStart(s, start, today)

        assertEquals(start, plan.start)
        // 9/7 到 10/20 是第 7 周。
        assertEquals(7, plan.currentWeek)
        assertEquals(WeekDates.range(start, s.weekCount).second, plan.end)
        assertEquals(s.weekCount, plan.weekCount)
    }

    @Test fun fromStartIsIdempotentWhenAlreadyCoherent() {
        val start = LocalDate.of(2026, 9, 7)
        val s = settings(
            start = start,
            end = SemesterLink.endFor(start, 20),
            currentWeek = 7,
        )
        val plan = SemesterLink.fromStart(s, start, today)
        assertTrue("已自洽时不该有任何变更：${plan.changes}", plan.isEmpty)
    }

    @Test fun fromWeekCountAlignsEnd() {
        val start = LocalDate.of(2026, 9, 7)
        val s = settings(start = start, end = SemesterLink.endFor(start, 20), currentWeek = 7)
        val plan = SemesterLink.fromWeekCount(s, 18)

        assertEquals(18, plan.weekCount)
        assertEquals(WeekDates.range(start, 18).second, plan.end)
        assertEquals(7, plan.currentWeek) // 18 周仍含第 7 周
    }

    @Test fun fromWeekCountClampsCurrentWeek() {
        val start = LocalDate.of(2026, 9, 7)
        val s = settings(start = start, end = SemesterLink.endFor(start, 20), currentWeek = 7)
        val plan = SemesterLink.fromWeekCount(s, 4)

        assertEquals(4, plan.weekCount)
        assertEquals(4, plan.currentWeek) // 7 被钳到 4
    }

    @Test fun fromEndAlignsWeekCount() {
        val start = LocalDate.of(2026, 9, 7)
        val s = settings(start = start, end = SemesterLink.endFor(start, 20), currentWeek = 7)
        // 选一个末周周日落在第 10 周的日子。
        val target = LocalDate.of(2026, 11, 15)
        val plan = SemesterLink.fromEnd(s, target, today)

        assertEquals(10, plan.weekCount)
        assertEquals(WeekDates.range(start, 10).second, plan.end)
    }

    @Test fun applyProducesCoherentSettings() {
        val s = settings()
        val plan = SemesterLink.fromCurrentWeek(s, 7, today)
        assertCoherent(SemesterLink.apply(s, plan))
    }

    @Test fun applyKeepsUnrelatedFields() {
        val s = settings()
        val applied = SemesterLink.apply(s, SemesterLink.fromCurrentWeek(s, 7, today))
        // 联动只动开学/结束/当前周/总周数，其余设置必须原样保留。
        assertEquals(s.periods, applied.periods)
        assertEquals(s.firstDay, applied.firstDay)
        assertEquals(s.lastDay, applied.lastDay)
        assertEquals(s.weekStart, applied.weekStart)
        assertEquals(s.theme, applied.theme)
        assertEquals(s.schoolUrl, applied.schoolUrl)
    }

    @Test fun issuesDetectsInconsistentState() {
        val start = LocalDate.of(2026, 9, 7)
        // 结束日与 20 周对不上，当前周也不是今天所在的第 7 周。
        val broken = settings(
            start = start,
            end = LocalDate.of(2027, 6, 1),
            currentWeek = 2,
        )
        val issues = SemesterLink.issues(broken, today)
        assertTrue(issues.any { it.contains("学期结束") })
        assertTrue(issues.any { it.contains("当前周") })
    }

    @Test fun issuesEmptyWhenCoherent() {
        val start = LocalDate.of(2026, 9, 7)
        val good = settings(
            start = start,
            end = SemesterLink.endFor(start, 20),
            currentWeek = 7,
        )
        assertTrue("自洽时不该报问题：${SemesterLink.issues(good, today)}",
            SemesterLink.issues(good, today).isEmpty())
    }

    @Test fun issuesFlagsFutureStart() {
        // 开学在未来：结束日也得在开学之后，否则 AppSettings 自身就拒绝。
        val s = settings(
            start = LocalDate.of(2027, 3, 1),
            end = LocalDate.of(2027, 7, 1),
            currentWeek = 1,
        )
        assertTrue(SemesterLink.issues(s, today).any { it.contains("早于学期开始") })
    }

    @Test fun everyChangeLineIsHumanReadable() {
        val s = settings()
        val plan = SemesterLink.fromCurrentWeek(s, 9, today)
        assertFalse(plan.isEmpty)
        // 每行都得是「字段：旧 → 新」的形状，确认弹窗才能读懂。
        plan.changes.forEach { line ->
            assertTrue("变更说明格式不对：$line", line.contains("→"))
            assertTrue("变更说明缺少字段名：$line", line.contains("："))
        }
    }
}
