package com.xu.wake.perf

import com.xu.wake.data.AppData
import com.xu.wake.data.AppSettings
import com.xu.wake.data.CourseSession
import com.xu.wake.ui.SearchText
import com.xu.wake.ui.searchScore
import com.xu.wake.ui.searchTerms
import com.xu.wake.ui.palette.PaletteEntry
import com.xu.wake.ui.palette.paletteEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random

class SearchPipelineBenchmarkTest {

    private val courseCount = 2000
    private val queryCount = 1000

    private fun course(i: Int): CourseSession {
        val start = i % 12 + 1
        val end = minOf(14, start + i % 3)
        return CourseSession(
            id = "course-$i",
            name = courseBases[i % courseBases.size] + "(" + (i / courseBases.size) + ")",
            weekday = i % 7 + 1,
            startPeriod = start,
            endPeriod = end,
            weeks = (1..20).filter { (i + it) % 3 == 0 },
            teacher = "教师" + (i % 120),
            location = "教" + (i % 40 + 1) + "-" + (i % 5 + 1) + "0" + (i % 9 + 1),
        )
    }

    private fun dataset(): AppData = AppData(
        settings = AppSettings(weekCount = 20, currentWeek = 1),
        courses = (0 until courseCount).map(::course),
        colors = (0 until courseCount).associate { "course-$it" to 0xffd9e9deL + it },
    )

    private fun queries(): List<String> {
        val rnd = Random(20260214L)
        val out = LinkedHashSet<String>()
        val pools = courseBases + listOf(
            "教师7", "教12-30", "周次", "导出", "主题", "外观", "动效", "提醒", "备份", "深色",
            "第 3 周", "第 11 周", "新增课程", "切换明暗模式", "打开设置", "数据结构", "编译原理",
        )
        var i = 0
        while (out.size < queryCount && i < queryCount * 8) {
            val q = when (i % 8) {
                0 -> pools[rnd.nextInt(pools.size)]
                1 -> pools[rnd.nextInt(pools.size)] + " " + pools[rnd.nextInt(pools.size)]
                2 -> "课程" + (i % 400)
                3 -> "教师" + (i % 200)
                4 -> "教" + (i % 41)
                5 -> "zzzq" + i
                6 -> "第 " + (i % 20) + " 周"
                else -> courseBases[i % courseBases.size].take(2) + i
            }
            out += q
            i++
        }
        assertEquals("query pool must reach $queryCount distinct strings", queryCount, out.size)
        return out.toList()
    }

