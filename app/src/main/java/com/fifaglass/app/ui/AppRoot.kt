package com.fifaglass.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.fifaglass.app.ui.screens.HomeScreen
import com.fifaglass.app.ui.screens.LiveMatchCompanion
import com.fifaglass.app.ui.screens.LiveStreamScreen
import com.fifaglass.app.ui.screens.MatchDetailScreen
import com.fifaglass.app.ui.screens.MatchesScreen
import com.fifaglass.app.ui.screens.RankingScreen
import com.fifaglass.app.ui.screens.SearchScreen
import com.fifaglass.app.ui.screens.SettingsScreen
import com.fifaglass.app.ui.screens.SettingsStore
import com.fifaglass.app.ui.screens.StatsScreen
import com.fifaglass.app.ui.screens.TeamDetailScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object Ranking : Screen
    data object Compare : Screen
    data object Stats : Screen
    data object Matches : Screen
    data object Competitions : Screen
    data object Settings : Screen
    data object LiveStream : Screen
    data object Search : Screen
    data class TeamDetail(val team: Team) : Screen
    data class MatchDetail(val match: MatchInfo) : Screen
    data class Companion(val match: MatchInfo) : Screen
    data class Stream(val match: MatchInfo?) : Screen
    data class CompetitionMatches(val competition: Competition) : Screen
}

private data class TabInfo(val label: String, val icon: String, val screen: Screen)

private val tabs = listOf(
    TabInfo("主页", "house.fill", Screen.Home),
    TabInfo("排名", "list.number", Screen.Ranking),
    TabInfo("比赛", "soccerball", Screen.Matches),
    TabInfo("直播", "play.tv.fill", Screen.LiveStream),
    TabInfo("我的", "person.crop.circle", Screen.Settings),
)

@Composable
fun AppRoot() {
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    fun navigate(s: Screen) { stack = stack + s }
    fun back() { if (stack.size > 1) stack = stack.dropLast(1) }
    fun toTab(s: Screen) { stack = listOf(s) }

    BackHandler(enabled = stack.size > 1) { back() }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        Favorites.init(context)
        SettingsStore.init(context)
        SettingsStore.recordOpen()
        com.fifaglass.app.data.StreamRepository.initFavorites(context)
    }

    var gender by remember { mutableStateOf(1) }
    var men by remember { mutableStateOf<List<Team>?>(null) }
    var women by remember { mutableStateOf<List<Team>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

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

    LaunchedEffect(stack) {
        val root = stack.firstOrNull()
        selectedTab = when (root) {
            Screen.Home -> 0
            Screen.Ranking, is Screen.TeamDetail -> 1
            Screen.Matches, is Screen.MatchDetail, is Screen.Companion, Screen.Competitions, is Screen.CompetitionMatches -> 2
            Screen.LiveStream, is Screen.Stream -> 3
            Screen.Settings -> 4
            Screen.Search, Screen.Compare, Screen.Stats -> 0
            else -> 0
        }
    }

    ProvideGlassTheme(isDark = false) {
        GlassBackground(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                Crossfade(targetState = screen, label = "page") { s ->
                    when (s) {
                        is Screen.Home -> HomeScreen(
                            teams = current,
                            gender = gender,
                            onGenderChange = { gender = it },
                            error = error,
                            favorites = Favorites.codes,
                            onTeamClick = { navigate(Screen.TeamDetail(it)) },
                            onMatchClick = { navigate(Screen.MatchDetail(it)) },
                            onOpenCompetitions = { navigate(Screen.Competitions) },
                            onOpenRanking = { toTab(Screen.Ranking) },
                            onOpenCompare = { toTab(Screen.Compare) },
                            onOpenMatches = { toTab(Screen.Matches) },
                            onOpenStats = { toTab(Screen.Stats) },

                        )
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
                            rankings = current,
                            onOpenCompanion = { navigate(Screen.Companion(s.match)) },
                            onOpenStream = { navigate(Screen.Stream(s.match)) }
                        )
                        is Screen.Companion -> LiveMatchCompanion(
                            m = s.match,
                            rankings = current,
                            onMatchClick = { navigate(Screen.MatchDetail(it)) },
                            onOpenStream = { navigate(Screen.Stream(s.match)) }
                        )
                        is Screen.Stream -> LiveStreamScreen(match = s.match)
                        is Screen.LiveStream -> LiveStreamScreen(match = null)
                        is Screen.Search -> SearchScreen(
                            teams = current,
                            onTeamClick = { navigate(Screen.TeamDetail(it)) },
                            onMatchClick = { navigate(Screen.MatchDetail(it)) },
                            onCompetitionClick = { navigate(Screen.CompetitionMatches(it)) }
                        )
                        is Screen.Settings -> SettingsScreen()
                    }
                }

                LiquidTabBar(
                    selectedIndex = selectedTab,
                    onTabSelected = { idx -> toTab(tabs[idx].screen) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun LiquidTabBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .shadow(20.dp, RoundedCornerShape(28.dp), clip = false)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        Color(0xFFF5F5F7).copy(alpha = 0.78f)
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { idx, tab ->
                TabItem(
                    tab = tab,
                    selected = idx == selectedIndex,
                    onClick = { onTabSelected(idx) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TabItem(tab: TabInfo, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(if (selected) 1.05f else 1f, label = "tab-scale")
    val iconColor = if (selected) GlassColors.accentBlue else GlassColors.textSecondary
    val textColor = if (selected) GlassColors.textPrimary else GlassColors.textSecondary
    val fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .scale(scale)
                        .clip(RoundedCornerShape(14.dp))
                        .background(GlassColors.accentBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    SimpleIcon(icon = tab.icon, color = iconColor, size = 22.dp)
                }
            } else {
                SimpleIcon(icon = tab.icon, color = iconColor, size = 22.dp)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                tab.label,
                color = textColor,
                fontSize = 10.sp,
                fontWeight = fontWeight
            )
        }
    }
}

@Composable
private fun SimpleIcon(icon: String, color: Color, size: androidx.compose.ui.unit.Dp) {
    val symbol = when (icon) {
        "house.fill" -> "\u2302"
        "list.number" -> "#"
        "soccerball" -> "\u26bd"
        "play.tv.fill" -> "\u25b6"
        "person.crop.circle" -> "\u00b7"
        else -> "?"
    }
    Text(
        symbol,
        color = color,
        fontSize = (size.value * 0.7f).sp,
        fontWeight = FontWeight.Bold
    )
}

private fun Modifier.scale(value: Float): Modifier = this.graphicsLayer(scaleX = value, scaleY = value)
