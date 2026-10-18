package com.xu.wake.ui.search

import com.xu.wake.data.AppData
import com.xu.wake.data.CourseSession
import com.xu.wake.ui.palette.PaletteEntry
import com.xu.wake.ui.palette.courseEntries
import com.xu.wake.ui.palette.paletteActions
import com.xu.wake.ui.palette.settingEntries

class IndexedEntry internal constructor(
    val entry: PaletteEntry,

    val order: Int,
    title: String,
    path: String,
    keywords: String,
) {
    val title: String = title.lowercase()
    val path: String = path.lowercase()
    val keywords: String = keywords.lowercase()

    fun score(terms: List<String>): Int {
        var total = 0
        for (i in terms.indices) {
            val term = terms[i]
            val inTitle = title.indexOf(term)
            total += when {
                inTitle == 0 -> 100
                inTitle > 0 -> 60
                keywords.indexOf(term) >= 0 -> 24
                path.indexOf(term) >= 0 -> 12
                else -> return -1
            }
        }
        return total
    }
}

class SearchResult internal constructor(

    val terms: List<String>,
    val entries: List<PaletteEntry>,
    internal val hits: List<IndexedEntry>,
    internal val scores: IntArray,
) {
    val isEmpty: Boolean get() = entries.isEmpty()

    fun scoreAt(index: Int): Int = scores[index]

    companion object {
        val Empty = SearchResult(emptyList(), emptyList(), emptyList(), IntArray(0))
    }
}

class SearchIndex private constructor(
    val entries: List<PaletteEntry>,
    private val indexed: List<IndexedEntry>,
) {
    val size: Int get() = indexed.size

    fun search(query: String, refineFrom: SearchResult? = null): SearchResult {
        val terms = normalize(query)
        if (terms.isEmpty()) return SearchResult.Empty
        val pool = refineFrom?.takeIf { it.terms.isNotEmpty() && extends(it.terms, terms) }?.let { previous ->
            if (previous.hits.size == indexed.size) indexed else previous.hits.map { indexed[it.order] }
        } ?: indexed
        if (pool.isEmpty()) return SearchResult(terms, emptyList(), emptyList(), IntArray(0))

        val packed = LongArray(pool.size)
        var count = 0
        for (i in pool.indices) {
            val candidate = pool[i]
            val score = candidate.score(terms)
            if (score >= 0) packed[count++] = ((SCORE_CEILING - score).toLong() shl 32) or candidate.order.toLong()
        }
        packed.sort(0, count)
        val hits = ArrayList<IndexedEntry>(count)
        val scores = IntArray(count)
        for (k in 0 until count) {
            val value = packed[k]
            hits.add(indexed[(value and ORDER_MASK).toInt()])
            scores[k] = (SCORE_CEILING - (value ushr 32).toInt())
        }
        return SearchResult(terms, List(count) { hits[it].entry }, hits, scores)
    }

    private fun extends(previous: List<String>, next: List<String>): Boolean =
        next.size > previous.size && next.subList(0, previous.size) == previous

    companion object {

        fun normalize(raw: String): List<String> =
            raw.trim().lowercase().split(WHITESPACE).filter { it.isNotEmpty() }

        private val staticEntries: List<PaletteEntry> by lazy { settingEntries() + paletteActions }

        private var cachedCourses: List<CourseSession>? = null
        private var cachedCourseEntries: List<PaletteEntry> = emptyList()
        private var cachedIndex: SearchIndex? = null
        private var cachedIndexCourses: List<CourseSession>? = null

        fun courseEntriesFor(courses: List<CourseSession>): List<PaletteEntry> {
            if (courses !== cachedCourses) {
                cachedCourses = courses
                cachedCourseEntries = courseEntries(courses)
            }
            return cachedCourseEntries
        }

        fun of(entries: List<PaletteEntry>): SearchIndex = SearchIndex(
            entries = entries,
            indexed = List(entries.size) { i ->
                val e = entries[i]
                IndexedEntry(e, i, e.title, e.subtitle, e.keywords)
            },
        )

        fun entriesFor(data: AppData): List<PaletteEntry> = courseEntriesFor(data.courses) + staticEntries

        fun forData(data: AppData): SearchIndex {
            val courses = data.courses
            cachedIndex?.let { if (cachedIndexCourses === courses) return it }
            return of(entriesFor(data)).also {
                cachedIndex = it
                cachedIndexCourses = courses
            }
        }

        private val WHITESPACE = Regex("\\s+")

        private const val SCORE_CEILING = 0x00FF_FFFF
        private const val ORDER_MASK = 0xFFFF_FFFFL
    }
}
