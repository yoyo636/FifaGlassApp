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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fifaglass.app.data.Competition
import com.fifaglass.app.data.FifaApi
import com.fifaglass.app.data.MatchInfo
import com.fifaglass.app.data.Team
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.LocalGlassTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    teams: List<Team>?,
    gender: Int,
    onGenderChange: (Int) -> Unit,
    error: String?,
    favorites: Set<String>,
    onTeamClick: (Team) -> Unit,
    onMatchClick: (MatchInfo) -> Unit,
    onOpenCompetitions: () -> Unit,
    onOpenRanking: () -> Unit,
    onOpenCompare: () -> Unit,
    onOpenMatches: () -> Unit,
    onOpenStats: () -> Unit,
) {
    var live by remember { mutableStateOf<List<MatchInfo>?>(null) }
    var recent by remember { mutableStateOf<List<MatchInfo>?>(null) }
    var competitions by remember { mutableStateOf<List<Competition>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val theme = LocalGlassTheme.current

    LaunchedEffect(gender) {
        loadError = null
        try {
            val l = withContext(Dispatchers.IO) { FifaApi.fetchLiveMatches(gender) }
            val r = withContext(Dispatchers.IO) { FifaApi.fetchRecentMatches(gender) }
            val c = withContext(Dispatchers.IO) { FifaApi.fetchCompetitions() }
            live = l
            recent = r
            competitions = c
        } catch (e: Exception) {
            loadError = e.message ?: "网络请求失败"
        }
    }

    val today = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("MM月dd日 EEEE")) }
    val currentError = error ?: loadError

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "FifaGlass",
                    color = GlassColors.textPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(today, color = GlassColors.textSecondary, fontSize = 13.sp)
            }
            GenderToggleHome(gender, onGenderChange)
        }
        Spacer(Modifier.height(20.dp))

        val err = currentError
        when {
            err != null -> ErrorBox(err)
            teams == null -> LoadingBox()
            else -> {
                QuickActions(
                    onOpenRanking = onOpenRanking,
                    onOpenCompare = onOpenCompare,
                    onOpenMatches = onOpenMatches,
                    onOpenStats = onOpenStats
                )
                Spacer(Modifier.height(20.dp))

                val favTeams = teams.filter { it.code in favorites }
                if (favTeams.isNotEmpty()) {
                    FavoritesRow(favTeams, onTeamClick)
                    Spacer(Modifier.height(20.dp))
                }

                SpotlightMatches(live, recent, onMatchClick)
                Spacer(Modifier.height(20.dp))

                if (teams.size >= 3) {
                    Top3Card(teams.take(3), onTeamClick)
                    Spacer(Modifier.height(20.dp))
                }

                if (!competitions.isNullOrEmpty()) {
                    CompetitionsRow(competitions!!, onOpenCompetitions)
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun GenderToggleHome(gender: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier
            .shadow(4.dp, RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.7f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf(1 to "男足", 2 to "女足").forEach { (g, label) ->
            val selected = gender == g
            Box(
                Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        if (selected) {
                            if (g == 1) GlassColors.accentBlue else GlassColors.accentPink
                        } else Color.Transparent
                    )
                    .clickable { onChange(g) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    label,
                    color = if (selected) Color.White else GlassColors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun QuickActions(
    onOpenRanking: () -> Unit,
    onOpenCompare: () -> Unit,
    onOpenMatches: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val items = listOf(
        Quad("排名", GlassColors.accentBlue) { onOpenRanking() },
        Quad("对比", GlassColors.accentPink) { onOpenCompare() },
        Quad("比赛", GlassColors.accentMint) { onOpenMatches() },
        Quad("图表", GlassColors.accentViolet) { onOpenStats() },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (label, color, onClick) ->
            Box(
                Modifier
                    .weight(1f)
                    .height(82.dp)
                    .shadow(6.dp, RoundedCornerShape(18.dp), clip = false)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.8f))
                    .clickable { onClick() }
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(label, color = GlassColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private data class Quad(val label: String, val color: Color, val onClick: () -> Unit)

@Composable
private fun FavoritesRow(teams: List<Team>, onTeamClick: (Team) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("我的关注", color = GlassColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${teams.size} 队", color = GlassColors.textSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(teams, key = { it.code }) { team ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onTeamClick(team) }
                        .padding(8.dp)
                ) {
                    AsyncImage(
                        model = team.flagUrl,
                        contentDescription = team.name,
                        modifier = Modifier.size(42.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(team.name, color = GlassColors.textPrimary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("#${team.rank}", color = GlassColors.accentGold, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SpotlightMatches(
    live: List<MatchInfo>?,
    recent: List<MatchInfo>?,
    onMatchClick: (MatchInfo) -> Unit,
) {
    val playing = live?.filter { it.isLive }?.take(3) ?: emptyList()
    val upcoming = live?.filter { it.isScheduled }?.take(3) ?: emptyList()
    val finished = recent?.filter { it.isFinished && it.homeScore != null }?.take(3) ?: emptyList()

    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("今日焦点", color = GlassColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${playing.size + upcoming.size + finished.size} 场", color = GlassColors.textSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        if (playing.isEmpty() && upcoming.isEmpty() && finished.isEmpty()) {
            Text("当前窗口暂无焦点比赛", color = GlassColors.textSecondary, fontSize = 13.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                playing.forEach { MatchRow(it, { onMatchClick(it) }, "进行中") }
                upcoming.forEach { MatchRow(it, { onMatchClick(it) }, "即将开始") }
                finished.forEach { MatchRow(it, { onMatchClick(it) }, "已结束") }
            }
        }
    }
}

@Composable
private fun MatchRow(m: MatchInfo, onClick: () -> Unit, badge: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.6f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val homeFlag = m.homeCode.takeIf { it.isNotEmpty() }?.let { "https://api.fifa.com/api/v3/picture/flags-sq-3/$it" }
                AsyncImage(
                    model = homeFlag,
                    contentDescription = m.homeName,
                    modifier = Modifier.size(20.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(6.dp))
                Text(m.homeName, color = GlassColors.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val awayFlag = m.awayCode.takeIf { it.isNotEmpty() }?.let { "https://api.fifa.com/api/v3/picture/flags-sq-3/$it" }
                AsyncImage(
                    model = awayFlag,
                    contentDescription = m.awayName,
                    modifier = Modifier.size(20.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(6.dp))
                Text(m.awayName, color = GlassColors.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when (badge) {
                            "进行中" -> GlassColors.up.copy(alpha = 0.15f)
                            "即将开始" -> GlassColors.accentGold.copy(alpha = 0.15f)
                            else -> Color.White.copy(alpha = 0.5f)
                        }
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    badge,
                    color = when (badge) {
                        "进行中" -> GlassColors.up
                        "即将开始" -> GlassColors.accentGold
                        else -> GlassColors.textSecondary
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(2.dp))
            if (m.homeScore != null && m.awayScore != null) {
                Text(
                    "${m.homeScore} : ${m.awayScore}",
                    color = GlassColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text("VS", color = GlassColors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Top3Card(teams: List<Team>, onTeamClick: (Team) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text("世界前三", color = GlassColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            teams.forEachIndexed { idx, team ->
                val medal = when (idx) {
                    0 -> "\uD83E\uDD47"
                    1 -> "\uD83E\uDD48"
                    2 -> "\uD83E\uDD49"
                    else -> ""
                }
                val medalColor = when (idx) {
                    0 -> GlassColors.accentGold
                    1 -> Color(0xFFC0C0C0)
                    2 -> Color(0xFFCD7F32)
                    else -> GlassColors.textSecondary
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onTeamClick(team) }
                        .padding(8.dp)
                ) {
                    Text(medal, fontSize = 28.sp, color = medalColor)
                    Spacer(Modifier.height(4.dp))
                    AsyncImage(
                        model = team.flagUrl,
                        contentDescription = team.name,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(team.name, color = GlassColors.textPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("${"%.1f".format(team.points)} pts", color = GlassColors.textSecondary, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun CompetitionsRow(competitions: List<Competition>, onOpenCompetitions: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("热门赛事", color = GlassColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("全部 ›", color = GlassColors.accentBlue, fontSize = 12.sp, modifier = Modifier.clickable { onOpenCompetitions() })
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(competitions.take(8), key = { it.id }) { c ->
                Column(
                    modifier = Modifier
                        .width(120.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(GlassColors.accentBlue.copy(alpha = 0.12f), GlassColors.accentViolet.copy(alpha = 0.08f))))
                        .clickable { onOpenCompetitions() }
                        .padding(12.dp)
                ) {
                    Text(c.name, color = GlassColors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
                    if (c.region.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(c.region, color = GlassColors.textSecondary, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
