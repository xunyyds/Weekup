package com.xunyyds.kelaile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XlsxTimetableTest {

    private fun grid(): List<List<String?>> = listOf(
        listOf("节次", "星期一", "星期二", "星期三", "星期四", "星期五"),
        listOf("第一二节 08:20-10:00", "JAVA程序设计基础 6-19周 致广楼313", "思想道德与法治 5-16周 知新楼508", null, null, null),
        listOf("第三四节 10:20-12:00", null, null, "公共英语(一) 5-16周 知新楼302", null, "高等数学I 5-16周 图书馆304"),
        listOf("备注:", "军事技能 3-4周;军事理论 2-19周;", null, null, null, null),
    )

    @Test
    fun parsesCoursesWithWeekdayAndPeriod() {
        val result = XlsxTimetable.parse(grid())
        assertEquals(4, result.courses.size)
        val java = result.courses.first { it.name.contains("JAVA") }
        assertEquals(1, java.weekday)
        assertEquals(1, java.startPeriod)
        assertEquals(2, java.endPeriod)
        assertEquals((6..19).toList(), java.weeks)
        assertTrue("教室应被识别", java.location.contains("致广楼"))
    }

    @Test
    fun periodTimingsDerivedFromLabel() {
        val periods = XlsxTimetable.parse(grid()).periods
        val first = periods.first { it.period == 1 }
        assertEquals(8 * 60 + 20, first.startMinutes)

        val second = periods.first { it.period == 2 }
        assertEquals(10 * 60, second.endMinutes)
    }

    @Test
    fun noteRowIsSkipped() {
        val names = XlsxTimetable.parse(grid()).courses.map { it.name }
        assertTrue("备注行不应产生课程", names.none { it.startsWith("备注") })
        assertTrue("备注内容不应产生课程", names.none { it.contains("军事技能") })
    }

    @Test
    fun singleEvenWeeks() {
        val g = listOf(
            listOf(null, "星期一", "星期二"),
            listOf("第一节", "数学 1-6周(单)", null),
        )
        val weeks = XlsxTimetable.parse(g).courses.single().weeks
        assertEquals(listOf(1, 3, 5), weeks)
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingWeekdayHeaderFails() {
        XlsxTimetable.parse(listOf(listOf("a"), listOf("b")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyGridFails() {
        XlsxTimetable.parse(emptyList())
    }

    @Test
    fun cellWithoutWeekIsIgnored() {
        val g = listOf(
            listOf(null, "星期一"),
            listOf("第一节", "没有周次的文字"),
        )
        val result = runCatching { XlsxTimetable.parse(g) }
        assertTrue("无周次的格子不应产出课程", result.isFailure)
    }
}
