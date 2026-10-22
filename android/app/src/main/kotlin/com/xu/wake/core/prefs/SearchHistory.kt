package com.xu.wake.core.prefs

import android.content.Context

                                                                                  
class SearchHistory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("kelaile_search", Context.MODE_PRIVATE)

    fun items(): List<String> = prefs.getString(KEY, "").orEmpty().split(SEPARATOR).filter { it.isNotBlank() }

    fun add(query: String) {
        val value = query.trim().take(MAX_QUERY)
        if (value.isEmpty()) return
        val merged = (listOf(value) + items().filterNot { it == value }).take(MAX_ITEMS)
        prefs.edit().putString(KEY, merged.joinToString(SEPARATOR)).apply()
    }

    fun remove(query: String) {
        prefs.edit().putString(KEY, items().filterNot { it == query }.joinToString(SEPARATOR)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "history"
        const val SEPARATOR = "\u0001"
        const val MAX_ITEMS = 8
        const val MAX_QUERY = 40
    }
}
