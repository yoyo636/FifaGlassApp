package com.fifaglass.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fifaglass.app.data.Competition
import com.fifaglass.app.data.FifaApi
import com.fifaglass.app.data.MatchInfo
import com.fifaglass.app.data.Team
import com.fifaglass.app.ui.screens.CompareScreen
import com.fifaglass.app.ui.screens.CompetitionMatchesScreen
import com.fifaglass.app.ui.screens.CompetitionsScreen
import com.fifaglass.app.ui.screens.FullPool
import com.fifaglass.app.ui.screens.MatchDetailScreen
import com.fifaglass.app.ui.screens.MatchesScreen
import com.fifaglass.app.ui.screens.RankingScreen
import com.fifaglass.app.ui.screens.StatsScreen
import com.fifaglass.app.ui.screens.TeamDetailScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Ranking : Screen
    data object Compare : Screen
    data object Stats : Screen
    data object Matches : Screen
    data object Competitions : Screen
    data class TeamDetail(val team: Team) : Screen
    data class MatchDetail(val match: MatchInfo) : Screen
    data class CompetitionMatches(val competition: Competition) : Screen
}

@Composable
fun AppRoot() {
    // 简单导航栈：栈顶为当前页
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Ranking)) }
    fun navigate(s: Screen) { stack = stack + s }
    fun back() { if (stack.size > 1) stack = stack.dropLast(1) }
    fun toTab(s: Screen) { stack = listOf(s) }

    // 安卓系统返回手势：从屏幕边缘侧滑返回上一页
    BackHandler(enabled = stack.size > 1) { back() }

    val context = LocalContext.current
    LaunchedEffect(Unit) { Favorites.init(context) }

    var gender by remember { mutableStateOf(1) }
    var men by remember { mutableStateOf<List<Team>?>(null) }
    var women by remember { mutableStateOf<List<Team>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(gender) {
        if (gender == 1 && men == null) {
            try {
                men = withContext(Dispatchers.IO) { FifaApi.fetchRankings(1) }
                FullPool.teams = men
            } catch (e: Exception) {
                error = e.message ?: "网络请求失败"
            }
        } else if (gender == 2 && women == null) {
            try {
                women = withContext(Dispatchers.IO) { FifaApi.fetchRankings(2) }
            } catch (e: Exception) {
                error = e.message ?: "网络请求失败"
            }
        }
    }

    val current = if (gender == 1) men else women
    val screen = stack.last()

    GlassBackground(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Crossfade(targetState = screen, label = "page") { s ->
                when (s) {
                    is Screen.Ranking -> RankingScreen(
                        teams = current,
                        gender = gender,
                        onGenderChange = { gender = it },
                        error = error,
                        favorites = Favorites.codes,
                        onToggleFavorite = { Favorites.toggle(it) },
                        onTeamClick = { navigate(Screen.TeamDetail(it)) }
                    )
                    is Screen.Compare -> CompareScreen(teams = men, error = error)
                    is Screen.Stats -> StatsScreen(teams = current, error = error)
                    is Screen.Matches -> MatchesScreen(
                        rankings = current,
                        gender = gender,
                        onGenderChange = { gender = it },
                        onMatchClick = { navigate(Screen.MatchDetail(it)) },
                        onOpenCompetitions = { navigate(Screen.Competitions) }
                    )
                    is Screen.Competitions -> CompetitionsScreen(
                        onCompetitionClick = { navigate(Screen.CompetitionMatches(it)) }
                    )
                    is Screen.CompetitionMatches -> CompetitionMatchesScreen(
                        competition = s.competition,
                        onMatchClick = { navigate(Screen.MatchDetail(it)) }
                    )
                    is Screen.TeamDetail -> TeamDetailScreen(
                        team = s.team,
                        all = current ?: listOf(s.team),
                        onMatchClick = { navigate(Screen.MatchDetail(it)) }
                    )
                    is Screen.MatchDetail -> MatchDetailScreen(
                        m = s.match,
                        rankings = current
                    )
                }
            }

            // 底部玻璃导航栏
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .glass(28.dp)
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NavItem(
                    "排名",
                    screen is Screen.Ranking || screen is Screen.TeamDetail
                ) { toTab(Screen.Ranking) }
                NavItem("对比", screen is Screen.Compare) { toTab(Screen.Compare) }
                NavItem("图表", screen is Screen.Stats) { toTab(Screen.Stats) }
                NavItem(
                    "比赛",
                    screen is Screen.Matches || screen is Screen.MatchDetail ||
                        screen is Screen.Competitions || screen is Screen.CompetitionMatches
                ) { toTab(Screen.Matches) }
            }
        }
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .then(if (selected) Modifier.glass(20.dp) else Modifier)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) GlassColors.textPrimary else GlassColors.textSecondary,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
