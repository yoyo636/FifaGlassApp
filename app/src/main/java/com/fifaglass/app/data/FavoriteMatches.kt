package com.fifaglass.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

object FavoriteMatches {
    private const val PREF = "fifaglass_fav_matches"
    private const val KEY = "matches"
    private lateinit var prefs: SharedPreferences

    var matchIds by mutableStateOf<Set<String>>(emptySet())
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        load()
    }

    fun isFavorite(matchId: String): Boolean = matchId in matchIds

    fun toggle(matchId: String) {
        matchIds = if (matchId in matchIds) matchIds - matchId else matchIds + matchId
        save()
    }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        val arr = JSONArray(raw)
        val set = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            set.add(arr.getString(i))
        }
        matchIds = set
    }

    private fun save() {
        val arr = JSONArray()
        matchIds.forEach { arr.put(it) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }
}
