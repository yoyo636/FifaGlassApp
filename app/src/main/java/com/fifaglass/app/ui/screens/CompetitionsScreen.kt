package com.fifaglass.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fifaglass.app.data.Competition
import com.fifaglass.app.data.FifaApi
import com.fifaglass.app.data.MatchInfo
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CompetitionsScreen(
    onCompetitionClick: (Competition) -> Unit,
) {
    var competitions by remember { mutableStateOf<List<Competition>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            competitions = withContext(Dispatchers.IO) { FifaApi.fetchCompetitions() }
        } catch (e: Exception) {
            error = e.message ?: "网络请求失败"
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("赛事中心", color = GlassColors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Text("全球联赛与杯赛 · 点击查看近期赛程", color = GlassColors.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))

        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().glass(20.dp),
            placeholder = { Text("搜索赛事（如 World Cup / Premier League）", color = GlassColors.textSecondary) },
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
            competitions == null -> LoadingBox()
            else -> {
                val filtered = competitions!!.filter {
                    query.isBlank() || it.name.contains(query, true)
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(filtered, key = { it.id }) { c ->
                        GlassCard(
                            Modifier.fillMaxWidth().clickable { onCompetitionClick(c) },
                            corner = 18.dp
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        c.name,
                                        color = GlassColors.textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (c.region.isNotEmpty()) {
                                        Text(c.region, color = GlassColors.textSecondary, fontSize = 12.sp)
                                    }
                                }
                                Text("›", color = GlassColors.textSecondary, fontSize = 20.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompetitionMatchesScreen(
    competition: Competition,
    onMatchClick: (MatchInfo) -> Unit,
) {
    var matches by remember { mutableStateOf<List<MatchInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(competition.id) {
        try {
            matches = withContext(Dispatchers.IO) {
                FifaApi.fetchCompetitionMatches(competition.id)
            }
        } catch (e: Exception) {
            error = e.message ?: "网络请求失败"
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(
            competition.name,
            color = GlassColors.textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text("近三周赛程赛果 · 点击比赛看详情", color = GlassColors.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))

        val err = error
        when {
            err != null -> ErrorBox(err)
            matches == null -> LoadingBox()
            matches!!.isEmpty() -> GlassCard(Modifier.fillMaxWidth()) {
                Text("该赛事近三周没有比赛", color = GlassColors.textSecondary, fontSize = 14.sp)
            }
            else -> {
                val finished = matches!!.filter { it.homeScore != null && it.awayScore != null }
                if (finished.isNotEmpty()) {
                    GroupStandingsCard(finished)
                    Spacer(Modifier.height(12.dp))
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(matches!!, key = { it.id }) { m ->
                        GlassCard(
                            Modifier.fillMaxWidth().clickable { onMatchClick(m) },
                            corner = 18.dp
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (m.date.length >= 10) m.date.substring(5, 10) else "",
                                    color = GlassColors.textSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.width(44.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${m.homeName} vs ${m.awayName}",
                                        color = GlassColors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        m.competition,
                                        color = GlassColors.textSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (m.homeScore != null && m.awayScore != null) {
                                    Text(
                                        "${m.homeScore}:${m.awayScore}",
                                        color = GlassColors.textPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Text("未赛", color = GlassColors.accentGold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class StandingData(
    val name: String,
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val gf: Int,
    val ga: Int,
) {
    val gd: Int get() = gf - ga
    val pts: Int get() = wins * 3 + draws
}

@Composable
private fun GroupStandingsCard(matches: List<MatchInfo>) {
    val standings = remember(matches) {
        val map = HashMap<String, StandingData>()
        for (m in matches) {
            if (m.homeScore == null || m.awayScore == null) continue
            val home = map.getOrPut(m.homeName) { StandingData(m.homeName, 0, 0, 0, 0, 0, 0) }
            val away = map.getOrPut(m.awayName) { StandingData(m.awayName, 0, 0, 0, 0, 0, 0) }
            map[m.homeName] = home.copy(
                played = home.played + 1,
                wins = home.wins + (if (m.homeScore > m.awayScore) 1 else 0),
                draws = home.draws + (if (m.homeScore == m.awayScore) 1 else 0),
                losses = home.losses + (if (m.homeScore < m.awayScore) 1 else 0),
                gf = home.gf + m.homeScore,
                ga = home.ga + m.awayScore,
            )
            map[m.awayName] = away.copy(
                played = away.played + 1,
                wins = away.wins + (if (m.awayScore > m.homeScore) 1 else 0),
                draws = away.draws + (if (m.homeScore == m.awayScore) 1 else 0),
                losses = away.losses + (if (m.awayScore < m.homeScore) 1 else 0),
                gf = away.gf + m.awayScore,
                ga = away.ga + m.homeScore,
            )
        }
        map.values.sortedWith(
            compareByDescending<StandingData> { it.pts }
                .thenByDescending { it.gd }
                .thenByDescending { it.gf }
        )
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Text("积分榜", color = GlassColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("基于近期比赛结果自动计算", color = GlassColors.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text("球队", color = GlassColors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("赛", color = GlassColors.textSecondary, fontSize = 12.sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
            Text("胜", color = GlassColors.textSecondary, fontSize = 12.sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
            Text("平", color = GlassColors.textSecondary, fontSize = 12.sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
            Text("负", color = GlassColors.textSecondary, fontSize = 12.sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
            Text("净", color = GlassColors.textSecondary, fontSize = 12.sp, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
            Text("分", color = GlassColors.textSecondary, fontSize = 12.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
        }

        val top = standings.take(10)
        for (idx in top.indices) {
            val s = top[idx]
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${idx + 1}", color = GlassColors.textSecondary, fontSize = 12.sp, modifier = Modifier.width(20.dp))
                Text(
                    s.name,
                    color = if (idx < 3) GlassColors.accentMint else GlassColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = if (idx < 3) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("${s.played}", color = GlassColors.textPrimary, fontSize = 12.sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                Text("${s.wins}", color = GlassColors.up, fontSize = 12.sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                Text("${s.draws}", color = GlassColors.textSecondary, fontSize = 12.sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                Text("${s.losses}", color = GlassColors.down, fontSize = 12.sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                Text("${if (s.gd > 0) "+" else ""}${s.gd}", color = GlassColors.textPrimary, fontSize = 12.sp, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
                Text("${s.pts}", color = GlassColors.accentGold, fontSize = 14.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            }
        }
    }
}
