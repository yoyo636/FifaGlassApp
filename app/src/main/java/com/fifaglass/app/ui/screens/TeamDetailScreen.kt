package com.fifaglass.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fifaglass.app.data.FifaApi
import com.fifaglass.app.data.MatchInfo
import com.fifaglass.app.data.Team
import com.fifaglass.app.rating.Evaluator
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.RadarChart
import com.fifaglass.app.ui.blueMintBrush
import com.fifaglass.app.ui.HBarRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 球队评测详情：综合评分 + 评级 + 六维雷达 + 数据明细 + 近期比赛 */
@Composable
fun TeamDetailScreen(team: Team, all: List<Team>, onMatchClick: (MatchInfo) -> Unit) {
    val result = remember(team, all) { Evaluator.evaluate(team, all) }
    var teamMatches by remember { mutableStateOf<List<MatchInfo>?>(null) }

    LaunchedEffect(team.idTeam) {
        try {
            teamMatches = withContext(Dispatchers.IO) { FifaApi.fetchTeamMatches(team.idTeam) }
        } catch (_: Exception) {
            teamMatches = emptyList()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = team.flagUrl,
                    contentDescription = team.name,
                    modifier = Modifier.size(56.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        team.name,
                        color = GlassColors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${team.code} · ${team.confederation}",
                        color = GlassColors.textSecondary,
                        fontSize = 13.sp
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        result.grade,
                        color = when (result.grade) {
                            "S" -> GlassColors.accentGold
                            "A" -> GlassColors.accentMint
                            "B" -> GlassColors.accentBlue
                            else -> GlassColors.textSecondary
                        },
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "%.1f".format(result.overall),
                        color = GlassColors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("综合评分", color = GlassColors.textSecondary, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                "六维能力评测",
                color = GlassColors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "基于 FIFA 积分与排名推导",
                color = GlassColors.textSecondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            RadarChart(
                dims = result.dims,
                modifier = Modifier.fillMaxWidth().height(260.dp)
            )
        }
        Spacer(Modifier.height(12.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                "维度明细",
                color = GlassColors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                result.dims.forEach { dim ->
                    HBarRow(
                        label = dim.label,
                        value = dim.value.toDouble(),
                        maxValue = 100.0,
                        valueText = "%.0f".format(dim.value),
                        barBrush = blueMintBrush
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                "官方数据",
                color = GlassColors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            DataRow("世界排名", "#${team.rank}")
            DataRow("FIFA 积分", "%.2f".format(team.points))
            DataRow("上期排名", if (team.prevRank > 0) "#${team.prevRank}" else "—")
            DataRow(
                "名次变化",
                when {
                    team.rankChange > 0 -> "上升 ${team.rankChange} 位"
                    team.rankChange < 0 -> "下降 ${-team.rankChange} 位"
                    else -> "持平"
                }
            )
            DataRow("所属足联", team.confederation)
        }
        Spacer(Modifier.height(12.dp))

        // 近期比赛
        val matches = teamMatches
        if (!matches.isNullOrEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    "近期比赛",
                    color = GlassColors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    matches.take(10).forEach { m ->
                        TeamMatchRow(m, team) { onMatchClick(m) }
                    }
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun TeamMatchRow(m: MatchInfo, team: Team, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (m.date.length >= 10) m.date.substring(5, 10) else "",
            color = GlassColors.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.width(42.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                "${m.homeName} vs ${m.awayName}",
                color = GlassColors.textPrimary,
                fontSize = 13.sp,
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
        Spacer(Modifier.width(8.dp))
        if (m.homeScore != null && m.awayScore != null) {
            // 相对本队的胜平负着色
            val isHome = m.homeCode == team.code
            val gf = if (isHome) m.homeScore else m.awayScore
            val ga = if (isHome) m.awayScore else m.homeScore
            val color = when {
                gf > ga -> GlassColors.up
                gf < ga -> GlassColors.down
                else -> GlassColors.textSecondary
            }
            Text(
                "${m.homeScore}:${m.awayScore}",
                color = color,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text("未赛", color = GlassColors.accentGold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DataRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = GlassColors.textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = GlassColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
