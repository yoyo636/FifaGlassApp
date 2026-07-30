package com.fifaglass.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class TeamGroup(
    val name: String,
    val codes: List<String>,
    val color: String,
)

object CustomGroups {
    private const val PREF = "fifaglass_groups"
    private const val KEY = "groups"
    private lateinit var prefs: SharedPreferences

    var groups by mutableStateOf<List<TeamGroup>>(emptyList())
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        load()
    }

    fun addGroup(name: String, codes: List<String>, color: String = "blue") {
        val g = TeamGroup(name = name, codes = codes, color = color)
        groups = groups + g
        save()
    }

    fun removeGroup(name: String) {
        groups = groups.filter { it.name != name }
        save()
    }

    fun addToGroup(groupName: String, code: String) {
        groups = groups.map {
            if (it.name == groupName && code !in it.codes) it.copy(codes = it.codes + code) else it
        }
        save()
    }

    fun removeFromGroup(groupName: String, code: String) {
        groups = groups.map {
            if (it.name == groupName) it.copy(codes = it.codes - code) else it
        }
        save()
    }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        val arr = JSONArray(raw)
        val list = mutableListOf<TeamGroup>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val codes = mutableListOf<String>()
            val cArr = o.getJSONArray("codes")
            for (j in 0 until cArr.length()) codes.add(cArr.getString(j))
            list.add(TeamGroup(name = o.getString("name"), codes = codes, color = o.optString("color", "blue")))
        }
        groups = list
    }

    private fun save() {
        val arr = JSONArray()
        groups.forEach { g ->
            arr.put(JSONObject().apply {
                put("name", g.name)
                put("codes", JSONArray(g.codes))
                put("color", g.color)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }
}
