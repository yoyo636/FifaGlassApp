package com.fifaglass.app.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 收藏球队（本地 SharedPreferences 持久化） */
object Favorites {
    private const val PREF_NAME = "fifaglass_prefs"
    private const val KEY_FAVORITES = "favorite_codes"

    var codes by mutableStateOf<Set<String>>(emptySet())
        private set

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        codes = prefs?.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()
    }

    fun isFavorite(code: String): Boolean = code in codes

    fun toggle(code: String) {
        codes = if (code in codes) codes - code else codes + code
        prefs?.edit()?.putStringSet(KEY_FAVORITES, codes)?.apply()
    }
}
