package com.fifaglass.app.data

/** 国家队排名条目（来自 FIFA 内部接口） */
data class Team(
    val idTeam: String,
    val rank: Int,
    val name: String,
    val code: String,
    val confederation: String,
    val points: Double,
    val prevRank: Int,
    val rankChange: Int, // 正数 = 名次上升
) {
    val flagUrl: String
        get() = "https://api.fifa.com/api/v3/picture/flags-sq-3/$code"
}

/** 比赛条目（实时或赛程赛果） */
data class MatchInfo(
    val id: String,
    val compId: String,
    val seasonId: String,
    val stageId: String,
    val competition: String,
    val homeName: String,
    val awayName: String,
    val homeCode: String,
    val awayCode: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val status: Int,        // 日历接口: 0=已结束 1=未开始 其他=进行中; 实时接口: 0=已结束 1=进行中
    val matchTime: String,  // 如 "67'"
    val date: String,
    val homeTactics: String?,
    val awayTactics: String?,
    val fromLiveEndpoint: Boolean = false, // 是否来自实时接口（影响状态判断）
) {
    val isFinished: Boolean get() = status == 0
    val isScheduled: Boolean
        get() = if (fromLiveEndpoint) {
            // 实时接口：status 非 0 即视为进行中，不再有"未开始"
            false
        } else {
            // 日历接口：status==1 且无比分 = 未开始
            status == 1 && homeScore == null
        }
    val isLive: Boolean get() = !isFinished && !isScheduled
}

enum class EventType { GOAL, YELLOW, RED, SUB, KICKOFF, HALF_TIME, FULL_TIME, ONGOING }

/** 赛事（联赛/杯赛） */
data class Competition(
    val id: String,
    val name: String,
    val region: String,
)

/** 比赛时间轴事件（进球/牌/换人/关键节点） */
data class MatchEvent(
    val type: EventType,
    val minuteLabel: String, // "30'" / "HT" / "FT"
    val sortKey: Int,        // period*1000 + 分钟
    val isHome: Boolean,
    val title: String,
    val subtitle: String = "",
)

data class PlayerInfo(
    val id: String,
    val name: String,
    val number: Int,
    val position: Int, // 0=GK 1=DF 2=MF 3=FW
    val captain: Boolean,
    val starter: Boolean,
)

/** 单场比赛完整详情 */
data class MatchDetail(
    val events: List<MatchEvent>,
    val homePlayers: List<PlayerInfo>,
    val awayPlayers: List<PlayerInfo>,
    val homeTactics: String?,
    val awayTactics: String?,
    val stadium: String,
    val referee: String,
    val attendance: Int,
    val homePossession: Int?, // 0-100，无数据为 null
)
