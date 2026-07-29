package com.fifaglass.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 双层缓存：
 *  L1 内存（进程存活期间有效，纳秒级命中）
 *  L2 SharedPreferences（跨进程持久化，启动时秒级命中）
 *
 * 后台预取：应用启动后异步把常用数据（男女足排名 + 近两周比赛）
 * 拉进缓存，避免首次切 Tab 时白屏等待。
 */
object Cache {

    private const val PREF = "fifaglass_cache"
    private const val KEY_PREFIX = "cache_"
    private const val TS_SUFFIX = "_ts"

    // 不同资源的 TTL（毫秒）
    const val TTL_RANKING_MS = 30 * 60 * 1000L   // 排名 30 分钟
    const val TTL_MATCHES_MS = 5 * 60 * 1000L    // 比赛列表 5 分钟
    const val TTL_DETAIL_MS = 60 * 1000L         // 单场详情 1 分钟
    const val TTL_COMP_MS = 60 * 60 * 1000L      // 赛事列表 1 小时

    // 内存层
    private val mem = HashMap<String, Pair<Long, String>>()

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    }

    private fun now() = System.currentTimeMillis()

    /** 写入缓存（内存 + 磁盘） */
    fun put(key: String, json: String) {
        mem[key] = now() to json
        prefs?.edit()
            ?.putString(KEY_PREFIX + key, json)
            ?.putLong(KEY_PREFIX + key + TS_SUFFIX, now())
            ?.apply()
    }

    /**
     * 读取缓存。
     * 返回 Pair(json, isFresh)：isFresh 表示未过期；
     * 即使过期也会返回数据，供网络失败时降级。
     */
    fun get(key: String, ttlMs: Long): Pair<String, Boolean>? {
        mem[key]?.let { (ts, json) ->
            return json to (now() - ts < ttlMs)
        }
        val p = prefs ?: return null
        val json = p.getString(KEY_PREFIX + key, null) ?: return null
        val ts = p.getLong(KEY_PREFIX + key + TS_SUFFIX, 0L)
        mem[key] = ts to json
        return json to (now() - ts < ttlMs)
    }

    /** 清空所有缓存 */
    fun clear() {
        mem.clear()
        prefs?.edit()?.clear()?.apply()
    }

    /** 估算缓存大小（字节） */
    fun estimateSize(): Int {
        var total = 0
        mem.values.forEach { total += it.second.length }
        val p = prefs ?: return total
        p.all.forEach { (k, v) ->
            if (k.startsWith(KEY_PREFIX) && v is String) total += v.length
        }
        return total
    }

    // ---------- 后台预取 ----------

    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 后台预取常用数据，不阻塞启动。
     * 静默吞掉所有异常：失败也无所谓，用户点击时会现拉。
     */
    fun prefetch() {
        prefetchScope.launch {
            runCatching { FifaApi.fetchRankings(1) }
            runCatching { FifaApi.fetchRankings(2) }
            runCatching { FifaApi.fetchLiveMatches() }
            runCatching { FifaApi.fetchRecentMatches() }
            runCatching { FifaApi.fetchCompetitions() }
        }
    }
}
