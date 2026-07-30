package com.fifaglass.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class StreamChannel(
    val id: String,
    val name: String,
    val url: String,
    val logo: String,
    val country: String,
    val category: String,
    val quality: String,
    val referrer: String,
    val userAgent: String,
)

data class StreamSource(
    val name: String,
    val url: String,
    val type: String,
)

object StreamRepository {

    private const val UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

    private const val IPTV_CHANNELS = "https://iptv-org.github.io/api/channels.json"
    private const val IPTV_STREAMS = "https://iptv-org.github.io/api/streams.json"
    private const val IPTV_SPORTS_M3U = "https://iptv-org.github.io/iptv/categories/sports.m3u"
    private const val FREE_TV_M3U = "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8"

    private val extraSources = listOf(
        StreamSource("iptv-org 体育", IPTV_SPORTS_M3U, "m3u"),
        StreamSource("Free-TV 精选", FREE_TV_M3U, "m3u"),
        StreamSource("iptv-org API", "", "json"),
    )

    @Volatile
    private var cachedChannels: List<StreamChannel>? = null
    private var lastFetchMs: Long = 0L
    private const val CACHE_TTL_MS = 30 * 60 * 1000L

    private var favPrefs: SharedPreferences? = null

    fun initFavorites(context: Context) {
        if (favPrefs == null) {
            favPrefs = context.applicationContext.getSharedPreferences("fav_channels", Context.MODE_PRIVATE)
        }
    }

    fun toggleFavorite(url: String) {
        val prefs = favPrefs ?: return
        val set = (prefs.getStringSet("favorites", setOf()) ?: setOf()).toMutableSet()
        if (url in set) set.remove(url) else set.add(url)
        prefs.edit().putStringSet("favorites", set).apply()
    }

    fun isFavorite(url: String): Boolean {
        val prefs = favPrefs ?: return false
        return (prefs.getStringSet("favorites", setOf()) ?: setOf()).contains(url)
    }

    fun getFavoriteUrls(): Set<String> {
        val prefs = favPrefs ?: return emptySet()
        return prefs.getStringSet("favorites", setOf()) ?: emptySet()
    }

    val presetChannels: List<StreamChannel> = listOf(
        StreamChannel("fifa-plus", "FIFA+ 官方", "https://www.fifa.com/fifaplus/en/tournaments", "", "INT", "football", "1080p", "", UA),
        StreamChannel("cctv5", "CCTV-5 体育频道", IPTV_SPORTS_M3U, "", "CN", "sports", "1080p", "", UA),
        StreamChannel("espn", "ESPN 体育", IPTV_SPORTS_M3U, "", "US", "sports", "1080p", "", UA),
        StreamChannel("skysports", "Sky Sports", IPTV_SPORTS_M3U, "", "GB", "sports", "1080p", "", UA),
        StreamChannel("beinsports", "beIN Sports", IPTV_SPORTS_M3U, "", "FR", "sports", "1080p", "", UA),
        StreamChannel("eurosport", "Eurosport", IPTV_SPORTS_M3U, "", "EU", "sports", "1080p", "", UA),
    )

    fun quickChannels(): List<StreamChannel> {
        val cached = cachedChannels
        return if (cached.isNullOrEmpty()) presetChannels else presetChannels + cached
    }

    suspend fun loadAllAsync(): List<StreamChannel> {
        val now = System.currentTimeMillis()
        val cached = cachedChannels
        if (!cached.isNullOrEmpty() && now - lastFetchMs < CACHE_TTL_MS) {
            return presetChannels + cached
        }
        return try {
            val all = fetchAllInternal()
            cachedChannels = all
            lastFetchMs = System.currentTimeMillis()
            presetChannels + all
        } catch (_: Exception) {
            cached ?: emptyList()
        }
    }

    fun fetchAllSportsStreams(): List<StreamChannel> = quickChannels()

    private fun fetchAllInternal(): List<StreamChannel> {
        val result = ConcurrentHashMap.newKeySet<String>()
        val channels = java.util.Collections.synchronizedList(mutableListOf<StreamChannel>())

        val tasks = listOf(
            { runCatching { fetchSportsChannelsInternal() }.getOrNull() },
            { runCatching { fetchFromM3U(IPTV_SPORTS_M3U) }.getOrNull() },
            { runCatching { fetchFromM3U(FREE_TV_M3U) }.getOrNull() },
        )

        for (task in tasks) {
            val list = task() ?: continue
            for (ch in list) {
                if (result.add(ch.url)) channels.add(ch)
            }
        }

        return channels.toList()
    }

    fun fetchSportsChannels(): List<StreamChannel> = fetchSportsChannelsInternal()

