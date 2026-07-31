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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fifaglass.app.data.FifaApi
import com.fifaglass.app.data.MatchInfo
import com.fifaglass.app.data.Team
import com.fifaglass.app.rating.Evaluator
import com.fifaglass.app.rating.PredictionFactories
import com.fifaglass.app.ui.Aurora
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.blueMintBrush
import com.fifaglass.app.ui.glass
import com.fifaglass.app.ui.pinkGoldBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

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
                    Text("赛事", color = GlassColors.accentBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    Modifier
                        .glass(16.dp)
                        .clickable { refreshTick++ }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text("刷新", color = GlassColors.accentMint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                        item { LiveSectionHeader("进行中 · ${playing.size} 场") }
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
private fun LiveSectionHeader(title: String) {
    val transition = rememberInfiniteTransition(label = "live-header")
    val scale by transition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "live-dot"
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFFFF453A), Color(0xFFFF9F0A))
                    )
                )
        )
        Spacer(Modifier.width(8.dp))
        Text(title, color = GlassColors.down, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
        if (m.isScheduled) {
            while (true) {
                kotlinx.coroutines.delay(1_000)
                nowTick++
            }
        }
    }

    val upset = remember(m.homeCode, m.awayCode) {
        PredictionFactories.isPotentialUpset(m.homeCode, m.awayCode)
    }

    val isLive = m.isLive
    val cardGlow = if (isLive) GlassColors.down else null

    val cardMod = if (cardGlow != null) {
        Modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(20.dp), ambientColor = cardGlow.copy(alpha = 0.30f))
    } else {
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.06f))
    }

    Box(
        cardMod
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isLive) Aurora.danger()
                else Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.95f),
                        Color.White.copy(alpha = 0.82f),
                    )
                ),
                alpha = if (isLive) 0.18f else 1f
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column {
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
                            .background(GlassColors.down.copy(alpha = 0.20f))
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
                if (isLive) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(GlassColors.down.copy(alpha = 0.22f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("LIVE", color = GlassColors.down, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamSide(m.homeName, m.homeCode, Modifier.weight(1f))
                Column(
                    Modifier.padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        m.isFinished && m.homeScore != null && m.awayScore != null -> {
                            Text(
                                "${m.homeScore} : ${m.awayScore}",
                                color = GlassColors.textPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        isLive -> {
                            Text(
                                "${m.homeScore ?: 0} : ${m.awayScore ?: 0}",
                                color = GlassColors.down,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                if (m.matchTime.isEmpty()) "—" else m.matchTime,
                                color = GlassColors.down,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        m.isScheduled && kickoff != null -> {
                            val nowTickLocal = nowTick
                            Text(
                                countdownText(kickoff),
                                color = GlassColors.accentGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        else -> {
                            Text(
                                "VS",
                                color = GlassColors.textSecondary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                TeamSide(m.awayName, m.awayCode, Modifier.weight(1f), alignEnd = true)
            }
            if (homePts != null && awayPts != null && (m.isScheduled || m.isLive)) {
                Spacer(Modifier.height(8.dp))
                val (pH, pD, pA) = Evaluator.matchProbabilities(homePts, awayPts)
                ProbabilityBarTriple(pH, pD, pA)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "${(pH * 100).toInt()}%",
                        color = GlassColors.accentMint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "平 ${(pD * 100).toInt()}%",
                        color = GlassColors.textSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        "${(pA * 100).toInt()}%",
                        color = GlassColors.accentPink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamSide(name: String, code: String, modifier: Modifier, alignEnd: Boolean = false) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            code,
            color = GlassColors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            name,
            color = GlassColors.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
