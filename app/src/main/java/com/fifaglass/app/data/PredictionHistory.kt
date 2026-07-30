package com.fifaglass.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class PredictionRecord(
    val id: String,
    val homeCode: String,
    val awayCode: String,
    val homeName: String,
    val awayName: String,
    val predictedHome: Double,
    val predictedDraw: Double,
    val predictedAway: Double,
    val likelyScore: String,
    val confidence: Int,
    val timestamp: Long,
    val actualResult: String? = null,
    val isCorrect: Boolean? = null,
)

object PredictionHistory {
    private const val PREF = "fifaglass_pred_history"
    private const val KEY = "records"
    private lateinit var prefs: SharedPreferences

    var records by mutableStateOf<List<PredictionRecord>>(emptyList())
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        load()
    }

    fun add(record: PredictionRecord) {
        val list = (records + record).sortedByDescending { it.timestamp }.take(200)
        records = list
        save(list)
    }

    fun updateResult(id: String, actualResult: String, isCorrect: Boolean) {
        val list = records.map {
            if (it.id == id) it.copy(actualResult = actualResult, isCorrect = isCorrect) else it
        }
        records = list
        save(list)
    }

    fun clear() {
        records = emptyList()
        prefs.edit().clear().apply()
    }

    fun accuracy(): Triple<Int, Int, Double> {
        val evaluated = records.filter { it.isCorrect != null }
        val correct = evaluated.count { it.isCorrect == true }
        val total = evaluated.size
        val rate = if (total > 0) correct.toDouble() / total else 0.0
        return Triple(correct, total, rate)
    }

    private fun load() {
        val json = prefs.getString(KEY, null) ?: return
        val arr = JSONArray(json)
        val list = mutableListOf<PredictionRecord>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(PredictionRecord(
                id = o.getString("id"),
                homeCode = o.getString("homeCode"),
                awayCode = o.getString("awayCode"),
                homeName = o.getString("homeName"),
                awayName = o.getString("awayName"),
                predictedHome = o.getDouble("predictedHome"),
                predictedDraw = o.getDouble("predictedDraw"),
                predictedAway = o.getDouble("predictedAway"),
                likelyScore = o.getString("likelyScore"),
                confidence = o.getInt("confidence"),
                timestamp = o.getLong("timestamp"),
                actualResult = o.optString("actualResult").ifEmpty { null },
                isCorrect = if (o.has("isCorrect") && !o.isNull("isCorrect")) o.getBoolean("isCorrect") else null,
            ))
        }
        records = list.sortedByDescending { it.timestamp }
    }

    private fun save(list: List<PredictionRecord>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("homeCode", r.homeCode)
                put("awayCode", r.awayCode)
                put("homeName", r.homeName)
                put("awayName", r.awayName)
                put("predictedHome", r.predictedHome)
                put("predictedDraw", r.predictedDraw)
                put("predictedAway", r.predictedAway)
                put("likelyScore", r.likelyScore)
                put("confidence", r.confidence)
                put("timestamp", r.timestamp)
                put("actualResult", r.actualResult ?: "")
                put("isCorrect", if (r.isCorrect != null) r.isCorrect else JSONObject.NULL)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }
}
