package com.xunyyds.kelaile.ui.search

import com.xunyyds.kelaile.data.AppData
import com.xunyyds.kelaile.data.CourseSession
import com.xunyyds.kelaile.ui.palette.PaletteEntry
import com.xunyyds.kelaile.ui.palette.PaletteKind
import com.xunyyds.kelaile.ui.palette.paletteActions
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ln

internal data class Suggestion(
    val entry: PaletteEntry,
    val score: Float,
    val reason: String,
)

/**
 * Mixed local recommender for the search sheet's empty state.
 *
 * Three independent signals are added together:
 *
 *  1. history - what the user actually searched, decayed by how long ago it was
 *  2. heat    - how often a course name / teacher / location appears locally
 *  3. context - what is happening now (today's weekday, current week)
 *
 * Scores live in a small fixed range so one strong signal can still win, while a
 * merely-recent search cannot drown out "you have this class today".
 */
internal object SearchSuggestions {

    private const val MAX_RESULTS = 6

    private const val HISTORY_BASE = 42f
    private const val HISTORY_PER_RANK = 4.5f
    private const val HISTORY_HALF_LIFE_DAYS = 21f
    private const val HISTORY_MIN_DECAY = 0.18f
    private const val HISTORY_MAX_AGE_DAYS = 60f

    private const val HEAT_MAX = 30f
    private const val CONTEXT_TODAY = 26f
    private const val CONTEXT_TODAY_MATCHED = 13f
    private const val CONTEXT_WEEK = 7f
    private const val ACTION_BONUS = 6f

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun suggest(
        data: AppData,
        history: List<String>,
        historyTimes: Map<String, Long>,
        nowMillis: Long,
        max: Int = MAX_RESULTS,
    ): List<Suggestion> {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).dayOfWeek
        val week = data.settings.currentWeek
        val courses = data.courses

        if (courses.isEmpty() && history.isEmpty()) return emptyList()

        val nameCount = courses.groupingBy { it.name }.eachCount()
        val teacherCount = courses.map { it.teacher }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
        val locationCount = courses.map { it.location }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()

        val todayCourses = courses.filter { it.weekday == today.value && week in it.weeks }
        val weekCourses = courses.filter { week in it.weeks }

        val todayNames = todayCourses.map { it.name }.toSet()
        val weekNames = weekCourses.map { it.name }.toSet()

        val ranked = LinkedHashMap<String, Suggestion>()

        fun offer(entry: PaletteEntry, score: Float, reason: String) {
            if (score <= 0f) return
            val key = entry.title
            val previous = ranked[key]
            if (previous == null || score > previous.score) {
                ranked[key] = Suggestion(entry, score, reason)
            }
        }

        courses.forEach { course ->
            val heat = heatOf(course, nameCount, teacherCount, locationCount)
            val isToday = course.name in todayNames
            val isThisWeek = course.name in weekNames
            val context = when {
                isToday -> CONTEXT_TODAY
                isThisWeek -> CONTEXT_WEEK
                else -> 0f
            }
            offer(
                courseEntry(course),
                heat + context,
                when {
                    isToday -> "今天有课"
                    isThisWeek -> "本周有课"
                    heat >= HEAT_MAX * 0.5f -> "常出现的课"
                    else -> ""
                },
            )
        }

        history.forEachIndexed { rank, raw ->
            val query = raw.trim()
            if (query.isEmpty()) return@forEachIndexed
            val usedAt = historyTimes[query]
            val ageDays = if (usedAt == null) HISTORY_MAX_AGE_DAYS else {
                ((nowMillis - usedAt).coerceAtLeast(0L) / 86_400_000f)
            }
            if (ageDays > HISTORY_MAX_AGE_DAYS) return@forEachIndexed

            val base = HISTORY_BASE - rank * HISTORY_PER_RANK
            if (base <= 0f) return@forEachIndexed
            val decay = if (usedAt == null) HISTORY_MIN_DECAY else halfLifeDecay(ageDays)
            val weighted = base * decay

            val matchedCourses = courses.filter { it.name.contains(query, ignoreCase = true) }
            if (matchedCourses.isEmpty()) {
                paletteActions
                    .firstOrNull { it.title.contains(query, ignoreCase = true) }
                    ?.let { offer(it, weighted + ACTION_BONUS, "常搜的操作") }
                return@forEachIndexed
            }
            matchedCourses.forEach { course ->
                val isToday = todayCourses.any { it.id == course.id }
                offer(
                    courseEntry(course),
                    weighted + if (isToday) CONTEXT_TODAY_MATCHED else 0f,
                    if (isToday) "常搜 · 今天有课" else "常搜的课",
                )
            }
        }

        if (ranked.isEmpty()) return fallback(data, today)

        return ranked.values
            .sortedWith(compareByDescending<Suggestion> { it.score }.thenBy { it.entry.title })
            .take(max)
    }

    private fun halfLifeDecay(ageDays: Float): Float {
        if (ageDays <= 0f) return 1f
        val decay = Math.pow(0.5, (ageDays / HISTORY_HALF_LIFE_DAYS).toDouble()).toFloat()
        return decay.coerceIn(HISTORY_MIN_DECAY, 1f)
    }

    private fun fallback(data: AppData, today: DayOfWeek): List<Suggestion> {
        val week = data.settings.currentWeek
        val todayCourses = data.courses.filter { it.weekday == today.value && week in it.weeks }
        val pool = todayCourses.ifEmpty { data.courses }
        return pool
            .sortedBy { it.startPeriod }
            .distinctBy { it.name }
            .take(MAX_RESULTS)
            .map { course ->
                Suggestion(
                    courseEntry(course),
                    CONTEXT_TODAY,
                    if (todayCourses.isNotEmpty()) "今天有课" else "",
                )
            }
    }

    private fun heatOf(
        course: CourseSession,
        nameCount: Map<String, Int>,
        teacherCount: Map<String, Int>,
        locationCount: Map<String, Int>,
    ): Float {
        var raw = (nameCount[course.name] ?: 1).toFloat() * 0.5f
        if (course.teacher.isNotBlank()) raw += (teacherCount[course.teacher] ?: 1) * 0.25f
        if (course.location.isNotBlank()) raw += (locationCount[course.location] ?: 1) * 0.25f
        return (ln(1f + raw) / ln(1f + 6f)).coerceIn(0f, 1f) * HEAT_MAX
    }

    private fun courseEntry(course: CourseSession): PaletteEntry = PaletteEntry(
        id = "course:${course.id}",
        kind = PaletteKind.Course,
        title = course.name,
        subtitle = "",
        keywords = "${course.teacher} ${course.location}",
        payload = course.id,
    )
}