    private fun runPipeline(entries: List<PaletteEntry>, query: String): List<PaletteEntry> {
        val terms = searchTerms(query)
        if (terms.isEmpty()) return emptyList()
        return entries
            .map { it to searchScore(SearchText(it.title, it.subtitle, it.keywords), terms) }
            .filter { it.second >= 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    @Test
    fun baseline() {
        val report = StringBuilder()
        fun emit(line: String) {
            report.append(line).append('\n')
            println(line)
        }

        emit("# xwake search pipeline — measured baseline")
        emit("jvm          = " + System.getProperty("java.version") + " / " + System.getProperty("os.name"))
        emit("fixture      = " + courseCount + " courses, " + queryCount + " distinct queries")
        emit("")

        val data = dataset()
        val dataTwin = data.copy(settings = data.settings)
        var sink = 0
        repeat(200) { sink += if (data == dataTwin) 1 else 0 }
        val equalsNanos = bestOf(50) { sink += if (data == dataTwin) 1 else 0 }
        emit("AppData.equals(2000 courses)  : " + us(equalsNanos) + " us  <- paid per remember(data) key check")

        val entries = paletteEntries(data)
        repeat(20) { sink += paletteEntries(data).size }
        val entriesNanos = bestOf(20) { sink += paletteEntries(data).size }
        emit("paletteEntries(AppData)       : " + us(entriesNanos) + " us  -> " + entries.size + " entries")
        emit("  settings seeds              : " + entries.count { it.id.startsWith("setting:") })
        emit("  action seeds                : " + entries.count { it.id.startsWith("action:") })
        emit("")

        val queries = queries()
        repeat(5000) { sink += searchTerms("主题 颜色").size }
        val termNanos = bestOf(2000) { sink += searchTerms("主题 颜色").size }
        emit("searchTerms(2 tokens)         : " + ns(termNanos) + " ns  <- Components.kt:509 compiles Regex(\\\\s+) every call")

        repeat(200) { sink += runPipeline(entries, queries[it % queries.size]).size }

        val perQuery = LongArray(queries.size)
        var total = 0L
        for (i in queries.indices) {
            val t0 = System.nanoTime()
            val r = runPipeline(entries, queries[i])
            val dt = System.nanoTime() - t0
            perQuery[i] = dt
            total += dt
            sink += r.size
        }
        val mean = total / queries.size
        val sorted = perQuery.sortedArray()
        val p50 = sorted[(queries.size * 0.50).toInt()]
        val p95 = sorted[((queries.size - 1) * 0.95).toInt()]
        val p99 = sorted[((queries.size - 1) * 0.99).toInt()]
        val max = sorted.last()

        emit("")
        emit("## full pipeline (searchTerms -> searchScore -> filter -> sortedByDescending)")
        emit("total      : " + ms(total) + " ms over " + queries.size + " queries")
        emit("per-query  : mean " + us(mean) + " us | p50 " + us(p50) + " | p95 " + us(p95) + " | p99 " + us(p99) + " | max " + us(max) + " us")
        emit("throughput : " + (1e9 / mean.toDouble()).toInt() + " queries/s")

        val gridNanos = bestOf(10) { sink += gridProject(data, 3).size }
        emit("")
        emit("## Timetable week projection (2000 courses, 7 days)")
        emit("one week   : " + us(gridNanos) + " us  <- unremembered, recomputed on every Timetable recomposition")

        val weekNanos = bestOf(50) {
            for (w in 1..20) sink += data.forWeek(w).size
        }
        emit("forWeek x20 : " + us(weekNanos) + " us  <- Models.kt:86 filter allocates a new list")

        writeBaseline(report.toString())

        assertTrue(
            "per-query mean regressed to " + us(mean) + " (gate: < 5000 us)",
            mean < 5_000_000L,
        )
        assertTrue("p95 regressed to " + us(p95) + " (gate: < 15000 us)", p95 < 15_000_000L)
        assertTrue("paletteEntries regressed to " + us(entriesNanos) + " (gate: < 20000 us)", entriesNanos < 20_000_000L)
        assertTrue("Timetable projection regressed to " + us(gridNanos) + " (gate: < 20000 us)", gridNanos < 20_000_000L)
        assertTrue("everything optimised away?", sink != 0)
    }

    @Test
    fun proposedPipelineHeadroom() {
        val data = dataset()
        val entries = paletteEntries(data)
        val queries = queries()
        val texts = entries.map { SearchText(it.title, it.subtitle, it.keywords) }
        val comparator = Comparator<Int> { a, b -> b.compareTo(a) }
        var sink = 0

        fun current(query: String): Int = runPipeline(entries, query).size

        fun proposed(query: String): Int {
            val terms = searchTerms(query)
            if (terms.isEmpty()) return 0
            val scored = ArrayList<Int>(texts.size / 8)
            for (i in texts.indices) {
                val s = searchScore(texts[i], terms)
                if (s >= 0) scored.add(i)
            }
            scored.sortWith(comparator)
            return scored.size
        }

        repeat(200) { sink += current(queries[it % queries.size]) + proposed(queries[it % queries.size]) }

        fun time(block: (String) -> Int): Long {
            var total = 0L
            for (q in queries) {
                val t0 = System.nanoTime()
                sink += block(q)
                total += System.nanoTime() - t0
            }
            return total / queries.size
        }

        val curMean = time(::current)
        val newMean = time(::proposed)
        val curTexts = bestOf(20) { sink += entries.map { SearchText(it.title, it.subtitle, it.keywords) }.size }
        val newTexts = bestOf(20) { sink += texts.size }

        val line1 = "current pipeline        : " + us(curMean) + " us/query"
        val line2 = "precomputed SearchText  : " + us(newMean) + " us/query"
        val line3 = "speedup                 : " + String.format("%.2f", curMean.toDouble() / newMean.toDouble()) + "x"
        val line4 = "SearchText alloc/query  : " + us(curTexts) + " us -> " + us(newTexts) + " us (remembered)"
        listOf(line1, line2, line3, line4).forEach(::println)
        writeBaseline(File("build/perf-baseline.txt").takeIf { it.exists() }?.readText().orEmpty() +
            "\n## proposed pipeline\n" + line1 + "\n" + line2 + "\n" + line3 + "\n" + line4 + "\n")

        assertTrue("optimised pipeline should not be slower: " + us(newMean) + " vs " + us(curMean), newMean <= curMean * 1.2)
        assertTrue("sink", sink != 0)
    }

    @Test
    fun stageBreakdown() {
        val data = dataset()
        val entries = paletteEntries(data)
        val queries = queries()
        val texts = entries.map { SearchText(it.title, it.subtitle, it.keywords) }
        var sink = 0

        var scoreNanos = 0L
        var scoreCalls = 0L
        var hits = 0L
        for (q in queries) {
            val terms = searchTerms(q)
            if (terms.isEmpty()) continue
            val t0 = System.nanoTime()
            var h = 0
            for (i in texts.indices) {
                if (searchScore(texts[i], terms) >= 0) h++
            }
            scoreNanos += System.nanoTime() - t0
            scoreCalls++
            hits += h
        }

        repeat(100) { sink += runPipeline(entries, queries[it % queries.size]).size }
        val allocFree = bestOf(50) {
            val terms = searchTerms("主题 颜色")
            for (i in texts.indices) sink += if (searchScore(texts[i], terms) >= 0) 1 else 0
        }
        val withPair = bestOf(50) {
            val terms = searchTerms("主题 颜色")
            sink += texts.map { it to searchScore(it, terms) }.count { it.second >= 0 }
        }
        val sortOnly = bestOf(200) {
            val scores = IntArray(texts.size) { it }
            sink += scores.sortedByDescending { it }.size
        }
        val txtSort = bestOf(200) {
            val scores = texts.indices.toList()
            sink += scores.sortedByDescending { it }.size
        }

        println("--- stage breakdown (mean over " + scoreCalls + " queries) ---")
        println("searchScore scan only    : " + us(scoreNanos / scoreCalls) + " us/query")
        println("mean rows kept           : " + (hits / scoreCalls) + " of " + texts.size)
        println("scan, no Pair alloc      : " + us(allocFree) + " us  [query '主题 颜色']")
        println("scan + Pair alloc        : " + us(withPair) + " us  [same query]")
        println("sortedByDescending(2044) : " + us(txtSort) + " us  [index list]")
        println("sortedByDescending(IntArray->List) : " + us(sortOnly) + " us")
        assertTrue("sink", sink != 0)
    }

    @Test
    fun primitiveCost() {
        val data = dataset()
        val texts = paletteEntries(data).map { SearchText(it.title, it.subtitle, it.keywords) }
        val term = "主题"
        val termLower = "主题".lowercase()
        var sink = 0

        fun ignoreCase() {
            for (t in texts) {
                sink += if (t.title.indexOf(term, ignoreCase = true) >= 0) 1 else 0
            }
        }

        fun exact() {
            for (t in texts) {
                sink += if (t.title.indexOf(term) >= 0) 1 else 0
            }
        }

        repeat(100) { ignoreCase(); exact() }
        val ic = bestOf(50) { ignoreCase() }
        val ex = bestOf(50) { exact() }
        val avgTitle = texts.sumOf { it.title.length }.toDouble() / texts.size
        val avgAll = texts.sumOf { (it.title.length + it.path.length + it.keywords.length).toLong() }
            .toDouble() / texts.size

        println("--- primitive cost (2044 entries, single term, title field) ---")
        println("avg title length         : " + String.format("%.1f", avgTitle) + " chars")
        println("avg title+path+keywords  : " + String.format("%.1f", avgAll) + " chars")
        println("indexOf ignoreCase=true  : " + us(ic) + " us  -> " + String.format("%.0f", ic.toDouble() / texts.size) + " ns/entry")
        println("indexOf exact            : " + us(ex) + " us  -> " + String.format("%.0f", ex.toDouble() / texts.size) + " ns/entry")
        println("ignoreCase overhead      : " + String.format("%.2f", ic.toDouble() / ex.toDouble()) + "x per field")
        println("searchScore does 3 fields x N terms of these")
        assertTrue("sink", sink != 0)
    }

    @Test
    fun lowercasePrecompute() {
        val data = dataset()
        val entries = paletteEntries(data)
        val queries = queries()
        val raw = entries.map { SearchText(it.title, it.subtitle, it.keywords) }
        val lower = raw.map {
            SearchText(it.title.lowercase(), it.path.lowercase(), it.keywords.lowercase())
        }
        var sink = 0

        fun fast(text: SearchText, terms: List<String>): Int {
            var score = 0
            for (term in terms) {
                val title = text.title.indexOf(term)
                val path = text.path.indexOf(term)
                val keywords = text.keywords.indexOf(term)
                score += when {
                    title == 0 -> 100
                    title > 0 -> 60
                    keywords >= 0 -> 24
                    path >= 0 -> 12
                    else -> return -1
                }
            }
            return score
        }

        fun runCurrent(q: String): List<Int> {
            val terms = searchTerms(q)
            if (terms.isEmpty()) return emptyList()
            return raw.indices.filter { searchScore(raw[it], terms) >= 0 }
        }

        fun runFast(q: String): List<Int> {
            val terms = searchTerms(q).map { it.lowercase() }
            if (terms.isEmpty()) return emptyList()
            return lower.indices.filter { fast(lower[it], terms) >= 0 }
        }

        var mismatches = 0
        for (q in queries) {
            if (runCurrent(q).toSet() != runFast(q).toSet()) mismatches++
        }
        println("--- lowercase precompute ---")
        println("result-set mismatches    : " + mismatches + " / " + queries.size)

        repeat(100) { sink += runCurrent(queries[it % queries.size]).size + runFast(queries[it % queries.size]).size }

        fun time(block: (String) -> List<Int>): Long {
            var total = 0L
            for (q in queries) {
                val t0 = System.nanoTime()
                sink += block(q).size
                total += System.nanoTime() - t0
            }
            return total / queries.size
        }

        val cur = time(::runCurrent)
        val optimized = time(::runFast)
        println("current (ignoreCase)     : " + us(cur) + " us/query")
        println("lowercased + plain indexOf: " + us(optimized) + " us/query")
        println("speedup                  : " + String.format("%.1f", cur.toDouble() / optimized.toDouble()) + "x")

        assertTrue("lowercase precompute changed results", mismatches == 0)
        assertTrue("sink", sink != 0)
    }

    @Test
    fun searchIndexVersusLegacy() {
        val data = dataset()
        val entries = paletteEntries(data)
        val queries = queries()
        val index = com.xu.wake.ui.search.SearchIndex.forData(data)
        var sink = 0

        repeat(200) {
            sink += runPipeline(entries, queries[it % queries.size]).size
            sink += index.search(queries[it % queries.size]).entries.size
        }

        var orderMismatch = 0
        for (q in queries) {
            val legacy = runPipeline(entries, q).map { it.id }
            val next = index.search(q).entries.map { it.id }
            if (legacy != next) orderMismatch++
        }

        fun time(block: (String) -> Int): Long {
            var total = 0L
            for (q in queries) {
                val t0 = System.nanoTime()
                sink += block(q)
                total += System.nanoTime() - t0
            }
            return total / queries.size
        }

        val legacyMean = time { runPipeline(entries, it).size }
        val indexMean = time { index.search(it).entries.size }

        println("--- SearchIndex (shipped) vs legacy pipeline ---")
        println("legacy map/filter/sort   : " + us(legacyMean) + " us/query")
        println("SearchIndex.search       : " + us(indexMean) + " us/query")
        println("speedup                  : " + String.format("%.1f", legacyMean.toDouble() / indexMean.toDouble()) + "x")
        println("result order mismatches  : " + orderMismatch + " / " + queries.size)
        println("index size               : " + index.size + " entries")

        assertTrue("SearchIndex changed result ordering", orderMismatch == 0)
        assertTrue("sink", sink != 0)
    }

    @Test
    fun settingsCatalogPipeline() {
        val catalog = (0 until 37).map { i -> SearchText("设置项" + i, "设置 · 分组" + (i % 6), "关键词" + i + " 主题 周次") }
        val queries = queries()
        var sink = 0
        repeat(100) { i ->
            val terms = searchTerms(queries[i % queries.size])
            sink += catalog.map { it to searchScore(it, terms) }.filter { it.second >= 0 }
                .sortedByDescending { it.second }.map { it.first }.size
        }
        var total = 0L
        for (q in queries) {
            val terms = searchTerms(q)
            val t0 = System.nanoTime()
            val r = if (terms.isEmpty()) emptyList() else catalog
                .map { it to searchScore(it, terms) }
                .filter { it.second >= 0 }
                .sortedByDescending { it.second }
                .map { it.first }
            total += System.nanoTime() - t0
            sink += r.size
        }
        val line = "Settings search (37 rows)   : " + us(total / queries.size) + " us/query"
        println(line)
        assertTrue("sink", sink != 0)
    }

    private fun gridProject(data: AppData, week: Int): List<CourseSession> {
        val s = data.settings
        val days = (s.firstDay..s.lastDay).sortedBy { (it - s.weekStart + 7) % 7 }
        val courses = data.forWeek(week)
        val placed = ArrayList<CourseSession>()
        for (day in days) {
            val dayCourses = courses.filter { it.weekday == day }.sortedBy { it.startPeriod }
            for (course in dayCourses) {
                val startIndex = s.periods.indexOfFirst { it.period == course.startPeriod }
                val endIndex = s.periods.indexOfFirst { it.period == course.endPeriod }
                val conflicts = dayCourses.filter {
                    it.startPeriod <= course.endPeriod && it.endPeriod >= course.startPeriod
                }
                val lane = conflicts.indexOfFirst { it.id == course.id }.coerceAtLeast(0)
                if (startIndex >= 0 && endIndex >= startIndex && lane >= 0) placed += course
            }
        }
        return placed
    }

    private fun writeBaseline(text: String) {
        runCatching {
            val f = File("build/perf-baseline.txt")
            f.parentFile?.mkdirs()
            f.writeText(text)
        }
    }

    private inline fun bestOf(times: Int, block: () -> Unit): Long {
        var best = Long.MAX_VALUE
        repeat(times) {
            val t0 = System.nanoTime()
            block()
            val dt = System.nanoTime() - t0
            if (dt < best) best = dt
        }
        return best
    }

    private fun us(n: Long): String = String.format("%.2f", n / 1000.0)
    private fun ms(n: Long): String = String.format("%.2f", n / 1_000_000.0)
    private fun ns(n: Long): String = String.format("%.0f", n.toDouble())

    private companion object {
        val courseBases = listOf(
            "高等数学", "大学英语", "数据结构", "操作系统", "计算机网络", "线性代数",
            "概率论", "马克思主义基本原理", "大学物理", "体育", "程序设计基础", "离散数学",
            "编译原理", "数据库系统", "软件工程", "人工智能",
        )
    }
}
