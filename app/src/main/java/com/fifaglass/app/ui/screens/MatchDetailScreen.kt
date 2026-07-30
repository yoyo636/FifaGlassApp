package com.fifaglass.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fifaglass.app.data.EventType
import com.fifaglass.app.data.FifaApi
import com.fifaglass.app.data.MatchDetail
import com.fifaglass.app.data.MatchEvent
import com.fifaglass.app.data.MatchInfo
import com.fifaglass.app.data.PlayerInfo
import com.fifaglass.app.rating.Evaluator
import com.fifaglass.app.data.Team
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 比赛详情：比分、事件时间轴（进球/红黄牌/换人/关键节点）、技术统计、阵容、内嵌预测 */
@Composable
fun MatchDetailScreen(m: MatchInfo, rankings: List<Team>?, onOpenCompanion: () -> Unit = {}) {
    var detail by remember { mutableStateOf<MatchDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var livePrediction by remember { mutableStateOf<com.fifaglass.app.rating.Prediction?>(null) }
    var prePrediction by remember { mutableStateOf<com.fifaglass.app.rating.PredictionOutput?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(m.id) {
        try {
            detail = withContext(Dispatchers.IO) { FifaApi.fetchMatchDetail(m) }
        } catch (e: Exception) {
            error = e.message ?: "详情加载失败"
        }
    }

    // 未开始比赛：运行完整赛前预测系统（万行系统，直接合成在比赛详情里）
    LaunchedEffect(m.id, m.isScheduled, rankings) {
        if (!m.isScheduled) { prePrediction = null; return@LaunchedEffect }
        val home = rankings?.firstOrNull { it.code == m.homeCode }
        val away = rankings?.firstOrNull { it.code == m.awayCode }
        if (home != null && away != null) {
            prePrediction = runCatching {
                withContext(Dispatchers.IO) {
                    com.fifaglass.app.rating.PredictionEngine.predictPreMatchFull(home, away, true)
                }
            }.getOrNull()
        }
    }

    // 进行中比赛：自研引擎实时预测（每秒左右刷新；用 tick 触发重算）
    LaunchedEffect(m.id, m.isLive, m.matchTime, m.homeScore, m.awayScore, refreshTick, rankings) {
        if (!m.isLive) { livePrediction = null; return@LaunchedEffect }
        val home = rankings?.firstOrNull { it.code == m.homeCode }
        val away = rankings?.firstOrNull { it.code == m.awayCode }
        if (home != null && away != null && m.homeScore != null && m.awayScore != null) {
            val min = m.matchTime.filter { it.isDigit() || it == '+' }
                .split('+').filter { it.isNotEmpty() }
                .sumOf { it.toIntOrNull() ?: 0 }
            livePrediction = withContext(Dispatchers.IO) {
                com.fifaglass.app.rating.PredictionEngine.predictLive(
                    home, away, m.homeScore, m.awayScore, min
                )
            }
        }
    }

    // 比赛进行中时每分钟自动重新拉详情 + 重新预测
    LaunchedEffect(m.id, m.isLive) {
        if (!m.isLive) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(60_000)
            refreshTick++
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // 比分头卡
        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                m.competition,
                color = GlassColors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = "https://api.fifa.com/api/v3/picture/flags-sq-3/${m.homeCode}",
                        contentDescription = m.homeName,
                        modifier = Modifier.size(42.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        m.homeName,
                        color = GlassColors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                Column(
                    Modifier.padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (m.homeScore != null && m.awayScore != null) {
                        Text(
                            "${m.homeScore} : ${m.awayScore}",
                            color = GlassColors.textPrimary,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black
                        )
                    } else {
                        Text(
                            "VS",
                            color = GlassColors.textSecondary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        when {
                            m.isLive -> "进行中 ${m.matchTime}"
                            m.isFinished -> "已结束"
                            else -> "未开始"
                        },
                        color = when {
                            m.isLive -> GlassColors.up
                            m.isFinished -> GlassColors.textSecondary
                            else -> GlassColors.accentGold
                        },
                        fontSize = 12.sp
                    )
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = "https://api.fifa.com/api/v3/picture/flags-sq-3/${m.awayCode}",
                        contentDescription = m.awayName,
                        modifier = Modifier.size(42.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        m.awayName,
                        color = GlassColors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            if (m.date.length >= 10) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text(
                        "开球日期 ${m.date.substring(0, 10)}",
                        color = GlassColors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(GlassColors.accentMint.copy(alpha = 0.15f))
                .clickable { onOpenCompanion() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("📺 看球伴侣 · 实时辅助工具", color = GlassColors.accentMint, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))

        // 赛前预测（未开始）：完整预测系统输出，直接合成在比赛详情里
        val pp = prePrediction
        if (m.isScheduled && pp != null) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    "赛前预测（独立预测系统）",
                    color = GlassColors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                ProbabilityBarTriple(pp.pHome, pp.pDraw, pp.pAway)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "${m.homeCode} ${(pp.pHome * 100).toInt()}%",
                        color = GlassColors.accentMint,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text("平 ${(pp.pDraw * 100).toInt()}%", color = GlassColors.textSecondary, fontSize = 12.sp)
                    Text(
                        "${(pp.pAway * 100).toInt()}% ${m.awayCode}",
                        color = GlassColors.accentPink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "最可能比分 ${pp.likelyScore} · 信心 ${pp.confidence} · xG ${"%.2f".format(pp.xgHome)}:${"%.2f".format(pp.xgAway)}",
                    color = GlassColors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "大2.5 ${(pp.overProbabilities.over25 * 100).toInt()}% · BTTS ${(pp.bttsProbability * 100).toInt()}% · 冷门 ${(pp.upsetProbability * 100).toInt()}%",
                    color = GlassColors.textSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        val err = error
        when {
            err != null -> GlassCard(Modifier.fillMaxWidth()) {
                Text("事件数据不可用", color = GlassColors.accentGold, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(err, color = GlassColors.textSecondary, fontSize = 12.sp)
            }
            detail == null -> GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = GlassColors.accentMint,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("正在加载事件时间轴…", color = GlassColors.textSecondary, fontSize = 13.sp)
                }
            }
            else -> {
                val d = detail!!

                // 实时预测（进行中）
                val lp = livePrediction
                if (lp != null) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(
                            "实时预测（自研引擎）",
                            color = GlassColors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))
                        ProbabilityBarTriple(lp.pHome, lp.pDraw, lp.pAway)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                "${m.homeCode} ${(lp.pHome * 100).toInt()}%",
                                color = GlassColors.accentMint,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text("平 ${(lp.pDraw * 100).toInt()}%", color = GlassColors.textSecondary, fontSize = 12.sp)
                            Text(
                                "${(lp.pAway * 100).toInt()}% ${m.awayCode}",
                                color = GlassColors.accentPink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "最可能比分 ${lp.likelyScore} · 信心 ${lp.confidence}",
                            color = GlassColors.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // 事件时间轴
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        "事件时间轴",
                        color = GlassColors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "进球 · 红黄牌 · 换人 · 关键节点",
                        color = GlassColors.textSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    if (d.events.isEmpty()) {
                        Text("本场暂无事件数据", color = GlassColors.textSecondary, fontSize = 13.sp)
                    } else {
                        Column {
                            d.events.forEach { e -> EventRow(e, m) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // 技术信息
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        "技术信息",
                        color = GlassColors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    if (d.homeTactics != null || d.awayTactics != null) {
                        InfoRow("阵型", "${d.homeTactics ?: "?"}  vs  ${d.awayTactics ?: "?"}")
                    }
                    if (d.homePossession != null) {
                        InfoRow("控球率", "${d.homePossession}%  :  ${100 - d.homePossession!!}%")
                    }
                    if (d.stadium.isNotEmpty()) InfoRow("球场", d.stadium)
                    if (d.referee.isNotEmpty()) InfoRow("主裁判", d.referee)
                    if (d.attendance > 0) InfoRow("观众", "%,d 人".format(d.attendance))

                    val homePts = rankings?.firstOrNull { it.code == m.homeCode }?.points
                    val awayPts = rankings?.firstOrNull { it.code == m.awayCode }?.points
                    if (homePts != null && awayPts != null) {
                        val (pH, pD, pA) = Evaluator.matchProbabilities(homePts, awayPts)
                        InfoRow(
                            "赛前胜率",
                            "${(pH * 100).toInt()}% / 平 ${(pD * 100).toInt()}% / ${(pA * 100).toInt()}%"
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                // 阵容
                if (d.homePlayers.isNotEmpty() || d.awayPlayers.isNotEmpty()) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(
                            "出场阵容",
                            color = GlassColors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                LineupBlock(m.homeCode, d.homePlayers)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                LineupBlock(m.awayCode, d.awayPlayers)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun EventRow(e: MatchEvent, m: MatchInfo) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 分钟徽章
        Box(
            Modifier
                .width(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    when (e.type) {
                        EventType.GOAL -> GlassColors.accentMint.copy(alpha = 0.25f)
                        EventType.YELLOW -> GlassColors.accentGold.copy(alpha = 0.22f)
                        EventType.RED -> GlassColors.down.copy(alpha = 0.22f)
                        EventType.ONGOING -> GlassColors.up.copy(alpha = 0.22f)
                        else -> Color.White.copy(alpha = 0.08f)
                    }
                )
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                e.minuteLabel,
                color = GlassColors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            when (e.type) {
                EventType.GOAL -> "⚽"
                EventType.YELLOW -> "🟨"
                EventType.RED -> "🟥"
                EventType.SUB -> "🔁"
                EventType.KICKOFF -> "🟢"
                EventType.HALF_TIME -> "⏸"
                EventType.FULL_TIME -> "🏁"
                EventType.ONGOING -> "▶️"
            },
            fontSize = 15.sp
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.title,
                color = GlassColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sideTag = when (e.type) {
                EventType.GOAL, EventType.YELLOW, EventType.RED, EventType.SUB ->
                    if (e.isHome) m.homeCode else m.awayCode
                else -> ""
            }
            Text(
                listOf(e.subtitle, sideTag).filter { it.isNotEmpty() }.joinToString(" · "),
                color = GlassColors.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            label,
            color = GlassColors.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.width(72.dp)
        )
        Text(
            value,
            color = GlassColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LineupBlock(title: String, players: List<PlayerInfo>) {
    Text(title, color = GlassColors.accentMint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    val starters = players.filter { it.starter }
    val list = if (starters.isNotEmpty()) starters else players
    val groups = listOf("GK", "DF", "MF", "FW")
    groups.forEachIndexed { pos, label ->
        val inPos = list.filter { it.position == pos }
        if (inPos.isNotEmpty()) {
            Text(label, color = GlassColors.textSecondary, fontSize = 11.sp)
            inPos.sortedBy { it.number }.forEach { p ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(
                        "${p.number}",
                        color = GlassColors.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.width(22.dp)
                    )
                    Text(
                        p.name + if (p.captain) " (C)" else "",
                        color = GlassColors.textPrimary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
