package com.xunyyds.kelaile.domain

import com.xunyyds.kelaile.data.AppData
import com.xunyyds.kelaile.data.CourseSession
import com.xunyyds.kelaile.data.PeriodTime
import com.xunyyds.kelaile.data.SchoolImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleActionsImportTest {

    private fun localPeriods() = (1..14).map {
        PeriodTime(it, 480 + (it - 1) * 55, 480 + (it - 1) * 55 + 45)
    }

    private fun local() = AppData().copy(
        settings = AppData().settings.copy(periods = localPeriods(), weekCount = 20),
    )

    private fun imported() = SchoolImport(
        courses = listOf(
            CourseSession("a", "JAVA程序设计基础", 1, 1, 2, (6..19).toList(), "伍老师", "A栋313"),
            CourseSession("b", "思想道德与法治", 2, 1, 2, (5..16).toList(), "苏老师", "B栋508"),
        ),
        periods = listOf(
            PeriodTime(1, 500, 540),
            PeriodTime(2, 540, 600),
            PeriodTime(3, 620, 660),
            PeriodTime(4, 660, 720),
            PeriodTime(5, 860, 900),
            PeriodTime(6, 900, 960),
            PeriodTime(7, 1020, 1060),
            PeriodTime(8, 1060, 1120),
            PeriodTime(9, 1140, 1180),
            PeriodTime(10, 1180, 1240),
        ),
        school = "长青大学",
    )

    @Test fun importedPeriodsWinAndNoOverlapSurvives() {
        val result = ScheduleActions.importSchool(local(), imported())
        val periods = result.settings.periods
        assertEquals("站点第 10 节结束于 1240，本地 11-14 节都排在它之前，应被丢弃", 10, periods.size)
        assertTrue(
            "合并后不得再有重叠",
            periods.sortedBy { it.period }.zipWithNext().all { (a, b) -> a.endMinutes <= b.startMinutes },
        )
    }

    @Test fun coursesAndSchoolLand() {
        val result = ScheduleActions.importSchool(local(), imported())
        assertEquals(2, result.courses.size)
        assertEquals("长青大学", result.settings.school)
        assertTrue("课程节次必须在设置里存在", result.courses.all { c ->
            (c.startPeriod..c.endPeriod).all { p -> result.settings.periods.any { it.period == p } }
        })
    }

    @Test fun laterLocalPeriodsAreKept() {
        val late = local().copy(
            settings = local().settings.copy(
                periods = listOf(PeriodTime(29, 1300, 1340), PeriodTime(30, 1340, 1380)),
            ),
        )
        val result = ScheduleActions.importSchool(late, imported())
        assertEquals("导入 10 节 + 其后的本地 2 节", 12, result.settings.periods.size)
    }

    @Test fun emptyImportedPeriodsKeepLocalSchedule() {
        val result = ScheduleActions.importSchool(local(), imported().copy(periods = emptyList()))
        assertEquals(14, result.settings.periods.size)
        assertEquals(2, result.courses.size)
    }
}
