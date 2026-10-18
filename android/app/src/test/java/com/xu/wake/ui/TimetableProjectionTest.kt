package com.xu.wake.ui

import com.xu.wake.data.AppData
import com.xu.wake.data.AppSettings
import com.xu.wake.data.CourseSession
import com.xu.wake.data.PeriodTime
import com.xu.wake.data.defaultDayPeriods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class TimetableProjectionTest {

    private data class LegacyPlaced(
        val course: CourseSession,
        val lane: Int,
        val laneCount: Int,
        val startIndex: Int,
        val endIndex: Int,
    )

    private fun legacyProjectWeek(
        courses: List<CourseSession>,
        week: Int,
        s: AppSettings,
    ): Pair<List<Int>, Map<Int, List<LegacyPlaced>>> {
        val days = (s.firstDay..s.lastDay).sortedBy { (it - s.weekStart + 7) % 7 }
        val weekCourses = courses.filter { week in it.weeks }
        val byDay = LinkedHashMap<Int, List<LegacyPlaced>>()
        days.forEach { day ->
            val dayCourses = weekCourses.filter { it.weekday == day }.sortedBy { it.startPeriod }
            byDay[day] = dayCourses.map { course ->
                val startIndex = s.periods.indexOfFirst { it.period == course.startPeriod }
                val endIndex = s.periods.indexOfFirst { it.period == course.endPeriod }
                val conflicts = dayCourses.filter { it.startPeriod <= course.endPeriod && it.endPeriod >= course.startPeriod }
                val lane = conflicts.indexOfFirst { it.id == course.id }.coerceAtLeast(0)
                LegacyPlaced(course, lane, conflicts.size.coerceAtLeast(1), startIndex, endIndex)
            }
        }
        return days to byDay
    }

    private fun project(courses: List<CourseSession>, week: Int, s: AppSettings): WeekProjection =
        projectWeek(courses, week, s.firstDay, s.lastDay, s.weekStart, s.periods)

    private fun assertMatchesOracle(courses: List<CourseSession>, week: Int, s: AppSettings) {
        val (days, legacy) = legacyProjectWeek(courses, week, s)
        val actual = project(courses, week, s)
        assertEquals("day order", days, actual.days)
        assertEquals("weekdays covered", legacy.keys.toList(), actual.placed.keys.toList())
        legacy.forEach { (day, expected) ->
            val normalizedExpected = expected.map { PlacedCourse(it.course, it.lane, it.laneCount, it.startIndex, it.endIndex) }
            assertEquals("day $day placements", normalizedExpected, actual.placed.getValue(day))
        }
    }

    private val settings = AppSettings(periods = defaultDayPeriods(), weekCount = 20)

    private fun course(
        id: String,
        weekday: Int,
        startPeriod: Int,
        endPeriod: Int,
        weeks: List<Int> = (1..20).toList(),
    ) = CourseSession(
        id = id,
        name = "课 $id",
        weekday = weekday,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        weeks = weeks,
        location = "教学楼",
    )

    @Test
    fun projectionIsPure() {
        val courses = listOf(
            course("a", 1, 1, 2),
            course("b", 1, 2, 3),
            course("c", 3, 5, 6, weeks = listOf(2, 3)),
        )
        val first = project(courses, 2, settings)
        val second = project(courses, 2, settings)
        assertEquals("same inputs must produce an equal structure", first, second)
        assertEquals(first.placed, second.placed)

        assertEquals(first, project(courses.toList(), 2, settings))
    }

    @Test
    fun nonOverlappingCoursesEachGetLaneZero() {
        val courses = listOf(
            course("a", 1, 1, 2),
            course("b", 1, 3, 4),
            course("c", 1, 9, 10),
        )
        val placed = project(courses, 1, settings).placed.getValue(1)
        assertEquals(3, placed.size)
        placed.forEach { pc ->
            assertEquals("lane of ${pc.course.id}", 0, pc.lane)
            assertEquals("laneCount of ${pc.course.id}", 1, pc.laneCount)
        }

        assertEquals(listOf(0, 2, 8), placed.map { it.startIndex })
        assertEquals(listOf(1, 3, 9), placed.map { it.endIndex })
    }

    @Test
    fun threeWayOverlapGetsLanesZeroOneTwo() {
        val courses = listOf(
            course("a", 2, 1, 4),
            course("b", 2, 2, 5),
            course("c", 2, 3, 6),
        )
        val placed = project(courses, 1, settings).placed.getValue(2)
        assertEquals(listOf(0, 1, 2), placed.map { it.lane })
        assertEquals(listOf(3, 3, 3), placed.map { it.laneCount })

        assertEquals(listOf("a", "b", "c"), placed.map { it.course.id })
        assertEquals(
            listOf("a", "b", "c"),
            project(courses.reversed(), 1, settings).placed.getValue(2).map { it.course.id },
        )
    }

    @Test
    fun partialOverlapDoesNotSpreadToTheWholeDay() {

        val courses = listOf(
            course("a", 4, 1, 2),
            course("b", 4, 2, 3),
            course("c", 4, 7, 8),
        )
        val placed = project(courses, 1, settings).placed.getValue(4)
        assertEquals(listOf(0, 1, 0), placed.map { it.lane })
        assertEquals(listOf(2, 2, 1), placed.map { it.laneCount })
    }

    @Test
    fun crossWeekCourseOnlyAppearsInItsWeeks() {
        val courses = listOf(
            course("always", 1, 1, 2),
            course("odd", 1, 3, 4, weeks = listOf(1, 3, 5)),
        )
        val empty = project(courses, 20, settings).placed.getValue(1)
        assertEquals(listOf("always"), empty.map { it.course.id })

        listOf(1, 3, 5).forEach { w ->
            assertEquals(listOf("always", "odd"), project(courses, w, settings).placed.getValue(1).map { it.course.id })
        }
        listOf(2, 4, 6).forEach { w ->
            assertEquals(listOf("always"), project(courses, w, settings).placed.getValue(1).map { it.course.id })
        }
    }

    @Test
    fun emptyWeekProducesEmptyDayLists() {
        val courses = listOf(course("only-week-1", 1, 1, 2, weeks = listOf(1)))
        val projection = project(courses, 7, settings)
        assertEquals((1..7).toList(), projection.days)
        projection.placed.values.forEach { assertTrue(it.isEmpty()) }
    }

    @Test
    fun dayOrderFollowsWeekStart() {
        val rotated = AppSettings(firstDay = 1, lastDay = 7, weekStart = 3, periods = defaultDayPeriods())
        assertEquals(listOf(3, 4, 5, 6, 7, 1, 2), project(emptyList(), 1, rotated).days)
        assertEquals(listOf(1, 2, 3, 4, 5), project(emptyList(), 1, AppSettings(firstDay = 1, lastDay = 5, weekStart = 1, periods = defaultDayPeriods())).days)
        assertMatchesOracle(emptyList(), 1, rotated)
    }

    @Test
    fun matchesLegacyOnRealisticTimetable() {
        val courses = listOf(
            course("高数", 1, 1, 2),
            course("高数习题", 3, 1, 2),
            course("英语", 1, 2, 3),
            course("物理", 2, 4, 5),
            course("物理实验", 2, 5, 6),
            course("化学", 3, 4, 7),
            course("体育", 5, 9, 10),
            course("选修", 5, 10, 11, weeks = listOf(2, 4, 6)),
            course("讲座", 7, 1, 3),
        )
        (1..20).forEach { week -> assertMatchesOracle(courses, week, settings) }
    }

    @Test
    fun matchesLegacyOnRandomisedData() {
        val random = Random(20240614L)
        repeat(25) { round ->
            val size = 1 + random.nextInt(40)
            val courses = List(size) { i ->
                val start = 1 + random.nextInt(14)
                val end = start + random.nextInt(15 - start)
                val weeks = (1..20).filter { random.nextInt(3) != 0 }
                course("r$round-$i", 1 + random.nextInt(7), start, end, weeks.ifEmpty { listOf(1 + random.nextInt(20)) })
            }
            val s = AppSettings(
                firstDay = 1,
                lastDay = 7,
                weekStart = 1 + random.nextInt(7),
                weekCount = 20,
                periods = defaultDayPeriods(),
            )
            (1..20).forEach { week -> assertMatchesOracle(courses, week, s) }
        }
    }

    @Test
    fun projectionIsCheapEnoughToCache() {
        val courses = AppData(
            courses = List(2000) { i ->
                val startPeriod = i % 12 + 1
                val endPeriod = minOf(14, startPeriod + i % 3)
                course("bench-$i", i % 7 + 1, startPeriod, endPeriod, (1..20).filter { i % 20 < it || it % 3 == 0 })
            },
        ).courses
        val week = 7

        val recompositions = 60

        fun recomposedWithProjection(): Long {
            var sink = 0
            repeat(recompositions) { sink += projectWeek(courses, week, 1, 7, 1, defaultDayPeriods()).placed.size }
            return sink.toLong()
        }

        fun recomposedWithLegacyInline(): Long {
            var sink = 0
            repeat(recompositions) { sink += legacyProjectWeek(courses, week, settings).second.size }
            return sink.toLong()
        }

        fun recomposedWithRemember(): Long {
            val cache = HashMap<List<Any>, WeekProjection>()
            var sink = 0
            repeat(recompositions) {
                val key = listOf(courses, week, 1, 7, 1, defaultDayPeriods())
                sink += cache.getOrPut(key) { projectWeek(courses, week, 1, 7, 1, defaultDayPeriods()) }.placed.size
            }
            return sink.toLong()
        }

        repeat(3) { recomposedWithProjection(); recomposedWithLegacyInline(); recomposedWithRemember() }

        fun time(block: () -> Long): Pair<Long, Long> {
            val t0 = System.nanoTime()
            val sink = block()
            return (System.nanoTime() - t0) to sink
        }

        val (legacyNanos, legacySink) = time(::recomposedWithLegacyInline)
        val (projectionNanos, projectionSink) = time(::recomposedWithProjection)
        val (rememberNanos, rememberSink) = time(::recomposedWithRemember)

        assertEquals(legacySink, projectionSink)
        assertEquals(legacySink, rememberSink)

        fun ms(nanos: Long) = String.format("%.2f", nanos / 1_000_000.0)
        fun perFrame(nanos: Long) = String.format("%.2f", nanos / 1_000_000.0 / recompositions)
        println("--- 2000 courses, one week, $recompositions recompositions ---")
        println("A. legacy inline (pre-change, every recomposition) : ${ms(legacyNanos)} ms total  /  ${perFrame(legacyNanos)} ms per recomposition")
        println("B. projectWeek, uncached (function cost only)     : ${ms(projectionNanos)} ms total  /  ${perFrame(projectionNanos)} ms per recomposition")
        println("C. projectWeek behind remember(...)               : ${ms(rememberNanos)} ms total  /  ${perFrame(rememberNanos)} ms per recomposition")
        println("=> per-recomposition projection cost: ${perFrame(legacyNanos)} ms -> ${perFrame(rememberNanos)} ms, one projection per key change instead of $recompositions.")
        assertTrue(rememberNanos < legacyNanos)
    }
}
