package com.xunyyds.kelaile.core.prefs

import android.content.Context

/**
 * Recent search queries, most recent first.
 *
 * Each query also stores the wall-clock time it was last used (an epoch-millis
 * side table keyed by the query string). The suggestion ranker needs that to
 * decay stale searches; without it "recency" would be list position only, which
 * never forgets anything.
 */
class SearchHistory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("kelaile_search", Context.MODE_PRIVATE)

    fun items(): List<String> = prefs.getString(KEY, "").orEmpty().split(SEPARATOR).filter { it.isNotBlank() }

    /** Last usage time per query; a missing entry means "unknown", treated as old. */
    fun times(): Map<String, Long> {
        val raw = prefs.getString(TIMES_KEY, "").orEmpty()
        if (raw.isBlank()) return emptyMap()
        val out = HashMap<String, Long>()
        raw.split(RECORD_SEPARATOR).forEach { record ->
            val cut = record.lastIndexOf(TIME_DELIMITER)
            if (cut > 0) {
                val text = record.substring(0, cut)
                val stamp = record.substring(cut + 1).toLongOrNull()
                if (text.isNotBlank() && stamp != null && stamp > 0L) out[text] = stamp
            }
        }
        return out
    }

    fun add(query: String, nowMillis: Long = System.currentTimeMillis()) {
        val value = query.trim().take(MAX_QUERY)
        if (value.isEmpty()) return
        val merged = (listOf(value) + items().filterNot { it == value }).take(MAX_ITEMS)
        val stamps = times().toMutableMap()
        stamps[value] = nowMillis
        prefs.edit()
            .putString(KEY, merged.joinToString(SEPARATOR))
            .putString(TIMES_KEY, merged.mapNotNull { text -> stamps[text]?.let { text + TIME_DELIMITER + it } }.joinToString(RECORD_SEPARATOR))
            .apply()
    }

    fun remove(query: String) {
        val remaining = items().filterNot { it == query }
        val stamps = times().toMutableMap()
        stamps.remove(query)
        prefs.edit()
            .putString(KEY, remaining.joinToString(SEPARATOR))
            .putString(TIMES_KEY, remaining.mapNotNull { text -> stamps[text]?.let { text + TIME_DELIMITER + it } }.joinToString(RECORD_SEPARATOR))
            .apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).remove(TIMES_KEY).apply()
    }

    private companion object {
        const val KEY = "history"
        const val TIMES_KEY = "history_times"
        const val SEPARATOR = "\u0001"
        const val RECORD_SEPARATOR = "\u0002"
        const val TIME_DELIMITER = "\u0003"
        const val MAX_ITEMS = 8
        const val MAX_QUERY = 40
    }
}
