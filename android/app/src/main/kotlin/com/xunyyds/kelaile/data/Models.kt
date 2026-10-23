package com.xunyyds.kelaile.data

import java.time.LocalDate

data class PeriodTime(val period: Int, val startMinutes: Int, val endMinutes: Int) {
    init {
        require(period in 1..100 && startMinutes in 0..1439 && endMinutes in 1..1440 && startMinutes < endMinutes) { "无效的节次时间" }
    }
}

const val DEFAULT_PERIOD_COUNT = 20

fun defaultDayPeriods(): List<PeriodTime> = resizePeriods(emptyList(), DEFAULT_PERIOD_COUNT)

fun resizePeriods(periods: List<PeriodTime>, count: Int): List<PeriodTime> {
    require(count in 1..100) { "每日节数须在 1–100 之间" }
    if (periods.isEmpty()) {

        return (0 until count).map { i ->
            val start = 480 + i * 45
            PeriodTime(i + 1, start, start + 40)
        }
    }
    if (count <= periods.size) return periods.take(count)
    val result = periods.toMutableList()
    while (result.size < count) {
        val last = result.last()
        val stride = if (result.size >= 2) last.startMinutes - result[result.size - 2].startMinutes else 45
        val start = last.startMinutes + stride.coerceAtLeast(1)
        require(start + 40 <= 1440) { "加到 " + count + " 节会超出当天时间，请先调整前面的节次" }
        result += PeriodTime(last.period + 1, start, start + 40)
    }
    return result
}

data class AppSettings(
    val school: String = "",
    val schoolUrl: String = "",
    val theme: String = "system",
    val navigationStyle: String = "default",
    val fontScale: Double = 1.0,
    val density: Double = 0.85,
    val cellTextAlign: String = "start",
    val firstDay: Int = 1,
    val lastDay: Int = 7,
    val weekStart: Int = 1,
    val currentWeek: Int = 1,
    val weekCount: Int = 20,
    val defaultColor: Long = 0xffd9e9deL,
    val reminderMinutes: Int = 10,
    val reminders: Boolean = false,
    val start: LocalDate = LocalDate.now(),
    val end: LocalDate = start.plusDays(139),
    val periods: List<PeriodTime> = defaultDayPeriods(),
    val themePalette: String = "blue",
    val dynamicColor: Boolean = false,
    val motionSpeed: Double = 1.0,
    val reduceMotion: Boolean = false,
    val transitionStyle: String = "shared",

    val autoHideBars: Boolean = false,
) {
    init {
        require(firstDay in 1..7 && lastDay in firstDay..7 && weekStart in 1..7)
        require(weekCount in 1..60 && currentWeek in 1..weekCount)
        require(fontScale.isFinite() && fontScale in .8..1.6 && density.isFinite() && density in .6..1.6)
        require(motionSpeed.isFinite() && motionSpeed in .5..2.0)
        require(transitionStyle in listOf("standard", "shared", "fade"))
        require(theme in listOf("system", "light", "dark"))
        require(themePalette in listOf("blue", "green", "purple", "orange", "rose", "teal"))
        require(navigationStyle in listOf("default", "floating", "liquid"))
        require(cellTextAlign in listOf("start", "center", "end"))
        require(!end.isBefore(start) && reminderMinutes in 0..10080 && defaultColor in 0..0xffffffffL)
        require(periods.isNotEmpty() && periods.map { it.period }.distinct().size == periods.size)
        val ordered = periods.sortedBy { it.period }
        require(ordered.zipWithNext().all { (a, b) -> a.endMinutes <= b.startMinutes }) { "节次时间重叠" }
    }
}

data class CourseSession(
    val id: String,
    val name: String,
    val weekday: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: List<Int>,
    val teacher: String = "",
    val location: String = "",
) {
    init {
        require(id.isNotBlank() && name.isNotBlank() && weekday in 1..7)
        require(startPeriod in 1..100 && endPeriod in startPeriod..100)
        require(weeks.isNotEmpty() && weeks.all { it in 1..60 }) { "无效的课程周次" }
    }
}

data class AppData(
    val settings: AppSettings = AppSettings(),
    val courses: List<CourseSession> = emptyList(),
    val colors: Map<String, Long> = emptyMap(),
) {
    init { validate() }
    fun validate() {
        require(courses.map { it.id }.distinct().size == courses.size) { "课程编号重复" }
        val numbers = settings.periods.map { it.period }.toSet()
        require(courses.all { c -> c.weeks.all { it <= settings.weekCount } && (c.startPeriod..c.endPeriod).all { it in numbers } }) { "课程周次或节次超出设置" }
        require(colors.values.all { it in 0..0xffffffffL }) { "无效的颜色" }
    }
    fun forWeek(week: Int): List<CourseSession> = courses.filter { week in it.weeks }
}

data class SchoolImport(
    val courses: List<CourseSession>,
    val weekCount: Int? = null,
    val currentWeek: Int? = null,
    val periods: List<PeriodTime> = emptyList(),
    val school: String? = null,
)

fun adjustPeriods(periods: List<PeriodTime>, index: Int, start: Int, end: Int, cascade: Boolean = true): List<PeriodTime> {
    require(index in periods.indices)
    val delta = end - periods[index].endMinutes

    if (cascade && delta > 0) {
        require(periods.last().endMinutes + delta <= 1440) { "顺延后最后一节会超出当天，请先缩短其它节次" }
    }
    val result = periods.mapIndexed { i, p ->
        when {
            i == index -> p.copy(startMinutes = start, endMinutes = end)
            cascade && i > index -> p.copy(startMinutes = p.startMinutes + delta, endMinutes = p.endMinutes + delta)
            else -> p
        }
    }
    require(result.zipWithNext().all { (a, b) -> a.endMinutes <= b.startMinutes }) { "调整后节次重叠" }
    return result
}
