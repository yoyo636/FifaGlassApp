package com.fifaglass.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object SearchHistory {
    private const val PREF = "fifaglass_search_history"
    private const val KEY = "queries"
    private lateinit var prefs: SharedPreferences

    var queries by mutableStateOf<List<String>>(emptyList())
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        load()
    }

    fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val list = (listOf(trimmed) + queries.filter { it != trimmed }).take(20)
        queries = list
        save(list)
    }

    fun remove(query: String) {
        val list = queries.filter { it != query }
        queries = list
        save(list)
    }

    fun clear() {
        queries = emptyList()
        prefs.edit().clear().apply()
    }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        queries = raw.split("\n").filter { it.isNotEmpty() }
    }

    private fun save(list: List<String>) {
        prefs.edit().putString(KEY, list.joinToString("\n")).apply()
    }
}
