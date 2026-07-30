package com.fifaglass.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startActivity
import com.fifaglass.app.data.FifaApi
import com.fifaglass.app.data.MatchInfo
import com.fifaglass.app.data.Team
import com.fifaglass.app.rating.Evaluator
import com.fifaglass.app.rating.PredictionFactories
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.blueMintBrush
import com.fifaglass.app.ui.glass
import com.fifaglass.app.ui.pinkGoldBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import androidx.compose.ui.graphics.Color as Color1

/** 实时比赛页：当前比赛 + 近期赛果，附实时评分与胜率；30 秒自动刷新；v1.3 新增倒计时/冷门/分享/性别切换 */
@Composable
fun MatchesScreen(
    rankings: List<Team>?,
    gender: Int,
    onGenderChange: (Int) -> Unit,
    onMatchClick: (MatchInfo) -> Unit,
    onOpenCompetitions: () -> Unit,
) {
    var live by remember { mutableStateOf<List<MatchInfo>?>(null) }
    var recent by remember { mutableStateOf<List<MatchInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var lastRefresh by remember { mutableStateOf("") }

    LaunchedEffect(refreshTick, gender) {
        error = null
        try {
            val l = withContext(Dispatchers.IO) { FifaApi.fetchLiveMatches(gender) }
            val r = withContext(Dispatchers.IO) { FifaApi.fetchRecentMatches(gender) }
            live = l
            recent = r
            lastRefresh = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        } catch (e: Exception) {
            error = e.message ?: "网络请求失败"
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            refreshTick++
        }
    }

    val pointsByCode = remember(rankings) {
        rankings?.associate { it.code to it.points } ?: emptyMap()
    }

    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "实时比赛",
                    color = GlassColors.textPrimary,
                    fontSize = 28.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (lastRefresh.isEmpty()) "比分 · 胜率预测 · 实时表现评分" 
                    else "每 30 秒自动刷新 · 上次 $lastRefresh",
                    color = GlassColors.textSecondary,
                    fontSize = 13.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GenderToggle(gender, onGenderChange)
                Box(
                    Modifier
                        .glass(16.dp)
                        .clickable { onOpenCompetitions() }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        "赛事",
                        color = GlassColors.accentBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(
                    Modifier
                        .glass(16.dp)
                        .clickable { refreshTick++ }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        "刷新",
                        color = GlassColors.accentMint,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().glass(20.dp),
            placeholder = { Text("搜索比赛（球队名 / 赛事名）", color = GlassColors.textSecondary) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = GlassColors.textPrimary,
                unfocusedTextColor = GlassColors.textPrimary,
                cursorColor = GlassColors.accentMint,
            )
        )
        Spacer(Modifier.height(10.dp))

        val err = error
        when {
            err != null -> ErrorBox(err)
            live == null || recent == null -> LoadingBox()
            else -> {
                val playing = live!!.filter { it.isLive && matchMatch(it, query) }
                val upcoming = live!!.filter { it.isScheduled && matchMatch(it, query) }
                val finished = recent!!.filter { it.isFinished && it.homeScore != null && matchMatch(it, query) }
                    .sortedByDescending { it.date }
                    .take(40)

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    if (playing.isNotEmpty()) {
                        item { SectionHeader("进行中 · ${playing.size} 场") }
                        items(playing, key = { "live_" + it.id }) { m ->
                            MatchCard(m, pointsByCode) { onMatchClick(m) }
                        }
                    }
                    if (upcoming.isNotEmpty()) {
                        item { SectionHeader("即将开始 · ${upcoming.size} 场") }
                        items(upcoming.take(15), key = { "up_" + it.id }) { m ->
                            MatchCard(m, pointsByCode) { onMatchClick(m) }
                        }
                    }
                    if (finished.isNotEmpty()) {
                        item { SectionHeader("近期赛果") }
                        items(finished, key = { "fin_" + it.id }) { m ->
                            MatchCard(m, pointsByCode) { onMatchClick(m) }
                        }
                    }
                    if (playing.isEmpty() && upcoming.isEmpty() && finished.isEmpty()) {
                        item {
                            GlassCard(Modifier.fillMaxWidth()) {
                                Text(
                                    "当前窗口没有比赛数据",
                                    color = GlassColors.textSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenderToggle(gender: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier
            .glass(16.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val menSelected = gender == 1
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (menSelected) GlassColors.accentBlue.copy(alpha = 0.3f) else Color.Transparent)
                .clickable { onChange(1) }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                "男",
                color = if (menSelected) GlassColors.accentBlue else GlassColors.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (!menSelected) GlassColors.accentPink.copy(alpha = 0.3f) else Color.Transparent)
                .clickable { onChange(2) }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                "女",
                color = if (!menSelected) GlassColors.accentPink else GlassColors.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = GlassColors.accentMint,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp)
    )
}

private fun matchMatch(m: MatchInfo, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    return m.homeName.lowercase().contains(q) ||
        m.awayName.lowercase().contains(q) ||
        m.competition.lowercase().contains(q) ||
        m.homeCode.lowercase().contains(q) ||
        m.awayCode.lowercase().contains(q)
}

private fun parseKickoff(date: String): Instant? {
    return try {
        Instant.parse(date)
    } catch (_: Exception) {
        null
    }
}

private fun countdownText(kickoff: Instant): String {
    val now = Instant.now()
    val d = Duration.between(now, kickoff)
    if (d.isNegative) return "进行中 / 已开始"
    val days = d.toDays()
    val hours = d.toHours() % 24
    val minutes = d.toMinutes() % 60
    val seconds = d.seconds % 60
    return if (days > 0) {
        String.format("%d天 %02d:%02d:%02d", days, hours, minutes, seconds)
    } else {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}

@Composable
private fun MatchCard(m: MatchInfo, pointsByCode: Map<String, Double>, onClick: () -> Unit) {
    val homePts = pointsByCode[m.homeCode]
    val awayPts = pointsByCode[m.awayCode]
    val context = LocalContext.current
    val kickoff = remember(m.date) { parseKickoff(m.date) }
    var nowTick by remember { mutableStateOf(0) }

    LaunchedEffect(m.date) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            nowTick++
        }
    }

    val upset = remember(m.homeCode, m.awayCode) {
        PredictionFactories.isPotentialUpset(m.homeCode, m.awayCode)
    }

    GlassCard(Modifier.fillMaxWidth().clickable { onClick() }, corner = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                m.competition,
                color = GlassColors.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (upset) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(GlassColors.down.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "冷门预警",
                        color = GlassColors.down,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(GlassColors.accentMint.copy(alpha = 0.15f))
                    .clickable {
                        val prediction = PredictionFactories.predictByCode(m.homeCode, m.awayCode)
                        val shareText = if (prediction != null) {
                            PredictionFactories.generateShareText(prediction, m.homeName, m.awayName)
                        } else {
                            "${m.homeName} vs ${m.awayName}\nFifaGlass AI 预测"
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        startActivity(context, Intent.createChooser(intent, "分享预测"), null)
                    }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    "分享",
                    color = GlassColors.accentMint,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    m.homeName,
                    color = GlassColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    m.awayName,
                    color = GlassColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (m.homeScore != null && m.awayScore != null) {
                    Text(
                        "${m.homeScore} : ${m.awayScore}",
                        color = GlassColors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    when {
                        m.isLive -> if (m.matchTime.isNotEmpty()) "进行中 ${m.matchTime}" else "进行中"
                        m.isFinished -> "已结束"
                        kickoff != null -> "开球倒计时 ${countdownText(kickoff)}"
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
        }

        if (homePts != null && awayPts != null) {
            Spacer(Modifier.height(10.dp))
            val (pHome, pDraw, pAway) = Evaluator.matchProbabilities(homePts, awayPts)
            ProbabilityBar(pHome.toFloat(), pDraw.toFloat(), pAway.toFloat())
            Spacer(Modifier.height(6.dp))
            Row {
                Text(
                    "${m.homeCode} ${(pHome * 100).toInt()}%",
                    color = GlassColors.accentMint,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "平 ${(pDraw * 100).toInt()}%",
                    color = GlassColors.textSecondary,
                    fontSize = 12.sp
                )
                Text(
                    "${(pAway * 100).toInt()}% ${m.awayCode}",
                    color = GlassColors.accentPink,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }

            if (m.homeScore != null && m.awayScore != null) {
                Spacer(Modifier.height(8.dp))
                val homePerf = Evaluator.livePerformance(homePts, awayPts, m.homeScore, m.awayScore)
                val awayPerf = Evaluator.livePerformance(awayPts, homePts, m.awayScore, m.homeScore)
                Row {
                    PerformanceBadge(
                        label = m.homeCode,
                        score = homePerf,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    PerformanceBadge(
                        label = m.awayCode,
                        score = awayPerf,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProbabilityBar(pHome: Float, pDraw: Float, pAway: Float) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f))
    ) {
        if (pHome > 0f) {
            Box(
                Modifier
                    .weight(pHome)
                    .fillMaxSize()
                    .background(blueMintBrush)
            )
        }
        if (pDraw > 0f) {
            Box(
                Modifier
                    .weight(pDraw)
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f))
            )
        }
        if (pAway > 0f) {
            Box(
                Modifier
                    .weight(pAway)
                    .fillMaxSize()
                    .background(pinkGoldBrush)
            )
        }
    }
}

@Composable
private fun PerformanceBadge(label: String, score: Float, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.07f))
            .padding(10.dp)
    ) {
        Text(
            "$label 表现分",
            color = GlassColors.textSecondary,
            fontSize = 11.sp
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "%.0f".format(score),
                color = when {
                    score >= 65 -> GlassColors.up
                    score >= 50 -> GlassColors.accentGold
                    else -> GlassColors.down
                },
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.width(6.dp))
            Text(
                Evaluator.performanceLabel(score),
                color = GlassColors.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
    }
}
