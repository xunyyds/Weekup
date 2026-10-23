package com.xunyyds.kelaile.ui.search

import com.xunyyds.kelaile.data.AppData
import com.xunyyds.kelaile.data.AppSettings
import com.xunyyds.kelaile.data.CourseSession
import com.xunyyds.kelaile.data.defaultDayPeriods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSuggestionsTest {

    private val dayMillis = 86_400_000L
    private val now = 1_700_000_000_000L

    private fun course(
        id: String,
        name: String,
        weekday: Int = 1,
        start: Int = 1,
        end: Int = 2,
        teacher: String = "",
        location: String = "",
        weeks: List<Int> = (1..20).toList(),
    ): CourseSession {
        require(weekday in 1..7) { "测试数据里的 weekday 必须落在 1..7，实际是 $weekday" }
        return CourseSession(id, name, weekday, start, end, weeks, teacher, location)
    }

    private fun data(courses: List<CourseSession>, currentWeek: Int = 5) = AppData(
        settings = AppSettings(periods = defaultDayPeriods(), weekCount = 20, currentWeek = currentWeek),
        courses = courses,
    )

    private fun suggest(
        data: AppData,
        history: List<String> = emptyList(),
        times: Map<String, Long> = emptyMap(),
        at: Long = now,
    ) = SearchSuggestions.suggest(data, history, times, at)

    private fun dayOfWeekOf(millis: Long): Int {
        val zone = java.time.ZoneId.systemDefault()
        return java.time.Instant.ofEpochMilli(millis).atZone(zone).dayOfWeek.value
    }

    @Test
    fun emptyTimetableAndNoHistorySuggestsNothing() {
        assertTrue(suggest(data(emptyList())).isEmpty())
    }

    private fun otherDayThan(day: Int): Int = if (day >= 7) 1 else day + 1

    @Test
    fun withoutAnyHistoryItStillRecommendsTodaysCourses() {
        val today = dayOfWeekOf(now)
        val courses = listOf(
            course("a", "高等数学", weekday = today, start = 1),
            course("b", "大学英语", weekday = today, start = 3, end = 4),
            course("c", "体育", weekday = otherDayThan(today), start = 5, end = 6),
        )
        val result = suggest(data(courses))
        assertTrue("应该给出建议", result.isNotEmpty())
        assertTrue("今天有课的应该排在更前面", result.take(2).all { it.reason == "今天有课" })
        assertTrue("最多 6 条", result.size <= 6)
    }

    @Test
    fun onlyCoursesOfTheCurrentWeekAreMarkedAsThisWeek() {
        val today = dayOfWeekOf(now)
        val otherDay = otherDayThan(today)
        val courses = listOf(
            course("a", "只在第 1 周", weekday = otherDay, weeks = listOf(1)),
            course("b", "本周有课", weekday = otherDay, weeks = listOf(5, 6)),
        )
        val result = suggest(data(courses, currentWeek = 5))
        val byTitle = result.associateBy { it.entry.title }
        assertEquals("当前周的课要被标成本周有课", "本周有课", byTitle.getValue("本周有课").reason)
        assertTrue(
            "不在当前周的课不该被标成本周有课",
            byTitle.getValue("只在第 1 周").reason != "本周有课",
        )
    }

    @Test
    fun recentlySearchedCourseOutranksAnUntouchedOne() {
        val today = dayOfWeekOf(now)
        val otherDay = otherDayThan(today)
        val courses = listOf(
            course("a", "冷门选修", weekday = otherDay, teacher = "王老师"),
            course("b", "高等数学", weekday = otherDay, teacher = "李老师"),
        )
        val result = suggest(
            data(courses),
            history = listOf("高等数学"),
            times = mapOf("高等数学" to now),
        )
        assertEquals("搜过的课要排第一", "高等数学", result.first().entry.title)
        assertTrue("并且给出常搜的理由", result.first().reason.startsWith("常搜"))
    }

    @Test
    fun historyEntryFindsCourseByNameFragment() {
        val courses = listOf(course("a", "JAVA程序设计基础", weekday = 3))
        val result = suggest(data(courses), history = listOf("java"), times = mapOf("java" to now))
        assertEquals(1, result.size)
        assertEquals("JAVA程序设计基础", result.first().entry.title)
    }

    @Test
    fun oldHistoryDecaysAndLosesToFreshHistory() {
        val today = dayOfWeekOf(now)
        val otherDay = otherDayThan(today)
        val courses = listOf(
            course("a", "旧课", weekday = otherDay),
            course("b", "新课", weekday = otherDay),
        )
        val result = suggest(
            data(courses),
            history = listOf("旧课", "新课"),
            times = mapOf("旧课" to now - 55 * dayMillis, "新课" to now - dayMillis),
        )
        assertEquals("刚搜过的排前面", "新课", result.first().entry.title)
        val old = result.first { it.entry.title == "旧课" }
        val fresh = result.first { it.entry.title == "新课" }
        assertTrue("旧记录分数必须明显更低", old.score < fresh.score)
    }

    @Test
    fun historyOlderThanTheWindowIsIgnored() {
        val today = dayOfWeekOf(now)
        val otherDay = otherDayThan(today)
        val courses = listOf(course("a", "某课", weekday = otherDay))
        val fresh = suggest(data(courses), history = listOf("某课"), times = mapOf("某课" to now))
        val stale = suggest(
            data(courses),
            history = listOf("某课"),
            times = mapOf("某课" to now - 90 * dayMillis),
        )
        assertTrue("过期历史不该再抬高分数", stale.first().score < fresh.first().score)
    }

    @Test
    fun todayCourseBeatsAStaleSearch() {
        val today = dayOfWeekOf(now)
        val courses = listOf(
            course("a", "今天要上的课", weekday = today),
            course("b", "很久以前搜过的课", weekday = otherDayThan(today)),
        )
        val result = suggest(
            data(courses),
            history = listOf("很久以前搜过的课"),
            times = mapOf("很久以前搜过的课" to now - 50 * dayMillis),
        )
        assertEquals("今天的课应该压过陈旧历史", "今天要上的课", result.first().entry.title)
    }

    @Test
    fun frequentlyRepeatedCourseGetsHigherHeat() {
        val today = dayOfWeekOf(now)
        val otherDay = otherDayThan(today)
        val courses = listOf(
            course("a1", "高频课", weekday = otherDay, teacher = "同一老师"),
            course("a2", "高频课", weekday = otherDay, start = 3, end = 4, teacher = "同一老师"),
            course("a3", "高频课", weekday = otherDay, start = 5, end = 6, teacher = "同一老师"),
            course("b", "低频课", weekday = otherDay, teacher = "另一老师"),
        )
        val result = suggest(data(courses))
        val byTitle = result.associateBy { it.entry.title }
        val repeated = byTitle.getValue("高频课")
        val once = byTitle.getValue("低频课")
        assertTrue("重复出现的课热度更高", repeated.score > once.score)
        assertEquals("同名课程只建议一次", 1, result.count { it.entry.title == "高频课" })
    }

    @Test
    fun duplicatesCollapseToASingleSuggestionPerCourse() {
        val courses = listOf(course("a", "同一门课", weekday = 2), course("b", "同一门课", weekday = 3))
        val result = suggest(data(courses))
        assertEquals("同名课不该占两条建议", 1, result.count { it.entry.title == "同一门课" })
    }

    @Test
    fun searchedActionIsSuggestedWhenNoCourseMatches() {
        val courses = listOf(course("a", "高等数学"))
        val result = suggest(data(courses), history = listOf("导入"), times = mapOf("导入" to now))
        assertTrue(
            "搜过操作词时应把对应动作推上来",
            result.any { it.entry.title.contains("导入") && it.reason == "常搜的操作" },
        )
    }

    @Test
    fun resultsAreDeterministicForTheSameInputs() {
        val today = dayOfWeekOf(now)
        val courses = listOf(
            course("a", "甲课", weekday = today),
            course("b", "乙课", weekday = today, start = 3, end = 4),
            course("c", "丙课", weekday = otherDayThan(today)),
        )
        val first = suggest(data(courses), history = listOf("甲课"), times = mapOf("甲课" to now))
        val second = suggest(data(courses), history = listOf("甲课"), times = mapOf("甲课" to now))
        assertEquals(first.map { it.entry.id }, second.map { it.entry.id })
        assertEquals(first.map { it.score }, second.map { it.score })
    }

    @Test
    fun neverExceedsTheRequestedCap() {
        val today = dayOfWeekOf(now)
        val courses = (1..20).map { course("c$it", "课程$it", weekday = today) }
        assertTrue(suggest(data(courses)).size <= 6)
        assertEquals(2, SearchSuggestions.suggest(data(courses), emptyList(), emptyMap(), now, max = 2).size)
    }

    @Test
    fun historyWithUnknownTimestampStillContributes() {
        val courses = listOf(course("a", "没时间戳的课", weekday = 3))
        val result = suggest(data(courses), history = listOf("没时间戳的课"), times = emptyMap())
        assertTrue("没有时间戳也要能给出建议", result.isNotEmpty())
        assertEquals("没时间戳的课", result.first().entry.title)
    }
}


