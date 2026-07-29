package com.fifaglass.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * FIFA 官网内部 JSON 接口（无需 API Key）。
 * 排名:      https://api.fifa.com/api/v3/rankings?locale=en&gender=1|2
 * 实时:      https://api.fifa.com/api/v3/live/football?language=en
 * 赛程:      https://api.fifa.com/api/v3/calendar/matches?language=en&from=YYYY-MM-DD&to=YYYY-MM-DD[&idTeam=ID]
 * 单场详情:  https://api.fifa.com/api/v3/live/football/{comp}/{season}/{stage}/{match}
 *            —— 内含进球、红黄牌、换人、阵容、阵型、裁判、球场
 */
object FifaApi {

    private const val UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20000
            readTimeout = 20000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json")
        }
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun localized(arr: JSONArray?): String {
        if (arr == null || arr.length() == 0) return ""
        var fallback = arr.getJSONObject(0).optString("Description")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("Locale").equals("en-gb", ignoreCase = true)) {
                val d = o.optString("Description")
                if (d.isNotEmpty()) return d
            }
        }
        return fallback
    }

    /** gender: 1=男足 2=女足，返回按名次升序 */
    fun fetchRankings(gender: Int): List<Team> {
        val root = JSONObject(get("https://api.fifa.com/api/v3/rankings?locale=en&gender=$gender"))
        val arr = root.getJSONArray("Results")
        val out = ArrayList<Team>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val points = if (o.has("DecimalTotalPoints") && !o.isNull("DecimalTotalPoints"))
                o.getDouble("DecimalTotalPoints") else o.optDouble("TotalPoints")
            out += Team(
                idTeam = o.optString("IdTeam"),
                rank = o.optInt("Rank"),
                name = localized(o.optJSONArray("TeamName")),
                code = o.optString("IdCountry"),
                confederation = o.optString("ConfederationName"),
                points = points,
                prevRank = o.optInt("PrevRank"),
                rankChange = o.optInt("RankingMovement"),
            )
        }
        return out.sortedBy { it.rank }
    }

    /** 当前进行/即将开始的比赛 */
    fun fetchLiveMatches(gender: Int = 1): List<MatchInfo> =
        parseMatches(JSONObject(get("https://api.fifa.com/api/v3/live/football?language=en&gender=$gender")), "HomeTeam", "AwayTeam", true)

    /** 近两周到未来一周的赛程赛果 */
    fun fetchRecentMatches(gender: Int = 1): List<MatchInfo> {
        val today = java.time.LocalDate.now()
        val from = today.minusDays(14).toString()
        val to = today.plusDays(7).toString()
        val root = JSONObject(
            get("https://api.fifa.com/api/v3/calendar/matches?language=en&from=$from&to=$to&count=200&gender=$gender")
        )
        return parseMatches(root, "Home", "Away")
    }

    /** 某支球队近 60 天到未来 14 天的比赛 */
    fun fetchTeamMatches(idTeam: String, gender: Int = 1): List<MatchInfo> =
        fetchTeamMatchesRange(idTeam, -60, 14, gender)

    /** 某支球队在指定时间窗口内的比赛（worker 调用） */
    fun fetchTeamMatchesRange(idTeam: String, fromDays: Int, toDays: Int, gender: Int = 1): List<MatchInfo> {
        val today = java.time.LocalDate.now()
        val from = today.plusDays(fromDays.toLong()).toString()
        val to = today.plusDays(toDays.toLong()).toString()
        val root = JSONObject(
            get("https://api.fifa.com/api/v3/calendar/matches?language=en&idTeam=$idTeam&from=$from&to=$to&count=30&gender=$gender")
        )
        return parseMatches(root, "Home", "Away").sortedByDescending { it.date }
    }

    /** 赛事列表（联赛/杯赛） */
    fun fetchCompetitions(): List<Competition> {
        val root = JSONObject(get("https://api.fifa.com/api/v3/competitions?language=en&count=300"))
        val arr = root.optJSONArray("Results") ?: return emptyList()
        val out = ArrayList<Competition>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = localized(o.optJSONArray("Name"))
            if (name.isEmpty()) continue
            val region = buildList {
                o.optJSONArray("IdConfederation")?.let { c ->
                    if (c.length() > 0) add(c.optString(0))
                }
                o.optJSONArray("IdMemberAssociation")?.let { c ->
                    if (c.length() > 0) add(c.optString(0))
                }
            }.joinToString(" · ")
            out += Competition(
                id = o.optString("IdCompetition"),
                name = name,
                region = region,
            )
        }
        return out.sortedBy { it.name }
    }

    /** 某赛事近 21 天到未来 7 天的比赛 */
    fun fetchCompetitionMatches(compId: String, gender: Int = 1): List<MatchInfo> {
        val today = java.time.LocalDate.now()
        val from = today.minusDays(21).toString()
        val to = today.plusDays(7).toString()
        val root = JSONObject(
            get("https://api.fifa.com/api/v3/calendar/matches?language=en&idCompetition=$compId&from=$from&to=$to&count=100&gender=$gender")
        )
        return parseMatches(root, "Home", "Away").sortedByDescending { it.date }
    }

    private fun parseMatches(root: JSONObject, homeKey: String, awayKey: String, fromLive: Boolean = false): List<MatchInfo> {
        val arr = root.optJSONArray("Results") ?: return emptyList()
        val out = ArrayList<MatchInfo>(arr.length())
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val h = m.optJSONObject(homeKey)
            val a = m.optJSONObject(awayKey)

            fun sideScore(side: JSONObject?, fallbackKey: String): Int? {
                side?.let {
                    if (!it.isNull("Score")) return it.optInt("Score")
                }
                if (m.has(fallbackKey) && !m.isNull(fallbackKey)) return m.optInt(fallbackKey)
                return null
            }

            out += MatchInfo(
                id = m.optString("IdMatch"),
                compId = m.optString("IdCompetition"),
                seasonId = m.optString("IdSeason"),
                stageId = m.optString("IdStage"),
                competition = localized(m.optJSONArray("CompetitionName")),
                homeName = localized(h?.optJSONArray("TeamName")).ifEmpty { "待定" },
                awayName = localized(a?.optJSONArray("TeamName")).ifEmpty { "待定" },
                homeCode = h?.optString("IdCountry").orEmpty(),
                awayCode = a?.optString("IdCountry").orEmpty(),
                homeScore = sideScore(h, "HomeTeamScore"),
                awayScore = sideScore(a, "AwayTeamScore"),
                status = m.optInt("MatchStatus"),
                matchTime = m.optString("MatchTime"),
                date = m.optString("Date"),
                homeTactics = h?.optString("Tactics")?.takeIf { t -> t.isNotEmpty() && t != "null" },
                awayTactics = a?.optString("Tactics")?.takeIf { t -> t.isNotEmpty() && t != "null" },
                fromLiveEndpoint = fromLive,
            )
        }
        return out
    }

    /** 分钟文本如 "45+3'" -> 48；"30'" -> 30 */
    private fun minuteOf(raw: String): Int {
        val digits = raw.filter { it.isDigit() || it == '+' }
        if (digits.isEmpty()) return 0
        return digits.split('+').filter { it.isNotEmpty() }
            .fold(0) { acc, p -> acc + (p.toIntOrNull() ?: 0) }
    }

    /** Period: 3=上半场 5=下半场 7/9=加时 11=点球；用于事件排序分层 */
    private fun periodBase(period: Int): Int = when (period) {
        3 -> 3000
        5 -> 5000
        7 -> 7000
        9 -> 9000
        11 -> 11000
        else -> 1000
    }

    /** 单场完整详情：事件时间轴 + 阵容 + 技术信息 */
    fun fetchMatchDetail(m: MatchInfo): MatchDetail {
        require(m.compId.isNotEmpty() && m.seasonId.isNotEmpty() && m.stageId.isNotEmpty()) {
            "该场比赛没有详情数据"
        }
        val root = JSONObject(
            get("https://api.fifa.com/api/v3/live/football/${m.compId}/${m.seasonId}/${m.stageId}/${m.id}")
        )
        val home = root.optJSONObject("HomeTeam")
        val away = root.optJSONObject("AwayTeam")

        fun parsePlayers(side: JSONObject?): Pair<List<PlayerInfo>, Map<String, PlayerInfo>> {
            val arr = side?.optJSONArray("Players") ?: return emptyList<PlayerInfo>() to emptyMap()
            val list = ArrayList<PlayerInfo>(arr.length())
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                list += PlayerInfo(
                    id = p.optString("IdPlayer"),
                    name = localized(p.optJSONArray("PlayerName")).ifEmpty { "未知" },
                    number = p.optInt("ShirtNumber"),
                    position = p.optInt("Position"),
                    captain = p.optBoolean("Captain"),
                    starter = p.optInt("Status") == 1,
                )
            }
            return list to list.associateBy { it.id }
        }

        val (homePlayers, homeById) = parsePlayers(home)
        val (awayPlayers, awayById) = parsePlayers(away)
        val allById = homeById + awayById

        fun playerName(id: String?): String =
            if (id.isNullOrEmpty()) "" else allById[id]?.name ?: ""

        val events = ArrayList<MatchEvent>()

        // 进球
        for ((side, isHome) in listOf(home to true, away to false)) {
            val goals = side?.optJSONArray("Goals") ?: continue
            for (i in 0 until goals.length()) {
                val g = goals.getJSONObject(i)
                val scorer = playerName(g.optString("IdPlayer")).ifEmpty { "进球" }
                val assist = playerName(g.optString("IdAssistPlayer"))
                events += MatchEvent(
                    type = EventType.GOAL,
                    minuteLabel = g.optString("Minute"),
                    sortKey = periodBase(g.optInt("Period")) + minuteOf(g.optString("Minute")),
                    isHome = isHome,
                    title = scorer,
                    subtitle = if (assist.isNotEmpty()) "助攻：$assist" else "进球",
                )
            }
            // 红黄牌
            val bookings = side.optJSONArray("Bookings") ?: continue
            for (i in 0 until bookings.length()) {
                val b = bookings.getJSONObject(i)
                val who = playerName(b.optString("IdPlayer")).ifEmpty { "球队官员" }
                val isYellow = b.optInt("Card") == 1
                events += MatchEvent(
                    type = if (isYellow) EventType.YELLOW else EventType.RED,
                    minuteLabel = b.optString("Minute"),
                    sortKey = periodBase(b.optInt("Period")) + minuteOf(b.optString("Minute")),
                    isHome = isHome,
                    title = who,
                    subtitle = if (isYellow) "黄牌" else "红牌",
                )
            }
            // 换人
            val subs = side.optJSONArray("Substitutions") ?: continue
            for (i in 0 until subs.length()) {
                val s = subs.getJSONObject(i)
                val on = localized(s.optJSONArray("PlayerOnName"))
                    .ifEmpty { playerName(s.optString("IdPlayerOn")) }
                val off = localized(s.optJSONArray("PlayerOffName"))
                    .ifEmpty { playerName(s.optString("IdPlayerOff")) }
                events += MatchEvent(
                    type = EventType.SUB,
                    minuteLabel = s.optString("Minute"),
                    sortKey = periodBase(s.optInt("Period")) + minuteOf(s.optString("Minute")),
                    isHome = isHome,
                    title = on.ifEmpty { "换人调整" },
                    subtitle = if (off.isNotEmpty()) "换下：$off" else "替补登场",
                )
            }
        }

        // 关键时间节点
        events += MatchEvent(EventType.KICKOFF, "0'", 0, true, "比赛开始")
        if (m.isLive || m.isFinished) {
            events += MatchEvent(EventType.HALF_TIME, "HT", 4500, true, "中场休息")
        }
        if (m.isFinished) {
            events += MatchEvent(EventType.FULL_TIME, "FT", 9999, true, "比赛结束")
        } else if (m.isLive && m.matchTime.isNotEmpty()) {
            val cur = minuteOf(m.matchTime)
            val base = if (cur <= 45) 3000 else 5000
            events += MatchEvent(
                EventType.ONGOING, m.matchTime, base + cur + 500, true,
                "正在进行 ${m.matchTime}"
            )
        }

        // 裁判
        var referee = ""
        val officials = root.optJSONArray("Officials")
        if (officials != null) {
            for (i in 0 until officials.length()) {
                val o = officials.getJSONObject(i)
                if (o.optInt("OfficialType") == 1) {
                    referee = localized(o.optJSONArray("Name"))
                    break
                }
            }
        }

        val stadiumObj = root.optJSONObject("Stadium")
        val stadium = buildList {
            localized(stadiumObj?.optJSONArray("Name")).takeIf { it.isNotEmpty() }?.let { add(it) }
            localized(stadiumObj?.optJSONArray("CityName")).takeIf { it.isNotEmpty() }?.let { add(it) }
        }.joinToString(" · ")

        // 控球率（多数比赛无此数据）
        var possession: Int? = null
        root.optJSONObject("BallPossession")?.optJSONObject("Overall")?.let {
            val hp = it.optDouble("Home", -1.0)
            if (hp >= 0) possession = hp.toInt()
        }

        return MatchDetail(
            events = events.sortedBy { it.sortKey },
            homePlayers = homePlayers,
            awayPlayers = awayPlayers,
            homeTactics = home?.optString("Tactics")?.takeIf { it.isNotEmpty() && it != "null" },
            awayTactics = away?.optString("Tactics")?.takeIf { it.isNotEmpty() && it != "null" },
            stadium = stadium,
            referee = referee,
            attendance = root.optInt("Attendance", 0),
            homePossession = possession,
        )
    }
}
