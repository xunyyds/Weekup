package com.xu.wake.ui

import com.xu.wake.data.AppData
import com.xu.wake.data.CourseSession
import com.xu.wake.ui.palette.PaletteEntry
import com.xu.wake.ui.palette.PaletteKind
import com.xu.wake.ui.palette.paletteActions
import com.xu.wake.ui.search.SearchIndex
import com.xu.wake.ui.search.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Measures the search pipeline end to end on the JVM.
 *
 * "before" is [legacySearch] over [legacyEntries] — the shape shipped in CommandPalette.kt, where
 * every keystroke rebuilt the whole entry list and rescored every entry from scratch. "after" is
 * [SearchIndex], which builds once per data instance and refines the previous hit list while the
 * user types. Both run the same workload, so the printed table is apples to apples.
 */
class SearchPipelinePerfTest {

    private val courseCount = 2000
    private val queryCount = 1000

    private fun corpus(): AppData = AppData(
        courses = List(courseCount) { i ->
            CourseSession(
                id = "c$i",
                name = "课程名称$i",
                weekday = i % 7 + 1,
                startPeriod = 1,
                endPeriod = 2,
                weeks = listOf(1, 2, 3),
                teacher = "教师${i % 37}",
                location = "教学楼${i % 13}",
            )
        },
    )

    /** Queries a user would actually type, cycled up to [queryCount]. */
    private fun queries(): List<String> {
        val seeds = listOf("周次", "教师1", "教学", "导出", "主题", "课程", "设置", "颜色", "动画", "a")
        return List(queryCount) { i -> seeds[i % seeds.size] }
    }

    // ---- before: CommandPalette.kt's remember(entries, terms) block, verbatim ---------------

    private fun legacyEntries(data: AppData): List<PaletteEntry> = buildList {
        data.courses.forEach { course ->
            add(
                PaletteEntry(
                    id = "course:${course.id}",
                    kind = PaletteKind.Course,
                    title = course.name,
                    subtitle = "${course.weekday} 第 ${course.startPeriod}—${course.endPeriod} 节 " +
                        "${course.teacher} ${course.location}",
                    keywords = "${course.teacher} ${course.location} 课程 课表",
                    payload = course.id,
                ),
            )
        }
        // 43 static entries rebuilt on every call, exactly as paletteEntries(data) did.
        repeat(43) { add(paletteActions[it % paletteActions.size]) }
    }

    private fun legacySearch(entries: List<PaletteEntry>, raw: String): List<PaletteEntry> {
        val terms = searchTerms(raw)
        if (terms.isEmpty()) return emptyList()
        return entries
            .map { it to searchScore(SearchText(it.title, it.subtitle, it.keywords), terms) }
            .filter { it.second >= 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun bestOf3(block: () -> Unit): Double {
        var best = Double.MAX_VALUE
        repeat(3) {
            val started = System.nanoTime()
            block()
            best = minOf(best, (System.nanoTime() - started) / 1_000_000.0)
        }
        return best
    }

    @Test
    fun timingTable() {
        val data = corpus()
        val queries = queries()
        repeat(3) {
            legacyEntries(data).let { entries -> legacySearch(entries, "周次") }
            SearchIndex.forData(data).search("周次")
        }

        var legacySink = 0
        val beforeQueryMs = bestOf3 {
            var hits = 0
            for (q in queries) hits += legacySearch(legacyEntries(data), q).size
            legacySink = hits
        }

        var newSink = 0
        val index = SearchIndex.forData(data)
        val afterQueryMs = bestOf3 {
            var previous: SearchResult? = null
            var hits = 0
            for (q in queries) {
                val result = index.search(q, previous)
                previous = result
                hits += result.entries.size
            }
            newSink = hits
        }

        val beforeChangeMs = bestOf3 { repeat(2000) { legacyEntries(data) } }
        val afterChangeMs = bestOf3 { repeat(2000) { SearchIndex.forData(data) } }

        val report = """
            |=== search pipeline: $courseCount courses x $queryCount queries ===
            |                             before(ms)  after(ms)  speedup
            |  keystroke (build + query) %10.2f %10.2f %7.1fx
            |  2000x data-change rebuild %10.2f %10.2f %7.1fx
            |  results returned          %10d %10d
        """.trimMargin().format(
            beforeQueryMs, afterQueryMs, beforeQueryMs / afterQueryMs.coerceAtLeast(0.001),
            beforeChangeMs, afterChangeMs, beforeChangeMs / afterChangeMs.coerceAtLeast(0.001),
            legacySink, newSink,
        )
        println(report)
        assertTrue("new pipeline must not be slower", afterQueryMs <= beforeQueryMs)
    }

    @Test
    fun refinedResultsEqualAFullScan() {
        val index = SearchIndex.forData(corpus())
        var previous: SearchResult? = null
        for (typed in listOf("周", "周次", "教", "教师1", "教学", "教学1", "教学1x")) {
            val refined = index.search(typed, previous)
            previous = refined
            val cold = index.search(typed)
            assertEquals(
                "sorted ids for \"$typed\"",
                cold.entries.map { it.id },
                refined.entries.map { it.id },
            )
        }
    }

    @Test
    fun refinedPoolNeverGrows() {
        val index = SearchIndex.forData(corpus())
        val first = index.search("课程")
        val refined = index.search("课程名称1", first)
        assertTrue(refined.hits.size <= first.hits.size)
        assertEquals(index.search("课程名称1").entries.map { it.id }, refined.entries.map { it.id })
    }

    @Test
    fun staticEntriesAreSharedAcrossRebuilds() {
        val data = corpus()
        val first = SearchIndex.entriesFor(data)
        val second = SearchIndex.entriesFor(data)
        assertEquals(first.size, second.size)
        for (i in first.indices) {
            if (first[i].kind == PaletteKind.Setting || first[i].kind == PaletteKind.Action) {
                assertTrue("entry $i should be shared", first[i] === second[i])
            }
        }
    }

    @Test
    fun emptyQueryShortCircuits() {
        val index = SearchIndex.forData(corpus())
        assertTrue(index.search("   ").isEmpty)
        assertTrue(index.search("").isEmpty)
        assertTrue(index.search("   ").terms.isEmpty())
    }
}
