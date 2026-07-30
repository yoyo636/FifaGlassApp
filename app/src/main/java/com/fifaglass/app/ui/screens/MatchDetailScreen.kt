package com.fifaglass.app.ui.screens

import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.fifaglass.app.ui.Aurora
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MatchDetailScreen(m: MatchInfo, rankings: List<Team>?, onOpenCompanion: () -> Unit = {}, onOpenStream: () -> Unit = {}) {
    val context = LocalContext.current
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
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        ScoreHeaderCard(m)
        Spacer(Modifier.height(12.dp))

        AuroraActionButton("📺 看球伴侣 · 实时辅助工具", GlassColors.accentMint) { onOpenCompanion() }
        Spacer(Modifier.height(8.dp))
        AuroraActionButton("📹 观看直播 / 录播", GlassColors.up) { onOpenStream() }
        Spacer(Modifier.height(8.dp))

        AuroraActionButton("📤 分享比赛卡片", GlassColors.accentViolet) {
            val shareText = buildString {
                appendLine("⚽ FifaGlass 比赛卡片")
                appendLine("────────────────")
                appendLine("${m.competition}")
                appendLine("${m.homeName} ${m.homeScore ?: "-"} : ${m.awayScore ?: "-"} ${m.awayName}")
                appendLine("日期: ${m.date}")
                appendLine()
                val lp = livePrediction
                val pp = prePrediction
                if (lp != null) {
                    appendLine("实时预测:")
                    appendLine("${m.homeName} ${(lp.pHome * 100).toInt()}% | 平 ${(lp.pDraw * 100).toInt()}% | ${m.awayName} ${(lp.pAway * 100).toInt()}%")
                } else if (pp != null) {
                    appendLine("赛前预测:")
                    appendLine("${m.homeName} ${(pp.pHome * 100).toInt()}% | 平 ${(pp.pDraw * 100).toInt()}% | ${m.awayName} ${(pp.pAway * 100).toInt()}%")
                    appendLine("预测比分: ${pp.likelyScore}")
                    appendLine("信心指数: ${pp.confidence}")
                }
                appendLine()
                appendLine("⭐ FifaGlass - 智能足球预测应用")
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "分享比赛卡片").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
        Spacer(Modifier.height(12.dp))

        val pp = prePrediction
        if (m.isScheduled && pp != null) {
            PredictionCard("赛前预测（独立预测系统）", pp.pHome, pp.pDraw, pp.pAway, m.homeCode, m.awayCode, pp.likelyScore, pp.confidence, pp.xgHome, pp.xgAway, pp.overProbabilities.over25, pp.bttsProbability, pp.upsetProbability)
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
                    CircularProgressIndicator(color = GlassColors.accentMint, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("正在加载事件时间轴…", color = GlassColors.textSecondary, fontSize = 13.sp)
                }
            }
            else -> {
                val d = detail!!
                val lp = livePrediction
                if (lp != null) {
                    LivePredictionCard(lp, m.homeCode, m.awayCode)
                    Spacer(Modifier.height(12.dp))
                }
                TimelineCard(d, m)
                Spacer(Modifier.height(12.dp))
                TechInfoCard(d, m, rankings)
                Spacer(Modifier.height(12.dp))
                if (d.homePlayers.isNotEmpty() || d.awayPlayers.isNotEmpty()) {
                    LineupCard(m, d)
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun ScoreHeaderCard(m: MatchInfo) {
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(20.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black.copy(alpha = 0.12f))
            .clip(RoundedCornerShape(28.dp))
            .background(
                if (m.isLive) Brush.linearGradient(listOf(Color(0xFFFF375F), Color(0xFFFF9F0A)))
                else Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6), Color(0xFFBF5AF2)))
            )
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                m.competition,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = "https://api.fifa.com/api/v3/picture/flags-sq-3/${m.homeCode}",
                        contentDescription = m.homeName,
                        modifier = Modifier.size(48.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        m.homeName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                }
                Column(
                    Modifier.padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (m.homeScore != null && m.awayScore != null) {
                        Text(
                            "${m.homeScore} : ${m.awayScore}",
                            color = Color.White,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black
                        )
                    } else {
                        Text("VS", color = Color.White.copy(alpha = 0.7f), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        when {
                            m.isLive -> "进行中 ${m.matchTime}"
                            m.isFinished -> "已结束"
                            else -> "未开始"
                        },
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = "https://api.fifa.com/api/v3/picture/flags-sq-3/${m.awayCode}",
                        contentDescription = m.awayName,
                        modifier = Modifier.size(48.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        m.awayName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (m.date.length >= 10) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "开球日期 ${m.date.substring(0, 10)}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AuroraActionButton(text: String, accent: Color, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "btn-glow")
    val glow by transition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glow"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(14.dp), ambientColor = accent.copy(alpha = glow * 0.3f))
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.15f))
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PredictionCard(
    title: String, pHome: Double, pDraw: Double, pAway: Double,
    homeCode: String, awayCode: String, likelyScore: String, confidence: Int,
    xgHome: Double, xgAway: Double, over25: Double, btts: Double, upset: Double
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(title, color = GlassColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        ProbabilityBarTriple(pHome, pDraw, pAway)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("${homeCode} ${(pHome * 100).toInt()}%", color = GlassColors.accentMint, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("平 ${(pDraw * 100).toInt()}%", color = GlassColors.textSecondary, fontSize = 12.sp)
            Text("${(pAway * 100).toInt()}% ${awayCode}", color = GlassColors.accentPink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        Spacer(Modifier.height(6.dp))
        Text("最可能比分 $likelyScore · 信心 $confidence · xG ${"%.2f".format(xgHome)}:${"%.2f".format(xgAway)}", color = GlassColors.textSecondary, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text("大2.5 ${(over25 * 100).toInt()}% · BTTS ${(btts * 100).toInt()}% · 冷门 ${(upset * 100).toInt()}%", color = GlassColors.textSecondary, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun LivePredictionCard(lp: com.fifaglass.app.rating.Prediction, homeCode: String, awayCode: String) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text("实时预测（自研引擎）", color = GlassColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        ProbabilityBarTriple(lp.pHome, lp.pDraw, lp.pAway)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("${homeCode} ${(lp.pHome * 100).toInt()}%", color = GlassColors.accentMint, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("平 ${(lp.pDraw * 100).toInt()}%", color = GlassColors.textSecondary, fontSize = 12.sp)
            Text("${(lp.pAway * 100).toInt()}% ${awayCode}", color = GlassColors.accentPink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        Spacer(Modifier.height(6.dp))
        Text("最可能比分 ${lp.likelyScore} · 信心 ${lp.confidence}", color = GlassColors.textSecondary, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun TimelineCard(d: MatchDetail, m: MatchInfo) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text("事件时间轴", color = GlassColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("进球 · 红黄牌 · 换人 · 关键节点", color = GlassColors.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        if (d.events.isEmpty()) {
            Text("本场暂无事件数据", color = GlassColors.textSecondary, fontSize = 13.sp)
        } else {
            Column {
                d.events.forEach { e -> AnimatedEventRow(e, m) }
            }
        }
    }
}

@Composable
private fun AnimatedEventRow(e: MatchEvent, m: MatchInfo) {
    val transition = rememberInfiniteTransition(label = "event-${e.sortKey}")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "event-glow"
    )
    val bgColor = when (e.type) {
        EventType.GOAL -> GlassColors.accentMint.copy(alpha = glowAlpha * 0.25f)
        EventType.YELLOW -> GlassColors.accentGold.copy(alpha = glowAlpha * 0.22f)
        EventType.RED -> GlassColors.down.copy(alpha = glowAlpha * 0.22f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(46.dp).clip(RoundedCornerShape(10.dp)).background(bgColor).padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(e.minuteLabel, color = GlassColors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            Text(e.title, color = GlassColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sideTag = when (e.type) {
                EventType.GOAL, EventType.YELLOW, EventType.RED, EventType.SUB -> if (e.isHome) m.homeCode else m.awayCode
                else -> ""
            }
            Text(listOf(e.subtitle, sideTag).filter { it.isNotEmpty() }.joinToString(" · "), color = GlassColors.textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TechInfoCard(d: MatchDetail, m: MatchInfo, rankings: List<Team>?) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text("技术信息", color = GlassColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
            InfoRow("赛前胜率", "${(pH * 100).toInt()}% / 平 ${(pD * 100).toInt()}% / ${(pA * 100).toInt()}%")
        }
    }
}

@Composable
private fun LineupCard(m: MatchInfo, d: MatchDetail) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text("出场阵容", color = GlassColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { LineupBlock(m.homeCode, d.homePlayers) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { LineupBlock(m.awayCode, d.awayPlayers) }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = GlassColors.textSecondary, fontSize = 13.sp, modifier = Modifier.width(72.dp))
        Text(value, color = GlassColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                    Text("${p.number}", color = GlassColors.textSecondary, fontSize = 12.sp, modifier = Modifier.width(22.dp))
                    Text(p.name + if (p.captain) " (C)" else "", color = GlassColors.textPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
