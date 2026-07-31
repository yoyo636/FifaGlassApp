package com.fifaglass.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import com.fifaglass.app.data.ForumPost
import com.fifaglass.app.data.ForumRepository
import com.fifaglass.app.data.MatchInfo
import com.fifaglass.app.data.Team
import com.fifaglass.app.data.UserRepository
import com.fifaglass.app.ui.screens.AIAnalystScreen
import com.fifaglass.app.ui.screens.CompareScreen
import com.fifaglass.app.ui.screens.CompetitionMatchesScreen
import com.fifaglass.app.ui.screens.CompetitionsScreen
import com.fifaglass.app.ui.screens.CreatePostScreen
import com.fifaglass.app.ui.screens.FormationScreen
import com.fifaglass.app.ui.screens.ForumScreen
import com.fifaglass.app.ui.screens.FullPool
import com.fifaglass.app.ui.screens.HomeScreen
import com.fifaglass.app.ui.screens.LiveMatchCompanion
import com.fifaglass.app.ui.screens.LiveStreamScreen
import com.fifaglass.app.ui.screens.MatchDetailScreen
import com.fifaglass.app.ui.screens.MatchesScreen
import com.fifaglass.app.ui.screens.MyPostsScreen
import com.fifaglass.app.ui.screens.PostDetailScreen
import com.fifaglass.app.ui.screens.RankingScreen
import com.fifaglass.app.ui.screens.SearchScreen
import com.fifaglass.app.ui.screens.SettingsScreen
import com.fifaglass.app.ui.screens.SettingsStore
import com.fifaglass.app.ui.screens.StatsScreen
import com.fifaglass.app.ui.screens.TeamDetailScreen
import com.fifaglass.app.ui.screens.UserCenterScreen
import com.fifaglass.app.ui.screens.WatchPartyScreen
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
    data object UserCenter : Screen
    data object Forum : Screen
    data object CreatePost : Screen
    data object MyPosts : Screen
    data class TeamDetail(val team: Team) : Screen
    data class MatchDetail(val match: MatchInfo) : Screen
    data class Companion(val match: MatchInfo) : Screen
    data class Stream(val match: MatchInfo?) : Screen
    data class CompetitionMatches(val competition: Competition) : Screen
    data class PostDetail(val post: ForumPost) : Screen
    data class AIAnalyst(val home: Team, val away: Team, val match: MatchInfo?) : Screen
    data class Formation(val home: Team, val away: Team, val homeTactics: String?, val awayTactics: String?) : Screen
    data class WatchParty(val matchId: String?, val matchTitle: String?) : Screen
}

private data class TabInfo(val label: String, val icon: String, val screen: Screen)

private val tabs = listOf(
    TabInfo("主页", "house.fill", Screen.Home),
    TabInfo("排名", "list.number", Screen.Ranking),
    TabInfo("比赛", "soccerball", Screen.Matches),
    TabInfo("直播", "play.tv.fill", Screen.LiveStream),
    TabInfo("我的", "person.crop.circle", Screen.UserCenter),
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
        UserRepository.init(context)
        ForumRepository.init(context)
        com.fifaglass.app.data.WatchPartyRepository.init(context)
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
            Screen.UserCenter, Screen.Settings, Screen.Forum, is Screen.PostDetail, Screen.CreatePost, Screen.MyPosts -> 4
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
                        is Screen.MatchDetail -> {
                            val homeTeam = current?.find { it.code == s.match.homeCode } ?: current?.firstOrNull()
                            val awayTeam = current?.find { it.code == s.match.awayCode } ?: current?.lastOrNull()
                            MatchDetailScreen(
                                m = s.match,
                                rankings = current,
                                onOpenCompanion = { navigate(Screen.Companion(s.match)) },
                                onOpenStream = { navigate(Screen.Stream(s.match)) },
                                onOpenAIAnalyst = {
                                    if (homeTeam != null && awayTeam != null) {
                                        navigate(Screen.AIAnalyst(homeTeam, awayTeam, s.match))
                                    }
                                },
                                onOpenFormation = {
                                    if (homeTeam != null && awayTeam != null) {
                                        navigate(Screen.Formation(homeTeam, awayTeam, s.match.homeTactics, s.match.awayTactics))
                                    }
                                },
                                onOpenWatchParty = {
                                    navigate(Screen.WatchParty(s.match.id, "${s.match.homeName} vs ${s.match.awayName}"))
                                },
                            )
                        }
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
                        is Screen.UserCenter -> UserCenterScreen(
                            onOpenForum = { navigate(Screen.Forum) },
                            onOpenMyPosts = { navigate(Screen.MyPosts) },
                            onOpenSettings = { navigate(Screen.Settings) },
                            onLogout = { }
                        )
                        is Screen.Forum -> ForumScreen(
                            onPostClick = { navigate(Screen.PostDetail(it)) },
                            onCreatePost = { navigate(Screen.CreatePost) }
                        )
                        is Screen.PostDetail -> PostDetailScreen(
                            post = s.post,
                            onBack = { back() }
                        )
                        is Screen.CreatePost -> CreatePostScreen(
                            onPublished = { back() },
                            onBack = { back() }
                        )
                        is Screen.MyPosts -> MyPostsScreen(
                            onPostClick = { navigate(Screen.PostDetail(it)) },
                            onBack = { back() }
                        )
                        is Screen.AIAnalyst -> AIAnalystScreen(
                            home = s.home,
                            away = s.away,
                            match = s.match,
                            onBack = { back() }
                        )
                        is Screen.Formation -> FormationScreen(
                            home = s.home,
                            away = s.away,
                            homeTactics = s.homeTactics,
                            awayTactics = s.awayTactics,
                            onBack = { back() }
                        )
                        is Screen.WatchParty -> WatchPartyScreen(
                            matchId = s.matchId,
                            matchTitle = s.matchTitle,
                            onBack = { back() }
                        )
                    }
                }

                AuroraTabBar(
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
private fun AuroraTabBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .shadow(28.dp, RoundedCornerShape(32.dp), clip = false, ambientColor = Color(0x40000000))
            .clip(RoundedCornerShape(32.dp))
            .background(Aurora.tabBar())
            .padding(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else if (selected) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "tab-scale"
    )
    val iconColor = if (selected) Color.White else GlassColors.textSecondary
    val textColor = if (selected) GlassColors.textPrimary else GlassColors.textSecondary
    val fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(if (selected) 44.dp else 32.dp)
                    .clip(RoundedCornerShape(if (selected) 16.dp else 12.dp))
                    .background(
                        if (selected) Aurora.primary(isDark = false)
                        else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .graphicsLayerScale(scale),
                contentAlignment = Alignment.Center
            ) {
                SimpleIcon(icon = tab.icon, color = iconColor, size = 20.dp)
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

private fun Modifier.graphicsLayerScale(value: Float): Modifier =
    this.then(
        Modifier.graphicsLayer(scaleX = value, scaleY = value)
    )

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
