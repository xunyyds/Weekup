package com.xu.wake.data

import org.junit.Assert.*
import org.junit.Test

class SchoolParserTest {
    private fun html(week: String = "1-6周(单)", span: String = "") = """
        <p>总周数：6 当前周：2</p><table>
        <tr><th>节次</th><th>星期一</th><th>星期二</th></tr>
        <tr><td>第1节 08:00-08:45</td><td $span><div class="kbcontent">数学<br>$week<br><span title="教师">张老师</span><br><span title="教室">A101</span></div></td><td></td></tr></table>
    """.trimIndent()
    @Test fun parsesOddWeeksAndMetadata() {
        val result = SchoolParser.parse(html())
        assertEquals(listOf(1, 3, 5), result.courses.single().weeks)
        assertEquals("数学", result.courses.single().name)
        assertEquals("张老师", result.courses.single().teacher)
        assertEquals("A101", result.courses.single().location)
        assertEquals(6, result.weekCount)
        assertEquals(2, result.currentWeek)
        assertEquals(PeriodTime(1, 480, 525), result.periods.single())
    }
    @Test fun parsesEvenAndDisjointWeeks() {
        assertEquals(listOf(2, 4, 6), SchoolParser.parse(html("1-6(双周)")).courses.single().weeks)
        assertEquals(listOf(1, 3, 4, 6), SchoolParser.parse(html("1,3-4、6周")).courses.single().weeks)
    }
    @Test fun refusesUnknownAndAmbiguousLayouts() {
        listOf("<table><tr><td>数学 1-6周</td></tr></table>", html("每周"), html("6-1周"), html("0-6周"), html("1-6周", "rowspan='2'"), html() + html()).forEach {
            assertThrows(IllegalArgumentException::class.java) { SchoolParser.parse(it) }
        }
    }
    @Test fun recognizesSchoolOnlyFromPageIdentity() {
        listOf(
            "<title>长青大学 - 教务管理系统</title>" to "长青大学",
            "<header><h1>北京大学教务处</h1></header>" to "北京大学",
            "<meta name='school' content='长沙市实验学校'>" to "长沙市实验学校",
            "<meta property='og:site_name' content='清华大学'>" to "清华大学",
        ).forEach { (identity, expected) -> assertEquals(expected, SchoolParser.parse(identity + html()).school) }
    }
    @Test fun missingGenericConflictingAndUntrustedSchoolEvidenceReturnsNull() {
        listOf("", "<title>教务管理系统</title>", "<title>未知学校</title>",
            "<title>学校教务系统</title>", "<title>高等学校</title>",
            "<p>北京大学</p>", "<input value='北京大学'>", "<meta name='description' content='北京大学'>",
            "<form><h1>北京大学</h1></form>", "<h1 hidden>北京大学</h1>",
            "<title>北京大学</title><h1>清华大学</h1>")
            .forEach { assertNull(it, SchoolParser.parse(it + html()).school) }
    }
    @Test fun preservesExistingSchoolAndDefaultsToNull() {
        assertEquals("已有学校", SchoolParser.parse("<title>北京大学</title>" + html(), " 已有学校 ").school)
        assertNull(SchoolParser.parse(html(), " ").school)
        assertNull(SchoolImport(emptyList()).school)
    }
    @Test fun ignoresUnrelatedFirstTable() {
        val source = "<table><tr><td>登录</td></tr></table>" + html()
        assertEquals(1, SchoolParser.parse(source).courses.size)
    }

    @Test fun parsesSeveralCoursesInOneCell() {
        val source = """
            <table>
            <tr><th>节次</th><th>星期一</th><th>星期二</th></tr>
            <tr><td>第1节 08:00-08:45</td><td>
              <div class="kbcontent">高等数学I<br>莫从庆<br>5-16(周)[01-02节]<br>图书馆304<br>----------------------<br>大学英语<br>李梅<br>3-8(周)[01-02节]<br>教1-201</div>
            </td><td></td></tr></table>
        """.trimIndent()
        val courses = SchoolParser.parse(source).courses
        assertEquals(2, courses.size)
        assertEquals(setOf("高等数学I", "大学英语"), courses.map { it.name }.toSet())
        val math = courses.first { it.name == "高等数学I" }
        assertEquals((5..16).toList(), math.weeks)
        assertEquals("图书馆304", math.location)
        assertEquals("莫从庆", math.teacher)
        val english = courses.first { it.name == "大学英语" }
        assertEquals((3..8).toList(), english.weeks)
        assertEquals("教1-201", english.location)
    }

    @Test fun noteRowDoesNotBlockImport() {
        val source = """
            <table>
            <tr><th>节次</th><th>星期一</th><th>星期二</th></tr>
            <tr><td>第1节 08:00-08:45</td><td><div class="kbcontent">数学<br>1-6周(单)</div></td><td></td></tr>
            <tr><th>备注:</th><td colspan="2">军事技能 3-4周;军事理论 2-19周;劳动实践 2-19周;</td></tr>
            </table>
        """.trimIndent()
        val courses = SchoolParser.parse(source).courses
        assertEquals(1, courses.size)
        assertEquals("数学", courses.single().name)
    }

    @Test fun unreadableCellIsSkippedNotFatal() {
        val source = """
            <table>
            <tr><th>节次</th><th>星期一</th><th>星期二</th></tr>
            <tr><td>第1节 08:00-08:45</td><td><div class="kbcontent">数学<br>1-6周(单)</div></td><td><div class="kbcontent">没有周次的文字</div></td></tr></table>
        """.trimIndent()
        val courses = SchoolParser.parse(source).courses
        assertEquals(1, courses.size)
        assertEquals("数学", courses.single().name)
    }
}
