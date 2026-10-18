package com.xu.wake.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用真实抓取的页面结构做回归。
 *
 * 登录后首页同时存在「课表预览小部件」（课名截断、无周次）与用户打开的理论课表，
 * 提取脚本会把两张表一起交出来，解析器必须挑出真正的那张。
 *
 * 夹具已去除学校、楼名与个人信息。
 */
class RealPageParseTest {

    private fun page(): String {
        val stream = javaClass.classLoader!!.getResourceAsStream("page-two-tables.html")
        requireNotNull(stream) { "测试夹具缺失" }
        return stream.readBytes().toString(Charsets.UTF_8)
    }

    @Test fun picksTheRealTimetableOutOfTwoTables() {
        val result = SchoolParser.parse(page())
        assertTrue("应当解析出课程，实际 ${result.courses.size} 门", result.courses.size >= 10)
        val java = result.courses.first { it.name.contains("JAVA") }
        assertEquals("周次应为区间末端的 19 周", (6..19).toList(), java.weeks)
        assertEquals(1, java.weekday)
        assertEquals(1, java.startPeriod)
        assertEquals(2, java.endPeriod)
    }

    /** 教室取站点标注的「教室」，而不是块尾的「(讲课:56)」这类附注。 */
    @Test fun roomComesFromTheLabelledField() {
        val java = SchoolParser.parse(page()).courses.first { it.name.contains("JAVA") }
        assertEquals("A栋313", java.location)
        assertTrue("教师应被识别", java.teacher.isNotBlank())
    }

    @Test fun periodsCarryRealTimes() {
        val periods = SchoolParser.parse(page()).periods
        assertTrue("节次时间应被识别", periods.size >= 8)
        val first = periods.first { it.period == 1 }
        assertTrue("第一节应有起始时间", first.startMinutes > 0)
    }
}
