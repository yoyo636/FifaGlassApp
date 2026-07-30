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
import com.fifaglass.app.ui.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 主页：应用入口，聚合今日比赛、收藏球队、快捷入口与世界前三 */
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
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "FifaGlass",
                    color = GlassColors.textPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    today,
                    color = GlassColors.textSecondary,
                    fontSize = 13.sp
                )
            }
            GenderToggleHome(gender, onGenderChange)
        }
        Spacer(Modifier.height(14.dp))

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
                Spacer(Modifier.height(14.dp))

                val favTeams = teams.filter { it.code in favorites }
                if (favTeams.isNotEmpty()) {
                    FavoritesRow(favTeams, onTeamClick)
                    Spacer(Modifier.height(14.dp))
                }

                SpotlightMatches(live, recent, onMatchClick)
                Spacer(Modifier.height(14.dp))

                if (teams.size >= 3) {
                    Top3Card(teams.take(3), onTeamClick)
                    Spacer(Modifier.height(14.dp))
                }

                if (!competitions.isNullOrEmpty()) {
                    CompetitionsRow(competitions!!, onOpenCompetitions)
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun GenderToggleHome(gender: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier
            .glass(16.dp)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(1 to "男", 2 to "女").forEach { (g, label) ->
            val selected = gender == g
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) {
                            if (g == 1) GlassColors.accentBlue.copy(alpha = 0.3f)
                            else GlassColors.accentPink.copy(alpha = 0.3f)
                        } else Color.Transparent
                    )
                    .clickable { onChange(g) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    label,
                    color = if (selected) GlassColors.textPrimary else GlassColors.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
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
        Triple("排名", GlassColors.accentBlue) { onOpenRanking() },
        Triple("对比", GlassColors.accentPink) { onOpenCompare() },
        Triple("比赛", GlassColors.accentMint) { onOpenMatches() },
        Triple("图表", GlassColors.accentViolet) { onOpenStats() },
    )

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (label, color, onClick) ->
            Box(
                Modifier
                    .weight(1f)
                    .height(76.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(color.copy(alpha = 0.18f))
                    .clickable { onClick() }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = color,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FavoritesRow(teams: List<Team>, onTeamClick: (Team) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(
            "我的关注",
            color = GlassColors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
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
                    Text(
                        team.name,
                        color = GlassColors.textPrimary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "#${team.rank}",
                        color = GlassColors.accentGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
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
            Text(
                "今日焦点",
                color = GlassColors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${playing.size + upcoming.size + finished.size} 场",
                color = GlassColors.textSecondary,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(10.dp))

        if (playing.isEmpty() && upcoming.isEmpty() && finished.isEmpty()) {
            Text(
                "当前窗口暂无焦点比赛",
                color = GlassColors.textSecondary,
                fontSize = 13.sp
            )
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
private fun MatchRow(
    m: MatchInfo,
    onClick: () -> Unit,
    badge: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
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
                    modifier = Modifier.size(22.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    m.homeName,
                    color = GlassColors.textPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val awayFlag = m.awayCode.takeIf { it.isNotEmpty() }?.let { "https://api.fifa.com/api/v3/picture/flags-sq-3/$it" }
                AsyncImage(
                    model = awayFlag,
                    contentDescription = m.awayName,
                    modifier = Modifier.size(22.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    m.awayName,
                    color = GlassColors.textPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when (badge) {
                            "进行中" -> GlassColors.up.copy(alpha = 0.2f)
                            "即将开始" -> GlassColors.accentGold.copy(alpha = 0.2f)
                            else -> GlassColors.textSecondary.copy(alpha = 0.2f)
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    badge,
                    color = when (badge) {
                        "进行中" -> GlassColors.up
                        "即将开始" -> GlassColors.accentGold
                        else -> GlassColors.textSecondary
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            if (m.homeScore != null && m.awayScore != null) {
                Text(
                    "${m.homeScore} : ${m.awayScore}",
                    color = GlassColors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            } else {
                Text(
                    m.date.substring(11, 16).takeIf { m.date.length >= 16 } ?: "--:--",
                    color = GlassColors.textSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun Top3Card(top3: List<Team>, onTeamClick: (Team) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), corner = 24.dp) {
        Text(
            "世界前三",
            color = GlassColors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            PodiumItemHome(top3[1], "\uD83E\uDD48", GlassColors.textSecondary, 0.9f, onTeamClick)
            PodiumItemHome(top3[0], "\uD83E\uDD47", GlassColors.accentGold, 1.15f, onTeamClick)
            PodiumItemHome(top3[2], "\uD83E\uDD49", Color(0xFFE8A87C), 0.82f, onTeamClick)
        }
    }
}

@Composable
private fun PodiumItemHome(
    team: Team,
    medal: String,
    accent: Color,
    scale: Float,
    onTeamClick: (Team) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onTeamClick(team) }
            .padding(6.dp)
    ) {
        Text(medal, fontSize = (22 * scale).sp)
        Spacer(Modifier.height(4.dp))
        AsyncImage(
            model = team.flagUrl,
            contentDescription = team.name,
            modifier = Modifier.size((44 * scale).dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(6.dp))
        Text(
            team.name,
            color = GlassColors.textPrimary,
            fontSize = (13 * scale).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            "%.0f 分".format(team.points),
            color = accent,
            fontSize = (11 * scale).sp
        )
    }
}

@Composable
private fun CompetitionsRow(competitions: List<Competition>, onOpenCompetitions: () -> Unit) {
    val featured = remember(competitions) {
        val priority = listOf("WC", "World Cup", "EURO", "Copa", "Asian Cup", "Africa Cup", "Gold Cup")
        val picked = competitions
            .filter { c -> priority.any { p -> c.name.contains(p, ignoreCase = true) } }
            .take(5)
        if (picked.isNotEmpty()) picked else competitions.take(5)
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "热门赛事",
                color = GlassColors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(GlassColors.accentBlue.copy(alpha = 0.15f))
                    .clickable { onOpenCompetitions() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "全部",
                    color = GlassColors.accentBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(featured, key = { it.id }) { comp ->
                Box(
                    Modifier
                        .width(140.dp)
                        .height(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(GlassColors.accentViolet.copy(alpha = 0.12f))
                        .clickable { onOpenCompetitions() }
                        .padding(12.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Text(
                        comp.name,
                        color = GlassColors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
