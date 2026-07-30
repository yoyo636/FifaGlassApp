package com.fifaglass.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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

    var cachedChannels: List<StreamChannel>? = null

    /** 从 iptv-org JSON API 获取体育频道（channels.json + streams.json 关联） */
    fun fetchSportsChannels(): List<StreamChannel> {
        cachedChannels?.let { return it }

        val channelsJson = fetchJson(IPTV_CHANNELS)
        val streamsJson = fetchJson(IPTV_STREAMS)

        val channels = JSONArray(channelsJson)
        val streams = JSONArray(streamsJson)

        val streamMap = HashMap<String, MutableList<JSONObject>>()
        for (i in 0 until streams.length()) {
            val s = streams.getJSONObject(i)
            val chId = s.optString("channel")
            streamMap.getOrPut(chId) { mutableListOf() }.add(s)
        }

        val result = mutableListOf<StreamChannel>()
        for (i in 0 until channels.length()) {
            val ch = channels.getJSONObject(i)
            val categories = ch.optJSONArray("categories") ?: continue
            var isSports = false
            for (j in 0 until categories.length()) {
                if (categories.getString(j) == "sports") { isSports = true; break }
            }
            if (!isSports) continue

            val chId = ch.optString("id")
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

        cachedChannels = result
        return result
    }

    /** 解析 M3U 播放列表 */
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
                currentLogo = logoMatch?.groupValues?.get(1) ?: ""
                val groupMatch = Regex("group-title=\"([^\"]+)\"").find(trimmed)
                currentGroup = groupMatch?.groupValues?.get(1) ?: ""
                val qualityMatch = Regex("tvg-quality=\"([^\"]+)\"").find(trimmed)
                currentQuality = qualityMatch?.groupValues?.get(1) ?: ""
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
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
                currentName = ""
                currentLogo = ""
                currentGroup = ""
            }
        }
        return channels
    }

    /** 从 M3U 源获取体育频道 */
    fun fetchFromM3U(url: String): List<StreamChannel> {
        val raw = fetchText(url)
        return parseM3U(raw)
    }

    /** 综合获取所有体育流频道 */
    fun fetchAllSportsStreams(): List<StreamChannel> {
        val result = mutableListOf<StreamChannel>()

        runCatching {
            result.addAll(fetchSportsChannels())
        }
        runCatching {
            result.addAll(fetchFromM3U(IPTV_SPORTS_M3U))
        }
        runCatching {
            result.addAll(fetchFromM3U(FREE_TV_M3U))
        }

        return result.distinctBy { it.url }
    }

    /** 按关键词搜索频道 */
    fun searchChannels(channels: List<StreamChannel>, query: String): List<StreamChannel> {
        if (query.isBlank()) return channels
        val q = query.trim().lowercase()
        return channels.filter {
            it.name.lowercase().contains(q) ||
            it.country.lowercase().contains(q) ||
            it.category.lowercase().contains(q)
        }
    }

    /** 根据比赛信息匹配可能的转播频道 */
    fun matchChannelsForMatch(channels: List<StreamChannel>, m: MatchInfo): List<StreamChannel> {
        val keywords = mutableListOf(m.competition)
        if (m.homeCode.isNotEmpty()) {
            keywords.add(m.homeCode)
            keywords.add(m.homeName)
        }
        if (m.awayCode.isNotEmpty()) {
            keywords.add(m.awayCode)
            keywords.add(m.awayName)
        }

        val lowerKeywords = keywords.filter { it.isNotEmpty() }.map { it.lowercase() }
        if (lowerKeywords.isEmpty()) return channels.filter { it.category.contains("sport", ignoreCase = true) }.take(20)

        val matched = channels.filter { ch ->
            val lowerName = ch.name.lowercase()
            lowerKeywords.any { kw -> lowerName.contains(kw) }
        }

        return if (matched.isNotEmpty()) matched else channels.filter { it.category.contains("sport", ignoreCase = true) }.take(20)
    }

    private fun fetchJson(url: String): String = fetchText(url)

    private fun fetchText(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "*/*")
        }
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** 获取可用的流源列表 */
    fun getSources(): List<StreamSource> = extraSources

    /** 获取常用体育频道快捷列表 */
    val presetChannels = listOf(
        StreamChannel(
            id = "fifa-plus",
            name = "FIFA+ 官方直播",
            url = "https://www.fifa.com/fifaplus/en/tournaments",
            logo = "",
            country = "INT",
            category = "football",
            quality = "1080p",
            referrer = "",
            userAgent = UA,
        ),
        StreamChannel(
            id = "cctv5",
            name = "CCTV-5 体育频道",
            url = "https://iptv-org.github.io/iptv/categories/sports.m3u",
            logo = "",
            country = "CN",
            category = "sports",
            quality = "1080p",
            referrer = "",
            userAgent = UA,
        ),
    )
}