    private fun fetchSportsChannelsInternal(): List<StreamChannel> {
        val channelsJson = fetchJson(IPTV_CHANNELS)
        val streamsJson = fetchJson(IPTV_STREAMS)

        val channels = JSONArray(channelsJson)
        val streams = JSONArray(streamsJson)

        val streamMap = HashMap<String, MutableList<JSONObject>>()
        for (i in 0 until streams.length()) {
            val s = streams.getJSONObject(i)
            val chId = s.optString("channel")
            if (chId.isNotEmpty()) {
                streamMap.getOrPut(chId) { mutableListOf() }.add(s)
            }
        }

        val result = mutableListOf<StreamChannel>()
        for (i in 0 until channels.length()) {
            val ch = channels.getJSONObject(i)
            val categories = ch.optJSONArray("categories") ?: continue
            var isSports = false
            for (j in 0 until categories.length()) {
                if (categories.optString(j) == "sports") { isSports = true; break }
            }
            if (!isSports) continue

            val chId = ch.optString("id")
            if (chId.isEmpty()) continue
            val name = ch.optString("name").ifEmpty { chId }
            val logo = ch.optString("logo", "")
            val country = ch.optString("country", "")

            val chStreams = streamMap[chId] ?: continue
            for (s in chStreams) {
                val url = s.optString("url")
                if (url.isEmpty()) continue
                result.add(StreamChannel(
                    id = chId,
                    name = name,
                    url = url,
                    logo = logo,
                    country = country,
                    category = "sports",
                    quality = s.optString("quality", ""),
                    referrer = s.optString("referrer", ""),
                    userAgent = s.optString("user_agent", UA),
                ))
            }
        }

        return result
    }

    fun parseM3U(raw: String): List<StreamChannel> {
        val channels = mutableListOf<StreamChannel>()
        val lines = raw.lines()
        var currentName = ""
        var currentLogo = ""
        var currentGroup = ""
        var currentQuality = ""

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF")) {
                val nameMatch = Regex(",(.+)$").find(trimmed)
                currentName = nameMatch?.groupValues?.lastOrNull()?.trim() ?: ""
                val logoMatch = Regex("tvg-logo=\"([^\"]+)\"").find(trimmed)
                currentLogo = logoMatch?.groupValues?.getOrNull(1) ?: ""
                val groupMatch = Regex("group-title=\"([^\"]+)\"").find(trimmed)
                currentGroup = groupMatch?.groupValues?.getOrNull(1) ?: ""
                val qualityMatch = Regex("tvg-quality=\"([^\"]+)\"").find(trimmed)
                currentQuality = qualityMatch?.groupValues?.getOrNull(1) ?: ""
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                if (currentName.isNotEmpty()) {
                    channels.add(StreamChannel(
                        id = currentName,
                        name = currentName,
                        url = trimmed,
                        logo = currentLogo,
                        country = "",
                        category = currentGroup,
                        quality = currentQuality,
                        referrer = "",
                        userAgent = UA,
                    ))
                }
                currentName = ""
                currentLogo = ""
                currentGroup = ""
            }
        }
        return channels
    }

    fun fetchFromM3U(url: String): List<StreamChannel> {
        val raw = fetchText(url)
        return parseM3U(raw)
    }

    fun searchChannels(channels: List<StreamChannel>, query: String): List<StreamChannel> {
        if (query.isBlank()) return channels
        val q = query.trim().lowercase()
        return channels.filter {
            it.name.lowercase().contains(q) ||
            it.country.lowercase().contains(q) ||
            it.category.lowercase().contains(q)
        }
    }

    fun matchChannelsForMatch(channels: List<StreamChannel>, m: MatchInfo): List<StreamChannel> {
        val keywords = mutableListOf<String>()
        if (m.competition.isNotEmpty()) keywords.add(m.competition)
        if (m.homeCode.isNotEmpty()) { keywords.add(m.homeCode); keywords.add(m.homeName) }
        if (m.awayCode.isNotEmpty()) { keywords.add(m.awayCode); keywords.add(m.awayName) }

        val lowerKeywords = keywords.filter { it.isNotEmpty() }.map { it.lowercase() }
        if (lowerKeywords.isEmpty()) {
            return channels.filter { it.category.contains("sport", ignoreCase = true) }.take(20)
        }

        val matched = channels.filter { ch ->
            val lowerName = ch.name.lowercase()
            lowerKeywords.any { kw -> lowerName.contains(kw) }
        }

        return if (matched.isNotEmpty()) matched
        else channels.filter { it.category.contains("sport", ignoreCase = true) }.take(20)
    }

    private fun fetchJson(url: String): String = fetchText(url)

    private fun fetchText(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6000
            readTimeout = 8000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Connection", "close")
        }
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun getSources(): List<StreamSource> = extraSources
}
