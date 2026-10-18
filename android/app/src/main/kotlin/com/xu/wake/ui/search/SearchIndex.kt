package com.xu.wake.ui.search

import com.xu.wake.data.AppData
import com.xu.wake.data.CourseSession
import com.xu.wake.ui.palette.PaletteEntry
import com.xu.wake.ui.palette.courseEntries
import com.xu.wake.ui.palette.paletteActions
import com.xu.wake.ui.palette.settingEntries

/**
 * A palette entry prepared for repeated scoring: fields are pre-lowercased so a keystroke neither
 * re-allocates a SearchText nor re-folds case per character.
 */
class IndexedEntry internal constructor(
    val entry: PaletteEntry,
    /** Position in the original list — the tie-breaker that keeps ordering identical to a stable full sort. */
    val order: Int,
    title: String,
    path: String,
    keywords: String,
) {
    val title: String = title.lowercase()
    val path: String = path.lowercase()
    val keywords: String = keywords.lowercase()

    /** Same weights as [com.xu.wake.ui.searchScore]; -1 when any term misses every field. */
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

/** One result set. Hand it back to [SearchIndex.search] as refineFrom to narrow the next query. */
class SearchResult internal constructor(
    /** Lowercased terms that produced this result. */
    val terms: List<String>,
    val entries: List<PaletteEntry>,
    internal val hits: List<IndexedEntry>,
    internal val scores: IntArray,
) {
    val isEmpty: Boolean get() = entries.isEmpty()

    /** Score of the result at [index]; label highlighting and ordering both use it. */
    fun scoreAt(index: Int): Int = scores[index]

    companion object {
        val Empty = SearchResult(emptyList(), emptyList(), emptyList(), IntArray(0))
    }
}

/**
 * Searchable projection of the app's palette entries.
 *
 * Two costs leave the typing path:
 *  - the 43 static entries (settings + actions) are built once per process instead of on every
 *    [AppData] change, and course entries are rebuilt only when the course list instance changes;
 *  - refining a query ("周" -> "周次") rescans only the previous hits.
 *
 * Refinement is exact because results are ordered by (score desc, original index asc) — a total
 * order that does not depend on the input order, so a stable full sort agrees with it.
 */
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
        // Pack (inverted score, original index) into one Long and sort primitives: no boxing per
        // comparison, and the index component makes the order total, so ties keep palette order.
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
        /** Split on whitespace and fold case — the shared shape of every scoring term. */
        fun normalize(raw: String): List<String> =
            raw.trim().lowercase().split(WHITESPACE).filter { it.isNotEmpty() }

        /** Settings and action entries never depend on user data; build them once per process. */
        private val staticEntries: List<PaletteEntry> by lazy { settingEntries() + paletteActions }

        private var cachedCourses: List<CourseSession>? = null
        private var cachedCourseEntries: List<PaletteEntry> = emptyList()
        private var cachedIndex: SearchIndex? = null
        private var cachedIndexCourses: List<CourseSession>? = null

        /** Course entries are rebuilt only when the course list instance actually changes. */
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

        /** Scores are term-count × 100 at most; this keeps the packed (score, index) key positive. */
        private const val SCORE_CEILING = 0x00FF_FFFF
        private const val ORDER_MASK = 0xFFFF_FFFFL
    }
}
